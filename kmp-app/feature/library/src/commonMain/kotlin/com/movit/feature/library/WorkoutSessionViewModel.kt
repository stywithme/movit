package com.movit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.data.MovitData
import com.movit.core.data.cache.CacheState
import com.movit.core.data.repository.TrainingConfigEnsureResult
import com.movit.core.data.repository.ensureAll
import com.movit.shared.AppResult
import com.movit.shared.training.MovitTrainingAnalytics
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.random.Random

data class WorkoutSessionUiState(
    val isLoading: Boolean = false,
    val isEditMode: Boolean = false,
    val isSaving: Boolean = false,
    val session: WorkoutSessionUi? = null,
    val errorMessage: String? = null,
    val activeSheet: SessionSheet? = null,
    val plannedWorkoutCards: List<PlannedWorkoutCardUi> = emptyList(),
    val expandedPlannedWorkoutId: String? = null,
    val catchUpPrompt: SessionCatchUpPromptUi? = null,
    val showCatchUpDialog: Boolean = false,
    val catchUpDialogDismissed: Boolean = false,
    val snackbarMessageKey: String? = null,
    val saveError: String? = null,
    val launchReadiness: LaunchReadiness = LaunchReadiness.LoadingContent,
    /** P1.4 — open run for Resume primary / Restart secondary. */
    val openRun: WorkoutRunOpenState? = null,
    val showRestartConfirm: Boolean = false,
)

class WorkoutSessionViewModel(
    private val workoutId: String,
    private val repository: WorkoutSessionRepository = defaultWorkoutSessionRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(WorkoutSessionUiState(isLoading = true))
    val state: StateFlow<WorkoutSessionUiState> = _state.asStateFlow()
    private val _effects = MutableSharedFlow<WorkoutSessionEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<WorkoutSessionEffect> = _effects.asSharedFlow()
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var preflightJob: Job? = null

    fun onEvent(event: WorkoutSessionEvent) {
        when (event) {
            WorkoutSessionEvent.ToggleEditMode -> toggleEditMode()
            is WorkoutSessionEvent.ExerciseClicked -> onExerciseClick(event.exerciseId)
            is WorkoutSessionEvent.RestClicked -> onRestClick(event.restId)
            is WorkoutSessionEvent.OpenSwapSheet -> openSwapSheet(event.exerciseId)
            WorkoutSessionEvent.OpenAddExerciseSheet -> openAddExerciseSheet()
            is WorkoutSessionEvent.SwapQueryChanged -> onSwapQueryChange(event.query)
            is WorkoutSessionEvent.AddExerciseQueryChanged -> onAddExerciseQueryChange(event.query)
            is WorkoutSessionEvent.SwapCandidateSelected -> selectSwapCandidate(event.slug)
            is WorkoutSessionEvent.AddExerciseCandidateSelected -> selectAddExerciseCandidate(event.slug)
            is WorkoutSessionEvent.OpenEditSheet -> openEditSheet(event.exerciseId)
            is WorkoutSessionEvent.EditDraftChanged -> updateEditDraft(event.transform)
            is WorkoutSessionEvent.RestDurationChanged -> updateRestDuration(event.seconds)
            WorkoutSessionEvent.SaveEditDetails -> saveEditDetails()
            WorkoutSessionEvent.SaveRestEdit -> saveRestEdit()
            is WorkoutSessionEvent.DeleteExercise -> deleteExercise(event.exerciseId)
            is WorkoutSessionEvent.DeleteBlock -> deleteBlock(event.blockId)
            is WorkoutSessionEvent.MoveBlock -> moveBlock(event.sectionPhaseRole, event.blockId, event.delta)
            WorkoutSessionEvent.AddRestBlock -> addRestBlock()
            WorkoutSessionEvent.DismissSheet -> dismissSheet()
            is WorkoutSessionEvent.TogglePlannedWorkoutExpand -> togglePlannedWorkoutExpand(event.workoutId)
            WorkoutSessionEvent.DismissCatchUpDialog -> dismissCatchUpDialog()
            WorkoutSessionEvent.OpenCatchUpDayClicked -> {
                viewModelScope.launch {
                    sessionKeyForCatchUpDay()?.let { key ->
                        _effects.emit(WorkoutSessionEffect.OpenCatchUpDay(key))
                    }
                }
            }
            WorkoutSessionEvent.SkipWarmup -> skipWarmup()
            WorkoutSessionEvent.SwitchEditToSwap -> switchEditSheetToSwap()
            is WorkoutSessionEvent.SelectPlannedWorkout -> handlePlannedWorkoutSelected(event.plannedWorkoutId)
            WorkoutSessionEvent.StartWorkoutClicked -> handleStartWorkoutClicked()
            WorkoutSessionEvent.ResumeWorkoutClicked -> handleResumeWorkoutClicked()
            WorkoutSessionEvent.RestartWorkoutClicked -> {
                _state.update { it.copy(showRestartConfirm = true) }
            }
            WorkoutSessionEvent.ConfirmRestartWorkout -> {
                _state.update { it.copy(showRestartConfirm = false, openRun = null) }
                WorkoutRunStore.abandonActiveForWorkout(workoutId)
                handleStartWorkoutClicked()
            }
            WorkoutSessionEvent.DismissRestartConfirm -> {
                _state.update { it.copy(showRestartConfirm = false) }
            }
            is WorkoutSessionEvent.OpenExerciseClicked -> handleOpenExerciseClicked(event.exerciseId)
            WorkoutSessionEvent.RetryClicked -> {
                when (_state.value.launchReadiness) {
                    is LaunchReadiness.Blocked -> runPreflight(_state.value.session)
                    else -> viewModelScope.launch { load() }
                }
            }
            WorkoutSessionEvent.RetrySaveClicked -> {
                viewModelScope.launch { persistSession() }
            }
            WorkoutSessionEvent.SnackbarConsumed -> consumeSnackbar()
        }
    }

    private fun handlePlannedWorkoutSelected(plannedWorkoutId: String) {
        val context = _state.value.session?.context ?: return
        if (plannedWorkoutId == context.plannedWorkoutId) return
        val sessionKey = WorkoutSessionKeys.encode(
            programId = context.programId,
            weekNumber = context.weekNumber,
            dayNumber = context.dayNumber,
            plannedWorkoutId = plannedWorkoutId,
        )
        _effects.tryEmit(WorkoutSessionEffect.SwitchWorkout(sessionKey))
    }

    private fun handleResumeWorkoutClicked() {
        val open = _state.value.openRun ?: return handleStartWorkoutClicked()
        val readiness = _state.value.launchReadiness
        when (readiness) {
            is LaunchReadiness.Blocked -> {
                runPreflight(_state.value.session)
                return
            }
            LaunchReadiness.Ready,
            LaunchReadiness.OfflineReady,
            -> Unit
            LaunchReadiness.Launching,
            LaunchReadiness.Preparing,
            LaunchReadiness.LoadingContent,
            -> return
        }
        val session = _state.value.session
        val existing = WorkoutRunStore.activeForWorkout(workoutId)
        // Prefer durable saved snapshot when resuming — never remap cursor onto a fresh plan.
        val snapshot = resolveResumeSnapshot(
            durableSnapshot = existing?.snapshot,
            sessionSnapshot = session?.toRunSnapshot(),
        ) ?: return
        if (!snapshot.isStartable) {
            applyLaunchReadiness(LaunchReadiness.Blocked("session_exercise_target_invalid"))
            return
        }
        val progress = existing?.progress ?: WorkoutRunProgressCursor(
            exerciseIndex = open.exerciseIndex,
            currentSet = open.currentSet,
            blockPhase = open.blockPhase,
            exerciseSlug = open.exerciseSlug,
        )
        if (!resumeProgressMatchesSnapshot(progress, snapshot)) {
            WorkoutRunStore.abandonActiveForWorkout(workoutId)
            _state.update {
                it.copy(
                    openRun = null,
                    showRestartConfirm = true,
                    launchReadiness = LaunchReadiness.Blocked("session_resume_plan_mismatch"),
                )
            }
            return
        }
        val launch = WorkoutLaunchCoordinator.peek(workoutId)
        WorkoutRunStore.start(
            workoutId = workoutId,
            snapshot = snapshot,
            source = WorkoutRunSource.Resume,
            returnTarget = launch?.returnTarget
                ?: existing?.returnTarget
                ?: ReturnTarget.WorkoutSession(workoutId),
            doneTarget = launch?.let { WorkoutLaunchCoordinator.doneTargetFor(it) }
                ?: existing?.doneTarget
                ?: if (session?.context != null) ReturnTarget.Train else ReturnTarget.Explore,
            runId = WorkoutRunId(open.runId),
            progress = progress,
        )
        session?.let { WorkoutFlowCache.put(workoutId, WorkoutFlowMapper.fromSession(it)) }
        val exerciseId = snapshot.exercises.getOrNull(progress.exerciseIndex)?.exerciseId
            ?: session?.let { WorkoutFlowMapper.fromSession(it).exercises.firstOrNull()?.id }
            ?: return
        if (!_state.value.launchReadiness.canStart()) return
        _state.update { it.copy(launchReadiness = LaunchReadiness.Launching) }
        val emitted = _effects.tryEmit(WorkoutSessionEffect.ResumeWorkout(exerciseId, open.runId))
        if (!emitted) {
            _state.update {
                it.copy(
                    launchReadiness = if (readiness is LaunchReadiness.OfflineReady) {
                        LaunchReadiness.OfflineReady
                    } else {
                        LaunchReadiness.Ready
                    },
                )
            }
        }
    }

    private fun handleStartWorkoutClicked() {
        if (_state.value.isSaving) return
        val readiness = _state.value.launchReadiness
        when (readiness) {
            is LaunchReadiness.Blocked -> {
                runPreflight(_state.value.session)
                return
            }
            LaunchReadiness.Ready,
            LaunchReadiness.OfflineReady,
            -> Unit
            LaunchReadiness.Launching,
            LaunchReadiness.Preparing,
            LaunchReadiness.LoadingContent,
            -> return
        }
        val session = _state.value.session ?: return
        val snapshot = session.toRunSnapshot()
        if (!snapshot.isStartable) {
            applyLaunchReadiness(LaunchReadiness.Blocked("session_exercise_target_invalid"))
            return
        }
        val firstExercise = WorkoutFlowMapper.fromSession(session).exercises.firstOrNull() ?: return
        // Prevent double launch until effect is consumed / navigation fails.
        if (!_state.value.launchReadiness.canStart()) return
        _state.update { it.copy(launchReadiness = LaunchReadiness.Launching) }
        MovitTrainingAnalytics.trackStartWorkout(
            workoutId = workoutId,
            source = session.context?.programId ?: "explore",
        )
        val emitted = _effects.tryEmit(WorkoutSessionEffect.StartWorkout(firstExercise.id))
        if (!emitted) {
            _state.update {
                it.copy(
                    launchReadiness = if (readiness is LaunchReadiness.OfflineReady) {
                        LaunchReadiness.OfflineReady
                    } else {
                        LaunchReadiness.Ready
                    },
                )
            }
        }
    }

    /** Called by route if navigation fails after Launching. */
    fun resetLaunchReadinessAfterFailedNav() {
        if (_state.value.launchReadiness !is LaunchReadiness.Launching) return
        runPreflight(_state.value.session)
    }

    private fun handleOpenExerciseClicked(exerciseId: String) {
        if (_state.value.session != null) {
            _effects.tryEmit(
                WorkoutSessionEffect.OpenExercise(
                    exerciseId = exerciseId,
                    runDraftId = workoutId,
                ),
            )
        }
    }

    private fun emitSnackbar(key: String) {
        _state.update { it.copy(snackbarMessageKey = key) }
        _effects.tryEmit(WorkoutSessionEffect.ShowSnackbar(key))
    }

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        if (_state.value.session == null) {
            _state.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    launchReadiness = LaunchReadiness.LoadingContent,
                )
            }
        }
        repository.observeSession(workoutId).collect { cacheState ->
            when (cacheState) {
                is CacheState.Cached -> {
                    if (shouldPreserveLocalSessionDraft()) return@collect
                    applyLoadedSession(cacheState.value.recalculated())
                }
                is CacheState.Fresh -> {
                    // Ignore Fresh while editing / saving / launching so in-progress edits aren't clobbered.
                    if (shouldPreserveLocalSessionDraft()) return@collect
                    applyLoadedSession(cacheState.value.recalculated())
                }
                is CacheState.Error -> _state.update {
                    it.copy(isLoading = false, errorMessage = cacheState.message)
                }
                is CacheState.Loading -> Unit
            }
        }
    }

    private fun shouldPreserveLocalSessionDraft(): Boolean {
        val current = _state.value
        if (current.session == null) return false
        return current.isEditMode ||
            current.isSaving ||
            current.launchReadiness is LaunchReadiness.Launching ||
            current.activeSheet != null
    }

    private fun runPreflight(session: WorkoutSessionUi?) {
        if (session == null) {
            _state.update { it.copy(launchReadiness = LaunchReadiness.LoadingContent) }
            return
        }
        preflightJob?.cancel()
        // Preview / JVM host tests have no Main dispatcher; keep Start usable without viewModelScope.
        if (!MovitData.isInstalled) {
            applyLaunchReadiness(LaunchReadiness.Ready)
            return
        }
        // ponytail: Default scope — preflight must not require Main (host tests / early init).
        // Ceiling: persistScope outlives VM; upgrade: inject test dispatcher + cancel in onCleared.
        preflightJob = persistScope.launch {
            applyLaunchReadiness(LaunchReadiness.Preparing)
            applyLaunchReadiness(evaluateLaunchReadiness(session))
        }
    }

    private fun applyLaunchReadiness(readiness: LaunchReadiness) {
        _state.update { current ->
            if (current.launchReadiness is LaunchReadiness.Launching) current
            else current.copy(launchReadiness = readiness)
        }
    }

    private suspend fun evaluateLaunchReadiness(session: WorkoutSessionUi): LaunchReadiness {
        val config = WorkoutFlowMapper.fromSession(session)
        val slugs = config.exercises.map { it.exerciseSlug }.filter { it.isNotBlank() }
        if (slugs.isEmpty()) {
            return LaunchReadiness.Blocked("session_no_exercises")
        }
        val snapshot = session.toRunSnapshot()
        if (!snapshot.isStartable) {
            return LaunchReadiness.Blocked("session_exercise_target_invalid")
        }
        if (!MovitData.isInstalled) {
            // Preview / JVM tests without MovitData — allow Start so UI/tests can exercise launch.
            return LaunchReadiness.Ready
        }
        MovitData.bootstrapLocalCaches()
        val repo = MovitData.trainingConfig
        val allCached = slugs.all { slug ->
            repo.resolveAvailableSlug(slug, normalizeTrainingSlug(slug)) != null ||
                repo.supports(slug) ||
                repo.supports(normalizeTrainingSlug(slug))
        }
        if (allCached) {
            val online = MovitData.requirePlatform().isNetworkAvailable()
            return if (online) LaunchReadiness.Ready else LaunchReadiness.OfflineReady
        }
        val online = MovitData.requirePlatform().isNetworkAvailable()
        if (!online) {
            // Partial cache is not OfflineReady — every exercise config must be local.
            return resolveOfflineConfigReadiness(allConfigsAvailable = false)
        }
        val templateId = workoutId.takeUnless { it.startsWith("session:") }
        val ensured = runCatching {
            repo.ensureAll(
                slugs = slugs,
                workoutTemplateId = templateId,
            )
        }.getOrElse {
            TrainingConfigEnsureResult.Unavailable(
                TrainingConfigEnsureResult.Unavailable.Reason.NotFoundAfterSync,
            )
        }
        return when (ensured) {
            TrainingConfigEnsureResult.Available -> LaunchReadiness.Ready
            is TrainingConfigEnsureResult.Unavailable -> when (ensured.reason) {
                TrainingConfigEnsureResult.Unavailable.Reason.Offline ->
                    LaunchReadiness.Blocked("training_config_offline_unavailable")
                TrainingConfigEnsureResult.Unavailable.Reason.NotFoundAfterSync ->
                    LaunchReadiness.Blocked("training_config_first_use_online")
            }
        }
    }


    fun toggleEditMode() {
        if (_state.value.isEditMode) {
            // persistScope: host tests have no Main dispatcher (same as skipWarmup).
            persistScope.launch {
                persistSession()
                _state.update { it.copy(isEditMode = false, activeSheet = null) }
            }
        } else {
            _state.update { it.copy(isEditMode = true, saveError = null) }
        }
    }

    fun onExerciseClick(exerciseId: String) {
        if (!_state.value.isEditMode) return
        openEditSheet(exerciseId)
    }

    fun onRestClick(restId: String) {
        if (!_state.value.isEditMode) return
        val rest = findRest(restId) ?: return
        _state.update {
            it.copy(activeSheet = SessionSheet.EditRest(restId = restId, durationSeconds = rest.durationSeconds))
        }
    }

    fun openSwapSheet(exerciseId: String) {
        val exercise = findExercise(exerciseId) ?: return
        _state.update {
            it.copy(
                activeSheet = SessionSheet.Swap(
                    exerciseId = exerciseId,
                    exerciseName = exercise.name,
                    exerciseCategory = exercise.category,
                    replacingSlug = exercise.exerciseSlug,
                    isLoadingCandidates = true,
                ),
            )
        }
        viewModelScope.launch { refreshSwapCandidates(exerciseId, "") }
    }

    fun openAddExerciseSheet() {
        val sectionRole = _state.value.session
            ?.sections
            ?.lastOrNull { it.phaseRole == "MAIN" }
            ?.phaseRole
            ?: _state.value.session?.sections?.lastOrNull()?.phaseRole
            ?: "MAIN"
        _state.update {
            it.copy(
                activeSheet = SessionSheet.AddExercise(
                    sectionPhaseRole = sectionRole,
                    isLoadingCandidates = true,
                ),
            )
        }
        viewModelScope.launch { refreshAddCandidates("") }
    }

    fun onSwapQueryChange(query: String) {
        val sheet = _state.value.activeSheet as? SessionSheet.Swap ?: return
        _state.update {
            it.copy(activeSheet = sheet.copy(query = query, isLoadingCandidates = true))
        }
        viewModelScope.launch { refreshSwapCandidates(sheet.exerciseId, query) }
    }

    fun onAddExerciseQueryChange(query: String) {
        val sheet = _state.value.activeSheet as? SessionSheet.AddExercise ?: return
        _state.update {
            it.copy(activeSheet = sheet.copy(query = query, isLoadingCandidates = true))
        }
        viewModelScope.launch { refreshAddCandidates(query) }
    }

    fun selectSwapCandidate(slug: String) {
        val sheet = _state.value.activeSheet as? SessionSheet.Swap ?: return
        val candidate = sheet.candidates.firstOrNull { it.slug == slug } ?: return
        updateSession { session ->
            session.copy(
                sections = session.sections.map { section ->
                    section.copy(
                        items = section.items.map { block ->
                            if (block is WorkoutSessionBlockUi.Exercise && block.id == sheet.exerciseId) {
                                block.copy(
                                    exerciseSlug = slug,
                                    name = candidate.name,
                                    category = candidate.subtitle.substringBefore(" ·").ifBlank { block.category },
                                    imageUrl = candidate.imageUrl ?: block.imageUrl,
                                    variantIndex = 0,
                                )
                            } else {
                                block
                            }
                        },
                    )
                },
            ).recalculated()
        }
        dismissSheet()
    }

    fun selectAddExerciseCandidate(slug: String) {
        val sheet = _state.value.activeSheet as? SessionSheet.AddExercise ?: return
        val candidate = sheet.candidates.firstOrNull { it.slug == slug } ?: return
        val newId = "ex-new-${Random.nextLong()}"
        updateSession { session ->
            val sectionIndex = session.sections.indexOfLast { it.phaseRole == sheet.sectionPhaseRole }
                .takeIf { it >= 0 } ?: session.sections.lastIndex
            if (sectionIndex < 0) return@updateSession session
            val section = session.sections[sectionIndex]
            val nextIndex = section.items.count { it is WorkoutSessionBlockUi.Exercise } + 1
            val newExercise = WorkoutSessionBlockUi.Exercise(
                id = newId,
                exerciseSlug = slug,
                index = nextIndex,
                name = candidate.name,
                category = candidate.subtitle,
                imageUrl = candidate.imageUrl,
                sets = 3,
                reps = 12,
                restSeconds = 60,
                setsLabel = WorkoutSessionFormatting.setsLabel(3, 12, null),
                restLabel = WorkoutSessionFormatting.restLabel(60),
                phaseRole = section.phaseRole,
            )
            val updatedSections = session.sections.toMutableList()
            updatedSections[sectionIndex] = section.copy(items = section.items + newExercise)
            session.copy(sections = updatedSections).recalculated()
        }
        openEditSheet(newId)
    }

    fun openEditSheet(exerciseId: String) {
        val exercise = findExercise(exerciseId) ?: return
        _state.update {
            it.copy(
                activeSheet = SessionSheet.EditDetails(
                    exerciseId = exerciseId,
                    draft = ExerciseEditDraft(
                        sets = exercise.sets,
                        reps = exercise.reps,
                        durationSeconds = exercise.durationSeconds,
                        weightKg = exercise.weightKg,
                        restSeconds = exercise.restSeconds,
                    ),
                ),
            )
        }
    }

    fun updateEditDraft(transform: (ExerciseEditDraft) -> ExerciseEditDraft) {
        val sheet = _state.value.activeSheet as? SessionSheet.EditDetails ?: return
        _state.update {
            it.copy(activeSheet = sheet.copy(draft = transform(sheet.draft)))
        }
    }

    fun updateRestDuration(seconds: Int) {
        val sheet = _state.value.activeSheet as? SessionSheet.EditRest ?: return
        _state.update {
            it.copy(activeSheet = sheet.copy(durationSeconds = seconds.coerceAtLeast(5)))
        }
    }

    fun saveEditDetails() {
        val sheet = _state.value.activeSheet as? SessionSheet.EditDetails ?: return
        updateSession { session ->
            session.copy(
                sections = session.sections.map { section ->
                    section.copy(
                        items = section.items.map { block ->
                            if (block is WorkoutSessionBlockUi.Exercise && block.id == sheet.exerciseId) {
                                block.withUpdatedMetrics(
                                    sets = sheet.draft.sets.coerceAtLeast(1),
                                    reps = sheet.draft.reps,
                                    durationSeconds = sheet.draft.durationSeconds,
                                    restSeconds = sheet.draft.restSeconds.coerceAtLeast(0),
                                    weightKg = sheet.draft.weightKg,
                                )
                            } else {
                                block
                            }
                        },
                    )
                },
            ).recalculated()
        }
        dismissSheet()
    }

    fun saveRestEdit() {
        val sheet = _state.value.activeSheet as? SessionSheet.EditRest ?: return
        updateSession { session ->
            session.copy(
                sections = session.sections.map { section ->
                    section.copy(
                        items = section.items.map { block ->
                            if (block is WorkoutSessionBlockUi.Rest && block.id == sheet.restId) {
                                block.copy(
                                    durationSeconds = sheet.durationSeconds,
                                    durationLabel = WorkoutSessionFormatting.restDurationLabel(sheet.durationSeconds),
                                )
                            } else {
                                block
                            }
                        },
                    )
                },
            )
        }
        dismissSheet()
    }

    fun deleteExercise(exerciseId: String) {
        deleteBlock(exerciseId)
    }

    fun deleteBlock(blockId: String) {
        updateSession { session ->
            session.copy(
                sections = session.sections.map { section ->
                    reindexSection(
                        section.copy(items = section.items.filterNot { it.id == blockId }),
                    )
                },
            ).recalculated()
        }
        dismissSheetIfTargeting(blockId)
    }

    fun moveBlock(sectionPhaseRole: String, blockId: String, delta: Int) {
        if (delta == 0) return
        updateSession { session ->
            val sectionIndex = session.sections.indexOfFirst { it.phaseRole == sectionPhaseRole }
            if (sectionIndex < 0) return@updateSession session
            val section = session.sections[sectionIndex]
            val fromIndex = section.items.indexOfFirst { it.id == blockId }
            if (fromIndex < 0) return@updateSession session
            val toIndex = (fromIndex + delta).coerceIn(0, section.items.lastIndex)
            if (fromIndex == toIndex) return@updateSession session
            val items = section.items.toMutableList()
            val moved = items.removeAt(fromIndex)
            items.add(toIndex, moved)
            val updatedSections = session.sections.toMutableList()
            updatedSections[sectionIndex] = reindexSection(section.copy(items = items))
            session.copy(sections = updatedSections).recalculated()
        }
    }

    fun addRestBlock() {
        val restId = "rest-${Random.nextLong()}"
        updateSession { session ->
            val mainIndex = session.sections.indexOfLast { it.phaseRole == "MAIN" }
                .takeIf { it >= 0 } ?: session.sections.lastIndex
            if (mainIndex < 0) return@updateSession session
            val updatedSections = session.sections.toMutableList()
            val target = updatedSections[mainIndex]
            updatedSections[mainIndex] = target.copy(
                items = target.items + WorkoutSessionBlockUi.Rest(
                    id = restId,
                    durationLabel = "90s",
                    durationSeconds = 90,
                ),
            )
            session.copy(sections = updatedSections).recalculated()
        }
    }

    fun dismissSheet() {
        _state.update { it.copy(activeSheet = null) }
    }

    fun togglePlannedWorkoutExpand(workoutId: String) {
        _state.update { current ->
            val next = if (current.expandedPlannedWorkoutId == workoutId) null else workoutId
            current.copy(expandedPlannedWorkoutId = next)
        }
    }

    fun dismissCatchUpDialog() {
        _state.update { it.copy(showCatchUpDialog = false, catchUpDialogDismissed = true) }
    }

    fun consumeSnackbar() {
        _state.update { it.copy(snackbarMessageKey = null) }
    }

    fun skipWarmup() {
        val session = _state.value.session ?: return
        if (!session.hasWarmupSection()) {
            emitSnackbar("session_skip_warmup_none")
            return
        }
        updateSession { it.withoutWarmup() }
        emitSnackbar("session_skip_warmup_done")
        persistScope.launch { persistSession() }
    }

    suspend fun sessionKeyForCatchUpDay(): String? {
        val prompt = _state.value.catchUpPrompt ?: return null
        val programId = _state.value.session?.context?.programId ?: return null
        return repository.sessionKeyForDay(
            programId = programId,
            weekNumber = prompt.missedWeekNumber,
            dayNumber = prompt.missedDayNumber,
        )
    }

    private suspend fun applyLoadedSession(session: WorkoutSessionUi) {
        val dayContext = repository.loadDayContext(session.id)
        val selectedId = session.context?.plannedWorkoutId
        _state.update { current ->
            current.copy(
                isLoading = false,
                session = session,
                plannedWorkoutCards = dayContext.plannedWorkoutCards,
                expandedPlannedWorkoutId = selectedId ?: current.expandedPlannedWorkoutId,
                catchUpPrompt = dayContext.catchUpPrompt,
                showCatchUpDialog = dayContext.catchUpPrompt != null && !current.catchUpDialogDismissed,
                openRun = WorkoutRunStore.openStateForWorkout(workoutId),
            )
        }
        // Keep content visible; readiness only drives the Start dock.
        if (_state.value.launchReadiness !is LaunchReadiness.Launching) {
            runPreflight(session)
        }
    }

    fun switchEditSheetToSwap() {
        val sheet = _state.value.activeSheet as? SessionSheet.EditDetails ?: return
        openSwapSheet(sheet.exerciseId)
    }

    private suspend fun refreshSwapCandidates(exerciseId: String, query: String) {
        val sheet = _state.value.activeSheet as? SessionSheet.Swap ?: return
        val candidates = repository.findSwapCandidates(query, sheet.replacingSlug)
        _state.update { state ->
            val current = state.activeSheet as? SessionSheet.Swap ?: return@update state
            if (current.exerciseId != exerciseId) return@update state
            state.copy(
                activeSheet = current.copy(
                    query = query,
                    candidates = candidates,
                    isLoadingCandidates = false,
                ),
            )
        }
    }

    private suspend fun refreshAddCandidates(query: String) {
        val candidates = repository.findAddExerciseCandidates(query)
        _state.update { state ->
            val current = state.activeSheet as? SessionSheet.AddExercise ?: return@update state
            state.copy(
                activeSheet = current.copy(
                    query = query,
                    candidates = candidates,
                    isLoadingCandidates = false,
                ),
            )
        }
    }

    private suspend fun persistSession() {
        val session = _state.value.session ?: return
        _state.update { it.copy(isSaving = true, saveError = null) }
        val result = if (session.context == null) {
            saveLocalRunDraft(session)
            AppResult.Success(Unit)
        } else {
            repository.saveSession(session)
        }
        when (result) {
            is AppResult.Success -> {
                _state.update { it.copy(isSaving = false) }
                runPreflight(_state.value.session)
            }
            is AppResult.Failure -> {
                _state.update { it.copy(isSaving = false, saveError = result.message) }
                emitSnackbar("session_save_failed")
            }
        }
    }

    private fun saveLocalRunDraft(session: WorkoutSessionUi) {
        WorkoutFlowCache.put(workoutId, WorkoutFlowMapper.fromSession(session))
    }

    private fun updateSession(transform: (WorkoutSessionUi) -> WorkoutSessionUi) {
        _state.update { state ->
            val session = state.session ?: return@update state
            state.copy(session = transform(session))
        }
    }

    private fun findExercise(exerciseId: String): WorkoutSessionBlockUi.Exercise? {
        return _state.value.session
            ?.sections
            ?.flatMap { it.items }
            ?.filterIsInstance<WorkoutSessionBlockUi.Exercise>()
            ?.firstOrNull { it.id == exerciseId }
    }

    private fun findRest(restId: String): WorkoutSessionBlockUi.Rest? {
        return _state.value.session
            ?.sections
            ?.flatMap { it.items }
            ?.filterIsInstance<WorkoutSessionBlockUi.Rest>()
            ?.firstOrNull { it.id == restId }
    }

    private fun dismissSheetIfTargeting(blockId: String) {
        when (val sheet = _state.value.activeSheet) {
            is SessionSheet.Swap -> if (sheet.exerciseId == blockId) dismissSheet()
            is SessionSheet.EditDetails -> if (sheet.exerciseId == blockId) dismissSheet()
            is SessionSheet.EditRest -> if (sheet.restId == blockId) dismissSheet()
            else -> Unit
        }
    }

    private fun reindexSection(section: WorkoutSessionSectionUi): WorkoutSessionSectionUi {
        var index = 0
        return section.copy(
            items = section.items.map { block ->
                if (block is WorkoutSessionBlockUi.Exercise) {
                    index += 1
                    block.copy(index = index)
                } else {
                    block
                }
            },
        )
    }

    override fun onCleared() {
        preflightJob?.cancel()
        persistScope.cancel()
        super.onCleared()
    }
}

/** OfflineReady only when every exercise config is local — never for a partial cache. */
internal fun resolveOfflineConfigReadiness(allConfigsAvailable: Boolean): LaunchReadiness =
    if (allConfigsAvailable) {
        LaunchReadiness.OfflineReady
    } else {
        LaunchReadiness.Blocked("training_config_offline_unavailable")
    }

/**
 * Prefer the durable open-run snapshot when resuming so a refreshed plan cannot
 * silently remap the saved cursor onto a different exercise order.
 */
internal fun resolveResumeSnapshot(
    durableSnapshot: WorkoutRunSnapshot?,
    sessionSnapshot: WorkoutRunSnapshot?,
): WorkoutRunSnapshot? = when {
    durableSnapshot != null && durableSnapshot.isStartable -> durableSnapshot
    sessionSnapshot != null && sessionSnapshot.isStartable -> sessionSnapshot
    durableSnapshot != null -> durableSnapshot
    else -> sessionSnapshot
}

/** Index↔slug must match; blank slug (legacy cursor) is accepted when the index is in range. */
internal fun resumeProgressMatchesSnapshot(
    progress: WorkoutRunProgressCursor,
    snapshot: WorkoutRunSnapshot,
): Boolean {
    val exercises = snapshot.exercises
    if (exercises.isEmpty()) return false
    val atIndex = exercises.getOrNull(progress.exerciseIndex) ?: return false
    if (progress.exerciseSlug.isBlank()) return true
    return normalizeTrainingSlug(atIndex.slug) == normalizeTrainingSlug(progress.exerciseSlug) ||
        atIndex.exerciseId == progress.exerciseSlug
}
