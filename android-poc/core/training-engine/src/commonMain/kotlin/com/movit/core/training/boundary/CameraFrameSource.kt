package com.movit.core.training.boundary

import com.movit.core.training.model.PoseFrame

/**
 * Platform camera lifecycle. Phase 07 actuals: Android CameraX, iOS AVFoundation.
 */
expect interface CameraFrameSource {
    fun start(configuration: CameraSourceConfiguration)

    fun stop()

    fun setFrameListener(listener: ((PoseFrame?) -> Unit)?)
}
