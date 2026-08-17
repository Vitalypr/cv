package com.vitalypr.daylog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.data.db.DayLogDb
import com.vitalypr.daylog.data.export.Exporter
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.di.DatabaseModule
import com.vitalypr.daylog.domain.model.FieldJob
import com.vitalypr.daylog.domain.model.TimeSource
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

    @Test fun `json export is versioned and round-trippable`() = runTest {
        repo.setArrival(date, 492, TimeSource.MANUAL)
        repo.setDeparture(date, 1055, TimeSource.MANUAL)
        repo.addFieldJob(date, FieldJob("אתר, עם פסיק", null, 600, 810))
        repo.setNotes(date, "הערה")

        val json = JSONObject(exporter.exportJson(date.minusDays(7), date))
        assertEquals(2, json.getInt("schemaVersion")) // v2: activity durationMin
        val day = json.getJSONArray("days").getJSONObject(0)
        assertEquals("2026-08-04", day.getString("date"))
        assertEquals(492, day.getInt("arrivalMin"))
        assertEquals("אתר, עם פסיק", day.getJSONArray("fieldJobs").getJSONObject(0).getString("title"))
    }

    @Test fun `csv export escapes commas and includes totals`() = runTest {
        repo.setArrival(date, 480, TimeSource.MANUAL)
        repo.setDeparture(date, 1020, TimeSource.MANUAL)
        repo.addFieldJob(date, FieldJob("אתר, עם פסיק", null, null, null))

        val csv = exporter.exportCsv(date.minusDays(7), date)
        val lines = csv.lines()
        assertEquals("date,day_type,arrival,departure,total_hours,field_jobs,activities,notes", lines[0])
        assertTrue(lines[1].startsWith("2026-08-04,WORK,480,1020,9:00,"))
        assertTrue(lines[1].contains("\"אתר, עם פסיק\""))
    }
}
