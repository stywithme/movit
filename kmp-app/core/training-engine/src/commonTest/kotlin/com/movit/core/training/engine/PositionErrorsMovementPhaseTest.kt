package com.movit.core.training.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PositionErrorsMovementPhaseTest {
    @Test
    fun positionWarning_duringDiscardedAttempt_doesNotPenalizeNextRep() {
        val counter = RepCounter(minRepIntervalMs = 0L, primaryJoints = setOf("elbow"))
        counter.addPositionWarning("setup_warning")
        counter.discardCurrentRepAttempt(RepIncompleteReason.NO_TARGET_DEPTH)
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
        counter.completeRep()
        val result = counter.getLastRepResult()!!
        assertEquals(100f, result.score)
        assertTrue(result.isCounted)
        assertEquals(0, result.positionWarningCount)
    }
}
