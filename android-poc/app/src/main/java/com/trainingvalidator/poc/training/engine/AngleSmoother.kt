package com.trainingvalidator.poc.training.engine

import com.trainingvalidator.poc.training.config.SettingsManager
import java.util.ArrayDeque

/**
 * AngleSmoother - Centralized angle smoothing layer
 * 
 * Single Source of Truth for smoothed angles.
 * All components (PhaseStateMachine, FormValidator, UI) should use
 * smoothed angles from this class to ensure consistency.
 * 
 * Smoothing Algorithm:
 * Uses moving average with configurable window size.
 * Each joint has its own circular buffer for independent smoothing.
 * 
 * Performance:
 * - Uses ArrayDeque for O(1) add/remove operations
 * - Maintains running sum for O(1) average calculation
 * 
 * Usage:
 * ```kotlin
 * val smoother = AngleSmoother()
 * 
 * // In processFrame:
 * val smoothedAngles = smoother.smooth(rawAngles)
 * 
 * // Pass smoothedAngles to all components
 * stateMachine.update(smoothedAngles)
 * formValidator.validate(smoothedAngles, phase)
 * ```
 * 
 * Benefits:
 * - Consistent angles across all components
 * - No Phase/Validation desynchronization
 * - Single place to tune smoothing parameters
 * - O(1) operations for high-frequency frame processing
 */
class AngleSmoother(
    private val windowSize: Int = SettingsManager.getSmoothingWindowSize()
) {
    
    companion object {
        private const val TAG = "AngleSmoother"
    }
    
    /**
     * Circular buffer per joint code using ArrayDeque for O(1) operations
     */
    private val jointBuffers = mutableMapOf<String, CircularAngleBuffer>()
    
    /**
     * Circular buffer with running sum for O(1) average calculation
     */
    private inner class CircularAngleBuffer {
        private val buffer = ArrayDeque<Double>(windowSize)
        private var runningSum = 0.0
        
        fun add(value: Double): Double {
            // Remove oldest if at capacity
            if (buffer.size >= windowSize) {
                runningSum -= buffer.removeFirst()
            }
            
            // Add new value
            buffer.addLast(value)
            runningSum += value
            
            // Return average (O(1) calculation)
            return runningSum / buffer.size
        }
        
        fun average(): Double? {
            return if (buffer.isEmpty()) null else runningSum / buffer.size
        }
        
        fun size(): Int = buffer.size
        
        fun toList(): List<Double> = buffer.toList()
        
        fun clear() {
            buffer.clear()
            runningSum = 0.0
        }
    }
    
    /**
     * Smooth a map of joint angles
     * 
     * @param rawAngles Map of joint code to raw angle value
     * @return Map of joint code to smoothed angle value
     */
    fun smooth(rawAngles: Map<String, Double>): Map<String, Double> {
        val smoothedAngles = mutableMapOf<String, Double>()
        
        for ((jointCode, rawAngle) in rawAngles) {
            smoothedAngles[jointCode] = smoothJoint(jointCode, rawAngle)
        }
        
        return smoothedAngles
    }
    
    /**
     * Smooth a single joint's angle
     * 
     * Uses circular buffer with running sum for O(1) operations
     * 
     * @param jointCode Unique identifier for the joint
     * @param rawAngle Raw angle value from AngleCalculator
     * @return Smoothed angle value
     */
    private fun smoothJoint(jointCode: String, rawAngle: Double): Double {
        val buffer = jointBuffers.getOrPut(jointCode) { CircularAngleBuffer() }
        return buffer.add(rawAngle)
    }
    
    /**
     * Get the smoothed angle for a specific joint
     * Returns the last smoothed value or null if no history
     * 
     * @param jointCode Joint identifier
     * @return Last smoothed angle or null
     */
    fun getSmoothedAngle(jointCode: String): Double? {
        return jointBuffers[jointCode]?.average()
    }
    
    /**
     * Get raw history for a joint (for debugging)
     * 
     * @param jointCode Joint identifier
     * @return List of recent angle values
     */
    fun getHistory(jointCode: String): List<Double> {
        return jointBuffers[jointCode]?.toList() ?: emptyList()
    }
    
    /**
     * Clear smoothing buffers for specific joints (e.g. Any-Side skipped joints).
     * Next time these joints appear, they start from scratch instead of averaging
     * with stale historical data that may belong to a different camera perspective.
     */
    fun clearJoints(jointCodes: Set<String>) {
        for (code in jointCodes) {
            jointBuffers.remove(code)
        }
    }

    /**
     * Reset all smoothing history
     * Call when training restarts or exercise changes
     */
    fun reset() {
        jointBuffers.values.forEach { it.clear() }
        jointBuffers.clear()
    }
    
    /**
     * Reset history for a specific joint
     * 
     * @param jointCode Joint identifier
     */
    fun resetJoint(jointCode: String) {
        jointBuffers[jointCode]?.clear()
        jointBuffers.remove(jointCode)
    }
    
    /**
     * Check if smoother has enough history for stable output
     * 
     * @param jointCode Joint identifier
     * @return True if history is at least half the window size
     */
    fun isStable(jointCode: String): Boolean {
        val buffer = jointBuffers[jointCode] ?: return false
        return buffer.size() >= windowSize / 2
    }
    
    /**
     * Check if all tracked joints have stable smoothing
     * 
     * @param jointCodes List of joint codes to check
     * @return True if all joints are stable
     */
    fun areAllStable(jointCodes: Collection<String>): Boolean {
        return jointCodes.all { isStable(it) }
    }
}
