package com.movit.core.training.model

import com.movit.core.training.geometry.PoseLandmarkMirroring

/**
 * One analyzed camera frame ready for the training engine.
 * Platform camera/ML adapters produce this; common code never sees CameraX/MediaPipe types.
 */
data class PoseFrame(
    val angles: JointAngles,
    val landmarks: List<Landmark>?,
    val worldLandmarks: List<Landmark>? = null,
    val isFrontCamera: Boolean,
    val timestampMs: Long,
    /** Width of the upright bitmap sent to pose detection (0 = unknown). */
    val analysisImageWidth: Int = 0,
    /** Height of the upright bitmap sent to pose detection (0 = unknown). */
    val analysisImageHeight: Int = 0,
) {
    val hasPose: Boolean get() = landmarks != null

    fun mirrored(): PoseFrame {
        if (!isFrontCamera || landmarks == null) return this
        val mirroredLandmarks = PoseLandmarkMirroring.mirrorLandmarks(landmarks)
        val mirroredWorld = worldLandmarks?.let(PoseLandmarkMirroring::mirrorLandmarks)
        return copy(
            landmarks = mirroredLandmarks,
            worldLandmarks = mirroredWorld,
            angles = PoseLandmarkMirroring.mirrorAngles(angles),
            isFrontCamera = false,
        )
    }
}
