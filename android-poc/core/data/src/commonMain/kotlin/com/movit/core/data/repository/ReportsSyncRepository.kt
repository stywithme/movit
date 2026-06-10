package com.movit.core.data.repository

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.MetricsApiResponse
import com.movit.core.network.dto.ReportsDashboardApiResponse
import com.movit.shared.AppResult

class ReportsSyncRepository(
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
}
