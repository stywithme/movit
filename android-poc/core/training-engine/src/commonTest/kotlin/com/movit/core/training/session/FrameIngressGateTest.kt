package com.movit.core.training.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrameIngressGateTest {
    @Test
    fun dropsConcurrentAcquires() {
        val gate = FrameIngressGate()
        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())
        assertEquals(1, gate.droppedFrameCount)
        gate.release()
        assertTrue(gate.tryAcquire())
    }
}
