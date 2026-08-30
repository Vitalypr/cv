package com.vitalypr.daylog.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WorkDayEntity::class, FieldJobEntity::class, ActivityEntity::class,
        CategoryEntity::class, JobLocationEntity::class, ProjectEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class DayLogDb : RoomDatabase() {
    abstract fun dayDao(): DayDao
    abstract fun categoryDao(): CategoryDao
    abstract fun jobLocationDao(): JobLocationDao
    abstract fun projectDao(): ProjectDao

    companion object {
        /** Spec F4 default categories, seeded on first run in this order. */
        val DEFAULT_CATEGORIES = listOf(
            "דיון", "התקנה", "בדיקות", "פיתוח", "תכנון", "תיעוד", "תמיכה", "אחר",
        )

        /** Seeded projects (v1.2); the user adds their own alongside these. */
        val DEFAULT_PROJECTS = listOf("רובוטיקה", "הנדסת מערכת למחלקה", "AI למחלקה")

        /** Holds activities that predate projects, so the mandatory link never lies. */
        const val LEGACY_PROJECT = "ללא שיוך"

        /** v0.6: job locations + suggested field-job times (spec §6.6b). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `job_location` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, " +
                        "`radiusM` INTEGER NOT NULL, `isActive` INTEGER NOT NULL)",
                )
                db.execSQL("ALTER TABLE `field_job` ADD COLUMN `jobLocationId` INTEGER")
                db.execSQL("ALTER TABLE `field_job` ADD COLUMN `suggestedStartMin` INTEGER")
                db.execSQL("ALTER TABLE `field_job` ADD COLUMN `suggestedEndMin` INTEGER")
            }
        }

        /**
         * v0.9: activities carry a half-hour duration instead of a clock range.
         * SQLite cannot drop columns here, so the table is rebuilt; an existing
         * start+end pair is carried over as its span, rounded to the nearest
         * half hour (minimum one step) so no logged work silently disappears.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `activity_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, " +
                        "`categoryId` INTEGER NOT NULL, `durationMin` INTEGER, `note` TEXT NOT NULL, " +
                        "`result` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`date`) REFERENCES `work_day`(`date`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`categoryId`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
                )
                db.execSQL(
                    "INSERT INTO `activity_new` (`id`, `date`, `categoryId`, `durationMin`, `note`, `result`, `sortOrder`) " +
                        "SELECT `id`, `date`, `categoryId`, " +
                        "CASE WHEN `startMin` IS NOT NULL AND `endMin` IS NOT NULL AND `endMin` > `startMin` " +
                        "THEN MAX(30, CAST(ROUND((`endMin` - `startMin`) / 30.0) AS INTEGER) * 30) END, " +
                        "`note`, `result`, `sortOrder` FROM `activity`",
                )
                db.execSQL("DROP TABLE `activity`")
                db.execSQL("ALTER TABLE `activity_new` RENAME TO `activity`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_date` ON `activity` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_categoryId` ON `activity` (`categoryId`)")
            }
        }

        /** v1.0: short-visit flag on a geofence arrival (spec §6.6 short-visit rule). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `work_day` ADD COLUMN `arrivalUncertain` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v1.2: projects, and every activity belongs to one.
         *
         * The column is NOT NULL, so existing activities need a home: rather than
         * misfiling them under a real project they are moved to a clearly-named
         * "[LEGACY_PROJECT]" one, created only if there is anything to put in it.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `project` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`isArchived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)",
                )
                DEFAULT_PROJECTS.forEachIndexed { i, name ->
                    db.execSQL("INSERT INTO `project` (`name`, `isArchived`, `sortOrder`) VALUES (?, 0, ?)", arrayOf(name, i))
                }

                var legacyId = 0L
                db.query("SELECT COUNT(*) FROM `activity`").use { c ->
                    if (c.moveToFirst() && c.getInt(0) > 0) {
                        db.execSQL(
                            "INSERT INTO `project` (`name`, `isArchived`, `sortOrder`) VALUES (?, 0, ?)",
                            arrayOf(LEGACY_PROJECT, DEFAULT_PROJECTS.size),
                        )
                        db.query("SELECT last_insert_rowid()").use { r ->
                            if (r.moveToFirst()) legacyId = r.getLong(0)
                        }
                    }
                }

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `activity_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, " +
                        "`categoryId` INTEGER NOT NULL, `projectId` INTEGER NOT NULL, `durationMin` INTEGER, " +
                        "`note` TEXT NOT NULL, `result` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`date`) REFERENCES `work_day`(`date`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`categoryId`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
                )
                db.execSQL(
                    "INSERT INTO `activity_new` (`id`, `date`, `categoryId`, `projectId`, `durationMin`, `note`, `result`, `sortOrder`) " +
                        "SELECT `id`, `date`, `categoryId`, $legacyId, `durationMin`, `note`, `result`, `sortOrder` FROM `activity`",
                )
                db.execSQL("DROP TABLE `activity`")
                db.execSQL("ALTER TABLE `activity_new` RENAME TO `activity`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_date` ON `activity` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_categoryId` ON `activity` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_projectId` ON `activity` (`projectId`)")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
    }
}
