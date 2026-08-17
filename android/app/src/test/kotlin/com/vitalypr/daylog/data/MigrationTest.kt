package com.vitalypr.daylog.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.data.db.DayLogDb
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Migrations 1→2 (spec §6.6b) and 2→3 (activity durations). MigrationTestHelper cannot see app assets under
 * Robolectric (instrumentation.context serves only framework assets), so this
 * builds a REAL v1 database from the exported v1 schema SQL and lets Room run
 * the migration and validate the resulting schema on open — a failure in the
 * migration or a schema mismatch makes Room throw.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    // Verbatim createSql from app/schemas/.../1.json.
    private val v1Schema = listOf(
        "CREATE TABLE IF NOT EXISTS `work_day` (`date` TEXT NOT NULL, `arrivalMin` INTEGER, `departureMin` INTEGER, `arrivalSource` TEXT NOT NULL, `departureSource` TEXT NOT NULL, `notes` TEXT NOT NULL, `dayType` TEXT NOT NULL, `reportedAt` INTEGER, `editedAfterReport` INTEGER NOT NULL, PRIMARY KEY(`date`))",
        "CREATE TABLE IF NOT EXISTS `field_job` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `title` TEXT NOT NULL, `locationText` TEXT, `startMin` INTEGER, `endMin` INTEGER, FOREIGN KEY(`date`) REFERENCES `work_day`(`date`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_field_job_date` ON `field_job` (`date`)",
        "CREATE TABLE IF NOT EXISTS `activity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `categoryId` INTEGER NOT NULL, `startMin` INTEGER, `endMin` INTEGER, `note` TEXT NOT NULL, `result` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, FOREIGN KEY(`date`) REFERENCES `work_day`(`date`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`categoryId`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        "CREATE INDEX IF NOT EXISTS `index_activity_date` ON `activity` (`date`)",
        "CREATE INDEX IF NOT EXISTS `index_activity_categoryId` ON `activity` (`categoryId`)",
        "CREATE TABLE IF NOT EXISTS `category` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `emoji` TEXT, `isHidden` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '74322a6c968ae10fb99eb74e99456f8b')",
    )

    @Test
    fun `migrate 1 to 3 preserves data and yields a valid schema`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("migration-test.db")
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { raw ->
            v1Schema.forEach(raw::execSQL)
            raw.execSQL("INSERT INTO work_day (date, notes, dayType, editedAfterReport, arrivalSource, departureSource, arrivalMin, departureMin) VALUES ('2026-08-04', '', 'WORK', 0, 'MANUAL', 'MANUAL', 492, 1055)")
            raw.execSQL("INSERT INTO field_job (date, title, startMin, endMin) VALUES ('2026-08-04', 'אתר', 600, 810)")
            raw.execSQL("INSERT INTO category (name, isHidden, sortOrder) VALUES ('פיתוח', 0, 0)")
            // 09:00–10:35 = 95 minutes, and one entry that never carried times.
            raw.execSQL("INSERT INTO activity (date, categoryId, startMin, endMin, note, result, sortOrder) VALUES ('2026-08-04', 1, 540, 635, 'קוד', '', 0)")
            raw.execSQL("INSERT INTO activity (date, categoryId, startMin, endMin, note, result, sortOrder) VALUES ('2026-08-04', 1, NULL, NULL, 'דיון', '', 1)")
            raw.version = 1
        }

        // Room migrates on open and VALIDATES the resulting schema against current entities.
        val db = Room.databaseBuilder(context, DayLogDb::class.java, "migration-test.db")
            .addMigrations(*DayLogDb.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val day = db.dayDao().getDay("2026-08-04")!!
            assertEquals(492, day.day.arrivalMin)
            val job = day.fieldJobs.single()
            assertEquals("אתר", job.title)
            assertEquals(600, job.startMin)
            assertNull(job.suggestedStartMin) // new columns default null
            assertNull(job.jobLocationId)

            // v3: a start+end pair became a duration on the half-hour grid; an
            // entry that never had times stays unstated. No logged work is lost.
            val acts = day.activities.sortedBy { it.activity.sortOrder }
            assertEquals(2, acts.size)
            assertEquals(90, acts[0].activity.durationMin) // 95 min → 1:30
            assertEquals("קוד", acts[0].activity.note)
            assertNull(acts[1].activity.durationMin)

            // New table is usable post-migration.
            db.jobLocationDao().insert(
                com.vitalypr.daylog.data.db.JobLocationEntity(name = "אתר צפון", lat = 32.7, lon = 35.0),
            )
            assertTrue(db.jobLocationDao().activeLocations().single().radiusM == 2000)
        } finally {
            db.close()
            dbFile.delete()
        }
    }
}
