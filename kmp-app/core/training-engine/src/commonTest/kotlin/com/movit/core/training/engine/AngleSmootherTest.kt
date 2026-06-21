package com.movit.core.training.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class AngleSmootherTest {
    @Test
    fun smooth_averages_over_window() {
        val smoother = AngleSmoother(windowSize = 3)
        val first = smoother.smooth(mapOf("left_knee" to 100.0))
        assertEquals(100.0, first.getValue("left_knee"))
        val second = smoother.smooth(mapOf("left_knee" to 106.0))
        assertEquals(103.0, second.getValue("left_knee"))
        val third = smoother.smooth(mapOf("left_knee" to 112.0))
        assertEquals(106.0, third.getValue("left_knee"))
        val fourth = smoother.smooth(mapOf("left_knee" to 118.0))
        assertEquals(112.0, fourth.getValue("left_knee"))
    }

    @Test
    fun clearJoints_drops_history() {
        val smoother = AngleSmoother(windowSize = 3)
        smoother.smooth(mapOf("left_knee" to 90.0))
        smoother.clearJoints(setOf("left_knee"))
        val next = smoother.smooth(mapOf("left_knee" to 100.0))
        assertEquals(100.0, next.getValue("left_knee"))
    }
}
