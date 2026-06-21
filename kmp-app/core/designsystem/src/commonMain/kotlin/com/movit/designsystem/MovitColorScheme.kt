package com.movit.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Brand palette primitives — the only place raw hex values are defined.
 * All components must use [ColorScheme] roles via [MovitTheme].
 */
internal object MovitPalette {
    val Aqua = Color(0xFF8ECFE3)
    val Lime = Color(0xFFC4D489)
    val Ink = Color(0xFF282828)
    val Coral = Color(0xFFE76D46)
    /** Destructive/error — redder than Coral accent so errors read clearly. */
    val ErrorRed = Color(0xFFC62828)
    val ErrorRedDark = Color(0xFFEF5350)
    val Mist = Color(0xFFD8DCDF)
    val White = Color(0xFFFFFFFF)

    val LightBackground = Color(0xFFE8EFF2)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFEDF5F8)
    val LightOnSurfaceVariant = Color(0xFF6A8A92)
    val LightOutline = Color(0xFFD6E8EE)
    val LightPrimaryContainer = Color(0xFFEBF6FA)
    val LightSecondaryContainer = Color(0xFFECF1D9)
    val LightTertiaryContainer = Color(0xFFFCEEE9)
    val LightErrorContainer = Color(0xFFFDECEA)

    val DarkBackground = Color(0xFF121617)
    val DarkSurface = Color(0xFF1E2C30)
    val DarkSurfaceVariant = Color(0xFF243438)
    val DarkOnBackground = Color(0xFFF0F8FA)
    val DarkOnSurfaceVariant = Color(0xFF7AAAB5)
    val DarkOutline = Color(0xFF2E3E44)
    val DarkPrimaryContainer = Color(0xFF1E3A42)
    val DarkSecondaryContainer = Color(0xFF2A3420)
    val DarkTertiaryContainer = Color(0xFF3D2A24)
    val DarkErrorContainer = Color(0xFF4A1C1C)
}

val MovitLightColorScheme: ColorScheme = lightColorScheme(
    primary = MovitPalette.Aqua,
    onPrimary = MovitPalette.Ink,
    primaryContainer = MovitPalette.LightPrimaryContainer,
    onPrimaryContainer = MovitPalette.Ink,
    secondary = MovitPalette.Lime,
    onSecondary = MovitPalette.Ink,
    secondaryContainer = MovitPalette.LightSecondaryContainer,
    onSecondaryContainer = MovitPalette.Ink,
    tertiary = MovitPalette.Coral,
    onTertiary = MovitPalette.White,
    tertiaryContainer = MovitPalette.LightTertiaryContainer,
    onTertiaryContainer = MovitPalette.Ink,
    background = MovitPalette.LightBackground,
    onBackground = MovitPalette.Ink,
    surface = MovitPalette.LightSurface,
    onSurface = MovitPalette.Ink,
    surfaceVariant = MovitPalette.LightSurfaceVariant,
    onSurfaceVariant = MovitPalette.LightOnSurfaceVariant,
    outline = MovitPalette.LightOutline,
    error = MovitPalette.ErrorRed,
    onError = MovitPalette.White,
    errorContainer = MovitPalette.LightErrorContainer,
    onErrorContainer = MovitPalette.ErrorRed,
)

val MovitDarkColorScheme: ColorScheme = darkColorScheme(
    primary = MovitPalette.Aqua,
    onPrimary = MovitPalette.Ink,
    primaryContainer = MovitPalette.DarkPrimaryContainer,
    onPrimaryContainer = MovitPalette.DarkOnBackground,
    secondary = MovitPalette.Lime,
    onSecondary = MovitPalette.Ink,
    secondaryContainer = MovitPalette.DarkSecondaryContainer,
    onSecondaryContainer = MovitPalette.DarkOnBackground,
    tertiary = MovitPalette.Coral,
    onTertiary = MovitPalette.White,
    tertiaryContainer = MovitPalette.DarkTertiaryContainer,
    onTertiaryContainer = MovitPalette.DarkOnBackground,
    background = MovitPalette.DarkBackground,
    onBackground = MovitPalette.DarkOnBackground,
    surface = MovitPalette.DarkSurface,
    onSurface = MovitPalette.DarkOnBackground,
    surfaceVariant = MovitPalette.DarkSurfaceVariant,
    onSurfaceVariant = MovitPalette.DarkOnSurfaceVariant,
    outline = MovitPalette.DarkOutline,
    error = MovitPalette.ErrorRedDark,
    onError = MovitPalette.Ink,
    errorContainer = MovitPalette.DarkErrorContainer,
    onErrorContainer = MovitPalette.ErrorRedDark,
)
