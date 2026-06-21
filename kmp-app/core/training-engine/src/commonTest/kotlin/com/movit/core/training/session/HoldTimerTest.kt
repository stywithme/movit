package com.movit.core.training.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HoldTimerTest {
    @Test
    fun completesAfterTargetDuration() {
        val timer = HoldTimer(targetDurationMs = 3_000L, gracePeriodMs = 1_000L)
        var completed = false
        timer.onCompleted = { _, _ -> completed = true }

        timer.update(isInHoldZone = true, currentTimeMs = 0L)
        timer.update(isInHoldZone = true, currentTimeMs = 3_000L)

        assertTrue(completed)
        assertEquals(HoldState.COMPLETED, timer.state)
        assertEquals(3_000L, timer.elapsedMs)
    }

    @Test
    fun gracePeriodResumesAndFails() {
        val timer = HoldTimer(targetDurationMs = 10_000L, gracePeriodMs = 500L)
        timer.update(isInHoldZone = true, currentTimeMs = 0L)
        timer.update(isInHoldZone = false, currentTimeMs = 1_000L)
        assertEquals(HoldState.GRACE_PERIOD, timer.state)

        timer.update(isInHoldZone = true, currentTimeMs = 1_200L)
        assertEquals(HoldState.HOLDING, timer.state)

        timer.update(isInHoldZone = false, currentTimeMs = 2_000L)
        timer.update(isInHoldZone = false, currentTimeMs = 2_600L)
        assertEquals(HoldState.FAILED, timer.state)
    }
}
