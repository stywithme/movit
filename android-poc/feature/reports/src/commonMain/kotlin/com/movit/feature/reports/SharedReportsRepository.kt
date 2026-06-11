package com.movit.feature.reports

import com.movit.core.data.MovitData
import com.movit.core.data.cache.CacheState
import com.movit.core.data.cache.staleWhileRevalidate
import com.movit.resources.strings.ReportsStrings
import com.movit.shared.AppResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class SharedReportsRepository : ReportsRepository {

    override fun observeDashboard(): Flow<CacheState<ReportsDashboardUi>> {
        if (!MovitData.isInstalled) {
            return flowOf(CacheState.Error(DATA_LAYER_NOT_INSTALLED))
        }

        val platform = MovitData.requirePlatform()
        if (!platform.isProUser()) {
            return flowOf(
                CacheState.Fresh(ReportsDashboardUi(hubState = ReportsHubState.Locked)),
            )
        }

        return staleWhileRevalidate(
            screenId = "reports",
            readCached = {
                val language = platform.preferredLanguage()
                val strings = ReportsStrings.load(language)
                MovitData.reports.readCachedDashboard()?.let { cached ->
                    if (ReportsApiMapper.hasTrainingData(cached)) {
                        ReportsApiMapper.map(cached, strings)
                    } else {
                        ReportsDashboardUi(hubState = ReportsHubState.Empty)
                    }
                } ?: if (MovitData.reports.hasLocalTrainingActivity()) {
                    ReportsDashboardUi(hubState = ReportsHubState.Empty)
                } else {
                    null
                }
            },
            syncFresh = {
                val language = platform.preferredLanguage()
                val strings = ReportsStrings.load(language)
                val programId = MovitData.home.readCached()?.trainMode?.activeProgram?.id
                when (val result = MovitData.reports.syncDashboard(programId = programId)) {
                    is AppResult.Success -> AppResult.Success(ReportsApiMapper.map(result.value, strings))
                    is AppResult.Failure -> result
                }
            },
        )
    }

    override suspend fun getReportsDashboard(refresh: Boolean): AppResult<ReportsDashboardUi> =
        coroutineScope {
            if (!MovitData.isInstalled) {
                return@coroutineScope AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
            }

            val platform = MovitData.requirePlatform()
            if (!platform.isProUser()) {
                return@coroutineScope AppResult.Success(ReportsDashboardUi(hubState = ReportsHubState.Locked))
            }

            val language = platform.preferredLanguage()
            val strings = ReportsStrings.load(language)
            val programId = MovitData.home.readCached()?.trainMode?.activeProgram?.id

            if (!refresh) {
                val cached = MovitData.reports.readCachedDashboard()
                if (cached != null) {
                    launch { MovitData.reports.syncDashboard(programId = programId) }
                    return@coroutineScope mapDashboard(cached, strings)
                }
                if (MovitData.reports.hasLocalTrainingActivity()) {
                    launch { MovitData.reports.syncDashboard(programId = programId) }
                    return@coroutineScope AppResult.Success(
                        ReportsDashboardUi(hubState = ReportsHubState.Empty),
                    )
                }
            }

            return@coroutineScope when (val result = MovitData.reports.syncDashboard(programId = programId)) {
                is AppResult.Success -> mapDashboard(result.value, strings)
                is AppResult.Failure -> {
                    val cached = MovitData.reports.readCachedDashboard()
                    when {
                        cached != null && ReportsApiMapper.hasTrainingData(cached) ->
                            mapDashboard(cached, strings)
                        cached != null ->
                            AppResult.Success(ReportsDashboardUi(hubState = ReportsHubState.Empty))
                        MovitData.reports.hasLocalTrainingActivity() ->
                            AppResult.Success(ReportsDashboardUi(hubState = ReportsHubState.Empty))
                        else -> AppResult.Failure(result.message)
                    }
                }
            }
        }

    private suspend fun mapDashboard(
        dashboard: com.movit.core.network.dto.ReportsDashboardApiResponse,
        strings: ReportsStrings,
    ): AppResult<ReportsDashboardUi> =
        AppResult.Success(ReportsApiMapper.map(dashboard, strings))

    private companion object {
        const val DATA_LAYER_NOT_INSTALLED = "App data layer is not installed."
    }
}
