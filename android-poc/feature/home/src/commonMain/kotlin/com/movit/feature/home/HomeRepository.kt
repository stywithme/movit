package com.movit.feature.home

import com.movit.core.data.cache.CacheState
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface HomeRepository {
    suspend fun getHomeDashboard(): AppResult<HomeDashboardUi>

    fun observeDashboard(): Flow<CacheState<HomeDashboardUi>> {
        val self = this
        return flow {
            when (val result = self.getHomeDashboard()) {
                is AppResult.Success -> emit(CacheState.Fresh(result.value))
                is AppResult.Failure -> emit(CacheState.Error(result.message))
            }
        }
    }
}
