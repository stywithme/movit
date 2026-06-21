package com.movit.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic tokens from [app.css] that extend Material 3 [androidx.compose.material3.ColorScheme].
 * Features must use [MaterialTheme.movitColors] — never raw hex in feature modules.
 */
@Immutable
data class MovitExtendedColors(
    val textSecondary: Color,
    val textTertiary: Color,
    val textQuaternary: Color,
    val surface2: Color,
    val elevated: Color,
    val divider: Color,
    val stroke: Color,
    val primaryTint: Color,
    val primaryPress: Color,
    val limeDeep: Color,
    val limeTint: Color,
    val coralTint: Color,
    val success: Color,
    val successTint: Color,
    val warning: Color,
    val warningTint: Color,
    val gold: Color,
    val ink: Color,
    val onInk: Color,
    val inkVeil05: Color,
    val inkVeil72: Color,
    val inkVeil78: Color,
    val onInkVeil16: Color,
    val onInkVeil18: Color,
    val onInkVeil22: Color,
    val onInkVeil55: Color,
    val onInkVeil70: Color,
    val onInkVeil88: Color,
    val onInkVeil90: Color,
    val shadow: Color,
    val shadowSm: Color,
)

val LocalMovitExtendedColors = staticCompositionLocalOf { MovitLightExtendedColors }

val MaterialTheme.movitColors: MovitExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalMovitExtendedColors.current

internal val MovitLightExtendedColors = MovitExtendedColors(
    textSecondary = Color(0xFF3E5A62),
    textTertiary = Color(0xFF6A8A92),
    textQuaternary = Color(0xFF96AFBA),
    surface2 = Color(0xFFEDF5F8),
    elevated = Color(0xFFFFFFFF),
    divider = Color(0xFFE2EDF0),
    stroke = Color(0xFFD8DCDF),
    primaryTint = MovitPalette.Aqua.copy(alpha = 0.18f),
    primaryPress = Color(0xFF6BBAD0),
    limeDeep = Color(0xFFAABF6A),
    limeTint = MovitPalette.Lime.copy(alpha = 0.32f),
    coralTint = MovitPalette.Coral.copy(alpha = 0.14f),
    success = Color(0xFFAABF6A),
    successTint = MovitPalette.Lime.copy(alpha = 0.32f),
    warning = MovitPalette.Coral,
    warningTint = MovitPalette.Coral.copy(alpha = 0.14f),
    gold = Color(0xFFAABF6A),
    ink = MovitPalette.Ink,
    onInk = Color(0xFFFFFFFF),
    inkVeil05 = MovitPalette.Ink.copy(alpha = 0.05f),
    inkVeil72 = MovitPalette.Ink.copy(alpha = 0.72f),
    inkVeil78 = MovitPalette.Ink.copy(alpha = 0.78f),
    onInkVeil16 = Color.White.copy(alpha = 0.16f),
    onInkVeil18 = Color.White.copy(alpha = 0.18f),
    onInkVeil22 = Color.White.copy(alpha = 0.22f),
    onInkVeil55 = Color.White.copy(alpha = 0.55f),
    onInkVeil70 = Color.White.copy(alpha = 0.70f),
    onInkVeil88 = Color.White.copy(alpha = 0.88f),
    onInkVeil90 = Color.White.copy(alpha = 0.90f),
    shadow = Color(0xFF1E465A).copy(alpha = 0.18f),
    shadowSm = Color(0xFF1E465A).copy(alpha = 0.16f),
)

internal val MovitDarkExtendedColors = MovitExtendedColors(
    textSecondary = Color(0xFFBDD8DF),
    textTertiary = Color(0xFF7AAAB5),
    textQuaternary = Color(0xFF4E7A85),
    surface2 = Color(0xFF243438),
    elevated = Color(0xFF2A3C42),
    divider = Color(0xFF263238),
    stroke = Color(0xFF384C52),
    primaryTint = MovitPalette.Aqua.copy(alpha = 0.20f),
    primaryPress = Color(0xFFA8DAEB),
    limeDeep = Color(0xFFB0C270),
    limeTint = MovitPalette.Lime.copy(alpha = 0.18f),
    coralTint = MovitPalette.Coral.copy(alpha = 0.20f),
    success = MovitPalette.Lime,
    successTint = MovitPalette.Lime.copy(alpha = 0.18f),
    warning = MovitPalette.Coral,
    warningTint = MovitPalette.Coral.copy(alpha = 0.20f),
    gold = MovitPalette.Lime,
    ink = Color(0xFF0E1618),
    onInk = Color(0xFFF0F8FA),
    inkVeil05 = Color(0xFF0E1618).copy(alpha = 0.05f),
    inkVeil72 = Color(0xFF0E1618).copy(alpha = 0.72f),
    inkVeil78 = Color(0xFF0E1618).copy(alpha = 0.78f),
    onInkVeil16 = Color(0xFFF0F8FA).copy(alpha = 0.16f),
    onInkVeil18 = Color(0xFFF0F8FA).copy(alpha = 0.18f),
    onInkVeil22 = Color(0xFFF0F8FA).copy(alpha = 0.22f),
    onInkVeil55 = Color(0xFFF0F8FA).copy(alpha = 0.55f),
    onInkVeil70 = Color(0xFFF0F8FA).copy(alpha = 0.70f),
    onInkVeil88 = Color(0xFFF0F8FA).copy(alpha = 0.88f),
    onInkVeil90 = Color(0xFFF0F8FA).copy(alpha = 0.90f),
    shadow = Color.Black.copy(alpha = 0.65f),
    shadowSm = Color.Black.copy(alpha = 0.58f),
)
