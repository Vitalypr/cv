package com.vitalypr.daylog.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.vitalypr.daylog.ui.theme.DayLogTheme
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The keyboard used to cover the field being typed into.
 *
 * Root cause: the app draws edge to edge, so the window no longer resizes for
 * the IME. Nothing consumed the IME inset, so every screen's scroll viewport
 * still ran to the bottom of the window — *behind* the keyboard. Compose duly
 * "scrolled the focused field into view", into a part of the viewport the user
 * could not see, and the field stayed hidden.
 *
 * These tests replay that sequence: a field low on a scrolling screen, focus,
 * and a keyboard opening over it — in both orders, because the insets arrive
 * after focus on a real device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "he-rIL-ldrtl-w412dp-h915dp-xxxhdpi", sdk = [34])
class ImeInsetsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val keyboardPx = 700

    /** What the platform sends when the keyboard opens. */
    private fun showKeyboard() {
        composeRule.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboardPx))
                .build()
            // Compose listens on its own view, so hand the insets to every view
            // on the way down rather than to the decor alone.
            views().forEach { ViewCompat.dispatchApplyWindowInsets(it, insets) }
        }
        composeRule.waitForIdle()
    }

    private fun views(): List<android.view.View> = buildList {
        fun walk(v: android.view.View) {
            add(v)
            if (v is android.view.ViewGroup) (0 until v.childCount).forEach { walk(v.getChildAt(it)) }
        }
        walk(composeRule.activity.window.decorView)
    }

    /** A screen like the real ones: scrolling content with a field near the bottom. */
    private fun screen(spacerHeight: Int = 880) {
        composeRule.setContent {
            DayLogTheme {
                DayLogScaffold(bottomBar = { Text("nav") }) { padding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .testTag("content"),
                    ) {
                        Spacer(Modifier.height(spacerHeight.dp))
                        Field("detail")
                    }
                }
            }
        }
    }

    @Composable
    private fun Field(tag: String) {
        var text by remember { mutableStateOf("") }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            modifier = Modifier
                .height(28.dp)
                .testTag(tag),
        )
    }

    private fun focusField() {
        composeRule.onNodeWithTag("detail").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
    }

    private fun keyboardTop(): Float = composeRule.activity.window.decorView.height - keyboardPx.toFloat()

    private fun fieldBottom(): Float =
        composeRule.onNodeWithTag("detail").fetchSemanticsNode().boundsInWindow.bottom

    @Test fun `the content area ends above the keyboard, not behind it`() {
        screen()
        val fullHeight = composeRule.onNodeWithTag("content").fetchSemanticsNode().size.height

        showKeyboard()

        val withKeyboard = composeRule.onNodeWithTag("content").fetchSemanticsNode().size.height
        assertTrue(
            withKeyboard <= fullHeight - keyboardPx,
            "content kept its full height ($withKeyboard of $fullHeight) — the IME inset is not consumed",
        )
    }

    /** The reported bug: typing into a field low on the screen, keyboard over it. */
    @Test fun `a field focused before the keyboard opens is lifted clear of it`() {
        screen()
        focusField()
        composeRule.onNodeWithTag("detail").assertIsFocused()

        showKeyboard()

        assertTrue(
            fieldBottom() <= keyboardTop(),
            "the field ends at ${fieldBottom()}, under a keyboard that starts at ${keyboardTop()}",
        )
    }

    /** And the other order: the keyboard is already up when the field takes focus. */
    @Test fun `a field focused with the keyboard already up is scrolled into view`() {
        screen()
        showKeyboard()
        focusField()

        assertTrue(
            fieldBottom() <= keyboardTop(),
            "the field ends at ${fieldBottom()}, under a keyboard that starts at ${keyboardTop()}",
        )
    }

    /** A field far down a long day — the activity detail row on a busy screen. */
    @Test fun `a field deep in a long day is scrolled up to the keyboard line`() {
        screen(spacerHeight = 2000)
        showKeyboard()
        focusField()

        assertTrue(
            fieldBottom() <= keyboardTop(),
            "the field ends at ${fieldBottom()}, under a keyboard that starts at ${keyboardTop()}",
        )
    }
}
