package com.vitalypr.daylog.geofence

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.FakeSettingsSource
import com.vitalypr.daylog.InMemoryFenceStateStore
import com.vitalypr.daylog.data.db.DayLogDb
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.di.DatabaseModule
import com.vitalypr.daylog.domain.model.TimeSource
import com.vitalypr.daylog.notifications.Channels
import com.vitalypr.daylog.notifications.Notifier
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** One test per row of the geofence decision table (spec §5.5/§6.6). */
@RunWith(RobolectricTestRunner::class)
class GeofenceEngineTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: DayLogDb
    private lateinit var repo: DayRepository
    private lateinit var settings: FakeSettingsSource
    private lateinit var fenceState: InMemoryFenceStateStore
    private lateinit var engine: GeofenceEngine
    private lateinit var nm: NotificationManager

    // Tuesday (workday), 08:12.
    private var nowDt = LocalDateTime.of(2026, 8, 4, 8, 12)
    private val today = LocalDate.of(2026, 8, 4)

    @Before fun setup() {
        Channels.ensure(context)
        db = Room.inMemoryDatabaseBuilder(context, DayLogDb::class.java)
            .addCallback(DatabaseModule.SeedCallback)
            .allowMainThreadQueries()
            .build()
        repo = DayRepository(db.dayDao(), db.categoryDao()) { Instant.now() }
        settings = FakeSettingsSource()
        fenceState = InMemoryFenceStateStore()
        engine = GeofenceEngine(
            context, repo, settings, Notifier(context), fenceState,
            com.vitalypr.daylog.widget.DayWidgetRefresher(context),
        ) { nowDt }
        nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @After fun teardown() = db.close()

    private fun titles(): List<String> =
        shadowOf(nm).allNotifications.map { shadowOf(it).contentTitle.toString() }

    private fun at(hour: Int, minute: Int, date: LocalDate = today) =
        LocalDateTime.of(date, LocalTime.of(hour, minute))

    /** Arrive for real: the clock and the transition agree, as on a device. */
    private suspend fun enter(hour: Int, minute: Int, date: LocalDate = today) {
        nowDt = at(hour, minute, date)
        engine.onEnter(nowDt)
    }

    private suspend fun exitDebounce(hour: Int, minute: Int, date: LocalDate = today) {
        nowDt = at(hour, minute, date)
        engine.onExitConfirmedByDebounce(nowDt)
    }

    // --- the reported bug ---------------------------------------------------

    @Test fun `exit delivered with no recorded arrival is ignored, not prompted`() = runTest {
        // Play Services flushes yesterday's exit as the user reaches the office.
        engine.onExitDetected(at(8, 20))
        exitDebounce(8, 30)
        assertTrue(titles().isEmpty(), "a phantom exit must never suggest a departure")
    }

    @Test fun `boundary drift just after arriving does not ask to log the day`() = runTest {
        // Indoor GPS wanders past a 150 m fence while the user sits down at 08:12.
        enter(8, 12)
        engine.onExitDetected(at(8, 20))
        exitDebounce(8, 30)
        assertEquals(listOf("הגעת למשרד?"), titles(), "the arrival suggestion must survive")
    }

    @Test fun `a full day with no arrival logged still offers to log it on the way out`() = runTest {
        enter(8, 12)
        nm.cancelAll() // user ignored the arrival suggestion
        exitDebounce(17, 35)
        assertEquals(listOf("לרשום את היום?"), titles())
    }

    @Test fun `yesterday's visit delivered this morning is dropped, not written to today`() = runTest {
        enter(8, 12, today.minusDays(1))
        repo.setArrival(today.minusDays(1), 492, TimeSource.GEOFENCE)
        nm.cancelAll()

        exitDebounce(8, 30, today) // catch-up delivery the next morning
        assertTrue(titles().isEmpty())
        assertNull(repo.getDay(today)?.departureMin)
    }

    // --- dwell --------------------------------------------------------------

    @Test fun `driving past the office withdraws the suggestion instead of prompting`() = runTest {
        enter(8, 0)
        assertEquals(listOf("הגעת למשרד?"), titles())
        nowDt = at(8, 3)
        engine.onExitDetected(at(8, 3)) // three minutes inside — never actually stopped
        assertTrue(titles().isEmpty())
    }

    @Test fun `a confirmed arrival survives a short exit`() = runTest {
        enter(8, 0)
        engine.confirmArrival(today, 480)
        nowDt = at(8, 3)
        engine.onExitDetected(at(8, 3))
        assertEquals(480, repo.getDay(today)?.arrivalMin)
    }

    // --- decision table -----------------------------------------------------

    @Test fun `enter on workday with no arrival prompts with event time`() = runTest {
        enter(8, 12)
        assertEquals(listOf("הגעת למשרד?"), titles())
        assertNull(repo.getDay(today)?.arrivalMin) // nothing written without confirmation
    }

    @Test fun `duplicate enter while already inside does not re-prompt`() = runTest {
        enter(8, 12)
        nm.cancelAll()
        enter(8, 25)
        assertTrue(titles().isEmpty())
    }

    @Test fun `enter when arrival already set is silent`() = runTest {
        repo.setArrival(today, 480, TimeSource.MANUAL)
        enter(8, 12)
        assertTrue(titles().isEmpty())
    }

    @Test fun `enter on weekend is silent`() = runTest {
        enter(9, 0, LocalDate.of(2026, 8, 8)) // Saturday
        assertTrue(titles().isEmpty())
    }

    @Test fun `confirm arrival writes event time with geofence source`() = runTest {
        engine.confirmArrival(today, 492)
        val day = repo.getDay(today)!!
        assertEquals(492, day.arrivalMin)
        assertEquals(TimeSource.GEOFENCE, day.arrivalSource)
    }

    @Test fun `confirm writes to the event's day, not the day of the tap`() = runTest {
        val yesterday = today.minusDays(1)
        engine.confirmDeparture(yesterday, 1055) // tapped after midnight
        assertEquals(1055, repo.getDay(yesterday)?.departureMin)
        assertNull(repo.getDay(today)?.departureMin)
    }

    @Test fun `silent mode writes arrival directly without notification`() = runTest {
        settings.update { it.copy(silentGeofence = true) }
        enter(8, 12)
        assertEquals(492, repo.getDay(today)?.arrivalMin)
        assertTrue(titles().isEmpty())
    }

    @Test fun `exit debounce - no arrival set offers to log the day`() = runTest {
        enter(8, 12)
        nm.cancelAll()
        exitDebounce(17, 35)
        assertEquals(listOf("לרשום את היום?"), titles())
    }

    @Test fun `exit debounce - departure unset prompts with event time`() = runTest {
        repo.setArrival(today, 492, TimeSource.MANUAL)
        enter(8, 12)
        exitDebounce(17, 35)
        assertEquals(listOf("יציאה 17:35?"), titles())
    }

    @Test fun `exit debounce - geofence departure offers update, last exit wins`() = runTest {
        repo.setArrival(today, 492, TimeSource.MANUAL)
        repo.setDeparture(today, 1000, TimeSource.GEOFENCE)
        enter(8, 12)
        exitDebounce(19, 5)
        assertEquals(listOf("לעדכן יציאה ל־19:05?"), titles())
        engine.confirmDeparture(today, 1145)
        assertEquals(1145, repo.getDay(today)?.departureMin)
    }

    @Test fun `exit debounce - MANUAL departure is never touched`() = runTest {
        repo.setArrival(today, 492, TimeSource.MANUAL)
        repo.setDeparture(today, 1000, TimeSource.MANUAL)
        enter(8, 12)
        exitDebounce(19, 5)
        assertTrue(titles().isEmpty())
        engine.confirmDeparture(today, 1145) // even a stray confirm must not overwrite
        assertEquals(1000, repo.getDay(today)?.departureMin)
    }

    @Test fun `enter on a day marked off is silent`() = runTest {
        repo.setDayType(today, com.vitalypr.daylog.domain.model.DayType.OFF)
        enter(8, 12)
        assertTrue(titles().isEmpty())
    }

    @Test fun `exit on a holiday is silent`() = runTest {
        repo.setDayType(today, com.vitalypr.daylog.domain.model.DayType.HOLIDAY)
        enter(8, 12)
        exitDebounce(17, 35)
        assertTrue(titles().isEmpty())
    }

    @Test fun `enter cancels a pending departure suggestion notification`() = runTest {
        repo.setArrival(today, 492, TimeSource.MANUAL)
        enter(8, 12)
        exitDebounce(12, 30) // lunch exit prompt
        assertEquals(1, titles().size)
        enter(13, 15) // back from lunch
        assertTrue(shadowOf(nm).allNotifications.isEmpty())
    }

    @Test fun `overnight shift attributes the exit to the day that is still open`() = runTest {
        val yesterday = today.minusDays(1)
        repo.setArrival(yesterday, 22 * 60, TimeSource.MANUAL)
        enter(22, 0, yesterday)
        exitDebounce(1, 30, today)
        // Stored as 25:30 on the day that is still open, rendered per §6.2.
        assertEquals(listOf("יציאה 01:30 (למחרת)?"), titles())
    }

    @Test fun `a stale transition is not treated as an arrival`() = runTest {
        nowDt = at(9, 30)
        engine.onEnter(at(7, 0)) // delivered two and a half hours late
        assertTrue(titles().isEmpty())
    }
}
