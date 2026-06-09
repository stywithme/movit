package com.movit.designsystem.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.MovitTheme
import com.movit.designsystem.MovitThemeMode
import com.movit.designsystem.components.MovitScaffold
import com.movit.designsystem.movitColors

/**
 * Components tab content — same header shell as main app tabs.
 */
@Composable
fun MovitComponentsTabScreen(
    modifier: Modifier = Modifier,
    initialThemeMode: MovitThemeMode = MovitThemeMode.System,
) {
    var themeMode by remember { mutableStateOf(initialThemeMode) }

    MovitTheme(themeMode = themeMode) {
        MovitScaffold(
            modifier = modifier,
            title = "Components",
            subtitle = "Design system catalog for visual QA.",
            showNotification = false,
            actions = {
                ThemeToggleChips(
                    themeMode = themeMode,
                    onThemeModeChange = { themeMode = it },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MovitSpacing.lg)
                    .padding(bottom = MovitSpacing.lg),
            ) {
                Text(
                    text = "مرجع بصري لكل مكوّنات Movit قبل استخدامها في الصفحات.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.movitColors.textSecondary,
                    modifier = Modifier.padding(bottom = MovitSpacing.xs),
                )
                Text(
                    text = "Review colors, buttons, cards and premium patterns here before page integration.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.movitColors.textTertiary,
                    fontWeight = FontWeight.W500,
                    modifier = Modifier.padding(bottom = MovitSpacing.md),
                )

                MovitComponentsCatalogContent()
            }
        }
    }
}

/** @see MovitComponentsTabScreen */
@Composable
fun MovitComponentsScreen(
    modifier: Modifier = Modifier,
    initialThemeMode: MovitThemeMode = MovitThemeMode.System,
) {
    MovitComponentsTabScreen(
        modifier = modifier,
        initialThemeMode = initialThemeMode,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeToggleChips(
    themeMode: MovitThemeMode,
    onThemeModeChange: (MovitThemeMode) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.xs)) {
        MovitThemeMode.entries.forEach { mode ->
            FilterChip(
                selected = themeMode == mode,
                onClick = { onThemeModeChange(mode) },
                label = {
                    Text(
                        text = mode.name,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}

/** All catalog sections in prototype order. */
@Composable
fun MovitComponentsCatalogContent() {
    MovitCatalogPaletteBlock()
    MovitCatalogTypographyBlock()
    MovitCatalogButtonsBlock()
    MovitCatalogCardsBlock()
    MovitCatalogMetricsBlock()
    MovitCatalogSearchBlock()
    MovitCatalogFilterRowBlock()
    MovitCatalogMediaCardsBlock()
    MovitCatalogExerciseCardsBlock()
    MovitCatalogStatesBlock()
    MovitCatalogPlaceholderBlock()
    MovitCatalogRtlBlock()
    MovitCatalogFilterChipsBlock()
    MovitCatalogEmptyStateBlock()
    MovitCatalogExtendedTokensSection()
    MovitCatalogButtonsExtendedSection()
    MovitCatalogIconBoxSection()
    MovitCatalogTagsSection()
    MovitCatalogSegmentedSection()
    MovitCatalogStatCardsSection()
    MovitCatalogChartsSection()
    MovitCatalogListRowsSection()
    MovitCatalogFloatingNavPreview()
    MovitCatalogFloatingControlsSection()
    MovitCatalogHeroAccentSection()
    MovitCatalogWeekSessionSection()
    MovitCatalogFeedbackSection()
    MovitCatalogPremiumSection()
    MovitCatalogSkeletonSection()
}
