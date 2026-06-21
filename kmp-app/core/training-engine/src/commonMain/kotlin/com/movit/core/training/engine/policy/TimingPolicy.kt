package com.movit.core.training.engine.policy

import com.movit.core.training.feedback.CoachIntensity

/**
 * Time-based guard rails for one exercise run.
 * Legacy Android reads runtime defaults from SettingsManager via [fromSettings] factory.
 */
data class TimingPolicy(
    val defaultMinRepIntervalMs: Long = DEFAULT_MIN_REP_INTERVAL_MS,
    val defaultMaxRepIntervalMs: Long = DEFAULT_MAX_REP_INTERVAL_MS,
    val defaultMinPhaseDurationMs: Long = DEFAULT_MIN_PHASE_DURATION_MS,
    val defaultHoldDurationSeconds: Int = DEFAULT_HOLD_DURATION_SECONDS,
    val defaultGracePeriodMs: Long = DEFAULT_GRACE_PERIOD_MS,
    val smoothingWindowSize: Int = DEFAULT_SMOOTHING_WINDOW_SIZE,
    val visibilityResumeCountdownMs: Long = 3000L,
    val visibilityMinVisibility: Float = 0.3f,
    val visibilityGraceDurationMs: Long = 1000L,
    val visibilityWarningDurationMs: Long = 1000L,
    val visibilityPauseAfterMs: Long = 4000L,
    val minSpeakIntervalMs: Long = 1000L,
    val stateMessageCooldownMs: Long = DEFAULT_STATE_MESSAGE_COOLDOWN_MS,
    val cameraWarningEventCooldownMs: Long = 2000L,
    val maxRepsGuardMultiplier: Int = 3,
    val minExecutionDurationFloorMs: Long = 180_000L,
    val repExecutionMinRepTimeMultiplier: Int = 4,
    val holdExecutionMaxTargetMultiplier: Int = 3,
) {
    fun minRepIntervalFor(rep: RepCountingTimingOverrides?): Long =
        rep?.minRepInterval(defaultMinRepIntervalMs) ?: defaultMinRepIntervalMs

    fun maxRepIntervalFor(rep: RepCountingTimingOverrides?): Long =
        rep?.maxRepInterval(defaultMaxRepIntervalMs) ?: defaultMaxRepIntervalMs

    fun minPhaseDurationFor(
        rep: RepCountingTimingOverrides?,
        numberOfPhases: Int,
        default: Long = defaultMinPhaseDurationMs,
    ): Long = rep?.calculateMinPhaseDuration(numberOfPhases, default)
        ?: default

    companion object {
        const val DEFAULT_MIN_REP_INTERVAL_MS = 400L
        const val DEFAULT_MAX_REP_INTERVAL_MS = 5000L
        const val DEFAULT_MIN_PHASE_DURATION_MS = 100L
        const val DEFAULT_HOLD_DURATION_SECONDS = 30
        const val DEFAULT_GRACE_PERIOD_MS = 3000L
        const val DEFAULT_SMOOTHING_WINDOW_SIZE = 3
        const val DEFAULT_STATE_MESSAGE_COOLDOWN_MS = 2000L

        val DEFAULT = TimingPolicy()

        fun default(): TimingPolicy = DEFAULT

        /** I-17 — scales engine-side cooldowns with live coach density (mirrors FeedbackScheduler multipliers). */
        fun withCoachIntensity(intensity: CoachIntensity, base: TimingPolicy = DEFAULT): TimingPolicy {
            val multiplier = when (intensity) {
                CoachIntensity.CALM -> 1.6
                CoachIntensity.STANDARD -> 1.0
                CoachIntensity.STRICT -> 0.75
            }
            return base.copy(
                stateMessageCooldownMs = (base.stateMessageCooldownMs * multiplier).toLong(),
                cameraWarningEventCooldownMs = (base.cameraWarningEventCooldownMs * multiplier).toLong(),
                visibilityGraceDurationMs = (base.visibilityGraceDurationMs * multiplier).toLong(),
                visibilityWarningDurationMs = (base.visibilityWarningDurationMs * multiplier).toLong(),
            )
        }
    }
}
