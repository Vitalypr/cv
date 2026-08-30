package com.vitalypr.daylog.widget

import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.di.Now
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.TimeSource
import com.vitalypr.daylog.domain.model.WorkMode
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The rules behind the two widget buttons; the receiver is a thin shell around
 * this (same split as GeofenceReceiver/GeofenceEngine). Writes carry the REAL
 * current time with MANUAL source, which by the repository's source rules
 * overrides anything already stored — a geofence suggestion or an earlier tap.
 */
@Singleton
class WidgetActions @Inject constructor(
    private val repository: DayRepository,
    @Now private val now: () -> LocalDateTime,
) {

    /** Returns false when the tap was refused (חופש/חג accept no hours — spec S4). */
    suspend fun record(arrival: Boolean): Boolean {
        val at = now()
        val date = at.toLocalDate()
        val day = repository.getDay(date)
        if (day != null && day.dayType != DayType.WORK) return false
        val minutes = at.toLocalTime().toSecondOfDay() / 60
        // The widget logs time at the base: כניסה opens a session, יציאה closes
        // the running one. Tapping כניסה again while one is open restarts it, so
        // a mis-tap is still correctable (MANUAL always wins).
        if (arrival) {
            val open = repository.openSession(date, WorkMode.BASE)
            if (open != null) {
                repository.updateSession(open.copy(startMin = minutes, startSource = TimeSource.MANUAL.name))
            } else {
                repository.startSession(date, WorkMode.BASE, minutes, TimeSource.MANUAL)
            }
        } else {
            // Closes the running visit, or corrects the last one's leaving time.
            val recorded = repository.recordDeparture(date, WorkMode.BASE, minutes, TimeSource.MANUAL)
            // Nothing logged at all yet: an end-only session still records the fact.
            if (!recorded) repository.addSession(date, WorkMode.BASE, startMin = null, endMin = minutes)
        }
        return true
    }
}
