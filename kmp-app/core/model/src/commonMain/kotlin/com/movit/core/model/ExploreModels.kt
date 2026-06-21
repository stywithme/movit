package com.movit.core.model

import com.movit.shared.AppResult

data class ExploreItemUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: ExploreItemType,
    val imageUrl: String? = null,
    val badge: String? = null,
    val badgeVariant: MovitTagVariant? = null,
    val focusLabel: String? = null,
    val metadata: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val levelNumber: Int? = null,
    val durationMinutes: Int? = null,
    val categoryCode: String? = null,
)

enum class ExploreItemType {
    Exercise,
    Workout,
    Program,
}

data class ExploreContent(
    val featured: List<ExploreItemUi>,
    val workouts: List<ExploreItemUi>,
    val exercises: List<ExploreItemUi>,
    val programs: List<ExploreItemUi>,
)

interface ExploreRepository {
    suspend fun getExploreContent(): AppResult<ExploreContent>

    suspend fun refreshExploreContent(): AppResult<ExploreContent> = getExploreContent()
}
