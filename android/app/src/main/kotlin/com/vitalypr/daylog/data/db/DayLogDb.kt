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
    version = 2,
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

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
    }
}
