package com.movit.feature.reports

import com.movit.core.data.MovitData
import com.movit.core.data.cache.CacheState
import com.movit.core.data.cache.staleWhileRevalidate
import com.movit.resources.strings.ReportsStrings
import com.movit.shared.AppResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class SharedReportsRepository : ReportsRepository {

    override fun observeDashboard(): Flow<CacheState<ReportsDashboardUi>> {
        if (!MovitData.isInstalled) {
            return flowOf(CacheState.Error(DATA_LAYER_NOT_INSTALLED))
        }

        val platform = MovitData.requirePlatform()
        if (!platform.isProUser()) {
            return flow {
                val strings = ReportsStrings.load(platform.preferredLanguage())
                emit(
                    CacheState.Fresh(
                        readLocalDashboard(strings) ?: ReportsDashboardUi(hubState = ReportsHubState.Locked),
                    ),
                )
            }
        }

        return staleWhileRevalidate(
            screenId = "reports",
            readCached = {
                val strings = ReportsStrings.load(platform.preferredLanguage())
                readLocalDashboard(strings)
            },
            syncFresh = {
                val strings = ReportsStrings.load(platform.preferredLanguage())
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
            val language = platform.preferredLanguage()
            val strings = ReportsStrings.load(language)
            if (!platform.isProUser()) {
                return@coroutineScope AppResult.Success(
                    readLocalDashboard(strings) ?: ReportsDashboardUi(hubState = ReportsHubState.Locked),
                )
            }

            val programId = MovitData.home.readCached()?.trainMode?.activeProgram?.id

            if (!refresh) {
                readLocalDashboard(strings)?.let { cached ->
                    launch { MovitData.reports.syncDashboard(programId = programId) }
                    return@coroutineScope AppResult.Success(cached)
                }
            }

            return@coroutineScope when (val result = MovitData.reports.syncDashboard(programId = programId)) {
                is AppResult.Success -> mapDashboard(result.value, strings)
                is AppResult.Failure -> {
                    readLocalDashboard(strings)?.let { AppResult.Success(it) }
                        ?: AppResult.Failure(result.message)
                }
            }
        }

    private suspend fun readLocalDashboard(strings: ReportsStrings): ReportsDashboardUi? {
        MovitData.reports.readCachedDashboard()?.let { cached ->
            return if (ReportsApiMapper.hasTrainingData(cached)) {
                ReportsApiMapper.map(cached, strings)
            } else {
                ReportsDashboardUi(hubState = ReportsHubState.Empty)
            }
        }
        return if (MovitData.reports.hasLocalTrainingActivity()) {
            ReportsDashboardUi(hubState = ReportsHubState.Empty)
        } else {
            null
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
