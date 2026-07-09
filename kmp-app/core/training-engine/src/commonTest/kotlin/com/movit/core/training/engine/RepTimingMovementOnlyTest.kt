package com.movit.core.training.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RepTimingMovementOnlyTest {
    @Test
    fun setPhaseTimings_keepsOnlyMovementPhases() {
        val counter = RepCounter(minRepIntervalMs = 0L, primaryJoints = setOf("elbow"))
        counter.setPhaseTimings(
            mapOf(
                Phase.START to 400L,
                Phase.DOWN to 800L,
                Phase.BOTTOM to 200L,
                Phase.UP to 700L,
                Phase.IDLE to 300L,
            ),
        )
        counter.updateJointStates(
            mapOf(
                "elbow" to JointStateInfo(
                    jointCode = "elbow",
                    state = JointState.NORMAL,
                    isPrimary = true,
                    currentZone = ZoneType.UP_ZONE,
                ),
            ),
        )
        counter.completeRep()
        val timings = counter.getLastRepResult()!!.phaseTimings
        assertFalse(timings.containsKey("start"))
        assertFalse(timings.containsKey("idle"))
        assertEquals(800L, timings["down"])
        assertEquals(200L, timings["bottom"])
        assertEquals(700L, timings["up"])
    }

    @Test
    fun discardIncompleteAttempt_clearsPendingTimings() {
        val counter = RepCounter(minRepIntervalMs = 0L, primaryJoints = setOf("elbow"))
        counter.setPhaseTimings(mapOf(Phase.DOWN to 500L, Phase.START to 900L))
        counter.discardCurrentRepAttempt(RepIncompleteReason.TOO_FAST)
        counter.setPhaseTimings(mapOf(Phase.DOWN to 600L, Phase.UP to 500L))
        counter.updateJointStates(
            mapOf(
                "elbow" to JointStateInfo(
                    jointCode = "elbow",
                    state = JointState.NORMAL,
                    isPrimary = true,
                    currentZone = ZoneType.UP_ZONE,
                ),
            ),
        )
        counter.completeRep()
        val timings = counter.getLastRepResult()!!.phaseTimings
        assertEquals(600L, timings["down"])
        assertEquals(500L, timings["up"])
        assertFalse(timings.containsKey("start"))
    }
}
