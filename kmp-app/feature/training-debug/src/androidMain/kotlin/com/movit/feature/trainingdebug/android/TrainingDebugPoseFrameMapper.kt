package com.movit.feature.trainingdebug.android

import com.movit.core.posecapture.android.MediaPipePoseDetector
import com.movit.core.posecapture.boundary.trainingdebug.PoseModelType
import com.movit.core.posecapture.boundary.trainingdebug.TrainingDebugPoseFrame
import com.movit.core.posecapture.boundary.trainingdebug.TrainingDebugSourceConfig as PoseCaptureDebugConfig
import com.movit.core.training.geometry.AngleModeStickyState
import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.core.training.model.PoseFrame
import com.movit.feature.trainingdebug.DebugPoseModelType
import com.movit.feature.trainingdebug.ElbowDiagnosticsPort
import com.movit.feature.trainingdebug.ElbowEstimatorDiagnosticsPort
import com.movit.feature.trainingdebug.TrainingDebugFrameFactory
import com.movit.feature.trainingdebug.TrainingDebugFrameInput
import com.movit.feature.trainingdebug.TrainingDebugSourceConfig

fun DebugPoseModelType.toPoseModelType(): PoseModelType = when (this) {
    DebugPoseModelType.FULL -> PoseModelType.FULL
    DebugPoseModelType.HEAVY -> PoseModelType.HEAVY
}

fun TrainingDebugSourceConfig.toPoseCaptureConfig(): PoseCaptureDebugConfig =
    PoseCaptureDebugConfig(modelType = modelType.toPoseModelType())

fun MediaPipePoseDetector.DetectionResult.toDebugFrameInput(
    poseFrame: PoseFrame,
    elbowDiagnosticsPort: ElbowDiagnosticsPort = ElbowDiagnosticsPort.NoOp,
): TrainingDebugFrameInput =
    TrainingDebugFrameFactory.fromAssembled(
        poseFrame = poseFrame,
        rawLandmarks = rawNormalizedLandmarks,
        smoothedLandmarks = landmarks,
        rawWorldLandmarks = rawWorldLandmarks,
        smoothedWorldLandmarks = worldLandmarks,
        inferenceTimeMs = inferenceTimeMs,
        elbowDiagnosticsPort = elbowDiagnosticsPort,
    )

fun TrainingDebugPoseFrame.toDebugFrameInput(
    isFrontCamera: Boolean,
    elbowEstimator: com.movit.core.training.geometry.ElbowAngleEstimator,
    stickyState: AngleModeStickyState,
): TrainingDebugFrameInput {
    val poseFrame = PoseFrameAssembler.assemble(
        landmarks = smoothedNormalizedLandmarks,
        timestampMs = timestampMs,
        isFrontCamera = isFrontCamera,
        worldLandmarks = smoothedWorldLandmarks,
        analysisImageWidth = analysisImageWidth,
        analysisImageHeight = analysisImageHeight,
        estimator = elbowEstimator,
        stickyState = stickyState,
    )
    return TrainingDebugFrameFactory.fromAssembled(
        poseFrame = poseFrame,
        rawLandmarks = rawNormalizedLandmarks,
        smoothedLandmarks = smoothedNormalizedLandmarks,
        rawWorldLandmarks = rawWorldLandmarks,
        smoothedWorldLandmarks = smoothedWorldLandmarks,
        inferenceTimeMs = inferenceTimeMs,
        elbowDiagnosticsPort = ElbowEstimatorDiagnosticsPort(elbowEstimator),
    )
}
