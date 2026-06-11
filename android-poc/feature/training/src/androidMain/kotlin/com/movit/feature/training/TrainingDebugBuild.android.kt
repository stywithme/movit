package com.movit.feature.training

import com.movit.feature.training.BuildConfig

internal actual fun isTrainingDebugBuild(): Boolean = BuildConfig.DEBUG
