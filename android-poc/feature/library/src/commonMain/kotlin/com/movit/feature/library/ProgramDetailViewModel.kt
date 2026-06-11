package com.movit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.data.MovitData
import com.movit.core.network.dto.ProgramExportDto
import com.movit.core.model.ExploreItemUi
import com.movit.resources.strings.ProgramFlowStrings
import com.movit.shared.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProgramDetailViewModel(
    private val programId: String,
    private val repository: LibraryRepository = defaultLibraryRepository(),
    private val enrollProgram: suspend (String) -> AppResult<String> = defaultEnrollProgram(),
) : ViewModel() {
    private val toastScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loadedProgram: ExploreItemUi? = null
    private var loadedProgramExport: ProgramExportDto? = null
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
                if (program == null) {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = "Program not found.")
                    }
                } else {
                    loadedProgram = program
                    loadedProgramExport = loadProgramExport(program.id)
                    enrollment = resolveEnrollment(program.id)
                    publish(program)
                }
            }
            is AppResult.Failure -> {
                _state.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun onTabSelected(tab: ProgramDetailTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun onWeekSelected(weekNumber: Int) {
        selectedWeekNumber = weekNumber
        loadedProgram?.let { program ->
            toastScope.launch { publish(program) }
        }
    }

    fun onEditReasonSelected(reason: ProgramEditReason) {
        editState = editState.copy(selectedReason = reason)
        loadedProgram?.let { program ->
            toastScope.launch { publish(program) }
        }
    }

    fun onEditScopeSelected(scope: ProgramEditScope) {
        editState = editState.copy(selectedScope = scope)
        loadedProgram?.let { program ->
            toastScope.launch { publish(program) }
        }
    }

    fun onWeeklyTargetChange(delta: Int) {
        val next = (editState.weeklyTarget + delta).coerceIn(1, 7)
        editState = editState.copy(weeklyTarget = next)
        loadedProgram?.let { program ->
            toastScope.launch { publish(program) }
        }
    }

    fun onPauseCalendarToggle() {
        editState = editState.copy(pauseCalendar = !editState.pauseCalendar)
        enrollment = enrollment.copy(isPaused = editState.pauseCalendar)
        loadedProgram?.let { program ->
            toastScope.launch { publish(program) }
        }
    }

    fun onSaveEdit() {
        editState = editState.copy(
            showSaveToast = true,
        )
        enrollment = enrollment.copy(
            customEditsCount = enrollment.customEditsCount + 1,
            syncLabel = "Synced today",
        )
        loadedProgram?.let { program ->
            toastScope.launch { publish(program) }
        }
        toastScope.launch {
            delay(2_500)
            editState = editState.copy(showSaveToast = false)
            loadedProgram?.let { program -> publish(program) }
        }
    }

    override fun onCleared() {
        toastScope.cancel()
        super.onCleared()
    }

    suspend fun startProgramAndGetSessionKey(): String? {
        val program = loadedProgram ?: return null
        if (!enrollment.isEnrolled) {
            when (val result = enrollProgram(program.id)) {
                is AppResult.Success -> {
                    loadedProgramExport = loadProgramExport(program.id) ?: loadedProgramExport
                    enrollment = resolveEnrollment(program.id).copy(
                        isEnrolled = true,
                        startedLabel = "Started today",
                        syncLabel = "Synced today",
                    )
                    publish(program)
                }
                is AppResult.Failure -> {
                    _state.update { it.copy(errorMessage = result.message) }
                    return null
                }
            }
        }
        return sessionKeyForProgram(program)
    }

    fun startCtaLabel(isEnrolled: Boolean): String =
        if (isEnrolled) "Start next session" else "Start program"

    private suspend fun loadProgramExport(programId: String): ProgramExportDto? {
        if (!MovitData.isInstalled) return null
        val repo = MovitData.programFlow
        return when (val result = repo.syncProgram(programId)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> repo.readCachedProgram(programId)
        }
    }

    private fun resolveEnrollment(programId: String): ProgramEnrollmentUi {
        if (!MovitData.isInstalled) {
            return ProgramEnrollmentUi(isEnrolled = false)
        }
        val active = MovitData.home.readCached()?.trainMode?.activeProgram
        val isEnrolled = active?.id == programId
        return ProgramEnrollmentUi(
            isEnrolled = isEnrolled,
            startedLabel = if (isEnrolled) "Started" else null,
            syncLabel = if (isEnrolled) "Synced today" else null,
        )
    }

    private suspend fun sessionKeyForProgram(program: ExploreItemUi): String? {
        val export = loadedProgramExport
        if (export != null && MovitData.isInstalled) {
            val language = MovitData.requirePlatform().preferredLanguage()
            val strings = ProgramFlowStrings.load(language)
            val home = MovitData.home.readCached()
            ProgramDetailApiMapper.nextSession(export, home, language, strings)?.sessionWorkoutId?.let {
                return it
            }
        }
        _state.update {
            it.copy(errorMessage = "No upcoming session found for this program.")
        }
        return null
    }

    private suspend fun publish(program: ExploreItemUi) {
        val weeks = loadedProgramExport?.let { export ->
            if (!MovitData.isInstalled) {
                emptyList()
            } else {
                val language = MovitData.requirePlatform().preferredLanguage()
                val strings = ProgramFlowStrings.load(language)
                val home = MovitData.home.readCached()
                ProgramDetailApiMapper.mapWeeks(export, home, language, strings)
            }
        }.orEmpty()
        val nextSession = loadedProgramExport?.let { export ->
            if (!MovitData.isInstalled || !enrollment.isEnrolled) {
                null
            } else {
                val language = MovitData.requirePlatform().preferredLanguage()
                val strings = ProgramFlowStrings.load(language)
                val home = MovitData.home.readCached()
                ProgramDetailApiMapper.nextSession(export, home, language, strings)
            }
        }
        _state.value = ProgramDetailMapper.map(
            program = program,
            enrollment = enrollment,
            selectedWeekNumber = selectedWeekNumber,
            edit = editState,
            weeks = weeks,
            nextSession = nextSession,
        ).copy(
            selectedTab = _state.value.selectedTab,
            programId = program.id,
            title = program.title,
            subtitle = program.subtitle,
        )
    }
}

private fun defaultEnrollProgram(): suspend (String) -> AppResult<String> = { programId ->
    if (!MovitData.isInstalled) {
        AppResult.Failure("Sign in to enroll in a program.")
    } else {
        MovitData.plan.enrollProgram(programId)
    }
}
