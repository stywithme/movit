package com.movit.feature.trainingdebug.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.movit.core.data.MovitData
import com.movit.core.posecapture.android.MediaPipeSyncPoseDetector
import com.movit.core.posecapture.boundary.trainingdebug.MediaPipeSyncRunningMode
import com.movit.feature.trainingdebug.TrainingDebugFrameInput
import com.movit.feature.trainingdebug.TrainingDebugInputMode
import com.movit.feature.trainingdebug.TrainingDebugPoseSource
import com.movit.feature.trainingdebug.TrainingDebugSourceConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AndroidDebugImagePoseSource(
    private val context: Context,
) : TrainingDebugPoseSource {
    override val mode: TrainingDebugInputMode = TrainingDebugInputMode.IMAGE

    private val syncDetector: MediaPipeSyncPoseDetector? = resolveSyncDetector()
    private val frameFlow = MutableSharedFlow<TrainingDebugFrameInput?>(extraBufferCapacity = 1)
    override val frames: Flow<TrainingDebugFrameInput?> = frameFlow.asSharedFlow()

    private var loadedBitmap: Bitmap? = null
    private var config = TrainingDebugSourceConfig()
    private var modelLabelCache = "full"

    override suspend fun start(sourceConfig: TrainingDebugSourceConfig) {
        config = sourceConfig
        val detector = syncDetector ?: return
        val resolved = detector.warmUp(MediaPipeSyncRunningMode.IMAGE, sourceConfig.toPoseCaptureConfig())
        modelLabelCache = resolved.displayLabel
        reanalyze()
    }

    override suspend fun stop() {
        loadedBitmap?.recycle()
        loadedBitmap = null
        syncDetector?.shutdown()
    }

    override suspend fun resetTracking(reason: String) {
        syncDetector?.resetTracking(reason)
        syncDetector?.warmUp(MediaPipeSyncRunningMode.IMAGE, config.toPoseCaptureConfig())
        reanalyze()
    }

    fun loadUri(uri: Uri) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val raw = BitmapFactory.decodeStream(input) ?: return
            val rotation = readExifRotation(uri)
            loadedBitmap?.recycle()
            loadedBitmap = if (rotation == 0) {
                raw
            } else {
                val rotated = AndroidDebugBitmapUtils.applyExifRotation(raw, rotation)
                raw.recycle()
                rotated
            }
        }
        reanalyze()
    }

    fun reanalyze() {
        val bitmap = loadedBitmap ?: return
        val detector = syncDetector ?: return
        val frame = detector.detect(bitmap, timestampMs = 0L) ?: return
        frameFlow.tryEmit(frame.toDebugFrameInput(config.isFrontCamera))
    }

    fun hasImage(): Boolean = loadedBitmap != null

    fun imageSize(): Pair<Int, Int>? {
        val bmp = loadedBitmap ?: return null
        return bmp.width to bmp.height
    }

    fun modelLabel(): String = modelLabelCache

    private fun readExifRotation(uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun resolveSyncDetector(): MediaPipeSyncPoseDetector? {
        if (!MovitData.isInstalled) return null
        return runCatching { MovitData.koin().get<MediaPipeSyncPoseDetector>() }.getOrNull()
    }
}
