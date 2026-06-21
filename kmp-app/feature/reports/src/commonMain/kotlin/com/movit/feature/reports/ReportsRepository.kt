package com.movit.feature.reports

import com.movit.core.data.cache.CacheState
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface ReportsRepository {
    suspend fun getReportsDashboard(refresh: Boolean = false): AppResult<ReportsDashboardUi>

    fun observeDashboard(): Flow<CacheState<ReportsDashboardUi>> {
        val self = this
        return flow {
            when (val result = self.getReportsDashboard(refresh = false)) {
                is AppResult.Success -> emit(CacheState.Fresh(result.value))
                is AppResult.Failure -> emit(CacheState.Error(result.message))
            }
        }
    }
}
