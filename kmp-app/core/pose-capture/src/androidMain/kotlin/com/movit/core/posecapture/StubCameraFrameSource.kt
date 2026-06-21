package com.movit.core.posecapture

import com.movit.core.training.boundary.CameraFrameSource
import com.movit.core.training.boundary.CameraSourceConfiguration
import com.movit.core.training.model.PoseFrame

/** WS-0 placeholder — replaced by CameraX actual in WS-4. */
class StubCameraFrameSource : CameraFrameSource {
    override fun start(configuration: CameraSourceConfiguration) = Unit

    override fun stop() = Unit

    override fun setFrameListener(listener: ((PoseFrame?) -> Unit)?) = Unit

    override fun setErrorListener(listener: ((String) -> Unit)?) = Unit

    override fun setOnCameraBoundListener(listener: (() -> Unit)?) = Unit
}
