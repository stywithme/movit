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
    val holdDefaults: HoldDefaults = HoldDefaults(),
    val visual: VisualSettings = VisualSettings(),
    val smoothing: SmoothingSettings = SmoothingSettings()
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

/**
 * Visual settings - Controls visual feedback elements
 * 
 * Arc Range Indicator settings control the gradient arc displayed around
 * tracked joints showing valid angle ranges.
 * 
 * @param showArcRangeIndicators Whether to show arc indicators around joints
 * @param arcIndicatorRadiusDp Arc radius in dp
 * @param arcIndicatorStrokeWidthDp Arc stroke width in dp
 * @param arcShowCurrentIndicator Whether to show current position indicator on arc
 * @param arcShowOnlyOnError Only show arc when joint is in error/warning state
 * @param arcShowOnlyPrimary Only show arc for primary joints (used for rep counting)
 * @param arcOpacity Arc opacity (0.0 - 1.0)
 */
data class VisualSettings(
    val showArcRangeIndicators: Boolean = true,
    val arcIndicatorRadiusDp: Float = 45f,
    val arcIndicatorStrokeWidthDp: Float = 6f,
    val arcShowCurrentIndicator: Boolean = true,
    val arcShowOnlyOnError: Boolean = false,
    val arcShowOnlyPrimary: Boolean = true,
    val arcOpacity: Float = 0.9f
)

/**
 * Landmark smoothing settings - Controls skeleton tracking smoothness
 * 
 * Uses One Euro Filter algorithm for adaptive smoothing:
 * - Fast movements → responsive tracking (low smoothing)
 * - Slow/stationary → stable tracking (high smoothing, no jitter)
 * 
 * Presets:
 * - "responsive" → Fast tracking (minCutoff=2.5, beta=0.02)
 * - "balanced"   → Default (minCutoff=1.5, beta=0.01)
 * - "smooth"     → Slow exercises (minCutoff=0.8, beta=0.005)
 * 
 * @param preset Quick configuration ("responsive", "balanced", "smooth", "custom")
 * @param minCutoff Base smoothness. Lower = smoother but more lag. Range: 0.5-3.0
 * @param beta Speed adaptation. Higher = more responsive to fast movements. Range: 0.0-0.5
 * @param useLegacyEMA Use simple EMA instead of One Euro (for comparison only)
 * @param legacyAlpha EMA alpha if useLegacyEMA is true
 */
data class SmoothingSettings(
    val preset: String = "balanced",
    val minCutoff: Float = 1.5f,
    val beta: Float = 0.01f,
    val useLegacyEMA: Boolean = false,
    val legacyAlpha: Float = 0.6f
)
