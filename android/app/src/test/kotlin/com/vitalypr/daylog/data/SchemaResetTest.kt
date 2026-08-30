package com.vitalypr.daylog.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.data.db.DayLogDb
import com.vitalypr.daylog.di.DatabaseModule
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v2.0 replaced arrival/departure and field jobs with work sessions — a
 * different shape, not a widening — so the product owner chose a clean break
 * over a lossy conversion and no migration chain is carried.
 *
 * What must still hold is that an upgrade never bricks the app: an older
 * database is rebuilt and re-seeded rather than throwing on open. This builds a
 * real pre-v2.0 database file and opens it with the production builder.
 */
@RunWith(RobolectricTestRunner::class)
class SchemaResetTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `an older database is rebuilt and re-seeded instead of crashing`() = runTest {
        val file = context.getDatabasePath("daylog.db")
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
            legacy.execSQL(
                "CREATE TABLE work_day (`date` TEXT NOT NULL, `arrivalMin` INTEGER, " +
                    "`departureMin` INTEGER, PRIMARY KEY(`date`))",
            )
            legacy.execSQL("INSERT INTO work_day (`date`, `arrivalMin`) VALUES ('2026-08-04', 492)")
            legacy.version = 5 // the last pre-session schema
        }

        val db = DatabaseModule.database(context)
        try {
            assertTrue(db.dayDao().allDays().isEmpty(), "old rows should be gone, not half-migrated")
            assertEquals(DayLogDb.DEFAULT_CATEGORIES, db.categoryDao().all().map { it.name })
            assertEquals(DayLogDb.DEFAULT_PROJECTS, db.projectDao().all().map { it.name })
        } finally {
            db.close()
        }
    }
}
