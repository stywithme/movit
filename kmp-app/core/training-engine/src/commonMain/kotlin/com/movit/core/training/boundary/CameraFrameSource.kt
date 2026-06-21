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
}
