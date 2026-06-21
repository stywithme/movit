package com.movit.core.network.dto

import kotlinx.serialization.Serializable

/** Subset of GET workout-templates/{id}/training-config used for session preview UI. */
@Serializable
data class WorkoutTemplateTrainingConfigDto(
    val id: String = "",
    val slug: String = "",
    val name: Map<String, String> = emptyMap(),
    val description: Map<String, String>? = null,
    val coverImageUrl: String? = null,
    val estimatedDurationMin: Int? = null,
    val exercises: List<WorkoutTemplateExerciseDto> = emptyList(),
    val phases: List<WorkoutTemplatePhaseDto> = emptyList(),
)

@Serializable
data class WorkoutTemplatePhaseDto(
    val id: String = "",
    val role: String = "MAIN",
    val name: Map<String, String> = emptyMap(),
    val sortOrder: Int = 0,
    val exercises: List<WorkoutTemplateExerciseDto> = emptyList(),
)

@Serializable
data class WorkoutTemplateExerciseDto(
    val workoutExerciseId: String = "",
    val sortOrder: Int = 0,
    val variantIndex: Int = 0,
    val targetReps: Int? = null,
    val targetDuration: Int? = null,
    val sets: Int? = null,
    val restBetweenSetsMs: Long? = null,
    val restAfterExerciseMs: Long? = null,
    val weightPerSet: List<Double>? = null,
    val exercise: WorkoutTemplateEmbeddedExerciseDto = WorkoutTemplateEmbeddedExerciseDto(),
)

@Serializable
data class WorkoutTemplateEmbeddedExerciseDto(
    val id: String = "",
    val slug: String = "",
    val name: Map<String, String> = emptyMap(),
    val attributes: List<WorkoutTemplateAttributeDto> = emptyList(),
)

@Serializable
data class WorkoutTemplateAttributeDto(
    val code: String = "",
    val name: Map<String, String> = emptyMap(),
)
