package com.vitalypr.daylog.reminder

import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.di.Now
import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.report.ReportBuilder
import com.vitalypr.daylog.notifications.Notifier
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The report-time decision table of spec §5.5. Kept out of the BroadcastReceiver
 * so every row is directly unit-testable.
 */
@Singleton
class ReminderEngine @Inject constructor(
    private val repository: DayRepository,
    private val notifier: Notifier,
    private val scheduler: ReminderScheduler,
    @Now private val now: () -> LocalDateTime,
) {

    suspend fun onReminderFired(isRepeat: Boolean) {
        val today = now().toLocalDate()
        val day = repository.getDay(today) ?: DaySnapshot(date = today)

        val posted = when {
            day.dayType != DayType.WORK -> false // חופש/חג — no nag (spec S4)
            day.reported && !day.editedAfterReport -> false // already sent
            day.arrivalMin != null && day.departureMin != null -> {
                notifier.reportReady(today, ReportBuilder.daily(day)); true
            }
            day.arrivalMin != null -> {
                notifier.stillAtOffice(today); true
            }
            else -> {
                notifier.completeYourLog(); true
            }
        }

        if (posted && !isRepeat) scheduler.scheduleRepeat()
        if (!isRepeat) scheduler.scheduleNext()
    }
}
