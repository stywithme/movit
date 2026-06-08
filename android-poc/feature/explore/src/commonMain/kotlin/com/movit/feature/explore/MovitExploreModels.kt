package com.movit.feature.explore

data class ExploreItemUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: ExploreItemType,
    val imageUrl: String? = null,
    val badge: String? = null,
    val metadata: List<String> = emptyList(),
)

enum class ExploreItemType {
    Exercise,
    Workout,
    Program,
}

enum class ExploreFilter(val label: String) {
    All("All"),
    Exercises("Exercises"),
    Workouts("Workouts"),
    Programs("Programs"),
    ;

    companion object {
        val defaults: List<ExploreFilter> = entries.toList()
    }
}

data class ExploreContent(
    val featured: List<ExploreItemUi>,
    val exercises: List<ExploreItemUi>,
)
