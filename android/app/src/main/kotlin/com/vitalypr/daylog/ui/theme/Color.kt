package com.vitalypr.daylog.ui.theme

import androidx.compose.ui.graphics.Color

// Design tokens per docs/dev/ui-guidelines.md — the ONLY place colors are defined.

val Petrol = Color(0xFF0B6E6A)
val PetrolDeep = Color(0xFF085250)
val PetrolTint = Color(0xFFE4F1F0)

val SendGreen = Color(0xFF1BA65B) // send/sent semantics ONLY
val SendGreenDark = Color(0xFF178A4C)
val SendGreenTint = Color(0xFFE6F5EC)

val Amber = Color(0xFFA9770F) // "logged, not sent" status
val AmberTint = Color(0xFFFBF3DF)

// A real contradiction in the data the user must fix — today only the
// over-allocated time budget (activities claiming more time than was worked).
// Distinct from Amber, which means "unconfirmed / not sent yet", not "wrong".
val Warn = Color(0xFFB3261E)
val WarnTint = Color(0xFFFCEDEC)

val Ink = Color(0xFF1B2733)
val InkSecondary = Color(0xFF5A6B77)
val InkMuted = Color(0xFF8A99A4)

val Ground = Color(0xFFF7F9FA)
val Card = Color(0xFFFFFFFF)
val Line = Color(0xFFE2E9EC)

// Chart series — CVD-validated trio (one hue per work mode); never change one
// without re-validating all three. Teal/ochre/indigo stay separable under
// deuteranopia and protanopia by hue AND lightness, and each clears 3:1 on the
// card surface. Exact values are always available as text (KPI tiles, tooltip).
val ChartOffice = Color(0xFF00897B) // בסיס
val ChartField = Color(0xFF9E6410) // שטח
val ChartHome = Color(0xFF4054B2) // בית
