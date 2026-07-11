package com.movit.core.training.boundary

actual interface PoseDetector {
    actual fun warmUp(configuration: PoseDetectorConfiguration)

    actual fun resetTrackingState()

    actual fun shutdown()
}
