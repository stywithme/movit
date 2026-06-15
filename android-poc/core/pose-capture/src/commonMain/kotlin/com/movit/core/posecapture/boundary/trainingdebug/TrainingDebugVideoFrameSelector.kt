package com.movit.core.posecapture.boundary.trainingdebug

/**
 * Deterministic video frame selection aligned with Legacy [VideoManager] (~30 fps video-time).
 *
 * Processes frames at fixed video timeline intervals so the same video yields comparable
 * timestamps across runs. Skips when the previous frame is still being analyzed.
 */
object TrainingDebugVideoFrameSelector {
    /** ~30 fps on the video timeline (Legacy `DETERMINISTIC_FRAME_INTERVAL_MS`). */
    const val DETERMINISTIC_FRAME_INTERVAL_MS: Long = 33L

    fun shouldProcessFrame(
        videoTimestampMs: Long,
        lastProcessedVideoTimestampMs: Long,
        isProcessingFrame: Boolean,
    ): Boolean {
        if (isProcessingFrame) return false
        if (lastProcessedVideoTimestampMs < 0L) return true
        return (videoTimestampMs - lastProcessedVideoTimestampMs) >= DETERMINISTIC_FRAME_INTERVAL_MS
    }
}
