package com.movit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.data.cache.CacheState
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
)

class WorkoutSessionViewModel(
    private val workoutId: String,
    private val repository: WorkoutSessionRepository = defaultWorkoutSessionRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(WorkoutSessionUiState(isLoading = true))
    val state: StateFlow<WorkoutSessionUiState> = _state.asStateFlow()
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        if (_state.value.session == null) {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
        }
        repository.observeSession(workoutId).collect { cacheState ->
            when (cacheState) {
                is CacheState.Cached -> applyLoadedSession(cacheState.value.recalculated())
                is CacheState.Fresh -> applyLoadedSession(cacheState.value.recalculated())
                is CacheState.Error -> _state.update {
                    it.copy(isLoading = false, errorMessage = cacheState.message)
                }
                is CacheState.Loading -> Unit
            }
        }
    }

    fun toggleEditMode() {
        val wasEditing = _state.value.isEditMode
        if (wasEditing) {
            viewModelScope.launch { persistSession() }
        }
        _state.update {
            it.copy(
                isEditMode = !wasEditing,
                activeSheet = null,
            )
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
                setsLabel = "3 × 12",
                restLabel = "60s rest",
                phaseRole = section.phaseRole,
            )
            val updatedSections = session.sections.toMutableList()
            updatedSections[sectionIndex] = section.copy(items = section.items + newExercise)
            session.copy(sections = updatedSections).recalculated()
        }
        dismissSheet()
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
            _state.update { it.copy(snackbarMessageKey = "session_skip_warmup_none") }
            return
        }
        updateSession { it.withoutWarmup() }
        _state.update { it.copy(snackbarMessageKey = "session_skip_warmup_done") }
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
            )
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
        if (session.context == null) return
        _state.update { it.copy(isSaving = true) }
        when (val result = repository.saveSession(session)) {
            is AppResult.Success -> _state.update { it.copy(isSaving = false) }
            is AppResult.Failure -> _state.update {
                it.copy(isSaving = false, errorMessage = result.message)
            }
        }
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
}
