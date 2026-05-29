package com.trainingvalidator.poc.training.config

/**
 * AppSettings - Global application settings loaded from app_settings.json
 * 
 * These settings are shared across all exercises and provide default values
 * for angle detection, movement detection, and timing thresholds.
 */
data class AppSettings(
    val version: String = "1.2.0",
    val feedback: FeedbackSettings = FeedbackSettings(),
    val angleDetection: AngleDetectionSettings = AngleDetectionSettings(),
    val movementDetection: MovementDetectionSettings = MovementDetectionSettings(),
    val defaults: DefaultTimingSettings = DefaultTimingSettings(),
    val holdDefaults: HoldDefaults = HoldDefaults(),
    val smoothing: SmoothingSettings = SmoothingSettings(),
    val visibility: VisibilitySettings = VisibilitySettings(),
    val poseValidation: PoseValidationSettings = PoseValidationSettings(),
    val setupValidation: SetupValidationSettings = SetupValidationSettings(),
    val overlayOpacity: OverlayOpacitySettings = OverlayOpacitySettings(),
    val rangeIndicator: RangeIndicatorSettings = RangeIndicatorSettings(),
    val deviceTilt: DeviceTiltSettings = DeviceTiltSettings(),
    val lineIndicator: LineIndicatorSettings = LineIndicatorSettings()
)

/**
 * Feedback settings — message timing for training (not UI language; use Profile / [Context] locale).
 */
data class FeedbackSettings(
    val stateMessageCooldownMs: Long = 2000,
    val randomMessageIdleMs: Long = 5000,
    val randomMessageCooldownMs: Long = 10000
)

/**
 * Range Indicator Type Settings - Choose between Arc and Line visual indicators
 * 
 * @param type "line" for line-on-limb indicator, "arc" for arc-around-joint indicator
 */
data class RangeIndicatorSettings(
    val type: String = "line"
) {
    /**
     * Check if Arc indicator should be used
     */
    fun isArc(): Boolean = type.equals("arc", ignoreCase = true)
    
    /**
     * Check if Line indicator should be used
     */
    fun isLine(): Boolean = type.equals("line", ignoreCase = true)
}

/**
 * Device tilt correction settings for position/posture validation.
 *
 * @param enabled Kill switch for automatic tilt correction.
 * @param sensorRateMicros Requested sensor sampling period in microseconds.
 * @param smoothingTauMs EMA time constant applied to the correction angle.
 * @param deadZoneDegrees Tiny screen roll values below this are treated as zero.
 */
data class DeviceTiltSettings(
    val enabled: Boolean = true,
    val sensorRateMicros: Int = 200_000,
    val smoothingTauMs: Long = 120L,
    val deadZoneDegrees: Float = 1.0f
)

/**
 * Angle detection settings - used by [JointAngleTracker], [PhaseStateMachine], and overlays
 *
 * @param hysteresisDegrees Buffer to prevent flickering when transitioning between zones
 * @param boundaryBufferDegrees Buffer around range boundaries for validation tolerance
 */
data class AngleDetectionSettings(
    val hysteresisDegrees: Double = 5.0,
    val boundaryBufferDegrees: Double = 3.0
)

/**
 * Movement detection settings - Used by PhaseStateMachine for smoothing
 *
 * @param smoothingWindowSize Number of frames to average for angle smoothing
 */
data class MovementDetectionSettings(
    val smoothingWindowSize: Int = 3
)

/**
 * Default timing settings - Fallback values when not specified per-exercise
 *
 * @param minRepIntervalMs Minimum time between reps (prevents double counting)
 * @param minPhaseDurationMs Minimum time in a phase before transitioning
 */
data class DefaultTimingSettings(
    val minRepIntervalMs: Long = 400,
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
 * Landmark smoothing settings - Controls skeleton tracking smoothness
 * 
 * Uses One Euro Filter algorithm for adaptive smoothing:
 * - Fast movements → responsive tracking (low smoothing)
 * - Slow/stationary → stable tracking (high smoothing, no jitter)
 * 
 * Presets (tuned for normalized MediaPipe coordinates 0–1):
 * - "responsive" → Fast tracking (minCutoff=1.3, beta=2.0)
 * - "balanced"   → Default (minCutoff=1.0, beta=1.5)
 * - "smooth"     → Slow exercises (minCutoff=0.7, beta=1.0)
 * 
 * @param preset Quick configuration ("responsive", "balanced", "smooth", "custom")
 * @param minCutoff Base smoothness. Lower = smoother but more lag. Range: 0.7–1.3
 * @param beta Speed adaptation. Higher = more responsive to fast movements. Range: 1.0–2.5
 * @param useLegacyEMA Use simple EMA instead of One Euro (for comparison only)
 * @param legacyAlpha EMA alpha if useLegacyEMA is true
 */
data class SmoothingSettings(
    val preset: String = "balanced",
    val minCutoff: Float = 1.0f,
    val beta: Float = 1.5f,
    val useLegacyEMA: Boolean = false,
    val legacyAlpha: Float = 0.6f
)

/**
 * Visibility thresholds - Controls when landmarks/joints are considered "visible"
 * 
 * MediaPipe returns a visibility score (0-1) for each landmark.
 * Higher threshold = more strict (requires higher confidence)
 * Lower threshold = more tolerant (accepts lower confidence)
 * 
 * @param overlay Threshold for drawing skeleton overlay
 * @param poseValidation Threshold for validating startPose before training
 */
data class VisibilitySettings(
    val overlay: Float = 0.5f,
    val poseValidation: Float = 0.3f
)

/**
 * Pose validation — anatomical angle bounds for [SettingsManager.isAngleValid].
 */
data class PoseValidationSettings(
    val minValidAngle: Float = 5f,
    val maxValidAngle: Float = 175f
)

/**
 * Skeleton overlay opacity settings - Controls visual prominence of skeleton parts
 * 
 * Minimalist Design: Non-tracked joints should be faint (low opacity)
 * while tracked joints are more visible, especially on errors.
 * 
 * @param nonTracked Opacity for non-tracked joints (0.0-1.0)
 *                   Lower makes them less distracting
 * @param trackedCorrect Opacity for tracked joints in correct position (0.0-1.0)
 *                       Moderate to focus attention on Arc indicator
 * @param trackedError Opacity for tracked joints in error position (0.0-1.0)
 *                     Higher to draw attention to the problem
 */
data class OverlayOpacitySettings(
    val nonTracked: Float = 0.18f,
    val trackedCorrect: Float = 0.50f,
    val trackedError: Float = 0.75f
)

/**
 * Setup validation settings - Controls the SETUP_POSE pre-training guidance
 *
 * Uses a rolling window (windowSize frames, requiredValid must be valid) instead of
 * a strict N-consecutive approach to tolerate camera noise.
 *
 * @param windowSize       Total frames in the rolling window (default 12)
 * @param requiredValid    Frames that must be valid within the window (default 9)
 * @param closeThresholdDegrees  Distance (°) from range where joint is YELLOW vs RED
 * @param voiceCooldownMs  Base minimum ms between voice guidance messages (same-message uses 2x)
 * @param cameraTipEnabled Whether to show camera-position tip in the VIEW card
 * @param cameraCheckWindowSize  Rolling window size for camera detection (default 12)
 * @param cameraCheckRequired    Frames agreeing on camera position (default 9)
 * @param countdownToleranceMs  Duration (ms) of invalid pose silently ignored during countdown
 * @param countdownCancelMs    Duration (ms) after which countdown is cancelled entirely
 */
data class SetupValidationSettings(
    val windowSize: Int = 12,
    val requiredValid: Int = 9,
    val closeThresholdDegrees: Double = 15.0,
    val voiceCooldownMs: Long = 5000L,
    val cameraTipEnabled: Boolean = true,
    val cameraCheckWindowSize: Int = 12,
    val cameraCheckRequired: Int = 9,
    val countdownToleranceMs: Long = 150L,
    val countdownCancelMs: Long = 1200L
)

/**
 * Line Indicator settings - Controls the line-based range indicator
 * 
 * Replaces Arc indicator with a simpler visual:
 * - Static Track: Shows full movement range with gradient colors
 * - Moving Indicator: Shows current position on the track
 * 
 * @param centerAngle The center angle where line length is 0 (typically 90°)
 * @param smoothingFactor Smoothness of indicator movement (0.1=smooth, 0.5=responsive)
 * @param snapToZeroThreshold Below this ratio, snap to 0 to prevent flickering
 * @param track Settings for the static track line
 * @param indicator Settings for the moving indicator line
 * @param lengthRatio Max length ratios for UP and DOWN limbs
 * @param strokeWidth Stroke widths for different states
 * @param jointRadius Joint circle radius for different states
 */
data class LineIndicatorSettings(
    val centerAngle: Double = 90.0,
    val smoothingFactor: Float = 0.12f,
    val snapToZeroThreshold: Float = 0.04f,
    val track: LineTrackSettings = LineTrackSettings(),
    val indicator: LineIndicatorStyleSettings = LineIndicatorStyleSettings(),
    val lengthRatio: LineLengthRatioSettings = LineLengthRatioSettings(),
    val strokeWidth: LineStrokeWidthSettings = LineStrokeWidthSettings(),
    val jointRadius: LineJointRadiusSettings = LineJointRadiusSettings()
)

/**
 * Static track settings
 * @param alpha Track opacity (0-255)
 * @param widthRatio Track width relative to indicator (e.g., 0.7 = 70% of indicator width)
 */
data class LineTrackSettings(
    val alpha: Int = 130,
    val widthRatio: Float = 0.7f
)

/**
 * Moving indicator style settings
 * @param widthMultiplier Indicator width multiplier relative to base stroke width
 */
data class LineIndicatorStyleSettings(
    val widthMultiplier: Float = 1.3f
)

/**
 * Length ratio settings for UP and DOWN limbs
 * @param upper Max length ratio for UP direction (0.0-1.0)
 * @param lower Max length ratio for DOWN direction (0.0-1.0)
 */
data class LineLengthRatioSettings(
    val upper: Float = 0.50f,
    val lower: Float = 0.75f
)

/**
 * Stroke width settings for different states (in dp)
 */
data class LineStrokeWidthSettings(
    val normal: Float = 10f,
    val warning: Float = 12f,
    val error: Float = 14f
)

/**
 * Joint radius settings for different states (in dp)
 */
data class LineJointRadiusSettings(
    val normal: Float = 14f,
    val warning: Float = 18f,
    val error: Float = 22f
)
