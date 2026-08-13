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

    /** Rendered on a dark ground, as it sits on a home screen. [heightDp] 40 = the
     *  declared minimum, where clipping would first show. */
    private fun capture(name: String, state: WidgetState, heightDp: Int = 72) {
        val activity = composeRule.activity
        val density = context.resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).toInt()

        val host = FrameLayout(activity).apply {
            setBackgroundColor(HOME_SCREEN_GROUND)
            setPadding(px(8), px(8), px(8), px(8))
        }
        val view = DayWidgetRenderer.render(context, state, heightDp).apply(activity, host)
        host.addView(view, FrameLayout.LayoutParams(px(250), px(heightDp)))

        activity.setContentView(
            host,
            FrameLayout.LayoutParams(px(250 + 16), px(heightDp + 16)),
        )
        composeRule.waitForIdle()
        host.captureRoboImage("src/test/snapshots/images/$name.png")
    }

    private companion object {
        const val HOME_SCREEN_GROUND = 0xFF0D1117.toInt()
    }

    @Test fun nothingLoggedYet() = capture("widget_empty", WidgetState())

    @Test fun arrivalLogged() = capture("widget_arrival", WidgetState(arrivalMin = 8 * 60 + 12))

    @Test fun bothLogged() =
        capture("widget_full", WidgetState(arrivalMin = 8 * 60 + 12, departureMin = 17 * 60 + 35))

    @Test fun dayOff() = capture("widget_off", WidgetState(specialDay = DayType.OFF))

    /** Declared 40dp floor — label + time stack must still fit without clipping. */
    @Test fun minimumHeight() = capture(
        "widget_min_height",
        WidgetState(arrivalMin = 8 * 60 + 12),
        heightDp = 40,
    )
}
