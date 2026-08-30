package com.vitalypr.daylog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.data.db.DayLogDb
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.di.DatabaseModule
import com.vitalypr.daylog.domain.model.DayStatus
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.TimeSource
import com.vitalypr.daylog.domain.model.WorkMode
import com.vitalypr.daylog.domain.model.status
import com.vitalypr.daylog.domain.stats.StatsCalculator
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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

    private suspend fun firstProjectId(): Long = db.projectDao().all().first().id

    @Test fun `day is created lazily on first fact`() = runTest {
        assertNull(repo.getDay(date))
        repo.startSession(date, WorkMode.BASE, 492, TimeSource.MANUAL)
        assertEquals(492, repo.getDay(date)!!.sessions.single().startMin)
    }

    @Test fun `default categories seeded once in spec order`() = runTest {
        repo.startSession(date, WorkMode.BASE, 1, TimeSource.MANUAL) // force db open
        assertEquals(8, db.categoryDao().count())
        assertEquals(DayLogDb.DEFAULT_CATEGORIES, db.categoryDao().all().map { it.name })
    }

    @Test fun `default projects seeded`() = runTest {
        repo.startSession(date, WorkMode.BASE, 1, TimeSource.MANUAL)
        assertEquals(DayLogDb.DEFAULT_PROJECTS, db.projectDao().all().map { it.name })
    }

    @Test fun `a second start while one session is running opens nothing`() = runTest {
        assertTrue(repo.startSession(date, WorkMode.BASE, 492, TimeSource.GEOFENCE))
        assertFalse(repo.startSession(date, WorkMode.BASE, 500, TimeSource.GEOFENCE))
        assertEquals(1, repo.getDay(date)!!.sessions.size)
        assertEquals(492, repo.getDay(date)!!.sessions.single().startMin)
    }

    /** The twice-a-day bug: a second visit must not overwrite the first one's hours. */
    @Test fun `two visits to the base on one day are two sessions`() = runTest {
        repo.startSession(date, WorkMode.BASE, 8 * 60, TimeSource.GEOFENCE)
        repo.endSession(date, WorkMode.BASE, 12 * 60, TimeSource.GEOFENCE)
        repo.startSession(date, WorkMode.BASE, 15 * 60, TimeSource.GEOFENCE)
        repo.endSession(date, WorkMode.BASE, 18 * 60, TimeSource.GEOFENCE)

        val sessions = repo.getDay(date)!!.sessions
        assertEquals(2, sessions.size)
        assertEquals(listOf(8 * 60, 15 * 60), sessions.map { it.startMin })
        assertEquals(7 * 60, StatsCalculator.dayMinutes(repo.getDay(date)!!).base)
    }

    /** A session the user closed by hand is not open, so a geofence exit can't move it. */
    @Test fun `geofence exit never overwrites a manual end`() = runTest {
        val id = repo.addSession(date, WorkMode.BASE, startMin = 492, endMin = 1000)
        assertFalse(repo.endSession(date, WorkMode.BASE, 1055, TimeSource.GEOFENCE))
        assertEquals(1000, repo.getDay(date)!!.sessions.single().endMin)
        assertNotNull(id)
    }

    @Test fun `a manual edit of a session always wins`() = runTest {
        repo.startSession(date, WorkMode.BASE, 492, TimeSource.GEOFENCE)
        val open = repo.openSession(date, WorkMode.BASE)!!
        repo.updateSession(open.copy(startMin = 480, startSource = TimeSource.MANUAL.name))
        assertEquals(480, repo.getDay(date)!!.sessions.single().startMin)
        // and the geofence may not re-open one over it
        assertFalse(repo.startSession(date, WorkMode.BASE, 500, TimeSource.GEOFENCE))
    }

    @Test fun `a hand-typed start is never flagged as a short visit`() = runTest {
        repo.startSession(date, WorkMode.BASE, 492, TimeSource.MANUAL)
        val session = repo.openSession(date, WorkMode.BASE)!!
        repo.setStartUncertain(session.id, date, true)
        assertFalse(repo.getDay(date)!!.sessions.single().startUncertain)
    }

    @Test fun `a geofence start can be flagged and un-flagged`() = runTest {
        repo.startSession(date, WorkMode.BASE, 492, TimeSource.GEOFENCE)
        val session = repo.openSession(date, WorkMode.BASE)!!
        repo.setStartUncertain(session.id, date, true)
        assertTrue(repo.getDay(date)!!.sessions.single().startUncertain)
        repo.setStartUncertain(session.id, date, false)
        assertFalse(repo.getDay(date)!!.sessions.single().startUncertain)
    }

    /** Two mutations issued back-to-back must not read the same row and clobber each other. */
    @Test fun `concurrent session writes both survive`() = runTest {
        kotlinx.coroutines.coroutineScope {
            val a = async { repo.startSession(date, WorkMode.BASE, 492, TimeSource.MANUAL) }
            val b = async { repo.startSession(date, WorkMode.HOME, 1080, TimeSource.MANUAL) }
            a.await(); b.await()
        }
        val sessions = repo.getDay(date)!!.sessions
        assertEquals(2, sessions.size)
        assertEquals(setOf(WorkMode.BASE, WorkMode.HOME), sessions.map { it.mode }.toSet())
    }

    @Test fun `clearing a session start empties it back to unset`() = runTest {
        repo.startSession(date, WorkMode.BASE, 492, TimeSource.MANUAL)
        val open = repo.openSession(date, WorkMode.BASE)!!
        repo.updateSession(open.copy(startMin = null))
        assertNull(repo.getDay(date)!!.sessions.single().startMin)
    }

    @Test fun `removing a session removes its activities`() = runTest {
        val id = repo.addSession(date, WorkMode.BASE, startMin = 492, endMin = 1000)
        repo.addActivity(id, categoryId = 4, projectId = firstProjectId())
        assertEquals(1, db.dayDao().allActivities().size)

        repo.removeSession(db.dayDao().sessionsOn(date.toString()).single())
        assertEquals(0, db.dayDao().allActivities().size)
        assertTrue(repo.getDay(date)!!.sessions.isEmpty())
    }

    @Test fun `clearing a time after reporting marks the day edited`() = runTest {
        repo.startSession(date, WorkMode.BASE, 492, TimeSource.MANUAL)
        repo.markReported(date)
        val open = repo.openSession(date, WorkMode.BASE)!!
        repo.updateSession(open.copy(startMin = null))
        assertEquals(DayStatus.REPORTED_EDITED, repo.getDay(date)!!.status())
    }

    @Test fun `activities carry project, category, duration, note, result`() = runTest {
        val sessionId = repo.addSession(date, WorkMode.BASE, startMin = 492, endMin = 1000)
        val projectId = firstProjectId()
        repo.addActivity(sessionId, categoryId = 4, projectId = projectId) // פיתוח is the 4th seed
        val stored = db.dayDao().allActivities().single()
        repo.updateActivity(stored.copy(durationMin = 90, note = "בדיקת קוד", result = "הושלם"))

        val a = repo.getDay(date)!!.activities.single()
        assertEquals(DayLogDb.DEFAULT_PROJECTS.first(), a.project)
        assertEquals("פיתוח", a.category)
        assertEquals(90, a.durationMin)
        assertEquals("בדיקת קוד", a.note)
        assertEquals("הושלם", a.result)
    }

    @Test fun `activities belong to their own session`() = runTest {
        val base = repo.addSession(date, WorkMode.BASE, startMin = 600, endMin = 840)
        val home = repo.addSession(date, WorkMode.HOME, startMin = 1080, endMin = 1260)
        val projectId = firstProjectId()
        repo.addActivity(base, categoryId = 1, projectId = projectId)
        repo.addActivity(home, categoryId = 4, projectId = projectId)

        val day = repo.getDay(date)!!
        assertEquals(listOf(1, 1), day.sessions.map { it.activities.size })
        assertEquals("פיתוח", day.sessions.first { it.mode == WorkMode.HOME }.activities.single().category)
    }

    @Test fun `report lifecycle - reported, edited, resend clears edited`() = runTest {
        repo.startSession(date, WorkMode.BASE, 492, TimeSource.MANUAL)
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

    @Test fun `a field session round-trips with its title`() = runTest {
        repo.startJobSession(date, jobLocationId = 7, title = "אתר אקמה", minutes = 600)
        val session = repo.getDay(date)!!.sessions.single()
        assertEquals(WorkMode.FIELD, session.mode)
        assertEquals("אתר אקמה", session.title)
        assertEquals(600, session.startMin)
        assertEquals(7L, repo.openJobSession(date, 7)!!.jobLocationId)
        assertEquals(1, repo.sessionsForJobLocation(date, 7).size)
    }

    @Test fun `range query returns days in order`() = runTest {
        repo.startSession(date, WorkMode.BASE, 1, TimeSource.MANUAL)
        repo.startSession(date.plusDays(2), WorkMode.BASE, 2, TimeSource.MANUAL)
        val range = repo.getRange(date, date.plusDays(6))
        assertEquals(2, range.size)
        assertEquals(date, range.first().date)
        assertNotNull(range.first().firstStartMin)
    }
}
