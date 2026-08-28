package com.vitalypr.daylog.domain.geo

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Reproductions of the field failures: Play Services drops transitions, and the
 * machine has to survive that. Each case here failed before v1.1.
 */
class FenceRecoveryTest {

    private val mon: LocalDate = LocalDate.of(2026, 8, 3)
    private val tue: LocalDate = mon.plusDays(1)
    private fun at(h: Int, m: Int, d: LocalDate = mon) = LocalDateTime.of(d, LocalTime.of(h, m))
    private fun ctx(date: LocalDate?, arrivalSet: Boolean = false) =
        DayContext(date = date, arrivalSet = arrivalSet)

    /**
     * RC-1: the exit was never delivered, so the fence stayed Inside overnight.
     * The next morning's arrival must still be recognised, not swallowed as a
     * duplicate — that silence is what made the feature look dead.
     */
    @Test fun `an entry the next day starts a new visit even if yesterday never closed`() {
        val stuck = FenceState.Inside(at(8, 0, mon)) // yesterday, exit missed
        val t = OfficeFenceMachine.step(
            stuck, FenceEvent.Enter(at(8, 12, tue)), ctx(tue), now = at(8, 12, tue),
        )
        assertEquals(FenceState.Inside(at(8, 12, tue)), t.state)
        assertTrue(
            t.actions.any { it is FenceAction.SuggestArrival || it is FenceAction.WriteArrival },
            "the new day's arrival must be acted on",
        )
    }

    /** A genuine duplicate delivery inside the same visit is still ignored. */
    @Test fun `a duplicate entry minutes later is still a duplicate`() {
        val inside = FenceState.Inside(at(8, 12))
        val t = OfficeFenceMachine.step(inside, FenceEvent.Enter(at(8, 25)), ctx(mon), now = at(8, 25))
        assertEquals(inside, t.state)
        assertTrue(t.actions.none { it is FenceAction.SuggestArrival })
    }

    /** An implausibly long "visit" means we missed the exit, even within a day. */
    @Test fun `an entry after an implausibly long visit starts fresh`() {
        val stuck = FenceState.Inside(at(2, 0, mon))
        val t = OfficeFenceMachine.step(
            stuck, FenceEvent.Enter(at(20, 0, mon)), ctx(mon), now = at(20, 0, mon),
        )
        assertEquals(FenceState.Inside(at(20, 0, mon)), t.state)
    }

    /**
     * RC-2: the debounce alarm never fired (Doze / OEM battery killer) and the
     * user came back the next day. Resuming a day-old visit hid that day too.
     */
    @Test fun `an entry long after a pending exit does not resume the old visit`() {
        val stranded = FenceState.Leaving(at(8, 0, mon), at(17, 0, mon))
        val t = OfficeFenceMachine.step(
            stranded, FenceEvent.Enter(at(8, 12, tue)), ctx(tue), now = at(8, 12, tue),
        )
        assertEquals(FenceState.Inside(at(8, 12, tue)), t.state)
        assertTrue(t.actions.any { it is FenceAction.SuggestArrival || it is FenceAction.WriteArrival })
    }

    /** Coming back within the debounce window is still the same visit. */
    @Test fun `an entry inside the debounce window still resumes the visit`() {
        val leaving = FenceState.Leaving(at(8, 0), at(12, 0))
        val t = OfficeFenceMachine.step(leaving, FenceEvent.Enter(at(12, 6)), ctx(mon), now = at(12, 6))
        assertEquals(FenceState.Inside(at(8, 0)), t.state)
        assertTrue(t.actions.contains(FenceAction.CancelDebounce))
    }

    /**
     * RC-3 shape: two visits in one day, automatic recording on. The day must
     * open at the FIRST arrival and close at the LAST departure.
     */
    @Test fun `two visits in a day keep the first arrival and the last departure`() {
        var state: FenceState = FenceState.Outside
        val actions = mutableListOf<FenceAction>()
        fun step(e: FenceEvent, c: DayContext, now: LocalDateTime) {
            val t = OfficeFenceMachine.step(state, e, c, now)
            state = t.state
            actions += t.actions
        }
        val auto = { arrival: Boolean, departure: Boolean, depGeo: Boolean ->
            DayContext(
                date = mon, arrivalSet = arrival, arrivalFromGeofence = arrival,
                departureSet = departure, departureFromGeofence = depGeo, silentMode = true,
            )
        }
        step(FenceEvent.Enter(at(8, 0)), auto(false, false, false), at(8, 0))
        step(FenceEvent.Exit(at(11, 0)), auto(true, false, false), at(11, 0))
        step(FenceEvent.DebounceElapsed, auto(true, false, false), at(11, 10))
        step(FenceEvent.Enter(at(14, 0)), auto(true, true, true), at(14, 0))
        step(FenceEvent.Exit(at(18, 0)), auto(true, true, true), at(18, 0))
        step(FenceEvent.DebounceElapsed, auto(true, true, true), at(18, 10))

        val arrivals = actions.filterIsInstance<FenceAction.WriteArrival>()
        assertEquals(listOf(480), arrivals.map { it.minutes }, "only the first arrival is written")
        val departures = actions.filterIsInstance<FenceAction.WriteDeparture>()
        assertEquals(listOf(660, 1080), departures.map { it.minutes }, "the last exit wins")
    }

    /** Automatic mode must still refuse to invent a departure for a pass-by. */
    @Test fun `automatic mode does not write a departure for a short visit`() {
        val t = OfficeFenceMachine.step(
            FenceState.Leaving(at(10, 0), at(10, 20)),
            FenceEvent.DebounceElapsed,
            DayContext(date = mon, arrivalSet = true, arrivalFromGeofence = true, silentMode = true),
            now = at(10, 30),
        )
        assertTrue(t.actions.none { it is FenceAction.WriteDeparture })
        assertIs<FenceAction.MarkArrivalUncertain>(t.actions.single())
    }
}
