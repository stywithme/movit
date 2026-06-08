package com.movit.feature.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitScaffold
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.feature.train.components.TrainQuickActions
import com.movit.feature.train.components.TrainReadinessCard
import com.movit.feature.train.components.TrainStatusBanner
import com.movit.feature.train.components.TrainTodayCard
import com.movit.feature.train.components.TrainWeekPreview

@Composable
fun MovitTrainScreen(
    state: MovitTrainUiState,
    onEvent: (MovitTrainEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dashboard = state.dashboard
    MovitScaffold(
        modifier = modifier,
        title = dashboard?.title ?: "Train",
        subtitle = dashboard?.subtitle ?: "Your program and today's plan.",
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MovitSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
        ) {
            when {
                state.isLoading && dashboard == null -> {
                    MovitLoadingState(message = "Loading your training plan…")
                }
                state.errorMessage != null -> {
                    MovitErrorState(
                        message = state.errorMessage,
                        onRetry = { onEvent(MovitTrainEvent.RetryClicked) },
                    )
                }
                dashboard != null -> {
                    TrainStatusBanner(dashboard = dashboard)
                    TrainTodayCard(
                        dashboard = dashboard,
                        onPrimaryAction = {
                            when (dashboard.status) {
                                TrainDashboardStatus.ActivePlan -> {
                                    onEvent(MovitTrainEvent.StartWorkoutClicked)
                                }
                                TrainDashboardStatus.NoPlan,
                                TrainDashboardStatus.RestDay,
                                -> {
                                    onEvent(MovitTrainEvent.ExploreProgramsClicked)
                                }
                                TrainDashboardStatus.CompletedToday,
                                TrainDashboardStatus.ProgramComplete,
                                -> {
                                    onEvent(MovitTrainEvent.ViewReportClicked)
                                }
                            }
                        },
                    )

                    if (dashboard.week.days.isNotEmpty()) {
                        TrainWeekPreview(week = dashboard.week)
                    }

                    TrainReadinessCard(readiness = dashboard.readiness)

                    dashboard.report?.let { report ->
                        TrainReportCard(
                            report = report,
                            onViewReport = { onEvent(MovitTrainEvent.ViewReportClicked) },
                        )
                    }

                    TrainQuickActions(
                        actions = dashboard.quickActions,
                        onActionClick = { onEvent(MovitTrainEvent.QuickActionClicked(it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrainReportCard(
    report: TrainReportSummaryUi,
    onViewReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        MovitSectionHeader(
            title = report.title,
            subtitle = report.insight,
            actionLabel = "Open",
            onActionClick = onViewReport,
        )
        MovitCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
            ) {
                report.metrics.take(4).forEach { metric ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
                    ) {
                        Text(
                            text = metric.value,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = metric.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
