package com.movit.core.posecapture.android

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.movit.core.training.boundary.AcquirableDeviceTiltPort
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Android sensor-backed [DeviceTiltPort] (legacy DeviceTiltProvider pattern).
 */
class AndroidDeviceTiltPort(
    context: Context,
    private val enabled: Boolean = true,
    private val deadZoneDegrees: Float = 2f,
    private val smoothingTauMs: Long = 120L,
) : SensorEventListener, AcquirableDeviceTiltPort {

    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val tiltSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val owners = mutableSetOf<String>()
    private var smoothedCorrectionRadians: Float? = null
    private var lastEventTimestampNs: Long = 0L

    @Volatile
    private var correctionRadiansValue: Float = 0f

    @Volatile
    private var rollDegreesValue: Float = 0f

    override var isRunning: Boolean = false
        private set

    override val isAvailable: Boolean get() = enabled && tiltSensor != null
    override val correctionRadians: Float get() = correctionRadiansValue
    override val rollDegrees: Float get() = rollDegreesValue

    override fun acquire(owner: String) {
        if (owner.isBlank() || !isAvailable) return
        synchronized(owners) {
            if (!owners.add(owner)) return
            if (!isRunning) registerLocked()
        }
    }

    override fun release(owner: String) {
        if (owner.isBlank()) return
        synchronized(owners) {
            if (!owners.remove(owner)) return
            if (owners.isEmpty()) unregisterLocked()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val gx = event.values.getOrNull(0) ?: return
        val gy = event.values.getOrNull(1) ?: return
        updateFromGravity(gx, gy, event.timestamp)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun registerLocked() {
        val sensor = tiltSensor ?: return
        isRunning = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun unregisterLocked() {
        if (isRunning) sensorManager.unregisterListener(this)
        isRunning = false
        correctionRadiansValue = 0f
        rollDegreesValue = 0f
        smoothedCorrectionRadians = null
        lastEventTimestampNs = 0L
    }

    private fun updateFromGravity(gx: Float, gy: Float, timestampNs: Long) {
        val rollRadians = atan2(gx, gy)
        val rawCorrection = -rollRadians
        val corrected = smoothCorrection(rawCorrection, timestampNs)
        val deadZoneRadians = Math.toRadians(deadZoneDegrees.toDouble()).toFloat()
        correctionRadiansValue = if (abs(corrected) < deadZoneRadians) 0f else corrected
        rollDegreesValue = Math.toDegrees(rollRadians.toDouble()).toFloat()
    }

    private fun smoothCorrection(rawCorrection: Float, timestampNs: Long): Float {
        val previous = smoothedCorrectionRadians
        if (previous == null || smoothingTauMs <= 0L || lastEventTimestampNs == 0L) {
            smoothedCorrectionRadians = rawCorrection
            lastEventTimestampNs = timestampNs
            return rawCorrection
        }
        val dtMs = ((timestampNs - lastEventTimestampNs).coerceAtLeast(0L) / 1_000_000.0).toFloat()
        lastEventTimestampNs = timestampNs
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
