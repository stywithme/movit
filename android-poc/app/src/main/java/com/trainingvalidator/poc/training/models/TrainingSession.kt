package com.trainingvalidator.poc.training.models

import com.trainingvalidator.poc.training.engine.PositionError

/**
 * TrainingSession - Holds the current training session state
 * 
 * This is the runtime state of an active training session.
 * It tracks reps, errors, and timing for the current exercise.
 */
data class TrainingSession(
    val exerciseName: String,
    val difficulty: DifficultyType,
    val targetReps: Int,
    val startTime: Long = System.currentTimeMillis()
) {
    // Rep tracking
    private val _repResults = mutableListOf<RepResult>()
    val repResults: List<RepResult> get() = _repResults.toList()
    
    // Current state
    var currentRep: Int = 0
        private set
    
    var correctReps: Int = 0
        private set
    
    var incorrectReps: Int = 0
        private set
    
    var isCompleted: Boolean = false
        private set
    
    var endTime: Long? = null
        private set
    
    /**
     * Add a completed rep
     */
    fun addRep(result: RepResult) {
        _repResults.add(result)
        currentRep++
        
        if (result.isCorrect) {
            correctReps++
        } else {
            incorrectReps++
        }
        
        // Check if target reached
        if (currentRep >= targetReps) {
            complete()
        }
    }
    
    /**
     * Mark session as completed
     */
    fun complete() {
        isCompleted = true
        endTime = System.currentTimeMillis()
    }
    
    /**
     * Get session duration in milliseconds
     */
    fun getDurationMs(): Long {
        val end = endTime ?: System.currentTimeMillis()
        return end - startTime
    }
    
    /**
     * Get accuracy percentage
     */
    fun getAccuracy(): Float {
        if (currentRep == 0) return 0f
        return (correctReps.toFloat() / currentRep.toFloat()) * 100f
    }
    
    /**
     * Get most common errors
     */
    fun getMostCommonErrors(): Map<String, Int> {
        return _repResults
            .flatMap { it.errors }
            .groupingBy { "${it.jointCode}:${it.errorType}" }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .toMap()
    }
    
    /**
     * Generate session summary
     */
    fun getSummary(): SessionSummary {
        return SessionSummary(
            exerciseName = exerciseName,
            difficulty = difficulty,
            totalReps = currentRep,
            correctReps = correctReps,
            incorrectReps = incorrectReps,
            accuracy = getAccuracy(),
            durationMs = getDurationMs(),
            commonErrors = getMostCommonErrors(),
            repDetails = repResults
        )
    }
}

/**
 * RepResult - Result of a single repetition
 * 
 * @param errors Angle-based errors from FormValidator
 * @param positionErrors Position-based errors from PositionValidator (severity: ERROR only)
 */
data class RepResult(
    val repNumber: Int,
    val isCorrect: Boolean,
    val errors: List<JointError> = emptyList(),
    val positionErrors: List<PositionError> = emptyList(),
    val phaseTimings: Map<String, Long> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Get total error count (angle + position)
     */
    fun getTotalErrorCount(): Int = errors.size + positionErrors.size
}

/**
 * JointError - Error detected on a joint
 */
data class JointError(
    val jointCode: String,
    val errorType: ErrorType,
    val actualAngle: Double,
    val expectedMin: Double,
    val expectedMax: Double,
    val message: LocalizedText
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
 */
data class SessionSummary(
    val exerciseName: String,
    val difficulty: DifficultyType,
    val totalReps: Int,
    val correctReps: Int,
    val incorrectReps: Int,
    val accuracy: Float,
    val durationMs: Long,
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
}
