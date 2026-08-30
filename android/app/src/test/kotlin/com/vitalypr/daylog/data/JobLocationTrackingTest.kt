package com.vitalypr.daylog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.data.db.DayLogDb
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.data.repo.JobLocationRepository
import com.vitalypr.daylog.di.DatabaseModule
import com.vitalypr.daylog.domain.model.TimeSource
import com.vitalypr.daylog.domain.model.WorkMode
import com.vitalypr.daylog.domain.model.WorkSession
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Spec §6.6b, v2.0: a visit to a client site is a FIELD work session. ENTER opens
 * one, EXIT closes it, and each visit is tracked per location.
 */
@RunWith(RobolectricTestRunner::class)
class JobLocationTrackingTest {

    private lateinit var db: DayLogDb
    private lateinit var jobs: JobLocationRepository
    private lateinit var days: DayRepository
    private val date = LocalDate.of(2026, 8, 4)
    private var locId: Long = 0

    @Before fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DayLogDb::class.java)
            .addCallback(DatabaseModule.SeedCallback)
            .allowMainThreadQueries()
            .build()
        days = DayRepository(db.dayDao(), db.categoryDao()) { Instant.now() }
        jobs = JobLocationRepository(db.jobLocationDao(), days)
        locId = jobs.add("אתר צפון", 32.7, 35.0)
    }

    @After fun teardown() = db.close()

    private suspend fun visits(): List<WorkSession> =
        days.getDay(date)?.sessions?.filter { it.mode == WorkMode.FIELD }.orEmpty()

    @Test fun `default radius is 2 km`() = runTest {
        assertEquals(2000, jobs.activeLocations().single().radiusM)
    }

    @Test fun `first enter opens a field session named after the location`() = runTest {
        jobs.onEnter(locId, date, 600)
        val visit = visits().single()
        assertEquals("אתר צפון", visit.title)
        assertEquals(600, visit.startMin)
        assertNull(visit.endMin)
        assertEquals(TimeSource.GEOFENCE, visit.startSource)
    }

    @Test fun `a duplicate enter while the visit is running opens nothing`() = runTest {
        jobs.onEnter(locId, date, 600)
        jobs.onEnter(locId, date, 615)
        assertEquals(1, visits().size)
        assertEquals(600, visits().single().startMin)
    }

    /** Two stretches on the same site are two sessions — the same rule the base follows. */
    @Test fun `a return visit after a long break is a second session`() = runTest {
        jobs.onEnter(locId, date, 600) // 10:00
        jobs.onExit(locId, date, 750, visitStartMin = 600) // 12:30
        jobs.onEnter(locId, date, 795) // 13:15
        jobs.onExit(locId, date, 1020, visitStartMin = 795) // 17:00

        assertEquals(listOf(600 to 750, 795 to 1020), visits().map { it.startMin to it.endMin })
    }

    @Test fun `a visit the user closed by hand is never re-closed by a geofence exit`() = runTest {
        jobs.onEnter(locId, date, 600)
        val open = days.openJobSession(date, locId)!!
        days.updateSession(open.copy(endMin = 1050, endSource = TimeSource.MANUAL.name))

        jobs.onExit(locId, date, 1100, visitStartMin = 600)
        assertEquals(1050, visits().single().endMin)
    }

    /**
     * An exit we never saw the entry for is a late delivery, not a visit —
     * inventing a row from it produced field jobs with an end and no start.
     */
    @Test fun `exit with no recorded enter creates nothing`() = runTest {
        jobs.onExit(locId, date, 900, visitStartMin = 600)
        assertNull(days.getDay(date))
    }

    /**
     * Under an hour on site is a pass-by: the arrival stands (amber "ביקור קצר",
     * the user may confirm it) but no leaving time is invented.
     */
    @Test fun `driving past a site suggests no leaving time`() = runTest {
        jobs.onEnter(locId, date, 600) // crossed the 2 km fence…
        jobs.onExit(locId, date, 606, visitStartMin = 600) // …and out again six minutes later
        val visit = visits().single()
        assertEquals(600, visit.startMin)
        assertNull(visit.endMin)
        assertTrue(visit.startUncertain)
    }

    @Test fun `a stay of an hour or more gets its leaving time and no doubt mark`() = runTest {
        jobs.onEnter(locId, date, 600)
        jobs.onExit(locId, date, 660, visitStartMin = 600) // exactly one hour on site
        val visit = visits().single()
        assertEquals(660, visit.endMin)
        assertFalse(visit.startUncertain)
    }

    /** Driving past in the evening must not drag the real 17:00 leaving time later. */
    @Test fun `a brief evening pass-by does not touch the finished visit`() = runTest {
        jobs.onEnter(locId, date, 600)
        jobs.onExit(locId, date, 1020, visitStartMin = 600) // the real visit, 10:00–17:00
        jobs.onEnter(locId, date, 1075)
        jobs.onExit(locId, date, 1080, visitStartMin = 1075) // passed by for five minutes

        assertEquals(1020, visits().first().endMin)
        assertNull(visits().last().endMin) // the pass-by stays open and flagged
        assertTrue(visits().last().startUncertain)
    }

    @Test fun `two locations same day track independently`() = runTest {
        val second = jobs.add("אתר דרום", 31.2, 34.8)
        jobs.onEnter(locId, date, 540)
        jobs.onEnter(second, date, 780)
        jobs.onExit(second, date, 900, visitStartMin = 780)

        assertEquals(2, visits().size)
        assertNull(days.openJobSession(date, second))
        assertEquals(540, days.openJobSession(date, locId)!!.startMin) // still on the first site
    }

    @Test fun `no tracking on a day marked off or holiday`() = runTest {
        days.setDayType(date, com.vitalypr.daylog.domain.model.DayType.OFF)
        jobs.onEnter(locId, date, 600)
        jobs.onExit(locId, date, 1020, visitStartMin = 795)
        assertTrue(visits().isEmpty())
    }

    @Test fun `next day starts fresh`() = runTest {
        jobs.onEnter(locId, date, 600)
        jobs.onEnter(locId, date.plusDays(1), 615)
        assertEquals(600, days.getDay(date)!!.sessions.single().startMin)
        assertEquals(615, days.getDay(date.plusDays(1))!!.sessions.single().startMin)
    }
}
