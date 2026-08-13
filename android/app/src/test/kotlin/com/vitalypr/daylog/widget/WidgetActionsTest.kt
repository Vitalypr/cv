package com.vitalypr.daylog.widget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vitalypr.daylog.data.db.DayLogDb
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.di.DatabaseModule
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.TimeSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The two widget buttons: they log the real clock time and override what is stored. */
@RunWith(RobolectricTestRunner::class)
class WidgetActionsTest {

    private lateinit var db: DayLogDb
    private lateinit var repo: DayRepository
    private lateinit var actions: WidgetActions

    private var nowDt = LocalDateTime.of(2026, 8, 4, 8, 12)
    private val today = LocalDate.of(2026, 8, 4)

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DayLogDb::class.java)
            .addCallback(DatabaseModule.SeedCallback)
            .allowMainThreadQueries()
            .build()
        repo = DayRepository(db.dayDao(), db.categoryDao()) { Instant.now() }
        actions = WidgetActions(repo) { nowDt }
    }

    @After fun teardown() = db.close()

    @Test fun `arrival tap records the real current time`() = runTest {
        assertTrue(actions.record(arrival = true))
        assertEquals(8 * 60 + 12, repo.getDay(today)!!.arrivalMin)
    }

    @Test fun `departure tap records the real current time`() = runTest {
        nowDt = LocalDateTime.of(2026, 8, 4, 17, 35)
        assertTrue(actions.record(arrival = false))
        assertEquals(17 * 60 + 35, repo.getDay(today)!!.departureMin)
    }

    @Test fun `a second tap overrides the earlier value`() = runTest {
        actions.record(arrival = true)
        nowDt = LocalDateTime.of(2026, 8, 4, 9, 5)
        actions.record(arrival = true)
        assertEquals(9 * 60 + 5, repo.getDay(today)!!.arrivalMin)
    }

    @Test fun `widget overrides a geofence-written value`() = runTest {
        repo.setArrival(today, 500, TimeSource.GEOFENCE)
        actions.record(arrival = true)
        val day = repo.getDay(today)!!
        assertEquals(8 * 60 + 12, day.arrivalMin)
        assertEquals(TimeSource.MANUAL, day.arrivalSource) // and locks out later geofence writes
    }

    @Test fun `widget overrides a value typed in the app`() = runTest {
        repo.setArrival(today, 480, TimeSource.MANUAL)
        actions.record(arrival = true)
        assertEquals(8 * 60 + 12, repo.getDay(today)!!.arrivalMin)
    }

    @Test fun `special day refuses the tap and writes nothing`() = runTest {
        repo.setDayType(today, DayType.OFF)
        assertFalse(actions.record(arrival = true))
        assertNull(repo.getDay(today)!!.arrivalMin)
    }

    @Test fun `past-midnight tap lands on the current logical day`() = runTest {
        nowDt = LocalDateTime.of(2026, 8, 5, 1, 30)
        actions.record(arrival = false)
        assertEquals(90, repo.getDay(LocalDate.of(2026, 8, 5))!!.departureMin)
    }
}
