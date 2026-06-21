package com.movit.core.training.filter

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Documents expected filter behaviour used by legacy Android delegates.
 */
class OneEuroFilterParityTest {

    @Test
    fun sequentialSamples_matchDocumentedSmoothing() {
        val filter = OneEuroFilter(minCutoff = 1.0f, beta = 1.5f, dCutoff = 1.0f)
        val samples = listOf(0f, 0.2f, 0.5f, 0.8f, 1.0f)
        var timestamp = 0L
        val outputs = samples.map { sample ->
            val out = filter.filter(sample, timestamp)
            timestamp += 33
            out
        }
        assertEquals(0f, outputs[0])
        assertEquals(0.2f, outputs[1], absoluteTolerance = 0.15f)
        assertEquals(1.0f, outputs.last(), absoluteTolerance = 0.2f)
    }

    @Test
    fun filter3D_axesAreIndependent() {
        val filter = OneEuroFilter3D(minCutoff = 1.0f, beta = 1.5f)
        val first = filter.filter(1f, 2f, 3f, 0)
        val second = filter.filter(1f, 2f, 3f, 33)
        assertEquals(1f, first.x)
        assertEquals(2f, first.y)
        assertEquals(3f, first.z)
        assertEquals(1f, second.x)
        assertEquals(2f, second.y)
        assertEquals(3f, second.z)
    }
}
