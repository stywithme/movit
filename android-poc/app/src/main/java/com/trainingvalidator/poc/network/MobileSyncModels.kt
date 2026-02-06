package com.trainingvalidator.poc.network

import com.google.gson.annotations.SerializedName
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.WorkoutConfig
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.WorkoutType
import com.trainingvalidator.poc.training.models.ExecutionMode
import com.trainingvalidator.poc.training.models.WorkoutExercise
import com.trainingvalidator.poc.training.models.ReportMetricsConfig

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
    val deletedExerciseIds: List<String>,
    val workouts: List<WorkoutConfigWithMeta> = emptyList(),
    val deletedWorkoutIds: List<String> = emptyList(),
    val audioManifest: AudioManifest
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
    val hasPositionChecks: Boolean = false
) {
    /**
     * Convert to ExerciseConfig (without meta)
     */
    fun toExerciseConfig(): ExerciseConfig {
        return ExerciseConfig(
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
            fileName = slug  // Use slug as fileName for compatibility
        )
    }
}

/**
 * Sync metadata
 */
data class SyncMeta(
    val totalExercises: Int,
    val totalWorkouts: Int = 0,
    val isFullSync: Boolean,
    val serverVersion: String,
    val exercisesInResponse: Int,
    val workoutsInResponse: Int = 0
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
    val type: WorkoutType = WorkoutType.CIRCUIT,
    val executionMode: ExecutionMode = ExecutionMode.SEQUENTIAL,
    val repsPerSwitch: Int? = null,
    val rounds: Int = 1,
    val restBetweenExercisesMs: Long? = null,
    val restBetweenRoundsMs: Long = 60000,
    val restBetweenSwitchMs: Long? = null,
    val exercises: List<WorkoutExercise> = emptyList()
) {
    /**
     * Convert to WorkoutConfig (without meta)
     */
    fun toWorkoutConfig(): WorkoutConfig {
        return WorkoutConfig(
            name = name,
            description = description,
            type = type,
            executionMode = executionMode,
            repsPerSwitch = repsPerSwitch ?: 0,
            rounds = rounds,
            restBetweenExercisesMs = restBetweenExercisesMs ?: 10000L,
            restBetweenRoundsMs = restBetweenRoundsMs,
            restBetweenSwitchMs = restBetweenSwitchMs ?: 0L,
            exercises = exercises,
            fileName = slug  // Use slug as fileName for compatibility
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
