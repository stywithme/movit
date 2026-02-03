package com.trainingvalidator.poc.training.engine

import android.util.Log
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.models.*

/**
 * RepCounter - STATE-BASED Rep Counting and Scoring
 * 
 * This component is responsible for:
 * - Counting reps with score-based evaluation
 * - Tracking worst state reached per rep
 * - Recording errors per rep
 * - Providing rep results for analytics
 * 
 * SCORING SYSTEM:
 * - Score is based on WORST STATE reached during critical phases (BOTTOM/EXTENDED)
 * - PERFECT = 100, NORMAL = 60, PAD = 20, WARNING = 0, DANGER = 0
 * - DANGER also invalidates the rep
 * 
 * For HOLD exercises, score is calculated using WEIGHTED AVERAGE of time in states.
 */
class RepCounter(
    private val targetReps: Int = 12,
    repCountingConfig: RepCountingConfig? = null,
    private val isHoldExercise: Boolean = false
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
     * Current rep count (all reps)
     */
    var count: Int = 0
        private set
    
    /**
     * Number of counted reps (PERFECT/NORMAL/PAD)
     */
    var countedCount: Int = 0
        private set
    
    /**
     * Number of uncounted reps (WARNING - doesn't count but not invalidated)
     */
    var uncountedCount: Int = 0
        private set
    
    /**
     * Number of invalidated reps (DANGER)
     */
    var invalidatedCount: Int = 0
        private set
    
    /**
     * Total score of all counted reps (for averaging)
     */
    private var totalScore: Float = 0f
    
    /**
     * All rep results
     */
    private val _repResults = mutableListOf<RepResult>()
    val repResults: List<RepResult> get() = _repResults.toList()
    
    /**
     * Errors accumulated during current rep (angle-based)
     */
    private val currentRepErrors = mutableListOf<JointError>()
    
    /**
     * Position errors accumulated during current rep
     * Only ERROR severity position errors are tracked here
     */
    private val currentPositionErrors = mutableListOf<PositionError>()
    
    /**
     * Worst state reached during current rep
     */
    private var currentRepWorstState: JointState = JointState.PERFECT
    
    /**
     * For HOLD exercises: time tracking per state
     */
    private val stateTimeTracking = mutableMapOf<JointState, Long>()
    private var lastStateUpdateTime: Long = 0L
    private var currentTrackingState: JointState = JointState.PERFECT
    
    /**
     * Phase timings for current rep
     */
    private var currentPhaseTimings = mapOf<String, Long>()
    
    /**
     * Listener for rep count changes
     */
    var onRepCountChanged: ((Int, Float, Boolean) -> Unit)? = null  // count, score, isCounted
    
    /**
     * Listener for target reached
     */
    var onTargetReached: (() -> Unit)? = null
    
    /**
     * Flag to prevent multiple onTargetReached emissions
     */
    private var targetReachedEmitted = false
    
    // ==================== State Tracking ====================
    
    /**
     * Update the worst state during current rep
     * Called each frame during critical phases (BOTTOM/EXTENDED)
     */
    fun updateWorstState(state: JointState) {
        // Update worst state using JointState.isWorseThan
        if (state.isWorseThan(currentRepWorstState)) {
            currentRepWorstState = state
            Log.d(TAG, "Worst state updated to: $state")
        }
        
        // For HOLD exercises, track time in each state
        if (isHoldExercise) {
            updateStateTimeTracking(state)
        }
    }
    
    /**
     * Track time spent in each state (for HOLD exercises)
     */
    private fun updateStateTimeTracking(state: JointState) {
        val now = System.currentTimeMillis()
        
        if (lastStateUpdateTime > 0) {
            val duration = now - lastStateUpdateTime
            stateTimeTracking[currentTrackingState] = 
                (stateTimeTracking[currentTrackingState] ?: 0L) + duration
        }
        
        currentTrackingState = state
        lastStateUpdateTime = now
    }
    
    // NOTE: getStatePriority has been moved to JointState.priority
    
    // ==================== Error Tracking ====================
    
    /**
     * Add an error to current rep
     */
    fun addError(error: JointError) {
        val exists = currentRepErrors.any { 
            it.jointCode == error.jointCode && it.errorType == error.errorType 
        }
        if (!exists) {
            currentRepErrors.add(error)
        }
    }
    
    /**
     * Add a position error to current rep
     */
    fun addPositionError(error: PositionError) {
        if (error.severity != CheckSeverity.ERROR) return
        
        val exists = currentPositionErrors.any { it.checkId == error.checkId }
        if (!exists) {
            currentPositionErrors.add(error)
        }
    }
    
    /**
     * Set phase timings for current rep
     */
    fun setPhaseTimings(timings: Map<Phase, Long>) {
        currentPhaseTimings = timings.mapKeys { it.key.name.lowercase() }
    }
    
    /**
     * Get current worst state for motion recording
     * Called before completeRep() to capture state for analytics
     */
    fun getCurrentWorstState(): JointState = currentRepWorstState
    
    /**
     * Get pending score for motion recording
     * Calculates what the score will be before completing the rep
     */
    fun getPendingScore(): Float {
        return if (isHoldExercise) {
            // For hold exercises, calculate from time tracking
            updateStateTimeTracking(currentTrackingState)
            calculateHoldScore().first
        } else {
            // For rep exercises, use worst state rate
            StateConfig.getConfig(currentRepWorstState).rate.coerceAtLeast(0f)
        }
    }
    
    // ==================== Rep Completion ====================
    
    /**
     * Complete a rep with the tracked worst state
     * 
     * For Rep-based exercises: score = rate of worst state
     * For Hold exercises: score = weighted average of time in states
     */
    fun completeRep() {
        val now = System.currentTimeMillis()
        val timeSinceLastRep = now - lastRepTime
        
        // Safety check: prevent counting if too fast
        if (lastRepTime > 0 && timeSinceLastRep < minRepIntervalMs) {
            Log.w(TAG, "Rep completion rejected - too fast (${timeSinceLastRep}ms < ${minRepIntervalMs}ms)")
            return
        }
        
        lastRepTime = now
        count++
        
        // Calculate score based on exercise type
        val score: Float
        val isCounted: Boolean
        val isInvalidated: Boolean
        
        if (isHoldExercise) {
            // Finalize time tracking
            updateStateTimeTracking(currentTrackingState)
            
            // Calculate weighted average score
            val holdResult = calculateHoldScore()
            score = holdResult.first
            isInvalidated = holdResult.second
            isCounted = !isInvalidated && score > 0
        } else {
            // Rep-based: use worst state
            val config = StateConfig.getConfig(currentRepWorstState)
            score = config.rate.coerceAtLeast(0f)
            isCounted = config.isRepCounted
            isInvalidated = config.invalidatesRep
        }
        
        // Update counters
        if (isInvalidated) {
            invalidatedCount++
        } else if (isCounted) {
            countedCount++
            totalScore += score
        } else {
            uncountedCount++
        }
        
        // Create rep result
        val result = RepResult(
            repNumber = count,
            score = score,
            worstState = currentRepWorstState,
            isCounted = isCounted,
            isInvalidated = isInvalidated,
            errors = currentRepErrors.toList(),
            positionErrors = currentPositionErrors.toList(),
            phaseTimings = currentPhaseTimings
        )
        
        _repResults.add(result)
        
        Log.d(TAG, "Rep $count completed. Score: $score, WorstState: $currentRepWorstState, Counted: $isCounted, Invalidated: $isInvalidated")
        
        // Clear for next rep
        resetCurrentRepTracking()
        
        // Notify listeners
        onRepCountChanged?.invoke(count, score, isCounted)
        
        // Check if target reached
        if (count >= targetReps && !targetReachedEmitted) {
            targetReachedEmitted = true
            onTargetReached?.invoke()
        }
    }
    
    /**
     * Complete a rep with explicit worst state (alternative method)
     */
    fun completeRepWithState(worstState: JointState) {
        currentRepWorstState = worstState
        completeRep()
    }
    
    /**
     * Calculate score for HOLD exercises using weighted average
     * 
     * Formula: (TimeInPerfect * 1.0 + TimeInNormal * 0.6 + TimeInPad * 0.2) / TotalTime
     * 
     * @return Pair of (score, isInvalidated)
     */
    private fun calculateHoldScore(): Pair<Float, Boolean> {
        val perfectTime = stateTimeTracking[JointState.PERFECT] ?: 0L
        val normalTime = stateTimeTracking[JointState.NORMAL] ?: 0L
        val padTime = stateTimeTracking[JointState.PAD] ?: 0L
        val warningTime = stateTimeTracking[JointState.WARNING] ?: 0L
        val dangerTime = stateTimeTracking[JointState.DANGER] ?: 0L
        
        val totalTime = perfectTime + normalTime + padTime + warningTime + dangerTime
        
        // If DANGER occurred at any point, invalidate
        if (dangerTime > 0) {
            return Pair(0f, true)
        }
        
        if (totalTime == 0L) {
            return Pair(0f, false)
        }
        
        // Weighted average
        val weightedSum = (perfectTime * 1.0f) + (normalTime * 0.6f) + (padTime * 0.2f)
        val score = (weightedSum / totalTime) * 100f
        
        return Pair(score, false)
    }
    
    /**
     * Reset tracking for next rep
     */
    private fun resetCurrentRepTracking() {
        currentRepErrors.clear()
        currentPositionErrors.clear()
        currentPhaseTimings = emptyMap()
        currentRepWorstState = JointState.PERFECT
        stateTimeTracking.clear()
        lastStateUpdateTime = 0L
        currentTrackingState = JointState.PERFECT
    }
    
    // ==================== Query Methods ====================
    
    /**
     * Get average score of counted reps
     */
    fun getAverageScore(): Float {
        if (countedCount == 0) return 0f
        return totalScore / countedCount
    }
    
    /**
     * Get accuracy percentage (legacy - returns counted ratio * 100)
     */
    fun getAccuracy(): Float {
        if (count == 0) return 100f
        return (countedCount.toFloat() / count.toFloat()) * 100f
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
     * Get state breakdown across all reps
     */
    fun getStateBreakdown(): Map<JointState, Int> {
        return _repResults
            .groupingBy { it.worstState }
            .eachCount()
    }
    
    /**
     * Reset counter
     */
    fun reset() {
        count = 0
        countedCount = 0
        uncountedCount = 0
        invalidatedCount = 0
        totalScore = 0f
        _repResults.clear()
        resetCurrentRepTracking()
        lastRepTime = 0L
        targetReachedEmitted = false
    }
    
    /**
     * Has any rep been completed?
     */
    fun hasStarted(): Boolean = count > 0
    
    /**
     * Get the last completed rep result
     */
    fun getLastRepResult(): RepResult? {
        return _repResults.lastOrNull()
    }
    
    // ==================== Legacy Compatibility ====================
    
    /**
     * Legacy: correct count maps to counted count
     */
    val correctCount: Int get() = countedCount
    
    /**
     * Legacy: incorrect count maps to uncounted + invalidated
     */
    val incorrectCount: Int get() = uncountedCount + invalidatedCount
}
