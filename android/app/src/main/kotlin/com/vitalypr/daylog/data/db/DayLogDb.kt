package com.vitalypr.daylog.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WorkDayEntity::class, FieldJobEntity::class, ActivityEntity::class,
        CategoryEntity::class, JobLocationEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class DayLogDb : RoomDatabase() {
    abstract fun dayDao(): DayDao
    abstract fun categoryDao(): CategoryDao
    abstract fun jobLocationDao(): JobLocationDao

    companion object {
        /** Spec F4 default categories, seeded on first run in this order. */
        val DEFAULT_CATEGORIES = listOf(
            "דיון", "התקנה", "בדיקות", "פיתוח", "תכנון", "תיעוד", "תמיכה", "אחר",
        )

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

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
