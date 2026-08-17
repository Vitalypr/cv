package com.vitalypr.daylog.geofence

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.data.settings.SettingsSource
import com.vitalypr.daylog.di.Now
import com.vitalypr.daylog.domain.geo.GeofenceRules
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.TimeSource
import com.vitalypr.daylog.notifications.Notifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Office geofence decision table per spec §5.5/§6.6.
 *
 * Ordering is the hard part, not the table: Play Services delivers a transition
 * when it next gets a location fix, so an exit can arrive hours late and *after*
 * the entry that followed it. Three invariants keep that from producing nonsense:
 *
 *  1. Occupancy — an exit is only acted on if we recorded the matching entry
 *     ([FenceStateStore]). A phantom exit is dropped, never prompted.
 *  2. Event time — every decision and every write uses the transition's own
 *     timestamp and its logical day, never "now" (spec §6.2 covers midnight).
 *  3. Dwell — a visit shorter than [GeofenceRules.MIN_OFFICE_DWELL] is a
 *     drive-past: the pending arrival suggestion is withdrawn and no exit fires.
 *
 * Plus the original invariants: confirms write the EVENT time, GEOFENCE never
 * overwrites MANUAL, exits are debounced 10 minutes and cancelled by re-entry.
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
    suspend fun onEnter(eventAt: LocalDateTime? = null) {
        val at = eventAt ?: now()
        cancelPendingExit() // re-entry cancels a pending departure suggestion
        notifier.cancelDeparturePrompt()

        if (GeofenceRules.isStale(at, now())) return // catch-up delivery — not an arrival now

        // Already inside: a duplicate ENTER must not re-prompt or restart the dwell.
        if (fenceState.insideSince(GeofenceManager.FENCE_ID) != null) return
        fenceState.markInside(GeofenceManager.FENCE_ID, at)

        val settings = settingsRepository.settings.first()
        val date = at.toLocalDate()
        if (date.dayOfWeek !in settings.workDays) return
        val day = repository.getDay(date)
        if (day != null && day.dayType != DayType.WORK) return // חופש/חג — no prompts (S4)
        if (day?.arrivalMin != null) return

        val minutes = minutesOf(at)
        if (settings.silentGeofence) {
            repository.setArrival(date, minutes, TimeSource.GEOFENCE)
            widgetRefresher.refresh()
        } else {
            notifier.arrivalPrompt(date, minutes)
        }
    }

    /** Exit detected: validate it, then arm the 10-minute debounce carrying the event. */
    suspend fun onExitDetected(eventAt: LocalDateTime? = null) {
        val at = eventAt ?: now()
        val insideSince = fenceState.insideSince(GeofenceManager.FENCE_ID)
            ?: return // never saw the arrival — a late/duplicate delivery, not an exit

        if (GeofenceRules.isDriveBy(insideSince, at)) {
            // Drove past: withdraw the unconfirmed suggestion instead of following it
            // with a departure prompt.
            fenceState.markOutside(GeofenceManager.FENCE_ID)
            val day = repository.getDay(insideSince.toLocalDate())
            if (day?.arrivalMin == null) notifier.cancelArrivalPrompt()
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val fireAt = now().plusMinutes(DEBOUNCE_MINUTES)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, exitPendingIntent(at))
    }

    /** Debounce elapsed without re-entry: apply the exit decision table. */
    suspend fun onExitConfirmedByDebounce(eventAt: LocalDateTime) {
        val insideSince = fenceState.insideSince(GeofenceManager.FENCE_ID) ?: return
        fenceState.markOutside(GeofenceManager.FENCE_ID)

        val previousDay = repository.getDay(insideSince.toLocalDate())
        val openArrival = previousDay?.arrivalMin != null && previousDay.departureMin == null
        // Which day does this exit belong to — or none at all (§6.2, stale catch-up).
        val date = GeofenceRules.exitDate(insideSince, eventAt, openArrival) ?: return

        val settings = settingsRepository.settings.first()
        val day = repository.getDay(date)
        if (day != null && day.dayType != DayType.WORK) return // חופש/חג — no prompts (S4)

        val minutes = minutesOf(eventAt, relativeTo = date)
        val dwell = java.time.Duration.between(insideSince, eventAt)
        when {
            day?.arrivalMin == null ->
                // Nothing logged for the day: only ask after a stay long enough to
                // have been work. A brief visit — or boundary drift while the user
                // is sitting at their desk — must stay silent and leave the arrival
                // suggestion standing.
                if (dwell >= GeofenceRules.MIN_WORKDAY_DWELL) {
                    notifier.cancelArrivalPrompt()
                    notifier.logDayPrompt(minutes)
                }
            day.departureMin == null -> {
                notifier.cancelArrivalPrompt()
                if (settings.silentGeofence) {
                    repository.setDeparture(date, minutes, TimeSource.GEOFENCE)
                    widgetRefresher.refresh()
                } else {
                    notifier.departurePrompt(date, minutes, isUpdate = false)
                }
            }
            day.departureSource == TimeSource.GEOFENCE ->
                notifier.departurePrompt(date, minutes, isUpdate = true) // last exit wins on confirm
            else -> Unit // MANUAL departure — never touched (F2)
        }
    }

    suspend fun confirmArrival(date: LocalDate, eventMinutes: Int) {
        repository.setArrival(date, eventMinutes, TimeSource.GEOFENCE)
        widgetRefresher.refresh()
    }

    suspend fun confirmDeparture(date: LocalDate, eventMinutes: Int) {
        repository.setDeparture(date, eventMinutes, TimeSource.GEOFENCE)
        widgetRefresher.refresh()
    }

    fun cancelPendingExit() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // FLAG_NO_CREATE: never mint (and never rewrite the extras of) an intent just to cancel it.
        PendingIntent.getBroadcast(
            context,
            RC_EXIT_DEBOUNCE,
            Intent(context, GeofenceExitDebounceReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let(alarmManager::cancel)
    }

    /** Minutes from midnight of [relativeTo]; may exceed 1440 past midnight (§6.2). */
    private fun minutesOf(at: LocalDateTime, relativeTo: LocalDate = at.toLocalDate()): Int {
        val dayOffset = (at.toLocalDate().toEpochDay() - relativeTo.toEpochDay()).toInt()
        return at.toLocalTime().toSecondOfDay() / 60 + dayOffset * MINUTES_PER_DAY
    }

    private fun exitPendingIntent(at: LocalDateTime): PendingIntent = PendingIntent.getBroadcast(
        context,
        RC_EXIT_DEBOUNCE,
        Intent(context, GeofenceExitDebounceReceiver::class.java)
            .putExtra(EXTRA_EVENT_EPOCH_SECOND, at.toEpochSecond(java.time.ZoneOffset.UTC)),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val DEBOUNCE_MINUTES = 10L
        const val EXTRA_EVENT_MINUTES = "event_minutes"
        const val EXTRA_EVENT_DATE = "event_epoch_day"
        const val EXTRA_EVENT_EPOCH_SECOND = "event_epoch_second"
        private const val RC_EXIT_DEBOUNCE = 300
        private const val MINUTES_PER_DAY = 24 * 60
    }
}
