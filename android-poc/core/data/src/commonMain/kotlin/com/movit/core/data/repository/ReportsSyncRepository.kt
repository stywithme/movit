package com.movit.core.data.repository

import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.network.MovitJson
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.MetricsApiResponse
import com.movit.core.network.dto.ReportsDashboardApiResponse
import com.movit.shared.AppResult

class ReportsSyncRepository(
    private val api: MovitMobileApi,
    private val platform: () -> MovitPlatformBindings,
) {
    fun readCachedDashboard(): ReportsDashboardApiResponse? {
        val raw = platform().readCache(MovitCacheKeys.REPORTS_STORE, MovitCacheKeys.REPORTS_DASHBOARD)
            ?: return null
        return runCatching {
            MovitJson.decodeFromString<ReportsDashboardApiResponse>(raw)
        }.getOrNull()
    }

    fun readCachedExerciseMetrics(exerciseSlug: String): MetricsApiResponse? {
        val raw = platform().readCache(
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.reportsExerciseKey(exerciseSlug),
        ) ?: return null
        return runCatching { MovitJson.decodeFromString<MetricsApiResponse>(raw) }.getOrNull()
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

        val response = api.fetchReportsDashboard(
            programId = programId,
            period = period,
            source = source,
            authorization = auth,
        ).getOrElse { error ->
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure(error.message ?: "Reports sync failed.")
        }

        if (!response.success) {
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure(response.error ?: "Reports sync failed.")
        }

        bindings.writeCache(
            MovitCacheKeys.REPORTS_STORE,
            MovitCacheKeys.REPORTS_DASHBOARD,
            MovitJson.encodeToString(ReportsDashboardApiResponse.serializer(), response),
        )

        return AppResult.Success(response)
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

        val response = api.fetchExerciseMetrics(
            programId = programId,
            exerciseSlug = exerciseSlug,
            authorization = auth,
        ).getOrElse { error ->
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure(error.message ?: "Exercise report sync failed.")
        }

        if (!response.success) {
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure(response.error ?: "Exercise report sync failed.")
        }

        bindings.writeCache(
            MovitCacheKeys.REPORTS_STORE,
            cacheKey,
            MovitJson.encodeToString(MetricsApiResponse.serializer(), response),
        )

        return AppResult.Success(response)
    }
}
