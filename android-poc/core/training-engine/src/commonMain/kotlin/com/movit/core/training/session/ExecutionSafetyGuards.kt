package com.movit.core.training.session

import com.movit.core.training.engine.policy.TimingPolicy

/**
 * Exercise-run safety caps: max reps and max wall-clock duration for one session.
 * Ported from legacy [com.trainingvalidator.poc.training.engine.ExecutionSafetyGuards].
 */
class ExecutionSafetyGuards(
    timingPolicy: TimingPolicy,
    isHoldExercise: Boolean,
    targetReps: Int,
    targetDurationMs: Long?,
    minRepIntervalMs: Long,
) {
    val maxRepsGuard: Int = if (isHoldExercise) {
        1
    } else {
        if (targetReps > 0) {
            maxOf(
                targetReps * timingPolicy.maxRepsGuardMultiplier,
                targetReps + 12,
            )
        } else {
            60
        }
    }

    val maxExecutionDurationGuardMs: Long = if (isHoldExercise) {
        maxOf(
            (targetDurationMs ?: 0L) * timingPolicy.holdExecutionMaxTargetMultiplier,
            timingPolicy.minExecutionDurationFloorMs,
        )
    } else {
        maxOf(
            targetReps.coerceAtLeast(1).toLong() * minRepIntervalMs *
                timingPolicy.repExecutionMinRepTimeMultiplier,
            timingPolicy.minExecutionDurationFloorMs,
        )
    }
}
