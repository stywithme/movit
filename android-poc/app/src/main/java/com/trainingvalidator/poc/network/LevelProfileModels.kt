package com.trainingvalidator.poc.network

/**
 * Level Profile API Models
 *
 * Response models for the level profile endpoints.
 */

// ── Level profile response ──

data class LevelProfileResponse(
    val success: Boolean,
    val data: LevelProfileData? = null,
    val error: String? = null
)

data class LevelProfileData(
    val id: String,
    val userId: String,
    val overallLevel: Int,
    val bodyScore: Double,
    val domainLevels: List<DomainLevelData>,
    val regionLevels: List<RegionLevelData>,
    val limitingFactors: List<LimitingFactorData>,
    val assessmentId: String,
    val classifiedAt: String,
    val levelInfo: LevelInfoData
)

data class DomainLevelData(
    val domain: String,
    val level: Int,
    val score: Double
)

data class RegionLevelData(
    val region: String,
    val level: Int,
    val score: Double,
    val isLimiting: Boolean
)

data class LimitingFactorData(
    val type: String,
    val code: String,
    val currentLevel: Int,
    val targetLevel: Int,
    val gap: Int
)

data class LevelInfoData(
    val number: Int,
    val code: String,
    val name: LocalizedName,
    val description: LocalizedName?,
    val color: String?
)

data class LocalizedName(
    val en: String,
    val ar: String
)

// ── Level profile history response ──

data class LevelProfileHistoryResponse(
    val success: Boolean,
    val data: List<LevelProfileData>? = null,
    val error: String? = null
)

// ── Levels list response ──

data class LevelsListResponse(
    val success: Boolean,
    val data: List<LevelDefinition>? = null,
    val error: String? = null
)

data class LevelDefinition(
    val number: Int,
    val code: String,
    val name: LocalizedName,
    val description: LocalizedName?,
    val color: String?,
    val entryThreshold: Double,
    val maxThreshold: Double? = null
)
