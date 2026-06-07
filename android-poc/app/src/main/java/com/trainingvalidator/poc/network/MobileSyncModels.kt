package com.trainingvalidator.poc.network

import android.util.Log

import com.google.gson.annotations.SerializedName
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.WorkoutConfig
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.WorkoutExercise
import com.trainingvalidator.poc.training.models.ReportMetricsConfig
import com.trainingvalidator.poc.training.models.BilateralConfig
import com.trainingvalidator.poc.training.models.ProgramConfig
import com.trainingvalidator.poc.training.models.ProgramWeek

/**
 * Mobile Sync API Response Models
 * 
 * These models match the backend API response structure.
 */

/**
 * Main sync response from /api/mobile/sync
 */
data class MobileSyncResponse(
    val success: Boolean,
    val timestamp: String,
    val data: SyncData?,
    val meta: SyncMeta?,
    val error: String? = null
)

/**
 * Sync data containing exercises, workouts, and audio manifest
 */
data class SyncData(
    val exercises: List<ExerciseConfigWithMeta>,
    val messageLibrary: List<MessageTemplate> = emptyList(),
    /** Fixed-key system messages (training TTS/UI); looked up by code */
    val systemMessages: List<SystemMessageTemplate> = emptyList(),
    val deletedExerciseIds: List<String>,
    @SerializedName(value = "workoutTemplates", alternate = ["workouts"])
    val workoutTemplates: List<WorkoutConfigWithMeta> = emptyList(),
    @SerializedName(value = "deletedWorkoutTemplateIds", alternate = ["deletedWorkoutIds"])
    val deletedWorkoutTemplateIds: List<String> = emptyList(),
    val programs: List<ProgramConfigWithMeta> = emptyList(),
    val deletedProgramIds: List<String> = emptyList(),
    val userPrograms: List<UserProgramExport> = emptyList(),
    /** Per-user targets for standalone training; null when not authenticated */
    val userExercisePreferences: List<UserExercisePreferenceSync>? = null,
    @SerializedName("plannedWorkoutReports") val workoutReports: List<WorkoutReportExport> = emptyList(),
    val audioManifest: AudioManifest
)

/**
 * User-specific reps / hold duration / weight overrides (backend + sync).
 */
data class UserExercisePreferenceSync(
    val exerciseId: String,
    val exerciseSlug: String,
    val customReps: Int? = null,
    val customDurationSec: Int? = null,
    val customWeightKg: Double? = null,
    val updatedAt: String
)

/** Request body for PUT /api/mobile/exercise-preferences/:exerciseId */
data class UserExercisePreferenceUpsertRequest(
    val customReps: Int? = null,
    val customDurationSec: Int? = null,
    val customWeightKg: Float? = null
)

data class ExercisePreferenceApiResponse(
    val success: Boolean,
    val data: UserExercisePreferenceSync? = null,
    val error: String? = null
)

/**
 * Completed planned-workout report from the backend.
 * Used to sync training history to the mobile app.
 */
data class WorkoutReportExport(
    val id: String,
    val plannedWorkoutId: String,
    val programId: String,
    val weekNumber: Int,
    val dayNumber: Int,
    val startedAt: String,
    val completedAt: String,
    val status: String,
    val totalDurationMs: Int = 0,
    val totalExercises: Int = 0,
    val totalSets: Int = 0,
    val completedSets: Int = 0,
    val totalReps: Int = 0,
    val avgAccuracy: Double = 0.0,
    val avgFormScore: Double? = null,
    val rpe: Int? = null,
    val report: Any? = null
)

data class UserProgramExport(
    val id: String,
    val programId: String? = null,
    val name: LocalizedText? = null,
    val startDate: String,
    val isActive: Boolean,
    val customizations: Map<String, Any>? = null,
    val updatedAt: String,
    val pausedAt: String? = null,
    val totalPausedDays: Int = 0,
    val customizationsUpdatedAt: String? = null
)

/**
 * Exercise config with additional metadata from server
 */
data class ExerciseConfigWithMeta(
    // Metadata from server
    val id: String,
    val slug: String,
    val updatedAt: String,
    
    // ExerciseConfig fields (copied from ExerciseConfig)
    val name: com.trainingvalidator.poc.training.models.LocalizedText,
    val description: com.trainingvalidator.poc.training.models.LocalizedText? = null,
    val instructions: com.trainingvalidator.poc.training.models.LocalizedText? = null,
    val imageUrl: String? = null,
    val category: com.trainingvalidator.poc.training.models.CategoryInfo,
    val countingMethod: com.trainingvalidator.poc.training.models.CountingMethod,
    val muscles: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val poseVariants: List<com.trainingvalidator.poc.training.models.PoseVariant> = emptyList(),
    val repCountingConfig: com.trainingvalidator.poc.training.models.RepCountingConfig = com.trainingvalidator.poc.training.models.RepCountingConfig(),
    
    // ═══════════════════════════════════════════════════════════════
    // WEIGHT & METRICS CONFIGURATION (from server)
    // ═══════════════════════════════════════════════════════════════
    
    /** Does this exercise support weights? */
    val supportsWeight: Boolean = false,
    
    /** Weight limits (kg) */
    val minWeight: Float? = null,
    val maxWeight: Float? = null,
    val defaultWeight: Float? = null,
    
    /** Report metrics configuration */
    val reportMetrics: ReportMetricsConfig? = null,
    
    /** Is this exercise bilateral (has paired joints)? - auto-detected by server */
    val isBilateral: Boolean = false,
    
    /** Does this exercise have position checks? (for Alignment metric) */
    val hasPositionChecks: Boolean = false,

    /** Bilateral config (per-rep side alternation) */
    val bilateralConfig: BilateralConfig? = null
) {
    /**
     * Convert to ExerciseConfig (without meta)
     */
    fun toExerciseConfig(): ExerciseConfig {
        val config = ExerciseConfig(
            name = name,
            description = description,
            instructions = instructions,
            imageUrl = imageUrl,
            category = category,
            countingMethod = countingMethod,
            muscles = muscles,
            equipment = equipment,
            tags = tags,
            poseVariants = poseVariants,
            repCountingConfig = repCountingConfig,
            // Weight & Metrics
            supportsWeight = supportsWeight,
            minWeight = minWeight,
            maxWeight = maxWeight,
            defaultWeight = defaultWeight,
            reportMetrics = reportMetrics,
            hasPositionChecks = hasPositionChecks,
            isBilateral = isBilateral,
            bilateralConfig = bilateralConfig,
            fileName = slug  // Use slug as fileName for compatibility
        ).sanitizeGsonDefaults()
        val issues = config.validationIssues()
        if (issues.isNotEmpty()) {
            Log.w(TAG, "ExerciseConfig validation for slug=$slug: ${issues.joinToString("; ")}")
        }
        return config
    }

    companion object {
        private const val TAG = "ExerciseConfigWithMeta"
        /**
         * Build network meta from a cached [ExerciseConfig] (e.g. offline fallback or message re-resolve).
         */
        fun fromExerciseConfig(
            id: String,
            slug: String,
            updatedAt: String,
            config: ExerciseConfig
        ): ExerciseConfigWithMeta {
            return ExerciseConfigWithMeta(
                id = id,
                slug = slug,
                updatedAt = updatedAt,
                name = config.name,
                description = config.description,
                instructions = config.instructions,
                imageUrl = config.imageUrl,
                category = config.category,
                countingMethod = config.countingMethod,
                muscles = config.muscles,
                equipment = config.equipment,
                tags = config.tags,
                poseVariants = config.poseVariants,
                repCountingConfig = config.repCountingConfig,
                supportsWeight = config.supportsWeight,
                minWeight = config.minWeight,
                maxWeight = config.maxWeight,
                defaultWeight = config.defaultWeight,
                reportMetrics = config.reportMetrics,
                isBilateral = config.isBilateral,
                hasPositionChecks = config.hasPositionChecks,
                bilateralConfig = config.bilateralConfig
            )
        }
    }
}

/**
 * Sync metadata
 */
data class SyncMeta(
    val totalExercises: Int,
    @SerializedName(value = "totalWorkoutTemplates", alternate = ["totalWorkouts"])
    val totalWorkoutTemplates: Int = 0,
    val totalPrograms: Int = 0,
    val isFullSync: Boolean,
    val serverVersion: String,
    val exercisesInResponse: Int,
    @SerializedName(value = "workoutTemplatesInResponse", alternate = ["workoutsInResponse"])
    val workoutTemplatesInResponse: Int = 0,
    val programsInResponse: Int = 0,
    val messageLibraryStats: MessageLibraryStats? = null
)

/**
 * Global message stats from server — used to detect stale caches
 */
data class MessageLibraryStats(
    val totalMessages: Int = 0,
    val totalWithAudio: Int = 0,
    val totalAssignments: Int = 0,
    /** Server fingerprint for message/audio/assignment changes (beyond raw counts). */
    val fingerprint: String = ""
)

/**
 * Response wrapper for GET .../audio-manifest (exercise or workout).
 */
data class EntityAudioManifestApiResponse(
    val success: Boolean,
    val data: EntityAudioManifestPayload? = null,
    val error: String? = null
)

data class EntityAudioManifestPayload(
    val entityType: String,
    val slug: String,
    val timestamp: String,
    val filesInManifest: Int,
    val audioManifest: AudioManifest
)

/**
 * Workout config with additional metadata from server
 */
data class WorkoutConfigWithMeta(
    // Metadata from server
    val id: String,
    val slug: String,
    val updatedAt: String,
    
    // WorkoutConfig fields
    val name: LocalizedText,
    val description: LocalizedText? = null,
    val coverImageUrl: String? = null,
    val difficulty: String = "beginner",
    val estimatedDurationMin: Int? = null,
    val tags: List<String>? = emptyList(),
    val exercises: List<WorkoutExercise>? = emptyList()
) {
    /**
     * Convert to WorkoutConfig (without meta)
     */
    fun toWorkoutConfig(): WorkoutConfig {
        return WorkoutConfig(
            name = name,
            description = description,
            coverImageUrl = coverImageUrl,
            difficulty = difficulty,
            estimatedDurationMin = estimatedDurationMin,
            tags = tags ?: emptyList(),
            exercises = exercises ?: emptyList(),
            fileName = slug  // Use slug as fileName for compatibility
        )
    }
}

/**
 * Program config with additional metadata from server
 */
data class ProgramConfigWithMeta(
    val id: String,
    val slug: String,
    val updatedAt: String,
    val name: LocalizedText,
    val description: LocalizedText? = null,
    val coverImageUrl: String? = null,
    val durationWeeks: Int,
    val levelRangeMin: Int = 0,
    val levelRangeMax: Int = 0,
    val tags: List<String>? = emptyList(),
    val weeks: List<ProgramWeek>? = emptyList(),
    val weeklyWorkoutTarget: Int? = null,
    val estimatedWorkoutMinutes: Int? = null,
    val isFeatured: Boolean = false
) {
    fun toProgramConfig(): ProgramConfig {
        return ProgramConfig(
            id = id,
            slug = slug,
            name = name,
            description = description,
            coverImageUrl = coverImageUrl,
            durationWeeks = durationWeeks,
            levelRangeMin = levelRangeMin,
            levelRangeMax = levelRangeMax,
            tags = tags ?: emptyList(),
            weeksField = weeks ?: emptyList(),
            weeklyWorkoutTarget = weeklyWorkoutTarget,
            estimatedWorkoutMinutes = estimatedWorkoutMinutes,
            isFeatured = isFeatured
        )
    }
}

/**
 * Audio manifest for downloading audio files
 */
data class AudioManifest(
    val baseUrl: String,
    val files: List<AudioFileInfo>
)

/**
 * Audio file information
 */
data class AudioFileInfo(
    val filename: String,
    val url: String,
    val size: Long? = null,
    val language: String,  // "ar" or "en"
    val exerciseId: String? = null
)

/**
 * Message template for library-based feedback
 */
data class MessageTemplate(
    val id: String,
    val code: String,
    val category: String,
    val context: String? = null,
    val content: LocalizedText
)

/**
 * System message from sync (editable text/audio on server; immutable code).
 */
data class SystemMessageTemplate(
    val code: String,
    val content: LocalizedText,
    val updatedAt: String = ""
)
