package com.movit.feature.reports

import com.movit.core.data.cache.MovitLruCache
import com.movit.core.training.report.MovitPostTrainingReport

/**
 * In-memory post-training reports keyed by upload id (G4 / §14.2).
 * Populated when a session finalizes; consumed by [SharedReportDetailRepository].
 */
object TrainingSessionReportCache {
    private const val MAX_REPORTS = 10
    private val reports = MovitLruCache<String, MovitPostTrainingReport>(MAX_REPORTS)

    fun put(uploadId: String, report: MovitPostTrainingReport) {
        if (uploadId.isBlank()) return
        reports.put(uploadId, report)
    }

    fun get(uploadId: String): MovitPostTrainingReport? = reports.get(uploadId)

    internal fun clearAll() {
        reports.clear()
    }
}
