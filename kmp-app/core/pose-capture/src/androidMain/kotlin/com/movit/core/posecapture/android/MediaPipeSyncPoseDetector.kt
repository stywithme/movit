package com.movit.core.posecapture.android

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.movit.core.posecapture.boundary.trainingdebug.MediaPipeSyncRunningMode
import com.movit.core.posecapture.boundary.trainingdebug.PoseModelTypePort
import com.movit.core.posecapture.boundary.trainingdebug.ResolvedPoseModel
import com.movit.core.posecapture.boundary.trainingdebug.TrainingDebugPoseFrame
import com.movit.core.posecapture.boundary.trainingdebug.TrainingDebugSourceConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug-oriented synchronous MediaPipe detector for still images and sequential video frames.
 *
 * Uses [RunningMode.IMAGE] with [PoseLandmarker.detect] or [RunningMode.VIDEO] with
 * [PoseLandmarker.detectForVideo]. Never uses [RunningMode.LIVE_STREAM].
 */
class MediaPipeSyncPoseDetector(
    private val context: Context,
    private val modelPort: PoseModelTypePort,
) {
    private var landmarker: PoseLandmarker? = null
    private var runningMode: MediaPipeSyncRunningMode? = null
    private var resolvedModel: ResolvedPoseModel? = null
    private var lastConfig: TrainingDebugSourceConfig? = null
    private val landmarkSmoother = LandmarkSmoother()
    private val initLock = Any()
    private var useGpu = true
    private var heavyUpgradeInFlight = false
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var lastInferenceTimeMs: Long = 0L
        private set

    fun warmUp(
        mode: MediaPipeSyncRunningMode,
        config: TrainingDebugSourceConfig,
    ): ResolvedPoseModel = synchronized(initLock) {
        runningMode = mode
        lastConfig = config
        useGpu = config.useGpu
        val mpRunningMode = when (mode) {
            MediaPipeSyncRunningMode.IMAGE -> RunningMode.IMAGE
            MediaPipeSyncRunningMode.VIDEO -> RunningMode.VIDEO
        }
        try {
            val baseBuilder = BaseOptions.builder()
            if (useGpu) {
                baseBuilder.setDelegate(Delegate.GPU)
            } else {
                baseBuilder.setDelegate(Delegate.CPU)
            }
            val resolved = PoseModelResolver.resolveForDebug(
                context = context,
                baseBuilder = baseBuilder,
                modelPort = modelPort,
                requested = config.modelType,
            )
            resolvedModel = resolved
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseBuilder.build())
                .setRunningMode(mpRunningMode)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(config.minDetectionConfidence)
                .setMinTrackingConfidence(config.minTrackingConfidence)
                .setMinPosePresenceConfidence(config.minPresenceConfidence)
                .build()
            val previous = landmarker
            landmarker = PoseLandmarker.createFromOptions(context, options)
            closeLandmarkerQuietly(previous)
            landmarkSmoother.reset()
            Log.d(TAG, "Sync pose detector ready mode=$mode gpu=$useGpu model=${resolved.displayLabel}")
            if (resolved.scheduleHeavyUpgrade) {
                scheduleHeavyUpgrade(mode, config)
            }
            resolved
        } catch (e: Exception) {
            if (useGpu) {
                warmUp(mode, config.copy(useGpu = false))
            } else {
                error("Failed to initialize sync pose detector: ${e.message}")
            }
        }
    }

    fun detect(bitmap: Bitmap, timestampMs: Long): TrainingDebugPoseFrame? {
        val marker = landmarker ?: return null
        val mode = runningMode ?: return null
        val model = resolvedModel ?: return null
        val startMs = SystemClock.uptimeMillis()
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = when (mode) {
                MediaPipeSyncRunningMode.IMAGE -> marker.detect(mpImage)
                MediaPipeSyncRunningMode.VIDEO -> marker.detectForVideo(mpImage, timestampMs)
            }
            lastInferenceTimeMs = SystemClock.uptimeMillis() - startMs
            result?.let { buildFrame(it, bitmap.width, bitmap.height, timestampMs, model.displayLabel) }
        } catch (e: Exception) {
            Log.e(TAG, "detect failed mode=$mode: ${e.message}")
            null
        }
    }

    /**
     * Clears temporal smoothing state. For VIDEO mode also recreates the landmarker when
     * timestamps may move backwards (seek / restart), matching Legacy [resetForVideo].
     */
    fun resetTracking(reason: String, recreateVideoLandmarker: Boolean = false) {
        landmarkSmoother.reset()
        if (recreateVideoLandmarker && runningMode == MediaPipeSyncRunningMode.VIDEO) {
            val config = lastConfig
            if (config != null) {
                Log.d(TAG, "Recreating VIDEO landmarker after reset: $reason")
                warmUp(MediaPipeSyncRunningMode.VIDEO, config)
            }
        } else {
            Log.d(TAG, "Reset tracking smoother: $reason")
        }
    }

    fun shutdown() = synchronized(initLock) {
        shutdownLandmarkerOnly()
    }

    private fun buildFrame(
        result: PoseLandmarkerResult,
        width: Int,
        height: Int,
        timestampMs: Long,
        modelDisplayLabel: String,
    ): TrainingDebugPoseFrame? {
        if (result.landmarks().isEmpty()) return null
        val norm = result.landmarks()[0]
        val world = result.worldLandmarks().firstOrNull()
        val rawNormalized = MediaPipeLandmarkMapper.mapNormalizedRaw(norm)
        val smoothedNormalized = landmarkSmoother.smooth(norm, timestampMs)
        val rawWorld = world?.let(MediaPipeLandmarkMapper::mapWorldRaw)
        val smoothedWorld = world?.let { landmarkSmoother.smoothWorld(it, timestampMs) }
        return TrainingDebugPoseFrame(
            rawNormalizedLandmarks = rawNormalized,
            smoothedNormalizedLandmarks = smoothedNormalized,
            rawWorldLandmarks = rawWorld,
            smoothedWorldLandmarks = smoothedWorld,
            timestampMs = if (runningMode == MediaPipeSyncRunningMode.VIDEO) result.timestampMs() else timestampMs,
            inferenceTimeMs = lastInferenceTimeMs,
            analysisImageWidth = width,
            analysisImageHeight = height,
            modelDisplayLabel = modelDisplayLabel,
            isFrontCamera = false,
        )
    }

    private fun shutdownLandmarkerOnly() {
        closeLandmarkerQuietly(landmarker)
        landmarker = null
        runningMode = null
        resolvedModel = null
        landmarkSmoother.reset()
        heavyUpgradeInFlight = false
    }

    private fun closeLandmarkerQuietly(marker: PoseLandmarker?) {
        if (marker == null) return
        runCatching { marker.close() }
    }

    private fun scheduleHeavyUpgrade(
        mode: MediaPipeSyncRunningMode,
        config: TrainingDebugSourceConfig,
    ) {
        if (heavyUpgradeInFlight) return
        heavyUpgradeInFlight = true
        backgroundScope.launch {
            try {
                val cached = PoseLandmarkerHeavyModelStore.ensureCached(context)
                if (!cached) return@launch
                if (modelPort.getSelectedModel() != config.modelType) return@launch
                val preview = modelPort.resolveForInitialization(config.modelType)
                if (preview.usesHeavyFallback) return@launch
                Log.i(TAG, "Heavy model cached — swapping sync pose detector")
                synchronized(initLock) {
                    warmUp(mode, config)
                }
            } finally {
                heavyUpgradeInFlight = false
            }
        }
    }

    companion object {
        private const val TAG = "MediaPipeSyncPoseDetector"
    }
}
