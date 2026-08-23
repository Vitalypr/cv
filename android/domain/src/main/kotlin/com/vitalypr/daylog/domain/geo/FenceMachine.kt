package com.vitalypr.daylog.domain.geo

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Where we believe the user is with respect to one fence.
 *
 * [Leaving] is distinct from [Outside] on purpose: an exit is not believed until
 * the debounce elapses without a re-entry, and the original entry time has to
 * survive that window so the visit's length can be measured.
 */
sealed interface FenceState {
    data object Outside : FenceState
    data class Inside(val since: LocalDateTime) : FenceState
    data class Leaving(val since: LocalDateTime, val exitAt: LocalDateTime) : FenceState
}

sealed interface FenceEvent {
    data class Enter(val at: LocalDateTime) : FenceEvent
    data class Exit(val at: LocalDateTime) : FenceEvent
    /** The 10-minute debounce armed by an [Exit] elapsed with no re-entry. */
    data object DebounceElapsed : FenceEvent
}

/**
 * Everything outside the fence itself that the decision depends on, resolved by
 * the caller for the day the event belongs to.
 *
 * @param date the logical day the event lands on, or null when it belongs to no
 *   day we can trust (stale catch-up delivery — see [GeofenceRules.exitDate]).
 */
data class DayContext(
    val date: LocalDate?,
    val isWorkDay: Boolean = true,
    val isSpecialDay: Boolean = false,
    val arrivalSet: Boolean = false,
    val arrivalFromGeofence: Boolean = false,
    val arrivalUncertain: Boolean = false,
    val departureSet: Boolean = false,
    val departureFromGeofence: Boolean = false,
    val silentMode: Boolean = false,
)

sealed interface FenceAction {
    data class SuggestArrival(val date: LocalDate, val minutes: Int, val shortVisit: Boolean) : FenceAction
    data class WriteArrival(val date: LocalDate, val minutes: Int) : FenceAction
    data class SuggestDeparture(val date: LocalDate, val minutes: Int, val isUpdate: Boolean) : FenceAction
    data class WriteDeparture(val date: LocalDate, val minutes: Int) : FenceAction
    data class SuggestLogDay(val date: LocalDate, val minutes: Int) : FenceAction
    /** Paint the recorded arrival amber: the visit was too short to be a work day. */
    data class MarkArrivalUncertain(val date: LocalDate) : FenceAction
    /** A real stay proves the day after all. */
    data class ClearArrivalUncertain(val date: LocalDate) : FenceAction
    data class ArmDebounce(val exitAt: LocalDateTime) : FenceAction
    data object CancelDebounce : FenceAction
    data object CancelDeparturePrompt : FenceAction
    data object CancelArrivalPrompt : FenceAction
}

data class Transition(val state: FenceState, val actions: List<FenceAction> = emptyList())

/**
 * The office fence as an explicit state machine (spec §5.5/§6.6).
 *
 * Pure: no clock, no I/O, no Android. The caller supplies the event's own
 * timestamp, the persisted [FenceState] and a [DayContext], and gets back the
 * next state plus the actions to perform — which makes every row of the
 * decision table, including the awkward orderings Play Services produces,
 * testable in isolation.
 *
 * The rule that shapes most of it: a visit shorter than
 * [GeofenceRules.MIN_VISIT] is not a working session. Such a visit never
 * suggests a departure; it leaves the arrival standing but flags it, so a
 * drive-past shows up as an amber "check this" rather than as a logged day.
 */
object OfficeFenceMachine {

    fun step(state: FenceState, event: FenceEvent, ctx: DayContext, now: LocalDateTime): Transition =
        when (event) {
            is FenceEvent.Enter -> onEnter(state, event.at, ctx, now)
            is FenceEvent.Exit -> onExit(state, event.at)
            FenceEvent.DebounceElapsed -> onDebounce(state, ctx)
        }

    private fun onEnter(state: FenceState, at: LocalDateTime, ctx: DayContext, now: LocalDateTime): Transition {
        // A catch-up delivery is not an arrival happening now.
        if (GeofenceRules.isStale(at, now)) return Transition(state)

        return when (state) {
            // Re-entry within the debounce window: the exit never happened.
            is FenceState.Leaving -> Transition(
                FenceState.Inside(state.since),
                listOf(FenceAction.CancelDebounce, FenceAction.CancelDeparturePrompt),
            )
            // Already inside — a duplicate delivery must not re-prompt or restart the visit.
            is FenceState.Inside -> Transition(state)
            FenceState.Outside -> {
                // Coming back always retires a standing departure suggestion — the
                // user is here, so the time it offers is stale (spec §5.5).
                val cancel = listOf(FenceAction.CancelDeparturePrompt)
                val date = ctx.date ?: return Transition(FenceState.Inside(at), cancel)
                val blocked = ctx.isSpecialDay || !ctx.isWorkDay || ctx.arrivalSet
                val actions = cancel + when {
                    blocked -> emptyList()
                    ctx.silentMode -> listOf(FenceAction.WriteArrival(date, minutesOn(date, at)))
                    else -> listOf(FenceAction.SuggestArrival(date, minutesOn(date, at), shortVisit = false))
                }
                Transition(FenceState.Inside(at), actions)
            }
        }
    }

    private fun onExit(state: FenceState, at: LocalDateTime): Transition = when (state) {
        // No recorded entry: a phantom exit, dropped without a trace.
        FenceState.Outside -> Transition(state)
        is FenceState.Inside ->
            if (!at.isAfter(state.since)) Transition(state) // predates its own entry — out of order
            else Transition(FenceState.Leaving(state.since, at), listOf(FenceAction.ArmDebounce(at)))
        // Another exit while already leaving: the later one wins.
        is FenceState.Leaving ->
            if (!at.isAfter(state.since)) Transition(state)
            else Transition(FenceState.Leaving(state.since, at), listOf(FenceAction.ArmDebounce(at)))
    }

    private fun onDebounce(state: FenceState, ctx: DayContext): Transition {
        // Nothing pending — the exit was cancelled by a re-entry, or never happened.
        if (state !is FenceState.Leaving) return Transition(state)

        val date = ctx.date ?: return Transition(FenceState.Outside) // stale: belongs to no day
        if (ctx.isSpecialDay) return Transition(FenceState.Outside) // חופש/חג accept nothing (S4)

        val minutes = minutesOn(date, state.exitAt)
        val shortVisit = Duration.between(state.since, state.exitAt) < GeofenceRules.MIN_VISIT

        val actions = if (shortVisit) {
            // Passed by rather than worked: never a departure. Keep the arrival,
            // but mark it so it reads as a suggestion to double-check.
            when {
                ctx.arrivalSet && ctx.arrivalFromGeofence -> listOf(FenceAction.MarkArrivalUncertain(date))
                ctx.arrivalSet -> emptyList() // typed by hand — trusted, left alone
                else -> listOf(
                    FenceAction.SuggestArrival(date, minutesOn(date, state.since), shortVisit = true),
                )
            }
        } else {
            val settled = buildList {
                if (ctx.arrivalUncertain) add(FenceAction.ClearArrivalUncertain(date))
                add(FenceAction.CancelArrivalPrompt)
            }
            when {
                !ctx.arrivalSet -> listOf(FenceAction.SuggestLogDay(date, minutes)) + settled
                !ctx.departureSet ->
                    settled + if (ctx.silentMode) FenceAction.WriteDeparture(date, minutes)
                    else FenceAction.SuggestDeparture(date, minutes, isUpdate = false)
                // A geofence departure may be moved later — last exit of the day wins.
                ctx.departureFromGeofence ->
                    settled + FenceAction.SuggestDeparture(date, minutes, isUpdate = true)
                // MANUAL departure is never touched (F2).
                else -> if (ctx.arrivalUncertain) listOf(FenceAction.ClearArrivalUncertain(date)) else emptyList()
            }
        }
        return Transition(FenceState.Outside, actions)
    }

    /** Minutes from midnight of [date]; may exceed 1440 past midnight (§6.2). */
    fun minutesOn(date: LocalDate, at: LocalDateTime): Int {
        val dayOffset = (at.toLocalDate().toEpochDay() - date.toEpochDay()).toInt()
        return at.toLocalTime().toSecondOfDay() / 60 + dayOffset * MINUTES_PER_DAY
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
