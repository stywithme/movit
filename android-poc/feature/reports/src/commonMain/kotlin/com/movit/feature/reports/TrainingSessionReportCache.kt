package com.movit.feature.reports

import com.movit.core.training.report.MovitPostTrainingReport

/**
 * In-memory post-training reports keyed by upload id (G4 / §14.2).
 * Populated when a session finalizes; consumed by [SharedReportDetailRepository].
 */
object TrainingSessionReportCache {
    private val reports = mutableMapOf<String, MovitPostTrainingReport>()

    fun put(uploadId: String, report: MovitPostTrainingReport) {
        if (uploadId.isBlank()) return
        reports[uploadId] = report
    }

    fun get(uploadId: String): MovitPostTrainingReport? = reports[uploadId]

    internal fun clearAll() {
        reports.clear()
    }
}
