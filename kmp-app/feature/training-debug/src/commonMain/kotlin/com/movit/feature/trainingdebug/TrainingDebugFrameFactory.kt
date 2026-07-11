package com.movit.feature.trainingdebug

import com.movit.core.training.geometry.AngleModeStickyState
import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseFrame

object TrainingDebugFrameFactory {
    fun fromAssembled(
        poseFrame: PoseFrame,
        rawLandmarks: List<Landmark>,
        smoothedLandmarks: List<Landmark>,
        rawWorldLandmarks: List<Landmark>?,
        smoothedWorldLandmarks: List<Landmark>?,
        inferenceTimeMs: Long = 0L,
        elbowDiagnosticsPort: ElbowDiagnosticsPort = ElbowDiagnosticsPort.NoOp,
    ): TrainingDebugFrameInput = TrainingDebugFrameInput(
        poseFrame = poseFrame,
        rawLandmarks = rawLandmarks,
        smoothedLandmarks = smoothedLandmarks,
        rawWorldLandmarks = rawWorldLandmarks,
        smoothedWorldLandmarks = smoothedWorldLandmarks,
        inferenceTimeMs = inferenceTimeMs,
        elbowDiagnosticsPort = elbowDiagnosticsPort,
    )

    fun fromLandmarks(
        landmarks: List<Landmark>,
        worldLandmarks: List<Landmark>?,
        timestampMs: Long,
        isFrontCamera: Boolean,
        analysisImageWidth: Int = 0,
        analysisImageHeight: Int = 0,
        inferenceTimeMs: Long = 0L,
        elbowEstimator: com.movit.core.training.geometry.ElbowAngleEstimator =
            com.movit.core.training.geometry.ElbowAngleEstimator(),
        stickyState: AngleModeStickyState = AngleModeStickyState(),
    ): TrainingDebugFrameInput {
        val poseFrame = PoseFrameAssembler.assemble(
            landmarks = landmarks,
            timestampMs = timestampMs,
            isFrontCamera = isFrontCamera,
            worldLandmarks = worldLandmarks,
            analysisImageWidth = analysisImageWidth,
            analysisImageHeight = analysisImageHeight,
            estimator = elbowEstimator,
            stickyState = stickyState,
        )
        return fromAssembled(
            poseFrame = poseFrame,
            rawLandmarks = landmarks,
            smoothedLandmarks = landmarks,
            rawWorldLandmarks = worldLandmarks,
            smoothedWorldLandmarks = worldLandmarks,
            inferenceTimeMs = inferenceTimeMs,
            elbowDiagnosticsPort = ElbowEstimatorDiagnosticsPort(elbowEstimator),
        )
    }
}
