package com.vitalypr.daylog.domain.stats

import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.WorkMode

data class PeriodSummary(
    val label: String,
    val workDays: Int,
    val totalMinutes: Int,
    val baseMinutes: Int,
    val homeMinutes: Int,
    val fieldMinutes: Int,
    val fieldDays: Int,
    val offDays: Int,
    val holidays: Int,
    val avgArrivalMin: Int?,
    val avgDepartureMin: Int?,
    val categoryCounts: List<Pair<String, Int>>,
    val projectCounts: List<Pair<String, Int>>,
)

/**
 * Hours rule (v2.0): a day's total is the sum of its work sessions, whatever
 * mode each one is — four hours at the base plus two from home plus three on a
 * site is a nine-hour day. Sessions are distinct stretches of time and are not
 * expected to overlap, so they are simply added; this single rule feeds the
 * report's total, the PDF, the Today screen and Statistics.
 */
object StatsCalculator {

    data class DayMinutes(val byMode: Map<WorkMode, Int>) {
        val base: Int get() = byMode[WorkMode.BASE] ?: 0
        val home: Int get() = byMode[WorkMode.HOME] ?: 0
        val field: Int get() = byMode[WorkMode.FIELD] ?: 0
        val total: Int get() = byMode.values.sum()
    }

    fun dayMinutes(day: DaySnapshot): DayMinutes = DayMinutes(
        day.sessions
            .mapNotNull { s -> s.spanMin?.let { s.mode to it } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, spans) -> spans.sum() },
    )

    fun summarize(label: String, days: List<DaySnapshot>): PeriodSummary {
        val workSnapshots = days.filter { it.dayType == DayType.WORK }
        val perDay = workSnapshots.map { it to dayMinutes(it) }
        val counted = perDay.filter { (_, m) -> m.total > 0 }

        val arrivals = workSnapshots.mapNotNull { it.firstStartMin }
        val departures = workSnapshots.mapNotNull { it.lastEndMin }

        fun <T> tally(select: (com.vitalypr.daylog.domain.model.ActivityEntry) -> T) = workSnapshots
            .flatMap { it.activities }
            .groupingBy(select)
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        return PeriodSummary(
            label = label,
            workDays = counted.size,
            totalMinutes = counted.sumOf { (_, m) -> m.total },
            baseMinutes = counted.sumOf { (_, m) -> m.base },
            homeMinutes = counted.sumOf { (_, m) -> m.home },
            fieldMinutes = counted.sumOf { (_, m) -> m.field },
            fieldDays = workSnapshots.count { d -> d.sessions.any { it.mode == WorkMode.FIELD } },
            offDays = days.count { it.dayType == DayType.OFF },
            holidays = days.count { it.dayType == DayType.HOLIDAY },
            avgArrivalMin = arrivals.ifEmpty { null }?.let { it.sum() / it.size },
            avgDepartureMin = departures.ifEmpty { null }?.let { it.sum() / it.size },
            categoryCounts = tally { it.category }.filter { it.first.isNotBlank() },
            projectCounts = tally { it.project }.filter { it.first.isNotBlank() },
        )
    }
}
