package com.movit.core.data.repository

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.training.report.MovitPostTrainingReport
import com.movit.core.training.report.MovitSessionReport
import kotlinx.serialization.Serializable

@Serializable
private data class ExerciseSetReportsIndex(
    val reportsBySet: Map<Int, String> = emptyMap(),
)

@Serializable
private data class ReportSessionExerciseRef(
    val sessionExerciseKey: String,
)

/** Durable post-training / session reports keyed by upload or server report id. */
class PostTrainingReportLocalStore(
    private val localStore: MovitLocalStore,
) {
    fun putPostTraining(reportId: String, report: MovitPostTrainingReport) {
        if (reportId.isBlank()) return
        MovitCachePolicy.writeJson(
            localStore,
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.postTrainingReportKey(reportId),
            report,
            MovitPostTrainingReport.serializer(),
        )
    }

    fun getPostTraining(reportId: String): MovitPostTrainingReport? {
        if (reportId.isBlank()) return null
        return MovitCachePolicy.readJson(
            localStore,
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.postTrainingReportKey(reportId),
            MovitPostTrainingReport.serializer(),
        )
    }

    fun registerExerciseSetReport(sessionExerciseKey: String, setNumber: Int, reportId: String) {
        if (sessionExerciseKey.isBlank() || reportId.isBlank() || setNumber <= 0) return
        val current = readSetReportsIndex(sessionExerciseKey).reportsBySet.toMutableMap()
        current[setNumber] = reportId
        writeSetReportsIndex(sessionExerciseKey, ExerciseSetReportsIndex(current))
        MovitCachePolicy.writeJson(
            localStore,
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.reportSessionExerciseKey(reportId),
            ReportSessionExerciseRef(sessionExerciseKey),
            ReportSessionExerciseRef.serializer(),
        )
    }

    fun getReportSessionExerciseKey(reportId: String): String? {
        if (reportId.isBlank()) return null
        return MovitCachePolicy.readJson(
            localStore,
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.reportSessionExerciseKey(reportId),
            ReportSessionExerciseRef.serializer(),
        )?.sessionExerciseKey
    }

    fun listExerciseSetReportIds(sessionExerciseKey: String): Map<Int, String> {
        if (sessionExerciseKey.isBlank()) return emptyMap()
        return readSetReportsIndex(sessionExerciseKey).reportsBySet
    }

    fun putSession(reportId: String, report: MovitSessionReport) {
        if (reportId.isBlank()) return
        MovitCachePolicy.writeJson(
            localStore,
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.sessionReportKey(reportId),
            report,
            MovitSessionReport.serializer(),
        )
    }

    fun getSession(reportId: String): MovitSessionReport? {
        if (reportId.isBlank()) return null
        return MovitCachePolicy.readJson(
            localStore,
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.sessionReportKey(reportId),
            MovitSessionReport.serializer(),
        )
    }

    fun rekeyPostTraining(fromId: String, toId: String) {
        if (fromId.isBlank() || toId.isBlank() || fromId == toId) return
        val report = getPostTraining(fromId) ?: return
        putPostTraining(toId, report.copy(id = toId, workoutId = toId))
        localStore.removeJsonCache(
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.postTrainingReportKey(fromId),
        )
        val sessionExerciseKey = getReportSessionExerciseKey(fromId) ?: return
        localStore.removeJsonCache(
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.reportSessionExerciseKey(fromId),
        )
        MovitCachePolicy.writeJson(
            localStore,
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.reportSessionExerciseKey(toId),
            ReportSessionExerciseRef(sessionExerciseKey),
            ReportSessionExerciseRef.serializer(),
        )
        val index = readSetReportsIndex(sessionExerciseKey).reportsBySet.toMutableMap()
        index.entries.find { it.value == fromId }?.let { (setNumber, _) ->
            index[setNumber] = toId
            writeSetReportsIndex(sessionExerciseKey, ExerciseSetReportsIndex(index))
        }
    }

    private fun readSetReportsIndex(sessionExerciseKey: String): ExerciseSetReportsIndex =
        MovitCachePolicy.readJson(
            localStore,
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.exerciseSetReportsIndexKey(sessionExerciseKey),
            ExerciseSetReportsIndex.serializer(),
        ) ?: ExerciseSetReportsIndex()

    private fun writeSetReportsIndex(sessionExerciseKey: String, index: ExerciseSetReportsIndex) {
        MovitCachePolicy.writeJson(
            localStore,
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.exerciseSetReportsIndexKey(sessionExerciseKey),
            index,
            ExerciseSetReportsIndex.serializer(),
        )
    }
}
