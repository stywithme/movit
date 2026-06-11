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
 * **v1 (07.6):** compiles and wires the camera pipeline; frame inference is a no-op stub until
 * MediaPipe Tasks Vision iOS is linked (see plan §12 / 07.6 integration decision).
 *
 * **Target path:** `MovitPoseLandmarkerBridge` in `iosApp` (Swift) wrapping
 * `MediaPipeTasksVision` `PoseLandmarker` with the same `.task` bundles as Android, registered
 * via Koin factory at `MovitData.install(additionalModules = …)` — avoids fragile Gradle cinterop.
 */
@OptIn(ExperimentalForeignApi::class)
class IosPoseDetector : PoseDetector {
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

    private var listener: Listener? = null
    private var warmedUp = false

    var lastInferenceTimeMs: Long = 0L
        private set

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    override fun warmUp(configuration: PoseDetectorConfiguration) {
        warmedUp = true
        // MediaPipe PoseLandmarker (LIVE_STREAM) initializes in Swift bridge — not in this stub.
    }

    /**
     * Called from [IosCameraFrameSource] for each [CMSampleBufferRef].
     * Stub: always reports no pose until MediaPipe bridge is wired.
     */
    fun detectAsync(
        @Suppress("UNUSED_PARAMETER") sampleBuffer: CMSampleBufferRef?,
        isFrontCamera: Boolean,
    ) {
        if (!warmedUp) return
        lastInferenceTimeMs = 0L
        listener?.onNoPoseDetected()
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
        listener = null
    }

    companion object {
        fun uptimeMillis(): Long =
            (NSDate().timeIntervalSince1970 * 1000.0).toLong()
    }
}
