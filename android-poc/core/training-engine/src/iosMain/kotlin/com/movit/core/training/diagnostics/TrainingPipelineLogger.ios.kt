package com.movit.core.training.diagnostics

internal actual fun trainingPipelineLog(line: String) {
    println("[$TAG] $line")
}

private const val TAG = "TrainingPipeline"

internal actual fun isTrainingPipelineDiagnosticsEnabled(): Boolean = false
