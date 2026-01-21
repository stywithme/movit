package com.trainingvalidator.poc.training.models

import com.trainingvalidator.poc.training.engine.PositionError
import com.trainingvalidator.poc.training.report.PerformanceRating

// NOTE: TrainingSession class has been REMOVED as it duplicated RepCounter logic.
// Session state is managed by TrainingEngine and RepCounter.
// SessionSummary is used for the final report.

/**
 * RepResult - Result of a single repetition
 * 
 * STATE-BASED SCORING:
 * - score: 0-100 based on worst state (PERFECT=100, NORMAL=60, PAD=20, WARNING/DANGER=0)
 * - worstState: The worst JointState reached during the rep
 * - isCounted: Whether this rep counts toward the total
 * - isInvalidated: Whether this rep was invalidated by DANGER state
 * 
 * @param errors Angle-based errors from FormValidator
 * @param positionErrors Position-based errors from PositionValidator (severity: ERROR only)
 */
data class RepResult(
    val repNumber: Int,
    val score: Float,                      // 0-100 based on worst state
    val worstState: JointState,            // Worst state reached during rep
    val isCounted: Boolean,                // Whether this rep counts
    val isInvalidated: Boolean = false,    // Whether DANGER was reached
    val errors: List<JointError> = emptyList(),
    val positionErrors: List<PositionError> = emptyList(),
    val phaseTimings: Map<String, Long> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Get total error count (angle + position)
     */
    fun getTotalErrorCount(): Int = errors.size + positionErrors.size
    
    /**
     * Legacy compatibility - isCorrect maps to isCounted
     */
    val isCorrect: Boolean get() = isCounted
    
    companion object {
        /**
         * Create a RepResult from worst state
         */
        fun fromWorstState(
            repNumber: Int,
            worstState: JointState,
            errors: List<JointError> = emptyList(),
            positionErrors: List<PositionError> = emptyList(),
            phaseTimings: Map<String, Long> = emptyMap()
        ): RepResult {
            val config = StateConfig.getConfig(worstState)
            return RepResult(
                repNumber = repNumber,
                score = config.rate.coerceAtLeast(0f),
                worstState = worstState,
                isCounted = config.isRepCounted,
                isInvalidated = config.invalidatesRep,
                errors = errors,
                positionErrors = positionErrors,
                phaseTimings = phaseTimings
            )
        }
    }
}

/**
 * JointError - Error detected on a joint
 * 
 * STATE-BASED ERROR TRACKING:
 * - Includes the JointState for proper categorization
 * - Used in report generation to show ALL errors, not just worst state
 */
data class JointError(
    val jointCode: String,
    val errorType: ErrorType,
    val actualAngle: Double,
    val expectedMin: Double,
    val expectedMax: Double,
    val message: LocalizedText,
    val state: JointState = JointState.WARNING,  // The state that triggered this error
    val isPrimary: Boolean = true                // Whether this is from a primary joint
)

/**
 * Error type enum
 */
enum class ErrorType {
    TOO_HIGH,   // Angle above expected range
    TOO_LOW     // Angle below expected range
}

/**
 * SessionSummary - Final summary of training session
 * 
 * STATE-BASED METRICS:
 * - averageScore: Average score of counted reps (0-100)
 * - countedRatio: Ratio of counted reps to total reps
 * - stateBreakdown: Count of reps per worst state
 */
data class SessionSummary(
    val exerciseName: String,
    val totalReps: Int,
    val countedReps: Int,
    val invalidatedReps: Int,
    val averageScore: Float,
    val countedRatio: Float,
    val durationMs: Long,
    val stateBreakdown: Map<JointState, Int>,
    val commonErrors: Map<String, Int>,
    val repDetails: List<RepResult>
) {
    /**
     * Format duration as mm:ss
     */
    fun getFormattedDuration(): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / 1000) / 60
        return "%02d:%02d".format(minutes, seconds)
    }
    
    /**
     * Get performance rating based on score and counted ratio
     */
    fun getPerformanceRating(): PerformanceRating {
        return PerformanceRating.fromScoreAndRatio(averageScore, countedRatio)
    }
    
    /**
     * Legacy compatibility - accuracy maps to countedRatio * 100
     */
    val accuracy: Float get() = countedRatio * 100f
    
    /**
     * Legacy compatibility - correctReps maps to countedReps
     */
    val correctReps: Int get() = countedReps
    
    /**
     * Legacy compatibility - incorrectReps maps to totalReps - countedReps
     */
    val incorrectReps: Int get() = totalReps - countedReps
}

// NOTE: PerformanceRating enum has been moved to PostTrainingReport.kt
// Import it from: com.trainingvalidator.poc.training.report.PerformanceRating
