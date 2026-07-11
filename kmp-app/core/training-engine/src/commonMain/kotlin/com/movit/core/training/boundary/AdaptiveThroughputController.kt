package com.movit.core.training.boundary

/**
 * WP-19: after ~[WARMUP_FRAMES] samples, if p95 inference ms exceeds the budget for the
 * current target fps, recommend one ladder step down (HIGH → MEDIUM → STABLE).
 */
class AdaptiveThroughputController(
    private val warmupFrames: Int = WARMUP_FRAMES,
    private val budgetSlackMs: Float = BUDGET_SLACK_MS,
) {
    private val samples = FloatArray(WARMUP_FRAMES)
    private var sampleCount = 0
    private var downgradeCount = 0
    private var decided = false

    val adaptiveDowngradeCount: Int get() = downgradeCount

    /**
     * @return next profile if a downgrade is warranted this call; null otherwise.
     */
    fun onInferenceMs(
        inferenceMs: Float,
        current: TrainingThroughputProfile,
    ): TrainingThroughputProfile? {
        if (decided || sampleCount >= warmupFrames) {
            decided = true
            return null
        }
        samples[sampleCount++] = inferenceMs
        if (sampleCount < warmupFrames) return null
        decided = true
        val p95 = percentile95(samples, sampleCount)
        val budget = (1000f / current.targetFps.coerceAtLeast(1)) - budgetSlackMs
        if (p95 <= budget) return null
        val next = TrainingThroughputProfiles.stepDown(current) ?: return null
        downgradeCount++
        return next
    }

    fun reset() {
        sampleCount = 0
        decided = false
        // keep downgradeCount across session for SessionQualityMeta
    }

    fun resetSession() {
        reset()
        downgradeCount = 0
    }

    companion object {
        const val WARMUP_FRAMES = 90
        const val BUDGET_SLACK_MS = 5f

        internal fun percentile95(values: FloatArray, count: Int): Float {
            if (count <= 0) return 0f
            val copy = values.copyOf(count).also { it.sort() }
            val idx = ((count - 1) * 0.95).toInt().coerceIn(0, count - 1)
            return copy[idx]
        }
    }
}
