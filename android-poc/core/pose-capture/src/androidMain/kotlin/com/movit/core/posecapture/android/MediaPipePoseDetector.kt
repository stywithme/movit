package com.movit.core.posecapture.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.os.ParcelFileDescriptor
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
import com.movit.core.posecapture.boundary.NoOpPoseRefiner
import com.movit.core.posecapture.boundary.PoseRefiner
import com.movit.core.training.boundary.PoseDetector
import com.movit.core.training.boundary.PoseDetectorConfiguration
import com.movit.core.training.diagnostics.TrainingPipelineDiagnostics
import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseFrame
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

class MediaPipePoseDetector(
    private val context: Context,
    private val poseRefiner: PoseRefiner = NoOpPoseRefiner,
) : PoseDetector {
    data class DetectionResult(
        val landmarks: List<Landmark>,
        val worldLandmarks: List<Landmark>?,
        val timestampMs: Long,
        val inferenceTimeMs: Long,
        val isFrontCamera: Boolean,
        val analysisImageWidth: Int,
        val analysisImageHeight: Int,
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
        private const val MODEL_TYPE_HEAVY = "heavy"
        private const val INFERENCE_STALL_TIMEOUT_MS = 1_500L
    }

    private var landmarker: PoseLandmarker? = null
    private var modelLabel = PoseLandmarkerHeavyModelStore.FULL_MODEL_ASSET
    private var useGpu = true
    private val frameCameraState = ConcurrentHashMap<Long, Boolean>()
    private val landmarkSmoother = LandmarkSmoother()
    private var listener: Listener? = null
    private var lastConfiguration: PoseDetectorConfiguration? = null
    private var heavyUpgradeInFlight = false
    private val initLock = Any()
    private val bitmapLock = Any()
    private var lastFrameBitmap: Bitmap? = null
    private val transformMatrix = Matrix()
    private val srcRect = RectF()
    private val dstRect = RectF()
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inferenceInFlight = AtomicBoolean(false)
    private val lastSubmittedFrameMs = AtomicLong(0L)
    private val lastStallWarningMs = AtomicLong(0L)
    private val skippedBusyFrames = AtomicInteger(0)

    var lastInferenceTimeMs: Long = 0L
        private set

    fun consumeBusySkipCount(): Int = skippedBusyFrames.getAndSet(0)

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    override fun warmUp(configuration: PoseDetectorConfiguration) {
        lastConfiguration = configuration
        synchronized(initLock) {
            useGpu = configuration.useGpu
            shutdownLandmarkerOnly()
            try {
                val baseBuilder = BaseOptions.builder()
                val resolved = applyModelToBaseOptions(baseBuilder, configuration)
                modelLabel = resolved.label
                if (useGpu) {
                    baseBuilder.setDelegate(Delegate.GPU)
                } else {
                    baseBuilder.setDelegate(Delegate.CPU)
                }
                val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseBuilder.build())
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumPoses(1)
                    .setMinPoseDetectionConfidence(configuration.minDetectionConfidence)
                    .setMinTrackingConfidence(configuration.minTrackingConfidence)
                    .setMinPosePresenceConfidence(MIN_PRESENCE)
                    .setResultListener(this::onPoseResult)
                    .setErrorListener { e ->
                        inferenceInFlight.set(false)
                        listener?.onError(e.message ?: "Pose error")
                    }
                    .build()
                landmarker = PoseLandmarker.createFromOptions(context, options)
                Log.d(TAG, "Pose landmarker ready gpu=$useGpu model=$modelLabel")
                if (resolved.scheduleHeavyUpgrade) {
                    scheduleHeavyUpgrade(configuration)
                }
            } catch (e: Exception) {
                if (useGpu) {
                    warmUp(configuration.copy(useGpu = false))
                } else {
                    listener?.onError("Failed to initialize pose detection: ${e.message}")
                }
            }
        }
    }

    fun detectAsync(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val marker = landmarker
        if (marker == null) {
            imageProxy.close()
            return
        }
        val frameTime = SystemClock.uptimeMillis()
        if (!tryAcquireInferenceSlot(frameTime)) {
            imageProxy.close()
            return
        }
        try {
            frameCameraState[frameTime] = isFrontCamera
            lastSubmittedFrameMs.set(frameTime)
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val sourceBitmap = imageProxy.toBitmap()
            imageProxy.close()
            val analysisBitmap = if (rotationDegrees == 0) {
                sourceBitmap
            } else {
                val rotated = rotateBitmapForAnalysis(sourceBitmap, rotationDegrees)
                sourceBitmap.recycle()
                rotated
            }
            lastAnalysisWidth = analysisBitmap.width
            lastAnalysisHeight = analysisBitmap.height
            synchronized(bitmapLock) {
                lastFrameBitmap?.takeIf { it !== analysisBitmap }?.recycle()
                lastFrameBitmap = analysisBitmap
            }
            val mpImage = BitmapImageBuilder(analysisBitmap).build()
            marker.detectAsync(mpImage, frameTime)
        } catch (e: Exception) {
            inferenceInFlight.set(false)
            Log.e(TAG, "detectAsync failed: ${e.message}")
            try { imageProxy.close() } catch (_: Exception) {}
        }
    }

    private fun tryAcquireInferenceSlot(nowMs: Long): Boolean {
        if (inferenceInFlight.compareAndSet(false, true)) return true
        val submittedAt = lastSubmittedFrameMs.get()
        val stalled = submittedAt > 0L && nowMs - submittedAt > INFERENCE_STALL_TIMEOUT_MS
        val lastWarning = lastStallWarningMs.get()
        if (stalled && nowMs - lastWarning > INFERENCE_STALL_TIMEOUT_MS && lastStallWarningMs.compareAndSet(lastWarning, nowMs)) {
            TrainingPipelineDiagnostics.recordInferenceStall()
        }
        skippedBusyFrames.incrementAndGet()
        return false
    }

    private var lastAnalysisWidth: Int = 0
    private var lastAnalysisHeight: Int = 0

    /**
     * Rotates the camera buffer to upright display orientation before MediaPipe.
     * Front-camera horizontal mirror is **not** applied here — preview mirroring is handled
     * in [com.movit.core.training.geometry.DisplayLandmarkTransform] so engine math stays on
     * unmirrored landmarks + [com.movit.core.training.model.PoseFrame.mirrored].
     */
    internal fun rotateBitmapForAnalysis(source: Bitmap, rotationDegrees: Int): Bitmap {
        transformMatrix.reset()
        transformMatrix.postRotate(rotationDegrees.toFloat())
        srcRect.set(0f, 0f, source.width.toFloat(), source.height.toFloat())
        transformMatrix.mapRect(dstRect, srcRect)
        val outWidth = dstRect.width().roundToInt().coerceAtLeast(1)
        val outHeight = dstRect.height().roundToInt().coerceAtLeast(1)
        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            translate(-dstRect.left, -dstRect.top)
            concat(transformMatrix)
            drawBitmap(source, 0f, 0f, null)
        }
        return output
    }

    /**
     * Returns a downscaled JPEG of the most recent camera frame for report evidence.
     * Held only until the next frame arrives — not persisted until the caller writes it.
     */
    fun takeSnapshotJpeg(maxDimension: Int, quality: Int): ByteArray? {
        val source = synchronized(bitmapLock) { lastFrameBitmap } ?: return null
        val working = source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        return try {
            compressBitmapToJpeg(working, maxDimension, quality)
        } finally {
            working.recycle()
        }
    }

    private fun compressBitmapToJpeg(bitmap: Bitmap, maxDimension: Int, quality: Int): ByteArray? {
        val toCompress = scaleDown(bitmap, maxDimension)
        return ByteArrayOutputStream().use { stream ->
            if (!toCompress.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), stream)) {
                if (toCompress !== bitmap) toCompress.recycle()
                return null
            }
            val bytes = stream.toByteArray()
            if (toCompress !== bitmap) toCompress.recycle()
            bytes
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longest.toFloat()
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
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
        try {
            if (result.landmarks().isEmpty()) {
                listener?.onNoPoseDetected()
                return
            }
            val norm = result.landmarks()[0]
            val world = result.worldLandmarks().firstOrNull()
            val smoothed = landmarkSmoother.smooth(norm, frameTs)
            val refinedLandmarks = if (poseRefiner.isAvailable) {
                poseRefiner.refineLandmarks(smoothed)
            } else {
                smoothed
            }
            val smoothedWorld = world?.let(::mapWorldLandmarks)
            listener?.onPoseDetected(
                DetectionResult(
                    landmarks = refinedLandmarks,
                    worldLandmarks = smoothedWorld,
                    timestampMs = frameTs,
                    inferenceTimeMs = lastInferenceTimeMs,
                    isFrontCamera = isFrontCamera,
                    analysisImageWidth = lastAnalysisWidth,
                    analysisImageHeight = lastAnalysisHeight,
                ),
            )
        } finally {
            inferenceInFlight.set(false)
        }
    }

    override fun shutdown() {
        synchronized(initLock) {
            shutdownLandmarkerOnly()
        }
        synchronized(bitmapLock) {
            lastFrameBitmap?.recycle()
            lastFrameBitmap = null
        }
    }

    private fun shutdownLandmarkerOnly() {
        landmarker?.close()
        landmarker = null
        frameCameraState.clear()
        landmarkSmoother.reset()
        inferenceInFlight.set(false)
        lastSubmittedFrameMs.set(0L)
        lastStallWarningMs.set(0L)
        skippedBusyFrames.set(0)
        heavyUpgradeInFlight = false
    }

    private data class ResolvedModel(
        val label: String,
        val scheduleHeavyUpgrade: Boolean = false,
    )

    private fun applyModelToBaseOptions(
        baseBuilder: BaseOptions.Builder,
        configuration: PoseDetectorConfiguration,
    ): ResolvedModel {
        configuration.modelAssetName?.let { explicitAsset ->
            baseBuilder.setModelAssetPath(explicitAsset)
            return ResolvedModel(label = explicitAsset)
        }

        val modelType = PoseModelTypePreference.getModelType(context)
        if (modelType != MODEL_TYPE_HEAVY) {
            baseBuilder.setModelAssetPath(PoseLandmarkerHeavyModelStore.FULL_MODEL_ASSET)
            return ResolvedModel(label = PoseLandmarkerHeavyModelStore.FULL_MODEL_ASSET)
        }

        return when (val resolved = PoseLandmarkerHeavyModelStore.resolveHeavyOrFallback(context)) {
            is PoseLandmarkerHeavyModelStore.ResolveResult.HeavyFile -> {
                applyHeavyFileToBaseOptions(baseBuilder, File(resolved.absolutePath))
                ResolvedModel(label = resolved.absolutePath)
            }
            is PoseLandmarkerHeavyModelStore.ResolveResult.HeavyAsset -> {
                baseBuilder.setModelAssetPath(resolved.assetName)
                ResolvedModel(label = resolved.assetName)
            }
            is PoseLandmarkerHeavyModelStore.ResolveResult.FallbackFullAsset -> {
                baseBuilder.setModelAssetPath(resolved.assetName)
                Log.i(
                    TAG,
                    "Heavy model unavailable — using bundled full; download will run in background",
                )
                ResolvedModel(
                    label = "${resolved.assetName} (heavy pending)",
                    scheduleHeavyUpgrade = true,
                )
            }
        }
    }

    private fun applyHeavyFileToBaseOptions(baseBuilder: BaseOptions.Builder, modelFile: File) {
        ParcelFileDescriptor.open(modelFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            baseBuilder.setModelAssetFileDescriptor(pfd.detachFd())
        }
    }

    private fun scheduleHeavyUpgrade(configuration: PoseDetectorConfiguration) {
        if (heavyUpgradeInFlight) return
        if (PoseModelTypePreference.getModelType(context) != MODEL_TYPE_HEAVY) return
        heavyUpgradeInFlight = true
        backgroundScope.launch {
            try {
                val cached = PoseLandmarkerHeavyModelStore.ensureCached(context)
                if (!cached) return@launch
                if (PoseModelTypePreference.getModelType(context) != MODEL_TYPE_HEAVY) return@launch
                val resolved = PoseLandmarkerHeavyModelStore.resolveHeavyOrFallback(context)
                if (resolved is PoseLandmarkerHeavyModelStore.ResolveResult.FallbackFullAsset) {
                    return@launch
                }
                Log.i(TAG, "Heavy model cached — re-initializing pose landmarker")
                warmUp(lastConfiguration ?: configuration)
            } finally {
                heavyUpgradeInFlight = false
            }
        }
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
