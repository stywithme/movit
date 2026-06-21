package com.movit.feature.training

import com.movit.core.training.boundary.CameraSourceConfiguration
import com.movit.core.training.boundary.TrainingThroughputProfiles

internal expect fun readTrainingThroughputProfileFlag(): String?

fun resolveTrainingCameraConfiguration(useFrontCamera: Boolean): CameraSourceConfiguration {
    val profile = TrainingThroughputProfiles.resolve(readTrainingThroughputProfileFlag())
    return TrainingThroughputProfiles.toCameraConfiguration(profile, useFrontCamera)
}
