package com.vitalypr.daylog.ui.stats

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.vitalypr.daylog.domain.stats.PeriodSummary
import com.vitalypr.daylog.ui.history.HistoryContent
import com.vitalypr.daylog.ui.history.HistoryDayCard
import com.vitalypr.daylog.ui.history.HistoryUiState
import com.vitalypr.daylog.domain.model.DayStatus
import com.vitalypr.daylog.ui.theme.DayLogTheme
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "he-rIL-ldrtl-w412dp-h915dp-xxxhdpi", sdk = [34])
class StatsScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val weekBars = listOf(
        StatsBar("א", 540, 0),
        StatsBar("ב", 480, 120),
        StatsBar("ג", 0, 0, isOff = true),
        StatsBar("ד", 522, 0),
        StatsBar("ה", 474, 72),
        StatsBar("ו", 0, 0),
        StatsBar("ש", 0, 0),
    )

    private val summary = PeriodSummary(
        label = "סיכום שבועי (02.08.2026–08.08.2026)",
        workDays = 4, totalMinutes = 2208, officeMinutes = 2016, fieldMinutes = 192,
        fieldDays = 2, offDays = 1, holidays = 0,
        avgArrivalMin = 8 * 60 + 19, avgDepartureMin = 17 * 60 + 41,
        categoryCounts = listOf("פיתוח" to 6, "דיון" to 4, "התקנה" to 3, "בדיקות" to 2),
    )

    @Test fun statsWeek() {
        composeRule.setContent {
            DayLogTheme {
                StatsContent(
                    StatsUiState(
                        period = StatsPeriod.WEEK,
                        summary = summary,
                        bars = weekBars,
                        chartTitle = "שעות לפי יום — השבוע",
                        selectedBar = 1,
                    ),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/images/stats_week.png")
    }

    @Test fun historyMonth() {
        val cards = listOf(
            HistoryDayCard(LocalDate.of(2026, 8, 4), DayStatus.LOGGED, 563, "פיתוח · דיון"),
            HistoryDayCard(LocalDate.of(2026, 8, 3), DayStatus.REPORTED, 600, "התקנה · בדיקות · שטח"),
            HistoryDayCard(LocalDate.of(2026, 8, 2), DayStatus.REPORTED, 540, "פיתוח"),
            HistoryDayCard(LocalDate.of(2026, 7, 30), DayStatus.OFF, 0, ""),
        )
        composeRule.setContent {
            DayLogTheme {
                HistoryContent(HistoryUiState(month = YearMonth.of(2026, 8), days = cards))
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/images/history_month.png")
    }
}
