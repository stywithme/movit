package com.movit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.movit.core.data.MovitData
import com.movit.core.data.repository.TrainingConfigEnsureResult
import com.movit.core.data.repository.ensure
import kotlinx.coroutines.launch

enum class ExercisePrepareMode {
    Prepare,
    Rest,
}

data class ExercisePrepareUi(
    val id: String,
    val exerciseSlug: String,
    val name: String,
    val category: String,
    val sets: String,
    val reps: String,
    val repsLabelKey: String = "prepare_stat_reps",
    val rest: String,
    val equipment: String,
    val axesLabel: String,
    val distanceTip: String,
    val instructions: List<String> = emptyList(),
    val targetMuscles: List<String> = emptyList(),
    val sessionProgressPercent: Int,
    val sessionSummary: String,
    val legacyFileName: String,
    val heroImageUrl: String? = null,
    val poseVariants: List<PreparePoseVariantUi> = emptyList(),
    val selectedPoseVariantIndex: Int = 0,
)

data class ExercisePrepareUiState(
    val isLoading: Boolean = false,
    val mode: ExercisePrepareMode = ExercisePrepareMode.Prepare,
    val exercise: ExercisePrepareUi? = null,
    val upNextExercise: ExercisePrepareUi? = null,
    val restSeconds: Int = 30,
    val isRestPaused: Boolean = false,
    val errorMessage: String? = null,
    val isEnsuringConfig: Boolean = false,
    val trainingConfigUnavailableMessage: String? = null,
) {
    val displayExercise: ExercisePrepareUi?
        get() = when (mode) {
            ExercisePrepareMode.Prepare -> exercise
            ExercisePrepareMode.Rest -> upNextExercise ?: exercise
        }

    val headerProgressPercent: Int
        get() = when (mode) {
            ExercisePrepareMode.Prepare -> exercise?.sessionProgressPercent ?: 20
            ExercisePrepareMode.Rest -> 40
        }
}

class ExercisePrepareViewModel(
    private val exerciseId: String,
    private val workoutId: String? = null,
    private val initialMode: ExercisePrepareMode = ExercisePrepareMode.Prepare,
    private val initialRestSeconds: Int? = null,
    private val upNextExerciseId: String? = null,
    private val repository: LibraryRepository = defaultLibraryRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(ExercisePrepareUiState(isLoading = true))
    val state: StateFlow<ExercisePrepareUiState> = _state.asStateFlow()
    private val _effects = MutableSharedFlow<ExercisePrepareEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<ExercisePrepareEffect> = _effects.asSharedFlow()
    private val _startEffects = MutableSharedFlow<TrainingStartAction>(extraBufferCapacity = 1)
    val startEffects: SharedFlow<TrainingStartAction> = _startEffects.asSharedFlow()
    private var restTimerJob: Job? = null

    /** Disable in JVM unit tests where [viewModelScope] has no dispatcher. */
    internal var enableRestTicker: Boolean = true

    fun onEvent(event: ExercisePrepareEvent) {
        when (event) {
            is ExercisePrepareEvent.StartClicked -> requestTrainingStart(workoutId = event.workoutId)
            ExercisePrepareEvent.SkipRest -> skipRest()
            ExercisePrepareEvent.ToggleRestPause -> toggleRestPause()
            ExercisePrepareEvent.AddRestTime -> addRestTime()
            is ExercisePrepareEvent.PoseVariantSelected -> selectPoseVariant(event.index)
            ExercisePrepareEvent.RetryClicked -> Unit
        }
    }

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        if (MovitData.isInstalled) {
            MovitData.bootstrapLocalCaches()
        }
        _state.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                isEnsuringConfig = false,
                trainingConfigUnavailableMessage = null,
            )
        }
        val exercise = ExerciseContentMapper.loadWorkoutExercise(exerciseId, workoutId)
            ?: ExerciseContentMapper.loadExercise(exerciseId, repository)
        if (exercise == null) {
            _state.update {
                it.copy(isLoading = false, errorMessage = "prepare_not_found")
            }
        } else {
            val upNext = upNextExerciseId?.let {
                ExerciseContentMapper.loadWorkoutExercise(it, workoutId)
                    ?: ExerciseContentMapper.loadExercise(it, repository)
            }
            val restSeconds = initialRestSeconds ?: parseRestSeconds(exercise.rest)
            _state.update {
                it.copy(
                    isLoading = false,
                    mode = initialMode,
                    exercise = exercise,
                    upNextExercise = upNext,
                    restSeconds = restSeconds,
                    isRestPaused = false,
                )
            }
            val configSlug = exercise.legacyFileName.ifBlank { exercise.exerciseSlug }
            logTrainingConfigCache(
                context = "enter_prepare",
                snapshot = inspectTrainingConfigCache(
                    slug = configSlug,
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                ),
                workoutId = workoutId,
            )
            if (initialMode == ExercisePrepareMode.Rest) {
                startRestTimer()
            }
        }
    }

    fun enterRestMode() {
        _state.update { it.copy(mode = ExercisePrepareMode.Rest, isRestPaused = false) }
        startRestTimer()
    }

    fun skipRest() {
        restTimerJob?.cancel()
        _state.update { current -> transitionRestToPrepare(current) }
    }

    override fun onCleared() {
        restTimerJob?.cancel()
        super.onCleared()
    }

    private fun startRestTimer() {
        if (!enableRestTicker) return
        restTimerJob?.cancel()
        restTimerJob = viewModelScope.launch {
            while (_state.value.mode == ExercisePrepareMode.Rest) {
                delay(REST_TICK_MS)
                _state.update { current -> applyRestSecondTick(current) }
            }
        }
    }

    fun toggleRestPause() {
        _state.update { it.copy(isRestPaused = !it.isRestPaused) }
    }

    fun addRestTime() {
        _state.update { it.copy(restSeconds = it.restSeconds + 15) }
    }

    fun requestTrainingStart(workoutId: String? = null) {
        if (_state.value.isEnsuringConfig) return
        val exercise = _state.value.displayExercise ?: return
        viewModelScope.launch {
            val slug = exercise.legacyFileName.ifBlank { exercise.exerciseSlug }
            val cacheSnapshot = inspectTrainingConfigCache(
                slug = slug,
                exerciseId = exercise.id,
                exerciseName = exercise.name,
            )
            logTrainingConfigCache(
                context = "start_pressed",
                snapshot = cacheSnapshot,
                workoutId = workoutId,
            )
            _state.update {
                it.copy(
                    isEnsuringConfig = true,
                    trainingConfigUnavailableMessage = null,
                )
            }
            val ensured = runCatching {
                ensureTrainingConfig(
                    slug = slug,
                    workoutTemplateId = workoutId,
                )
            }.getOrElse {
                TrainingConfigEnsureResult.Unavailable(
                    TrainingConfigEnsureResult.Unavailable.Reason.NotFoundAfterSync,
                )
            }
            val resolvedSnapshot = inspectTrainingConfigCache(
                slug = slug,
                exerciseId = exercise.id,
                exerciseName = exercise.name,
            )
            if (ensured is TrainingConfigEnsureResult.Unavailable) {
                val message = when (ensured.reason) {
                    TrainingConfigEnsureResult.Unavailable.Reason.Offline ->
                        "training_config_offline_unavailable"
                    TrainingConfigEnsureResult.Unavailable.Reason.NotFoundAfterSync ->
                        "training_config_first_use_online"
                }
                _state.update {
                    it.copy(
                        isEnsuringConfig = false,
                        trainingConfigUnavailableMessage = message,
                    )
                }
                logTrainingConfigCache(
                    context = "start_blocked",
                    snapshot = resolvedSnapshot,
                    workoutId = workoutId,
                    extra = "reason=${ensured.reason}",
                )
                return@launch
            }
            val reps = exercise.reps.filter { it.isDigit() }.toIntOrNull() ?: 12
            val flowConfig = workoutId?.let { WorkoutFlowCache.get(it) }
            val progress = workoutId?.let { WorkoutRunProgressStore.read(it) }
            val startIndex = progress?.exerciseIndex ?: 0
            val flowItems = flowConfig?.toTrainingFlowItems(startIndex)
            val action = resolveTrainingStart(
                slug = slug,
                exerciseName = exercise.name,
                targetReps = reps,
                workoutId = workoutId,
                flowItems = flowItems,
                startExerciseIndex = startIndex,
                exerciseId = exercise.id,
            )
            if (action == null) {
                _state.update {
                    it.copy(
                        isEnsuringConfig = false,
                        trainingConfigUnavailableMessage = "training_config_first_use_online",
                    )
                }
                logTrainingConfigCache(
                    context = "start_blocked",
                    snapshot = resolvedSnapshot,
                    workoutId = workoutId,
                    extra = "reason=no_cached_config",
                )
                return@launch
            }
            _state.update {
                it.copy(
                    isEnsuringConfig = false,
                    trainingConfigUnavailableMessage = null,
                )
            }
            logTrainingConfigCache(
                context = "start_launching",
                snapshot = resolvedSnapshot,
                workoutId = workoutId,
                extra = "launchSlug=${(action as? TrainingStartAction.KmpLive)?.slug}",
            )
            val resolved = when (action) {
                is TrainingStartAction.KmpLive -> action.copy(
                    plannedWorkout = workoutId?.let { resolvePlannedWorkoutLaunch(it, null) },
                    poseVariantIndex = exercise.selectedPoseVariantIndex,
                )
                else -> action
            }
            _effects.emit(ExercisePrepareEffect.StartTraining(resolved))
            _startEffects.emit(resolved)
        }
    }

    fun legacyFileNameForStart(): String? = _state.value.exercise?.legacyFileName

    fun selectPoseVariant(index: Int) {
        val exercise = _state.value.displayExercise ?: return
        val media = ExercisePrepareMediaResolver.withSelectedVariant(
            media = ExercisePrepareMediaUi(
                heroImageUrl = exercise.heroImageUrl,
                poseVariants = exercise.poseVariants,
                selectedPoseVariantIndex = exercise.selectedPoseVariantIndex,
                axesLabel = exercise.axesLabel,
                instructions = exercise.instructions,
                targetMuscles = exercise.targetMuscles,
            ),
            index = index,
            language = prepareLanguage(),
            exerciseSlug = exercise.exerciseSlug,
        )
        _state.update { current ->
            val updateTarget: (ExercisePrepareUi) -> ExercisePrepareUi = { target ->
                target.copy(
                    heroImageUrl = media.heroImageUrl,
                    selectedPoseVariantIndex = media.selectedPoseVariantIndex,
                    axesLabel = media.axesLabel,
                )
            }
            current.copy(
                exercise = current.exercise?.let(updateTarget),
                upNextExercise = current.upNextExercise?.let(updateTarget),
            )
        }
    }

    private fun prepareLanguage(): String =
        if (MovitData.isInstalled) MovitData.requirePlatform().preferredLanguage() else "en"

    private suspend fun ensureTrainingConfig(
        slug: String,
        workoutTemplateId: String?,
    ): TrainingConfigEnsureResult {
        if (!MovitData.isInstalled) {
            return TrainingConfigEnsureResult.Unavailable(
                TrainingConfigEnsureResult.Unavailable.Reason.NotFoundAfterSync,
            )
        }
        return MovitData.trainingConfig.ensure(
            slug = slug,
            workoutTemplateId = workoutTemplateId,
        )
    }

}

internal fun transitionRestToPrepare(state: ExercisePrepareUiState): ExercisePrepareUiState =
    state.copy(
        mode = ExercisePrepareMode.Prepare,
        exercise = state.upNextExercise ?: state.exercise,
        upNextExercise = null,
        isRestPaused = false,
        restSeconds = 0,
    )

internal fun legacySlug(exerciseId: String): String = when (exerciseId) {
    else -> exerciseId.removePrefix("ex-")
}

internal fun parseRestSeconds(restLabel: String): Int {
    val digits = restLabel.filter { it.isDigit() }
    return digits.toIntOrNull()?.coerceAtLeast(0) ?: 30
}

internal fun formatRestTimer(seconds: Int): String {
    val clamped = seconds.coerceAtLeast(0)
    val minutes = clamped / 60
    val remainder = clamped % 60
    return "${minutes.toString().padStart(2, '0')}:${remainder.toString().padStart(2, '0')}"
}

internal fun applyRestSecondTick(state: ExercisePrepareUiState): ExercisePrepareUiState {
    if (state.mode != ExercisePrepareMode.Rest || state.isRestPaused) return state
    return when {
        state.restSeconds <= 1 -> transitionRestToPrepare(state)
        else -> state.copy(restSeconds = state.restSeconds - 1)
    }
}

private const val REST_TICK_MS = 1_000L
