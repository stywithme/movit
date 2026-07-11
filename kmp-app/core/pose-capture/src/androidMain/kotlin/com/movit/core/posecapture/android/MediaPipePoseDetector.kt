package com.movit.core.posecapture.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
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
import com.movit.core.posecapture.boundary.trainingdebug.PoseModelType
import com.movit.core.posecapture.boundary.trainingdebug.PoseModelTypePort
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
import kotlin.math.max
import kotlin.math.roundToInt

class MediaPipePoseDetector(
    private val context: Context,
    private val poseRefiner: PoseRefiner = NoOpPoseRefiner,
    private val modelPort: PoseModelTypePort = AndroidPoseModelTypePort(context),
) : PoseDetector {
    data class DetectionResult(
        val landmarks: List<Landmark>,
        val worldLandmarks: List<Landmark>?,
        val rawNormalizedLandmarks: List<Landmark>,
        val rawWorldLandmarks: List<Landmark>?,
        val timestampMs: Long,
        val inferenceTimeMs: Long,
        val isFrontCamera: Boolean,
        val analysisImageWidth: Int,
        val analysisImageHeight: Int,
        val modelDisplayLabel: String = "",
    )

    interface Listener {
        fun onPoseDetected(result: DetectionResult)
        fun onNoPoseDetected(isFrontCamera: Boolean)
        fun onError(message: String)
    }

    companion object {
        private const val TAG = "MediaPipePoseDetector"
        private const val INFERENCE_STALL_TIMEOUT_MS = 1_500L
        private const val MAX_FRAME_CAMERA_STATE = 64
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
    /**
     * Double-buffer for rotation reuse (WP-09 tear fix): while [takeSnapshotJpegs] copies
     * [lastFrameBitmap] under [bitmapLock], the next frame draws into the alternate buffer.
     */
    private var rotatedBitmapA: Bitmap? = null
    private var rotatedBitmapB: Bitmap? = null
    private var writeRotatedA = true
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
            try {
                val baseBuilder = buildBaseOptions(useGpu)
                val resolved = PoseModelResolver.resolve(
                    context = context,
                    baseBuilder = baseBuilder,
                    configuration = configuration,
                    modelPort = modelPort,
                )
                modelLabel = resolved.displayLabel
                val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseBuilder.build())
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumPoses(1)
                    .setMinPoseDetectionConfidence(configuration.minDetectionConfidence)
                    .setMinTrackingConfidence(configuration.minTrackingConfidence)
                    .setMinPosePresenceConfidence(configuration.minPosePresenceConfidence)
                    .setResultListener(this::onPoseResult)
                    .setErrorListener { e ->
                        inferenceInFlight.set(false)
                        frameCameraState.clear()
                        listener?.onError(e.message ?: "Pose error")
                    }
                    .build()
                val previous = landmarker
                landmarker = PoseLandmarker.createFromOptions(context, options)
                closeLandmarkerQuietly(previous)
                frameCameraState.clear()
                landmarkSmoother.reset()
                inferenceInFlight.set(false)
                lastSubmittedFrameMs.set(0L)
                lastStallWarningMs.set(0L)
                skippedBusyFrames.set(0)
                heavyUpgradeInFlight = false
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

    private fun buildBaseOptions(useGpu: Boolean): BaseOptions.Builder {
        val baseBuilder = BaseOptions.builder()
        if (useGpu) {
            baseBuilder.setDelegate(Delegate.GPU)
        } else {
            baseBuilder.setDelegate(Delegate.CPU)
        }
        return baseBuilder
    }

    override fun resetTrackingState() {
        landmarkSmoother.reset()
        frameCameraState.clear()
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
            rememberFrameCamera(frameTime, isFrontCamera)
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
                val previous = lastFrameBitmap
                lastFrameBitmap = analysisBitmap
                // Recycle only one-shot (non-pool) bitmaps from rotationDegrees == 0 path.
                if (previous != null && previous !== analysisBitmap && !isRotatedPoolBitmap(previous)) {
                    previous.recycle()
                }
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
     *
     * Alternates between two reusable buffers so snapshot copy of the published frame cannot
     * tear against the next draw (WP-09).
     */
    internal fun rotateBitmapForAnalysis(source: Bitmap, rotationDegrees: Int): Bitmap {
        transformMatrix.reset()
        transformMatrix.postRotate(rotationDegrees.toFloat())
        srcRect.set(0f, 0f, source.width.toFloat(), source.height.toFloat())
        transformMatrix.mapRect(dstRect, srcRect)
        val outWidth = dstRect.width().roundToInt().coerceAtLeast(1)
        val outHeight = dstRect.height().roundToInt().coerceAtLeast(1)
        val output = obtainRotatedWriteBuffer(outWidth, outHeight)
        output.eraseColor(0)
        Canvas(output).apply {
            translate(-dstRect.left, -dstRect.top)
            concat(transformMatrix)
            drawBitmap(source, 0f, 0f, null)
        }
        return output
    }

    private fun obtainRotatedWriteBuffer(outWidth: Int, outHeight: Int): Bitmap {
        val useA = writeRotatedA
        writeRotatedA = !writeRotatedA
        val existing = if (useA) rotatedBitmapA else rotatedBitmapB
        val reusable = existing?.takeIf {
            it.width == outWidth && it.height == outHeight && !it.isRecycled
        }
        if (reusable != null) return reusable
        val created = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        synchronized(bitmapLock) {
            val published = lastFrameBitmap
            if (useA) {
                rotatedBitmapA?.takeIf { it !== created && it !== published }?.recycle()
                rotatedBitmapA = created
            } else {
                rotatedBitmapB?.takeIf { it !== created && it !== published }?.recycle()
                rotatedBitmapB = created
            }
        }
        return created
    }

    private fun isRotatedPoolBitmap(bitmap: Bitmap): Boolean =
        bitmap === rotatedBitmapA || bitmap === rotatedBitmapB

    /**
     * Returns a downscaled JPEG of the most recent camera frame for report evidence.
     * Held only until the next frame arrives — not persisted until the caller writes it.
     */
    fun takeSnapshotJpeg(maxDimension: Int, quality: Int): ByteArray? {
        val working = copyLastFrameBitmap() ?: return null
        return try {
            compressBitmapToJpeg(working, maxDimension, quality)
        } finally {
            working.recycle()
        }
    }

    /** One bitmap copy under [bitmapLock]; full + thumb JPEG from the same frame (A-13/H-07). */
    fun takeSnapshotJpegs(
        fullMaxDimension: Int,
        fullQuality: Int,
        thumbMaxDimension: Int,
        thumbQuality: Int,
    ): SnapshotJpegPair? {
        val working = copyLastFrameBitmap() ?: return null
        return try {
            val full = compressBitmapToJpeg(working, fullMaxDimension, fullQuality) ?: return null
            val thumb = compressBitmapToJpeg(working, thumbMaxDimension, thumbQuality) ?: full
            SnapshotJpegPair(full, thumb)
        } finally {
            working.recycle()
        }
    }

    data class SnapshotJpegPair(val full: ByteArray, val thumb: ByteArray)

    private fun copyLastFrameBitmap(): Bitmap? = synchronized(bitmapLock) {
        val source = lastFrameBitmap ?: return null
        source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
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

    private fun onPoseResult(result: PoseLandmarkerResult, @Suppress("UNUSED_PARAMETER") input: com.google.mediapipe.framework.image.MPImage) {
        val finishMs = SystemClock.uptimeMillis()
        val frameTs = result.timestampMs()
        lastInferenceTimeMs = finishMs - frameTs
        val isFrontCamera = frameCameraState.remove(frameTs) ?: false
        if (result.landmarks().isEmpty()) {
            inferenceInFlight.set(false)
            listener?.onNoPoseDetected(isFrontCamera)
            return
        }
        val norm = result.landmarks()[0]
        val world = result.worldLandmarks().firstOrNull()
        val rawNormalized = MediaPipeLandmarkMapper.mapNormalizedRaw(norm)
        val rawWorld = world?.let(MediaPipeLandmarkMapper::mapWorldRaw)
        // A-02: release before smooth/assemble — MediaPipe callbacks for one landmarker are serial.
        inferenceInFlight.set(false)
        val smoothed = landmarkSmoother.smooth(norm, frameTs)
        val refinedLandmarks = if (poseRefiner.isAvailable) {
            poseRefiner.refineLandmarks(smoothed)
        } else {
            smoothed
        }
        val smoothedWorld = world?.let { landmarkSmoother.smoothWorld(it, frameTs) }
        listener?.onPoseDetected(
            DetectionResult(
                landmarks = refinedLandmarks,
                worldLandmarks = smoothedWorld,
                rawNormalizedLandmarks = rawNormalized,
                rawWorldLandmarks = rawWorld,
                timestampMs = frameTs,
                inferenceTimeMs = lastInferenceTimeMs,
                isFrontCamera = isFrontCamera,
                analysisImageWidth = lastAnalysisWidth,
                analysisImageHeight = lastAnalysisHeight,
                modelDisplayLabel = modelLabel,
            ),
        )
    }

    override fun shutdown() {
        synchronized(initLock) {
            shutdownLandmarkerOnly()
        }
        synchronized(bitmapLock) {
            lastFrameBitmap?.takeIf { !isRotatedPoolBitmap(it) }?.recycle()
            lastFrameBitmap = null
        }
        rotatedBitmapA?.recycle()
        rotatedBitmapA = null
        rotatedBitmapB?.recycle()
        rotatedBitmapB = null
        writeRotatedA = true
    }

    private fun shutdownLandmarkerOnly() {
        closeLandmarkerQuietly(landmarker)
        landmarker = null
        frameCameraState.clear()
        landmarkSmoother.reset()
        inferenceInFlight.set(false)
        lastSubmittedFrameMs.set(0L)
        lastStallWarningMs.set(0L)
        skippedBusyFrames.set(0)
        heavyUpgradeInFlight = false
    }

    private fun closeLandmarkerQuietly(marker: PoseLandmarker?) {
        if (marker == null) return
        runCatching { marker.close() }
    }

    private fun rememberFrameCamera(frameTime: Long, isFrontCamera: Boolean) {
        while (frameCameraState.size >= MAX_FRAME_CAMERA_STATE) {
            val oldest = frameCameraState.keys.minOrNull() ?: break
            frameCameraState.remove(oldest)
        }
        frameCameraState[frameTime] = isFrontCamera
    }

    private fun scheduleHeavyUpgrade(configuration: PoseDetectorConfiguration) {
        if (heavyUpgradeInFlight) return
        if (modelPort.getSelectedModel() != PoseModelType.HEAVY) return
        heavyUpgradeInFlight = true
        backgroundScope.launch {
            try {
                val cached = PoseLandmarkerHeavyModelStore.ensureCached(context)
                if (!cached) return@launch
                if (modelPort.getSelectedModel() != PoseModelType.HEAVY) return@launch
                val preview = modelPort.resolveForInitialization(PoseModelType.HEAVY)
                if (preview.usesHeavyFallback) return@launch
                Log.i(TAG, "Heavy model cached — swapping pose landmarker")
                synchronized(initLock) {
                    val config = lastConfiguration ?: configuration
                    val baseBuilder = buildBaseOptions(useGpu)
                    val resolved = PoseModelResolver.resolve(
                        context = context,
                        baseBuilder = baseBuilder,
                        configuration = config,
                        modelPort = modelPort,
                    )
                    modelLabel = resolved.displayLabel
                    val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                        .setBaseOptions(baseBuilder.build())
                        .setRunningMode(RunningMode.LIVE_STREAM)
                        .setNumPoses(1)
                        .setMinPoseDetectionConfidence(config.minDetectionConfidence)
                        .setMinTrackingConfidence(config.minTrackingConfidence)
                        .setMinPosePresenceConfidence(config.minPosePresenceConfidence)
                        .setResultListener(this@MediaPipePoseDetector::onPoseResult)
                        .setErrorListener { e ->
                            inferenceInFlight.set(false)
                            frameCameraState.clear()
                            listener?.onError(e.message ?: "Pose error")
                        }
                        .build()
                    val previous = landmarker
                    landmarker = PoseLandmarker.createFromOptions(context, options)
                    closeLandmarkerQuietly(previous)
                }
            } finally {
                heavyUpgradeInFlight = false
            }
        }
    }
}
