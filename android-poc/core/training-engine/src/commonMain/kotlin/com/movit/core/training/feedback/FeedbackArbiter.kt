package com.movit.core.training.feedback

import com.movit.core.training.boundary.AudioFeedbackPlayer
import com.movit.core.training.boundary.HapticPattern
import com.movit.core.training.boundary.HapticsPort
import com.movit.core.training.boundary.SpeechSynthesizer
import com.movit.core.training.config.FeedbackMessages

/**
 * WS-7 arbiter: [FeedbackScheduler] decides *if*; this class executes voice/haptic channels.
 * Visual delivery stays in [com.movit.core.training.engine.feedback.FeedbackRouter].
 */
class FeedbackArbiter(
    private val scheduler: FeedbackScheduler = FeedbackScheduler(),
    private val speech: SpeechSynthesizer? = null,
    private val haptics: HapticsPort? = null,
    private val audioPlayer: AudioFeedbackPlayer? = null,
    private val motivationalCoordinator: MotivationalMessageCoordinator = MotivationalMessageCoordinator(),
    private val language: String = "en",
) {
    var voiceEnabled: Boolean = true

    fun updateCoachIntensity(intensity: CoachIntensity) {
        scheduler.updateSettings(intensity, CameraCueMode.VOICE)
    }

    fun setRandomMessages(messages: FeedbackMessages?) {
        motivationalCoordinator.setMessages(messages)
    }

    fun deliver(
        signal: FeedbackSignal,
        mode: FeedbackRuntimeMode = FeedbackRuntimeMode.CAMERA,
    ): FeedbackDeliveryPlan {
        val gated = if (voiceEnabled) signal else signal.copy(allowVoice = false, forceAudible = false)
        val plan = scheduler.schedule(gated, mode)
        if (!plan.shouldDeliver) return plan
        if (gated.severity.priority >= FeedbackSeverity.WARNING.priority) {
            motivationalCoordinator.markHighPriorityDelivered()
        }
        when (plan.audible) {
            FeedbackAudible.VOICE -> {
                if (gated.text.isNotBlank()) {
                    if (audioPlayer != null) {
                        audioPlayer.play(gated)
                    } else {
                        speech?.speak(gated.text, plan.speechPriority)
                    }
                }
            }
            FeedbackAudible.TONE -> audioPlayer?.play(gated)
            FeedbackAudible.NONE -> Unit
        }
        if (plan.vibrate) {
            haptics?.vibrate(hapticFor(gated.severity))
        }
        return plan
    }

    fun tryDeliverRandomMessage(hasActiveErrors: Boolean): FeedbackDeliveryPlan? {
        val signal = motivationalCoordinator.tryBuildSignal(hasActiveErrors, language) ?: return null
        return deliver(signal)
    }

    fun speakText(
        text: String,
        kind: FeedbackKind = FeedbackKind.SYSTEM,
        severity: FeedbackSeverity = FeedbackSeverity.INFO,
        dedupeKey: String,
        forceAudible: Boolean = false,
    ) {
        if (text.isBlank()) return
        deliver(
            FeedbackSignal(
                kind = kind,
                severity = severity,
                text = text,
                dedupeKey = dedupeKey,
                forceAudible = forceAudible,
                allowVisual = false,
            ),
        )
    }

    fun resetAll() {
        scheduler.resetAll()
        motivationalCoordinator.reset()
        speech?.stop()
    }

    fun shutdown() {
        speech?.release()
    }

    private fun hapticFor(severity: FeedbackSeverity): HapticPattern = when (severity) {
        FeedbackSeverity.CRITICAL, FeedbackSeverity.ERROR -> HapticPattern.HEAVY
        FeedbackSeverity.WARNING -> HapticPattern.MEDIUM
        FeedbackSeverity.SUCCESS -> HapticPattern.MEDIUM
        else -> HapticPattern.LIGHT
    }
}
