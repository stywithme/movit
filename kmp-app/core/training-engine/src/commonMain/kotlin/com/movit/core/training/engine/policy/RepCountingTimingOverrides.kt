package com.movit.core.training.engine.policy

/**
 * Per-exercise rep timing overrides (mirrors legacy RepCountingConfig timing fields).
 */
data class RepCountingTimingOverrides(
    val minRepIntervalMs: Long? = null,
    val maxRepIntervalMs: Long? = null,
) {
    fun minRepInterval(default: Long): Long = minRepIntervalMs ?: default

    fun maxRepInterval(default: Long): Long = maxRepIntervalMs ?: default

    fun calculateMinPhaseDuration(numberOfPhases: Int, defaultMinPhaseDuration: Long): Long {
        val minInterval = minRepIntervalMs ?: return defaultMinPhaseDuration
        if (numberOfPhases <= 0) return defaultMinPhaseDuration
        return minInterval / numberOfPhases
    }
}
