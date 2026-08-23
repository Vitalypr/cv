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

    /**
     * Geofence ENTER: confirmable arrival suggestion carrying the event day + time.
     * [shortVisit] means the stay turned out to be under an hour — the suggestion
     * is kept but coloured amber and worded as a doubt, because a pass-by looks
     * exactly like an arrival until the user leaves again.
     */
    fun arrivalPrompt(date: LocalDate, eventMinutes: Int, shortVisit: Boolean = false) {
        post(
            ID_ARRIVAL,
            base(Channels.GEOFENCE_ARRIVAL)
                .setContentTitle(if (shortVisit) "ביקור קצר במשרד — לרשום כניסה?" else "הגעת למשרד?")
                .setContentText(
                    if (shortVisit) "היית פחות משעה · כניסה ${formatMinutes(eventMinutes)}"
                    else "רישום כניסה ${formatMinutes(eventMinutes)}",
                )
                .apply { if (shortVisit) setColor(AMBER).setColorized(false) }
                .setTimeoutAfter(timeoutUntilMidnight())
                .addAction(
                    0, "אישור",
                    geofenceAction(GeofenceActionReceiver.ACTION_CONFIRM_ARRIVAL, date, eventMinutes, 210),
                )
                .addAction(0, "עריכה", openAppPending())
                .build(),
        )
    }

    fun cancelArrivalPrompt() = nm.cancel(ID_ARRIVAL)

    /** Geofence EXIT (after debounce): departure suggestion or update offer. */
    fun departurePrompt(date: LocalDate, eventMinutes: Int, isUpdate: Boolean) {
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
                    geofenceAction(GeofenceActionReceiver.ACTION_CONFIRM_DEPARTURE, date, eventMinutes, 211),
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

    private fun geofenceAction(
        action: String,
        date: LocalDate,
        eventMinutes: Int,
        requestCode: Int,
    ): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode,
        Intent(context, GeofenceActionReceiver::class.java)
            .putExtra(GeofenceActionReceiver.EXTRA_ACTION, action)
            .putExtra(GeofenceEngine.EXTRA_EVENT_MINUTES, eventMinutes)
            // The day the transition happened — confirming near midnight must not
            // land the value on the day the user happened to tap.
            .putExtra(GeofenceEngine.EXTRA_EVENT_DATE, date.toEpochDay()),
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
        /** ui/theme Amber — "logged, not sent"/uncertain semantics. */
        private const val AMBER = 0xFFA9770F.toInt()
        const val ID_REPORT = 10
        const val ID_ARRIVAL = 11
        const val ID_DEPARTURE = 12
    }
}
