package com.trainingvalidator.poc.ui.training

import android.os.CountDownTimer
import android.util.Log

/**
 * CountdownController - Manages the 3-2-1-GO countdown before training starts.
 *
 * Improvements over original:
 * - Fixed tick calculation: uses ceiling division to avoid showing the same
 *   number twice (CountDownTimer is not perfectly precise).
 * - Freeze / Unfreeze: pauses the visual countdown when the user temporarily
 *   leaves the start position, then resumes from where it stopped.
 * - onCountdownFinished signal fires immediately (caller must not delay it
 *   with animations - run animations in parallel instead).
 */
class CountdownController(
    private val countdownSeconds: Int = DEFAULT_COUNTDOWN_SECONDS
) {

    companion object {
        private const val TAG = "CountdownController"
        const val DEFAULT_COUNTDOWN_SECONDS = 3
    }

    interface CountdownListener {
        /** Called every second: secondsRemaining is 3, 2, 1 */
        fun onTick(secondsRemaining: Int)
        /** Countdown reached zero - start training immediately */
        fun onFinish()
        /** Countdown was cancelled (back to setup pose) */
        fun onCancelled()
        /** Countdown is temporarily frozen (user moved out of position) */
        fun onFrozen()
        /** Countdown resumed after being frozen */
        fun onUnfrozen()
    }

    private var countdownTimer: CountDownTimer? = null

    @Volatile private var isRunning = false
    @Volatile private var isFrozen = false

    private var millisRemaining: Long = 0L
    private var currentValue: Int = countdownSeconds
    private var listener: CountdownListener? = null

    fun setListener(listener: CountdownListener) {
        this.listener = listener
    }

    // ──────────────────────────────────────────────────────────────────────

    /** Start a fresh countdown from [countdownSeconds]. */
    fun start() {
        // Stop any existing timer without firing onCancelled
        countdownTimer?.cancel()
        countdownTimer = null
        isRunning = false
        isFrozen = false
        millisRemaining = countdownSeconds * 1000L
        startInternalTimer(millisRemaining)
        Log.d(TAG, "Countdown started from ${countdownSeconds}s")
    }

    /**
     * Temporarily freeze the countdown at the current position.
     * The visual number stays visible; the timer is paused.
     */
    fun freeze() {
        if (!isRunning || isFrozen) return
        isFrozen = true
        countdownTimer?.cancel()
        countdownTimer = null
        isRunning = false
        listener?.onFrozen()
        Log.d(TAG, "Countdown frozen at ${currentValue}s (${millisRemaining}ms remaining)")
    }

    /**
     * Resume a frozen countdown from where it paused.
     */
    fun unfreeze() {
        if (!isFrozen || millisRemaining <= 0L) return
        isFrozen = false
        startInternalTimer(millisRemaining)
        listener?.onUnfrozen()
        Log.d(TAG, "Countdown unfrozen, resuming from ${millisRemaining}ms")
    }

    /** Cancel and reset completely. */
    fun cancel() {
        if (isRunning || isFrozen) {
            listener?.onCancelled()
        }
        countdownTimer?.cancel()
        countdownTimer = null
        isRunning = false
        isFrozen = false
        millisRemaining = 0L
        Log.d(TAG, "Countdown cancelled")
    }

    fun isRunning(): Boolean = isRunning
    fun isFrozen(): Boolean = isFrozen
    fun getCurrentValue(): Int = currentValue

    fun release() {
        cancel()
        listener = null
    }

    // ──────────────────────────────────────────────────────────────────────

    private fun startInternalTimer(fromMs: Long) {
        isRunning = true
        countdownTimer = object : CountDownTimer(fromMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                millisRemaining = millisUntilFinished
                // Ceiling division: guarantees 3→2→1 without duplicates
                currentValue = ((millisUntilFinished + 999L) / 1000L).toInt()
                Log.d(TAG, "Tick: ${currentValue}s")
                listener?.onTick(currentValue)
            }

            override fun onFinish() {
                isRunning = false
                millisRemaining = 0L
                currentValue = 0
                Log.d(TAG, "Countdown finished")
                // Fire immediately - caller must NOT delay onCountdownFinished
                listener?.onFinish()
            }
        }.start()
    }
}
