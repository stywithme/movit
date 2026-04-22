package com.trainingvalidator.poc.pose

import com.trainingvalidator.poc.analysis.SmoothedLandmark

/**
 * JointLandmarkMapping - Single Source of Truth for Joint ↔ Landmark mapping
 * 
 * This object provides centralized mapping between:
 * - Joint codes (e.g., "left_knee") used in exercise configurations
 * - MediaPipe landmark indices (e.g., 25 for left knee)
 * 
 * IMPORTANT: This is the ONLY place where this mapping should be defined.
 * All other classes should use this object to avoid duplication and inconsistency.
 * 
 * Usage:
 * ```kotlin
 * // Get landmark index for a joint
 * val index = JointLandmarkMapping.jointToLandmark("left_knee") // Returns 25
 * 
 * // Get joint code for a landmark index
 * val joint = JointLandmarkMapping.landmarkToJoint(25) // Returns "left_knee"
 * ```
 */
object JointLandmarkMapping {
    
    /**
     * Complete mapping of joint codes to MediaPipe landmark indices
     * 
     * MediaPipe Pose Landmarks:
     * 0: nose, 1-6: eyes, 7-8: ears, 9-10: mouth
     * 11-12: shoulders, 13-14: elbows, 15-16: wrists
     * 17-22: hands (pinky, index, thumb)
     * 23-24: hips, 25-26: knees, 27-28: ankles
     * 29-30: heels, 31-32: foot index
     */
    private val jointToLandmarkMap = mapOf(
        // Upper body
        "nose" to 0,
        "left_eye_inner" to 1,
        "left_eye" to 2,
        "left_eye_outer" to 3,
        "right_eye_inner" to 4,
        "right_eye" to 5,
        "right_eye_outer" to 6,
        "left_ear" to 7,
        "right_ear" to 8,
        "mouth_left" to 9,
        "mouth_right" to 10,
        
        // Arms
        "left_shoulder" to 11,
        "right_shoulder" to 12,
        "left_elbow" to 13,
        "right_elbow" to 14,
        "left_wrist" to 15,
        "right_wrist" to 16,
        
        // Hands
        "left_pinky" to 17,
        "right_pinky" to 18,
        "left_index" to 19,
        "right_index" to 20,
        "left_thumb" to 21,
        "right_thumb" to 22,
        
        // Torso / Legs
        "left_hip" to 23,
        "right_hip" to 24,
        "left_knee" to 25,
        "right_knee" to 26,
        "left_ankle" to 27,
        "right_ankle" to 28,
        "left_heel" to 29,
        "right_heel" to 30,
        "left_foot_index" to 31,
        "left_toe" to 31,  // Alias
        "right_foot_index" to 32,
        "right_toe" to 32,  // Alias
        
        // Virtual landmarks (computed midpoints)
        "neck" to 33,              // Midpoint of shoulders
        "spine" to 34              // Midpoint of hips
    )
    
    /**
     * Reverse mapping: landmark index to joint code
     * Note: Uses primary names only (no aliases)
     */
    private val landmarkToJointMap = mapOf(
        0 to "nose",
        1 to "left_eye_inner",
        2 to "left_eye",
        3 to "left_eye_outer",
        4 to "right_eye_inner",
        5 to "right_eye",
        6 to "right_eye_outer",
        7 to "left_ear",
        8 to "right_ear",
        9 to "mouth_left",
        10 to "mouth_right",
        11 to "left_shoulder",
        12 to "right_shoulder",
        13 to "left_elbow",
        14 to "right_elbow",
        15 to "left_wrist",
        16 to "right_wrist",
        17 to "left_pinky",
        18 to "right_pinky",
        19 to "left_index",
        20 to "right_index",
        21 to "left_thumb",
        22 to "right_thumb",
        23 to "left_hip",
        24 to "right_hip",
        25 to "left_knee",
        26 to "right_knee",
        27 to "left_ankle",
        28 to "right_ankle",
        29 to "left_heel",
        30 to "right_heel",
        31 to "left_foot_index",
        32 to "right_foot_index",
        
        // Virtual landmarks
        33 to "neck",
        34 to "spine"
    )
    
    /**
     * Tracked joints commonly used in exercises (excludes face/hands)
     */
    val trackedJointCodes = setOf(
        "left_shoulder", "right_shoulder",
        "left_shoulder_cross", "right_shoulder_cross",
        "left_elbow", "right_elbow",
        "left_wrist", "right_wrist",
        "left_hip", "right_hip",
        "left_knee", "right_knee",
        "left_ankle", "right_ankle",
        "neck", "neck_left", "neck_right", "neck_spine", "spine"
    )
    
    /**
     * Get MediaPipe landmark index for a joint code
     * 
     * @param jointCode Joint identifier (e.g., "left_knee")
     * @return Landmark index or null if not found
     */
    fun jointToLandmark(jointCode: String): Int? {
        return jointToLandmarkMap[jointCode.lowercase()]
    }
    
    /**
     * Get joint code for a MediaPipe landmark index
     * 
     * @param landmarkIndex MediaPipe landmark index (0-32)
     * @return Joint code or null if not found
     */
    fun landmarkToJoint(landmarkIndex: Int): String? {
        return landmarkToJointMap[landmarkIndex]
    }
    
    /**
     * Check if a landmark index is a tracked joint (commonly used in exercises)
     */
    fun isTrackedLandmark(landmarkIndex: Int): Boolean {
        val jointCode = landmarkToJoint(landmarkIndex) ?: return false
        return jointCode in trackedJointCodes
    }
    
    /**
     * Get all landmark indices for a list of joint codes
     */
    fun jointCodesToLandmarks(jointCodes: Collection<String>): List<Int> {
        return jointCodes.mapNotNull { jointToLandmark(it) }
    }
    
    /**
     * Get all joint codes for a list of landmark indices
     */
    fun landmarksToJointCodes(landmarks: Collection<Int>): List<String> {
        return landmarks.mapNotNull { landmarkToJoint(it) }
    }
    
    /**
     * Get all landmark indices required to calculate an angle for a joint
     * 
     * Each joint angle is calculated from 3 points:
     * - The joint before (toward torso/center)
     * - The joint itself
     * - The joint after (toward extremity)
     * 
     * @param jointCode The joint for which we want to calculate an angle
     * @return List of 3 landmark indices required, or empty if joint not supported
     */
    fun getLandmarksForAngle(jointCode: String): List<Int> {
        return when (jointCode.lowercase()) {
            // Arm angles
            "left_elbow" -> listOf(11, 13, 15)  // shoulder, elbow, wrist
            "right_elbow" -> listOf(12, 14, 16)
            "left_shoulder" -> listOf(13, 11, 23)  // elbow, shoulder, hip
            "right_shoulder" -> listOf(14, 12, 24)
            // Cross Shoulders: elbow, shoulder, opposite_shoulder
            "left_shoulder_cross" -> listOf(13, 11, 12)
            "right_shoulder_cross" -> listOf(14, 12, 11)
            "left_wrist" -> listOf(13, 15, 19)  // elbow, wrist, index
            "right_wrist" -> listOf(14, 16, 20)
            
            // Leg angles
            "left_hip" -> listOf(11, 23, 25)  // shoulder, hip, knee
            "right_hip" -> listOf(12, 24, 26)
            "left_knee" -> listOf(23, 25, 27)  // hip, knee, ankle
            "right_knee" -> listOf(24, 26, 28)
            "left_ankle" -> listOf(25, 27, 31)  // knee, ankle, foot
            "right_ankle" -> listOf(26, 28, 32)
            
            // Virtual joints
            // Neck Left angle: left_shoulder(11) -> neck(33) -> nose(0)
            "neck", "neck_left" -> listOf(11, 33, 0)
            // Neck Right angle: right_shoulder(12) -> neck(33) -> nose(0)
            "neck_right" -> listOf(12, 33, 0)
            // Neck Spine angle: spine(34) -> neck(33) -> nose(0)
            "neck_spine" -> listOf(34, 33, 0)
            // Spine angle: neck(33) -> spine(34) -> left_knee(25)
            "spine" -> listOf(33, 34, 25)
            
            else -> emptyList()
        }
    }
    
    /**
     * Get all unique landmarks required to calculate angles for multiple joints
     * 
     * @param jointCodes List of joint codes
     * @return Set of unique landmark indices required
     */
    fun getAllLandmarksForAngles(jointCodes: Collection<String>): Set<Int> {
        return jointCodes.flatMap { getLandmarksForAngle(it) }.toSet()
    }

    /**
     * Minimum visibility across the three landmarks that define this joint's angle.
     * Stricter than average: if any defining point is occluded, the joint is treated as low visibility.
     *
     * When [isFrontCamera] is true, raw landmark indices are mirrored because MediaPipe
     * runs on the already-mirrored image — so the user's anatomical "right_knee"
     * sits at the mirrored index of landmark 26 rather than 26 itself.
     *
     * @param jointCode Anatomical joint code (same space as [getLandmarksForAngle])
     * @param landmarks Smoothed landmarks in MediaPipe index order (raw from MediaPipe)
     * @param isFrontCamera True when the pose was detected on a mirrored image.
     */
    fun computeJointVisibility(
        jointCode: String,
        landmarks: List<SmoothedLandmark>,
        isFrontCamera: Boolean = false
    ): Float {
        val indices = getLandmarksForAngle(jointCode)
        if (indices.isEmpty() || landmarks.isEmpty()) return 0f
        var minV = 1f
        for (rawIdx in indices) {
            val idx = if (isFrontCamera) BodyLandmarks.getMirroredIndex(rawIdx) else rawIdx
            if (idx < 0 || idx >= landmarks.size) return 0f
            minV = kotlin.math.min(minV, landmarks[idx].visibility)
        }
        return minV
    }
}
