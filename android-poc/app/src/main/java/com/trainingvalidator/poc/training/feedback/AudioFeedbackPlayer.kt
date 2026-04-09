package com.trainingvalidator.poc.training.feedback

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.trainingvalidator.poc.storage.AudioCacheManager
import com.trainingvalidator.poc.training.models.LocalizedText
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.jvm.Synchronized

/**
 * AudioFeedbackPlayer
 * 
 * Plays audio feedback for exercises, prioritizing cached audio files
 * over text-to-speech (TTS).
 * 
 * Audio Priority:
 * 1. Cached audio file (if available)
 * 2. Text-to-speech (fallback)
 * 
 * Features:
 * - Queue-based playback (prevents overlapping audio)
 * - Priority levels (HIGH can interrupt, LOW waits in queue)
 * - Automatic fallback to TTS when audio not cached
 * - Cooldown to prevent spam
 */
class AudioFeedbackPlayer(
    private val context: Context,
    private val audioCache: AudioCacheManager,
    private var language: String = "en"
) {
    
    companion object {
        private const val TAG = "AudioFeedbackPlayer"
        private const val MIN_PLAY_INTERVAL_MS = 1000L
        private const val TTS_SAFETY_TIMEOUT_MS = 6000L
    }
    
    /**
     * Playback priority levels
     */
    enum class Priority {
        HIGH,   // Interrupts current playback (errors, warnings)
        NORMAL, // Waits in queue (rep counts, motivation)
        LOW     // Skipped if busy (tips)
    }
    
    // MediaPlayer for cached audio
    private var mediaPlayer: MediaPlayer? = null
    private var isMediaPlayerPlaying = false
    
    // TTS for fallback
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    
    // Playback queue
    private val playbackQueue = ConcurrentLinkedQueue<PlaybackItem>()
    private var isProcessingQueue = false
    
    // Cooldown
    private var lastPlayTime = 0L
    
    // Listener for playback events
    private var playbackListener: PlaybackListener? = null
    
    interface PlaybackListener {
        fun onPlaybackStarted(text: String)
        fun onPlaybackCompleted()
        fun onPlaybackError(error: String)
    }
    
    private data class PlaybackItem(
        val text: String,
        val audioUrl: String?,
        val priority: Priority
    )
    
    // ==================== Initialization ====================
    
    /**
     * Initialize TTS for fallback
     */
    fun initialize() {
        initTtsWithEngine(TtsVoiceSelector.getPreferredEngine())
    }

    private fun initTtsWithEngine(engine: String?) {
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let {
                    TtsVoiceSelector.applyBestVoice(it, language)
                    it.setSpeechRate(0.95f)
                    it.setPitch(0.95f)
                    isTtsReady = true
                    Log.d(TAG, "TTS fallback ready (engine=${engine ?: "default"})")

                    it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                onTtsCompleted()
                            }
                        }
                        @Deprecated("Use onError(String, Int) override", ReplaceWith(""))
                        override fun onError(utteranceId: String?) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                onTtsCompleted()
                            }
                        }
                        override fun onError(utteranceId: String?, errorCode: Int) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                onTtsCompleted()
                            }
                        }
                    })
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
    
    fun setPlaybackListener(listener: PlaybackListener?) {
        playbackListener = listener
    }
    
    // ==================== Playback Methods ====================
    
    /**
     * Play audio for localized text
     * 
     * @param localizedText Text with optional audio URLs
     * @param priority Playback priority
     */
    fun play(localizedText: LocalizedText, priority: Priority = Priority.NORMAL) {
        val text = localizedText.get(language)
        val audioUrl = localizedText.getAudioUrl(language)
        
        Log.d("AUDIO_TRACE", "[PLAYER] lang=$language url=${audioUrl?.takeLast(30) ?: "NULL"} text=${text.take(25)}")
        play(text, audioUrl, priority)
    }
    
    /**
     * Play audio with explicit text and URL
     * 
     * @param text Text to speak (used for TTS fallback)
     * @param audioUrl Optional cached audio URL
     * @param priority Playback priority
     */
    @Synchronized
    fun play(text: String, audioUrl: String? = null, priority: Priority = Priority.NORMAL) {
        if (text.isBlank()) return
        
        // Check cooldown for non-high priority
        val now = System.currentTimeMillis()
        if (priority != Priority.HIGH && now - lastPlayTime < MIN_PLAY_INTERVAL_MS) {
            Log.d(TAG, "Skipping playback (cooldown): $text")
            return
        }
        
        // LOW priority should be dropped whenever the player is already
        // preparing, speaking, playing, or has queued work waiting.
        val isBusy = isProcessingQueue || isMediaPlayerPlaying || isTtsSpeaking || playbackQueue.isNotEmpty()
        if (priority == Priority.LOW && isBusy) {
            Log.d(TAG, "Skipping low priority (busy): $text")
            return
        }
        
        val item = PlaybackItem(text, audioUrl, priority)
        
        // HIGH priority interrupts current playback (resets queue state)
        if (priority == Priority.HIGH) {
            stopCurrentPlayback()
            playbackQueue.clear()
        }
        
        playbackQueue.offer(item)
        processQueue()
    }
    
    /**
     * Play countdown number
     */
    fun playCountdown(number: Int) {
        play(number.toString(), null, Priority.HIGH)
    }
    
    /**
     * Play "Go!" message
     */
    fun playGo() {
        val goText = if (language == "ar") "ابدأ!" else "Go!"
        play(goText, null, Priority.HIGH)
    }
    
    // ==================== Queue Processing ====================
    
    @Synchronized
    private fun processQueue() {
        if (isProcessingQueue || playbackQueue.isEmpty()) return
        if (isMediaPlayerPlaying) return
        
        isProcessingQueue = true
        
        val item = playbackQueue.poll()
        if (item != null) {
            lastPlayTime = System.currentTimeMillis()
            playItem(item)
        } else {
            isProcessingQueue = false
        }
    }
    
    private fun playItem(item: PlaybackItem) {
        // Try cached audio first
        val audioUrl = item.audioUrl
        
        if (audioUrl != null) {
            val audioFile = audioCache.getAudioPathFromUrl(audioUrl)
            
            if (audioFile != null && audioFile.exists()) {
                Log.d(TAG, "AUDIO_DECISION: CACHED file=${audioFile.name}")
                playCachedAudio(audioFile, item.text)
                return
            } else {
                Log.w(
                    TAG,
                    "AUDIO_DECISION: CACHE_MISS url=$audioUrl exists=${audioFile?.exists()}"
                )
            }
        } else {
            Log.d(TAG, "AUDIO_DECISION: NO_URL text=${item.text.take(30)}...")
        }
        
        // Fall back to TTS
        playTts(item.text)
    }
    
    private fun playCachedAudio(file: File, text: String) {
        try {
            releaseMediaPlayer()
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener {
                    isMediaPlayerPlaying = true
                    playbackListener?.onPlaybackStarted(text)
                    start()
                    Log.d(TAG, "Playing cached audio: ${file.name}")
                }
                setOnCompletionListener {
                    onPlaybackComplete()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: $what, $extra")
                    playbackListener?.onPlaybackError("MediaPlayer error: $what")
                    onPlaybackComplete()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play cached audio", e)
            playTts(text)
        }
    }
    
    // Whether TTS is currently speaking (tracked via UtteranceProgressListener)
    private var isTtsSpeaking = false
    private var ttsTimeoutHandler: android.os.Handler? = null
    private var ttsTimeoutRunnable: Runnable? = null
    
    private fun playTts(text: String) {
        if (!isTtsReady) {
            Log.w(TAG, "TTS not ready, skipping: $text")
            onPlaybackComplete()
            return
        }
        
        isTtsSpeaking = true
        playbackListener?.onPlaybackStarted(text)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "feedback_${System.currentTimeMillis()}")
        Log.d(TAG, "Playing TTS: $text")
        
        // Safety timeout in case UtteranceProgressListener doesn't fire
        cancelTtsTimeout()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = Runnable {
            if (isTtsSpeaking) {
                Log.w(TAG, "TTS safety timeout - forcing completion")
                onTtsCompleted()
            }
        }
        handler.postDelayed(runnable, TTS_SAFETY_TIMEOUT_MS)
        ttsTimeoutHandler = handler
        ttsTimeoutRunnable = runnable
    }
    
    private fun onTtsCompleted() {
        isTtsSpeaking = false
        cancelTtsTimeout()
        onPlaybackComplete()
    }
    
    private fun cancelTtsTimeout() {
        ttsTimeoutRunnable?.let { ttsTimeoutHandler?.removeCallbacks(it) }
        ttsTimeoutRunnable = null
    }
    
    @Synchronized
    private fun onPlaybackComplete() {
        isMediaPlayerPlaying = false
        isProcessingQueue = false
        playbackListener?.onPlaybackCompleted()
        processQueue()
    }
    
    // ==================== Control Methods ====================
    
    /**
     * Stop current playback
     */
    @Synchronized
    fun stopCurrentPlayback() {
        cancelTtsTimeout()
        releaseMediaPlayer()
        tts?.stop()
        isMediaPlayerPlaying = false
        isTtsSpeaking = false
        isProcessingQueue = false
    }
    
    /**
     * Stop all playback and clear queue
     */
    @Synchronized
    fun stopAll() {
        stopCurrentPlayback()
        playbackQueue.clear()
        isProcessingQueue = false
    }
    
    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer", e)
        }
        mediaPlayer = null
        isMediaPlayerPlaying = false
    }
    
    // ==================== Lifecycle ====================
    
    /**
     * Release all resources
     */
    fun release() {
        stopAll()
        cancelTtsTimeout()
        releaseMediaPlayer()
        tts?.shutdown()
        tts = null
        isTtsReady = false
        isTtsSpeaking = false
        playbackListener = null
    }
    
    /**
     * Update language setting
     * Updates both the language used for audio file selection and TTS locale
     */
    fun setLanguage(newLanguage: String) {
        if (newLanguage == language) return
        language = newLanguage
        tts?.let { TtsVoiceSelector.applyBestVoice(it, newLanguage) }
        Log.d(TAG, "Language changed to: $newLanguage")
    }
}
