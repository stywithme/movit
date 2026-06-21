package com.movit.feature.train

import com.movit.core.data.cache.CacheState
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface TrainRepository {
    suspend fun getTrainDashboard(): AppResult<TrainDashboardUi>

    fun observeDashboard(): Flow<CacheState<TrainDashboardUi>> {
        val self = this
        return flow {
            when (val result = self.getTrainDashboard()) {
                is AppResult.Success -> emit(CacheState.Fresh(result.value))
                is AppResult.Failure -> emit(CacheState.Error(result.message))
            }
        }
    }
}
