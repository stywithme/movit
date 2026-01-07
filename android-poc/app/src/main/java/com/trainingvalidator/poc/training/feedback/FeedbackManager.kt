package com.trainingvalidator.poc.training.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import com.trainingvalidator.poc.training.engine.PositionError
import com.trainingvalidator.poc.training.models.CheckSeverity
import com.trainingvalidator.poc.training.models.JointError
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
    }
    
    // Smart message orchestrator (prevents spam, manages delivery)
    private val messageOrchestrator = MessageOrchestrator()
    
    // ===== MODE-AWARE SETTINGS =====
    var isVideoMode: Boolean = false
        set(value) {
            field = value
            Log.d(TAG, "Mode changed: ${if (value) "VIDEO" else "CAMERA"}")
        }
    
    // TTS enabled only in Camera mode
    private val isTtsEnabled: Boolean
        get() = !isVideoMode && config.enableAudio
    
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
    
    // Text-to-speech
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    
    // Vibrator
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    
    // Cooldown tracking (legacy - mostly handled by MessageOrchestrator now)
    private var lastSpeakTime = 0L
    private val minSpeakInterval = 1500L
    
    // Streak tracking for motivation
    private var correctRepStreak = 0
    private var lastAnnouncedRep = 0
    
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
     * Initialize TTS engine
     */
    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = if (config.language == "ar") {
                    Locale("ar")
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
            
            else -> {}
        }
    }
    
    // ==================== Joint Error Handling ====================
    
    private suspend fun handleJointError(event: FeedbackEvent.JointErrorDetected) {
        val messageKey = "joint:${event.error.jointCode}:${event.error.errorType}"
        val message = event.error.message.get(config.language)
        
        // Use MessageOrchestrator for smart delivery
        val decision = messageOrchestrator.decide(
            messageKey = messageKey,
            category = MessageOrchestrator.Category.ERROR,
            messageText = message
        )
        
        // Deliver based on decision
        deliverMessage(message, decision, MessageType.ERROR)
        
        // Reset streak on error (only if message was delivered)
        if (decision.channel != MessageOrchestrator.DeliveryChannel.SILENT) {
            correctRepStreak = 0
        }
    }
    
    /**
     * Deliver a message based on orchestrator decision
     * Handles mode-aware delivery (Camera vs Video)
     */
    private suspend fun deliverMessage(
        message: String, 
        decision: MessageOrchestrator.DeliveryDecision,
        messageType: MessageType
    ) {
        when (decision.channel) {
            MessageOrchestrator.DeliveryChannel.SILENT -> {
                // No message - visual overlay handles it
                Log.d(TAG, "Message silenced: $message (repeat #${decision.repeatCount})")
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
                    emitVisualMessage(message, messageType)
                }
                // In camera mode: just haptic as visual cue
                if (!isVideoMode && isHapticEnabled) {
                    vibrateLight()
                }
            }
            
            MessageOrchestrator.DeliveryChannel.AUDIO_AND_VISUAL -> {
                // Full feedback
                if (isVideoMode) {
                    emitVisualMessage(message, messageType)
                } else {
                    speak(message)
                    if (isHapticEnabled) {
                        when (messageType) {
                            MessageType.ERROR -> vibrateError()
                            MessageType.WARNING -> vibrateWarning()
                            MessageType.MOTIVATION -> vibrateSuccess()
                            else -> vibrateLight()
                        }
                    }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(30)
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
            
            // Announce rep count every N reps
            if (event.repNumber - lastAnnouncedRep >= REP_AUDIO_INTERVAL) {
                speak("${event.repNumber}")
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
            speak(message)
            if (isHapticEnabled) vibrateComplete()
        }
    }
    
    // ==================== Motivation ====================
    
    private suspend fun handleMotivation(event: FeedbackEvent.MotivationalMessage) {
        val message = event.message.get(config.language)
        
        // Use MessageOrchestrator for smart delivery
        val decision = messageOrchestrator.decide(
            messageKey = "motivation:${message.hashCode()}",
            category = MessageOrchestrator.Category.MOTIVATION,
            messageText = message
        )
        
        deliverMessage(message, decision, MessageType.MOTIVATION)
    }
    
    private suspend fun triggerStreakMotivation(streak: Int) {
        val message = when {
            streak >= STREAK_THRESHOLD_LARGE -> 
                if (config.language == "ar") "ممتاز! استمر!" else "Excellent! Keep going!"
            streak >= STREAK_THRESHOLD_MEDIUM -> 
                if (config.language == "ar") "أداء رائع!" else "Great form!"
            else -> 
                if (config.language == "ar") "جيد!" else "Good!"
        }
        
        // Use MessageOrchestrator (will prevent duplicate messages)
        val decision = messageOrchestrator.decide(
            messageKey = "streak:$streak",
            category = MessageOrchestrator.Category.MOTIVATION,
            messageText = message
        )
        
        deliverMessage(message, decision, MessageType.MOTIVATION)
    }
    
    // ==================== Hold Event Handlers ====================
    
    private fun handleHoldStarted() {
        if (!isVideoMode && isHapticEnabled) {
            vibrateSuccess()
        }
        Log.d(TAG, "Hold started - feedback sent")
    }
    
    private suspend fun handleHoldGraceStarted(event: FeedbackEvent.HoldGraceStarted) {
        if (isVideoMode) {
            emitVisualMessage("Stay in position!", MessageType.WARNING)
        } else {
            speak("Stay in position")
            if (isHapticEnabled) vibrateWarning()
        }
    }
    
    private fun handleHoldResumed(event: FeedbackEvent.HoldResumed) {
        if (!isVideoMode) {
            speak("Good, keep holding")
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
            speak(message)
            if (isHapticEnabled) vibrateComplete()
        }
    }
    
    private fun handleHoldFailed(event: FeedbackEvent.HoldFailed) {
        if (!isVideoMode) {
            speak("Position lost. Try again")
            if (isHapticEnabled) vibrateError()
        }
    }
    
    // ==================== Position Event Handlers ====================
    
    private suspend fun handlePositionError(event: FeedbackEvent.PositionErrorDetected) {
        val message = event.error.message.get(config.language)
        
        // Use MessageOrchestrator for smart delivery
        val decision = messageOrchestrator.decide(
            messageKey = "position:${event.error.checkId}",
            category = MessageOrchestrator.Category.ERROR,
            messageText = message
        )
        
        deliverMessage(message, decision, MessageType.ERROR)
    }
    
    private suspend fun handlePositionWarning(event: FeedbackEvent.PositionWarningDetected) {
        val message = event.error.message.get(config.language)
        
        // Use MessageOrchestrator for smart delivery
        val decision = messageOrchestrator.decide(
            messageKey = "position_warn:${event.error.checkId}",
            category = MessageOrchestrator.Category.WARNING,
            messageText = message
        )
        
        deliverMessage(message, decision, MessageType.WARNING)
    }

    private suspend fun handlePositionTip(event: FeedbackEvent.PositionTipDetected) {
        val message = event.error.message.get(config.language)
        
        // Use MessageOrchestrator for smart delivery
        // Tips are VISUAL_ONLY by design (low priority)
        val decision = messageOrchestrator.decide(
            messageKey = "position_tip:${event.error.checkId}",
            category = MessageOrchestrator.Category.TIP,
            messageText = message
        )
        
        // Only deliver in Video mode (tips are visual-only per plan)
        if (isVideoMode && decision.channel != MessageOrchestrator.DeliveryChannel.SILENT) {
            emitVisualMessage(message, MessageType.TIP)
        }
        
        Log.d(TAG, "Position tip: ${event.error.checkId} (channel: ${decision.channel})")
    }
    
    private suspend fun handleCameraWarning(event: FeedbackEvent.CameraPositionWarning) {
        val message = event.warning.message.get(config.language)
        
        // Use MessageOrchestrator for smart delivery
        // Use WARNING category so user hears it at least once, then reduces
        // Camera warnings are important but shouldn't spam
        val decision = messageOrchestrator.decide(
            messageKey = "camera:${event.warning.expectedPosition}",
            category = MessageOrchestrator.Category.WARNING,  // Changed from INFO for better handling
            messageText = message
        )
        
        deliverMessage(message, decision, MessageType.WARNING)
    }
    
    // ==================== Visual Message Emission ====================
    
    private suspend fun emitVisualMessage(text: String, type: MessageType, durationMs: Long = 3000L) {
        _visualMessages.emit(VisualMessage(text, type, durationMs))
    }
    
    // ==================== Audio (TTS) ====================
    
    /**
     * Speak text using TTS (Camera mode only)
     */
    fun speak(text: String) {
        if (!isTtsEnabled || !isTtsReady) return
        
        val now = System.currentTimeMillis()
        if (now - lastSpeakTime < minSpeakInterval) {
            return
        }
        
        lastSpeakTime = now
        
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "feedback_${now}")
        Log.d(TAG, "Speaking: $text")
    }
    
    /**
     * Speak countdown number with emphasis
     */
    fun speakCountdown(number: Int) {
        if (!isTtsEnabled || !isTtsReady) return
        tts?.speak(number.toString(), TextToSpeech.QUEUE_FLUSH, null, "countdown_$number")
    }
    
    /**
     * Speak "Go!" with energy
     */
    fun speakGo() {
        if (!isTtsEnabled || !isTtsReady) return
        tts?.speak("Go!", TextToSpeech.QUEUE_FLUSH, null, "go")
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
        
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, repeat))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, repeat)
            }
        }
    }
    
    // ==================== Utilities ====================
    
    /**
     * Reset all message states (call when training starts/restarts)
     */
    fun resetMessageStates() {
        messageOrchestrator.resetAll()
        lastAnnouncedRep = 0
        correctRepStreak = 0
        Log.d(TAG, "Message states reset")
    }
    
    @Deprecated("Use resetMessageStates() instead", ReplaceWith("resetMessageStates()"))
    fun clearCooldowns() {
        resetMessageStates()
    }
    
    fun setLanguage(language: String) {
        val locale = if (language == "ar") Locale("ar") else Locale.US
        tts?.setLanguage(locale)
    }
    
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
    }
}
