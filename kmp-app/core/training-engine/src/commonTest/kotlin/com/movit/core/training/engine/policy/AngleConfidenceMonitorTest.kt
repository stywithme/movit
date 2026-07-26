package com.movit.core.training.engine.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AngleConfidenceMonitorTest {

    @Test
    fun emptyConfidence_isFullyTrusted() {
        val monitor = AngleConfidenceMonitor(primaryJointCodes = setOf("left_elbow"))

        val verdict = monitor.evaluate(emptyMap(), isBilateralFlipped = false)

        assertEquals(AngleConfidenceVerdict.TRUSTED, verdict)
        assertEquals(0, monitor.snapshot().measuredFrames)
    }

    @Test
    fun aboveScoringMin_staysTrusted() {
        val monitor = AngleConfidenceMonitor(primaryJointCodes = setOf("left_elbow"))

        val verdict = monitor.evaluate(mapOf("left_elbow" to 0.9f), isBilateralFlipped = false)

        assertTrue(verdict.untrustedJointCodes.isEmpty())
        assertFalse(verdict.freezePhase)
    }

    @Test
    fun belowScoringMin_dropsScoringButKeepsCounting() {
        val monitor = AngleConfidenceMonitor(primaryJointCodes = setOf("left_elbow"))

        val verdict = monitor.evaluate(
            mapOf("left_elbow" to AngleConfidenceDefaults.SCORING_MIN - 0.01f),
            isBilateralFlipped = false,
        )

        assertEquals(setOf("left_elbow"), verdict.untrustedJointCodes)
        // Still above the freeze threshold: the rep is allowed to close, it just is not graded.
        assertFalse(verdict.freezePhase)
    }

    @Test
    fun belowFreezeMin_onPrimaryJoint_freezesPhase() {
        val monitor = AngleConfidenceMonitor(primaryJointCodes = setOf("left_elbow"))

        val verdict = monitor.evaluate(
            mapOf("left_elbow" to AngleConfidenceDefaults.PHASE_FREEZE_MIN - 0.01f),
            isBilateralFlipped = false,
        )

        assertTrue(verdict.freezePhase)
    }

    @Test
    fun belowFreezeMin_onSecondaryJoint_doesNotFreezePhase() {
        val monitor = AngleConfidenceMonitor(primaryJointCodes = setOf("left_knee"))

        val verdict = monitor.evaluate(mapOf("left_elbow" to 0.05f), isBilateralFlipped = false)

        assertEquals(setOf("left_elbow"), verdict.untrustedJointCodes)
        assertFalse(verdict.freezePhase, "a non-primary joint must never stall rep counting")
    }

    @Test
    fun bilateralFlip_mapsConfidenceOntoTheJointThatReadsIt() {
        val monitor = AngleConfidenceMonitor(primaryJointCodes = setOf("left_elbow"))

        // Flipped: tracked `left_elbow` reads the *right* elbow angle, so the right elbow's
        // confidence is what describes it. Without the hop this would gate the wrong arm.
        val verdict = monitor.evaluate(
            mapOf("right_elbow" to 0.05f, "left_elbow" to 1f),
            isBilateralFlipped = true,
        )

        assertEquals(setOf("left_elbow"), verdict.untrustedJointCodes)
        assertTrue(verdict.freezePhase)
    }

    @Test
    fun snapshot_reportsPerJointAndFrozenShares() {
        val monitor = AngleConfidenceMonitor(primaryJointCodes = setOf("left_elbow"))

        monitor.evaluate(mapOf("left_elbow" to 1f), isBilateralFlipped = false)
        monitor.evaluate(mapOf("left_elbow" to 1f), isBilateralFlipped = false)
        monitor.evaluate(mapOf("left_elbow" to 0.1f), isBilateralFlipped = false)
        monitor.evaluate(mapOf("left_elbow" to 0.3f), isBilateralFlipped = false)

        val report = monitor.snapshot()

        assertEquals(4, report.measuredFrames)
        assertEquals(0.5f, report.lowConfidenceShareByJoint["left_elbow"])
        assertEquals(0.25f, report.frozenFrameShare)
    }

    @Test
    fun reset_clearsSessionStatistics() {
        val monitor = AngleConfidenceMonitor(primaryJointCodes = setOf("left_elbow"))
        monitor.evaluate(mapOf("left_elbow" to 0.1f), isBilateralFlipped = false)

        monitor.reset()

        val report = monitor.snapshot()
        assertEquals(0, report.measuredFrames)
        assertEquals(0f, report.frozenFrameShare)
        assertTrue(report.lowConfidenceShareByJoint.isEmpty())
    }
}
