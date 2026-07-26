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
    /**
     * WP-23: per-joint trust in [angles], keyed by joint code, values in `[0,1]`.
     *
     * **An absent key means "no confidence information"** and must be treated as fully
     * trusted — only producers that can actually measure their own doubt populate it
     * (today: the elbows, via `ElbowAngleEstimator`).
     */
    val angleConfidence: Map<String, Float> = emptyMap(),
    /**
     * WP-24: per-joint measurement geometry quality in `[0,1]`, keyed by joint code.
     * `0` means the joint's motion plane contains the optical axis — no algorithm can
     * recover the angle from this camera position; only moving the camera helps.
     */
    val angleObservability: Map<String, Float> = emptyMap(),
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
            // WP-23: keys must travel with the angles they describe, or confidence
            // silently describes the opposite arm on the front camera.
            angleConfidence = PoseLandmarkMirroring.mirrorJointKeyedMap(angleConfidence),
            angleObservability = PoseLandmarkMirroring.mirrorJointKeyedMap(angleObservability),
            isFrontCamera = false,
        )
    }
}
