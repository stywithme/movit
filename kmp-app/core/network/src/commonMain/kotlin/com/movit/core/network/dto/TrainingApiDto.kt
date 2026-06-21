package com.movit.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ── Training config (GET workout-templates/{id}/training-config) ───────────────

@Serializable
data class TrainingConfigApiResponse(
    val success: Boolean = false,
    val data: JsonElement? = null,
    val error: String? = null,
)

// ── Audio manifest (GET exercises|workout-templates/{slug}/audio-manifest) ───

@Serializable
data class EntityAudioManifestApiResponse(
    val success: Boolean = false,
    val data: EntityAudioManifestPayloadDto? = null,
    val error: String? = null,
)

@Serializable
data class EntityAudioManifestPayloadDto(
    val entityType: String = "",
    val slug: String = "",
    val timestamp: String = "",
    val filesInManifest: Int = 0,
    val audioManifest: AudioManifestDto = AudioManifestDto(),
)

@Serializable
data class AudioManifestDto(
    val baseUrl: String = "",
    val files: List<AudioFileInfoDto> = emptyList(),
)

@Serializable
data class AudioFileInfoDto(
    val filename: String = "",
    val url: String = "",
    val size: Long? = null,
    val language: String = "",
    val exerciseId: String? = null,
)

// ── Planned workout lifecycle ────────────────────────────────────────────────

@Serializable
data class PlannedWorkoutStartRequestDto(
    val programId: String? = null,
    val weekNumber: Int,
    val dayNumber: Int,
    val startedAt: Long? = null,
)

@Serializable
data class PlannedWorkoutCompleteRequestDto(
    val completedAt: Long? = null,
    val totalDurationMs: Int? = null,
    val totalExercises: Int? = null,
    val totalSets: Int? = null,
    val completedSets: Int? = null,
    val totalReps: Int? = null,
    val avgAccuracy: Float? = null,
    val avgFormScore: Float? = null,
    val rpe: Int? = null,
    val report: JsonElement? = null,
)

@Serializable
data class PlannedWorkoutApiResponse(
    val success: Boolean = false,
    val data: PlannedWorkoutReportDto? = null,
    val error: String? = null,
)

@Serializable
data class PlannedWorkoutReportDto(
    val id: String = "",
    val userId: String? = null,
    val programId: String? = null,
    val plannedWorkoutId: String = "",
    val weekNumber: Int = 0,
    val dayNumber: Int = 0,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val status: String = "",
    val totalDurationMs: Int? = null,
    val totalExercises: Int? = null,
    val totalSets: Int? = null,
    val completedSets: Int? = null,
    val totalReps: Int? = null,
    val avgAccuracy: Float? = null,
    val avgFormScore: Float? = null,
    val rpe: Int? = null,
    val report: JsonElement? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

// ── Workout execution upload (POST workout-executions) ───────────────────────

@Serializable
data class RepMetricsDto(
    val rom: Float,
    val symmetry: Float? = null,
    val stability: Float,
    val tempo: List<Int> = emptyList(),
    val velocity: Float? = null,
    val formScore: Float,
    val alignmentAccuracy: Float,
)

@Serializable
data class RepMetricsDataDto(
    val num: Int,
    val durationMs: Int,
    val worstState: Int,
    val score: Float,
    val weightKg: Float? = null,
    val side: String? = null,
    val metrics: RepMetricsDto,
)

@Serializable
data class ExecutionMetricsDto(
    val avgRom: Float,
    val avgSymmetry: Float? = null,
    val avgStability: Float,
    val avgTempo: List<Int> = emptyList(),
    val avgVelocity: Float? = null,
    val avgFormScore: Float,
    val avgAlignmentAccuracy: Float,
    val totalTUT: Int,
    val totalVolume: Float? = null,
    val maxWeight: Float? = null,
    val est1RM: Float? = null,
    val relativeStrength: Float? = null,
    val intensityPercentage: Float? = null,
    val formConsistency: Float? = null,
    val fatigueIndex: Float? = null,
)

/** Shared with POST workout-executions and explore executions[]. */
@Serializable
data class WorkoutExecutionUploadRequestDto(
    val id: String,
    val exerciseId: String,
    val timestamp: Long,
    val durationMs: Int,
    val totalReps: Int,
    val countedReps: Int,
    val invalidReps: Int,
    val weightKg: Float? = null,
    val weightUnit: String = "kg",
    val repMetrics: List<RepMetricsDataDto> = emptyList(),
    val executionMetrics: ExecutionMetricsDto,
    val context: String? = null,
    val workoutGroupId: String? = null,
    val workoutTemplateId: String? = null,
    val legacyReport: JsonElement? = null,
)

@Serializable
data class WorkoutExecutionApiResponse(
    val success: Boolean = false,
    val data: WorkoutExecutionResponseDto? = null,
    val error: String? = null,
)

@Serializable
data class WorkoutExecutionResponseDto(
    val id: String = "",
    val exerciseId: String = "",
    val timestamp: String = "",
    val durationMs: Int = 0,
    val totalReps: Int = 0,
    val countedReps: Int = 0,
    val invalidReps: Int = 0,
    val weightKg: Float? = null,
    val weightUnit: String = "kg",
    val executionMetrics: ExecutionMetricsDto? = null,
    val repMetrics: List<RepMetricsDataDto> = emptyList(),
)

// ── Explore workout upload (POST workout-executions/explore) ─────────────────

@Serializable
data class ExploreWorkoutUploadRequestDto(
    val workoutGroupId: String,
    val workoutTemplateId: String? = null,
    val isCustomized: Boolean? = null,
    val context: String,
    val executions: List<WorkoutExecutionUploadRequestDto>,
)

@Serializable
data class ExploreWorkoutApiResponse(
    val success: Boolean = false,
    val data: ExploreWorkoutResultDto? = null,
    val error: String? = null,
)

@Serializable
data class ExploreWorkoutResultDto(
    val workoutGroupId: String = "",
    val savedCount: Int = 0,
    val executions: List<ExploreWorkoutSavedExecutionDto> = emptyList(),
)

@Serializable
data class ExploreWorkoutSavedExecutionDto(
    val id: String = "",
    val exerciseId: String = "",
    val totalReps: Int = 0,
)
