package com.vitalypr.daylog.geofence

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.data.db.DayLogDb
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.FakeSettingsSource
import com.vitalypr.daylog.di.DatabaseModule
import com.vitalypr.daylog.domain.model.TimeSource
import com.vitalypr.daylog.notifications.Channels
import com.vitalypr.daylog.notifications.Notifier
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
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
        engine = GeofenceEngine(context, repo, settings, Notifier(context)) { nowDt }
        nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @After fun teardown() = db.close()

    private fun titles(): List<String> =
        shadowOf(nm).allNotifications.map { shadowOf(it).contentTitle.toString() }

    @Test fun `enter on workday with no arrival prompts with event time`() = runTest {
        engine.onEnter()
        assertEquals(listOf("הגעת למשרד?"), titles())
        assertNull(repo.getDay(today)?.arrivalMin) // nothing written without confirmation
    }

    @Test fun `enter when arrival already set is silent`() = runTest {
        repo.setArrival(today, 480, TimeSource.MANUAL)
        engine.onEnter()
        assertTrue(titles().isEmpty())
    }

    @Test fun `enter on weekend is silent`() = runTest {
        nowDt = LocalDateTime.of(2026, 8, 8, 9, 0) // Saturday
        engine.onEnter()
        assertTrue(titles().isEmpty())
    }

    @Test fun `confirm arrival writes event time with geofence source`() = runTest {
        engine.confirmArrival(492)
        val day = repo.getDay(today)!!
        assertEquals(492, day.arrivalMin)
        assertEquals(TimeSource.GEOFENCE, day.arrivalSource)
    }

    @Test fun `silent mode writes arrival directly without notification`() = runTest {
        settings.update { it.copy(silentGeofence = true) }
        engine.onEnter()
        assertEquals(492, repo.getDay(today)?.arrivalMin)
        assertTrue(titles().isEmpty())
    }

    @Test fun `exit debounce - no arrival set offers to log the day`() = runTest {
        engine.onExitConfirmedByDebounce(1055)
        assertEquals(listOf("לרשום את היום?"), titles())
    }

    @Test fun `exit debounce - departure unset prompts with event time`() = runTest {
        repo.setArrival(today, 492, TimeSource.MANUAL)
        engine.onExitConfirmedByDebounce(1055)
        assertEquals(listOf("יציאה 17:35?"), titles())
    }

    @Test fun `exit debounce - geofence departure offers update, last exit wins`() = runTest {
        repo.setArrival(today, 492, TimeSource.MANUAL)
        repo.setDeparture(today, 1000, TimeSource.GEOFENCE)
        engine.onExitConfirmedByDebounce(1145)
        assertEquals(listOf("לעדכן יציאה ל־19:05?"), titles())
        engine.confirmDeparture(1145)
        assertEquals(1145, repo.getDay(today)?.departureMin)
    }

    @Test fun `exit debounce - MANUAL departure is never touched`() = runTest {
        repo.setArrival(today, 492, TimeSource.MANUAL)
        repo.setDeparture(today, 1000, TimeSource.MANUAL)
        engine.onExitConfirmedByDebounce(1145)
        assertTrue(titles().isEmpty())
        engine.confirmDeparture(1145) // even a stray confirm must not overwrite
        assertEquals(1000, repo.getDay(today)?.departureMin)
    }

    @Test fun `enter cancels a pending departure suggestion notification`() = runTest {
        repo.setArrival(today, 492, TimeSource.MANUAL)
        engine.onExitConfirmedByDebounce(750) // lunch exit prompt
        assertEquals(1, titles().size)
        engine.onEnter() // back from lunch
        assertTrue(shadowOf(nm).allNotifications.isEmpty())
    }
}
