package com.movit.feature.training

/**
 * Monotonic presence timestamps when camera frames lack pose or a usable clock.
 */
internal object TrainingPresenceClock {
    fun resolveTimestamp(frameTimestampMs: Long?, lastFrameTimestampMs: Long): Long {
        frameTimestampMs?.takeIf { it > 0L }?.let { return it }
        val wall = trainingWallClockMs()
        return if (lastFrameTimestampMs > 0L) {
            maxOf(lastFrameTimestampMs + 1L, wall)
        } else {
            wall
        }
    }
}

internal expect fun trainingWallClockMs(): Long