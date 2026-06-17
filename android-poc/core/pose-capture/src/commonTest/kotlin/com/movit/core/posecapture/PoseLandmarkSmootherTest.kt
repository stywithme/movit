package com.movit.core.posecapture

import com.movit.core.training.model.Landmark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoseLandmarkSmootherTest {

    private fun frame(x: Float, y: Float, z: Float = 0f): List<Landmark> =
        List(33) { Landmark(x = x, y = y, z = z, visibility = 0.9f, presence = 0.8f) }

    @Test
    fun preservesSizeVisibilityAndPresence() {
        val out = PoseLandmarkSmoother().smooth(frame(0.3f, 0.4f), timestampMs = 0L)
        assertEquals(33, out.size)
        assertEquals(0.9f, out[10].visibility)
        assertEquals(0.8f, out[10].presence)
    }

    @Test
    fun deterministicAcrossInstances_guaranteesAndroidIosParity() {
        val sequence = listOf(
            frame(0.10f, 0.10f) to 0L,
            frame(0.60f, 0.70f) to 33L,
            frame(0.40f, 0.55f) to 66L,
            frame(0.45f, 0.50f) to 99L,
        )
        val a = PoseLandmarkSmoother()
        val b = PoseLandmarkSmoother()
        for ((landmarks, ts) in sequence) {
            val ra = a.smooth(landmarks, ts)
            val rb = b.smooth(landmarks, ts)
            for (i in ra.indices) {
                assertEquals(ra[i].x, rb[i].x)
                assertEquals(ra[i].y, rb[i].y)
                assertEquals(ra[i].z, rb[i].z)
            }
        }
    }

    @Test
    fun smoothsTowardSignal_doesNotJumpInstantly() {
        val smoother = PoseLandmarkSmoother()
        smoother.smooth(frame(0.20f, 0.20f), 0L)
        // A large jump should be damped: the filtered value stays between the previous and the target.
        val jumped = smoother.smooth(frame(0.80f, 0.80f), 33L)
        assertTrue(jumped[0].x in 0.20f..0.80f, "expected damped value, got ${jumped[0].x}")
    }

    @Test
    fun reset_behavesLikeFreshInstance() {
        val reused = PoseLandmarkSmoother()
        reused.smooth(frame(0.9f, 0.9f), 0L)
        reused.smooth(frame(0.9f, 0.9f), 33L)
        reused.reset()

        val fresh = PoseLandmarkSmoother()
        val next = frame(0.2f, 0.3f)
        val fromReset = reused.smooth(next, 66L)
        val fromFresh = fresh.smooth(next, 66L)
        for (i in next.indices) {
            assertEquals(fromFresh[i].x, fromReset[i].x)
            assertEquals(fromFresh[i].y, fromReset[i].y)
        }
    }
}
