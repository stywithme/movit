package com.movit.core.training.geometry

/**
 * Per-owner sticky 3D/2D hysteresis for limb angles (E-11 / WP-02).
 *
 * Held by each frame-source owner (same ownership model as [ElbowAngleEstimator]) and passed
 * into [PoseFrameAssembler.assemble]. Clear via [reset] on lens switch, session start, and
 * flow exercise boundaries — never share across owners.
 */
class AngleModeStickyState {
    private val stickyMode = mutableMapOf<String, StickyJointState>()

    fun reset() {
        stickyMode.clear()
    }

    /**
     * After locking a mode, require [STICKY_MODE_FRAMES] consecutive opposite frames before
     * switching. First observation locks immediately (not a "switch").
     */
    fun resolveUse3d(
        jointCode: String,
        wants3d: Boolean,
        modeSwitchedOut: MutableSet<String>,
    ): Boolean {
        val desired = wants3d
        val state = stickyMode.getOrPut(jointCode) { StickyJointState() }
        val locked = state.use3d
        if (locked == null) {
            state.use3d = desired
            return desired
        }
        if (desired == locked) {
            state.pendingUse3d = null
            state.pendingCount = 0
            return locked
        }
        if (state.pendingUse3d == desired) {
            state.pendingCount++
        } else {
            state.pendingUse3d = desired
            state.pendingCount = 1
        }
        if (state.pendingCount >= STICKY_MODE_FRAMES) {
            state.use3d = desired
            state.pendingUse3d = null
            state.pendingCount = 0
            modeSwitchedOut.add(jointCode)
            return desired
        }
        return locked
    }

    private class StickyJointState {
        var use3d: Boolean? = null
        var pendingUse3d: Boolean? = null
        var pendingCount: Int = 0
    }

    companion object {
        const val STICKY_MODE_FRAMES = 3
    }
}
