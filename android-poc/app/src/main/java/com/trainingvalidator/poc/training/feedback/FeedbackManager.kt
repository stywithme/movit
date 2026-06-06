package com.trainingvalidator.poc.training.feedback

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.ContextCompat
import com.trainingvalidator.poc.storage.AudioCacheManager
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.models.CheckSeverity
import com.trainingvalidator.poc.training.models.FeedbackMessages
import com.trainingvalidator.poc.training.models.JointError
import com.trainingvalidator.poc.training.engine.RepIncompleteReason
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.LocalizedText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.*

/**
 * FeedbackManager - Professional mode-aware feedback delivery system
 * 
 * MODE-AWARE BEHAVIOR:
 * - Camera Mode: Voice-only feedback while the trainee is away from the phone
 * - Video Mode:  Text-only feedback while the trainee reviews performance
 * 
 * SMART MESSAGING (via FeedbackScheduler):
 * - First is loud, repeat is quiet
 * - Progressive silence after max repeats
 * - Category-aware delivery channels
 * - State-aware reset when issue resolves
 * 
 * Features:
 * - Text-to-speech for audio feedback (Camera mode)
 * - Smart throttling via FeedbackScheduler
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
        /** When TTS is off (e.g. video mode), approximate pacing for countdown sequencing */
        private const val NO_AUDIO_POSE_MS = 1200L
        private const val NO_AUDIO_COUNTDOWN_MS = 850L
    }
    
    // Single feedback scheduler (prevents spam, priority conflicts, and output overlap)
    private val feedbackScheduler = FeedbackScheduler(
        coachIntensity = CoachIntensity.from(SettingsManager.getCoachIntensity()),
        cameraCueMode = CameraCueMode.from(SettingsManager.getCameraCueMode())
    )
    
    // ===== MODE-AWARE SETTINGS =====
    var isVideoMode: Boolean = false
        set(value) {
            field = value
            Log.d(TAG, "Mode changed: ${if (value) "VIDEO" else "CAMERA"}")
        }
    
    // TTS enabled only in Camera mode and if voice is enabled
    private val isTtsEnabled: Boolean
        get() = !isVideoMode && isVoiceEnabled()
    
    // Haptic helpers remain for legacy call sites; feedback delivery is voice/text-only.
    private val isHapticEnabled: Boolean
        get() = config.enableHaptic
    
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

    /** Active feedback locale (e.g. "en", "ar") for diagnostics and UI alignment with TTS/audio. */
    val feedbackLanguage: String
        get() = config.language
    
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

    private var toneGenerator: ToneGenerator? = null
    
    // Cooldown tracking (legacy fallback; scheduler owns feedback timing)
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
        initTtsWithEngine(TtsVoiceSelector.getPreferredEngine())
    }

    private fun initTtsWithEngine(engine: String?) {
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let {
                    TtsVoiceSelector.applyBestVoice(it, config.language)
                    it.setSpeechRate(0.95f)
                    it.setPitch(0.95f)
                    isTtsReady = true
                    Log.d(TAG, "TTS ready (engine=${engine ?: "default"})")
                }
            } else if (engine != null) {
                Log.w(TAG, "Preferred engine '$engine' failed, trying default")
                initTtsWithEngine(null)
            } else {
                Log.e(TAG, "TTS init failed completely")
            }
        }

        tts = if (engine != null) {
            TextToSpeech(context, listener, engine)
        } else {
            TextToSpeech(context, listener)
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
            is FeedbackEvent.JointQuality -> when (val c = event.content) {
                is JointQualityContent.Error -> handleJointQualityError(c.error)
                is JointQualityContent.StateMessage -> handleJointStateMessage(c)
            }
            is FeedbackEvent.RepCompleted -> handleRepCompleted(event)
            is FeedbackEvent.RepIncomplete -> handleRepIncomplete(event)
            is FeedbackEvent.TargetReached -> handleTargetReached(event)
            
            // Hold events
            is FeedbackEvent.HoldStarted -> handleHoldStarted()
            is FeedbackEvent.HoldGraceStarted -> handleHoldGraceStarted(event)
            is FeedbackEvent.HoldResumed -> handleHoldResumed(event)
            is FeedbackEvent.HoldCompleted -> handleHoldCompleted(event)
            is FeedbackEvent.HoldFailed -> handleHoldFailed(event)
            
            // Position / alignment
            is FeedbackEvent.PositionCheckFeedback -> handlePositionCheckFeedback(event)
            is FeedbackEvent.SceneWarnings -> handleSceneWarnings(event)
            
            // Visibility events (Camera mode audio feedback)
            is FeedbackEvent.VisibilityWarning -> handleVisibilityWarning(event)
            is FeedbackEvent.VisibilityPaused -> handleVisibilityPaused(event)
            is FeedbackEvent.VisibilityResumed -> handleVisibilityResumed(event)
        }
    }
    
    // ==================== Joint Error Handling ====================
    
    private suspend fun handleJointQualityError(error: JointError) {
        val messageKey = "joint:${error.jointCode}:${error.errorType}"
        val localizedText = error.message
        val displayText = localizedText.get(config.language)

        val severity = when (error.state) {
            JointState.DANGER -> FeedbackSeverity.CRITICAL
            JointState.WARNING -> FeedbackSeverity.WARNING
            else -> FeedbackSeverity.ERROR
        }
        val messageType = when (severity) {
            FeedbackSeverity.WARNING -> MessageType.WARNING
            else -> MessageType.ERROR
        }

        val delivered = scheduleAndDeliver(
            kind = FeedbackKind.JOINT_QUALITY,
            severity = severity,
            localizedText = localizedText,
            displayText = displayText,
            dedupeKey = messageKey,
            activeKey = "correction",
            cooldownGroup = messageKey,
            messageType = messageType
        )

        if (delivered) {
            correctRepStreak = 0
        }
    }

    // ==================== Joint State Messages ====================

    private suspend fun handleJointStateMessage(event: JointQualityContent.StateMessage) {
        // Ignore transition (shouldn't be emitted) and empty messages
        val localizedText = event.message
        val displayText = localizedText.get(config.language)
        if (displayText.isBlank()) return
        
        val severity = when (event.state) {
            JointState.DANGER -> FeedbackSeverity.CRITICAL
            JointState.WARNING -> FeedbackSeverity.WARNING
            JointState.PAD -> FeedbackSeverity.TIP
            JointState.NORMAL -> FeedbackSeverity.INFO
            JointState.PERFECT -> FeedbackSeverity.MOTIVATION
            JointState.TRANSITION -> FeedbackSeverity.INFO
        }
        
        val messageType = when (event.state) {
            JointState.DANGER -> MessageType.ERROR
            JointState.WARNING -> MessageType.WARNING
            JointState.PAD -> MessageType.TIP
            JointState.NORMAL -> MessageType.INFO
            JointState.PERFECT -> MessageType.MOTIVATION
            JointState.TRANSITION -> MessageType.INFO
        }
        
        val messageKey = "state:${event.jointCode}:${event.state}:${event.zone}"
        scheduleAndDeliver(
            kind = FeedbackKind.JOINT_QUALITY,
            severity = severity,
            localizedText = localizedText,
            displayText = displayText,
            dedupeKey = messageKey,
            activeKey = if (severity.priority >= FeedbackSeverity.WARNING.priority) "correction" else messageKey,
            cooldownGroup = messageKey,
            messageType = messageType
        )
    }

    private suspend fun scheduleAndDeliver(
        kind: FeedbackKind,
        severity: FeedbackSeverity,
        localizedText: LocalizedText,
        displayText: String,
        dedupeKey: String,
        activeKey: String = dedupeKey,
        cooldownGroup: String = dedupeKey,
        messageType: MessageType,
        forceAudible: Boolean = false,
        allowVoice: Boolean = true,
        allowTone: Boolean = true,
        allowVisual: Boolean = true,
        allowHaptic: Boolean = true,
        interruptPolicy: FeedbackInterruptPolicy = FeedbackInterruptPolicy.defaultFor(severity)
    ): Boolean {
        refreshSchedulerSettings()
        val plan = feedbackScheduler.schedule(
            FeedbackSignal(
                kind = kind,
                severity = severity,
                text = displayText,
                dedupeKey = dedupeKey,
                activeKey = activeKey,
                cooldownGroup = cooldownGroup,
                interruptPolicy = interruptPolicy,
                forceAudible = forceAudible,
                allowVoice = allowVoice && isTtsEnabled,
                allowTone = allowTone,
                allowVisual = allowVisual,
                allowHaptic = allowHaptic
            ),
            currentFeedbackMode()
        )
        if (!plan.shouldDeliver) return false

        if (plan.showVisual) {
            emitVisualMessage(displayText, messageType, plan.displayDurationMs)
        }

        when (plan.audible) {
            FeedbackAudible.VOICE -> speakLocalized(localizedText, plan.speechPriority.toSpeakPriority())
            FeedbackAudible.TONE -> playFeedbackTone(plan.tone)
            FeedbackAudible.NONE -> {}
        }

        if (plan.vibrate && isHapticEnabled) {
            vibrateForMessageType(messageType, severity)
        }

        if (severity == FeedbackSeverity.CRITICAL ||
            severity == FeedbackSeverity.ERROR ||
            severity == FeedbackSeverity.WARNING
        ) {
            markHighPriorityMessageDelivered()
        }

        return true
    }

    private fun scheduleAndDeliverImmediate(
        kind: FeedbackKind,
        severity: FeedbackSeverity,
        localizedText: LocalizedText,
        displayText: String,
        dedupeKey: String,
        activeKey: String = dedupeKey,
        cooldownGroup: String = dedupeKey,
        messageType: MessageType,
        forceAudible: Boolean = false,
        allowVoice: Boolean = true,
        allowTone: Boolean = true,
        allowVisual: Boolean = true,
        allowHaptic: Boolean = true,
        interruptPolicy: FeedbackInterruptPolicy = FeedbackInterruptPolicy.defaultFor(severity)
    ): Boolean {
        refreshSchedulerSettings()
        val plan = feedbackScheduler.schedule(
            FeedbackSignal(
                kind = kind,
                severity = severity,
                text = displayText,
                dedupeKey = dedupeKey,
                activeKey = activeKey,
                cooldownGroup = cooldownGroup,
                interruptPolicy = interruptPolicy,
                forceAudible = forceAudible,
                allowVoice = allowVoice && isTtsEnabled,
                allowTone = allowTone,
                allowVisual = allowVisual,
                allowHaptic = allowHaptic
            ),
            currentFeedbackMode()
        )
        if (!plan.shouldDeliver) return false

        if (plan.showVisual) {
            _visualMessages.tryEmit(VisualMessage(displayText, messageType, plan.displayDurationMs))
        }

        when (plan.audible) {
            FeedbackAudible.VOICE -> speakLocalized(localizedText, plan.speechPriority.toSpeakPriority())
            FeedbackAudible.TONE -> playFeedbackTone(plan.tone)
            FeedbackAudible.NONE -> {}
        }
        if (plan.vibrate && isHapticEnabled) {
            vibrateForMessageType(messageType, severity)
        }
        if (severity == FeedbackSeverity.CRITICAL ||
            severity == FeedbackSeverity.ERROR ||
            severity == FeedbackSeverity.WARNING
        ) {
            markHighPriorityMessageDelivered()
        }
        return true
    }

    private fun currentFeedbackMode(): FeedbackRuntimeMode =
        if (isVideoMode) FeedbackRuntimeMode.VIDEO else FeedbackRuntimeMode.CAMERA

    private fun refreshSchedulerSettings() {
        feedbackScheduler.updateSettings(
            coachIntensity = CoachIntensity.from(SettingsManager.getCoachIntensity()),
            cameraCueMode = CameraCueMode.from(SettingsManager.getCameraCueMode())
        )
    }

    private fun FeedbackSpeechPriority.toSpeakPriority(): SpeakPriority = when (this) {
        FeedbackSpeechPriority.INTERRUPT -> SpeakPriority.HIGH
        FeedbackSpeechPriority.NORMAL -> SpeakPriority.NORMAL
        FeedbackSpeechPriority.LOW -> SpeakPriority.LOW
    }

    private fun vibrateForMessageType(messageType: MessageType, severity: FeedbackSeverity) {
        when {
            severity == FeedbackSeverity.CRITICAL -> vibrateError()
            messageType == MessageType.ERROR -> vibrateWarning()
            messageType == MessageType.WARNING -> vibrateLight()
            messageType == MessageType.MOTIVATION -> vibrateSuccess()
            severity == FeedbackSeverity.SUCCESS -> vibrateSuccess()
            else -> {}
        }
    }

    private fun playFeedbackTone(tone: FeedbackTone) {
        if (isVideoMode || tone == FeedbackTone.NONE) return
        try {
            val generator = toneGenerator ?: ToneGenerator(AudioManager.STREAM_MUSIC, 70).also {
                toneGenerator = it
            }
            val (toneType, durationMs) = when (tone) {
                FeedbackTone.SUCCESS -> ToneGenerator.TONE_PROP_ACK to 120
                FeedbackTone.WARNING -> ToneGenerator.TONE_PROP_BEEP to 120
                FeedbackTone.ERROR -> ToneGenerator.TONE_PROP_NACK to 170
                FeedbackTone.CRITICAL -> ToneGenerator.TONE_PROP_NACK to 260
                FeedbackTone.INFO -> ToneGenerator.TONE_PROP_BEEP to 90
                FeedbackTone.NONE -> ToneGenerator.TONE_PROP_BEEP to 0
            }
            if (durationMs > 0) {
                generator.startTone(toneType, durationMs)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tone playback failed", e)
        }
    }
    
    /** Legacy vibration helper; scheduler delivery is currently voice/text-only. */
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

    private suspend fun handleRepIncomplete(event: FeedbackEvent.RepIncomplete) {
        correctRepStreak = 0

        val localizedText = messageForRepIncomplete(event.reason)
        val displayText = localizedText.get(config.language)

        scheduleAndDeliver(
            kind = FeedbackKind.REP,
            severity = FeedbackSeverity.ERROR,
            localizedText = localizedText,
            displayText = displayText,
            dedupeKey = "rep_incomplete:${event.reason}",
            activeKey = "correction",
            cooldownGroup = "rep_incomplete:${event.reason}",
            // Keep amber visual (not a hard error), but raise delivery weight:
            // ERROR + REPLACE_LOWER clears the active joint-WARNING slot, and
            // forceAudible bypasses the min audible gap so the "rep not counted"
            // cue is not swallowed by form chatter at end of the movement.
            messageType = MessageType.WARNING,
            forceAudible = true,
            interruptPolicy = FeedbackInterruptPolicy.REPLACE_LOWER
        )
    }

    private fun messageForRepIncomplete(reason: RepIncompleteReason): LocalizedText = when (reason) {
        RepIncompleteReason.NO_TARGET_DEPTH -> SystemMessageRegistry.get(
            "training_rep_incomplete_depth",
            "لم تصل إلى الوضع المطلوب، أكمل المدى كاملاً",
            "You didn't reach the target. Complete the full range."
        )
        RepIncompleteReason.NO_FULL_RETURN -> SystemMessageRegistry.get(
            "training_rep_incomplete_return",
            "أكمل الرجوع إلى وضع البداية",
            "Return fully to the start position."
        )
        RepIncompleteReason.TOO_FAST -> SystemMessageRegistry.get(
            "training_rep_too_fast",
            "حركة سريعة جداً، تمهّل قليلاً",
            "Too fast — slow down."
        )
        RepIncompleteReason.TOO_SLOW -> SystemMessageRegistry.get(
            "training_rep_too_slow",
            "تجاوزت الوقت المحدد، حافظ على الإيقاع",
            "Too slow — keep a steady pace."
        )
    }
    
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
            // Video mode is text-only; rep pulses are handled by the review UI.
        } else {
            // Announce rep count every N reps (NORMAL priority - shouldn't interrupt errors)
            if (event.repNumber - lastAnnouncedRep >= REP_AUDIO_INTERVAL) {
                val repLt = MobileMessageResolver.resolveTrainingNumeral(event.repNumber)
                scheduleAndDeliver(
                    kind = FeedbackKind.REP,
                    severity = FeedbackSeverity.INFO,
                    localizedText = repLt,
                    displayText = repLt.get(config.language),
                    dedupeKey = "rep_count:${event.repNumber}",
                    activeKey = "rep_count",
                    cooldownGroup = "rep_count:${event.repNumber}",
                    messageType = MessageType.INFO,
                    forceAudible = true,
                    allowTone = false,
                    allowVisual = false,
                    allowHaptic = false,
                    interruptPolicy = FeedbackInterruptPolicy.WAIT_FOR_SLOT
                )
                lastAnnouncedRep = event.repNumber
            }
        }
    }
    
    // ==================== Target Reached ====================
    
    private suspend fun handleTargetReached(event: FeedbackEvent.TargetReached) {
        val base = SystemMessageRegistry.get(
            "training_target_reached",
            "أحسنت! اكتملت {n} تكرار",
            "Great job! {n} reps completed"
        )
        val lt = SystemMessageRegistry.substitute(base, mapOf("n" to event.totalReps.toString()))
        val message = lt.get(config.language)
        
        scheduleAndDeliver(
            kind = FeedbackKind.TARGET,
            severity = FeedbackSeverity.MOTIVATION,
            localizedText = lt,
            displayText = message,
            dedupeKey = "target:${event.totalReps}",
            activeKey = "target_reached",
            cooldownGroup = "target",
            messageType = MessageType.MOTIVATION,
            forceAudible = true,
            interruptPolicy = FeedbackInterruptPolicy.REPLACE_LOWER
        )
    }
    
    // ==================== Motivation ====================
    
    private suspend fun triggerStreakMotivation(streak: Int) {
        val localizedText = when {
            streak >= STREAK_THRESHOLD_LARGE ->
                SystemMessageRegistry.get(
                    "training_streak_excellent",
                    "ممتاز! استمر!",
                    "Excellent! Keep going!"
                )
            streak >= STREAK_THRESHOLD_MEDIUM ->
                SystemMessageRegistry.get(
                    "training_streak_great",
                    "أداء رائع!",
                    "Great form!"
                )
            else ->
                SystemMessageRegistry.get("training_streak_good", "جيد!", "Good!")
        }
        val displayText = localizedText.get(config.language)
        
        scheduleAndDeliver(
            kind = FeedbackKind.REP,
            severity = FeedbackSeverity.MOTIVATION,
            localizedText = localizedText,
            displayText = displayText,
            dedupeKey = "streak:$streak",
            activeKey = "motivation",
            cooldownGroup = "streak:$streak",
            messageType = MessageType.MOTIVATION
        )
    }
    
    // ==================== Hold Event Handlers ====================
    
    private fun handleHoldStarted() {
        Log.d(TAG, "Hold started - feedback sent")
    }
    
    private suspend fun handleHoldGraceStarted(event: FeedbackEvent.HoldGraceStarted) {
        val lt = SystemMessageRegistry.get("training_hold_stay", "ابق ثابتاً!", "Stay in position!")
        val message = lt.get(config.language)

        scheduleAndDeliver(
            kind = FeedbackKind.HOLD,
            severity = FeedbackSeverity.WARNING,
            localizedText = lt,
            displayText = message,
            dedupeKey = "hold:grace",
            activeKey = "correction",
            cooldownGroup = "hold:grace",
            messageType = MessageType.WARNING,
            forceAudible = true,
            interruptPolicy = FeedbackInterruptPolicy.WAIT_FOR_SLOT
        )
    }
    
    private suspend fun handleHoldResumed(event: FeedbackEvent.HoldResumed) {
        val lt = SystemMessageRegistry.get("training_hold_resumed", "أحسنت، استمر", "Good, keep holding")
        scheduleAndDeliver(
            kind = FeedbackKind.HOLD,
            severity = FeedbackSeverity.SUCCESS,
            localizedText = lt,
            displayText = lt.get(config.language),
            dedupeKey = "hold:resumed",
            activeKey = "hold:resumed",
            cooldownGroup = "hold:resumed",
            messageType = MessageType.MOTIVATION,
            forceAudible = true,
            interruptPolicy = FeedbackInterruptPolicy.SKIP_IF_BUSY
        )
    }
    
    private suspend fun handleHoldCompleted(event: FeedbackEvent.HoldCompleted) {
        val seconds = event.totalMs / 1000
        val base = SystemMessageRegistry.get(
            "training_hold_completed",
            "أحسنت! ثبات {n} ثانية",
            "Great job! Held for {n} seconds"
        )
        val doneLt = SystemMessageRegistry.substitute(base, mapOf("n" to seconds.toString()))
        val message = doneLt.get(config.language)
        
        scheduleAndDeliver(
            kind = FeedbackKind.HOLD,
            severity = FeedbackSeverity.MOTIVATION,
            localizedText = doneLt,
            displayText = message,
            dedupeKey = "hold:completed",
            activeKey = "hold:completed",
            cooldownGroup = "hold:completed",
            messageType = MessageType.MOTIVATION,
            forceAudible = true,
            interruptPolicy = FeedbackInterruptPolicy.REPLACE_LOWER
        )
    }
    
    private suspend fun handleHoldFailed(event: FeedbackEvent.HoldFailed) {
        val lt = SystemMessageRegistry.get(
            "training_hold_failed",
            "فقدت الوضعية. حاول مجدداً",
            "Position lost. Try again"
        )
        scheduleAndDeliver(
            kind = FeedbackKind.HOLD,
            severity = FeedbackSeverity.ERROR,
            localizedText = lt,
            displayText = lt.get(config.language),
            dedupeKey = "hold:failed",
            activeKey = "correction",
            cooldownGroup = "hold:failed",
            messageType = MessageType.ERROR,
            forceAudible = true,
            interruptPolicy = FeedbackInterruptPolicy.REPLACE_LOWER
        )
    }
    
    // ==================== Position / alignment (single event + severity) ====================
    
    private suspend fun handlePositionCheckFeedback(event: FeedbackEvent.PositionCheckFeedback) {
        val pe = event.check
        when (pe.severity) {
            CheckSeverity.ERROR -> {
                val localizedText = pe.message
                val displayText = localizedText.get(config.language)
                scheduleAndDeliver(
                    kind = FeedbackKind.POSITION_CHECK,
                    severity = FeedbackSeverity.ERROR,
                    localizedText = localizedText,
                    displayText = displayText,
                    dedupeKey = "position:${pe.checkId}",
                    activeKey = "correction",
                    cooldownGroup = "position:${pe.checkId}",
                    messageType = MessageType.ERROR
                )
            }
            CheckSeverity.WARNING -> {
                val localizedText = pe.message
                val displayText = localizedText.get(config.language)
                if (displayText.isBlank()) {
                    Log.w(TAG, "⚡ Position WARNING skipped (empty message): checkId=${pe.checkId}")
                    return
                }
                Log.d(
                    TAG,
                    "⚡ Position WARNING received: checkId=${pe.checkId}, text='${displayText.take(40)}', isVideoMode=$isVideoMode, isTtsEnabled=$isTtsEnabled"
                )
                val delivered = scheduleAndDeliver(
                    kind = FeedbackKind.POSITION_CHECK,
                    severity = FeedbackSeverity.WARNING,
                    localizedText = localizedText,
                    displayText = displayText,
                    dedupeKey = "position_warn:${pe.checkId}",
                    activeKey = "correction",
                    cooldownGroup = "position_warn:${pe.checkId}",
                    messageType = MessageType.WARNING
                )
                Log.d(TAG, "⚡ Position WARNING scheduled: checkId=${pe.checkId}, delivered=$delivered")
            }
            CheckSeverity.TIP -> {
                val localizedText = pe.message
                val displayText = localizedText.get(config.language)
                if (displayText.isBlank()) {
                    Log.w(TAG, "Position TIP skipped (empty message): checkId=${pe.checkId}")
                    return
                }
                val delivered = scheduleAndDeliver(
                    kind = FeedbackKind.POSITION_CHECK,
                    severity = FeedbackSeverity.TIP,
                    localizedText = localizedText,
                    displayText = displayText,
                    dedupeKey = "position_tip:${pe.checkId}",
                    activeKey = "tip",
                    cooldownGroup = "position_tip:${pe.checkId}",
                    messageType = MessageType.TIP
                )
                Log.d(TAG, "Position tip scheduled: ${pe.checkId} delivered=$delivered")
            }
        }
    }
    
    private suspend fun handleSceneWarnings(event: FeedbackEvent.SceneWarnings) {
        for (warning in event.warnings) {
            val localizedText = warning.message
            val displayText = localizedText.get(config.language)

            scheduleAndDeliver(
                kind = FeedbackKind.SCENE,
                severity = FeedbackSeverity.WARNING,
                localizedText = localizedText,
                displayText = displayText,
                dedupeKey = "scene:${warning.axis}",
                activeKey = "correction",
                cooldownGroup = "scene:${warning.axis}",
                messageType = MessageType.WARNING
            )
        }
    }
    
    // ==================== Visibility Event Handlers ====================
    
    private suspend fun handleVisibilityWarning(event: FeedbackEvent.VisibilityWarning) {
        val localizedText = event.message
        val displayText = localizedText.get(config.language)
        
        scheduleAndDeliver(
            kind = FeedbackKind.VISIBILITY,
            severity = FeedbackSeverity.WARNING,
            localizedText = localizedText,
            displayText = displayText,
            dedupeKey = "visibility:warning",
            activeKey = "correction",
            cooldownGroup = "visibility:warning",
            messageType = MessageType.WARNING,
            forceAudible = true,
            interruptPolicy = FeedbackInterruptPolicy.WAIT_FOR_SLOT
        )
    }
    
    private suspend fun handleVisibilityPaused(event: FeedbackEvent.VisibilityPaused) {
        val localizedText = event.message
        val displayText = localizedText.get(config.language)
        
        scheduleAndDeliver(
            kind = FeedbackKind.VISIBILITY,
            severity = FeedbackSeverity.CRITICAL,
            localizedText = localizedText,
            displayText = displayText,
            dedupeKey = "visibility:paused",
            activeKey = "critical",
            cooldownGroup = "visibility:paused",
            messageType = MessageType.ERROR,
            forceAudible = true,
            interruptPolicy = FeedbackInterruptPolicy.INTERRUPT
        )
    }
    
    private fun handleVisibilityResumed(event: FeedbackEvent.VisibilityResumed) {
        // Only provide feedback in camera mode
        if (!isVideoMode) {
            // Reset visibility message state so next warning is treated as new
            feedbackScheduler.reset("visibility:warning")
            feedbackScheduler.reset("visibility:paused")
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
    
    /**
     * Speak text using TTS or cached audio (Camera mode only)
     * @param text The text to speak
     * @param priority Determines if this can interrupt other speech
     */
    fun speak(text: String, priority: SpeakPriority = SpeakPriority.HIGH) {
        if (!isTtsEnabled) return
        
        Log.d("AUDIO_TRACE", "[SPEAK_RAW] text=${text.take(30)} (no audioUrl → always TTS)")
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
        
        // Keep legacy LOW-priority behavior aligned with AudioFeedbackPlayer:
        // tips/info should be skipped instead of queued when TTS is busy.
        if (priority == SpeakPriority.LOW && tts?.isSpeaking == true) {
            Log.d(TAG, "Skipping low-priority speech (legacy TTS busy): $text")
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
        val lt = MobileMessageResolver.resolveTrainingNumeral(number)
        val text = lt.get(config.language)
        val cueKey = "countdown:$number:${System.nanoTime()}"
        scheduleAndDeliverImmediate(
            kind = FeedbackKind.COUNTDOWN,
            severity = FeedbackSeverity.CRITICAL,
            localizedText = lt,
            displayText = text,
            dedupeKey = cueKey,
            activeKey = "countdown",
            cooldownGroup = cueKey,
            messageType = MessageType.INFO,
            forceAudible = true,
            interruptPolicy = FeedbackInterruptPolicy.INTERRUPT
        )
    }
    
    /**
     * Speak "Go!" with energy (localized)
     * Uses HIGH priority - start signal is critical
     */
    fun speakGo() {
        val goLt = SystemMessageRegistry.get("training_countdown_go", "ابدأ!", "Go!")
        val text = goLt.get(config.language)
        val cueKey = "countdown:go:${System.nanoTime()}"
        scheduleAndDeliverImmediate(
            kind = FeedbackKind.COUNTDOWN,
            severity = FeedbackSeverity.CRITICAL,
            localizedText = goLt,
            displayText = text,
            dedupeKey = cueKey,
            activeKey = "countdown",
            cooldownGroup = cueKey,
            messageType = MessageType.MOTIVATION,
            forceAudible = true,
            interruptPolicy = FeedbackInterruptPolicy.INTERRUPT
        )
    }
    
    // ==================== Setup Pose Guidance ====================

    /**
     * Speak directional guidance for the worst joint during SETUP_POSE (ANGLES phase).
     */
    fun speakSetupGuidance(joint: com.trainingvalidator.poc.ui.training.JointGuidance) {
        val localizedText = joint.message
        val message = localizedText.get(config.language)
        if (message.isBlank()) return
        scheduleAndDeliverImmediate(
            kind = FeedbackKind.SETUP,
            severity = FeedbackSeverity.WARNING,
            localizedText = localizedText,
            displayText = message,
            dedupeKey = "setup:${joint.jointCode}",
            activeKey = "setup",
            cooldownGroup = "setup:${joint.jointCode}",
            messageType = MessageType.WARNING,
            forceAudible = true,
            interruptPolicy = FeedbackInterruptPolicy.WAIT_FOR_SLOT
        )
        Log.d(TAG, "Setup guidance: $message")
    }

    /**
     * Speak scene-phase guidance (Region / Posture / Direction).
     * Routed through the scheduler so setup cues do not stack or cut each other.
     */
    fun speakSetupPhaseGuidance(message: com.trainingvalidator.poc.training.models.LocalizedText) {
        val text = message.get(config.language)
        if (text.isBlank()) return
        scheduleAndDeliverImmediate(
            kind = FeedbackKind.SETUP,
            severity = FeedbackSeverity.WARNING,
            localizedText = message,
            displayText = text,
            dedupeKey = "setup_phase:${text.hashCode()}",
            activeKey = "setup",
            cooldownGroup = "setup_phase:${text.hashCode()}",
            messageType = MessageType.WARNING,
            forceAudible = true,
            interruptPolicy = FeedbackInterruptPolicy.WAIT_FOR_SLOT
        )
        Log.d(TAG, "Phase guidance: $text")
    }

    /**
     * Speak a short "well done, get ready" message when the pose is confirmed
     * and the countdown is about to start.
     */
    fun speakPoseConfirmed() {
        val lt = SystemMessageRegistry.get("training_pose_confirmed", "ممتاز، استعد!", "Great, get ready!")
        val text = lt.get(config.language)
        val cueKey = "setup:pose_confirmed:${System.nanoTime()}"
        scheduleAndDeliverImmediate(
            kind = FeedbackKind.SETUP,
            severity = FeedbackSeverity.SUCCESS,
            localizedText = lt,
            displayText = text,
            dedupeKey = cueKey,
            activeKey = "setup",
            cooldownGroup = cueKey,
            messageType = MessageType.MOTIVATION,
            forceAudible = true,
            interruptPolicy = FeedbackInterruptPolicy.WAIT_FOR_SLOT
        )
    }

    /**
     * Await end of pose-confirmed audio (or pacing delay when voice is off).
     * Used by [com.trainingvalidator.poc.ui.training.CountdownController] so countdown does not overlap cues.
     */
    suspend fun speakPoseConfirmedAndAwait() {
        speakPoseConfirmed()
        delay(NO_AUDIO_POSE_MS)
    }

    /**
     * Await end of countdown digit audio (sequential; no overlap with next cue).
     */
    suspend fun speakCountdownAndAwait(number: Int) {
        speakCountdown(number)
        delay(NO_AUDIO_COUNTDOWN_MS)
    }

    /** Stops countdown / feedback audio when countdown is frozen (cancels pending await). */
    fun abortCountdownAudio() {
        audioPlayer?.stopAll()
    }

    fun speakSystemCue(
        messageKey: String,
        localizedText: LocalizedText,
        severity: FeedbackSeverity = FeedbackSeverity.CRITICAL
    ) {
        val message = localizedText.get(config.language)
        if (message.isBlank()) return
        scheduleAndDeliverImmediate(
            kind = FeedbackKind.SYSTEM,
            severity = severity,
            localizedText = localizedText,
            displayText = message,
            dedupeKey = "system:$messageKey",
            activeKey = if (severity == FeedbackSeverity.CRITICAL) "critical" else "system",
            cooldownGroup = "system:$messageKey",
            messageType = if (severity.priority >= FeedbackSeverity.ERROR.priority) MessageType.ERROR else MessageType.WARNING,
            forceAudible = true,
            interruptPolicy = if (severity == FeedbackSeverity.CRITICAL) {
                FeedbackInterruptPolicy.INTERRUPT
            } else {
                FeedbackInterruptPolicy.WAIT_FOR_SLOT
            }
        )
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
    fun setRandomMessages(feedbackMessages: FeedbackMessages?) {
        if (feedbackMessages == null) {
            availableMotivationalMessages = emptyList()
            availableTipMessages = emptyList()
            Log.d(TAG, "Random messages cleared: feedbackMessages is null")
            return
        }
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
        
        val delivered = scheduleAndDeliver(
            kind = FeedbackKind.RANDOM,
            severity = if (useMotivational) FeedbackSeverity.MOTIVATION else FeedbackSeverity.TIP,
            localizedText = message,
            displayText = messageText,
            dedupeKey = "random:${messageText.hashCode()}",
            activeKey = "random",
            cooldownGroup = "random:${messageText.hashCode()}",
            messageType = messageType,
            interruptPolicy = FeedbackInterruptPolicy.SKIP_IF_BUSY
        )
        if (!delivered) return false
        
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
        feedbackScheduler.resetAll()
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
        tts?.let {
            TtsVoiceSelector.applyBestVoice(it, language)
        }
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

        toneGenerator?.release()
        toneGenerator = null
    }
}
