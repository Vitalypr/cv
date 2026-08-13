package com.vitalypr.daylog.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Widget taps → [WidgetActions]. Thin; all rules live in the actions class. */
@AndroidEntryPoint
class WidgetActionReceiver : BroadcastReceiver() {

    @Inject lateinit var actions: WidgetActions
    @Inject lateinit var refresher: DayWidgetRefresher

    override fun onReceive(context: Context, intent: Intent) {
        val arrival = when (intent.action) {
            ACTION_ARRIVE -> true
            ACTION_LEAVE -> false
            else -> return
        }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (actions.record(arrival)) refresher.refresh()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ARRIVE = "com.vitalypr.daylog.widget.ARRIVE"
        const val ACTION_LEAVE = "com.vitalypr.daylog.widget.LEAVE"
    }
}
