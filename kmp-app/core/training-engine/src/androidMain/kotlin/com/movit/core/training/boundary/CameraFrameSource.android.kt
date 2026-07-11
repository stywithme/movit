package com.movit.core.training.boundary

import com.movit.core.training.model.PoseFrame

actual interface CameraFrameSource {
    actual fun start(configuration: CameraSourceConfiguration)

    actual fun stop()

    actual fun setFrameListener(listener: ((PoseFrame?) -> Unit)?)

    actual fun setErrorListener(listener: ((String) -> Unit)?)

    actual fun setOnCameraBoundListener(listener: (() -> Unit)?)

    actual fun resetAngleTracking()
}
