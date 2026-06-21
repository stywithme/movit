package com.movit.core.training.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhaseStateMachineTest {

    private val joint = object : PhaseJointConfig {
        override val jointCode: String = "left_elbow"
        override fun hasUpDownRanges(): Boolean = true
        override fun hasHoldRange(): Boolean = false
        override fun upRangeEffectiveMin(): Double = 120.0
        override fun upRangeOutermostMax(): Double = 180.0
        override fun downRangeOutermostMin(): Double = 0.0
        override fun downRangeEffectiveMax(): Double = 80.0
        override fun holdRangeOutermostMin(): Double = 0.0
        override fun holdRangeEffectiveMax(): Double = 0.0
    }

    private fun machine(
        time: () -> Long = { 0L },
        minRepIntervalMs: Long = 100L,
        minPhaseDurationMs: Long = 0L,
    ): PhaseStateMachine {
        return PhaseStateMachine(
            countingMethod = CountingMethod.UP_DOWN,
            primaryJoints = listOf(joint),
            timing = PhaseTimingConfig(
                minRepIntervalMs = minRepIntervalMs,
                maxRepIntervalMs = 60_000L,
                minPhaseDurationMs = minPhaseDurationMs,
            ),
            timeProvider = time,
            phaseHysteresisDegrees = 0.0,
        )
    }

    @Test
    fun upDown_fullCycleCompletesRep() {
        var now = 0L
        val sm = machine(time = { now })
        var repCompleted = false
        sm.onRepCompleted = { repCompleted = true }

        assertEquals(Phase.START, sm.update(mapOf("left_elbow" to 150.0)))
        assertEquals(Phase.START, sm.currentPhase)

        now = 200L
        assertEquals(Phase.DOWN, sm.update(mapOf("left_elbow" to 100.0)))

        now = 400L
        assertEquals(Phase.BOTTOM, sm.update(mapOf("left_elbow" to 60.0)))

        now = 600L
        assertEquals(Phase.UP, sm.update(mapOf("left_elbow" to 100.0)))

        now = 900L
        assertEquals(Phase.START, sm.update(mapOf("left_elbow" to 150.0)))

        assertTrue(repCompleted)
        assertTrue(sm.wasRepJustCompleted())
    }

    @Test
    fun upDown_partialDepthDoesNotCompleteRep() {
        var now = 0L
        val sm = machine(time = { now }, minPhaseDurationMs = 0L)
        var incomplete: RepIncompleteReason? = null
        sm.onRepIncomplete = { incomplete = it }

        sm.update(mapOf("left_elbow" to 150.0))
        now = 100L
        sm.update(mapOf("left_elbow" to 100.0))
        now = 200L
        sm.update(mapOf("left_elbow" to 150.0))

        assertEquals(RepIncompleteReason.NO_TARGET_DEPTH, incomplete)
        assertFalse(sm.wasRepJustCompleted())
    }

    @Test
    fun hold_entersAndExitsCountPhase() {
        val holdJoint = object : PhaseJointConfig {
            override val jointCode: String = "core"
            override fun hasUpDownRanges(): Boolean = false
            override fun hasHoldRange(): Boolean = true
            override fun upRangeEffectiveMin(): Double = 0.0
            override fun upRangeOutermostMax(): Double = 0.0
            override fun downRangeOutermostMin(): Double = 0.0
            override fun downRangeEffectiveMax(): Double = 0.0
            override fun holdRangeOutermostMin(): Double = 70.0
            override fun holdRangeEffectiveMax(): Double = 110.0
        }
        val sm = PhaseStateMachine(
            countingMethod = CountingMethod.HOLD,
            primaryJoints = listOf(holdJoint),
            timing = PhaseTimingConfig(500L, 60_000L, 0L),
            phaseHysteresisDegrees = 0.0,
        )

        assertEquals(Phase.COUNT, sm.update(mapOf("core" to 90.0)))
        assertEquals(Phase.IDLE, sm.update(mapOf("core" to 120.0)))
    }
}
