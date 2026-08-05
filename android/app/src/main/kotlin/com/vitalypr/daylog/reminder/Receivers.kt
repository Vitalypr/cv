package com.vitalypr.daylog.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Thin receivers — all logic lives in ReminderEngine/ReminderScheduler. */

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var engine: ReminderEngine

    override fun onReceive(context: Context, intent: Intent) {
        val repeat = intent.getBooleanExtra(EXTRA_REPEAT, false)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                engine.onReminderFired(repeat)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_REPEAT = "repeat"
    }
}

/** Re-arms the alarm after reboot and clock/timezone changes (alarms don't survive these). */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var geofenceManager: com.vitalypr.daylog.geofence.GeofenceManager

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            -> {
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        scheduler.scheduleNext()
                        geofenceManager.sync()
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
