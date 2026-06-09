package com.movit.feature.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitScaffold
import com.movit.feature.train.components.TrainNoPlanSection
import com.movit.feature.train.components.TrainQuickActions
import com.movit.feature.train.components.TrainReadinessCard
import com.movit.feature.train.components.TrainReportSection
import com.movit.feature.train.components.TrainStatusBanner
import com.movit.feature.train.components.TrainTodayCard
import com.movit.feature.train.components.TrainWeekPreview
import com.movit.resources.movitText

@Composable
fun MovitTrainScreen(
    state: MovitTrainUiState,
    onEvent: (MovitTrainEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dashboard = state.dashboard
    MovitScaffold(
        modifier = modifier,
        title = movitText("train_title"),
        subtitle = dashboard?.subtitle ?: movitText("train_dest_subtitle"),
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
                    MovitLoadingState(message = movitText("train_loading"))
                }
                state.errorMessage != null -> {
                    MovitErrorState(
                        message = state.errorMessage,
                        onRetry = { onEvent(MovitTrainEvent.RetryClicked) },
                    )
                }
                dashboard != null -> {
                    TrainDashboardContent(
                        dashboard = dashboard,
                        onEvent = onEvent,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrainDashboardContent(
    dashboard: TrainDashboardUi,
    onEvent: (MovitTrainEvent) -> Unit,
) {
    val onPrimaryAction: () -> Unit = {
        when (dashboard.status) {
            TrainDashboardStatus.ActivePlan -> onEvent(MovitTrainEvent.StartWorkoutClicked)
            TrainDashboardStatus.NoPlan,
            TrainDashboardStatus.RestDay,
            -> onEvent(MovitTrainEvent.ExploreProgramsClicked)
            TrainDashboardStatus.CompletedToday,
            TrainDashboardStatus.ProgramComplete,
            -> onEvent(MovitTrainEvent.ViewReportClicked)
        }
    }

    TrainStatusBanner(
        dashboard = dashboard,
        onExplorePrograms = { onEvent(MovitTrainEvent.ExploreProgramsClicked) },
    )

    if (dashboard.status == TrainDashboardStatus.NoPlan) {
        TrainNoPlanSection(
            programs = dashboard.featuredPrograms,
            onExplorePrograms = { onEvent(MovitTrainEvent.ExploreProgramsClicked) },
        )
    }

    if (dashboard.week.days.isNotEmpty() && dashboard.status != TrainDashboardStatus.NoPlan) {
        TrainWeekPreview(week = dashboard.week)
    }

    TrainTodayCard(
        dashboard = dashboard,
        onPrimaryAction = onPrimaryAction,
    )

    if (shouldShowReadiness(dashboard.status)) {
        TrainReadinessCard(readiness = dashboard.readiness)
    }

    if (shouldShowReport(dashboard)) {
        dashboard.report?.let { report ->
            TrainReportSection(
                report = report,
                onViewReport = { onEvent(MovitTrainEvent.ViewReportClicked) },
            )
        }
    }

    TrainQuickActions(
        actions = dashboard.quickActions,
        onActionClick = { onEvent(MovitTrainEvent.QuickActionClicked(it)) },
    )

    if (dashboard.program != null && dashboard.status != TrainDashboardStatus.ProgramComplete) {
        MovitButton(
            text = movitText("train_browse_programs"),
            onClick = { onEvent(MovitTrainEvent.ExploreProgramsClicked) },
            variant = MovitButtonVariant.Text,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun shouldShowReadiness(status: TrainDashboardStatus): Boolean = when (status) {
    TrainDashboardStatus.ActivePlan,
    TrainDashboardStatus.RestDay,
    TrainDashboardStatus.NoPlan,
    -> true
    else -> false
}

private fun shouldShowReport(dashboard: TrainDashboardUi): Boolean =
    dashboard.report != null && dashboard.program != null && dashboard.status != TrainDashboardStatus.NoPlan
