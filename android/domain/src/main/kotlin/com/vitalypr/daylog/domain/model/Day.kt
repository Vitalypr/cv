package com.vitalypr.daylog.domain.model

import java.time.LocalDate

enum class DayType { WORK, OFF, HOLIDAY }

enum class TimeSource { MANUAL, GEOFENCE }

/**
 * Where a stretch of work happened (v2.0). A day is made of one or more
 * sessions, and each carries its own hours and its own activities — the same
 * day can hold time at the base, from home and on a client site.
 */
enum class WorkMode { BASE, HOME, FIELD }

/**
 * One logged activity, always inside a [WorkSession]. Carries how long it took
 * in half-hour steps ([ActivityDuration]) — not a clock range.
 */
data class ActivityEntry(
    val project: String = "",
    val category: String = "",
    val durationMin: Int? = null,
    val note: String = "",
    val result: String = "",
)

/**
 * A continuous stretch of work in one [mode]. Times are minutes from midnight of
 * the day; [endMin] may exceed 1440 (worked past midnight).
 */
data class WorkSession(
    val id: Long = 0,
    val mode: WorkMode = WorkMode.BASE,
    val startMin: Int? = null,
    val endMin: Int? = null,
    /** Client/site for FIELD; free text the user may set for any mode. */
    val title: String = "",
    val locationText: String? = null,
    val startSource: TimeSource = TimeSource.MANUAL,
    val endSource: TimeSource = TimeSource.MANUAL,
    /** Amber "check this": the geofence visit behind the start was under an hour. */
    val startUncertain: Boolean = false,
    val activities: List<ActivityEntry> = emptyList(),
) {
    /** Worked minutes, or null while the session is still open or malformed. */
    val spanMin: Int?
        get() = if (startMin != null && endMin != null && endMin > startMin) endMin - startMin else null

    val isOpen: Boolean get() = startMin != null && endMin == null

    val hasData: Boolean
        get() = startMin != null || endMin != null || title.isNotBlank() || activities.isNotEmpty()
}

/**
 * How much of a session's hours the logged activities account for.
 *
 * The point is to be able to fill a day honestly: the screen shows what is left
 * to describe, and says so plainly when the activities claim more time than was
 * actually worked.
 */
data class TimeBudget(val spanMin: Int?, val allocatedMin: Int) {
    /** Null when the session has no measurable span yet — nothing to divide up. */
    val remainingMin: Int? get() = spanMin?.let { it - allocatedMin }
    val overAllocated: Boolean get() = spanMin != null && allocatedMin > spanMin
    val complete: Boolean get() = spanMin != null && allocatedMin == spanMin
}

fun WorkSession.budget(): TimeBudget =
    TimeBudget(spanMin, activities.sumOf { it.durationMin ?: 0 })

/** Immutable snapshot of one logical day. */
data class DaySnapshot(
    val date: LocalDate,
    val sessions: List<WorkSession> = emptyList(),
    val dayType: DayType = DayType.WORK,
    val notes: String = "",
    val reported: Boolean = false,
    val editedAfterReport: Boolean = false,
) {
    val activities: List<ActivityEntry> get() = sessions.flatMap { it.activities }

    /** Earliest start / latest end across every session — the day's outer bounds. */
    val firstStartMin: Int? get() = sessions.mapNotNull { it.startMin }.minOrNull()
    val lastEndMin: Int? get() = sessions.mapNotNull { it.endMin }.maxOrNull()

    val hasData: Boolean
        get() = sessions.any { it.hasData } || notes.isNotBlank()

    fun budget(): TimeBudget = TimeBudget(
        spanMin = sessions.mapNotNull { it.spanMin }.takeIf { it.isNotEmpty() }?.sum(),
        allocatedMin = activities.sumOf { it.durationMin ?: 0 },
    )
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
