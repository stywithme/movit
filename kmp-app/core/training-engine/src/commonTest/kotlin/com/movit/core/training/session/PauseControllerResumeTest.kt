package com.movit.core.training.session

import com.movit.core.training.visibility.VisibilityMonitor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PauseControllerResumeTest {
    @Test
    fun onUserOrSupervisorResume_clearsVisibilityFlags() {
        var now = 0L
        val controller = PauseController(visibilityResumeCountdownMs = 3_000L) { now }
        val monitor = VisibilityMonitor(
            visibilityTrackedJoints = emptyList(),
            timeProvider = { now },
        )
        controller.processVisibilityResult(
            result = com.movit.core.training.visibility.VisibilityCheckResult.PauseTraining(
                savedRepCount = 2,
                savedPhase = com.movit.core.training.engine.Phase.UP,
                invisibleJoints = listOf("left_knee"),
            ),
            emit = {},
            onAutoResumeComplete = {},
        )
        assertTrue(controller.isVisibilityPaused)
        assertTrue(controller.isCountingSuspended)

        assertTrue(controller.onUserOrSupervisorResume(monitor))

        assertFalse(controller.isVisibilityPaused)
        assertFalse(controller.isCountingSuspended)
        assertEquals(null, controller.visibilityResumeCountdown)
    }

    @Test
    fun onUserOrSupervisorResume_noOpWhenNotPaused() {
        val controller = PauseController(visibilityResumeCountdownMs = 3_000L) { 0L }
        val monitor = VisibilityMonitor(visibilityTrackedJoints = emptyList(), timeProvider = { 0L })
        assertFalse(controller.onUserOrSupervisorResume(monitor))
    }
}
