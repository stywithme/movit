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
        
        // Frame extraction interval (~60 fps target)
        // Lower = more frames, but depends on processing speed
        private const val FRAME_INTERVAL_MS = 16L
        
        // Minimum time between processed frames to prevent queue buildup
        private const val MIN_PROCESS_INTERVAL_MS = 20L
        
        // DETERMINISTIC MODE: Process frames at fixed video timestamps
        // This ensures consistent results across multiple runs of the same video
        private const val DETERMINISTIC_FRAME_INTERVAL_MS = 33L  // ~30fps from video timeline
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
    
    // Performance optimization: track last processed time to skip frames if processing is slow
    @Volatile
    private var lastProcessedTimeMs: Long = 0L
    
    @Volatile
    private var isProcessingFrame: Boolean = false
    
    // DETERMINISTIC MODE: Track last processed VIDEO timestamp (not system time)
    // This ensures we process frames at consistent video timeline positions
    @Volatile
    private var lastProcessedVideoTimestampMs: Long = -1L
    
    // Reusable bitmap to reduce allocations (optional optimization)
    private var reusableBitmap: Bitmap? = null
    
    // OPTIMIZED: Reusable Matrix for rotation to avoid allocation per frame
    private val reusableRotationMatrix = Matrix()
    
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
        lastProcessedVideoTimestampMs = -1L  // Reset for deterministic frame selection after seek
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
     * OPTIMIZED VERSION:
     * - Uses async processing to avoid blocking the extraction loop
     * - Implements frame skipping when processing is slower than extraction
     * - Reduces allocations by reusing bitmaps where possible
     * 
     * IMPORTANT: ExoPlayer must be accessed from the Main thread only.
     */
    private fun startFrameExtraction() {
        stopFrameExtraction()
        lastProcessedTimeMs = 0L
        lastProcessedVideoTimestampMs = -1L  // Reset for deterministic frame selection
        isProcessingFrame = false
        
        // Use Main dispatcher for player access, but process frames async
        frameExtractorJob = extractorScope.launch(Dispatchers.Main) {
            Log.d(TAG, "Frame extraction started (optimized)")
            
            while (isActive) {
                try {
                    // Check if player is still playing (on main thread)
                    val isPlaying = player?.isPlaying ?: false
                    if (!isPlaying) break
                    
                    val timestampMs = player?.currentPosition ?: 0L
                    val duration = player?.duration ?: 0L
                    
                    // Update progress on main thread
                    onProgressChanged(timestampMs, duration)
                    
                    // DETERMINISTIC FRAME SELECTION:
                    // Process frames at fixed video timeline intervals, not system time
                    // This ensures the SAME frames are processed every time for the same video
                    val shouldProcessFrame = if (lastProcessedVideoTimestampMs < 0) {
                        // First frame - always process
                        true
                    } else {
                        // Process if enough video time has passed since last processed frame
                        (timestampMs - lastProcessedVideoTimestampMs) >= DETERMINISTIC_FRAME_INTERVAL_MS
                    }
                    
                    // Also check we're not still processing previous frame
                    if (!isProcessingFrame && shouldProcessFrame) {
                        lastProcessedVideoTimestampMs = timestampMs
                        // Extract frame on main thread (required for TextureView)
                        extractAndProcessFrameAsync(timestampMs)
                    }
                    
                    // Wait for next frame interval
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
     * Extract frame from TextureView and send for ASYNC processing
     * 
     * This method:
     * 1. Extracts bitmap on Main thread (required by TextureView)
     * 2. Copies bitmap data to avoid holding TextureView's buffer
     * 3. Sends to processing on a background thread (non-blocking)
     */
    private fun extractAndProcessFrameAsync(timestampMs: Long) {
        try {
            // Get bitmap from TextureView (must be on main thread)
            val sourceBitmap = textureView.getBitmap() ?: return
            
            // Mark as processing
            isProcessingFrame = true
            
            // Launch async processing on Default dispatcher (background thread)
            extractorScope.launch(Dispatchers.Default) {
                try {
                    // Copy bitmap to avoid blocking TextureView's buffer
                    // This is important for performance
                    var processedBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    
                    // Apply rotation if needed
                    if (videoRotation != 0) {
                        val rotated = rotateBitmap(processedBitmap, videoRotation)
                        if (rotated !== processedBitmap) {
                            processedBitmap.recycle()
                            processedBitmap = rotated
                        }
                    }
                    
                    // Send frame for analysis (callback handles its own threading)
                    onFrameAvailable(processedBitmap, timestampMs)
                    
                    // Update last processed time
                    lastProcessedTimeMs = System.currentTimeMillis()
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing frame: ${e.message}")
                } finally {
                    isProcessingFrame = false
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in extractAndProcessFrameAsync: ${e.message}")
            isProcessingFrame = false
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
     * OPTIMIZED: Reuses Matrix object to avoid allocation per frame
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        
        reusableRotationMatrix.reset()
        reusableRotationMatrix.postRotate(degrees.toFloat())
        
        return Bitmap.createBitmap(
            bitmap, 0, 0,
            bitmap.width, bitmap.height,
            reusableRotationMatrix, true
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
