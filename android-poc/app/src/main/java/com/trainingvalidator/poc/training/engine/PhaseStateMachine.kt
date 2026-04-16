package com.trainingvalidator.poc.training.engine

import android.util.Log
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.models.CountingMethod
import com.trainingvalidator.poc.training.models.RepCountingConfig
import com.trainingvalidator.poc.training.models.TrackedJoint

/**
 * PhaseStateMachine - Manages exercise phases using STATE-BASED ranges
 * 
 * Phase determination uses the OUTERMOST bounds of StateRanges to define zones.
 * Quality assessment (PERFECT/NORMAL/PAD/WARNING/DANGER) is handled by FormValidator.
 * 
 * Zone Layout (using StateRanges):
 * 
 *   180° ─────────────────────────
 *        │ Outside Range          │
 *   ─────┼────────────────────────┤ upRange.getEffectiveMax() (or danger.max)
 *        │                        │
 *        │  ✅ UP STATE           │
 *        │  (Start Position)      │
 *        │                        │
 *   ─────┼────────────────────────┤ upRange.getEffectiveMin() (pad/normal/perfect.min)
 *        │                        │
 *        │  🔄 TRANSITION         │
 *        │  (Moving)              │
 *        │                        │
 *   ─────┼────────────────────────┤ downRange.getEffectiveMax() (pad/normal/perfect.max)
 *        │                        │
 *        │  ✅ DOWN STATE         │
 *        │  (Target Position)     │
 *        │                        │
 *   ─────┼────────────────────────┤ downRange.getEffectiveMin() (or danger.min)
 *        │ Outside Range          │
 *     0° ─────────────────────────
 * 
 * NOTE: Difficulty level has been REMOVED. All users get the same phase thresholds.
 * Quality scoring is handled separately by FormValidator using JointState.
 */
class PhaseStateMachine(
    private val countingMethod: CountingMethod,
    private val primaryJoints: List<TrackedJoint>,
    private val repCountingConfig: RepCountingConfig? = null,
    private val numberOfPhases: Int = 4,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    
    companion object {
        private const val TAG = "PhaseStateMachine"
    }
    
    // ==================== Configurable Thresholds ====================
    
    /**
     * Hysteresis buffer from global settings (prevents flickering at boundaries)
     */
    private val hysteresis: Double = SettingsManager.getHysteresis()
    
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
     * Initialize to 0 - will be set properly on first update()
     */
    private var phaseEntryTime: Long = 0L
    
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
    
    /** Primary joints with up/down ranges (rep counting uses these for UP_DOWN strict mode). */
    private val upDownPrimaryJoints: List<TrackedJoint> =
        primaryJoints.filter { it.hasStateUpDownRanges() }
    
    /** Primary joints with hold range only (HOLD strict mode). */
    private val holdPrimaryJoints: List<TrackedJoint> =
        primaryJoints.filter { it.hasStateHoldRange() }
    
    // Calculated thresholds aggregated across primary joints' StateRanges (debug + legacy fallback)
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
        // Aggregate thresholds for logging and legacy fallback when no per-joint ranges exist.
        val upDownJoints = upDownPrimaryJoints
        val holdRangeJoints = holdPrimaryJoints

        if (upDownJoints.isNotEmpty()) {
            upRangeMin = upDownJoints.map { it.getStateUpRange().effectiveMin }.average()
            upRangeMax = upDownJoints.map { it.getStateUpRange().outermostMax }.average()
            downRangeMin = upDownJoints.map { it.getStateDownRange().outermostMin }.average()
            downRangeMax = upDownJoints.map { it.getStateDownRange().effectiveMax }.average()
        } else if (holdRangeJoints.isNotEmpty()) {
            upRangeMin = holdRangeJoints.map { it.getStateHoldRange().effectiveMin }.average()
            upRangeMax = holdRangeJoints.map { it.getStateHoldRange().outermostMax }.average()
            downRangeMin = holdRangeJoints.map { it.getStateHoldRange().outermostMin }.average()
            downRangeMax = holdRangeJoints.map { it.getStateHoldRange().effectiveMax }.average()
        } else {
            // Fallback defaults
            upRangeMin = 120.0
            upRangeMax = 180.0
            downRangeMin = 0.0
            downRangeMax = 80.0
        }
        
        Log.d(TAG, "PhaseStateMachine initialized (STATE-BASED):")
        Log.d(TAG, "  Method: $countingMethod")
        Log.d(TAG, "  upRange: $upRangeMin - $upRangeMax")
        Log.d(TAG, "  downRange: $downRangeMin - $downRangeMax")
        Log.d(TAG, "  Transition Zone: $downRangeMax - $upRangeMin")
        Log.d(TAG, "  Settings (configurable):")
        Log.d(TAG, "    Hysteresis: $hysteresis°")
        Log.d(TAG, "    Min Rep Interval: ${minRepIntervalMs}ms")
        Log.d(TAG, "    Min Phase Duration: ${minPhaseDurationMs}ms")
    }
    
    /**
     * Update the state machine with new angles
     * 
     * NOTE: Expects pre-smoothed angles from AngleSmoother (via TrainingEngine)
     * This ensures consistency with FormValidator which uses the same smoothed angles.
     * 
     * For multi-primary UP_DOWN exercises, each joint uses its own StateRanges thresholds
     * (strict all-must-complete). Transitions require every primary joint to satisfy the
     * same phase predicate with joint-specific bounds (avoids averaged-threshold drift when
     * ranges differ, e.g. elbow vs knee).
     * 
     * @param primaryAngles Map of primary joint codes to their current (smoothed) angles
     * @return Current phase after update
     */
    fun update(primaryAngles: Map<String, Double>): Phase {
        if (primaryAngles.isEmpty() || primaryJoints.isEmpty()) {
            return currentPhase
        }
        
        val nextPhase = when (countingMethod) {
            CountingMethod.UP_DOWN -> {
                if (upDownPrimaryJoints.isNotEmpty()) {
                    updateUpDownStrict(upDownPrimaryJoints, primaryAngles)
                } else {
                    val angleValues = primaryAngles.values
                    if (angleValues.isEmpty()) return currentPhase
                    updateUpDownLegacy(angleValues.min(), angleValues.max())
                }
            }
            CountingMethod.HOLD -> {
                if (holdPrimaryJoints.isNotEmpty()) {
                    updateHoldStrict(holdPrimaryJoints, primaryAngles)
                } else {
                    val angleValues = primaryAngles.values
                    if (angleValues.isEmpty()) return currentPhase
                    updateHoldLegacy(angleValues.min(), angleValues.max())
                }
            }
        }
        
        // Handle phase transition
        if (nextPhase != currentPhase) {
            handlePhaseTransition(nextPhase)
        }
        
        return currentPhase
    }
    
    // --- Per-joint bounds (match init aggregation: up effectiveMin/outermostMax, down outermostMin/effectiveMax) ---
    
    private fun jointInUpRange(joint: TrackedJoint, angle: Double, exiting: Boolean): Boolean {
        val up = joint.getStateUpRange()
        val minBound = if (exiting) up.effectiveMin - hysteresis else up.effectiveMin
        val maxBound = up.outermostMax + hysteresis
        return angle in minBound..maxBound
    }
    
    private fun jointInDownRange(joint: TrackedJoint, angle: Double, exiting: Boolean): Boolean {
        val down = joint.getStateDownRange()
        val minBound = down.outermostMin - hysteresis
        val maxBound = if (exiting) down.effectiveMax + hysteresis else down.effectiveMax
        return angle in minBound..maxBound
    }
    
    private fun jointInHoldRange(joint: TrackedJoint, angle: Double, exiting: Boolean): Boolean {
        val hold = joint.getStateHoldRange()
        val minBound = hold.outermostMin - hysteresis
        val maxBound = if (exiting) hold.effectiveMax + hysteresis else hold.effectiveMax
        return angle in minBound..maxBound
    }
    
    private fun jointHasLeftUpRange(joint: TrackedJoint, angle: Double): Boolean {
        return angle < joint.getStateUpRange().effectiveMin - hysteresis
    }
    
    private fun jointHasEnteredDownRange(joint: TrackedJoint, angle: Double): Boolean {
        return angle <= joint.getStateDownRange().effectiveMax
    }
    
    private fun jointHasLeftDownRange(joint: TrackedJoint, angle: Double): Boolean {
        return angle > joint.getStateDownRange().effectiveMax + hysteresis
    }
    
    private fun jointHasEnteredUpRange(joint: TrackedJoint, angle: Double): Boolean {
        return angle >= joint.getStateUpRange().effectiveMin
    }
    
    /**
     * UP_DOWN with per-joint thresholds: every primary with up/down must satisfy the transition.
     */
    private fun updateUpDownStrict(joints: List<TrackedJoint>, primaryAngles: Map<String, Double>): Phase {
        fun angleOrNull(j: TrackedJoint): Double? = primaryAngles[j.joint]
        
        return when (currentPhase) {
            Phase.IDLE -> {
                if (joints.all { j ->
                        val a = angleOrNull(j) ?: return@all false
                        jointInUpRange(j, a, exiting = false)
                    }) {
                    Log.d(TAG, "Entered UP range (strict per joint)")
                    Phase.START
                } else {
                    Phase.IDLE
                }
            }
            
            Phase.START -> {
                if (joints.all { j ->
                        val a = angleOrNull(j) ?: return@all false
                        jointHasLeftUpRange(j, a)
                    }) {
                    Log.d(TAG, "Left UP range, starting descent (strict per joint)")
                    repCountedThisCycle = false
                    Phase.DOWN
                } else {
                    Phase.START
                }
            }
            
            Phase.DOWN -> {
                when {
                    joints.all { j ->
                        val a = angleOrNull(j) ?: return@all false
                        jointHasEnteredDownRange(j, a)
                    } -> {
                        Log.d(TAG, "Entered DOWN range (strict per joint)")
                        Phase.BOTTOM
                    }
                    joints.all { j ->
                        val a = angleOrNull(j) ?: return@all false
                        jointInUpRange(j, a, exiting = false)
                    } -> {
                        Log.d(TAG, "Returned to UP without reaching DOWN (strict per joint)")
                        Phase.START
                    }
                    else -> Phase.DOWN
                }
            }
            
            Phase.BOTTOM -> {
                if (joints.all { j ->
                        val a = angleOrNull(j) ?: return@all false
                        jointHasLeftDownRange(j, a)
                    }) {
                    Log.d(TAG, "Left DOWN range, ascending (strict per joint)")
                    Phase.UP
                } else {
                    Phase.BOTTOM
                }
            }
            
            Phase.UP -> {
                when {
                    joints.all { j ->
                        val a = angleOrNull(j) ?: return@all false
                        jointHasEnteredUpRange(j, a)
                    } -> {
                        Log.d(TAG, "★ Requesting REP completion - Entered UP range (strict per joint)")
                        Phase.START
                    }
                    joints.all { j ->
                        val a = angleOrNull(j) ?: return@all false
                        jointInDownRange(j, a, exiting = false)
                    } -> {
                        Log.d(TAG, "Returned to DOWN range (strict per joint)")
                        Phase.BOTTOM
                    }
                    else -> Phase.UP
                }
            }
            
            else -> currentPhase
        }
    }
    
    /**
     * HOLD with per-joint hold ranges: all must enter; any leaving exits COUNT.
     */
    private fun updateHoldStrict(joints: List<TrackedJoint>, primaryAngles: Map<String, Double>): Phase {
        return when (currentPhase) {
            Phase.IDLE -> {
                if (joints.all { j ->
                        val a = primaryAngles[j.joint] ?: return@all false
                        jointInHoldRange(j, a, exiting = false)
                    }) {
                    Log.d(TAG, "Entered HOLD zone (strict per joint)")
                    Phase.COUNT
                } else {
                    Phase.IDLE
                }
            }
            
            Phase.COUNT -> {
                val anyLeft = joints.any { j ->
                    val a = primaryAngles[j.joint] ?: return@any true
                    !jointInHoldRange(j, a, exiting = true)
                }
                if (anyLeft) {
                    Log.d(TAG, "Left HOLD zone (strict per joint)")
                    Phase.IDLE
                } else {
                    Phase.COUNT
                }
            }
            
            else -> currentPhase
        }
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
     * Legacy UP_DOWN path when no joint has state up/down ranges (uses aggregated thresholds + min/max).
     */
    private fun updateUpDownLegacy(minAngle: Double, maxAngle: Double): Phase {
        return when (currentPhase) {
            Phase.IDLE -> {
                // ALL joints must be in UP range → min must be high enough
                if (isInUpRange(minAngle)) {
                    Log.d(TAG, "Entered UP range: min=$minAngle max=$maxAngle (${upRangeMin}-${upRangeMax})")
                    Phase.START
                } else {
                    Phase.IDLE
                }
            }
            
            Phase.START -> {
                // ALL joints must have left UP range → max (last to leave) must be below threshold
                if (hasLeftUpRange(maxAngle)) {
                    Log.d(TAG, "Left UP range, starting descent: min=$minAngle max=$maxAngle")
                    repCountedThisCycle = false
                    Phase.DOWN
                } else {
                    Phase.START
                }
            }
            
            Phase.DOWN -> {
                when {
                    // ALL joints must have entered DOWN range → max (highest) must be low enough
                    hasEnteredDownRange(maxAngle) -> {
                        Log.d(TAG, "Entered DOWN range: min=$minAngle max=$maxAngle (${downRangeMin}-${downRangeMax})")
                        Phase.BOTTOM
                    }
                    // ALL joints returned to UP range (incomplete rep)
                    isInUpRange(minAngle) -> {
                        Log.d(TAG, "Returned to UP without reaching DOWN: min=$minAngle max=$maxAngle")
                        Phase.START
                    }
                    else -> Phase.DOWN
                }
            }
            
            Phase.BOTTOM -> {
                // ALL joints must have left DOWN range → min (last to leave) must be above threshold
                if (hasLeftDownRange(minAngle)) {
                    Log.d(TAG, "Left DOWN range, ascending: min=$minAngle max=$maxAngle")
                    Phase.UP
                } else {
                    Phase.BOTTOM
                }
            }
            
            Phase.UP -> {
                when {
                    // ALL joints must have entered UP range → min (lowest) must be high enough
                    hasEnteredUpRange(minAngle) -> {
                        Log.d(TAG, "★ Requesting REP completion - Entered UP range: min=$minAngle max=$maxAngle")
                        Phase.START
                    }
                    // ALL joints returned to DOWN range
                    isInDownRange(maxAngle) -> {
                        Log.d(TAG, "Returned to DOWN range: min=$minAngle max=$maxAngle")
                        Phase.BOTTOM
                    }
                    else -> Phase.UP
                }
            }
            
            else -> currentPhase
        }
    }
    
    
    /**
     * Legacy HOLD path when no joint has a hold range (uses aggregated thresholds + min/max).
     */
    private fun updateHoldLegacy(minAngle: Double, maxAngle: Double): Phase {
        return when (currentPhase) {
            Phase.IDLE -> {
                // ALL joints must be in hold zone → max must be below upper bound, min above lower
                if (isInDownRange(minAngle) && isInDownRange(maxAngle)) {
                    Log.d(TAG, "Entered HOLD zone: min=$minAngle max=$maxAngle")
                    Phase.COUNT
                } else {
                    Phase.IDLE
                }
            }
            
            Phase.COUNT -> {
                // Exit if ANY joint leaves the hold zone
                if (!isInDownRange(minAngle, exiting = true) || !isInDownRange(maxAngle, exiting = true)) {
                    Log.d(TAG, "Left HOLD zone: min=$minAngle max=$maxAngle")
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
        val now = timeProvider()
        
        // If phaseEntryTime not yet set, allow transition (first frame scenario)
        val phaseDuration = if (phaseEntryTime > 0L) now - phaseEntryTime else minPhaseDurationMs
        
        // Only transition if minimum duration has passed
        if (phaseDuration < minPhaseDurationMs) {
            Log.d(TAG, "Phase transition rejected - too fast (${phaseDuration}ms < ${minPhaseDurationMs}ms)")
            return
        }
        
        // Record timing
        phaseTimings[currentPhase] = phaseDuration
        
        // Check if this is a rep completion transition
        val isRepCompletionTransition = 
            (currentPhase == Phase.UP && nextPhase == Phase.START)
        
        // Update phases
        previousPhase = currentPhase
        currentPhase = nextPhase
        phaseEntryTime = now
        
        Log.d(TAG, "Phase: $previousPhase → $currentPhase")
        
        // Handle rep completion if applicable
        if (isRepCompletionTransition) {
            // If lastRepCompletedTime is 0, this is the first rep - allow it
            val timeSinceLastRep = if (lastRepCompletedTime > 0L) now - lastRepCompletedTime else minRepIntervalMs
            
            when {
                repCountedThisCycle -> {
                    Log.w(TAG, "Rep already counted this cycle - ignoring")
                }
                timeSinceLastRep < minRepIntervalMs -> {
                    Log.w(TAG, "Rep cooldown not passed (${timeSinceLastRep}ms < ${minRepIntervalMs}ms) - ignoring")
                }
                else -> {
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
        phaseEntryTime = 0L  // Will be set properly on first update()
        repCountedThisCycle = false
        lastRepCompletedTime = 0L
    }
    
    /**
     * Check if a rep was just completed
     */
    fun wasRepJustCompleted(): Boolean {
        return (previousPhase == Phase.UP && currentPhase == Phase.START)
    }
    
    /**
     * Get current zone info for debugging
     */
    fun getZoneInfo(angle: Double): String {
        return when {
            angle > upRangeMax -> "Above UP Range"
            angle >= upRangeMin -> "UP Zone"
            angle > downRangeMax -> "Transition"
            angle >= downRangeMin -> "DOWN Zone"
            else -> "Below DOWN Range"
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
    
    // HOLD specific
    COUNT       // In hold zone (timer running)
}
