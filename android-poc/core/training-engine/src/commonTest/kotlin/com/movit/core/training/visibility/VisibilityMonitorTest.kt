package com.movit.core.training.visibility

import com.movit.core.training.engine.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VisibilityMonitorTest {

    private val leftElbow = VisibilityJointConfig(
        joint = "left_elbow",
        role = VisibilityJointRole.PRIMARY,
    )

    @Test
    fun visibleJoints_continueTraining() {
        val monitor = VisibilityMonitor(
            visibilityTrackedJoints = listOf(leftElbow),
            timeProvider = { 0L },
        )
        val result = monitor.checkVisibility(
            jointVisibilities = mapOf("left_elbow" to 0.9f),
            currentRepCount = 2,
            currentPhase = Phase.DOWN,
        )
        assertIs<VisibilityCheckResult.ContinueTraining>(result)
        assertEquals(VisibilityState.VISIBLE, monitor.state)
    }

    @Test
    fun briefInvisibility_withinGrace_continuesTraining() {
        var now = 0L
        val monitor = VisibilityMonitor(
            visibilityTrackedJoints = listOf(leftElbow),
            graceDurationMs = 500,
            warningDurationMs = 1500,
            pauseAfterMs = 3000,
            timeProvider = { now },
        )
        monitor.checkVisibility(emptyMap(), 1, Phase.DOWN)
        now = 400L
        val result = monitor.checkVisibility(emptyMap(), 1, Phase.DOWN)
        assertIs<VisibilityCheckResult.ContinueTraining>(result)
    }

    @Test
    fun prolongedInvisibility_triggersWarningThenPause() {
        var now = 0L
        val monitor = VisibilityMonitor(
            visibilityTrackedJoints = listOf(leftElbow),
            graceDurationMs = 500,
            warningDurationMs = 1500,
            pauseAfterMs = 3000,
            timeProvider = { now },
        )
        monitor.checkVisibility(emptyMap(), 3, Phase.UP)
        now = 2000L
        val warning = monitor.checkVisibility(emptyMap(), 3, Phase.UP)
        assertIs<VisibilityCheckResult.ShowWarning>(warning)
        assertEquals(listOf("left_elbow"), warning.invisibleJoints)

        now = 3500L
        val pause = monitor.checkVisibility(emptyMap(), 3, Phase.UP)
        assertIs<VisibilityCheckResult.PauseTraining>(pause)
        assertEquals(3, pause.savedRepCount)
        assertEquals(Phase.UP, pause.savedPhase)
        assertEquals(1, monitor.getStats().totalPauseCount)
    }

    @Test
    fun pauseThenVisible_startsResumeCountdown() {
        var now = 0L
        val monitor = VisibilityMonitor(
            visibilityTrackedJoints = listOf(leftElbow),
            pauseAfterMs = 1000,
            warningDurationMs = 500,
            graceDurationMs = 0,
            timeProvider = { now },
        )
        monitor.checkVisibility(emptyMap(), 5, Phase.COUNT)
        now = 2000L
        monitor.checkVisibility(emptyMap(), 5, Phase.COUNT)

        val resume = monitor.checkVisibility(mapOf("left_elbow" to 1f), 5, Phase.COUNT)
        assertIs<VisibilityCheckResult.StartResumeCountdown>(resume)
        assertEquals(5, resume.resumeFromRep)
        assertEquals(Phase.COUNT, resume.resumeFromPhase)
        assertEquals(VisibilityState.RESUMING, monitor.state)
    }

    @Test
    fun anySideLenientPair_oneVisibleSideIsEnough() {
        val monitor = VisibilityMonitor(
            visibilityTrackedJoints = listOf(
                VisibilityJointConfig(
                    joint = "left_shoulder",
                    role = VisibilityJointRole.PRIMARY,
                    trackingMode = VisibilityTrackingMode.ANY_SIDE,
                    pairedWith = "right_shoulder",
                ),
                VisibilityJointConfig(
                    joint = "right_shoulder",
                    role = VisibilityJointRole.PRIMARY,
                    trackingMode = VisibilityTrackingMode.ANY_SIDE,
                    pairedWith = "left_shoulder",
                ),
            ),
            timeProvider = { 0L },
        )
        val result = monitor.checkVisibility(
            jointVisibilities = mapOf(
                "left_shoulder" to 0.1f,
                "right_shoulder" to 0.9f,
            ),
            currentRepCount = 0,
            currentPhase = Phase.IDLE,
        )
        assertIs<VisibilityCheckResult.ContinueTraining>(result)
    }

    @Test
    fun reset_clearsPausedState() {
        var now = 0L
        val monitor = VisibilityMonitor(
            visibilityTrackedJoints = listOf(leftElbow),
            graceDurationMs = 0,
            warningDurationMs = 0,
            pauseAfterMs = 100,
            timeProvider = { now },
        )
        monitor.checkVisibility(emptyMap(), 1, Phase.DOWN)
        now = 200L
        monitor.checkVisibility(emptyMap(), 1, Phase.DOWN)
        assertTrue(monitor.isPausedOrResuming())

        monitor.reset()
        assertEquals(VisibilityState.VISIBLE, monitor.state)
        assertTrue(!monitor.isPausedOrResuming())
    }
}
