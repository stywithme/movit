package com.movit.core.training.boundary

/**
 * Read-only source for screen-plane tilt correction (WS-3).
 * Android: sensor-backed via [core:pose-capture]; iOS v1: no-op stub.
 */
interface DeviceTiltPort {
    val isAvailable: Boolean
    val correctionRadians: Float
    val rollDegrees: Float
}

/**
 * Lifecycle contract for sources that consume device resources while active.
 */
interface AcquirableDeviceTiltPort : DeviceTiltPort {
    val isRunning: Boolean
    fun acquire(owner: String)
    fun release(owner: String)
}

object NoOpDeviceTiltPort : DeviceTiltPort {
    override val isAvailable: Boolean = false
    override val correctionRadians: Float = 0f
    override val rollDegrees: Float = 0f
}
