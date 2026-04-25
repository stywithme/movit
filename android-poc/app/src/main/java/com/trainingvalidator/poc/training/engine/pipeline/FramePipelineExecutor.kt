package com.trainingvalidator.poc.training.engine.pipeline

import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.training.engine.AngleSmoother
import com.trainingvalidator.poc.training.engine.PhaseStateMachine
import com.trainingvalidator.poc.training.engine.PositionValidator
import com.trainingvalidator.poc.training.engine.StartPoseGate
import com.trainingvalidator.poc.training.engine.PositionValidationResult

/**
 * Stages 3–6: smooth → [StartPoseGate] → PSM update → [PositionValidator] (optional) →
 * [FrameEvaluationPipeline] (joint quality).
 * Angle **extraction** and **visibility** stay in [com.trainingvalidator.poc.training.TrainingEngine.processFrame].
 */
class FramePipelineExecutor(
    private val angleSmoother: AngleSmoother,
    private val startPoseGate: StartPoseGate,
    private val stateMachine: PhaseStateMachine,
    private val positionValidator: PositionValidator,
    private val frameEvalPipeline: FrameEvaluationPipeline,
    private val primaryJointCodes: Set<String>
) {
    /**
     * @param rawTrackedAngles from [com.trainingvalidator.poc.training.engine.JointAngleTracker] for this frame
     */
    fun runMainPath(
        rawTrackedAngles: Map<String, Double>,
        skippedForFrame: Set<String>,
        landmarks: List<SmoothedLandmark>?,
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
        if (landmarks != null) {
            positionResult = positionValidator.validate(
                landmarks, currentPhase, isBilateralFlipped, isFrontCamera
            )
            if (!positionValidator.isSceneLocked) { positionValidator.lockScene() }
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
            frameJoint = frameJoint
        )
    }
}
