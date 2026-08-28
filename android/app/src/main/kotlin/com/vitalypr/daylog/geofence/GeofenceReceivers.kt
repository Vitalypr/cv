package com.vitalypr.daylog.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** GMS geofence transitions → engine. Thin; all rules live in GeofenceEngine. */
@AndroidEntryPoint
class GeofenceReceiver : BroadcastReceiver() {

    @Inject lateinit var engine: GeofenceEngine
    @Inject lateinit var jobEngine: JobLocationEngine
    @Inject lateinit var geofenceManager: GeofenceManager

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            // GEOFENCE_NOT_AVAILABLE (location off, NLP disabled) means the fences
            // are gone; a re-sync re-registers them once the condition clears.
            val pendingError = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try { geofenceManager.resync() } finally { pendingError.finish() }
            }
            return
        }
        val transition = event.geofenceTransition
        val ids = event.triggeringGeofences?.map { it.requestId }.orEmpty()
        val eventAt = eventTime(event.triggeringLocation?.time)

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ids.forEach { id ->
                    when {
                        id == GeofenceManager.FENCE_ID -> when (transition) {
                            Geofence.GEOFENCE_TRANSITION_ENTER -> engine.onEnter(eventAt)
                            Geofence.GEOFENCE_TRANSITION_EXIT -> engine.onExitDetected(eventAt)
                        }
                        id.startsWith(GeofenceManager.JOB_PREFIX) ->
                            id.removePrefix(GeofenceManager.JOB_PREFIX).toLongOrNull()?.let { locId ->
                                when (transition) {
                                    Geofence.GEOFENCE_TRANSITION_ENTER -> jobEngine.onEnter(locId, eventAt)
                                    Geofence.GEOFENCE_TRANSITION_EXIT -> jobEngine.onExit(locId, eventAt)
                                }
                            }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * When the boundary was crossed, as far as we can trust it.
 *
 * `triggeringLocation.time` is the age of the FIX, not of the crossing: the fused
 * provider can hand back a location that is minutes old, which used to be written
 * as the arrival time (wrong hour) or — past the staleness bound — thrown away
 * entirely (nothing recorded). A fix older than [MAX_FIX_AGE] or dated in the
 * future is therefore ignored in favour of the delivery time, which is late but
 * never wrong by more than the delivery lag.
 */
private val MAX_FIX_AGE = java.time.Duration.ofMinutes(10)

internal fun eventTime(fixEpochMillis: Long?, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
    val fix = fixEpochMillis?.takeIf { it > 0L }
        ?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
        ?: return now
    val age = java.time.Duration.between(fix, now)
    return if (age.isNegative || age > MAX_FIX_AGE) now else fix
}

/** Fires when the 10-minute exit debounce elapses without re-entry. */
@AndroidEntryPoint
class GeofenceExitDebounceReceiver : BroadcastReceiver() {

    @Inject lateinit var engine: GeofenceEngine

    override fun onReceive(context: Context, intent: Intent) {
        // The exit's own timestamp lives in the persisted fence state, not in the
        // alarm — an alarm extra could not survive a reboot or a rewritten intent.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                engine.onExitConfirmedByDebounce()
            } finally {
                pending.finish()
            }
        }
    }
}

/** Notification "אישור" actions: write the EVENT time, on the EVENT's day. */
@AndroidEntryPoint
class GeofenceActionReceiver : BroadcastReceiver() {

    @Inject lateinit var engine: GeofenceEngine

    override fun onReceive(context: Context, intent: Intent) {
        val minutes = intent.getIntExtra(GeofenceEngine.EXTRA_EVENT_MINUTES, -1)
        if (minutes < 0) return
        val epochDay = intent.getLongExtra(GeofenceEngine.EXTRA_EVENT_DATE, Long.MIN_VALUE)
        if (epochDay == Long.MIN_VALUE) return
        val date = LocalDate.ofEpochDay(epochDay)
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_CONFIRM_ARRIVAL -> engine.confirmArrival(date, minutes)
                    ACTION_CONFIRM_DEPARTURE -> engine.confirmDeparture(date, minutes)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val ACTION_CONFIRM_ARRIVAL = "confirm_arrival"
        const val ACTION_CONFIRM_DEPARTURE = "confirm_departure"
    }
}
