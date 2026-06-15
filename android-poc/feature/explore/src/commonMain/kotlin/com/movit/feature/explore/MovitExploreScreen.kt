package com.movit.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.movit.core.model.ExploreItemType
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitEmptyState
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitMediaCard
import com.movit.designsystem.components.MovitProgramCard
import com.movit.designsystem.components.MovitScaffold
import com.movit.designsystem.components.MovitSectionHeader
import com.movit.designsystem.components.movitFloatingNavScrollPadding
import com.movit.designsystem.movitColors
import com.movit.feature.explore.components.ExploreExerciseList
import com.movit.feature.explore.components.ExploreFilterSection
import com.movit.feature.explore.components.ExploreHero
import com.movit.feature.explore.components.ExploreMuscleStrip
import com.movit.feature.explore.components.ExploreSearchSection
import com.movit.feature.explore.components.ExploreWorkoutIntro
import com.movit.resources.movitText
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovitExploreScreen(
    state: MovitExploreUiState,
    onEvent: (MovitExploreEvent) -> Unit,
    modifier: Modifier = Modifier,
    userName: String = "",
    onProfileClick: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val canRefresh = state.errorMessage == null && !state.isLoading
    val workoutsRequester = remember { BringIntoViewRequester() }
    val exercisesRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(state.scrollToWorkouts) {
        if (state.scrollToWorkouts) {
            workoutsRequester.bringIntoView()
            onEvent(MovitExploreEvent.ScrollToWorkoutsHandled)
        }
    }
    LaunchedEffect(state.scrollToExercises) {
        if (state.scrollToExercises) {
            exercisesRequester.bringIntoView()
            onEvent(MovitExploreEvent.ScrollToExercisesHandled)
        }
    }

    MovitScaffold(
        modifier = modifier,
        title = movitText("explore_title"),
        subtitle = movitText("explore_subtitle"),
        userName = userName,
        onProfileClick = onProfileClick,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = {
                if (canRefresh) {
                    onEvent(MovitExploreEvent.RefreshRequested)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(MovitSpacing.lg)
                    .movitFloatingNavScrollPadding(),
                verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
            ) {
            ExploreSearchSection(
                query = state.query,
                onQueryChange = { onEvent(MovitExploreEvent.QueryChanged(it)) },
                onFilterClick = { onEvent(MovitExploreEvent.FilterButtonClicked) },
                filtersActive = state.secondaryFiltersVisible,
                enabled = state.errorMessage == null,
            )
            ExploreFilterSection(
                filters = state.filters,
                selectedFilter = state.selectedFilter,
                onFilterSelected = { onEvent(MovitExploreEvent.FilterSelected(it)) },
                enabled = state.errorMessage == null && !state.isLoading,
            )
            if (state.errorMessage == null && !state.isLoading && !state.isEmpty) {
                Text(
                    text = movitText(
                        "explore_results_summary",
                        state.filteredWorkoutCount,
                        state.filteredExerciseCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.movitColors.textSecondary,
                )
            }

            when {
                state.isLoading && state.featured.isEmpty() -> {
                    MovitLoadingState(message = movitText("explore_loading"))
                }
                state.errorMessage != null -> {
                    MovitErrorState(
                        title = movitText("common_error_title"),
                        message = state.errorMessage,
                        actionLabel = movitText("common_retry"),
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
                            onEvent(MovitExploreEvent.WorkoutFilterSelected(ExploreWorkoutFilter.All))
                            onEvent(MovitExploreEvent.ExerciseCategorySelected(null))
                        },
                    )
                }
                else -> {
                    if (state.featured.isNotEmpty() && state.selectedFilter == ExploreFilter.All) {
                        ExploreHero(
                            items = state.featured,
                            onItemClick = { item ->
                                onEvent(MovitExploreEvent.ItemClicked(item.id, item.type))
                            },
                            onSeeAllClick = { onEvent(MovitExploreEvent.SeeAllWorkoutsClicked) },
                        )
                    }

                    if (state.showWorkoutsSection && state.workouts.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(workoutsRequester),
                        ) {
                            MovitSectionHeader(
                                title = movitText("explore_target_muscle"),
                                subtitle = movitText("explore_workouts"),
                                actionLabel = movitText("explore_see_all"),
                                onActionClick = { onEvent(MovitExploreEvent.SeeAllWorkoutsClicked) },
                            )
                            ExploreWorkoutIntro()
                            if (state.secondaryFiltersVisible) {
                                ExploreMuscleStrip(
                                    filters = state.workoutFilters,
                                    selectedFilter = state.selectedWorkoutFilter,
                                    onFilterSelected = {
                                        onEvent(MovitExploreEvent.WorkoutFilterSelected(it))
                                    },
                                    modifier = Modifier.padding(bottom = MovitSpacing.sm),
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
                                state.workouts.take(3).forEach { item ->
                                    MovitMediaCard(
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        metadata = item.metadata,
                                        badge = item.badge,
                                        focusLabel = item.focusLabel,
                                        imageUrl = item.imageUrl,
                                        imageContentDescription = movitText(
                                            "explore_a11y_media_image",
                                            item.title,
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            onEvent(
                                                MovitExploreEvent.ItemClicked(
                                                    item.id,
                                                    ExploreItemType.Workout,
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (state.showExercisesSection && state.exercises.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(exercisesRequester),
                        ) {
                            ExploreExerciseList(
                                items = state.exercises,
                                categoryChips = state.exerciseCategoryChips,
                                selectedCategoryCode = state.selectedExerciseCategory,
                                secondaryFiltersVisible = state.secondaryFiltersVisible,
                                onCategorySelected = {
                                    onEvent(MovitExploreEvent.ExerciseCategorySelected(it))
                                },
                                onItemClick = { item ->
                                    onEvent(
                                        MovitExploreEvent.ItemClicked(item.id, ExploreItemType.Exercise),
                                    )
                                },
                                onSeeAllClick = { onEvent(MovitExploreEvent.SeeAllExercisesClicked) },
                            )
                        }
                    }

                    state.programs.firstOrNull()?.let { program ->
                        MovitSectionHeader(
                            title = movitText("explore_guided_paths"),
                            subtitle = movitText("explore_programs"),
                            actionLabel = movitText("explore_see_all"),
                            onActionClick = {
                                onEvent(MovitExploreEvent.SeeAllProgramsClicked)
                            },
                        )
                        MovitProgramCard(
                            title = program.title,
                            description = program.subtitle,
                            metadata = program.metadata,
                            badge = program.badge,
                            imageUrl = program.imageUrl,
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
}
