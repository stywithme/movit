package com.movit.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitEmptyState
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitHeroCard
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitMediaCard
import com.movit.designsystem.components.MovitScaffold
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.feature.explore.components.ExploreFilterSection
import com.movit.feature.explore.components.ExploreSearchSection
import com.movit.resources.movitText

@Composable
fun MovitExploreScreen(
    state: MovitExploreUiState,
    onEvent: (MovitExploreEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    MovitScaffold(
        modifier = modifier,
        title = movitText("explore_title"),
        subtitle = movitText("explore_subtitle"),
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
                state.isLoading && state.featured.isEmpty() -> {
                    MovitLoadingState(message = movitText("explore_loading"))
                }
                state.errorMessage != null -> {
                    MovitErrorState(
                        message = state.errorMessage,
                        onRetry = { onEvent(MovitExploreEvent.RetryClicked) },
                    )
                }
                state.isEmpty -> {
                    MovitEmptyState(
                        title = movitText("explore_no_results"),
                        message = movitText("explore_try_filter"),
                        actionLabel = movitText("explore_clear_filters"),
                        onActionClick = {
                            onEvent(MovitExploreEvent.QueryChanged(""))
                            onEvent(MovitExploreEvent.FilterSelected(ExploreFilter.All))
                        },
                    )
                }
                else -> {
                    if (state.featured.isNotEmpty()) {
                        MovitSectionHeader(
                            title = movitText("explore_best_start"),
                            subtitle = movitText("explore_recommended"),
                            actionLabel = movitText("explore_see_all"),
                            onActionClick = { onEvent(MovitExploreEvent.SeeAllWorkoutsClicked) },
                        )
                        state.featured.firstOrNull()?.let { item ->
                            MovitHeroCard(
                                eyebrow = item.badge ?: movitText("explore_smart_pick"),
                                title = item.title,
                                membersLabel = item.metadata.joinToString(" · "),
                                ctaLabel = movitText("explore_open_workout"),
                                onCtaClick = {
                                    onEvent(MovitExploreEvent.ItemClicked(item.id, item.type))
                                },
                                showPlayFab = true,
                            )
                        }
                    }

                    if (state.workouts.isNotEmpty()) {
                        MovitSectionHeader(
                            title = movitText("explore_target_muscle"),
                            subtitle = movitText("explore_workouts"),
                            actionLabel = movitText("explore_see_all"),
                            onActionClick = { onEvent(MovitExploreEvent.SeeAllWorkoutsClicked) },
                        )
                        state.workouts.take(3).forEach { item ->
                            MovitMediaCard(
                                title = item.title,
                                subtitle = item.subtitle,
                                metadata = item.metadata,
                                badge = item.badge,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    onEvent(MovitExploreEvent.ItemClicked(item.id, ExploreItemType.Workout))
                                },
                            )
                        }
                    }

                    if (state.exercises.isNotEmpty()) {
                        MovitSectionHeader(
                            title = movitText("explore_popular"),
                            subtitle = movitText("explore_exercises"),
                            actionLabel = movitText("explore_see_all"),
                            onActionClick = { onEvent(MovitExploreEvent.SeeAllExercisesClicked) },
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = MovitSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
                        ) {
                            state.exercises.take(4).chunked(2).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
                                ) {
                                    rowItems.forEach { item ->
                                        MovitMediaCard(
                                            title = item.title,
                                            subtitle = item.metadata.joinToString(" · ").ifBlank { item.subtitle },
                                            badge = item.badge,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                onEvent(
                                                    MovitExploreEvent.ItemClicked(item.id, ExploreItemType.Exercise),
                                                )
                                            },
                                        )
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    state.programs.firstOrNull()?.let { program ->
                        MovitSectionHeader(
                            title = movitText("explore_guided_paths"),
                            subtitle = movitText("explore_programs"),
                            actionLabel = movitText("explore_open"),
                            onActionClick = {
                                onEvent(MovitExploreEvent.ItemClicked(program.id, ExploreItemType.Program))
                            },
                        )
                        MovitMediaCard(
                            title = program.title,
                            subtitle = program.subtitle,
                            metadata = program.metadata,
                            badge = program.badge,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                onEvent(MovitExploreEvent.ItemClicked(program.id, ExploreItemType.Program))
                            },
                        )
                    }
                }
            }
        }
    }
}
