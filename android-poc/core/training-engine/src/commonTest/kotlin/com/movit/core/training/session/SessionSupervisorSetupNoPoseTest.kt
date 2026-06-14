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

class SessionSupervisorSetupNoPoseTest {
    @Test
    fun setupPose_noPoseFrame_emitsSetupHintOnceUntilPoseReturns() = runBlocking {
        val supervisor = SessionSupervisor()
        val actions = mutableListOf<SupervisorAction>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            supervisor.actions.collect { actions.add(it) }
        }

        supervisor.onExerciseLoaded()
        assertEquals(SessionRunState.SETUP_POSE, supervisor.state.value)
        yield()

        supervisor.processSignal(SupervisorSignal.NoPoseFrame(timestampMs = 100L))
        yield()
        assertEquals(1, actions.filterIsInstance<SupervisorAction.ShowSetupNoPoseHint>().size)

        supervisor.processSignal(SupervisorSignal.NoPoseFrame(timestampMs = 200L))
        yield()
        assertEquals(1, actions.filterIsInstance<SupervisorAction.ShowSetupNoPoseHint>().size)

        collector.cancel()
    }

    @Test
    fun setupPose_poseFrameAfterNoPose_allowsAnotherHint() = runBlocking {
        val supervisor = SessionSupervisor()
        val actions = mutableListOf<SupervisorAction>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            supervisor.actions.collect { actions.add(it) }
        }

        supervisor.onExerciseLoaded()
        yield()
        supervisor.processSignal(SupervisorSignal.NoPoseFrame(timestampMs = 100L))
        yield()

        supervisor.processSignal(
            SupervisorSignal.PoseFrame(
                angles = JointAngles(),
                landmarks = emptyList(),
                isFrontCamera = true,
                timestampMs = 200L,
            ),
        )
        supervisor.processSignal(SupervisorSignal.NoPoseFrame(timestampMs = 300L))
        yield()

        assertEquals(2, actions.filterIsInstance<SupervisorAction.ShowSetupNoPoseHint>().size)
        collector.cancel()
    }
}
