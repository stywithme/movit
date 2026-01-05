package com.trainingvalidator.poc.analysis

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.components.containers.Landmark

/**
 * LandmarkSmoother - LIGHTWEIGHT smoothing for landmark positions
 * 
 * Uses simple Exponential Moving Average (EMA) which is:
 * - Very fast (just multiplication and addition)
 * - Good enough for reducing jitter
 * - No lag when alpha is properly tuned
 */
class LandmarkSmoother(
    private val alpha: Float = 0.6f  // Reduced from 0.7 for smoother skeleton (Lower = smoother)
) {
    private var previousLandmarks: MutableList<SmoothedLandmark>? = null
    
    /**
     * Smooth a list of landmarks using simple EMA
     * This is O(n) with minimal computation per landmark
     */
    fun smooth(landmarks: List<NormalizedLandmark>, timestamp: Long): List<SmoothedLandmark> {
        val prev = previousLandmarks
        
        val smoothed = landmarks.mapIndexed { index, landmark ->
            val visibility = landmark.visibility().orElse(0f)
            val presence = landmark.presence().orElse(0f)
            
            if (prev == null || index >= prev.size) {
                // First frame - no smoothing
                SmoothedLandmark(
                    x = landmark.x(),
                    y = landmark.y(),
                    z = landmark.z(),
                    visibility = visibility,
                    presence = presence
                )
            } else {
                val p = prev[index]
                // Simple EMA: new = alpha * current + (1-alpha) * previous
                SmoothedLandmark(
                    x = alpha * landmark.x() + (1 - alpha) * p.x,
                    y = alpha * landmark.y() + (1 - alpha) * p.y,
                    z = alpha * landmark.z() + (1 - alpha) * p.z,
                    visibility = visibility,  // Don't smooth visibility
                    presence = presence
                )
            }
        }.toMutableList()
        
        previousLandmarks = smoothed
        return smoothed
    }
    
    /**
     * Convert world landmarks to SmoothedLandmark WITHOUT smoothing
     * World landmarks are already relatively stable
     */
    fun convertWorld(landmarks: List<Landmark>): List<SmoothedLandmark> {
        return landmarks.map { landmark ->
            SmoothedLandmark(
                x = landmark.x(),
                y = landmark.y(),
                z = landmark.z(),
                visibility = landmark.visibility().orElse(0f),
                presence = landmark.presence().orElse(0f)
            )
        }
    }

    fun reset() {
        previousLandmarks = null
    }
}

/**
 * Smoothed landmark with visibility information
 */
data class SmoothedLandmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float,
    val presence: Float
) {
    fun isVisible(threshold: Float = 0.5f): Boolean = visibility >= threshold
    fun isPresent(threshold: Float = 0.5f): Boolean = presence >= threshold
}
