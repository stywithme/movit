package com.trainingvalidator.poc.training.engine.policy

import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.models.RepCountingConfig

/**
 * All time-based / cooldown / guard rails for one exercise run in [TrainingEngine].
 */
data class TimingPolicy(
    val defaultMinRepIntervalMs: Long = SettingsManager.getDefaultMinRepInterval(),
    val defaultMaxRepIntervalMs: Long = SettingsManager.getDefaultMaxRepInterval(),
    val defaultMinPhaseDurationMs: Long = SettingsManager.getDefaultMinPhaseDuration(),
    val defaultHoldDurationSeconds: Int = SettingsManager.getDefaultHoldDuration(),
    val defaultGracePeriodMs: Long = SettingsManager.getDefaultGracePeriod(),
    val smoothingWindowSize: Int = SettingsManager.getSmoothingWindowSize(),
    val visibilityResumeCountdownMs: Long = 3000L,
    /** [VisibilityMonitor] and pose visibility gate. */
    val visibilityMinVisibility: Float = 0.3f,
    val visibilityGraceDurationMs: Long = 1000L,
    val visibilityWarningDurationMs: Long = 1000L,
    val visibilityPauseAfterMs: Long = 4000L,
    val minSpeakIntervalMs: Long = 1000L,
    val stateMessageCooldownMs: Long = SettingsManager.getStateMessageCooldown(),
    val cameraWarningEventCooldownMs: Long = 2000L,
    val maxRepsGuardMultiplier: Int = 3,
    val minExecutionDurationFloorMs: Long = 180_000L,
    /** [TrainingEngine] rep-run cap uses `minRepIntervalMs * targetReps *` this. */
    val repExecutionMinRepTimeMultiplier: Int = 4,
    /** [TrainingEngine] max hold duration cap uses `target *` this. */
    val holdExecutionMaxTargetMultiplier: Int = 3
) {
    fun minRepIntervalFor(rep: RepCountingConfig?): Long =
        rep?.getMinRepInterval(defaultMinRepIntervalMs) ?: defaultMinRepIntervalMs

    fun maxRepIntervalFor(rep: RepCountingConfig?): Long =
        rep?.getMaxRepInterval(defaultMaxRepIntervalMs) ?: defaultMaxRepIntervalMs

    fun minPhaseDurationFor(rep: RepCountingConfig?, numberOfPhases: Int, default: Long = defaultMinPhaseDurationMs): Long =
        rep?.calculateMinPhaseDuration(numberOfPhases, default) ?: default

    companion object {
        fun default(): TimingPolicy = TimingPolicy()
    }
}
