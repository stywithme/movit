package com.movit.core.training.engine.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CameraPlacementAdvisorTest {

    @Test
    fun goodGeometry_neverAdvises() {
        val advisor = CameraPlacementAdvisor()

        val hints = feed(advisor, observability = 0.9f, frames = 120)

        assertTrue(hints.isEmpty())
        assertFalse(advisor.isAdvising)
    }

    @Test
    fun oneBadFrame_doesNotAdvise() {
        val advisor = CameraPlacementAdvisor()
        feed(advisor, observability = 0.9f, frames = 60)

        val hint = advisor.onFrame(mapOf("left_elbow" to 0.05f), 60 * 33L)

        assertNull(hint, "a single bad frame must not trigger camera advice")
    }

    @Test
    fun sustainedBadGeometry_advisesExactlyOncePerEpisode() {
        val advisor = CameraPlacementAdvisor()

        val hints = feed(advisor, observability = 0.05f, frames = 200)

        assertEquals(1, hints.size, "the hint must fire once, not every frame")
        assertTrue(advisor.isAdvising)
        assertEquals("left_elbow", hints.first().jointCode)
        assertTrue(hints.first().observability < CameraPlacementAdvisor.OBSERVABILITY_POOR)
    }

    @Test
    fun warmUp_blocksAdviceOnTheFirstFrames() {
        val advisor = CameraPlacementAdvisor()

        val early = (0 until CameraPlacementAdvisor.MIN_SAMPLES - 1).mapNotNull { frame ->
            advisor.onFrame(mapOf("left_elbow" to 0.02f), frame * 33L)
        }

        assertTrue(early.isEmpty(), "advice fired before the warm-up window closed")
    }

    @Test
    fun recovery_clearsAdvice_andAllowsANewEpisode() {
        val advisor = CameraPlacementAdvisor()
        var ts = 0L
        val first = feed(advisor, observability = 0.05f, frames = 200, startTs = ts)
        ts += 200 * 33L
        assertEquals(1, first.size)

        // Camera moved: geometry recovers, the advisory state must clear.
        val duringRecovery = feed(advisor, observability = 0.95f, frames = 200, startTs = ts)
        ts += 200 * 33L
        assertTrue(duringRecovery.isEmpty())
        assertFalse(advisor.isAdvising)

        // Camera moved back: it is allowed to speak again.
        val second = feed(advisor, observability = 0.05f, frames = 200, startTs = ts)
        assertEquals(1, second.size)
    }

    @Test
    fun worstJoint_isTheOneReported() {
        val advisor = CameraPlacementAdvisor()
        var hint: CameraPlacementHint? = null
        for (frame in 0 until 200) {
            hint = hint ?: advisor.onFrame(
                mapOf("left_elbow" to 0.6f, "right_elbow" to 0.04f),
                frame * 33L,
            )
        }

        assertNotNull(hint)
        assertEquals("right_elbow", hint.jointCode)
    }

    @Test
    fun reset_returnsToWarmUpState() {
        val advisor = CameraPlacementAdvisor()
        feed(advisor, observability = 0.05f, frames = 200)
        assertTrue(advisor.isAdvising)

        advisor.reset()

        assertFalse(advisor.isAdvising)
        val early = (0 until CameraPlacementAdvisor.MIN_SAMPLES - 1).mapNotNull { frame ->
            advisor.onFrame(mapOf("left_elbow" to 0.02f), frame * 33L)
        }
        assertTrue(early.isEmpty())
    }

    private fun feed(
        advisor: CameraPlacementAdvisor,
        observability: Float,
        frames: Int,
        startTs: Long = 0L,
    ): List<CameraPlacementHint> = (0 until frames).mapNotNull { frame ->
        advisor.onFrame(mapOf("left_elbow" to observability), startTs + frame * 33L)
    }
}
