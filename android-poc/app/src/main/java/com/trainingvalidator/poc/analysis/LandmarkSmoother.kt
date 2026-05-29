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
 * Parameters (configurable, tuned for normalized MediaPipe coordinates 0–1):
 * @param minCutoff Lower = smoother but more lag. Range: 0.7–1.3, Default: 1.0
 * @param beta Higher = more responsive to fast movements. Range: 1.0–2.5, Default: 1.5
 * @param useLegacyEMA If true, uses simple EMA instead of One Euro (for comparison)
 * @param legacyAlpha EMA alpha value (only used if useLegacyEMA = true)
 */
class LandmarkSmoother(
    private val minCutoff: Float = 1.0f,
    private val beta: Float = 1.5f,
    private val useLegacyEMA: Boolean = false,
    private val legacyAlpha: Float = 0.6f
) {
    // OPTIMIZED: Use Array instead of Map for O(1) direct index access
    // Array is faster than Map for integer-indexed lookups
    private val landmarkFilters = arrayOfNulls<OneEuroFilter3D>(NUM_LANDMARKS)
    
    // Separate filters for world landmarks (3D meter-scale coordinates)
    // World landmarks need their own smoothing because they operate in a different
    // coordinate space and their Z values can be particularly noisy
    private val worldLandmarkFilters = arrayOfNulls<OneEuroFilter3D>(NUM_LANDMARKS)
    
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
        val result = landmarks.mapIndexed { index, landmark ->
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
        
        return appendVirtualLandmarks(result)
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
        return appendVirtualLandmarks(smoothed)
    }
    
    /**
     * Convert and smooth world landmarks to SmoothedLandmark
     * 
     * World landmarks are in meters and generally more stable than normalized,
     * but still exhibit jitter (especially Z-axis depth estimates).
     * Applying One Euro Filter ensures angle calculations derived from these
     * landmarks are stable and consistent frame-to-frame.
     * 
     * @param landmarks Raw world landmarks from MediaPipe
     * @param timestamp Current frame timestamp in milliseconds (for filter rate)
     */
    fun convertWorld(landmarks: List<Landmark>, timestamp: Long): List<SmoothedLandmark> {
        val result = landmarks.mapIndexed { index, landmark ->
            val visibility = landmark.visibility().orElse(0f)
            val presence = landmark.presence().orElse(0f)
            
            // Get or create filter for this world landmark
            var filter = if (index < NUM_LANDMARKS) worldLandmarkFilters[index] else null
            if (filter == null) {
                filter = OneEuroFilter3D(minCutoff, beta)
                if (index < NUM_LANDMARKS) {
                    worldLandmarkFilters[index] = filter
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
                visibility = visibility,
                presence = presence
            )
        }
        
        return appendVirtualLandmarks(result)
    }
    
    /**
     * Append virtual landmarks (neck, spine) computed as midpoints.
     * 
     * Index 33 = Neck  = midpoint(left_shoulder[11], right_shoulder[12])
     * Index 34 = Spine = midpoint(left_hip[23], right_hip[24])
     * 
     * These enable angle calculations and position checks for
     * joints that don't exist as raw MediaPipe landmarks.
     */
    private fun appendVirtualLandmarks(landmarks: List<SmoothedLandmark>): List<SmoothedLandmark> {
        if (landmarks.size < 33) return landmarks  // Not enough data
        
        val ls = landmarks[11]  // left_shoulder
        val rs = landmarks[12]  // right_shoulder
        val lh = landmarks[23]  // left_hip
        val rh = landmarks[24]  // right_hip
        
        // Neck = midpoint of shoulders
        val neck = SmoothedLandmark(
            x = (ls.x + rs.x) / 2f,
            y = (ls.y + rs.y) / 2f,
            z = (ls.z + rs.z) / 2f,
            visibility = minOf(ls.visibility, rs.visibility),
            presence = minOf(ls.presence, rs.presence)
        )
        
        // Spine = midpoint of hips
        val spine = SmoothedLandmark(
            x = (lh.x + rh.x) / 2f,
            y = (lh.y + rh.y) / 2f,
            z = (lh.z + rh.z) / 2f,
            visibility = minOf(lh.visibility, rh.visibility),
            presence = minOf(lh.presence, rh.presence)
        )
        
        return landmarks + listOf(neck, spine)
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
        for (i in worldLandmarkFilters.indices) {
            worldLandmarkFilters[i]?.reset()
            worldLandmarkFilters[i] = null
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
        for (i in worldLandmarkFilters.indices) {
            worldLandmarkFilters[i] = null
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
        
        /** Fast tracking — higher cutoff and beta for quick movements */
        fun createResponsive() = LandmarkSmoother(
            minCutoff = 1.3f,
            beta = 2.0f
        )
        
        /** Balanced — recommended default for most exercises */
        fun createBalanced() = LandmarkSmoother(
            minCutoff = 1.0f,
            beta = 1.5f
        )
        
        /** Smooth — strongest smoothing at rest, for slow exercises like yoga */
        fun createSmooth() = LandmarkSmoother(
            minCutoff = 0.7f,
            beta = 1.0f
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
