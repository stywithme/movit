package com.movit.core.posecapture

import com.movit.core.training.boundary.PoseDetector
import com.movit.core.training.boundary.PoseDetectorConfiguration
import com.movit.core.training.geometry.PoseFrameAssembler
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseFrame
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreMedia.CMSampleBufferRef
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

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

    var lastInferenceTimeMs: Long = 0L
        private set

    fun bridgeStatus(): IosPoseBridgeStatus = bridgeStatus

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    override fun warmUp(configuration: PoseDetectorConfiguration) {
        val bridge = IosPoseLandmarkerBridgeRegistry.current()
        bridgeStatus = when {
            bridge == null -> IosPoseBridgeStatus.NOT_INSTALLED
            !bridge.isAvailable -> IosPoseBridgeStatus.INSTALLED_UNAVAILABLE
            bridge.warmUp(configuration) -> IosPoseBridgeStatus.READY
            else -> IosPoseBridgeStatus.INSTALLED_UNAVAILABLE
        }
        if (bridgeStatus == IosPoseBridgeStatus.READY) {
            bridge?.bindResultHandler(resultHandler)
        } else {
            bridge?.bindResultHandler(null)
        }
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
        bridge.detectAsync(sampleBuffer, isFrontCamera, uptimeMillis())
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

    override fun shutdown() {
        warmedUp = false
        IosPoseLandmarkerBridgeRegistry.current()?.bindResultHandler(null)
        bridgeStatus = when (IosPoseLandmarkerBridgeRegistry.current()) {
            null -> IosPoseBridgeStatus.NOT_INSTALLED
            else -> IosPoseBridgeStatus.INSTALLED_UNAVAILABLE
        }
        listener = null
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
            lastInferenceTimeMs = inferenceTimeMs
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
            lastInferenceTimeMs = 0L
            listener?.onNoPoseDetected(isFrontCamera)
        }

        override fun onError(message: String) {
            lastInferenceTimeMs = 0L
            listener?.onError(message)
        }
    }

    companion object {
        fun uptimeMillis(): Long =
            (NSDate().timeIntervalSince1970 * 1000.0).toLong()
    }
}
