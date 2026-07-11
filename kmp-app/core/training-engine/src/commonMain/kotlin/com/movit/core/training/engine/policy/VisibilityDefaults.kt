package com.movit.core.training.engine.policy

/**
 * Documented visibility thresholds (E-07 / J-08).
 *
 * The split is intentional: angle math uses a stricter gate so joint angles stay
 * trustworthy; pause / partial-visibility uses a more lenient gate so brief
 * occlusion does not stop the session. Change one constant here if product unifies.
 */
object VisibilityDefaults {
    /** Minimum landmark visibility for angle calculation and setup validation. */
    const val ANGLE_GATE: Float = 0.5f

    /** Minimum joint visibility for [com.movit.core.training.visibility.VisibilityMonitor] pause logic. */
    const val PAUSE_GATE: Float = 0.3f
}
