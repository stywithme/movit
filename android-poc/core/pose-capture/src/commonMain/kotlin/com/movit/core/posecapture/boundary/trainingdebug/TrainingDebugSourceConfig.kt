package com.movit.core.posecapture.boundary.trainingdebug

data class TrainingDebugSourceConfig(
    val modelType: PoseModelType = PoseModelType.FULL,
    val useGpu: Boolean = true,
    val minDetectionConfidence: Float = 0.5f,
    val minTrackingConfidence: Float = 0.5f,
    val minPresenceConfidence: Float = 0.5f,
)
