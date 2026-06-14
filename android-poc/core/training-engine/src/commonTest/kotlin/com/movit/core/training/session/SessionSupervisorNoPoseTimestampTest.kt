package com.movit.core.training.session

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionSupervisorNoPoseTimestampTest {
    @Test
    fun training_noPose_reachesSupervisorWhileTraining() = runBlocking {
        var wall = 10_000L
        val supervisor = SessionSupervisor(timeProvider = { wall })
        val actions = mutableListOf<SupervisorAction>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            supervisor.actions.collect { actions.add(it) }
        }

        enterTraining(supervisor)
        yield()
        actions.clear()

        supervisor.processSignal(SupervisorSignal.NoPoseFrame(0L))
        wall = 12_500L
        supervisor.processSignal(SupervisorSignal.NoPoseFrame(0L))
        yield()

        assertEquals(SessionRunState.TRAINING, supervisor.state.value)
        assertTrue(
            actions.filterIsInstance<SupervisorAction.ShowNoPoseWarning>().isNotEmpty(),
            "expected no-pose warning, actions=$actions",
        )
        collector.cancel()
    }

    @Test
    fun effectivePresenceNow_whenSignalStuck_usesWallClock() {
        var wall = 10_000L
        val supervisor = SessionSupervisor(timeProvider = { wall })
        supervisor.testNoPoseStartTimeMs = 5_000L
        wall = 13_500L
        assertEquals(13_500L, supervisor.testEffectivePresenceNow(5_000L))
    }

    private fun enterTraining(supervisor: SessionSupervisor) {
        supervisor.onExerciseLoaded()
        supervisor.processSignal(SupervisorSignal.PoseConfirmed)
        supervisor.processSignal(SupervisorSignal.CountdownFinished)
        assertEquals(SessionRunState.TRAINING, supervisor.state.value)
    }
}
