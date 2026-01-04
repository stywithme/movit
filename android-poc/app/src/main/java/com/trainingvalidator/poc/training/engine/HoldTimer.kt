package com.trainingvalidator.poc.training.engine

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * HoldTimer - Manages timing for HOLD exercises
 * 
 * This component tracks:
 * - Time spent in hold position
 * - Grace period when user leaves position
 * - Completion and failure states
 * 
 * It receives updates from TrainingEngine based on PhaseStateMachine state
 * (Phase.COUNT = in hold zone, Phase.IDLE = out of hold zone)
 * 
 * State Flow:
 *   IDLE → HOLDING (entered hold zone)
 *   HOLDING → COMPLETED (reached target duration)
 *   HOLDING → GRACE_PERIOD (left hold zone)
 *   GRACE_PERIOD → HOLDING (returned within grace period)
 *   GRACE_PERIOD → FAILED (exceeded grace period)
 *   FAILED → IDLE (auto-reset)
 */
class HoldTimer(
    private val targetDurationMs: Long,
    private val gracePeriodMs: Long
) {
    
    companion object {
        private const val TAG = "HoldTimer"
    }
    
    // ==================== State ====================
    
    private val _state = MutableStateFlow(HoldState.IDLE)
    val state: StateFlow<HoldState> = _state
    
    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs
    
    private val _graceRemainingMs = MutableStateFlow<Long?>(null)
    val graceRemainingMs: StateFlow<Long?> = _graceRemainingMs
    
    // ==================== Callbacks ====================
    
    /**
     * Called when hold state changes
     */
    var onStateChanged: ((oldState: HoldState, newState: HoldState) -> Unit)? = null
    
    /**
     * Called when hold is started (first entry to hold zone)
     */
    var onHoldStarted: (() -> Unit)? = null
    
    /**
     * Called when grace period starts
     */
    var onGraceStarted: ((elapsedMs: Long, gracePeriodMs: Long) -> Unit)? = null
    
    /**
     * Called when user returns from grace period
     */
    var onGraceResumed: ((elapsedMs: Long, gracePeriodsUsed: Int) -> Unit)? = null
    
    /**
     * Called when hold is completed successfully
     */
    var onCompleted: ((totalMs: Long, gracePeriodsUsed: Int) -> Unit)? = null
    
    /**
     * Called when hold fails (exceeded grace period)
     */
    var onFailed: ((elapsedMs: Long, gracePeriodsUsed: Int) -> Unit)? = null
    
    // ==================== Internal Tracking ====================
    
    /**
     * Time when hold started (first entry to hold zone)
     */
    private var holdStartTime: Long = 0L
    
    /**
     * Accumulated time in hold zone (excluding grace periods)
     */
    private var accumulatedHoldMs: Long = 0L
    
    /**
     * Time when current hold segment started
     * (reset after each grace period return)
     */
    private var currentSegmentStartTime: Long = 0L
    
    /**
     * Time when grace period started
     */
    private var graceStartTime: Long = 0L
    
    /**
     * Number of grace periods triggered
     */
    private var gracePeriodCount: Int = 0
    
    /**
     * Flag to track if hold has been started at least once
     */
    private var hasStarted: Boolean = false
    
    // ==================== Public API ====================
    
    /**
     * Update the timer based on current hold zone state
     * 
     * @param isInHoldZone Whether user is currently in hold position (Phase.COUNT)
     * @param currentTimeMs Current system time in milliseconds
     */
    fun update(isInHoldZone: Boolean, currentTimeMs: Long) {
        when (_state.value) {
            HoldState.IDLE -> {
                if (isInHoldZone) {
                    enterHoldZone(currentTimeMs)
                }
            }
            
            HoldState.HOLDING -> {
                if (isInHoldZone) {
                    // Continue holding - update elapsed time
                    val currentSegmentTime = currentTimeMs - currentSegmentStartTime
                    val totalElapsed = accumulatedHoldMs + currentSegmentTime
                    _elapsedMs.value = totalElapsed
                    
                    // Check if target reached
                    if (totalElapsed >= targetDurationMs) {
                        complete(currentTimeMs)
                    }
                } else {
                    // Left hold zone - start grace period
                    startGracePeriod(currentTimeMs)
                }
            }
            
            HoldState.GRACE_PERIOD -> {
                if (isInHoldZone) {
                    // Returned to hold zone within grace period
                    resumeFromGrace(currentTimeMs)
                } else {
                    // Still in grace period - update remaining time
                    val graceElapsed = currentTimeMs - graceStartTime
                    val graceRemaining = gracePeriodMs - graceElapsed
                    _graceRemainingMs.value = graceRemaining.coerceAtLeast(0)
                    
                    // Check if grace period exceeded
                    if (graceRemaining <= 0) {
                        fail(currentTimeMs)
                    }
                }
            }
            
            HoldState.COMPLETED, HoldState.FAILED -> {
                // Terminal states - do nothing until reset
            }
        }
    }
    
    /**
     * Reset the timer to initial state
     */
    fun reset() {
        val oldState = _state.value
        
        _state.value = HoldState.IDLE
        _elapsedMs.value = 0L
        _graceRemainingMs.value = null
        
        holdStartTime = 0L
        accumulatedHoldMs = 0L
        currentSegmentStartTime = 0L
        graceStartTime = 0L
        gracePeriodCount = 0
        hasStarted = false
        
        if (oldState != HoldState.IDLE) {
            Log.d(TAG, "Reset from $oldState to IDLE")
        }
    }
    
    /**
     * Get progress towards target (0.0 - 1.0)
     */
    fun getProgress(): Float {
        if (targetDurationMs <= 0) return 0f
        return (_elapsedMs.value.toFloat() / targetDurationMs.toFloat()).coerceIn(0f, 1f)
    }
    
    /**
     * Get remaining time in milliseconds
     */
    fun getRemainingMs(): Long {
        return (targetDurationMs - _elapsedMs.value).coerceAtLeast(0)
    }
    
    /**
     * Get target duration
     */
    fun getTargetDurationMs(): Long = targetDurationMs
    
    /**
     * Get grace period duration
     */
    fun getGracePeriodMs(): Long = gracePeriodMs
    
    /**
     * Get number of grace periods triggered
     */
    fun getGracePeriodCount(): Int = gracePeriodCount
    
    /**
     * Check if hold has been started
     */
    fun hasStarted(): Boolean = hasStarted
    
    /**
     * Check if hold is completed
     */
    fun isCompleted(): Boolean = _state.value == HoldState.COMPLETED
    
    /**
     * Check if hold has failed
     */
    fun isFailed(): Boolean = _state.value == HoldState.FAILED
    
    /**
     * Check if currently in grace period
     */
    fun isInGracePeriod(): Boolean = _state.value == HoldState.GRACE_PERIOD
    
    /**
     * Check if actively holding
     */
    fun isHolding(): Boolean = _state.value == HoldState.HOLDING
    
    // ==================== Private Methods ====================
    
    private fun enterHoldZone(currentTimeMs: Long) {
        val oldState = _state.value
        _state.value = HoldState.HOLDING
        
        if (!hasStarted) {
            // First entry to hold zone
            hasStarted = true
            holdStartTime = currentTimeMs
            accumulatedHoldMs = 0L
            
            Log.d(TAG, "Hold started (first entry)")
            onHoldStarted?.invoke()
        }
        
        currentSegmentStartTime = currentTimeMs
        _graceRemainingMs.value = null
        
        Log.d(TAG, "Entered hold zone: $oldState → HOLDING")
        onStateChanged?.invoke(oldState, HoldState.HOLDING)
    }
    
    private fun startGracePeriod(currentTimeMs: Long) {
        val oldState = _state.value
        
        // Save accumulated time before grace
        val currentSegmentTime = currentTimeMs - currentSegmentStartTime
        accumulatedHoldMs += currentSegmentTime
        _elapsedMs.value = accumulatedHoldMs
        
        // Start grace period
        _state.value = HoldState.GRACE_PERIOD
        graceStartTime = currentTimeMs
        gracePeriodCount++
        _graceRemainingMs.value = gracePeriodMs
        
        Log.d(TAG, "Grace period started (count: $gracePeriodCount, elapsed: ${accumulatedHoldMs}ms)")
        
        onStateChanged?.invoke(oldState, HoldState.GRACE_PERIOD)
        onGraceStarted?.invoke(accumulatedHoldMs, gracePeriodMs)
    }
    
    private fun resumeFromGrace(currentTimeMs: Long) {
        val oldState = _state.value
        _state.value = HoldState.HOLDING
        
        // Start new segment
        currentSegmentStartTime = currentTimeMs
        _graceRemainingMs.value = null
        
        Log.d(TAG, "Resumed from grace (gracePeriodsUsed: $gracePeriodCount, elapsed: ${accumulatedHoldMs}ms)")
        
        onStateChanged?.invoke(oldState, HoldState.HOLDING)
        onGraceResumed?.invoke(accumulatedHoldMs, gracePeriodCount)
    }
    
    private fun complete(currentTimeMs: Long) {
        val oldState = _state.value
        _state.value = HoldState.COMPLETED
        
        // Final elapsed time
        val currentSegmentTime = currentTimeMs - currentSegmentStartTime
        val totalElapsed = accumulatedHoldMs + currentSegmentTime
        _elapsedMs.value = totalElapsed
        _graceRemainingMs.value = null
        
        Log.d(TAG, "★ Hold COMPLETED! (totalMs: $totalElapsed, gracePeriodsUsed: $gracePeriodCount)")
        
        onStateChanged?.invoke(oldState, HoldState.COMPLETED)
        onCompleted?.invoke(totalElapsed, gracePeriodCount)
    }
    
    private fun fail(currentTimeMs: Long) {
        val oldState = _state.value
        _state.value = HoldState.FAILED
        _graceRemainingMs.value = 0L
        
        Log.d(TAG, "✗ Hold FAILED! (elapsedMs: ${accumulatedHoldMs}, gracePeriodsUsed: $gracePeriodCount)")
        
        onStateChanged?.invoke(oldState, HoldState.FAILED)
        onFailed?.invoke(accumulatedHoldMs, gracePeriodCount)
    }
}

/**
 * HoldState - Internal state for hold timer
 * 
 * Note: This is separate from Phase enum to avoid modifying the shared state machine.
 * PhaseStateMachine continues to use Phase.IDLE/Phase.COUNT for hold exercises.
 */
enum class HoldState {
    /**
     * Waiting for user to enter hold position
     */
    IDLE,
    
    /**
     * User is in hold position, timer is running
     */
    HOLDING,
    
    /**
     * User left hold position temporarily, grace period countdown active
     */
    GRACE_PERIOD,
    
    /**
     * User held for target duration - success!
     */
    COMPLETED,
    
    /**
     * User exceeded grace period - fail
     */
    FAILED
}
