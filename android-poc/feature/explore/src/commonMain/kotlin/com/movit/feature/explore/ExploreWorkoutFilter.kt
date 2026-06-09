package com.movit.feature.explore

import androidx.compose.runtime.Composable
import com.movit.resources.movitText

enum class ExploreWorkoutFilter {
    All,
    Beginner,
    Intermediate,
    Advanced,
    Short,
    ;

    companion object {
        val defaults: List<ExploreWorkoutFilter> = entries.toList()
    }
}

@Composable
fun ExploreWorkoutFilter.label(): String = when (this) {
    ExploreWorkoutFilter.All -> movitText("explore_filter_all")
    ExploreWorkoutFilter.Beginner -> movitText("explore_workout_filter_beginner")
    ExploreWorkoutFilter.Intermediate -> movitText("explore_workout_filter_intermediate")
    ExploreWorkoutFilter.Advanced -> movitText("explore_workout_filter_advanced")
    ExploreWorkoutFilter.Short -> movitText("explore_workout_filter_short")
}

data class ExploreCategoryChip(
    val code: String?,
    val label: String,
)

object ExploreWorkoutFilterLogic {

    fun matches(item: ExploreItemUi, filter: ExploreWorkoutFilter): Boolean {
        if (item.type != ExploreItemType.Workout) return true
        return when (filter) {
            ExploreWorkoutFilter.All -> true
            ExploreWorkoutFilter.Beginner -> (item.levelNumber ?: 0) in 1..2
            ExploreWorkoutFilter.Intermediate -> item.levelNumber == 3
            ExploreWorkoutFilter.Advanced -> (item.levelNumber ?: 0) >= 4
            ExploreWorkoutFilter.Short -> (item.durationMinutes ?: Int.MAX_VALUE) in 1..20
        }
    }
}
