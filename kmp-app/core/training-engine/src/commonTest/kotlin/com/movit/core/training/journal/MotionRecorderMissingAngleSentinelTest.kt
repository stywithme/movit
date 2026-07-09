package com.movit.core.training.journal

import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotionRecorderMissingAngleSentinelTest {
    @Test
    fun missingAngleKey_recordsSentinel_notZero() {
        val array = anglesToShortArray(
            trackedJoints = listOf("left_knee"),
            angles = emptyMap(),
            skippedJointCodes = emptySet(),
        )
        assertEquals(JOINT_SKIPPED_ANGLE_SENTINEL, array[0])
    }

    @Test
    fun missingAngleKey_doesNotDeflateRomToZero() {
        val recorder = MotionRecorder(
            trackedJoints = listOf("left_knee"),
            exerciseId = "squat",
            timeProvider = { 0L },
        )
        recorder.start(0L)
        recorder.record(100L, Phase.DOWN, mapOf("left_knee" to 120.0))
        recorder.record(200L, Phase.DOWN, angles = emptyMap())
        recorder.record(300L, Phase.UP, mapOf("left_knee" to 60.0))
        recorder.finalizeRep(
            repNumber = 1,
            phaseTimings = mapOf("down" to 200L, "up" to 100L),
            worstState = JointState.NORMAL,
            score = 80f,
        )
        val rom = recorder.finalize(endTimestampMs = 400L).repMetrics.single().metrics.rom
        assertTrue(rom > 0, "Missing key must not be treated as 0° and collapse ROM")
        assertEquals(600, rom.toInt())
    }
}
