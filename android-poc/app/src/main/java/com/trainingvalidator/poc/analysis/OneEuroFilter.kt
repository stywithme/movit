package com.trainingvalidator.poc.analysis

import com.movit.core.training.filter.FilterResult3D as KmpFilterResult3D
import com.movit.core.training.filter.OneEuroFilter as KmpOneEuroFilter
import com.movit.core.training.filter.OneEuroFilter3D as KmpOneEuroFilter3D

/**
 * OneEuroFilter - Adaptive low-pass filter for smooth, responsive tracking.
 * Delegates to KMP [com.movit.core.training.filter.OneEuroFilter].
 */
class OneEuroFilter(
    minCutoff: Float = 1.0f,
    beta: Float = 1.5f,
    dCutoff: Float = 1.0f,
) {
    private val core = KmpOneEuroFilter(minCutoff, beta, dCutoff)

    fun filter(value: Float, timestamp: Long): Float = core.filter(value, timestamp)

    fun reset() = core.reset()
}

typealias FilterResult3D = KmpFilterResult3D

/**
 * OneEuroFilter3D - One Euro Filter for 3D coordinates.
 * Delegates to KMP [com.movit.core.training.filter.OneEuroFilter3D].
 */
class OneEuroFilter3D(
    minCutoff: Float = 1.0f,
    beta: Float = 1.5f,
    dCutoff: Float = 1.0f,
) {
    private val core = KmpOneEuroFilter3D(minCutoff, beta, dCutoff)

    fun filter(x: Float, y: Float, z: Float, timestamp: Long): FilterResult3D =
        core.filter(x, y, z, timestamp)

    fun reset() = core.reset()
}
