package com.movit.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitScaffold
import com.movit.feature.home.components.HomeHeroSummary
import com.movit.feature.home.components.HomeInsightCard
import com.movit.feature.home.components.HomeProgressSection
import com.movit.feature.home.components.HomeQuickActions
import com.movit.feature.home.components.HomeReportPreview
import com.movit.feature.home.components.TodayPlanCard

@Composable
fun MovitHomeScreen(
    state: MovitHomeUiState,
    onEvent: (MovitHomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    MovitScaffold(
        modifier = modifier,
        title = state.greetingTitle,
        subtitle = state.greetingSubtitle,
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
                state.isLoading && state.progress == null && state.todayPlan == null -> {
                    MovitLoadingState(message = "Loading your dashboard…")
                }
                state.errorMessage != null -> {
                    MovitErrorState(
                        message = state.errorMessage,
                        onRetry = { onEvent(MovitHomeEvent.RetryClicked) },
                    )
                }
                else -> {
                    state.insightMessage?.let { message ->
                        HomeInsightCard(message = message)
                    }

                    HomeHeroSummary(
                        greetingTitle = state.greetingTitle,
                        greetingSubtitle = state.greetingSubtitle,
                        progress = state.progress,
                        hasTodayPlan = state.todayPlan != null,
                        onStartTraining = { onEvent(MovitHomeEvent.StartTodayPlanClicked) },
                        onExplore = { onEvent(MovitHomeEvent.ExploreClicked) },
                    )

                    TodayPlanCard(
                        todayPlan = state.todayPlan,
                        onStartPlan = { onEvent(MovitHomeEvent.StartTodayPlanClicked) },
                        onExplore = { onEvent(MovitHomeEvent.ExploreClicked) },
                    )

                    state.progress?.let { progress ->
                        HomeProgressSection(progress = progress)
                    }

                    HomeReportPreview(
                        reportPreview = state.reportPreview,
                        onOpenReports = { onEvent(MovitHomeEvent.ReportsClicked) },
                    )

                    HomeQuickActions(
                        actions = state.quickActions,
                        onActionClick = { onEvent(MovitHomeEvent.QuickActionClicked(it)) },
                    )
                }
            }
        }
    }
}
