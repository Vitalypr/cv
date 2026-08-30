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
import com.vitalypr.daylog.domain.model.WorkMode
import com.vitalypr.daylog.domain.model.WorkSession
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
import kotlin.test.assertFalse
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
        // These rows cover suggestion mode; automatic mode (the v1.1 default) has
        // its own tests below.
        settings.update { it.copy(silentGeofence = false) }
        fenceState = InMemoryFenceStateStore()
        engine = GeofenceEngine(
            context, repo, settings, Notifier(context), fenceState,
            com.vitalypr.daylog.geofence.GeofenceLog(context),
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

    /** The base visits of a day, in order — v2.0 stores worked time as sessions. */
    private suspend fun visits(on: LocalDate = today): List<WorkSession> =
        repo.getDay(on)?.sessions?.filter { it.mode == WorkMode.BASE }.orEmpty()

    private suspend fun arrival(on: LocalDate = today): Int? = visits(on).firstOrNull()?.startMin
    private suspend fun departure(on: LocalDate = today): Int? = visits(on).lastOrNull()?.endMin
    private suspend fun uncertain(on: LocalDate = today): Boolean =
        visits(on).lastOrNull()?.startUncertain == true

    /** Pre-existing hours, as if typed in the app. */
    private suspend fun typedArrival(minutes: Int, on: LocalDate = today) =
        repo.startSession(on, WorkMode.BASE, minutes, TimeSource.MANUAL)

    private suspend fun typedDeparture(minutes: Int, on: LocalDate = today) =
        repo.recordDeparture(on, WorkMode.BASE, minutes, TimeSource.MANUAL)

    /** The real sequence: an exit is detected, then the debounce elapses. */
    private suspend fun exitDebounce(hour: Int, minute: Int, date: LocalDate = today) {
        nowDt = at(hour, minute, date)
        engine.onExitDetected(nowDt)
        engine.onExitConfirmedByDebounce()
    }

    // --- the reported bug ---------------------------------------------------

    @Test fun `exit delivered with no recorded arrival is ignored, not prompted`() = runTest {
        // Play Services flushes yesterday's exit as the user reaches the office.
        nowDt = at(8, 20)
        engine.onExitDetected(nowDt)
        engine.onExitConfirmedByDebounce()
        assertTrue(titles().isEmpty(), "a phantom exit must never suggest a departure")
    }

    @Test fun `boundary drift just after arriving does not ask to log the day`() = runTest {
        // Indoor GPS wanders past a 150 m fence while the user sits down at 08:12.
        enter(8, 12)
        exitDebounce(8, 30)
        // The stay was under an hour: the arrival is re-offered as a short visit,
        // and no departure is invented.
        assertEquals(listOf("ביקור קצר במשרד — לרשום כניסה?"), titles())
    }

    @Test fun `a short visit flags an arrival the geofence already logged`() = runTest {
        enter(8, 12)
        engine.confirmArrival(today, 8 * 60)
        nm.cancelAll()
        exitDebounce(8, 40)
        assertTrue(titles().isEmpty(), "no departure for a 28-minute visit")
        assertTrue(uncertain(), "arrival should be flagged amber")
    }

    @Test fun `a real stay later in the day clears the short-visit flag`() = runTest {
        enter(8, 12)
        engine.confirmArrival(today, 8 * 60)
        exitDebounce(8, 40)
        assertTrue(uncertain())

        enter(9, 30)
        exitDebounce(17, 35)
        assertFalse(uncertain())
        assertTrue(titles().any { it.startsWith("יציאה") })
    }

    @Test fun `a hand-typed arrival is never flagged by a short visit`() = runTest {
        typedArrival(8 * 60)
        enter(8, 12)
        exitDebounce(8, 40)
        assertFalse(uncertain())
    }

    @Test fun `a full day with no arrival logged still offers to log it on the way out`() = runTest {
        enter(8, 12)
        nm.cancelAll() // user ignored the arrival suggestion
        exitDebounce(17, 35)
        assertEquals(listOf("לרשום את היום?"), titles())
    }

    @Test fun `exactly one hour counts as a real visit`() = runTest {
        typedArrival(8 * 60)
        enter(8, 12)
        exitDebounce(9, 12)
        assertEquals(listOf("יציאה 09:15?"), titles())
    }

    @Test fun `yesterday's visit delivered this morning is dropped, not written to today`() = runTest {
        enter(8, 12, today.minusDays(1))
        repo.startSession(today.minusDays(1), WorkMode.BASE, 8 * 60, TimeSource.GEOFENCE)
        nm.cancelAll()

        exitDebounce(8, 30, today) // catch-up delivery the next morning
        assertTrue(titles().isEmpty())
        assertNull(departure())
    }

    // --- dwell --------------------------------------------------------------

    @Test fun `driving past the office never suggests a leaving time`() = runTest {
        enter(9, 0)
        exitDebounce(9, 3) // three minutes inside — never actually stopped
        assertTrue(titles().none { it.startsWith("יציאה") || it == "לרשום את היום?" })
        assertNull(departure())
    }

    // --- decision table -----------------------------------------------------

    @Test fun `enter on workday with no arrival prompts with event time`() = runTest {
        enter(8, 12)
        assertEquals(listOf("הגעת למשרד?"), titles())
        assertNull(arrival()) // nothing written without confirmation
    }

    @Test fun `duplicate enter while already inside does not re-prompt`() = runTest {
        enter(8, 12)
        nm.cancelAll()
        enter(8, 25)
        assertTrue(titles().isEmpty())
    }

    @Test fun `enter when arrival already set is silent`() = runTest {
        typedArrival(8 * 60)
        enter(8, 12)
        assertTrue(titles().isEmpty())
    }

    @Test fun `enter on weekend is silent`() = runTest {
        enter(9, 0, LocalDate.of(2026, 8, 8)) // Saturday
        assertTrue(titles().isEmpty())
    }

    @Test fun `confirm arrival writes event time with geofence source`() = runTest {
        engine.confirmArrival(today, 8 * 60)
        val visit = visits().single()
        assertEquals(8 * 60, visit.startMin)
        assertEquals(TimeSource.GEOFENCE, visit.startSource)
    }

    @Test fun `confirm writes to the event's day, not the day of the tap`() = runTest {
        val yesterday = today.minusDays(1)
        repo.startSession(yesterday, WorkMode.BASE, 8 * 60, TimeSource.GEOFENCE)
        engine.confirmDeparture(yesterday, 17 * 60 + 35) // tapped after midnight
        assertEquals(17 * 60 + 45, departure(yesterday))
        assertNull(repo.getDay(today))
    }

    // --- automatic recording (the v1.1 default) -----------------------------

    @Test fun `automatic mode records the arrival as it happens`() = runTest {
        settings.update { it.copy(silentGeofence = true) }
        enter(8, 12)
        assertEquals(8 * 60, arrival())
        assertEquals(listOf("נרשמה כניסה 08:00"), titles()) // informational, correctable
    }

    /**
     * The reported failure: two visits in a day recorded only one of them, because
     * nothing was written unless a notification was tapped.
     */
    @Test fun `two visits in a day are two sessions, both kept in full`() = runTest {
        settings.update { it.copy(silentGeofence = true) }
        enter(8, 0)
        exitDebounce(11, 0)
        enter(14, 0)
        exitDebounce(18, 0)

        assertEquals(
            listOf(8 * 60 to 11 * 60, 14 * 60 to 18 * 60),
            visits().map { it.startMin to it.endMin },
        )
        assertEquals(7 * 60, com.vitalypr.daylog.domain.stats.StatsCalculator.dayMinutes(repo.getDay(today)!!).base)
    }

    /**
     * The exit was never delivered, so the fence stayed "inside" overnight. Before
     * v1.1 that silently disabled every following day.
     */
    @Test fun `a missed exit does not silence the next day`() = runTest {
        settings.update { it.copy(silentGeofence = true) }
        enter(8, 0)
        assertEquals(480, arrival())
        // …no exit ever arrives…

        val tomorrow = today.plusDays(1)
        enter(8, 30, tomorrow)
        exitDebounce(17, 0, tomorrow)

        assertEquals(8 * 60 + 30, arrival(tomorrow))
        assertEquals(17 * 60, departure(tomorrow))
    }

    /** The debounce alarm never fired (Doze); coming back next day must still work. */
    @Test fun `a stranded pending exit is settled when the user returns`() = runTest {
        settings.update { it.copy(silentGeofence = true) }
        enter(8, 0)
        nowDt = at(17, 0)
        engine.onExitDetected(nowDt) // alarm armed, never delivered

        val tomorrow = today.plusDays(1)
        enter(8, 30, tomorrow)

        assertEquals(17 * 60, departure(), "yesterday closes")
        assertEquals(8 * 60 + 30, arrival(tomorrow), "today opens")
    }

    @Test fun `exit debounce - no arrival set offers to log the day`() = runTest {
        enter(8, 12)
        nm.cancelAll()
        exitDebounce(17, 35)
        assertEquals(listOf("לרשום את היום?"), titles())
    }

    @Test fun `exit debounce - departure unset prompts with event time`() = runTest {
        typedArrival(8 * 60)
        enter(8, 12)
        exitDebounce(17, 35)
        assertEquals(listOf("יציאה 17:45?"), titles())
    }

    /**
     * Lunch used to be an overwrite ("only the last visit is recorded"). Now the
     * afternoon return opens its own visit and both survive with their own hours.
     */
    @Test fun `returning after a confirmed departure opens a second visit`() = runTest {
        typedArrival(8 * 60)
        enter(8, 12)
        exitDebounce(12, 30)
        engine.confirmDeparture(today, 12 * 60 + 30)
        nm.cancelAll()

        enter(13, 15)
        assertEquals(listOf("הגעת למשרד?"), titles())
        engine.confirmArrival(today, 13 * 60 + 15)
        exitDebounce(17, 35)
        engine.confirmDeparture(today, 17 * 60 + 35)

        assertEquals(
            listOf(8 * 60 to 12 * 60 + 30, 13 * 60 + 15 to 17 * 60 + 45),
            visits().map { it.startMin to it.endMin },
        )
    }

    @Test fun `a hand-typed leaving time is never touched by the geofence`() = runTest {
        typedArrival(8 * 60)
        enter(8, 12)
        typedDeparture(16 * 60 + 45) // the user closes the day by hand while still inside
        exitDebounce(19, 5)
        engine.confirmDeparture(today, 19 * 60 + 5) // even a stray confirm must not overwrite
        assertEquals(16 * 60 + 45, departure())
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
        typedArrival(8 * 60)
        enter(8, 12)
        exitDebounce(12, 30) // lunch exit prompt
        assertEquals(1, titles().size)
        enter(13, 15) // back from lunch
        assertTrue(shadowOf(nm).allNotifications.isEmpty())
    }

    @Test fun `overnight shift attributes the exit to the day that is still open`() = runTest {
        val yesterday = today.minusDays(1)
        typedArrival(22 * 60, yesterday)
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
