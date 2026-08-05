package com.vitalypr.daylog.domain.report

import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.stats.PeriodSummary
import com.vitalypr.daylog.domain.time.formatDate
import com.vitalypr.daylog.domain.time.formatDuration
import com.vitalypr.daylog.domain.time.formatMinutes
import com.vitalypr.daylog.domain.time.formatRange
import com.vitalypr.daylog.domain.time.hebrewDayName

/**
 * Renders the WhatsApp-bound plain-text reports (spec §2.4, §2.5).
 * Every line is prefixed with RLM so lines starting with emoji/digits keep RTL
 * direction in WhatsApp. Empty fragments/sections are omitted entirely.
 * Golden-string tests are the contract; do not change output casually.
 */
object ReportBuilder {

    const val RLM = "‏"

    fun daily(day: DaySnapshot): String {
        val lines = mutableListOf<String>()
        lines += "📋 דוח יומי — ${hebrewDayName(day.date)} ${formatDate(day.date)}"

        if (day.arrivalMin != null) {
            var t = "🕗 כניסה: ${formatMinutes(day.arrivalMin)}"
            if (day.departureMin != null) {
                t += " | יציאה: ${formatMinutes(day.departureMin)}"
                val dur = day.departureMin - day.arrivalMin
                if (dur > 0) t += " | סה״כ ${formatDuration(dur)}"
            }
            lines += t
        }

        for (job in day.fieldJobs) {
            var r = "🚗 שטח: ${job.title}"
            r += if (job.startMin != null) " (${formatRange(job.startMin, job.endMin)})" else ""
            lines += r
        }

        val acts = day.activities.sortedWith(compareBy(nullsLast()) { it.startMin })
        if (acts.isNotEmpty()) {
            lines += "✅ פעילויות:"
            for (a in acts) {
                var r = "• ${a.category}"
                r += if (a.startMin != null) " (${formatRange(a.startMin, a.endMin)})" else ""
                if (a.note.isNotBlank()) r += " — ${a.note.trim()}"
                if (a.result.isNotBlank()) r += " · תוצאה: ${a.result.trim()}"
                lines += r
            }
        }

        if (day.notes.isNotBlank()) lines += "📝 הערות: ${day.notes.trim()}"

        return lines.joinToString("\n") { RLM + it }
    }

    fun period(s: PeriodSummary): String {
        val lines = mutableListOf<String>()
        lines += "📊 ${s.label}"
        lines += "ימי עבודה: ${s.workDays} | סה״כ שעות: ${formatDuration(s.totalMinutes)}" +
            if (s.workDays > 0) " | ממוצע ליום: ${formatDuration(s.totalMinutes / s.workDays)}" else ""
        var special = "🚗 ימי שטח: ${s.fieldDays}"
        if (s.offDays > 0) special += " | חופש: ${s.offDays}"
        if (s.holidays > 0) special += " | חגים: ${s.holidays}"
        lines += special
        if (s.categoryCounts.isNotEmpty()) {
            lines += "✅ פעילויות: " + s.categoryCounts.joinToString(" · ") { (c, n) -> "$c $n" }
        }
        return lines.joinToString("\n") { RLM + it }
    }
}
