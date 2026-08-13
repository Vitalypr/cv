package com.vitalypr.daylog.widget

import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.DayType
import java.time.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What each pill shows: live clock until a value exists, then the recorded value. */
class WidgetStateTest {

    private val date = LocalDate.of(2026, 8, 4)

    @Test fun `no day row yet - both slots show the live clock`() {
        val state = WidgetState.of(null)
        assertFalse(state.isSpecialDay)
        assertNull(state.arrivalMin)
        assertNull(state.departureMin)
    }

    @Test fun `recorded times are surfaced`() {
        val state = WidgetState.of(DaySnapshot(date = date, arrivalMin = 492, departureMin = 1055))
        assertEquals(492, state.arrivalMin)
        assertEquals(1055, state.departureMin)
    }

    @Test fun `arrival recorded but departure still open`() {
        val state = WidgetState.of(DaySnapshot(date = date, arrivalMin = 492))
        assertEquals(492, state.arrivalMin)
        assertNull(state.departureMin) // still the live clock
    }

    @Test fun `day off replaces the buttons and hides any stale hours`() {
        val state = WidgetState.of(
            DaySnapshot(date = date, arrivalMin = 492, dayType = DayType.OFF),
        )
        assertTrue(state.isSpecialDay)
        assertEquals(DayType.OFF, state.specialDay)
        assertNull(state.arrivalMin)
    }

    @Test fun `holiday replaces the buttons`() {
        val state = WidgetState.of(DaySnapshot(date = date, dayType = DayType.HOLIDAY))
        assertEquals(DayType.HOLIDAY, state.specialDay)
    }
}
