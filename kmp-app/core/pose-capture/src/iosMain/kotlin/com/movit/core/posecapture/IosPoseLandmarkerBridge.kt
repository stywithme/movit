package com.movit.core.posecapture

import com.movit.core.training.boundary.PoseDetectorConfiguration
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreMedia.CMSampleBufferRef

/**
 * Swift `MovitPoseLandmarkerBridge` implements this protocol (MediaPipe Tasks Vision iOS).
 * Registered via [installIosPoseLandmarkerBridge] from `iosApp` before the camera starts.
 */
@OptIn(ExperimentalForeignApi::class)
interface IosPoseLandmarkerBridge {
    val isAvailable: Boolean

    fun bindResultHandler(handler: IosPoseLandmarkerResultHandler?)

    fun warmUp(configuration: PoseDetectorConfiguration): Boolean

    fun detectAsync(
        sampleBuffer: CMSampleBufferRef?,
        isFrontCamera: Boolean,
        timestampMs: Long,
        analysisImageWidth: Int,
        analysisImageHeight: Int,
    )

    /** Latest upright camera frame as JPEG bytes for report evidence (optional). */
    fun takeSnapshotJpeg(maxDimension: Int, quality: Int): ByteArray?

    /** One frame capture; full + thumb JPEG from the same source frame (A-13/H-07). */
    fun takeSnapshotJpegs(
        fullMaxDimension: Int,
        fullQuality: Int,
        thumbMaxDimension: Int,
        thumbQuality: Int,
    ): FrameSnapshotJpegs?

    fun shutdown()
}

data class FrameSnapshotJpegs(
    val fullJpeg: ByteArray,
    val thumbJpeg: ByteArray,
)

/** Async inference callbacks — Swift calls these from MediaPipe result listeners. */
interface IosPoseLandmarkerResultHandler {
    fun onPoseDetected(
        landmarksFlat: FloatArray,
        worldLandmarksFlat: FloatArray?,
        timestampMs: Long,
        inferenceTimeMs: Long,
        isFrontCamera: Boolean,
        analysisImageWidth: Int,
        analysisImageHeight: Int,
    )

    fun onNoPoseDetected(isFrontCamera: Boolean)

    fun onError(message: String)
}

enum class IosPoseBridgeStatus {
    /** No bridge registered from Swift (default on simulators without iosApp hook). */
    NOT_INSTALLED,

    /** Bridge registered but MediaPipe pod/model not ready — honest no-pose fallback. */
    INSTALLED_UNAVAILABLE,

    /** MediaPipe warm-up succeeded — live landmarks expected. */
    READY,
}

object IosPoseLandmarkerBridgeRegistry {
    private var bridge: IosPoseLandmarkerBridge? = null

    fun current(): IosPoseLandmarkerBridge? = bridge

    internal fun install(bridge: IosPoseLandmarkerBridge?) {
        this.bridge?.bindResultHandler(null)
        this.bridge?.shutdown()
        this.bridge = bridge
    }
}
