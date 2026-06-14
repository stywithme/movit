package com.movit.core.training.engine.feedback

import com.movit.core.training.feedback.FeedbackInterruptPolicy
import com.movit.core.training.feedback.FeedbackKind
import com.movit.core.training.feedback.FeedbackSeverity
import com.movit.core.training.feedback.FeedbackSignal

/**
 * Maps training lifecycle events to [FeedbackSignal]s for [FeedbackRouter].
 *
 * Parity with legacy `FeedbackEvent` + `FeedbackManager` handlers (MO worktree).
 * Setup voice and rep-incomplete audio are owned by separate P1 tasks — not routed here.
 */
class TrainingFeedbackEventRouter(
    private val messages: TrainingSystemMessagePort,
    private val repAudioInterval: Int = REP_AUDIO_INTERVAL,
) {
    private var correctRepStreak = 0
    private var lastAnnouncedRep = 0

    fun reset() {
        correctRepStreak = 0
    }

    fun routeRepCompleted(repNumber: Int, isCounted: Boolean): FeedbackRouteBatch {
        val signals = mutableListOf<FeedbackSignal>()
        if (isCounted) {
            correctRepStreak++
            streakMotivationSignal(correctRepStreak)?.let(signals::add)
        } else {
            correctRepStreak = 0
        }
        if (repNumber - lastAnnouncedRep >= repAudioInterval) {
            signals += repCountAnnouncement(repNumber)
            lastAnnouncedRep = repNumber
        }
        return FeedbackRouteBatch(signals)
    }

    fun routeTargetReached(totalReps: Int): FeedbackSignal {
        val text = messages.resolve(
            key = "training_target_reached",
            defaultAr = "أحسنت! اكتملت {n} تكرار",
            defaultEn = "Great job! {n} reps completed",
            substitutions = mapOf("n" to totalReps.toString()),
        )
        return FeedbackSignal(
            kind = FeedbackKind.TARGET,
            severity = FeedbackSeverity.MOTIVATION,
            text = text,
            dedupeKey = "target:$totalReps",
            activeKey = "target_reached",
            cooldownGroup = "target",
            forceAudible = true,
            allowVisual = false,
            interruptPolicy = FeedbackInterruptPolicy.REPLACE_LOWER,
        )
    }

    fun routeHoldGraceStarted(): FeedbackSignal = holdMessage(
        key = "training_hold_stay",
        defaultAr = "ابق ثابتاً!",
        defaultEn = "Stay in position!",
        kind = FeedbackKind.HOLD,
        severity = FeedbackSeverity.WARNING,
        dedupeKey = "hold:grace",
        activeKey = "correction",
        cooldownGroup = "hold:grace",
        forceAudible = true,
        interruptPolicy = FeedbackInterruptPolicy.WAIT_FOR_SLOT,
    )

    fun routeHoldResumed(): FeedbackSignal = holdMessage(
        key = "training_hold_resumed",
        defaultAr = "أحسنت، استمر",
        defaultEn = "Good, keep holding",
        kind = FeedbackKind.HOLD,
        severity = FeedbackSeverity.SUCCESS,
        dedupeKey = "hold:resumed",
        activeKey = "hold:resumed",
        cooldownGroup = "hold:resumed",
        forceAudible = true,
        interruptPolicy = FeedbackInterruptPolicy.SKIP_IF_BUSY,
    )

    fun routeHoldCompleted(totalMs: Long): FeedbackSignal {
        val seconds = totalMs / 1_000L
        val text = messages.resolve(
            key = "training_hold_completed",
            defaultAr = "أحسنت! ثبات {n} ثانية",
            defaultEn = "Great job! Held for {n} seconds",
            substitutions = mapOf("n" to seconds.toString()),
        )
        return FeedbackSignal(
            kind = FeedbackKind.HOLD,
            severity = FeedbackSeverity.MOTIVATION,
            text = text,
            dedupeKey = "hold:completed",
            activeKey = "hold:completed",
            cooldownGroup = "hold:completed",
            forceAudible = true,
            allowVisual = false,
            interruptPolicy = FeedbackInterruptPolicy.REPLACE_LOWER,
        )
    }

    fun routeHoldFailed(): FeedbackSignal = holdMessage(
        key = "training_hold_failed",
        defaultAr = "فقدت الوضعية. حاول مجدداً",
        defaultEn = "Position lost. Try again",
        kind = FeedbackKind.HOLD,
        severity = FeedbackSeverity.ERROR,
        dedupeKey = "hold:failed",
        activeKey = "correction",
        cooldownGroup = "hold:failed",
        forceAudible = true,
        interruptPolicy = FeedbackInterruptPolicy.REPLACE_LOWER,
    )

    fun routeCountdownFrozen(): VignetteCue = VignetteCue.WARNING

    fun routeCountdownUnfrozen(): VignetteCue = VignetteCue.CLEAR

    fun routeVisibilityWarning(): VignetteCue = VignetteCue.WARNING

    private fun repCountAnnouncement(repNumber: Int): FeedbackSignal {
        val key = trainingNumeralKey(repNumber)
        val text = if (key != null) {
            messages.resolve(key, repNumber.toString(), repNumber.toString(), emptyMap())
        } else {
            repNumber.toString()
        }
        return FeedbackSignal(
            kind = FeedbackKind.REP,
            severity = FeedbackSeverity.INFO,
            text = text,
            dedupeKey = "rep_count:$repNumber",
            activeKey = "rep_count",
            cooldownGroup = "rep_count:$repNumber",
            forceAudible = true,
            allowTone = false,
            allowVisual = false,
            allowHaptic = false,
            interruptPolicy = FeedbackInterruptPolicy.INTERRUPT,
        )
    }

    private fun streakMotivationSignal(streak: Int): FeedbackSignal? {
        val (key, defaultAr, defaultEn) = when {
            streak >= STREAK_THRESHOLD_LARGE -> Triple(
                "training_streak_excellent",
                "ممتاز! استمر!",
                "Excellent! Keep going!",
            )
            streak >= STREAK_THRESHOLD_MEDIUM -> Triple(
                "training_streak_great",
                "أداء رائع!",
                "Great form!",
            )
            streak >= STREAK_THRESHOLD_SMALL -> Triple(
                "training_streak_good",
                "جيد!",
                "Good!",
            )
            else -> return null
        }
        return FeedbackSignal(
            kind = FeedbackKind.REP,
            severity = FeedbackSeverity.MOTIVATION,
            text = messages.resolve(key, defaultAr, defaultEn, emptyMap()),
            dedupeKey = "streak:$streak",
            activeKey = "motivation",
            cooldownGroup = "streak:$streak",
            allowVisual = false,
        )
    }

    private fun holdMessage(
        key: String,
        defaultAr: String,
        defaultEn: String,
        kind: FeedbackKind,
        severity: FeedbackSeverity,
        dedupeKey: String,
        activeKey: String,
        cooldownGroup: String,
        forceAudible: Boolean,
        interruptPolicy: FeedbackInterruptPolicy,
    ): FeedbackSignal = FeedbackSignal(
        kind = kind,
        severity = severity,
        text = messages.resolve(key, defaultAr, defaultEn, emptyMap()),
        dedupeKey = dedupeKey,
        activeKey = activeKey,
        cooldownGroup = cooldownGroup,
        forceAudible = forceAudible,
        allowVisual = false,
        interruptPolicy = interruptPolicy,
    )

    companion object {
        const val REP_AUDIO_INTERVAL = 3
        const val STREAK_THRESHOLD_SMALL = 3
        const val STREAK_THRESHOLD_MEDIUM = 5
        const val STREAK_THRESHOLD_LARGE = 10
        const val TRAINING_NUMERAL_MAX = 30

        fun trainingNumeralKey(n: Int): String? =
            if (n in 1..TRAINING_NUMERAL_MAX) "training_countdown_$n" else null
    }
}

fun interface TrainingSystemMessagePort {
    fun resolve(
        key: String,
        defaultAr: String,
        defaultEn: String,
        substitutions: Map<String, String>,
    ): String
}

data class FeedbackRouteBatch(
    val signals: List<FeedbackSignal> = emptyList(),
    val vignette: VignetteCue? = null,
)

enum class VignetteCue {
    WARNING,
    ERROR,
    CLEAR,
}
