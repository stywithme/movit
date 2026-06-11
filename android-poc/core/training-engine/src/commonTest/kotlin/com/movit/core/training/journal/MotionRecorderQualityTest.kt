package com.movit.core.training.journal

import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MotionRecorderQualityTest {
    @Test
    fun sessionQualityMeta_tracksDropRateAndCoverage() {
        val recorder = MotionRecorder(
            trackedJoints = listOf("left_knee", "right_knee"),
            exerciseId = "bodyweight-squat",
            timeProvider = { 1_000L },
        )
        recorder.start(1_000L)
        repeat(10) { index ->
            recorder.record(
                timestamp = 1_000L + index * 33L,
                phase = Phase.DOWN,
                angles = mapOf("left_knee" to 90.0, "right_knee" to 95.0),
            )
        }
        recorder.record(
            timestamp = 1_400L,
            phase = Phase.DOWN,
            angles = emptyMap(),
            skippedJointCodes = setOf("left_knee", "right_knee"),
        )
        val meta = recorder.sessionQualityMeta()
        assertEquals(11, meta.framesOffered)
        assertEquals(11, meta.framesRecorded)
        assertNotNull(meta.jointCoverageRatio)
        assertEquals(0f, meta.frameDropRate)
    }

    @Test
    fun snapshot_persistsFrameStatsForJournalRestore() {
        val recorder = MotionRecorder(
            trackedJoints = listOf("left_knee"),
            exerciseId = "squat",
            timeProvider = { 0L },
        )
        recorder.start(0L)
        recorder.record(100L, Phase.UP, mapOf("left_knee" to 120.0))
        val snapshot = recorder.snapshot("sess-q", isAssessmentMode = false)
        assertEquals(1, snapshot.framesOffered)
        val restored = MotionRecorder(
            trackedJoints = listOf("left_knee"),
            exerciseId = "squat",
        )
        restored.restore(snapshot)
        assertEquals(1, restored.sessionQualityMeta().framesOffered)
    }
}
