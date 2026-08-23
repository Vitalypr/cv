package com.vitalypr.daylog.ui.today

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.vitalypr.daylog.data.db.CategoryEntity
import com.vitalypr.daylog.data.repo.ActivityRow
import com.vitalypr.daylog.data.repo.FieldJobRow
import com.vitalypr.daylog.domain.model.ActivityEntry
import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.DayStatus
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.FieldJob
import com.vitalypr.daylog.domain.model.status
import com.vitalypr.daylog.domain.report.ReportBuilder
import com.vitalypr.daylog.ui.theme.DayLogTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Visual regression per docs/dev/testing.md: S23 Ultra proxy config (412dp-class,
 * xxxhdpi) in Hebrew RTL. Record: -Proborazzi.test.record=true; verify in CI runs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "he-rIL-ldrtl-w412dp-h915dp-xxxhdpi", sdk = [34])
class TodayScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val date = LocalDate.of(2026, 8, 4)

    private val categories = listOf(
        "דיון", "התקנה", "בדיקות", "פיתוח", "תכנון", "תיעוד", "תמיכה", "אחר",
    ).mapIndexed { i, name -> CategoryEntity(id = (i + 1).toLong(), name = name, sortOrder = i) }

    private fun snap(day: DaySnapshot, rows: List<ActivityRow> = emptyList(), jobs: List<FieldJobRow> = emptyList()) =
        TodayUiState(
            date = date, day = day, activityRows = rows, fieldJobRows = jobs,
            categories = categories,
            reportText = if (day.dayType == DayType.WORK) ReportBuilder.daily(day) else "",
            status = day.status(),
        )

    private fun capture(name: String, state: TodayUiState) {
        composeRule.setContent { DayLogTheme { TodayContent(state, TodayCallbacks()) } }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/images/$name.png")
    }

    @Test fun emptyDay() = capture("today_empty", snap(DaySnapshot(date = date)))

    @Test fun shortVisitArrival() {
        capture(
            "today_short_visit",
            snap(
                DaySnapshot(
                    date = date,
                    arrivalMin = 492,
                    arrivalSource = com.vitalypr.daylog.domain.model.TimeSource.GEOFENCE,
                    arrivalUncertain = true,
                ),
            ),
        )
    }

    @Test fun fullDay() {
        val day = DaySnapshot(
            date = date,
            arrivalMin = 492, departureMin = 1055,
            fieldJobs = listOf(FieldJob("תחנת משנה אקמה", "צפון", 600, 810)),
            activities = listOf(
                ActivityEntry("התקנה", 150, "חיווט לוח, תא 4", "הושלם"),
                ActivityEntry("דיון", note = "סקירת ליקויים"),
            ),
            notes = "הוזמן CT רזרבי",
        )
        capture(
            "today_full",
            snap(
                day,
                rows = listOf(
                    ActivityRow(1, 2, "התקנה", 150, "חיווט לוח, תא 4", "הושלם", date, 0),
                    ActivityRow(2, 1, "דיון", null, "סקירת ליקויים", "", date, 1),
                ),
                jobs = listOf(FieldJobRow(1, "תחנת משנה אקמה", "צפון", 600, 810)),
            ),
        )
    }

    @Test fun dayOff() = capture("today_off", snap(DaySnapshot(date = date, dayType = DayType.OFF)))

    @Test fun reportedDay() = capture(
        "today_reported",
        snap(DaySnapshot(date = date, arrivalMin = 492, departureMin = 1055, reported = true)),
    )
}
