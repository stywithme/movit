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

class DefaultWorkoutSessionRepository(
    private val libraryRepository: LibraryRepository = defaultLibraryRepository(),
) : WorkoutSessionRepository {
    override suspend fun loadSession(workoutId: String): AppResult<WorkoutSessionUi> {
        return when {
            workoutId == "preview" -> AppResult.Success(WorkoutSessionPreviewData.preview)
            WorkoutSessionKeys.parse(workoutId) != null -> {
                AppResult.Failure("Session bridge not installed.")
            }
            else -> buildExploreFallback(workoutId)
        }
    }

    override suspend fun loadDayContext(workoutId: String): SessionDayContext = SessionDayContext()

    override suspend fun saveFlowCustomization(
        workoutId: String,
        config: WorkoutFlowConfigUi,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun sessionKeyForDay(
        programId: String,
        weekNumber: Int,
        dayNumber: Int,
    ): String? = null

    override suspend fun saveSession(session: WorkoutSessionUi): AppResult<Unit> {
        return if (session.context != null) {
            AppResult.Failure("Session bridge not installed.")
        } else {
            AppResult.Success(Unit)
        }
    }

    override suspend fun findSwapCandidates(
        query: String,
        replacingSlug: String,
    ): List<SessionSwapCandidateUi> = SessionSwapPreviewData.candidates(query, replacingSlug)

    override suspend fun findAddExerciseCandidates(query: String): List<SessionSwapCandidateUi> =
        SessionSwapPreviewData.candidates(query, replacingSlug = "")

    private suspend fun buildExploreFallback(workoutId: String): AppResult<WorkoutSessionUi> {
        val item = libraryRepository.findItem(workoutId)
            ?: return AppResult.Failure("Workout not found.")
        return AppResult.Success(
            WorkoutSessionUi(
                id = item.id,
                title = item.title,
                subtitle = item.subtitle,
                exerciseCount = 1,
                durationLabel = item.metadata.firstOrNull { it.contains("min") } ?: "~30m",
                setCount = 3,
                sections = listOf(
                    WorkoutSessionSectionUi(
                        title = "Main workout",
                        phaseRole = "MAIN",
                        items = listOf(
                            WorkoutSessionBlockUi.Exercise(
                                id = item.id,
                                exerciseSlug = item.id,
                                index = 1,
                                name = item.title,
                                category = item.subtitle,
                                setsLabel = "3 × 12",
                                sets = 3,
                                reps = 12,
                                restLabel = "60s rest",
                                restSeconds = 60,
                                phaseRole = "MAIN",
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}

expect fun defaultWorkoutSessionRepository(): WorkoutSessionRepository
