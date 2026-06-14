package com.movit.feature.training

import com.movit.core.training.session.TrainingFlowItem

/**
 * Resolves the active pose variant for a training session (P2 / §34.2 row 15).
 *
 * Route-level index wins for single-exercise entry; workout flow items override per exercise.
 */
object TrainingPoseVariantResolver {
    fun resolve(
        routePoseVariantIndex: Int,
        flowExercise: TrainingFlowItem.Exercise?,
        variantCount: Int,
    ): Int {
        val requested = flowExercise?.poseVariantIndex ?: routePoseVariantIndex
        if (variantCount <= 0) return 0
        return requested.coerceIn(0, variantCount - 1)
    }
}
