package com.movit.core.posecapture

import com.movit.core.training.boundary.AcquirableDeviceTiltPort
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreMotion.CMAttitudeReferenceFrameXArbitraryZVertical
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue
import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * CoreMotion-backed tilt port (mirrors Android [com.movit.core.posecapture.android.AndroidDeviceTiltPort]).
 */
@OptIn(ExperimentalForeignApi::class)
class IosDeviceTiltPort(
    private val enabled: Boolean = true,
    private val deadZoneDegrees: Float = 2f,
    private val smoothingTauMs: Long = 120L,
) : AcquirableDeviceTiltPort {
    private val motionManager = CMMotionManager()
    private val owners = mutableSetOf<String>()
    private var smoothedCorrectionRadians: Float? = null
    private var lastEventTimestampMs: Long = 0L

    @Volatile
    private var correctionRadiansValue: Float = 0f

    @Volatile
    private var rollDegreesValue: Float = 0f

    override var isRunning: Boolean = false
        private set

    override val isAvailable: Boolean
        get() = enabled && motionManager.deviceMotionAvailable

    override val correctionRadians: Float
        get() = correctionRadiansValue

    override val rollDegrees: Float
        get() = rollDegreesValue

    override fun acquire(owner: String) {
        if (owner.isBlank() || !isAvailable) return
        if (!owners.add(owner)) return
        if (!isRunning) startLocked()
    }

    override fun release(owner: String) {
        if (owner.isBlank()) return
        if (!owners.remove(owner)) return
        if (owners.isEmpty()) stopLocked()
    }

    private fun startLocked() {
        motionManager.deviceMotionUpdateInterval = 1.0 / 60.0
        motionManager.startDeviceMotionUpdatesUsingReferenceFrame(
            CMAttitudeReferenceFrameXArbitraryZVertical,
            toQueue = NSOperationQueue.mainQueue,
            withHandler = { motion, _ ->
                val gravity = motion?.gravity ?: return@startDeviceMotionUpdatesUsingReferenceFrame
                val timestampMs = ((motion.timestamp) * 1000.0).toLong()
                gravity.useContents {
                    updateFromGravity(x.toFloat(), y.toFloat(), timestampMs)
                }
            },
        )
        isRunning = motionManager.deviceMotionActive
    }

    private fun stopLocked() {
        motionManager.stopDeviceMotionUpdates()
        isRunning = false
        correctionRadiansValue = 0f
        rollDegreesValue = 0f
        smoothedCorrectionRadians = null
        lastEventTimestampMs = 0L
    }

    private fun updateFromGravity(gx: Float, gy: Float, timestampMs: Long) {
        val rollRadians = atan2(gx, gy)
        val rawCorrection = -rollRadians
        val corrected = smoothCorrection(rawCorrection, timestampMs)
        val deadZoneRadians = (deadZoneDegrees * PI / 180.0).toFloat()
        correctionRadiansValue = if (abs(corrected) < deadZoneRadians) 0f else corrected
        rollDegreesValue = (rollRadians * 180.0 / PI).toFloat()
    }

    private fun smoothCorrection(rawCorrection: Float, timestampMs: Long): Float {
        val previous = smoothedCorrectionRadians
        if (previous == null || smoothingTauMs <= 0L || lastEventTimestampMs == 0L) {
            smoothedCorrectionRadians = rawCorrection
            lastEventTimestampMs = timestampMs
            return rawCorrection
        }
        val dtMs = (timestampMs - lastEventTimestampMs).coerceAtLeast(0L).toFloat()
        lastEventTimestampMs = timestampMs
        val alpha = (dtMs / (smoothingTauMs + dtMs)).coerceIn(0f, 1f)
        val delta = normalizeRadians(rawCorrection - previous)
        val smoothed = normalizeRadians(previous + alpha * delta)
        smoothedCorrectionRadians = smoothed
        return smoothed
    }

    private fun normalizeRadians(value: Float): Float {
        var result = value
        val pi = PI.toFloat()
        val twoPi = (2.0 * PI).toFloat()
        while (result > pi) result -= twoPi
        while (result < -pi) result += twoPi
        return result
    }
}
