package com.movit.core.training.engine.pipeline



import com.movit.core.training.engine.AngleSmoother

import com.movit.core.training.engine.Phase

import com.movit.core.training.engine.PhaseStateMachine

import com.movit.core.training.engine.StartPoseGate

import com.movit.core.training.model.Landmark

import com.movit.core.training.position.PositionValidationResult

import com.movit.core.training.position.PositionValidator



/**

 * Stages: smooth → [StartPoseGate] → PSM update → [PositionValidator] (optional) →

 * [FrameEvaluationPipeline] (joint quality).

 */

class FramePipelineExecutor(

    private val angleSmoother: AngleSmoother,

    private val startPoseGate: StartPoseGate,

    private val stateMachine: PhaseStateMachine,

    private val positionValidator: PositionValidator?,

    private val frameEvalPipeline: FrameEvaluationPipeline,

    private val primaryJointCodes: Set<String>,

) {

    fun runMainPath(

        rawTrackedAngles: Map<String, Double>,

        skippedForFrame: Set<String>,

        landmarks: List<Landmark>?,

        isBilateralFlipped: Boolean,

        isFrontCamera: Boolean,

        allJointsVisible: Boolean,

    ): MainPathFrameResult {

        if (skippedForFrame.isNotEmpty()) {

            angleSmoother.clearJoints(skippedForFrame)

        }

        val smoothedAngles = angleSmoother.smooth(rawTrackedAngles)

        val primaryAngles = smoothedAngles.filterKeys { primaryJointCodes.contains(it) }



        val inStartPos = startPoseGate.isInStartPosition(smoothedAngles)

        val currentPhase = stateMachine.update(primaryAngles)

        var positionResult: PositionValidationResult? = null

        val validator = positionValidator

        if (landmarks != null && validator != null) {

            positionResult = validator.validate(

                landmarks,

                currentPhase,

                isBilateralFlipped,

                isFrontCamera,

            )

            if (!validator.isSceneLocked) {

                validator.lockScene()

            }

        }

        val frameJoint = frameEvalPipeline.evaluate(smoothedAngles, currentPhase)

        return MainPathFrameResult(

            skippedForFrame = skippedForFrame,

            rawTrackedAngles = rawTrackedAngles,

            smoothedAngles = smoothedAngles,

            inStartPosition = inStartPos,

            currentPhase = currentPhase,

            positionResult = positionResult,

            allJointsVisible = allJointsVisible,

            frameJoint = frameJoint,

        )

    }

}



data class MainPathFrameResult(

    val skippedForFrame: Set<String>,

    val rawTrackedAngles: Map<String, Double>,

    val smoothedAngles: Map<String, Double>,

    val inStartPosition: Boolean,

    val currentPhase: Phase,

    val positionResult: PositionValidationResult?,

    val allJointsVisible: Boolean,

    val frameJoint: FrameJointEvaluationResult,

)

