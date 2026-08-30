package com.vitalypr.daylog.domain.report

import com.vitalypr.daylog.domain.model.ActivityEntry
import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.WorkMode
import com.vitalypr.daylog.domain.model.WorkSession
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Golden strings for the daily report (spec §2.4). These are the contract. */
class ReportBuilderTest {

    private val rlm = "‏"
    private val tue: LocalDate = LocalDate.of(2026, 8, 4)

    @Test fun `a day of base, home and field work matches the golden string`() {
        val day = DaySnapshot(
            date = tue,
            sessions = listOf(
                WorkSession(
                    mode = WorkMode.BASE, startMin = 10 * 60, endMin = 14 * 60,
                    activities = listOf(
                        ActivityEntry("רובוטיקה", "התקנה", 150, "חיווט לוח, תא 4", "הושלם"),
                        ActivityEntry("רובוטיקה", "דיון", note = "סקירת ליקויים"),
                    ),
                ),
                WorkSession(
                    mode = WorkMode.FIELD, startMin = 6 * 60, endMin = 9 * 60,
                    title = "תחנת משנה אקמה",
                    activities = listOf(ActivityEntry("AI למחלקה", "בדיקות", 120)),
                ),
                WorkSession(mode = WorkMode.HOME, startMin = 18 * 60, endMin = 20 * 60),
            ),
            notes = "הוזמן CT רזרבי",
        )
        val expected = listOf(
            "📋 דוח יומי — יום ג׳ 04.08.2026",
            "🕗 סה״כ 9:00 — בסיס 4:00 · בית 2:00 · שטח 3:00",
            "🏢 בסיס 10:00‎–‎14:00 (4:00)",
            "• רובוטיקה · התקנה — חיווט לוח, תא 4 (2:30 שע׳) · תוצאה: הושלם",
            "• רובוטיקה · דיון — סקירת ליקויים",
            "🚗 שטח: תחנת משנה אקמה 06:00‎–‎09:00 (3:00)",
            "• AI למחלקה · בדיקות (2 שע׳)",
            "🏠 בית 18:00‎–‎20:00 (2:00)",
            "📝 הערות: הוזמן CT רזרבי",
        ).joinToString("\n") { rlm + it }
        assertEquals(expected, ReportBuilder.daily(day))
    }

    @Test fun `every line carries the RLM that keeps WhatsApp right-to-left`() {
        val day = DaySnapshot(tue, listOf(WorkSession(startMin = 492, endMin = 1055)))
        assertTrue(ReportBuilder.daily(day).lines().all { it.startsWith(rlm) })
    }

    @Test fun `an open session renders without an end or a span`() {
        val day = DaySnapshot(tue, listOf(WorkSession(startMin = 10 * 60)))
        val lines = ReportBuilder.daily(day).lines()
        assertEquals("$rlm🏢 בסיס 10:00‎–‎…", lines[1])
        assertFalse(ReportBuilder.daily(day).contains("סה״כ"))
    }

    @Test fun `an empty day is just its header`() {
        assertEquals("$rlm📋 דוח יומי — יום ג׳ 04.08.2026", ReportBuilder.daily(DaySnapshot(tue)))
    }

    @Test fun `the activity line reads project, work, note, then how long`() {
        val day = DaySnapshot(
            tue,
            listOf(
                WorkSession(
                    startMin = 8 * 60, endMin = 16 * 60,
                    activities = listOf(
                        ActivityEntry("AI למחלקה", "פיתוח", 120, "מודל ניבוי", "הושלם"),
                        ActivityEntry("רובוטיקה", "תכנון"),
                    ),
                ),
            ),
        )
        val lines = ReportBuilder.daily(day).lines()
        assertEquals("$rlm• AI למחלקה · פיתוח — מודל ניבוי (2 שע׳) · תוצאה: הושלם", lines[3])
        assertEquals("$rlm• רובוטיקה · תכנון", lines[4])
    }

    @Test fun `duration renders in half-hour steps with a unit`() {
        val day = DaySnapshot(
            tue,
            listOf(
                WorkSession(
                    startMin = 8 * 60, endMin = 16 * 60,
                    activities = listOf(
                        ActivityEntry("פ", "פיתוח", 30),
                        ActivityEntry("פ", "תכנון", 60),
                    ),
                ),
            ),
        )
        val lines = ReportBuilder.daily(day).lines()
        assertEquals("$rlm• פ · פיתוח (30 דק׳)", lines[3])
        assertEquals("$rlm• פ · תכנון (1 שע׳)", lines[4])
    }

    @Test fun `activities render in the order they were logged`() {
        val day = DaySnapshot(
            tue,
            listOf(
                WorkSession(
                    startMin = 8 * 60, endMin = 16 * 60,
                    activities = listOf(
                        ActivityEntry("פ", "אחר"),
                        ActivityEntry("פ", "דיון", 30),
                        ActivityEntry("פ", "התקנה", 60),
                    ),
                ),
            ),
        )
        val cats = ReportBuilder.daily(day).lines().drop(3).map { it.substringAfterLast("· ").substringBefore(" (") }
        assertEquals(listOf("אחר", "דיון", "התקנה"), cats)
    }

    @Test fun `working past midnight renders the next-day marker`() {
        val day = DaySnapshot(tue, listOf(WorkSession(startMin = 22 * 60, endMin = 25 * 60 + 30)))
        val report = ReportBuilder.daily(day)
        assertTrue(report.contains("01:30 (למחרת)"), report)
        assertTrue(report.contains("סה״כ 3:30"), report)
    }
}
