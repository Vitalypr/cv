package com.vitalypr.daylog.domain.report

import com.vitalypr.daylog.domain.stats.PeriodSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReportBuilderPeriodTest {

    private val rlm = "‏"

    private fun summary(off: Int = 0, hol: Int = 0) = PeriodSummary(
        label = "סיכום חודשי — אוגוסט 2026",
        workDays = 21,
        totalMinutes = 186 * 60 + 30,
        officeMinutes = 170 * 60,
        fieldMinutes = 16 * 60 + 30,
        fieldDays = 6,
        offDays = off,
        holidays = hol,
        avgArrivalMin = 8 * 60 + 24,
        avgDepartureMin = 17 * 60 + 38,
        categoryCounts = listOf("פיתוח" to 14, "התקנה" to 9, "דיון" to 7),
    )

    @Test fun `monthly summary golden string per spec 2_5`() {
        val expected = listOf(
            "📊 סיכום חודשי — אוגוסט 2026",
            "ימי עבודה: 21 | סה״כ שעות: 186:30 | ממוצע ליום: 8:52",
            "🚗 ימי שטח: 6",
            "✅ פעילויות: פיתוח 14 · התקנה 9 · דיון 7",
        ).joinToString("\n") { rlm + it }
        assertEquals(expected, ReportBuilder.period(summary()))
    }

    @Test fun `off days and holidays appear only when nonzero`() {
        val withSpecial = ReportBuilder.period(summary(off = 2, hol = 1))
        assertEquals("$rlm🚗 ימי שטח: 6 | חופש: 2 | חגים: 1", withSpecial.lines()[2])
        assertFalse(ReportBuilder.period(summary()).contains("חופש"))
    }
}
