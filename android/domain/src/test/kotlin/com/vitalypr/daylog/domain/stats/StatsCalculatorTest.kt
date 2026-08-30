package com.vitalypr.daylog.domain.stats

import com.vitalypr.daylog.domain.model.ActivityEntry
import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.WorkMode
import com.vitalypr.daylog.domain.model.WorkSession
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The hours rule (spec §2.5 v2.0): a day is the sum of its sessions. */
class StatsCalculatorTest {

    private val mon: LocalDate = LocalDate.of(2026, 8, 3)

    private fun session(mode: WorkMode, start: Int, end: Int, vararg acts: ActivityEntry) =
        WorkSession(mode = mode, startMin = start, endMin = end, activities = acts.toList())

    @Test fun `a day adds up its sessions, whatever mode they are`() {
        val day = DaySnapshot(
            mon,
            listOf(
                session(WorkMode.BASE, 10 * 60, 14 * 60),
                session(WorkMode.HOME, 18 * 60, 20 * 60),
                session(WorkMode.FIELD, 6 * 60, 9 * 60),
            ),
        )
        val m = StatsCalculator.dayMinutes(day)
        assertEquals(4 * 60, m.base)
        assertEquals(2 * 60, m.home)
        assertEquals(3 * 60, m.field)
        assertEquals(9 * 60, m.total)
    }

    @Test fun `two visits to the base on one day are both counted`() {
        val day = DaySnapshot(
            mon,
            listOf(session(WorkMode.BASE, 8 * 60, 12 * 60), session(WorkMode.BASE, 14 * 60, 18 * 60)),
        )
        assertEquals(8 * 60, StatsCalculator.dayMinutes(day).total)
    }

    @Test fun `an open or malformed session contributes nothing`() {
        val day = DaySnapshot(
            mon,
            listOf(
                WorkSession(startMin = 10 * 60), // still open
                WorkSession(startMin = 14 * 60, endMin = 12 * 60), // end before start
            ),
        )
        assertEquals(0, StatsCalculator.dayMinutes(day).total)
    }

    @Test fun `working past midnight counts the whole stretch`() {
        val day = DaySnapshot(mon, listOf(session(WorkMode.BASE, 22 * 60, 25 * 60 + 30)))
        assertEquals(210, StatsCalculator.dayMinutes(day).total)
    }

    @Test fun `a period sums days and breaks the hours down by mode`() {
        val days = listOf(
            DaySnapshot(mon, listOf(session(WorkMode.BASE, 8 * 60, 16 * 60))),
            DaySnapshot(
                mon.plusDays(1),
                listOf(session(WorkMode.HOME, 9 * 60, 12 * 60), session(WorkMode.FIELD, 13 * 60, 17 * 60)),
            ),
            DaySnapshot(mon.plusDays(2), dayType = DayType.OFF),
            DaySnapshot(mon.plusDays(3), dayType = DayType.HOLIDAY),
        )
        val s = StatsCalculator.summarize("שבוע", days)
        assertEquals(2, s.workDays)
        assertEquals(15 * 60, s.totalMinutes)
        assertEquals(8 * 60, s.baseMinutes)
        assertEquals(3 * 60, s.homeMinutes)
        assertEquals(4 * 60, s.fieldMinutes)
        assertEquals(1, s.fieldDays)
        assertEquals(1, s.offDays)
        assertEquals(1, s.holidays)
    }

    @Test fun `averages use the day's first start and last end`() {
        val days = listOf(
            DaySnapshot(
                mon,
                listOf(session(WorkMode.FIELD, 6 * 60, 9 * 60), session(WorkMode.BASE, 10 * 60, 18 * 60)),
            ),
        )
        val s = StatsCalculator.summarize("שבוע", days)
        assertEquals(6 * 60, s.avgArrivalMin)
        assertEquals(18 * 60, s.avgDepartureMin)
    }

    @Test fun `a period with no worked time has no averages`() {
        val s = StatsCalculator.summarize("שבוע", listOf(DaySnapshot(mon)))
        assertEquals(0, s.workDays)
        assertNull(s.avgArrivalMin)
    }

    @Test fun `activities are tallied by category and by project`() {
        val day = DaySnapshot(
            mon,
            listOf(
                session(
                    WorkMode.BASE, 8 * 60, 16 * 60,
                    ActivityEntry("רובוטיקה", "פיתוח", 120),
                    ActivityEntry("רובוטיקה", "דיון", 60),
                    ActivityEntry("AI למחלקה", "פיתוח", 60),
                ),
            ),
        )
        val s = StatsCalculator.summarize("שבוע", listOf(day))
        assertEquals(listOf("פיתוח" to 2, "דיון" to 1), s.categoryCounts)
        assertEquals(listOf("רובוטיקה" to 2, "AI למחלקה" to 1), s.projectCounts)
    }
}
