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
import com.vitalypr.daylog.notifications.Notifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
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
    private val widgetRefresher: com.vitalypr.daylog.widget.DayWidgetRefresher,
    @Now private val now: () -> LocalDateTime,
) {

    /** @param eventAt the transition's own timestamp; null falls back to the clock. */
    suspend fun onEnter(eventAt: LocalDateTime? = null) = handle(FenceEvent.Enter(eventAt ?: now()))

    suspend fun onExitDetected(eventAt: LocalDateTime? = null) = handle(FenceEvent.Exit(eventAt ?: now()))

    suspend fun onExitConfirmedByDebounce() = handle(FenceEvent.DebounceElapsed)

    private suspend fun handle(event: FenceEvent) {
        val state = fenceState.state(GeofenceManager.FENCE_ID)
        val transition = OfficeFenceMachine.step(state, event, contextFor(state, event), now())
        fenceState.save(GeofenceManager.FENCE_ID, transition.state)
        transition.actions.forEach { perform(it) }
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
                val openArrival = entryDay?.arrivalMin != null && entryDay.departureMin == null
                leaving?.let { GeofenceRules.exitDate(it.since, it.exitAt, openArrival) }
            }
        }
        val day = date?.let { repository.getDay(it) }
        return DayContext(
            date = date,
            isWorkDay = date == null || date.dayOfWeek in settings.workDays,
            isSpecialDay = day != null && day.dayType != DayType.WORK,
            arrivalSet = day?.arrivalMin != null,
            arrivalFromGeofence = day?.arrivalSource == TimeSource.GEOFENCE,
            arrivalUncertain = day?.arrivalUncertain == true,
            departureSet = day?.departureMin != null,
            departureFromGeofence = day?.departureSource == TimeSource.GEOFENCE,
            silentMode = settings.silentGeofence,
        )
    }

    private suspend fun perform(action: FenceAction) = when (action) {
        is FenceAction.SuggestArrival ->
            notifier.arrivalPrompt(action.date, action.minutes, shortVisit = action.shortVisit)
        is FenceAction.WriteArrival -> {
            repository.setArrival(action.date, action.minutes, TimeSource.GEOFENCE)
            widgetRefresher.refresh()
        }
        is FenceAction.SuggestDeparture ->
            notifier.departurePrompt(action.date, action.minutes, action.isUpdate)
        is FenceAction.WriteDeparture -> {
            repository.setDeparture(action.date, action.minutes, TimeSource.GEOFENCE)
            widgetRefresher.refresh()
        }
        is FenceAction.SuggestLogDay -> notifier.logDayPrompt(action.minutes)
        is FenceAction.MarkArrivalUncertain -> {
            repository.setArrivalUncertain(action.date, true)
            widgetRefresher.refresh()
        }
        is FenceAction.ClearArrivalUncertain -> repository.setArrivalUncertain(action.date, false)
        is FenceAction.ArmDebounce -> armDebounce()
        FenceAction.CancelDebounce -> cancelPendingExit()
        FenceAction.CancelDeparturePrompt -> notifier.cancelDeparturePrompt()
        FenceAction.CancelArrivalPrompt -> notifier.cancelArrivalPrompt()
    }

    suspend fun confirmArrival(date: LocalDate, eventMinutes: Int) {
        repository.setArrival(date, eventMinutes, TimeSource.GEOFENCE)
        // Confirming is the user's own judgement — the doubt is settled.
        repository.setArrivalUncertain(date, false)
        widgetRefresher.refresh()
    }

    suspend fun confirmDeparture(date: LocalDate, eventMinutes: Int) {
        repository.setDeparture(date, eventMinutes, TimeSource.GEOFENCE)
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
        const val DEBOUNCE_MINUTES = 10L
        const val EXTRA_EVENT_MINUTES = "event_minutes"
        const val EXTRA_EVENT_DATE = "event_epoch_day"
        private const val RC_EXIT_DEBOUNCE = 300
    }
}
