package com.trainingvalidator.poc.ui.training

import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.models.ExerciseConfig

/**
 * PoseValidator - Validates user pose against exercise requirements
 * 
 * Used to check if user is in correct starting position before training begins.
 * 
 * Settings (from app_settings.json):
 * - requiredValidFrames: Number of consecutive valid frames to confirm pose
 * - minValidAngle/maxValidAngle: Reject angles outside anatomical range
 */
class PoseValidator {
    
    // Validation state
    private var validFrameCount = 0
    
    // Settings
    private val requiredValidFrames: Int
        get() = SettingsManager.getRequiredValidFrames()
    
    /**
     * Result of pose validation
     */
    data class ValidationResult(
        val isValid: Boolean,
        val jointStatuses: List<JointStatus>,
        val validFrameCount: Int,
        val requiredFrames: Int = SettingsManager.getRequiredValidFrames()
    ) {
        val isConfirmed: Boolean
            get() = validFrameCount >= requiredFrames
        
        val progressPercent: Int
            get() = ((validFrameCount.toFloat() / requiredFrames) * 100).toInt().coerceIn(0, 100)
    }
    
    /**
     * Status of a single joint
     */
    data class JointStatus(
        val jointCode: String,
        val jointName: String,
        val currentAngle: Double?,
        val minAngle: Double,
        val maxAngle: Double,
        val isValid: Boolean,
        val isVisible: Boolean
    ) {
        fun getStatusText(): String {
            return if (!isVisible) {
                "❌ $jointName: Not visible"
            } else {
                val status = if (isValid) "✅" else "❌"
                val current = "${currentAngle?.toInt()}°"
                val expected = "${minAngle.toInt()}°-${maxAngle.toInt()}°"
                "$status $jointName: $current (need $expected)"
            }
        }
    }
    
    /**
     * Validate current pose against exercise config
     * 
     * Validates ALL tracked joints (primary + secondary) - not just primary.
     * All joints must be:
     * 1. Visible (angle != null, meaning all required landmarks are detected)
     * 2. Within their startPose range
     * 
     * @param angles Current joint angles
     * @param exerciseConfig Exercise configuration
     * @param poseVariantIndex Index of the pose variant to use
     * @return ValidationResult with detailed joint statuses
     */
    fun validate(
        angles: JointAngles?,
        exerciseConfig: ExerciseConfig?,
        poseVariantIndex: Int
    ): ValidationResult {
        if (angles == null || exerciseConfig == null) {
            validFrameCount = 0
            return ValidationResult(
                isValid = false,
                jointStatuses = emptyList(),
                validFrameCount = 0
            )
        }
        
        val variant = exerciseConfig.poseVariants.getOrNull(poseVariantIndex)
            ?: return ValidationResult(
                isValid = false,
                jointStatuses = emptyList(),
                validFrameCount = 0
            )
        
        // Validate ALL tracked joints (primary + secondary)
        // All joints must be visible and in range to start training
        val allTrackedJoints = variant.trackedJoints
        val jointStatuses = mutableListOf<JointStatus>()
        var allValid = true
        
        for (joint in allTrackedJoints) {
            val angle = getAngleForJoint(angles, joint.joint)
            
            // Check if angle is visible AND anatomically valid
            val isVisible = angle != null && SettingsManager.isAngleValid(angle)
            
            // Check if angle is within startPose range
            val inRange = if (angle != null && isVisible) {
                angle >= joint.startPose.min && angle <= joint.startPose.max
            } else {
                false // Not visible or invalid = not valid
            }
            
            jointStatuses.add(
                JointStatus(
                    jointCode = joint.joint,
                    jointName = formatJointName(joint.joint),
                    currentAngle = angle,
                    minAngle = joint.startPose.min,
                    maxAngle = joint.startPose.max,
                    isValid = inRange,
                    isVisible = isVisible
                )
            )
            
            // Both visibility AND range are required
            if (!isVisible || !inRange) {
                allValid = false
            }
        }
        
        // Update valid frame count
        if (allValid) {
            validFrameCount++
        } else {
            validFrameCount = 0
        }
        
        return ValidationResult(
            isValid = allValid,
            jointStatuses = jointStatuses,
            validFrameCount = validFrameCount
        )
    }
    
    /**
     * Get angle for a specific joint from JointAngles
     */
    private fun getAngleForJoint(angles: JointAngles, jointCode: String): Double? {
        return when (jointCode) {
            "left_shoulder" -> angles.leftShoulder
            "right_shoulder" -> angles.rightShoulder
            "left_elbow" -> angles.leftElbow
            "right_elbow" -> angles.rightElbow
            "left_hip" -> angles.leftHip
            "right_hip" -> angles.rightHip
            "left_knee" -> angles.leftKnee
            "right_knee" -> angles.rightKnee
            "left_ankle" -> angles.leftAnkle
            "right_ankle" -> angles.rightAnkle
            else -> null
        }
    }
    
    /**
     * Format joint code to readable name
     */
    private fun formatJointName(jointCode: String): String {
        return jointCode
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
    
    /**
     * Get pose requirements text for display
     * Shows all tracked joints (primary + secondary)
     */
    fun getPoseRequirementsText(
        exerciseConfig: ExerciseConfig?,
        poseVariantIndex: Int
    ): String {
        val variant = exerciseConfig?.poseVariants?.getOrNull(poseVariantIndex) ?: return ""
        val allTrackedJoints = variant.trackedJoints
        
        return buildString {
            appendLine("Get into starting position:")
            appendLine()
            allTrackedJoints.forEach { joint ->
                val name = formatJointName(joint.joint)
                val roleIndicator = if (joint.role.name == "PRIMARY") "●" else "○"
                appendLine("$roleIndicator $name: ${joint.startPose.min.toInt()}° - ${joint.startPose.max.toInt()}°")
            }
        }
    }
    
    /**
     * Reset validation state
     */
    fun reset() {
        validFrameCount = 0
    }
    
    /**
     * Get current valid frame count
     */
    fun getValidFrameCount(): Int = validFrameCount
}
