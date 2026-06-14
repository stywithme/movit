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
    val restMs = restBetweenSetsSeconds.coerceAtLeast(0) * 1_000L
    return exercises
        .drop(startExerciseIndex.coerceAtLeast(0))
        .map { exercise ->
            val exerciseRestMs = exercise.restSeconds.coerceAtLeast(0) * 1_000L
            TrainingFlowItem.Exercise(
                slug = resolveFlowExerciseSlug(exercise),
                displayName = exercise.name,
                sets = exercise.sets.coerceAtLeast(1),
                targetReps = exercise.reps ?: 12,
                restBetweenSetsMs = exerciseRestMs.takeIf { it > 0 } ?: restMs,
                restAfterExerciseMs = exerciseRestMs.takeIf { it > 0 } ?: restMs,
                phaseRole = exercise.phaseRole,
            )
        }
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
