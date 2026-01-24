package com.trainingvalidator.poc.network

import com.google.gson.annotations.SerializedName
import com.trainingvalidator.poc.training.models.ExerciseConfig

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
 * Sync data containing exercises and audio manifest
 */
data class SyncData(
    val exercises: List<ExerciseConfigWithMeta>,
    val deletedExerciseIds: List<String>,
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
    val category: com.trainingvalidator.poc.training.models.CategoryInfo,
    val countingMethod: com.trainingvalidator.poc.training.models.CountingMethod,
    val muscles: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val poseVariants: List<com.trainingvalidator.poc.training.models.PoseVariant> = emptyList(),
    val repCountingConfig: com.trainingvalidator.poc.training.models.RepCountingConfig = com.trainingvalidator.poc.training.models.RepCountingConfig()
) {
    /**
     * Convert to ExerciseConfig (without meta)
     */
    fun toExerciseConfig(): ExerciseConfig {
        return ExerciseConfig(
            name = name,
            description = description,
            instructions = instructions,
            category = category,
            countingMethod = countingMethod,
            muscles = muscles,
            equipment = equipment,
            tags = tags,
            poseVariants = poseVariants,
            repCountingConfig = repCountingConfig,
            fileName = slug  // Use slug as fileName for compatibility
        )
    }
}

/**
 * Sync metadata
 */
data class SyncMeta(
    val totalExercises: Int,
    val isFullSync: Boolean,
    val serverVersion: String,
    val exercisesInResponse: Int
)

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
