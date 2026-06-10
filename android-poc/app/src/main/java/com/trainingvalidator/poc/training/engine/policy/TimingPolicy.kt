package com.trainingvalidator.poc.training.engine.policy

import com.movit.core.training.engine.policy.RepCountingTimingOverrides
import com.movit.core.training.engine.policy.TimingPolicy as KmpTimingPolicy
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.models.RepCountingConfig

typealias TimingPolicy = KmpTimingPolicy

fun fromSettings(): TimingPolicy = KmpTimingPolicy(
    defaultMinRepIntervalMs = SettingsManager.getDefaultMinRepInterval(),
    defaultMaxRepIntervalMs = SettingsManager.getDefaultMaxRepInterval(),
    defaultMinPhaseDurationMs = SettingsManager.getDefaultMinPhaseDuration(),
    defaultHoldDurationSeconds = SettingsManager.getDefaultHoldDuration(),
    defaultGracePeriodMs = SettingsManager.getDefaultGracePeriod(),
    smoothingWindowSize = SettingsManager.getSmoothingWindowSize(),
    stateMessageCooldownMs = SettingsManager.getStateMessageCooldown(),
)

internal fun RepCountingConfig.toTimingOverrides(): RepCountingTimingOverrides =
    RepCountingTimingOverrides(
        minRepIntervalMs = minRepIntervalMs,
        maxRepIntervalMs = maxRepIntervalMs,
    )

fun TimingPolicy.minRepIntervalFor(rep: RepCountingConfig?): Long =
    minRepIntervalFor(rep?.toTimingOverrides())

fun TimingPolicy.maxRepIntervalFor(rep: RepCountingConfig?): Long =
    maxRepIntervalFor(rep?.toTimingOverrides())

fun TimingPolicy.minPhaseDurationFor(
    rep: RepCountingConfig?,
    numberOfPhases: Int,
    default: Long = defaultMinPhaseDurationMs,
): Long = minPhaseDurationFor(rep?.toTimingOverrides(), numberOfPhases, default)
