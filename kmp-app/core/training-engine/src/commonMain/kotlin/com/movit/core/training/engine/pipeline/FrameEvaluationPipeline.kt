package com.movit.core.training.engine.pipeline

import com.movit.core.training.engine.JointStateInfo
import com.movit.core.training.engine.Phase
import com.movit.core.training.engine.evaluation.JointEval
import com.movit.core.training.engine.evaluation.JointEvaluator

class FrameEvaluationPipeline(
    private val jointEvaluator: JointEvaluator,
) {
    fun evaluate(
        smoothedAngles: Map<String, Double>,
        currentPhase: Phase,
    ): FrameJointEvaluationResult {
        val evals = jointEvaluator.evaluate(smoothedAngles, currentPhase)
        val stateInfos = evals.mapValues { it.value.toJointStateInfo() }
        return FrameJointEvaluationResult(jointEvals = evals, jointStateInfos = stateInfos)
    }
}

data class FrameJointEvaluationResult(
    val jointEvals: Map<String, JointEval>,
    val jointStateInfos: Map<String, JointStateInfo>,
) {
    fun forScoring(skippedJointCodes: Set<String>): Map<String, JointEval> =
        if (skippedJointCodes.isEmpty()) jointEvals else jointEvals.filterKeys { it !in skippedJointCodes }
}
