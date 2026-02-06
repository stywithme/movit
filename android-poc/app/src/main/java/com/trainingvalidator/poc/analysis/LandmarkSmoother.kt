package com.trainingvalidator.poc.analysis

import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.components.containers.Landmark
import com.trainingvalidator.poc.training.config.SettingsManager

/**
 * LandmarkSmoother - Adaptive smoothing for landmark positions
 * 
 * Uses One Euro Filter for optimal balance between:
 * - Responsiveness (fast movements track accurately)
 * - Stability (slow/stationary positions don't jitter)
 * 
 * One Euro Filter automatically adapts:
 * - When movement is FAST → less smoothing → responsive tracking
 * - When movement is SLOW → more smoothing → no jitter
 * 
 * Parameters (configurable):
 * @param minCutoff Lower = smoother but more lag. Range: 0.5-3.0, Default: 1.5
 * @param beta Higher = more responsive to fast movements. Range: 0.0-0.5, Default: 0.01
 * @param useLegacyEMA If true, uses simple EMA instead of One Euro (for comparison)
 * @param legacyAlpha EMA alpha value (only used if useLegacyEMA = true)
 */
class LandmarkSmoother(
    private val minCutoff: Float = 1.5f,   // Slightly higher for responsiveness
    private val beta: Float = 0.01f,        // Adaptive speed coefficient
    private val useLegacyEMA: Boolean = false,
    private val legacyAlpha: Float = 0.6f
) {
    // OPTIMIZED: Use Array instead of Map for O(1) direct index access
    // Array is faster than Map for integer-indexed lookups
    private val landmarkFilters = arrayOfNulls<OneEuroFilter3D>(NUM_LANDMARKS)
    
    // Legacy EMA storage
    private var previousLandmarks: MutableList<SmoothedLandmark>? = null
    
    /**
     * Smooth a list of landmarks using One Euro Filter
     * 
     * @param landmarks Raw landmarks from MediaPipe
     * @param timestamp Current frame timestamp in milliseconds
     * @return Smoothed landmarks ready for display
     */
    fun smooth(landmarks: List<NormalizedLandmark>, timestamp: Long): List<SmoothedLandmark> {
        return if (useLegacyEMA) {
            smoothWithEMA(landmarks)
        } else {
            smoothWithOneEuro(landmarks, timestamp)
        }
    }
    
    /**
     * One Euro Filter smoothing - RECOMMENDED
     * 
     * Provides adaptive smoothing that:
     * - Tracks fast movements accurately (low lag)
     * - Eliminates jitter during slow/stationary poses
     */
    private fun smoothWithOneEuro(
        landmarks: List<NormalizedLandmark>,
        timestamp: Long
    ): List<SmoothedLandmark> {
        return landmarks.mapIndexed { index, landmark ->
            val visibility = landmark.visibility().orElse(0f)
            val presence = landmark.presence().orElse(0f)
            
            // OPTIMIZED: Direct array access instead of map lookup
            // Get or create filter for this landmark
            var filter = if (index < NUM_LANDMARKS) landmarkFilters[index] else null
            if (filter == null) {
                filter = OneEuroFilter3D(minCutoff, beta)
                if (index < NUM_LANDMARKS) {
                    landmarkFilters[index] = filter
                }
            }
            
            // Apply adaptive filtering
            val (smoothX, smoothY, smoothZ) = filter.filter(
                landmark.x(),
                landmark.y(),
                landmark.z(),
                timestamp
            )
            
            SmoothedLandmark(
                x = smoothX,
                y = smoothY,
                z = smoothZ,
                visibility = visibility,  // Don't smooth visibility/presence
                presence = presence
            )
        }
    }
    
    /**
     * Legacy EMA smoothing - kept for comparison/fallback
     * 
     * Simple but has trade-off: smooth OR responsive, not both
     */
    private fun smoothWithEMA(landmarks: List<NormalizedLandmark>): List<SmoothedLandmark> {
        val prev = previousLandmarks
        
        val smoothed = landmarks.mapIndexed { index, landmark ->
            val visibility = landmark.visibility().orElse(0f)
            val presence = landmark.presence().orElse(0f)
            
            if (prev == null || index >= prev.size) {
                SmoothedLandmark(
                    x = landmark.x(),
                    y = landmark.y(),
                    z = landmark.z(),
                    visibility = visibility,
                    presence = presence
                )
            } else {
                val p = prev[index]
                SmoothedLandmark(
                    x = legacyAlpha * landmark.x() + (1 - legacyAlpha) * p.x,
                    y = legacyAlpha * landmark.y() + (1 - legacyAlpha) * p.y,
                    z = legacyAlpha * landmark.z() + (1 - legacyAlpha) * p.z,
                    visibility = visibility,
                    presence = presence
                )
            }
        }.toMutableList()
        
        previousLandmarks = smoothed
        return smoothed
    }
    
    /**
     * Convert world landmarks to SmoothedLandmark
     * 
     * World landmarks are in meters and generally more stable,
     * so we apply lighter smoothing or none.
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

    /**
     * Reset all filter states
     * Call when pose detection restarts or loses track
     */
    fun reset() {
        // OPTIMIZED: Reset array elements instead of map operations
        for (i in landmarkFilters.indices) {
            landmarkFilters[i]?.reset()
            landmarkFilters[i] = null
        }
        previousLandmarks = null
    }
    
    /**
     * Update filter parameters at runtime
     * Useful for tuning without restart
     */
    fun updateParameters(newMinCutoff: Float, newBeta: Float) {
        // Clear filters to use new parameters on next frame
        for (i in landmarkFilters.indices) {
            landmarkFilters[i] = null
        }
    }
    
    companion object {
        // MediaPipe returns 33 landmarks
        private const val NUM_LANDMARKS = 33
        
        /**
         * Create LandmarkSmoother from app settings
         * This is the RECOMMENDED way to create a smoother
         * 
         * Reads configuration from SettingsManager which loads from app_settings.json
         */
        fun createFromSettings(): LandmarkSmoother {
            val useLegacy = SettingsManager.useLegacySmoothing()
            val minCutoff = SettingsManager.getSmoothingMinCutoff()
            val beta = SettingsManager.getSmoothingBeta()
            val preset = SettingsManager.getSmoothingPreset()
            
            Log.d("LandmarkSmoother", "Creating smoother from settings:")
            Log.d("LandmarkSmoother", "  Preset: $preset")
            Log.d("LandmarkSmoother", "  Use Legacy EMA: $useLegacy")
            
            return if (useLegacy) {
                val alpha = SettingsManager.getLegacySmoothingAlpha()
                Log.d("LandmarkSmoother", "  Legacy Alpha: $alpha")
                createLegacy(alpha)
            } else {
                Log.d("LandmarkSmoother", "  One Euro Filter - minCutoff: $minCutoff, beta: $beta")
                LandmarkSmoother(
                    minCutoff = minCutoff,
                    beta = beta
                )
            }
        }
        
        /**
         * Preset configurations for different use cases
         */
        
        /** Fast tracking with minimal smoothing - good for fast exercises */
        fun createResponsive() = LandmarkSmoother(
            minCutoff = 2.5f,
            beta = 0.02f
        )
        
        /** Balanced - good for most exercises */
        fun createBalanced() = LandmarkSmoother(
            minCutoff = 1.5f,
            beta = 0.01f
        )
        
        /** Smooth - good for slow exercises like yoga/stretching */
        fun createSmooth() = LandmarkSmoother(
            minCutoff = 0.8f,
            beta = 0.005f
        )
        
        /** Legacy EMA mode for comparison */
        fun createLegacy(alpha: Float = 0.6f) = LandmarkSmoother(
            useLegacyEMA = true,
            legacyAlpha = alpha
        )
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
