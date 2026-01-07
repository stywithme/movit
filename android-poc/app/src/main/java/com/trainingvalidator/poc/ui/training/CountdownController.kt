package com.trainingvalidator.poc.ui.training

import android.os.CountDownTimer
import android.util.Log

/**
 * CountdownController - Manages countdown timer logic
 * 
 * Handles the 3-2-1-GO countdown before training starts.
 * Also handles resume countdown after visibility pause.
 */
class CountdownController(
    private val countdownSeconds: Int = DEFAULT_COUNTDOWN_SECONDS
) {
    
    companion object {
        private const val TAG = "CountdownController"
        const val DEFAULT_COUNTDOWN_SECONDS = 3
    }
    
    /**
     * Callback interface for countdown events
     */
    interface CountdownListener {
        /** Called on each countdown tick (3, 2, 1) */
        fun onTick(secondsRemaining: Int)
        
        /** Called when countdown reaches zero (GO!) */
        fun onFinish()
        
        /** Called when countdown is cancelled */
        fun onCancelled()
    }
    
    private var countdownTimer: CountDownTimer? = null
    private var currentValue: Int = countdownSeconds
    private var listener: CountdownListener? = null
    private var isRunning = false
    
    /**
     * Set the countdown listener
     */
    fun setListener(listener: CountdownListener) {
        this.listener = listener
    }
    
    /**
     * Start the countdown
     */
    fun start() {
        cancel() // Cancel any existing timer
        
        currentValue = countdownSeconds
        isRunning = true
        
        Log.d(TAG, "Starting countdown from $countdownSeconds")
        
        countdownTimer = object : CountDownTimer((countdownSeconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                currentValue = (millisUntilFinished / 1000).toInt() + 1
                Log.d(TAG, "Countdown tick: $currentValue")
                listener?.onTick(currentValue)
            }
            
            override fun onFinish() {
                isRunning = false
                Log.d(TAG, "Countdown finished!")
                listener?.onFinish()
            }
        }.start()
    }
    
    /**
     * Cancel the countdown
     */
    fun cancel() {
        if (isRunning) {
            Log.d(TAG, "Countdown cancelled at $currentValue")
            listener?.onCancelled()
        }
        
        countdownTimer?.cancel()
        countdownTimer = null
        isRunning = false
    }
    
    /**
     * Check if countdown is currently running
     */
    fun isRunning(): Boolean = isRunning
    
    /**
     * Get current countdown value
     */
    fun getCurrentValue(): Int = currentValue
    
    /**
     * Release resources
     */
    fun release() {
        cancel()
        listener = null
    }
}
