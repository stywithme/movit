package com.movit.core.training.boundary

data class CameraSourceConfiguration(
    val useFrontCamera: Boolean = false,
    val targetFps: Int = TrainingThroughputProfiles.STABLE.targetFps,
    val analysisWidth: Int = TrainingThroughputProfiles.STABLE.analysisWidth,
    val analysisHeight: Int = TrainingThroughputProfiles.STABLE.analysisHeight,
    val throughputProfileId: String = TrainingThroughputProfiles.STABLE.id,
)
