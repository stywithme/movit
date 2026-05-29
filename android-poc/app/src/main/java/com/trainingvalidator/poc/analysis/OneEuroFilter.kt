package com.trainingvalidator.poc.analysis

import kotlin.math.abs

/**
 * OneEuroFilter - Adaptive low-pass filter for smooth, responsive tracking
 * 
 * The One Euro Filter is the gold standard for filtering noisy position data
 * while maintaining responsiveness. It's used in:
 * - MediaPipe's internal smoothing
 * - Apple ARKit
 * - Microsoft Kinect
 * - Most VR/AR tracking systems
 * 
 * Key Innovation:
 * - When movement is SLOW → applies more smoothing (reduces jitter)
 * - When movement is FAST → applies less smoothing (reduces lag)
 * 
 * Parameters:
 * @param minCutoff Minimum cutoff frequency (Hz). Lower = smoother but more lag.
 *                  Normalized landmarks: 0.7–1.3. Default: 1.0
 * @param beta Speed coefficient. Higher = more responsive to fast movements.
 *             Normalized landmarks: 1.0–2.5. Default: 1.5
 * @param dCutoff Derivative cutoff frequency. Usually keep at 1.0.
 * 
 * Tuning Guide (normalized MediaPipe coordinates 0–1):
 * - Reduce minCutoff to reduce jitter (but adds lag for slow movements)
 * - Increase beta to reduce lag during fast movements
 * - Start with minCutoff=1.0, beta=1.5, then adjust
 * 
 * Reference: http://cristal.univ-lille.fr/~casiez/1euro/
 */
class OneEuroFilter(
    private val minCutoff: Float = 1.0f,
    private val beta: Float = 1.5f,
    private val dCutoff: Float = 1.0f
) {
    companion object {
        // Pre-computed constant to avoid Math.PI.toFloat() on every frame
        private const val TWO_PI_FLOAT = 6.2831855f  // 2 * PI as Float
    }
    
    private var xFilter: LowPassFilter? = null
    private var dxFilter: LowPassFilter? = null
    private var lastTime: Long = 0
    private var lastValue: Float? = null
    
    /**
     * Filter a new value
     * 
     * @param value The raw input value
     * @param timestamp Current timestamp in milliseconds
     * @return Filtered value
     */
    fun filter(value: Float, timestamp: Long): Float {
        // Calculate time delta
        val dt = if (lastTime == 0L) {
            1.0f / 30.0f  // Assume 30 FPS for first frame
        } else {
            ((timestamp - lastTime) / 1000.0f).coerceIn(0.001f, 0.1f)
        }
        lastTime = timestamp
        
        // Initialize filters on first call
        if (xFilter == null) {
            xFilter = LowPassFilter(computeAlpha(minCutoff, dt), value)
            dxFilter = LowPassFilter(computeAlpha(dCutoff, dt), 0f)
            lastValue = value
            return value
        }
        
        // Calculate derivative (speed of change)
        val dx = (value - (lastValue ?: value)) / dt
        lastValue = value
        
        // Filter the derivative
        val filteredDx = dxFilter!!.filter(dx, computeAlpha(dCutoff, dt))
        
        // Adaptive cutoff based on speed
        // Fast movement → higher cutoff → less smoothing → more responsive
        // Slow movement → lower cutoff → more smoothing → less jitter
        val cutoff = minCutoff + beta * abs(filteredDx)
        
        // Apply the main filter with adaptive cutoff
        return xFilter!!.filter(value, computeAlpha(cutoff, dt))
    }
    
    /**
     * Reset the filter state
     * Call this when there's a discontinuity (e.g., new pose detection)
     */
    fun reset() {
        xFilter = null
        dxFilter = null
        lastTime = 0
        lastValue = null
    }
    
    /**
     * Compute alpha for low-pass filter from cutoff frequency
     * OPTIMIZED: Uses pre-computed TWO_PI_FLOAT constant
     */
    private fun computeAlpha(cutoff: Float, dt: Float): Float {
        val tau = 1.0f / (TWO_PI_FLOAT * cutoff)
        return 1.0f / (1.0f + tau / dt)
    }
    
    /**
     * Simple low-pass filter
     */
    private class LowPassFilter(
        private var alpha: Float,
        private var previousValue: Float
    ) {
        fun filter(value: Float, alpha: Float): Float {
            this.alpha = alpha
            val result = alpha * value + (1 - alpha) * previousValue
            previousValue = result
            return result
        }
    }
}

/**
 * FilterResult3D - Reusable container for 3D filter output
 * 
 * Avoids allocating a new Triple on every frame (~1000/sec for 33 landmarks at 30fps).
 * Supports Kotlin destructuring via componentN operators.
 */
class FilterResult3D {
    var x: Float = 0f
    var y: Float = 0f
    var z: Float = 0f
    
    operator fun component1() = x
    operator fun component2() = y
    operator fun component3() = z
}

/**
 * OneEuroFilter3D - One Euro Filter for 3D coordinates
 * 
 * Applies independent filtering to X, Y, Z axes.
 * Each axis adapts independently based on its movement speed.
 * 
 * Performance: Uses a reusable FilterResult3D to avoid allocation per frame.
 * Kotlin destructuring still works: val (sx, sy, sz) = filter.filter(x, y, z, t)
 */
class OneEuroFilter3D(
    minCutoff: Float = 1.0f,
    beta: Float = 1.5f,
    dCutoff: Float = 1.0f
) {
    private val xFilter = OneEuroFilter(minCutoff, beta, dCutoff)
    private val yFilter = OneEuroFilter(minCutoff, beta, dCutoff)
    private val zFilter = OneEuroFilter(minCutoff, beta, dCutoff)
    
    /** Reusable result object — values are immediately destructured by callers */
    private val result = FilterResult3D()
    
    /**
     * Filter a 3D point
     * 
     * @return Reusable FilterResult3D (supports destructuring)
     */
    fun filter(x: Float, y: Float, z: Float, timestamp: Long): FilterResult3D {
        result.x = xFilter.filter(x, timestamp)
        result.y = yFilter.filter(y, timestamp)
        result.z = zFilter.filter(z, timestamp)
        return result
    }
    
    /**
     * Reset all axis filters
     */
    fun reset() {
        xFilter.reset()
        yFilter.reset()
        zFilter.reset()
    }
}
