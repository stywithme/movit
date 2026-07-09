package com.movit.core.training.report

import kotlinx.serialization.Serializable

/**
 * Local peak snapshot reference (D9 v1) — no matting/segmentation.
 * Paths are platform-local file URIs; thumbnails optional.
 */
@Serializable
enum class MovitPeakCaptureType {
    PEAK_FRAME,
    BEST_REP,
    DANGER_FRAME,
    ERROR_FRAME,
    HOLD_SAMPLE,
}

@Serializable
data class MovitPeakFrameCapture(
    val id: String,
    val repNumber: Int,
    val setNumber: Int = 1,
    val phaseCode: Byte,
    val capturedAtMs: Long,
    val captureType: MovitPeakCaptureType,
    val localPath: String,
    val thumbnailPath: String? = null,
    val errorType: String? = null,
    val metadata: MovitFrameCaptureMetadata = MovitFrameCaptureMetadata(),
)
