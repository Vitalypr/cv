package com.vitalypr.daylog.geofence

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.data.settings.SettingsSource
import com.vitalypr.daylog.di.Now
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.TimeSource
import com.vitalypr.daylog.notifications.Notifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Geofence decision table per spec §5.5/§6.6. Invariants enforced here and in
 * DayRepository: nothing writes without confirmation (unless silent mode),
 * confirms write EVENT time, GEOFENCE never overwrites MANUAL, exits are
 * debounced 10 minutes and cancelled by re-entry.
 */
@Singleton
class GeofenceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DayRepository,
    private val settingsRepository: SettingsSource,
    private val notifier: Notifier,
    @Now private val now: () -> LocalDateTime,
) {

    suspend fun onEnter() {
        cancelPendingExit() // re-entry cancels a pending departure suggestion
        notifier.cancelDeparturePrompt()

        val settings = settingsRepository.settings.first()
        val today = now().toLocalDate()
        val minutes = nowMinutes()
        if (today.dayOfWeek !in settings.workDays) return
        val day = repository.getDay(today)
        if (day != null && day.dayType != DayType.WORK) return // חופש/חג — no prompts (S4)
        if (day?.arrivalMin != null) return

        if (settings.silentGeofence) {
            repository.setArrival(today, minutes, TimeSource.GEOFENCE)
        } else {
            notifier.arrivalPrompt(minutes)
        }
    }

    /** Exit detected: arm the 10-minute debounce alarm carrying the event time. */
    fun onExitDetected() {
        val minutes = nowMinutes()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = now().plusMinutes(DEBOUNCE_MINUTES)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, exitPendingIntent(minutes))
    }

    /** Debounce elapsed without re-entry: apply the exit decision table. */
    suspend fun onExitConfirmedByDebounce(eventMinutes: Int) {
        val settings = settingsRepository.settings.first()
        val today = now().toLocalDate()
        val day = repository.getDay(today)
        if (day != null && day.dayType != DayType.WORK) return // חופש/חג — no prompts (S4)

        when {
            day?.arrivalMin == null -> notifier.logDayPrompt(eventMinutes)
            day.departureMin == null ->
                if (settings.silentGeofence) {
                    repository.setDeparture(today, eventMinutes, TimeSource.GEOFENCE)
                } else {
                    notifier.departurePrompt(eventMinutes, isUpdate = false)
                }
            day.departureSource == TimeSource.GEOFENCE ->
                notifier.departurePrompt(eventMinutes, isUpdate = true) // last exit wins on confirm
            else -> Unit // MANUAL departure — never touched (F2)
        }
    }

    suspend fun confirmArrival(eventMinutes: Int) {
        repository.setArrival(now().toLocalDate(), eventMinutes, TimeSource.GEOFENCE)
    }

    suspend fun confirmDeparture(eventMinutes: Int) {
        repository.setDeparture(now().toLocalDate(), eventMinutes, TimeSource.GEOFENCE)
    }

    fun cancelPendingExit() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(exitPendingIntent(0))
    }

    private fun nowMinutes(): Int = now().toLocalTime().toSecondOfDay() / 60

    private fun exitPendingIntent(eventMinutes: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        RC_EXIT_DEBOUNCE,
        Intent(context, GeofenceExitDebounceReceiver::class.java)
            .putExtra(EXTRA_EVENT_MINUTES, eventMinutes),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val DEBOUNCE_MINUTES = 10L
        const val EXTRA_EVENT_MINUTES = "event_minutes"
        private const val RC_EXIT_DEBOUNCE = 300
    }
}
