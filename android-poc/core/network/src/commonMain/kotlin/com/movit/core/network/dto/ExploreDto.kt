package com.movit.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ExploreApiResponse(
    val success: Boolean = false,
    val timestamp: String = "",
    val data: ExploreDataDto? = null,
    val meta: ExploreMetaDto? = null,
    val error: String? = null,
)

@Serializable
data class ExploreDataDto(
    val levels: List<ExploreLevelDto> = emptyList(),
    val programs: List<ExploreProgramDto> = emptyList(),
    val workoutTemplates: List<ExploreWorkoutDto> = emptyList(),
    val exercises: List<ExploreExerciseDto> = emptyList(),
    val deletedProgramIds: List<String> = emptyList(),
    val deletedWorkoutTemplateIds: List<String> = emptyList(),
    val deletedExerciseIds: List<String> = emptyList(),
)

@Serializable
data class ExploreMetaDto(
    val isFullSync: Boolean = false,
    val serverVersion: String = "",
    val levelsInResponse: Int = 0,
    val programsInResponse: Int = 0,
    val workoutTemplatesInResponse: Int = 0,
    val exercisesInResponse: Int = 0,
)

@Serializable
data class ExploreLevelDto(
    val number: Int = 0,
    val code: String = "",
    val name: LocalizedNameDto = LocalizedNameDto(),
)

@Serializable
data class ExploreProgramDto(
    val id: String = "",
    val slug: String = "",
    val name: LocalizedNameDto = LocalizedNameDto(),
    val levelMin: ExploreProgramLevelDto? = null,
    val levelMax: ExploreProgramLevelDto? = null,
    val durationWeeks: Int = 0,
    val coverImageUrl: String? = null,
    val updatedAt: String = "",
)

@Serializable
data class ExploreProgramLevelDto(
    val number: Int = 0,
    val code: String = "",
    val name: LocalizedNameDto = LocalizedNameDto(),
)

@Serializable
data class ExploreWorkoutDto(
    val id: String = "",
    val slug: String = "",
    val name: LocalizedNameDto = LocalizedNameDto(),
    val level: ExploreWorkoutLevelDto? = null,
    val estimatedDurationMin: Int? = null,
    val coverImageUrl: String? = null,
    val exerciseCount: Int = 0,
    val updatedAt: String = "",
)

@Serializable
data class ExploreWorkoutLevelDto(
    val number: Int = 0,
    val code: String = "",
    val name: LocalizedNameDto = LocalizedNameDto(),
)

@Serializable
data class ExploreExerciseDto(
    val id: String = "",
    val slug: String = "",
    val name: LocalizedNameDto = LocalizedNameDto(),
    val categoryCode: String? = null,
    val categoryName: LocalizedNameDto? = null,
    val musclesCount: Int = 0,
    val imageUrl: String? = null,
    val updatedAt: String = "",
)
