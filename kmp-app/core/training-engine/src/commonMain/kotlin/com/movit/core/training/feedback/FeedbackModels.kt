package com.movit.core.training.feedback

/**
 * Mode-neutral request for feedback. [FeedbackScheduler] is the only place that
 * turns this into user-facing output (live camera training is voice-first).
 */
data class FeedbackSignal(
    val kind: FeedbackKind,
    val severity: FeedbackSeverity,
    val text: String,
    val dedupeKey: String,
    val activeKey: String = dedupeKey,
    val cooldownGroup: String = dedupeKey,
    val messageCode: String? = null,
    /** Optional cached coaching clip URL (filename used for on-disk lookup). */
    val audioUrl: String? = null,
    val interruptPolicy: FeedbackInterruptPolicy = FeedbackInterruptPolicy.defaultFor(severity),
    val forceAudible: Boolean = false,
    val allowVoice: Boolean = true,
    val allowTone: Boolean = true,
    val allowVisual: Boolean = true,
    val allowHaptic: Boolean = true,
)

enum class FeedbackKind {
    JOINT_QUALITY,
    POSITION_CHECK,
    SCENE,
    VISIBILITY,
    HOLD,
    REP,
    TARGET,
    SETUP,
    COUNTDOWN,
    RANDOM,
    SYSTEM,
}

enum class FeedbackSeverity(val priority: Int) {
    MOTIVATION(10),
    SUCCESS(15),
    INFO(20),
    TIP(30),
    WARNING(50),
    ERROR(70),
    CRITICAL(100),
}

enum class FeedbackRuntimeMode {
    CAMERA,
}

enum class FeedbackInterruptPolicy {
    INTERRUPT,
    REPLACE_LOWER,
    WAIT_FOR_SLOT,
    SKIP_IF_BUSY;

    companion object {
        fun defaultFor(severity: FeedbackSeverity): FeedbackInterruptPolicy = when (severity) {
            FeedbackSeverity.CRITICAL -> INTERRUPT
            FeedbackSeverity.ERROR -> REPLACE_LOWER
            FeedbackSeverity.WARNING -> WAIT_FOR_SLOT
            FeedbackSeverity.TIP,
            FeedbackSeverity.INFO,
            FeedbackSeverity.MOTIVATION,
            FeedbackSeverity.SUCCESS -> SKIP_IF_BUSY
        }
    }
}

enum class FeedbackAudible {
    NONE,
    VOICE,
    TONE,
}

enum class FeedbackTone {
    NONE,
    SUCCESS,
    WARNING,
    ERROR,
    CRITICAL,
    INFO,
}

enum class FeedbackSpeechPriority {
    INTERRUPT,
    NORMAL,
    LOW,
}

enum class CoachIntensity {
    CALM,
    STANDARD,
    STRICT;

    companion object {
        fun from(raw: String?): CoachIntensity = when (raw?.trim()?.lowercase()) {
            "calm" -> CALM
            "strict" -> STRICT
            else -> STANDARD
        }
    }
}

enum class CameraCueMode {
    VOICE,
    TONES,
    TONES_BASIC;

    companion object {
        fun from(raw: String?): CameraCueMode = when (raw?.trim()?.lowercase()) {
            "tones", "tone" -> TONES
            "tones_basic", "tone_basic", "basic_tones", "basic" -> TONES_BASIC
            else -> VOICE
        }
    }
}

data class FeedbackDeliveryPlan(
    val shouldDeliver: Boolean,
    val audible: FeedbackAudible = FeedbackAudible.NONE,
    val speechPriority: FeedbackSpeechPriority = FeedbackSpeechPriority.NORMAL,
    val tone: FeedbackTone = FeedbackTone.NONE,
    val showVisual: Boolean = false,
    val vibrate: Boolean = false,
    val displayDurationMs: Long = 0L,
    val repeatCount: Int = 0,
    val reason: String = "",
) {
    companion object {
        fun silent(repeatCount: Int, reason: String) = FeedbackDeliveryPlan(
            shouldDeliver = false,
            repeatCount = repeatCount,
            reason = reason,
        )
    }
}
