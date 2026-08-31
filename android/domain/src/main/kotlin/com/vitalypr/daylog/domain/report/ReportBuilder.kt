package com.vitalypr.daylog.domain.report

import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.WorkMode
import com.vitalypr.daylog.domain.stats.PeriodSummary
import com.vitalypr.daylog.domain.stats.StatsCalculator
import com.vitalypr.daylog.domain.time.formatActivityDuration
import com.vitalypr.daylog.domain.time.formatDate
import com.vitalypr.daylog.domain.time.formatDuration
import com.vitalypr.daylog.domain.time.formatRange
import com.vitalypr.daylog.domain.time.hebrewDayName

/**
 * Renders the WhatsApp-bound plain-text reports (spec §2.4, §2.5).
 *
 * Plain words, no emoji (product-owner decision, v2.1): the report is a record
 * someone reads, and the icons added nothing a label does not already say.
 * Every line is still prefixed with RLM — lines that start with a digit flip
 * without it in WhatsApp. Empty fragments and sections are omitted entirely.
 * Golden-string tests are the contract; do not change output casually.
 */
object ReportBuilder {

    const val RLM = "‏"

    fun daily(day: DaySnapshot): String {
        val lines = mutableListOf<String>()
        lines += "דוח יומי — ${hebrewDayName(day.date)} ${formatDate(day.date)}"

        // Header: the day's total, then what it is made of (v2.0 — a day can mix
        // time at the base, from home and on a site).
        val minutes = StatsCalculator.dayMinutes(day)
        if (minutes.total > 0) {
            val parts = WorkMode.entries
                .filter { (minutes.byMode[it] ?: 0) > 0 }
                .joinToString(" · ") { "${modeName(it)} ${formatDuration(minutes.byMode.getValue(it))}" }
            lines += "סה״כ ${formatDuration(minutes.total)} — $parts"
        }

        for (session in day.sessions) {
            if (!session.hasData) continue
            var header = modeName(session.mode)
            if (session.title.isNotBlank()) header += ": ${session.title.trim()}"
            if (session.startMin != null) header += " ${formatRange(session.startMin, session.endMin)}"
            session.spanMin?.let { header += " (${formatDuration(it)})" }
            lines += header

            // Project first, then what was done, then the detail, then how long
            // (product-owner order, v2.0).
            for (a in session.activities) {
                var r = "• "
                r += listOf(a.project, a.category).filter { it.isNotBlank() }.joinToString(" · ")
                if (a.note.isNotBlank()) r += " — ${a.note.trim()}"
                if (a.durationMin != null) r += " (${formatActivityDuration(a.durationMin)})"
                lines += r
            }
        }

        if (day.notes.isNotBlank()) lines += "הערות: ${day.notes.trim()}"

        return lines.joinToString("\n") { RLM + it }
    }

    fun modeName(mode: WorkMode): String = when (mode) {
        WorkMode.BASE -> "בסיס"
        WorkMode.HOME -> "בית"
        WorkMode.FIELD -> "שטח"
    }


    fun period(s: PeriodSummary): String {
        val lines = mutableListOf<String>()
        lines += s.label
        lines += "ימי עבודה: ${s.workDays} | סה״כ שעות: ${formatDuration(s.totalMinutes)}" +
            if (s.workDays > 0) " | ממוצע ליום: ${formatDuration(s.totalMinutes / s.workDays)}" else ""
        val modes = buildList {
            if (s.baseMinutes > 0) add("בסיס ${formatDuration(s.baseMinutes)}")
            if (s.homeMinutes > 0) add("בית ${formatDuration(s.homeMinutes)}")
            if (s.fieldMinutes > 0) add("שטח ${formatDuration(s.fieldMinutes)}")
        }
        if (modes.isNotEmpty()) lines += modes.joinToString(" · ")
        var special = "ימי שטח: ${s.fieldDays}"
        if (s.offDays > 0) special += " | חופש: ${s.offDays}"
        if (s.holidays > 0) special += " | חגים: ${s.holidays}"
        lines += special
        if (s.projectMinutes.isNotEmpty()) {
            lines += "פרויקטים: " + s.projectMinutes.joinToString(" · ") { (p, m) -> "$p ${formatDuration(m)}" } +
                if (s.unallocatedMinutes > 0) " · לא שויך ${formatDuration(s.unallocatedMinutes)}" else ""
        }
        if (s.categoryCounts.isNotEmpty()) {
            lines += "פעילויות: " + s.categoryCounts.joinToString(" · ") { (c, n) -> "$c $n" }
        }
        return lines.joinToString("\n") { RLM + it }
    }
}
