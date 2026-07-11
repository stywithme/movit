package com.movit.core.training.session

import kotlin.test.Test
import kotlin.test.assertEquals

class PresenceSupervisorBridgeTest {
    @Test
    fun visibilityPaused_mapsToSupervisorSignal() {
        val event = PresenceSupervisorEvent.VisibilityPaused(
            savedRepCount = 3,
            savedPhase = com.movit.core.training.engine.Phase.COUNT,
            invisibleJoints = listOf("left_knee"),
        )
        assertEquals(SupervisorSignal.VisibilityPaused, event.toSupervisorSignal())
    }

    @Test
    fun visibilityResumed_mapsToSupervisorSignal() {
        val event = PresenceSupervisorEvent.VisibilityResumed(repCount = 3)
        assertEquals(SupervisorSignal.VisibilityRestored, event.toSupervisorSignal())
    }
}
