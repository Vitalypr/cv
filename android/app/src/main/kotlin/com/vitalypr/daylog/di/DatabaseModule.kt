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
            .addMigrations(*DayLogDb.ALL_MIGRATIONS)
            // v2.0 clean break (product-owner decision — data is re-entered from
            // scratch): an older database is rebuilt instead of migrated, so an
            // upgrade can never leave the app unable to open its own storage.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun dayDao(db: DayLogDb): DayDao = db.dayDao()
    @Provides fun categoryDao(db: DayLogDb): CategoryDao = db.categoryDao()
    @Provides fun projectDao(db: DayLogDb): com.vitalypr.daylog.data.db.ProjectDao = db.projectDao()
    @Provides fun jobLocationDao(db: DayLogDb): com.vitalypr.daylog.data.db.JobLocationDao = db.jobLocationDao()

    @Provides fun clock(): () -> Instant = Instant::now

    /**
     * Seeds the 8 default Hebrew categories (spec F4) and the default projects
     * (v1.2).
     *
     * `onCreate` covers a fresh install, but a destructive rebuild — the v2.0
     * clean break — drops and recreates the tables WITHOUT it, which would leave
     * an upgraded install with no categories and no projects, unable to log
     * anything at all. So the seed is also checked on open and applied only to a
     * table that is actually empty, which is both idempotent and cheap.
     */
    object SeedCallback : RoomDatabase.Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) = seed(db)

        override fun onOpen(db: SupportSQLiteDatabase) = seed(db)

        private fun seed(db: SupportSQLiteDatabase) {
            if (isEmpty(db, "category")) {
                DayLogDb.DEFAULT_CATEGORIES.forEachIndexed { i, name ->
                    db.execSQL(
                        "INSERT INTO category (name, emoji, isHidden, sortOrder) VALUES (?, NULL, 0, ?)",
                        arrayOf(name, i),
                    )
                }
            }
            if (isEmpty(db, "project")) {
                DayLogDb.DEFAULT_PROJECTS.forEachIndexed { i, name ->
                    db.execSQL(
                        "INSERT INTO project (name, isArchived, sortOrder) VALUES (?, 0, ?)",
                        arrayOf(name, i),
                    )
                }
            }
        }

        private fun isEmpty(db: SupportSQLiteDatabase, table: String): Boolean =
            db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst() && it.getInt(0) == 0 }
    }
}
