package com.movit.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class EffectivePlanApiResponse(
    val success: Boolean = false,
    val data: EffectivePlanPayloadDto? = null,
    val error: String? = null,
)

@Serializable
data class EffectivePlanPayloadDto(
    val userProgramId: String = "",
    val programId: String? = null,
    val weekNumber: Int = 1,
    val dayNumber: Int = 1,
    val plannedWorkouts: List<EffectivePlannedWorkoutDto> = emptyList(),
)

@Serializable
data class EffectivePlannedWorkoutDto(
    val id: String = "",
    val name: Map<String, String>? = null,
    val sortOrder: Int = 0,
    val workoutTemplateId: String? = null,
    val estimatedDurationMin: Int? = null,
    val items: List<EffectivePlanItemDto> = emptyList(),
)

@Serializable
data class EffectivePlanItemDto(
    val id: String = "",
    val type: String = "",
    val exerciseId: String? = null,
    val sets: Int? = null,
    val targetReps: Int? = null,
    val targetDuration: Int? = null,
    val restBetweenSetsMs: Int? = null,
    val weightPerSet: List<Double>? = null,
    val restDurationMs: Int? = null,
    val sortOrder: Int = 0,
    val phaseIndex: Int? = null,
    val phaseRole: String? = null,
    val skipped: Boolean? = null,
    val suggestion: EffectivePlanSuggestionDto? = null,
)

@Serializable
data class EffectivePlanSuggestionDto(
    val suggestedWeightKg: Double? = null,
    val suggestedReps: Int? = null,
    val suggestedSets: Int? = null,
    val suggestedDuration: Int? = null,
    val source: String? = null,
)

@Serializable
data class SubstitutionExercisesApiResponse(
    val success: Boolean = false,
    val data: List<SubstitutionExerciseDto>? = null,
    val error: String? = null,
)

@Serializable
data class SubstitutionExerciseDto(
    val id: String = "",
    val slug: String = "",
    val name: Map<String, String>? = null,
    val archetype: String? = null,
)

@Serializable
data class UserProgramUpdateRequest(
    val customizations: Map<String, List<EffectivePlannedWorkoutDto>> = emptyMap(),
)
