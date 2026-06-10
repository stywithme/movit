package com.movit.feature.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitBarChart
import com.movit.designsystem.components.MovitBarChartItem
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
import com.movit.designsystem.components.MovitUnderlineTabRow
import com.movit.designsystem.movitColors
import com.movit.resources.movitText
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovitReportsScreen(
    state: MovitReportsUiState,
    onEvent: (MovitReportsEvent) -> Unit,
    modifier: Modifier = Modifier,
    userName: String = movitText("reports_athlete_fallback"),
) {
    val dashboard = state.dashboard
    val canRefresh = dashboard?.hubState == ReportsHubState.Success

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

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = {
                    if (canRefresh) {
                        onEvent(MovitReportsEvent.RefreshRequested)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(MovitSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
                ) {
                    when {
                        state.isLoading && dashboard == null -> {
                            Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
                                MovitLoadingState(message = movitText("reports_loading"))
                                Text(
                                    text = movitText("reports_loading_sub"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.movitColors.textSecondary,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        state.errorMessage != null && dashboard?.hubState == ReportsHubState.Error -> {
                            MovitErrorState(
                                title = movitText("common_error_title"),
                                message = state.errorMessage,
                                actionLabel = movitText("common_retry"),
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
    MovitUnderlineTabRow(
        tabs = labels,
        selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
        onTabSelected = { index -> onTabSelected(tabs[index]) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MovitSpacing.lg),
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
        ReportsChartCard(
            title = movitText("reports_form_journey"),
            contentDescription = movitText("reports_form_journey"),
        ) {
            val normalized = dashboard.formScorePoints.map { (it / 100f).coerceIn(0f, 1f) }
            MovitLineChart(
                points = normalized,
                modifier = Modifier.padding(top = MovitSpacing.md),
            )
        }
    }

    if (dashboard.weeklyBarValues.isNotEmpty()) {
        ReportsChartCard(
            title = movitText("reports_weekly_breakdown"),
            contentDescription = movitText("reports_weekly_breakdown"),
        ) {
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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
    val (textColor, backgroundColor) = when {
        exercise.scorePercent >= 85 -> movit.success to movit.successTint
        exercise.scorePercent >= 70 -> MaterialTheme.colorScheme.primary to movit.primaryTint
        else -> movit.warning to movit.warningTint
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = MovitSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = backgroundColor,
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
    val movit = MaterialTheme.movitColors

    dashboard.trendInsight?.let { insight ->
        MovitInsightCard(
            title = insight.title,
            message = insight.message,
            icon = Icons.Default.BarChart,
            variant = MovitInsightVariant.Success,
        )
    }

    dashboard.improvementRatePercent?.let { rate ->
        if (rate != 0f) {
            val signedRate = buildString {
                if (rate > 0f) append('+')
                append(ReportsFormatting.formatOneDecimal(abs(rate)))
            }
            Text(
                text = movitText("reports_improvement_since_week_one", signedRate),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.W700,
                color = if (rate >= 0f) movit.success else MaterialTheme.colorScheme.error,
            )
        }
    }

    if (dashboard.formScorePoints.size >= 2) {
        ReportsChartCard(
            title = movitText("reports_improvement_rate"),
            contentDescription = movitText("reports_improvement_rate"),
        ) {
            val normalized = dashboard.formScorePoints.map { (it / 100f).coerceIn(0f, 1f) }
            MovitLineChart(
                points = normalized,
                lineColor = movit.success,
                fillColor = movit.success.copy(alpha = 0.15f),
                modifier = Modifier.padding(top = MovitSpacing.md),
            )
        }
    }

    if (dashboard.volumeBarValues.isNotEmpty()) {
        ReportsChartCard(
            title = movitText("reports_volume_trend"),
            contentDescription = movitText("reports_volume_trend"),
        ) {
            MovitBarChart(
                items = dashboard.volumeBarValues.mapIndexed { index, value ->
                    MovitBarChartItem(
                        value = value,
                        label = dashboard.volumeBarLabels.getOrElse(index) {
                            movitText("reports_week_short", index + 1)
                        },
                        highlighted = value == dashboard.volumeBarValues.maxOrNull(),
                    )
                },
                modifier = Modifier.padding(top = MovitSpacing.md),
            )
        }
    }

    if (dashboard.weeklyBarValues.isNotEmpty()) {
        ReportsChartCard(
            title = movitText("reports_attendance_trend"),
            contentDescription = movitText("reports_attendance_trend"),
        ) {
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

    dashboard.fatigueIndex?.let { fatigue ->
        MovitCard(variant = MovitCardVariant.Outlined) {
            Column(modifier = Modifier.padding(MovitSpacing.lg)) {
                Text(
                    text = fatigue.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = movit.textSecondary,
                    fontWeight = FontWeight.W700,
                )
                Text(
                    text = fatigue.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.W800,
                    color = movit.warning,
                    modifier = Modifier.padding(top = MovitSpacing.xs),
                )
                Text(
                    text = fatigue.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = movit.textSecondary,
                    modifier = Modifier.padding(top = MovitSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun ReportsChartCard(
    title: String,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    MovitCard(variant = MovitCardVariant.Filled) {
        Column(
            modifier = Modifier
                .padding(MovitSpacing.md)
                .semantics { this.contentDescription = contentDescription },
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W800,
            )
            content()
        }
    }
}
