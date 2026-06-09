package com.movit.core.training.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepCounterTest {

    @Test
    fun zonePeakScoring_usesWeakerOfUpAndDownPeaks() {
        var now = 0L
        val counter = RepCounter(
            minRepIntervalMs = 0L,
            primaryJoints = setOf("elbow"),
            timeProvider = { now },
        )

        counter.updateJointStates(
            mapOf(
                "elbow" to JointStateInfo(
                    jointCode = "elbow",
                    state = JointState.PERFECT,
                    isPrimary = true,
                    currentZone = ZoneType.UP_ZONE,
                ),
            ),
        )
        counter.updateJointStates(
            mapOf(
                "elbow" to JointStateInfo(
                    jointCode = "elbow",
                    state = JointState.PAD,
                    isPrimary = true,
                    currentZone = ZoneType.DOWN_ZONE,
                ),
            ),
        )

        now = 100L
        counter.completeRep()

        assertEquals(1, counter.count)
        assertEquals(60f, counter.getLastRepResult()?.score)
        assertTrue(counter.getLastRepResult()?.isCounted == true)
    }

    @Test
    fun positionError_reducesScoreAndUncountsRep() {
        val counter = RepCounter(minRepIntervalMs = 0L, primaryJoints = setOf("elbow"))
        counter.updateJointStates(
            mapOf(
                "elbow" to JointStateInfo(
                    jointCode = "elbow",
                    state = JointState.PERFECT,
                    isPrimary = true,
                    currentZone = ZoneType.UP_ZONE,
                ),
            ),
        )
        counter.addPositionError("knee_over_toe")
        counter.completeRep()

        val result = counter.getLastRepResult()!!
        assertEquals(85f, result.score)
        assertFalse(result.isCounted)
        assertEquals(JointState.WARNING, result.worstState)
    }

    @Test
    fun holdExercise_scoresFromTimeTracking() {
        var now = 1L
        val counter = RepCounter(
            minRepIntervalMs = 0L,
            isHoldExercise = true,
            timeProvider = { now },
        )

        counter.updateJointStates(mapOf("core" to JointStateInfo("core", JointState.PERFECT, isPrimary = true)))
        now = 801L
        counter.updateJointStates(mapOf("core" to JointStateInfo("core", JointState.NORMAL, isPrimary = true)))
        now = 1001L
        counter.completeRep()

        assertEquals(96f, counter.getLastRepResult()!!.score, absoluteTolerance = 0.1f)
    }
}
