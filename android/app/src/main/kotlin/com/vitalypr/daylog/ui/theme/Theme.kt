package com.vitalypr.daylog.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Petrol,
    onPrimary = Card,
    primaryContainer = PetrolTint,
    onPrimaryContainer = PetrolDeep,
    secondary = InkSecondary,
    background = Ground,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Ground,
    onSurfaceVariant = InkSecondary,
    outline = Line,
    error = Amber,
    errorContainer = AmberTint,
)

/** App is a committed light design (approved mockup); dark theme is a future decision. */
@Composable
fun DayLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
