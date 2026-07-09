package com.movit.core.training.journal

import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MotionRecorderAnySideRomTest {
    @Test
    fun finalizeRep_usesPrimaryJointNotFirstTrackedIndex() {
        val recorder = MotionRecorder(
            trackedJoints = listOf("left_shoulder", "right_knee"),
            primaryJointIndices = listOf(1),
            exerciseId = "any-side-lunge",
            timeProvider = { 0L },
        )
        recorder.start(0L)
        recorder.record(100L, Phase.DOWN, mapOf("left_shoulder" to 10.0, "right_knee" to 90.0))
        recorder.record(500L, Phase.DOWN, mapOf("left_shoulder" to 12.0, "right_knee" to 150.0))
        recorder.record(900L, Phase.UP, mapOf("left_shoulder" to 11.0, "right_knee" to 120.0))
        recorder.finalizeRep(
            repNumber = 1,
            phaseTimings = mapOf("down" to 400L, "up" to 400L),
            worstState = JointState.NORMAL,
            score = 85f,
            side = "right",
        )
        val upload = recorder.finalize(endTimestampMs = 1_000L)
        val rom = upload.repMetrics.single().metrics.rom
        assertTrue(rom > 0, "ROM should come from primary knee, not secondary shoulder")
        assertEquals(600, rom.toInt())
    }

    @Test
    fun finalizeRep_usesPrimaryJointWithConcentricSamplesForVelocity() {
        val recorder = MotionRecorder(
            trackedJoints = listOf("left_knee", "right_knee"),
            primaryJointIndices = listOf(0, 1),
            exerciseId = "any-side-lunge",
            timeProvider = { 0L },
        )
        recorder.start(0L)
        recorder.record(100L, Phase.DOWN, mapOf("left_knee" to 90.0, "right_knee" to 100.0))
        recorder.record(200L, Phase.DOWN, mapOf("left_knee" to 110.0, "right_knee" to 100.0))
        recorder.record(300L, Phase.UP, mapOf("right_knee" to 120.0))
        recorder.record(500L, Phase.UP, mapOf("right_knee" to 150.0))

        recorder.finalizeRep(
            repNumber = 1,
            phaseTimings = mapOf("down" to 200L, "up" to 200L),
            worstState = JointState.NORMAL,
            score = 85f,
            side = "right",
        )

        val velocity = recorder.finalize(endTimestampMs = 600L).repMetrics.single().metrics.velocity
        assertNotNull(velocity)
        assertTrue(velocity > 0)
    }
}
