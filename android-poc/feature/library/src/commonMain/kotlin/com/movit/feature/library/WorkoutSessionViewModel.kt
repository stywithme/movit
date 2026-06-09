package com.movit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class WorkoutSessionUiState(
    val isLoading: Boolean = false,
    val isEditMode: Boolean = false,
    val isSaving: Boolean = false,
    val session: WorkoutSessionUi? = null,
    val errorMessage: String? = null,
    val activeSheet: SessionSheet? = null,
)

class WorkoutSessionViewModel(
    private val workoutId: String,
    private val repository: WorkoutSessionRepository = defaultWorkoutSessionRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(WorkoutSessionUiState(isLoading = true))
    val state: StateFlow<WorkoutSessionUiState> = _state.asStateFlow()

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        when (val result = repository.loadSession(workoutId)) {
            is AppResult.Success -> {
                _state.update {
                    it.copy(isLoading = false, session = result.value.recalculated())
                }
            }
            is AppResult.Failure -> {
                _state.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
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

    fun onSwapQueryChange(query: String) {
        val sheet = _state.value.activeSheet as? SessionSheet.Swap ?: return
        _state.update {
            it.copy(activeSheet = sheet.copy(query = query, isLoadingCandidates = true))
        }
        viewModelScope.launch { refreshSwapCandidates(sheet.exerciseId, query) }
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

    fun deleteExercise(exerciseId: String) {
        updateSession { session ->
            session.copy(
                sections = session.sections.map { section ->
                    section.copy(
                        items = section.items.filterNot {
                            it is WorkoutSessionBlockUi.Exercise && it.id == exerciseId
                        },
                    )
                }.map { section ->
                    reindexSection(section)
                },
            ).recalculated()
        }
        if ((_state.value.activeSheet as? SessionSheet.Swap)?.exerciseId == exerciseId ||
            (_state.value.activeSheet as? SessionSheet.EditDetails)?.exerciseId == exerciseId
        ) {
            dismissSheet()
        }
    }

    fun addRestBlock() {
        val restId = "rest-${Random.nextLong()}"
        updateSession { session ->
            val mainIndex = session.sections.indexOfLast { it.phaseRole == "MAIN" }
                .takeIf { it >= 0 } ?: session.sections.lastIndex
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
