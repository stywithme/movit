package com.movit.core.training.engine.feedback

import com.movit.core.training.config.AngleRange
import com.movit.core.training.config.StateRanges
import com.movit.core.training.engine.ErrorType
import com.movit.core.training.engine.JointError
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.evaluation.JointEval

/**
 * Build angle-based [JointError] rows from per-joint [JointEval] (stateless).
 */
object JointErrorCollection {

    private val acceptableStates = setOf(
        JointState.PERFECT,
        JointState.NORMAL,
        JointState.PAD,
        JointState.TRANSITION,
    )

    fun collectJointErrors(jointEvals: Map<String, JointEval>): List<JointError> {
        val errors = ArrayList<JointError>()
        for ((jointCode, eval) in jointEvals) {
            if (eval.state in acceptableStates) continue
            val expectedRange = resolveExpectedRangeForState(eval.stateRanges, eval.state)
            val errorType = resolveErrorType(
                angle = eval.smoothedAngle,
                stateRanges = eval.stateRanges,
                expectedRange = expectedRange,
            )
            errors.add(
                JointError(
                    jointCode = jointCode,
                    errorType = errorType,
                    actualAngle = eval.smoothedAngle,
                    expectedMin = expectedRange.min,
                    expectedMax = expectedRange.max,
                    state = eval.state,
                    isPrimary = eval.isPrimary,
                ),
            )
        }
        return errors
    }
}

private fun resolveExpectedRangeForState(stateRanges: StateRanges?, state: JointState): AngleRange {
    if (stateRanges == null) return AngleRange(0.0, 180.0)
    return when (state) {
        JointState.PERFECT -> stateRanges.perfect
        JointState.NORMAL -> stateRanges.normal ?: stateRanges.perfect
        JointState.PAD -> stateRanges.pad ?: stateRanges.normal ?: stateRanges.perfect
        JointState.WARNING -> stateRanges.warning
            ?: stateRanges.pad
            ?: stateRanges.normal
            ?: stateRanges.perfect
        JointState.DANGER -> stateRanges.danger
            ?: stateRanges.warning
            ?: stateRanges.pad
            ?: stateRanges.normal
            ?: stateRanges.perfect
        JointState.TRANSITION -> AngleRange(stateRanges.effectiveMin, stateRanges.effectiveMax)
    }
}

private fun resolveErrorType(
    angle: Double,
    stateRanges: StateRanges?,
    expectedRange: AngleRange,
): ErrorType {
    stateRanges?.let { ranges ->
        if (angle > ranges.effectiveMax) return ErrorType.TOO_HIGH
        if (angle < ranges.effectiveMin) return ErrorType.TOO_LOW
    }
    if (angle > expectedRange.max) return ErrorType.TOO_HIGH
    if (angle < expectedRange.min) return ErrorType.TOO_LOW
    val midpoint = (expectedRange.min + expectedRange.max) / 2.0
    return if (angle >= midpoint) ErrorType.TOO_HIGH else ErrorType.TOO_LOW
}
