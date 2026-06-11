package com.movit.core.training.session

import com.movit.core.training.engine.currentTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * KMP 3-2-1-GO countdown (legacy CountdownController without android.os.SystemClock).
 */
class CountdownController(
    private val countdownSeconds: Int = DEFAULT_COUNTDOWN_SECONDS,
    private val timeProvider: () -> Long = { currentTimeMillis() },
) {
    companion object {
        const val DEFAULT_COUNTDOWN_SECONDS = 3
        private const val POST_POSE_MIN_MS = 1_500L
        private const val TICK_MIN_MS = 1_000L
    }

    interface CountdownAudioProvider {
        suspend fun playPoseConfirmed()
        suspend fun playCountdownNumber(secondsRemaining: Int)
        suspend fun playGo()
    }

    interface CountdownListener {
        fun onTick(secondsRemaining: Int)
        fun onFinish()
        fun onCancelled()
        fun onFrozen()
        fun onUnfrozen()
    }

    var audioProvider: CountdownAudioProvider? = null
    private var listener: CountdownListener? = null
    private var hostScope: CoroutineScope? = null
    private var countdownJob: Job? = null

    private var isRunning = false
    private var isFrozen = false
    private var currentValue: Int = countdownSeconds
    private var checkpointPoseDone = false
    private var checkpointNumbers: List<Int> = emptyList()
    private var frozenSnapshot: Pair<Boolean, List<Int>>? = null

    fun setListener(listener: CountdownListener) {
        this.listener = listener
    }

    fun start(scope: CoroutineScope) {
        hostScope = scope
        val snap = frozenSnapshot
        frozenSnapshot = null
        val initialPoseDone = snap?.first ?: false
        val initialNumbers = snap?.second?.toMutableList()
            ?: (countdownSeconds downTo 1).toMutableList()
        countdownJob?.cancel()
        isFrozen = false
        checkpointPoseDone = initialPoseDone
        checkpointNumbers = initialNumbers.toList()
        currentValue = initialNumbers.firstOrNull() ?: countdownSeconds
        isRunning = true
        countdownJob = scope.launch {
            try {
                runCountdownLoop(initialPoseDone, initialNumbers)
            } catch (_: CancellationException) {
                throw CancellationException()
            } finally {
                isRunning = false
            }
        }
    }

    fun freeze() {
        if (!isRunning || isFrozen) return
        isFrozen = true
        frozenSnapshot = Pair(checkpointPoseDone, checkpointNumbers.toList())
        countdownJob?.cancel()
        countdownJob = null
        isRunning = false
        listener?.onFrozen()
    }

    fun unfreeze() {
        if (!isFrozen) return
        val scope = hostScope ?: return
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
        if (hadProgress) listener?.onCancelled()
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
            val tPose = timeProvider()
            provider?.playPoseConfirmed()
            remainderDelay(tPose, POST_POSE_MIN_MS)
            poseDone = true
            checkpointPoseDone = true
            checkpointNumbers = numbers.toList()
        }
        while (numbers.isNotEmpty()) {
            val n = numbers.first()
            currentValue = n
            listener?.onTick(n)
            checkpointPoseDone = true
            checkpointNumbers = numbers.toList()
            val stepStart = timeProvider()
            provider?.playCountdownNumber(n)
            remainderDelay(stepStart, TICK_MIN_MS)
            numbers.removeAt(0)
        }
        currentValue = 0
        listener?.onFinish()
        hostScope?.launch {
            runCatching { audioProvider?.playGo() }
        }
    }

    private suspend fun remainderDelay(stepStartMs: Long, minDurationMs: Long) {
        val elapsed = timeProvider() - stepStartMs
        val pad = (minDurationMs - elapsed).coerceAtLeast(0L)
        if (pad > 0L) delay(pad)
    }
}
