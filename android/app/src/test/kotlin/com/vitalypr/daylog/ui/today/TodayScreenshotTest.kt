package com.vitalypr.daylog.ui.today

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.vitalypr.daylog.data.db.CategoryEntity
import com.vitalypr.daylog.data.db.ProjectEntity
import com.vitalypr.daylog.data.db.WorkSessionEntity
import com.vitalypr.daylog.data.repo.ActivityRow
import com.vitalypr.daylog.data.repo.SessionRow
import com.vitalypr.daylog.domain.model.ActivityEntry
import com.vitalypr.daylog.domain.model.DaySnapshot
import com.vitalypr.daylog.domain.model.DayType
import com.vitalypr.daylog.domain.model.TimeSource
import com.vitalypr.daylog.domain.model.WorkMode
import com.vitalypr.daylog.domain.model.WorkSession
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

    private val projects = listOf("רובוטיקה", "הנדסת מערכת למחלקה", "AI למחלקה")
        .mapIndexed { i, name -> ProjectEntity(id = (i + 1).toLong(), name = name, sortOrder = i) }

    /** Builds the paired domain session + editable row the screen renders from. */
    private fun session(
        id: Long,
        mode: WorkMode,
        startMin: Int? = null,
        endMin: Int? = null,
        title: String = "",
        startSource: TimeSource = TimeSource.MANUAL,
        startUncertain: Boolean = false,
        activities: List<ActivityEntry> = emptyList(),
    ): Pair<WorkSession, SessionRow> {
        val domain = WorkSession(
            id = id, mode = mode, startMin = startMin, endMin = endMin, title = title,
            startSource = startSource, startUncertain = startUncertain, activities = activities,
        )
        val entity = WorkSessionEntity(
            id = id, date = date.toString(), mode = mode.name, startMin = startMin, endMin = endMin,
            title = title, startSource = startSource.name, startUncertain = startUncertain,
        )
        val rows = activities.mapIndexed { i, a ->
            ActivityRow(
                id = id * 100 + i, sessionId = id,
                categoryId = categories.first { it.name == a.category }.id, category = a.category,
                projectId = projects.first { it.name == a.project }.id, project = a.project,
                durationMin = a.durationMin, note = a.note, sortOrder = i,
            )
        }
        return domain to SessionRow(entity, domain, rows)
    }

    private fun snap(
        sessions: List<Pair<WorkSession, SessionRow>> = emptyList(),
        dayType: DayType = DayType.WORK,
        notes: String = "",
        reported: Boolean = false,
    ): TodayUiState {
        val day = DaySnapshot(
            date = date,
            sessions = sessions.map { it.first },
            dayType = dayType,
            notes = notes,
            reported = reported,
        )
        return TodayUiState(
            date = date,
            day = day,
            sessionRows = sessions.map { it.second },
            categories = categories,
            projects = projects,
            reportText = if (dayType == DayType.WORK) ReportBuilder.daily(day) else "",
            status = day.status(),
        )
    }

    private fun capture(name: String, state: TodayUiState) {
        composeRule.setContent { DayLogTheme { TodayContent(state, TodayCallbacks()) } }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/images/$name.png")
    }

    @Test fun emptyDay() = capture("today_empty", snap())

    @Test fun shortVisitArrival() = capture(
        "today_short_visit",
        snap(
            listOf(
                session(
                    1, WorkMode.BASE, startMin = 480,
                    startSource = TimeSource.GEOFENCE, startUncertain = true,
                ),
            ),
        ),
    )

    /** The whole v2.0 point: three stretches of work, each with its own budget. */
    @Test fun fullDay() = capture(
        "today_full",
        snap(
            listOf(
                session(
                    1, WorkMode.BASE, startMin = 480, endMin = 840,
                    activities = listOf(
                        ActivityEntry("רובוטיקה", "התקנה", 150, "חיווט לוח, תא 4"),
                        ActivityEntry("רובוטיקה", "דיון", 60, "סקירת ליקויים"),
                    ),
                ),
                session(
                    2, WorkMode.FIELD, startMin = 900, endMin = 1065, title = "תחנת משנה אקמה",
                    activities = listOf(ActivityEntry("AI למחלקה", "בדיקות", 120, "בדיקות קבלה")),
                ),
                session(
                    3, WorkMode.HOME, startMin = 1140, endMin = 1260,
                    activities = listOf(ActivityEntry("הנדסת מערכת למחלקה", "תיעוד")),
                ),
            ),
            notes = "הוזמן CT רזרבי",
        ),
    )

    /** Activities claiming more time than was worked — the red "there is a problem" state. */
    @Test fun overAllocatedDay() = capture(
        "today_over_allocated",
        snap(
            listOf(
                session(
                    1, WorkMode.BASE, startMin = 600, endMin = 840,
                    activities = listOf(
                        ActivityEntry("רובוטיקה", "פיתוח", 240),
                        ActivityEntry("AI למחלקה", "בדיקות", 120),
                    ),
                ),
            ),
        ),
    )

    /** Every worked hour described: the budget bar is full and green. */
    @Test fun balancedDay() = capture(
        "today_balanced",
        snap(
            listOf(
                session(
                    1, WorkMode.BASE, startMin = 600, endMin = 840,
                    activities = listOf(
                        ActivityEntry("רובוטיקה", "פיתוח", 150),
                        ActivityEntry("AI למחלקה", "בדיקות", 90),
                    ),
                ),
            ),
        ),
    )

    @Test fun dayOff() = capture("today_off", snap(dayType = DayType.OFF))

    @Test fun reportedDay() = capture(
        "today_reported",
        snap(listOf(session(1, WorkMode.BASE, startMin = 480, endMin = 1065)), reported = true),
    )
}
