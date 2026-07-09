package com.movit.core.training.report

import kotlinx.serialization.Serializable

@Serializable
data class MovitFrameCaptureMetadata(
    val angles: Map<String, Double> = emptyMap(),
    val hasError: Boolean = false,
    val errorDetails: String? = null,
)

@Serializable
data class MovitReplayFrameRef(
    val frameUri: String,
    val offsetMs: Long,
)

/**
 * Sampled JPEG sequence for one rep — mirrors Legacy [RepReplayClip].
 */
@Serializable
data class MovitRepReplayClip(
    val repNumber: Int,
    val setNumber: Int = 1,
    val frames: List<MovitReplayFrameRef>,
) {
    val posterFrameUri: String?
        get() = frames.getOrNull(frames.size / 2)?.frameUri

    val durationMs: Long
        get() = frames.lastOrNull()?.offsetMs ?: 0L
}
