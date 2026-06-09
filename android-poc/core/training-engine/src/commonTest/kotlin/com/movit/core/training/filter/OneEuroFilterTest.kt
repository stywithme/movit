package com.movit.core.training.filter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OneEuroFilterTest {

    @Test
    fun firstSample_isPassedThrough() {
        val filter = OneEuroFilter()
        assertEquals(1.0f, filter.filter(1.0f, timestampMs = 0))
    }

    @Test
    fun slowMovement_smoothsTowardTarget() {
        val filter = OneEuroFilter(minCutoff = 0.5f, beta = 0.5f)
        var value = filter.filter(0f, 0)
        value = filter.filter(1f, 33)
        assertTrue(value in 0f..1f)
        assertTrue(value < 1f)
    }

    @Test
    fun reset_restartsFromNextSample() {
        val filter = OneEuroFilter()
        filter.filter(5f, 0)
        filter.filter(10f, 33)
        filter.reset()
        assertEquals(2f, filter.filter(2f, 66))
    }

    @Test
    fun filter3D_updatesAllAxes() {
        val filter = OneEuroFilter3D()
        val (x, y, z) = filter.filter(1f, 2f, 3f, 0)
        assertEquals(1f, x)
        assertEquals(2f, y)
        assertEquals(3f, z)
    }
}
