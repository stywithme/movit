package com.movit.core.posecapture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LensSwitchFrameGateTest {
    @Test
    fun noPendingSwitch_deliversAllFrames() {
        val gate = LensSwitchFrameGate()
        assertEquals(LensSwitchFrameGate.FrameDecision.Deliver, gate.acceptFrame(isFrontCamera = true))
        assertEquals(LensSwitchFrameGate.FrameDecision.Deliver, gate.acceptFrame(isFrontCamera = false))
    }

    @Test
    fun pendingSwitch_suppressesStaleFacingUntilMatch() {
        val gate = LensSwitchFrameGate()
        gate.beginAwaitingFrames(useFrontCamera = false)
        assertTrue(gate.isAwaitingFrames())
        assertEquals(LensSwitchFrameGate.FrameDecision.Suppress, gate.acceptFrame(isFrontCamera = true))
        assertEquals(
            LensSwitchFrameGate.FrameDecision.DeliverCompleteSwitch,
            gate.acceptFrame(isFrontCamera = false),
        )
        assertFalse(gate.isAwaitingFrames())
        assertEquals(LensSwitchFrameGate.FrameDecision.Deliver, gate.acceptFrame(isFrontCamera = true))
    }

    @Test
    fun clear_cancelsPendingAwait() {
        val gate = LensSwitchFrameGate()
        gate.beginAwaitingFrames(useFrontCamera = true)
        gate.clear()
        assertFalse(gate.isAwaitingFrames())
        assertEquals(LensSwitchFrameGate.FrameDecision.Deliver, gate.acceptFrame(isFrontCamera = false))
    }
}
