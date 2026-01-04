package com.trainingvalidator.poc.analysis

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.sqrt

/**
 * VelocityFilter - Rejects landmarks that move too fast (teleportation)
 * 
 * IMPORTANT: Only applies to HIGH VISIBILITY landmarks.
 * Low visibility landmarks are passed through unchanged - let the drawing code handle them.
 */
class VelocityFilter(
    private val maxVelocity: Float = 0.3f, // Max 30% of screen per frame
    private val visibilityThreshold: Float = 0.65f, // Only filter high-confidence landmarks
    private val maxTimeDelta: Long = 500L // Reset if more than 500ms between frames
) {
    private var previousLandmarks: List<NormalizedLandmark>? = null
    private var previousTimestamp: Long = 0L

    /**
     * Filter landmarks based on velocity
     * Only applies to landmarks with good visibility on BOTH frames
     */
    fun filter(
        landmarks: List<NormalizedLandmark>,
        timestamp: Long
    ): List<NormalizedLandmark> {
        val prev = previousLandmarks
        val timeDelta = timestamp - previousTimestamp
        
        // Reset conditions:
        // 1. First frame
        // 2. Timestamp went backwards
        // 3. Size changed
        // 4. Too much time passed (person might have moved significantly)
        if (prev == null || 
            timestamp <= previousTimestamp || 
            prev.size != landmarks.size ||
            timeDelta > maxTimeDelta) {
            previousLandmarks = landmarks
            previousTimestamp = timestamp
            return landmarks
        }
        
        val filteredLandmarks = landmarks.mapIndexed { i, current ->
            val previous = prev[i]
            
            val currentVisibility = current.visibility().orElse(0f)
            val previousVisibility = previous.visibility().orElse(0f)
            
            // Only apply velocity filter if BOTH have good visibility
            // If either has low visibility, pass through the current value unchanged
            if (currentVisibility < visibilityThreshold || previousVisibility < visibilityThreshold) {
                // Don't filter low-visibility landmarks - let them pass through
                // The drawing code will handle them based on visibility
                current
            } else {
                // Both have good visibility - check for teleportation
                val dx = current.x() - previous.x()
                val dy = current.y() - previous.y()
                val distance = sqrt(dx * dx + dy * dy)
                
                if (distance > maxVelocity) {
                    // Teleportation detected on a visible landmark!
                    // Use previous position to prevent jump
                    previous
                } else {
                    current
                }
            }
        }
        
        // IMPORTANT: Always store the CURRENT landmarks (not filtered)
        // This prevents accumulating stale positions
        previousLandmarks = landmarks
        previousTimestamp = timestamp
        
        return filteredLandmarks
    }
    
    fun reset() {
        previousLandmarks = null
        previousTimestamp = 0L
    }
}
