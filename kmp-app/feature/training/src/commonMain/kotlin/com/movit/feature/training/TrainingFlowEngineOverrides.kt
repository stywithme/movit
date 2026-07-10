package com.movit.feature.training

import com.movit.core.training.session.TrainingFlowItem
import com.movit.core.training.session.TrainingSessionFlowCoordinator

/**
 * Maps [TrainingFlowItem.Exercise] session customizations into engine constructor args.
 * Keeps duration/weight wiring testable without a full ViewModel.
 */
object TrainingFlowEngineOverrides {
    fun targetDurationSeconds(exercise: TrainingFlowItem.Exercise?): Int? =
        exercise?.targetDurationSeconds?.takeIf { it > 0 }

    /** 1-based [setNumber]; falls back to last listed weight when set exceeds the list. */
    fun weightKgForSet(exercise: TrainingFlowItem.Exercise?, setNumber: Int): Float? {
        val weights = exercise?.weightPerSetKg?.takeIf { it.isNotEmpty() } ?: return null
        val index = (setNumber.coerceAtLeast(1) - 1).coerceIn(0, weights.lastIndex)
        return weights[index].takeIf { it > 0f }
    }

    fun weightKgForRestPreview(
        exercise: TrainingFlowItem.Exercise,
        upcomingSetNumber: Int,
        restContext: TrainingSessionFlowCoordinator.RestContext,
    ): Float? = weightKgForSet(
        exercise = exercise,
        setNumber = when (restContext) {
            TrainingSessionFlowCoordinator.RestContext.BETWEEN_SETS -> upcomingSetNumber
            TrainingSessionFlowCoordinator.RestContext.BETWEEN_EXERCISES -> 1
        },
    )
}
