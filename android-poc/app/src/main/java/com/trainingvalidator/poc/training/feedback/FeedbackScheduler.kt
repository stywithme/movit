package com.trainingvalidator.poc.training.feedback

/**
 * Single owner of feedback timing, priority, repetition, and mode routing.
 *
 * Channel policy:
 * - Camera mode is voice-only while the trainee is away from the phone.
 * - Video mode is text-only while the trainee reviews the completed exercise.
 */
class FeedbackScheduler(
    private var coachIntensity: CoachIntensity = CoachIntensity.STANDARD,
    @Suppress("UNUSED_PARAMETER")
    cameraCueMode: CameraCueMode = CameraCueMode.VOICE,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private data class SignalState(
        var repeatCount: Int = 0,
        var lastDeliveredTime: Long = 0L,
        var lastSeenTime: Long = 0L
    )

    private data class ActiveSignal(
        val dedupeKey: String,
        val activeKey: String,
        val severity: FeedbackSeverity,
        val untilMs: Long
    )

    private val states = mutableMapOf<String, SignalState>()
    private var activeSignal: ActiveSignal? = null
    private var lastAudibleTimeMs = 0L
    private var lastMotivationText: String? = null

    fun updateSettings(
        coachIntensity: CoachIntensity,
        @Suppress("UNUSED_PARAMETER")
        cameraCueMode: CameraCueMode
    ) {
        this.coachIntensity = coachIntensity
    }

    fun schedule(
        signal: FeedbackSignal,
        mode: FeedbackRuntimeMode
    ): FeedbackDeliveryPlan {
        val now = nowProvider()
        val stateKey = signal.cooldownGroup.ifBlank { signal.dedupeKey }
        val state = states.getOrPut(stateKey) { SignalState() }
        val previousSeen = state.lastSeenTime
        state.lastSeenTime = now

        if (previousSeen > 0 &&
            now - previousSeen > ISSUE_RESOLVED_THRESHOLD_MS &&
            state.repeatCount > 0
        ) {
            state.repeatCount = 0
        }

        val active = activeSignal
        if (active != null && now >= active.untilMs) {
            activeSignal = null
        } else if (active != null &&
            (active.activeKey != signal.activeKey || active.dedupeKey != signal.dedupeKey)
        ) {
            val canReplace = when (signal.interruptPolicy) {
                FeedbackInterruptPolicy.INTERRUPT ->
                    signal.severity.priority >= active.severity.priority
                FeedbackInterruptPolicy.REPLACE_LOWER ->
                    signal.severity.priority > active.severity.priority
                FeedbackInterruptPolicy.WAIT_FOR_SLOT,
                FeedbackInterruptPolicy.SKIP_IF_BUSY -> false
            }
            if (!canReplace) {
                return FeedbackDeliveryPlan.silent(state.repeatCount, "active:${active.activeKey}")
            }
        }

        val cooldown = cooldownFor(signal.severity, state.repeatCount)
        val timeSinceDelivery = now - state.lastDeliveredTime
        if (state.lastDeliveredTime > 0 && timeSinceDelivery < cooldown) {
            return FeedbackDeliveryPlan.silent(state.repeatCount, "cooldown")
        }

        if (state.repeatCount >= maxRepeatsFor(signal.severity)) {
            return FeedbackDeliveryPlan.silent(state.repeatCount, "max_repeats")
        }

        if (signal.severity == FeedbackSeverity.MOTIVATION &&
            signal.text.isNotBlank() &&
            signal.text == lastMotivationText
        ) {
            return FeedbackDeliveryPlan.silent(state.repeatCount, "duplicate_motivation")
        }

        val repeatBeforeDelivery = state.repeatCount
        val audible = audibleFor(signal, mode, repeatBeforeDelivery, now)
        val tone = FeedbackTone.NONE
        val showVisual = mode == FeedbackRuntimeMode.VIDEO &&
            signal.allowVisual &&
            signal.text.isNotBlank()
        val vibrate = false

        if (audible == FeedbackAudible.NONE && !showVisual && !vibrate) {
            return FeedbackDeliveryPlan.silent(state.repeatCount, "no_channel")
        }

        state.lastDeliveredTime = now
        state.repeatCount++
        if (signal.severity == FeedbackSeverity.MOTIVATION && signal.text.isNotBlank()) {
            lastMotivationText = signal.text
        }
        if (audible != FeedbackAudible.NONE) {
            lastAudibleTimeMs = now
        }

        activeSignal = ActiveSignal(
            dedupeKey = signal.dedupeKey,
            activeKey = signal.activeKey,
            severity = signal.severity,
            untilMs = now + activeWindowFor(signal.severity)
        )

        return FeedbackDeliveryPlan(
            shouldDeliver = true,
            audible = audible,
            speechPriority = speechPriorityFor(signal),
            tone = tone,
            showVisual = showVisual,
            vibrate = vibrate,
            displayDurationMs = displayDurationFor(signal.severity),
            repeatCount = state.repeatCount
        )
    }

    fun reset(messageKey: String) {
        states.remove(messageKey)
        activeSignal?.takeIf { it.activeKey == messageKey || it.dedupeKey == messageKey }?.let {
            activeSignal = null
        }
    }

    fun resetCategory(prefix: String) {
        val keys = states.keys.filter { it.startsWith(prefix) }
        keys.forEach { states.remove(it) }
        activeSignal?.takeIf {
            it.activeKey.startsWith(prefix) || it.dedupeKey.startsWith(prefix)
        }?.let { activeSignal = null }
    }

    fun resetAll() {
        states.clear()
        activeSignal = null
        lastAudibleTimeMs = 0L
        lastMotivationText = null
    }

    private fun audibleFor(
        signal: FeedbackSignal,
        mode: FeedbackRuntimeMode,
        repeatCount: Int,
        now: Long
    ): FeedbackAudible {
        if (mode != FeedbackRuntimeMode.CAMERA) return FeedbackAudible.NONE

        val wantsAudible = signal.forceAudible || when (signal.severity) {
            FeedbackSeverity.CRITICAL -> repeatCount <= 1
            FeedbackSeverity.ERROR -> repeatCount == 0
            FeedbackSeverity.WARNING -> repeatCount == 0
            FeedbackSeverity.TIP -> repeatCount == 0
            FeedbackSeverity.MOTIVATION -> repeatCount == 0
            FeedbackSeverity.INFO -> repeatCount == 0
            FeedbackSeverity.SUCCESS -> repeatCount == 0
        }
        if (!wantsAudible) return FeedbackAudible.NONE

        if (!signal.forceAudible &&
            signal.severity != FeedbackSeverity.CRITICAL &&
            now - lastAudibleTimeMs < minAudibleGapMs()
        ) {
            return FeedbackAudible.NONE
        }

        return if (signal.allowVoice && signal.text.isNotBlank()) {
            FeedbackAudible.VOICE
        } else {
            FeedbackAudible.NONE
        }
    }

    private fun speechPriorityFor(signal: FeedbackSignal): FeedbackSpeechPriority = when {
        signal.severity == FeedbackSeverity.CRITICAL ||
            signal.interruptPolicy == FeedbackInterruptPolicy.INTERRUPT -> FeedbackSpeechPriority.INTERRUPT
        signal.forceAudible -> FeedbackSpeechPriority.NORMAL
        signal.severity == FeedbackSeverity.TIP ||
            signal.severity == FeedbackSeverity.MOTIVATION -> FeedbackSpeechPriority.LOW
        else -> FeedbackSpeechPriority.NORMAL
    }

    private fun cooldownFor(severity: FeedbackSeverity, repeatCount: Int): Long {
        val base = when (severity) {
            FeedbackSeverity.CRITICAL -> listOf(1800L, 2500L, 4000L, 7000L)
            FeedbackSeverity.ERROR -> listOf(2200L, 4500L, 8000L, 14000L, 22000L)
            FeedbackSeverity.WARNING -> listOf(3000L, 6000L, 10000L, 16000L)
            FeedbackSeverity.TIP -> listOf(8000L, 16000L)
            FeedbackSeverity.MOTIVATION -> listOf(14000L)
            FeedbackSeverity.SUCCESS -> listOf(1500L)
            FeedbackSeverity.INFO -> listOf(Long.MAX_VALUE)
        }.let { cooldowns -> cooldowns.getOrElse(repeatCount) { cooldowns.last() } }

        if (base == Long.MAX_VALUE) return Long.MAX_VALUE
        return (base * cooldownMultiplier()).toLong()
    }

    private fun maxRepeatsFor(severity: FeedbackSeverity): Int {
        val base = when (severity) {
            FeedbackSeverity.CRITICAL -> Int.MAX_VALUE
            FeedbackSeverity.ERROR -> 4
            FeedbackSeverity.WARNING -> 3
            FeedbackSeverity.TIP -> 1
            FeedbackSeverity.MOTIVATION -> 1
            FeedbackSeverity.SUCCESS -> 1
            FeedbackSeverity.INFO -> 1
        }
        return when (coachIntensity) {
            CoachIntensity.CALM -> (base - 1).coerceAtLeast(1)
            CoachIntensity.STANDARD -> base
            CoachIntensity.STRICT -> if (base == Int.MAX_VALUE) base else base + 1
        }
    }

    private fun cooldownMultiplier(): Double = when (coachIntensity) {
        CoachIntensity.CALM -> 1.6
        CoachIntensity.STANDARD -> 1.0
        CoachIntensity.STRICT -> 0.75
    }

    private fun minAudibleGapMs(): Long = when (coachIntensity) {
        CoachIntensity.CALM -> 3000L
        CoachIntensity.STANDARD -> 2000L
        CoachIntensity.STRICT -> 1200L
    }

    private fun activeWindowFor(severity: FeedbackSeverity): Long = when (severity) {
        FeedbackSeverity.CRITICAL -> 2800L
        FeedbackSeverity.ERROR -> 2400L
        FeedbackSeverity.WARNING -> 2000L
        FeedbackSeverity.TIP -> 1600L
        FeedbackSeverity.MOTIVATION -> 1400L
        FeedbackSeverity.SUCCESS -> 800L
        FeedbackSeverity.INFO -> 1200L
    }

    private fun displayDurationFor(severity: FeedbackSeverity): Long = when (severity) {
        FeedbackSeverity.CRITICAL -> 4000L
        FeedbackSeverity.ERROR -> 3000L
        FeedbackSeverity.WARNING -> 2500L
        FeedbackSeverity.TIP -> 3000L
        FeedbackSeverity.MOTIVATION -> 2200L
        FeedbackSeverity.SUCCESS -> 1200L
        FeedbackSeverity.INFO -> 2500L
    }

    companion object {
        private const val ISSUE_RESOLVED_THRESHOLD_MS = 3000L
    }
}
