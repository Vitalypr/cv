package com.vitalypr.daylog.geofence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.InMemoryFenceStateStore
import com.vitalypr.daylog.data.db.DayLogDb
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.data.repo.JobLocationRepository
import com.vitalypr.daylog.di.DatabaseModule
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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ordering invariants for the 2 km job fences (spec §6.6b). */
@RunWith(RobolectricTestRunner::class)
class JobLocationEngineTest {

    private lateinit var db: DayLogDb
    private lateinit var days: DayRepository
    private lateinit var engine: JobLocationEngine
    private lateinit var fenceState: InMemoryFenceStateStore

    private val date = LocalDate.of(2026, 8, 4)
    private var nowDt = LocalDateTime.of(2026, 8, 4, 10, 0)
    private var locId: Long = 0

    @Before fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DayLogDb::class.java)
            .addCallback(DatabaseModule.SeedCallback)
            .allowMainThreadQueries()
            .build()
        days = DayRepository(db.dayDao(), db.categoryDao()) { Instant.now() }
        val jobs = JobLocationRepository(db.jobLocationDao(), days)
        fenceState = InMemoryFenceStateStore()
        engine = JobLocationEngine(jobs, fenceState) { nowDt }
        locId = jobs.add("אתר צפון", 32.7, 35.0)
    }

    @After fun teardown() = db.close()

    private fun at(hour: Int, minute: Int, on: LocalDate = date) =
        LocalDateTime.of(on, LocalTime.of(hour, minute))

    private suspend fun enter(hour: Int, minute: Int, on: LocalDate = date) {
        nowDt = at(hour, minute, on)
        engine.onEnter(locId, nowDt)
    }

    private suspend fun exit(hour: Int, minute: Int, on: LocalDate = date) {
        nowDt = at(hour, minute, on)
        engine.onExit(locId, nowDt)
    }

    @Test fun `each stretch on site is its own session`() = runTest {
        enter(10, 0)
        exit(12, 30) // lunch, after 2.5 h on site
        enter(13, 15)
        exit(17, 0)
        assertEquals(
            listOf(600 to 750, 795 to 1020),
            visits().map { it.startMin to it.endMin },
        )
    }

    @Test fun `an exit we never saw an entry for is ignored`() = runTest {
        exit(17, 0)
        assertNull(days.getDay(date))
    }

    @Test fun `a duplicate enter does not move the suggested start`() = runTest {
        enter(10, 0)
        enter(10, 40) // re-delivered while still on site
        assertEquals(600, visits().single().startMin)
    }

    @Test fun `a visit left open across midnight is not closed the next day`() = runTest {
        enter(10, 0)
        exit(9, 0, date.plusDays(1)) // catch-up delivery the following morning
        val visit = visits().single()
        assertEquals(600, visit.startMin)
        assertNull(visit.endMin) // never invented an end for a day we lost track of
        assertNull(days.getDay(date.plusDays(1))) // and nothing written to today
    }

    @Test fun `a stale enter is not treated as an arrival`() = runTest {
        nowDt = at(12, 0)
        engine.onEnter(locId, at(9, 0)) // delivered three hours late
        assertNull(days.getDay(date))
    }

    @Test fun `driving past the site suggests an arrival but never a leaving time`() = runTest {
        enter(10, 0)
        exit(10, 6)
        val visit = visits().single()
        assertEquals(600, visit.startMin)
        assertNull(visit.endMin)
        assertTrue(visit.startUncertain) // amber "ביקור קצר"
    }

    private suspend fun visits(): List<com.vitalypr.daylog.domain.model.WorkSession> =
        days.getDay(date)?.sessions.orEmpty()
}
