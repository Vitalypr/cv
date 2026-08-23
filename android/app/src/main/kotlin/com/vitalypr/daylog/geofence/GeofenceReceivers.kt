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

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val transition = event.geofenceTransition
        val ids = event.triggeringGeofences?.map { it.requestId }.orEmpty()
        // When the boundary was actually crossed. GMS reports transitions only once
        // it has a fix, so this can be well before delivery — the engines depend on
        // it to tell a real exit from a catch-up.
        val eventAt = event.triggeringLocation?.time
            ?.takeIf { it > 0L }
            ?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }

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
