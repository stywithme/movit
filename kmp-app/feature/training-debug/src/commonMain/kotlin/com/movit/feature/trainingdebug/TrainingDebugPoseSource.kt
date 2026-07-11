package com.movit.feature.trainingdebug

import com.movit.core.training.geometry.AngleModeStickyState
import com.movit.core.training.geometry.ElbowAngleEstimator
import com.movit.core.training.model.Landmark
import kotlinx.coroutines.flow.Flow

data class TrainingDebugSourceConfig(
    val modelType: DebugPoseModelType = DebugPoseModelType.FULL,
    val isFrontCamera: Boolean = true,
)

interface TrainingDebugPoseSource {
    val mode: TrainingDebugInputMode
    val frames: Flow<TrainingDebugFrameInput?>
    suspend fun start(config: TrainingDebugSourceConfig)
    suspend fun stop()
    suspend fun resetTracking(reason: String)
}

/** Landmark bundle emitted by platform detectors before analyzer ingestion. */
data class DebugPoseDetectionResult(
    val rawLandmarks: List<Landmark>,
    val smoothedLandmarks: List<Landmark>,
    val rawWorldLandmarks: List<Landmark>?,
    val smoothedWorldLandmarks: List<Landmark>?,
    val timestampMs: Long,
    val isFrontCamera: Boolean,
    val analysisImageWidth: Int,
    val analysisImageHeight: Int,
    val inferenceTimeMs: Long = 0L,
) {
    fun toFrameInput(
        elbowEstimator: ElbowAngleEstimator,
        stickyState: AngleModeStickyState,
    ): TrainingDebugFrameInput =
        TrainingDebugFrameFactory.fromLandmarks(
            landmarks = smoothedLandmarks,
            worldLandmarks = smoothedWorldLandmarks,
            timestampMs = timestampMs,
            isFrontCamera = isFrontCamera,
            analysisImageWidth = analysisImageWidth,
            analysisImageHeight = analysisImageHeight,
            inferenceTimeMs = inferenceTimeMs,
            elbowEstimator = elbowEstimator,
            stickyState = stickyState,
        ).let { assembled ->
            assembled.copy(
                rawLandmarks = rawLandmarks,
                rawWorldLandmarks = rawWorldLandmarks,
            )
        }
}
