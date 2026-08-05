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

/** LRM anchors keep the neutral en-dash inside an LTR run so ranges don't flip in RTL text. */
const val LRM = "‎"

/**
 * Bidi-safe time range for RTL contexts (spec §2.4): "10:00–13:30" must render
 * left-to-right even inside a Hebrew paragraph. Open ranges render "10:00–…".
 */
fun formatRange(startMin: Int, endMin: Int?): String {
    val start = formatMinutes(startMin)
    return if (endMin != null) "$start$LRM–$LRM${formatMinutes(endMin)}" else "$start$LRM–$LRM…"
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

/** Full Hebrew day name: יום ראשון .. שבת. */
fun hebrewDayNameFull(date: LocalDate): String = when (date.dayOfWeek) {
    DayOfWeek.SUNDAY -> "יום ראשון"
    DayOfWeek.MONDAY -> "יום שני"
    DayOfWeek.TUESDAY -> "יום שלישי"
    DayOfWeek.WEDNESDAY -> "יום רביעי"
    DayOfWeek.THURSDAY -> "יום חמישי"
    DayOfWeek.FRIDAY -> "יום שישי"
    DayOfWeek.SATURDAY -> "שבת"
}

/** dd.MM.yyyy with Western digits. */
fun formatDate(date: LocalDate): String =
    "%02d.%02d.%04d".format(date.dayOfMonth, date.monthValue, date.year)

val HEBREW_MONTHS = listOf(
    "ינואר", "פברואר", "מרץ", "אפריל", "מאי", "יוני",
    "יולי", "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר",
)

fun hebrewMonthName(month: Int): String = HEBREW_MONTHS[month - 1]
