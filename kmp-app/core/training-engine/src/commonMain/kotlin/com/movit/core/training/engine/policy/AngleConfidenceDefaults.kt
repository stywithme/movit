package com.movit.core.training.engine.policy

/**
 * Confidence policy for computed joint angles (WP-23).
 *
 * A joint angle is a *measurement*, and monocular capture cannot always make it. Before this
 * policy existed the engine could not tell a real reading from a frozen `HOLD` value, so a
 * fabricated angle scored reps exactly like a measured one (EL-10).
 *
 * Semantics of the confidence map carried on `PoseFrame.angleConfidence`:
 * - **absent key** = no confidence information for that joint → treat as fully trusted
 *   (all channels except the elbows behave exactly as before this policy landed).
 * - **present value** in `[0,1]` = how much the producer trusts the angle this frame.
 *
 * Two thresholds, deliberately split:
 * - [SCORING_MIN] gates *quality scoring* only — cheap to be strict here, the rep still counts.
 * - [PHASE_FREEZE_MIN] gates the *phase machine*, which stops reps from closing. Set well below
 *   [SCORING_MIN] so only genuinely unobservable frames can stall counting.
 *
 * All constants are initial values; M-E (WP-26) calibrates them against reference recordings.
 */
object AngleConfidenceDefaults {
    /** Below this, a joint is excluded from quality scoring and feedback. */
    const val SCORING_MIN: Float = 0.45f

    /** Below this on a **primary** joint, the phase machine freezes (no rep can close). */
    const val PHASE_FREEZE_MIN: Float = 0.25f

    /** Ceiling applied while replaying `lastStable` for an occluded joint. */
    const val OCCLUDED_HOLD_CEILING: Float = 0.30f

    /** How much full limb depth (`depthRamp = 1`) is allowed to erode confidence. */
    private const val DEPTH_PENALTY: Float = 0.85f

    /** Disagreement between the 2D and 3D measurements that counts as "total". */
    private const val DISAGREEMENT_SPAN_DEG: Double = 60.0

    /** How much total 2D/3D disagreement is allowed to erode confidence. */
    private const val DISAGREEMENT_PENALTY: Float = 0.70f

    /**
     * Elbow confidence = product of three independent doubts.
     *
     * @param visibilityFactor `[0,1]` ramp of the worst of the three landmark visibilities.
     * @param depthRamp `[0,1]` share of the limb that lies along the optical axis.
     * @param disagreementDeg `|ang3D - ang2D|` — two biased measurements pulling apart is
     *   evidence that at least one of them is wrong, regardless of which.
     */
    fun elbowConfidence(
        visibilityFactor: Float,
        depthRamp: Float,
        disagreementDeg: Double,
    ): Float {
        val depthFactor = 1f - DEPTH_PENALTY * depthRamp.coerceIn(0f, 1f)
        val disagreementShare = (disagreementDeg / DISAGREEMENT_SPAN_DEG).coerceIn(0.0, 1.0).toFloat()
        val agreementFactor = 1f - DISAGREEMENT_PENALTY * disagreementShare
        return (visibilityFactor.coerceIn(0f, 1f) * depthFactor * agreementFactor).coerceIn(0f, 1f)
    }
}
