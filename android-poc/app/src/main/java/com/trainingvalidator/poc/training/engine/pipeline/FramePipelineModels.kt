package com.trainingvalidator.poc.training.engine.pipeline

import com.trainingvalidator.poc.training.engine.Phase
import com.trainingvalidator.poc.training.engine.PositionValidationResult

/**
 * Stages: extract (engine) → visibility (engine) →
 * [FramePipelineExecutor.runAfterExtract] = smooth → start-gate → PSM → position (optional) → joint eval.
 */
data class MainPathFrameResult(
    val skippedForFrame: Set<String>,
    val rawTrackedAngles: Map<String, Double>,
    val smoothedAngles: Map<String, Double>,
    val inStartPosition: Boolean,
    val currentPhase: Phase,
    val positionResult: PositionValidationResult?,
    val allJointsVisible: Boolean,
    val frameJoint: FrameJointEvaluationResult
)
