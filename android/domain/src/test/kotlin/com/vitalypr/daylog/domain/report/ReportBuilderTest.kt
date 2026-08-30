package com.vitalypr.daylog.domain.report

import com.vitalypr.daylog.domain.model.ActivityEntry
import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.FieldJob
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Golden strings per spec §2.4. RLM = U+200F prefixes every line. */
class ReportBuilderTest {

    private val rlm = "‏"
    private val tue = LocalDate.of(2026, 8, 4) // a real Tuesday

    @Test fun `full day matches spec golden string`() {
        val day = DaySnapshot(
            date = tue,
            arrivalMin = 8 * 60 + 12,
            departureMin = 17 * 60 + 35,
            fieldJobs = listOf(
                FieldJob("תחנת משנה אקמה — הרצה", startMin = 600, endMin = 810),
            ),
            activities = listOf(
                ActivityEntry("התקנה", "רובוטיקה", 150, "חיווט לוח, תא 4", "הושלם"),
                ActivityEntry("בדיקות", "רובוטיקה", 90, "בדיקות קבלה לממסרים", "עברו"),
                ActivityEntry("דיון", note = "סקירת ליקויים עם מנהל האתר"),
            ),
            notes = "הוזמן CT רזרבי, צפי הגעה יום חמישי",
        )
        val expected = listOf(
            "📋 דוח יומי — יום ג׳ 04.08.2026",
            "🕗 כניסה: 08:12 | יציאה: 17:35 | סה״כ 9:23",
            "🚗 שטח: תחנת משנה אקמה — הרצה (10:00‎–‎13:30)",
            "✅ פעילויות:",
            "• התקנה · רובוטיקה (2:30 שע׳) — חיווט לוח, תא 4 · תוצאה: הושלם",
            "• בדיקות · רובוטיקה (1:30 שע׳) — בדיקות קבלה לממסרים · תוצאה: עברו",
            "• דיון — סקירת ליקויים עם מנהל האתר",
            "📝 הערות: הוזמן CT רזרבי, צפי הגעה יום חמישי",
        ).joinToString("\n") { rlm + it }
        assertEquals(expected, ReportBuilder.daily(day))
    }

    @Test fun `every line starts with RLM`() {
        val day = DaySnapshot(date = tue, arrivalMin = 492, departureMin = 1055)
        ReportBuilder.daily(day).lines().forEach { line ->
            assertTrue(line.startsWith(rlm), "line missing RLM: $line")
        }
    }

    @Test fun `empty sections omitted entirely`() {
        val report = ReportBuilder.daily(DaySnapshot(date = tue, arrivalMin = 492))
        assertFalse(report.contains("שטח"))
        assertFalse(report.contains("פעילויות"))
        assertFalse(report.contains("הערות"))
        assertFalse(report.contains("יציאה"))
    }

    @Test fun `no arrival drops the time line but keeps header`() {
        val report = ReportBuilder.daily(DaySnapshot(date = tue, notes = "רק הערה"))
        assertEquals(2, report.lines().size)
        assertTrue(report.lines()[0].contains("דוח יומי"))
        assertTrue(report.lines()[1].contains("רק הערה"))
    }

    @Test fun `arrival without departure omits out and total fragments`() {
        val report = ReportBuilder.daily(DaySnapshot(date = tue, arrivalMin = 492))
        val timeLine = report.lines()[1]
        assertEquals("$rlm🕗 כניסה: 08:12", timeLine)
    }

    @Test fun `field time outside office hours adds to the daily total`() {
        // Office 08:00-17:00 (9h) + field 16:00-19:00 -> 2h outside -> 11:00 total.
        val day = DaySnapshot(
            date = tue, arrivalMin = 480, departureMin = 1020,
            fieldJobs = listOf(FieldJob("אתר", startMin = 960, endMin = 1140)),
        )
        assertTrue(ReportBuilder.daily(day).lines()[1].endsWith("סה״כ 11:00"))
    }

    @Test fun `field fully inside office hours does not inflate the total`() {
        val day = DaySnapshot(
            date = tue, arrivalMin = 480, departureMin = 1020,
            fieldJobs = listOf(FieldJob("אתר", startMin = 600, endMin = 780)),
        )
        assertTrue(ReportBuilder.daily(day).lines()[1].endsWith("סה״כ 9:00"))
    }

    @Test fun `overnight departure renders next-day and correct duration`() {
        val day = DaySnapshot(date = tue, arrivalMin = 22 * 60, departureMin = 25 * 60 + 30)
        val timeLine = ReportBuilder.daily(day).lines()[1]
        assertEquals("$rlm🕗 כניסה: 22:00 | יציאה: 01:30 (למחרת) | סה״כ 3:30", timeLine)
    }

    @Test fun `field job with only start renders open range, without times renders bare`() {
        val day = DaySnapshot(
            date = tue,
            fieldJobs = listOf(
                FieldJob("אתר א", startMin = 600),
                FieldJob("אתר ב"),
            ),
        )
        val lines = ReportBuilder.daily(day).lines()
        assertEquals("$rlm🚗 שטח: אתר א (10:00‎–‎…)", lines[1])
        assertEquals("$rlm🚗 שטח: אתר ב", lines[2])
    }

    @Test fun `activities render in the order they were logged`() {
        val day = DaySnapshot(
            date = tue,
            activities = listOf(
                ActivityEntry("אחר"),
                ActivityEntry("דיון", durationMin = 30),
                ActivityEntry("התקנה", durationMin = 60),
                ActivityEntry("תמיכה"),
            ),
        )
        val cats = ReportBuilder.daily(day).lines().drop(2).map { it.substringAfter("• ").substringBefore(" ") }
        assertEquals(listOf("אחר", "דיון", "התקנה", "תמיכה"), cats)
    }

    @Test fun `the project the work was booked to appears on the line`() {
        val day = DaySnapshot(
            date = tue,
            activities = listOf(
                ActivityEntry("פיתוח", project = "AI למחלקה", durationMin = 120),
                ActivityEntry("דיון"), // legacy row with no project
            ),
        )
        val lines = ReportBuilder.daily(day).lines().drop(2)
        assertEquals(rlm + "• פיתוח · AI למחלקה (2 שע׳)", lines[0])
        assertEquals(rlm + "• דיון", lines[1])
    }

    @Test fun `duration renders in half-hour steps with a unit, or is omitted`() {
        val day = DaySnapshot(
            date = tue,
            activities = listOf(
                ActivityEntry("פיתוח", durationMin = 30),
                ActivityEntry("תכנון", durationMin = 60),
                ActivityEntry("דיון"),
            ),
        )
        val lines = ReportBuilder.daily(day).lines().drop(2)
        assertEquals(rlm + "• פיתוח (30 דק׳)", lines[0])
        assertEquals(rlm + "• תכנון (1 שע׳)", lines[1])
        assertEquals(rlm + "• דיון", lines[2])
    }

    @Test fun `repeated categories allowed as separate lines`() {
        val day = DaySnapshot(
            date = tue,
            activities = listOf(
                ActivityEntry("דיון", durationMin = 30, note = "בוקר"),
                ActivityEntry("דיון", durationMin = 30, note = "ערב"),
            ),
        )
        val report = ReportBuilder.daily(day)
        assertEquals(2, report.lines().count { it.contains("דיון") })
    }
}
