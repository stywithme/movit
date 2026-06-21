package com.movit.core.training.engine

/**
 * Per-joint phase bounds for [PhaseStateMachine].
 * Android [TrackedJoint] adapters implement this interface.
 */
interface PhaseJointConfig {
    val jointCode: String

    fun hasUpDownRanges(): Boolean

    fun hasHoldRange(): Boolean

    fun upRangeEffectiveMin(): Double

    fun upRangeOutermostMax(): Double

    fun downRangeOutermostMin(): Double

    fun downRangeEffectiveMax(): Double

    fun holdRangeOutermostMin(): Double

    fun holdRangeEffectiveMax(): Double
}

data class PhaseTimingConfig(
    val minRepIntervalMs: Long,
    val maxRepIntervalMs: Long,
    val minPhaseDurationMs: Long,
)
