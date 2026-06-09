package com.movit.feature.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitBarChart
import com.movit.designsystem.components.MovitBarChartItem
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitEmptyState
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitInsightCard
import com.movit.designsystem.components.MovitInsightVariant
import com.movit.designsystem.components.MovitKpiGrid
import com.movit.designsystem.components.MovitKpiItem
import com.movit.designsystem.components.MovitLineChart
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitScaffold
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.components.MovitSegmentedControl
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@Composable
fun MovitReportsScreen(
    state: MovitReportsUiState,
    onEvent: (MovitReportsEvent) -> Unit,
    modifier: Modifier = Modifier,
    userName: String = movitText("reports_athlete_fallback"),
) {
    val dashboard = state.dashboard
    MovitScaffold(
        modifier = modifier,
        title = movitText("reports_title"),
        subtitle = movitText("reports_subtitle"),
        userName = userName,
        onProfileClick = null,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ReportsTabBar(
                selectedTab = state.selectedTab,
                onTabSelected = { onEvent(MovitReportsEvent.TabSelected(it)) },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(MovitSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
            ) {
                when {
                    state.isLoading && dashboard == null -> {
                        MovitLoadingState(message = movitText("reports_loading"))
                    }
                    state.errorMessage != null && dashboard?.hubState == ReportsHubState.Error -> {
                        MovitErrorState(
                            message = state.errorMessage,
                            onRetry = { onEvent(MovitReportsEvent.RetryClicked) },
                        )
                    }
                    dashboard?.hubState == ReportsHubState.Locked -> {
                        MovitEmptyState(
                            title = movitText("reports_pro_title"),
                            message = movitText("reports_pro_message"),
                            actionLabel = movitText("reports_upgrade"),
                            onActionClick = { onEvent(MovitReportsEvent.UpgradeClicked) },
                        )
                    }
                    dashboard?.hubState == ReportsHubState.Empty -> {
                        MovitEmptyState(
                            title = movitText("reports_empty_title"),
                            message = movitText("reports_empty_message"),
                            actionLabel = movitText("reports_start_training"),
                            onActionClick = { onEvent(MovitReportsEvent.StartTrainingClicked) },
                        )
                    }
                    dashboard?.hubState == ReportsHubState.Success -> {
                        when (state.selectedTab) {
                            ReportsTab.Overview -> ReportsOverviewPanel(dashboard)
                            ReportsTab.Exercises -> ReportsExercisesPanel(dashboard, onEvent)
                            ReportsTab.Trends -> ReportsTrendsPanel(dashboard)
                        }
                    }
                    else -> {
                        MovitLoadingState(message = movitText("reports_loading"))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportsTabBar(
    selectedTab: ReportsTab,
    onTabSelected: (ReportsTab) -> Unit,
) {
    val tabs = listOf(ReportsTab.Overview, ReportsTab.Exercises, ReportsTab.Trends)
    val labels = listOf(
        movitText("reports_tab_overview"),
        movitText("reports_tab_exercises"),
        movitText("reports_tab_trends"),
    )
    MovitSegmentedControl(
        options = labels,
        selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
        onOptionSelected = { index -> onTabSelected(tabs[index]) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.sm),
    )
}

@Composable
private fun ReportsOverviewPanel(dashboard: ReportsDashboardUi) {
    MovitSectionHeader(
        title = movitText("reports_key_numbers"),
        subtitle = dashboard.periodLabel,
    )
    MovitKpiGrid(
        items = dashboard.kpis.map {
            MovitKpiItem(
                value = it.value,
                label = it.label,
                highlighted = it.highlighted,
            )
        },
    )

    if (dashboard.formScorePoints.isNotEmpty()) {
        MovitCard(variant = MovitCardVariant.Filled) {
            Column(modifier = Modifier.padding(MovitSpacing.md)) {
                Text(
                    text = movitText("reports_form_journey"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W800,
                )
                val normalized = dashboard.formScorePoints.map { (it / 100f).coerceIn(0f, 1f) }
                MovitLineChart(
                    points = normalized,
                    modifier = Modifier.padding(top = MovitSpacing.md),
                )
            }
        }
    }

    if (dashboard.weeklyBarValues.isNotEmpty()) {
        MovitCard(variant = MovitCardVariant.Filled) {
            Column(modifier = Modifier.padding(MovitSpacing.md)) {
                Text(
                    text = movitText("reports_weekly_breakdown"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W800,
                )
                MovitBarChart(
                    items = dashboard.weeklyBarValues.mapIndexed { index, value ->
                        MovitBarChartItem(
                            value = value,
                            label = dashboard.weeklyBarLabels.getOrElse(index) {
                                movitText("reports_week_short", index + 1)
                            },
                            highlighted = value == dashboard.weeklyBarValues.maxOrNull(),
                        )
                    },
                    modifier = Modifier.padding(top = MovitSpacing.md),
                )
            }
        }
    }
}

@Composable
private fun ReportsExercisesPanel(
    dashboard: ReportsDashboardUi,
    onEvent: (MovitReportsEvent) -> Unit,
) {
    MovitSectionHeader(title = movitText("reports_by_exercise"))
    MovitCard(variant = MovitCardVariant.Outlined) {
        Column(modifier = Modifier.padding(horizontal = MovitSpacing.md)) {
            dashboard.exercises.forEachIndexed { index, exercise ->
                ExerciseReportRow(
                    exercise = exercise,
                    onClick = { onEvent(MovitReportsEvent.ExerciseReportClicked(exercise.id)) },
                )
                if (index < dashboard.exercises.lastIndex) {
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseReportRow(
    exercise: ReportExerciseUi,
    onClick: () -> Unit,
) {
    val movit = MaterialTheme.movitColors
    val textColor = when {
        exercise.scorePercent >= 85 -> movit.success
        exercise.scorePercent >= 70 -> MaterialTheme.colorScheme.primary
        else -> movit.warning
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = MovitSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
    ) {
        MovitCard(
            variant = MovitCardVariant.Filled,
            modifier = Modifier,
        ) {
            Text(
                text = exercise.scoreLabel,
                modifier = Modifier.padding(horizontal = MovitSpacing.md, vertical = MovitSpacing.sm),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W800,
                color = textColor,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.W700,
            )
            Text(
                text = exercise.sessionsLabel,
                style = MaterialTheme.typography.bodySmall,
                color = movit.textSecondary,
            )
        }
    }
}

@Composable
private fun ReportsTrendsPanel(dashboard: ReportsDashboardUi) {
    dashboard.trendInsight?.let { insight ->
        MovitInsightCard(
            title = insight.title,
            message = insight.message,
            icon = Icons.Default.BarChart,
            variant = MovitInsightVariant.Success,
        )
    }

    if (dashboard.volumeBarValues.isNotEmpty()) {
        MovitCard(variant = MovitCardVariant.Filled) {
            Column(modifier = Modifier.padding(MovitSpacing.md)) {
                Text(
                    text = movitText("reports_volume_trend"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W800,
                )
                MovitBarChart(
                    items = dashboard.volumeBarValues.mapIndexed { index, value ->
                        MovitBarChartItem(
                            value = value,
                            label = dashboard.volumeBarLabels.getOrElse(index) { "" },
                            highlighted = value == dashboard.volumeBarValues.maxOrNull(),
                        )
                    },
                    modifier = Modifier.padding(top = MovitSpacing.md),
                )
            }
        }
    }

    dashboard.fatigueIndex?.let { fatigue ->
        MovitCard(variant = MovitCardVariant.Outlined) {
            Column(modifier = Modifier.padding(MovitSpacing.lg)) {
                Text(
                    text = fatigue.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.movitColors.textSecondary,
                    fontWeight = FontWeight.W700,
                )
                Text(
                    text = fatigue.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.W800,
                    color = MaterialTheme.movitColors.warning,
                    modifier = Modifier.padding(top = MovitSpacing.xs),
                )
                Text(
                    text = fatigue.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.movitColors.textSecondary,
                    modifier = Modifier.padding(top = MovitSpacing.xs),
                )
            }
        }
    }
}
