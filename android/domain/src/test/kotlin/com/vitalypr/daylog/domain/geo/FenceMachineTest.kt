package com.vitalypr.daylog.domain.geo

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Every row of the office decision table, plus the orderings Play Services
 * actually produces. The machine is pure, so each case is a single call.
 */
class FenceMachineTest {

    private val mon: LocalDate = LocalDate.of(2026, 8, 3)
    private fun at(h: Int, m: Int, d: LocalDate = mon) = LocalDateTime.of(d, LocalTime.of(h, m))
    private fun ctx(
        date: LocalDate? = mon,
        isWorkDay: Boolean = true,
        isSpecialDay: Boolean = false,
        arrivalSet: Boolean = false,
        arrivalFromGeofence: Boolean = false,
        arrivalUncertain: Boolean = false,
        departureSet: Boolean = false,
        departureFromGeofence: Boolean = false,
        silentMode: Boolean = false,
    ) = DayContext(
        date, isWorkDay, isSpecialDay, arrivalSet, arrivalFromGeofence,
        arrivalUncertain, departureSet, departureFromGeofence, silentMode,
    )

    private fun step(state: FenceState, event: FenceEvent, c: DayContext = ctx(), now: LocalDateTime = at(23, 0)) =
        OfficeFenceMachine.step(state, event, c, now)

    // ---- ENTER ------------------------------------------------------------

    @Test fun `entering on a workday suggests the arrival and records occupancy`() {
        val t = step(FenceState.Outside, FenceEvent.Enter(at(8, 12)), now = at(8, 12))
        assertEquals(FenceState.Inside(at(8, 12)), t.state)
        assertTrue(t.actions.contains(FenceAction.SuggestArrival(mon, 492, shortVisit = false)))
    }

    @Test fun `coming back retires a stale departure suggestion`() {
        val t = step(FenceState.Outside, FenceEvent.Enter(at(13, 0)), ctx(arrivalSet = true), now = at(13, 0))
        assertTrue(t.actions.contains(FenceAction.CancelDeparturePrompt))
    }

    @Test fun `entering in silent mode writes instead of asking`() {
        val t = step(FenceState.Outside, FenceEvent.Enter(at(8, 12)), ctx(silentMode = true), now = at(8, 12))
        assertTrue(t.actions.contains(FenceAction.WriteArrival(mon, 492)))
    }

    @Test fun `entering when the arrival is already logged says nothing`() {
        val t = step(FenceState.Outside, FenceEvent.Enter(at(8, 12)), ctx(arrivalSet = true), now = at(8, 12))
        assertEquals(FenceState.Inside(at(8, 12)), t.state) // still tracked
        assertTrue(t.actions.none { it is FenceAction.SuggestArrival || it is FenceAction.WriteArrival })
    }

    @Test fun `entering on a non-workday says nothing`() {
        val t = step(FenceState.Outside, FenceEvent.Enter(at(8, 12)), ctx(isWorkDay = false), now = at(8, 12))
        assertTrue(t.actions.none { it is FenceAction.SuggestArrival })
    }

    @Test fun `entering on a day off says nothing`() {
        val t = step(FenceState.Outside, FenceEvent.Enter(at(8, 12)), ctx(isSpecialDay = true), now = at(8, 12))
        assertTrue(t.actions.none { it is FenceAction.SuggestArrival })
    }

    @Test fun `a duplicate enter neither re-prompts nor restarts the visit`() {
        val inside = FenceState.Inside(at(8, 12))
        val t = step(inside, FenceEvent.Enter(at(8, 40)), now = at(8, 40))
        assertEquals(inside, t.state) // original entry time preserved
        assertTrue(t.actions.isEmpty())
    }

    @Test fun `re-entering during the debounce cancels the exit`() {
        val leaving = FenceState.Leaving(at(8, 0), at(12, 0))
        val t = step(leaving, FenceEvent.Enter(at(12, 5)), now = at(12, 5))
        assertEquals(FenceState.Inside(at(8, 0)), t.state)
        assertTrue(FenceAction.CancelDebounce in t.actions)
        assertTrue(FenceAction.CancelDeparturePrompt in t.actions)
    }

    @Test fun `a stale enter is not an arrival`() {
        val t = step(FenceState.Outside, FenceEvent.Enter(at(6, 0)), now = at(9, 0))
        assertEquals(FenceState.Outside, t.state)
        assertTrue(t.actions.isEmpty())
    }

    // ---- EXIT -------------------------------------------------------------

    @Test fun `an exit with no recorded entry is dropped`() {
        val t = step(FenceState.Outside, FenceEvent.Exit(at(8, 20)))
        assertEquals(FenceState.Outside, t.state)
        assertTrue(t.actions.isEmpty())
    }

    @Test fun `an exit from inside arms the debounce`() {
        val t = step(FenceState.Inside(at(8, 0)), FenceEvent.Exit(at(17, 30)))
        assertEquals(FenceState.Leaving(at(8, 0), at(17, 30)), t.state)
        assertEquals(listOf(FenceAction.ArmDebounce(at(17, 30))), t.actions)
    }

    @Test fun `a later exit while leaving wins`() {
        val t = step(FenceState.Leaving(at(8, 0), at(17, 0)), FenceEvent.Exit(at(17, 30)))
        assertEquals(FenceState.Leaving(at(8, 0), at(17, 30)), t.state)
    }

    @Test fun `an exit stamped before its own entry is out of order and ignored`() {
        val inside = FenceState.Inside(at(8, 12))
        val t = step(inside, FenceEvent.Exit(at(8, 5)))
        assertEquals(inside, t.state)
        assertTrue(t.actions.isEmpty())
    }

    // ---- THE SHORT-VISIT RULE ---------------------------------------------

    @Test fun `passing by suggests the arrival as a short visit and never a departure`() {
        val t = step(FenceState.Leaving(at(10, 0), at(10, 4)), FenceEvent.DebounceElapsed)
        assertEquals(FenceState.Outside, t.state)
        assertEquals(listOf(FenceAction.SuggestArrival(mon, 600, shortVisit = true)), t.actions)
    }

    @Test fun `a short visit flags an arrival the geofence logged`() {
        val t = step(
            FenceState.Leaving(at(10, 0), at(10, 20)),
            FenceEvent.DebounceElapsed,
            ctx(arrivalSet = true, arrivalFromGeofence = true),
        )
        assertEquals(listOf(FenceAction.MarkArrivalUncertain(mon)), t.actions)
    }

    @Test fun `a short visit leaves a hand-typed arrival alone`() {
        val t = step(
            FenceState.Leaving(at(10, 0), at(10, 20)),
            FenceEvent.DebounceElapsed,
            ctx(arrivalSet = true, arrivalFromGeofence = false),
        )
        assertTrue(t.actions.isEmpty())
    }

    @Test fun `fifty-nine minutes is still a short visit`() {
        val t = step(FenceState.Leaving(at(10, 0), at(10, 59)), FenceEvent.DebounceElapsed)
        assertTrue(t.actions.single() is FenceAction.SuggestArrival)
    }

    @Test fun `exactly one hour is a real visit`() {
        val t = step(
            FenceState.Leaving(at(10, 0), at(11, 0)),
            FenceEvent.DebounceElapsed,
            ctx(arrivalSet = true),
        )
        assertTrue(t.actions.any { it is FenceAction.SuggestDeparture })
    }

    // ---- DEBOUNCE: the real-visit table ------------------------------------

    @Test fun `a full day with nothing logged offers to log it`() {
        val t = step(FenceState.Leaving(at(8, 0), at(17, 35)), FenceEvent.DebounceElapsed)
        assertTrue(t.actions.contains(FenceAction.SuggestLogDay(mon, 1055)))
    }

    @Test fun `departure unset is suggested`() {
        val t = step(
            FenceState.Leaving(at(8, 0), at(17, 35)), FenceEvent.DebounceElapsed,
            ctx(arrivalSet = true),
        )
        assertTrue(t.actions.contains(FenceAction.SuggestDeparture(mon, 1055, isUpdate = false)))
    }

    @Test fun `silent mode writes the departure`() {
        val t = step(
            FenceState.Leaving(at(8, 0), at(17, 35)), FenceEvent.DebounceElapsed,
            ctx(arrivalSet = true, silentMode = true),
        )
        assertTrue(t.actions.contains(FenceAction.WriteDeparture(mon, 1055)))
    }

    @Test fun `a geofence departure is offered an update - last exit wins`() {
        val t = step(
            FenceState.Leaving(at(8, 0), at(19, 5)), FenceEvent.DebounceElapsed,
            ctx(arrivalSet = true, departureSet = true, departureFromGeofence = true),
        )
        assertTrue(t.actions.contains(FenceAction.SuggestDeparture(mon, 1145, isUpdate = true)))
    }

    @Test fun `a hand-typed departure is never touched`() {
        val t = step(
            FenceState.Leaving(at(8, 0), at(19, 5)), FenceEvent.DebounceElapsed,
            ctx(arrivalSet = true, departureSet = true, departureFromGeofence = false),
        )
        assertTrue(t.actions.isEmpty())
    }

    @Test fun `a real stay clears an earlier short-visit flag`() {
        val t = step(
            FenceState.Leaving(at(9, 0), at(17, 0)), FenceEvent.DebounceElapsed,
            ctx(arrivalSet = true, arrivalUncertain = true),
        )
        assertTrue(t.actions.contains(FenceAction.ClearArrivalUncertain(mon)))
    }

    @Test fun `a day off swallows the exit`() {
        val t = step(
            FenceState.Leaving(at(8, 0), at(17, 0)), FenceEvent.DebounceElapsed,
            ctx(isSpecialDay = true),
        )
        assertEquals(FenceState.Outside, t.state)
        assertTrue(t.actions.isEmpty())
    }

    @Test fun `an exit belonging to no trustworthy day is dropped`() {
        val t = step(FenceState.Leaving(at(8, 0), at(17, 0)), FenceEvent.DebounceElapsed, ctx(date = null))
        assertEquals(FenceState.Outside, t.state)
        assertTrue(t.actions.isEmpty())
    }

    @Test fun `a debounce with nothing pending does nothing`() {
        assertTrue(step(FenceState.Outside, FenceEvent.DebounceElapsed).actions.isEmpty())
        val inside = FenceState.Inside(at(8, 0))
        assertEquals(inside, step(inside, FenceEvent.DebounceElapsed).state)
    }

    @Test fun `an overnight shift lands past midnight on the day that is open`() {
        // Entered 22:00 Monday, left 01:30 Tuesday; caller resolved the day to Monday.
        val t = step(
            FenceState.Leaving(at(22, 0), at(1, 30, mon.plusDays(1))),
            FenceEvent.DebounceElapsed,
            ctx(date = mon, arrivalSet = true),
        )
        val suggest = t.actions.filterIsInstance<FenceAction.SuggestDeparture>().single()
        assertEquals(25 * 60 + 30, suggest.minutes) // 01:30 next day = 25:30 on Monday
    }

    @Test fun `a brief visit either side of midnight is still short`() {
        val t = step(
            FenceState.Leaving(at(23, 50), at(0, 10, mon.plusDays(1))),
            FenceEvent.DebounceElapsed,
            ctx(date = mon),
        )
        assertIs<FenceAction.SuggestArrival>(t.actions.single())
    }

    // ---- SEQUENCES: how a real day plays out --------------------------------

    /** Replays a whole day through the machine, threading the state. */
    private fun replay(events: List<Pair<FenceEvent, DayContext>>): Pair<FenceState, List<FenceAction>> {
        var state: FenceState = FenceState.Outside
        val all = mutableListOf<FenceAction>()
        events.forEach { (e, c) ->
            val now = when (e) {
                is FenceEvent.Enter -> e.at
                is FenceEvent.Exit -> e.at
                FenceEvent.DebounceElapsed -> at(23, 0)
            }
            val t = OfficeFenceMachine.step(state, e, c, now)
            state = t.state
            all += t.actions
        }
        return state to all
    }

    @Test fun `scenario - drive past the office on the way elsewhere`() {
        val (state, actions) = replay(
            listOf(
                FenceEvent.Enter(at(9, 0)) to ctx(),
                FenceEvent.Exit(at(9, 3)) to ctx(),
                FenceEvent.DebounceElapsed to ctx(),
            ),
        )
        assertEquals(FenceState.Outside, state)
        assertTrue(actions.none { it is FenceAction.SuggestDeparture || it is FenceAction.SuggestLogDay })
        assertTrue(actions.last() == FenceAction.SuggestArrival(mon, 540, shortVisit = true))
        assertTrue(actions.count { it is FenceAction.SuggestArrival } == 2) // normal on entry, short on exit
    }

    @Test fun `scenario - ordinary day with a lunch break`() {
        val (state, actions) = replay(
            listOf(
                FenceEvent.Enter(at(8, 12)) to ctx(),
                // Out for lunch, back inside before the debounce elapses.
                FenceEvent.Exit(at(12, 30)) to ctx(arrivalSet = true),
                FenceEvent.Enter(at(12, 38)) to ctx(arrivalSet = true),
                FenceEvent.Exit(at(17, 35)) to ctx(arrivalSet = true),
                FenceEvent.DebounceElapsed to ctx(arrivalSet = true),
            ),
        )
        assertEquals(FenceState.Outside, state)
        val departures = actions.filterIsInstance<FenceAction.SuggestDeparture>()
        assertEquals(1, departures.size)
        assertEquals(1055, departures.single().minutes) // the last exit, not the lunch one
    }

    @Test fun `scenario - lunch long enough to fire, then the real departure updates it`() {
        val (_, actions) = replay(
            listOf(
                FenceEvent.Enter(at(8, 0)) to ctx(),
                FenceEvent.Exit(at(12, 0)) to ctx(arrivalSet = true),
                FenceEvent.DebounceElapsed to ctx(arrivalSet = true), // suggests 12:00
                FenceEvent.Enter(at(13, 0)) to ctx(arrivalSet = true, departureSet = true, departureFromGeofence = true),
                FenceEvent.Exit(at(17, 30)) to ctx(arrivalSet = true, departureSet = true, departureFromGeofence = true),
                FenceEvent.DebounceElapsed to ctx(arrivalSet = true, departureSet = true, departureFromGeofence = true),
            ),
        )
        val departures = actions.filterIsInstance<FenceAction.SuggestDeparture>()
        assertEquals(listOf(720, 1050), departures.map { it.minutes })
        assertTrue(departures.last().isUpdate)
    }

    @Test fun `scenario - short visit in the morning, real work in the afternoon clears the flag`() {
        val (_, actions) = replay(
            listOf(
                FenceEvent.Enter(at(9, 0)) to ctx(),
                FenceEvent.Exit(at(9, 20)) to ctx(),
                FenceEvent.DebounceElapsed to ctx(arrivalSet = true, arrivalFromGeofence = true),
                FenceEvent.Enter(at(13, 0)) to ctx(arrivalSet = true, arrivalUncertain = true),
                FenceEvent.Exit(at(18, 0)) to ctx(arrivalSet = true, arrivalUncertain = true),
                FenceEvent.DebounceElapsed to ctx(arrivalSet = true, arrivalUncertain = true),
            ),
        )
        assertTrue(actions.contains(FenceAction.MarkArrivalUncertain(mon)))
        assertTrue(actions.contains(FenceAction.ClearArrivalUncertain(mon)))
        assertTrue(actions.contains(FenceAction.SuggestDeparture(mon, 1080, isUpdate = false)))
    }

    @Test fun `scenario - the morning phantom exit that started all this`() {
        // Yesterday's exit is flushed as the user reaches the office, before the entry.
        val (state, actions) = replay(
            listOf(
                FenceEvent.Exit(at(8, 5)) to ctx(),
                FenceEvent.Enter(at(8, 12)) to ctx(),
                FenceEvent.DebounceElapsed to ctx(),
            ),
        )
        assertEquals(FenceState.Inside(at(8, 12)), state)
        assertTrue(actions.contains(FenceAction.SuggestArrival(mon, 492, shortVisit = false)))
        assertTrue(actions.none { it is FenceAction.SuggestDeparture || it is FenceAction.SuggestLogDay })
    }

    @Test fun `scenario - indoor drift reports an exit while the user is at their desk`() {
        val (_, actions) = replay(
            listOf(
                FenceEvent.Enter(at(8, 12)) to ctx(),
                FenceEvent.Exit(at(8, 40)) to ctx(arrivalSet = true, arrivalFromGeofence = true),
                FenceEvent.DebounceElapsed to ctx(arrivalSet = true, arrivalFromGeofence = true),
            ),
        )
        // Flagged for review, but no invented departure.
        assertTrue(actions.none { it is FenceAction.SuggestDeparture })
        assertTrue(actions.contains(FenceAction.MarkArrivalUncertain(mon)))
    }
}
