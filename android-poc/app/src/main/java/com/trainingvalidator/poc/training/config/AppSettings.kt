package com.trainingvalidator.poc.training.config

/**
 * AppSettings - Global application settings loaded from app_settings.json
 * 
 * These settings are shared across all exercises and provide default values
 * for angle detection, movement detection, and timing thresholds.
 */
data class AppSettings(
    val version: String = "1.0.0",
    val angleDetection: AngleDetectionSettings = AngleDetectionSettings(),
    val movementDetection: MovementDetectionSettings = MovementDetectionSettings(),
    val defaults: DefaultTimingSettings = DefaultTimingSettings(),
    val holdDefaults: HoldDefaults = HoldDefaults()
)

/**
 * Angle detection settings - Used by FormValidator and PhaseStateMachine
 * 
 * @param hysteresisDegrees Buffer to prevent flickering when transitioning between zones
 * @param boundaryBufferDegrees Buffer around range boundaries for validation tolerance
 * @param extremeErrorThresholdDegrees How far outside range before flagging extreme error
 */
data class AngleDetectionSettings(
    val hysteresisDegrees: Double = 5.0,
    val boundaryBufferDegrees: Double = 3.0,
    val extremeErrorThresholdDegrees: Double = 10.0
)

/**
 * Movement detection settings - Used by PhaseStateMachine for smoothing
 * 
 * @param smoothingWindowSize Number of frames to average for angle smoothing
 * @param angleChangeThresholdDegrees Minimum angle change to consider as movement
 */
data class MovementDetectionSettings(
    val smoothingWindowSize: Int = 3,
    val angleChangeThresholdDegrees: Double = 2.0
)

/**
 * Default timing settings - Fallback values when not specified per-exercise
 * 
 * @param minRepIntervalMs Minimum time between reps (prevents double counting)
 * @param maxRepIntervalMs Maximum time for a rep before considered timeout
 * @param minPhaseDurationMs Minimum time in a phase before transitioning
 */
data class DefaultTimingSettings(
    val minRepIntervalMs: Long = 400,
    val maxRepIntervalMs: Long = 5000,
    val minPhaseDurationMs: Long = 100
)

/**
 * Hold exercise defaults - Used for HOLD counting method
 * 
 * @param defaultDurationSeconds Default target duration for hold exercises
 * @param defaultGracePeriodMs Default grace period when user leaves hold position
 */
data class HoldDefaults(
    val defaultDurationSeconds: Int = 30,
    val defaultGracePeriodMs: Long = 3000
)
