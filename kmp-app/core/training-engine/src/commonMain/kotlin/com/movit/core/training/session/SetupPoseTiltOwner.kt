package com.movit.core.training.session

import com.movit.core.training.boundary.AcquirableDeviceTiltPort

/**
 * Acquires the tilt sensor during setup/countdown (legacy "setup-pose" owner).
 * Coexists with [MovitTrainingEngine] TILT_OWNER via ref-count on the port.
 */
class SetupPoseTiltOwner(
    private val tiltPort: AcquirableDeviceTiltPort?,
    private val ownerId: String = OWNER_ID,
) {
    private var held: Boolean = false

    fun onRunState(state: SessionRunState) {
        val need = state.shouldValidatePose()
        when {
            need && !held -> {
                tiltPort?.acquire(ownerId)
                held = true
            }
            !need && held -> {
                tiltPort?.release(ownerId)
                held = false
            }
        }
    }

    fun release() {
        if (!held) return
        tiltPort?.release(ownerId)
        held = false
    }

    fun isHeld(): Boolean = held

    companion object {
        const val OWNER_ID: String = "setup-pose"
    }
}
