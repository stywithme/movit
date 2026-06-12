package com.movit.feature.library



import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope

import com.movit.core.data.MovitData

import com.movit.core.network.dto.EffectivePlanPayloadDto

import com.movit.core.network.dto.ProgramExportDto

import com.movit.core.network.dto.UserProgramUpdateRequest

import com.movit.core.model.ExploreItemUi

import com.movit.resources.strings.ProgramDetailStrings

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

    private val saveDayCustomizations: suspend (

        userProgramId: String,

        weekNumber: Int,

        dayNumber: Int,

        request: UserProgramUpdateRequest,

    ) -> AppResult<Unit> = defaultSaveDayCustomizations(),

) : ViewModel() {

    private val toastScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var loadedProgram: ExploreItemUi? = null

    private var loadedProgramExport: ProgramExportDto? = null

    private var effectiveDayPlan: EffectivePlanPayloadDto? = null

    private var removedSessionIds = mutableSetOf<String>()

    private var removedExerciseIds = mutableSetOf<String>()

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



    fun onWeekSelected(weekNumber: Int) {

        selectedWeekNumber = weekNumber

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

            syncLabel = "Synced today",

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

                val language = resolveLanguage()

                val strings = ProgramFlowStrings.load(language)

                val home = MovitData.home.readCached()

                ProgramDetailApiMapper.mapWeeks(export, home, language, strings)

            }

        }.orEmpty()

        val nextSession = loadedProgramExport?.let { export ->

            if (!MovitData.isInstalled || !enrollment.isEnrolled) {

                null

            } else {

                val language = resolveLanguage()

                val strings = ProgramFlowStrings.load(language)

                val home = MovitData.home.readCached()

                ProgramDetailApiMapper.nextSession(export, home, language, strings)

            }

        }

        val language = resolveLanguage()

        val detailStrings = ProgramDetailStrings.load(language)

        editState = editState.copy(editingWeekNumber = selectedWeekNumber)

        _state.value = ProgramDetailMapper.map(

            program = program,

            enrollment = enrollment,

            selectedWeekNumber = selectedWeekNumber,

            edit = editState,

            weeks = weeks,

            nextSession = nextSession,

            strings = detailStrings,

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



private fun defaultSaveDayCustomizations(): suspend (

    String,

    Int,

    Int,

    UserProgramUpdateRequest,

) -> AppResult<Unit> = { userProgramId, weekNumber, dayNumber, request ->

    if (!MovitData.isInstalled) {

        AppResult.Failure("Sign in to save program edits.")

    } else if (userProgramId == "preview-user-program" || userProgramId.isBlank()) {

        AppResult.Failure("Sign in to save program edits.")

    } else {

        MovitData.workoutSession.saveDayCustomizations(

            userProgramId = userProgramId,

            weekNumber = weekNumber,

            dayNumber = dayNumber,

            request = request,

        )

    }

}


