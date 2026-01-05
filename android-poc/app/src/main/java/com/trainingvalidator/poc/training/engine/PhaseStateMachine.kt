package com.trainingvalidator.poc.training.engine

import android.util.Log
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.models.CountingMethod
import com.trainingvalidator.poc.training.models.DifficultyType
import com.trainingvalidator.poc.training.models.RepCountingConfig
import com.trainingvalidator.poc.training.models.TrackedJoint

/**
 * PhaseStateMachine - Manages exercise phases using upRange/downRange system
 * 
 * NEW LOGIC based on clear zones:
 * 
 *   180° ─────────────────────────
 *        │ ERROR: Too High        │
 *   ─────┼────────────────────────┤ upRange.max
 *        │                        │
 *        │  ✅ UP STATE           │
 *        │  (Start Position)      │
 *        │                        │
 *   ─────┼────────────────────────┤ upRange.min
 *        │                        │
 *        │  🔄 TRANSITION         │
 *        │  (Moving)              │
 *        │                        │
 *   ─────┼────────────────────────┤ downRange.max
 *        │                        │
 *        │  ✅ DOWN STATE         │
 *        │  (Target Position)     │
 *        │                        │
 *   ─────┼────────────────────────┤ downRange.min
 *        │ ERROR: Too Low         │
 *     0° ─────────────────────────
 * 
 * State Transitions:
 *   IDLE → START:  angle enters upRange
 *   START → DOWN:  angle exits upRange (< upRange.min)
 *   DOWN → BOTTOM: angle enters downRange (<= downRange.max)
 *   BOTTOM → UP:   angle exits downRange (> downRange.max)
 *   UP → START:    angle enters upRange (>= upRange.min) → REP COMPLETED!
 * 
 * Configurable Settings:
 *   - Global: hysteresis (from app_settings.json)
 *   - Smoothing: Handled centrally by AngleSmoother (Single Source of Truth)
 *   - Per-Exercise: minRepInterval, maxRepInterval (from exercise JSON)
 *   - Calculated: minPhaseDuration = minRepInterval / numberOfPhases
 */
class PhaseStateMachine(
    private val countingMethod: CountingMethod,
    private val primaryJoints: List<TrackedJoint>,
    private val difficulty: DifficultyType,
    private val repCountingConfig: RepCountingConfig? = null,
    private val numberOfPhases: Int = 4
) {
    
    companion object {
        private const val TAG = "PhaseStateMachine"
    }
    
    // ==================== Configurable Thresholds ====================
    
    /**
     * Hysteresis buffer from global settings (prevents flickering at boundaries)
     */
    private val hysteresis: Double = SettingsManager.getHysteresis()
    
    // NOTE: Smoothing is now handled centrally by AngleSmoother in TrainingEngine
    // PhaseStateMachine receives pre-smoothed angles for consistency with FormValidator
    
    /**
     * Minimum rep interval from exercise config or global default
     */
    private val minRepIntervalMs: Long = repCountingConfig?.getMinRepInterval(
        SettingsManager.getDefaultMinRepInterval()
    ) ?: SettingsManager.getDefaultMinRepInterval()
    
    /**
     * Minimum phase duration calculated from minRepInterval / numberOfPhases
     * or fallback to global default
     */
    private val minPhaseDurationMs: Long = repCountingConfig?.calculateMinPhaseDuration(
        numberOfPhases,
        SettingsManager.getDefaultMinPhaseDuration()
    ) ?: SettingsManager.getDefaultMinPhaseDuration()
    
    /**
     * Current phase of the exercise
     */
    var currentPhase: Phase = Phase.IDLE
        private set
    
    /**
     * Previous phase (for transition detection)
     */
    var previousPhase: Phase = Phase.IDLE
        private set
    
    /**
     * Timestamp when entered current phase
     */
    private var phaseEntryTime: Long = System.currentTimeMillis()
    
    /**
     * Phase timings for current rep (for analytics)
     */
    private val phaseTimings = mutableMapOf<Phase, Long>()
    
    /**
     * Listener for phase changes
     */
    var onPhaseChanged: ((Phase, Phase) -> Unit)? = null
    
    /**
     * Listener for rep completion
     */
    var onRepCompleted: (() -> Unit)? = null
    
    // Calculated thresholds from first primary joint
    private val upRangeMin: Double
    private val upRangeMax: Double
    private val downRangeMin: Double
    private val downRangeMax: Double
    
    /**
     * Flag to prevent counting the same rep multiple times
     * Reset when starting a new descent (leaving UP range)
     */
    private var repCountedThisCycle = false
    
    /**
     * Timestamp of last rep completion for cooldown
     */
    private var lastRepCompletedTime: Long = 0L
    
    
    init {
        val joint = primaryJoints.first()
        val upRange = joint.getUpRange(difficulty)
        val downRange = joint.getDownRange(difficulty)
        
        upRangeMin = upRange.min
        upRangeMax = upRange.max
        downRangeMin = downRange.min
        downRangeMax = downRange.max
        
        Log.d(TAG, "PhaseStateMachine initialized:")
        Log.d(TAG, "  Method: $countingMethod")
        Log.d(TAG, "  Difficulty: $difficulty")
        Log.d(TAG, "  upRange: $upRangeMin - $upRangeMax")
        Log.d(TAG, "  downRange: $downRangeMin - $downRangeMax")
        Log.d(TAG, "  Transition Zone: $downRangeMax - $upRangeMin")
        Log.d(TAG, "  Settings (configurable):")
        Log.d(TAG, "    Hysteresis: $hysteresis°")
        Log.d(TAG, "    Smoothing: Handled by central AngleSmoother")
        Log.d(TAG, "    Min Rep Interval: ${minRepIntervalMs}ms")
        Log.d(TAG, "    Min Phase Duration: ${minPhaseDurationMs}ms (calculated)")
    }
    
    /**
     * Update the state machine with new angles
     * 
     * NOTE: Expects pre-smoothed angles from AngleSmoother (via TrainingEngine)
     * This ensures consistency with FormValidator which uses the same smoothed angles.
     * 
     * @param primaryAngles Map of primary joint codes to their current (smoothed) angles
     * @return Current phase after update
     */
    fun update(primaryAngles: Map<String, Double>): Phase {
        if (primaryAngles.isEmpty() || primaryJoints.isEmpty()) {
            return currentPhase
        }
        
        // Calculate average angle of primary joints
        // NOTE: Angles are already smoothed by AngleSmoother
        val angle = calculateAverageAngle(primaryAngles)
        
        // Determine next phase based on counting method
        val nextPhase = when (countingMethod) {
            CountingMethod.UP_DOWN -> updateUpDown(angle)
            CountingMethod.PUSH_PULL -> updatePushPull(angle)
            CountingMethod.HOLD -> updateHold(angle)
        }
        
        // Handle phase transition
        if (nextPhase != currentPhase) {
            handlePhaseTransition(nextPhase)
        }
        
        return currentPhase
    }
    
    /**
     * Check if angle is in UP range (with hysteresis for exiting)
     */
    private fun isInUpRange(angle: Double, exiting: Boolean = false): Boolean {
        val min = if (exiting) upRangeMin - hysteresis else upRangeMin
        val max = upRangeMax + hysteresis
        return angle >= min && angle <= max
    }
    
    /**
     * Check if angle is in DOWN range (with hysteresis for exiting)
     */
    private fun isInDownRange(angle: Double, exiting: Boolean = false): Boolean {
        val min = downRangeMin - hysteresis
        val max = if (exiting) downRangeMax + hysteresis else downRangeMax
        return angle >= min && angle <= max
    }
    
    /**
     * Check if angle has left UP range (going down)
     */
    private fun hasLeftUpRange(angle: Double): Boolean {
        return angle < upRangeMin - hysteresis
    }
    
    /**
     * Check if angle has entered DOWN range
     */
    private fun hasEnteredDownRange(angle: Double): Boolean {
        return angle <= downRangeMax
    }
    
    /**
     * Check if angle has left DOWN range (going up)
     */
    private fun hasLeftDownRange(angle: Double): Boolean {
        return angle > downRangeMax + hysteresis
    }
    
    /**
     * Check if angle has entered UP range
     */
    private fun hasEnteredUpRange(angle: Double): Boolean {
        return angle >= upRangeMin
    }
    
    /**
     * Update for UP_DOWN counting method (Squat, Bicep Curl, etc.)
     * 
     * Flow: IDLE → START → DOWN → BOTTOM → UP → START (+1 rep)
     * 
     * NOTE: onRepCompleted is NOT called here - it's called in handlePhaseTransition
     * AFTER confirming the phase actually changed (passed MIN_PHASE_DURATION check)
     */
    private fun updateUpDown(angle: Double): Phase {
        return when (currentPhase) {
            Phase.IDLE -> {
                // Wait for user to get into UP position
                if (isInUpRange(angle)) {
                    Log.d(TAG, "Entered UP range: $angle (${upRangeMin}-${upRangeMax})")
                    Phase.START
                } else {
                    Phase.IDLE
                }
            }
            
            Phase.START -> {
                // User is in start position, wait for them to leave UP range
                if (hasLeftUpRange(angle)) {
                    Log.d(TAG, "Left UP range, starting descent: $angle")
                    // Reset the flag for new rep cycle
                    repCountedThisCycle = false
                    Phase.DOWN
                } else {
                    Phase.START
                }
            }
            
            Phase.DOWN -> {
                // User is moving down, wait for DOWN range
                when {
                    hasEnteredDownRange(angle) -> {
                        Log.d(TAG, "Entered DOWN range: $angle (${downRangeMin}-${downRangeMax})")
                        Phase.BOTTOM
                    }
                    isInUpRange(angle) -> {
                        // User went back without reaching target - reset
                        Log.d(TAG, "Returned to UP without reaching DOWN: $angle")
                        Phase.START
                    }
                    else -> Phase.DOWN
                }
            }
            
            Phase.BOTTOM -> {
                // User is at bottom, wait for them to leave DOWN range
                if (hasLeftDownRange(angle)) {
                    Log.d(TAG, "Left DOWN range, ascending: $angle")
                    Phase.UP
                } else {
                    Phase.BOTTOM
                }
            }
            
            Phase.UP -> {
                // User is going up, wait for UP range
                when {
                    hasEnteredUpRange(angle) -> {
                        // Request rep completion - will be validated in handlePhaseTransition
                        Log.d(TAG, "★ Requesting REP completion - Entered UP range: $angle")
                        Phase.START
                    }
                    isInDownRange(angle) -> {
                        // User went back to bottom
                        Log.d(TAG, "Returned to DOWN range: $angle")
                        Phase.BOTTOM
                    }
                    else -> Phase.UP
                }
            }
            
            else -> currentPhase
        }
    }
    
    /**
     * Update for PUSH_PULL counting method (Push-up, Bench Press)
     * 
     * Flow: IDLE → START → PUSH → EXTENDED → PULL → START (+1 rep)
     * 
     * Note: For push-pull, "UP" range is extended position, "DOWN" is bent
     * NOTE: onRepCompleted is NOT called here - it's called in handlePhaseTransition
     */
    private fun updatePushPull(angle: Double): Phase {
        return when (currentPhase) {
            Phase.IDLE -> {
                if (isInUpRange(angle)) Phase.START else Phase.IDLE
            }
            
            Phase.START -> {
                if (hasLeftUpRange(angle)) {
                    // Reset the flag for new rep cycle
                    repCountedThisCycle = false
                    Phase.PUSH
                } else Phase.START
            }
            
            Phase.PUSH -> {
                when {
                    hasEnteredDownRange(angle) -> Phase.EXTENDED
                    isInUpRange(angle) -> Phase.START
                    else -> Phase.PUSH
                }
            }
            
            Phase.EXTENDED -> {
                if (hasLeftDownRange(angle)) Phase.PULL else Phase.EXTENDED
            }
            
            Phase.PULL -> {
                when {
                    hasEnteredUpRange(angle) -> {
                        // Request rep completion - will be validated in handlePhaseTransition
                        Log.d(TAG, "★ Requesting REP completion (PUSH_PULL)")
                        Phase.START
                    }
                    isInDownRange(angle) -> Phase.EXTENDED
                    else -> Phase.PULL
                }
            }
            
            else -> currentPhase
        }
    }
    
    /**
     * Update for HOLD counting method (Plank, Wall Sit)
     * 
     * Uses downRange as the "hold zone" - timer runs when in range
     */
    private fun updateHold(angle: Double): Phase {
        return when (currentPhase) {
            Phase.IDLE -> {
                // Wait for user to get into hold position (DOWN range)
                if (isInDownRange(angle)) {
                    Log.d(TAG, "Entered HOLD zone: $angle")
                    Phase.COUNT
                } else {
                    Phase.IDLE
                }
            }
            
            Phase.COUNT -> {
                // Stay in COUNT while in hold zone
                if (!isInDownRange(angle, exiting = true)) {
                    Log.d(TAG, "Left HOLD zone: $angle")
                    Phase.IDLE
                } else {
                    Phase.COUNT
                }
            }
            
            else -> currentPhase
        }
    }
    
    /**
     * Handle phase transition
     * 
     * This is where rep completion is actually triggered, AFTER confirming:
     * 1. Minimum phase duration has passed
     * 2. Rep hasn't been counted this cycle
     * 3. Cooldown period has passed since last rep
     */
    private fun handlePhaseTransition(nextPhase: Phase) {
        val now = System.currentTimeMillis()
        val phaseDuration = now - phaseEntryTime
        
        // Only transition if minimum duration has passed (configurable per exercise)
        if (phaseDuration < minPhaseDurationMs) {
            Log.d(TAG, "Phase transition rejected - too fast (${phaseDuration}ms < ${minPhaseDurationMs}ms)")
            return
        }
        
        // Record timing
        phaseTimings[currentPhase] = phaseDuration
        
        // Check if this is a rep completion transition
        val isRepCompletionTransition = 
            (currentPhase == Phase.UP && nextPhase == Phase.START) ||
            (currentPhase == Phase.PULL && nextPhase == Phase.START)
        
        // Update phases FIRST
        previousPhase = currentPhase
        currentPhase = nextPhase
        phaseEntryTime = now
        
        Log.d(TAG, "Phase: $previousPhase → $currentPhase")
        
        // Handle rep completion if applicable
        if (isRepCompletionTransition) {
            val timeSinceLastRep = now - lastRepCompletedTime
            
            // Check all conditions before counting (using configurable minRepInterval)
            when {
                repCountedThisCycle -> {
                    Log.w(TAG, "Rep already counted this cycle - ignoring")
                }
                timeSinceLastRep < minRepIntervalMs -> {
                    Log.w(TAG, "Rep cooldown not passed (${timeSinceLastRep}ms < ${minRepIntervalMs}ms) - ignoring")
                }
                else -> {
                    // All checks passed - count the rep!
                    repCountedThisCycle = true
                    lastRepCompletedTime = now
                    Log.d(TAG, "★ REP COMPLETED! (validated)")
                    onRepCompleted?.invoke()
                }
            }
        }
        
        // Notify listener
        onPhaseChanged?.invoke(previousPhase, currentPhase)
    }
    
    /**
     * Calculate average angle from multiple joints
     */
    private fun calculateAverageAngle(angles: Map<String, Double>): Double {
        return angles.values.average()
    }
    
    /**
     * Get phase timings for current rep
     */
    fun getPhaseTimings(): Map<Phase, Long> {
        return phaseTimings.toMap()
    }
    
    /**
     * Clear phase timings (call after rep is recorded)
     */
    fun clearTimings() {
        phaseTimings.clear()
    }
    
    /**
     * Reset state machine to IDLE
     */
    fun reset() {
        previousPhase = currentPhase
        currentPhase = Phase.IDLE
        phaseTimings.clear()
        phaseEntryTime = System.currentTimeMillis()
        repCountedThisCycle = false
        lastRepCompletedTime = 0L
        // NOTE: Angle smoothing history is now managed by AngleSmoother
    }
    
    /**
     * Check if a rep was just completed
     * Handles both UP_DOWN (UP -> START) and PUSH_PULL (PULL -> START) transitions
     */
    fun wasRepJustCompleted(): Boolean {
        return (previousPhase == Phase.UP && currentPhase == Phase.START) ||
               (previousPhase == Phase.PULL && currentPhase == Phase.START)
    }
    
    /**
     * Get current zone info for debugging
     */
    fun getZoneInfo(angle: Double): String {
        return when {
            angle > upRangeMax -> "ERROR: Too High"
            angle >= upRangeMin -> "UP Zone"
            angle > downRangeMax -> "Transition"
            angle >= downRangeMin -> "DOWN Zone"
            else -> "ERROR: Too Low"
        }
    }
}

/**
 * Phase enum - All possible phases across counting methods
 */
enum class Phase {
    // Common
    IDLE,       // Waiting for user to get in position
    START,      // In starting position (ready to begin)
    
    // UP_DOWN specific
    DOWN,       // Moving towards target (going down)
    BOTTOM,     // At target position (bottom)
    UP,         // Returning to start (going up)
    
    // PUSH_PULL specific
    PUSH,       // Pushing motion (going towards target)
    EXTENDED,   // At extended/target position
    PULL,       // Pulling motion (returning)
    
    // HOLD specific
    COUNT       // In hold zone (timer running)
}
