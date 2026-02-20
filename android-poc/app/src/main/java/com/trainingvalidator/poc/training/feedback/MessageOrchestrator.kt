package com.trainingvalidator.poc.training.feedback

import android.util.Log

/**
 * MessageOrchestrator - Smart message management to prevent spam
 * 
 * Core Principles:
 * 1. FIRST IS LOUD, REPEAT IS QUIET: First occurrence = full feedback, repeats = reduced
 * 2. PROGRESSIVE SILENCE: After N repeats, stop messaging (visual overlay is enough)
 * 3. CATEGORY-AWARE: Different message types have different rules
 * 4. STATE-AWARE: If issue disappears and returns, reset the counter
 * 5. ONE AT A TIME: Only the most important message gets through
 * 
 * Message Categories:
 * - CRITICAL: Safety errors (always show, reduced cooldown for repeats)
 * - ERROR: Form errors (first = audio+visual, repeat = visual only)
 * - WARNING: Pre-warnings (first = audio, repeat = visual, max 2 repeats)
 * - TIP: Suggestions (visual only, max 1 repeat)
 * - MOTIVATION: Positive feedback (infrequent, never repeat same message)
 * - INFO: Neutral information (no repeat)
 */
class MessageOrchestrator {
    
    companion object {
        private const val TAG = "MessageOrchestrator"
        
        // Max repeats before going silent
        // NOTE: In Camera mode VISUAL_ONLY still speaks (low priority), so higher
        // max repeats mean more correction chances without spam.
        private const val MAX_ERROR_REPEATS = 6
        private const val MAX_WARNING_REPEATS = 5
        private const val MAX_TIP_REPEATS = 2
        
        // Progressive cooldowns (ms) - increase with each repeat
        private val ERROR_COOLDOWNS   = listOf(2000L, 3000L, 5000L, 8000L, 12000L, 20000L)
        private val WARNING_COOLDOWNS = listOf(2500L, 4000L, 6000L, 10000L, 15000L)
        private val TIP_COOLDOWNS     = listOf(5000L, 10000L)
        private val MOTIVATION_COOLDOWN = 10000L
        
        // Time before considering issue "resolved" and resetting counter
        private const val ISSUE_RESOLVED_THRESHOLD_MS = 3000L
    }
    
    /**
     * Message category determines behavior
     */
    enum class Category {
        CRITICAL,   // Safety - always important
        ERROR,      // Form error - important but can reduce
        WARNING,    // Pre-warning - moderate importance
        TIP,        // Suggestion - low importance
        MOTIVATION, // Positive - infrequent
        INFO        // Neutral - one-time
    }
    
    /**
     * Delivery channel for the message
     */
    enum class DeliveryChannel {
        AUDIO_AND_VISUAL,  // Full feedback (first occurrence)
        VISUAL_ONLY,       // Reduced feedback (repeat)
        HAPTIC_ONLY,       // Just vibration (minimal reminder)
        SILENT             // No message (visual overlay is enough)
    }
    
    /**
     * Result of orchestration decision
     */
    data class DeliveryDecision(
        val channel: DeliveryChannel,
        val repeatCount: Int,
        val isFirstOccurrence: Boolean,
        val suggestedCooldownMs: Long
    )
    
    /**
     * Tracked state for each message key
     */
    private data class MessageState(
        var repeatCount: Int = 0,
        var lastShownTime: Long = 0L,
        var lastActiveTime: Long = 0L,  // When the issue was last detected (not shown)
        var totalOccurrences: Int = 0
    )
    
    // Track state per message key
    private val messageStates = mutableMapOf<String, MessageState>()
    
    // Track which message is currently "active" to prevent overlapping
    private var currentActiveMessageKey: String? = null
    private var currentActiveUntil: Long = 0L
        private var currentActiveCategory: Category? = null
    
    // Last message shown (to prevent same motivation twice in a row)
    private var lastMotivationMessage: String? = null
    
    // Track which message keys already logged their "max repeats" warning (prevent log spam)
    private val maxRepeatsLoggedKeys = mutableSetOf<String>()
    
    /**
     * Decide how to deliver a message
     * 
     * @param messageKey Unique identifier for this message type (e.g., "joint:left_knee:TOO_HIGH")
     * @param category Message category
     * @param messageText The actual message (for motivation duplicate check)
     * @return DeliveryDecision with channel and metadata
     */
    fun decide(
        messageKey: String,
        category: Category,
        messageText: String = ""
    ): DeliveryDecision {
        val now = System.currentTimeMillis()
        val state = messageStates.getOrPut(messageKey) { MessageState() }
        
        // Detect if issue disappeared and returned (gap in detections)
        val previousActiveTime = state.lastActiveTime
        state.lastActiveTime = now
        
        // ONE AT A TIME: Only block if a STRICTLY HIGHER priority message is active.
        // Same-priority messages (e.g. two position warnings) are allowed through —
        // the audio player queues them. Only block lower-priority vs higher-priority
        // (e.g. a TIP while a CRITICAL is active).
        if (currentActiveMessageKey != null &&
            currentActiveMessageKey != messageKey &&
            now < currentActiveUntil
        ) {
            val currentPriority = currentActiveCategory?.let { getCategoryPriority(it) } ?: 0
            val newPriority = getCategoryPriority(category)
            if (newPriority < currentPriority) {
                // Strictly lower priority - silence it
                return DeliveryDecision(
                    channel = DeliveryChannel.SILENT,
                    repeatCount = state.repeatCount,
                    isFirstOccurrence = false,
                    suggestedCooldownMs = 0
                )
            }
            if (newPriority > currentPriority) {
                Log.d(TAG, "Preempting $currentActiveMessageKey ($currentPriority) with $messageKey ($newPriority)")
            }
            // Same priority: let it through (audio player queues them)
        }
        
        // Check if issue was "resolved" and is now back
        // Use lastActiveTime (detections), NOT lastShownTime (messages),
        // otherwise cooldown-silenced states may incorrectly reset and cause spam.
        val gapSinceLastDetection = if (previousActiveTime > 0) now - previousActiveTime else 0L
        if (gapSinceLastDetection > ISSUE_RESOLVED_THRESHOLD_MS && state.repeatCount > 0) {
            // Issue went away and came back - treat as new
            Log.d(TAG, "Issue reset for $messageKey (gap ${gapSinceLastDetection}ms)")
            state.repeatCount = 0
        }
        
        // Check cooldown
        val cooldown = getCooldownForRepeat(category, state.repeatCount)
        val timeSinceLastShown = now - state.lastShownTime
        if (state.lastShownTime > 0 && timeSinceLastShown < cooldown) {
            // Still in cooldown
            return DeliveryDecision(
                channel = DeliveryChannel.SILENT,
                repeatCount = state.repeatCount,
                isFirstOccurrence = false,
                suggestedCooldownMs = cooldown - timeSinceLastShown
            )
        }
        
        // Check max repeats
        val maxRepeats = getMaxRepeats(category)
        if (state.repeatCount >= maxRepeats) {
            // Max repeats reached - go silent (visual overlay takes over)
            if (maxRepeatsLoggedKeys.add(messageKey)) {
                Log.d(TAG, "Max repeats ($maxRepeats) reached for $messageKey - going silent")
            }
            return DeliveryDecision(
                channel = DeliveryChannel.SILENT,
                repeatCount = state.repeatCount,
                isFirstOccurrence = false,
                suggestedCooldownMs = 0
            )
        }
        
        // Special handling for motivation (no duplicate messages)
        if (category == Category.MOTIVATION) {
            if (messageText == lastMotivationMessage) {
                return DeliveryDecision(
                    channel = DeliveryChannel.SILENT,
                    repeatCount = 0,
                    isFirstOccurrence = false,
                    suggestedCooldownMs = MOTIVATION_COOLDOWN
                )
            }
            lastMotivationMessage = messageText
        }
        
        // Determine delivery channel based on repeat count
        val isFirst = state.repeatCount == 0
        val channel = getDeliveryChannel(category, state.repeatCount)
        
        // Update state
        state.repeatCount++
        state.totalOccurrences++
        state.lastShownTime = now
        
        // Mark this message as active
        val displayDuration = getDisplayDuration(category)
        currentActiveMessageKey = messageKey
        currentActiveUntil = now + displayDuration
        currentActiveCategory = category
        
        Log.d(TAG, "Message decision: $messageKey -> $channel (repeat #${state.repeatCount})")
        
        return DeliveryDecision(
            channel = channel,
            repeatCount = state.repeatCount,
            isFirstOccurrence = isFirst,
            suggestedCooldownMs = getCooldownForRepeat(category, state.repeatCount)
        )
    }
    
    /**
     * Mark an issue as resolved (no longer present)
     * Call this when the error condition clears
     */
    fun markResolved(messageKey: String) {
        messageStates[messageKey]?.let { state ->
            // Don't reset immediately - let the threshold do it
            // This prevents flickering issues from causing spam
        }
        
        // Clear active if this was the active message
        if (currentActiveMessageKey == messageKey) {
            currentActiveMessageKey = null
            currentActiveUntil = 0L
            currentActiveCategory = null
        }
    }
    
    /**
     * Force reset a single message key
     */
    fun reset(messageKey: String) {
        messageStates.remove(messageKey)
        if (currentActiveMessageKey == messageKey) {
            currentActiveMessageKey = null
            currentActiveUntil = 0L
            currentActiveCategory = null
        }
    }
    
    /**
     * Reset all message states whose keys start with the given prefix.
     * Used at rep boundaries to give position-check corrections a fresh chance.
     * Example: resetCategory("position") resets all "position:*" and "position_warn:*" keys.
     */
    fun resetCategory(keyPrefix: String) {
        val toRemove = messageStates.keys.filter { it.startsWith(keyPrefix) }
        toRemove.forEach { key ->
            messageStates.remove(key)
            if (currentActiveMessageKey == key) {
                currentActiveMessageKey = null
                currentActiveUntil = 0L
                currentActiveCategory = null
            }
        }
        if (toRemove.isNotEmpty()) {
            Log.d(TAG, "Reset ${toRemove.size} message states with prefix '$keyPrefix'")
        }
    }
    
    /**
     * Reset all message states (e.g., when training restarts)
     */
    fun resetAll() {
        messageStates.clear()
        currentActiveMessageKey = null
        currentActiveUntil = 0L
        currentActiveCategory = null
        lastMotivationMessage = null
        maxRepeatsLoggedKeys.clear()
        Log.d(TAG, "All message states reset")
    }

    /**
     * Category priority for preemption (higher = more important)
     */
    private fun getCategoryPriority(category: Category): Int {
        return when (category) {
            Category.CRITICAL -> 100
            Category.ERROR -> 80
            Category.WARNING -> 60
            Category.TIP -> 40
            Category.INFO -> 30
            Category.MOTIVATION -> 20
        }
    }
    
    /**
     * Get cooldown for a specific repeat count
     */
    private fun getCooldownForRepeat(category: Category, repeatCount: Int): Long {
        val cooldowns = when (category) {
            Category.CRITICAL -> ERROR_COOLDOWNS
            Category.ERROR -> ERROR_COOLDOWNS
            Category.WARNING -> WARNING_COOLDOWNS
            Category.TIP -> TIP_COOLDOWNS
            Category.MOTIVATION -> listOf(MOTIVATION_COOLDOWN)
            Category.INFO -> listOf(Long.MAX_VALUE)  // Never repeat
        }
        
        // Use last cooldown if repeat count exceeds array
        return cooldowns.getOrElse(repeatCount) { cooldowns.lastOrNull() ?: 2000L }
    }
    
    /**
     * Get max repeats before going silent
     */
    private fun getMaxRepeats(category: Category): Int {
        return when (category) {
            Category.CRITICAL -> Int.MAX_VALUE  // Never go fully silent for safety
            Category.ERROR -> MAX_ERROR_REPEATS
            Category.WARNING -> MAX_WARNING_REPEATS
            Category.TIP -> MAX_TIP_REPEATS
            Category.MOTIVATION -> 1  // Don't repeat motivation
            Category.INFO -> 1        // One-time info
        }
    }
    
    /**
     * Get delivery channel based on category and repeat count
     * 
     * Pattern:
     * - First occurrence: AUDIO_AND_VISUAL
     * - Second occurrence: VISUAL_ONLY (or HAPTIC for critical)
     * - Third+: HAPTIC_ONLY or SILENT
     */
    private fun getDeliveryChannel(category: Category, repeatCount: Int): DeliveryChannel {
        return when (category) {
            Category.CRITICAL -> {
                // Critical errors: always some feedback
                when (repeatCount) {
                    0 -> DeliveryChannel.AUDIO_AND_VISUAL
                    1 -> DeliveryChannel.AUDIO_AND_VISUAL  // Repeat audio for critical
                    else -> DeliveryChannel.HAPTIC_ONLY
                }
            }
            Category.ERROR -> {
                // Form errors: reduce after first
                when (repeatCount) {
                    0 -> DeliveryChannel.AUDIO_AND_VISUAL
                    1 -> DeliveryChannel.VISUAL_ONLY
                    else -> DeliveryChannel.HAPTIC_ONLY
                }
            }
            Category.WARNING -> {
                // Pre-warnings: visual focus
                when (repeatCount) {
                    0 -> DeliveryChannel.AUDIO_AND_VISUAL
                    else -> DeliveryChannel.VISUAL_ONLY
                }
            }
            Category.TIP -> {
                // Tips: visual only
                DeliveryChannel.VISUAL_ONLY
            }
            Category.MOTIVATION -> {
                // Motivation: full but infrequent
                DeliveryChannel.AUDIO_AND_VISUAL
            }
            Category.INFO -> {
                // Info: visual only
                DeliveryChannel.VISUAL_ONLY
            }
        }
    }
    
    /**
     * Get how long a message should be considered "active"
     */
    private fun getDisplayDuration(category: Category): Long {
        return when (category) {
            Category.CRITICAL -> 4000L
            Category.ERROR -> 3000L
            Category.WARNING -> 2500L
            Category.TIP -> 3000L
            Category.MOTIVATION -> 2000L
            Category.INFO -> 3000L
        }
    }
}
