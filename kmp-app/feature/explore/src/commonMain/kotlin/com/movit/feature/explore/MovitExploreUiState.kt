package com.movit.feature.explore

import com.movit.core.model.ExploreItemUi

data class MovitExploreUiState(
    val isLoading: Boolean = false,
    val query: String = "",
    val selectedFilter: ExploreFilter = ExploreFilter.All,
    val filters: List<ExploreFilter> = ExploreFilter.defaults,
    val selectedWorkoutFilter: ExploreWorkoutFilter = ExploreWorkoutFilter.All,
    val workoutFilters: List<ExploreWorkoutFilter> = ExploreWorkoutFilter.defaults,
    val selectedExerciseCategory: String? = null,
    val exerciseCategoryChips: List<ExploreCategoryChip> = emptyList(),
    val secondaryFiltersVisible: Boolean = true,
    val filteredWorkoutCount: Int = 0,
    val filteredExerciseCount: Int = 0,
    val featured: List<ExploreItemUi> = emptyList(),
    val workouts: List<ExploreItemUi> = emptyList(),
    val exercises: List<ExploreItemUi> = emptyList(),
    val programs: List<ExploreItemUi> = emptyList(),
    val errorMessage: String? = null,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val scrollToWorkouts: Boolean = false,
    val scrollToExercises: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null &&
            featured.isEmpty() && workouts.isEmpty() && exercises.isEmpty() && programs.isEmpty()

    val showWorkoutsSection: Boolean
        get() = selectedFilter != ExploreFilter.Exercises && selectedFilter != ExploreFilter.Programs

    val showExercisesSection: Boolean
        get() = selectedFilter != ExploreFilter.Workouts && selectedFilter != ExploreFilter.Programs
}
