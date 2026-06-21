package com.movit.feature.account

import com.movit.shared.AppResult

data class LevelProfileUi(
    val levelNumber: Int,
    val levelName: String,
    val bodyScore: Int,
    val progressToNext: Int,
    val pointsToNext: Int,
    val reassessmentLabel: String,
    val domains: List<LevelDomainUi>,
    val regions: List<LevelRegionUi> = emptyList(),
    val limitingFactors: List<LevelLimitingFactorUi> = emptyList(),
    val planPhases: List<PlanPhaseUi>,
)

data class LevelRegionUi(
    val name: String,
    val level: Int,
    val score: Int,
    val isLimiting: Boolean,
)

data class LevelLimitingFactorUi(
    val type: String,
    val name: String,
    val currentLevel: Int,
    val targetLevel: Int,
    val gap: Int,
)

data class LevelDomainUi(
    val name: String,
    val score: Int,
)

data class PlanPhaseUi(
    val title: String,
    val subtitle: String,
    val status: PlanPhaseStatus,
    val progressPercent: Int? = null,
    val highlight: String? = null,
)

enum class PlanPhaseStatus {
    Done,
    Active,
    Upcoming,
}

interface LevelRepository {
    suspend fun fetchLevelProfile(): AppResult<LevelProfileUi>
}
