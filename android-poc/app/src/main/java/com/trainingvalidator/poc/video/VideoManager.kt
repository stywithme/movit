package com.trainingvalidator.poc.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*

/**
 * VideoManager - Manages video playback and frame extraction for analysis
 * 
 * Uses ExoPlayer for playback + TextureView.getBitmap() for frame extraction.
 * 
 * Responsibilities:
 * - Load video from URI
 * - Control playback (play/pause/seek)
 * - Extract frames via TextureView.getBitmap()
 * - Send frames for analysis
 * 
 * @param context Application context
 * @param textureView TextureView for video rendering
 * @param onFrameAvailable Callback for each extracted frame (bitmap, timestampMs)
 * @param onPlaybackStateChanged Callback for playback state changes
 * @param onProgressChanged Callback for progress updates (currentMs, durationMs)
 * @param onSeekPerformed Callback when seek is performed (to reset analysis state)
 * @param onVideoEnded Callback when video playback ends
 */
class VideoManager(
    private val context: Context,
    private val textureView: TextureView,
    private val onFrameAvailable: (Bitmap, Long) -> Unit,
    private val onPlaybackStateChanged: (PlaybackState) -> Unit,
    private val onProgressChanged: (currentMs: Long, durationMs: Long) -> Unit,
    private val onSeekPerformed: () -> Unit,
    private val onVideoEnded: () -> Unit
) {
    companion object {
        private const val TAG = "VideoManager"
        
        // Frame extraction interval (~30 fps)
        private const val FRAME_INTERVAL_MS = 33L
    }
    
    /**
     * Playback state enum
     */
    enum class PlaybackState {
        IDLE,
        LOADING,
        READY,
        PLAYING,
        PAUSED,
        ENDED,
        ERROR
    }
    
    private var player: ExoPlayer? = null
    private var frameExtractorJob: Job? = null
    private var currentState: PlaybackState = PlaybackState.IDLE
    
    // Video metadata
    private var videoDurationMs: Long = 0L
    private var videoRotation: Int = 0
    
    // Coroutine scope for frame extraction
    private val extractorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Load video from URI
     */
    @OptIn(UnstableApi::class)
    fun loadVideo(uri: Uri) {
        Log.d(TAG, "Loading video: $uri")
        
        updateState(PlaybackState.LOADING)
        
        // Get video rotation
        videoRotation = getVideoRotation(uri)
        Log.d(TAG, "Video rotation: $videoRotation degrees")
        
        // Create and configure ExoPlayer
        player = ExoPlayer.Builder(context).build().apply {
            // Attach to TextureView for rendering
            setVideoTextureView(textureView)
            
            // Set media item
            setMediaItem(MediaItem.fromUri(uri))
            
            // Add listener for state changes
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    handleExoPlayerState(state)
                }
                
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        updateState(PlaybackState.PLAYING)
                        startFrameExtraction()
                    } else if (currentState == PlaybackState.PLAYING) {
                        updateState(PlaybackState.PAUSED)
                        stopFrameExtraction()
                    }
                }
            })
            
            // Prepare the player
            prepare()
        }
    }
    
    /**
     * Handle ExoPlayer state changes
     */
    private fun handleExoPlayerState(state: Int) {
        when (state) {
            Player.STATE_IDLE -> {
                updateState(PlaybackState.IDLE)
            }
            Player.STATE_BUFFERING -> {
                updateState(PlaybackState.LOADING)
            }
            Player.STATE_READY -> {
                videoDurationMs = player?.duration ?: 0L
                Log.d(TAG, "Video ready. Duration: ${videoDurationMs}ms")
                
                if (currentState != PlaybackState.PLAYING) {
                    updateState(PlaybackState.READY)
                }
            }
            Player.STATE_ENDED -> {
                updateState(PlaybackState.ENDED)
                stopFrameExtraction()
                onVideoEnded()
            }
        }
    }
    
    /**
     * Start video playback
     */
    fun play() {
        player?.let {
            if (it.playbackState == Player.STATE_ENDED) {
                // Restart from beginning
                it.seekTo(0)
                onSeekPerformed()
            }
            it.play()
            Log.d(TAG, "Video playback started")
        }
    }
    
    /**
     * Pause video playback
     */
    fun pause() {
        player?.pause()
        Log.d(TAG, "Video playback paused")
    }
    
    /**
     * Toggle play/pause
     */
    fun togglePlayPause() {
        if (player?.isPlaying == true) {
            pause()
        } else {
            play()
        }
    }
    
    /**
     * Seek to position
     * IMPORTANT: This triggers onSeekPerformed to reset analysis state
     */
    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        Log.d(TAG, "Seeking to ${positionMs}ms")
        onSeekPerformed()
    }
    
    /**
     * Rewind by specified milliseconds
     */
    fun rewind(ms: Long = 10_000L) {
        val currentPos = player?.currentPosition ?: 0L
        val newPos = maxOf(0L, currentPos - ms)
        seekTo(newPos)
    }
    
    /**
     * Fast forward by specified milliseconds
     */
    fun fastForward(ms: Long = 10_000L) {
        val currentPos = player?.currentPosition ?: 0L
        val duration = player?.duration ?: 0L
        val newPos = minOf(duration, currentPos + ms)
        seekTo(newPos)
    }
    
    /**
     * Start frame extraction loop
     * 
     * IMPORTANT: ExoPlayer must be accessed from the Main thread only.
     * We use Dispatchers.Main for the entire loop to avoid threading issues.
     */
    private fun startFrameExtraction() {
        stopFrameExtraction()
        
        // Use Main dispatcher to ensure all player access is on main thread
        frameExtractorJob = extractorScope.launch(Dispatchers.Main) {
            Log.d(TAG, "Frame extraction started")
            
            while (isActive) {
                try {
                    // Check if player is still playing (on main thread)
                    val isPlaying = player?.isPlaying ?: false
                    if (!isPlaying) break
                    
                    val timestampMs = player?.currentPosition ?: 0L
                    val duration = player?.duration ?: 0L
                    
                    // Extract frame from TextureView (already on main thread)
                    extractAndProcessFrame(timestampMs)
                    onProgressChanged(timestampMs, duration)
                    
                    // Wait for next frame
                    delay(FRAME_INTERVAL_MS)
                    
                } catch (e: CancellationException) {
                    // Normal cancellation, just exit
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error extracting frame: ${e.message}")
                }
            }
            
            Log.d(TAG, "Frame extraction stopped")
        }
    }
    
    /**
     * Extract frame from TextureView and send for processing
     */
    private fun extractAndProcessFrame(timestampMs: Long) {
        try {
            // Get bitmap from TextureView
            var bitmap = textureView.getBitmap() ?: return
            
            // Apply rotation if needed
            if (videoRotation != 0) {
                bitmap = rotateBitmap(bitmap, videoRotation)
            }
            
            // Send frame for analysis
            onFrameAvailable(bitmap, timestampMs)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in extractAndProcessFrame: ${e.message}")
        }
    }
    
    /**
     * Stop frame extraction
     */
    private fun stopFrameExtraction() {
        frameExtractorJob?.cancel()
        frameExtractorJob = null
    }
    
    /**
     * Update playback state and notify listener
     */
    private fun updateState(newState: PlaybackState) {
        if (currentState != newState) {
            currentState = newState
            onPlaybackStateChanged(newState)
            Log.d(TAG, "State changed to: $newState")
        }
    }
    
    /**
     * Get video rotation from metadata
     */
    private fun getVideoRotation(uri: Uri): Int {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val rotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )?.toIntOrNull() ?: 0
            retriever.release()
            rotation
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get video rotation: ${e.message}")
            0
        }
    }
    
    /**
     * Rotate bitmap by specified degrees
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        
        val matrix = Matrix().apply {
            postRotate(degrees.toFloat())
        }
        
        return Bitmap.createBitmap(
            bitmap, 0, 0,
            bitmap.width, bitmap.height,
            matrix, true
        )
    }
    
    // ==================== Getters ====================
    
    /**
     * Get video duration in milliseconds
     */
    fun getDuration(): Long = player?.duration ?: 0L
    
    /**
     * Get current playback position in milliseconds
     */
    fun getCurrentPosition(): Long = player?.currentPosition ?: 0L
    
    /**
     * Check if video is currently playing
     */
    fun isPlaying(): Boolean = player?.isPlaying ?: false
    
    /**
     * Get current playback state
     */
    fun getState(): PlaybackState = currentState
    
    /**
     * Get playback progress (0.0 - 1.0)
     */
    fun getProgress(): Float {
        val duration = getDuration()
        if (duration <= 0) return 0f
        return getCurrentPosition().toFloat() / duration.toFloat()
    }
    
    // ==================== Lifecycle ====================
    
    /**
     * Release all resources
     */
    fun release() {
        Log.d(TAG, "Releasing VideoManager resources")
        
        stopFrameExtraction()
        extractorScope.cancel()
        
        player?.release()
        player = null
        
        updateState(PlaybackState.IDLE)
    }
}
