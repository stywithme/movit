package com.trainingvalidator.poc.training.engine

import android.util.Log
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.models.*

/**
 * RepCounter - STATE-BASED Rep Counting and Scoring
 *
 * This component is responsible for:
 * - Counting reps with score-based evaluation
 * - Tracking joint states per rep (weighted scoring)
 * - Recording errors per rep
 * - Providing rep results for analytics
 *
 * SCORING SYSTEM (Updated):
 * - Score is calculated using WEIGHTED AVERAGE of all joint states
 * - Primary joints have weight 1.0, secondary joints have weight 0.3
 * - DANGER state adds a penalty (-15% per DANGER joint)
 * - Rates: PERFECT=100, NORMAL=80, PAD=60, WARNING=40, DANGER=0
 *
 * For HOLD exercises, score is calculated using WEIGHTED AVERAGE of time in states.
 */
class RepCounter(
    private val targetReps: Int = 12,
    repCountingConfig: RepCountingConfig? = null,
    private val isHoldExercise: Boolean = false,
    private val primaryJoints: Set<String> = emptySet(),
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    /**
     * Minimum time between reps (safety backup) - from exercise config or global default
     */
    private val minRepIntervalMs: Long = repCountingConfig?.getMinRepInterval(
        SettingsManager.getDefaultMinRepInterval()
    ) ?: SettingsManager.getDefaultMinRepInterval()

    companion object {
        private const val TAG = "RepCounter"
        private const val POSITION_ERROR_PENALTY = 15f
        private const val POSITION_WARNING_PENALTY = 6f
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
     * Total score of all completed reps (for averaging)
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
     * Only ERROR severity position errors are tracked here (full objects for report)
     */
    private val currentPositionErrors = mutableListOf<PositionError>()

    /**
     * Position check violation tracking per severity (for alignment metrics).
     * WARNING and TIP are tracked separately — they don't affect rep scoring
     * but provide valuable data for the Alignment Accuracy metric.
     *
     * We deduplicate by checkId so a warning that fires for 30+ frames
     * is counted once, not once per frame.
     */
    private val currentPositionWarningIds = mutableSetOf<String>()
    private val currentPositionTipIds = mutableSetOf<String>()

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
     * Track all joint states for weighted scoring.
     *
     * IMPORTANT: This is the *instantaneous* snapshot from the latest frame.
     * It is used only as fallback / for real-time UI display.
     */
    private var currentJointStates: Map<String, JointStateInfo> = emptyMap()

    /**
     * Accumulated worst state per joint across the entire current rep.
     *
     * When a rep completes the joints are typically in TRANSITION (moving
     * back to start position).  If we scored from the instantaneous
     * [currentJointStates] all joints would be TRANSITION → skipped →
     * totalWeight = 0 → score = 0%.
     *
     * Instead we keep the worst state each joint reached during the rep
     * and score from that.
     */
    private val repAccumulatedStates = mutableMapOf<String, JointStateInfo>()

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
     * NEW: Update with full joint state information for weighted scoring
     * This is the preferred method - provides more accurate scoring
     *
     * @param jointStates Map of joint code to its state info
     */
    fun updateJointStates(jointStates: Map<String, JointStateInfo>) {
        // Store instantaneous snapshot (real-time UI / fallback)
        currentJointStates = jointStates

        // ── Accumulate worst state per joint across the rep ──────
        for ((jointCode, stateInfo) in jointStates) {
            if (stateInfo.state == JointState.TRANSITION) continue

            val existing = repAccumulatedStates[jointCode]
            if (existing == null || stateInfo.state.isWorseThan(existing.state)) {
                repAccumulatedStates[jointCode] = stateInfo
            }
        }

        // Also update global worst state for backward compatibility
        val worstState = jointStates.values
            .map { it.state }
            .filter { it != JointState.TRANSITION }
            .maxByOrNull { it.priority } ?: JointState.PERFECT

        if (worstState.isWorseThan(currentRepWorstState)) {
            currentRepWorstState = worstState
        }

        // For HOLD exercises, track time in worst state
        if (isHoldExercise) {
            updateStateTimeTracking(worstState)
        }
    }

    /**
     * Track time spent in each state (for HOLD exercises)
     */
    private fun updateStateTimeTracking(state: JointState) {
        val now = timeProvider()

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
     * Add an error to current rep.
     *
     * Deduplicates by joint + direction, but keeps the most severe state.
     * If severity is equal, we keep the latest sample to preserve fresh angle/range.
     */
    fun addError(error: JointError) {
        val existingIndex = currentRepErrors.indexOfFirst {
            it.jointCode == error.jointCode && it.errorType == error.errorType
        }

        if (existingIndex < 0) {
            currentRepErrors.add(error)
            return
        }

        val existing = currentRepErrors[existingIndex]
        if (error.state.isWorseThan(existing.state) || error.state == existing.state) {
            currentRepErrors[existingIndex] = error
        }
    }

    /**
     * Add a position error to current rep (ERROR severity — affects rep scoring)
     */
    fun addPositionError(error: PositionError) {
        if (error.severity != CheckSeverity.ERROR) return

        val exists = currentPositionErrors.any { it.checkId == error.checkId }
        if (!exists) {
            currentPositionErrors.add(error)
        }
    }

    /**
     * Record a WARNING-severity position check violation for alignment metrics.
     * Deduplicated by checkId — counted once per rep regardless of frame count.
     * Does NOT affect rep scoring or counting — only tracked for analytics.
     */
    fun addPositionWarning(error: PositionError) {
        currentPositionWarningIds.add(error.checkId)
    }

    /**
     * Record a TIP-severity position check for alignment metrics.
     * Deduplicated by checkId — counted once per rep regardless of frame count.
     * Does NOT affect rep scoring or counting — only tracked for analytics.
     */
    fun addPositionTip(error: PositionError) {
        currentPositionTipIds.add(error.checkId)
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
            ScoreCalculator.calculateHoldScore(stateTimeTracking).score
        } else if (repAccumulatedStates.isNotEmpty()) {
            // Use accumulated worst-per-joint for accuracy
            ScoreCalculator.calculateRepScore(repAccumulatedStates, primaryJoints).score
        } else if (currentJointStates.isNotEmpty()) {
            // Fallback: instantaneous snapshot
            ScoreCalculator.calculateRepScore(currentJointStates, primaryJoints).score
        } else {
            // Fallback: use worst state rate
            ScoreCalculator.calculateScoreFromWorstState(currentRepWorstState)
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
        val now = timeProvider()
        val timeSinceLastRep = now - lastRepTime

        // Safety check: prevent counting if too fast
        if (lastRepTime > 0 && timeSinceLastRep < minRepIntervalMs) {
            Log.w(TAG, "Rep completion rejected - too fast (${timeSinceLastRep}ms < ${minRepIntervalMs}ms)")
            return
        }

        lastRepTime = now
        count++

        // Calculate score based on exercise type
        var score: Float
        var isCounted: Boolean
        var isInvalidated: Boolean
        var resultWorstState = currentRepWorstState

        if (isHoldExercise) {
            // Finalize time tracking
            updateStateTimeTracking(currentTrackingState)

            // Calculate weighted average score using ScoreCalculator
            val holdResult = ScoreCalculator.calculateHoldScore(stateTimeTracking)
            score = holdResult.score
            isInvalidated = holdResult.isInvalidated
            isCounted = !isInvalidated && score > 0
        } else if (repAccumulatedStates.isNotEmpty()) {
            // Rep-based with weighted scoring — uses accumulated worst per joint
            val repResult = ScoreCalculator.calculateRepScore(repAccumulatedStates, primaryJoints)
            score = repResult.score
            isCounted = repResult.isCounted
            isInvalidated = repResult.isInvalidated
            resultWorstState = repResult.worstState

            Log.d(TAG, "Weighted score: ${score.toInt()}%, worst=${repResult.worstState}, " +
                    "dangerJoints=${repResult.dangerJoints}, joints=${repAccumulatedStates.size}")
        } else if (currentJointStates.isNotEmpty()) {
            // Fallback: frame snapshot (should rarely happen)
            val repResult = ScoreCalculator.calculateRepScore(currentJointStates, primaryJoints)
            score = repResult.score
            isCounted = repResult.isCounted
            isInvalidated = repResult.isInvalidated
            resultWorstState = repResult.worstState

            Log.d(TAG, "Snapshot score: ${score.toInt()}%, worst=${repResult.worstState}")
        } else {
            // Fallback: Legacy worst state scoring
            val config = StateConfig.getConfig(currentRepWorstState)
            score = ScoreCalculator.getScoreRate(currentRepWorstState)
            isCounted = config.isRepCounted
            isInvalidated = config.invalidatesRep
        }

        // Position checks affect rep quality classification and score.
        val positionErrorPenalty = currentPositionErrors.size * POSITION_ERROR_PENALTY
        val positionWarningPenalty = currentPositionWarningIds.size * POSITION_WARNING_PENALTY
        if (positionErrorPenalty > 0f || positionWarningPenalty > 0f) {
            score = (score - positionErrorPenalty - positionWarningPenalty).coerceIn(0f, 100f)
        }
        if (currentPositionErrors.isNotEmpty()) {
            if (JointState.WARNING.isWorseThan(resultWorstState)) {
                resultWorstState = JointState.WARNING
            }
            // Position ERROR keeps rep completed but marks quality as non-correct.
            if (!isInvalidated) {
                isCounted = false
            }
        } else if (currentPositionWarningIds.isNotEmpty()) {
            if (JointState.PAD.isWorseThan(resultWorstState)) {
                resultWorstState = JointState.PAD
            }
        }

        // Update counters
        if (isInvalidated) {
            invalidatedCount++
        } else if (isCounted) {
            countedCount++
        } else {
            uncountedCount++
        }
        // Average score should reflect every completed rep.
        totalScore += score

        // Create rep result
        val result = RepResult(
            repNumber = count,
            score = score,
            worstState = resultWorstState,
            isCounted = isCounted,
            isInvalidated = isInvalidated,
            errors = currentRepErrors.toList(),
            positionErrors = currentPositionErrors.toList(),
            positionWarningCount = currentPositionWarningIds.size,
            positionTipCount = currentPositionTipIds.size,
            phaseTimings = currentPhaseTimings
        )

        _repResults.add(result)

        Log.d(TAG, "Rep $count completed. Score: $score, WorstState: $resultWorstState, Counted: $isCounted, Invalidated: $isInvalidated")

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

    // NOTE: Hold score calculation moved to ScoreCalculator.calculateHoldScore()

    /**
     * Reset tracking for next rep
     */
    private fun resetCurrentRepTracking() {
        currentRepErrors.clear()
        currentPositionErrors.clear()
        currentPositionWarningIds.clear()
        currentPositionTipIds.clear()
        currentPhaseTimings = emptyMap()
        currentRepWorstState = JointState.PERFECT
        stateTimeTracking.clear()
        lastStateUpdateTime = 0L
        currentTrackingState = JointState.PERFECT
        currentJointStates = emptyMap()
        repAccumulatedStates.clear()
    }

    // ==================== Query Methods ====================

    /**
     * Get average score across all completed reps
     */
    fun getAverageScore(): Float {
        if (count == 0) return 0f
        return totalScore / count
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
