package com.movit.core.training.geometry

import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseFrame
import com.movit.core.training.model.PoseLandmarkIndices

/**
 * Builds [PoseFrame] from platform-neutral landmarks using shared angle math.
 */
object PoseFrameAssembler {
    fun assemble(
        landmarks: List<Landmark>,
        timestampMs: Long,
        isFrontCamera: Boolean,
        visibilityThreshold: Float = 0.5f,
    ): PoseFrame = PoseFrame(
        angles = calculateAngles(landmarks, visibilityThreshold),
        landmarks = landmarks,
        isFrontCamera = isFrontCamera,
        timestampMs = timestampMs,
    )

    fun calculateAngles(
        landmarks: List<Landmark>,
        visibilityThreshold: Float = 0.5f,
    ): JointAngles = JointAngles(
        leftElbow = angleAt(landmarks, PoseLandmarkIndices.LEFT_SHOULDER, PoseLandmarkIndices.LEFT_ELBOW, PoseLandmarkIndices.LEFT_WRIST, visibilityThreshold),
        rightElbow = angleAt(landmarks, PoseLandmarkIndices.RIGHT_SHOULDER, PoseLandmarkIndices.RIGHT_ELBOW, PoseLandmarkIndices.RIGHT_WRIST, visibilityThreshold),
        leftShoulder = angleAt(landmarks, PoseLandmarkIndices.LEFT_ELBOW, PoseLandmarkIndices.LEFT_SHOULDER, PoseLandmarkIndices.LEFT_HIP, visibilityThreshold),
        rightShoulder = angleAt(landmarks, PoseLandmarkIndices.RIGHT_ELBOW, PoseLandmarkIndices.RIGHT_SHOULDER, PoseLandmarkIndices.RIGHT_HIP, visibilityThreshold),
        leftHip = angleAt(landmarks, PoseLandmarkIndices.LEFT_SHOULDER, PoseLandmarkIndices.LEFT_HIP, PoseLandmarkIndices.LEFT_KNEE, visibilityThreshold),
        rightHip = angleAt(landmarks, PoseLandmarkIndices.RIGHT_SHOULDER, PoseLandmarkIndices.RIGHT_HIP, PoseLandmarkIndices.RIGHT_KNEE, visibilityThreshold),
        leftKnee = angleAt(landmarks, PoseLandmarkIndices.LEFT_HIP, PoseLandmarkIndices.LEFT_KNEE, PoseLandmarkIndices.LEFT_ANKLE, visibilityThreshold),
        rightKnee = angleAt(landmarks, PoseLandmarkIndices.RIGHT_HIP, PoseLandmarkIndices.RIGHT_KNEE, PoseLandmarkIndices.RIGHT_ANKLE, visibilityThreshold),
        leftAnkle = angleAt(landmarks, PoseLandmarkIndices.LEFT_KNEE, PoseLandmarkIndices.LEFT_ANKLE, PoseLandmarkIndices.LEFT_FOOT_INDEX, visibilityThreshold),
        rightAnkle = angleAt(landmarks, PoseLandmarkIndices.RIGHT_KNEE, PoseLandmarkIndices.RIGHT_ANKLE, PoseLandmarkIndices.RIGHT_FOOT_INDEX, visibilityThreshold),
    )

    private fun angleAt(
        landmarks: List<Landmark>,
        indexA: Int,
        indexB: Int,
        indexC: Int,
        visibilityThreshold: Float,
    ): Double? {
        if (landmarks.size <= maxOf(indexA, indexB, indexC)) return null
        val a = landmarks[indexA]
        val b = landmarks[indexB]
        val c = landmarks[indexC]
        if (!a.isVisible(visibilityThreshold) || !b.isVisible(visibilityThreshold) || !c.isVisible(visibilityThreshold)) {
            return null
        }
        return JointAngleCalculator.angleDegrees(
            pointA = PosePoint2D(a.x, a.y),
            pointB = PosePoint2D(b.x, b.y),
            pointC = PosePoint2D(c.x, c.y),
        )
    }
}
