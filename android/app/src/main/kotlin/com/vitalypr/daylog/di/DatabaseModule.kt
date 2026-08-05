package com.vitalypr.daylog.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vitalypr.daylog.data.db.CategoryDao
import com.vitalypr.daylog.data.db.DayDao
import com.vitalypr.daylog.data.db.DayLogDb
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): DayLogDb =
        Room.databaseBuilder(context, DayLogDb::class.java, "daylog.db")
            .addCallback(SeedCallback)
            .build()

    @Provides fun dayDao(db: DayLogDb): DayDao = db.dayDao()
    @Provides fun categoryDao(db: DayLogDb): CategoryDao = db.categoryDao()

    @Provides fun clock(): () -> Instant = Instant::now

    /** Seeds the 8 default Hebrew categories on first database creation (spec F4). */
    object SeedCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            DayLogDb.DEFAULT_CATEGORIES.forEachIndexed { i, name ->
                db.execSQL(
                    "INSERT INTO category (name, emoji, isHidden, sortOrder) VALUES (?, NULL, 0, ?)",
                    arrayOf(name, i),
                )
            }
        }
    }
}
