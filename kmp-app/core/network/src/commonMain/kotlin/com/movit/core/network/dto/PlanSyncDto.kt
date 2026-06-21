@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.movit.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames

/** Full sync payload alias used by [com.movit.core.data.sync.MovitSyncOrchestrator]. */
typealias MobileSyncFullApiResponse = MobileSyncApiResponse

@Serializable
data class EnrollProgramRequestDto(
    val programId: String,
)

@Serializable
data class MobileSyncApiResponse(
    val success: Boolean = false,
    val timestamp: String = "",
    val data: MobileSyncDataDto? = null,
    val meta: SyncMetaDto? = null,
    val error: String? = null,
)

@Serializable
data class MobileSyncDataDto(
    val exercises: List<JsonElement> = emptyList(),
    val messageLibrary: List<SyncMessageTemplateDto> = emptyList(),
    val systemMessages: List<SyncSystemMessageDto> = emptyList(),
    val deletedExerciseIds: List<String> = emptyList(),
    @JsonNames("workoutTemplates", "workouts")
    val workoutTemplates: List<JsonElement> = emptyList(),
    @JsonNames("deletedWorkoutTemplateIds", "deletedWorkoutIds")
    val deletedWorkoutTemplateIds: List<String> = emptyList(),
    val programs: List<JsonElement> = emptyList(),
    val deletedProgramIds: List<String> = emptyList(),
    val userPrograms: List<UserProgramExportDto> = emptyList(),
    val userExercisePreferences: List<UserExercisePreferenceSyncDto>? = null,
    @SerialName("plannedWorkoutReports")
    val plannedWorkoutReports: List<PlannedWorkoutReportExportDto> = emptyList(),
    val audioManifest: AudioManifestDto = AudioManifestDto(),
)

@Serializable
data class SyncMetaDto(
    val totalExercises: Int = 0,
    @JsonNames("totalWorkoutTemplates", "totalWorkouts")
    val totalWorkoutTemplates: Int = 0,
    val totalPrograms: Int = 0,
    val isFullSync: Boolean = false,
    val serverVersion: String = "",
    val exercisesInResponse: Int = 0,
    @JsonNames("workoutTemplatesInResponse", "workoutsInResponse")
    val workoutTemplatesInResponse: Int = 0,
    val programsInResponse: Int = 0,
    val messageLibraryStats: MessageLibraryStatsDto? = null,
)

@Serializable
data class MessageLibraryStatsDto(
    val totalMessages: Int = 0,
    val totalWithAudio: Int = 0,
    val totalAssignments: Int = 0,
    val fingerprint: String = "",
)

@Serializable
data class SyncMessageTemplateDto(
    val id: String = "",
    val code: String = "",
    val category: String = "",
    val context: String? = null,
    val content: LocalizedNameDto = LocalizedNameDto(),
)

@Serializable
data class SyncSystemMessageDto(
    val code: String = "",
    val content: LocalizedNameDto = LocalizedNameDto(),
    val updatedAt: String = "",
)

@Serializable
data class UserProgramExportDto(
    val id: String = "",
    val programId: String? = null,
    val name: LocalizedNameDto? = null,
    val startDate: String? = null,
    val isActive: Boolean = false,
    val customizations: JsonElement? = null,
    val updatedAt: String? = null,
    val pausedAt: String? = null,
    val totalPausedDays: Int = 0,
    val customizationsUpdatedAt: String? = null,
    val trainingWeekdays: List<Int>? = null,
)

@Serializable
data class UserExercisePreferenceSyncDto(
    val exerciseId: String = "",
    val exerciseSlug: String = "",
    val customReps: Int? = null,
    val customDurationSec: Int? = null,
    val customWeightKg: Double? = null,
    val updatedAt: String = "",
)

@Serializable
data class PlannedWorkoutReportExportDto(
    val id: String = "",
    val plannedWorkoutId: String = "",
    val programId: String = "",
    val weekNumber: Int = 0,
    val dayNumber: Int = 0,
    val startedAt: String = "",
    val completedAt: String = "",
    val status: String = "",
    val totalDurationMs: Int = 0,
    val totalExercises: Int = 0,
    val totalSets: Int = 0,
    val completedSets: Int = 0,
    val totalReps: Int = 0,
    val avgAccuracy: Double = 0.0,
    val avgFormScore: Double? = null,
    val rpe: Int? = null,
    val report: JsonElement? = null,
)
