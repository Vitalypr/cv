package com.vitalypr.daylog.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.data.settings.Settings
import com.vitalypr.daylog.data.settings.SettingsSource
import com.vitalypr.daylog.di.Now
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Reminder scheduling per spec §6.5: one-shot AlarmManager.setAndAllowWhileIdle
 * (WorkManager slips hours under Doze — see docs/dev/gotchas.md), re-armed after
 * each firing, on boot, on timezone/time change, and on app open (self-healing).
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsSource,
    private val dayRepository: DayRepository,
    @Now private val now: () -> LocalDateTime,
) {

    suspend fun scheduleNext() {
        val settings = settingsRepository.settings.first()
        val current = now()
        val next = nextReminderAt(current, settings) { date ->
            dayRepository.getDay(date)?.hasData == true
        }
        setAlarm(next, repeat = false)
    }

    /** +2h gentle repeat, never past 23:30 and never into the next day (spec §5.5). */
    fun scheduleRepeat() {
        val current = now()
        val repeatAt = current.plusHours(2)
        val cutoff = current.toLocalDate().atTime(23, 30)
        if (repeatAt.isAfter(cutoff)) return
        setAlarm(repeatAt, repeat = true)
    }

    private fun setAlarm(at: LocalDateTime, repeat: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent(repeat))
    }

    private fun pendingIntent(repeat: Boolean): PendingIntent = PendingIntent.getBroadcast(
        context,
        if (repeat) RC_REPEAT else RC_PRIMARY,
        Intent(context, ReminderReceiver::class.java).putExtra(ReminderReceiver.EXTRA_REPEAT, repeat),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val RC_PRIMARY = 100
        private const val RC_REPEAT = 101

        /**
         * Next reminder: today at report time if still ahead, else scan forward.
         * A day is eligible if it's a configured workday OR already has data
         * (weekend work still gets its evening reminder — spec S4/F7).
         */
        suspend fun nextReminderAt(
            now: LocalDateTime,
            settings: Settings,
            hasData: suspend (LocalDate) -> Boolean,
        ): LocalDateTime {
            var date = now.toLocalDate()
            repeat(14) {
                val at = date.atStartOfDay().plusMinutes(settings.reportTimeMin.toLong())
                val eligible = date.dayOfWeek in settings.workDays || hasData(date)
                if (eligible && at.isAfter(now)) return at
                date = date.plusDays(1)
            }
            // No eligible day in two weeks (degenerate config) — fall back to tomorrow.
            return now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(settings.reportTimeMin.toLong())
        }
    }
}
