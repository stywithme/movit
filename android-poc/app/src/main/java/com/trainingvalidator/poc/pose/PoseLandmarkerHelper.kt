package com.trainingvalidator.poc.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.ConcurrentHashMap

/**
 * ModelType - Enum for supported MediaPipe models
 */
enum class ModelType(val fileName: String, val displayName: String) {
    FULL("pose_landmarker_full.task", "Full (Balanced)"),
    HEAVY("pose_landmarker_heavy.task", "Heavy (Accuracy)")
}

/**
 * RunMode - Enum for PoseLandmarker running modes
 */
enum class RunMode {
    LIVE_STREAM,  // For camera (async with callback)
    VIDEO,        // For video (sync with return value)
    IMAGE         // For single image (sync with return value)
}

/**
 * PoseLandmarkerHelper - Wrapper for MediaPipe Pose Landmarker
 * 
 * Updated based on Google's official MediaPipe sample:
 * https://github.com/google-ai-edge/mediapipe-samples
 * 
 * Key improvements:
 * - Uses SystemClock.uptimeMillis() for accurate timestamps
 * - Uses copyPixelsFromBuffer() for faster image conversion
 * - Returns image dimensions for proper scaling in overlay
 */
class PoseLandmarkerHelper(
    private val context: Context,
    private val listener: PoseDetectionListener
) {
    companion object {
        private const val TAG = "PoseLandmarkerHelper"
        
        // Detection thresholds
        private const val MIN_POSE_DETECTION_CONFIDENCE = 0.5f
        private const val MIN_POSE_TRACKING_CONFIDENCE = 0.5f
        private const val MIN_POSE_PRESENCE_CONFIDENCE = 0.5f
        
        // Number of poses to detect (1 for single user)
        private const val NUM_POSES = 1
    }

    private var poseLandmarker: PoseLandmarker? = null
    private var isInitialized = false
    private var currentModelType = ModelType.FULL
    
    // VIDEO mode landmarker (separate instance)
    private var videoModeLandmarker: PoseLandmarker? = null
    private var isVideoModeInitialized = false
    private var currentRunMode = RunMode.LIVE_STREAM
    
    // Store last image dimensions for result callback
    private var lastImageWidth = 0
    private var lastImageHeight = 0
    
    // Per-frame camera state: maps frame timestamp → isFrontCamera
    // This avoids a race condition where detectAsync() is called with a new frame
    // (updating a shared variable) before onPoseResult() returns for the previous frame.
    // Using ConcurrentHashMap ensures each frame's result is paired with the correct camera state.
    private val frameCameraState = ConcurrentHashMap<Long, Boolean>()
    
    // Reusable Matrix to avoid allocation on every frame
    private val reusableMatrix = Matrix()
    
    // Bitmap pool for transformed frames - alternates between 2 bitmaps
    // to avoid concurrent access (current frame + MediaPipe processing previous)
    private val transformedBitmapPool = arrayOfNulls<Bitmap>(2)
    private var transformPoolIndex = 0
    private var reusableCanvas: Canvas? = null
    private val srcRectF = RectF()
    private val dstRectF = RectF()
    private val transformPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /**
     * Initialize the pose landmarker with optimal settings
     * @param modelType The model type to use (Full or Heavy)
     * @param useGpu true to use GPU acceleration (recommended)
     */
    fun initialize(modelType: ModelType = ModelType.FULL, useGpu: Boolean = true) {
        currentModelType = modelType
        try {
            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath(modelType.fileName)

            // Use GPU if available, fallback to CPU
            if (useGpu) {
                baseOptionsBuilder.setDelegate(Delegate.GPU)
            } else {
                baseOptionsBuilder.setDelegate(Delegate.CPU)
            }

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(NUM_POSES)
                .setMinPoseDetectionConfidence(MIN_POSE_DETECTION_CONFIDENCE)
                .setMinTrackingConfidence(MIN_POSE_TRACKING_CONFIDENCE)
                .setMinPosePresenceConfidence(MIN_POSE_PRESENCE_CONFIDENCE)
                .setResultListener(this::onPoseResult)
                .setErrorListener(this::onPoseError)
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            isInitialized = true
            
            Log.d(TAG, "PoseLandmarker initialized successfully with GPU=$useGpu, Model=${modelType.displayName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PoseLandmarker: ${e.message}")
            
            // Fallback to CPU if GPU failed
            if (useGpu) {
                Log.w(TAG, "Retrying with CPU...")
                initialize(modelType, useGpu = false)
            } else {
                listener.onError("Failed to initialize pose detection: ${e.message}")
            }
        }
    }

    /**
     * Process a camera frame for pose detection.
     *
     * Uses ImageProxy.toBitmap() (CameraX 1.3+) which handles YUV→RGB
     * conversion efficiently, avoiding the RGBA_8888 output format that
     * forces a slow software conversion inside CameraX and caps FPS at ~30.
     */
    fun detectPose(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (!isInitialized || poseLandmarker == null) {
            imageProxy.close()
            return
        }

        try {
            val frameTime = SystemClock.uptimeMillis()
            frameCameraState[frameTime] = isFrontCamera

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val proxyWidth = imageProxy.width
            val proxyHeight = imageProxy.height

            // Convert YUV → Bitmap using CameraX's optimized path, then release.
            val sourceBitmap = imageProxy.toBitmap()
            imageProxy.close()

            // Build the rotation + mirror matrix (reuse object)
            reusableMatrix.reset()
            reusableMatrix.postRotate(rotationDegrees.toFloat())
            if (isFrontCamera) {
                reusableMatrix.postScale(
                    -1f, 1f,
                    proxyWidth.toFloat(),
                    proxyHeight.toFloat()
                )
            }

            // Calculate output dimensions
            srcRectF.set(0f, 0f, sourceBitmap.width.toFloat(), sourceBitmap.height.toFloat())
            reusableMatrix.mapRect(dstRectF, srcRectF)
            val rotatedWidth = Math.round(dstRectF.width())
            val rotatedHeight = Math.round(dstRectF.height())

            // Alternate between 2 pooled bitmaps (current write vs MediaPipe reading previous)
            transformPoolIndex = (transformPoolIndex + 1) % 2
            var rotatedBitmap = transformedBitmapPool[transformPoolIndex]
            if (rotatedBitmap == null || rotatedBitmap.width != rotatedWidth ||
                rotatedBitmap.height != rotatedHeight || rotatedBitmap.isRecycled) {
                rotatedBitmap = Bitmap.createBitmap(rotatedWidth, rotatedHeight, Bitmap.Config.ARGB_8888)
                transformedBitmapPool[transformPoolIndex] = rotatedBitmap
            }

            val canvas = reusableCanvas ?: Canvas().also { reusableCanvas = it }
            canvas.setBitmap(rotatedBitmap)
            canvas.save()
            canvas.translate(-dstRectF.left, -dstRectF.top)
            canvas.concat(reusableMatrix)
            canvas.drawBitmap(sourceBitmap, 0f, 0f, transformPaint)
            canvas.restore()

            lastImageWidth = rotatedBitmap.width
            lastImageHeight = rotatedBitmap.height

            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
            poseLandmarker?.detectAsync(mpImage, frameTime)

        } catch (e: Exception) {
            Log.e(TAG, "Error during pose detection: ${e.message}")
            try { imageProxy.close() } catch (_: Exception) {}
        }
    }

    /**
     * Callback when pose detection result is available
     */
    private fun onPoseResult(result: PoseLandmarkerResult, input: MPImage) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val frameTimestampMs = result.timestampMs()
        val inferenceTime = finishTimeMs - frameTimestampMs
        
        // Retrieve and remove the camera state for THIS specific frame
        // This eliminates the race condition with the old @Volatile approach
        val isFrontCamera = frameCameraState.remove(frameTimestampMs) ?: false
        
        if (result.landmarks().isEmpty()) {
            listener.onNoPoseDetected()
            return
        }
        
        // Get the first (and only) detected pose
        val landmarks = result.landmarks()[0]
        val worldLandmarks = if (result.worldLandmarks().isNotEmpty()) {
            result.worldLandmarks()[0]
        } else null
        
        listener.onPoseDetected(
            PoseResult(
                landmarks = landmarks,
                worldLandmarks = worldLandmarks,
                // Use the original frame timestamp for smoothing consistency
                timestampMs = frameTimestampMs,
                inferenceTimeMs = inferenceTime,
                imageWidth = input.width,
                imageHeight = input.height,
                modelType = currentModelType.displayName,
                isFrontCamera = isFrontCamera
            )
        )
    }

    /**
     * Callback when pose detection error occurs
     */
    private fun onPoseError(error: RuntimeException) {
        Log.e(TAG, "Pose detection error: ${error.message}")
        listener.onError(error.message ?: "Unknown error")
    }

    /**
     * Release resources
     */
    fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
        isInitialized = false
        
        closeVideoMode()
        closeImageMode()
        
        frameCameraState.clear()
        
        transformedBitmapPool.forEachIndexed { i, bmp ->
            bmp?.recycle()
            transformedBitmapPool[i] = null
        }
        reusableCanvas?.setBitmap(null)
        reusableCanvas = null
        
        Log.d(TAG, "PoseLandmarker closed")
    }

    /**
     * Check if initialized
     */
    fun isReady(): Boolean = isInitialized
    
    // ==================== VIDEO Mode Support ====================
    
    /**
     * Initialize for VIDEO mode (synchronous detection)
     * 
     * VIDEO mode is used for analyzing pre-recorded videos.
     * Key differences from LIVE_STREAM:
     * - Uses detectForVideo() instead of detectAsync()
     * - Returns result directly (synchronous)
     * - Expects monotonically increasing timestamps
     * 
     * @param modelType The model type to use
     * @param useGpu true to use GPU acceleration
     */
    fun initializeForVideo(modelType: ModelType = ModelType.FULL, useGpu: Boolean = true) {
        currentRunMode = RunMode.VIDEO
        currentModelType = modelType
        
        try {
            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath(modelType.fileName)
            
            if (useGpu) {
                baseOptionsBuilder.setDelegate(Delegate.GPU)
            } else {
                baseOptionsBuilder.setDelegate(Delegate.CPU)
            }
            
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(RunningMode.VIDEO)  // VIDEO mode - synchronous
                .setNumPoses(NUM_POSES)
                .setMinPoseDetectionConfidence(MIN_POSE_DETECTION_CONFIDENCE)
                .setMinTrackingConfidence(MIN_POSE_TRACKING_CONFIDENCE)
                .setMinPosePresenceConfidence(MIN_POSE_PRESENCE_CONFIDENCE)
                // No ResultListener needed for VIDEO mode
                .build()
            
            videoModeLandmarker = PoseLandmarker.createFromOptions(context, options)
            isVideoModeInitialized = true
            
            Log.d(TAG, "PoseLandmarker VIDEO mode initialized with GPU=$useGpu, Model=${modelType.displayName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize VIDEO mode: ${e.message}")
            
            // Fallback to CPU if GPU failed
            if (useGpu) {
                Log.w(TAG, "Retrying VIDEO mode with CPU...")
                initializeForVideo(modelType, useGpu = false)
            } else {
                listener.onError("Failed to initialize VIDEO mode: ${e.message}")
            }
        }
    }
    
    /**
     * Detect pose from bitmap (VIDEO mode - synchronous)
     * 
     * IMPORTANT: Uses detectForVideo() - the correct API for VIDEO mode
     * - detect() is for IMAGE mode (single image)
     * - detectForVideo() is for VIDEO mode (sequential frames)
     * - detectAsync() is for LIVE_STREAM mode (camera)
     * 
     * @param bitmap The frame to analyze
     * @param timestampMs The presentation timestamp of the frame
     * @return PoseResult or null if no pose detected
     */
    fun detectPoseFromBitmap(bitmap: Bitmap, timestampMs: Long): PoseResult? {
        if (!isVideoModeInitialized || videoModeLandmarker == null) {
            Log.w(TAG, "VIDEO mode not initialized")
            return null
        }
        
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            
            // Use detectForVideo() for VIDEO mode (synchronous)
            val result = videoModeLandmarker?.detectForVideo(mpImage, timestampMs)
            
            result?.let { convertToResult(it, bitmap.width, bitmap.height) }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in VIDEO mode detection: ${e.message}")
            null
        }
    }
    
    /**
     * Reset VIDEO mode landmarker
     * 
     * IMPORTANT: MediaPipe VIDEO mode expects monotonically increasing timestamps.
     * When seeking backwards in a video, we must recreate the landmarker
     * to reset its internal state.
     * 
     * Call this when:
     * - User seeks to a previous position
     * - Analysis is restarted
     */
    fun resetForVideo() {
        Log.d(TAG, "Resetting VIDEO mode landmarker")
        
        videoModeLandmarker?.close()
        videoModeLandmarker = null
        isVideoModeInitialized = false
        
        // Reinitialize
        initializeForVideo(currentModelType)
    }
    
    /**
     * Convert PoseLandmarkerResult to PoseResult
     */
    private fun convertToResult(
        result: PoseLandmarkerResult,
        width: Int,
        height: Int
    ): PoseResult? {
        if (result.landmarks().isEmpty()) {
            return null
        }
        
        val landmarks = result.landmarks()[0]
        val worldLandmarks = if (result.worldLandmarks().isNotEmpty()) {
            result.worldLandmarks()[0]
        } else null
        
        return PoseResult(
            landmarks = landmarks,
            worldLandmarks = worldLandmarks,
            timestampMs = result.timestampMs(),
            inferenceTimeMs = 0,
            imageWidth = width,
            imageHeight = height,
            modelType = currentModelType.displayName
        )
    }
    
    /**
     * Close VIDEO mode landmarker
     */
    fun closeVideoMode() {
        videoModeLandmarker?.close()
        videoModeLandmarker = null
        isVideoModeInitialized = false
        Log.d(TAG, "VIDEO mode landmarker closed")
    }
    
    /**
     * Check if VIDEO mode is ready
     */
    fun isVideoModeReady(): Boolean = isVideoModeInitialized
    
    // ==================== IMAGE Mode Support ====================
    
    private var imageModeLandmarker: PoseLandmarker? = null
    private var isImageModeInitialized = false
    
    /**
     * Initialize for IMAGE mode (single-frame synchronous detection).
     *
     * IMAGE mode uses detect() which has no temporal tracking —
     * each frame is treated independently.
     */
    fun initializeForImage(modelType: ModelType = ModelType.FULL, useGpu: Boolean = true) {
        currentModelType = modelType
        
        try {
            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath(modelType.fileName)
            
            if (useGpu) {
                baseOptionsBuilder.setDelegate(Delegate.GPU)
            } else {
                baseOptionsBuilder.setDelegate(Delegate.CPU)
            }
            
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(RunningMode.IMAGE)
                .setNumPoses(NUM_POSES)
                .setMinPoseDetectionConfidence(MIN_POSE_DETECTION_CONFIDENCE)
                .setMinTrackingConfidence(MIN_POSE_TRACKING_CONFIDENCE)
                .setMinPosePresenceConfidence(MIN_POSE_PRESENCE_CONFIDENCE)
                .build()
            
            imageModeLandmarker = PoseLandmarker.createFromOptions(context, options)
            isImageModeInitialized = true
            
            Log.d(TAG, "PoseLandmarker IMAGE mode initialized with GPU=$useGpu, Model=${modelType.displayName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize IMAGE mode: ${e.message}")
            if (useGpu) {
                Log.w(TAG, "Retrying IMAGE mode with CPU...")
                initializeForImage(modelType, useGpu = false)
            } else {
                listener.onError("Failed to initialize IMAGE mode: ${e.message}")
            }
        }
    }
    
    /**
     * Detect pose from bitmap (IMAGE mode - synchronous, single frame).
     */
    fun detectPoseFromImage(bitmap: Bitmap): PoseResult? {
        if (!isImageModeInitialized || imageModeLandmarker == null) {
            Log.w(TAG, "IMAGE mode not initialized")
            return null
        }
        
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = imageModeLandmarker?.detect(mpImage)
            result?.let { convertToResult(it, bitmap.width, bitmap.height) }
        } catch (e: Exception) {
            Log.e(TAG, "Error in IMAGE mode detection: ${e.message}")
            null
        }
    }
    
    fun closeImageMode() {
        imageModeLandmarker?.close()
        imageModeLandmarker = null
        isImageModeInitialized = false
        Log.d(TAG, "IMAGE mode landmarker closed")
    }
    
    fun isImageModeReady(): Boolean = isImageModeInitialized
    
    /**
     * Get current run mode
     */
    fun getCurrentRunMode(): RunMode = currentRunMode

    /**
     * Listener interface for pose detection results
     */
    interface PoseDetectionListener {
        fun onPoseDetected(result: PoseResult)
        fun onNoPoseDetected()
        fun onError(message: String)
    }
}

/**
 * Data class to hold pose detection result
 * Includes image dimensions for proper overlay scaling
 */
data class PoseResult(
    val landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
    val worldLandmarks: List<com.google.mediapipe.tasks.components.containers.Landmark>?,
    val timestampMs: Long,
    val inferenceTimeMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val modelType: String,
    /**
     * Whether this result is from front camera.
     * When true, landmarks need to be swapped (LEFT ↔ RIGHT) for correct angle calculation
     * because the image was mirrored before pose detection.
     */
    val isFrontCamera: Boolean = false
)
