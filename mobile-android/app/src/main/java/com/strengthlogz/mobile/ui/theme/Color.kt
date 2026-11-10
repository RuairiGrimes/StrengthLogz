package com.strengthlogz.mobile.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Obsidian & Slate Deep Dark Theme Palette
val BgDark = Color(0xFF0C0E12)
val SurfaceDark = Color(0xFF13161D)
val SurfaceCard = Color(0xFF181C26)
val SurfaceCardElevated = Color(0xFF1F2432)
val SurfaceHighlight = Color(0xFF262C3D)
val BorderDark = Color(0xFF282E3D)
val BorderSubtle = Color(0xFF1C212D)

// Text Tokens
val TextPrimary = Color(0xFFF1F3F8)
val TextSecondary = Color(0xFF949EB2)
val TextMuted = Color(0xFF646E82)

// Accent Colors
val AccentOrange = Color(0xFFFF7A45)
val AccentOrangeDark = Color(0xFFE5581F)
val AccentOrangeGlow = Color(0x33FF7A45)
val AccentPeach = Color(0xFFFF9A6B)
val IncreaseGreen = Color(0xFF34D399)
val IncreaseGreenDark = Color(0xFF059669)
val IncreaseGreenGlow = Color(0x2E34D399)
val DecreaseRed = Color(0xFFF87171)
val DecreaseRedGlow = Color(0x2EF87171)
val GoldPR = Color(0xFFFFD166)
val GoldPRGlow = Color(0x40FFD166)
val BlueAccent = Color(0xFF38BDF8)
val PurpleAccent = Color(0xFFA78BFA)

// Visual Gradients
val OrangeGradient = Brush.horizontalGradient(
    listOf(AccentOrange, AccentPeach)
)

val GoldGradient = Brush.horizontalGradient(
    listOf(GoldPR, AccentPeach)
)

val PrCardGradient = Brush.linearGradient(
    listOf(Color(0xFF241E10), SurfaceCard)
)

val HeroHeaderGradient = Brush.verticalGradient(
    listOf(Color(0xFF221711), SurfaceDark)
)

val CardSurfaceGradient = Brush.verticalGradient(
    listOf(SurfaceCardElevated, SurfaceCard)
)

val StrengthDarkColorScheme = darkColorScheme(
    primary = AccentOrange,
    onPrimary = Color(0xFF1A0D06),
    secondary = AccentPeach,
    onSecondary = Color(0xFF1A0D06),
    background = BgDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceHighlight,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = BorderDark,
    outlineVariant = BorderSubtle
)
