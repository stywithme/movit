package com.movit.core.posecapture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CameraStartGateTest {
    @Test
    fun startBeforePreview_defersUntilPreviewReady() {
        val gate = CameraStartGate()
        assertIs<CameraStartGate.Action.Defer>(gate.onStartRequested(useFrontCamera = true))
        assertFalse(gate.shouldStartNow())
        assertIs<CameraStartGate.Action.InitialBind>(gate.onPreviewReady(useFrontCamera = true))
        assertTrue(gate.shouldStartNow())
    }

    @Test
    fun previewBeforeStart_defersUntilStartRequested() {
        val gate = CameraStartGate()
        assertIs<CameraStartGate.Action.Defer>(gate.onPreviewReady(useFrontCamera = false))
        assertFalse(gate.shouldStartNow())
        assertIs<CameraStartGate.Action.InitialBind>(gate.onStartRequested(useFrontCamera = false))
        assertTrue(gate.shouldStartNow())
    }

    @Test
    fun reset_clearsPendingStart() {
        val gate = CameraStartGate()
        gate.onStartRequested(useFrontCamera = true)
        gate.onPreviewReady(useFrontCamera = true)
        gate.reset()
        assertFalse(gate.shouldStartNow())
        assertFalse(gate.isSessionBound())
    }

    @Test
    fun sameFacingAfterBind_isNoOp() {
        val gate = CameraStartGate()
        gate.onStartRequested(useFrontCamera = true)
        gate.onPreviewReady(useFrontCamera = true)
        gate.markBound(useFrontCamera = true)
        assertIs<CameraStartGate.Action.NoOp>(gate.onStartRequested(useFrontCamera = true))
    }

    @Test
    fun facingChangeAfterBind_requestsSwitchOnly() {
        val gate = CameraStartGate()
        gate.onStartRequested(useFrontCamera = true)
        gate.onPreviewReady(useFrontCamera = true)
        gate.markBound(useFrontCamera = true)
        val action = gate.onStartRequested(useFrontCamera = false)
        assertIs<CameraStartGate.Action.SwitchFacing>(action)
        assertEquals(false, action.useFrontCamera)
    }

    @Test
    fun facingChangeFromPreviewReady_requestsSwitch() {
        val gate = CameraStartGate()
        gate.onStartRequested(useFrontCamera = true)
        gate.onPreviewReady(useFrontCamera = true)
        gate.markBound(useFrontCamera = true)
        val action = gate.onPreviewReady(useFrontCamera = false)
        assertIs<CameraStartGate.Action.SwitchFacing>(action)
        assertEquals(false, action.useFrontCamera)
    }

    @Test
    fun facingChangeAfterBind_keepsSessionBound() {
        val gate = CameraStartGate()
        gate.onStartRequested(useFrontCamera = true)
        gate.onPreviewReady(useFrontCamera = true)
        gate.markBound(useFrontCamera = true)
        assertTrue(gate.isSessionBound())
        val action = gate.onStartRequested(useFrontCamera = false)
        assertIs<CameraStartGate.Action.SwitchFacing>(action)
        assertTrue(gate.isSessionBound())
        assertEquals(true, gate.boundFacing())
    }

    @Test
    fun markBound_tracksSessionFacing() {
        val gate = CameraStartGate()
        assertFalse(gate.isSessionBound())
        gate.markBound(useFrontCamera = true)
        assertTrue(gate.isSessionBound())
        assertEquals(true, gate.boundFacing())
    }
}
