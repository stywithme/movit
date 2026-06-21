package com.movit.feature.trainingdebug

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

@OptIn(ExperimentalNativeApi::class)
actual fun isTrainingDebugLabEnabled(): Boolean = Platform.isDebugBinary

internal actual fun isMediaInputModeSupported(): Boolean = false
