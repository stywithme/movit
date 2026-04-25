package com.trainingvalidator.poc.training.engine.policy

import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.models.JointState

/**
 * Unified thresholds for stable joint/phase/position decisions (single source for tuning).
 * Defaults mirror legacy constants from the pre-refactor monolith (phase machine, position, smoothing).
 */
data class StabilityPolicy(
    val stateHysteresisNormalPad: Double = 3.0,
    val stateHysteresisPadWarning: Double = 2.0,
    val stateHysteresisWarningDanger: Double = 2.0,
    val minDangerFrames: Int = 3,
    /** Used when confirming WARNING / boundary-style transitions. */
    val minTransitionMarginDegrees: Double = 1.5,
    /** Phase boundary buffer (°) — was [com.trainingvalidator.poc.training.config.SettingsManager.getHysteresis]. */
    val phaseHysteresisDegrees: Double,
    /** Start-position feedback buffer (°) — was SettingsManager.getBoundaryBuffer(). */
    val boundaryBuffer: Double,
    val positionMinErrorFrames: Int = 2,
    val positionHysteresisBuffer: Float = 0.01f,
    val anySideTiebreakGap: Float = 0.1f,
    /** Hard min landmark visibility: below this, Any-Side may drop the partner side. */
    val anySideVisibilityThreshold: Float = SettingsManager.getAnySideVisibilityThreshold(),
    /**
     * “Clearly visible” for dominant-side win over borderline (see [com.trainingvalidator.poc.training.engine.JointAngleTracker]).
     * Distinct from [anySideVisibilityThreshold].
     */
    val anySideStrongMinVisibility: Float = 0.7f
) {
    /**
     * Map-style hysteresis for [JointState] pairs (simplified 3-bucket + default).
     */
    fun statePairHysteresis(from: JointState, to: JointState): Double = when {
        from == JointState.DANGER || to == JointState.DANGER -> stateHysteresisWarningDanger
        from == JointState.WARNING || to == JointState.WARNING -> stateHysteresisPadWarning
        from == JointState.PAD || to == JointState.PAD -> stateHysteresisNormalPad
        else -> 1.0
    }

    companion object {
        fun default(): StabilityPolicy = StabilityPolicy(
            phaseHysteresisDegrees = SettingsManager.getHysteresis(),
            boundaryBuffer = SettingsManager.getBoundaryBuffer()
        )
    }
}
