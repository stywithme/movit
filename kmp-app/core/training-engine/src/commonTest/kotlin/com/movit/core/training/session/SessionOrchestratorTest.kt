package com.movit.core.training.session

import com.movit.core.training.engine.policy.TimingPolicy
import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionOrchestratorTest {
    @Test
    fun startPauseResumeSnapshot() {
        var now = 1_000L
        val orchestrator = SessionOrchestrator(
            timingPolicy = TimingPolicy.DEFAULT,
            wallClock = { now },
        )
        orchestrator.start()
        orchestrator.onFrameClock(sampleFrame(timestampMs = 1_000L))
        now = 4_000L
        orchestrator.pause()
        now = 7_000L
        orchestrator.resume()
        now = 9_000L
        orchestrator.onFrameClock(sampleFrame(timestampMs = 9_000L))

        val snapshot = orchestrator.snapshot()
        assertEquals(SessionLifecycle.RUNNING, snapshot.lifecycle)
        assertFalse(snapshot.isPaused)
        assertEquals(5_000L, snapshot.activeDurationMs)
    }

    @Test
    fun safetyStopOnMaxReps() {
        val orchestrator = SessionOrchestrator(
            timingPolicy = TimingPolicy.DEFAULT,
            targetReps = 5,
        )
        orchestrator.start()
        orchestrator.updateRepCount(orchestrator.safetyGuards.maxRepsGuard)

        assertTrue(orchestrator.safetyStopTriggered)
        assertTrue(orchestrator.isCompleted)
    }

    @Test
    fun shouldNotProcessWhenManuallyPaused() {
        val orchestrator = SessionOrchestrator()
        orchestrator.start()
        orchestrator.pause()
        assertFalse(orchestrator.shouldProcessFrame())
    }

    private fun sampleFrame(timestampMs: Long) = PoseFrame(
        angles = JointAngles(leftElbow = 90.0),
        landmarks = listOf(Landmark(0.5f, 0.5f, 0f, 1f, 1f)),
        isFrontCamera = false,
        timestampMs = timestampMs,
    )
}
