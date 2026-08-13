package com.vitalypr.daylog.widget

import com.vitalypr.daylog.data.repo.DayRepository
import com.vitalypr.daylog.di.Now
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.TimeSource
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
        if (arrival) {
            repository.setArrival(date, minutes, TimeSource.MANUAL)
        } else {
            repository.setDeparture(date, minutes, TimeSource.MANUAL)
        }
        return true
    }
}
