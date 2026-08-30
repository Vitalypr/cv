package com.vitalypr.daylog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.data.db.DayLogDb
import com.vitalypr.daylog.data.export.Exporter
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.di.DatabaseModule
import com.vitalypr.daylog.domain.model.WorkMode
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ExporterTest {

    private lateinit var db: DayLogDb
    private lateinit var repo: DayRepository
    private lateinit var exporter: Exporter
    private val date = LocalDate.of(2026, 8, 4)

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DayLogDb::class.java)
            .addCallback(DatabaseModule.SeedCallback)
            .allowMainThreadQueries()
            .build()
        repo = DayRepository(db.dayDao(), db.categoryDao()) { Instant.now() }
        exporter = Exporter(repo)
    }

    @After fun teardown() = db.close()

    @Test fun `json export is versioned and carries every session`() = runTest {
        repo.addSession(date, WorkMode.BASE, 492, 1055)
        repo.addSession(date, WorkMode.FIELD, 600, 810, title = "אתר, עם פסיק")
        repo.setNotes(date, "הערה")

        val json = JSONObject(exporter.exportJson(date.minusDays(7), date))
        assertEquals(3, json.getInt("schemaVersion")) // v3: worked time is a list of sessions
        val day = json.getJSONArray("days").getJSONObject(0)
        assertEquals("2026-08-04", day.getString("date"))
        assertEquals((1055 - 492) + (810 - 600), day.getInt("totalMinutes"))
        val sessions = day.getJSONArray("sessions")
        assertEquals(2, sessions.length())
        assertEquals("BASE", sessions.getJSONObject(0).getString("mode"))
        assertEquals("אתר, עם פסיק", sessions.getJSONObject(1).getString("title"))
    }

    @Test fun `csv writes one row per session and escapes commas`() = runTest {
        val sessionId = repo.addSession(date, WorkMode.BASE, 480, 1020)
        repo.addActivity(sessionId, categoryId = 4, projectId = db.projectDao().all().first().id)
        repo.addSession(date, WorkMode.HOME, 1080, 1260, title = "אתר, עם פסיק")

        val csv = exporter.exportCsv(date.minusDays(7), date)
        val lines = csv.lines()
        assertEquals("date,mode,title,start,end,minutes,projects,categories,dayType,notes", lines[0])
        assertTrue(lines[1].startsWith("2026-08-04,בסיס,,08:00,17:00,9:00,"))
        assertTrue(lines[1].contains("פיתוח"))
        assertTrue(lines[2].contains("\"אתר, עם פסיק\""))
    }
}
