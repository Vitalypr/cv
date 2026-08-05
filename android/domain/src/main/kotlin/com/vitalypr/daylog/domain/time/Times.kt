package com.vitalypr.daylog.domain.time

import java.time.DayOfWeek
import java.time.LocalDate

/** Formats minutes-from-midnight. Values >= 1440 belong to the next calendar day. */
fun formatMinutes(min: Int): String {
    require(min >= 0) { "negative time: $min" }
    val m = min % 1440
    val base = "%02d:%02d".format(m / 60, m % 60)
    return if (min >= 1440) "$base (למחרת)" else base
}

/** Duration as h:mm (no leading zero on hours), e.g. 563 -> "9:23". */
fun formatDuration(minutes: Int): String {
    require(minutes >= 0) { "negative duration: $minutes" }
    return "${minutes / 60}:${"%02d".format(minutes % 60)}"
}

/** Hebrew short day name, week starting Sunday: יום א׳ .. יום ש׳. */
fun hebrewDayName(date: LocalDate): String {
    val letter = when (date.dayOfWeek) {
        DayOfWeek.SUNDAY -> "א׳"
        DayOfWeek.MONDAY -> "ב׳"
        DayOfWeek.TUESDAY -> "ג׳"
        DayOfWeek.WEDNESDAY -> "ד׳"
        DayOfWeek.THURSDAY -> "ה׳"
        DayOfWeek.FRIDAY -> "ו׳"
        DayOfWeek.SATURDAY -> "ש׳"
    }
    return "יום $letter"
}

/** dd.MM.yyyy with Western digits. */
fun formatDate(date: LocalDate): String =
    "%02d.%02d.%04d".format(date.dayOfMonth, date.monthValue, date.year)

val HEBREW_MONTHS = listOf(
    "ינואר", "פברואר", "מרץ", "אפריל", "מאי", "יוני",
    "יולי", "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר",
)

fun hebrewMonthName(month: Int): String = HEBREW_MONTHS[month - 1]
