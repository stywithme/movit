package com.trainingvalidator.poc.training.engine

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
    }
    
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
     */
    private fun validateJoint(
        joint: TrackedJoint,
        angle: Double,
        phase: Phase
    ): JointStatus {
        val upRange = joint.getUpRange(difficulty)
        val downRange = joint.getDownRange(difficulty)
        
        return when (phase) {
            // ===== PHASES WITH UP RANGE VALIDATION =====
            Phase.IDLE, Phase.START -> {
                validateAgainstUpRange(joint, angle, upRange)
            }
            
            // ===== PHASES WITH DOWN RANGE VALIDATION =====
            Phase.BOTTOM, Phase.EXTENDED, Phase.COUNT -> {
                validateAgainstDownRange(joint, angle, downRange)
            }
            
            // ===== TRANSITION PHASES - NO VALIDATION =====
            Phase.DOWN, Phase.UP, Phase.PUSH, Phase.PULL -> {
                // Check for extreme errors only
                checkExtremeErrors(joint, angle, upRange, downRange)
            }
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
     * Get arrow info for all tracked joints
     * This determines the zone and arrow direction for each joint
     * 
     * NOTE: Uses BOUNDARY_BUFFER for consistency with validation logic
     * to prevent UI flicker where arrows show error but validation shows correct
     */
    fun getJointArrowInfos(currentAngles: Map<String, Double>): Map<String, JointArrowInfo> {
        val arrowInfos = mutableMapOf<String, JointArrowInfo>()
        
        for (joint in trackedJoints) {
            val currentAngle = currentAngles[joint.joint] ?: continue
            val upRange = joint.getUpRange(difficulty)
            val downRange = joint.getDownRange(difficulty)
            
            // Use boundaryBuffer for consistency with validation logic
            val upMinWithBuffer = upRange.min - boundaryBuffer
            val upMaxWithBuffer = upRange.max + boundaryBuffer
            val downMinWithBuffer = downRange.min - boundaryBuffer
            val downMaxWithBuffer = downRange.max + boundaryBuffer
            
            // Determine zone (with buffer for consistency with validation)
            val zone = when {
                currentAngle > upMaxWithBuffer -> JointZone.TOO_HIGH
                currentAngle >= upMinWithBuffer -> JointZone.UP_ZONE
                currentAngle > downMaxWithBuffer -> JointZone.TRANSITION
                currentAngle >= downMinWithBuffer -> JointZone.DOWN_ZONE
                else -> JointZone.TOO_LOW
            }
            
            // Determine arrow direction based on zone
            val arrowDirection = when (zone) {
                JointZone.TOO_HIGH -> ArrowDirection.DOWN   // Need to go down to enter UP zone
                JointZone.UP_ZONE -> ArrowDirection.DOWN    // Encourage moving to DOWN zone
                JointZone.TRANSITION -> ArrowDirection.NONE // Moving, no arrow needed
                JointZone.DOWN_ZONE -> ArrowDirection.UP    // Encourage moving back to UP zone
                JointZone.TOO_LOW -> ArrowDirection.UP      // Need to go up to enter DOWN zone
            }
            
            // Is it an error?
            val isError = zone == JointZone.TOO_HIGH || zone == JointZone.TOO_LOW
            
            arrowInfos[joint.joint] = JointArrowInfo(
                jointCode = joint.joint,
                zone = zone,
                arrowDirection = arrowDirection,
                isError = isError,
                currentAngle = currentAngle,
                upRangeMin = upRange.min,
                upRangeMax = upRange.max,
                downRangeMin = downRange.min,
                downRangeMax = downRange.max
            )
        }
        
        return arrowInfos
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
    TRANSITION,     // downRange.max < angle < upRange.min → Moving, no arrow
    DOWN_ZONE,      // downRange.min <= angle <= downRange.max → At target, move up
    TOO_LOW         // angle < downRange.min → Error: need to move up
}

/**
 * Arrow direction for visual feedback
 */
enum class ArrowDirection {
    UP,     // Arrow pointing up (angle should increase)
    DOWN,   // Arrow pointing down (angle should decrease)
    NONE    // No arrow needed
}

/**
 * JointArrowInfo - Complete info for drawing an arrow on a joint's moving segment
 */
data class JointArrowInfo(
    val jointCode: String,
    val zone: JointZone,
    val arrowDirection: ArrowDirection,
    val isError: Boolean,
    val currentAngle: Double,
    val upRangeMin: Double,
    val upRangeMax: Double,
    val downRangeMin: Double,
    val downRangeMax: Double
) {
    /**
     * Should show arrow? (show in UP_ZONE and DOWN_ZONE from middle, and always in error zones)
     */
    fun shouldShowArrow(): Boolean {
        return when (zone) {
            JointZone.TOO_HIGH -> true
            JointZone.TOO_LOW -> true
            JointZone.UP_ZONE -> {
                // Show when past middle of up range (moving towards down)
                val upMid = (upRangeMin + upRangeMax) / 2
                currentAngle <= upMid
            }
            JointZone.DOWN_ZONE -> {
                // Show when past middle of down range (moving towards up)
                val downMid = (downRangeMin + downRangeMax) / 2
                currentAngle >= downMid
            }
            JointZone.TRANSITION -> false
        }
    }
}
