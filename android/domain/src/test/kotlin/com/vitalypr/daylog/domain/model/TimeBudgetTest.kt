package com.vitalypr.daylog.domain.model

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Filling a day honestly: what is left to describe, and when it does not add up. */
class TimeBudgetTest {

    private fun session(start: Int?, end: Int?, vararg durations: Int?) = WorkSession(
        mode = WorkMode.BASE,
        startMin = start,
        endMin = end,
        activities = durations.map { ActivityEntry(durationMin = it) },
    )

    @Test fun `four hours worked and two logged leaves two`() {
        val b = session(10 * 60, 14 * 60, 120).budget()
        assertEquals(240, b.spanMin)
        assertEquals(120, b.allocatedMin)
        assertEquals(120, b.remainingMin)
        assertFalse(b.overAllocated)
    }

    @Test fun `logging more than was worked is flagged`() {
        val b = session(10 * 60, 14 * 60, 240, 120).budget()
        assertTrue(b.overAllocated)
        assertEquals(-120, b.remainingMin) // two hours too many
    }

    @Test fun `a fully described session is complete`() {
        val b = session(10 * 60, 14 * 60, 120, 120).budget()
        assertTrue(b.complete)
        assertEquals(0, b.remainingMin)
        assertFalse(b.overAllocated)
    }

    @Test fun `an open session has nothing to divide up yet`() {
        val b = session(10 * 60, null, 60).budget()
        assertNull(b.spanMin)
        assertNull(b.remainingMin)
        assertFalse(b.overAllocated)
        assertEquals(60, b.allocatedMin)
    }

    @Test fun `activities with no duration do not consume the budget`() {
        val b = session(10 * 60, 14 * 60, null, 60).budget()
        assertEquals(60, b.allocatedMin)
        assertEquals(180, b.remainingMin)
    }

    @Test fun `the day budget adds every session up`() {
        val day = DaySnapshot(
            date = LocalDate.of(2026, 8, 4),
            sessions = listOf(
                session(10 * 60, 14 * 60, 120),      // 4 h at the base
                WorkSession(mode = WorkMode.HOME, startMin = 18 * 60, endMin = 20 * 60),
                WorkSession(mode = WorkMode.FIELD, startMin = 6 * 60, endMin = 9 * 60),
            ),
        )
        val b = day.budget()
        assertEquals(9 * 60, b.spanMin) // 4 + 2 + 3
        assertEquals(120, b.allocatedMin)
        assertEquals(7 * 60, b.remainingMin)
    }

    @Test fun `a day with no measurable session has no budget`() {
        val day = DaySnapshot(LocalDate.of(2026, 8, 4), listOf(session(10 * 60, null)))
        assertNull(day.budget().spanMin)
    }
}
