package com.movit.core.training.feedback

import com.movit.core.training.config.LocalizedText
import com.movit.core.training.session.JointSetupGuidance

/**
 * Builds [FeedbackSignal] for setup pose voice guidance (legacy `speakSetupPhaseGuidance` /
 * `speakSetupGuidance` parity).
 */
object SetupFeedbackSignals {
    fun phaseGuidance(
        message: LocalizedText,
        language: String,
    ): FeedbackSignal? {
        val text = message.get(language)
        if (text.isBlank()) return null
        val dedupeKey = "setup_phase:${text.hashCode()}"
        return FeedbackSignal(
            kind = FeedbackKind.SETUP,
            severity = FeedbackSeverity.WARNING,
            text = text,
            dedupeKey = dedupeKey,
            activeKey = SETUP_ACTIVE_KEY,
            cooldownGroup = dedupeKey,
            audioUrl = message.getAudioUrl(language),
            interruptPolicy = FeedbackInterruptPolicy.WAIT_FOR_SLOT,
            forceAudible = true,
            allowVisual = false,
        )
    }

    fun jointGuidance(
        joint: JointSetupGuidance,
        language: String,
    ): FeedbackSignal? {
        val text = joint.message.get(language)
        if (text.isBlank()) return null
        val dedupeKey = "setup:${joint.jointCode}"
        return FeedbackSignal(
            kind = FeedbackKind.SETUP,
            severity = FeedbackSeverity.WARNING,
            text = text,
            dedupeKey = dedupeKey,
            activeKey = SETUP_ACTIVE_KEY,
            cooldownGroup = dedupeKey,
            audioUrl = joint.message.getAudioUrl(language),
            interruptPolicy = FeedbackInterruptPolicy.WAIT_FOR_SLOT,
            forceAudible = true,
            allowVisual = false,
        )
    }

    const val SETUP_ACTIVE_KEY = "setup"
}
