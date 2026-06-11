package com.movit.feature.library

import com.movit.core.data.MovitData
import com.movit.core.data.cache.CacheState
import com.movit.core.data.cache.staleWhileRevalidate
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.ExploreWorkoutDto
import com.movit.resources.strings.SessionStrings
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

class SharedWorkoutSessionRepository(
    private val libraryRepository: LibraryRepository = defaultLibraryRepository(),
) : WorkoutSessionRepository {

    override fun observeSession(workoutId: String): Flow<CacheState<WorkoutSessionUi>> {
        if (workoutId == "preview") {
            return flowOf(CacheState.Fresh(WorkoutSessionPreviewData.preview))
        }

        val parsed = WorkoutSessionKeys.parse(workoutId)
        return if (parsed != null) {
            observeProgramSession(parsed)
        } else {
            observeTemplateSession(workoutId)
        }
    }

    override suspend fun loadSession(workoutId: String): AppResult<WorkoutSessionUi> {
        if (workoutId == "preview") {
            return AppResult.Failure("Workout preview is only available in design previews.")
        }

        val parsed = WorkoutSessionKeys.parse(workoutId)
        return if (parsed != null) {
            loadProgramSession(parsed)
        } else {
            loadTemplateSession(workoutId)
        }
    }

    private fun observeProgramSession(parsed: ParsedSessionKey): Flow<CacheState<WorkoutSessionUi>> {
        if (!MovitData.isInstalled) {
            return flow {
                emit(CacheState.Error(SessionStrings.load("en").dataNotInstalled))
            }
        }

        val platform = MovitData.requirePlatform()
        val userProgramId = platform.activeUserProgramId()
            ?: return flow {
                emit(CacheState.Error(SessionStrings.load(platform.preferredLanguage()).noEnrollment))
            }

        return staleWhileRevalidate(
            screenId = "workout_session_program",
            readCached = {
                val language = platform.preferredLanguage()
                val strings = SessionStrings.load(language)
                val explore = MovitData.explore.readCached()
                val imageResolver: (String) -> String? = { slug -> platform.exerciseImageUrl(slug) }
                val (exerciseBySlug, exerciseById) = WorkoutSessionApiMapper.buildExerciseCatalog(
                    explore,
                    language,
                    imageResolver,
                )
                MovitData.workoutSession.readCachedEffectivePlan(
                    userProgramId = userProgramId,
                    weekNumber = parsed.weekNumber,
                    dayNumber = parsed.dayNumber,
                )?.let { plan ->
                    WorkoutSessionApiMapper.mapSession(
                        parsed = parsed,
                        plan = plan,
                        language = language,
                        strings = strings,
                        exerciseBySlug = exerciseBySlug,
                        exerciseById = exerciseById,
                    )
                }
            },
            syncFresh = {
                val language = platform.preferredLanguage()
                val strings = SessionStrings.load(language)
                val explore = MovitData.explore.readCached()
                val imageResolver: (String) -> String? = { slug -> platform.exerciseImageUrl(slug) }
                val (exerciseBySlug, exerciseById) = WorkoutSessionApiMapper.buildExerciseCatalog(
                    explore,
                    language,
                    imageResolver,
                )
                when (
                    val planResult = MovitData.workoutSession.syncEffectivePlan(
                        userProgramId = userProgramId,
                        weekNumber = parsed.weekNumber,
                        dayNumber = parsed.dayNumber,
                    )
                ) {
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
                    is AppResult.Failure -> AppResult.Failure(planResult.message)
                }
            },
        )
    }

    private fun observeTemplateSession(slugOrId: String): Flow<CacheState<WorkoutSessionUi>> {
        if (!MovitData.isInstalled) {
            return flow {
                emit(CacheState.Error(SessionStrings.load("en").dataNotInstalled))
            }
        }

        val platform = MovitData.requirePlatform()

        return staleWhileRevalidate(
            screenId = "workout_session_template",
            readCached = {
                val language = platform.preferredLanguage()
                val strings = SessionStrings.load(language)
                val explore = MovitData.explore.readCached()
                val templateMeta = findWorkoutTemplate(explore?.workoutTemplates.orEmpty(), slugOrId)
                val templateId = templateMeta?.id?.takeIf { it.isNotBlank() }
                    ?: templateMeta?.slug?.takeIf { it.isNotBlank() }
                    ?: slugOrId
                val imageResolver: (String) -> String? = { slug -> platform.exerciseImageUrl(slug) }
                val (exerciseBySlug, _) = WorkoutSessionApiMapper.buildExerciseCatalog(
                    explore,
                    language,
                    imageResolver,
                )
                MovitData.workoutSession.readCachedTrainingConfig(templateId)?.let { config ->
                    val session = WorkoutTemplateSessionMapper.mapSession(
                        slugOrId = slugOrId,
                        config = config,
                        templateMeta = templateMeta,
                        language = language,
                        strings = strings,
                        exerciseBySlug = exerciseBySlug,
                    )
                    session.takeIf { it.sections.isNotEmpty() }
                } ?: WorkoutTemplateSessionMapper.mapExploreFallback(
                    slugOrId = slugOrId,
                    templateMeta = templateMeta,
                    explore = explore,
                    language = language,
                    strings = strings,
                    exerciseBySlug = exerciseBySlug,
                )
            },
            syncFresh = {
                syncTemplateSession(slugOrId)
            },
        )
    }

    private suspend fun syncTemplateSession(slugOrId: String): AppResult<WorkoutSessionUi> {
        val platform = MovitData.requirePlatform()
        val language = platform.preferredLanguage()
        val strings = SessionStrings.load(language)
        val explore = resolveExploreData()
        val templateMeta = findWorkoutTemplate(explore?.workoutTemplates.orEmpty(), slugOrId)
            ?: libraryRepository.findItem(slugOrId)?.let { item ->
                ExploreWorkoutDto(
                    id = item.id,
                    slug = item.id,
                    name = com.movit.core.network.dto.LocalizedNameDto(en = item.title),
                    estimatedDurationMin = item.durationMinutes,
                    exerciseCount = item.metadata.firstOrNull { it.contains("exercise", ignoreCase = true) }
                        ?.filter { it.isDigit() }
                        ?.toIntOrNull()
                        ?: 1,
                    coverImageUrl = item.imageUrl,
                )
            }
        val templateId = templateMeta?.id?.takeIf { it.isNotBlank() }
            ?: templateMeta?.slug?.takeIf { it.isNotBlank() }
            ?: slugOrId
        val imageResolver: (String) -> String? = { slug -> platform.exerciseImageUrl(slug) }
        val (exerciseBySlug, _) = WorkoutSessionApiMapper.buildExerciseCatalog(
            explore,
            language,
            imageResolver,
        )

        return when (val configResult = MovitData.workoutSession.syncTrainingConfig(templateId)) {
            is AppResult.Success -> {
                val session = WorkoutTemplateSessionMapper.mapSession(
                    slugOrId = slugOrId,
                    config = configResult.value,
                    templateMeta = templateMeta,
                    language = language,
                    strings = strings,
                    exerciseBySlug = exerciseBySlug,
                )
                if (session.sections.isEmpty()) {
                    fallbackTemplateSession(
                        slugOrId = slugOrId,
                        templateMeta = templateMeta,
                        explore = explore,
                        language = language,
                        strings = strings,
                        exerciseBySlug = exerciseBySlug,
                        failureMessage = strings.workoutNotFound,
                    )
                } else {
                    AppResult.Success(session)
                }
            }
            is AppResult.Failure -> {
                fallbackTemplateSession(
                    slugOrId = slugOrId,
                    templateMeta = templateMeta,
                    explore = explore,
                    language = language,
                    strings = strings,
                    exerciseBySlug = exerciseBySlug,
                    failureMessage = configResult.message,
                )
            }
        }
    }

    private suspend fun loadProgramSession(parsed: ParsedSessionKey): AppResult<WorkoutSessionUi> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(SessionStrings.load("en").dataNotInstalled)
        }

        val platform = MovitData.requirePlatform()
        val language = platform.preferredLanguage()
        val strings = SessionStrings.load(language)
        val userProgramId = platform.activeUserProgramId()
            ?: return AppResult.Failure(strings.noEnrollment)
        val explore = MovitData.explore.readCached()
        val imageResolver: (String) -> String? = { slug -> platform.exerciseImageUrl(slug) }
        val (exerciseBySlug, exerciseById) = WorkoutSessionApiMapper.buildExerciseCatalog(
            explore,
            language,
            imageResolver,
        )

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

    private suspend fun loadTemplateSession(slugOrId: String): AppResult<WorkoutSessionUi> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(SessionStrings.load("en").dataNotInstalled)
        }
        return syncTemplateSession(slugOrId)
    }

    private suspend fun resolveExploreData(): ExploreDataDto? {
        MovitData.explore.readCached()?.let { return it }
        return when (val sync = MovitData.explore.sync()) {
            is AppResult.Success -> sync.value
            is AppResult.Failure -> null
        }
    }

    private fun fallbackTemplateSession(
        slugOrId: String,
        templateMeta: ExploreWorkoutDto?,
        explore: ExploreDataDto?,
        language: String,
        strings: SessionStrings,
        exerciseBySlug: Map<String, ExerciseCatalogEntry>,
        failureMessage: String,
    ): AppResult<WorkoutSessionUi> {
        val session = WorkoutTemplateSessionMapper.mapExploreFallback(
            slugOrId = slugOrId,
            templateMeta = templateMeta,
            explore = explore,
            language = language,
            strings = strings,
            exerciseBySlug = exerciseBySlug,
        )
        return session?.let { AppResult.Success(it) }
            ?: AppResult.Failure(failureMessage.ifBlank { strings.workoutNotFound })
    }

    private fun findWorkoutTemplate(
        templates: List<ExploreWorkoutDto>,
        slugOrId: String,
    ): ExploreWorkoutDto? =
        templates.firstOrNull { it.slug == slugOrId || it.id == slugOrId }

    override suspend fun saveSession(session: WorkoutSessionUi): AppResult<Unit> {
        val context = session.context
            ?: return AppResult.Success(Unit)

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
        val imageResolver: (String) -> String? = { slug -> platform.exerciseImageUrl(slug) }
        val (exerciseBySlug, _) = WorkoutSessionApiMapper.buildExerciseCatalog(
            explore,
            language,
            imageResolver,
        )
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
            return emptyList()
        }

        val platform = MovitData.requirePlatform()
        val language = platform.preferredLanguage()
        val strings = SessionStrings.load(language)
        val explore = MovitData.explore.readCached()
        val imageResolver: (String) -> String? = { slug -> platform.exerciseImageUrl(slug) }
        val (exerciseBySlug, _) = WorkoutSessionApiMapper.buildExerciseCatalog(
            explore,
            language,
            imageResolver,
        )

        return when (
            val result = MovitData.workoutSession.fetchSubstitutionCandidates(replacingSlug)
        ) {
            is AppResult.Success -> {
                WorkoutSessionApiMapper.mapSubstitutionCandidates(
                    rows = result.value,
                    query = query,
                    replacingSlug = replacingSlug,
                    language = language,
                    strings = strings,
                    exerciseBySlug = exerciseBySlug,
                )
            }
            is AppResult.Failure -> emptyList()
        }
    }

    override suspend fun findAddExerciseCandidates(query: String): List<SessionSwapCandidateUi> {
        if (!MovitData.isInstalled) {
            return emptyList()
        }
        val platform = MovitData.requirePlatform()
        val language = platform.preferredLanguage()
        val explore = MovitData.explore.readCached()
        val imageResolver: (String) -> String? = { slug -> platform.exerciseImageUrl(slug) }
        return WorkoutSessionApiMapper.listExerciseCandidates(
            explore = explore,
            language = language,
            query = query,
            imageUrlForSlug = imageResolver,
        )
    }
}
