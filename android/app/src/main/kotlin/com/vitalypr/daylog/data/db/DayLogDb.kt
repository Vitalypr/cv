package com.vitalypr.daylog.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WorkDayEntity::class, WorkSessionEntity::class, ActivityEntity::class,
        CategoryEntity::class, JobLocationEntity::class, ProjectEntity::class,
    ],
    version = 7,
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

        /**
         * v2.0 replaced arrival/departure and field jobs with work sessions — a
         * different shape, not a widening. The product owner chose a clean break
         * over a lossy conversion, so anything older than v6 is rebuilt on
         * upgrade (`fallbackToDestructiveMigration`).
         *
         * From v6 forward the chain is real again: a bump ships a Migration and a
         * test, because by then the database holds work the user typed.
         */
        val ALL_MIGRATIONS: Array<Migration> get() = arrayOf(MIGRATION_6_7)

        /**
         * v7 drops `activity.result`: the product owner retired the field, an
         * activity now carries only its detail. SQLite cannot drop a column in a
         * way Room will validate, so the table is rebuilt and copied — keys,
         * links and order survive; only the retired column is gone.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `activity_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, " +
                        "`projectId` INTEGER NOT NULL, `durationMin` INTEGER, " +
                        "`note` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `work_session`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`categoryId`) REFERENCES `category`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT )",
                )
                db.execSQL(
                    "INSERT INTO `activity_new` " +
                        "(`id`, `sessionId`, `categoryId`, `projectId`, `durationMin`, `note`, `sortOrder`) " +
                        "SELECT `id`, `sessionId`, `categoryId`, `projectId`, `durationMin`, `note`, `sortOrder` " +
                        "FROM `activity`",
                )
                db.execSQL("DROP TABLE `activity`")
                db.execSQL("ALTER TABLE `activity_new` RENAME TO `activity`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_sessionId` ON `activity` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_categoryId` ON `activity` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_projectId` ON `activity` (`projectId`)")
            }
        }
    }
}
