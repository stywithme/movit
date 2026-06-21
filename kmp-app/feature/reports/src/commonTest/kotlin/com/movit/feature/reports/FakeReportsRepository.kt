package com.movit.feature.reports

import com.movit.core.data.cache.CacheState
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeReportsRepository(
    private val dashboard: ReportsDashboardUi = MovitReportsPreviewData.dashboardWithData,
    private val shouldFail: Boolean = false,
) : ReportsRepository {

    override suspend fun getReportsDashboard(refresh: Boolean): AppResult<ReportsDashboardUi> {
        return if (shouldFail) {
            AppResult.Failure("Unable to load reports.")
        } else {
            AppResult.Success(dashboard)
        }
    }

    override fun observeDashboard(): Flow<CacheState<ReportsDashboardUi>> = flow {
        when (val result = getReportsDashboard(refresh = false)) {
            is AppResult.Success -> emit(CacheState.Fresh(result.value))
            is AppResult.Failure -> emit(CacheState.Error(result.message))
        }
    }
}
