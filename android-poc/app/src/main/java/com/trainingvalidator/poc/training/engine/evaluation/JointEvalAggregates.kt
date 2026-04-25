package com.trainingvalidator.poc.training.engine.evaluation

import com.trainingvalidator.poc.training.models.JointState

/**
 * Reducers over a frame of [JointEval] (worst / danger) for the training loop.
 */
fun Map<String, JointEval>.getWorstStateExcludingTransition(): JointState {
    val quality = values.map { it.state }.filter { it != JointState.TRANSITION }
    return JointState.getWorst(quality)
}

fun Map<String, JointEval>.hasAnyDangerState(): Boolean =
    values.any { it.state == JointState.DANGER }
