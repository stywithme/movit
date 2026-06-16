package com.movit.feature.library

import com.movit.core.data.cache.CacheState
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class SessionSwapCandidateUi(
    val slug: String,
    val name: String,
    val subtitle: String,
    val badge: String? = null,
    val imageUrl: String? = null,
)

interface WorkoutSessionRepository {
    suspend fun loadSession(workoutId: String): AppResult<WorkoutSessionUi>
    suspend fun loadDayContext(workoutId: String): SessionDayContext
    suspend fun saveSession(session: WorkoutSessionUi): AppResult<Unit>
    suspend fun saveFlowCustomization(workoutId: String, config: WorkoutFlowConfigUi): AppResult<Unit>
    suspend fun sessionKeyForDay(programId: String, weekNumber: Int, dayNumber: Int): String?
    suspend fun findSwapCandidates(
        query: String,
        replacingSlug: String,
    ): List<SessionSwapCandidateUi>

    suspend fun findAddExerciseCandidates(query: String): List<SessionSwapCandidateUi>

    fun observeSession(workoutId: String): Flow<CacheState<WorkoutSessionUi>> {
        val self = this
        return flow {
            when (val result = self.loadSession(workoutId)) {
                is AppResult.Success -> emit(CacheState.Fresh(result.value))
                is AppResult.Failure -> emit(CacheState.Error(result.message))
            }
        }
    }
}

expect fun defaultWorkoutSessionRepository(): WorkoutSessionRepository
