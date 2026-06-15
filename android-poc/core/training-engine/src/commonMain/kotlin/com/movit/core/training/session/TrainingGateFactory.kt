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
        posePositionCode: String,
        sceneExpectation: PoseSceneExpectation,
        tiltSource: DeviceTiltPort? = null,
    ): PositionValidator = PositionValidator(
        positionChecks = positionChecks,
        posePositionCode = posePositionCode,
        sceneExpectation = sceneExpectation,
        tiltSource = tiltSource,
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
            posePositionCode = poseVariant.posePosition ?: poseVariant.cameraPosition ?: "standing_side",
            sceneExpectation = poseVariant.resolveSceneExpectation(),
            tiltSource = tiltSource,
        )
    }
}
