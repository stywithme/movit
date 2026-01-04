package com.trainingvalidator.poc.training.engine

import android.util.Log
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.models.JointError
import com.trainingvalidator.poc.training.models.RepCountingConfig
import com.trainingvalidator.poc.training.models.RepResult

/**
 * RepCounter - Counts and tracks repetitions
 * 
 * This component is responsible for:
 * - Counting reps
 * - Recording errors per rep
 * - Tracking correct vs incorrect reps
 * - Providing rep results for analytics
 * 
 * Configurable Settings:
 *   - minRepIntervalMs: from exercise config or global default (safety backup)
 */
class RepCounter(
    private val targetReps: Int = 12,
    repCountingConfig: RepCountingConfig? = null
) {
    /**
     * Minimum time between reps (safety backup) - from exercise config or global default
     */
    private val minRepIntervalMs: Long = repCountingConfig?.getMinRepInterval(
        SettingsManager.getDefaultMinRepInterval()
    ) ?: SettingsManager.getDefaultMinRepInterval()
    
    companion object {
        private const val TAG = "RepCounter"
    }
    
    /**
     * Timestamp of last rep completion (backup safety check)
     */
    private var lastRepTime: Long = 0L
    
    /**
     * Current rep count
     */
    var count: Int = 0
        private set
    
    /**
     * Number of correct reps
     */
    var correctCount: Int = 0
        private set
    
    /**
     * Number of incorrect reps
     */
    var incorrectCount: Int = 0
        private set
    
    /**
     * All rep results
     */
    private val _repResults = mutableListOf<RepResult>()
    val repResults: List<RepResult> get() = _repResults.toList()
    
    /**
     * Errors accumulated during current rep
     */
    private val currentRepErrors = mutableListOf<JointError>()
    
    /**
     * Phase timings for current rep
     */
    private var currentPhaseTimings = mapOf<String, Long>()
    
    /**
     * Listener for rep count changes
     */
    var onRepCountChanged: ((Int, Boolean) -> Unit)? = null
    
    /**
     * Listener for target reached
     */
    var onTargetReached: (() -> Unit)? = null
    
    /**
     * Flag to prevent multiple onTargetReached emissions
     */
    private var targetReachedEmitted = false
    
    /**
     * Add an error to current rep
     */
    fun addError(error: JointError) {
        // Avoid duplicate errors for same joint in same rep
        val exists = currentRepErrors.any { 
            it.jointCode == error.jointCode && it.errorType == error.errorType 
        }
        if (!exists) {
            currentRepErrors.add(error)
        }
    }
    
    /**
     * Set phase timings for current rep
     */
    fun setPhaseTimings(timings: Map<Phase, Long>) {
        currentPhaseTimings = timings.mapKeys { it.key.name.lowercase() }
    }
    
    /**
     * Complete a rep (called when state machine detects rep completion)
     * 
     * Includes a safety check to prevent double counting in case
     * the state machine somehow calls this multiple times rapidly
     */
    fun completeRep() {
        val now = System.currentTimeMillis()
        val timeSinceLastRep = now - lastRepTime
        
        // Safety check: prevent counting if too fast (backup protection)
        if (lastRepTime > 0 && timeSinceLastRep < minRepIntervalMs) {
            Log.w(TAG, "Rep completion rejected - too fast (${timeSinceLastRep}ms < ${minRepIntervalMs}ms)")
            return
        }
        
        lastRepTime = now
        count++
        
        // Determine if rep was correct (no high-priority errors)
        val isCorrect = currentRepErrors.isEmpty()
        
        if (isCorrect) {
            correctCount++
        } else {
            incorrectCount++
        }
        
        // Create rep result
        val result = RepResult(
            repNumber = count,
            isCorrect = isCorrect,
            errors = currentRepErrors.toList(),
            phaseTimings = currentPhaseTimings
        )
        
        _repResults.add(result)
        
        Log.d(TAG, "Rep $count completed. Correct: $isCorrect, Errors: ${currentRepErrors.size}, Interval: ${timeSinceLastRep}ms")
        
        // Clear for next rep
        currentRepErrors.clear()
        currentPhaseTimings = emptyMap()
        
        // Notify listeners
        onRepCountChanged?.invoke(count, isCorrect)
        
        // Check if target reached (only emit once)
        if (count >= targetReps && !targetReachedEmitted) {
            targetReachedEmitted = true
            onTargetReached?.invoke()
        }
    }
    
    /**
     * Get accuracy percentage
     */
    fun getAccuracy(): Float {
        if (count == 0) return 100f
        return (correctCount.toFloat() / count.toFloat()) * 100f
    }
    
    /**
     * Get progress towards target (0.0 - 1.0)
     */
    fun getProgress(): Float {
        if (targetReps == 0) return 0f
        return (count.toFloat() / targetReps.toFloat()).coerceIn(0f, 1f)
    }
    
    /**
     * Check if target is reached
     */
    fun isTargetReached(): Boolean {
        return count >= targetReps
    }
    
    /**
     * Get remaining reps
     */
    fun getRemainingReps(): Int {
        return (targetReps - count).coerceAtLeast(0)
    }
    
    /**
     * Get most common errors across all reps
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
     * Reset counter
     */
    fun reset() {
        count = 0
        correctCount = 0
        incorrectCount = 0
        _repResults.clear()
        currentRepErrors.clear()
        currentPhaseTimings = emptyMap()
        lastRepTime = 0L
        targetReachedEmitted = false
    }
    
    /**
     * Has any rep been completed?
     */
    fun hasStarted(): Boolean = count > 0
    
    /**
     * Get the last completed rep result (for feedback events)
     */
    fun getLastRepResult(): RepResult? {
        return _repResults.lastOrNull()
    }
}
