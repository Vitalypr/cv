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
        baseMinutes = 150 * 60,
        homeMinutes = 20 * 60,
        fieldMinutes = 16 * 60 + 30,
        fieldDays = 6,
        offDays = off,
        holidays = hol,
        avgArrivalMin = 8 * 60 + 24,
        avgDepartureMin = 17 * 60 + 38,
        categoryCounts = listOf("פיתוח" to 14, "התקנה" to 9, "דיון" to 7),
        projectCounts = listOf("רובוטיקה" to 18, "AI למחלקה" to 12),
    )

    @Test fun `monthly summary golden string per spec 2_5`() {
        val expected = listOf(
            "סיכום חודשי — אוגוסט 2026",
            "ימי עבודה: 21 | סה״כ שעות: 186:30 | ממוצע ליום: 8:52",
            "בסיס 150:00 · בית 20:00 · שטח 16:30",
            "ימי שטח: 6",
            "פעילויות: פיתוח 14 · התקנה 9 · דיון 7",
        ).joinToString("\n") { rlm + it }
        assertEquals(expected, ReportBuilder.period(summary()))
    }

    @Test fun `off days and holidays appear only when nonzero`() {
        val withSpecial = ReportBuilder.period(summary(off = 2, hol = 1))
        assertEquals("${rlm}ימי שטח: 6 | חופש: 2 | חגים: 1", withSpecial.lines()[3])
        assertFalse(ReportBuilder.period(summary()).contains("חופש"))
    }
}
