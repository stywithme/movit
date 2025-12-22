package com.trainingvalidator.poc.analysis

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * LandmarkSmoother - Smooths landmark positions to reduce jitter
 * 
 * Uses Exponential Moving Average (EMA) for each landmark position.
 * This reduces the "jumping" effect when landmarks are detected with slight variations.
 */
class LandmarkSmoother(
    private val smoothingFactor: Float = 0.4f // 0 = no smoothing, 1 = no change
) {
    private var previousLandmarks: MutableList<SmoothedLandmark>? = null
    
    /**
     * Smooth a list of landmarks
     * 
     * @param landmarks Raw landmarks from MediaPipe
     * @return Smoothed landmarks
     */
    fun smooth(landmarks: List<NormalizedLandmark>): List<SmoothedLandmark> {
        val previous = previousLandmarks
        
        val smoothed = if (previous == null || previous.size != landmarks.size) {
            // First frame or landmark count changed - no smoothing
            landmarks.map { landmark ->
                SmoothedLandmark(
                    x = landmark.x(),
                    y = landmark.y(),
                    z = landmark.z(),
                    visibility = landmark.visibility().orElse(0f),
                    presence = landmark.presence().orElse(0f)
                )
            }
        } else {
            // Apply smoothing
            landmarks.mapIndexed { index, landmark ->
                val prev = previous[index]
                SmoothedLandmark(
                    x = prev.x + smoothingFactor * (landmark.x() - prev.x),
                    y = prev.y + smoothingFactor * (landmark.y() - prev.y),
                    z = prev.z + smoothingFactor * (landmark.z() - prev.z),
                    visibility = landmark.visibility().orElse(0f),
                    presence = landmark.presence().orElse(0f)
                )
            }
        }.toMutableList()
        
        previousLandmarks = smoothed
        return smoothed
    }
    
    /**
     * Reset the smoother (call when switching cameras or restarting)
     */
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
    val visibility: Float,  // 0-1, how likely the landmark is visible
    val presence: Float     // 0-1, how likely the landmark is present in the image
) {
    /**
     * Check if landmark is visible enough to draw
     */
    fun isVisible(threshold: Float = 0.5f): Boolean {
        return visibility >= threshold
    }
    
    /**
     * Check if landmark is present enough to use
     */
    fun isPresent(threshold: Float = 0.5f): Boolean {
        return presence >= threshold
    }
}
