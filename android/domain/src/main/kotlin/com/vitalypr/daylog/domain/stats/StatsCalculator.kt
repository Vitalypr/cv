package com.vitalypr.daylog.domain.stats

import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.DayType

data class PeriodSummary(
    val label: String,
    val workDays: Int,
    val totalMinutes: Int,
    val officeMinutes: Int,
    val fieldMinutes: Int,
    val fieldDays: Int,
    val offDays: Int,
    val holidays: Int,
    val avgArrivalMin: Int?,
    val avgDepartureMin: Int?,
    val categoryCounts: List<Pair<String, Int>>,
)

/**
 * Hours rule (spec §2.5): a day's total = office span (arrival→departure) plus
 * field-job time OUTSIDE the office span (no double counting). Overlapping field
 * jobs are merged before counting. A day with only field jobs counts their spans.
 */
object StatsCalculator {

    data class DayMinutes(val office: Int, val fieldOutside: Int) {
        val total: Int get() = office + fieldOutside
    }

    fun dayMinutes(day: DaySnapshot): DayMinutes {
        val office: IntRange? =
            if (day.arrivalMin != null && day.departureMin != null && day.departureMin > day.arrivalMin) {
                day.arrivalMin until day.departureMin
            } else null

        val jobs = day.fieldJobs
            .filter { it.startMin != null && it.endMin != null && it.endMin > it.startMin }
            .map { it.startMin!! until it.endMin!! }
        val merged = mergeIntervals(jobs)

        val officeLen = office?.let { it.last + 1 - it.first } ?: 0
        val fieldOutside = merged.sumOf { r ->
            val len = r.last + 1 - r.first
            if (office == null) len else len - overlap(r, office)
        }
        return DayMinutes(officeLen, fieldOutside)
    }

    fun summarize(label: String, days: List<DaySnapshot>): PeriodSummary {
        val workSnapshots = days.filter { it.dayType == DayType.WORK }
        val perDay = workSnapshots.map { it to dayMinutes(it) }
        val counted = perDay.filter { (_, m) -> m.total > 0 }

        val arrivals = workSnapshots.mapNotNull { it.arrivalMin }
        val departures = workSnapshots.mapNotNull { it.departureMin }

        val categoryCounts = workSnapshots
            .flatMap { it.activities }
            .groupingBy { it.category }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        return PeriodSummary(
            label = label,
            workDays = counted.size,
            totalMinutes = counted.sumOf { (_, m) -> m.total },
            officeMinutes = counted.sumOf { (_, m) -> m.office },
            fieldMinutes = counted.sumOf { (_, m) -> m.fieldOutside },
            fieldDays = workSnapshots.count { d -> d.fieldJobs.isNotEmpty() },
            offDays = days.count { it.dayType == DayType.OFF },
            holidays = days.count { it.dayType == DayType.HOLIDAY },
            avgArrivalMin = arrivals.ifEmpty { null }?.let { it.sum() / it.size },
            avgDepartureMin = departures.ifEmpty { null }?.let { it.sum() / it.size },
            categoryCounts = categoryCounts,
        )
    }

    private fun mergeIntervals(ranges: List<IntRange>): List<IntRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.first }
        val out = mutableListOf(sorted.first())
        for (r in sorted.drop(1)) {
            val last = out.last()
            if (r.first <= last.last + 1) {
                out[out.lastIndex] = last.first..maxOf(last.last, r.last)
            } else {
                out += r
            }
        }
        return out
    }

    private fun overlap(a: IntRange, b: IntRange): Int {
        val lo = maxOf(a.first, b.first)
        val hi = minOf(a.last, b.last)
        return if (hi >= lo) hi + 1 - lo else 0
    }
}
