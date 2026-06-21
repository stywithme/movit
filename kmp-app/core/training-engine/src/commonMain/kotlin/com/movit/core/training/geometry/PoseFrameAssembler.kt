package com.movit.core.training.geometry

import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseFrame
import com.movit.core.training.model.PoseLandmarkIndices

/**
 * Builds [PoseFrame] from platform-neutral landmarks using shared angle math.
 */
object PoseFrameAssembler {
    private val elbowEstimator = ElbowAngleEstimator()

    fun assemble(
        landmarks: List<Landmark>,
        timestampMs: Long,
        isFrontCamera: Boolean,
        worldLandmarks: List<Landmark>? = null,
        analysisImageWidth: Int = 0,
        analysisImageHeight: Int = 0,
        visibilityThreshold: Float = 0.5f,
        applyElbowCorrection: Boolean = true,
    ): PoseFrame {
        val resolvedLandmarks = VirtualLandmarks.ensureAppended(landmarks)
        var angles = calculateAngles(resolvedLandmarks, visibilityThreshold, worldLandmarks)
        if (applyElbowCorrection && worldLandmarks != null && worldLandmarks.size >= 33) {
            angles = elbowEstimator.correct(angles, worldLandmarks, resolvedLandmarks, timestampMs)
        }
        return PoseFrame(
            angles = angles,
            landmarks = resolvedLandmarks,
            worldLandmarks = worldLandmarks,
            isFrontCamera = isFrontCamera,
            timestampMs = timestampMs,
            analysisImageWidth = analysisImageWidth,
            analysisImageHeight = analysisImageHeight,
        )
    }

    fun resetElbowEstimator() = elbowEstimator.reset()

    /** Read-only elbow diagnostics from the shared production estimator instance. */
    fun lastElbowDiagnostics(): Array<ElbowCorrectionDiagnostics?> = elbowEstimator.lastDiagnostics

    fun calculateAngles(
        landmarks: List<Landmark>,
        visibilityThreshold: Float = 0.5f,
        worldLandmarks: List<Landmark>? = null,
    ): JointAngles {
        val world = worldLandmarks?.takeIf { it.size >= landmarks.size }
        return JointAngles(
        leftElbow = angleAt3D(world, landmarks, PoseLandmarkIndices.LEFT_SHOULDER, PoseLandmarkIndices.LEFT_ELBOW, PoseLandmarkIndices.LEFT_WRIST, visibilityThreshold),
        rightElbow = angleAt3D(world, landmarks, PoseLandmarkIndices.RIGHT_SHOULDER, PoseLandmarkIndices.RIGHT_ELBOW, PoseLandmarkIndices.RIGHT_WRIST, visibilityThreshold),
        leftShoulder = angleAt3D(world, landmarks, PoseLandmarkIndices.LEFT_ELBOW, PoseLandmarkIndices.LEFT_SHOULDER, PoseLandmarkIndices.LEFT_HIP, visibilityThreshold),
        rightShoulder = angleAt3D(world, landmarks, PoseLandmarkIndices.RIGHT_ELBOW, PoseLandmarkIndices.RIGHT_SHOULDER, PoseLandmarkIndices.RIGHT_HIP, visibilityThreshold),
        leftHip = angleAt3D(world, landmarks, PoseLandmarkIndices.LEFT_SHOULDER, PoseLandmarkIndices.LEFT_HIP, PoseLandmarkIndices.LEFT_KNEE, visibilityThreshold),
        rightHip = angleAt3D(world, landmarks, PoseLandmarkIndices.RIGHT_SHOULDER, PoseLandmarkIndices.RIGHT_HIP, PoseLandmarkIndices.RIGHT_KNEE, visibilityThreshold),
        leftKnee = angleAt3D(world, landmarks, PoseLandmarkIndices.LEFT_HIP, PoseLandmarkIndices.LEFT_KNEE, PoseLandmarkIndices.LEFT_ANKLE, visibilityThreshold),
        rightKnee = angleAt3D(world, landmarks, PoseLandmarkIndices.RIGHT_HIP, PoseLandmarkIndices.RIGHT_KNEE, PoseLandmarkIndices.RIGHT_ANKLE, visibilityThreshold),
        leftAnkle = angleAt3D(world, landmarks, PoseLandmarkIndices.LEFT_KNEE, PoseLandmarkIndices.LEFT_ANKLE, PoseLandmarkIndices.LEFT_FOOT_INDEX, visibilityThreshold),
        rightAnkle = angleAt3D(world, landmarks, PoseLandmarkIndices.RIGHT_KNEE, PoseLandmarkIndices.RIGHT_ANKLE, PoseLandmarkIndices.RIGHT_FOOT_INDEX, visibilityThreshold),
        )
    }

    private fun angleAt3D(
        world: List<Landmark>?,
        landmarks: List<Landmark>,
        indexA: Int,
        indexB: Int,
        indexC: Int,
        visibilityThreshold: Float,
    ): Double? {
        if (world != null && world.size > maxOf(indexA, indexB, indexC)) {
            val a = world[indexA]; val b = world[indexB]; val c = world[indexC]
            if (a.isVisible(visibilityThreshold) && b.isVisible(visibilityThreshold) && c.isVisible(visibilityThreshold)) {
                return JointAngleCalculator.angleDegrees3D(
                    PosePoint3D(a.x, a.y, a.z),
                    PosePoint3D(b.x, b.y, b.z),
                    PosePoint3D(c.x, c.y, c.z),
                )
            }
        }
        return angleAt(landmarks, indexA, indexB, indexC, visibilityThreshold)
    }

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
