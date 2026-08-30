package com.vitalypr.daylog.domain.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Worked time is booked in quarter hours: the start rounds down, the end rounds
 * up. Both directions widen the session rather than trimming it — an hour the
 * user worked must never disappear into the rounding.
 */
class WorkTimeStepTest {

    @Test fun `an arrival rounds down to the quarter`() {
        assertEquals(8 * 60, WorkTimeStep.roundStart(8 * 60 + 12))
        assertEquals(8 * 60 + 15, WorkTimeStep.roundStart(8 * 60 + 29))
        assertEquals(8 * 60 + 45, WorkTimeStep.roundStart(8 * 60 + 59))
    }

    @Test fun `a leaving time rounds up to the quarter`() {
        assertEquals(17 * 60 + 45, WorkTimeStep.roundEnd(17 * 60 + 35))
        assertEquals(17 * 60 + 15, WorkTimeStep.roundEnd(17 * 60 + 1))
        assertEquals(18 * 60, WorkTimeStep.roundEnd(17 * 60 + 46))
    }

    @Test fun `a time already on a quarter is untouched`() {
        listOf(0, 15, 8 * 60, 8 * 60 + 30, 17 * 60 + 45).forEach {
            assertEquals(it, WorkTimeStep.roundStart(it))
            assertEquals(it, WorkTimeStep.roundEnd(it))
        }
    }

    @Test fun `rounding is idempotent`() {
        (0..1500).forEach {
            assertEquals(WorkTimeStep.roundStart(it), WorkTimeStep.roundStart(WorkTimeStep.roundStart(it)))
            assertEquals(WorkTimeStep.roundEnd(it), WorkTimeStep.roundEnd(WorkTimeStep.roundEnd(it)))
        }
    }

    /** Past midnight (spec §6.2): minutes may exceed 1440 and still round. */
    @Test fun `a past-midnight leaving time rounds up too`() {
        assertEquals(25 * 60 + 30, WorkTimeStep.roundEnd(25 * 60 + 20)) // 01:20 the next day
        assertEquals(24 * 60, WorkTimeStep.roundStart(24 * 60 + 7))
    }

    @Test fun `rounding never shortens the worked stretch`() {
        (0..1440).forEach { start ->
            val end = start + 37
            assertTrue(WorkTimeStep.roundEnd(end) - WorkTimeStep.roundStart(start) >= end - start)
        }
    }
}
