package com.movit.core.data.cache

import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.data.sync.WeekOfflinePackPrefetcher
import com.movit.core.network.MovitClock
import com.movit.core.network.MovitJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * GC policies for JSON blob caches (P2.5). Invoked after a successful sync cycle
 * (and optionally once per cold start). Never deletes post-training reports that
 * still have a non-SUCCEEDED outbox row (upload unconfirmed).
 */
class JsonCacheMaintenance(
    private val localStore: MovitLocalStore,
    private val platform: (() -> MovitPlatformBindings)? = null,
    private val nowEpochMs: () -> Long = { MovitClock.nowEpochMs() },
) {
    data class PurgeResult(
        val postTrainingRemoved: Int = 0,
        val plannedReportsRemoved: Int = 0,
        val metricsRemoved: Int = 0,
        val effectivePlansRemoved: Int = 0,
    )

    suspend fun runAfterSuccessfulSync(activeWeekNumber: Int? = null): PurgeResult {
        val protectedReportIds = protectedPostTrainingReportIds()
        val postTraining = purgePostTrainingReports(protectedReportIds)
        val planned = purgePlannedWorkoutReports()
        val metrics = purgeMetricsLru()
        val plans = purgeStaleEffectivePlans(activeWeekNumber)
        return PurgeResult(
            postTrainingRemoved = postTraining,
            plannedReportsRemoved = planned,
            metricsRemoved = metrics,
            effectivePlansRemoved = plans,
        )
    }

    /**
     * Report ids still referenced by a non-SUCCEEDED outbox row.
     * Link: operationId == upload.id == report id (plan P2.5).
     */
    suspend fun protectedPostTrainingReportIds(): Set<String> {
        val entries = localStore.listAllOutbox()
        return entries
            .asSequence()
            .filter { it.status != OutboxStatus.SUCCEEDED }
            .map { it.id }
            .filter { it.isNotBlank() }
            .toSet()
    }

    /** Session ids whose frame_captures/ dirs must survive GC (P2.5). */
    suspend fun protectedFrameCaptureSessionIds(): Set<String> {
        val fromOutbox = protectedPostTrainingReportIds()
        val fromCache = localStore.listJsonCacheEntriesWithTimestamps(MovitCacheKeys.REPORTS_STORE)
            .asSequence()
            .filter { it.key.startsWith(POST_TRAINING_PREFIX) }
            .map { it.key.removePrefix(POST_TRAINING_PREFIX) }
            .filter { it.isNotBlank() }
            .toSet()
        return fromOutbox + fromCache
    }

    fun purgePostTrainingReports(protectedIds: Set<String>): Int {
        val cutoff = nowEpochMs() - POST_TRAINING_TTL_MS
        val entries = localStore.listJsonCacheEntriesWithTimestamps(MovitCacheKeys.REPORTS_STORE)
        var removed = 0
        for (entry in entries) {
            if (!entry.key.startsWith(POST_TRAINING_PREFIX)) continue
            val reportId = entry.key.removePrefix(POST_TRAINING_PREFIX)
            if (reportId in protectedIds) continue
            // Absence of outbox row = upload confirmed (SUCCEEDED purged after 7d).
            if (entry.updatedAtEpochMs > 0L && entry.updatedAtEpochMs < cutoff) {
                removePostTrainingReportArtifacts(reportId)
                removed++
            }
        }
        return removed
    }

    fun purgePlannedWorkoutReports(): Int {
        val indexRaw = localStore.readJsonCache(
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.PLANNED_WORKOUT_REPORTS_INDEX,
        ) ?: return 0
        val ids = runCatching {
            MovitJson.decodeFromString(ListSerializer(String.serializer()), indexRaw)
        }.getOrDefault(emptyList())
        if (ids.isEmpty()) return 0

        val entries = localStore.listJsonCacheEntriesWithTimestamps(MovitCacheKeys.REPORTS_STORE)
            .filter { it.key.startsWith(PLANNED_REPORT_PREFIX) }
            .associateBy { it.key }

        val cutoff = nowEpochMs() - PLANNED_REPORT_TTL_MS
        val keep = linkedSetOf<String>()
        // Prefer newest by updated_at; fall back to index order for missing timestamps.
        val ranked = ids.sortedByDescending { id ->
            entries[MovitCacheKeys.plannedWorkoutReportKey(id)]?.updatedAtEpochMs ?: 0L
        }
        for (id in ranked) {
            val key = MovitCacheKeys.plannedWorkoutReportKey(id)
            val meta = entries[key]
            val tooOld = meta != null && meta.updatedAtEpochMs > 0L && meta.updatedAtEpochMs < cutoff
            if (keep.size < PLANNED_REPORT_MAX_KEEP && !tooOld) {
                keep += id
            }
        }
        // Always keep at least the newest N even if all are "old".
        if (keep.isEmpty() && ranked.isNotEmpty()) {
            keep.addAll(ranked.take(PLANNED_REPORT_MAX_KEEP))
        }

        var removed = 0
        for (id in ids) {
            if (id in keep) continue
            localStore.removeJsonCache(
                MovitCacheKeys.REPORTS_STORE,
                MovitCacheKeys.plannedWorkoutReportKey(id),
            )
            removed++
        }
        if (removed > 0) {
            localStore.writeJsonCache(
                MovitCacheKeys.REPORTS_STORE,
                MovitCacheKeys.PLANNED_WORKOUT_REPORTS_INDEX,
                MovitJson.encodeToString(ListSerializer(String.serializer()), keep.toList()),
            )
        }
        return removed
    }

    fun purgeMetricsLru(): Int {
        val metrics = localStore.listJsonCacheEntriesWithTimestamps(MovitCacheKeys.REPORTS_STORE)
            .filter { it.key.startsWith(METRICS_PREFIX) || it.key.startsWith(EXERCISE_METRICS_PREFIX) }
        if (metrics.size <= METRICS_LRU_MAX) return 0
        val sorted = metrics.sortedBy { it.updatedAtEpochMs }
        val toRemove = sorted.take(metrics.size - METRICS_LRU_MAX)
        toRemove.forEach { localStore.removeJsonCache(MovitCacheKeys.REPORTS_STORE, it.key) }
        return toRemove.size
    }

    fun protectedOfflineWeekNumbers(): Set<Int> {
        val bindings = platform?.invoke() ?: return emptySet()
        return WeekOfflinePackPrefetcher.protectedOfflineWeekNumbers(bindings)
    }

    fun purgeStaleEffectivePlans(activeWeekNumber: Int? = null): Int {
        val cutoff = nowEpochMs() - EFFECTIVE_PLAN_TTL_MS
        val offlineProtectedWeeks = protectedOfflineWeekNumbers()
        val entries = localStore.listJsonCacheEntriesWithTimestamps(MovitCacheKeys.SESSION_STORE)
        var removed = 0
        for (entry in entries) {
            if (!entry.key.startsWith(EFFECTIVE_PLAN_PREFIX)) continue
            val week = parseEffectivePlanWeek(entry.key)
            if (week != null && week in offlineProtectedWeeks) continue
            val outsideActiveWindow =
                activeWeekNumber != null &&
                    week != null &&
                    kotlin.math.abs(week - activeWeekNumber) > 1
            val expiredByTtl = entry.updatedAtEpochMs > 0L && entry.updatedAtEpochMs < cutoff
            if (outsideActiveWindow || expiredByTtl) {
                localStore.removeJsonCache(MovitCacheKeys.SESSION_STORE, entry.key)
                removed++
            }
        }
        return removed
    }

    private fun removePostTrainingReportArtifacts(reportId: String) {
        val sessionExerciseKey = runCatching {
            localStore.readJsonCache(
                MovitCacheKeys.REPORTS_STORE,
                MovitCacheKeys.reportSessionExerciseKey(reportId),
            )?.let { raw ->
                MovitJson.decodeFromString(ReportSessionExerciseRef.serializer(), raw).sessionExerciseKey
            }
        }.getOrNull()

        localStore.removeJsonCache(
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.postTrainingReportKey(reportId),
        )
        localStore.removeJsonCache(
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.sessionReportKey(reportId),
        )
        localStore.removeJsonCache(
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.reportSessionExerciseKey(reportId),
        )

        if (sessionExerciseKey.isNullOrBlank()) return
        val indexKey = MovitCacheKeys.exerciseSetReportsIndexKey(sessionExerciseKey)
        val raw = localStore.readJsonCache(MovitCacheKeys.REPORTS_STORE, indexKey) ?: return
        val index = runCatching {
            MovitJson.decodeFromString(ExerciseSetReportsIndex.serializer(), raw)
        }.getOrDefault(ExerciseSetReportsIndex())
        val updated = index.reportsBySet.filterValues { it != reportId }
        if (updated.isEmpty()) {
            localStore.removeJsonCache(MovitCacheKeys.REPORTS_STORE, indexKey)
        } else {
            localStore.writeJsonCache(
                MovitCacheKeys.REPORTS_STORE,
                indexKey,
                MovitJson.encodeToString(
                    ExerciseSetReportsIndex.serializer(),
                    ExerciseSetReportsIndex(updated),
                ),
            )
        }
    }

    /** Key shape: `effective_plan_{userProgramId}_{week}_{day}` — week is second-to-last segment. */
    private fun parseEffectivePlanWeek(key: String): Int? {
        val parts = key.removePrefix(EFFECTIVE_PLAN_PREFIX).split('_')
        if (parts.size < 2) return null
        return parts[parts.size - 2].toIntOrNull()
    }

    @Serializable
    private data class ExerciseSetReportsIndex(
        val reportsBySet: Map<Int, String> = emptyMap(),
    )

    @Serializable
    private data class ReportSessionExerciseRef(
        val sessionExerciseKey: String,
    )

    companion object {
        private const val MS_PER_DAY = 24L * 60 * 60 * 1000
        const val POST_TRAINING_TTL_DAYS = 60
        const val PLANNED_REPORT_TTL_DAYS = 180 // ~6 months
        const val PLANNED_REPORT_MAX_KEEP = 90
        const val METRICS_LRU_MAX = 50
        const val EFFECTIVE_PLAN_TTL_DAYS = 21

        private val POST_TRAINING_TTL_MS = POST_TRAINING_TTL_DAYS * MS_PER_DAY
        private val PLANNED_REPORT_TTL_MS = PLANNED_REPORT_TTL_DAYS * MS_PER_DAY
        private val EFFECTIVE_PLAN_TTL_MS = EFFECTIVE_PLAN_TTL_DAYS * MS_PER_DAY

        private const val POST_TRAINING_PREFIX = "post_training_report_"
        private const val PLANNED_REPORT_PREFIX = "planned_workout_report_"
        private const val METRICS_PREFIX = "reports_metrics_"
        private const val EXERCISE_METRICS_PREFIX = "reports_exercise_"
        private const val EFFECTIVE_PLAN_PREFIX = "effective_plan_"
    }
}
