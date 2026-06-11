package com.movit.feature.library

data class WorkoutFlowExerciseUi(
    val id: String,
    val exerciseSlug: String,
    val name: String,
    val sets: Int,
    val reps: Int?,
    val durationSeconds: Int?,
)

data class WorkoutFlowConfigUi(
    val workoutId: String,
    val title: String,
    val subtitle: String,
    val exercises: List<WorkoutFlowExerciseUi>,
    val restBetweenSetsSeconds: Int = 60,
) {
    val exerciseCount: Int get() = exercises.size

    val estimatedMinutes: Int
        get() {
            val totalSeconds = exercises.sumOf { ex ->
                val sets = ex.sets.coerceAtLeast(1)
                val perSet = (ex.durationSeconds ?: (ex.reps ?: 10) * 3) + restBetweenSetsSeconds
                sets * perSet
            }
            return (totalSeconds / 60).coerceAtLeast(1)
        }

    fun summaryLabel(restSeconds: Int = restBetweenSetsSeconds): String =
        "$exerciseCount exercises · ${restSeconds}s rest"
}

enum class WorkoutRunExerciseStatus {
    Pending,
    Active,
    Done,
}

data class WorkoutRunSequenceItemUi(
    val index: Int,
    val name: String,
    val status: WorkoutRunExerciseStatus,
)

data class WorkoutCustomizeUiState(
    val isLoading: Boolean = false,
    val config: WorkoutFlowConfigUi? = null,
    val errorMessage: String? = null,
)

data class WorkoutRunUiState(
    val isLoading: Boolean = false,
    val config: WorkoutFlowConfigUi? = null,
    val currentExerciseIndex: Int = 0,
    val currentSet: Int = 1,
    val previousFormPercent: Int? = null,
    val previousFormTip: String? = null,
    val errorMessage: String? = null,
) {
    val currentExercise: WorkoutFlowExerciseUi?
        get() = config?.exercises?.getOrNull(currentExerciseIndex)

    val progressPercent: Int
        get() {
            val exercises = config?.exercises.orEmpty()
            if (exercises.isEmpty()) return 0
            val totalSets = exercises.sumOf { it.sets.coerceAtLeast(1) }
            if (totalSets == 0) return 0
            val completedSets = exercises.take(currentExerciseIndex).sumOf { it.sets.coerceAtLeast(1) }
            val current = currentExercise?.sets?.coerceAtLeast(1) ?: 1
            val setProgress = (currentSet - 1).coerceAtLeast(0)
            return (((completedSets + setProgress).toFloat() / totalSets) * 100).toInt().coerceIn(0, 100)
        }

    fun sequenceItems(): List<WorkoutRunSequenceItemUi> =
        config?.exercises.orEmpty().mapIndexed { index, exercise ->
            val status = when {
                index < currentExerciseIndex -> WorkoutRunExerciseStatus.Done
                index == currentExerciseIndex -> WorkoutRunExerciseStatus.Active
                else -> WorkoutRunExerciseStatus.Pending
            }
            WorkoutRunSequenceItemUi(index = index + 1, name = exercise.name, status = status)
        }
}

/** Resolves Explore/Library/Workout training entry when [MovitTrainingKmpGate] is on. */
fun resolveTrainingStartAction(
    slug: String,
    exerciseName: String,
    targetReps: Int,
    workoutId: String? = null,
): TrainingStartAction? {
    val kmpReady = com.movit.core.data.MovitData.isInstalled &&
        com.movit.core.data.MovitData.trainingConfig.supports(slug)
    return when {
        kmpReady -> TrainingStartAction.KmpLive(
            slug = slug,
            exerciseName = exerciseName,
            targetReps = targetReps,
            workoutId = workoutId,
        )
        com.movit.core.data.MovitTrainingKmpGate.enabled -> null
        else -> TrainingStartAction.Legacy(slug)
    }
}

/** Start path from workout run / prepare — KMP live camera or legacy [TrainingActivity]. */
sealed interface TrainingStartAction {
    data class KmpLive(
        val slug: String,
        val exerciseName: String,
        val targetReps: Int,
        val workoutId: String? = null,
    ) : TrainingStartAction

    data class Legacy(val exerciseFileName: String) : TrainingStartAction
}

/** In-memory handoff between customize → run (Phase 05 shell; no persisted plan write). */
object WorkoutFlowCache {
    private val configs = mutableMapOf<String, WorkoutFlowConfigUi>()

    fun put(config: WorkoutFlowConfigUi) {
        configs[config.workoutId] = config
    }

    fun get(workoutId: String): WorkoutFlowConfigUi? = configs[workoutId]

    fun clear(workoutId: String) {
        configs.remove(workoutId)
    }

    internal fun clearAll() {
        configs.clear()
    }
}

internal object WorkoutFlowMapper {
    fun fromSession(session: WorkoutSessionUi, restBetweenSetsSeconds: Int = 60): WorkoutFlowConfigUi {
        val exercises = session.sections
            .flatMap { it.items }
            .filterIsInstance<WorkoutSessionBlockUi.Exercise>()
            .map { block ->
                WorkoutFlowExerciseUi(
                    id = block.id,
                    exerciseSlug = block.exerciseSlug,
                    name = block.name,
                    sets = block.sets,
                    reps = block.reps,
                    durationSeconds = block.durationSeconds,
                )
            }
        return WorkoutFlowConfigUi(
            workoutId = session.id,
            title = session.title,
            subtitle = session.subtitle,
            exercises = exercises,
            restBetweenSetsSeconds = restBetweenSetsSeconds,
        )
    }
}
