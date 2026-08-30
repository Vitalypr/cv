package com.vitalypr.daylog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.data.db.ActivityEntity
import com.vitalypr.daylog.data.db.DayLogDb
import com.vitalypr.daylog.data.db.WorkDayEntity
import com.vitalypr.daylog.data.db.WorkSessionEntity
import com.vitalypr.daylog.data.repo.ProjectRepository
import com.vitalypr.daylog.di.DatabaseModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Projects: seeded defaults, add/remove, and the never-break-history rule. */
@RunWith(RobolectricTestRunner::class)
class ProjectRepositoryTest {

    private lateinit var db: DayLogDb
    private lateinit var repo: ProjectRepository

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DayLogDb::class.java)
            .addCallback(DatabaseModule.SeedCallback)
            .allowMainThreadQueries()
            .build()
        repo = ProjectRepository(db.projectDao())
    }

    @After fun teardown() = db.close()

    @Test fun `the three defaults are seeded on a fresh install`() = runTest {
        assertEquals(
            listOf("רובוטיקה", "הנדסת מערכת למחלקה", "AI למחלקה"),
            repo.all().map { it.name },
        )
    }

    @Test fun `a project can be added and appears in the picker`() = runTest {
        repo.add("מערכות בקרה")
        assertTrue(repo.observeActive().first().any { it.name == "מערכות בקרה" })
    }

    @Test fun `blank names and duplicates are not added`() = runTest {
        val before = repo.all().size
        repo.add("   ")
        repo.add("רובוטיקה")
        repo.add("רובוטיקה ")
        assertEquals(before, repo.all().size)
    }

    @Test fun `an unused project is deleted outright`() = runTest {
        val id = repo.add("זמני")
        val project = repo.all().first { it.id == id }
        assertTrue(repo.remove(project))
        assertTrue(repo.all().none { it.name == "זמני" })
    }

    /** Deleting a project that history references would break past days. */
    @Test fun `a project with logged work is archived, not deleted`() = runTest {
        val project = repo.all().first()
        logWorkOn(project.id)

        assertFalse(repo.remove(project))
        val stored = repo.all().first { it.id == project.id }
        assertTrue(stored.isArchived)
        assertTrue(repo.observeActive().first().none { it.id == project.id }) // hidden from the picker
    }

    @Test fun `re-adding an archived name revives it instead of duplicating`() = runTest {
        val project = repo.all().first()
        logWorkOn(project.id)
        repo.remove(project)

        val revivedId = repo.add(project.name)
        assertEquals(project.id, revivedId)
        assertFalse(repo.all().first { it.id == project.id }.isArchived)
        assertEquals(1, repo.all().count { it.name == project.name })
    }

    /** An activity always hangs off a session, which hangs off a day. */
    private suspend fun logWorkOn(projectId: Long) {
        db.dayDao().upsertDay(WorkDayEntity(date = "2026-08-04"))
        val sessionId = db.dayDao().insertSession(
            WorkSessionEntity(date = "2026-08-04", mode = "BASE", startMin = 600, endMin = 840),
        )
        db.dayDao().insertActivity(
            ActivityEntity(sessionId = sessionId, categoryId = 1, projectId = projectId),
        )
    }
}
