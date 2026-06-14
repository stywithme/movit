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
        val fullJpeg = detector.takeSnapshotJpeg(FULL_MAX_DIMENSION, FULL_JPEG_QUALITY) ?: return@withContext null
        val thumbJpeg = detector.takeSnapshotJpeg(THUMB_MAX_DIMENSION, THUMB_JPEG_QUALITY) ?: fullJpeg
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

    private companion object {
        const val FULL_MAX_DIMENSION = 720
        const val THUMB_MAX_DIMENSION = 200
        const val FULL_JPEG_QUALITY = 85
        const val THUMB_JPEG_QUALITY = 80
    }
}
