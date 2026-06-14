package com.movit.core.training.bilateral

import com.movit.core.training.engine.RepCounter
import com.movit.core.training.session.SessionOrchestrator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BilateralCompletionTargetRepsTest {

    @Test
    fun afterAllReps_completionTargetIsDoublePerSideTarget() {
        val config = BilateralConfigInput(switchMode = BilateralSwitchMode.AFTER_ALL_REPS)
        assertEquals(24, completionTargetReps(true, config, perSideTargetReps = 12))
        assertTrue(isAfterAllRepsBilateral(true, config, perSideTargetReps = 12))
    }

    @Test
    fun legacySwitchEveryEqualsTarget_usesDoubleCompletionTarget() {
        val config = BilateralConfigInput(switchEvery = 12)
        assertEquals(24, completionTargetReps(true, config, perSideTargetReps = 12))
        assertTrue(isAfterAllRepsBilateral(true, config, perSideTargetReps = 12))
    }

    @Test
    fun nonAfterAllReps_keepsSingleCompletionTarget() {
        val config = BilateralConfigInput(switchMode = BilateralSwitchMode.EVERY_REP)
        assertEquals(12, completionTargetReps(true, config, perSideTargetReps = 12))
        assertFalse(isAfterAllRepsBilateral(true, config, perSideTargetReps = 12))
    }

    @Test
    fun afterAllReps_twelvePerSide_requiresTwentyFourBeforeCompletion_sideSwitchesAtTwelve() {
        val perSideTarget = 12
        val bilateralConfig = BilateralConfigInput(
            switchMode = BilateralSwitchMode.AFTER_ALL_REPS,
            startSide = "right",
        )
        val totalTarget = completionTargetReps(true, bilateralConfig, perSideTarget)

        val repCounter = RepCounter(
            minRepIntervalMs = 0L,
            targetReps = totalTarget,
            primaryJoints = setOf("elbow"),
        )
        val bilateral = BilateralController(
            isBilateral = true,
            config = bilateralConfig,
            targetReps = perSideTarget,
        )
        val session = SessionOrchestrator(targetReps = totalTarget)

        var targetReached = false
        repCounter.onTargetReached = {
            targetReached = true
            session.markCompleted()
        }
        session.start()

        repeat(perSideTarget - 1) { index ->
            repCounter.completeRep()
            bilateral.onRepCounted(repCounter.count)
            session.updateRepCount(repCounter.count)
            assertFalse(targetReached, "target reached too early at rep ${index + 1}")
            assertEquals(BilateralSide.RIGHT, bilateral.currentSide)
            assertFalse(session.isCompleted)
        }

        repCounter.completeRep()
        bilateral.onRepCounted(repCounter.count)
        session.updateRepCount(repCounter.count)
        assertEquals(perSideTarget, repCounter.count)
        assertFalse(targetReached, "session must not complete after first side")
        assertEquals(BilateralSide.LEFT, bilateral.currentSide)
        assertFalse(session.isCompleted)

        repeat(perSideTarget - 1) {
            repCounter.completeRep()
            bilateral.onRepCounted(repCounter.count)
            session.updateRepCount(repCounter.count)
            assertFalse(targetReached)
            assertFalse(session.isCompleted)
        }

        repCounter.completeRep()
        bilateral.onRepCounted(repCounter.count)
        session.updateRepCount(repCounter.count)

        assertEquals(totalTarget, repCounter.count)
        assertTrue(targetReached)
        assertTrue(repCounter.isTargetReached())
        assertTrue(session.isCompleted)
        assertEquals(1f, repCounter.getProgress())
    }
}
