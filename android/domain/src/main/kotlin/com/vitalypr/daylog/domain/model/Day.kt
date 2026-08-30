package com.vitalypr.daylog.domain.model

import java.time.LocalDate

enum class DayType { WORK, OFF, HOLIDAY }

enum class TimeSource { MANUAL, GEOFENCE }

/**
 * One logged activity. Carries how long it took, in half-hour steps
 * ([ActivityDuration]) — not a clock range. null = duration not stated.
 */
data class ActivityEntry(
    val category: String,
    /** The project the work was booked against (v1.2); blank only for legacy rows. */
    val project: String = "",
    val durationMin: Int? = null,
    val note: String = "",
    val result: String = "",
)

data class FieldJob(
    val title: String,
    val locationText: String? = null,
    val startMin: Int? = null,
    val endMin: Int? = null,
)

/**
 * Immutable snapshot of one logical day. Arrival/departure are minutes from midnight
 * of [date]; departure may exceed 1440 (worked past midnight).
 */
data class DaySnapshot(
    val date: LocalDate,
    val arrivalMin: Int? = null,
    val departureMin: Int? = null,
    val arrivalSource: TimeSource = TimeSource.MANUAL,
    /** Amber "check this": the geofence visit behind the arrival was under an hour. */
    val arrivalUncertain: Boolean = false,
    val departureSource: TimeSource = TimeSource.MANUAL,
    val dayType: DayType = DayType.WORK,
    val notes: String = "",
    val fieldJobs: List<FieldJob> = emptyList(),
    val activities: List<ActivityEntry> = emptyList(),
    val reported: Boolean = false,
    val editedAfterReport: Boolean = false,
) {
    val hasData: Boolean
        get() = arrivalMin != null || departureMin != null ||
            fieldJobs.isNotEmpty() || activities.isNotEmpty() || notes.isNotBlank()
}

enum class DayStatus { EMPTY, LOGGED, REPORTED, REPORTED_EDITED, OFF, HOLIDAY }

fun DaySnapshot.status(): DayStatus = when {
    dayType == DayType.OFF -> DayStatus.OFF
    dayType == DayType.HOLIDAY -> DayStatus.HOLIDAY
    reported && editedAfterReport -> DayStatus.REPORTED_EDITED
    reported -> DayStatus.REPORTED
    hasData -> DayStatus.LOGGED
    else -> DayStatus.EMPTY
}
