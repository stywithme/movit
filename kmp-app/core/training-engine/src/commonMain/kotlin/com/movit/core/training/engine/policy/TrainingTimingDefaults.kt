package com.movit.core.training.engine.policy

/** Single source for rep / phase timing defaults (J-07). */
object TrainingTimingDefaults {
    const val MIN_REP_INTERVAL_MS: Long = 400L
    const val MAX_REP_INTERVAL_MS: Long = 5_000L
    const val MIN_PHASE_DURATION_MS: Long = 100L
}
