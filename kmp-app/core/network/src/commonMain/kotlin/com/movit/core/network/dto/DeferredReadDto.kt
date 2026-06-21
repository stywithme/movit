package com.movit.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class LevelProfileHistoryApiResponse(
    val success: Boolean = false,
    val data: List<LevelProfileDetailDto>? = null,
    val error: String? = null,
)

@Serializable
data class LevelsListApiResponse(
    val success: Boolean = false,
    val data: List<LevelDefinitionDto>? = null,
    val error: String? = null,
)

@Serializable
data class LevelDefinitionDto(
    val number: Int = 0,
    val code: String = "",
    val name: LocalizedNameDto = LocalizedNameDto(),
    val description: LocalizedNameDto? = null,
    val color: String? = null,
    val icon: String? = null,
    val entryThreshold: Double = 0.0,
    val defaults: LevelTrainingDefaultsDto? = null,
)

@Serializable
data class LevelTrainingDefaultsDto(
    val setsRange: LevelIntRangeDto? = null,
    val repsRange: LevelIntRangeDto? = null,
    val intensityGuide: String? = null,
    val restBetweenSetsMs: Int? = null,
    val workoutDurationRange: LevelIntRangeDto? = null,
    val weeklyFrequencyRange: LevelIntRangeDto? = null,
)

@Serializable
data class LevelIntRangeDto(
    val min: Int = 0,
    val max: Int = 0,
)

@Serializable
data class TodayPlanApiResponse(
    val success: Boolean = false,
    val data: TodayPlanDto? = null,
    val error: String? = null,
)

@Serializable
data class TodayPlanDto(
    val activePlanStatus: String = "",
    val currentProgram: TodayProgramDto? = null,
    val nextReassessment: NextReassessmentDto? = null,
    val isPaused: Boolean? = null,
    val isTrainingDay: Boolean? = null,
    val catchUpSuggestion: CatchUpSuggestionDto? = null,
)

@Serializable
data class TodayProgramDto(
    val name: Map<String, String> = emptyMap(),
    val weekNumber: Int = 0,
    val dayNumber: Int = 0,
    val dayType: String = "",
    val isRestDay: Boolean = false,
    val plannedWorkouts: List<TodayWorkoutDto> = emptyList(),
)

@Serializable
data class TodayWorkoutDto(
    val id: String = "",
    val name: Map<String, String> = emptyMap(),
    val estimatedDurationMin: Int? = null,
    val itemCount: Int = 0,
    val isCompleted: Boolean = false,
)

@Serializable
data class ProgressionHistoryApiResponse(
    val success: Boolean = false,
    val data: List<ProgressionEntryDto>? = null,
    val error: String? = null,
)

@Serializable
data class ProgressionEntryDto(
    val id: String = "",
    val field: String = "",
    val previousValue: Double = 0.0,
    val newValue: Double = 0.0,
    val reason: String = "",
    val appliedAt: String = "",
    val seen: Boolean = true,
    val axis: String? = null,
    val decisionType: String? = null,
)

@Serializable
data class ProgramPreviewApiResponse(
    val success: Boolean = false,
    val data: ProgramPreviewDto? = null,
    val error: String? = null,
)

@Serializable
data class ProgramPreviewDto(
    val id: String = "",
    val slug: String = "",
    val name: LocalizedNameDto = LocalizedNameDto(),
    val description: LocalizedNameDto? = null,
    val coverImageUrl: String? = null,
    val durationWeeks: Int = 0,
    val levelMinId: String? = null,
    val levelMaxId: String? = null,
    val levelRangeMin: Int? = null,
    val levelRangeMax: Int? = null,
    val totalExercisesInFirstWeek: Int = 0,
    val muscleGroups: List<String> = emptyList(),
    val weeks: List<ProgramExportWeekDto> = emptyList(),
    val updatedAt: String = "",
)
