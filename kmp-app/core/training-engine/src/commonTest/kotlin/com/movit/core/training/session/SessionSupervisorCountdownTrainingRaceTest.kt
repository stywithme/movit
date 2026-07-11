package com.movit.core.training.session

import com.movit.core.training.model.JointAngles
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * WP-03 / C-01: COUNTDOWN→TRAINING race must not emit a second engine ingress path
 * and must leave a single StartEngine (no duplicate / lost transition).
 */
class SessionSupervisorCountdownTrainingRaceTest {
    @Test
    fun countdownFinished_concurrentWithPoseFrames_emitsStartEngineOnce_noProcessFrame() = runBlocking {
        val supervisor = SessionSupervisor(
            setupValidation = SetupValidationConfig(
                countdownToleranceMs = 100L,
                countdownCancelMs = 500L,
            ),
        )
        val actions = mutableListOf<SupervisorAction>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            supervisor.actions.collect { actions.add(it) }
        }

        supervisor.onExerciseLoaded()
        supervisor.processSignal(SupervisorSignal.PoseConfirmed)
        yield()
        assertEquals(SessionRunState.COUNTDOWN, supervisor.state.value)
        actions.clear()

        val pose = SupervisorSignal.PoseFrame(
            angles = JointAngles(),
            landmarks = emptyList(),
            isFrontCamera = true,
            timestampMs = 1_000L,
        )

        val jobs = listOf(
            async(Dispatchers.Default) {
                repeat(40) { supervisor.processSignal(pose) }
            },
            async(Dispatchers.Default) {
                supervisor.processSignal(SupervisorSignal.CountdownFinished)
            },
            async(Dispatchers.Default) {
                repeat(40) { supervisor.processSignal(pose) }
            },
        )
        jobs.awaitAll()
        yield()
        yield()

        assertEquals(SessionRunState.TRAINING, supervisor.state.value)
        assertEquals(1, actions.filterIsInstance<SupervisorAction.StartEngine>().size)
        // ProcessFrame / ValidatePose removed — sealed hierarchy has no such types.
        assertTrue(
            actions.none { it is SupervisorAction.StartCountdown },
            "countdown already running; StartCountdown must not re-fire during race",
        )
        collector.cancel()
        supervisor.close()
    }
}
