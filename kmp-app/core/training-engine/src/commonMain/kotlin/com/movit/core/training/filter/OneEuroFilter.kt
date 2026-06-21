package com.movit.core.training.filter

import kotlin.math.abs

/**
 * Adaptive low-pass filter for smooth, responsive pose tracking.
 * Ported from legacy Android analysis layer for KMP sharing.
 */
class OneEuroFilter(
    private val minCutoff: Float = 1.0f,
    private val beta: Float = 1.5f,
    private val dCutoff: Float = 1.0f,
) {
    private var xFilter: LowPassFilter? = null
    private var dxFilter: LowPassFilter? = null
    private var lastTime: Long = 0
    private var lastValue: Float? = null

    fun filter(value: Float, timestampMs: Long): Float {
        val dt = if (lastTime == 0L) {
            1.0f / 30.0f
        } else {
            ((timestampMs - lastTime) / 1000.0f).coerceIn(0.001f, 0.1f)
        }
        lastTime = timestampMs

        if (xFilter == null) {
            xFilter = LowPassFilter(computeAlpha(minCutoff, dt), value)
            dxFilter = LowPassFilter(computeAlpha(dCutoff, dt), 0f)
            lastValue = value
            return value
        }

        val dx = (value - (lastValue ?: value)) / dt
        lastValue = value
        val filteredDx = dxFilter!!.filter(dx, computeAlpha(dCutoff, dt))
        val cutoff = minCutoff + beta * abs(filteredDx)
        return xFilter!!.filter(value, computeAlpha(cutoff, dt))
    }

    fun reset() {
        xFilter = null
        dxFilter = null
        lastTime = 0
        lastValue = null
    }

    private fun computeAlpha(cutoff: Float, dt: Float): Float {
        val tau = 1.0f / (TWO_PI * cutoff)
        return 1.0f / (1.0f + tau / dt)
    }

    private class LowPassFilter(
        private var alpha: Float,
        private var previousValue: Float,
    ) {
        fun filter(value: Float, alpha: Float): Float {
            this.alpha = alpha
            val result = alpha * value + (1 - alpha) * previousValue
            previousValue = result
            return result
        }
    }

    private companion object {
        const val TWO_PI = 6.2831855f
    }
}

class FilterResult3D {
    var x: Float = 0f
    var y: Float = 0f
    var z: Float = 0f

    operator fun component1() = x
    operator fun component2() = y
    operator fun component3() = z
}

class OneEuroFilter3D(
    minCutoff: Float = 1.0f,
    beta: Float = 1.5f,
    dCutoff: Float = 1.0f,
) {
    private val xFilter = OneEuroFilter(minCutoff, beta, dCutoff)
    private val yFilter = OneEuroFilter(minCutoff, beta, dCutoff)
    private val zFilter = OneEuroFilter(minCutoff, beta, dCutoff)
    private val result = FilterResult3D()

    fun filter(x: Float, y: Float, z: Float, timestampMs: Long): FilterResult3D {
        result.x = xFilter.filter(x, timestampMs)
        result.y = yFilter.filter(y, timestampMs)
        result.z = zFilter.filter(z, timestampMs)
        return result
    }

    fun reset() {
        xFilter.reset()
        yFilter.reset()
        zFilter.reset()
    }
}
