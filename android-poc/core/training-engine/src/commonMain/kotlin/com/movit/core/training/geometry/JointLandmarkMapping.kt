package com.movit.core.training.geometry

import com.movit.core.training.model.Landmark

/** Joint ↔ MediaPipe landmark index mapping (ported from legacy). */
object JointLandmarkMapping {
    private val jointToLandmarkMap = mapOf(
        "left_shoulder" to 11,
        "right_shoulder" to 12,
        "left_elbow" to 13,
        "right_elbow" to 14,
        "left_wrist" to 15,
        "right_wrist" to 16,
        "left_hip" to 23,
        "right_hip" to 24,
        "left_knee" to 25,
        "right_knee" to 26,
        "left_ankle" to 27,
        "right_ankle" to 28,
        "left_foot_index" to 31,
        "right_foot_index" to 32,
        "neck" to 33,
        "spine" to 34,
    )

    fun jointToLandmark(jointCode: String): Int? = jointToLandmarkMap[jointCode.lowercase()]

    fun getLandmarksForAngle(jointCode: String): List<Int> = when (jointCode.lowercase()) {
        "left_elbow" -> listOf(11, 13, 15)
        "right_elbow" -> listOf(12, 14, 16)
        "left_shoulder" -> listOf(13, 11, 23)
        "right_shoulder" -> listOf(14, 12, 24)
        "left_shoulder_cross" -> listOf(13, 11, 12)
        "right_shoulder_cross" -> listOf(14, 12, 11)
        "left_wrist" -> listOf(13, 15, 19)
        "right_wrist" -> listOf(14, 16, 20)
        "left_hip" -> listOf(11, 23, 25)
        "right_hip" -> listOf(12, 24, 26)
        "left_hip_cross" -> listOf(25, 23, 24)
        "right_hip_cross" -> listOf(26, 24, 23)
        "left_knee" -> listOf(23, 25, 27)
        "right_knee" -> listOf(24, 26, 28)
        "left_ankle" -> listOf(25, 27, 31)
        "right_ankle" -> listOf(26, 28, 32)
        "neck", "neck_left" -> listOf(11, 33, 0)
        "neck_right" -> listOf(12, 33, 0)
        "neck_spine" -> listOf(34, 33, 0)
        "spine" -> listOf(33, 34, 25)
        else -> emptyList()
    }

    fun computeJointVisibility(
        jointCode: String,
        landmarks: List<Landmark>,
        isFrontCamera: Boolean = false,
    ): Float {
        val indices = getLandmarksForAngle(jointCode)
        if (indices.isEmpty() || landmarks.isEmpty()) return 0f
        var minV = 1f
        var found = 0
        for (rawIdx in indices) {
            val idx = if (isFrontCamera) PoseLandmarkMirroring.mirroredIndex(rawIdx) else rawIdx
            val landmark = landmarks.getOrNull(idx) ?: continue
            found++
            minV = kotlin.math.min(minV, landmark.visibility)
        }
        return if (found == 0) 0f else minV
    }
}
