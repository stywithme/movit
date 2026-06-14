package com.movit.feature.training

import com.movit.core.posecapture.IosPoseLandmarkerBridgeRegistry
import com.movit.core.training.boundary.PersistedFrameSnapshot
import com.movit.core.training.boundary.TrainingFrameSnapshotPort
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.writeToFile

/**
 * Persists peak/error frame JPEGs when Swift MediaPipe bridge exposes [takeSnapshotJpeg].
 * When bridge is absent, [defaultTrainingFrameSnapshotPort] falls back to NoOp — captures
 * metadata-only in [TrainingFrameCaptureCoordinator].
 */
@OptIn(ExperimentalForeignApi::class)
class IosTrainingFrameSnapshotPort(
    private val filesRoot: String,
) : TrainingFrameSnapshotPort {
    override val isAvailable: Boolean
        get() = IosPoseLandmarkerBridgeRegistry.current()?.isAvailable == true

    override suspend fun persistSnapshot(
        sessionId: String,
        captureId: String,
    ): PersistedFrameSnapshot? = withContext(Dispatchers.IO) {
        val bridge = IosPoseLandmarkerBridgeRegistry.current() ?: return@withContext null
        val fullJpeg = bridge.takeSnapshotJpeg(FULL_MAX_DIMENSION, FULL_JPEG_QUALITY) ?: return@withContext null
        val thumbJpeg = bridge.takeSnapshotJpeg(THUMB_MAX_DIMENSION, THUMB_JPEG_QUALITY) ?: fullJpeg
        val dir = "$filesRoot/frame_captures/$sessionId"
        NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        val fullPath = "$dir/$captureId.jpg"
        val thumbPath = "$dir/${captureId}_thumb.jpg"
        if (!writeBytes(fullPath, fullJpeg) || !writeBytes(thumbPath, thumbJpeg)) {
            return@withContext null
        }
        PersistedFrameSnapshot(
            localPath = fullPath,
            thumbnailPath = thumbPath,
        )
    }

    override suspend fun persistReplaySnapshot(
        sessionId: String,
        captureId: String,
    ): PersistedFrameSnapshot? = withContext(Dispatchers.IO) {
        val bridge = IosPoseLandmarkerBridgeRegistry.current() ?: return@withContext null
        val replayJpeg = bridge.takeSnapshotJpeg(REPLAY_MAX_DIMENSION, REPLAY_JPEG_QUALITY)
            ?: return@withContext null
        val dir = "$filesRoot/frame_captures/$sessionId/replay"
        NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        val path = "$dir/$captureId.jpg"
        if (!writeBytes(path, replayJpeg)) return@withContext null
        PersistedFrameSnapshot(localPath = path)
    }

    private fun writeBytes(path: String, bytes: ByteArray): Boolean {
        val data = bytes.toNSData() ?: return false
        return data.writeToFile(path, atomically = true)
    }

    private fun ByteArray.toNSData(): NSData? =
        usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
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
