package com.movit.core.training.diagnostics

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

internal actual fun trainingPipelineLog(line: String) {
    println("[$TAG] $line")
}

private const val TAG = "TrainingPipeline"

@OptIn(ExperimentalNativeApi::class)
internal actual fun isTrainingPipelineDiagnosticsEnabled(): Boolean = Platform.isDebugBinary
