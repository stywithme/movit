package com.trainingvalidator.poc.sensors

import android.content.Context
import android.hardware.SensorEventListener
import androidx.test.core.app.ApplicationProvider
import com.trainingvalidator.poc.training.config.DeviceTiltSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.cos
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
class DeviceTiltProviderTest {

    @Test
    fun `acquire and release are ref counted by owner`() {
        val backend = FakeBackend(hasSensor = true)
        val provider = DeviceTiltProvider.createForTest(
            settingsProvider = { DeviceTiltSettings(enabled = true) },
            backend = backend
        )

        provider.acquire("engine")
        provider.acquire("engine")
        provider.acquire("debug")

        assertTrue(provider.isRunning)
        assertEquals(1, backend.registerCount)

        provider.release("engine")
        assertTrue(provider.isRunning)
        assertEquals(0, backend.unregisterCount)

        provider.release("debug")
        assertFalse(provider.isRunning)
        assertEquals(1, backend.unregisterCount)
    }

    @Test
    fun `dead zone zeros tiny roll and preserves larger correction`() {
        val backend = FakeBackend(hasSensor = true)
        val provider = DeviceTiltProvider.createForTest(
            settingsProvider = {
                DeviceTiltSettings(
                    enabled = true,
                    smoothingTauMs = 0L,
                    deadZoneDegrees = 1.0f
                )
            },
            backend = backend
        )

        provider.updateFromGravity(
            gx = sin(Math.toRadians(0.5)).toFloat(),
            gy = cos(Math.toRadians(0.5)).toFloat(),
            timestampNs = 1_000_000L
        )
        assertEquals(0f, provider.correctionRadians, 0.0001f)

        provider.updateFromGravity(
            gx = sin(Math.toRadians(10.0)).toFloat(),
            gy = cos(Math.toRadians(10.0)).toFloat(),
            timestampNs = 2_000_000L
        )
        assertEquals(-10.0, Math.toDegrees(provider.correctionRadians.toDouble()), 0.1)
    }

    private class FakeBackend(
        override val hasSensor: Boolean
    ) : DeviceTiltProvider.SensorBackend {
        override val context: Context = ApplicationProvider.getApplicationContext()
        var registerCount = 0
        var unregisterCount = 0

        override fun register(listener: SensorEventListener, rateMicros: Int): Boolean {
            registerCount++
            return hasSensor
        }

        override fun unregister(listener: SensorEventListener) {
            unregisterCount++
        }
    }
}
