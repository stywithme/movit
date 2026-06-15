package com.movit.feature.trainingdebug.android

import android.content.Context
import android.net.Uri
import android.view.TextureView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.movit.core.data.MovitData
import com.movit.core.posecapture.android.MediaPipeSyncPoseDetector
import com.movit.core.posecapture.boundary.trainingdebug.MediaPipeSyncRunningMode
import com.movit.core.posecapture.boundary.trainingdebug.TrainingDebugVideoFrameSelector
import com.movit.feature.trainingdebug.TrainingDebugFrameInput
import com.movit.feature.trainingdebug.TrainingDebugInputMode
import com.movit.feature.trainingdebug.TrainingDebugPoseSource
import com.movit.feature.trainingdebug.TrainingDebugSourceConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AndroidDebugVideoPoseSource(
    private val context: Context,
    private val textureView: TextureView,
) : TrainingDebugPoseSource {
    override val mode: TrainingDebugInputMode = TrainingDebugInputMode.VIDEO

    private val syncDetector: MediaPipeSyncPoseDetector? = resolveSyncDetector()
    private val frameFlow = MutableSharedFlow<TrainingDebugFrameInput?>(extraBufferCapacity = 1)
    override val frames: Flow<TrainingDebugFrameInput?> = frameFlow.asSharedFlow()

    private var player: ExoPlayer? = null
    private var extractorJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var config = TrainingDebugSourceConfig()
    private var lastProcessedVideoTimestampMs = -1L
    private var isProcessingFrame = false
    private var onProgress: ((Long, Long) -> Unit)? = null
    private var onSeekReset: (() -> Unit)? = null
    private var modelLabelCache = "full"

    fun setProgressListener(listener: (currentMs: Long, durationMs: Long) -> Unit) {
        onProgress = listener
    }

    fun setSeekResetListener(listener: () -> Unit) {
        onSeekReset = listener
    }

    override suspend fun start(sourceConfig: TrainingDebugSourceConfig) {
        config = sourceConfig
        val detector = syncDetector ?: return
        val resolved = detector.warmUp(MediaPipeSyncRunningMode.VIDEO, sourceConfig.toPoseCaptureConfig())
        modelLabelCache = resolved.displayLabel
    }

    override suspend fun stop() {
        extractorJob?.cancel()
        player?.release()
        player = null
        syncDetector?.shutdown()
        scope.cancel()
    }

    override suspend fun resetTracking(reason: String) {
        lastProcessedVideoTimestampMs = -1L
        syncDetector?.resetTracking(reason, recreateVideoLandmarker = true)
    }

    fun loadUri(uri: Uri) {
        player?.release()
        player = ExoPlayer.Builder(context).build().apply {
            setVideoTextureView(textureView)
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        onProgress?.invoke(currentPosition, duration.coerceAtLeast(0L))
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        pause()
                    }
                }
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    lastProcessedVideoTimestampMs = -1L
                    onSeekReset?.invoke()
                }
            })
        }
        startExtractionLoop()
    }

    fun playPause() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        lastProcessedVideoTimestampMs = -1L
        onSeekReset?.invoke()
    }

    fun resetPlayback() {
        player?.seekTo(0L)
        lastProcessedVideoTimestampMs = -1L
        onSeekReset?.invoke()
    }

    fun isPlaying(): Boolean = player?.isPlaying == true

    fun durationMs(): Long = player?.duration?.coerceAtLeast(0L) ?: 0L

    fun currentPositionMs(): Long = player?.currentPosition ?: 0L

    fun skippedBusyFrames(): Int = 0

    fun modelLabel(): String = modelLabelCache

    private fun startExtractionLoop() {
        extractorJob?.cancel()
        extractorJob = scope.launch {
            while (isActive) {
                val p = player
                if (p != null && p.isPlaying) {
                    val videoTs = p.currentPosition
                    if (
                        TrainingDebugVideoFrameSelector.shouldProcessFrame(
                            videoTimestampMs = videoTs,
                            lastProcessedVideoTimestampMs = lastProcessedVideoTimestampMs,
                            isProcessingFrame = isProcessingFrame,
                        )
                    ) {
                        extractAndAnalyze(videoTs)
                        lastProcessedVideoTimestampMs = videoTs
                    }
                    onProgress?.invoke(p.currentPosition, p.duration.coerceAtLeast(0L))
                }
                delay(16L)
            }
        }
    }

    private fun extractAndAnalyze(videoTimestampMs: Long) {
        if (!textureView.isAvailable) return
        val bitmap = textureView.bitmap ?: return
        val detector = syncDetector ?: return
        isProcessingFrame = true
        try {
            val frame = detector.detect(bitmap, videoTimestampMs) ?: return
            frameFlow.tryEmit(frame.toDebugFrameInput(config.isFrontCamera))
        } finally {
            isProcessingFrame = false
        }
    }

    fun analyzeCurrentFrame() {
        val ts = player?.currentPosition ?: 0L
        extractAndAnalyze(ts)
    }

    private fun resolveSyncDetector(): MediaPipeSyncPoseDetector? {
        if (!MovitData.isInstalled) return null
        return runCatching { MovitData.koin().get<MediaPipeSyncPoseDetector>() }.getOrNull()
    }
}
