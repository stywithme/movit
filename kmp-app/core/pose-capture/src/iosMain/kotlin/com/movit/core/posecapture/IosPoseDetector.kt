package com.movit.core.posecapture

import com.movit.core.training.boundary.PoseDetector
import com.movit.core.training.boundary.PoseDetectorConfiguration
import com.movit.core.training.model.Landmark
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreMedia.CMSampleBufferRef
import platform.QuartzCore.CACurrentMediaTime

/**
 * iOS pose ML boundary (D3).
 *
 * Delegates frame inference to Swift [IosPoseLandmarkerBridge] when registered and available.
 * Without a ready bridge, [detectAsync] reports no-pose honestly (preview/permissions still work).
 */
@OptIn(ExperimentalForeignApi::class)
class IosPoseDetector : PoseDetector {
    data class DetectionResult(
        val landmarks: List<Landmark>,
        val worldLandmarks: List<Landmark>?,
        val timestampMs: Long,
        val inferenceTimeMs: Long,
        val isFrontCamera: Boolean,
        val analysisImageWidth: Int = 0,
        val analysisImageHeight: Int = 0,
    )

    interface Listener {
        fun onPoseDetected(result: DetectionResult)
        fun onNoPoseDetected(isFrontCamera: Boolean)
        fun onError(message: String)
    }

    private var listener: Listener? = null
    private var warmedUp = false
    private var bridgeStatus: IosPoseBridgeStatus = IosPoseBridgeStatus.NOT_INSTALLED
    private var analysisImageWidth: Int = 0
    private var analysisImageHeight: Int = 0
    private val inferenceInFlight = atomic(false)
    private val skippedBusyFrames = atomic(0)

    var lastInferenceTimeMs: Long = 0L
        private set

    /**
     * `true` after [warmUp] when the Swift MediaPipe bridge loaded `pose_landmarker_full.task`.
     * Mirrors Android [com.movit.core.posecapture.android.MediaPipePoseDetector] readiness.
     */
    val isAvailable: Boolean
        get() = bridgeStatus == IosPoseBridgeStatus.READY

    fun bridgeStatus(): IosPoseBridgeStatus = bridgeStatus

    fun consumeBusySkipCount(): Int = skippedBusyFrames.getAndSet(0)

    fun setAnalysisImageSize(width: Int, height: Int) {
        analysisImageWidth = width.coerceAtLeast(0)
        analysisImageHeight = height.coerceAtLeast(0)
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    override fun warmUp(configuration: PoseDetectorConfiguration) {
        val bridge = IosPoseLandmarkerBridgeRegistry.current()
        bridgeStatus = when {
            bridge == null -> IosPoseBridgeStatus.NOT_INSTALLED
            !bridge.isAvailable -> IosPoseBridgeStatus.INSTALLED_UNAVAILABLE
            bridge.warmUp(configuration) -> IosPoseBridgeStatus.READY
            // B-08: GPU warmUp fail → retry CPU before INSTALLED_UNAVAILABLE.
            configuration.useGpu && bridge.warmUp(configuration.copy(useGpu = false)) ->
                IosPoseBridgeStatus.READY
            else -> IosPoseBridgeStatus.INSTALLED_UNAVAILABLE
        }
        if (bridgeStatus == IosPoseBridgeStatus.READY) {
            bridge?.bindResultHandler(resultHandler)
        } else {
            bridge?.bindResultHandler(null)
        }
        inferenceInFlight.value = false
        skippedBusyFrames.value = 0
        warmedUp = true
    }

    override fun resetTrackingState() = Unit

    /**
     * Called from [IosCameraFrameSource] for each [CMSampleBufferRef].
     */
    fun detectAsync(
        sampleBuffer: CMSampleBufferRef?,
        isFrontCamera: Boolean,
    ) {
        if (!warmedUp) return
        val bridge = IosPoseLandmarkerBridgeRegistry.current()
        if (bridge == null || bridgeStatus != IosPoseBridgeStatus.READY) {
            lastInferenceTimeMs = 0L
            listener?.onNoPoseDetected(isFrontCamera)
            return
        }
        if (!tryAcquireInferenceSlot()) return
        bridge.detectAsync(
            sampleBuffer = sampleBuffer,
            isFrontCamera = isFrontCamera,
            timestampMs = uptimeMillis(),
            analysisImageWidth = analysisImageWidth,
            analysisImageHeight = analysisImageHeight,
        )
    }

    override fun shutdown() {
        warmedUp = false
        IosPoseLandmarkerBridgeRegistry.current()?.bindResultHandler(null)
        bridgeStatus = when (IosPoseLandmarkerBridgeRegistry.current()) {
            null -> IosPoseBridgeStatus.NOT_INSTALLED
            else -> IosPoseBridgeStatus.INSTALLED_UNAVAILABLE
        }
        inferenceInFlight.value = false
        skippedBusyFrames.value = 0
        listener = null
    }

    private fun tryAcquireInferenceSlot(): Boolean {
        if (inferenceInFlight.compareAndSet(expect = false, update = true)) return true
        skippedBusyFrames.incrementAndGet()
        return false
    }

    private fun releaseInferenceSlot() {
        inferenceInFlight.value = false
    }

    private val resultHandler = object : IosPoseLandmarkerResultHandler {
        override fun onPoseDetected(
            landmarksFlat: FloatArray,
            worldLandmarksFlat: FloatArray?,
            timestampMs: Long,
            inferenceTimeMs: Long,
            isFrontCamera: Boolean,
            analysisImageWidth: Int,
            analysisImageHeight: Int,
        ) {
            // A-02/B-03: early release — flat arrays already copied into Kotlin.
            releaseInferenceSlot()
            lastInferenceTimeMs = inferenceTimeMs
            // ponytail: B-02/M6 dump hook — no-op until landmark dump lands; do not touch mirroring.
            IosLandmarkDumpScaffold.onPoseLandmarks(landmarksFlat, isFrontCamera)
            runCatching {
                DetectionResult(
                    landmarks = PoseLandmarkFlatCodec.decode(landmarksFlat),
                    worldLandmarks = PoseLandmarkFlatCodec.decodeOptional(worldLandmarksFlat),
                    timestampMs = timestampMs,
                    inferenceTimeMs = inferenceTimeMs,
                    isFrontCamera = isFrontCamera,
                    analysisImageWidth = analysisImageWidth,
                    analysisImageHeight = analysisImageHeight,
                )
            }.onSuccess { result ->
                listener?.onPoseDetected(result)
            }.onFailure { error ->
                listener?.onError(error.message ?: "Invalid pose landmarks")
            }
        }

        override fun onNoPoseDetected(isFrontCamera: Boolean) {
            releaseInferenceSlot()
            lastInferenceTimeMs = 0L
            listener?.onNoPoseDetected(isFrontCamera)
        }

        override fun onError(message: String) {
            releaseInferenceSlot()
            lastInferenceTimeMs = 0L
            listener?.onError(message)
        }
    }

    companion object {
        /** Monotonic ms (B-04) — mirrors Android [android.os.SystemClock.uptimeMillis]. */
        fun uptimeMillis(): Long =
            (CACurrentMediaTime() * 1000.0).toLong()
    }
}

/**
 * B-02 / M6 scaffolding: future side-by-side L/R landmark dump.
 * Blocked until M6 device capture — do **not** remove `setVideoMirrored(true)` before that.
 */
internal object IosLandmarkDumpScaffold {
    fun onPoseLandmarks(
        @Suppress("UNUSED_PARAMETER") landmarksFlat: FloatArray,
        @Suppress("UNUSED_PARAMETER") isFrontCamera: Boolean,
    ) = Unit
}
