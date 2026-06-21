package com.movit.core.training.session

import com.movit.core.training.model.JointAngles
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SessionSupervisorCountdownInvalidTest {
    @Test
    fun poseFrameWithoutValidSignal_doesNotResetInvalidTimer() = runBlocking {
        var now = 1_000L
        val supervisor = SessionSupervisor(
            setupValidation = SetupValidationConfig(
                countdownToleranceMs = 100L,
                countdownCancelMs = 500L,
            ),
            timeProvider = { now },
        )
        val actions = mutableListOf<SupervisorAction>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            supervisor.actions.collect { actions.add(it) }
        }

        supervisor.onExerciseLoaded()
        supervisor.processSignal(SupervisorSignal.PoseConfirmed)
        yield()
        actions.clear()

        supervisor.processSignal(
            SupervisorSignal.PoseFrame(
                angles = JointAngles(),
                landmarks = emptyList(),
                isFrontCamera = true,
                timestampMs = now,
            ),
        )
        supervisor.processSignal(SupervisorSignal.PoseInvalid)
        yield()

        now = 1_250L
        supervisor.processSignal(SupervisorSignal.PoseInvalid)
        yield()

        assertTrue(actions.filterIsInstance<SupervisorAction.FreezeCountdown>().isNotEmpty())
        collector.cancel()
    }

    @Test
    fun countdownPoseValid_resetsInvalidTimerAndUnfreezes() = runBlocking {
        var now = 1_000L
        val supervisor = SessionSupervisor(
            setupValidation = SetupValidationConfig(
                countdownToleranceMs = 100L,
                countdownCancelMs = 500L,
            ),
            timeProvider = { now },
        )
        val actions = mutableListOf<SupervisorAction>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            supervisor.actions.collect { actions.add(it) }
        }

        supervisor.onExerciseLoaded()
        supervisor.processSignal(SupervisorSignal.PoseConfirmed)
        yield()
        actions.clear()

        supervisor.processSignal(SupervisorSignal.PoseInvalid)
        now = 1_250L
        supervisor.processSignal(SupervisorSignal.PoseInvalid)
        yield()
        assertIs<SupervisorAction.FreezeCountdown>(actions.filterIsInstance<SupervisorAction.FreezeCountdown>().last())

        supervisor.processSignal(SupervisorSignal.CountdownPoseValid)
        yield()
        assertIs<SupervisorAction.UnfreezeCountdown>(actions.last())

        now = 1_400L
        supervisor.processSignal(SupervisorSignal.PoseInvalid)
        yield()
        assertEquals(0, actions.filterIsInstance<SupervisorAction.CancelCountdown>().size)
        collector.cancel()
    }
}
