package com.movit.designsystem.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitThemeMode

/**
 * Legacy entry point — delegates to [MovitComponentsScreen] with full app frame.
 */
@Composable
fun MovitComponentCatalogScreen(
    modifier: Modifier = Modifier,
    initialThemeMode: MovitThemeMode = MovitThemeMode.System,
) {
    MovitComponentsTabScreen(
        modifier = modifier,
        initialThemeMode = initialThemeMode,
    )
}
