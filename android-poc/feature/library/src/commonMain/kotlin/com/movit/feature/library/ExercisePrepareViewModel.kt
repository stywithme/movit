package com.movit.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.movit.core.data.MovitData
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
    private val repository: LibraryRepository = defaultLibraryRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(ExercisePrepareUiState(isLoading = true))
    val state: StateFlow<ExercisePrepareUiState> = _state.asStateFlow()
    private var restTimerJob: Job? = null

    /** Disable in JVM unit tests where [viewModelScope] has no dispatcher. */
    internal var enableRestTicker: Boolean = true

    fun loadInitial() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        val exercise = buildExercise(exerciseId)
        if (exercise == null) {
            _state.update {
                it.copy(isLoading = false, errorMessage = "prepare_not_found")
            }
        } else {
            _state.update {
                it.copy(
                    isLoading = false,
                    exercise = exercise,
                    upNextExercise = ExercisePreparePreviewData.restUpNext,
                    restSeconds = parseRestSeconds(exercise.rest),
                )
            }
        }
    }

    fun enterRestMode() {
        _state.update { it.copy(mode = ExercisePrepareMode.Rest, isRestPaused = false) }
        startRestTimer()
    }

    fun skipRest() {
        restTimerJob?.cancel()
        _state.update { it.copy(mode = ExercisePrepareMode.Prepare, isRestPaused = false) }
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

    fun trainingStartAction(workoutId: String? = null): TrainingStartAction? {
        val exercise = _state.value.displayExercise ?: return null
        val slug = exercise.legacyFileName
        val reps = exercise.reps.filter { it.isDigit() }.toIntOrNull() ?: 12
        return resolveTrainingStartAction(
            slug = slug,
            exerciseName = exercise.name,
            targetReps = reps,
            workoutId = workoutId,
        )
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

    private suspend fun buildExercise(exerciseId: String): ExercisePrepareUi? {
        val preview = ExercisePreparePreviewData.byId(exerciseId)
        if (preview != null) return preview
        val item = repository.findItem(exerciseId) ?: return null
        val slug = legacySlug(exerciseId)
        val media = ExercisePrepareMediaResolver.resolve(
            exerciseSlug = slug,
            language = prepareLanguage(),
            fallbackImageUrl = item.imageUrl,
        )
        return ExercisePrepareUi(
            id = item.id,
            exerciseSlug = slug,
            name = item.title,
            category = item.subtitle,
            sets = "3",
            reps = "12",
            rest = "60s",
            equipment = item.metadata.firstOrNull() ?: "None",
            axesLabel = media.axesLabel,
            distanceTip = "Stand ~2 m from the camera, full body in frame.",
            instructions = media.instructions.ifEmpty { listOf(item.subtitle) },
            targetMuscles = media.targetMuscles.ifEmpty { item.metadata.take(3) },
            sessionProgressPercent = 20,
            sessionSummary = "1 exercise · ~10 min",
            legacyFileName = slug,
            heroImageUrl = media.heroImageUrl,
            poseVariants = media.poseVariants,
            selectedPoseVariantIndex = media.selectedPoseVariantIndex,
        )
    }
}

internal fun legacySlug(exerciseId: String): String = when (exerciseId) {
    "ex-squat", "ex-squat-warm" -> "bodyweight-squat"
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
        state.restSeconds <= 1 -> state.copy(
            mode = ExercisePrepareMode.Prepare,
            isRestPaused = false,
            restSeconds = 0,
        )
        else -> state.copy(restSeconds = state.restSeconds - 1)
    }
}

private const val REST_TICK_MS = 1_000L

private object ExercisePreparePreviewData {
    fun byId(id: String): ExercisePrepareUi? = when (id) {
        "ex-squat", "ex-squat-warm" -> squat.copy(id = id)
        "preview" -> squat
        else -> null
    }

    val squat = ExercisePrepareUi(
        id = "ex-squat",
        exerciseSlug = "bodyweight-squat",
        name = "Bodyweight Squat",
        category = "Lower Body · Quads & Glutes focus",
        sets = "3",
        reps = "15",
        rest = "30s",
        equipment = "None",
        axesLabel = "Left Side - Standing - Full Body",
        distanceTip = "Place your phone 2m away at waist level.",
        instructions = listOf(
            "Stand with feet shoulder-width apart, chest upright.",
            "Lower your hips back and down as if sitting in a chair. Keep knees aligned with toes.",
            "Drive through your heels to return to the starting position. Keep core engaged.",
        ),
        targetMuscles = listOf("Quads", "Glutes", "Hamstrings"),
        sessionProgressPercent = 20,
        sessionSummary = "6 exercises · ~45 min",
        legacyFileName = "bodyweight-squat",
    )

    val restUpNext = ExercisePrepareUi(
        id = "ex-leg-swings",
        exerciseSlug = "leg-swings",
        name = "Leg Swings",
        category = "Lower Body · Hips & Glutes mobility",
        sets = "2",
        reps = "20s",
        repsLabelKey = "prepare_stat_duration",
        rest = "20s",
        equipment = "None",
        axesLabel = "Front View - Standing - Full Body",
        distanceTip = "Place phone 2.5m away, directly in front at chest level.",
        instructions = listOf(
            "Stand tall near a wall or chair for light balance support.",
            "Swing one leg forward and backward smoothly in a controlled arc.",
            "Keep your torso upright and avoid arching your lower back. Swap legs after 20s.",
        ),
        targetMuscles = listOf("Hips", "Glutes", "Hamstrings"),
        sessionProgressPercent = 40,
        sessionSummary = "6 exercises · ~45 min",
        legacyFileName = "leg-swings",
    )
}
