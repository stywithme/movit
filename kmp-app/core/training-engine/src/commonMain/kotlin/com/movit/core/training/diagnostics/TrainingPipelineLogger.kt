package com.movit.core.training.diagnostics

/** Platform sink for unified training pipeline diagnostics (debug / device triage). */
internal expect fun trainingPipelineLog(line: String)

internal expect fun isTrainingPipelineDiagnosticsEnabled(): Boolean
