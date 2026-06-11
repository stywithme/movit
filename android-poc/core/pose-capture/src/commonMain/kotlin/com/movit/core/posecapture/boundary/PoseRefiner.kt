package com.movit.core.posecapture.boundary

import com.movit.core.training.model.Landmark

/**
 * D5: optional MLP landmark refinement (Android LiteRT actual; iOS v1 no-op).
 */
interface PoseRefiner {
    val isAvailable: Boolean
    fun refineLandmarks(landmarks: List<Landmark>): List<Landmark>
}

object NoOpPoseRefiner : PoseRefiner {
    override val isAvailable: Boolean = false
    override fun refineLandmarks(landmarks: List<Landmark>): List<Landmark> = landmarks
}
