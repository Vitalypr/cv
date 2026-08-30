package com.vitalypr.daylog.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.data.db.DayLogDb
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migration 6→7: `activity.result` is retired.
 *
 * By v6 the database holds work the user typed, so a schema bump owes them a
 * migration rather than a rebuild. MigrationTestHelper cannot see app assets
 * under Robolectric (instrumentation.context serves only framework assets), so
 * this builds a REAL v6 database from the exported v6 schema, puts a day of work
 * in it, and lets Room migrate and validate on open — a broken migration or a
 * schema mismatch makes Room throw here.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Verbatim createSql from app/schemas/.../6.json.
    private val v6Schema = listOf(
        "CREATE TABLE IF NOT EXISTS `work_day` (`date` TEXT NOT NULL, `notes` TEXT NOT NULL, " +
            "`dayType` TEXT NOT NULL, `reportedAt` INTEGER, `editedAfterReport` INTEGER NOT NULL, " +
            "PRIMARY KEY(`date`))",
        "CREATE TABLE IF NOT EXISTS `work_session` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`date` TEXT NOT NULL, `mode` TEXT NOT NULL, `startMin` INTEGER, `endMin` INTEGER, " +
            "`title` TEXT NOT NULL, `locationText` TEXT, `startSource` TEXT NOT NULL, " +
            "`endSource` TEXT NOT NULL, `startUncertain` INTEGER NOT NULL, `jobLocationId` INTEGER, " +
            "`sortOrder` INTEGER NOT NULL, FOREIGN KEY(`date`) REFERENCES `work_day`(`date`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_work_session_date` ON `work_session` (`date`)",
        "CREATE TABLE IF NOT EXISTS `activity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`sessionId` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, `projectId` INTEGER NOT NULL, " +
            "`durationMin` INTEGER, `note` TEXT NOT NULL, `result` TEXT NOT NULL, " +
            "`sortOrder` INTEGER NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `work_session`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`categoryId`) REFERENCES `category`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE RESTRICT )",
        "CREATE INDEX IF NOT EXISTS `index_activity_sessionId` ON `activity` (`sessionId`)",
        "CREATE INDEX IF NOT EXISTS `index_activity_categoryId` ON `activity` (`categoryId`)",
        "CREATE INDEX IF NOT EXISTS `index_activity_projectId` ON `activity` (`projectId`)",
        "CREATE TABLE IF NOT EXISTS `category` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `emoji` TEXT, `isHidden` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `job_location` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `radiusM` INTEGER NOT NULL, " +
            "`isActive` INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `project` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `isArchived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
            "VALUES(42, '30c1f851ec17b90e9dca204a9629141d')",
    )

    private fun buildV6(name: String) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.delete()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            v6Schema.forEach(db::execSQL)
            db.execSQL("INSERT INTO category (name, emoji, isHidden, sortOrder) VALUES ('פיתוח', NULL, 0, 0)")
            db.execSQL("INSERT INTO project (name, isArchived, sortOrder) VALUES ('רובוטיקה', 0, 0)")
            db.execSQL("INSERT INTO work_day VALUES ('2026-08-04', 'הערה', 'WORK', NULL, 0)")
            db.execSQL(
                "INSERT INTO work_session (id, date, mode, startMin, endMin, title, locationText, " +
                    "startSource, endSource, startUncertain, jobLocationId, sortOrder) " +
                    "VALUES (1, '2026-08-04', 'BASE', 480, 1065, '', NULL, 'GEOFENCE', 'GEOFENCE', 0, NULL, 0)",
            )
            db.execSQL(
                "INSERT INTO activity (id, sessionId, categoryId, projectId, durationMin, note, result, sortOrder) " +
                    "VALUES (7, 1, 1, 1, 120, 'חיווט לוח', 'הושלם', 0)",
            )
            db.version = 6
        }
    }

    private fun open(name: String): DayLogDb =
        Room.databaseBuilder(context, DayLogDb::class.java, name)
            .addMigrations(*DayLogDb.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

    @Test fun `6 to 7 drops the result column and keeps the work`() = runTest {
        buildV6("migration-6-7.db")
        val db = open("migration-6-7.db")
        try {
            val activity = db.dayDao().allActivities().single()
            assertEquals(7L, activity.id) // ids are preserved, so links survive
            assertEquals(1L, activity.sessionId)
            assertEquals(120, activity.durationMin)
            assertEquals("חיווט לוח", activity.note)

            val session = db.dayDao().allSessions().single()
            assertEquals(480 to 1065, session.startMin to session.endMin)
            assertEquals("2026-08-04", db.dayDao().allDays().single().date)
        } finally {
            db.close()
        }
    }

    @Test fun `the migrated activity table no longer has a result column`() = runTest {
        buildV6("migration-columns.db")
        val db = open("migration-columns.db")
        db.dayDao().allActivities() // opening runs the migration
        db.close()

        SQLiteDatabase.openDatabase(
            context.getDatabasePath("migration-columns.db").path, null, SQLiteDatabase.OPEN_READONLY,
        ).use { raw ->
            val columns = raw.rawQuery("PRAGMA table_info(activity)", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(1)) }
            }
            assertFalse("result" in columns, "the retired column is still there: $columns")
            assertTrue("note" in columns)
            assertTrue("sortOrder" in columns)
        }
    }
}
