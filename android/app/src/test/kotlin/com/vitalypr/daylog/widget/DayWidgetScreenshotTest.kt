package com.vitalypr.daylog.widget

import android.content.Context
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.vitalypr.daylog.domain.model.DayType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Visual regression for the home-screen widget. The RemoteViews tree is inflated
 * and laid out at the real 4x1 footprint (250dp x 40dp, the declared minimum) so
 * clipping at the smallest supported size is caught.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "he-rIL-ldrtl-w412dp-h915dp-xxxhdpi", sdk = [34])
class DayWidgetScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun capture(name: String, state: WidgetState) {
        val activity = composeRule.activity
        val view = DayWidgetRenderer.render(context, state).apply(activity, FrameLayout(activity))
        val density = context.resources.displayMetrics.density
        activity.setContentView(
            view,
            FrameLayout.LayoutParams((250 * density).toInt(), (40 * density).toInt()),
        )
        composeRule.waitForIdle()
        view.captureRoboImage("src/test/snapshots/images/$name.png")
    }

    @Test fun nothingLoggedYet() = capture("widget_empty", WidgetState())

    @Test fun arrivalLogged() = capture("widget_arrival", WidgetState(arrivalMin = 8 * 60 + 12))

    @Test fun bothLogged() =
        capture("widget_full", WidgetState(arrivalMin = 8 * 60 + 12, departureMin = 17 * 60 + 35))

    @Test fun dayOff() = capture("widget_off", WidgetState(specialDay = DayType.OFF))
}
