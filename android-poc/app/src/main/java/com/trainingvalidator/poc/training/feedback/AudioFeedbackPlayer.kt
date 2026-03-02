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
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = if (language == "ar") {
                    Locale.forLanguageTag("ar")
                } else {
                    Locale.US
                }
                
                val result = tts?.setLanguage(locale)
                isTtsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                             result != TextToSpeech.LANG_NOT_SUPPORTED
                
                if (isTtsReady) {
                    Log.d(TAG, "TTS initialized with language: $locale")
                    tts?.setSpeechRate(0.95f)
                    
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
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
                } else {
                    Log.w(TAG, "TTS language not supported: $locale")
                }
            }
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
        
        play(text, audioUrl, priority)
    }
    
    /**
     * Play audio with explicit text and URL
     * 
     * @param text Text to speak (used for TTS fallback)
     * @param audioUrl Optional cached audio URL
     * @param priority Playback priority
     */
    fun play(text: String, audioUrl: String? = null, priority: Priority = Priority.NORMAL) {
        if (text.isBlank()) return
        
        // Check cooldown for non-high priority
        val now = System.currentTimeMillis()
        if (priority != Priority.HIGH && now - lastPlayTime < MIN_PLAY_INTERVAL_MS) {
            Log.d(TAG, "Skipping playback (cooldown): $text")
            return
        }
        
        // For LOW priority, skip if busy
        if (priority == Priority.LOW && (isMediaPlayerPlaying || playbackQueue.isNotEmpty())) {
            Log.d(TAG, "Skipping low priority (busy): $text")
            return
        }
        
        val item = PlaybackItem(text, audioUrl, priority)
        
        // HIGH priority interrupts current playback
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
            Log.d(TAG, "Audio URL available: $audioUrl")
            val audioFile = audioCache.getAudioPathFromUrl(audioUrl)
            
            if (audioFile != null && audioFile.exists()) {
                Log.d(TAG, "Using cached audio: ${audioFile.absolutePath}")
                playCachedAudio(audioFile, item.text)
                return
            } else {
                Log.w(TAG, "Audio file not in cache, falling back to TTS. URL: $audioUrl")
            }
        } else {
            Log.d(TAG, "No audio URL, using TTS for: ${item.text.take(30)}...")
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
    fun stopCurrentPlayback() {
        releaseMediaPlayer()
        tts?.stop()
        isMediaPlayerPlaying = false
    }
    
    /**
     * Stop all playback and clear queue
     */
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
        
        // Update language for audio file selection
        language = newLanguage
        
        // Update TTS locale
        val locale = if (newLanguage == "ar") {
            Locale.forLanguageTag("ar")
        } else {
            Locale.US
        }
        tts?.setLanguage(locale)
        
        Log.d(TAG, "Language changed to: $newLanguage")
    }
}
