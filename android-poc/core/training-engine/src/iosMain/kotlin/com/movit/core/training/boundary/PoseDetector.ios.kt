package com.movit.core.training.boundary

import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseFrame

actual interface PoseDetector {
    actual fun warmUp(configuration: PoseDetectorConfiguration)

    actual fun resetTrackingState()

    actual fun buildPoseFrame(
        landmarks: List<Landmark>,
        timestampMs: Long,
        isFrontCamera: Boolean,
    ): PoseFrame

    actual fun shutdown()
}
