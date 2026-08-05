package com.vitalypr.daylog.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Distinct channels per notification type (spec §5.5) so each can be tuned in system settings. */
object Channels {
    const val REPORT = "report"
    const val GEOFENCE_ARRIVAL = "geofence_arrival"
    const val GEOFENCE_DEPARTURE = "geofence_departure"

    fun ensure(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(REPORT, "תזכורת דוח יומי", NotificationManager.IMPORTANCE_HIGH),
        )
        nm.createNotificationChannel(
            NotificationChannel(GEOFENCE_ARRIVAL, "אישור כניסה למשרד", NotificationManager.IMPORTANCE_HIGH),
        )
        nm.createNotificationChannel(
            NotificationChannel(GEOFENCE_DEPARTURE, "אישור יציאה מהמשרד", NotificationManager.IMPORTANCE_HIGH),
        )
    }
}
