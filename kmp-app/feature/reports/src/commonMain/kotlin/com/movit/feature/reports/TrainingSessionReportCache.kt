package com.movit.feature.reports

import com.movit.core.data.MovitData
import com.movit.core.data.cache.MovitLruCache
import com.movit.core.data.repository.PostTrainingReportLocalStore
import com.movit.core.training.report.MovitPostTrainingReport
import com.movit.core.training.report.MovitPostTrainingReportCrossSetAggregator
import com.movit.core.training.report.MovitSessionReport

/**
 * Post-training reports keyed by upload id (G4 / §14.2).
 * In-memory LRU for hot reads; SQL-backed when [MovitData] is installed.
 */
object TrainingSessionReportCache {
    private const val MAX_REPORTS = 10
    private val reports = MovitLruCache<String, MovitPostTrainingReport>(MAX_REPORTS)
    private val sessionReports = MovitLruCache<String, MovitSessionReport>(MAX_REPORTS)
    private val setReportsBySessionExercise = mutableMapOf<String, MutableMap<Int, String>>()
    private val reportSessionExerciseKeys = mutableMapOf<String, String>()

    private fun persistentStore(): PostTrainingReportLocalStore? =
        if (MovitData.isInstalled) PostTrainingReportLocalStore(MovitData.localStore) else null

    fun put(
        uploadId: String,
        report: MovitPostTrainingReport,
        sessionExerciseKey: String? = null,
        setNumber: Int? = null,
    ) {
        if (uploadId.isBlank()) return
        reports.put(uploadId, report)
        persistentStore()?.putPostTraining(uploadId, report)
        registerSetReport(uploadId, sessionExerciseKey, setNumber)
    }

    fun get(uploadId: String): MovitPostTrainingReport? {
        if (uploadId.isBlank()) return null
        reports.get(uploadId)?.let { return it }
        return persistentStore()?.getPostTraining(uploadId)?.also { reports.put(uploadId, it) }
    }

    /** Loads a report and merges sibling per-set uploads when indexed for the same exercise session. */
    fun getMergedForDisplay(uploadId: String): MovitPostTrainingReport? {
        val primary = get(uploadId) ?: return null
        val sessionExerciseKey = reportSessionExerciseKeys[uploadId]
            ?: persistentStore()?.getReportSessionExerciseKey(uploadId)
            ?: return primary
        val siblingIds = listSetReportIds(sessionExerciseKey).values.distinct()
        if (siblingIds.size <= 1) return primary
        val siblings = siblingIds.mapNotNull { get(it) }
        return MovitPostTrainingReportCrossSetAggregator.merge(siblings) ?: primary
    }

    fun putSession(reportId: String, report: MovitSessionReport) {
        if (reportId.isBlank()) return
        sessionReports.put(reportId, report)
        persistentStore()?.putSession(reportId, report)
    }

    fun getSession(reportId: String): MovitSessionReport? {
        if (reportId.isBlank()) return null
        sessionReports.get(reportId)?.let { return it }
        return persistentStore()?.getSession(reportId)?.also { sessionReports.put(reportId, it) }
    }

    /** Re-keys a cached post-training report when the server returns a different report id. */
    fun rekeyPostTraining(fromId: String, toId: String) {
        if (fromId.isBlank() || toId.isBlank() || fromId == toId) return
        val report = reports.get(fromId) ?: persistentStore()?.getPostTraining(fromId) ?: return
        val rekeyed = report.copy(id = toId, workoutId = toId)
        reports.put(toId, rekeyed)
        reports.remove(fromId)
        reportSessionExerciseKeys.remove(fromId)?.let { sessionKey ->
            reportSessionExerciseKeys[toId] = sessionKey
            setReportsBySessionExercise[sessionKey]?.entries?.find { it.value == fromId }?.let { (setNumber, _) ->
                setReportsBySessionExercise.getOrPut(sessionKey) { mutableMapOf() }[setNumber] = toId
            }
        }
        persistentStore()?.rekeyPostTraining(fromId, toId)
    }

    internal fun clearAll() {
        reports.clear()
        sessionReports.clear()
        setReportsBySessionExercise.clear()
        reportSessionExerciseKeys.clear()
    }

    private fun registerSetReport(uploadId: String, sessionExerciseKey: String?, setNumber: Int?) {
        if (sessionExerciseKey.isNullOrBlank() || setNumber == null || setNumber <= 0) return
        setReportsBySessionExercise.getOrPut(sessionExerciseKey) { mutableMapOf() }[setNumber] = uploadId
        reportSessionExerciseKeys[uploadId] = sessionExerciseKey
        persistentStore()?.registerExerciseSetReport(sessionExerciseKey, setNumber, uploadId)
    }

    private fun listSetReportIds(sessionExerciseKey: String): Map<Int, String> {
        val memory = setReportsBySessionExercise[sessionExerciseKey]
        if (memory != null && memory.isNotEmpty()) return sortedBySetNumber(memory)
        val disk = persistentStore()?.listExerciseSetReportIds(sessionExerciseKey).orEmpty()
        if (disk.isNotEmpty()) {
            setReportsBySessionExercise[sessionExerciseKey] = disk.toMutableMap()
            disk.values.forEach { reportId ->
                reportSessionExerciseKeys[reportId] = sessionExerciseKey
            }
        }
        return sortedBySetNumber(disk)
    }

    /** ponytail: LinkedHashMap from sorted entries — `toSortedMap` is JVM-only. */
    private fun sortedBySetNumber(map: Map<Int, String>): Map<Int, String> =
        map.entries.sortedBy { it.key }.associate { it.key to it.value }
}
