package com.movit.core.training.bilateral

/**
 * Session completion target for bilateral "after all reps" mode.
 *
 * [BilateralController] keeps [perSideTargetReps] for side switching; [com.movit.core.training.engine.RepCounter]
 * and [com.movit.core.training.session.SessionOrchestrator] use the total returned here.
 */
fun isAfterAllRepsBilateral(
    isBilateral: Boolean,
    config: BilateralConfigInput?,
    perSideTargetReps: Int,
): Boolean =
    isBilateral && config?.let { cfg ->
        cfg.switchMode == BilateralSwitchMode.AFTER_ALL_REPS ||
            (cfg.switchMode == null && cfg.switchEvery == perSideTargetReps)
    } == true

fun completionTargetReps(
    isBilateral: Boolean,
    config: BilateralConfigInput?,
    perSideTargetReps: Int,
): Int =
    if (isAfterAllRepsBilateral(isBilateral, config, perSideTargetReps)) {
        perSideTargetReps * 2
    } else {
        perSideTargetReps
    }
