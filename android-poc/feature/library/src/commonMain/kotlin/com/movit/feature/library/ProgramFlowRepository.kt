package com.movit.feature.library

import com.movit.core.data.cache.CacheState
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface ProgramFlowRepository {
    suspend fun loadPrograms(): AppResult<List<ProgramListItemUi>>
    suspend fun loadWeekPlan(programId: String, weekNumber: Int): AppResult<ProgramWeekPlanUi>
    suspend fun loadWeeklyReport(programId: String, weekNumber: Int): AppResult<WeeklyReportUi>

    fun observePrograms(): Flow<CacheState<List<ProgramListItemUi>>> {
        val self = this
        return flow {
            when (val result = self.loadPrograms()) {
                is AppResult.Success -> emit(CacheState.Fresh(result.value))
                is AppResult.Failure -> emit(CacheState.Error(result.message))
            }
        }
    }

    fun observeWeekPlan(programId: String, weekNumber: Int): Flow<CacheState<ProgramWeekPlanUi>> {
        val self = this
        return flow {
            when (val result = self.loadWeekPlan(programId, weekNumber)) {
                is AppResult.Success -> emit(CacheState.Fresh(result.value))
                is AppResult.Failure -> emit(CacheState.Error(result.message))
            }
        }
    }
}

fun defaultProgramFlowRepository(): ProgramFlowRepository = SharedProgramFlowRepository()
