package com.vitalypr.daylog.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Half-hour stepping, with "not stated" as a first-class value (spec F4). */
class ActivityDurationTest {

    @Test fun `first step from unset is half an hour`() {
        assertEquals(30, ActivityDuration.increase(null))
    }

    @Test fun `steps go up in half hours`() {
        assertEquals(60, ActivityDuration.increase(30))
        assertEquals(90, ActivityDuration.increase(60))
    }

    @Test fun `stepping down below half an hour clears the value`() {
        assertEquals(30, ActivityDuration.decrease(60))
        assertNull(ActivityDuration.decrease(30))
        assertNull(ActivityDuration.decrease(null))
    }

    @Test fun `duration is capped at twelve hours`() {
        assertEquals(720, ActivityDuration.increase(720))
        assertEquals(720, ActivityDuration.increase(700))
    }

    @Test fun `snap rounds legacy values onto the half-hour grid`() {
        assertEquals(90, ActivityDuration.snap(95)) // 09:00–10:35 became 1:30
        assertEquals(30, ActivityDuration.snap(20)) // anything logged rounds to a step
        assertEquals(120, ActivityDuration.snap(120))
    }

    @Test fun `snap treats nothing and non-positive spans as unset`() {
        assertNull(ActivityDuration.snap(null))
        assertNull(ActivityDuration.snap(0))
        assertNull(ActivityDuration.snap(-30))
    }
}
