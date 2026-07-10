package com.movit.feature.train

import com.movit.core.data.cache.CacheState
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeTrainRepository(
    private val dashboard: TrainDashboardUi = MovitTrainPreviewData.activePlan,
    private val shouldFail: Boolean = false,
    private val freshDashboard: TrainDashboardUi? = null,
) : TrainRepository {

    override suspend fun getTrainDashboard(): AppResult<TrainDashboardUi> {
        return if (shouldFail) {
            AppResult.Failure("Unable to load training dashboard.")
        } else {
            AppResult.Success(freshDashboard ?: dashboard)
        }
    }

    override fun observeDashboard(): Flow<CacheState<TrainDashboardUi>> = flow {
        when (val result = getTrainDashboard()) {
            is AppResult.Success -> emit(CacheState.Fresh(result.value))
            is AppResult.Failure -> emit(CacheState.Error(result.message))
        }
    }
}
