package com.trainingvalidator.poc.video

import com.trainingvalidator.poc.training.models.DifficultyType
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.RepResult
import java.util.UUID

/**
 * VideoAnalysisResult - Data model for video analysis results
 * 
 * Stores all information about a completed video analysis session,
 * including reps, accuracy, errors, and metadata.
 * 
 * Uses Gson for serialization (already in project).
 */
data class VideoAnalysisResult(
    /** Unique identifier for this analysis */
    val id: String = UUID.randomUUID().toString(),
    
    /** Exercise identifier (file name) */
    val exerciseId: String,
    
    /** Exercise name (localized) */
    val exerciseName: LocalizedText,
    
    /** URI of the analyzed video */
    val videoUri: String,
    
    /** Video duration in milliseconds */
    val videoDurationMs: Long,
    
    /** Timestamp when analysis was performed */
    val analysisDate: Long = System.currentTimeMillis(),
    
    // ==================== Analysis Results ====================
    
    /** Total number of repetitions detected */
    val totalReps: Int,
    
    /** Number of correct repetitions */
    val correctReps: Int,
    
    /** Accuracy percentage (0.0 - 100.0) */
    val accuracy: Float,
    
    /** Difficulty level used for analysis */
    val difficulty: DifficultyType,
    
    /** Details for each repetition */
    val repDetails: List<RepResult>,
    
    /** Summary of common errors */
    val commonErrors: List<ErrorSummary>,
    
    // ==================== Hold Exercise Fields ====================
    
    /** Actual hold duration achieved (for HOLD exercises) */
    val holdDurationMs: Long? = null,
    
    /** Target hold duration (for HOLD exercises) */
    val holdTargetMs: Long? = null,
    
    /** Number of grace periods used (for HOLD exercises) */
    val gracePeriodsUsed: Int? = null,
    
    /** Whether the hold was completed successfully */
    val holdCompleted: Boolean? = null
) {
    
    /**
     * Check if this is a hold exercise result
     */
    fun isHoldExercise(): Boolean = holdTargetMs != null
    
    /**
     * Get formatted accuracy string
     */
    fun getFormattedAccuracy(): String = String.format("%.0f%%", accuracy)
    
    /**
     * Get formatted analysis date
     */
    fun getFormattedDate(): String {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(analysisDate))
    }
    
    /**
     * Get formatted video duration
     */
    fun getFormattedDuration(): String {
        val totalSeconds = videoDurationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
    
    /**
     * Get summary text
     */
    fun getSummaryText(): String {
        return if (isHoldExercise()) {
            val achieved = (holdDurationMs ?: 0) / 1000
            val target = (holdTargetMs ?: 0) / 1000
            "Held for ${achieved}s / ${target}s target"
        } else {
            "$correctReps / $totalReps correct (${getFormattedAccuracy()})"
        }
    }
}

/**
 * ErrorSummary - Summary of a specific error type
 */
data class ErrorSummary(
    /** Type/category of the error */
    val errorType: String,
    
    /** Number of times this error occurred */
    val count: Int,
    
    /** Human-readable message (localized) */
    val message: LocalizedText,
    
    /** Joint code associated with this error (if applicable) */
    val jointCode: String? = null
) {
    /**
     * Get formatted error text
     */
    fun getFormattedText(language: String = "en"): String {
        val msg = message.get(language)
        return "$msg (${count}x)"
    }
}

/**
 * Extension function to create VideoAnalysisResult from SessionSummary
 */
fun com.trainingvalidator.poc.training.models.SessionSummary.toVideoAnalysisResult(
    exerciseId: String,
    exerciseName: LocalizedText,
    videoUri: String,
    videoDurationMs: Long,
    difficulty: DifficultyType,
    holdDurationMs: Long? = null,
    holdTargetMs: Long? = null,
    gracePeriodsUsed: Int? = null,
    holdCompleted: Boolean? = null
): VideoAnalysisResult {
    // Convert common errors to ErrorSummary
    val errorSummaries = this.commonErrors.map { (errorType, count) ->
        ErrorSummary(
            errorType = errorType,
            count = count,
            message = LocalizedText(
                en = formatErrorMessage(errorType),
                ar = formatErrorMessageAr(errorType)
            )
        )
    }
    
    return VideoAnalysisResult(
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        videoUri = videoUri,
        videoDurationMs = videoDurationMs,
        totalReps = this.totalReps,
        correctReps = this.correctReps,
        accuracy = this.accuracy,
        difficulty = difficulty,
        repDetails = this.repDetails,
        commonErrors = errorSummaries,
        holdDurationMs = holdDurationMs,
        holdTargetMs = holdTargetMs,
        gracePeriodsUsed = gracePeriodsUsed,
        holdCompleted = holdCompleted
    )
}

/**
 * Format error type to human-readable message (English)
 */
private fun formatErrorMessage(errorType: String): String {
    return errorType
        .replace("_", " ")
        .split(" ")
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

/**
 * Format error type to human-readable message (Arabic)
 */
private fun formatErrorMessageAr(errorType: String): String {
    // Basic Arabic translations for common errors
    return when {
        errorType.contains("knee") -> "خطأ في الركبة"
        errorType.contains("hip") -> "خطأ في الورك"
        errorType.contains("elbow") -> "خطأ في المرفق"
        errorType.contains("shoulder") -> "خطأ في الكتف"
        errorType.contains("ankle") -> "خطأ في الكاحل"
        errorType.contains("back") -> "خطأ في الظهر"
        else -> formatErrorMessage(errorType)
    }
}
