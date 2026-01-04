package com.trainingvalidator.poc.training.engine

import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.pose.BodyLandmarks
import kotlin.math.abs

/**
 * CameraPositionDetector - Automatically detects camera position and body facing direction
 * 
 * Uses Z-depth and X-position analysis to determine:
 * - Camera angle (front, side_left, side_right, back)
 * - Which side of the body is closer to camera
 * - Body facing direction
 * 
 * This enables position checks to work correctly regardless of how the user is positioned.
 */
object CameraPositionDetector {
    
    private const val TAG = "CameraPositionDetector"
    
    // Detection thresholds
    private const val SHOULDER_X_DIFF_THRESHOLD = 0.12f  // Below = front/back view
    private const val SHOULDER_Z_DIFF_THRESHOLD = 0.08f  // Above = side view
    private const val NOSE_VISIBILITY_THRESHOLD = 0.6f   // To distinguish front from back
    private const val FACING_Z_THRESHOLD = 0.03f         // To determine body facing direction
    
    /**
     * Detected camera position with confidence and body info
     */
    data class CameraDetectionResult(
        val position: DetectedCameraPosition,
        val confidence: Float,
        val facingDirection: DetectedFacing,
        val closerSide: BodySide,
        val depthInfo: DepthInfo
    )
    
    /**
     * Detected camera positions
     */
    enum class DetectedCameraPosition {
        FRONT_VIEW,      // Camera from front
        BACK_VIEW,       // Camera from back
        SIDE_VIEW_LEFT,  // Camera from left (left side closer)
        SIDE_VIEW_RIGHT, // Camera from right (right side closer)
        DIAGONAL,        // Angled view
        UNKNOWN
    }
    
    /**
     * Detected facing direction
     */
    enum class DetectedFacing {
        FACING_RIGHT,    // Person facing right in frame
        FACING_LEFT,     // Person facing left in frame
        FACING_CAMERA,   // Person facing camera
        FACING_AWAY,     // Person's back to camera
        UNKNOWN
    }
    
    /**
     * Which side of body is closer to camera
     */
    enum class BodySide {
        LEFT,
        RIGHT,
        BOTH_EQUAL,
        UNKNOWN
    }
    
    /**
     * Depth information for key landmarks
     */
    data class DepthInfo(
        val leftShoulderZ: Float,
        val rightShoulderZ: Float,
        val leftHipZ: Float,
        val rightHipZ: Float,
        val leftKneeZ: Float,
        val rightKneeZ: Float,
        val averageBodyZ: Float,
        val bodyDepthRange: Float
    )
    
    /**
     * Detect camera position and facing from landmarks
     * 
     * @param landmarks List of smoothed landmarks (33 points)
     * @return Detection result with position, facing, and confidence
     */
    fun detect(landmarks: List<SmoothedLandmark>): CameraDetectionResult {
        if (landmarks.size < 33) {
            return createUnknownResult()
        }
        
        // Extract key landmarks
        val leftShoulder = landmarks[BodyLandmarks.LEFT_SHOULDER]
        val rightShoulder = landmarks[BodyLandmarks.RIGHT_SHOULDER]
        val leftHip = landmarks[BodyLandmarks.LEFT_HIP]
        val rightHip = landmarks[BodyLandmarks.RIGHT_HIP]
        val leftKnee = landmarks[BodyLandmarks.LEFT_KNEE]
        val rightKnee = landmarks[BodyLandmarks.RIGHT_KNEE]
        val nose = landmarks[BodyLandmarks.NOSE]
        
        // Calculate depth info
        val depthInfo = DepthInfo(
            leftShoulderZ = leftShoulder.z,
            rightShoulderZ = rightShoulder.z,
            leftHipZ = leftHip.z,
            rightHipZ = rightHip.z,
            leftKneeZ = leftKnee.z,
            rightKneeZ = rightKnee.z,
            averageBodyZ = (leftShoulder.z + rightShoulder.z + leftHip.z + rightHip.z) / 4f,
            bodyDepthRange = calculateBodyDepthRange(landmarks)
        )
        
        // Calculate differences for detection
        val shoulderXDiff = abs(leftShoulder.x - rightShoulder.x)
        val shoulderZDiff = abs(leftShoulder.z - rightShoulder.z)
        
        // Average Z difference between left and right side
        val avgLeftZ = (leftShoulder.z + leftHip.z + leftKnee.z) / 3f
        val avgRightZ = (rightShoulder.z + rightHip.z + rightKnee.z) / 3f
        val sideZDiff = avgLeftZ - avgRightZ
        
        // Determine closer side
        val closerSide = when {
            abs(sideZDiff) < FACING_Z_THRESHOLD -> BodySide.BOTH_EQUAL
            sideZDiff < 0 -> BodySide.LEFT   // Left side has smaller Z = closer
            else -> BodySide.RIGHT
        }
        
        // Detect camera position
        val (position, confidence) = detectCameraPosition(
            shoulderXDiff, shoulderZDiff, sideZDiff, nose.visibility
        )
        
        // Detect facing direction
        val facingDirection = detectFacingDirection(position, closerSide, nose.visibility)
        
        return CameraDetectionResult(
            position = position,
            confidence = confidence,
            facingDirection = facingDirection,
            closerSide = closerSide,
            depthInfo = depthInfo
        )
    }
    
    private fun detectCameraPosition(
        shoulderXDiff: Float,
        shoulderZDiff: Float,
        sideZDiff: Float,
        noseVisibility: Float
    ): Pair<DetectedCameraPosition, Float> {
        return when {
            // Front/Back View: Shoulders are spread in X, similar in Z
            shoulderXDiff > SHOULDER_X_DIFF_THRESHOLD && 
            shoulderZDiff < SHOULDER_Z_DIFF_THRESHOLD -> {
                if (noseVisibility > NOSE_VISIBILITY_THRESHOLD) {
                    DetectedCameraPosition.FRONT_VIEW to 0.9f
                } else {
                    DetectedCameraPosition.BACK_VIEW to 0.7f
                }
            }
            
            // Side View: Shoulders are close in X, different in Z
            shoulderXDiff < SHOULDER_X_DIFF_THRESHOLD &&
            shoulderZDiff > SHOULDER_Z_DIFF_THRESHOLD -> {
                val sideConfidence = minOf(shoulderZDiff / 0.15f, 1f)
                if (sideZDiff < 0) {
                    DetectedCameraPosition.SIDE_VIEW_LEFT to sideConfidence
                } else {
                    DetectedCameraPosition.SIDE_VIEW_RIGHT to sideConfidence
                }
            }
            
            // Diagonal: Mix of both
            shoulderXDiff > 0.08f && shoulderZDiff > 0.05f -> {
                DetectedCameraPosition.DIAGONAL to 0.6f
            }
            
            else -> DetectedCameraPosition.UNKNOWN to 0.3f
        }
    }
    
    private fun detectFacingDirection(
        cameraPosition: DetectedCameraPosition,
        closerSide: BodySide,
        noseVisibility: Float
    ): DetectedFacing {
        return when (cameraPosition) {
            DetectedCameraPosition.FRONT_VIEW -> DetectedFacing.FACING_CAMERA
            DetectedCameraPosition.BACK_VIEW -> DetectedFacing.FACING_AWAY
            
            DetectedCameraPosition.SIDE_VIEW_LEFT,
            DetectedCameraPosition.SIDE_VIEW_RIGHT -> {
                // In side view, facing depends on which side is closer
                // If left side is closer, person is facing right (and vice versa)
                when (closerSide) {
                    BodySide.LEFT -> DetectedFacing.FACING_RIGHT
                    BodySide.RIGHT -> DetectedFacing.FACING_LEFT
                    else -> DetectedFacing.UNKNOWN
                }
            }
            
            else -> DetectedFacing.UNKNOWN
        }
    }
    
    private fun calculateBodyDepthRange(landmarks: List<SmoothedLandmark>): Float {
        val keyIndices = listOf(
            BodyLandmarks.LEFT_SHOULDER, BodyLandmarks.RIGHT_SHOULDER,
            BodyLandmarks.LEFT_HIP, BodyLandmarks.RIGHT_HIP,
            BodyLandmarks.LEFT_KNEE, BodyLandmarks.RIGHT_KNEE
        )
        
        val zValues = keyIndices.mapNotNull { landmarks.getOrNull(it)?.z }
        if (zValues.isEmpty()) return 0f
        
        return (zValues.maxOrNull() ?: 0f) - (zValues.minOrNull() ?: 0f)
    }
    
    private fun createUnknownResult() = CameraDetectionResult(
        DetectedCameraPosition.UNKNOWN, 0f,
        DetectedFacing.UNKNOWN, BodySide.UNKNOWN,
        DepthInfo(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    )
    
    /**
     * Convert detected position to JSON camera position string
     */
    fun toJsonCameraPosition(detected: DetectedCameraPosition): String {
        return when (detected) {
            DetectedCameraPosition.FRONT_VIEW -> "front_view"
            DetectedCameraPosition.BACK_VIEW -> "back_view"
            DetectedCameraPosition.SIDE_VIEW_LEFT,
            DetectedCameraPosition.SIDE_VIEW_RIGHT -> "side_view"
            DetectedCameraPosition.DIAGONAL -> "diagonal"
            DetectedCameraPosition.UNKNOWN -> "unknown"
        }
    }
    
    /**
     * Check if detected position matches expected position from config
     */
    fun matchesExpected(detected: DetectedCameraPosition, expected: String): Boolean {
        return when (expected) {
            "side_view" -> detected == DetectedCameraPosition.SIDE_VIEW_LEFT ||
                          detected == DetectedCameraPosition.SIDE_VIEW_RIGHT
            "front_view" -> detected == DetectedCameraPosition.FRONT_VIEW
            "back_view" -> detected == DetectedCameraPosition.BACK_VIEW
            else -> true  // If unknown expected, accept any
        }
    }
}
