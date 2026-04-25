package com.trainingvalidator.poc.training.engine

import com.trainingvalidator.poc.training.engine.policy.TimingPolicy

/**
 * Session-level safety caps: max reps and max wall-clock duration.
 */
class SessionSafetyGuards(
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
                targetReps + 12
            )
        } else 60
    }

    val maxSessionDurationGuardMs: Long = if (isHoldExercise) {
        maxOf(
            (targetDurationMs ?: 0L) * timingPolicy.holdSessionMaxTargetMultiplier,
            timingPolicy.minSessionDurationFloorMs
        )
    } else {
        maxOf(
            targetReps.coerceAtLeast(1).toLong() * minRepIntervalMs *
                timingPolicy.repSessionMinRepTimeMultiplier,
            timingPolicy.minSessionDurationFloorMs
        )
    }
}
