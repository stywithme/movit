package com.movit.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitEmptyState
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitScaffold
import com.movit.feature.explore.components.ExploreExerciseList
import com.movit.feature.explore.components.ExploreFilterSection
import com.movit.feature.explore.components.ExploreHero
import com.movit.feature.explore.components.ExploreSearchSection

@Composable
fun MovitExploreScreen(
    state: MovitExploreUiState,
    onEvent: (MovitExploreEvent) -> Unit,
    modifier: Modifier = Modifier,
    useRtlPreview: Boolean = false,
) {
    MovitScaffold(
        modifier = modifier,
        title = if (useRtlPreview) "استكشف" else "Explore",
        subtitle = if (useRtlPreview) {
            "تمارين وجلسات وبرامج في مكان واحد."
        } else {
            "Workouts, exercises and programs in one place."
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MovitSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
        ) {
            ExploreSearchSection(
                query = state.query,
                onQueryChange = { onEvent(MovitExploreEvent.QueryChanged(it)) },
                enabled = state.errorMessage == null,
            )
            ExploreFilterSection(
                filters = state.filters,
                selectedFilter = state.selectedFilter,
                onFilterSelected = { onEvent(MovitExploreEvent.FilterSelected(it)) },
                enabled = state.errorMessage == null && !state.isLoading,
            )

            when {
                state.isLoading && state.featured.isEmpty() && state.exercises.isEmpty() -> {
                    MovitLoadingState(message = "Loading Movit library…")
                }
                state.errorMessage != null -> {
                    MovitErrorState(
                        message = state.errorMessage,
                        onRetry = { onEvent(MovitExploreEvent.RetryClicked) },
                    )
                }
                state.isEmpty -> {
                    MovitEmptyState(
                        title = "No results",
                        message = "Try a different search or filter.",
                        actionLabel = "Clear filters",
                        onActionClick = {
                            onEvent(MovitExploreEvent.QueryChanged(""))
                            onEvent(MovitExploreEvent.FilterSelected(ExploreFilter.All))
                        },
                    )
                }
                else -> {
                    ExploreHero(
                        items = state.featured,
                        onItemClick = { onEvent(MovitExploreEvent.ItemClicked(it)) },
                    )
                    ExploreExerciseList(
                        items = state.exercises,
                        onItemClick = { onEvent(MovitExploreEvent.ItemClicked(it)) },
                    )
                }
            }
        }
    }
}
