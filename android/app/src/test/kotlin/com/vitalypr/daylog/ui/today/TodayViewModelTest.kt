package com.vitalypr.daylog.ui.today

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.vitalypr.daylog.data.db.DayLogDb
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.di.DatabaseModule
import com.vitalypr.daylog.domain.model.DayStatus
import com.vitalypr.daylog.domain.model.DayType
import java.time.Instant
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
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class TodayViewModelTest {

    private lateinit var db: DayLogDb
    private lateinit var vm: TodayViewModel
    private val dispatcher = StandardTestDispatcher()
    // Fixed clock: Tuesday 2026-08-04, 08:12.
    private val fixedNow = LocalDateTime.of(2026, 8, 4, 8, 12)

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DayLogDb::class.java)
            .addCallback(DatabaseModule.SeedCallback)
            .allowMainThreadQueries()
            .build()
        val repo = DayRepository(db.dayDao(), db.categoryDao()) { Instant.parse("2026-08-04T12:00:00Z") }
        // PdfDocument can't run on Robolectric — fake the renderer (drawing is covered by ReportPdfTest).
        val fakePdf = com.vitalypr.daylog.reporting.DailyPdfRenderer { day ->
            java.io.File.createTempFile("daylog-${day.date}", ".pdf").apply { writeText("%PDF-fake") }
        }
        val projects = com.vitalypr.daylog.data.repo.ProjectRepository(db.projectDao())
        vm = TodayViewModel(repo, projects, fakePdf, { fixedNow }, androidx.lifecycle.SavedStateHandle())
    }

    @After fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test fun `arrive now logs the fixed clock time and report updates`() = runTest(dispatcher) {
        vm.uiState.test {
            assertEquals(DayStatus.EMPTY, awaitItem().status)
            vm.arriveNow()
            val state = expectMostRecentItemAfter { it.day.arrivalMin != null }
            assertEquals(8 * 60 + 12, state.day.arrivalMin)
            assertEquals(DayStatus.LOGGED, state.status)
            assertTrue(state.reportText.contains("כניסה: 08:12"))
            assertTrue(state.reportText.contains("יום ג׳ 04.08.2026"))
        }
    }

    @Test fun `clearing arrival and departure returns the day to unset`() = runTest(dispatcher) {
        vm.uiState.test {
            awaitItem()
            vm.arriveNow()
            vm.setDeparture(17 * 60 + 35)
            expectMostRecentItemAfter { it.day.arrivalMin != null && it.day.departureMin != null }

            vm.clearArrival()
            assertEquals(null, expectMostRecentItemAfter { it.day.arrivalMin == null }.day.arrivalMin)
            vm.clearDeparture()
            val cleared = expectMostRecentItemAfter { it.day.departureMin == null }
            assertEquals(null, cleared.day.departureMin)
            // Report drops the emptied segments rather than rendering a half line.
            assertTrue(!cleared.reportText.contains("כניסה") && !cleared.reportText.contains("יציאה"))
        }
    }

    @Test fun `toggling day type twice returns to work day`() = runTest(dispatcher) {
        vm.uiState.test {
            awaitItem()
            vm.toggleDayType(DayType.OFF)
            assertEquals(DayType.OFF, expectMostRecentItemAfter { it.day.dayType == DayType.OFF }.day.dayType)
            vm.toggleDayType(DayType.OFF)
            assertEquals(DayType.WORK, expectMostRecentItemAfter { it.day.dayType == DayType.WORK }.day.dayType)
        }
    }

    @Test fun `special day clears report text and blocks share`() = runTest(dispatcher) {
        vm.uiState.test {
            awaitItem()
            vm.arriveNow()
            expectMostRecentItemAfter { it.day.arrivalMin != null }
            vm.toggleDayType(DayType.HOLIDAY)
            val state = expectMostRecentItemAfter { it.day.dayType == DayType.HOLIDAY }
            assertEquals("", state.reportText)
        }
        vm.share() // must not emit an effect
        vm.effect.test { expectNoEvents() }
    }

    @Test fun `share marks day reported and emits launch effect`() = runTest(dispatcher) {
        vm.uiState.test {
            awaitItem()
            vm.arriveNow()
            expectMostRecentItemAfter { it.day.arrivalMin != null }

            vm.effect.test {
                vm.share()
                val effect = awaitItem()
                assertTrue(effect is TodayEffect.LaunchShare)
                val share = effect as TodayEffect.LaunchShare
                assertTrue(share.caption.contains("דוח יומי"))
                assertTrue(share.pdf.exists() && share.pdf.name.endsWith(".pdf"))
            }
            val state = expectMostRecentItemAfter { it.day.reported }
            assertEquals(DayStatus.REPORTED, state.status)
        }
    }

    @Test fun `adding activity from category chip appears with category name`() = runTest(dispatcher) {
        vm.uiState.test {
            awaitItem()
            // Categories and projects arrive in the same emission.
            val loaded = expectMostRecentItemAfter { it.categories.isNotEmpty() && it.projects.isNotEmpty() }
            val pituach = loaded.categories.first { it.name == "פיתוח" }
            val project = loaded.projects.first { it.name == "רובוטיקה" }
            vm.addActivity(pituach.id, project.id)
            val state = expectMostRecentItemAfter { it.activityRows.isNotEmpty() }
            val row = state.activityRows.single()
            assertEquals("פיתוח", row.category)
            assertEquals("רובוטיקה", row.project) // an activity always names its project
        }
    }

    /** Awaits emissions until the predicate holds; fails on timeout via Turbine. */
    private suspend fun app.cash.turbine.ReceiveTurbine<TodayUiState>.expectMostRecentItemAfter(
        predicate: (TodayUiState) -> Boolean,
    ): TodayUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}
