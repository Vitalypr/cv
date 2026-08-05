package com.vitalypr.daylog.domain.stats

import com.vitalypr.daylog.domain.model.ActivityEntry
import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.FieldJob
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatsCalculatorTest {

    private var d = 1
    private fun day(
        arr: Int? = null, dep: Int? = null,
        jobs: List<FieldJob> = emptyList(),
        type: DayType = DayType.WORK,
        acts: List<ActivityEntry> = emptyList(),
    ) = DaySnapshot(
        date = LocalDate.of(2026, 7, d++),
        arrivalMin = arr, departureMin = dep,
        fieldJobs = jobs, dayType = type, activities = acts,
    )

    // --- hours rule (spec §2.5) ---

    @Test fun `office only`() {
        val m = StatsCalculator.dayMinutes(day(arr = 480, dep = 1020))
        assertEquals(540, m.office)
        assertEquals(0, m.fieldOutside)
    }

    @Test fun `field inside office span not double counted`() {
        val m = StatsCalculator.dayMinutes(
            day(arr = 480, dep = 1020, jobs = listOf(FieldJob("x", startMin = 600, endMin = 810))),
        )
        assertEquals(540, m.total)
    }

    @Test fun `field partially outside adds only the outside part`() {
        val m = StatsCalculator.dayMinutes(
            day(arr = 480, dep = 1020, jobs = listOf(FieldJob("x", startMin = 960, endMin = 1140))),
        )
        assertEquals(540, m.office)
        assertEquals(120, m.fieldOutside) // 17:00–19:00
    }

    @Test fun `field-only day counts field spans`() {
        val m = StatsCalculator.dayMinutes(day(jobs = listOf(FieldJob("x", startMin = 600, endMin = 780))))
        assertEquals(0, m.office)
        assertEquals(180, m.fieldOutside)
    }

    @Test fun `overlapping field jobs merged before counting`() {
        val m = StatsCalculator.dayMinutes(
            day(jobs = listOf(
                FieldJob("a", startMin = 600, endMin = 720),
                FieldJob("b", startMin = 660, endMin = 780),
            )),
        )
        assertEquals(180, m.fieldOutside) // 10:00–13:00 merged, not 120+120
    }

    @Test fun `overnight office span counts across midnight`() {
        val m = StatsCalculator.dayMinutes(day(arr = 22 * 60, dep = 25 * 60 + 30))
        assertEquals(210, m.office)
    }

    // --- summarize ---

    @Test fun `summarize counts and averages`() {
        val days = listOf(
            day(arr = 480, dep = 1020, acts = listOf(ActivityEntry("פיתוח"), ActivityEntry("דיון"))),
            day(arr = 500, dep = 1040, jobs = listOf(FieldJob("x", startMin = 1050, endMin = 1110)),
                acts = listOf(ActivityEntry("פיתוח"))),
            day(type = DayType.OFF),
            day(type = DayType.HOLIDAY),
            day(), // empty work day — not counted as a work day
        )
        val s = StatsCalculator.summarize("סיכום", days)
        assertEquals(2, s.workDays)
        assertEquals(540 + 540 + 60, s.totalMinutes)
        assertEquals(1, s.fieldDays)
        assertEquals(1, s.offDays)
        assertEquals(1, s.holidays)
        assertEquals(490, s.avgArrivalMin)
        assertEquals(1030, s.avgDepartureMin)
        assertEquals(listOf("פיתוח" to 2, "דיון" to 1), s.categoryCounts)
    }

    @Test fun `no timed days yields null averages and zero work days`() {
        val s = StatsCalculator.summarize("ריק", listOf(day(type = DayType.OFF)))
        assertEquals(0, s.workDays)
        assertNull(s.avgArrivalMin)
        assertNull(s.avgDepartureMin)
    }
}
