package com.vitalypr.daylog.geofence

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.data.settings.SettingsSource
import com.vitalypr.daylog.di.Now
import com.vitalypr.daylog.domain.geo.DayContext
import com.vitalypr.daylog.domain.geo.FenceAction
import com.vitalypr.daylog.domain.geo.FenceEvent
import com.vitalypr.daylog.domain.geo.FenceState
import com.vitalypr.daylog.domain.geo.GeofenceRules
import com.vitalypr.daylog.domain.geo.OfficeFenceMachine
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.TimeSource
import com.vitalypr.daylog.domain.model.WorkMode
import com.vitalypr.daylog.domain.time.WorkTimeStep
import com.vitalypr.daylog.notifications.Notifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Drives [OfficeFenceMachine]: loads the persisted fence state and the facts the
 * decision needs, asks the machine what to do, then performs it. No rules live
 * here — the machine owns the decision table and is exhaustively unit-tested,
 * including the out-of-order deliveries Play Services produces.
 */
@Singleton
class GeofenceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DayRepository,
    private val settingsRepository: SettingsSource,
    private val notifier: Notifier,
    private val fenceState: FenceStateStore,
    private val log: GeofenceLog,
    private val widgetRefresher: com.vitalypr.daylog.widget.DayWidgetRefresher,
    @Now private val now: () -> LocalDateTime,
) {

    /** @param eventAt the transition's own timestamp; null falls back to the clock. */
    suspend fun onEnter(eventAt: LocalDateTime? = null) = handle(FenceEvent.Enter(eventAt ?: now()))

    suspend fun onExitDetected(eventAt: LocalDateTime? = null) = handle(FenceEvent.Exit(eventAt ?: now()))

    suspend fun onExitConfirmedByDebounce() = handle(FenceEvent.DebounceElapsed)

    private suspend fun handle(event: FenceEvent) {
        // An entry arriving long after a pending exit means the debounce alarm was
        // never delivered (Doze, OEM battery manager). Settle that exit on its own
        // day first, or it would be silently swallowed along with the day it closes.
        if (event is FenceEvent.Enter) {
            val pending = fenceState.state(GeofenceManager.FENCE_ID)
            if (pending is FenceState.Leaving &&
                Duration.between(pending.exitAt, event.at) > GeofenceRules.DEBOUNCE
            ) {
                step(pending, FenceEvent.DebounceElapsed)
            }
        }
        step(fenceState.state(GeofenceManager.FENCE_ID), event)
    }

    private suspend fun step(state: FenceState, event: FenceEvent) {
        val transition = OfficeFenceMachine.step(state, event, contextFor(state, event), now())
        fenceState.save(GeofenceManager.FENCE_ID, transition.state)
        transition.actions.forEach { perform(it) }
        log.record(now(), describe(event, transition.actions))
    }

    /** One readable line per transition, for the Settings diagnostics list. */
    private fun describe(event: FenceEvent, actions: List<FenceAction>): String {
        val what = when (event) {
            is FenceEvent.Enter -> "כניסה לגדר"
            is FenceEvent.Exit -> "יציאה מהגדר"
            FenceEvent.DebounceElapsed -> "אישור יציאה"
        }
        val done = actions.mapNotNull {
            when (it) {
                is FenceAction.WriteArrival -> "נרשמה כניסה"
                is FenceAction.WriteDeparture -> "נרשמה יציאה"
                is FenceAction.SuggestArrival -> if (it.shortVisit) "הוצע ביקור קצר" else "הוצעה כניסה"
                is FenceAction.SuggestDeparture -> "הוצעה יציאה"
                is FenceAction.SuggestLogDay -> "הוצע לרשום את היום"
                is FenceAction.MarkArrivalUncertain -> "סומן ביקור קצר"
                else -> null
            }
        }
        return if (done.isEmpty()) "$what · ללא פעולה" else "$what · ${done.joinToString(", ")}"
    }

    /** Resolves the day the event lands on and everything the machine asks about it. */
    private suspend fun contextFor(state: FenceState, event: FenceEvent): DayContext {
        val settings = settingsRepository.settings.first()
        val date: LocalDate? = when (event) {
            is FenceEvent.Enter -> event.at.toLocalDate()
            is FenceEvent.Exit -> null // exits decide nothing until the debounce elapses
            FenceEvent.DebounceElapsed -> {
                val leaving = state as? FenceState.Leaving
                val entryDay = leaving?.let { repository.getDay(it.since.toLocalDate()) }
                val openArrival = entryDay?.sessions?.any { it.mode == WorkMode.BASE && it.isOpen } == true
                leaving?.let { GeofenceRules.exitDate(it.since, it.exitAt, openArrival) }
            }
        }
        val day = date?.let { repository.getDay(it) }
        // The machine reasons about ONE visit to the base, so the context must
        // describe the right one. Arriving asks about a visit in progress — a
        // finished visit is not it, or a second visit the same day would be read
        // as "already arrived" and silently swallowed (the reported bug). Leaving
        // asks about the visit being closed: the running one, else the last.
        val open = date?.let { repository.openSession(it, WorkMode.BASE) }
        val last = date?.let { repository.lastSessionOf(it, WorkMode.BASE) }
        val current = if (event is FenceEvent.Enter) open else open ?: last
        return DayContext(
            date = date,
            isWorkDay = date == null || date.dayOfWeek in settings.workDays,
            isSpecialDay = day != null && day.dayType != DayType.WORK,
            arrivalSet = current?.startMin != null,
            arrivalFromGeofence = current?.startSource == TimeSource.GEOFENCE.name,
            arrivalUncertain = current?.startUncertain == true,
            departureSet = current?.endMin != null,
            departureFromGeofence = current?.endSource == TimeSource.GEOFENCE.name,
            silentMode = settings.silentGeofence,
        )
    }

    // Worked time is booked in quarter hours, so a prompt must offer — and a
    // confirmation must carry — the value that will actually be stored.
    private fun arrivalMinutes(raw: Int) = WorkTimeStep.roundStart(raw)
    private fun departureMinutes(raw: Int) = WorkTimeStep.roundEnd(raw)

    private suspend fun perform(action: FenceAction) = when (action) {
        is FenceAction.SuggestArrival ->
            notifier.arrivalPrompt(action.date, arrivalMinutes(action.minutes), shortVisit = action.shortVisit)
        is FenceAction.WriteArrival -> {
            val minutes = arrivalMinutes(action.minutes)
            repository.startSession(action.date, WorkMode.BASE, minutes, TimeSource.GEOFENCE)
            notifier.recorded(action.date, minutes, arrival = true)
            widgetRefresher.refresh()
        }
        is FenceAction.SuggestDeparture ->
            notifier.departurePrompt(action.date, departureMinutes(action.minutes), action.isUpdate)
        is FenceAction.WriteDeparture -> {
            val minutes = departureMinutes(action.minutes)
            repository.recordDeparture(action.date, WorkMode.BASE, minutes, TimeSource.GEOFENCE)
            notifier.recorded(action.date, minutes, arrival = false)
            widgetRefresher.refresh()
        }
        is FenceAction.SuggestLogDay -> notifier.logDayPrompt(departureMinutes(action.minutes))
        is FenceAction.MarkArrivalUncertain -> {
            markUncertain(action.date, true)
            widgetRefresher.refresh()
        }
        is FenceAction.ClearArrivalUncertain -> markUncertain(action.date, false)
        is FenceAction.ArmDebounce -> armDebounce()
        FenceAction.CancelDebounce -> cancelPendingExit()
        FenceAction.CancelDeparturePrompt -> notifier.cancelDeparturePrompt()
        FenceAction.CancelArrivalPrompt -> notifier.cancelArrivalPrompt()
    }

    private suspend fun markUncertain(date: LocalDate, uncertain: Boolean) {
        val session = repository.openSession(date, WorkMode.BASE)
            ?: repository.lastSessionOf(date, WorkMode.BASE)
            ?: return
        repository.setStartUncertain(session.id, date, uncertain)
    }

    suspend fun confirmArrival(date: LocalDate, eventMinutes: Int) {
        repository.startSession(date, WorkMode.BASE, eventMinutes, TimeSource.GEOFENCE)
        // Confirming is the user's own judgement — the doubt is settled.
        markUncertain(date, false)
        widgetRefresher.refresh()
    }

    suspend fun confirmDeparture(date: LocalDate, eventMinutes: Int) {
        repository.recordDeparture(date, WorkMode.BASE, eventMinutes, TimeSource.GEOFENCE)
        widgetRefresher.refresh()
    }

    private fun armDebounce() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val fireAt = now().plusMinutes(DEBOUNCE_MINUTES)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, exitPendingIntent())
    }

    fun cancelPendingExit() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // FLAG_NO_CREATE: never mint an intent just to cancel it.
        PendingIntent.getBroadcast(
            context,
            RC_EXIT_DEBOUNCE,
            Intent(context, GeofenceExitDebounceReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let(alarmManager::cancel)
    }

    private fun exitPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        RC_EXIT_DEBOUNCE,
        Intent(context, GeofenceExitDebounceReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        val DEBOUNCE_MINUTES: Long = GeofenceRules.DEBOUNCE.toMinutes()
        const val EXTRA_EVENT_MINUTES = "event_minutes"
        const val EXTRA_EVENT_DATE = "event_epoch_day"
        private const val RC_EXIT_DEBOUNCE = 300
    }
}
