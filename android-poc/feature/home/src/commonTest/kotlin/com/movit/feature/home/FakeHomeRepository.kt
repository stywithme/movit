package com.movit.feature.home

import com.movit.core.data.cache.CacheState
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeHomeRepository(
    private val dashboard: HomeDashboardUi = MovitHomePreviewData.dashboardWithPlan,
    private val shouldFail: Boolean = false,
) : HomeRepository {

    override suspend fun getHomeDashboard(): AppResult<HomeDashboardUi> {
        return if (shouldFail) {
            AppResult.Failure("Unable to load home dashboard.")
        } else {
            AppResult.Success(dashboard)
        }
    }

    override fun observeDashboard(): Flow<CacheState<HomeDashboardUi>> = flow {
        when (val result = getHomeDashboard()) {
            is AppResult.Success -> emit(CacheState.Fresh(result.value))
            is AppResult.Failure -> emit(CacheState.Error(result.message))
        }
    }
}
