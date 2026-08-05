package com.vitalypr.daylog.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.vitalypr.daylog.MainActivity
import com.vitalypr.daylog.domain.time.formatMinutes
import com.vitalypr.daylog.geofence.GeofenceActionReceiver
import com.vitalypr.daylog.geofence.GeofenceEngine
import com.vitalypr.daylog.reporting.SendReportActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Builds and posts the notification variants of spec §5.5. */
@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val nm get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Report ready: preview + Send action (trampoline-safe activity) + Edit. */
    fun reportReady(date: LocalDate, reportText: String) {
        post(
            ID_REPORT,
            base(Channels.REPORT)
                .setContentTitle("הדוח היומי מוכן")
                .setStyle(NotificationCompat.BigTextStyle().bigText(reportText))
                .addAction(0, "שליחה לוואטסאפ", sendPending(date))
                .addAction(0, "עריכה", openAppPending())
                .build(),
        )
    }

    /** Arrival set, no departure: "still at the office?" with send-anyway. */
    fun stillAtOffice(date: LocalDate) {
        post(
            ID_REPORT,
            base(Channels.REPORT)
                .setContentTitle("עדיין במשרד?")
                .setContentText("השלם שעת יציאה ושלח את הדוח")
                .addAction(0, "עריכה", openAppPending())
                .addAction(0, "שליחה בכל זאת", sendPending(date))
                .build(),
        )
    }

    /** Nothing logged: complete-your-log. */
    fun completeYourLog() {
        post(
            ID_REPORT,
            base(Channels.REPORT)
                .setContentTitle("השלם את יומן היום")
                .setContentText("לא נרשמה כניסה היום")
                .setContentIntent(openAppPending())
                .build(),
        )
    }

    fun cancelReport() = nm.cancel(ID_REPORT)

    /** Geofence ENTER: confirmable arrival suggestion carrying the event time. */
    fun arrivalPrompt(eventMinutes: Int) {
        post(
            ID_ARRIVAL,
            base(Channels.GEOFENCE_ARRIVAL)
                .setContentTitle("הגעת למשרד?")
                .setContentText("רישום כניסה ${formatMinutes(eventMinutes)}")
                .setTimeoutAfter(timeoutUntilMidnight())
                .addAction(0, "אישור", geofenceAction(GeofenceActionReceiver.ACTION_CONFIRM_ARRIVAL, eventMinutes, 210))
                .addAction(0, "עריכה", openAppPending())
                .build(),
        )
    }

    /** Geofence EXIT (after debounce): departure suggestion or update offer. */
    fun departurePrompt(eventMinutes: Int, isUpdate: Boolean) {
        post(
            ID_DEPARTURE,
            base(Channels.GEOFENCE_DEPARTURE)
                .setContentTitle(
                    if (isUpdate) "לעדכן יציאה ל־${formatMinutes(eventMinutes)}?"
                    else "יציאה ${formatMinutes(eventMinutes)}?",
                )
                .setTimeoutAfter(timeoutUntilMidnight())
                .addAction(
                    0, if (isUpdate) "עדכון" else "אישור",
                    geofenceAction(GeofenceActionReceiver.ACTION_CONFIRM_DEPARTURE, eventMinutes, 211),
                )
                .addAction(0, "עריכה", openAppPending())
                .build(),
        )
    }

    /** Geofence EXIT with no arrival ever set: offer to open the day editor. */
    fun logDayPrompt(eventMinutes: Int) {
        post(
            ID_DEPARTURE,
            base(Channels.GEOFENCE_DEPARTURE)
                .setContentTitle("לרשום את היום?")
                .setContentText("יציאה ${formatMinutes(eventMinutes)}")
                .setTimeoutAfter(timeoutUntilMidnight())
                .setContentIntent(openAppPending())
                .build(),
        )
    }

    fun cancelDeparturePrompt() = nm.cancel(ID_DEPARTURE)

    private fun geofenceAction(action: String, eventMinutes: Int, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, GeofenceActionReceiver::class.java)
                .putExtra(GeofenceActionReceiver.EXTRA_ACTION, action)
                .putExtra(GeofenceEngine.EXTRA_EVENT_MINUTES, eventMinutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Suggestion notifications auto-expire at midnight (spec §5.5). */
    private fun timeoutUntilMidnight(): Long {
        val now = java.time.LocalDateTime.now()
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
        return java.time.Duration.between(now, midnight).toMillis()
    }

    private fun base(channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(android.R.drawable.ic_menu_agenda)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)

    private fun post(id: Int, notification: Notification) = nm.notify(id, notification)

    private fun sendPending(date: LocalDate): PendingIntent = PendingIntent.getActivity(
        context, 200, SendReportActivity.intent(context, date),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openAppPending(): PendingIntent = PendingIntent.getActivity(
        context, 201,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ID_REPORT = 10
        const val ID_ARRIVAL = 11
        const val ID_DEPARTURE = 12
    }
}
