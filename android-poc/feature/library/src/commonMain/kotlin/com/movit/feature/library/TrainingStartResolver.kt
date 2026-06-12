package com.movit.feature.library

import com.movit.core.data.MovitData
import com.movit.core.data.repository.TrainingConfigEnsureResult
import com.movit.core.data.repository.ensure
import com.movit.core.training.session.TrainingFlowItem

sealed interface TrainingStartResolveResult {
    data class Ready(val action: TrainingStartAction) : TrainingStartResolveResult

    data class Unavailable(
        val reason: TrainingConfigEnsureResult.Unavailable.Reason,
    ) : TrainingStartResolveResult
}

data class TrainingConfigUnavailableUi(
    val messageKey: String,
    val canSync: Boolean,
)

fun TrainingConfigEnsureResult.Unavailable.Reason.toUi(): TrainingConfigUnavailableUi =
    when (this) {
        TrainingConfigEnsureResult.Unavailable.Reason.Offline ->
            TrainingConfigUnavailableUi(
                messageKey = "training_config_offline_unavailable",
                canSync = false,
            )
        TrainingConfigEnsureResult.Unavailable.Reason.NotFoundAfterSync ->
            TrainingConfigUnavailableUi(
                messageKey = "training_config_first_use_online",
                canSync = true,
            )
    }

suspend fun resolveTrainingStartWithEnsure(
    slug: String,
    exerciseName: String,
    targetReps: Int,
    workoutId: String? = null,
    flowItems: List<TrainingFlowItem>? = null,
    startExerciseIndex: Int = 0,
    exerciseId: String? = null,
): TrainingStartResolveResult {
    if (!MovitData.isInstalled) {
        return TrainingStartResolveResult.Unavailable(
            TrainingConfigEnsureResult.Unavailable.Reason.NotFoundAfterSync,
        )
    }
    MovitData.bootstrapLocalCaches()

    val normalized = normalizeTrainingSlug(slug)
    when (val ensureResult = MovitData.trainingConfig.ensure(
        slug = normalized,
        workoutTemplateId = workoutId,
    )) {
        is TrainingConfigEnsureResult.Available -> Unit
        is TrainingConfigEnsureResult.Unavailable ->
            return TrainingStartResolveResult.Unavailable(ensureResult.reason)
    }

    val base = resolveTrainingStartAction(
        slug = slug,
        exerciseName = exerciseName,
        targetReps = targetReps,
        workoutId = workoutId,
    ) ?: return TrainingStartResolveResult.Unavailable(
        TrainingConfigEnsureResult.Unavailable.Reason.NotFoundAfterSync,
    )

    val kmp = base as? TrainingStartAction.KmpLive ?: return TrainingStartResolveResult.Ready(base)
    if (workoutId.isNullOrBlank()) return TrainingStartResolveResult.Ready(kmp)

    val plannedWorkout = resolvePlannedWorkoutLaunch(workoutId, sessionContext = null)
    val config = WorkoutFlowCache.get(workoutId)
    if (config == null) {
        return TrainingStartResolveResult.Ready(
            kmp.copy(
                flowItems = flowItems,
                plannedWorkout = plannedWorkout,
                startExerciseIndex = startExerciseIndex,
            ),
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

    return TrainingStartResolveResult.Ready(
        kmp.copy(
            flowItems = flowItems ?: config.toTrainingFlowItems(resolvedIndex),
            plannedWorkout = plannedWorkout,
            startExerciseIndex = resolvedIndex,
        ),
    )
}
