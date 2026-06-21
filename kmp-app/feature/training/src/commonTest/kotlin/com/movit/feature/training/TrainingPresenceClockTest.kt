package com.movit.feature.training

import kotlin.test.Test
import kotlin.test.assertTrue

class TrainingPresenceClockTest {
    @Test
    fun resolveTimestamp_withoutFrameClock_advancesFromLastFrame() {
        val first = TrainingPresenceClock.resolveTimestamp(frameTimestampMs = null, lastFrameTimestampMs = 0L)
        val second = TrainingPresenceClock.resolveTimestamp(frameTimestampMs = null, lastFrameTimestampMs = first)
        assertTrue(second > first, "expected monotonic presence clock: first=$first second=$second")
    }

    @Test
    fun resolveTimestamp_prefersFrameTimestampWhenPresent() {
        val resolved = TrainingPresenceClock.resolveTimestamp(frameTimestampMs = 42_000L, lastFrameTimestampMs = 1L)
        kotlin.test.assertEquals(42_000L, resolved)
    }
}
