package com.movit.core.training.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PresenceSupervisorBridgeTest {
    @Test
    fun noPose_emitsWarnThenPauseAtUnifiedThresholds() {
        var now = 0L
        val bridge = PresenceSupervisorBridge(nowMs = { now })
        assertNull(bridge.onNoPoseFrame(0))
        now = 2_500L
        assertIs<PresenceSupervisorEvent.NoPoseWarning>(bridge.onNoPoseFrame(now))
        now = 4_500L
        assertIs<PresenceSupervisorEvent.NoPosePaused>(bridge.onNoPoseFrame(now))
    }

    @Test
    fun poseRestored_clearsNoPoseState() {
        var now = 0L
        val bridge = PresenceSupervisorBridge(nowMs = { now })
        bridge.onNoPoseFrame(0)
        now = 5_000L
        bridge.onNoPoseFrame(now)
        assertIs<PresenceSupervisorEvent.PoseRestored>(bridge.onPoseRestored())
        now = 6_000L
        assertNull(bridge.onNoPoseFrame(now))
    }

    @Test
    fun visibilityPaused_mapsToSupervisorSignal() {
        val event = PresenceSupervisorEvent.VisibilityPaused(
            savedRepCount = 3,
            savedPhase = com.movit.core.training.engine.Phase.COUNT,
            invisibleJoints = listOf("left_knee"),
        )
        assertEquals(SupervisorSignal.VisibilityPaused, event.toSupervisorSignal())
    }
}
