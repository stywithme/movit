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
        /** WP-02/E-11: joints whose 3D/2D mode flipped — clear MA buffer on switch. */
        angleModeSwitchedJoints: Set<String> = emptySet(),
        worldLandmarks: List<Landmark>? = null,
    ): MainPathFrameResult {
        val clearer = skippedForFrame + angleModeSwitchedJoints
        if (clearer.isNotEmpty()) {
            angleSmoother.clearJoints(clearer)
        }
        val smoothedAngles = angleSmoother.smooth(rawTrackedAngles)
        val primaryAngles = smoothedAngles.filterKeys { primaryJointCodes.contains(it) }

        val inStartPos = startPoseGate.isInStartPosition(smoothedAngles)
        val currentPhase = stateMachine.update(primaryAngles)
        var positionResult: PositionValidationResult? = null
        val validator = positionValidator
        if (landmarks != null && validator != null) {
            // D-04 / product: keep hints in IDLE/START; drop per-check debug outside counting.
            positionResult = validator.validate(
                landmarks = landmarks,
                currentPhase = currentPhase,
                isBilateralFlipped = isBilateralFlipped,
                isFrontCamera = isFrontCamera,
                worldLandmarks = worldLandmarks,
            )
            if (!validator.isSceneLocked) {
                validator.lockScene()
            }
        }
        val frameJoint = frameEvalPipeline.evaluate(smoothedAngles, currentPhase)
        return MainPathFrameResult(
            smoothedAngles = smoothedAngles,
            inStartPosition = inStartPos,
            currentPhase = currentPhase,
            positionResult = positionResult,
            frameJoint = frameJoint,
        )
    }
}

/** Consumed fields only (WP-08 / D-06 / I-03). */
data class MainPathFrameResult(
    val smoothedAngles: Map<String, Double>,
    val inStartPosition: Boolean,
    val currentPhase: Phase,
    val positionResult: PositionValidationResult?,
    val frameJoint: FrameJointEvaluationResult,
)
