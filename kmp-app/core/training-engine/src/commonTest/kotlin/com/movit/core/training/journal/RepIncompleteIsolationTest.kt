package com.movit.core.training.journal

import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.Phase
import com.movit.core.training.engine.RepCounter
import com.movit.core.training.engine.RepIncompleteReason
import com.movit.core.training.engine.JointStateInfo
import com.movit.core.training.engine.ZoneType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepIncompleteIsolationTest {
    @Test
    fun discardCurrentRepAttempt_clearsMotionBufferBeforeNextRep() {
        val recorder = MotionRecorder(
            trackedJoints = listOf("left_knee"),
            exerciseId = "squat",
            timeProvider = { 0L },
        )
        recorder.start(0L)
        recorder.record(100L, Phase.DOWN, mapOf("left_knee" to 120.0))
        recorder.record(200L, Phase.DOWN, mapOf("left_knee" to 100.0))
        recorder.discardCurrentRepAttempt()
        recorder.record(300L, Phase.DOWN, mapOf("left_knee" to 90.0))
        recorder.record(500L, Phase.UP, mapOf("left_knee" to 150.0))
        recorder.finalizeRep(
            repNumber = 1,
            phaseTimings = mapOf("down" to 200L, "up" to 200L),
            worstState = JointState.NORMAL,
            score = 90f,
        )
        val rom = recorder.finalize(endTimestampMs = 600L).repMetrics.single().metrics.rom
        assertEquals(600, rom.toInt())
    }

    @Test
    fun repCounter_discard_clearsPendingErrorsBeforeNextRep() {
        val counter = RepCounter(minRepIntervalMs = 0L, primaryJoints = setOf("elbow"))
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
        counter.addPositionWarning("knee_over_toe")
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
        assertEquals(1, counter.count)
        assertEquals(100f, result.score)
        assertTrue(result.isCounted)
        assertEquals(0, result.positionWarningCount)
    }
}
