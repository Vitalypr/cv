package com.vitalypr.daylog

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.vitalypr.daylog.reminder.ReminderScheduler
import com.vitalypr.daylog.ui.DayLogRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var geofenceManager: com.vitalypr.daylog.geofence.GeofenceManager
    @Inject lateinit var widgetRefresher: com.vitalypr.daylog.widget.DayWidgetRefresher

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* app works either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        // Self-healing re-arm on every app open (spec §6.5/§6.6).
        lifecycleScope.launch {
            reminderScheduler.scheduleNext()
            geofenceManager.sync()
        }
        setContent {
            DayLogRoot()
        }
    }

    /** One choke point for every in-app edit: the widget is only seen after we leave. */
    override fun onStop() {
        super.onStop()
        widgetRefresher.refresh()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
