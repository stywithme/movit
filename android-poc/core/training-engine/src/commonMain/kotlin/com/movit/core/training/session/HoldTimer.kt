package com.movit.core.training.session

/**
 * Hold-exercise timing extracted from legacy Android [com.movit.training.engine.HoldTimer].
 * Uses callbacks instead of StateFlow so it stays platform-neutral.
 */
class HoldTimer(
    private val targetDurationMs: Long,
    private val gracePeriodMs: Long,
) {
    var state: HoldState = HoldState.IDLE
        private set

    var elapsedMs: Long = 0L
        private set

    var graceRemainingMs: Long? = null
        private set

    var gracePeriodCount: Int = 0
        private set

    var onStateChanged: ((HoldState, HoldState) -> Unit)? = null
    var onHoldStarted: (() -> Unit)? = null
    var onGraceStarted: ((elapsedMs: Long, gracePeriodMs: Long) -> Unit)? = null
    var onGraceResumed: ((elapsedMs: Long, gracePeriodsUsed: Int) -> Unit)? = null
    var onCompleted: ((totalMs: Long, gracePeriodsUsed: Int) -> Unit)? = null
    var onFailed: ((elapsedMs: Long, gracePeriodsUsed: Int) -> Unit)? = null

    private var holdStartTime: Long = 0L
    private var accumulatedHoldMs: Long = 0L
    private var currentSegmentStartTime: Long = 0L
    private var graceStartTime: Long = 0L
    private var hasStarted: Boolean = false

    fun update(isInHoldZone: Boolean, currentTimeMs: Long) {
        when (state) {
            HoldState.IDLE -> if (isInHoldZone) enterHoldZone(currentTimeMs)

            HoldState.HOLDING -> if (isInHoldZone) {
                val currentSegmentTime = currentTimeMs - currentSegmentStartTime
                val totalElapsed = accumulatedHoldMs + currentSegmentTime
                elapsedMs = totalElapsed
                if (totalElapsed >= targetDurationMs) {
                    complete(currentTimeMs)
                }
            } else {
                startGracePeriod(currentTimeMs)
            }

            HoldState.GRACE_PERIOD -> if (isInHoldZone) {
                resumeFromGrace(currentTimeMs)
            } else {
                val graceElapsed = currentTimeMs - graceStartTime
                val graceRemaining = gracePeriodMs - graceElapsed
                graceRemainingMs = graceRemaining.coerceAtLeast(0)
                if (graceRemaining <= 0) {
                    fail(currentTimeMs)
                }
            }

            HoldState.COMPLETED, HoldState.FAILED -> Unit
        }
    }

    fun reset() {
        val oldState = state
        state = HoldState.IDLE
        elapsedMs = 0L
        graceRemainingMs = null
        holdStartTime = 0L
        accumulatedHoldMs = 0L
        currentSegmentStartTime = 0L
        graceStartTime = 0L
        gracePeriodCount = 0
        hasStarted = false
        if (oldState != HoldState.IDLE) {
            onStateChanged?.invoke(oldState, HoldState.IDLE)
        }
    }

    fun getProgress(): Float {
        if (targetDurationMs <= 0) return 0f
        return (elapsedMs.toFloat() / targetDurationMs.toFloat()).coerceIn(0f, 1f)
    }

    fun getRemainingMs(): Long = (targetDurationMs - elapsedMs).coerceAtLeast(0)

    fun getTargetDurationMs(): Long = targetDurationMs

    fun getGracePeriodMs(): Long = gracePeriodMs

    fun hasStarted(): Boolean = hasStarted

    fun isCompleted(): Boolean = state == HoldState.COMPLETED

    fun isFailed(): Boolean = state == HoldState.FAILED

    fun isInGracePeriod(): Boolean = state == HoldState.GRACE_PERIOD

    fun isHolding(): Boolean = state == HoldState.HOLDING

    private fun enterHoldZone(currentTimeMs: Long) {
        val oldState = state
        state = HoldState.HOLDING
        if (!hasStarted) {
            hasStarted = true
            holdStartTime = currentTimeMs
            accumulatedHoldMs = 0L
            onHoldStarted?.invoke()
        }
        currentSegmentStartTime = currentTimeMs
        graceRemainingMs = null
        onStateChanged?.invoke(oldState, HoldState.HOLDING)
    }

    private fun startGracePeriod(currentTimeMs: Long) {
        val oldState = state
        val currentSegmentTime = currentTimeMs - currentSegmentStartTime
        accumulatedHoldMs += currentSegmentTime
        elapsedMs = accumulatedHoldMs
        state = HoldState.GRACE_PERIOD
        graceStartTime = currentTimeMs
        gracePeriodCount++
        graceRemainingMs = gracePeriodMs
        onStateChanged?.invoke(oldState, HoldState.GRACE_PERIOD)
        onGraceStarted?.invoke(accumulatedHoldMs, gracePeriodMs)
    }

    private fun resumeFromGrace(currentTimeMs: Long) {
        val oldState = state
        state = HoldState.HOLDING
        currentSegmentStartTime = currentTimeMs
        graceRemainingMs = null
        onStateChanged?.invoke(oldState, HoldState.HOLDING)
        onGraceResumed?.invoke(accumulatedHoldMs, gracePeriodCount)
    }

    private fun complete(currentTimeMs: Long) {
        val oldState = state
        state = HoldState.COMPLETED
        val currentSegmentTime = currentTimeMs - currentSegmentStartTime
        val totalElapsed = accumulatedHoldMs + currentSegmentTime
        elapsedMs = totalElapsed
        graceRemainingMs = null
        onStateChanged?.invoke(oldState, HoldState.COMPLETED)
        onCompleted?.invoke(totalElapsed, gracePeriodCount)
    }

    private fun fail(currentTimeMs: Long) {
        val oldState = state
        state = HoldState.FAILED
        graceRemainingMs = 0L
        onStateChanged?.invoke(oldState, HoldState.FAILED)
        onFailed?.invoke(accumulatedHoldMs, gracePeriodCount)
    }
}
