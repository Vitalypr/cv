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
    version = 6,
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
         * over a lossy conversion, so the database is rebuilt on upgrade
         * (`fallbackToDestructiveMigration`) and no migration chain is carried.
         */
        val ALL_MIGRATIONS: Array<Migration> = emptyArray()
    }
}
