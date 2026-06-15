package com.movit.feature.trainingdebug

expect fun isTrainingDebugLabEnabled(): Boolean

fun isInputModeSupportedOnPlatform(mode: TrainingDebugInputMode): Boolean = when (mode) {
    TrainingDebugInputMode.CAMERA -> true
    TrainingDebugInputMode.VIDEO,
    TrainingDebugInputMode.IMAGE,
    -> isTrainingDebugLabEnabled() && isMediaInputModeSupported()
}

internal expect fun isMediaInputModeSupported(): Boolean
