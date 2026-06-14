package com.movit.core.training.feedback

import com.movit.core.training.engine.RepIncompleteReason

/**
 * Maps [RepIncompleteReason] to [FeedbackSignal] with legacy parity:
 * ERROR severity, REPLACE_LOWER interrupt, force-audible rep-not-counted cues.
 */
object RepIncompleteFeedback {
    data class DefaultCopy(val ar: String, val en: String)

    fun messageCode(reason: RepIncompleteReason): String = when (reason) {
        RepIncompleteReason.NO_TARGET_DEPTH -> "training_rep_incomplete_depth"
        RepIncompleteReason.NO_FULL_RETURN -> "training_rep_incomplete_return"
        RepIncompleteReason.TOO_FAST -> "training_rep_too_fast"
        RepIncompleteReason.TOO_SLOW -> "training_rep_too_slow"
    }

    fun defaultCopy(reason: RepIncompleteReason): DefaultCopy = when (reason) {
        RepIncompleteReason.NO_TARGET_DEPTH -> DefaultCopy(
            ar = "لم تصل إلى الوضع المطلوب، أكمل المدى كاملاً",
            en = "You didn't reach the target. Complete the full range.",
        )
        RepIncompleteReason.NO_FULL_RETURN -> DefaultCopy(
            ar = "أكمل الرجوع إلى وضع البداية",
            en = "Return fully to the start position.",
        )
        RepIncompleteReason.TOO_FAST -> DefaultCopy(
            ar = "حركة سريعة جداً، تمهّل قليلاً",
            en = "Too fast — slow down.",
        )
        RepIncompleteReason.TOO_SLOW -> DefaultCopy(
            ar = "تجاوزت الوقت المحدد، حافظ على الإيقاع",
            en = "Too slow — keep a steady pace.",
        )
    }

    fun toSignal(
        reason: RepIncompleteReason,
        text: String,
        audioUrl: String? = null,
    ): FeedbackSignal {
        val dedupeKey = "rep_incomplete:$reason"
        return FeedbackSignal(
            kind = FeedbackKind.REP,
            severity = FeedbackSeverity.ERROR,
            text = text,
            dedupeKey = dedupeKey,
            activeKey = "correction",
            cooldownGroup = dedupeKey,
            messageCode = messageCode(reason),
            audioUrl = audioUrl,
            forceAudible = true,
            interruptPolicy = FeedbackInterruptPolicy.INTERRUPT,
        )
    }
}
