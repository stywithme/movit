package com.movit.core.training.feedback

import com.movit.core.training.config.FeedbackMessages
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.engine.currentTimeMillis
import kotlin.random.Random

/**
 * Legacy [FeedbackManager.checkAndDeliverRandomMessage] parity (I-11):
 * quiet-time motivational/tip picker with dedupe + 70/30 motivational bias.
 */
class MotivationalMessageCoordinator(
    private val minIdleMs: Long = DEFAULT_MIN_IDLE_MS,
    private val randomCooldownMs: Long = DEFAULT_RANDOM_COOLDOWN_MS,
    private val motivationalBias: Double = 0.7,
    private val random: Random = Random.Default,
    private val nowProvider: () -> Long = { currentTimeMillis() },
) {
    private var motivational: List<LocalizedText> = emptyList()
    private var tips: List<LocalizedText> = emptyList()
    private var lastHighPriorityMs: Long = 0L
    private var lastRandomMs: Long = 0L
    private var lastDeliveredText: String? = null

    fun setMessages(messages: FeedbackMessages?) {
        motivational = messages?.motivational.orEmpty()
        tips = messages?.tips.orEmpty()
    }

    fun reset() {
        lastHighPriorityMs = nowProvider()
        lastRandomMs = 0L
        lastDeliveredText = null
    }

    fun markHighPriorityDelivered(atMs: Long = nowProvider()) {
        lastHighPriorityMs = atMs
    }

    fun tryBuildSignal(
        hasActiveErrors: Boolean,
        language: String,
    ): FeedbackSignal? {
        if (hasActiveErrors) {
            markHighPriorityDelivered()
            return null
        }
        val now = nowProvider()
        if (now - lastHighPriorityMs < minIdleMs) return null
        if (lastRandomMs > 0L && now - lastRandomMs < randomCooldownMs) return null
        if (motivational.isEmpty() && tips.isEmpty()) return null

        val useMotivational = motivational.isNotEmpty() &&
            (tips.isEmpty() || random.nextDouble() < motivationalBias)
        val message = if (useMotivational) {
            motivational.randomOrNull(random)
        } else {
            tips.randomOrNull(random)
        } ?: return null

        val text = message.display(language)
        if (text.isBlank() || text == lastDeliveredText) return null

        lastRandomMs = now
        lastDeliveredText = text
        return FeedbackSignal(
            kind = FeedbackKind.RANDOM,
            severity = if (useMotivational) FeedbackSeverity.MOTIVATION else FeedbackSeverity.TIP,
            text = text,
            dedupeKey = "random:${text.hashCode()}",
            activeKey = "random",
            cooldownGroup = "random:${text.hashCode()}",
            interruptPolicy = FeedbackInterruptPolicy.SKIP_IF_BUSY,
            allowVisual = true,
        )
    }

    private fun LocalizedText.display(language: String): String =
        if (language == "ar") ar.ifBlank { en } else en.ifBlank { ar }

    companion object {
        private const val DEFAULT_MIN_IDLE_MS = 5_000L
        private const val DEFAULT_RANDOM_COOLDOWN_MS = 10_000L
    }
}
