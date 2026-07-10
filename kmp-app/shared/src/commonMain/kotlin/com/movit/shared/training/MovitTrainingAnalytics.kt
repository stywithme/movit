package com.movit.shared.training

/**
 * Thin product-analytics facade for the training journey.
 * ponytail: default [sink] is println-only — no product SDK in repo yet; host apps
 * may assign a real sink at startup (see Android `AnalyticsStorage` / outbox for uploads only).
 */
object MovitTrainingAnalytics {
    var sink: (String, Map<String, String>) -> Unit = { name, params ->
        val tail = if (params.isEmpty()) "" else " " + params.entries.joinToString { "${it.key}=${it.value}" }
        println("[MovitTrainingAnalytics] $name$tail")
    }

    fun trackStartWorkout(workoutId: String, source: String? = null) {
        sink("training_start_workout", buildMap {
            put("workout_id", workoutId)
            source?.let { put("source", it) }
        })
    }

    fun trackCompleteRun(runId: String) {
        sink("training_complete_run", mapOf("run_id" to runId))
    }

    fun trackSaveAndExit(runId: String?) {
        sink("training_save_and_exit", buildMap {
            runId?.let { put("run_id", it) }
        })
    }

    fun trackEndWorkout(runId: String?) {
        sink("training_end_workout", buildMap {
            runId?.let { put("run_id", it) }
        })
    }

    fun trackOpenReport(reportId: String, scope: String? = null) {
        sink("training_open_report", buildMap {
            put("report_id", reportId)
            scope?.let { put("scope", it) }
        })
    }
}
