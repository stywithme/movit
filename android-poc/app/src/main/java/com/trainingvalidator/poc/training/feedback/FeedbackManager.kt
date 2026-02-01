package com.trainingvalidator.poc.training.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.ContextCompat
import com.trainingvalidator.poc.storage.AudioCacheManager
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.engine.PositionError
import com.trainingvalidator.poc.training.models.CheckSeverity
import com.trainingvalidator.poc.training.models.FeedbackMessages
import com.trainingvalidator.poc.training.models.JointError
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.LocalizedText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.*

/**
 * FeedbackManager - Professional mode-aware feedback delivery system
 * 
 * MODE-AWARE BEHAVIOR:
 * - Camera Mode: Audio-first (TTS + Haptic), minimal visual messages
 * - Video Mode:  Visual-first (Glassmorphic messages), no audio/haptic
 * 
 * SMART MESSAGING (via MessageOrchestrator):
 * - First is loud, repeat is quiet
 * - Progressive silence after max repeats
 * - Category-aware delivery channels
 * - State-aware reset when issue resolves
 * 
 * Features:
 * - Text-to-speech for audio feedback (Camera mode)
 * - Vibration patterns for haptic feedback (Camera mode)
 * - Smart throttling via MessageOrchestrator
 * - Priority-based feedback queuing
 * - Streak tracking for motivation
 */
class FeedbackManager(
    private val context: Context,
    private val config: FeedbackConfig = FeedbackConfig()
) {
    
    companion object {
        private const val TAG = "FeedbackManager"
        
        // Rep announcement interval
        private const val REP_AUDIO_INTERVAL = 3  // Announce every N reps
        
        // Streak thresholds for motivation
        private const val STREAK_THRESHOLD_SMALL = 3
        private const val STREAK_THRESHOLD_MEDIUM = 5
        private const val STREAK_THRESHOLD_LARGE = 10
        
        // Random message settings (LOW priority - fills quiet time)
        // NOTE: These are now configurable via SettingsManager
        private const val MIN_IDLE_TIME_FOR_RANDOM_MS_DEFAULT = 5000L
        private const val RANDOM_MESSAGE_COOLDOWN_MS_DEFAULT = 10000L
    }
    
    // Smart message orchestrator (prevents spam, manages delivery)
    private val messageOrchestrator = MessageOrchestrator()
    
    // ===== MODE-AWARE SETTINGS =====
    var isVideoMode: Boolean = false
        set(value) {
            field = value
            Log.d(TAG, "Mode changed: ${if (value) "VIDEO" else "CAMERA"}")
        }
    
    // TTS enabled only in Camera mode and if voice is enabled
    private val isTtsEnabled: Boolean
        get() = !isVideoMode && isVoiceEnabled()
    
    // Haptic enabled only in Camera mode
    private val isHapticEnabled: Boolean
        get() = !isVideoMode && config.enableHaptic
    
    // Event flow for UI to observe (Glassmorphic messages in Video mode)
    private val _events = MutableSharedFlow<FeedbackEvent>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val events: SharedFlow<FeedbackEvent> = _events
    
    // Visual message events (for Glassmorphic pills in Video mode)
    private val _visualMessages = MutableSharedFlow<VisualMessage>(
        replay = 0,
        extraBufferCapacity = 5
    )
    val visualMessages: SharedFlow<VisualMessage> = _visualMessages
    
    // Text-to-speech (legacy, used as fallback)
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    
    // Runtime voice control
    private var voiceEnabledOverride: Boolean? = null
    
    /**
     * Enable or disable voice feedback at runtime
     */
    fun setVoiceEnabled(enabled: Boolean) {
        voiceEnabledOverride = enabled
        Log.d(TAG, "Voice feedback ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Check if voice is currently enabled
     */
    private fun isVoiceEnabled(): Boolean {
        return voiceEnabledOverride ?: config.enableAudio
    }
    
    // Audio feedback player (prioritizes cached audio over TTS)
    private var audioPlayer: AudioFeedbackPlayer? = null
    private var useAudioPlayer = false
    
    // Vibrator
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        // Avoid deprecated string-based service lookup
        ContextCompat.getSystemService(context, Vibrator::class.java)
    }
    
    // Cooldown tracking (legacy - mostly handled by MessageOrchestrator now)
    private var lastSpeakTime = 0L
    private val minSpeakInterval = 1500L
    
    // Streak tracking for motivation
    private var correctRepStreak = 0
    private var lastAnnouncedRep = 0
    
    // Random message tracking (LOW priority - fills quiet time)
    private var lastHighPriorityMessageTime = 0L
    private var lastRandomMessageTime = 0L
    private var availableMotivationalMessages: List<LocalizedText> = emptyList()
    private var availableTipMessages: List<LocalizedText> = emptyList()
    
    /**
     * Visual message for Glassmorphic UI (Video mode)
     */
    data class VisualMessage(
        val text: String,
        val type: MessageType,
        val durationMs: Long = 3000L
    )
    
    enum class MessageType {
        TIP,        // Blue - informational
        WARNING,    // Amber - caution
        ERROR,      // Red - critical
        MOTIVATION, // Green - positive
        INFO        // Gray - neutral
    }
    
    /**
     * Initialize TTS engine (legacy mode)
     */
    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = if (config.language == "ar") {
                    Locale.forLanguageTag("ar")
                } else {
                    Locale.US
                }
                
                val result = tts?.setLanguage(locale)
                isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && 
                             result != TextToSpeech.LANG_NOT_SUPPORTED
                
                if (isTtsReady) {
                    Log.d(TAG, "TTS initialized with language: $locale")
                    // Set speech rate for clarity
                    tts?.setSpeechRate(0.95f)
                } else {
                    Log.w(TAG, "TTS language not supported: $locale")
                }
            }
        }
    }
    
    /**
     * Initialize with AudioFeedbackPlayer for cached audio support
     * 
     * Call this instead of initialize() to use cached audio files
     * when available, with TTS as fallback.
     * 
     * @param audioCache AudioCacheManager for accessing cached audio files
     */
    fun initializeWithAudioCache(audioCache: AudioCacheManager) {
        audioPlayer = AudioFeedbackPlayer(context, audioCache, config.language)
        audioPlayer?.initialize()
        useAudioPlayer = true
        Log.d(TAG, "Initialized with AudioFeedbackPlayer (cached audio support)")
    }
    
    /**
     * Emit a feedback event - Routes to appropriate channel based on mode
     */
    suspend fun emit(event: FeedbackEvent) {
        _events.emit(event)
        
        // Handle based on event type and mode
        when (event) {
            is FeedbackEvent.JointErrorDetected -> handleJointError(event)
            is FeedbackEvent.RepCompleted -> handleRepCompleted(event)
            is FeedbackEvent.TargetReached -> handleTargetReached(event)
            is FeedbackEvent.MotivationalMessage -> handleMotivation(event)
            is FeedbackEvent.JointStateMessage -> handleJointStateMessage(event)
            
            // Hold events
            is FeedbackEvent.HoldStarted -> handleHoldStarted()
            is FeedbackEvent.HoldGraceStarted -> handleHoldGraceStarted(event)
            is FeedbackEvent.HoldResumed -> handleHoldResumed(event)
            is FeedbackEvent.HoldCompleted -> handleHoldCompleted(event)
            is FeedbackEvent.HoldFailed -> handleHoldFailed(event)
            
            // Position events
            is FeedbackEvent.PositionErrorDetected -> handlePositionError(event)
            is FeedbackEvent.PositionWarningDetected -> handlePositionWarning(event)
            is FeedbackEvent.PositionTipDetected -> handlePositionTip(event)
            is FeedbackEvent.CameraPositionWarning -> handleCameraWarning(event)
            
            // Visibility events (Camera mode audio feedback)
            is FeedbackEvent.VisibilityWarning -> handleVisibilityWarning(event)
            is FeedbackEvent.VisibilityPaused -> handleVisibilityPaused(event)
            is FeedbackEvent.VisibilityResumed -> handleVisibilityResumed(event)
            
            else -> {}
        }
    }
    
    // ==================== Joint Error Handling ====================
    
    private suspend fun handleJointError(event: FeedbackEvent.JointErrorDetected) {
        val messageKey = "joint:${event.error.jointCode}:${event.error.errorType}"
        val localizedText = event.error.message
        val displayText = localizedText.get(config.language)
        
        // Use MessageOrchestrator for smart delivery
        val decision = messageOrchestrator.decide(
            messageKey = messageKey,
            category = MessageOrchestrator.Category.ERROR,
            messageText = displayText
        )
        
        // Deliver based on decision (with LocalizedText for audio support)
        deliverLocalizedMessage(localizedText, displayText, decision, MessageType.ERROR)
        
        // Reset streak on error (only if message was delivered)
        if (decision.channel != MessageOrchestrator.DeliveryChannel.SILENT) {
            correctRepStreak = 0
        }
    }

    // ==================== Joint State Messages ====================

    private suspend fun handleJointStateMessage(event: FeedbackEvent.JointStateMessage) {
        // Ignore transition (shouldn't be emitted) and empty messages
        val localizedText = event.message
        val displayText = localizedText.get(config.language)
        if (displayText.isBlank()) return
        
        val category = when (event.state) {
            JointState.DANGER -> MessageOrchestrator.Category.CRITICAL
            JointState.WARNING -> MessageOrchestrator.Category.WARNING
            JointState.PAD -> MessageOrchestrator.Category.TIP
            JointState.NORMAL -> MessageOrchestrator.Category.INFO
            JointState.PERFECT -> MessageOrchestrator.Category.MOTIVATION
            JointState.TRANSITION -> MessageOrchestrator.Category.INFO
        }
        
        val messageType = when (event.state) {
            JointState.DANGER -> MessageType.ERROR
            JointState.WARNING -> MessageType.WARNING
            JointState.PAD -> MessageType.TIP
            JointState.NORMAL -> MessageType.INFO
            JointState.PERFECT -> MessageType.MOTIVATION
            JointState.TRANSITION -> MessageType.INFO
        }
        
        // Include zone in messageKey to track up/down messages separately
        val decision = messageOrchestrator.decide(
            messageKey = "state:${event.jointCode}:${event.state}:${event.zone}",
            category = category,
            messageText = displayText
        )
        
        // Deliver with LocalizedText for audio support
        deliverLocalizedMessage(localizedText, displayText, decision, messageType)
    }
    
    /**
     * Deliver a message based on orchestrator decision
     * Handles mode-aware delivery (Camera vs Video)
     * 
     * @param message Text-only message (legacy, no audio URL support)
     */
    private suspend fun deliverMessage(
        message: String, 
        decision: MessageOrchestrator.DeliveryDecision,
        messageType: MessageType
    ) {
        // Delegate to LocalizedText version (no audio URLs)
        deliverLocalizedMessage(
            localizedText = LocalizedText(ar = message, en = message),
            displayText = message,
            decision = decision,
            messageType = messageType
        )
    }
    
    /**
     * Deliver a localized message based on orchestrator decision
     * Supports cached audio playback via LocalizedText.audioAr/audioEn
     * 
     * @param localizedText Full LocalizedText with optional audio URLs
     * @param displayText Text to display (already localized)
     * @param decision Orchestrator delivery decision
     * @param messageType Type for visual styling and haptics
     */
    private suspend fun deliverLocalizedMessage(
        localizedText: LocalizedText,
        displayText: String,
        decision: MessageOrchestrator.DeliveryDecision,
        messageType: MessageType
    ) {
        when (decision.channel) {
            MessageOrchestrator.DeliveryChannel.SILENT -> {
                // No message - visual overlay handles it
                Log.d(TAG, "Message silenced: $displayText (repeat #${decision.repeatCount})")
            }
            
            MessageOrchestrator.DeliveryChannel.HAPTIC_ONLY -> {
                // Just haptic reminder
                if (!isVideoMode && isHapticEnabled) {
                    when (messageType) {
                        MessageType.ERROR -> vibrateWarning()  // Reduced from vibrateError
                        MessageType.WARNING -> vibrateLight()
                        else -> {}
                    }
                }
            }
            
            MessageOrchestrator.DeliveryChannel.VISUAL_ONLY -> {
                // Visual message only (no audio)
                if (isVideoMode) {
                    emitVisualMessage(displayText, messageType)
                }
                // In camera mode: just haptic as visual cue
                if (!isVideoMode && isHapticEnabled) {
                    vibrateLight()
                }
            }
            
            MessageOrchestrator.DeliveryChannel.AUDIO_AND_VISUAL -> {
                // Full feedback
                if (isVideoMode) {
                    emitVisualMessage(displayText, messageType)
                } else {
                    // Determine speech priority based on message type
                    val speechPriority = when (messageType) {
                        MessageType.ERROR -> SpeakPriority.HIGH      // Can interrupt
                        MessageType.WARNING -> SpeakPriority.HIGH    // Can interrupt
                        MessageType.MOTIVATION -> SpeakPriority.NORMAL // Waits in queue
                        MessageType.TIP -> SpeakPriority.LOW         // Skip if busy
                        MessageType.INFO -> SpeakPriority.NORMAL     // Waits in queue
                    }
                    // Use speakLocalized to leverage cached audio if available
                    speakLocalized(localizedText, speechPriority)
                    if (isHapticEnabled) {
                        when (messageType) {
                            MessageType.ERROR -> vibrateError()
                            MessageType.WARNING -> vibrateWarning()
                            MessageType.MOTIVATION -> vibrateSuccess()
                            else -> vibrateLight()
                        }
                    }
                }
                
                // Mark high-priority message delivered (resets idle timer for random messages)
                if (messageType == MessageType.ERROR || messageType == MessageType.WARNING) {
                    markHighPriorityMessageDelivered()
                }
            }
        }
    }
    
    /**
     * Light haptic feedback (subtle reminder)
     */
    private fun vibrateLight() {
        if (!isHapticEnabled) return
        
        try {
            vibrator?.let { v ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(30)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }
    
    // ==================== Rep Handling ====================
    
    private suspend fun handleRepCompleted(event: FeedbackEvent.RepCompleted) {
        if (event.isCorrect) {
            correctRepStreak++
            
            // Check for motivation trigger
            if (correctRepStreak == STREAK_THRESHOLD_SMALL ||
                correctRepStreak == STREAK_THRESHOLD_MEDIUM ||
                correctRepStreak == STREAK_THRESHOLD_LARGE) {
                triggerStreakMotivation(correctRepStreak)
            }
        } else {
            correctRepStreak = 0
        }
        
        if (isVideoMode) {
            // Video mode: No audio, just visual pulse (handled by UI)
        } else {
            // Camera mode: Haptic + occasional audio
            if (isHapticEnabled) {
                if (event.isCorrect) vibrateSuccess() else vibrateWarning()
            }
            
            // Announce rep count every N reps (NORMAL priority - shouldn't interrupt errors)
            if (event.repNumber - lastAnnouncedRep >= REP_AUDIO_INTERVAL) {
                speak("${event.repNumber}", SpeakPriority.NORMAL)
                lastAnnouncedRep = event.repNumber
            }
        }
    }
    
    // ==================== Target Reached ====================
    
    private suspend fun handleTargetReached(event: FeedbackEvent.TargetReached) {
        val message = if (config.language == "ar") {
            "أحسنت! اكتملت ${event.totalReps} تكرار"
        } else {
            "Great job! ${event.totalReps} reps completed"
        }
        
        if (isVideoMode) {
            emitVisualMessage(message, MessageType.MOTIVATION, 4000L)
        } else {
            // HIGH priority - target completion is important announcement
            speak(message, SpeakPriority.HIGH)
            if (isHapticEnabled) vibrateComplete()
        }
    }
    
    // ==================== Motivation ====================
    
    private suspend fun handleMotivation(event: FeedbackEvent.MotivationalMessage) {
        val localizedText = event.message
        val displayText = localizedText.get(config.language)
        
        // Use MessageOrchestrator for smart delivery
        val decision = messageOrchestrator.decide(
            messageKey = "motivation:${displayText.hashCode()}",
            category = MessageOrchestrator.Category.MOTIVATION,
            messageText = displayText
        )
        
        // Deliver with LocalizedText for audio support
        deliverLocalizedMessage(localizedText, displayText, decision, MessageType.MOTIVATION)
    }
    
    private suspend fun triggerStreakMotivation(streak: Int) {
        // Build LocalizedText for streak messages (no audio URLs for these)
        val localizedText = when {
            streak >= STREAK_THRESHOLD_LARGE -> 
                LocalizedText(ar = "ممتاز! استمر!", en = "Excellent! Keep going!")
            streak >= STREAK_THRESHOLD_MEDIUM -> 
                LocalizedText(ar = "أداء رائع!", en = "Great form!")
            else -> 
                LocalizedText(ar = "جيد!", en = "Good!")
        }
        val displayText = localizedText.get(config.language)
        
        // Use MessageOrchestrator (will prevent duplicate messages)
        val decision = messageOrchestrator.decide(
            messageKey = "streak:$streak",
            category = MessageOrchestrator.Category.MOTIVATION,
            messageText = displayText
        )
        
        // Deliver with LocalizedText (TTS fallback since no audio URLs)
        deliverLocalizedMessage(localizedText, displayText, decision, MessageType.MOTIVATION)
    }
    
    // ==================== Hold Event Handlers ====================
    
    private fun handleHoldStarted() {
        if (!isVideoMode && isHapticEnabled) {
            vibrateSuccess()
        }
        Log.d(TAG, "Hold started - feedback sent")
    }
    
    private suspend fun handleHoldGraceStarted(event: FeedbackEvent.HoldGraceStarted) {
        val message = if (config.language == "ar") "ابق ثابتاً!" else "Stay in position!"
        
        if (isVideoMode) {
            emitVisualMessage(message, MessageType.WARNING)
        } else {
            // HIGH priority - warning should interrupt other messages
            speak(message, SpeakPriority.HIGH)
            if (isHapticEnabled) vibrateWarning()
        }
    }
    
    private fun handleHoldResumed(event: FeedbackEvent.HoldResumed) {
        if (!isVideoMode) {
            val message = if (config.language == "ar") "أحسنت، استمر" else "Good, keep holding"
            // NORMAL priority - encouragement shouldn't interrupt warnings
            speak(message, SpeakPriority.NORMAL)
            if (isHapticEnabled) vibrateSuccess()
        }
    }
    
    private suspend fun handleHoldCompleted(event: FeedbackEvent.HoldCompleted) {
        val seconds = event.totalMs / 1000
        val message = if (config.language == "ar") {
            "أحسنت! ثبات $seconds ثانية"
        } else {
            "Great job! Held for $seconds seconds"
        }
        
        if (isVideoMode) {
            emitVisualMessage(message, MessageType.MOTIVATION, 4000L)
        } else {
            // HIGH priority - completion is important announcement
            speak(message, SpeakPriority.HIGH)
            if (isHapticEnabled) vibrateComplete()
        }
    }
    
    private fun handleHoldFailed(event: FeedbackEvent.HoldFailed) {
        if (!isVideoMode) {
            val message = if (config.language == "ar") "فقدت الوضعية. حاول مجدداً" else "Position lost. Try again"
            // HIGH priority - failure is important feedback
            speak(message, SpeakPriority.HIGH)
            if (isHapticEnabled) vibrateError()
        }
    }
    
    // ==================== Position Event Handlers ====================
    
    private suspend fun handlePositionError(event: FeedbackEvent.PositionErrorDetected) {
        val localizedText = event.error.message
        val displayText = localizedText.get(config.language)
        
        // Use MessageOrchestrator for smart delivery
        val decision = messageOrchestrator.decide(
            messageKey = "position:${event.error.checkId}",
            category = MessageOrchestrator.Category.ERROR,
            messageText = displayText
        )
        
        // Deliver with LocalizedText for audio support
        deliverLocalizedMessage(localizedText, displayText, decision, MessageType.ERROR)
    }
    
    private suspend fun handlePositionWarning(event: FeedbackEvent.PositionWarningDetected) {
        val localizedText = event.error.message
        val displayText = localizedText.get(config.language)
        
        // Use MessageOrchestrator for smart delivery
        val decision = messageOrchestrator.decide(
            messageKey = "position_warn:${event.error.checkId}",
            category = MessageOrchestrator.Category.WARNING,
            messageText = displayText
        )
        
        // Deliver with LocalizedText for audio support
        deliverLocalizedMessage(localizedText, displayText, decision, MessageType.WARNING)
    }

    private suspend fun handlePositionTip(event: FeedbackEvent.PositionTipDetected) {
        val displayText = event.error.message.get(config.language)
        
        // Use MessageOrchestrator for smart delivery
        // Tips are VISUAL_ONLY by design (low priority)
        val decision = messageOrchestrator.decide(
            messageKey = "position_tip:${event.error.checkId}",
            category = MessageOrchestrator.Category.TIP,
            messageText = displayText
        )
        
        // Only deliver in Video mode (tips are visual-only per plan)
        if (isVideoMode && decision.channel != MessageOrchestrator.DeliveryChannel.SILENT) {
            emitVisualMessage(displayText, MessageType.TIP)
        }
        
        Log.d(TAG, "Position tip: ${event.error.checkId} (channel: ${decision.channel})")
    }
    
    private suspend fun handleCameraWarning(event: FeedbackEvent.CameraPositionWarning) {
        val localizedText = event.warning.message
        val displayText = localizedText.get(config.language)
        
        // Use MessageOrchestrator for smart delivery
        // Use WARNING category so user hears it at least once, then reduces
        // Camera warnings are important but shouldn't spam
        val decision = messageOrchestrator.decide(
            messageKey = "camera:${event.warning.expectedPosition}",
            category = MessageOrchestrator.Category.WARNING,
            messageText = displayText
        )
        
        // Deliver with LocalizedText for audio support
        deliverLocalizedMessage(localizedText, displayText, decision, MessageType.WARNING)
    }
    
    // ==================== Visibility Event Handlers ====================
    
    private suspend fun handleVisibilityWarning(event: FeedbackEvent.VisibilityWarning) {
        val localizedText = event.message
        val displayText = localizedText.get(config.language)
        
        // Use MessageOrchestrator for smart delivery
        // Visibility warnings are important - user needs to adjust position
        val decision = messageOrchestrator.decide(
            messageKey = "visibility:warning",
            category = MessageOrchestrator.Category.WARNING,
            messageText = displayText
        )
        
        // Deliver with LocalizedText for audio support
        deliverLocalizedMessage(localizedText, displayText, decision, MessageType.WARNING)
    }
    
    private suspend fun handleVisibilityPaused(event: FeedbackEvent.VisibilityPaused) {
        val localizedText = event.message
        val displayText = localizedText.get(config.language)
        
        // Visibility paused is CRITICAL - training stopped, user must act
        // Use CRITICAL category to ensure it's always heard
        val decision = messageOrchestrator.decide(
            messageKey = "visibility:paused",
            category = MessageOrchestrator.Category.CRITICAL,
            messageText = displayText
        )
        
        // Deliver with LocalizedText for audio support
        deliverLocalizedMessage(localizedText, displayText, decision, MessageType.ERROR)
        
        // Extra haptic feedback for critical visibility loss
        if (!isVideoMode && isHapticEnabled) {
            vibrateError()
        }
    }
    
    private fun handleVisibilityResumed(event: FeedbackEvent.VisibilityResumed) {
        // Only provide feedback in camera mode
        if (!isVideoMode) {
            // Short positive feedback when visibility restored
            if (isHapticEnabled) {
                vibrateSuccess()
            }
            // Reset visibility message state so next warning is treated as new
            messageOrchestrator.reset("visibility:warning")
            messageOrchestrator.reset("visibility:paused")
        }
    }
    
    // ==================== Visual Message Emission ====================
    
    private suspend fun emitVisualMessage(text: String, type: MessageType, durationMs: Long = 3000L) {
        _visualMessages.emit(VisualMessage(text, type, durationMs))
    }
    
    // ==================== Audio (TTS) ====================
    
    /**
     * Speech priority determines queue behavior:
     * - HIGH: Interrupts any current speech (QUEUE_FLUSH) - for errors/warnings
     * - NORMAL: Waits for current speech to finish (QUEUE_ADD) - for rep counts, motivation
     * - LOW: Only speaks if nothing else is playing, skips otherwise
     */
    enum class SpeakPriority {
        HIGH,    // Interrupts current speech (errors, warnings, visibility)
        NORMAL,  // Waits in queue (rep counts, motivation)
        LOW      // Skip if busy (tips, info)
    }
    
    // Track if TTS is currently speaking (for LOW priority decisions)
    private var isSpeaking = false
    
    /**
     * Speak text using TTS or cached audio (Camera mode only)
     * @param text The text to speak
     * @param priority Determines if this can interrupt other speech
     */
    fun speak(text: String, priority: SpeakPriority = SpeakPriority.HIGH) {
        if (!isTtsEnabled) return
        
        // Use AudioFeedbackPlayer if available
        if (useAudioPlayer && audioPlayer != null) {
            val audioPriority = when (priority) {
                SpeakPriority.HIGH -> AudioFeedbackPlayer.Priority.HIGH
                SpeakPriority.NORMAL -> AudioFeedbackPlayer.Priority.NORMAL
                SpeakPriority.LOW -> AudioFeedbackPlayer.Priority.LOW
            }
            audioPlayer?.play(text, null, audioPriority)
            return
        }
        
        // Fallback to legacy TTS
        if (!isTtsReady) return
        
        val now = System.currentTimeMillis()
        
        // For LOW priority, skip if something is currently speaking
        if (priority == SpeakPriority.LOW && isSpeaking) {
            Log.d(TAG, "Skipping low-priority speech (busy): $text")
            return
        }
        
        // Cooldown check (skip for HIGH priority - important messages should not be throttled)
        if (priority != SpeakPriority.HIGH && now - lastSpeakTime < minSpeakInterval) {
            return
        }
        
        lastSpeakTime = now
        
        val queueMode = when (priority) {
            SpeakPriority.HIGH -> TextToSpeech.QUEUE_FLUSH  // Interrupt
            SpeakPriority.NORMAL, SpeakPriority.LOW -> TextToSpeech.QUEUE_ADD  // Wait
        }
        
        isSpeaking = true
        tts?.speak(text, queueMode, null, "feedback_${now}")
        Log.d(TAG, "Speaking ($priority): $text")
    }
    
    /**
     * Speak localized text using cached audio if available
     * @param localizedText LocalizedText with optional audio URLs
     * @param priority Determines if this can interrupt other speech
     */
    fun speakLocalized(localizedText: LocalizedText, priority: SpeakPriority = SpeakPriority.HIGH) {
        if (!isTtsEnabled) return
        
        // Debug: Log audio URL availability
        val audioUrl = localizedText.getAudioUrl(config.language)
        if (audioUrl != null) {
            Log.d(TAG, "LocalizedText has audio URL ($config.language): $audioUrl")
        } else {
            Log.d(TAG, "LocalizedText has NO audio URL for ${config.language}. Text: ${localizedText.get(config.language).take(30)}...")
        }
        
        // Use AudioFeedbackPlayer if available (supports cached audio)
        if (useAudioPlayer && audioPlayer != null) {
            val audioPriority = when (priority) {
                SpeakPriority.HIGH -> AudioFeedbackPlayer.Priority.HIGH
                SpeakPriority.NORMAL -> AudioFeedbackPlayer.Priority.NORMAL
                SpeakPriority.LOW -> AudioFeedbackPlayer.Priority.LOW
            }
            audioPlayer?.play(localizedText, audioPriority)
            return
        }
        
        // Fallback to text-only TTS
        speak(localizedText.get(config.language), priority)
    }
    
    /**
     * Speak countdown number with emphasis
     * Uses HIGH priority - countdown should interrupt other messages
     */
    fun speakCountdown(number: Int) {
        if (!isTtsEnabled) return
        
        if (useAudioPlayer && audioPlayer != null) {
            audioPlayer?.playCountdown(number)
            return
        }
        
        if (!isTtsReady) return
        isSpeaking = true
        tts?.speak(number.toString(), TextToSpeech.QUEUE_FLUSH, null, "countdown_$number")
    }
    
    /**
     * Speak "Go!" with energy (localized)
     * Uses HIGH priority - start signal is critical
     */
    fun speakGo() {
        if (!isTtsEnabled) return
        
        if (useAudioPlayer && audioPlayer != null) {
            audioPlayer?.playGo()
            return
        }
        
        if (!isTtsReady) return
        val goText = if (config.language == "ar") "ابدأ!" else "Go!"
        isSpeaking = true
        tts?.speak(goText, TextToSpeech.QUEUE_FLUSH, null, "go")
    }
    
    // ==================== Haptic (Vibration) ====================
    
    private fun vibrateError() {
        vibrate(longArrayOf(0, 100, 50, 100), -1)
    }
    
    private fun vibrateWarning() {
        vibrate(longArrayOf(0, 50, 50, 50), -1)
    }
    
    private fun vibrateSuccess() {
        vibrate(longArrayOf(0, 40), -1)
    }
    
    private fun vibrateComplete() {
        vibrate(longArrayOf(0, 100, 100, 100, 100, 200), -1)
    }
    
    private fun vibrate(pattern: LongArray, repeat: Int) {
        if (!isHapticEnabled) return
        
        try {
            vibrator?.let { v ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, repeat))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(pattern, repeat)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }
    
    // ==================== Random Messages (LOW Priority) ====================
    
    /**
     * Set available random messages from exercise configuration
     * 
     * Call this when training starts with the exercise's feedbackMessages.
     * These messages will be delivered randomly during "quiet time" when
     * no errors/warnings are active.
     * 
     * @param feedbackMessages The exercise's FeedbackMessages (motivational + tips)
     */
    fun setRandomMessages(feedbackMessages: FeedbackMessages) {
        availableMotivationalMessages = feedbackMessages.motivational
        availableTipMessages = feedbackMessages.tips
        Log.d(TAG, "Random messages set: ${availableMotivationalMessages.size} motivational, ${availableTipMessages.size} tips")
    }
    
    /**
     * Check if it's time for a random message and deliver one if appropriate
     * 
     * Call this periodically (e.g., every frame or every second) to check
     * if conditions are right for a random message:
     * 1. Enough idle time has passed (no high-priority messages)
     * 2. Cooldown from last random message has passed
     * 3. No current error/warning state
     * 
     * @param hasActiveErrors Whether there are currently active errors/warnings
     * @return true if a random message was delivered
     */
    suspend fun checkAndDeliverRandomMessage(hasActiveErrors: Boolean): Boolean {
        // Don't deliver if there are active errors
        if (hasActiveErrors) {
            lastHighPriorityMessageTime = System.currentTimeMillis()
            return false
        }
        
        val now = System.currentTimeMillis()
        
        // Check if enough idle time has passed (configurable via settings)
        val minIdleTime = SettingsManager.getRandomMessageIdleTime()
        val idleTime = now - lastHighPriorityMessageTime
        if (idleTime < minIdleTime) {
            return false
        }
        
        // Check cooldown from last random message (configurable via settings)
        val randomCooldown = SettingsManager.getRandomMessageCooldown()
        val timeSinceLastRandom = now - lastRandomMessageTime
        if (timeSinceLastRandom < randomCooldown) {
            return false
        }
        
        // Check if we have any messages
        if (availableMotivationalMessages.isEmpty() && availableTipMessages.isEmpty()) {
            return false
        }
        
        // Decide which type of message to deliver (70% motivational, 30% tips)
        val useMotivational = availableMotivationalMessages.isNotEmpty() && 
            (availableTipMessages.isEmpty() || Math.random() < 0.7)
        
        val message = if (useMotivational) {
            availableMotivationalMessages.randomOrNull()
        } else {
            availableTipMessages.randomOrNull()
        }
        
        if (message == null) return false
        
        val messageText = message.get(config.language)
        val messageType = if (useMotivational) MessageType.MOTIVATION else MessageType.TIP
        
        // Use MessageOrchestrator with LOW priority
        val decision = messageOrchestrator.decide(
            messageKey = "random:${messageText.hashCode()}",
            category = if (useMotivational) MessageOrchestrator.Category.MOTIVATION else MessageOrchestrator.Category.TIP,
            messageText = messageText
        )
        
        // Only deliver if not silenced
        if (decision.channel == MessageOrchestrator.DeliveryChannel.SILENT) {
            return false
        }
        
        // Deliver with LOW priority (won't interrupt other messages)
        if (isVideoMode) {
            emitVisualMessage(messageText, messageType)
        } else {
            speak(messageText, SpeakPriority.LOW)
            if (isHapticEnabled) {
                vibrateLight()
            }
        }
        
        lastRandomMessageTime = now
        Log.d(TAG, "Random message delivered: $messageText")
        return true
    }
    
    /**
     * Mark that a high-priority message was just delivered
     * 
     * Call this when delivering error/warning messages to reset the idle timer.
     */
    fun markHighPriorityMessageDelivered() {
        lastHighPriorityMessageTime = System.currentTimeMillis()
    }
    
    // ==================== Utilities ====================
    
    /**
     * Reset all message states (call when training starts/restarts)
     */
    fun resetMessageStates() {
        messageOrchestrator.resetAll()
        lastAnnouncedRep = 0
        correctRepStreak = 0
        lastHighPriorityMessageTime = System.currentTimeMillis()
        lastRandomMessageTime = 0L
        Log.d(TAG, "Message states reset")
    }
    
    @Deprecated("Use resetMessageStates() instead", ReplaceWith("resetMessageStates()"))
    fun clearCooldowns() {
        resetMessageStates()
    }
    
    fun setLanguage(language: String) {
        val locale = if (language == "ar") Locale.forLanguageTag("ar") else Locale.US
        tts?.setLanguage(locale)
        audioPlayer?.setLanguage(language)
    }
    
    fun release() {
        // Release audio player
        audioPlayer?.release()
        audioPlayer = null
        useAudioPlayer = false
        
        // Release TTS
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
    }
}
