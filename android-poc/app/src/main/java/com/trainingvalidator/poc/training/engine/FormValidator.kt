package com.trainingvalidator.poc.training.engine

import android.util.Log
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.models.*

/**
 * FormValidator - Validates exercise form using upRange/downRange system
 * 
 * Validation Logic by Phase:
 * 
 *   Phase      | Validation
 *   -----------|------------------------------------------
 *   IDLE       | Check if in UP range (to start)
 *   START      | Check if in UP range (ready position)
 *   DOWN/PUSH  | NO validation (transition zone allowed)
 *   BOTTOM/EXT | Check if in DOWN range (target position)
 *   UP/PULL    | NO validation (transition zone allowed)
 *   COUNT      | Check if in DOWN range (hold position)
 * 
 * Error Detection:
 *   - angle > upRange.max → TOO_HIGH error
 *   - angle < downRange.min → TOO_LOW error
 * 
 * Configurable Settings (from app_settings.json):
 *   - boundaryBuffer: Buffer zone to prevent flickering at boundaries
 *   - extremeErrorThreshold: How far outside range before flagging extreme error
 */
class FormValidator(
    private val trackedJoints: List<TrackedJoint>,
    private val difficulty: DifficultyType
) {
    
    companion object {
        private const val TAG = "FormValidator"
        
        // Hysteresis to prevent zone flickering at boundaries
        // Zone won't change unless angle crosses boundary by this amount
        private const val ZONE_HYSTERESIS = 2.0
    }
    
    // Previous zones for hysteresis (prevents flickering)
    private val previousZones = mutableMapOf<String, JointZone>()
    
    // ==================== Configurable Thresholds ====================
    
    /**
     * Boundary buffer from global settings (prevents flickering at validation boundaries)
     */
    private val boundaryBuffer: Double = SettingsManager.getBoundaryBuffer()
    
    /**
     * Extreme error threshold from global settings
     */
    private val extremeErrorThreshold: Double = SettingsManager.getExtremeErrorThreshold()
    
    /**
     * Validate all tracked joints for the current phase
     * 
     * @param currentAngles Map of joint code to current angle
     * @param currentPhase Current phase of exercise
     * @return Validation result with joint statuses and errors
     */
    fun validate(
        currentAngles: Map<String, Double>,
        currentPhase: Phase
    ): ValidationResult {
        val jointStatuses = mutableMapOf<String, JointStatus>()
        val errors = mutableListOf<JointError>()
        
        for (joint in trackedJoints) {
            val currentAngle = currentAngles[joint.joint] ?: continue
            
            val status = validateJoint(joint, currentAngle, currentPhase)
            jointStatuses[joint.joint] = status
            
            // Collect errors
            if (!status.isCorrect && status.error != null) {
                errors.add(status.error)
            }
        }
        
        return ValidationResult(
            isCorrect = errors.isEmpty(),
            jointStatuses = jointStatuses,
            errors = errors
        )
    }
    
    /**
     * Validate a single joint based on current phase
     * 
     * ZONE-AWARE VALIDATION:
     * First checks the actual zone of the angle. If angle is in TRANSITION zone
     * (between downRange.max and upRange.min), we don't report errors.
     * This is correct because TRANSITION is valid movement between positions.
     * 
     * NOTE: Both PhaseStateMachine and FormValidator now receive the same
     * smoothed angles from AngleSmoother (Single Source of Truth).
     */
    private fun validateJoint(
        joint: TrackedJoint,
        angle: Double,
        phase: Phase
    ): JointStatus {
        val upRange = joint.getUpRange(difficulty)
        val downRange = joint.getDownRange(difficulty)
        
        // First, determine the actual zone of the angle
        // This prevents errors during natural movement through TRANSITION zone
        val actualZone = determineZone(angle, upRange, downRange)
        
        // If angle is in TRANSITION zone, don't report errors
        // (User is moving between positions - this is correct behavior)
        if (actualZone == JointZone.TRANSITION) {
            return JointStatus(
                jointCode = joint.joint,
                isCorrect = true,
                currentAngle = angle,
                error = null
            )
        }
        
        return when (phase) {
            // ===== PHASES WITH UP RANGE VALIDATION =====
            Phase.IDLE, Phase.START -> {
                // Only validate if actually in or near UP zone
                // If in DOWN zone during START phase, it's a sync issue - use extreme check
                if (actualZone == JointZone.DOWN_ZONE) {
                    checkExtremeErrors(joint, angle, upRange, downRange)
                } else {
                    validateAgainstUpRange(joint, angle, upRange)
                }
            }
            
            // ===== PHASES WITH DOWN RANGE VALIDATION =====
            Phase.BOTTOM, Phase.EXTENDED, Phase.COUNT -> {
                // Only validate if actually in or near DOWN zone
                // If in UP zone during BOTTOM phase, it's a sync issue - use extreme check
                if (actualZone == JointZone.UP_ZONE) {
                    checkExtremeErrors(joint, angle, upRange, downRange)
                } else {
                    validateAgainstDownRange(joint, angle, downRange)
                }
            }
            
            // ===== TRANSITION PHASES - NO VALIDATION =====
            Phase.DOWN, Phase.UP, Phase.PUSH, Phase.PULL -> {
                // Check for extreme errors only
                checkExtremeErrors(joint, angle, upRange, downRange)
            }
        }
    }
    
    /**
     * Determine which zone an angle belongs to
     * Used internally to check actual position before applying phase-based validation
     */
    private fun determineZone(
        angle: Double,
        upRange: AngleRange,
        downRange: AngleRange
    ): JointZone {
        return when {
            angle > upRange.max -> JointZone.TOO_HIGH
            angle >= upRange.min -> JointZone.UP_ZONE
            angle > downRange.max -> JointZone.TRANSITION
            angle >= downRange.min -> JointZone.DOWN_ZONE
            else -> JointZone.TOO_LOW
        }
    }
    
    /**
     * Validate angle against UP range
     */
    private fun validateAgainstUpRange(
        joint: TrackedJoint,
        angle: Double,
        upRange: AngleRange
    ): JointStatus {
        val minWithBuffer = upRange.min - boundaryBuffer
        val maxWithBuffer = upRange.max + boundaryBuffer
        
        // Check if within UP range
        if (angle >= minWithBuffer && angle <= maxWithBuffer) {
            return JointStatus(
                jointCode = joint.joint,
                isCorrect = true,
                currentAngle = angle,
                error = null
            )
        }
        
        // Determine error type
        val (errorType, errorMessage) = if (angle > maxWithBuffer) {
            ErrorType.TOO_HIGH to joint.errorMessages.tooHigh
        } else {
            ErrorType.TOO_LOW to joint.errorMessages.tooLow
        }
        
        return JointStatus(
            jointCode = joint.joint,
            isCorrect = false,
            currentAngle = angle,
            error = JointError(
                jointCode = joint.joint,
                errorType = errorType,
                actualAngle = angle,
                expectedMin = upRange.min,
                expectedMax = upRange.max,
                message = errorMessage
            )
        )
    }
    
    /**
     * Validate angle against DOWN range
     */
    private fun validateAgainstDownRange(
        joint: TrackedJoint,
        angle: Double,
        downRange: AngleRange
    ): JointStatus {
        val minWithBuffer = downRange.min - boundaryBuffer
        val maxWithBuffer = downRange.max + boundaryBuffer
        
        // Check if within DOWN range
        if (angle >= minWithBuffer && angle <= maxWithBuffer) {
            return JointStatus(
                jointCode = joint.joint,
                isCorrect = true,
                currentAngle = angle,
                error = null
            )
        }
        
        // Determine error type
        val (errorType, errorMessage) = if (angle > maxWithBuffer) {
            // Not bending enough
            ErrorType.TOO_HIGH to joint.errorMessages.tooHigh
        } else {
            // Bending too much
            ErrorType.TOO_LOW to joint.errorMessages.tooLow
        }
        
        return JointStatus(
            jointCode = joint.joint,
            isCorrect = false,
            currentAngle = angle,
            error = JointError(
                jointCode = joint.joint,
                errorType = errorType,
                actualAngle = angle,
                expectedMin = downRange.min,
                expectedMax = downRange.max,
                message = errorMessage
            )
        )
    }
    
    /**
     * Check for extreme errors during transition (outside both ranges)
     */
    private fun checkExtremeErrors(
        joint: TrackedJoint,
        angle: Double,
        upRange: AngleRange,
        downRange: AngleRange
    ): JointStatus {
        // Only flag errors if VERY far outside the valid zones (configurable threshold)
        val extremeHighThreshold = upRange.max + extremeErrorThreshold
        val extremeLowThreshold = downRange.min - extremeErrorThreshold
        
        return when {
            angle > extremeHighThreshold -> {
                // TOO_HIGH: angle is above upRange, so expected range is upRange
                JointStatus(
                    jointCode = joint.joint,
                    isCorrect = false,
                    currentAngle = angle,
                    error = JointError(
                        jointCode = joint.joint,
                        errorType = ErrorType.TOO_HIGH,
                        actualAngle = angle,
                        expectedMin = upRange.min,
                        expectedMax = upRange.max,
                        message = joint.errorMessages.tooHigh
                    )
                )
            }
            angle < extremeLowThreshold -> {
                // TOO_LOW: angle is below downRange, so expected range is downRange
                JointStatus(
                    jointCode = joint.joint,
                    isCorrect = false,
                    currentAngle = angle,
                    error = JointError(
                        jointCode = joint.joint,
                        errorType = ErrorType.TOO_LOW,
                        actualAngle = angle,
                        expectedMin = downRange.min,
                        expectedMax = downRange.max,
                        message = joint.errorMessages.tooLow
                    )
                )
            }
            else -> {
                // In transition zone - all good
                JointStatus(
                    jointCode = joint.joint,
                    isCorrect = true,
                    currentAngle = angle,
                    error = null
                )
            }
        }
    }
    
    /**
     * Check if user is in valid start position (all primary joints in UP range)
     */
    fun isInStartPosition(currentAngles: Map<String, Double>): Boolean {
        for (joint in trackedJoints) {
            if (joint.role != JointRole.PRIMARY) continue
            
            val currentAngle = currentAngles[joint.joint] ?: return false
            if (!joint.isInUpRange(currentAngle, difficulty)) {
                return false
            }
        }
        return true
    }
    
    /**
     * Check if user is in valid startPose (pre-training check)
     * Uses startPose which is independent of difficulty levels
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
     */
    fun getStartPositionFeedback(currentAngles: Map<String, Double>): List<JointError> {
        val errors = mutableListOf<JointError>()
        
        for (joint in trackedJoints) {
            if (joint.role != JointRole.PRIMARY) continue
            
            val currentAngle = currentAngles[joint.joint] ?: continue
            val upRange = joint.getUpRange(difficulty)
            
            if (currentAngle > upRange.max) {
                errors.add(JointError(
                    jointCode = joint.joint,
                    errorType = ErrorType.TOO_HIGH,
                    actualAngle = currentAngle,
                    expectedMin = upRange.min,
                    expectedMax = upRange.max,
                    message = joint.errorMessages.tooHigh
                ))
            } else if (currentAngle < upRange.min) {
                errors.add(JointError(
                    jointCode = joint.joint,
                    errorType = ErrorType.TOO_LOW,
                    actualAngle = currentAngle,
                    expectedMin = upRange.min,
                    expectedMax = upRange.max,
                    message = joint.errorMessages.tooLow
                ))
            }
        }
        
        return errors
    }
    
    /**
     * Get feedback for getting into startPose (pre-training)
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
                    message = joint.errorMessages.tooHigh
                ))
            } else if (currentAngle < joint.startPose.min) {
                errors.add(JointError(
                    jointCode = joint.joint,
                    errorType = ErrorType.TOO_LOW,
                    actualAngle = currentAngle,
                    expectedMin = joint.startPose.min,
                    expectedMax = joint.startPose.max,
                    message = joint.errorMessages.tooLow
                ))
            }
        }
        
        return errors
    }
    
    /**
     * Get visual info for all tracked joints
     * This determines the current zone and severity for each joint
     * 
     * NOTE: Uses BOUNDARY_BUFFER for consistency with validation logic
     * to prevent UI flicker where visuals show error but validation shows correct
     * 
     * PRE-WARNING: When user is within warningThreshold of the boundary,
     * we show a warning (amber) instead of waiting for error (red).
     * This implements "Prevent, don't punish" principle.
     */
    fun getJointArrowInfos(currentAngles: Map<String, Double>): Map<String, JointArrowInfo> {
        val arrowInfos = mutableMapOf<String, JointArrowInfo>()
        
        // Warning threshold: how close to boundary before warning (degrees)
        val warningThreshold = 5.0
        
        for (joint in trackedJoints) {
            val currentAngle = currentAngles[joint.joint] ?: continue
            val upRange = joint.getUpRange(difficulty)
            val downRange = joint.getDownRange(difficulty)
            
            // Use boundaryBuffer for consistency with validation logic
            val upMinWithBuffer = upRange.min - boundaryBuffer
            val upMaxWithBuffer = upRange.max + boundaryBuffer
            val downMinWithBuffer = downRange.min - boundaryBuffer
            val downMaxWithBuffer = downRange.max + boundaryBuffer
            
            // Determine zone (with buffer and hysteresis for smooth transitions)
            val previousZone = previousZones[joint.joint]
            val rawZone = when {
                currentAngle > upMaxWithBuffer -> JointZone.TOO_HIGH
                currentAngle >= upMinWithBuffer -> JointZone.UP_ZONE
                currentAngle > downMaxWithBuffer -> JointZone.TRANSITION
                currentAngle >= downMinWithBuffer -> JointZone.DOWN_ZONE
                else -> JointZone.TOO_LOW
            }
            
            // Apply hysteresis: only change zone if crossed boundary by ZONE_HYSTERESIS
            val zone = if (previousZone != null && previousZone != rawZone) {
                // Check if we've crossed far enough to justify zone change
                val shouldChange = when {
                    // Entering error zones: change immediately (safety)
                    rawZone == JointZone.TOO_HIGH || rawZone == JointZone.TOO_LOW -> true
                    
                    // Exiting error zones: require hysteresis
                    previousZone == JointZone.TOO_HIGH && rawZone == JointZone.UP_ZONE ->
                        currentAngle < upMaxWithBuffer - ZONE_HYSTERESIS
                    previousZone == JointZone.TOO_LOW && rawZone == JointZone.DOWN_ZONE ->
                        currentAngle > downMinWithBuffer + ZONE_HYSTERESIS
                    
                    // UP_ZONE ↔ TRANSITION: require hysteresis
                    previousZone == JointZone.UP_ZONE && rawZone == JointZone.TRANSITION ->
                        currentAngle < upMinWithBuffer - ZONE_HYSTERESIS
                    previousZone == JointZone.TRANSITION && rawZone == JointZone.UP_ZONE ->
                        currentAngle > upMinWithBuffer + ZONE_HYSTERESIS
                    
                    // DOWN_ZONE ↔ TRANSITION: require hysteresis
                    previousZone == JointZone.DOWN_ZONE && rawZone == JointZone.TRANSITION ->
                        currentAngle > downMaxWithBuffer + ZONE_HYSTERESIS
                    previousZone == JointZone.TRANSITION && rawZone == JointZone.DOWN_ZONE ->
                        currentAngle < downMaxWithBuffer - ZONE_HYSTERESIS
                    
                    else -> true  // Other transitions: allow
                }
                
                if (shouldChange) rawZone else previousZone
            } else {
                rawZone
            }
            
            // Store zone for next frame
            previousZones[joint.joint] = zone
            
            // Is it an error?
            val isError = zone == JointZone.TOO_HIGH || zone == JointZone.TOO_LOW
            
            // PRE-WARNING: Check if approaching boundary
            // Only applicable when NOT in error (already in valid zone)
            val isWarning = when {
                isError -> false  // Already in error, no need for warning
                zone == JointZone.UP_ZONE -> {
                    // Warning if approaching TOO_HIGH boundary
                    currentAngle > upMaxWithBuffer - warningThreshold
                }
                zone == JointZone.DOWN_ZONE -> {
                    // Warning if approaching TOO_LOW boundary
                    currentAngle < downMinWithBuffer + warningThreshold
                }
                else -> false
            }
            
            arrowInfos[joint.joint] = JointArrowInfo(
                jointCode = joint.joint,
                zone = zone,
                isError = isError,
                isWarning = isWarning,
                isPrimary = joint.role == JointRole.PRIMARY,
                currentAngle = currentAngle,
                upRangeMin = upRange.min,
                upRangeMax = upRange.max,
                downRangeMin = downRange.min,
                downRangeMax = downRange.max
            )
        }
        
        return arrowInfos
    }
    
    /**
     * Reset state (call when training starts/restarts)
     */
    fun reset() {
        previousZones.clear()
        Log.d(TAG, "FormValidator state reset")
    }
}

/**
 * ValidationResult - Result of form validation
 */
data class ValidationResult(
    val isCorrect: Boolean,
    val jointStatuses: Map<String, JointStatus>,
    val errors: List<JointError>
)

/**
 * JointStatus - Status of a single joint
 */
data class JointStatus(
    val jointCode: String,
    val isCorrect: Boolean,
    val currentAngle: Double,
    val error: JointError? = null
) {
    /**
     * Get color for this joint (for UI)
     */
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
 */
enum class JointColor {
    DEFAULT,    // Not tracked or no data
    CORRECT,    // Within range (green)
    ERROR_HIGH, // Too high - not bending enough (red)
    ERROR_LOW   // Too low - bending too much (orange)
}

/**
 * JointZone - Defines which zone the current angle is in
 * 
 *  180° ───── TOO_HIGH (above upRange.max)
 *       ───── upRange.max
 *             UP_ZONE (within upRange)
 *       ───── upRange.min
 *             TRANSITION (between upRange.min and downRange.max)
 *       ───── downRange.max
 *             DOWN_ZONE (within downRange)
 *       ───── downRange.min
 *   0°  ───── TOO_LOW (below downRange.min)
 */
enum class JointZone {
    TOO_HIGH,       // angle > upRange.max → Error: need to move down
    UP_ZONE,        // upRange.min <= angle <= upRange.max → Ready, move down
    TRANSITION,     // downRange.max < angle < upRange.min → Moving (no validation)
    DOWN_ZONE,      // downRange.min <= angle <= downRange.max → At target, move up
    TOO_LOW         // angle < downRange.min → Error: need to move up
}

/**
 * JointArrowInfo - Visual info for a joint (zone + severity + ranges)
 * 
 * States:
 * - isError: User is in error zone (TOO_HIGH or TOO_LOW)
 * - isWarning: User is approaching boundary (within warningThreshold of error)
 * - normal: User is in correct zone with no imminent risk
 * 
 * Priority factors:
 * - isPrimary: Primary joints (for rep counting) have higher priority
 * - isError > isWarning > normal
 */
data class JointArrowInfo(
    val jointCode: String,
    val zone: JointZone,
    val isError: Boolean,
    val isWarning: Boolean = false,  // Near-boundary warning
    val isPrimary: Boolean = true,   // Primary joint (for focus ranking)
    val currentAngle: Double,
    val upRangeMin: Double,
    val upRangeMax: Double,
    val downRangeMin: Double,
    val downRangeMax: Double
)
