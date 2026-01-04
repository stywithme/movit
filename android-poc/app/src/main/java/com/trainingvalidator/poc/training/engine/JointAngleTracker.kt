package com.trainingvalidator.poc.training.engine

import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.pose.BodyLandmarks
import com.trainingvalidator.poc.training.models.TrackedJoint

/**
 * JointAngleTracker - Extracts angles for tracked joints only
 * 
 * This component maps the general JointAngles from AngleCalculator
 * to only the joints that are being tracked for this exercise.
 * 
 * It also converts joint codes (e.g., "left_knee") to actual angle values.
 */
class JointAngleTracker(
    private val trackedJoints: List<TrackedJoint>
) {
    
    companion object {
        private const val TAG = "JointAngleTracker"
        
        /**
         * Map of joint codes to their angle getter functions
         */
        private val JOINT_ANGLE_MAP = mapOf(
            // Arms
            "left_elbow" to { angles: JointAngles -> angles.leftElbow },
            "right_elbow" to { angles: JointAngles -> angles.rightElbow },
            "left_shoulder" to { angles: JointAngles -> angles.leftShoulder },
            "right_shoulder" to { angles: JointAngles -> angles.rightShoulder },
            
            // Torso
            "left_hip" to { angles: JointAngles -> angles.leftHip },
            "right_hip" to { angles: JointAngles -> angles.rightHip },
            "spine" to { angles: JointAngles -> angles.spine },
            
            // Legs
            "left_knee" to { angles: JointAngles -> angles.leftKnee },
            "right_knee" to { angles: JointAngles -> angles.rightKnee },
            "left_ankle" to { angles: JointAngles -> angles.leftAnkle },
            "right_ankle" to { angles: JointAngles -> angles.rightAnkle }
        )
        
        /**
         * Map of joint codes to BodyLandmarks index for skeleton drawing
         */
        val JOINT_LANDMARK_MAP = mapOf(
            "left_shoulder" to BodyLandmarks.LEFT_SHOULDER,
            "right_shoulder" to BodyLandmarks.RIGHT_SHOULDER,
            "left_elbow" to BodyLandmarks.LEFT_ELBOW,
            "right_elbow" to BodyLandmarks.RIGHT_ELBOW,
            "left_wrist" to BodyLandmarks.LEFT_WRIST,
            "right_wrist" to BodyLandmarks.RIGHT_WRIST,
            "left_hip" to BodyLandmarks.LEFT_HIP,
            "right_hip" to BodyLandmarks.RIGHT_HIP,
            "left_knee" to BodyLandmarks.LEFT_KNEE,
            "right_knee" to BodyLandmarks.RIGHT_KNEE,
            "left_ankle" to BodyLandmarks.LEFT_ANKLE,
            "right_ankle" to BodyLandmarks.RIGHT_ANKLE
        )
    }
    
    /**
     * Get tracked joint codes (for quick lookup)
     */
    val trackedJointCodes: Set<String> = trackedJoints.map { it.joint }.toSet()
    
    /**
     * Get primary joint codes
     */
    val primaryJointCodes: Set<String> = trackedJoints
        .filter { it.role == com.trainingvalidator.poc.training.models.JointRole.PRIMARY }
        .map { it.joint }
        .toSet()
    
    /**
     * Extract angles for tracked joints only
     * 
     * @param angles All joint angles from AngleCalculator
     * @return Map of joint code to angle value (only tracked joints)
     */
    fun extractTrackedAngles(angles: JointAngles): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        
        for (joint in trackedJoints) {
            val angleValue = getAngleForJoint(angles, joint.joint)
            if (angleValue != null) {
                result[joint.joint] = angleValue
            }
        }
        
        return result
    }
    
    /**
     * Extract angles for primary joints only (used for rep counting)
     */
    fun extractPrimaryAngles(angles: JointAngles): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        
        for (joint in trackedJoints) {
            if (joint.role == com.trainingvalidator.poc.training.models.JointRole.PRIMARY) {
                val angleValue = getAngleForJoint(angles, joint.joint)
                if (angleValue != null) {
                    result[joint.joint] = angleValue
                }
            }
        }
        
        return result
    }
    
    /**
     * Get average angle of primary joints (for state machine)
     */
    fun getAveragePrimaryAngle(angles: JointAngles): Double? {
        val primaryAngles = extractPrimaryAngles(angles)
        if (primaryAngles.isEmpty()) return null
        return primaryAngles.values.average()
    }
    
    /**
     * Get the angle for a specific joint code
     */
    fun getAngleForJoint(angles: JointAngles, jointCode: String): Double? {
        val getter = JOINT_ANGLE_MAP[jointCode] ?: return null
        return getter(angles)
    }
    
    /**
     * Check if a joint is being tracked
     */
    fun isTracked(jointCode: String): Boolean {
        return jointCode in trackedJointCodes
    }
    
    /**
     * Check if a joint is primary
     */
    fun isPrimary(jointCode: String): Boolean {
        return jointCode in primaryJointCodes
    }
    
    /**
     * Get TrackedJoint config by code
     */
    fun getJointConfig(jointCode: String): TrackedJoint? {
        return trackedJoints.find { it.joint == jointCode }
    }
    
    /**
     * Get landmark index for a joint (for skeleton overlay)
     */
    fun getLandmarkIndex(jointCode: String): Int? {
        return JOINT_LANDMARK_MAP[jointCode]
    }
    
    /**
     * Get all landmark indices for tracked joints
     */
    fun getTrackedLandmarkIndices(): List<Int> {
        return trackedJoints.mapNotNull { getLandmarkIndex(it.joint) }
    }
}

/**
 * TrackedAngleResult - Result of tracking angles for one frame
 */
data class TrackedAngleResult(
    val jointCode: String,
    val angle: Double,
    val isPrimary: Boolean,
    val config: TrackedJoint
)
