package com.movit.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Local workout template JSON (deep-link / offline quick-start).
 * Replaces legacy [com.movit.training.models.WorkoutConfig].
 */
@Serializable
data class WorkoutFlowConfigDto(
    val name: LocalizedNameDto = LocalizedNameDto(),
    val description: LocalizedNameDto? = null,
    val coverImageUrl: String? = null,
    val levelId: String? = null,
    val level: WorkoutFlowLevelDto? = null,
    val estimatedDurationMin: Int? = null,
    val tags: List<String> = emptyList(),
    val exercises: List<WorkoutFlowExerciseDto> = emptyList(),
    val phases: List<WorkoutFlowPhaseDto> = emptyList(),
) {
    fun effectiveExercises(): List<WorkoutFlowExerciseDto> =
        if (phases.isNotEmpty()) {
            phases.sortedBy { it.sortOrder }.flatMap { it.exercises }
        } else {
            exercises
        }
}

@Serializable
data class WorkoutFlowLevelDto(
    val id: String = "",
    val number: Int = 0,
    val code: String = "",
    val name: LocalizedNameDto = LocalizedNameDto(),
)

@Serializable
data class WorkoutFlowPhaseDto(
    val id: String? = null,
    val phaseId: String? = null,
    val slug: String? = null,
    val role: String = "MAIN",
    val name: LocalizedNameDto = LocalizedNameDto(en = "Phase", ar = "مرحلة"),
    val description: LocalizedNameDto? = null,
    val canSkip: Boolean = false,
    val canContinue: Boolean = true,
    val maxContinueTimeMs: Long? = null,
    val sortOrder: Int = 0,
    val exercises: List<WorkoutFlowExerciseDto> = emptyList(),
)

@Serializable
data class WorkoutFlowExerciseDto(
    val exercise: String,
    val variantIndex: Int = 0,
    val targetReps: Int? = null,
    val targetDuration: Int? = null,
    val sets: Int = 1,
    val restBetweenSetsMs: Long = 30_000,
    val restAfterExerciseMs: Long = 60_000,
)
