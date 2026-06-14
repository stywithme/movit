package com.movit.core.training.engine.feedback

import com.movit.core.training.boundary.AudioFeedbackPlayer
import com.movit.core.training.boundary.HapticsPort
import com.movit.core.training.boundary.HapticPattern
import com.movit.core.training.boundary.SpeechSynthesizer
import com.movit.core.training.config.FeedbackMessages
import com.movit.core.training.feedback.CoachIntensity
import com.movit.core.training.feedback.FeedbackAudible
import com.movit.core.training.feedback.FeedbackDeliveryPlan
import com.movit.core.training.feedback.FeedbackKind
import com.movit.core.training.feedback.FeedbackRuntimeMode
import com.movit.core.training.feedback.SetupFeedbackSignals
import com.movit.core.training.feedback.FeedbackScheduler
import com.movit.core.training.feedback.FeedbackSeverity
import com.movit.core.training.feedback.FeedbackSignal
import com.movit.core.training.feedback.FeedbackTone
import com.movit.core.training.feedback.MotivationalMessageCoordinator

/**
 * Routes engine feedback signals through [FeedbackScheduler] (arbiter) to platform ports.
 * Decision logic stays in common; playback is platform-specific (WS-7 / I-11).
 *
 * **TTS fallback (DS-6):** when [AudioFeedbackPlayer] has no cached clip for a signal,
 * [SpeechSynthesizer] speaks [FeedbackSignal.text] — same as legacy FeedbackManager.
 */
class FeedbackRouter(
    coachIntensity: CoachIntensity = CoachIntensity.STANDARD,
    private val speech: SpeechSynthesizer? = null,
    private val haptics: HapticsPort? = null,
    private val audioPlayer: AudioFeedbackPlayer? = null,
    private val scheduler: FeedbackScheduler = FeedbackScheduler(coachIntensity = coachIntensity),
    private val motivationalCoordinator: MotivationalMessageCoordinator = MotivationalMessageCoordinator(),
) {
    var onVisualMessage: ((FeedbackVisualMessage) -> Unit)? = null
    var coachIntensity: CoachIntensity = coachIntensity
        set(value) {
            field = value
            scheduler.updateSettings(value, com.movit.core.training.feedback.CameraCueMode.VOICE)
        }

    var voiceEnabled: Boolean = true

    fun setRandomMessages(messages: FeedbackMessages?) {
        motivationalCoordinator.setMessages(messages)
    }

    fun submit(
        signal: FeedbackSignal,
        mode: FeedbackRuntimeMode = FeedbackRuntimeMode.CAMERA,
    ): FeedbackDeliveryPlan {
        val gated = if (voiceEnabled) signal else signal.copy(allowVoice = false, forceAudible = false)
        val plan = scheduler.schedule(gated, mode)
        if (!plan.shouldDeliver) return plan
        if (gated.severity.priority >= FeedbackSeverity.WARNING.priority) {
            motivationalCoordinator.markHighPriorityDelivered()
        }
        deliver(gated, plan)
        return plan
    }

    fun tryDeliverRandomMessage(
        hasActiveErrors: Boolean,
        language: String,
    ): FeedbackDeliveryPlan? {
        val signal = motivationalCoordinator.tryBuildSignal(hasActiveErrors, language) ?: return null
        return submit(signal)
    }

    fun submitSetup(signal: FeedbackSignal): FeedbackDeliveryPlan =
        submit(
            signal.copy(
                kind = FeedbackKind.SETUP,
                activeKey = SetupFeedbackSignals.SETUP_ACTIVE_KEY,
                allowVisual = false,
            ),
        )

    fun resetSetupFeedback() {
        scheduler.resetCategory("setup")
    }

    fun resetAll() {
        scheduler.resetAll()
        motivationalCoordinator.reset()
    }

    fun release() {
        speech?.stop()
        audioPlayer?.stopAll()
    }

    private fun deliver(signal: FeedbackSignal, plan: FeedbackDeliveryPlan) {
        when (plan.audible) {
            FeedbackAudible.VOICE -> {
                if (signal.text.isNotBlank()) {
                    if (audioPlayer != null) {
                        audioPlayer.play(signal)
                    } else {
                        speech?.speak(signal.text, plan.speechPriority)
                    }
                } else {
                    audioPlayer?.play(signal)
                }
            }
            FeedbackAudible.TONE -> audioPlayer?.play(signal)
            FeedbackAudible.NONE -> Unit
        }

        if (plan.vibrate || shouldHapticFor(signal, plan)) {
            haptics?.vibrate(hapticFor(signal.severity))
        }

        if (plan.showVisual || shouldShowVisual(signal)) {
            onVisualMessage?.invoke(
                FeedbackVisualMessage(
                    text = signal.text,
                    severity = signal.severity,
                    durationMs = plan.displayDurationMs,
                    messageCode = signal.messageCode,
                ),
            )
        }
    }

    private fun shouldHapticFor(signal: FeedbackSignal, plan: FeedbackDeliveryPlan): Boolean =
        signal.allowHaptic &&
            (plan.tone == FeedbackTone.CRITICAL || plan.tone == FeedbackTone.ERROR)

    private fun shouldShowVisual(signal: FeedbackSignal): Boolean =
        signal.allowVisual &&
            signal.severity.priority >= FeedbackSeverity.WARNING.priority

    private fun hapticFor(severity: FeedbackSeverity): HapticPattern = when (severity) {
        FeedbackSeverity.CRITICAL -> HapticPattern.HEAVY
        FeedbackSeverity.ERROR -> HapticPattern.MEDIUM
        FeedbackSeverity.WARNING -> HapticPattern.LIGHT
        else -> HapticPattern.LIGHT
    }
}

data class FeedbackVisualMessage(
    val text: String,
    val severity: FeedbackSeverity,
    val durationMs: Long,
    val messageCode: String? = null,
)
