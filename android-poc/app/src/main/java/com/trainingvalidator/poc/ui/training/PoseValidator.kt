package com.trainingvalidator.poc.ui.training

import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.training.models.ExerciseConfig

/**
 * PoseValidator - Validates user pose against exercise requirements
 * 
 * Used to check if user is in correct starting position before training begins.
 */
class PoseValidator {
    
    companion object {
        /** Number of consecutive valid frames required to confirm pose */
        const val REQUIRED_VALID_FRAMES = 10
    }
    
    // Validation state
    private var validFrameCount = 0
    
    /**
     * Result of pose validation
     */
    data class ValidationResult(
        val isValid: Boolean,
        val jointStatuses: List<JointStatus>,
        val validFrameCount: Int,
        val requiredFrames: Int = REQUIRED_VALID_FRAMES
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
        
        val primaryJoints = variant.getPrimaryJoints()
        val jointStatuses = mutableListOf<JointStatus>()
        var allValid = true
        
        for (joint in primaryJoints) {
            val angle = getAngleForJoint(angles, joint.joint)
            val isVisible = angle != null
            val inRange = if (angle != null) {
                angle >= joint.startPose.min && angle <= joint.startPose.max
            } else {
                false
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
            
            if (!inRange) {
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
     */
    fun getPoseRequirementsText(
        exerciseConfig: ExerciseConfig?,
        poseVariantIndex: Int
    ): String {
        val variant = exerciseConfig?.poseVariants?.getOrNull(poseVariantIndex) ?: return ""
        val primaryJoints = variant.getPrimaryJoints()
        
        return buildString {
            appendLine("Get into starting position:")
            appendLine()
            primaryJoints.forEach { joint ->
                val name = formatJointName(joint.joint)
                appendLine("• $name: ${joint.startPose.min.toInt()}° - ${joint.startPose.max.toInt()}°")
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
