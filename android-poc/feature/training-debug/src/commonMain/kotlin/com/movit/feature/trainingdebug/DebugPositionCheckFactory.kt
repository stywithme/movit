package com.movit.feature.trainingdebug

import com.movit.core.training.config.LandmarkGroup
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.config.PositionCheck
import com.movit.core.training.config.PositionCondition
import com.movit.core.training.position.PoseSceneExpectation
import com.movit.core.training.position.PositionValidator
import com.movit.core.training.session.TrainingGateFactory

object DebugPositionCheckFactory {
    private const val DEBUG_CHECK_ID = "debug_check"

    fun buildCheck(config: DebugPositionCheckConfig): PositionCheck =
        PositionCheck(
            id = DEBUG_CHECK_ID,
            type = config.checkType,
            landmarks = LandmarkGroup(
                primary = config.primaryLandmark,
                secondary = config.secondaryLandmark,
            ),
            condition = PositionCondition(
                operator = config.operator,
                threshold = config.threshold,
            ),
            activePhases = listOf("top", "up", "down", "all"),
            errorMessage = LocalizedText(en = "Debug position check"),
            minErrorFrames = 1,
        )

    fun buildValidator(
        config: DebugPositionCheckConfig,
        sceneExpectation: PoseSceneExpectation,
        tiltSource: com.movit.core.training.boundary.DeviceTiltPort? = null,
    ): PositionValidator = TrainingGateFactory.buildPositionValidator(
        positionChecks = listOf(buildCheck(config)),
        posePositionCode = "standing_front",
        sceneExpectation = sceneExpectation,
        tiltSource = tiltSource,
    )
}
