package com.movit.core.training.boundary

/**
 * Read-only source for screen-plane tilt correction (WS-3).
 * Android: Gravity/Accelerometer sensor; iOS: CoreMotion device motion gravity.
 *
 * [gravityVector] is the latest (gx, gy, gz) when available — used by gravity-aligned
 * 3D position checks (WP-20). May be null when the sensor is idle or unavailable.
 */
interface DeviceTiltPort {
    val isAvailable: Boolean
    val correctionRadians: Float
    val rollDegrees: Float
    /** Latest gravity sample in device/sensor space, or null. */
    val gravityVector: FloatArray? get() = null
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
    override val gravityVector: FloatArray? = null
}
