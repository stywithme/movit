package com.movit.core.training.session

import kotlin.math.abs

/**
 * Tracks active execution duration with manual pause support.
 * Extracted from legacy [com.movit.training.TrainingEngine] time logic.
 */
class ExecutionClock(
    private val wallClock: () -> Long,
) {
    var executionStartTimeMs: Long = 0L
        private set

    var isPaused: Boolean = false
        private set

    private var totalPausedDurationMs: Long = 0L
    private var pauseStartTimeMs: Long = 0L
    private var currentFrameTimeMs: Long = 0L
    private var usesExternalFrameTimeline: Boolean = false

    fun reset() {
        isPaused = false
        currentFrameTimeMs = 0L
        usesExternalFrameTimeline = false
        executionStartTimeMs = 0L
        totalPausedDurationMs = 0L
        pauseStartTimeMs = 0L
    }

    fun onFrame(timestampMs: Long) {
        val frameTimeMs = if (timestampMs > 0L) timestampMs else wallClock()
        currentFrameTimeMs = frameTimeMs
        usesExternalFrameTimeline =
            timestampMs > 0L && abs(wallClock() - frameTimeMs) > 30_000L
        if (executionStartTimeMs == 0L) {
            executionStartTimeMs = frameTimeMs
        }
    }

    fun nowMs(): Long = if (currentFrameTimeMs > 0L) currentFrameTimeMs else wallClock()

    private fun pauseClockNowMs(): Long =
        if (usesExternalFrameTimeline) nowMs() else wallClock()

    fun pause() {
        if (!isPaused) {
            isPaused = true
            pauseStartTimeMs = pauseClockNowMs()
        }
    }

    fun resume() {
        settlePauseDuration()
        isPaused = false
    }

    fun settlePauseDuration() {
        if (pauseStartTimeMs > 0L) {
            val pausedFor = pauseClockNowMs() - pauseStartTimeMs
            totalPausedDurationMs += maxOf(0L, pausedFor)
            pauseStartTimeMs = 0L
        }
    }

    fun getActiveExecutionDurationMs(now: Long = nowMs()): Long {
        if (executionStartTimeMs <= 0L) return 0L

        val pendingPause = if (isPaused && pauseStartTimeMs > 0L) {
            pauseClockNowMs() - pauseStartTimeMs
        } else {
            0L
        }

        val elapsed = now - executionStartTimeMs
        return maxOf(0L, elapsed - totalPausedDurationMs - maxOf(0L, pendingPause))
    }

    fun finalizeDurationMs(): Long {
        val now = nowMs()
        val totalElapsed = if (executionStartTimeMs > 0) now - executionStartTimeMs else 0L
        val pendingPause = if (isPaused && pauseStartTimeMs > 0) {
            pauseClockNowMs() - pauseStartTimeMs
        } else {
            0L
        }
        return maxOf(0L, totalElapsed - totalPausedDurationMs - pendingPause)
    }
}
