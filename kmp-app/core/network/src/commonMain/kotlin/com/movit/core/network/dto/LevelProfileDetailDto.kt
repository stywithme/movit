package com.movit.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class LevelProfileApiResponse(
    val success: Boolean = false,
    val data: LevelProfileDetailDto? = null,
    val error: String? = null,
)

@Serializable
data class LevelProfileDetailDto(
    val id: String = "",
    val userId: String = "",
    val overallLevel: Int = 0,
    val bodyScore: Double = 0.0,
    val domainLevels: List<DomainLevelDto> = emptyList(),
    val regionLevels: List<RegionLevelDto> = emptyList(),
    val limitingFactors: List<LimitingFactorDto> = emptyList(),
    val assessmentId: String = "",
    val classifiedAt: String = "",
    val levelInfo: LevelInfoDetailDto = LevelInfoDetailDto(),
)

@Serializable
data class DomainLevelDto(
    val domain: String = "",
    val level: Int = 0,
    val score: Double = 0.0,
)

@Serializable
data class RegionLevelDto(
    val region: String = "",
    val level: Int = 0,
    val score: Double = 0.0,
    val isLimiting: Boolean = false,
)

@Serializable
data class LimitingFactorDto(
    val type: String = "",
    val code: String = "",
    val currentLevel: Int = 0,
    val targetLevel: Int = 0,
    val gap: Int = 0,
)

@Serializable
data class LevelInfoDetailDto(
    val number: Int = 0,
    val code: String = "",
    val name: LocalizedNameDto = LocalizedNameDto(),
    val description: LocalizedNameDto? = null,
    val color: String? = null,
)

@Serializable
data class TrainingProfileApiResponse(
    val success: Boolean = false,
    val data: TrainingProfilePayloadDto? = null,
    val error: String? = null,
)

@Serializable
data class TrainingProfilePayloadDto(
    val trainingGoal: String? = null,
    val profile: Map<String, kotlinx.serialization.json.JsonElement>? = null,
)

@Serializable
data class TrainingProfilePutRequest(
    val dateOfBirth: String? = null,
    val biologicalSex: String? = null,
    val currentActivityLevel: String? = null,
    val heightCm: Float? = null,
    val weightKg: Float? = null,
    val resistanceExperience: String? = null,
    val trainingExperienceMonths: Int? = null,
    val trainingWeekdays: List<Int> = emptyList(),
    val availableDaysPerWeek: Int? = null,
    val maxWorkoutMinutes: Int? = null,
    val trainingLocation: String? = null,
    val availableEquipment: List<String> = emptyList(),
    val knownInjuries: JsonElement? = null,
    val healthDisclaimerAccepted: Boolean = false,
    val trainingGoal: String? = null,
)
