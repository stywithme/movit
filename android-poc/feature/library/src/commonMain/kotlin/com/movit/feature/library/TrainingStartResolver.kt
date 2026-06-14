package com.movit.feature.library

import com.movit.core.data.MovitData
import com.movit.core.training.session.TrainingFlowItem

/**
 * Resolves training launch from local cache only — no sync/ensure on the Start path.
 */
suspend fun resolveTrainingStart(
    slug: String,
    exerciseName: String,
    targetReps: Int,
    workoutId: String? = null,
    flowItems: List<TrainingFlowItem>? = null,
    startExerciseIndex: Int = 0,
    exerciseId: String? = null,
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
    val config = WorkoutFlowCache.get(workoutId)
    if (config == null) {
        return kmp.copy(
            flowItems = flowItems,
            plannedWorkout = plannedWorkout,
            startExerciseIndex = startExerciseIndex,
        )
    }

    val resolvedIndex = if (startExerciseIndex > 0) {
        startExerciseIndex
    } else {
        config.exercises.indexOfFirst { item ->
            item.exerciseSlug.equals(normalized, ignoreCase = true) ||
                (exerciseId != null && item.id == exerciseId)
        }.let { index -> if (index >= 0) index else 0 }
    }

    return kmp.copy(
        flowItems = flowItems ?: config.toTrainingFlowItems(resolvedIndex),
        plannedWorkout = plannedWorkout,
        startExerciseIndex = resolvedIndex,
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
