package com.movit.feature.library

import com.movit.core.data.MovitData
import com.movit.resources.strings.SessionStrings
import com.movit.shared.AppResult

class SharedWorkoutSessionRepository(
    private val fallback: WorkoutSessionRepository = DefaultWorkoutSessionRepository(),
) : WorkoutSessionRepository {

    override suspend fun loadSession(workoutId: String): AppResult<WorkoutSessionUi> {
        if (workoutId == "preview") {
            return AppResult.Success(WorkoutSessionPreviewData.preview)
        }

        val parsed = WorkoutSessionKeys.parse(workoutId)
        if (parsed == null) {
            return fallback.loadSession(workoutId)
        }

        if (!MovitData.isInstalled) {
            return AppResult.Failure(SessionStrings.load("en").dataNotInstalled)
        }

        val platform = MovitData.requirePlatform()
        val language = platform.preferredLanguage()
        val strings = SessionStrings.load(language)
        val userProgramId = platform.activeUserProgramId()
            ?: return AppResult.Failure(strings.noEnrollment)
        val explore = MovitData.explore.readCached()
        val (exerciseBySlug, exerciseById) = WorkoutSessionApiMapper.buildExerciseCatalog(explore, language)

        val planResult = MovitData.workoutSession.syncEffectivePlan(
            userProgramId = userProgramId,
            weekNumber = parsed.weekNumber,
            dayNumber = parsed.dayNumber,
        )

        return when (planResult) {
            is AppResult.Success -> {
                val session = WorkoutSessionApiMapper.mapSession(
                    parsed = parsed,
                    plan = planResult.value,
                    language = language,
                    strings = strings,
                    exerciseBySlug = exerciseBySlug,
                    exerciseById = exerciseById,
                )
                if (session != null) {
                    AppResult.Success(session)
                } else {
                    AppResult.Failure(strings.workoutNotInPlan)
                }
            }
            is AppResult.Failure -> {
                val cached = MovitData.workoutSession.readCachedEffectivePlan(
                    userProgramId = userProgramId,
                    weekNumber = parsed.weekNumber,
                    dayNumber = parsed.dayNumber,
                )
                val session = cached?.let {
                    WorkoutSessionApiMapper.mapSession(
                        parsed = parsed,
                        plan = it,
                        language = language,
                        strings = strings,
                        exerciseBySlug = exerciseBySlug,
                        exerciseById = exerciseById,
                    )
                }
                if (session != null) {
                    AppResult.Success(session)
                } else {
                    AppResult.Failure(planResult.message)
                }
            }
        }
    }

    override suspend fun saveSession(session: WorkoutSessionUi): AppResult<Unit> {
        val context = session.context
            ?: return fallback.saveSession(session)

        if (!MovitData.isInstalled) {
            return AppResult.Failure(SessionStrings.load("en").dataNotInstalled)
        }

        val platform = MovitData.requirePlatform()
        val userProgramId = platform.activeUserProgramId()
            ?: return AppResult.Failure(SessionStrings.load(platform.preferredLanguage()).noEnrollment)

        val plan = MovitData.workoutSession.readCachedEffectivePlan(
            userProgramId = userProgramId,
            weekNumber = context.weekNumber,
            dayNumber = context.dayNumber,
        ) ?: when (
            val sync = MovitData.workoutSession.syncEffectivePlan(
                userProgramId = userProgramId,
                weekNumber = context.weekNumber,
                dayNumber = context.dayNumber,
            )
        ) {
            is AppResult.Success -> sync.value
            is AppResult.Failure -> return AppResult.Failure(sync.message)
        }

        val language = platform.preferredLanguage()
        val explore = MovitData.explore.readCached()
        val (exerciseBySlug, _) = WorkoutSessionApiMapper.buildExerciseCatalog(explore, language)
        val request = WorkoutSessionSaveEncoder.encodeDayUpdate(session, plan, exerciseBySlug)
        return MovitData.workoutSession.saveDayCustomizations(
            userProgramId = userProgramId,
            weekNumber = context.weekNumber,
            dayNumber = context.dayNumber,
            request = request,
        )
    }

    override suspend fun findSwapCandidates(
        query: String,
        replacingSlug: String,
    ): List<SessionSwapCandidateUi> {
        if (!MovitData.isInstalled) {
            return fallback.findSwapCandidates(query, replacingSlug)
        }

        val platform = MovitData.requirePlatform()
        val language = platform.preferredLanguage()
        val strings = SessionStrings.load(language)
        val explore = MovitData.explore.readCached()
        val (exerciseBySlug, _) = WorkoutSessionApiMapper.buildExerciseCatalog(explore, language)

        return when (
            val result = MovitData.workoutSession.fetchSubstitutionCandidates(replacingSlug)
        ) {
            is AppResult.Success -> {
                val mapped = WorkoutSessionApiMapper.mapSubstitutionCandidates(
                    rows = result.value,
                    query = query,
                    replacingSlug = replacingSlug,
                    language = language,
                    strings = strings,
                    exerciseBySlug = exerciseBySlug,
                )
                mapped.ifEmpty { fallback.findSwapCandidates(query, replacingSlug) }
            }
            is AppResult.Failure -> fallback.findSwapCandidates(query, replacingSlug)
        }
    }
}
