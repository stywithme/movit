package com.movit.core.posecapture.android

import com.movit.core.posecapture.boundary.PoseRefiner
import com.movit.core.training.model.Landmark

/**
 * LiteRT MLP wrapper placeholder — geometric pipeline is used until assets are wired.
 */
class AndroidPoseRefiner : PoseRefiner {
    override val isAvailable: Boolean = false
    override fun refineLandmarks(landmarks: List<Landmark>): List<Landmark> = landmarks
}
