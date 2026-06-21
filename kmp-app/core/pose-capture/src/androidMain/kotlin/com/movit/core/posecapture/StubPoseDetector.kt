package com.movit.core.posecapture

import com.movit.core.training.boundary.PoseDetector
import com.movit.core.training.boundary.PoseDetectorConfiguration
import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseFrame

/** WS-0 placeholder — replaced by MediaPipe actual in WS-4. */
class StubPoseDetector : PoseDetector {
    override fun warmUp(configuration: PoseDetectorConfiguration) = Unit

    override fun resetTrackingState() = Unit

    override fun buildPoseFrame(
        landmarks: List<Landmark>,
        timestampMs: Long,
        isFrontCamera: Boolean,
    ): PoseFrame = PoseFrameAssembler.assemble(landmarks, timestampMs, isFrontCamera)

    override fun shutdown() = Unit
}
