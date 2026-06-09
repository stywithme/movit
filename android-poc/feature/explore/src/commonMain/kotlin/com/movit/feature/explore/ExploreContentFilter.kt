package com.movit.feature.explore

object ExploreContentFilter {

    fun filterItems(
        items: List<ExploreItemUi>,
        query: String,
        filter: ExploreFilter,
        workoutFilter: ExploreWorkoutFilter = ExploreWorkoutFilter.All,
        exerciseCategoryCode: String? = null,
    ): List<ExploreItemUi> {
        return items
            .asSequence()
            .filter { matchesFilter(it, filter) }
            .filter { ExploreWorkoutFilterLogic.matches(it, workoutFilter) }
            .filter { matchesExerciseCategory(it, exerciseCategoryCode) }
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

    fun matchesExerciseCategory(item: ExploreItemUi, categoryCode: String?): Boolean {
        if (item.type != ExploreItemType.Exercise) return true
        val selected = categoryCode ?: return true
        return item.categoryCode.equals(selected, ignoreCase = true)
    }

    fun matchesQuery(item: ExploreItemUi, query: String): Boolean {
        if (query.isBlank()) return true
        val needle = query.trim().lowercase()
        return item.title.lowercase().contains(needle) ||
            item.subtitle.lowercase().contains(needle) ||
            item.metadata.any { it.lowercase().contains(needle) } ||
            item.tags.any { it.lowercase().contains(needle) }
    }

    fun buildExerciseCategoryChips(exercises: List<ExploreItemUi>): List<ExploreCategoryChip> {
        val grouped = exercises
            .mapNotNull { exercise ->
                exercise.categoryCode?.takeIf { it.isNotBlank() }?.let { code ->
                    code to (exercise.metadata.firstOrNull() ?: exercise.subtitle)
                }
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<String>>> { it.value.size }
                    .thenBy { it.key },
            )
            .take(6)
        return buildList {
            add(ExploreCategoryChip(code = null, label = ""))
            grouped.forEach { entry ->
                add(
                    ExploreCategoryChip(
                        code = entry.key,
                        label = entry.value.firstOrNull().orEmpty().ifBlank { entry.key },
                    ),
                )
            }
        }
    }
}
