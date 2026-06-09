package com.movit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.feature.explore.ExploreItemUi
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProgramDetailViewModel(
    private val programId: String,
    private val repository: LibraryRepository = defaultLibraryRepository(),
) : ViewModel() {
    private var loadedProgram: ExploreItemUi? = null
    private var enrollment = ProgramEnrollmentUi(isEnrolled = false)
    private var selectedWeekNumber = 1
    private var editState = ProgramEditUiState(startDateLabel = "Jun 2")

    private val _state = MutableStateFlow(ProgramDetailUiState(isLoading = true))
    val state: StateFlow<ProgramDetailUiState> = _state.asStateFlow()

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        when (val result = repository.loadContent()) {
            is AppResult.Success -> {
                val program = result.value.programs.firstOrNull { it.id == programId }
                    ?: result.value.featured.firstOrNull { it.id == programId }
                    ?: repository.findItem(programId)
                    ?: if (programId == ProgramDetailPreviewData.sampleProgram().id) {
                        ProgramDetailPreviewData.sampleProgram()
                    } else {
                        null
                    }
                if (program == null) {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = "Program not found.")
                    }
                } else {
                    loadedProgram = program
                    publish(program)
                }
            }
            is AppResult.Failure -> {
                val fallback = ProgramDetailPreviewData.sampleProgram()
                    .takeIf { programId == it.id || programId == "preview" }
                if (fallback != null) {
                    loadedProgram = fallback
                    publish(fallback)
                } else {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun onTabSelected(tab: ProgramDetailTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun onWeekSelected(weekNumber: Int) {
        selectedWeekNumber = weekNumber
        loadedProgram?.let { publish(it) }
    }

    fun onEditReasonSelected(reason: ProgramEditReason) {
        editState = editState.copy(selectedReason = reason)
        loadedProgram?.let { publish(it) }
    }

    fun onEditScopeSelected(scope: ProgramEditScope) {
        editState = editState.copy(selectedScope = scope)
        loadedProgram?.let { publish(it) }
    }

    fun onWeeklyTargetChange(delta: Int) {
        val next = (editState.weeklyTarget + delta).coerceIn(1, 7)
        editState = editState.copy(weeklyTarget = next)
        loadedProgram?.let { publish(it) }
    }

    fun onPauseCalendarToggle() {
        editState = editState.copy(pauseCalendar = !editState.pauseCalendar)
        enrollment = enrollment.copy(isPaused = editState.pauseCalendar)
        loadedProgram?.let { publish(it) }
    }

    fun onSaveEdit() {
        editState = editState.copy(
            showSaveToast = true,
        )
        enrollment = enrollment.copy(
            customEditsCount = enrollment.customEditsCount + 1,
            syncLabel = "Synced today",
        )
        loadedProgram?.let { publish(it) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(2_500)
            editState = editState.copy(showSaveToast = false)
            loadedProgram?.let { publish(it) }
        }
    }

    fun sessionKeyForStart(): String? {
        val program = loadedProgram ?: return null
        if (!enrollment.isEnrolled) {
            enrollment = ProgramEnrollmentUi(
                isEnrolled = true,
                startedLabel = "Started today",
                customEditsCount = 0,
                syncLabel = "Synced today",
            )
            publish(program)
        }
        return ProgramDetailPreviewData.nextSession(program.id)?.sessionWorkoutId
            ?: WorkoutSessionKeys.encode(
                programId = program.id,
                weekNumber = 1,
                dayNumber = 1,
                plannedWorkoutId = "preview",
            )
    }

    fun startCtaLabel(isEnrolled: Boolean): String =
        if (isEnrolled) "Start next session" else "Start program"

    private fun publish(program: ExploreItemUi) {
        _state.value = ProgramDetailMapper.map(
            program = program,
            enrollment = enrollment,
            selectedWeekNumber = selectedWeekNumber,
            edit = editState,
        ).copy(
            selectedTab = _state.value.selectedTab,
            programId = program.id,
            title = program.title,
            subtitle = program.subtitle,
        )
    }
}
