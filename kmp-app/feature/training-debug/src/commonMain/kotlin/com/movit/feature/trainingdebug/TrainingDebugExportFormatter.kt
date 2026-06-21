package com.movit.feature.trainingdebug

object TrainingDebugExportFormatter {
    fun formatText(
        config: TrainingDebugConfig,
        analysis: TrainingDebugAnalysisResult,
        fps: TrainingDebugFpsCounters,
        modelLabel: String,
    ): String = buildString {
        appendLine("=== Movit Training Debug Lab ===")
        appendLine("mode=${config.inputMode} tab=${config.activeTab} model=$modelLabel")
        appendLine("fps source=${fps.sourceFps} inference=${fps.inferenceFps} analysis=${fps.analysisFps}")
        appendLine("status=${analysis.statusText}")
        appendLine("live=${analysis.liveValueText}")
        appendLine()
        appendLine(analysis.infoPanelText)
        if (analysis.jsonSnapshot.isNotBlank()) {
            appendLine()
            appendLine("--- json ---")
            appendLine(analysis.jsonSnapshot)
        }
    }
}
