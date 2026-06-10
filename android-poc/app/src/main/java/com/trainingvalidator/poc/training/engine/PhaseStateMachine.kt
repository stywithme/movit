package com.trainingvalidator.poc.training.engine

import com.movit.core.training.engine.PhaseStateMachine as KmpPhaseStateMachine
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.engine.policy.TimingPolicy
import com.trainingvalidator.poc.training.engine.policy.fromSettings
import com.trainingvalidator.poc.training.models.CountingMethod
import com.trainingvalidator.poc.training.models.RepCountingConfig
import com.trainingvalidator.poc.training.models.TrackedJoint

/**
 * PhaseStateMachine - Manages exercise phases using STATE-BASED ranges.
 * Delegates to KMP [com.movit.core.training.engine.PhaseStateMachine].
 */
class PhaseStateMachine(
    countingMethod: CountingMethod,
    primaryJoints: List<TrackedJoint>,
    repCountingConfig: RepCountingConfig? = null,
    numberOfPhases: Int = 4,
    timeProvider: () -> Long = { System.currentTimeMillis() },
    phaseHysteresisDegrees: Double = SettingsManager.getHysteresis(),
    timingPolicy: TimingPolicy = fromSettings(),
) {
    private val core = KmpPhaseStateMachine(
        countingMethod = countingMethod.toKmp(),
        primaryJoints = primaryJoints.map { TrackedJointPhaseAdapter(it) },
        timing = buildPhaseTimingConfig(repCountingConfig, numberOfPhases, timingPolicy),
        numberOfPhases = numberOfPhases,
        timeProvider = timeProvider,
        phaseHysteresisDegrees = phaseHysteresisDegrees,
    )

    val currentPhase: Phase
        get() = core.currentPhase.toApp()

    val previousPhase: Phase
        get() = core.previousPhase.toApp()

    var onPhaseChanged: ((Phase, Phase) -> Unit)? = null
        set(value) {
            field = value
            core.onPhaseChanged = value?.let { callback ->
                { previous, current -> callback(previous.toApp(), current.toApp()) }
            }
        }

    var onRepCompleted: (() -> Unit)?
        get() = core.onRepCompleted
        set(value) {
            core.onRepCompleted = value
        }

    var onRepIncomplete: ((RepIncompleteReason) -> Unit)? = null
        set(value) {
            field = value
            core.onRepIncomplete = value?.let { callback ->
                { reason -> callback(reason.toApp()) }
            }
        }

    fun update(primaryAngles: Map<String, Double>): Phase =
        core.update(primaryAngles).toApp()

    fun getPhaseTimings(): Map<Phase, Long> =
        core.getPhaseTimings().mapKeys { it.key.toApp() }

    fun clearTimings() {
        core.clearTimings()
    }

    fun reset() {
        core.reset()
    }

    fun wasRepJustCompleted(): Boolean = core.wasRepJustCompleted()

    fun getZoneInfo(angle: Double): String = core.getZoneInfo(angle)
}

enum class RepIncompleteReason {
    NO_TARGET_DEPTH,
    NO_FULL_RETURN,
    TOO_FAST,
    TOO_SLOW,
}

enum class Phase {
    IDLE,
    START,
    DOWN,
    BOTTOM,
    UP,
    COUNT,
}
