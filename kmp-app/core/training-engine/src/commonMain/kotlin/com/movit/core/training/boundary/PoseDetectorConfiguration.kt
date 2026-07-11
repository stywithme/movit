package com.movit.core.training.boundary

data class PoseDetectorConfiguration(
    val modelAssetName: String? = null,
    val minDetectionConfidence: Float = 0.5f,
    val minTrackingConfidence: Float = 0.5f,
    val minPosePresenceConfidence: Float = 0.5f,
    val useGpu: Boolean = true,
)
