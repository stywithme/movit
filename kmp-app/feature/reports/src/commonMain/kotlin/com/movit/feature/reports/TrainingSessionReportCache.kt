package com.movit.feature.reports

import com.movit.core.data.cache.MovitLruCache
import com.movit.core.training.report.MovitPostTrainingReport
import com.movit.core.training.report.MovitSessionReport

/**
 * In-memory post-training reports keyed by upload id (G4 / §14.2).
 * Populated when a session finalizes; consumed by [SharedReportDetailRepository].
 */
object TrainingSessionReportCache {
    private const val MAX_REPORTS = 10
    private val reports = MovitLruCache<String, MovitPostTrainingReport>(MAX_REPORTS)
    private val sessionReports = MovitLruCache<String, MovitSessionReport>(MAX_REPORTS)

    fun put(uploadId: String, report: MovitPostTrainingReport) {
        if (uploadId.isBlank()) return
        reports.put(uploadId, report)
    }

    fun get(uploadId: String): MovitPostTrainingReport? = reports.get(uploadId)

    fun putSession(reportId: String, report: MovitSessionReport) {
        if (reportId.isBlank()) return
        sessionReports.put(reportId, report)
    }

    fun getSession(reportId: String): MovitSessionReport? = sessionReports.get(reportId)

    /** Re-keys a cached post-training report when the server returns a different report id. */
    fun rekeyPostTraining(fromId: String, toId: String) {
        if (fromId.isBlank() || toId.isBlank() || fromId == toId) return
        val report = reports.get(fromId) ?: return
        reports.put(toId, report.copy(id = toId, workoutId = toId))
        reports.remove(fromId)
    }

    internal fun clearAll() {
        reports.clear()
        sessionReports.clear()
    }
}
