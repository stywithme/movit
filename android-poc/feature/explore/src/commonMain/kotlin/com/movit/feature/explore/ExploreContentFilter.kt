package com.movit.feature.explore

object ExploreContentFilter {

    fun filterItems(
        items: List<ExploreItemUi>,
        query: String,
        filter: ExploreFilter,
    ): List<ExploreItemUi> {
        return items
            .asSequence()
            .filter { matchesFilter(it, filter) }
            .filter { matchesQuery(it, query) }
            .toList()
    }

    fun matchesFilter(item: ExploreItemUi, filter: ExploreFilter): Boolean {
        return when (filter) {
            ExploreFilter.All -> true
            ExploreFilter.Exercises -> item.type == ExploreItemType.Exercise
            ExploreFilter.Workouts -> item.type == ExploreItemType.Workout
            ExploreFilter.Programs -> item.type == ExploreItemType.Program
        }
    }

    fun matchesQuery(item: ExploreItemUi, query: String): Boolean {
        if (query.isBlank()) return true
        val needle = query.trim().lowercase()
        return item.title.lowercase().contains(needle) ||
            item.subtitle.lowercase().contains(needle) ||
            item.metadata.any { it.lowercase().contains(needle) }
    }
}
