package com.movit.feature.explore

enum class ExploreFilter {
    All,
    Exercises,
    Workouts,
    Programs,
    ;

    companion object {
        val defaults: List<ExploreFilter> = entries.toList()
    }
}
