package com.trainingvalidator.poc.training.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import com.trainingvalidator.poc.training.models.JointError
import com.trainingvalidator.poc.training.models.LocalizedText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.*

/**
 * FeedbackManager - Manages feedback delivery (visual, audio, haptic)
 * 
 * This component handles:
 * - Text-to-speech for audio feedback
 * - Vibration for haptic feedback
 * - Throttling to prevent feedback spam
 * - Priority-based feedback queuing
 */
class FeedbackManager(
    private val context: Context,
    private val config: FeedbackConfig = FeedbackConfig()
) {
    
    // ===== DISABLED: TTS and text messages =====
    // Visual feedback (arrows) will be used instead
    private val enableTTS = false
    private val enableTextMessages = false
    
    companion object {
        private const val TAG = "FeedbackManager"
    }
    
    // Event flow for UI to observe
    private val _events = MutableSharedFlow<FeedbackEvent>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val events: SharedFlow<FeedbackEvent> = _events
    
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
    
    // Cooldown tracking for errors
    private val lastErrorTimes = mutableMapOf<String, Long>()
    
    // Last spoken message time
    private var lastSpeakTime = 0L
    private val minSpeakInterval = 1500L
    
    /**
     * Initialize TTS engine - DISABLED (using visual arrows instead)
     */
    fun initialize() {
        // Skip TTS initialization if disabled
        if (!enableTTS) {
            Log.d(TAG, "TTS disabled - using visual feedback instead")
            return
        }
        
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
                } else {
                    Log.w(TAG, "TTS language not supported: $locale")
                }
            }
        }
    }
    
    /**
     * Emit a feedback event
     */
    suspend fun emit(event: FeedbackEvent) {
        _events.emit(event)
        
        // Handle based on event type
        when (event) {
            is FeedbackEvent.JointErrorDetected -> handleJointError(event)
            is FeedbackEvent.RepCompleted -> handleRepCompleted(event)
            is FeedbackEvent.TargetReached -> handleTargetReached(event)
            is FeedbackEvent.MotivationalMessage -> speak(event.message.get(config.language))
            
            // Hold events
            is FeedbackEvent.HoldStarted -> handleHoldStarted()
            is FeedbackEvent.HoldGraceStarted -> handleHoldGraceStarted(event)
            is FeedbackEvent.HoldResumed -> handleHoldResumed(event)
            is FeedbackEvent.HoldCompleted -> handleHoldCompleted(event)
            is FeedbackEvent.HoldFailed -> handleHoldFailed(event)
            
            else -> {}
        }
    }
    
    /**
     * Handle joint error with cooldown
     */
    private fun handleJointError(event: FeedbackEvent.JointErrorDetected) {
        val errorKey = "${event.error.jointCode}:${event.error.errorType}"
        val now = System.currentTimeMillis()
        val lastTime = lastErrorTimes[errorKey] ?: 0
        
        // Check cooldown
        if (now - lastTime < config.errorCooldownMs) {
            return
        }
        
        lastErrorTimes[errorKey] = now
        
        // Audio feedback
        if (config.enableAudio) {
            speak(event.error.message.get(config.language))
        }
        
        // Haptic feedback
        if (config.enableHaptic) {
            vibrateError()
        }
    }
    
    /**
     * Handle rep completed
     */
    private fun handleRepCompleted(event: FeedbackEvent.RepCompleted) {
        if (config.enableHaptic) {
            if (event.isCorrect) {
                vibrateSuccess()
            } else {
                vibrateWarning()
            }
        }
        
        // Speak rep count every few reps
        if (event.repNumber % 3 == 0 && config.enableAudio) {
            speak("${event.repNumber}")
        }
    }
    
    /**
     * Handle target reached
     */
    private fun handleTargetReached(event: FeedbackEvent.TargetReached) {
        if (config.enableAudio) {
            val message = if (config.language == "ar") {
                "أحسنت! اكتملت ${event.totalReps} تكرار"
            } else {
                "Great job! ${event.totalReps} reps completed"
            }
            speak(message)
        }
        
        if (config.enableHaptic) {
            vibrateComplete()
        }
    }
    
    // ==================== Hold Event Handlers ====================
    
    /**
     * Handle hold started
     */
    private fun handleHoldStarted() {
        if (config.enableHaptic) {
            vibrateSuccess()
        }
        Log.d(TAG, "Hold started - haptic feedback sent")
    }
    
    /**
     * Handle hold grace period started
     */
    private fun handleHoldGraceStarted(event: FeedbackEvent.HoldGraceStarted) {
        if (config.enableHaptic) {
            vibrateWarning()
        }
        Log.d(TAG, "Hold grace started - warning haptic sent")
    }
    
    /**
     * Handle hold resumed from grace
     */
    private fun handleHoldResumed(event: FeedbackEvent.HoldResumed) {
        if (config.enableHaptic) {
            vibrateSuccess()
        }
        Log.d(TAG, "Hold resumed - success haptic sent")
    }
    
    /**
     * Handle hold completed
     */
    private fun handleHoldCompleted(event: FeedbackEvent.HoldCompleted) {
        if (config.enableAudio) {
            val seconds = event.totalMs / 1000
            val message = if (config.language == "ar") {
                "أحسنت! ثبات $seconds ثانية"
            } else {
                "Great job! Held for $seconds seconds"
            }
            speak(message)
        }
        
        if (config.enableHaptic) {
            vibrateComplete()
        }
        Log.d(TAG, "Hold completed - completion feedback sent")
    }
    
    /**
     * Handle hold failed
     */
    private fun handleHoldFailed(event: FeedbackEvent.HoldFailed) {
        if (config.enableHaptic) {
            vibrateError()
        }
        Log.d(TAG, "Hold failed - error haptic sent")
    }
    
    /**
     * Speak text - DISABLED (using visual arrows instead)
     */
    fun speak(text: String) {
        // TTS is disabled - using visual feedback instead
        if (!enableTTS) return
        if (!config.enableAudio || !isTtsReady) return
        
        val now = System.currentTimeMillis()
        if (now - lastSpeakTime < minSpeakInterval) {
            return
        }
        
        lastSpeakTime = now
        
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "feedback_${now}")
    }
    
    /**
     * Vibrate for error
     */
    private fun vibrateError() {
        vibrate(longArrayOf(0, 100, 50, 100), -1)
    }
    
    /**
     * Vibrate for warning
     */
    private fun vibrateWarning() {
        vibrate(longArrayOf(0, 50, 50, 50), -1)
    }
    
    /**
     * Vibrate for success
     */
    private fun vibrateSuccess() {
        vibrate(longArrayOf(0, 50), -1)
    }
    
    /**
     * Vibrate for completion
     */
    private fun vibrateComplete() {
        vibrate(longArrayOf(0, 100, 100, 100, 100, 200), -1)
    }
    
    /**
     * Vibrate with pattern
     */
    private fun vibrate(pattern: LongArray, repeat: Int) {
        if (!config.enableHaptic) return
        
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, repeat))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, repeat)
            }
        }
    }
    
    /**
     * Clear error cooldowns
     */
    fun clearCooldowns() {
        lastErrorTimes.clear()
    }
    
    /**
     * Update language
     */
    fun setLanguage(language: String) {
        val locale = if (language == "ar") Locale("ar") else Locale.US
        tts?.setLanguage(locale)
    }
    
    /**
     * Release resources
     */
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
    }
}
