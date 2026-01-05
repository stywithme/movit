package com.trainingvalidator.poc.training.engine

import com.trainingvalidator.poc.training.config.SettingsManager

/**
 * AngleSmoother - Centralized angle smoothing layer
 * 
 * Single Source of Truth for smoothed angles.
 * All components (PhaseStateMachine, FormValidator, UI) should use
 * smoothed angles from this class to ensure consistency.
 * 
 * Smoothing Algorithm:
 * Uses moving average with configurable window size.
 * Each joint has its own history buffer for independent smoothing.
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
 * - Easy to debug and test
 */
class AngleSmoother(
    private val windowSize: Int = SettingsManager.getSmoothingWindowSize()
) {
    
    companion object {
        private const val TAG = "AngleSmoother"
    }
    
    /**
     * History buffer per joint code
     * Each joint maintains its own history for independent smoothing
     */
    private val jointHistories = mutableMapOf<String, MutableList<Double>>()
    
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
     * Uses moving average: adds new value, removes oldest if over window size
     * 
     * @param jointCode Unique identifier for the joint
     * @param rawAngle Raw angle value from AngleCalculator
     * @return Smoothed angle value
     */
    private fun smoothJoint(jointCode: String, rawAngle: Double): Double {
        val history = jointHistories.getOrPut(jointCode) { mutableListOf() }
        
        // Add new value
        history.add(rawAngle)
        
        // Remove oldest if over window size
        while (history.size > windowSize) {
            history.removeAt(0)
        }
        
        // Return average
        return history.average()
    }
    
    /**
     * Get the smoothed angle for a specific joint
     * Returns the last smoothed value or null if no history
     * 
     * @param jointCode Joint identifier
     * @return Last smoothed angle or null
     */
    fun getSmoothedAngle(jointCode: String): Double? {
        return jointHistories[jointCode]?.average()
    }
    
    /**
     * Get raw history for a joint (for debugging)
     * 
     * @param jointCode Joint identifier
     * @return List of recent angle values
     */
    fun getHistory(jointCode: String): List<Double> {
        return jointHistories[jointCode]?.toList() ?: emptyList()
    }
    
    /**
     * Reset all smoothing history
     * Call when training restarts or exercise changes
     */
    fun reset() {
        jointHistories.clear()
    }
    
    /**
     * Reset history for a specific joint
     * 
     * @param jointCode Joint identifier
     */
    fun resetJoint(jointCode: String) {
        jointHistories.remove(jointCode)
    }
    
    /**
     * Check if smoother has enough history for stable output
     * 
     * @param jointCode Joint identifier
     * @return True if history is at least half the window size
     */
    fun isStable(jointCode: String): Boolean {
        val history = jointHistories[jointCode] ?: return false
        return history.size >= windowSize / 2
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
