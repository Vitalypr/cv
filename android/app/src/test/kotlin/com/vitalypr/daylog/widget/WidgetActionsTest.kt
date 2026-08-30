package com.vitalypr.daylog.widget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.data.db.DayLogDb
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.di.DatabaseModule
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.TimeSource
import com.vitalypr.daylog.domain.model.WorkMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The two widget buttons: they log the real clock time and override what is stored. */
@RunWith(RobolectricTestRunner::class)
class WidgetActionsTest {

    private lateinit var db: DayLogDb
    private lateinit var repo: DayRepository
    private lateinit var actions: WidgetActions

    private var nowDt = LocalDateTime.of(2026, 8, 4, 8, 12)
    private val today = LocalDate.of(2026, 8, 4)

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DayLogDb::class.java)
            .addCallback(DatabaseModule.SeedCallback)
            .allowMainThreadQueries()
            .build()
        repo = DayRepository(db.dayDao(), db.categoryDao()) { Instant.now() }
        actions = WidgetActions(repo) { nowDt }
    }

    @After fun teardown() = db.close()

    @Test fun `arrival tap opens a base session at the real current time`() = runTest {
        assertTrue(actions.record(arrival = true))
        assertEquals(8 * 60 + 12, arrival())
        assertEquals(WorkMode.BASE, repo.getDay(today)!!.sessions.single().mode)
    }

    @Test fun `departure tap records the real current time`() = runTest {
        nowDt = LocalDateTime.of(2026, 8, 4, 17, 35)
        assertTrue(actions.record(arrival = false))
        assertEquals(17 * 60 + 35, departure())
    }

    @Test fun `a second tap overrides the earlier value`() = runTest {
        actions.record(arrival = true)
        nowDt = LocalDateTime.of(2026, 8, 4, 9, 5)
        actions.record(arrival = true)
        assertEquals(9 * 60 + 5, arrival())
        assertEquals(1, repo.getDay(today)!!.sessions.size) // corrected, not duplicated
    }

    @Test fun `widget overrides a geofence-written value`() = runTest {
        repo.startSession(today, WorkMode.BASE, 500, TimeSource.GEOFENCE)
        actions.record(arrival = true)
        val session = repo.getDay(today)!!.sessions.single()
        assertEquals(8 * 60 + 12, session.startMin)
        assertEquals(TimeSource.MANUAL, session.startSource) // and locks out later geofence writes
    }

    @Test fun `widget overrides a value typed in the app`() = runTest {
        repo.startSession(today, WorkMode.BASE, 480, TimeSource.MANUAL)
        actions.record(arrival = true)
        assertEquals(8 * 60 + 12, arrival())
    }

    @Test fun `special day refuses the tap and writes nothing`() = runTest {
        repo.setDayType(today, DayType.OFF)
        assertFalse(actions.record(arrival = true))
        assertTrue(repo.getDay(today)!!.sessions.isEmpty())
    }

    /** A ✓ belongs to the day it was logged on — the next day starts clean. */
    @Test fun `yesterday's value does not carry into today`() = runTest {
        actions.record(arrival = true)
        assertEquals(8 * 60 + 12, arrival())

        nowDt = LocalDateTime.of(2026, 8, 5, 7, 50)
        val tomorrow = LocalDate.of(2026, 8, 5)
        assertNull(WidgetState.of(repo.getDay(tomorrow)).arrivalMin) // live clock again

        actions.record(arrival = true)
        assertEquals(7 * 60 + 50, arrival(tomorrow))
        assertEquals(8 * 60 + 12, arrival()) // yesterday untouched
    }

    @Test fun `past-midnight tap lands on the current logical day`() = runTest {
        nowDt = LocalDateTime.of(2026, 8, 5, 1, 30)
        actions.record(arrival = false)
        assertEquals(90, departure(LocalDate.of(2026, 8, 5)))
    }

    /** Leaving after a second visit closes that visit, not the finished morning one. */
    @Test fun `a leaving tap closes the running visit`() = runTest {
        repo.startSession(today, WorkMode.BASE, 8 * 60, TimeSource.GEOFENCE)
        repo.endSession(today, WorkMode.BASE, 12 * 60, TimeSource.GEOFENCE)
        repo.startSession(today, WorkMode.BASE, 15 * 60, TimeSource.GEOFENCE)

        nowDt = LocalDateTime.of(2026, 8, 4, 18, 0)
        actions.record(arrival = false)
        assertEquals(listOf(12 * 60, 18 * 60), repo.getDay(today)!!.sessions.map { it.endMin })
    }

    private suspend fun arrival(on: LocalDate = today): Int? =
        WidgetState.of(repo.getDay(on)).arrivalMin

    private suspend fun departure(on: LocalDate = today): Int? =
        WidgetState.of(repo.getDay(on)).departureMin
}
