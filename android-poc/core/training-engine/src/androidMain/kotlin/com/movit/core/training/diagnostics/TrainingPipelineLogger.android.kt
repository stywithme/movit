package com.movit.core.training.diagnostics

import android.util.Log

private const val TAG = "TrainingPipeline"

internal actual fun trainingPipelineLog(line: String) {
    Log.i(TAG, line)
}

internal actual fun isTrainingPipelineDiagnosticsEnabled(): Boolean = true
