package com.vitalypr.daylog.widget

import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.WorkMode
import com.vitalypr.daylog.domain.model.WorkSession
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
        val state = WidgetState.of(day(WorkSession(startMin = 492, endMin = 1055)))
        assertEquals(492, state.arrivalMin)
        assertEquals(1055, state.departureMin)
    }

    @Test fun `arrival recorded but departure still open`() {
        val state = WidgetState.of(day(WorkSession(startMin = 492)))
        assertEquals(492, state.arrivalMin)
        assertNull(state.departureMin) // still the live clock
    }

    @Test fun `day off replaces the buttons and hides any stale hours`() {
        val state = WidgetState.of(day(WorkSession(startMin = 492), dayType = DayType.OFF))
        assertTrue(state.isSpecialDay)
        assertEquals(DayType.OFF, state.specialDay)
        assertNull(state.arrivalMin)
    }

    @Test fun `holiday replaces the buttons`() {
        val state = WidgetState.of(day(dayType = DayType.HOLIDAY))
        assertEquals(DayType.HOLIDAY, state.specialDay)
    }

    /** Two visits to the base: the widget speaks for the day's outer bounds. */
    @Test fun `the earliest start and the latest end of the base sessions win`() {
        val state = WidgetState.of(
            day(
                WorkSession(startMin = 492, endMin = 720),
                WorkSession(startMin = 800, endMin = 1055),
            ),
        )
        assertEquals(492, state.arrivalMin)
        assertEquals(1055, state.departureMin)
    }

    /** Working from home in the evening is not "still at the base". */
    @Test fun `sessions in other modes are ignored`() {
        val state = WidgetState.of(
            day(
                WorkSession(mode = WorkMode.BASE, startMin = 492, endMin = 1000),
                WorkSession(mode = WorkMode.HOME, startMin = 1080, endMin = 1260),
            ),
        )
        assertEquals(1000, state.departureMin)
    }

    private fun day(vararg sessions: WorkSession, dayType: DayType = DayType.WORK) =
        DaySnapshot(date = date, sessions = sessions.toList(), dayType = dayType)
}
