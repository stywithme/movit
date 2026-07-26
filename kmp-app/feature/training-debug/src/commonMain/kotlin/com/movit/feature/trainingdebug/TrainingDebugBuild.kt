package com.movit.feature.trainingdebug

import com.movit.core.data.access.TrainingDebugAccess

expect fun isTrainingDebugLabEnabled(): Boolean

fun isInputModeSupportedOnPlatform(mode: TrainingDebugInputMode): Boolean = when (mode) {
    TrainingDebugInputMode.CAMERA -> true
    // Whoever may open the lab gets all of it — a release-build admin included.
    TrainingDebugInputMode.VIDEO,
    TrainingDebugInputMode.IMAGE,
    -> TrainingDebugAccess.isLabAvailable(isTrainingDebugLabEnabled()) && isMediaInputModeSupported()
}

internal expect fun isMediaInputModeSupported(): Boolean
