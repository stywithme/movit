package com.movit.feature.explore

data class MovitExploreUiState(
    val isLoading: Boolean = false,
    val query: String = "",
    val selectedFilter: ExploreFilter = ExploreFilter.All,
    val filters: List<ExploreFilter> = ExploreFilter.defaults,
    val featured: List<ExploreItemUi> = emptyList(),
    val workouts: List<ExploreItemUi> = emptyList(),
    val exercises: List<ExploreItemUi> = emptyList(),
    val programs: List<ExploreItemUi> = emptyList(),
    val errorMessage: String? = null,
    val isRefreshing: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null &&
            featured.isEmpty() && workouts.isEmpty() && exercises.isEmpty() && programs.isEmpty()
}
