package com.movit.core.training.session

import com.movit.core.training.boundary.AcquirableDeviceTiltPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetupPoseTiltOwnerTest {

    @Test
    fun setupCountdownTrainingPauseExit_acquireReleaseSequence() {
        val port = FakeTiltPort()
        val owner = SetupPoseTiltOwner(port)

        owner.onRunState(SessionRunState.SETUP_POSE)
        assertTrue(owner.isHeld())
        assertEquals(listOf("acquire:setup-pose"), port.log)

        owner.onRunState(SessionRunState.COUNTDOWN)
        assertTrue(owner.isHeld())
        assertEquals(1, port.log.size) // still held — no re-acquire

        owner.onRunState(SessionRunState.TRAINING)
        assertFalse(owner.isHeld())
        assertEquals(listOf("acquire:setup-pose", "release:setup-pose"), port.log)

        owner.onRunState(SessionRunState.PAUSED)
        assertFalse(owner.isHeld())

        owner.onRunState(SessionRunState.RESUME_SETUP)
        assertTrue(owner.isHeld())
        assertEquals(3, port.log.size)

        owner.onRunState(SessionRunState.RESUME_COUNTDOWN)
        assertTrue(owner.isHeld())

        owner.onRunState(SessionRunState.TRAINING)
        assertFalse(owner.isHeld())

        owner.onRunState(SessionRunState.SETUP_POSE)
        assertTrue(owner.isHeld())
        owner.release() // exit / onCleared
        assertFalse(owner.isHeld())
        assertEquals(
            listOf(
                "acquire:setup-pose",
                "release:setup-pose",
                "acquire:setup-pose",
                "release:setup-pose",
                "acquire:setup-pose",
                "release:setup-pose",
            ),
            port.log,
        )
    }

    @Test
    fun nullPort_isNoOp() {
        val owner = SetupPoseTiltOwner(null)
        owner.onRunState(SessionRunState.SETUP_POSE)
        assertTrue(owner.isHeld()) // logical hold even without port
        owner.release()
        assertFalse(owner.isHeld())
    }

    private class FakeTiltPort : AcquirableDeviceTiltPort {
        val log = mutableListOf<String>()
        private val owners = mutableSetOf<String>()
        override var isRunning: Boolean = false
            private set
        override val isAvailable: Boolean = true
        override val correctionRadians: Float = 0f
        override val rollDegrees: Float = 0f
        override fun acquire(owner: String) {
            log += "acquire:$owner"
            owners += owner
            isRunning = owners.isNotEmpty()
        }
        override fun release(owner: String) {
            log += "release:$owner"
            owners -= owner
            isRunning = owners.isNotEmpty()
        }
    }
}
