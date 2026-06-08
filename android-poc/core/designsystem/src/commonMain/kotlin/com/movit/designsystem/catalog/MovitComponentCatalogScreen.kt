package com.movit.designsystem.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitPalette
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.MovitTheme
import com.movit.designsystem.MovitThemeMode
import com.movit.designsystem.components.MovitActionTile
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitEmptyState
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitExerciseCard
import com.movit.designsystem.components.MovitFilterChip
import com.movit.designsystem.components.MovitFilterRow
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitMediaCard
import com.movit.designsystem.components.MovitMetricTile
import com.movit.designsystem.components.MovitPlaceholderState
import com.movit.designsystem.components.MovitProgressBar
import com.movit.designsystem.components.MovitStatTileData
import com.movit.designsystem.components.MovitStatTileRow
import com.movit.designsystem.components.MovitSearchBar
import com.movit.designsystem.components.MovitSectionHeader
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovitComponentCatalogScreen(
    modifier: Modifier = Modifier,
    initialThemeMode: MovitThemeMode = MovitThemeMode.System,
) {
    var themeMode by remember { mutableStateOf(initialThemeMode) }

    MovitTheme(themeMode = themeMode) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Movit Design System",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(MovitSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(MovitSpacing.xl),
            ) {
                ThemeToggleSection(
                    themeMode = themeMode,
                    onThemeModeChange = { themeMode = it },
                )
                PaletteSection()
                TypographySection()
                ButtonsSection()
                CardsSection()
                MetricsSection()
                DashboardComponentsSection()
                SearchBarSection()
                FilterRowSection()
                MediaCardsSection()
                ExerciseCardsSection()
                StatesSection()
                PlaceholderStateSection()
                RtlSampleSection()
                FilterChipsSection()
                EmptyStateSection()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeToggleSection(
    themeMode: MovitThemeMode,
    onThemeModeChange: (MovitThemeMode) -> Unit,
) {
    CatalogSection(title = "Theme", subtitle = "Toggle light / dark preview") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
            MovitThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    label = { Text(mode.name) },
                )
            }
        }
    }
}

@Composable
private fun PaletteSection() {
    CatalogSection(title = "Palette", subtitle = "Brand primitives mapped to Material 3 roles") {
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
private fun TypographySection() {
    CatalogSection(title = "Typography", subtitle = "Display · Title · Body · Label") {
        Text("Display Large", style = MaterialTheme.typography.displayLarge)
        Text("Title Large", style = MaterialTheme.typography.titleLarge)
        Text("Body Medium", style = MaterialTheme.typography.bodyMedium)
        Text("Label Medium", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ButtonsSection() {
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
private fun CardsSection() {
    CatalogSection(title = "Cards") {
        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.md)) {
            MovitCard(variant = MovitCardVariant.Filled) {
                Text("Filled card", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Surface container with large radius.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MovitCard(variant = MovitCardVariant.Outlined) {
                Text("Outlined card", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Outline border from theme tokens.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricsSection() {
    CatalogSection(title = "Metric tiles") {
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
    }
}

@Composable
private fun DashboardComponentsSection() {
    CatalogSection(title = "Dashboard components") {
        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.md)) {
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
private fun FilterChipsSection() {
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
private fun SearchBarSection() {
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
private fun FilterRowSection() {
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
private fun MediaCardsSection() {
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
private fun ExerciseCardsSection() {
    CatalogSection(title = "Exercise cards") {
        MovitExerciseCard(
            title = "Bodyweight Squat",
            subtitle = "Quads, glutes and core control.",
            badge = "Popular",
            metadata = listOf("Legs", "Beginner"),
            onClick = {},
        )
    }
}

@Composable
private fun StatesSection() {
    CatalogSection(title = "Loading / error") {
        Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.md)) {
            MovitCard(variant = MovitCardVariant.Filled) {
                MovitLoadingState()
            }
            MovitCard(variant = MovitCardVariant.Filled) {
                MovitErrorState(
                    message = "Unable to load library.",
                    onRetry = {},
                )
            }
        }
    }
}

@Composable
private fun PlaceholderStateSection() {
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
private fun RtlSampleSection() {
    CatalogSection(title = "RTL sample", subtitle = "Arabic layout preview") {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            MovitCard(variant = MovitCardVariant.Filled) {
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
private fun EmptyStateSection() {
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
private fun CatalogSection(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.md)) {
        MovitSectionHeader(title = title, subtitle = subtitle)
        content()
    }
}
