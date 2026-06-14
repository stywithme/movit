package com.movit.core.training.session

import com.movit.core.training.engine.currentTimeMillis

/**
 * Setup voice cooldown gate (legacy [PoseSetupGuide] parity).
 *
 * Two-tier cooldown: [SetupValidationConfig.voiceCooldownMs] for new joint/phase messages,
 * 2× for repeated same joint or same phase.
 */
class SetupVoiceGuidanceGate(
    private val voiceCooldownMs: Long = SetupValidationConfig().voiceCooldownMs,
    private val nowProvider: () -> Long = { currentTimeMillis() },
) {
    private var lastVoiceJointCode: String? = null
    private var lastVoiceTimeMs: Long = 0L
    private var lastVoicePhase: SetupPhase? = null
    private var lastVoicePhaseTimeMs: Long = 0L

    fun reset() {
        lastVoiceJointCode = null
        lastVoiceTimeMs = 0L
        lastVoicePhase = null
        lastVoicePhaseTimeMs = 0L
    }

    fun shouldSpeakJointGuidance(joint: JointSetupGuidance): Boolean {
        if (joint.level != SetupGuidanceLevel.RED) return false
        val baseCooldown = voiceCooldownMs.takeIf { it > 0L } ?: DEFAULT_COOLDOWN_MS
        val now = nowProvider()
        val sameJoint = joint.jointCode == lastVoiceJointCode
        if (!sameJoint) return true
        return (now - lastVoiceTimeMs) >= baseCooldown * SAME_TARGET_MULTIPLIER
    }

    fun onJointGuidanceSpoken(joint: JointSetupGuidance) {
        lastVoiceJointCode = joint.jointCode
        lastVoiceTimeMs = nowProvider()
    }

    fun shouldSpeakPhaseGuidance(phase: SetupPhase): Boolean {
        if (phase == SetupPhase.ANGLES) return false
        val baseCooldown = voiceCooldownMs.takeIf { it > 0L } ?: DEFAULT_COOLDOWN_MS
        val now = nowProvider()
        val samePhase = phase == lastVoicePhase
        if (!samePhase) return true
        return (now - lastVoicePhaseTimeMs) >= baseCooldown * SAME_TARGET_MULTIPLIER
    }

    fun onPhaseGuidanceSpoken(phase: SetupPhase) {
        lastVoicePhase = phase
        lastVoicePhaseTimeMs = nowProvider()
    }

    private companion object {
        private const val DEFAULT_COOLDOWN_MS = 5_000L
        private const val SAME_TARGET_MULTIPLIER = 2L
    }
}
