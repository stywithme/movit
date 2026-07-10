package com.movit.feature.library

import com.movit.core.data.MovitData
import com.movit.core.training.session.TrainingFlowItem

/**
 * Resolves training launch from local cache only — no sync/ensure on the Start path.
 *
 * @param lockStartIndex when true (workout Start / [ExercisePrepareMode.WorkoutFirstExercise]),
 *   never re-derive index from the prepare exercise id (preview must not change start).
 */
suspend fun resolveTrainingStart(
    slug: String,
    exerciseName: String,
    targetReps: Int,
    workoutId: String? = null,
    flowItems: List<TrainingFlowItem>? = null,
    startExerciseIndex: Int = 0,
    exerciseId: String? = null,
    lockStartIndex: Boolean = false,
): TrainingStartAction? {
    if (!MovitData.isInstalled) return null
    MovitData.bootstrapLocalCaches()

    val normalized = resolveCachedTrainingSlug(slug, exerciseId) ?: return null
    val base = resolveTrainingStartAction(
        slug = normalized,
        exerciseName = exerciseName,
        targetReps = targetReps,
        workoutId = workoutId,
        exerciseId = exerciseId,
    ) ?: TrainingStartAction.KmpLive(
        slug = normalized,
        exerciseName = exerciseName,
        targetReps = targetReps,
        workoutId = workoutId,
    )

    val kmp = base as? TrainingStartAction.KmpLive ?: return base
    if (workoutId.isNullOrBlank()) return kmp

    val plannedWorkout = resolvePlannedWorkoutLaunch(workoutId, sessionContext = null)
    val run = WorkoutRunStore.activeForWorkout(workoutId)
    val config = WorkoutFlowCache.get(workoutId)
    if (run == null && config == null) {
        return kmp.copy(
            flowItems = flowItems,
            plannedWorkout = plannedWorkout,
            startExerciseIndex = startExerciseIndex,
            runId = null,
        )
    }

    // ponytail: lockStartIndex covers WorkoutFirstExercise; unlocked path keeps solo-in-workout match.
    val resolvedIndex = when {
        lockStartIndex || flowItems != null -> startExerciseIndex.coerceAtLeast(0)
        startExerciseIndex > 0 -> startExerciseIndex
        else -> {
            val exercises = run?.snapshot?.exercises.orEmpty()
            if (exercises.isNotEmpty()) {
                exercises.indexOfFirst { item ->
                    item.slug.equals(normalized, ignoreCase = true) ||
                        (exerciseId != null && item.exerciseId == exerciseId)
                }.let { index -> if (index >= 0) index else 0 }
            } else {
                config!!.exercises.indexOfFirst { item ->
                    item.exerciseSlug.equals(normalized, ignoreCase = true) ||
                        (exerciseId != null && item.id == exerciseId)
                }.let { index -> if (index >= 0) index else 0 }
            }
        }
    }

    val resolvedFlow = flowItems
        ?: run?.snapshot?.toTrainingFlowItems(resolvedIndex)
        ?: config?.toTrainingFlowItems(resolvedIndex)

    return kmp.copy(
        flowItems = resolvedFlow,
        plannedWorkout = plannedWorkout,
        startExerciseIndex = resolvedIndex,
        runId = run?.runId?.value,
    )
}

/** Finds a slug with a cached training config (index or on-disk record). */
fun resolveCachedTrainingSlug(slug: String, exerciseId: String? = null): String? {
    if (!MovitData.isInstalled) return null
    val candidates = listOfNotNull(
        slug,
        exerciseId,
        normalizeTrainingSlug(slug),
        exerciseId?.let(::normalizeTrainingSlug),
    )
    MovitData.trainingConfig.resolveAvailableSlug(*candidates.toTypedArray())?.let { return it }
    return candidates.firstNotNullOfOrNull { candidate ->
        MovitData.trainingConfig.getBySlug(candidate)?.slug?.takeIf { it.isNotBlank() }
    }
}
