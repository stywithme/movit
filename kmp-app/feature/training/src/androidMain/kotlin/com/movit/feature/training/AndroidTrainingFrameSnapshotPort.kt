package com.movit.feature.training

import com.movit.core.posecapture.android.MediaPipePoseDetector
import com.movit.core.training.boundary.PersistedFrameSnapshot
import com.movit.core.training.boundary.TrainingFrameSnapshotPort
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidTrainingFrameSnapshotPort(
    private val detector: MediaPipePoseDetector,
    private val filesRoot: File,
) : TrainingFrameSnapshotPort {
    override val isAvailable: Boolean = true

    override suspend fun persistSnapshot(
        sessionId: String,
        captureId: String,
    ): PersistedFrameSnapshot? = withContext(Dispatchers.IO) {
        val jpegs = detector.takeSnapshotJpegs(
            FULL_MAX_DIMENSION,
            FULL_JPEG_QUALITY,
            THUMB_MAX_DIMENSION,
            THUMB_JPEG_QUALITY,
        ) ?: return@withContext null
        val fullJpeg = jpegs.full
        val thumbJpeg = jpegs.thumb
        val dir = File(filesRoot, "frame_captures/$sessionId").apply { mkdirs() }
        val fullFile = File(dir, "$captureId.jpg")
        val thumbFile = File(dir, "${captureId}_thumb.jpg")
        fullFile.writeBytes(fullJpeg)
        thumbFile.writeBytes(thumbJpeg)
        PersistedFrameSnapshot(
            localPath = fullFile.absolutePath,
            thumbnailPath = thumbFile.absolutePath,
        )
    }

    override suspend fun persistReplaySnapshot(
        sessionId: String,
        captureId: String,
    ): PersistedFrameSnapshot? = withContext(Dispatchers.IO) {
        val jpeg = detector.takeSnapshotJpeg(REPLAY_MAX_DIMENSION, REPLAY_JPEG_QUALITY) ?: return@withContext null
        val dir = File(filesRoot, "frame_captures/$sessionId/replay").apply { mkdirs() }
        val file = File(dir, "$captureId.jpg")
        file.writeBytes(jpeg)
        PersistedFrameSnapshot(localPath = file.absolutePath)
    }

    private companion object {
        const val FULL_MAX_DIMENSION = 720
        const val THUMB_MAX_DIMENSION = 200
        const val FULL_JPEG_QUALITY = 85
        const val THUMB_JPEG_QUALITY = 80
        const val REPLAY_MAX_DIMENSION = 540
        const val REPLAY_JPEG_QUALITY = 82
    }
}
