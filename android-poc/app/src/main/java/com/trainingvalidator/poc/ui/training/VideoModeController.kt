package com.trainingvalidator.poc.ui.training

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.TextureView
import com.trainingvalidator.poc.analysis.AngleCalculator
import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.LandmarkSmoother
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.pose.PoseLandmarkerHelper
import com.trainingvalidator.poc.pose.PoseResult
import com.trainingvalidator.poc.storage.AnalysisResultStorage
import com.trainingvalidator.poc.training.TrainingEngine
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.video.VideoAnalysisResult
import com.trainingvalidator.poc.video.VideoManager
import com.trainingvalidator.poc.video.toVideoAnalysisResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * VideoModeController - Handles video mode training logic
 * 
 * Manages:
 * - Video playback (play, pause, seek)
 * - Frame extraction and pose detection
 * - Analysis result storage
 */
class VideoModeController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    
    companion object {
        private const val TAG = "VideoModeController"
    }
    
    /**
     * Callback interface for video mode events
     */
    interface VideoModeListener {
        /** Called when video is ready to play */
        fun onVideoReady(durationMs: Long)
        
        /** Called when playback state changes */
        fun onPlaybackStateChanged(state: VideoManager.PlaybackState)
        
        /** Called when progress updates */
        fun onProgressChanged(currentMs: Long, durationMs: Long)
        
        /** Called when seek is performed */
        fun onSeekPerformed()
        
        /** Called when video ends */
        fun onVideoEnded()
        
        /** Called with processed frame result */
        fun onFrameProcessed(
            angles: JointAngles,
            smoothedLandmarks: List<SmoothedLandmark>,
            imageWidth: Int,
            imageHeight: Int,
            timestampMs: Long
        )
        
        /** Called when no pose detected in frame */
        fun onNoPoseDetected(timestampMs: Long)
        
        /** Called when results are saved */
        fun onResultsSaved(success: Boolean)
        
        /** Called on error */
        fun onError(message: String)
    }
    
    private var videoManager: VideoManager? = null
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    private var landmarkSmoother: LandmarkSmoother? = null
    private var analysisResultStorage: AnalysisResultStorage? = null
    
    private var listener: VideoModeListener? = null
    private var videoUri: Uri? = null
    private var isInitialized = false
    
    // Store last processed frame for capture (for report images)
    private var lastFrameBitmap: Bitmap? = null
    private val lastFrameLock = Any()
    
    // OPTIMIZED: Flag to limit concurrent frame processing
    // Prevents spawning multiple coroutines when processing is slower than frame arrival
    @Volatile
    private var isProcessingFrame = false
    
    /**
     * Set the video mode listener
     */
    fun setListener(listener: VideoModeListener) {
        this.listener = listener
    }
    
    /**
     * Initialize video mode with URI
     * 
     * IMPORTANT: Resets smoother state for consistent results across runs
     */
    fun initialize(
        textureView: TextureView,
        uri: Uri,
        smoother: LandmarkSmoother
    ) {
        this.videoUri = uri
        this.landmarkSmoother = smoother
        
        // CRITICAL: Reset smoother state for deterministic results
        // This ensures the same video produces the same results every time
        smoother.reset()
        Log.d(TAG, "LandmarkSmoother reset for new video analysis")
        
        // Initialize storage
        analysisResultStorage = AnalysisResultStorage(context)
        
        // Initialize pose detection for video
        initializePoseDetection()
        
        // Initialize video manager
        videoManager = VideoManager(
            context = context,
            textureView = textureView,
            onFrameAvailable = { bitmap, timestampMs ->
                processFrame(bitmap, timestampMs)
            },
            onPlaybackStateChanged = { state ->
                handlePlaybackState(state)
            },
            onProgressChanged = { currentMs, durationMs ->
                listener?.onProgressChanged(currentMs, durationMs)
            },
            onSeekPerformed = {
                handleSeek()
            },
            onVideoEnded = {
                listener?.onVideoEnded()
            }
        )
        
        videoManager?.loadVideo(uri)
        isInitialized = true
        
        Log.d(TAG, "Video mode initialized with URI: $uri")
    }
    
    private fun initializePoseDetection() {
        poseLandmarkerHelper = PoseLandmarkerHelper(
            context = context,
            listener = object : PoseLandmarkerHelper.PoseDetectionListener {
                override fun onPoseDetected(result: PoseResult) {
                    // Not used - we use detectPoseFromBitmap directly
                }
                
                override fun onNoPoseDetected() {
                    // Not used
                }
                
                override fun onError(message: String) {
                    listener?.onError(message)
                }
            }
        )
        
        scope.launch(Dispatchers.IO) {
            poseLandmarkerHelper?.initializeForVideo(
                modelType = com.trainingvalidator.poc.pose.ModelType.FULL,
                useGpu = true
            )
            
            withContext(Dispatchers.Main) {
                if (poseLandmarkerHelper?.isVideoModeReady() == true) {
                    Log.d(TAG, "Pose detection VIDEO mode ready")
                }
            }
        }
    }
    
    /**
     * Process a video frame
     * 
     * OPTIMIZED: Limits concurrent processing to prevent coroutine accumulation
     * Frame rate is controlled by VideoManager (DETERMINISTIC_FRAME_INTERVAL_MS).
     */
    private fun processFrame(bitmap: Bitmap, timestampMs: Long) {
        val smoother = landmarkSmoother
        if (smoother == null) {
            bitmap.recycle()
            return
        }
        
        // OPTIMIZED: Drop frame if still processing previous one
        // Prevents unbounded coroutine spawning and memory pressure
        if (isProcessingFrame) {
            bitmap.recycle()
            return
        }
        
        // Store a copy for frame capture (before processing)
        synchronized(lastFrameLock) {
            lastFrameBitmap?.recycle()
            lastFrameBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        }
        
        isProcessingFrame = true
        scope.launch(Dispatchers.Default) {
            try {
                val poseResult = poseLandmarkerHelper?.detectPoseFromBitmap(bitmap, timestampMs)
                
                if (poseResult != null) {
                    val smoothedLandmarks = smoother.smooth(
                        poseResult.landmarks,
                        timestampMs
                    )
                    
                    val worldLandmarks = poseResult.worldLandmarks?.let {
                        smoother.convertWorld(it, timestampMs)
                    }
                    
                    val rawAngles = if (worldLandmarks != null) {
                        AngleCalculator.calculateAllAnglesSmoothed(
                            worldLandmarks,
                            visibilityThreshold = 0.5f,
                            use3D = true
                        )
                    } else {
                        AngleCalculator.calculateAllAnglesSmoothed(
                            smoothedLandmarks,
                            visibilityThreshold = 0.5f
                        )
                    }
                    
                    // Video mode doesn't use front camera mirroring
                    val angles = if (poseResult.isFrontCamera) rawAngles.mirrored() else rawAngles
                    
                    withContext(Dispatchers.Main) {
                        listener?.onFrameProcessed(
                            angles = angles,
                            smoothedLandmarks = smoothedLandmarks,
                            imageWidth = poseResult.imageWidth,
                            imageHeight = poseResult.imageHeight,
                            timestampMs = timestampMs
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        listener?.onNoPoseDetected(timestampMs)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing video frame: ${e.message}")
            } finally {
                isProcessingFrame = false
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }
    
    private fun handlePlaybackState(state: VideoManager.PlaybackState) {
        when (state) {
            VideoManager.PlaybackState.READY -> {
                val duration = videoManager?.getDuration() ?: 0L
                listener?.onVideoReady(duration)
            }
            else -> {}
        }
        
        listener?.onPlaybackStateChanged(state)
    }
    
    private fun handleSeek() {
        Log.d(TAG, "Video seek performed - resetting analysis state")
        
        poseLandmarkerHelper?.resetForVideo()
        landmarkSmoother?.reset()
        
        listener?.onSeekPerformed()
    }
    
    // ==================== Playback Controls ====================
    
    /**
     * Play video
     */
    fun play() {
        videoManager?.play()
    }
    
    /**
     * Pause video
     */
    fun pause() {
        videoManager?.pause()
    }
    
    /**
     * Toggle play/pause
     */
    fun togglePlayback(): Boolean {
        val isPlaying = videoManager?.isPlaying() ?: false
        if (isPlaying) {
            pause()
        } else {
            play()
        }
        return !isPlaying
    }
    
    /**
     * Seek to position
     */
    fun seekTo(positionMs: Long) {
        videoManager?.seekTo(positionMs)
    }
    
    /**
     * Get video duration
     */
    fun getDuration(): Long = videoManager?.getDuration() ?: 0L
    
    /**
     * Check if video is playing
     */
    fun isPlaying(): Boolean = videoManager?.isPlaying() ?: false
    
    /**
     * Get current frame as bitmap (for frame capture in video mode)
     * 
     * Returns a COPY of the last processed frame.
     * Caller is responsible for recycling the returned bitmap.
     */
    fun getCurrentFrameBitmap(): Bitmap? {
        synchronized(lastFrameLock) {
            return lastFrameBitmap?.copy(lastFrameBitmap?.config ?: Bitmap.Config.ARGB_8888, false)
        }
    }
    
    // ==================== Results ====================
    
    /**
     * Save analysis results
     */
    fun saveResults(
        engine: TrainingEngine,
        exerciseConfig: ExerciseConfig
    ): Boolean {
        val uri = videoUri?.toString() ?: return false
        
        val summary = engine.stop()
        
        val result = summary.toVideoAnalysisResult(
            exerciseId = exerciseConfig.fileName,
            exerciseName = exerciseConfig.name,
            videoUri = uri,
            videoDurationMs = getDuration(),
            holdDurationMs = if (engine.isHoldExercise) engine.holdElapsedMs.value else null,
            holdTargetMs = if (engine.isHoldExercise) engine.getTargetDurationMs() else null,
            gracePeriodsUsed = if (engine.isHoldExercise) engine.getGracePeriodCount() else null,
            holdCompleted = if (engine.isHoldExercise) engine.isHoldCompleted() else null
        )
        
        val saved = analysisResultStorage?.save(result) ?: false
        listener?.onResultsSaved(saved)
        
        return saved
    }
    
    // ==================== Lifecycle ====================
    
    /**
     * Release all resources
     */
    fun release() {
        videoManager?.release()
        poseLandmarkerHelper?.closeVideoMode()
        
        synchronized(lastFrameLock) {
            lastFrameBitmap?.recycle()
            lastFrameBitmap = null
        }
        
        videoManager = null
        poseLandmarkerHelper = null
        landmarkSmoother = null
        listener = null
        
        isInitialized = false
        Log.d(TAG, "Video mode controller released")
    }
}
