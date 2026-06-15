package com.movit.feature.training

import com.movit.feature.training.buildconfig.MovitGeneratedBuildConfig

internal actual fun isTrainingDebugBuild(): Boolean = MovitGeneratedBuildConfig.DEBUG
