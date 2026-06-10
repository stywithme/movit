package com.movit.core.training.model

/**
 * One analyzed camera frame ready for the training engine.
 * Platform camera/ML adapters produce this; common code never sees CameraX/MediaPipe types.
 */
data class PoseFrame(
    val angles: JointAngles,
    val landmarks: List<Landmark>?,
    val isFrontCamera: Boolean,
    val timestampMs: Long,
) {
    val hasPose: Boolean get() = landmarks != null
}
