package com.strengthlogz.mobile.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Graphite Neutral Dark Ramp & Calibrated Ember Accent
val BgDark = Color(0xFF141415)
val SurfaceDark = Color(0xFF0F0F10)
val SurfaceCard = Color(0xFF19191B)
val SurfaceCardElevated = Color(0xFF232325)
val SurfaceHighlight = Color(0xFF232325)
val BorderDark = Color(0x14EDEDED)
val BorderSubtle = Color(0x0FEDEDED)

// Text Tokens (3 neutral roles)
val TextPrimary = Color(0xFFEDEDED)
val TextSecondary = Color(0xFFA1A1A6)
val TextMuted = Color(0xFF85858B)

// Brand Accent & Toned Semantic Colors
val AccentOrange = Color(0xFFFF7A45)
val AccentOrangeDark = Color(0xFFE5581F)
val AccentOrangeGlow = Color(0x1FFF7A45)
val AccentPeach = Color(0xFFD4D4D8)
val IncreaseGreen = Color(0xFF4CC38A)
val IncreaseGreenDark = Color(0xFF2E9E6B)
val IncreaseGreenGlow = Color(0x1F4CC38A)
val DecreaseRed = Color(0xFFE5484D)
val DecreaseRedGlow = Color(0x1FE5484D)
val GoldPR = Color(0xFFFFD166)
val GoldPRGlow = Color(0x28FFD166)
val BlueAccent = Color(0xFFA1A1A6)
val PurpleAccent = Color(0xFFA1A1A6)

// Single-tone Brushes (no hue-to-hue gradients)
val OrangeGradient = Brush.horizontalGradient(
    listOf(AccentOrange, AccentOrange)
)

val GoldGradient = Brush.horizontalGradient(
    listOf(GoldPR, GoldPR)
)

val PrCardGradient = Brush.verticalGradient(
    listOf(SurfaceCard, SurfaceCard)
)

val HeroHeaderGradient = Brush.verticalGradient(
    listOf(SurfaceDark, SurfaceDark)
)

val CardSurfaceGradient = Brush.verticalGradient(
    listOf(SurfaceCardElevated, SurfaceCard)
)

val StrengthDarkColorScheme = darkColorScheme(
    primary = AccentOrange,
    onPrimary = Color(0xFF140D07),
    secondary = AccentPeach,
    onSecondary = Color(0xFF141415),
    background = BgDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceHighlight,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = BorderDark,
    outlineVariant = BorderSubtle
)
