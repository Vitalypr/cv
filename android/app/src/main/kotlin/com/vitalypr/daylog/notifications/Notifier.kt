package com.vitalypr.daylog.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.vitalypr.daylog.MainActivity
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
    }
}
