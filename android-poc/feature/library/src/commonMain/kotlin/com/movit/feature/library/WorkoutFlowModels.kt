package com.movit.feature.library

import com.movit.core.data.cache.MovitLruCache
import com.movit.core.training.session.TrainingFlowItem

data class WorkoutFlowExerciseUi(
    val id: String,
    val exerciseSlug: String,
    val name: String,
    val sets: Int,
    val reps: Int?,
    val durationSeconds: Int?,
    val restSeconds: Int = 60,
    val phaseRole: String? = "MAIN",
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

/** Normalizes library/explore ids to training-config slugs (bundled aliases in [TrainingConfigRepository]). */
fun normalizeTrainingSlug(slug: String): String = when {
    slug.startsWith("ex-") -> legacySlug(slug)
    else -> slug
}

/** Resolves shell training entry from local cache — KMP live when a config record exists. */
fun resolveTrainingStartAction(
    slug: String,
    exerciseName: String,
    targetReps: Int,
    workoutId: String? = null,
    exerciseId: String? = null,
): TrainingStartAction? {
    if (!com.movit.core.data.MovitData.isInstalled) return null
    val normalized = resolveCachedTrainingSlug(slug, exerciseId) ?: return null
    return TrainingStartAction.KmpLive(
        slug = normalized,
        exerciseName = exerciseName,
        targetReps = targetReps,
        workoutId = workoutId,
    )
}

/** Start path from workout run / prepare — KMP live camera or legacy [TrainingActivity]. */
sealed interface TrainingStartAction {
    data class KmpLive(
        val slug: String,
        val exerciseName: String,
        val targetReps: Int,
        val workoutId: String? = null,
        val flowItems: List<TrainingFlowItem>? = null,
        val plannedWorkout: PlannedWorkoutLaunch? = null,
        val startExerciseIndex: Int = 0,
    ) : TrainingStartAction

    data class Legacy(val exerciseFileName: String) : TrainingStartAction
}

/** In-memory handoff between workout details, run, prepare, and live training. */
object WorkoutFlowCache {
    private const val MAX_WORKOUTS = 4
    private val configs = MovitLruCache<String, WorkoutFlowConfigUi>(MAX_WORKOUTS)

    fun put(config: WorkoutFlowConfigUi) {
        configs.put(config.workoutId, config)
    }

    fun put(workoutId: String, config: WorkoutFlowConfigUi) {
        configs.put(workoutId, config.copy(workoutId = workoutId))
    }

    fun get(workoutId: String): WorkoutFlowConfigUi? = configs.get(workoutId)

    fun clear(workoutId: String) {
        configs.remove(workoutId)
    }

    internal fun clearAll() {
        configs.clear()
    }
}

internal object WorkoutFlowMapper {
    fun fromSession(session: WorkoutSessionUi, restBetweenSetsSeconds: Int = 60): WorkoutFlowConfigUi {
        val exercises = session.sectionsForTraining()
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
                    restSeconds = block.restSeconds,
                    phaseRole = block.phaseRole,
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
