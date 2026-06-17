package com.movit.core.data.repository

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.network.MovitJson
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.MetricsApiResponse
import com.movit.core.network.dto.PlannedWorkoutCompleteRequestDto
import com.movit.core.network.dto.PlannedWorkoutReportExportDto
import com.movit.core.network.dto.ReportDashboardSummaryDto
import com.movit.core.network.dto.ReportsDashboardApiResponse
import com.movit.core.network.dto.WorkoutExecutionUploadRequestDto
import com.movit.shared.AppResult
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

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

    fun readCachedExerciseMetrics(exerciseSlug: String): MetricsApiResponse? =
        MovitCachePolicy.readJson(
            localStore(),
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.reportsExerciseKey(exerciseSlug),
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
     * Mirrors legacy [com.movit.storage.SyncManager]: only backfill reports
     * that are not already present locally (pending offline completions win).
     */
    open fun hydrateFromSync(exports: List<PlannedWorkoutReportExportDto>) {
        if (exports.isEmpty()) return
        val store = localStore()
        val index = readPlannedWorkoutReportIndex(store).toMutableSet()
        for (export in exports) {
            val workoutId = export.plannedWorkoutId.ifBlank { export.id }
            if (workoutId.isBlank()) continue
            if (readCachedPlannedWorkoutReport(workoutId) != null) continue
            writePlannedWorkoutReport(store, workoutId, export.copy(plannedWorkoutId = workoutId))
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
                com.movit.core.network.dto.ExerciseMetricsSummaryDto(
                    exerciseSlug = slug,
                    exerciseName = summary?.exerciseName ?: slug.replace('-', ' '),
                    averageFormScore = formScore,
                    averageCompletionRate = summary?.averageCompletionRate,
                    setsCompleted = (summary?.setsCompleted ?: 0) + 1,
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
            return AppResult.Failure("Reports require a Pro subscription.")
        }

        return MovitCachePolicy.syncWithFallback(
            cached = cached,
            authRequired = true,
            hasAuth = auth != null,
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

    suspend fun syncExerciseMetrics(
        programId: String,
        exerciseSlug: String,
    ): AppResult<MetricsApiResponse> {
        val bindings = platform()
        val cacheKey = MovitCacheKeys.reportsExerciseKey(exerciseSlug)
        val cached = readCachedExerciseMetrics(exerciseSlug)
        val auth = bindings.authHeader()
            ?: return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure("Sign in to load report details.")

        if (!bindings.isProUser()) {
            return AppResult.Failure("Reports require a Pro subscription.")
        }

        return MovitCachePolicy.syncWithFallback(
            cached = cached,
            authRequired = true,
            hasAuth = auth != null,
            noAuthMessage = "Sign in to load report details.",
            fetch = {
                api.fetchExerciseMetrics(
                    programId = programId,
                    exerciseSlug = exerciseSlug,
                    authorization = auth,
                )
            },
            isSuccess = { it.success },
            errorMessage = { it.error ?: "Exercise report sync failed." },
            persist = { response ->
                MovitCachePolicy.writeJson(
                    localStore(),
                    MovitCacheKeys.REPORTS_STORE,
                    cacheKey,
                    response,
                    MetricsApiResponse.serializer(),
                )
            },
            failureMessage = { it.message ?: "Exercise report sync failed." },
        )
    }

    private fun patchDashboardFromCompletion(
        request: PlannedWorkoutCompleteRequestDto,
        programId: String?,
    ) {
        val store = localStore()
        val current = readCachedDashboard() ?: ReportsDashboardApiResponse(success = true)
        val summary = current.summary ?: ReportDashboardSummaryDto(programId = programId)
        val repsDelta = request.totalReps ?: 0
        val durationDelta = request.totalDurationMs?.toLong() ?: 0L
        val updatedSummary = summary.copy(
            programId = programId ?: summary.programId,
            daysTrained = (summary.daysTrained ?: 0) + 1,
            totalReps = (summary.totalReps ?: 0) + repsDelta,
            totalTrainingTime = (summary.totalTrainingTime ?: 0L) + durationDelta,
            overallFormScore = request.avgFormScore ?: summary.overallFormScore,
            currentStreak = ((summary.currentStreak ?: 0) + 1).takeIf { repsDelta > 0 }
                ?: summary.currentStreak,
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

    private fun formatEpochMillis(epochMs: Long?): String =
        epochMs?.toString().orEmpty()
}
