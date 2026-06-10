package com.movit.core.training.model

/**
 * Platform-neutral landmark produced by pose detection adapters.
 * Legacy Android [com.trainingvalidator.poc.analysis.SmoothedLandmark] maps here.
 */
data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float,
    val presence: Float,
) {
    fun isVisible(threshold: Float = 0.5f): Boolean = visibility >= threshold

    fun isPresent(threshold: Float = 0.5f): Boolean = presence >= threshold
}
