package com.movit.feature.library

import com.movit.core.data.MovitData
import com.movit.core.training.session.TrainingFlowItem

/**
 * Maps library workout models into KMP training session launch args (G1 / §14.2).
 */
data class PlannedWorkoutLaunch(
    val plannedWorkoutId: String,
    val programId: String?,
    val weekNumber: Int,
    val dayNumber: Int,
)

fun WorkoutFlowConfigUi.toTrainingFlowItems(
    startExerciseIndex: Int = 0,
): List<TrainingFlowItem> {
    val defaultRestMs = restBetweenSetsSeconds.coerceAtLeast(0) * 1_000L
    return exercises
        .drop(startExerciseIndex.coerceAtLeast(0))
        .map { exercise ->
            val betweenMs = exercise.restSeconds.coerceAtLeast(0) * 1_000L
            val afterMs = exercise.restAfterExerciseSeconds.coerceAtLeast(0) * 1_000L
            val isDuration = exercise.durationSeconds != null && exercise.reps == null
            TrainingFlowItem.Exercise(
                slug = resolveFlowExerciseSlug(exercise),
                displayName = exercise.name,
                sets = exercise.sets.coerceAtLeast(1),
                // No invented targets — missing reps → 0 (same as toRunSnapshot / !isStartable).
                targetReps = when {
                    isDuration -> 0
                    else -> exercise.reps ?: 0
                },
                targetDurationSeconds = exercise.durationSeconds.takeIf { isDuration || exercise.reps == null },
                restBetweenSetsMs = betweenMs.takeIf { it > 0 } ?: defaultRestMs,
                restAfterExerciseMs = afterMs.takeIf { it > 0 }
                    ?: betweenMs.takeIf { it > 0 }
                    ?: defaultRestMs,
                phaseRole = exercise.phaseRole,
                poseVariantIndex = exercise.variantIndex,
                weightPerSetKg = exercise.weightPerSetKg,
            )
        }
}

/**
 * Prefer canonical [WorkoutRunSnapshot] when available; falls back to [WorkoutFlowConfigUi].
 */
fun resolveTrainingFlowItems(
    workoutId: String,
    startExerciseIndex: Int = 0,
): List<TrainingFlowItem>? {
    WorkoutRunStore.activeForWorkout(workoutId)?.snapshot?.let { snapshot ->
        if (!snapshot.isStartable) return null
        return snapshot.toTrainingFlowItems(startExerciseIndex)
    }
    return WorkoutFlowCache.get(workoutId)?.toTrainingFlowItems(startExerciseIndex)
}

private fun resolveFlowExerciseSlug(exercise: WorkoutFlowExerciseUi): String {
    if (MovitData.isInstalled) {
        MovitData.trainingConfig.resolveAvailableSlug(
            exercise.exerciseSlug,
            exercise.id,
            normalizeTrainingSlug(exercise.exerciseSlug),
            normalizeTrainingSlug(exercise.id),
        )?.let { return it }
    }
    return normalizeTrainingSlug(exercise.exerciseSlug.ifBlank { exercise.id })
}

fun WorkoutSessionContextUi.toPlannedLaunch(): PlannedWorkoutLaunch = PlannedWorkoutLaunch(
    plannedWorkoutId = plannedWorkoutId,
    programId = programId,
    weekNumber = weekNumber,
    dayNumber = dayNumber,
)

fun resolvePlannedWorkoutLaunch(
    workoutId: String,
    sessionContext: WorkoutSessionContextUi?,
): PlannedWorkoutLaunch? {
    sessionContext?.let { return it.toPlannedLaunch() }
    val parsed = WorkoutSessionKeys.parse(workoutId) ?: return null
    return PlannedWorkoutLaunch(
        plannedWorkoutId = parsed.plannedWorkoutId,
        programId = parsed.programId,
        weekNumber = parsed.weekNumber,
        dayNumber = parsed.dayNumber,
    )
}
