package com.movit.feature.library



import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope

import com.movit.core.data.MovitData

import com.movit.core.data.sync.WeekOfflinePackPrefetcher

import com.movit.core.network.dto.EffectivePlanPayloadDto

import com.movit.core.network.dto.ProgramExportDto

import com.movit.core.network.dto.UserProgramUpdateRequest

import com.movit.core.model.ExploreItemUi

import com.movit.resources.strings.ProgramDetailStrings

import com.movit.resources.strings.ProgramFlowStrings

import com.movit.resources.localizedString

import com.movit.shared.AppResult

import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.SupervisorJob

import kotlinx.coroutines.cancel

import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.MutableSharedFlow

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.SharedFlow

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asSharedFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.update

import kotlinx.coroutines.launch



class ProgramDetailViewModel(

    private val programId: String,
    initialWeekNumber: Int? = null,

    private val repository: LibraryRepository = defaultLibraryRepository(),

    private val enrollProgram: suspend (String) -> AppResult<String> = defaultEnrollProgram(),

    private val saveDayCustomizations: suspend (

        userProgramId: String,

        weekNumber: Int,

        dayNumber: Int,

        request: UserProgramUpdateRequest,

    ) -> AppResult<Unit> = defaultSaveDayCustomizations(),

    private val prefetchWeekOffline: suspend (

        ProgramExportDto,

        Int,

        (Int) -> Unit,

    ) -> WeekOfflinePackPrefetcher.PrefetchOutcome = defaultPrefetchWeekOffline(),

    private val isWeekOfflineReady: (String, Int) -> Boolean = defaultIsWeekOfflineReady(),

    private val programExportLoader: suspend (String) -> ProgramExportDto? = { programId ->

        loadProgramExportForDetail(programId)

    },

) : ViewModel() {

    private val toastScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var loadedProgram: ExploreItemUi? = null

    private var loadedProgramExport: ProgramExportDto? = null

    private var effectiveDayPlan: EffectivePlanPayloadDto? = null

    private var removedSessionIds = mutableSetOf<String>()

    private var removedExerciseIds = mutableSetOf<String>()

    private var enrollment = ProgramEnrollmentUi(isEnrolled = false)

    private var selectedWeekNumber = initialWeekNumber ?: 1

    private var selectedDayNumber: Int? = null

    private var editState = ProgramEditUiState(
        startDateLabel = "Jun 2",
        editingWeekNumber = selectedWeekNumber,
    )



    private val _state = MutableStateFlow(ProgramDetailUiState(isLoading = true))

    val state: StateFlow<ProgramDetailUiState> = _state.asStateFlow()



    private val _effects = MutableSharedFlow<ProgramDetailEffect>(extraBufferCapacity = 1)

    val effects: SharedFlow<ProgramDetailEffect> = _effects.asSharedFlow()



    fun onEvent(event: ProgramDetailEvent) {

        when (event) {

            is ProgramDetailEvent.TabSelected -> onTabSelected(event.tab)

            is ProgramDetailEvent.WeekSelected -> onWeekSelected(event.weekNumber)

            is ProgramDetailEvent.DaySelected -> onDaySelected(event.dayNumber)

            ProgramDetailEvent.StartProgramClicked -> {

                viewModelScope.launch {

                    startProgramAndGetSessionKey()?.let { key ->

                        _effects.emit(ProgramDetailEffect.StartSession(key))

                    }

                }

            }

            is ProgramDetailEvent.EditReasonSelected -> onEditReasonSelected(event.reason)

            is ProgramDetailEvent.EditScopeSelected -> onEditScopeSelected(event.scope)

            is ProgramDetailEvent.WeeklyTargetChange -> onWeeklyTargetChange(event.delta)

            ProgramDetailEvent.PauseCalendarToggle -> onPauseCalendarToggle()

            is ProgramDetailEvent.SessionMove -> onSessionMove(event.sessionId, event.direction)

            is ProgramDetailEvent.ExerciseParamChange -> onExerciseParamChange(

                sessionId = event.sessionId,

                exerciseId = event.exerciseId,

                sets = event.sets,

                reps = event.reps,

                weightKg = event.weightKg,

                restSeconds = event.restSeconds,

            )

            is ProgramDetailEvent.RemoveSession -> onRemoveSession(event.sessionId)

            is ProgramDetailEvent.RemoveExercise -> onRemoveExercise(event.sessionId, event.exerciseId)

            ProgramDetailEvent.ResetEditDay -> onResetEditDay()

            ProgramDetailEvent.SaveEdit -> onSaveEdit()

            ProgramDetailEvent.ViewWeeklyReportClicked -> {

                _effects.tryEmit(ProgramDetailEffect.ViewWeeklyReport(_state.value.selectedWeekNumber))

            }

            ProgramDetailEvent.DownloadWeekOffline -> onDownloadWeekOffline()

            ProgramDetailEvent.RetryClicked -> {
                viewModelScope.launch { load() }
            }

        }

    }



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

                        it.copy(isLoading = false, errorMessage = "program_not_found")

                    }

                } else {

                    loadedProgram = program

                    loadedProgramExport = programExportLoader(program.id)

                    enrollment = resolveEnrollment(program.id)

                    if (!editState.isDirty) {

                        reloadEditDaySessions(program)

                    }

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

        if (tab == ProgramDetailTab.Edit) {

            loadedProgram?.let { program ->

                toastScope.launch {

                    if (!editState.isDirty) reloadEditDaySessions(program)

                    publish(program)

                }

            }

        }

    }



    fun onDaySelected(dayNumber: Int) {
        selectedDayNumber = dayNumber
        loadedProgram?.let { program ->
            toastScope.launch { publish(program) }
        }
    }

    fun onWeekSelected(weekNumber: Int) {

        selectedWeekNumber = weekNumber
        selectedDayNumber = null

        editState = editState.copy(

            editingWeekNumber = weekNumber,

            isDirty = false,

        )

        removedSessionIds.clear()

        removedExerciseIds.clear()

        loadedProgram?.let { program ->

            toastScope.launch {

                reloadEditDaySessions(program)

                publish(program)

            }

        }

    }



    fun onDownloadWeekOffline() {

        val program = loadedProgram ?: return

        val export = loadedProgramExport

        if (export == null) {

            _state.update {

                it.copy(

                    weekOffline = WeekOfflineUiState(

                        status = WeekOfflineStatus.Failed,

                        errorMessageKey = "program_week_offline_program_not_loaded",

                    ),

                )

            }

            return

        }

        if (!enrollment.isEnrolled) {

            _state.update {

                it.copy(

                    weekOffline = WeekOfflineUiState(

                        status = WeekOfflineStatus.Failed,

                        errorMessageKey = "program_week_offline_enroll_first",

                    ),

                )

            }

            return

        }

        toastScope.launch {

            _state.update {

                it.copy(

                    weekOffline = WeekOfflineUiState(

                        status = WeekOfflineStatus.Downloading,

                        progressPercent = 0,

                        errorMessage = null,
                        errorMessageKey = null,

                    ),

                )

            }

            val outcome = prefetchWeekOffline(export, selectedWeekNumber) { percent ->

                _state.update { current ->

                    current.copy(

                        weekOffline = current.weekOffline.copy(

                            status = WeekOfflineStatus.Downloading,

                            progressPercent = percent,

                        ),

                    )

                }

            }

            val weekOffline = when (outcome) {

                is WeekOfflinePackPrefetcher.PrefetchOutcome.Ready ->

                    WeekOfflineUiState(

                        status = WeekOfflineStatus.Ready,

                        progressPercent = 100,

                    )

                is WeekOfflinePackPrefetcher.PrefetchOutcome.SkippedNoWeek ->

                    WeekOfflineUiState(

                        status = WeekOfflineStatus.Failed,

                        errorMessageKey = "program_week_offline_week_unavailable",

                    )

                is WeekOfflinePackPrefetcher.PrefetchOutcome.Failed ->
                    weekOfflineFailedState(outcome.message)

            }

            _state.update { it.copy(weekOffline = weekOffline) }

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

        editState = editState.copy(weeklyTarget = next, isDirty = true)

        loadedProgram?.let { program ->

            toastScope.launch { publish(program) }

        }

    }



    fun onPauseCalendarToggle() {

        editState = editState.copy(

            pauseCalendar = !editState.pauseCalendar,

            isDirty = true,

        )

        enrollment = enrollment.copy(isPaused = editState.pauseCalendar)

        loadedProgram?.let { program ->

            toastScope.launch { publish(program) }

        }

    }



    fun onSessionMove(sessionId: String, direction: Int) {

        val sessions = editState.daySessions.toMutableList()

        val index = sessions.indexOfFirst { it.id == sessionId }

        if (index < 0) return

        val target = index + direction

        if (target !in sessions.indices) return

        val moved = sessions.removeAt(index)

        sessions.add(target, moved)

        editState = editState.copy(

            daySessions = sessions.mapIndexed { order, session ->

                session.copy(sortOrder = order, isEdited = true)

            },

            isDirty = true,

        )

        loadedProgram?.let { program ->

            toastScope.launch { publish(program) }

        }

    }



    fun onExerciseParamChange(

        sessionId: String,

        exerciseId: String,

        sets: Int? = null,

        reps: Int? = null,

        weightKg: Double? = null,

        restSeconds: Int? = null,

    ) {

        editState = editState.copy(

            daySessions = editState.daySessions.map { session ->

                if (session.id != sessionId) return@map session

                session.copy(

                    isEdited = true,

                    exercises = session.exercises.map { exercise ->

                        if (exercise.id != exerciseId) return@map exercise

                        exercise.copy(

                            sets = sets ?: exercise.sets,

                            reps = reps ?: exercise.reps,

                            weightKg = weightKg ?: exercise.weightKg,

                            restSeconds = restSeconds ?: exercise.restSeconds,

                            isEdited = true,

                        )

                    },

                )

            },

            isDirty = true,

        )

        loadedProgram?.let { program ->

            toastScope.launch { publish(program) }

        }

    }



    fun onRemoveSession(sessionId: String) {

        removedSessionIds.add(sessionId)

        editState = editState.copy(

            daySessions = editState.daySessions.filterNot { it.id == sessionId },

            isDirty = true,

        )

        loadedProgram?.let { program ->

            toastScope.launch { publish(program) }

        }

    }



    fun onRemoveExercise(sessionId: String, exerciseId: String) {

        removedExerciseIds.add(exerciseId)

        editState = editState.copy(

            daySessions = editState.daySessions.map { session ->

                if (session.id != sessionId) return@map session

                session.copy(

                    isEdited = true,

                    exercises = session.exercises.filterNot { it.id == exerciseId },

                )

            },

            isDirty = true,

        )

        loadedProgram?.let { program ->

            toastScope.launch { publish(program) }

        }

    }



    fun onResetEditDay() {

        removedSessionIds.clear()

        removedExerciseIds.clear()

        editState = editState.copy(isDirty = false)

        loadedProgram?.let { program ->

            toastScope.launch {

                reloadEditDaySessions(program)

                publish(program)

            }

        }

    }



    fun onSaveEdit() {

        val program = loadedProgram ?: return

        toastScope.launch {

            editState = editState.copy(isSaving = true, saveError = null)

            publish(program)



            val baseline = effectiveDayPlan

                ?: ProgramDetailEditMapper.toBaselinePlan(

                    sessions = editState.daySessions,

                    userProgramId = "preview",

                    weekNumber = editState.editingWeekNumber,

                    dayNumber = editState.editingDayNumber,

                    programId = program.id,

                )



            val shouldSyncDay = editState.isDirty && editState.daySessions.isNotEmpty()

            if (shouldSyncDay) {

                val request = ProgramEditSaveEncoder.encodeDayUpdate(

                    weekNumber = editState.editingWeekNumber,

                    dayNumber = editState.editingDayNumber,

                    sessions = editState.daySessions,

                    baselinePlan = baseline,

                    removedSessionIds = removedSessionIds,

                    removedExerciseIds = removedExerciseIds,

                )

                when (

                    val result = saveDayCustomizations(

                        resolveUserProgramId(),

                        editState.editingWeekNumber,

                        editState.editingDayNumber,

                        request,

                    )

                ) {

                    is AppResult.Success -> {

                        effectiveDayPlan = baseline.copy(

                            plannedWorkouts = request.customizations.values.firstOrNull()

                                ?: baseline.plannedWorkouts,

                        )

                        removedSessionIds.clear()

                        removedExerciseIds.clear()

                        completeSave(program)

                    }

                    is AppResult.Failure -> {

                        editState = editState.copy(

                            isSaving = false,

                            saveError = result.message,

                        )

                        publish(program)

                    }

                }

            } else {

                completeSave(program)

            }

        }

    }



    private suspend fun completeSave(program: ExploreItemUi) {

        editState = editState.copy(

            isSaving = false,

            isDirty = false,

            showSaveToast = true,

        )

        enrollment = enrollment.copy(

            customEditsCount = enrollment.customEditsCount + 1,

            syncLabel = localizedString(resolveLanguage(), "program_synced_today"),

        )

        publish(program)

        delay(2_500)

        editState = editState.copy(showSaveToast = false)

        publish(program)

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

                    loadedProgramExport = programExportLoader(program.id) ?: loadedProgramExport

                    enrollment = resolveEnrollment(program.id).copy(

                        isEnrolled = true,

                        startedLabel = localizedString(resolveLanguage(), "program_started_today"),

                        syncLabel = localizedString(resolveLanguage(), "program_synced_today"),

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

        if (isEnrolled) "program_start_next" else "program_start"



    private suspend fun reloadEditDaySessions(program: ExploreItemUi) {

        val language = resolveLanguage()

        val weekNumber = editState.editingWeekNumber

        val dayNumber = resolveEditingDayNumber(weekNumber)

        editState = editState.copy(editingDayNumber = dayNumber)



        val userProgramId = resolveUserProgramIdOrNull()

        if (userProgramId != null && MovitData.isInstalled) {

            when (

                val result = MovitData.workoutSession.syncEffectivePlan(

                    userProgramId = userProgramId,

                    weekNumber = weekNumber,

                    dayNumber = dayNumber,

                )

            ) {

                is AppResult.Success -> {

                    effectiveDayPlan = result.value

                    editState = editState.copy(

                        daySessions = ProgramDetailEditMapper.mapSessions(result.value, language),

                        editingDayTitle = dayTitleFor(weekNumber, dayNumber, language),

                    )

                    return

                }

                is AppResult.Failure -> Unit

            }

        }



        effectiveDayPlan = ProgramDetailEditMapper.toBaselinePlan(

            sessions = ProgramDetailEditMapper.fallbackSessions(

                export = loadedProgramExport,

                weekNumber = weekNumber,

                dayNumber = dayNumber,

                language = language,

            ),

            userProgramId = userProgramId.orEmpty(),

            weekNumber = weekNumber,

            dayNumber = dayNumber,

            programId = program.id,

        )

        editState = editState.copy(

            daySessions = ProgramDetailEditMapper.fallbackSessions(

                export = loadedProgramExport,

                weekNumber = weekNumber,

                dayNumber = dayNumber,

                language = language,

            ),

            editingDayTitle = dayTitleFor(weekNumber, dayNumber, language),

        )

    }



    private fun resolveEditingDayNumber(weekNumber: Int): Int {

        val weeks = _state.value.weeks

        val week = weeks.firstOrNull { it.weekNumber == weekNumber }

        return week?.days

            ?.firstOrNull { it.status == ProgramDayStatus.Next }

            ?.dayNumber

            ?: week?.days?.firstOrNull { it.status != ProgramDayStatus.Rest }?.dayNumber

            ?: editState.editingDayNumber

    }



    private suspend fun dayTitleFor(weekNumber: Int, dayNumber: Int, language: String): String {

        val strings = ProgramFlowStrings.load(language)

        return strings.weekTitle(weekNumber) + " · " + strings.dayShort(dayNumber)

    }



    private suspend fun resolveEnrollment(programId: String): ProgramEnrollmentUi {

        if (!MovitData.isInstalled) {

            return ProgramEnrollmentUi(isEnrolled = false)

        }

        val active = MovitData.home.readCached()?.trainMode?.activeProgram
        val export = loadedProgramExport
        val isEnrolled = active != null && (
            active.id == programId ||
                active.id == export?.slug ||
                active.id == export?.id
            )
        val language = resolveLanguage()

        return ProgramEnrollmentUi(

            isEnrolled = isEnrolled,

            startedLabel = if (isEnrolled) localizedString(language, "program_started") else null,

            syncLabel = if (isEnrolled) localizedString(language, "program_synced_today") else null,

        )

    }



    private fun resolveUserProgramIdOrNull(): String? =

        if (MovitData.isInstalled) MovitData.requirePlatform().activeUserProgramId() else null



    private fun resolveUserProgramId(): String =

        resolveUserProgramIdOrNull() ?: "preview-user-program"



    private fun resolveLanguage(): String =

        if (MovitData.isInstalled) MovitData.requirePlatform().preferredLanguage() else "en"



    private suspend fun sessionKeyForProgram(program: ExploreItemUi): String? {

        val export = loadedProgramExport

        if (export != null && MovitData.isInstalled) {

            val language = resolveLanguage()

            val strings = ProgramFlowStrings.load(language)

            val home = MovitData.home.readCached()

            ProgramDetailApiMapper.nextSession(export, home, language, strings)?.sessionWorkoutId?.let {

                return it

            }

            ProgramDetailApiMapper.previewNextSession(export, language, strings)?.sessionWorkoutId?.let {

                return it

            }

        }

        _state.update {

            it.copy(errorMessage = "program_no_upcoming_session")

        }

        return null

    }



    private suspend fun publish(program: ExploreItemUi) {

        val weeks = loadedProgramExport?.let { export ->

            if (!MovitData.isInstalled) {

                emptyList()

            } else {

                val language = resolveLanguage()

                val strings = ProgramFlowStrings.load(language)

                val home = MovitData.home.readCached()

                ProgramDetailApiMapper.mapWeeks(export, home, language, strings)

            }

        }.orEmpty()

        val nextSession = loadedProgramExport?.let { export ->

            if (!MovitData.isInstalled) {

                null

            } else {

                val language = resolveLanguage()

                val strings = ProgramFlowStrings.load(language)

                val home = MovitData.home.readCached()

                if (enrollment.isEnrolled) {
                    ProgramDetailApiMapper.nextSession(export, home, language, strings)
                } else {
                    ProgramDetailApiMapper.previewNextSession(export, language, strings)
                }

            }

        }

        val language = resolveLanguage()

        val detailStrings = ProgramDetailStrings.load(language)

        editState = editState.copy(editingWeekNumber = selectedWeekNumber)

        _state.value = ProgramDetailMapper.map(

            program = program,

            enrollment = enrollment,

            selectedWeekNumber = selectedWeekNumber,

            selectedDayNumber = selectedDayNumber,

            edit = editState,

            weeks = weeks,

            nextSession = nextSession,

            strings = detailStrings,

        ).copy(

            selectedTab = _state.value.selectedTab,

            programId = program.id,

            title = program.title,

            subtitle = program.subtitle,

            weekOffline = resolveWeekOfflineState(program.id, selectedWeekNumber),

        )

    }



    private fun weekOfflineFailedState(message: String): WeekOfflineUiState {
        return when {
            message.isBlank() -> WeekOfflineUiState(
                status = WeekOfflineStatus.Failed,
                errorMessageKey = "program_week_offline_download_failed",
            )
            message.startsWith("program_week_offline_") -> WeekOfflineUiState(
                status = WeekOfflineStatus.Failed,
                errorMessageKey = message,
            )
            else -> WeekOfflineUiState(
                status = WeekOfflineStatus.Failed,
                errorMessage = message,
            )
        }
    }

    private fun resolveWeekOfflineState(programId: String, weekNumber: Int): WeekOfflineUiState {

        val current = _state.value.weekOffline

        if (current.status == WeekOfflineStatus.Downloading) return current

        return if (isWeekOfflineReady(programId, weekNumber)) {

            WeekOfflineUiState(status = WeekOfflineStatus.Ready, progressPercent = 100)

        } else {

            WeekOfflineUiState(

                status = if (current.status == WeekOfflineStatus.Failed) {

                    WeekOfflineStatus.Failed

                } else {

                    WeekOfflineStatus.Idle

                },

                errorMessageKey = current.errorMessageKey,
                errorMessage = current.errorMessage,

            )

        }

    }

}



private suspend fun loadProgramExportForDetail(programId: String): ProgramExportDto? {

    if (!MovitData.isInstalled) return null

    val repo = MovitData.programFlow

    return when (val result = repo.syncProgram(programId)) {

        is AppResult.Success -> result.value

        is AppResult.Failure -> repo.readCachedProgram(programId)

    }

}



private fun defaultPrefetchWeekOffline(): suspend (

    ProgramExportDto,

    Int,

    (Int) -> Unit,

) -> WeekOfflinePackPrefetcher.PrefetchOutcome = { program, weekNumber, onProgress ->

    if (!MovitData.isInstalled) {

        WeekOfflinePackPrefetcher.PrefetchOutcome.Failed("program_week_offline_sign_in_required")

    } else {

        MovitData.weekOfflinePrefetch.prefetchWeek(program, weekNumber) { progress ->

            onProgress(progress.percent)

        }

    }

}



private fun defaultIsWeekOfflineReady(): (String, Int) -> Boolean = { programId, weekNumber ->

    MovitData.isInstalled && MovitData.weekOfflinePrefetch.isWeekReadyOffline(programId, weekNumber)

}



private fun defaultEnrollProgram(): suspend (String) -> AppResult<String> = { programId ->

    if (!MovitData.isInstalled) {

        AppResult.Failure("program_sign_in_to_enroll")

    } else {

        MovitData.plan.enrollProgram(programId)

    }

}



private fun defaultSaveDayCustomizations(): suspend (

    String,

    Int,

    Int,

    UserProgramUpdateRequest,

) -> AppResult<Unit> = { userProgramId, weekNumber, dayNumber, request ->

    if (!MovitData.isInstalled) {

        AppResult.Failure("program_sign_in_to_save_edits")

    } else if (userProgramId == "preview-user-program" || userProgramId.isBlank()) {

        AppResult.Failure("program_sign_in_to_save_edits")

    } else {

        MovitData.workoutSession.saveDayCustomizations(

            userProgramId = userProgramId,

            weekNumber = weekNumber,

            dayNumber = dayNumber,

            request = request,

        )

    }

}


