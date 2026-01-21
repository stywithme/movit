package com.trainingvalidator.poc.training.engine

import android.util.Log
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.models.*

/**
 * FormValidator - STATE-BASED Form Validation
 * 
 * Validates exercise form using the unified JointState system.
 * This is the SINGLE SOURCE OF TRUTH for form quality assessment.
 * 
 * State Hierarchy (by priority):
 * 1. DANGER  → Injury risk, invalidates rep
 * 2. WARNING → Error, doesn't count rep
 * 3. PERFECT → Ideal form, full score
 * 4. NORMAL  → Good form, partial score
 * 5. PAD     → Acceptable form, minimal score
 * 6. TRANSITION → Movement zone, no scoring
 * 
 * Key Changes from Previous Version:
 * - Removed difficulty parameter (no more beginner/normal/advanced)
 * - Uses JointState instead of JointZone for quality assessment
 * - Unified decision making from StateConfig
 * - Hysteresis applied between state transitions
 */
class FormValidator(
    private val trackedJoints: List<TrackedJoint>
) {
    
    companion object {
        private const val TAG = "FormValidator"
        
        // Hysteresis degrees for state transitions
        private const val STATE_HYSTERESIS_NORMAL_PAD = 3.0      // Between NORMAL ↔ PAD
        private const val STATE_HYSTERESIS_PAD_WARNING = 2.0     // Between PAD ↔ WARNING
        private const val STATE_HYSTERESIS_WARNING_DANGER = 2.0  // Between WARNING ↔ DANGER
        
        // Minimum consecutive frames to confirm DANGER (safety smoothing)
        private const val MIN_DANGER_FRAMES = 3
    }
    
    // Previous states for hysteresis (prevents flickering)
    private val previousStates = mutableMapOf<String, JointState>()
    
    // Danger frame counter for each joint (safety smoothing)
    private val dangerFrameCounts = mutableMapOf<String, Int>()
    
    // ==================== Configurable Thresholds ====================
    
    /**
     * Boundary buffer from global settings (prevents flickering at validation boundaries)
     */
    private val boundaryBuffer: Double = SettingsManager.getBoundaryBuffer()
    
    // ==================== Main Validation Methods ====================
    
    /**
     * Validate all tracked joints and return state information
     * 
     * @param currentAngles Map of joint code to current angle
     * @param currentPhase Current phase of exercise (for context)
     * @return Map of joint code to JointStateInfo
     */
    fun getJointStateInfos(currentAngles: Map<String, Double>): Map<String, JointStateInfo> {
        val stateInfos = mutableMapOf<String, JointStateInfo>()
        
        for (joint in trackedJoints) {
            val currentAngle = currentAngles[joint.joint] ?: continue
            
            val stateInfo = determineJointStateInfo(joint, currentAngle)
            stateInfos[joint.joint] = stateInfo
        }
        
        return stateInfos
    }
    
    /**
     * Legacy validation method - wraps state info into ValidationResult
     * 
     * @deprecated Use getJointStateInfos() instead for new code.
     * 
     * @param currentAngles Map of joint code to current angle
     * @param currentPhase Current phase of exercise
     * @return Validation result with joint statuses and errors
     */
    @Suppress("DEPRECATION")
    @Deprecated("Use getJointStateInfos() instead. Will be removed in future version.")
    fun validate(
        currentAngles: Map<String, Double>,
        currentPhase: Phase
    ): ValidationResult {
        val stateInfos = getJointStateInfos(currentAngles)
        val jointStatuses = mutableMapOf<String, JointStatus>()
        val errors = mutableListOf<JointError>()
        
        for ((jointCode, stateInfo) in stateInfos) {
            val joint = trackedJoints.find { it.joint == jointCode } ?: continue
            
            // Convert to legacy JointStatus for backward compatibility
            val isCorrect = stateInfo.state in listOf(
                JointState.PERFECT, JointState.NORMAL, JointState.PAD, JointState.TRANSITION
            )
            
            val errorType = if (stateInfo.currentAngle > 90) ErrorType.TOO_HIGH else ErrorType.TOO_LOW
            
            // Get message from stateMessages (not startPoseMessage)
            val errorMessage = joint.stateMessages?.getMessage(stateInfo.state)
                ?: joint.getStartPoseMessage(errorType)
            
            val status = JointStatus(
                jointCode = jointCode,
                isCorrect = isCorrect,
                currentAngle = stateInfo.currentAngle,
                error = if (!isCorrect) {
                    JointError(
                        jointCode = jointCode,
                        errorType = errorType,
                        actualAngle = stateInfo.currentAngle,
                        expectedMin = stateInfo.stateRanges?.perfect?.min ?: 0.0,
                        expectedMax = stateInfo.stateRanges?.perfect?.max ?: 180.0,
                        message = errorMessage,
                        state = stateInfo.state,  // Include actual state
                        isPrimary = stateInfo.isPrimary
                    )
                } else null
            )
            
            jointStatuses[jointCode] = status
            
            if (!isCorrect && status.error != null) {
                errors.add(status.error)
            }
        }
        
        return ValidationResult(
            isCorrect = errors.isEmpty(),
            jointStatuses = jointStatuses,
            errors = errors
        )
    }
    
    // ==================== State Determination ====================
    
    /**
     * Determine complete JointStateInfo for a joint
     * 
     * @param joint TrackedJoint configuration
     * @param angle Current angle in degrees
     * @return Complete JointStateInfo with all decision data
     */
    private fun determineJointStateInfo(
        joint: TrackedJoint,
        angle: Double
    ): JointStateInfo {
        val isPrimary = joint.role == JointRole.PRIMARY
        
        // Determine zone type (UP, DOWN, or TRANSITION)
        val zoneType = if (isPrimary && joint.hasStateUpDownRanges()) {
            joint.determineZoneType(angle)
        } else {
            ZoneType.UP_ZONE // SECONDARY joints use single zone
        }
        
        // Get the applicable StateRanges for this zone
        val stateRanges = getApplicableStateRanges(joint, zoneType)
        
        // Get UP and DOWN ranges for track rendering (each track needs its own ranges)
        val upStateRanges = if (joint.hasStateUpDownRanges()) {
            joint.getStateUpRange()
        } else if (joint.hasStateHoldRange()) {
            joint.getStateHoldRange()
        } else null
        
        val downStateRanges = if (joint.hasStateUpDownRanges()) {
            joint.getStateDownRange()
        } else if (joint.hasStateHoldRange()) {
            joint.getStateHoldRange()
        } else null
        
        // Determine raw state from ranges
        val rawState = if (zoneType == ZoneType.TRANSITION) {
            JointState.TRANSITION
        } else {
            stateRanges?.determineState(angle) ?: JointState.WARNING
        }
        
        // Apply hysteresis and danger smoothing
        val state = applyHysteresis(joint.joint, rawState, angle, stateRanges)
        
        // Get messages for this state
        val messages = joint.getMessagesForState(state)
        
        return JointStateInfo.create(
            jointCode = joint.joint,
            state = state,
            isPrimary = isPrimary,
            currentAngle = angle,
            currentZone = zoneType,
            stateRanges = stateRanges,
            upStateRanges = upStateRanges,
            downStateRanges = downStateRanges,
            messages = messages,
            invertIndicator = joint.invertIndicator
        )
    }
    
    /**
     * Get applicable StateRanges based on zone type
     */
    private fun getApplicableStateRanges(joint: TrackedJoint, zoneType: ZoneType): StateRanges? {
        return when {
            joint.hasStateHoldRange() -> joint.getStateHoldRange()
            zoneType == ZoneType.UP_ZONE && joint.hasStateUpDownRanges() -> joint.getStateUpRange()
            zoneType == ZoneType.DOWN_ZONE && joint.hasStateUpDownRanges() -> joint.getStateDownRange()
            else -> null
        }
    }
    
    /**
     * Apply hysteresis to prevent state flickering
     * 
     * Uses degree-based hysteresis for smooth state transitions:
     * - DANGER entry: Requires MIN_DANGER_FRAMES consecutive frames (safety)
     * - DANGER exit: Must move far enough from danger zone
     * - Other transitions: Apply degree buffer based on state pair
     * 
     * @param jointCode Joint identifier
     * @param rawState Newly determined state
     * @param angle Current angle
     * @param stateRanges Active state ranges for reference
     * @return Final state after hysteresis
     */
    private fun applyHysteresis(
        jointCode: String,
        rawState: JointState,
        angle: Double,
        stateRanges: StateRanges?
    ): JointState {
        val previousState = previousStates[jointCode]
        
        // First frame - no hysteresis needed
        if (previousState == null) {
            previousStates[jointCode] = rawState
            dangerFrameCounts[jointCode] = if (rawState == JointState.DANGER) 1 else 0
            return rawState
        }
        
        // CRITICAL: Reset danger frame count if NOT in DANGER
        // This ensures the counter starts fresh each time user approaches DANGER
        if (rawState != JointState.DANGER) {
            dangerFrameCounts[jointCode] = 0
        }
        
        // Same state - no change needed
        if (rawState == previousState) {
            if (rawState == JointState.DANGER) {
                dangerFrameCounts[jointCode] = (dangerFrameCounts[jointCode] ?: 0) + 1
            }
            return rawState
        }
        
        // Get hysteresis degree for this transition
        val hysteresisDegree = getHysteresisDegree(previousState, rawState)
        
        // Handle DANGER entry (requires frame count confirmation for safety)
        if (rawState == JointState.DANGER && previousState != JointState.DANGER) {
            val count = (dangerFrameCounts[jointCode] ?: 0) + 1
            dangerFrameCounts[jointCode] = count
            
            if (count >= MIN_DANGER_FRAMES) {
                previousStates[jointCode] = JointState.DANGER
                return JointState.DANGER
            }
            return previousState
        }
        
        // Handle transitions using degree-based hysteresis
        if (stateRanges != null && hysteresisDegree > 0) {
            // Check if angle has moved far enough to confirm transition
            val transitionConfirmed = checkTransitionConfirmed(
                previousState = previousState,
                newState = rawState,
                angle = angle,
                stateRanges = stateRanges,
                hysteresisDegree = hysteresisDegree
            )
            
            if (!transitionConfirmed) {
                return previousState
            }
        }
        
        // Transition confirmed
        previousStates[jointCode] = rawState
        return rawState
    }
    
    /**
     * Get hysteresis degree for a state transition pair
     */
    private fun getHysteresisDegree(from: JointState, to: JointState): Double {
        return when {
            // DANGER transitions
            from == JointState.DANGER || to == JointState.DANGER -> STATE_HYSTERESIS_WARNING_DANGER
            // WARNING transitions  
            from == JointState.WARNING || to == JointState.WARNING -> STATE_HYSTERESIS_PAD_WARNING
            // PAD/NORMAL transitions
            from == JointState.PAD || to == JointState.PAD -> STATE_HYSTERESIS_NORMAL_PAD
            // PERFECT/NORMAL - minimal hysteresis
            else -> 1.0
        }
    }
    
    /**
     * Check if angle has moved far enough from boundary to confirm transition
     * 
     * FIXED: Now applies hysteresis to ALL transitions including degradation (WARNING/TRANSITION)
     * This prevents flickering when angle is near boundaries due to camera noise.
     */
    private fun checkTransitionConfirmed(
        previousState: JointState,
        newState: JointState,
        angle: Double,
        stateRanges: StateRanges,
        hysteresisDegree: Double
    ): Boolean {
        // Minimum margin for ALL transitions (prevents boundary flickering)
        val minMargin = 1.5  // degrees
        
        // Check if angle is solidly inside the new state's range
        return when (newState) {
            JointState.PERFECT -> {
                val range = stateRanges.perfect
                angle >= (range.min + hysteresisDegree) && angle <= (range.max - hysteresisDegree)
            }
            JointState.NORMAL -> {
                val range = stateRanges.normal ?: return true
                angle >= (range.min + hysteresisDegree) && angle <= (range.max - hysteresisDegree)
            }
            JointState.PAD -> {
                val range = stateRanges.pad ?: return true
                angle >= (range.min + hysteresisDegree) && angle <= (range.max - hysteresisDegree)
            }
            JointState.WARNING -> {
                // For WARNING: require angle to be minMargin inside warning range
                val range = stateRanges.warning ?: return true
                angle >= (range.min + minMargin) && angle <= (range.max - minMargin)
            }
            JointState.TRANSITION -> {
                // For TRANSITION: require minMargin inside transition zone
                // This is handled separately since TRANSITION doesn't have explicit ranges
                true  // Let zone determination handle it
            }
            else -> true
        }
    }
    
    // ==================== Query Methods ====================
    
    /**
     * Check if user is in valid start position (all primary joints in UP zone with counted state)
     * 
     * For REP exercises: Checks if in UP_ZONE with counted state
     * For HOLD exercises: Checks if in hold range with counted state
     */
    fun isInStartPosition(currentAngles: Map<String, Double>): Boolean {
        for (joint in trackedJoints) {
            if (joint.role != JointRole.PRIMARY) continue
            
            val currentAngle = currentAngles[joint.joint] ?: return false
            
            when {
                joint.hasStateUpDownRanges() -> {
                    // REP exercise: check UP zone
                    val zoneType = joint.determineZoneType(currentAngle)
                    if (zoneType != ZoneType.UP_ZONE) return false
                    
                    val upRange = joint.getStateUpRange()
                    if (!upRange.isInCountedState(currentAngle)) return false
                }
                joint.hasStateHoldRange() -> {
                    // HOLD exercise: check hold range
                    val holdRange = joint.getStateHoldRange()
                    if (!holdRange.isInCountedState(currentAngle)) return false
                }
                else -> {
                    // No state ranges defined - skip this joint
                    continue
                }
            }
        }
        return true
    }
    
    /**
     * Check if user is in valid startPose (pre-training check)
     * Uses startPose which is independent of state ranges
     */
    fun isInStartPose(currentAngles: Map<String, Double>): Boolean {
        for (joint in trackedJoints) {
            if (joint.role != JointRole.PRIMARY) continue
            
            val currentAngle = currentAngles[joint.joint] ?: return false
            if (!joint.isInStartPose(currentAngle)) {
                return false
            }
        }
        return true
    }
    
    /**
     * Get feedback for getting into start position
     * 
     * For REP exercises: Checks UP range perfect zone
     * For HOLD exercises: Checks hold range perfect zone
     * 
     * Uses stateMessages.warning for feedback (falls back to generic message)
     */
    fun getStartPositionFeedback(currentAngles: Map<String, Double>): List<JointError> {
        val errors = mutableListOf<JointError>()
        
        for (joint in trackedJoints) {
            if (joint.role != JointRole.PRIMARY) continue
            
            val currentAngle = currentAngles[joint.joint] ?: continue
            
            // Get the perfect range based on exercise type
            val perfectRange = when {
                joint.hasStateUpDownRanges() -> joint.getStateUpRange().perfect
                joint.hasStateHoldRange() -> joint.getStateHoldRange().perfect
                else -> continue
            }
            
            if (currentAngle > perfectRange.max + boundaryBuffer) {
                errors.add(JointError(
                    jointCode = joint.joint,
                    errorType = ErrorType.TOO_HIGH,
                    actualAngle = currentAngle,
                    expectedMin = perfectRange.min,
                    expectedMax = perfectRange.max,
                    message = joint.getStartPoseMessage(ErrorType.TOO_HIGH),
                    state = JointState.WARNING,
                    isPrimary = joint.role == JointRole.PRIMARY
                ))
            } else if (currentAngle < perfectRange.min - boundaryBuffer) {
                errors.add(JointError(
                    jointCode = joint.joint,
                    errorType = ErrorType.TOO_LOW,
                    actualAngle = currentAngle,
                    expectedMin = perfectRange.min,
                    expectedMax = perfectRange.max,
                    message = joint.getStartPoseMessage(ErrorType.TOO_LOW),
                    state = JointState.WARNING,
                    isPrimary = joint.role == JointRole.PRIMARY
                ))
            }
        }
        
        return errors
    }
    
    /**
     * Get feedback for getting into startPose (pre-training)
     * 
     * Uses stateMessages.warning for feedback (falls back to generic message)
     */
    fun getStartPoseFeedback(currentAngles: Map<String, Double>): List<JointError> {
        val errors = mutableListOf<JointError>()
        
        for (joint in trackedJoints) {
            if (joint.role != JointRole.PRIMARY) continue
            
            val currentAngle = currentAngles[joint.joint] ?: continue
            
            if (currentAngle > joint.startPose.max) {
                errors.add(JointError(
                    jointCode = joint.joint,
                    errorType = ErrorType.TOO_HIGH,
                    actualAngle = currentAngle,
                    expectedMin = joint.startPose.min,
                    expectedMax = joint.startPose.max,
                    message = joint.getStartPoseMessage(ErrorType.TOO_HIGH),
                    state = JointState.WARNING,
                    isPrimary = true
                ))
            } else if (currentAngle < joint.startPose.min) {
                errors.add(JointError(
                    jointCode = joint.joint,
                    errorType = ErrorType.TOO_LOW,
                    actualAngle = currentAngle,
                    expectedMin = joint.startPose.min,
                    expectedMax = joint.startPose.max,
                    message = joint.getStartPoseMessage(ErrorType.TOO_LOW),
                    state = JointState.WARNING,
                    isPrimary = true
                ))
            }
        }
        
        return errors
    }
    
    /**
     * Get worst state across all joints
     * Used for rep scoring
     */
    fun getWorstState(stateInfos: Map<String, JointStateInfo>): JointState {
        val qualityStates = stateInfos.values
            .map { it.state }
            .filter { it != JointState.TRANSITION }
        
        return JointState.getWorst(qualityStates)
    }
    
    /**
     * Check if any joint is in DANGER state
     */
    fun hasDangerState(stateInfos: Map<String, JointStateInfo>): Boolean {
        return stateInfos.values.any { it.state == JointState.DANGER }
    }
    
    /**
     * Check if any joint invalidates the rep (DANGER state)
     */
    fun anyInvalidatesRep(stateInfos: Map<String, JointStateInfo>): Boolean {
        return stateInfos.values.any { it.invalidatesRep }
    }
    
    /**
     * Get the rate (score) for the worst state
     */
    fun getWorstRate(stateInfos: Map<String, JointStateInfo>): Float {
        val worstState = getWorstState(stateInfos)
        return StateConfig.getRate(worstState)
    }
    
    /**
     * Legacy method - Get visual info for all tracked joints
     * Wraps getJointStateInfos for backward compatibility with overlay code
     * 
     * @deprecated Use getJointStateInfos() instead.
     */
    @Suppress("DEPRECATION")
    @Deprecated("Use getJointStateInfos() instead. Will be removed in future version.")
    fun getJointArrowInfos(currentAngles: Map<String, Double>): Map<String, JointArrowInfo> {
        val stateInfos = getJointStateInfos(currentAngles)
        val arrowInfos = mutableMapOf<String, JointArrowInfo>()
        
        for ((jointCode, stateInfo) in stateInfos) {
            val joint = trackedJoints.find { it.joint == jointCode } ?: continue
            
            // Convert JointState to legacy JointZone
            val zone = when (stateInfo.currentZone) {
                ZoneType.UP_ZONE -> when (stateInfo.state) {
                    JointState.DANGER, JointState.WARNING -> {
                        if (stateInfo.currentAngle > 90) JointZone.TOO_HIGH else JointZone.TOO_LOW
                    }
                    else -> JointZone.UP_ZONE
                }
                ZoneType.DOWN_ZONE -> when (stateInfo.state) {
                    JointState.DANGER, JointState.WARNING -> {
                        if (stateInfo.currentAngle < 90) JointZone.TOO_LOW else JointZone.TOO_HIGH
                    }
                    else -> JointZone.DOWN_ZONE
                }
                ZoneType.TRANSITION -> JointZone.TRANSITION
            }
            
            val isError = stateInfo.state == JointState.DANGER || stateInfo.state == JointState.WARNING
            val isWarning = stateInfo.state == JointState.PAD
            
            // Get range bounds for legacy compatibility
            val upRange = if (joint.hasStateUpDownRanges()) {
                joint.getStateUpRange()
            } else if (joint.hasStateHoldRange()) {
                joint.getStateHoldRange()
            } else null
            
            val downRange = if (joint.hasStateUpDownRanges()) {
                joint.getStateDownRange()
            } else upRange
            
            arrowInfos[jointCode] = JointArrowInfo(
                jointCode = jointCode,
                zone = zone,
                isError = isError,
                isWarning = isWarning,
                isPrimary = stateInfo.isPrimary,
                currentAngle = stateInfo.currentAngle,
                upRangeMin = upRange?.perfect?.min ?: 0.0,
                upRangeMax = upRange?.perfect?.max ?: 180.0,
                downRangeMin = downRange?.perfect?.min ?: 0.0,
                downRangeMax = downRange?.perfect?.max ?: 180.0
            )
        }
        
        return arrowInfos
    }
    
    /**
     * Reset state (call when training starts/restarts)
     */
    fun reset() {
        previousStates.clear()
        dangerFrameCounts.clear()
        Log.d(TAG, "FormValidator state reset")
    }
}

/**
 * ValidationResult - Result of form validation
 * 
 * @deprecated Use getJointStateInfos() for new code.
 */
@Deprecated("Use getJointStateInfos() method instead. Will be removed in future version.")
data class ValidationResult(
    val isCorrect: Boolean,
    @Suppress("DEPRECATION") val jointStatuses: Map<String, JointStatus>,
    val errors: List<JointError>
)

/**
 * JointStatus - Status of a single joint
 * 
 * @deprecated Use JointStateInfo for new code.
 */
@Deprecated("Use JointStateInfo instead. Will be removed in future version.")
data class JointStatus(
    val jointCode: String,
    val isCorrect: Boolean,
    val currentAngle: Double,
    val error: JointError? = null
) {
    /**
     * Get color for this joint (for UI)
     */
    @Suppress("DEPRECATION")
    fun getColor(): JointColor {
        return when {
            isCorrect -> JointColor.CORRECT
            error?.errorType == ErrorType.TOO_HIGH -> JointColor.ERROR_HIGH
            error?.errorType == ErrorType.TOO_LOW -> JointColor.ERROR_LOW
            else -> JointColor.DEFAULT
        }
    }
}

/**
 * Joint color enum for UI
 * 
 * @deprecated Use StateConfig.getColor(JointState) instead.
 */
@Deprecated("Use StateConfig.getColor(JointState) instead. Will be removed in future version.")
enum class JointColor {
    DEFAULT,    // Not tracked or no data
    CORRECT,    // Within range (green)
    ERROR_HIGH, // Too high - not bending enough (red)
    ERROR_LOW   // Too low - bending too much (orange)
}

/**
 * JointZone - Legacy enum for backward compatibility
 * 
 * @deprecated Use JointState from JointState.kt for new code.
 * This is kept for overlay compatibility during migration.
 */
@Deprecated("Use JointState instead. Will be removed in future version.")
enum class JointZone {
    TOO_HIGH,       // Error: above valid range
    UP_ZONE,        // In up/start position
    TRANSITION,     // Moving between positions
    DOWN_ZONE,      // In down/target position
    TOO_LOW         // Error: below valid range
}

/**
 * JointArrowInfo - Legacy data class for backward compatibility
 * 
 * @deprecated Use JointStateInfo from JointState.kt for new code.
 * This is kept for overlay compatibility during migration.
 */
@Deprecated("Use JointStateInfo instead. Will be removed in future version.")
data class JointArrowInfo(
    val jointCode: String,
    @Suppress("DEPRECATION") val zone: JointZone,
    val isError: Boolean,
    val isWarning: Boolean = false,
    val isPrimary: Boolean = true,
    val currentAngle: Double,
    val upRangeMin: Double,
    val upRangeMax: Double,
    val downRangeMin: Double,
    val downRangeMax: Double
)
