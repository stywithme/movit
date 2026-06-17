package com.movit.feature.trainingdebug

import com.movit.core.posecapture.MovitPoseCaptureIosBindings
import com.movit.core.posecapture.boundary.trainingdebug.TrainingDebugInputMode as PoseCaptureInputMode
import com.movit.core.posecapture.boundary.trainingdebug.TrainingDebugPoseSource
import com.movit.core.posecapture.boundary.trainingdebug.TrainingDebugSourceConfig
import com.movit.core.training.boundary.CameraSourceConfiguration

/**
 * iOS live-camera [TrainingDebugPoseSource] (D10).
 * Image/video modes remain disabled at UI layer until MediaPipe iOS sync APIs land.
 */
class IosTrainingDebugPoseSource : TrainingDebugPoseSource {
    override val mode: PoseCaptureInputMode = PoseCaptureInputMode.CAMERA

    private val cameraSource = MovitPoseCaptureIosBindings.createCameraFrameSource()
    private var frameListener: ((com.movit.core.posecapture.boundary.trainingdebug.TrainingDebugPoseFrame) -> Unit)? = null

    fun setFrameListener(listener: ((com.movit.core.posecapture.boundary.trainingdebug.TrainingDebugPoseFrame) -> Unit)?) {
        frameListener = listener
    }

    override suspend fun start(config: TrainingDebugSourceConfig) {
        cameraSource.setFrameListener { poseFrame ->
            val frame = poseFrame ?: return@setFrameListener
            frameListener?.invoke(
                com.movit.core.posecapture.boundary.trainingdebug.TrainingDebugPoseFrame(
                    rawNormalizedLandmarks = frame.landmarks ?: emptyList(),
                    smoothedNormalizedLandmarks = frame.landmarks ?: emptyList(),
                    rawWorldLandmarks = frame.worldLandmarks,
                    smoothedWorldLandmarks = frame.worldLandmarks,
                    timestampMs = frame.timestampMs,
                    inferenceTimeMs = 0L,
                    analysisImageWidth = frame.analysisImageWidth,
                    analysisImageHeight = frame.analysisImageHeight,
                    modelDisplayLabel = config.modelType.name.lowercase(),
                    isFrontCamera = frame.isFrontCamera,
                ),
            )
        }
        cameraSource.start(CameraSourceConfiguration())
    }

    override suspend fun stop() {
        cameraSource.setFrameListener(null)
        cameraSource.stop()
    }

    override suspend fun resetTracking(reason: String) {
        // iOS live stream resets on camera rebind; full MediaPipe VIDEO reset lands with Agent 2.
    }
}
