package com.strengthlogz.mobile.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Graphite-Iron Dark Theme Palette
val BgDark = Color(0xFF0E0F12)
val SurfaceDark = Color(0xFF14161A)
val SurfaceCard = Color(0xFF1A1C22)
val SurfaceCardElevated = Color(0xFF22252D)
val SurfaceHighlight = Color(0xFF282C35)
val BorderDark = Color(0xFF2A2E37)
val BorderSubtle = Color(0xFF1F222A)

// Text Tokens
val TextPrimary = Color(0xFFEDEDEF)
val TextSecondary = Color(0xFFA0A4AE)
val TextMuted = Color(0xFF727782)

// Accent Colors
val AccentOrange = Color(0xFFFF7A45)
val AccentOrangeDark = Color(0xFFE5581F)
val AccentOrangeGlow = Color(0x29FF7A45)
val AccentPeach = Color(0xFFFF9A6B)
val IncreaseGreen = Color(0xFF4CC38A)
val IncreaseGreenDark = Color(0xFF16A34A)
val IncreaseGreenGlow = Color(0x264CC38A)
val DecreaseRed = Color(0xFFE5484D)
val DecreaseRedGlow = Color(0x26E5484D)
val GoldPR = Color(0xFFFFD166)
val GoldPRGlow = Color(0x33FFD166)
val BlueAccent = Color(0xFF60A5FA)
val PurpleAccent = Color(0xFFA78BFA)

// Subtle Tonal Surfaces (compatibility tokens kept for callers)
val OrangeGradient = Brush.horizontalGradient(
    listOf(AccentOrange, AccentOrange)
)

val GoldGradient = Brush.horizontalGradient(
    listOf(GoldPR, GoldPR)
)

val PrCardGradient = Brush.verticalGradient(
    listOf(SurfaceCardElevated, SurfaceCard)
)

val HeroHeaderGradient = Brush.verticalGradient(
    listOf(SurfaceDark, BgDark)
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
