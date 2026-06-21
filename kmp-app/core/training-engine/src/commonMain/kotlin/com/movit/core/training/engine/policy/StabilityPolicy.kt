package com.movit.core.training.engine.policy

import com.movit.core.training.engine.JointState

/**
 * Unified thresholds for stable joint/phase decisions (ported from legacy).
 */
data class StabilityPolicy(
    val stateHysteresisNormalPad: Double = 3.0,
    val stateHysteresisPadWarning: Double = 2.0,
    val stateHysteresisWarningDanger: Double = 2.0,
    val minDangerFrames: Int = 3,
    val minTransitionMarginDegrees: Double = 1.5,
    val phaseHysteresisDegrees: Double = DEFAULT_PHASE_HYSTERESIS,
    val boundaryBuffer: Double = DEFAULT_BOUNDARY_BUFFER,
    val positionMinErrorFrames: Int = 2,
    val positionHysteresisBuffer: Float = 0.01f,
    val anySideTiebreakGap: Float = 0.1f,
    val anySideVisibilityThreshold: Float = 0.5f,
    val anySideStrongMinVisibility: Float = 0.7f,
) {
    fun statePairHysteresis(from: JointState, to: JointState): Double = when {
        from == JointState.DANGER || to == JointState.DANGER -> stateHysteresisWarningDanger
        from == JointState.WARNING || to == JointState.WARNING -> stateHysteresisPadWarning
        from == JointState.PAD || to == JointState.PAD -> stateHysteresisNormalPad
        else -> 1.0
    }

    companion object {
        const val DEFAULT_PHASE_HYSTERESIS = 3.0
        const val DEFAULT_BOUNDARY_BUFFER = 5.0

        fun default(): StabilityPolicy = StabilityPolicy()
    }
}
