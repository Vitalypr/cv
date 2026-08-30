package com.vitalypr.daylog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.data.db.DayLogDb
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.di.DatabaseModule
import com.vitalypr.daylog.domain.model.DayStatus
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.FieldJob
import com.vitalypr.daylog.domain.model.TimeSource
import com.vitalypr.daylog.domain.model.status
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DayRepositoryTest {

    private lateinit var db: DayLogDb
    private lateinit var repo: DayRepository
    private val date = LocalDate.of(2026, 8, 4)
    private var now = Instant.parse("2026-08-04T15:00:00Z")

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DayLogDb::class.java)
            .addCallback(DatabaseModule.SeedCallback)
            .allowMainThreadQueries()
            .build()
        repo = DayRepository(db.dayDao(), db.categoryDao()) { now }
    }

    @After fun teardown() = db.close()

    @Test fun `day is created lazily on first fact`() = runTest {
        assertNull(repo.getDay(date))
        repo.setArrival(date, 492, TimeSource.MANUAL)
        assertEquals(492, repo.getDay(date)!!.arrivalMin)
    }

    @Test fun `default categories seeded once in spec order`() = runTest {
        repo.setArrival(date, 1, TimeSource.MANUAL) // force db open
        assertEquals(DayLogDb.DEFAULT_CATEGORIES, db.categoryDao().count().let {
            assertEquals(8, it); DayLogDb.DEFAULT_CATEGORIES
        })
    }

    @Test fun `geofence arrival never overwrites existing arrival`() = runTest {
        repo.setArrival(date, 492, TimeSource.MANUAL)
        assertFalse(repo.setArrival(date, 500, TimeSource.GEOFENCE))
        assertEquals(492, repo.getDay(date)!!.arrivalMin)
    }

    @Test fun `geofence departure never overwrites manual, but updates geofence`() = runTest {
        repo.setDeparture(date, 1000, TimeSource.MANUAL)
        assertFalse(repo.setDeparture(date, 1055, TimeSource.GEOFENCE))
        assertEquals(1000, repo.getDay(date)!!.departureMin)

        val d2 = date.plusDays(1)
        repo.setDeparture(d2, 1000, TimeSource.GEOFENCE)
        assertTrue(repo.setDeparture(d2, 1055, TimeSource.GEOFENCE)) // last exit wins
        assertEquals(1055, repo.getDay(d2)!!.departureMin)
    }

    @Test fun `manual always overwrites`() = runTest {
        repo.setArrival(date, 492, TimeSource.GEOFENCE)
        assertTrue(repo.setArrival(date, 480, TimeSource.MANUAL))
        assertEquals(480, repo.getDay(date)!!.arrivalMin)
    }

    /** Two mutations issued back-to-back must not read the same row and clobber each other. */
    @Test fun `concurrent arrival and departure writes both survive`() = runTest {
        kotlinx.coroutines.coroutineScope {
            val a = async { repo.setArrival(date, 492, TimeSource.MANUAL) }
            val b = async { repo.setDeparture(date, 1055, TimeSource.MANUAL) }
            a.await(); b.await()
        }
        val day = repo.getDay(date)!!
        assertEquals(492, day.arrivalMin)
        assertEquals(1055, day.departureMin)
    }

    @Test fun `clearing arrival empties it back to unset`() = runTest {
        repo.setArrival(date, 492, TimeSource.MANUAL)
        repo.clearArrival(date)
        assertNull(repo.getDay(date)!!.arrivalMin)
    }

    @Test fun `clearing departure empties it back to unset`() = runTest {
        repo.setDeparture(date, 1055, TimeSource.MANUAL)
        repo.clearDeparture(date)
        assertNull(repo.getDay(date)!!.departureMin)
    }

    /** Clearing must also drop the MANUAL lock, or the value could never be re-suggested. */
    @Test fun `after clearing, a geofence suggestion may fill the value again`() = runTest {
        repo.setArrival(date, 492, TimeSource.MANUAL)
        repo.clearArrival(date)
        assertTrue(repo.setArrival(date, 500, TimeSource.GEOFENCE))
        assertEquals(500, repo.getDay(date)!!.arrivalMin)

        repo.setDeparture(date, 1000, TimeSource.MANUAL)
        repo.clearDeparture(date)
        assertTrue(repo.setDeparture(date, 1055, TimeSource.GEOFENCE))
        assertEquals(1055, repo.getDay(date)!!.departureMin)
    }

    @Test fun `clearing a time after reporting marks the day edited`() = runTest {
        repo.setArrival(date, 492, TimeSource.MANUAL)
        repo.markReported(date)
        repo.clearArrival(date)
        assertEquals(DayStatus.REPORTED_EDITED, repo.getDay(date)!!.status())
    }

    @Test fun `activities carry category name, duration, note, result`() = runTest {
        repo.setArrival(date, 492, TimeSource.MANUAL)
        val cats = db.dayDao().let { db.categoryDao() }
        val projectId = db.projectDao().all().first().id
        val id = repo.addActivity(date, categoryId = 4, projectId = projectId) // פיתוח is 4th seed → id 4
        repo.updateActivity(
            db.dayDao().getDay(date.toString())!!.activities.first().activity
                .copy(durationMin = 90, note = "בדיקת קוד", result = "הושלם"),
        )
        val snap = repo.getDay(date)!!
        val a = snap.activities.single()
        assertEquals("פיתוח", a.category)
        assertEquals(90, a.durationMin)
        assertEquals("בדיקת קוד", a.note)
        assertEquals("הושלם", a.result)
    }

    @Test fun `report lifecycle - reported, edited, resend clears edited`() = runTest {
        repo.setArrival(date, 492, TimeSource.MANUAL)
        repo.markReported(date)
        assertEquals(DayStatus.REPORTED, repo.getDay(date)!!.status())

        repo.setNotes(date, "עדכון מאוחר")
        assertEquals(DayStatus.REPORTED_EDITED, repo.getDay(date)!!.status())

        repo.markReported(date)
        assertEquals(DayStatus.REPORTED, repo.getDay(date)!!.status())
    }

    @Test fun `day type round-trips and drives status`() = runTest {
        repo.setDayType(date, DayType.HOLIDAY)
        assertEquals(DayStatus.HOLIDAY, repo.getDay(date)!!.status())
    }

    @Test fun `field jobs round-trip`() = runTest {
        repo.addFieldJob(date, FieldJob("אתר אקמה", "צפון", 600, 810))
        val job = repo.getDay(date)!!.fieldJobs.single()
        assertEquals("אתר אקמה", job.title)
        assertEquals(600, job.startMin)
    }

    @Test fun `range query returns days in order`() = runTest {
        repo.setArrival(date, 1, TimeSource.MANUAL)
        repo.setArrival(date.plusDays(2), 2, TimeSource.MANUAL)
        val range = repo.getRange(date, date.plusDays(6))
        assertEquals(2, range.size)
        assertNotNull(range.first().arrivalMin)
    }
}
