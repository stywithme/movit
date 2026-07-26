package com.movit.core.training.boundary

import com.movit.core.training.model.PoseFrame

/**
 * Platform camera lifecycle. Phase 07 actuals: Android CameraX, iOS AVFoundation.
 */
expect interface CameraFrameSource {
    fun start(configuration: CameraSourceConfiguration)

    fun stop()

    fun setFrameListener(listener: ((PoseFrame?) -> Unit)?)

    fun setErrorListener(listener: ((String) -> Unit)?)

    /** Invoked after a successful bind (initial start or lens switch). */
    fun setOnCameraBoundListener(listener: (() -> Unit)?)

    /**
     * Clears elbow estimator + sticky 3D/2D state for this frame source (E-08).
     * Call on lens switch / session start / flow exercise boundaries.
     */
    fun resetAngleTracking()

    /**
     * Enables/disables the elbow arbiter for the running session (EL-11 / WP-22).
     *
     * Applied to the live configuration without rebinding the camera, so it is safe to call
     * on every flow exercise boundary. Exercises that track no elbow skip the arbiter — its
     * EMA state, hold window and confidence output have no consumer there.
     */
    fun setElbowCorrectionEnabled(enabled: Boolean)
}
