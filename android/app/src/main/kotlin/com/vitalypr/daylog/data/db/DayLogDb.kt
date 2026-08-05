package com.vitalypr.daylog.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [WorkDayEntity::class, FieldJobEntity::class, ActivityEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class DayLogDb : RoomDatabase() {
    abstract fun dayDao(): DayDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        /** Spec F4 default categories, seeded on first run in this order. */
        val DEFAULT_CATEGORIES = listOf(
            "דיון", "התקנה", "בדיקות", "פיתוח", "תכנון", "תיעוד", "תמיכה", "אחר",
        )
    }
}
