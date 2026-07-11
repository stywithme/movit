package com.movit.core.training.session

import com.movit.core.training.boundary.DeviceTiltPort
import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.config.PositionCheck
import com.movit.core.training.position.PoseSceneExpectation
import com.movit.core.training.position.PositionValidator
import com.movit.core.training.position.resolveSceneExpectation

/**
 * Shared construction path for training-session gates and debug-lab synthetic checks.
 */
object TrainingGateFactory {
    fun buildPositionValidator(
        positionChecks: List<PositionCheck>,
        sceneExpectation: PoseSceneExpectation,
        tiltSource: DeviceTiltPort? = null,
        alwaysCollectDebugChecks: Boolean = false,
    ): PositionValidator = PositionValidator(
        positionChecks = positionChecks,
        sceneExpectation = sceneExpectation,
        tiltSource = tiltSource,
        alwaysCollectDebugChecks = alwaysCollectDebugChecks,
    )

    fun buildPositionValidatorForExercise(
        exerciseConfig: ExerciseConfig,
        poseVariantIndex: Int,
        tiltSource: DeviceTiltPort? = null,
    ): PositionValidator? {
        if (!exerciseConfig.hasAnyPositionChecks(poseVariantIndex)) return null
        val poseVariant = exerciseConfig.poseVariants[poseVariantIndex]
        return buildPositionValidator(
            positionChecks = poseVariant.positionChecks,
            sceneExpectation = poseVariant.resolveSceneExpectation(),
            tiltSource = tiltSource,
        )
    }
}
