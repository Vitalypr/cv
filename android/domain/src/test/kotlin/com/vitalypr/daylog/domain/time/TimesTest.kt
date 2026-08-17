package com.vitalypr.daylog.domain.time

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TimesTest {

    @Test fun `formats plain time`() {
        assertEquals("08:12", formatMinutes(8 * 60 + 12))
        assertEquals("00:00", formatMinutes(0))
        assertEquals("23:59", formatMinutes(23 * 60 + 59))
    }

    @Test fun `past midnight renders next-day marker`() {
        assertEquals("01:30 (למחרת)", formatMinutes(25 * 60 + 30))
        assertEquals("00:00 (למחרת)", formatMinutes(1440))
    }

    @Test fun `negative time rejected`() {
        assertFailsWith<IllegalArgumentException> { formatMinutes(-1) }
    }

    @Test fun `duration h-mm without leading zero on hours`() {
        assertEquals("9:23", formatDuration(9 * 60 + 23))
        assertEquals("0:05", formatDuration(5))
        assertEquals("186:30", formatDuration(186 * 60 + 30))
    }

    @Test fun `activity duration carries a hebrew unit so it never reads as a clock time`() {
        assertEquals("30 דק׳", formatActivityDuration(30))
        assertEquals("1 שע׳", formatActivityDuration(60))
        assertEquals("1:30 שע׳", formatActivityDuration(90))
        assertEquals("2 שע׳", formatActivityDuration(120))
        assertEquals("12 שע׳", formatActivityDuration(720))
    }

    @Test fun `hebrew day names follow the real calendar`() {
        // 2026-08-02 is a Sunday.
        assertEquals("יום א׳", hebrewDayName(LocalDate.of(2026, 8, 2)))
        assertEquals("יום ג׳", hebrewDayName(LocalDate.of(2026, 8, 4)))
        // Spec prose called 05.08.2026 a Tuesday; the calendar says Wednesday — code follows the calendar.
        assertEquals("יום ד׳", hebrewDayName(LocalDate.of(2026, 8, 5)))
        assertEquals("יום ש׳", hebrewDayName(LocalDate.of(2026, 8, 8)))
    }

    @Test fun `date renders dd_MM_yyyy`() {
        assertEquals("04.08.2026", formatDate(LocalDate.of(2026, 8, 4)))
        assertEquals("01.01.2027", formatDate(LocalDate.of(2027, 1, 1)))
    }
}
