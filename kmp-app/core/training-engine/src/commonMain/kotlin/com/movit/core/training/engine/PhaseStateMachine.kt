package com.movit.core.training.engine

class PhaseStateMachine(
    private val countingMethod: CountingMethod,
    private val primaryJoints: List<PhaseJointConfig>,
    private val timing: PhaseTimingConfig,
    private val numberOfPhases: Int = 4,
    private val timeProvider: () -> Long = { currentTimeMillis() },
    phaseHysteresisDegrees: Double = DEFAULT_HYSTERESIS_DEGREES,
) {
    companion object {
        const val DEFAULT_HYSTERESIS_DEGREES = 3.0
    }

    private val hysteresis: Double = phaseHysteresisDegrees
    private val minRepIntervalMs: Long = timing.minRepIntervalMs
    private val maxRepIntervalMs: Long = timing.maxRepIntervalMs
    private val minPhaseDurationMs: Long = timing.minPhaseDurationMs

    var currentPhase: Phase = Phase.IDLE
        private set

    var previousPhase: Phase = Phase.IDLE
        private set

    private var phaseEntryTime: Long = 0L
    private val phaseTimings = mutableMapOf<Phase, Long>()

    var onPhaseChanged: ((Phase, Phase) -> Unit)? = null
    var onRepCompleted: (() -> Unit)? = null
    var onRepIncomplete: ((RepIncompleteReason) -> Unit)? = null

    private val upDownPrimaryJoints: List<PhaseJointConfig> =
        primaryJoints.filter { it.hasUpDownRanges() }

    private val holdPrimaryJoints: List<PhaseJointConfig> =
        primaryJoints.filter { it.hasHoldRange() }

    private val upRangeMin: Double
    private val upRangeMax: Double
    private val downRangeMin: Double
    private val downRangeMax: Double

    private var repCountedThisCycle = false
    private var repMovementStartTime: Long = 0L

    init {
        val upDownJoints = upDownPrimaryJoints
        val holdRangeJoints = holdPrimaryJoints

        if (upDownJoints.isNotEmpty()) {
            upRangeMin = upDownJoints.map { it.upRangeEffectiveMin() }.average()
            upRangeMax = upDownJoints.map { it.upRangeOutermostMax() }.average()
            downRangeMin = upDownJoints.map { it.downRangeOutermostMin() }.average()
            downRangeMax = upDownJoints.map { it.downRangeEffectiveMax() }.average()
        } else if (holdRangeJoints.isNotEmpty()) {
            upRangeMin = holdRangeJoints.map { it.holdRangeOutermostMin() }.average()
            upRangeMax = holdRangeJoints.map { it.holdRangeEffectiveMax() }.average()
            downRangeMin = holdRangeJoints.map { it.holdRangeOutermostMin() }.average()
            downRangeMax = holdRangeJoints.map { it.holdRangeEffectiveMax() }.average()
        } else {
            upRangeMin = 120.0
            upRangeMax = 180.0
            downRangeMin = 0.0
            downRangeMax = 80.0
        }
    }

    fun update(primaryAngles: Map<String, Double>): Phase {
        if (primaryAngles.isEmpty() || primaryJoints.isEmpty()) {
            return currentPhase
        }

        val nextPhase = when (countingMethod) {
            CountingMethod.UP_DOWN -> {
                if (upDownPrimaryJoints.isNotEmpty()) {
                    updateUpDownStrict(upDownPrimaryJoints, primaryAngles)
                } else {
                    val angleValues = primaryAngles.values
                    if (angleValues.isEmpty()) return currentPhase
                    updateUpDownLegacy(angleValues.min(), angleValues.max())
                }
            }
            CountingMethod.HOLD -> {
                if (holdPrimaryJoints.isNotEmpty()) {
                    updateHoldStrict(holdPrimaryJoints, primaryAngles)
                } else {
                    val angleValues = primaryAngles.values
                    if (angleValues.isEmpty()) return currentPhase
                    updateHoldLegacy(angleValues.min(), angleValues.max())
                }
            }
        }

        if (nextPhase != currentPhase) {
            handlePhaseTransition(nextPhase)
        }

        return currentPhase
    }

    private fun jointInUpRange(joint: PhaseJointConfig, angle: Double, exiting: Boolean): Boolean {
        val minBound = if (exiting) joint.upRangeEffectiveMin() - hysteresis else joint.upRangeEffectiveMin()
        val maxBound = joint.upRangeOutermostMax() + hysteresis
        return angle in minBound..maxBound
    }

    private fun jointInDownRange(joint: PhaseJointConfig, angle: Double, exiting: Boolean): Boolean {
        val minBound = joint.downRangeOutermostMin() - hysteresis
        val maxBound = if (exiting) joint.downRangeEffectiveMax() + hysteresis else joint.downRangeEffectiveMax()
        return angle in minBound..maxBound
    }

    private fun jointInHoldRange(joint: PhaseJointConfig, angle: Double, exiting: Boolean): Boolean {
        val minBound = joint.holdRangeOutermostMin() - hysteresis
        val maxBound = if (exiting) joint.holdRangeEffectiveMax() + hysteresis else joint.holdRangeEffectiveMax()
        return angle in minBound..maxBound
    }

    private fun jointHasLeftUpRange(joint: PhaseJointConfig, angle: Double): Boolean =
        angle < joint.upRangeEffectiveMin() - hysteresis

    private fun jointHasEnteredDownRange(joint: PhaseJointConfig, angle: Double): Boolean =
        angle <= joint.downRangeEffectiveMax()

    private fun jointHasLeftDownRange(joint: PhaseJointConfig, angle: Double): Boolean =
        angle > joint.downRangeEffectiveMax() + hysteresis

    private fun jointHasEnteredUpRange(joint: PhaseJointConfig, angle: Double): Boolean =
        angle >= joint.upRangeEffectiveMin()

    private fun updateUpDownStrict(joints: List<PhaseJointConfig>, primaryAngles: Map<String, Double>): Phase {
        val visibleJoints = joints.filter { primaryAngles[it.jointCode] != null }
        if (visibleJoints.isEmpty()) return currentPhase
        fun angleOf(j: PhaseJointConfig): Double = primaryAngles.getValue(j.jointCode)

        return when (currentPhase) {
            Phase.IDLE -> if (visibleJoints.all { jointInUpRange(it, angleOf(it), exiting = false) }) {
                Phase.START
            } else {
                Phase.IDLE
            }

            Phase.START -> if (visibleJoints.all { jointHasLeftUpRange(it, angleOf(it)) }) {
                repCountedThisCycle = false
                repMovementStartTime = timeProvider()
                Phase.DOWN
            } else {
                Phase.START
            }

            Phase.DOWN -> when {
                visibleJoints.all { jointHasEnteredDownRange(it, angleOf(it)) } -> Phase.BOTTOM
                visibleJoints.all { jointInUpRange(it, angleOf(it), exiting = false) } -> Phase.START
                else -> Phase.DOWN
            }

            Phase.BOTTOM -> if (visibleJoints.all { jointHasLeftDownRange(it, angleOf(it)) }) {
                Phase.UP
            } else {
                Phase.BOTTOM
            }

            Phase.UP -> when {
                visibleJoints.all { jointHasEnteredUpRange(it, angleOf(it)) } -> Phase.START
                visibleJoints.all { jointInDownRange(it, angleOf(it), exiting = false) } -> Phase.BOTTOM
                else -> Phase.UP
            }

            else -> currentPhase
        }
    }

    private fun updateHoldStrict(joints: List<PhaseJointConfig>, primaryAngles: Map<String, Double>): Phase {
        val visibleJoints = joints.filter { primaryAngles[it.jointCode] != null }
        if (visibleJoints.isEmpty()) return currentPhase
        fun angleOf(j: PhaseJointConfig): Double = primaryAngles.getValue(j.jointCode)

        return when (currentPhase) {
            Phase.IDLE -> if (visibleJoints.all { jointInHoldRange(it, angleOf(it), exiting = false) }) {
                Phase.COUNT
            } else {
                Phase.IDLE
            }

            Phase.COUNT -> {
                val anyLeft = visibleJoints.any { !jointInHoldRange(it, angleOf(it), exiting = true) }
                if (anyLeft) Phase.IDLE else Phase.COUNT
            }

            else -> currentPhase
        }
    }

    private fun isInUpRange(angle: Double, exiting: Boolean = false): Boolean {
        val min = if (exiting) upRangeMin - hysteresis else upRangeMin
        val max = upRangeMax + hysteresis
        return angle in min..max
    }

    private fun isInDownRange(angle: Double, exiting: Boolean = false): Boolean {
        val min = downRangeMin - hysteresis
        val max = if (exiting) downRangeMax + hysteresis else downRangeMax
        return angle in min..max
    }

    private fun hasLeftUpRange(angle: Double): Boolean = angle < upRangeMin - hysteresis

    private fun hasEnteredDownRange(angle: Double): Boolean = angle <= downRangeMax

    private fun hasLeftDownRange(angle: Double): Boolean = angle > downRangeMax + hysteresis

    private fun hasEnteredUpRange(angle: Double): Boolean = angle >= upRangeMin

    private fun updateUpDownLegacy(minAngle: Double, maxAngle: Double): Phase = when (currentPhase) {
        Phase.IDLE -> if (isInUpRange(minAngle)) Phase.START else Phase.IDLE
        Phase.START -> if (hasLeftUpRange(maxAngle)) {
            repCountedThisCycle = false
            repMovementStartTime = timeProvider()
            Phase.DOWN
        } else {
            Phase.START
        }
        Phase.DOWN -> when {
            hasEnteredDownRange(maxAngle) -> Phase.BOTTOM
            isInUpRange(minAngle) -> Phase.START
            else -> Phase.DOWN
        }
        Phase.BOTTOM -> if (hasLeftDownRange(minAngle)) Phase.UP else Phase.BOTTOM
        Phase.UP -> when {
            hasEnteredUpRange(minAngle) -> Phase.START
            isInDownRange(maxAngle) -> Phase.BOTTOM
            else -> Phase.UP
        }
        else -> currentPhase
    }

    private fun updateHoldLegacy(minAngle: Double, maxAngle: Double): Phase = when (currentPhase) {
        Phase.IDLE -> if (isInDownRange(minAngle) && isInDownRange(maxAngle)) Phase.COUNT else Phase.IDLE
        Phase.COUNT -> if (!isInDownRange(minAngle, exiting = true) || !isInDownRange(maxAngle, exiting = true)) {
            Phase.IDLE
        } else {
            Phase.COUNT
        }
        else -> currentPhase
    }

    private fun handlePhaseTransition(nextPhase: Phase) {
        val now = timeProvider()
        val phaseDuration = if (phaseEntryTime > 0L) now - phaseEntryTime else minPhaseDurationMs

        val isRepCompletionTransition = currentPhase == Phase.UP && nextPhase == Phase.START
        val isPartialDepthAbort = currentPhase == Phase.DOWN && nextPhase == Phase.START
        val isPartialReturnAbort = currentPhase == Phase.UP && nextPhase == Phase.BOTTOM

        if (!isRepCompletionTransition && phaseDuration < minPhaseDurationMs) {
            return
        }

        phaseTimings[currentPhase] = phaseDuration
        previousPhase = currentPhase
        currentPhase = nextPhase
        phaseEntryTime = now

        when {
            isPartialDepthAbort -> {
                if (!repCountedThisCycle) {
                    repMovementStartTime = 0L
                    onRepIncomplete?.invoke(RepIncompleteReason.NO_TARGET_DEPTH)
                }
            }
            isPartialReturnAbort -> {
                if (!repCountedThisCycle) {
                    onRepIncomplete?.invoke(RepIncompleteReason.NO_FULL_RETURN)
                }
            }
            isRepCompletionTransition -> {
                when {
                    repCountedThisCycle -> Unit
                    else -> {
                        val movementMs = if (repMovementStartTime > 0L) {
                            now - repMovementStartTime
                        } else {
                            minRepIntervalMs
                        }
                        when {
                            movementMs < minRepIntervalMs -> {
                                markRepCycleHandled()
                                onRepIncomplete?.invoke(RepIncompleteReason.TOO_FAST)
                            }
                            movementMs > maxRepIntervalMs -> {
                                markRepCycleHandled()
                                onRepIncomplete?.invoke(RepIncompleteReason.TOO_SLOW)
                            }
                            else -> {
                                markRepCycleHandled()
                                onRepCompleted?.invoke()
                            }
                        }
                    }
                }
            }
        }

        onPhaseChanged?.invoke(previousPhase, currentPhase)
    }

    private fun markRepCycleHandled() {
        repCountedThisCycle = true
        repMovementStartTime = 0L
    }

    fun getPhaseTimings(): Map<Phase, Long> = phaseTimings.toMap()

    fun clearTimings() {
        phaseTimings.clear()
    }

    fun reset() {
        previousPhase = currentPhase
        currentPhase = Phase.IDLE
        phaseTimings.clear()
        phaseEntryTime = 0L
        repCountedThisCycle = false
        repMovementStartTime = 0L
    }

    fun wasRepJustCompleted(): Boolean =
        previousPhase == Phase.UP && currentPhase == Phase.START

    fun getZoneInfo(angle: Double): String = when {
        angle > upRangeMax -> "Above UP Range"
        angle >= upRangeMin -> "UP Zone"
        angle > downRangeMax -> "Transition"
        angle >= downRangeMin -> "DOWN Zone"
        else -> "Below DOWN Range"
    }
}

internal expect fun currentTimeMillis(): Long
