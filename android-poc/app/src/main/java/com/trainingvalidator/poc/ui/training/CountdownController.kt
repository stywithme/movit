package com.trainingvalidator.poc.ui.training

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * CountdownController — 3-2-1-GO before training starts.
 *
 * Uses a coroutine-driven sequence with [CountdownAudioProvider] so each cue
 * **finishes** before the next (hybrid timing: at least [TICK_MIN_MS] per step,
 * extended if audio is longer). This avoids "one" being cut off by "Go".
 *
 * Freeze/unfreeze: job is cancelled and a snapshot resumes from the last checkpoint.
 */
class CountdownController(
    private val countdownSeconds: Int = DEFAULT_COUNTDOWN_SECONDS
) {

    companion object {
        private const val TAG = "CountdownController"
        const val DEFAULT_COUNTDOWN_SECONDS = 3
        /** Minimum time after pose-confirmed audio before first "3" */
        private const val POST_POSE_MIN_MS = 1500L
        /** Minimum time per countdown digit step (after audio starts) */
        private const val TICK_MIN_MS = 1000L
    }

    /**
     * Plays countdown audio; implemented by [com.trainingvalidator.poc.training.feedback.FeedbackManager] helpers.
     */
    interface CountdownAudioProvider {
        suspend fun playPoseConfirmed()
        suspend fun playCountdownNumber(secondsRemaining: Int)
        suspend fun playGo()
    }

    interface CountdownListener {
        /** Called each step: secondsRemaining is 3, 2, 1 */
        fun onTick(secondsRemaining: Int)
        /** Countdown reached zero — start training (audio for "Go" follows in provider) */
        fun onFinish()
        fun onCancelled()
        fun onFrozen()
        fun onUnfrozen()
    }

    var audioProvider: CountdownAudioProvider? = null

    private var listener: CountdownListener? = null
    private var hostScope: CoroutineScope? = null
    private var countdownJob: Job? = null

    @Volatile private var isRunning = false
    @Volatile private var isFrozen = false

    private var millisRemaining: Long = 0L
    private var currentValue: Int = countdownSeconds

    @Volatile private var checkpointPoseDone = false
    @Volatile private var checkpointNumbers: List<Int> = emptyList()

    /** Saved in [freeze] for [unfreeze] */
    private var frozenSnapshot: Pair<Boolean, List<Int>>? = null

    fun setListener(listener: CountdownListener) {
        this.listener = listener
    }

    /**
     * Start or resume countdown. Pass the same scope used for training (e.g. [androidx.lifecycle.viewModelScope]).
     */
    fun start(scope: CoroutineScope) {
        hostScope = scope
        val snap = frozenSnapshot
        frozenSnapshot = null

        val initialPoseDone = snap?.first ?: false
        val initialNumbers = snap?.second?.toMutableList()
            ?: (countdownSeconds downTo 1).toMutableList()

        countdownJob?.cancel()
        countdownJob = null

        isFrozen = false
        checkpointPoseDone = initialPoseDone
        checkpointNumbers = initialNumbers.toList()
        currentValue = initialNumbers.firstOrNull() ?: countdownSeconds
        millisRemaining = currentValue * 1000L

        isRunning = true
        countdownJob = scope.launch(Dispatchers.Main.immediate) {
            try {
                runCountdownLoop(initialPoseDone, initialNumbers)
            } catch (e: CancellationException) {
                throw e
            } finally {
                isRunning = false
            }
        }
        Log.d(TAG, "Countdown started (poseDone=$initialPoseDone numbers=$initialNumbers)")
    }

    fun freeze() {
        if (!isRunning || isFrozen) return
        isFrozen = true
        frozenSnapshot = Pair(checkpointPoseDone, checkpointNumbers.toList())
        countdownJob?.cancel()
        countdownJob = null
        isRunning = false
        listener?.onFrozen()
        Log.d(TAG, "Countdown frozen snapshot=$frozenSnapshot")
    }

    fun unfreeze() {
        if (!isFrozen) return
        val scope = hostScope ?: run {
            Log.e(TAG, "unfreeze: no host scope")
            return
        }
        isFrozen = false
        listener?.onUnfrozen()
        start(scope)
    }

    fun cancel() {
        val hadProgress = isRunning || isFrozen
        countdownJob?.cancel()
        countdownJob = null
        frozenSnapshot = null
        isRunning = false
        isFrozen = false
        millisRemaining = 0L
        if (hadProgress) {
            listener?.onCancelled()
        }
        Log.d(TAG, "Countdown cancelled")
    }

    fun isRunning(): Boolean = isRunning
    fun isFrozen(): Boolean = isFrozen
    fun getCurrentValue(): Int = currentValue

    fun release() {
        cancel()
        listener = null
        hostScope = null
        audioProvider = null
    }

    private suspend fun runCountdownLoop(poseDoneIn: Boolean, numbers: MutableList<Int>) {
        val provider = audioProvider
        var poseDone = poseDoneIn

        if (!poseDone) {
            checkpointPoseDone = false
            checkpointNumbers = numbers.toList()
            val tPose = SystemClock.elapsedRealtime()
            provider?.playPoseConfirmed()
            remainderDelay(tPose, POST_POSE_MIN_MS)
            poseDone = true
            checkpointPoseDone = true
            checkpointNumbers = numbers.toList()
        }

        while (numbers.isNotEmpty()) {
            val n = numbers.first()
            currentValue = n
            millisRemaining = n * 1000L
            listener?.onTick(n)
            checkpointPoseDone = true
            checkpointNumbers = numbers.toList()

            val stepStart = SystemClock.elapsedRealtime()
            provider?.playCountdownNumber(n)
            remainderDelay(stepStart, TICK_MIN_MS)
            numbers.removeAt(0)
        }

        currentValue = 0
        millisRemaining = 0L
        listener?.onFinish()
        // Fire-and-forget GO (same as legacy): engine/UI start first; GO must not block training start.
        hostScope?.launch(Dispatchers.Main.immediate) {
            try {
                audioProvider?.playGo()
            } catch (e: Exception) {
                Log.w(TAG, "playGo failed", e)
            }
        }
    }

    private suspend fun remainderDelay(stepStartMs: Long, minDurationMs: Long) {
        val elapsed = SystemClock.elapsedRealtime() - stepStartMs
        val pad = (minDurationMs - elapsed).coerceAtLeast(0L)
        if (pad > 0L) delay(pad)
    }
}
