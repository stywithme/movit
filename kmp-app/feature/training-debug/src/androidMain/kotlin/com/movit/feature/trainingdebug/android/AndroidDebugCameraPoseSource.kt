package com.movit.feature.trainingdebug.android

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.movit.core.data.MovitData
import com.movit.core.posecapture.android.CameraXFrameSource
import com.movit.core.posecapture.android.MediaPipePoseDetector
import com.movit.core.posecapture.boundary.trainingdebug.PoseModelTypePort
import com.movit.core.training.boundary.CameraFrameSource
import com.movit.core.training.boundary.PoseDetectorConfiguration
import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.feature.training.resolveTrainingCameraConfiguration
import com.movit.feature.trainingdebug.DebugPoseModelType
import com.movit.feature.trainingdebug.TrainingDebugFrameInput
import com.movit.feature.trainingdebug.TrainingDebugInputMode
import com.movit.feature.trainingdebug.TrainingDebugPoseSource
import com.movit.feature.trainingdebug.TrainingDebugSourceConfig
import com.movit.feature.trainingdebug.toPoseModelType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Live camera debug source — reuses production [CameraXFrameSource] + [MediaPipePoseDetector].
 */
class AndroidDebugCameraPoseSource : TrainingDebugPoseSource {
    override val mode: TrainingDebugInputMode = TrainingDebugInputMode.CAMERA

    private val cameraSource: CameraXFrameSource?
    private val poseDetector: MediaPipePoseDetector?
    private val modelPort: PoseModelTypePort?
    private val frameFlow = MutableSharedFlow<TrainingDebugFrameInput?>(extraBufferCapacity = 1)
    override val frames: Flow<TrainingDebugFrameInput?> = frameFlow.asSharedFlow()

    private var previewView: PreviewView? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var isFrontCamera = true
    private var config = TrainingDebugSourceConfig()
    private var modelLabelCache = "full"

    init {
        if (MovitData.isInstalled) {
            val koin = MovitData.koin()
            cameraSource = koin.get<CameraFrameSource>() as? CameraXFrameSource
            poseDetector = koin.get<MediaPipePoseDetector>()
            modelPort = koin.get<PoseModelTypePort>()
        } else {
            cameraSource = null
            poseDetector = null
            modelPort = null
        }
    }

    fun bindPreview(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        this.previewView = previewView
        this.lifecycleOwner = lifecycleOwner
        cameraSource?.bindPreview(previewView, lifecycleOwner)
    }

    override suspend fun start(config: TrainingDebugSourceConfig) {
        this.config = config
        isFrontCamera = config.isFrontCamera
        val source = cameraSource ?: return
        applySelectedModel(config.modelType)
        source.setDebugFrameListener { detection, poseFrame ->
            if (poseFrame == null) {
                frameFlow.tryEmit(null)
                return@setDebugFrameListener
            }
            modelLabelCache = detection?.modelDisplayLabel?.ifBlank { modelLabelCache } ?: modelLabelCache
            frameFlow.tryEmit(detection!!.toDebugFrameInput(poseFrame))
        }
        source.setFrameListener { /* debug path uses setDebugFrameListener */ }
        source.start(resolveTrainingCameraConfiguration(isFrontCamera))
    }

    override suspend fun stop() {
        cameraSource?.setDebugFrameListener(null)
        cameraSource?.setFrameListener(null)
        cameraSource?.stop()
    }

    override suspend fun resetTracking(reason: String) {
        poseDetector?.resetTrackingState()
        PoseFrameAssembler.resetElbowEstimator()
    }

    fun switchCamera() {
        isFrontCamera = !isFrontCamera
        config = config.copy(isFrontCamera = isFrontCamera)
        val source = cameraSource ?: return
        source.start(resolveTrainingCameraConfiguration(isFrontCamera))
    }

    fun skippedBusyFrames(): Int = poseDetector?.consumeBusySkipCount() ?: 0

    fun modelLabel(): String = modelLabelCache

    private fun applySelectedModel(modelType: DebugPoseModelType) {
        val port = modelPort ?: return
        val requested = modelType.toPoseModelType()
        port.setSelectedModel(requested)
        modelLabelCache = port.resolveForInitialization(requested).displayLabel
        poseDetector?.warmUp(PoseDetectorConfiguration(useGpu = true))
    }
}
