package com.movit.core.training.boundary

import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseFrame

/**
 * Platform pose ML boundary. Phase 07 actuals: Android MediaPipe, iOS Vision.
 * [buildPoseFrame] keeps angle assembly in common code via [com.movit.core.training.geometry.JointAngleCalculator].
 */
expect interface PoseDetector {
    fun warmUp(configuration: PoseDetectorConfiguration)

    fun buildPoseFrame(
        landmarks: List<Landmark>,
        timestampMs: Long,
        isFrontCamera: Boolean,
    ): PoseFrame

    fun shutdown()
}
