package com.movit.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun MovitTheme(
    themeMode: MovitThemeMode = MovitThemeMode.System,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        MovitThemeMode.Light -> false
        MovitThemeMode.Dark -> true
        MovitThemeMode.System -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (darkTheme) MovitDarkColorScheme else MovitLightColorScheme,
        typography = MovitTypography,
        shapes = MovitShapes,
        content = content,
    )
}
