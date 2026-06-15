package com.movit.feature.trainingdebug

import com.movit.feature.trainingdebug.buildconfig.MovitGeneratedBuildConfig

actual fun isTrainingDebugLabEnabled(): Boolean = MovitGeneratedBuildConfig.DEBUG
internal actual fun isMediaInputModeSupported(): Boolean = true
