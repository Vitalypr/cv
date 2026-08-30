package com.vitalypr.daylog.ui.stats

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.vitalypr.daylog.data.db.DayLogDb
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.di.DatabaseModule
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.WorkMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StatsViewModelTest {

    private lateinit var db: DayLogDb
    private lateinit var repo: DayRepository
    private val dispatcher = StandardTestDispatcher()
    // Fixed: Tuesday 2026-08-04. Week = Sun 02.08 – Sat 08.08.
    private val fixedNow = LocalDateTime.of(2026, 8, 4, 12, 0)

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DayLogDb::class.java)
            .addCallback(DatabaseModule.SeedCallback)
            .allowMainThreadQueries()
            .build()
        repo = DayRepository(db.dayDao(), db.categoryDao()) { Instant.parse("2026-08-04T12:00:00Z") }
    }

    @After fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    private val fakePdf = com.vitalypr.daylog.reporting.PeriodPdfRenderer { s ->
        java.io.File.createTempFile("daylog-summary", ".pdf").apply { writeText("%PDF-fake ${'$'}{s.label}") }
    }

    private fun vm() = StatsViewModel(repo, fakePdf) { fixedNow }

    private suspend fun seedWeek() {
        // Sunday: 9 h at the base. Monday: 8 h at the base + 2 h on a site + 1 h from home.
        repo.addSession(LocalDate.of(2026, 8, 2), WorkMode.BASE, 8 * 60, 17 * 60)
        repo.addSession(LocalDate.of(2026, 8, 3), WorkMode.BASE, 8 * 60, 16 * 60)
        repo.addSession(LocalDate.of(2026, 8, 3), WorkMode.FIELD, 17 * 60, 19 * 60, title = "אתר")
        repo.addSession(LocalDate.of(2026, 8, 3), WorkMode.HOME, 20 * 60, 21 * 60)
        repo.setDayType(LocalDate.of(2026, 8, 4), DayType.OFF)
    }

    @Test fun `week view - 7 bars RTL data with correct totals and off marker`() = runTest(dispatcher) {
        seedWeek()
        vm().uiState.test {
            val state = awaitUntil { it.summary != null && it.bars.size == 7 }
            // Sunday first (rendered rightmost), 9 h.
            assertEquals(9 * 60, state.bars[0].baseMin)
            assertEquals(8 * 60, state.bars[1].baseMin)
            assertEquals(2 * 60, state.bars[1].fieldMin)
            assertEquals(60, state.bars[1].homeMin)
            assertEquals(11 * 60, state.bars[1].totalMin)
            assertTrue(state.bars[2].isOff)
            val s = state.summary!!
            assertEquals(2, s.workDays)
            assertEquals(9 * 60 + 8 * 60 + 2 * 60 + 60, s.totalMinutes)
            assertEquals(17 * 60, s.baseMinutes)
            assertEquals(60, s.homeMinutes)
            assertEquals(2 * 60, s.fieldMinutes)
            assertEquals(1, s.fieldDays)
            assertEquals(1, s.offDays)
        }
    }

    @Test fun `year view - 12 bars aggregated by month`() = runTest(dispatcher) {
        seedWeek()
        val vm = vm()
        vm.setPeriod(StatsPeriod.YEAR)
        vm.uiState.test {
            val state = awaitUntil { it.period == StatsPeriod.YEAR && it.bars.size == 12 }
            assertEquals(9 * 60 + 8 * 60, state.bars[7].baseMin) // August is index 7
            assertEquals(2 * 60, state.bars[7].fieldMin)
            assertEquals(60, state.bars[7].homeMin)
            assertEquals(0, state.bars[0].totalMin)
        }
    }

    @Test fun `share emits period text with label and totals`() = runTest(dispatcher) {
        seedWeek()
        val vm = vm()
        vm.uiState.test { awaitUntil { it.summary != null } }
        vm.effect.test {
            vm.share()
            val effect = awaitItem() as StatsEffect.LaunchShare
            assertTrue(effect.caption.contains("סיכום שבועי"))
            assertTrue(effect.caption.contains("ימי עבודה: 2"))
            assertTrue(effect.caption.contains("סה״כ שעות: 20:00"))
            assertTrue(effect.caption.contains("בסיס 17:00"))
            assertTrue(effect.pdf.exists())
        }
    }

    private suspend fun <T> app.cash.turbine.ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}
