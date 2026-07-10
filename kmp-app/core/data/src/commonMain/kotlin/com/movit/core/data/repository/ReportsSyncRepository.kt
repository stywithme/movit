package com.movit.core.data.repository

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.outbox.OutboxPendingScan
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.network.MovitClock
import com.movit.core.network.MovitJson
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.MetricsApiResponse
import com.movit.core.network.dto.MetricsQuery
import com.movit.core.network.dto.MetricsScope
import com.movit.core.network.dto.PlannedWorkoutCompleteRequestDto
import com.movit.core.network.dto.PlannedWorkoutReportExportDto
import com.movit.core.network.dto.ReportDashboardSummaryDto
import com.movit.core.network.dto.ReportsDashboardApiResponse
import com.movit.core.network.dto.WorkoutExecutionUploadRequestDto
import com.movit.shared.AppResult
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

open class ReportsSyncRepository(
    private val api: MovitMobileApi,
    private val platform: () -> MovitPlatformBindings,
    private val localStore: () -> MovitLocalStore,
) {
    fun readCachedDashboard(): ReportsDashboardApiResponse? =
        MovitCachePolicy.readJson(
            localStore(),
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.REPORTS_DASHBOARD,
            ReportsDashboardApiResponse.serializer(),
        )

    fun readCachedExerciseMetrics(exerciseSlug: String, programId: String? = null): MetricsApiResponse? {
        if (!programId.isNullOrBlank()) {
            readCachedMetrics(
                MetricsQuery(
                    programId = programId,
                    scope = MetricsScope.Exercise,
                    exerciseSlug = exerciseSlug,
                ),
            )?.let { return it }
        }
        return MovitCachePolicy.readJson(
            localStore(),
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.reportsExerciseKey(exerciseSlug),
            MetricsApiResponse.serializer(),
        )
    }

    fun readCachedMetrics(query: MetricsQuery): MetricsApiResponse? =
        MovitCachePolicy.readJson(
            localStore(),
            MovitCacheKeys.REPORTS_STORE,
            metricsCacheKey(query),
            MetricsApiResponse.serializer(),
        )

    fun readCachedPlannedWorkoutReport(plannedWorkoutId: String): PlannedWorkoutReportExportDto? {
        if (plannedWorkoutId.isBlank()) return null
        return MovitCachePolicy.readJson(
            localStore(),
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.plannedWorkoutReportKey(plannedWorkoutId),
            PlannedWorkoutReportExportDto.serializer(),
        )
    }

    fun readAllCachedPlannedWorkoutReports(): List<PlannedWorkoutReportExportDto> {
        val store = localStore()
        val index = readPlannedWorkoutReportIndex(store)
        return index.mapNotNull { readCachedPlannedWorkoutReport(it) }
    }

    fun hasLocalTrainingActivity(): Boolean =
        readCachedDashboard()?.let(::dashboardHasTrainingData) == true ||
            readAllCachedPlannedWorkoutReports().isNotEmpty()

    /**
     * PR-6 field merge: pending complete protects local; server wins id/completedAt/metrics;
     * rich local [PlannedWorkoutReportExportDto.report] survives summary sync payloads.
     */
    open fun hydrateFromSync(
        exports: List<PlannedWorkoutReportExportDto>,
        pendingPlannedWorkoutIds: Set<String> = emptySet(),
    ) {
        if (exports.isEmpty()) return
        val store = localStore()
        val index = readPlannedWorkoutReportIndex(store).toMutableSet()
        for (export in exports) {
            val workoutId = export.plannedWorkoutId.ifBlank { export.id }
            if (workoutId.isBlank()) continue
            if (workoutId in pendingPlannedWorkoutIds) continue

            val existing = readCachedPlannedWorkoutReport(workoutId)
            val merged = if (existing == null) {
                export.copy(plannedWorkoutId = workoutId)
            } else {
                mergeReportFromServer(existing, export, workoutId)
            }
            writePlannedWorkoutReport(store, workoutId, merged)
            index += workoutId
        }
        writePlannedWorkoutReportIndex(store, index)
    }

    fun recordPendingPlannedWorkoutCompletion(
        workoutId: String,
        request: PlannedWorkoutCompleteRequestDto,
        programId: String?,
        weekNumber: Int = 0,
        dayNumber: Int = 0,
    ) {
        if (workoutId.isBlank()) return
        val export = PlannedWorkoutReportExportDto(
            id = workoutId,
            plannedWorkoutId = workoutId,
            programId = programId.orEmpty(),
            weekNumber = weekNumber,
            dayNumber = dayNumber,
            completedAt = formatEpochMillis(request.completedAt),
            status = "completed",
            totalDurationMs = request.totalDurationMs ?: 0,
            totalExercises = request.totalExercises ?: 0,
            totalSets = request.totalSets ?: 0,
            completedSets = request.completedSets ?: 0,
            totalReps = request.totalReps ?: 0,
            avgAccuracy = (request.avgAccuracy ?: 0f).toDouble(),
            avgFormScore = (request.avgFormScore ?: 0f).toDouble(),
            rpe = request.rpe,
            report = request.report,
        )
        upsertLocalPlannedWorkoutReport(export)
        patchDashboardFromCompletion(request, programId)
    }

    fun patchExerciseMetricsFromUpload(request: WorkoutExecutionUploadRequestDto) {
        val slug = request.exerciseId
        if (slug.isBlank()) return
        val store = localStore()
        val existing = readCachedExerciseMetrics(slug)
        val formScore = request.executionMetrics.avgFormScore
        val updated = MetricsApiResponse(
            success = true,
            scope = "exercise",
            summary = (existing?.summary).let { summary ->
                val prevSets = summary?.setsCompleted ?: 0
                com.movit.core.network.dto.ExerciseMetricsSummaryDto(
                    exerciseSlug = slug,
                    exerciseName = summary?.exerciseName ?: slug.replace('-', ' '),
                    averageFormScore = computeWeightedAverageFormScore(
                        previousAverage = summary?.averageFormScore,
                        previousSetsCompleted = prevSets,
                        newSetFormScore = formScore,
                    ),
                    averageCompletionRate = summary?.averageCompletionRate,
                    setsCompleted = prevSets + 1,
                    setsPlanned = summary?.setsPlanned,
                    totalReps = (summary?.totalReps ?: 0) + request.countedReps,
                    totalDurationMs = (summary?.totalDurationMs ?: 0L) + request.durationMs,
                    bestSetNumber = summary?.bestSetNumber,
                    dropOffRate = summary?.dropOffRate,
                    formRating = summary?.formRating,
                    jointBreakdown = summary?.jointBreakdown,
                    sets = summary?.sets,
                )
            },
            insights = existing?.insights,
        )
        MovitCachePolicy.writeJson(
            store,
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.reportsExerciseKey(slug),
            updated,
            MetricsApiResponse.serializer(),
        )
    }

    private fun upsertLocalPlannedWorkoutReport(report: PlannedWorkoutReportExportDto) {
        val workoutId = report.plannedWorkoutId.ifBlank { report.id }
        if (workoutId.isBlank()) return
        val store = localStore()
        writePlannedWorkoutReport(store, workoutId, report.copy(plannedWorkoutId = workoutId))
        val index = readPlannedWorkoutReportIndex(store).toMutableSet()
        index += workoutId
        writePlannedWorkoutReportIndex(store, index)
    }

    suspend fun syncDashboard(
        programId: String? = null,
        period: String = "all",
        source: String = "all",
    ): AppResult<ReportsDashboardApiResponse> {
        val bindings = platform()
        val cached = readCachedDashboard()
        val auth = bindings.authHeader()
            ?: return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure("Sign in to load your reports.")

        if (!bindings.isProUser()) {
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure("Reports require a Pro subscription.")
        }

        return MovitCachePolicy.syncWithFallback(
            cached = cached,
            authRequired = true,
            hasAuth = true,
            noAuthMessage = "Sign in to load your reports.",
            fetch = {
                api.fetchReportsDashboard(
                    programId = programId,
                    period = period,
                    source = source,
                    authorization = auth,
                )
            },
            isSuccess = { it.success },
            errorMessage = { it.error ?: "Reports sync failed." },
            persist = { response ->
                MovitCachePolicy.writeJson(
                    localStore(),
                    MovitCacheKeys.REPORTS_STORE,
                    MovitCacheKeys.REPORTS_DASHBOARD,
                    response,
                    ReportsDashboardApiResponse.serializer(),
                )
            },
            failureMessage = { it.message ?: "Reports sync failed." },
        )
    }

    suspend fun syncMetrics(query: MetricsQuery): AppResult<MetricsApiResponse> {
        val bindings = platform()
        val cacheKey = metricsCacheKey(query)
        val cached = readCachedMetrics(query)
        val auth = bindings.authHeader()
            ?: return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure("Sign in to load report details.")

        if (!bindings.isProUser()) {
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure("Reports require a Pro subscription.")
        }

        return MovitCachePolicy.syncWithFallback(
            cached = cached,
            authRequired = true,
            hasAuth = true,
            noAuthMessage = "Sign in to load report details.",
            fetch = { api.fetchMetrics(query, authorization = auth) },
            isSuccess = { it.success },
            errorMessage = { it.error ?: "Report metrics sync failed." },
            persist = { response ->
                MovitCachePolicy.writeJson(
                    localStore(),
                    MovitCacheKeys.REPORTS_STORE,
                    cacheKey,
                    response,
                    MetricsApiResponse.serializer(),
                )
            },
            failureMessage = { it.message ?: "Report metrics sync failed." },
        )
    }

    suspend fun syncExerciseMetrics(
        programId: String,
        exerciseSlug: String,
    ): AppResult<MetricsApiResponse> = syncMetrics(
        MetricsQuery(
            programId = programId,
            scope = MetricsScope.Exercise,
            exerciseSlug = exerciseSlug,
            includeHistory = true,
        ),
    )

    suspend fun syncProgramMetrics(programId: String): AppResult<MetricsApiResponse> =
        syncMetrics(
            MetricsQuery(
                programId = programId,
                scope = MetricsScope.Program,
                includeChildren = true,
            ),
        )

    suspend fun syncWeekMetrics(
        programId: String,
        weekNumber: Int,
        includeChildren: Boolean = true,
    ): AppResult<MetricsApiResponse> = syncMetrics(
        MetricsQuery(
            programId = programId,
            scope = MetricsScope.Week,
            weekNumber = weekNumber,
            includeChildren = includeChildren,
            includeHistory = true,
        ),
    )

    suspend fun syncDayMetrics(
        programId: String,
        weekNumber: Int,
        dayNumber: Int,
        includeChildren: Boolean = true,
    ): AppResult<MetricsApiResponse> = syncMetrics(
        MetricsQuery(
            programId = programId,
            scope = MetricsScope.Day,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
            includeChildren = includeChildren,
        ),
    )

    suspend fun syncPlannedWorkoutMetrics(
        programId: String,
        plannedWorkoutId: String,
        includeChildren: Boolean = true,
    ): AppResult<MetricsApiResponse> = syncMetrics(
        MetricsQuery(
            programId = programId,
            scope = MetricsScope.PlannedWorkout,
            plannedWorkoutId = plannedWorkoutId,
            includeChildren = includeChildren,
        ),
    )

    private fun metricsCacheKey(query: MetricsQuery): String =
        MovitCacheKeys.reportsMetricsKey(
            scope = query.scope.wireValue,
            programId = query.programId,
            weekNumber = query.weekNumber,
            dayNumber = query.dayNumber,
            plannedWorkoutId = query.plannedWorkoutId,
            exerciseSlug = query.exerciseSlug,
        )

    private fun patchDashboardFromCompletion(
        request: PlannedWorkoutCompleteRequestDto,
        programId: String?,
    ) {
        val store = localStore()
        val current = readCachedDashboard() ?: ReportsDashboardApiResponse(success = true)
        val summary = current.summary ?: ReportDashboardSummaryDto(programId = programId)
        val repsDelta = request.totalReps ?: 0
        val durationDelta = request.totalDurationMs?.toLong() ?: 0L
        val trainingDays = uniqueTrainingUtcDays()
        val updatedSummary = summary.copy(
            programId = programId ?: summary.programId,
            daysTrained = trainingDays.size,
            totalReps = (summary.totalReps ?: 0) + repsDelta,
            totalTrainingTime = (summary.totalTrainingTime ?: 0L) + durationDelta,
            overallFormScore = request.avgFormScore ?: summary.overallFormScore,
            currentStreak = computeCurrentStreakFromUtcDays(trainingDays),
        )
        val patched = current.copy(
            success = true,
            summary = updatedSummary,
            exerciseBreakdown = current.exerciseBreakdown,
        )
        MovitCachePolicy.writeJson(
            store,
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.REPORTS_DASHBOARD,
            patched,
            ReportsDashboardApiResponse.serializer(),
        )
    }

    private fun dashboardHasTrainingData(dashboard: ReportsDashboardApiResponse): Boolean {
        val summary = dashboard.summary
        return (summary?.daysTrained ?: 0) > 0 ||
            (summary?.totalReps ?: 0) > 0 ||
            (summary?.totalTrainingTime ?: 0L) > 0L ||
            !dashboard.exerciseBreakdown.isNullOrEmpty()
    }

    private fun readPlannedWorkoutReportIndex(store: MovitLocalStore): Set<String> {
        val raw = store.readString(
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.PLANNED_WORKOUT_REPORTS_INDEX,
        ) ?: return emptySet()
        return runCatching {
            MovitJson.decodeFromString(ListSerializer(String.serializer()), raw).toSet()
        }.getOrDefault(emptySet())
    }

    private fun writePlannedWorkoutReportIndex(store: MovitLocalStore, ids: Set<String>) {
        store.writeString(
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.PLANNED_WORKOUT_REPORTS_INDEX,
            MovitJson.encodeToString(ListSerializer(String.serializer()), ids.sorted()),
        )
    }

    private fun writePlannedWorkoutReport(
        store: MovitLocalStore,
        workoutId: String,
        report: PlannedWorkoutReportExportDto,
    ) {
        MovitCachePolicy.writeJson(
            store,
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.plannedWorkoutReportKey(workoutId),
            report,
            PlannedWorkoutReportExportDto.serializer(),
        )
    }

    private fun uniqueTrainingUtcDays(): Set<Long> =
        readAllCachedPlannedWorkoutReports()
            .mapNotNull { completedAtToUtcDay(it.completedAt) }
            .toSet()

    private fun formatEpochMillis(epochMs: Long?): String =
        epochMs?.let(DayCustomizationLocalStore::formatEpochMsToIsoUtc).orEmpty()

    companion object {
        suspend fun pendingPlannedWorkoutIdsFromOutbox(localStore: MovitLocalStore): Set<String> =
            OutboxPendingScan.pendingPlannedWorkoutIds(OutboxPendingScan.awaitingDispatch(localStore))

        internal fun mergeReportFromServer(
            local: PlannedWorkoutReportExportDto,
            server: PlannedWorkoutReportExportDto,
            workoutId: String,
        ): PlannedWorkoutReportExportDto {
            val report = when {
                hasRichReport(server.report) -> server.report
                hasRichReport(local.report) -> local.report
                else -> server.report ?: local.report
            }
            return server.copy(
                id = server.id.ifBlank { local.id }.ifBlank { workoutId },
                plannedWorkoutId = workoutId,
                programId = server.programId.ifBlank { local.programId },
                weekNumber = server.weekNumber.takeIf { it != 0 } ?: local.weekNumber,
                dayNumber = server.dayNumber.takeIf { it != 0 } ?: local.dayNumber,
                startedAt = server.startedAt.ifBlank { local.startedAt },
                completedAt = server.completedAt.ifBlank { local.completedAt },
                status = server.status.ifBlank { local.status },
                report = report,
            )
        }

        internal fun hasRichReport(report: JsonElement?): Boolean {
            if (report == null || report is JsonNull) return false
            return report !is JsonObject || report.isNotEmpty()
        }

        internal fun completedAtToUtcDay(completedAt: String): Long? {
            if (completedAt.isBlank()) return null
            val epochMs = completedAt.toLongOrNull()
                ?: DayCustomizationLocalStore.parseIsoToEpochMs(completedAt)
                ?: return null
            return epochMs.floorDiv(86_400_000L)
        }

        internal fun computeCurrentStreakFromUtcDays(days: Set<Long>): Int {
            if (days.isEmpty()) return 0
            val today = MovitClock.nowEpochMs() / 86_400_000L
            val mostRecent = days.maxOrNull() ?: return 0
            if (mostRecent < today - 1) return 0

            var streak = 0
            var expected = mostRecent
            for (day in days.sortedDescending()) {
                when {
                    day == expected -> {
                        streak++
                        expected--
                    }
                    day < expected -> break
                }
            }
            return streak
        }
    }
}
