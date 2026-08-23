package com.vitalypr.daylog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.data.db.DayLogDb
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.data.repo.JobLocationRepository
import com.vitalypr.daylog.di.DatabaseModule
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
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

/** Spec §6.6b: first-enter/last-exit tracking for 2 km job-location fences. */
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
        jobs = JobLocationRepository(db.jobLocationDao(), db.dayDao())
        days = DayRepository(db.dayDao(), db.categoryDao()) { Instant.now() }
        locId = jobs.add("אתר צפון", 32.7, 35.0)
    }

    @After fun teardown() = db.close()

    @Test fun `default radius is 2 km`() = runTest {
        assertEquals(2000, jobs.activeLocations().single().radiusM)
    }

    @Test fun `first enter auto-creates the field job with suggested start`() = runTest {
        jobs.onEnter(locId, date, 600)
        val job = days.getDay(date)!!.fieldJobs.single()
        assertEquals("אתר צפון", job.title)
        assertEquals(600, job.startMin) // effective = suggestion
        assertNull(job.endMin)
        val row = db.dayDao().fieldJobForLocation(date.toString(), locId)!!
        assertEquals(600, row.suggestedStartMin)
        assertNull(row.startMin) // MANUAL column untouched
    }

    @Test fun `lunch break does not count - first enter and last exit win`() = runTest {
        jobs.onEnter(locId, date, 600) // 10:00 arrive
        jobs.onExit(locId, date, 750, visitStartMin = 600) // 12:30 lunch out
        jobs.onEnter(locId, date, 795) // 13:15 back — start must NOT move
        jobs.onExit(locId, date, 1020, visitStartMin = 795) // 17:00 final leave — overwrites lunch exit

        val job = days.getDay(date)!!.fieldJobs.single()
        assertEquals(600, job.startMin)
        assertEquals(1020, job.endMin)
    }

    @Test fun `manual times always win over suggestions`() = runTest {
        jobs.onEnter(locId, date, 600)
        jobs.onExit(locId, date, 1020, visitStartMin = 795)
        val entity = db.dayDao().fieldJobForLocation(date.toString(), locId)!!
        db.dayDao().updateFieldJob(entity.copy(startMin = 590, endMin = 1050)) // user edits

        jobs.onEnter(locId, date, 615) // stray later events
        jobs.onExit(locId, date, 1100, visitStartMin = 615)

        val job = days.getDay(date)!!.fieldJobs.single()
        assertEquals(590, job.startMin)
        assertEquals(1050, job.endMin)
    }

    @Test fun `suggested flags drive the amber UI, cleared once manual`() = runTest {
        jobs.onEnter(locId, date, 600)
        jobs.onExit(locId, date, 1020, visitStartMin = 795)
        var row = observeRow()
        assertTrue(row.isStartSuggested)
        assertTrue(row.isEndSuggested)

        val entity = db.dayDao().fieldJobForLocation(date.toString(), locId)!!
        db.dayDao().updateFieldJob(entity.copy(startMin = 600))
        row = observeRow()
        assertFalse(row.isStartSuggested)
        assertTrue(row.isEndSuggested)
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
     * Under an hour on site is a pass-by: the arrival stands (amber מוצע, the user
     * may confirm it) but no leaving time is invented for a visit that never was.
     */
    @Test fun `driving past a site suggests no leaving time`() = runTest {
        jobs.onEnter(locId, date, 600) // crossed the 2 km fence…
        jobs.onExit(locId, date, 606, visitStartMin = 600) // …and out again six minutes later
        val job = days.getDay(date)!!.fieldJobs.single()
        assertEquals(600, job.startMin)
        assertNull(job.endMin)
    }

    @Test fun `a stay of an hour or more gets its leaving time`() = runTest {
        jobs.onEnter(locId, date, 600)
        jobs.onExit(locId, date, 660, visitStartMin = 600) // exactly one hour on site
        assertEquals(660, days.getDay(date)!!.fieldJobs.single().endMin)
    }

    /** Driving past in the evening must not drag a real 17:00 departure later. */
    @Test fun `a brief evening pass-by does not overwrite a real leaving time`() = runTest {
        jobs.onEnter(locId, date, 600)
        jobs.onExit(locId, date, 1020, visitStartMin = 600) // the real visit, 10:00–17:00
        jobs.onExit(locId, date, 1080, visitStartMin = 1075) // passed by for five minutes
        assertEquals(1020, days.getDay(date)!!.fieldJobs.single().endMin)
    }

    @Test fun `two locations same day create two field jobs`() = runTest {
        val second = jobs.add("אתר דרום", 31.2, 34.8)
        jobs.onEnter(locId, date, 540)
        jobs.onEnter(second, date, 780)
        assertEquals(2, days.getDay(date)!!.fieldJobs.size)
    }

    @Test fun `no tracking on a day marked off or holiday`() = runTest {
        days.setDayType(date, com.vitalypr.daylog.domain.model.DayType.OFF)
        jobs.onEnter(locId, date, 600)
        jobs.onExit(locId, date, 1020, visitStartMin = 795)
        assertTrue(days.getDay(date)!!.fieldJobs.isEmpty())
    }

    @Test fun `next day starts fresh`() = runTest {
        jobs.onEnter(locId, date, 600)
        jobs.onEnter(locId, date.plusDays(1), 615)
        assertEquals(600, days.getDay(date)!!.fieldJobs.single().startMin)
        assertEquals(615, days.getDay(date.plusDays(1))!!.fieldJobs.single().startMin)
    }

    private suspend fun observeRow(): com.vitalypr.daylog.data.repo.FieldJobRow =
        days.observeEditable(date).first()!!.fieldJobRows.single()
}
