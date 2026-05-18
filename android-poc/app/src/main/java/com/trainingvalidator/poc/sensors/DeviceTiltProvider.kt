package com.trainingvalidator.poc.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.trainingvalidator.poc.training.config.DeviceTiltSettings
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.engine.AcquirableTiltSource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Process-wide provider for screen-plane tilt correction.
 *
 * TYPE_GRAVITY is preferred because PositionValidator needs the gravity-aligned
 * 2D image axes, not full device attitude. Accelerometer is a fallback for
 * devices that do not expose a virtual gravity sensor.
 *
 * Resource ownership is ref-counted by owner id so production training, debug,
 * and future tools can safely share one sensor listener.
 */
class DeviceTiltProvider private constructor(
    private val settingsProvider: () -> DeviceTiltSettings,
    private val backend: SensorBackend
) : SensorEventListener, AcquirableTiltSource {

    constructor(
        context: Context,
        settingsProvider: () -> DeviceTiltSettings = { SettingsManager.getDeviceTiltSettings() }
    ) : this(
        settingsProvider = settingsProvider,
        backend = AndroidSensorBackend(context.applicationContext)
    )

    companion object {
        internal fun createForTest(
            settingsProvider: () -> DeviceTiltSettings,
            backend: SensorBackend
        ): DeviceTiltProvider = DeviceTiltProvider(settingsProvider, backend)
    }

    private val lock = Any()
    private val owners = mutableSetOf<String>()

    @Volatile private var correctionRadiansValue: Float = 0f
    @Volatile private var rollDegreesValue: Float = 0f
    @Volatile private var smoothedCorrectionRadians: Float? = null
    @Volatile private var lastEventTimestampNs: Long = 0L

    @Volatile
    override var isRunning: Boolean = false
        private set

    override val isAvailable: Boolean get() = settingsProvider().enabled && backend.hasSensor
    override val correctionRadians: Float get() = correctionRadiansValue
    override val rollDegrees: Float get() = rollDegreesValue

    val activeOwnerCount: Int get() = synchronized(lock) { owners.size }

    override fun acquire(owner: String) {
        if (owner.isBlank() || !isAvailable) return

        synchronized(lock) {
            if (!owners.add(owner)) return
            if (!isRunning) registerLocked()
        }
    }

    override fun release(owner: String) {
        if (owner.isBlank()) return

        synchronized(lock) {
            if (!owners.remove(owner)) return
            if (owners.isEmpty()) unregisterLocked()
        }
    }

    fun releaseAll() {
        synchronized(lock) {
            owners.clear()
            unregisterLocked()
        }
    }

    private fun registerLocked() {
        val rateMicros = settingsProvider().sensorRateMicros.coerceAtLeast(20_000)
        isRunning = backend.register(this, rateMicros)
        if (!isRunning) {
            owners.clear()
        }
    }

    private fun unregisterLocked() {
        if (isRunning) backend.unregister(this)
        isRunning = false
        correctionRadiansValue = 0f
        rollDegreesValue = 0f
        smoothedCorrectionRadians = null
        lastEventTimestampNs = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        val gx = event.values.getOrNull(0) ?: return
        val gy = event.values.getOrNull(1) ?: return
        updateFromGravity(gx, gy, event.timestamp)
    }

    internal fun updateFromGravity(gx: Float, gy: Float, timestampNs: Long) {
        val settings = settingsProvider()
        if (!settings.enabled) {
            releaseAll()
            return
        }

        // In portrait, +Y is screen-up. atan2(gx, gy) gives the screen roll;
        // negating it gives the correction needed to align image Y with gravity.
        val rollRadians = atan2(gx, gy)
        val rawCorrection = -rollRadians
        val corrected = smoothCorrection(rawCorrection, timestampNs, settings)

        val deadZoneRadians = Math.toRadians(settings.deadZoneDegrees.toDouble()).toFloat()
        correctionRadiansValue = if (abs(corrected) < deadZoneRadians) {
            0f
        } else {
            corrected
        }
        rollDegreesValue = Math.toDegrees(rollRadians.toDouble()).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun smoothCorrection(rawCorrection: Float, timestampNs: Long, settings: DeviceTiltSettings): Float {
        val previous = smoothedCorrectionRadians
        if (previous == null || settings.smoothingTauMs <= 0L || lastEventTimestampNs == 0L) {
            smoothedCorrectionRadians = rawCorrection
            lastEventTimestampNs = timestampNs
            return rawCorrection
        }

        val dtMs = ((timestampNs - lastEventTimestampNs).coerceAtLeast(0L) / 1_000_000.0).toFloat()
        lastEventTimestampNs = timestampNs
        val alpha = (dtMs / (settings.smoothingTauMs + dtMs)).coerceIn(0f, 1f)
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

    internal interface SensorBackend {
        val context: Context
        val hasSensor: Boolean
        fun register(listener: SensorEventListener, rateMicros: Int): Boolean
        fun unregister(listener: SensorEventListener)
    }

    private class AndroidSensorBackend(override val context: Context) : SensorBackend {
        private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private val tiltSensor: Sensor? =
            sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        override val hasSensor: Boolean get() = tiltSensor != null

        override fun register(listener: SensorEventListener, rateMicros: Int): Boolean {
            val sensor = tiltSensor ?: return false
            return sensorManager.registerListener(listener, sensor, rateMicros)
        }

        override fun unregister(listener: SensorEventListener) {
            sensorManager.unregisterListener(listener)
        }
    }
}
