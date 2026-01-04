package com.trainingvalidator.poc.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
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

/**
 * ModelType - Enum for supported MediaPipe models
 */
enum class ModelType(val fileName: String, val displayName: String) {
    FULL("pose_landmarker_full.task", "Full (Balanced)"),
    HEAVY("pose_landmarker_heavy.task", "Heavy (Accuracy)")
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
    
    // Store last image dimensions for result callback
    private var lastImageWidth = 0
    private var lastImageHeight = 0
    
    // Reusable bitmap buffer to reduce GC pressure
    private var bitmapBuffer: Bitmap? = null

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
     * Process a camera frame for pose detection
     * Uses Google's recommended approach for image conversion
     */
    fun detectPose(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (!isInitialized || poseLandmarker == null) {
            imageProxy.close()
            return
        }

        try {
            // Use SystemClock.uptimeMillis() for consistent timestamps (Google's approach)
            val frameTime = SystemClock.uptimeMillis()
            
            // Get or create bitmap buffer matching the ImageProxy dimensions
            val buffer = bitmapBuffer?.takeIf { 
                it.width == imageProxy.width && it.height == imageProxy.height 
            } ?: Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            ).also { bitmapBuffer = it }
            
            // Copy pixels directly from buffer (faster than toBitmap())
            imageProxy.use { 
                buffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) 
            }
            
            // Apply rotation and mirroring
            val matrix = Matrix().apply {
                // Rotate based on image rotation
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                
                // Mirror for front camera
                if (isFrontCamera) {
                    postScale(
                        -1f, 1f,
                        imageProxy.width.toFloat(),
                        imageProxy.height.toFloat()
                    )
                }
            }
            
            val rotatedBitmap = Bitmap.createBitmap(
                buffer, 0, 0,
                buffer.width, buffer.height,
                matrix, true
            )
            
            // Store dimensions for the result callback
            lastImageWidth = rotatedBitmap.width
            lastImageHeight = rotatedBitmap.height
            
            // Convert to MediaPipe Image and detect
            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
            poseLandmarker?.detectAsync(mpImage, frameTime)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during pose detection: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Callback when pose detection result is available
     */
    private fun onPoseResult(result: PoseLandmarkerResult, input: MPImage) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()
        
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
                timestampMs = finishTimeMs,
                inferenceTimeMs = inferenceTime,
                imageWidth = input.width,
                imageHeight = input.height,
                modelType = currentModelType.displayName
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
        Log.d(TAG, "PoseLandmarker closed")
    }

    /**
     * Check if initialized
     */
    fun isReady(): Boolean = isInitialized

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
    val modelType: String
)
