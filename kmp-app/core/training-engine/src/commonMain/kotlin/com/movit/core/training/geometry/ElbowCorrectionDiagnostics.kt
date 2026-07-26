package com.movit.core.training.geometry

/**
 * Read-only elbow correction diagnostics (Legacy `ElbowDiagnostics` parity).
 * Populated by [ElbowAngleEstimator] without changing [com.movit.core.training.model.JointAngles] output.
 */
data class ElbowCorrectionDiagnostics(
    val facingRatio: Float,
    val screenAngle: Double,
    val worldAngle: Double,
    val maxDzShare: Float,
    val dzImbalance: Float,
    val correctionPct: Float,
    val outputAngle: Double?,
    val isHolding: Boolean,
    val uaDzShare: Float,
    val faDzShare: Float,
    val strategy: ElbowCorrectionStrategy,
    /** WP-23: how much [outputAngle] deserves trust this frame, `[0,1]`. */
    val confidence: Float = 1f,
    /** WP-24: `|n_z| / ‖n‖` of the arm plane normal; 0 = angle not observable. `NaN` if unknown. */
    val armPlaneObservability: Float = Float.NaN,
)

/**
 * Diagnostics label only — since WP-21 the output is one continuous blend, not a branch pick.
 * The names are kept for Legacy `ElbowDiagnostics` parity in the debug lab.
 */

enum class ElbowCorrectionStrategy(val legacyCode: String) {
    STRAIGHT("STRAIGHT"),
    TRUST_3D("TRUST_3D"),
    TRUST_2D("TRUST_2D"),
    MILD_DOWN("MILD_DOWN"),
    DEEP_DOWN("DEEP_DOWN"),
    LOW_CONF("LOW_CONF"),
    HOLD("HOLD"),
}
