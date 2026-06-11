package com.movit.core.posecapture.android

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.movit.core.training.boundary.PoseDetector
import com.movit.core.training.boundary.PoseDetectorConfiguration
import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseFrame
import java.util.concurrent.ConcurrentHashMap

class MediaPipePoseDetector(
    private val context: Context,
) : PoseDetector {
    data class DetectionResult(
        val landmarks: List<Landmark>,
        val worldLandmarks: List<Landmark>?,
        val timestampMs: Long,
        val inferenceTimeMs: Long,
        val isFrontCamera: Boolean,
    )

    interface Listener {
        fun onPoseDetected(result: DetectionResult)
        fun onNoPoseDetected()
        fun onError(message: String)
    }

    companion object {
        private const val TAG = "MediaPipePoseDetector"
        private const val MIN_DETECTION = 0.5f
        private const val MIN_TRACKING = 0.5f
        private const val MIN_PRESENCE = 0.5f
    }

    private var landmarker: PoseLandmarker? = null
    private var modelAsset = "pose_landmarker_full.task"
    private var useGpu = true
    private val frameCameraState = ConcurrentHashMap<Long, Boolean>()
    private val landmarkSmoother = LandmarkSmoother()
    private var listener: Listener? = null

    var lastInferenceTimeMs: Long = 0L
        private set

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    override fun warmUp(configuration: PoseDetectorConfiguration) {
        modelAsset = configuration.modelAssetName ?: modelAsset
        useGpu = configuration.useGpu
        shutdown()
        try {
            val base = BaseOptions.builder().setModelAssetPath(modelAsset)
            if (useGpu) base.setDelegate(Delegate.GPU) else base.setDelegate(Delegate.CPU)
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(base.build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(configuration.minDetectionConfidence)
                .setMinTrackingConfidence(configuration.minTrackingConfidence)
                .setMinPosePresenceConfidence(MIN_PRESENCE)
                .setResultListener(this::onPoseResult)
                .setErrorListener { e -> listener?.onError(e.message ?: "Pose error") }
                .build()
            landmarker = PoseLandmarker.createFromOptions(context, options)
            Log.d(TAG, "Pose landmarker ready gpu=$useGpu model=$modelAsset")
        } catch (e: Exception) {
            if (useGpu) {
                warmUp(configuration.copy(useGpu = false))
            } else {
                listener?.onError("Failed to initialize pose detection: ${e.message}")
            }
        }
    }

    fun detectAsync(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val marker = landmarker
        if (marker == null) {
            imageProxy.close()
            return
        }
        try {
            val frameTime = SystemClock.uptimeMillis()
            frameCameraState[frameTime] = isFrontCamera
            val bitmap = imageProxy.toBitmap()
            imageProxy.close()
            val mpImage = BitmapImageBuilder(bitmap).build()
            marker.detectAsync(mpImage, frameTime)
        } catch (e: Exception) {
            Log.e(TAG, "detectAsync failed: ${e.message}")
            try { imageProxy.close() } catch (_: Exception) {}
        }
    }

    override fun buildPoseFrame(
        landmarks: List<Landmark>,
        timestampMs: Long,
        isFrontCamera: Boolean,
    ): PoseFrame = PoseFrameAssembler.assemble(
        landmarks = landmarks,
        timestampMs = timestampMs,
        isFrontCamera = isFrontCamera,
    )

    private fun onPoseResult(result: PoseLandmarkerResult, @Suppress("UNUSED_PARAMETER") input: com.google.mediapipe.framework.image.MPImage) {
        val finishMs = SystemClock.uptimeMillis()
        val frameTs = result.timestampMs()
        lastInferenceTimeMs = finishMs - frameTs
        val isFrontCamera = frameCameraState.remove(frameTs) ?: false
        if (result.landmarks().isEmpty()) {
            listener?.onNoPoseDetected()
            return
        }
        val norm = result.landmarks()[0]
        val world = result.worldLandmarks().firstOrNull()
        val smoothed = landmarkSmoother.smooth(norm, frameTs)
        val smoothedWorld = world?.let(::mapWorldLandmarks)
        listener?.onPoseDetected(
            DetectionResult(
                landmarks = smoothed,
                worldLandmarks = smoothedWorld,
                timestampMs = frameTs,
                inferenceTimeMs = lastInferenceTimeMs,
                isFrontCamera = isFrontCamera,
            ),
        )
    }

    override fun shutdown() {
        landmarker?.close()
        landmarker = null
        frameCameraState.clear()
        landmarkSmoother.reset()
    }

    private fun mapWorldLandmarks(
        world: List<com.google.mediapipe.tasks.components.containers.Landmark>,
    ): List<Landmark> = world.map { lm ->
        Landmark(
            x = lm.x(),
            y = lm.y(),
            z = lm.z(),
            visibility = lm.visibility().orElse(1f),
            presence = lm.presence().orElse(1f),
        )
    }
}
