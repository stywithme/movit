package com.movit.core.network.dto

import kotlinx.serialization.Serializable

/** Full workout template from `/api/mobile/sync` `workoutTemplates[]` (backend `WorkoutExport`). */
@Serializable
data class WorkoutExportDto(
    val id: String = "",
    val slug: String = "",
    val name: LocalizedNameDto = LocalizedNameDto(),
    val description: LocalizedNameDto? = null,
    val coverImageUrl: String? = null,
    val estimatedDurationMin: Int? = null,
    val exercises: List<WorkoutExportExerciseDto> = emptyList(),
    val phases: List<WorkoutExportPhaseDto> = emptyList(),
    val updatedAt: String = "",
)

@Serializable
data class WorkoutExportPhaseDto(
    val id: String = "",
    val role: String = "MAIN",
    val name: LocalizedNameDto = LocalizedNameDto(),
    val sortOrder: Int = 0,
    val exercises: List<WorkoutExportExerciseDto> = emptyList(),
)

@Serializable
data class WorkoutExportExerciseDto(
    val exercise: String = "",
    /** Localized display name from backend (P3.6). Falls back to slug when absent. */
    val name: LocalizedNameDto? = null,
    val variantIndex: Int = 0,
    val targetReps: Int? = null,
    val targetDuration: Int? = null,
    val sets: Int = 1,
    val restBetweenSetsMs: Long = 30_000,
    val restAfterExerciseMs: Long = 60_000,
    val weightPerSet: List<Double>? = null,
)
