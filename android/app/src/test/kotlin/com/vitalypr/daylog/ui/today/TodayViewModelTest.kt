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
import com.vitalypr.daylog.domain.model.WorkMode
import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

    @Test fun `adding a session on today starts it at the current clock time`() = runTest(dispatcher) {
        vm.uiState.test {
            assertEquals(DayStatus.EMPTY, awaitItem().status)
            vm.addSession(WorkMode.BASE)
            val state = expectMostRecentItemAfter { it.sessionRows.isNotEmpty() }
            assertEquals(8 * 60 + 12, state.sessionRows.single().session.startMin)
            assertEquals(DayStatus.LOGGED, state.status)
            assertTrue(state.reportText.contains("בסיס"))
            assertTrue(state.reportText.contains("יום ג׳ 04.08.2026"))
        }
    }

    @Test fun `clearing a session's times returns it to unset`() = runTest(dispatcher) {
        vm.uiState.test {
            awaitItem()
            vm.addSession(WorkMode.BASE)
            val opened = expectMostRecentItemAfter { it.sessionRows.isNotEmpty() }
            val id = opened.sessionRows.single().entity.id

            vm.setSessionEnd(id, 17 * 60 + 35)
            val closed = expectMostRecentItemAfter { it.sessionRows.single().session.endMin != null }
            assertEquals(17 * 60 + 35, closed.sessionRows.single().session.endMin)

            vm.setSessionStart(id, null)
            val cleared = expectMostRecentItemAfter { it.sessionRows.single().session.startMin == null }
            assertNull(cleared.sessionRows.single().session.startMin)
        }
    }

    /** Three stretches of work in one day, each with its own hours (product-owner v2.0). */
    @Test fun `base, home and field sessions coexist and sum into the day total`() = runTest(dispatcher) {
        vm.uiState.test {
            awaitItem()
            vm.addSession(WorkMode.BASE)
            val base = expectMostRecentItemAfter { it.sessionRows.size == 1 }.sessionRows.single().entity.id
            vm.setSessionStart(base, 10 * 60)
            vm.setSessionEnd(base, 14 * 60)

            vm.addSession(WorkMode.HOME)
            val home = expectMostRecentItemAfter { it.sessionRows.size == 2 }.sessionRows.last().entity.id
            vm.setSessionStart(home, 18 * 60)
            vm.setSessionEnd(home, 20 * 60)

            vm.addSession(WorkMode.FIELD)
            val field = expectMostRecentItemAfter { it.sessionRows.size == 3 }.sessionRows.last().entity.id
            vm.setSessionStart(field, 6 * 60)
            vm.setSessionEnd(field, 9 * 60)

            val state = expectMostRecentItemAfter { it.modeTotals.size == 3 }
            assertEquals(4 * 60, state.modeTotals[WorkMode.BASE])
            assertEquals(2 * 60, state.modeTotals[WorkMode.HOME])
            assertEquals(3 * 60, state.modeTotals[WorkMode.FIELD])
            assertEquals(9 * 60, state.budget.spanMin) // 4 + 2 + 3 = 9
            assertTrue(state.reportText.contains("סה״כ 9:00"))
        }
    }

    /**
     * The screen's job: show how much of the worked time is still undescribed,
     * and say so plainly when the activities claim more than was worked.
     */
    @Test fun `the time budget reports what is left to fill and flags an overflow`() = runTest(dispatcher) {
        vm.uiState.test {
            awaitItem()
            vm.addSession(WorkMode.BASE)
            val loaded = expectMostRecentItemAfter {
                it.sessionRows.isNotEmpty() && it.categories.isNotEmpty() && it.projects.isNotEmpty()
            }
            val session = loaded.sessionRows.single()
            vm.setSessionStart(session.entity.id, 10 * 60)
            vm.setSessionEnd(session.entity.id, 14 * 60) // four hours worked
            expectMostRecentItemAfter { it.budget.spanMin == 4 * 60 }

            val category = loaded.categories.first { it.name == "פיתוח" }
            val project = loaded.projects.first { it.name == "רובוטיקה" }
            vm.addActivity(session.entity.id, category.id, project.id)
            val added = expectMostRecentItemAfter { it.sessionRows.single().activityRows.isNotEmpty() }

            // Four taps of +½ = two hours; the stepper reads the stored value each
            // time, so rapid taps cannot lose one another.
            val activityId = added.sessionRows.single().activityRows.single().id
            repeat(4) { vm.stepActivityDuration(activityId, up = true) }
            val half = expectMostRecentItemAfter { it.budget.allocatedMin == 120 }
            assertEquals(2 * 60, half.budget.remainingMin) // "two hours busy, two hours left"
            assertFalse(half.budget.overAllocated)

            repeat(8) { vm.stepActivityDuration(activityId, up = true) }
            val over = expectMostRecentItemAfter { it.budget.allocatedMin == 360 }
            assertTrue(over.budget.overAllocated, "six hours of work in a four-hour day is a problem")
            assertEquals(-2 * 60, over.budget.remainingMin)
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
            vm.addSession(WorkMode.BASE)
            expectMostRecentItemAfter { it.sessionRows.isNotEmpty() }
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
            vm.addSession(WorkMode.BASE)
            expectMostRecentItemAfter { it.sessionRows.isNotEmpty() }

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

    @Test fun `an activity belongs to its session and names its project`() = runTest(dispatcher) {
        vm.uiState.test {
            awaitItem()
            vm.addSession(WorkMode.HOME)
            // Categories and projects arrive in the same emission.
            val loaded = expectMostRecentItemAfter {
                it.sessionRows.isNotEmpty() && it.categories.isNotEmpty() && it.projects.isNotEmpty()
            }
            val sessionId = loaded.sessionRows.single().entity.id
            val pituach = loaded.categories.first { it.name == "פיתוח" }
            val project = loaded.projects.first { it.name == "רובוטיקה" }
            vm.addActivity(sessionId, pituach.id, project.id)

            val state = expectMostRecentItemAfter { it.day.activities.isNotEmpty() }
            val row = state.sessionRows.single().activityRows.single()
            assertEquals("פיתוח", row.category)
            assertEquals("רובוטיקה", row.project) // an activity always names its project
            assertEquals(sessionId, row.sessionId)
        }
    }

    @Test fun `removing a session takes its activities with it`() = runTest(dispatcher) {
        vm.uiState.test {
            awaitItem()
            vm.addSession(WorkMode.BASE)
            val loaded = expectMostRecentItemAfter {
                it.sessionRows.isNotEmpty() && it.categories.isNotEmpty() && it.projects.isNotEmpty()
            }
            val session = loaded.sessionRows.single()
            vm.addActivity(session.entity.id, loaded.categories.first().id, loaded.projects.first().id)
            expectMostRecentItemAfter { it.day.activities.isNotEmpty() }

            vm.removeSession(session.entity.id)
            val state = expectMostRecentItemAfter { it.sessionRows.isEmpty() }
            assertTrue(state.day.activities.isEmpty())
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
