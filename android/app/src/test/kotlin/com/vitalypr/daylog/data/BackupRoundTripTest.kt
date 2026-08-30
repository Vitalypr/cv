package com.vitalypr.daylog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.data.backup.BackupCodec
import com.vitalypr.daylog.data.backup.BackupDocument
import com.vitalypr.daylog.data.backup.BackupRepository
import com.vitalypr.daylog.data.db.ActivityEntity
import com.vitalypr.daylog.data.db.DayLogDb
import com.vitalypr.daylog.data.db.JobLocationEntity
import com.vitalypr.daylog.data.db.ProjectEntity
import com.vitalypr.daylog.data.db.WorkDayEntity
import com.vitalypr.daylog.data.db.WorkSessionEntity
import com.vitalypr.daylog.data.settings.Settings
import com.vitalypr.daylog.data.settings.SettingsRepository
import com.vitalypr.daylog.di.DatabaseModule
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The promise of the backup: what comes back is exactly what went in — every
 * table and every setting, with the links between them intact.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {

    private lateinit var db: DayLogDb
    private lateinit var backup: BackupRepository
    private lateinit var settings: SettingsRepository

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DayLogDb::class.java)
            .addCallback(DatabaseModule.SeedCallback)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        backup = BackupRepository(db, db.dayDao(), db.categoryDao(), db.projectDao(), db.jobLocationDao(), settings)
    }

    @After fun teardown() = db.close()

    private suspend fun seedRealisticData() {
        db.dayDao().upsertDay(
            WorkDayEntity(
                date = "2026-08-04", notes = "הוזמן CT",
                reportedAt = 1_700_000_000_000, editedAfterReport = true,
            ),
        )
        db.dayDao().upsertDay(WorkDayEntity(date = "2026-08-05", dayType = "HOLIDAY"))
        val baseId = db.dayDao().insertSession(
            WorkSessionEntity(
                date = "2026-08-04", mode = "BASE", startMin = 492, endMin = 1055,
                startSource = "GEOFENCE", startUncertain = true, sortOrder = 0,
            ),
        )
        db.dayDao().insertSession(
            WorkSessionEntity(
                date = "2026-08-04", mode = "FIELD", startMin = 600, endMin = 1020,
                title = "אתר צפון", locationText = "חיפה", jobLocationId = 3, sortOrder = 1,
            ),
        )
        val projectId = db.projectDao().all().first { it.name == "רובוטיקה" }.id
        db.dayDao().insertActivity(
            ActivityEntity(
                sessionId = baseId, categoryId = 4, projectId = projectId,
                durationMin = 90, note = "חיווט", sortOrder = 0,
            ),
        )
        db.jobLocationDao().insert(JobLocationEntity(name = "אתר דרום", lat = 31.2, lon = 34.8, radiusM = 2000))
        settings.setWorkDays(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY))
        settings.setReportTime(18 * 60)
        settings.setOffice(32.0853, 34.7818, 200)
        settings.setGeofenceEnabled(true)
        settings.setSilentGeofence(false)
    }

    @Test fun `every table and every setting survives a full round trip`() = runTest {
        seedRealisticData()
        val before = backup.export()
        val json = BackupCodec.encode(before)

        // Wipe everything, as a fresh install would look.
        db.dayDao().clearActivities()
        db.dayDao().clearSessions()
        db.dayDao().clearDays()
        db.projectDao().clear()
        db.categoryDao().clear()
        db.jobLocationDao().clear()
        settings.replaceAll(Settings())

        backup.restoreJson(json)
        val after = backup.export()

        assertEquals(before.days, after.days)
        assertEquals(before.sessions, after.sessions)
        assertEquals(before.activities, after.activities)
        assertEquals(before.categories, after.categories)
        assertEquals(before.projects, after.projects)
        assertEquals(before.jobLocations, after.jobLocations)
        assertEquals(before.settings, after.settings)
    }

    @Test fun `an activity still points at the same project and session after restore`() = runTest {
        seedRealisticData()
        val json = backup.exportJson()
        db.dayDao().clearActivities()
        db.dayDao().clearSessions()
        db.projectDao().clear()
        backup.restoreJson(json)

        val activity = db.dayDao().allActivities().single()
        val project = db.projectDao().all().first { it.id == activity.projectId }
        assertEquals("רובוטיקה", project.name)
        val session = db.dayDao().allSessions().first { it.id == activity.sessionId }
        assertEquals("BASE", session.mode)
        assertEquals(492, session.startMin)
    }

    @Test fun `restore replaces rather than merges`() = runTest {
        seedRealisticData()
        val json = backup.exportJson()
        // A day that is not in the backup must be gone afterwards.
        db.dayDao().upsertDay(WorkDayEntity(date = "2026-09-01", notes = "יום נוסף"))
        backup.restoreJson(json)
        assertTrue(db.dayDao().allDays().none { it.date == "2026-09-01" })
    }

    @Test fun `the document is self-describing and versioned`() = runTest {
        val json = backup.exportJson()
        val root = org.json.JSONObject(json)
        assertEquals("DayLog", root.getString("app"))
        assertEquals(BackupCodec.VERSION, root.getInt("backupVersion"))
        assertTrue(root.has("exportedAt"))
    }

    @Test fun `a file that is not a backup is refused, not half-applied`() = runTest {
        seedRealisticData()
        val before = db.dayDao().allDays()
        assertFailsWith<BackupCodec.IncompatibleBackup> { backup.restoreJson("""{"hello":"world"}""") }
        assertFailsWith<BackupCodec.IncompatibleBackup> { backup.restoreJson("not json at all") }
        assertEquals(before, db.dayDao().allDays())
    }

    @Test fun `a backup from a newer app version is refused`() = runTest {
        val json = org.json.JSONObject(backup.exportJson())
            .put("backupVersion", BackupCodec.VERSION + 1).toString()
        assertFailsWith<BackupCodec.IncompatibleBackup> { backup.restoreJson(json) }
    }

    @Test fun `an empty backup restores an empty app without crashing`() = runTest {
        seedRealisticData()
        backup.restore(BackupDocument())
        assertTrue(db.dayDao().allDays().isEmpty())
        assertTrue(db.projectDao().all().isEmpty())
        assertEquals(Settings(), settings.settings.first())
    }
}
