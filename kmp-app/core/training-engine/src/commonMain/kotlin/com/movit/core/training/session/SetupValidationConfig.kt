package com.movit.core.training.session

/**
 * Setup/countdown validation thresholds (mirrors legacy SetupValidationSettings).
 */
data class SetupValidationConfig(
    val windowSize: Int = 12,
    val requiredValid: Int = 9,
    val closeThresholdDegrees: Double = 15.0,
    val voiceCooldownMs: Long = 5_000L,
    val cameraTipEnabled: Boolean = true,
    val cameraCheckWindowSize: Int = 12,
    val cameraCheckRequired: Int = 9,
    val countdownToleranceMs: Long = 150L,
    val countdownCancelMs: Long = 1_200L,
    /** Legacy `isStartPoseRoughlyValid` angle slack; defaults to `max(closeThreshold, 10°)`. */
    val countdownAngleToleranceDegrees: Double? = null,
    /** Minimum share of tracked joints that must be visible during countdown (Legacy ≈ 0.6). */
    val countdownMinJointPresenceRatio: Double = 0.6,
    /** When true, every primary joint must be visible (Legacy default). */
    val countdownRequireAllPrimaryPresent: Boolean = true,
) {
    fun resolvedCountdownAngleToleranceDegrees(): Double =
        countdownAngleToleranceDegrees ?: closeThresholdDegrees.coerceAtLeast(10.0)
}
