package com.movit.core.training.boundary

/**
 * Platform hook to persist a single training-frame JPEG for post-session report evidence.
 * Bitmap capture stays Android-only (pose-capture); common code only stores returned paths.
 */
data class PersistedFrameSnapshot(
    val localPath: String,
    val thumbnailPath: String? = null,
)

interface TrainingFrameSnapshotPort {
    val isAvailable: Boolean

    suspend fun persistSnapshot(
        sessionId: String,
        captureId: String,
    ): PersistedFrameSnapshot?

    /** Smaller JPEG for per-rep replay burst; optional on platforms without live preview capture. */
    suspend fun persistReplaySnapshot(
        sessionId: String,
        captureId: String,
    ): PersistedFrameSnapshot? = null
}

object NoOpTrainingFrameSnapshotPort : TrainingFrameSnapshotPort {
    override val isAvailable: Boolean = false

    override suspend fun persistSnapshot(
        sessionId: String,
        captureId: String,
    ): PersistedFrameSnapshot? = null
}
