package com.movit.feature.explore

sealed interface MovitExploreEvent {
    data class QueryChanged(val value: String) : MovitExploreEvent
    data class FilterSelected(val filter: ExploreFilter) : MovitExploreEvent
    data class ItemClicked(val id: String) : MovitExploreEvent
    data object RetryClicked : MovitExploreEvent
    data object RefreshRequested : MovitExploreEvent
}
