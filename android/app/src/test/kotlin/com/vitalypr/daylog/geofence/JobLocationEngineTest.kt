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
        val jobs = JobLocationRepository(db.jobLocationDao(), db.dayDao())
        days = DayRepository(db.dayDao(), db.categoryDao()) { Instant.now() }
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

    @Test fun `a real visit is recorded first-enter to last-exit`() = runTest {
        enter(10, 0)
        exit(12, 30) // lunch
        enter(13, 15)
        exit(17, 0)
        val job = days.getDay(date)!!.fieldJobs.single()
        assertEquals(600, job.startMin)
        assertEquals(1020, job.endMin)
    }

    @Test fun `an exit we never saw an entry for is ignored`() = runTest {
        exit(17, 0)
        assertNull(days.getDay(date))
    }

    @Test fun `a duplicate enter does not move the suggested start`() = runTest {
        enter(10, 0)
        enter(10, 40) // re-delivered while still on site
        assertEquals(600, days.getDay(date)!!.fieldJobs.single().startMin)
    }

    @Test fun `a visit left open across midnight is not closed the next day`() = runTest {
        enter(10, 0)
        exit(9, 0, date.plusDays(1)) // catch-up delivery the following morning
        val job = days.getDay(date)!!.fieldJobs.single()
        assertEquals(600, job.startMin)
        assertNull(job.endMin) // never invented an end for a day we lost track of
        assertNull(days.getDay(date.plusDays(1))) // and nothing written to today
    }

    @Test fun `a stale enter is not treated as an arrival`() = runTest {
        nowDt = at(12, 0)
        engine.onEnter(locId, at(9, 0)) // delivered three hours late
        assertNull(days.getDay(date))
    }

    @Test fun `driving past the site leaves nothing behind`() = runTest {
        enter(10, 0)
        exit(10, 6)
        assertTrue(days.getDay(date)!!.fieldJobs.isEmpty())
    }
}
