package com.movit.feature.explore

sealed interface MovitExploreEvent {
    data class QueryChanged(val value: String) : MovitExploreEvent
    data class FilterSelected(val filter: ExploreFilter) : MovitExploreEvent
    data class ItemClicked(val id: String, val type: ExploreItemType) : MovitExploreEvent
    data object SeeAllExercisesClicked : MovitExploreEvent
    data object SeeAllWorkoutsClicked : MovitExploreEvent
    data object OpenFeaturedProgramClicked : MovitExploreEvent
    data object RetryClicked : MovitExploreEvent
    data object RefreshRequested : MovitExploreEvent
}
