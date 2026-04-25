package com.trainingvalidator.poc.training.engine.pipeline

import com.trainingvalidator.poc.training.engine.Phase
import com.trainingvalidator.poc.training.engine.evaluation.JointEval
import com.trainingvalidator.poc.training.engine.evaluation.JointEvaluator
import com.trainingvalidator.poc.training.models.JointStateInfo

/**
 * Per-frame joint quality: one call into [JointEvaluator] and the derived [JointStateInfo] map
 * (same contract as the engine previously inlined here).
 */
class FrameEvaluationPipeline(
    private val jointEvaluator: JointEvaluator
) {
    fun evaluate(
        smoothedAngles: Map<String, Double>,
        currentPhase: Phase
    ): FrameJointEvaluationResult {
        val evals = jointEvaluator.evaluate(smoothedAngles, currentPhase)
        val stateInfos = evals.mapValues { it.value.toJointStateInfo() }
        return FrameJointEvaluationResult(
            jointEvals = evals,
            jointStateInfos = stateInfos
        )
    }
}

data class FrameJointEvaluationResult(
    val jointEvals: Map<String, JointEval>,
    val jointStateInfos: Map<String, JointStateInfo>
) {
    fun forScoring(skippedJointCodes: Set<String>): Map<String, JointEval> =
        if (skippedJointCodes.isEmpty()) jointEvals
        else jointEvals.filterKeys { it !in skippedJointCodes }
}
