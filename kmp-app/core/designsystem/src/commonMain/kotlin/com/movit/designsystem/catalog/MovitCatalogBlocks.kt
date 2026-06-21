package com.movit.designsystem.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import com.movit.designsystem.MovitPalette
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitActionTile
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitEmptyState
import com.movit.designsystem.components.MovitErrorState
import com.movit.resources.movitText
import com.movit.designsystem.components.MovitExerciseCard
import com.movit.designsystem.components.MovitFilterChip
import com.movit.designsystem.components.MovitFilterRow
import com.movit.designsystem.components.MovitFloatingNavBar
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitMediaCard
import com.movit.designsystem.components.MovitMetricTile
import com.movit.designsystem.components.MovitNavDestination
import com.movit.designsystem.components.MovitPlaceholderState
import com.movit.designsystem.components.MovitProgressBar
import com.movit.designsystem.components.MovitSearchBar
import com.movit.designsystem.components.MovitStatTileData
import com.movit.designsystem.components.MovitStatTileRow

@Composable
internal fun MovitCatalogPaletteBlock() {
    CatalogSection(
        eyebrow = "Foundations",
        title = "Palette",
        subtitle = "Brand primitives mapped to Material 3 roles",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        ) {
            PaletteSwatch("Primary", MaterialTheme.colorScheme.primary)
            PaletteSwatch("Secondary", MaterialTheme.colorScheme.secondary)
            PaletteSwatch("Tertiary", MaterialTheme.colorScheme.tertiary)
            PaletteSwatch("Surface", MaterialTheme.colorScheme.surface)
        }
        Spacer(modifier = Modifier.height(MovitSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        ) {
            PaletteSwatch("Aqua", MovitPalette.Aqua)
            PaletteSwatch("Lime", MovitPalette.Lime)
            PaletteSwatch("Ink", MovitPalette.Ink)
            PaletteSwatch("Coral", MovitPalette.Coral)
        }
    }
}

@Composable
private fun PaletteSwatch(label: String, color: androidx.compose.ui.graphics.Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(color, MaterialTheme.shapes.medium),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun MovitCatalogTypographyBlock() {
    CatalogSection(title = "Typography", subtitle = "Display · Title · Body · Label") {
        Text("Display Large", style = MaterialTheme.typography.displayLarge)
        Text("Title Large", style = MaterialTheme.typography.titleLarge)
        Text("Body Medium", style = MaterialTheme.typography.bodyMedium)
        Text("Label Medium", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun MovitCatalogButtonsBlock() {
    CatalogSection(title = "Buttons") {
        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
            MovitButton(
                text = "Filled Button",
                onClick = {},
                variant = MovitButtonVariant.Filled,
                modifier = Modifier.fillMaxWidth(),
            )
            MovitButton(
                text = "Tonal Button",
                onClick = {},
                variant = MovitButtonVariant.Tonal,
                modifier = Modifier.fillMaxWidth(),
            )
            MovitButton(
                text = "Outlined Button",
                onClick = {},
                variant = MovitButtonVariant.Outlined,
                modifier = Modifier.fillMaxWidth(),
            )
            MovitButton(
                text = "Disabled",
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun MovitCatalogCardsBlock() {
    CatalogSection(title = "Cards") {
        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.md)) {
            MovitCard(variant = MovitCardVariant.Elevated) {
                Text("Elevated card", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Shadow + border from prototype tokens.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MovitCard(variant = MovitCardVariant.Flat) {
                Text("Flat card", style = MaterialTheme.typography.titleMedium)
            }
            MovitCard(variant = MovitCardVariant.Outlined) {
                Text("Outlined card", style = MaterialTheme.typography.titleMedium)
            }
            MovitCard(variant = MovitCardVariant.Filled) {
                Text("Filled card", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
internal fun MovitCatalogMetricsBlock() {
    CatalogSection(title = "Metric tiles & dashboard") {
        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
            ) {
                MovitMetricTile(
                    label = "Reps",
                    value = "12",
                    subtitle = "+2 vs last",
                    modifier = Modifier.weight(1f),
                )
                MovitMetricTile(
                    label = "Score",
                    value = "87%",
                    modifier = Modifier.weight(1f),
                )
            }
            MovitProgressBar(
                progressPercent = 71,
                label = "Weekly completion · 71%",
            )
            MovitStatTileRow(
                stats = listOf(
                    MovitStatTileData(value = "71%", label = "This week"),
                    MovitStatTileData(value = "87%", label = "Form avg"),
                    MovitStatTileData(value = "12", label = "Day streak"),
                ),
            )
            MovitActionTile(
                label = "Train",
                description = "Start or resume training",
                onClick = {},
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MovitCatalogFilterChipsBlock() {
    var selected by remember { mutableStateOf("All") }
    val filters = listOf("All", "Strength", "Mobility", "Recovery")

    CatalogSection(title = "Filter chips") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
            filters.forEach { filter ->
                MovitFilterChip(
                    label = filter,
                    selected = selected == filter,
                    onClick = { selected = filter },
                )
            }
        }
    }
}

@Composable
internal fun MovitCatalogSearchBlock() {
    var query by remember { mutableStateOf("") }
    CatalogSection(title = "Search bar") {
        MovitSearchBar(
            query = query,
            onQueryChange = { query = it },
            placeholder = "Search workouts or exercises…",
        )
    }
}

@Composable
internal fun MovitCatalogFilterRowBlock() {
    var selected by remember { mutableStateOf("All") }
    CatalogSection(title = "Filter row") {
        MovitFilterRow(
            filters = listOf("All", "Exercises", "Workouts", "Programs"),
            selectedFilter = selected,
            onFilterSelected = { selected = it },
        )
    }
}

@Composable
internal fun MovitCatalogMediaCardsBlock() {
    CatalogSection(title = "Media cards") {
        MovitMediaCard(
            title = "Lower Body Strength",
            subtitle = "Quads, glutes and hamstrings in one guided session.",
            badge = "Smart pick",
            metadata = listOf("6 exercises", "~24 min"),
            onClick = {},
        )
    }
}

@Composable
internal fun MovitCatalogExerciseCardsBlock() {
    CatalogSection(title = "Exercise cards") {
        MovitExerciseCard(
            title = "Sample Exercise",
            subtitle = "Guided movement with form cues.",
            badge = "Popular",
            metadata = listOf("Mobility", "Beginner"),
            onClick = {},
        )
    }
}

@Composable
internal fun MovitCatalogStatesBlock() {
    CatalogSection(title = "Loading / error") {
        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.md)) {
            MovitCard(variant = MovitCardVariant.Elevated) {
                MovitLoadingState()
            }
            MovitCard(variant = MovitCardVariant.Elevated) {
                MovitErrorState(
                    title = movitText("common_error_title"),
                    message = "Unable to load library.",
                    actionLabel = movitText("common_retry"),
                    onRetry = {},
                )
            }
        }
    }
}

@Composable
internal fun MovitCatalogPlaceholderBlock() {
    CatalogSection(title = "Placeholder state", subtitle = "Shell tab placeholders") {
        MovitCard(variant = MovitCardVariant.Filled) {
            MovitPlaceholderState(
                title = "Train",
                subtitle = "Your training dashboard will live here.",
                statusLabel = "Coming soon",
                actionLabel = "Notify me",
                onActionClick = null,
            )
        }
    }
}

@Composable
internal fun MovitCatalogRtlBlock() {
    CatalogSection(title = "RTL sample", subtitle = "Arabic layout preview") {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            MovitCard(variant = MovitCardVariant.Elevated) {
                Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
                    Text(
                        text = "استكشف",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "تمارين وجلسات وبرامج في مكان واحد.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MovitFilterRow(
                        filters = listOf("الكل", "تمارين", "جلسات"),
                        selectedFilter = "الكل",
                        onFilterSelected = {},
                    )
                }
            }
        }
    }
}

@Composable
internal fun MovitCatalogEmptyStateBlock() {
    CatalogSection(title = "Empty state") {
        MovitCard(variant = MovitCardVariant.Filled) {
            MovitEmptyState(
                title = "No workouts yet",
                message = "Start your first session to see progress here.",
                actionLabel = "Browse exercises",
                onActionClick = {},
            )
        }
    }
}

@Composable
internal fun MovitCatalogFloatingNavPreview() {
    var selected by remember { mutableStateOf(MovitNavDestination.Train) }
    CatalogSection(
        title = "Floating navigation",
        subtitle = "Ink pill bottom bar — also pinned on this screen",
    ) {
        MovitFloatingNavBar(
            selected = selected,
            onDestinationSelected = { selected = it },
            modifier = Modifier.padding(vertical = MovitSpacing.sm),
        )
    }
}
