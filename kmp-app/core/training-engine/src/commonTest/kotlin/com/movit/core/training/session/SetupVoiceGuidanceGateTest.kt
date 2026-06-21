package com.movit.core.training.session

import com.movit.core.training.config.LocalizedText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetupVoiceGuidanceGateTest {
    private val joint = JointSetupGuidance(
        jointCode = "right_knee",
        level = SetupGuidanceLevel.RED,
        currentAngle = 120.0,
        targetMin = 160.0,
        targetMax = 180.0,
        distance = 40.0,
        direction = SetupGuidanceDirection.RAISE,
        message = LocalizedText(ar = "افرد", en = "Straighten"),
        isPrimary = true,
    )

    @Test
    fun speaksNewJointImmediately() {
        val gate = SetupVoiceGuidanceGate(voiceCooldownMs = 5_000L) { 0L }
        assertTrue(gate.shouldSpeakJointGuidance(joint))
    }

    @Test
    fun blocksSameJointUntilDoubleCooldown() {
        var now = 0L
        val gate = SetupVoiceGuidanceGate(voiceCooldownMs = 5_000L) { now }
        assertTrue(gate.shouldSpeakJointGuidance(joint))
        gate.onJointGuidanceSpoken(joint)

        now = 6_000L
        assertFalse(gate.shouldSpeakJointGuidance(joint))

        now = 10_000L
        assertTrue(gate.shouldSpeakJointGuidance(joint))
    }

    @Test
    fun allowsDifferentJointDuringBaseCooldown() {
        var now = 0L
        val gate = SetupVoiceGuidanceGate(voiceCooldownMs = 5_000L) { now }
        gate.onJointGuidanceSpoken(joint)

        val other = joint.copy(jointCode = "left_knee")
        now = 1_000L
        assertTrue(gate.shouldSpeakJointGuidance(other))
    }

    @Test
    fun ignoresYellowJointGuidance() {
        val gate = SetupVoiceGuidanceGate()
        val yellow = joint.copy(level = SetupGuidanceLevel.YELLOW)
        assertFalse(gate.shouldSpeakJointGuidance(yellow))
    }

    @Test
    fun speaksPhaseChangeImmediatelyThenDoubleCooldownForSamePhase() {
        var now = 0L
        val gate = SetupVoiceGuidanceGate(voiceCooldownMs = 5_000L) { now }
        assertTrue(gate.shouldSpeakPhaseGuidance(SetupPhase.REGION))
        gate.onPhaseGuidanceSpoken(SetupPhase.REGION)

        now = 6_000L
        assertFalse(gate.shouldSpeakPhaseGuidance(SetupPhase.REGION))

        now = 10_000L
        assertTrue(gate.shouldSpeakPhaseGuidance(SetupPhase.REGION))
    }

    @Test
    fun neverSpeaksAnglesPhaseGuidance() {
        val gate = SetupVoiceGuidanceGate()
        assertFalse(gate.shouldSpeakPhaseGuidance(SetupPhase.ANGLES))
    }

    @Test
    fun resetClearsCooldownState() {
        var now = 0L
        val gate = SetupVoiceGuidanceGate(voiceCooldownMs = 5_000L) { now }
        gate.onJointGuidanceSpoken(joint)
        gate.onPhaseGuidanceSpoken(SetupPhase.REGION)
        gate.reset()

        now = 1_000L
        assertTrue(gate.shouldSpeakJointGuidance(joint))
        assertTrue(gate.shouldSpeakPhaseGuidance(SetupPhase.REGION))
    }
}
