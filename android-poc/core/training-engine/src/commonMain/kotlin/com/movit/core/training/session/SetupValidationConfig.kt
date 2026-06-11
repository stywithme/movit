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
)
