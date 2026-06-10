package com.movit.core.training.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExecutionClockTest {
    private var wall = 1_000L

    @Test
    fun activeDurationExcludesPauseTime() {
        val clock = ExecutionClock { wall }
        clock.onFrame(1_000L)
        wall = 5_000L
        clock.pause()
        wall = 8_000L
        clock.resume()
        wall = 10_000L
        assertEquals(6_000L, clock.getActiveExecutionDurationMs(10_000L))
    }

    @Test
    fun resetClearsPauseState() {
        val clock = ExecutionClock { wall }
        clock.onFrame(1_000L)
        clock.pause()
        clock.reset()
        assertFalse(clock.isPaused)
        assertEquals(0L, clock.getActiveExecutionDurationMs())
    }

    @Test
    fun finalizeDurationMatchesActiveDurationWhenStopped() {
        val clock = ExecutionClock { wall }
        clock.onFrame(2_000L)
        clock.onFrame(7_000L)
        assertEquals(5_000L, clock.finalizeDurationMs())
    }

    @Test
    fun usesFrameTimelineWhenProvided() {
        val clock = ExecutionClock { 99_999L }
        clock.onFrame(4_000L)
        assertEquals(4_000L, clock.nowMs())
        assertTrue(clock.executionStartTimeMs == 4_000L)
    }
}
