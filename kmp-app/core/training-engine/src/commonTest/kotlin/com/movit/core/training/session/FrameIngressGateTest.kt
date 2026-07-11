package com.movit.core.training.session

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
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

    @Test
    fun multiDispatcher_twoAcquiresCannotBothSucceed() = runBlocking {
        val gate = FrameIngressGate()
        val success = atomic(0)
        val jobs = List(32) {
            async(Dispatchers.Default) {
                if (gate.tryAcquire()) {
                    success.incrementAndGet()
                }
            }
        }
        jobs.awaitAll()
        assertEquals(1, success.value)
        assertEquals(31, gate.droppedFrameCount)
        gate.release()
        assertTrue(gate.tryAcquire())
    }
}
