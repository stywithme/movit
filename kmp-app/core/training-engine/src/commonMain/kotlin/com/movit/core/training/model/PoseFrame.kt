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
    /**
     * Joint codes whose 3D/2D angle mode flipped this frame (WP-02 / E-11).
     * Engine clears [com.movit.core.training.engine.AngleSmoother] buffers for these.
     */
    val angleModeSwitchedJointCodes: Set<String> = emptySet(),
) {
    val hasPose: Boolean get() = landmarks != null

    /**
     * Front-camera engine space: swap L/R **angles** (and mode-switch codes) only.
     *
     * Landmark / world lists are reused by reference — they stay MediaPipe-indexed.
     * Consumers must keep passing the original [isFrontCamera] flag so visibility and
     * position checks remap via [PoseLandmarkMirroring.mirroredIndex] / name XOR.
     * Do not “fix” landmark swapping without clearing that flag (latent PF-11).
     */
    fun mirrored(): PoseFrame {
        if (!isFrontCamera || landmarks == null) return this
        return copy(
            // Reuse lists — mirrorLandmarks is a documented no-op / identity (WP-08 / D-01).
            landmarks = landmarks,
            worldLandmarks = worldLandmarks,
            angles = PoseLandmarkMirroring.mirrorAngles(angles),
            angleModeSwitchedJointCodes = PoseLandmarkMirroring.mirrorJointCodes(angleModeSwitchedJointCodes),
            isFrontCamera = false,
        )
    }
}
