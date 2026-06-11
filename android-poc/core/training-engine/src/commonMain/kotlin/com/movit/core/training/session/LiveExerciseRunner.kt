package com.movit.core.training.session

import com.movit.core.training.blueprint.ExerciseBlueprint
import com.movit.core.training.engine.Phase
import com.movit.core.training.engine.PhaseStateMachine
import com.movit.core.training.engine.RepCounter
import com.movit.core.training.model.PoseFrame

/**
 * Wires [SessionOrchestrator], [PhaseStateMachine], and [RepCounter] for one live exercise.
 * Frame evaluation is intentionally slimmer than legacy [com.trainingvalidator.poc.training.TrainingEngine].
 */
class LiveExerciseRunner(
    private val blueprint: ExerciseBlueprint,
    targetReps: Int = blueprint.defaultTargetReps,
    private val timeProvider: () -> Long = { com.movit.core.training.engine.currentTimeMillis() },
) {
    data class Metrics(
        val repCount: Int,
        val targetReps: Int,
        val liveFormScore: Float,
        val averageFormScore: Float,
        val phase: Phase,
        val isTargetReached: Boolean,
    )

    private val session = SessionOrchestrator(
        isHoldExercise = blueprint.countingMethod == com.movit.core.training.engine.CountingMethod.HOLD,
        targetReps = targetReps,
    )

    private val phaseMachine = PhaseStateMachine(
        countingMethod = blueprint.countingMethod,
        primaryJoints = blueprint.phaseJointConfigs(),
        timing = blueprint.timingConfig(),
        timeProvider = timeProvider,
    )

    private val repCounter = RepCounter(
        minRepIntervalMs = blueprint.minRepIntervalMs,
        targetReps = targetReps,
        isHoldExercise = blueprint.countingMethod == com.movit.core.training.engine.CountingMethod.HOLD,
        primaryJoints = blueprint.primaryJointCodes,
        timeProvider = timeProvider,
    )

    var onMetrics: ((Metrics) -> Unit)? = null
    var onTargetReached: (() -> Unit)? = null

    init {
        phaseMachine.onRepCompleted = {
            repCounter.setPhaseTimings(phaseMachine.getPhaseTimings())
            repCounter.completeRep()
            session.updateRepCount(repCounter.count)
            emitMetrics()
        }
        repCounter.onRepCountChanged = { _, _, _ -> emitMetrics() }
        repCounter.onTargetReached = {
            session.markCompleted()
            onTargetReached?.invoke()
            emitMetrics()
        }
    }

    fun start() {
        session.start()
        phaseMachine.reset()
        repCounter.reset()
        emitMetrics()
    }

    fun stop(): Long = session.stop()

    fun processFrame(frame: PoseFrame) {
        if (!frame.hasPose || !session.shouldProcessFrame()) return
        session.onFrameClock(frame)

        val primaryAngles = buildMap {
            for (code in blueprint.primaryJointCodes) {
                val angle = frame.angles.getAngle(code) ?: return
                put(code, angle)
            }
        }

        val phase = phaseMachine.update(primaryAngles)
        session.updatePhase(phase)

        val evals = blueprint.evaluateJoints(primaryAngles, phase)
        repCounter.updateJointEvals(evals)
        emitMetrics()
    }

    private fun emitMetrics() {
        onMetrics?.invoke(
            Metrics(
                repCount = repCounter.count,
                targetReps = repCounter.getRemainingReps() + repCounter.count,
                liveFormScore = repCounter.getPendingScore(),
                averageFormScore = repCounter.getAverageScore(),
                phase = phaseMachine.currentPhase,
                isTargetReached = repCounter.isTargetReached(),
            ),
        )
    }
}
