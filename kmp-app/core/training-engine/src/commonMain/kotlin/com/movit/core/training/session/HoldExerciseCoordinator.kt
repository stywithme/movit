package com.movit.core.training.session

import com.movit.core.training.bilateral.BilateralController
import com.movit.core.training.engine.RepCounter
import com.movit.core.training.engine.currentTimeMillis

/**
 * Hold FSM + form counters + [HoldStatus] mirroring (extracted from legacy TrainingEngine).
 */
class HoldExerciseCoordinator(
    private val holdTimer: HoldTimer?,
    private val repCounter: RepCounter,
    private val bilateral: BilateralController,
    private val timeProvider: () -> Long = { currentTimeMillis() },
) {
    var exerciseFormQuality: Float = 1f
        private set
    var exerciseErrorCount: Int = 0
        private set
    var exerciseJointErrorMap: Map<String, Int> = emptyMap()
        private set

    fun updateHoldTimer(isInHoldZone: Boolean, publishStatus: (HoldStatus?) -> Unit) {
        val timer = holdTimer ?: return
        timer.update(isInHoldZone, timeProvider())
        refreshStatus(publishStatus)
    }

    fun resetTracking(publishStatus: (HoldStatus?) -> Unit) {
        exerciseFormQuality = 1f
        exerciseErrorCount = 0
        exerciseJointErrorMap = emptyMap()
        refreshStatus(publishStatus)
    }

    fun installCallbacks(
        isHoldExercise: Boolean,
        publishStatus: (HoldStatus?) -> Unit,
        onHoldCompleted: () -> Unit,
    ) {
        if (!isHoldExercise || holdTimer == null) return
        val timer = holdTimer
        timer.onHoldStarted = { resetTracking(publishStatus) }
        timer.onCompleted = { _, _ ->
            val previousCount = repCounter.count
            repCounter.completeRep()
            val finalResult = repCounter.getLastRepResult()
            val score = finalResult?.score ?: 0f
            exerciseFormQuality = score / 100f
            exerciseErrorCount = finalResult?.errors?.size ?: 0
            exerciseJointErrorMap = emptyMap()
            refreshStatus(publishStatus)
            if (repCounter.count > previousCount && finalResult != null) {
                bilateral.onRepCounted(repCounter.count)
            }
            onHoldCompleted()
        }
        timer.onFailed = { _, _ ->
            timer.reset()
            resetTracking(publishStatus)
        }
        timer.onGraceStarted = { _, _ -> refreshStatus(publishStatus) }
        timer.onGraceResumed = { _, _ -> refreshStatus(publishStatus) }
        timer.onStateChanged = { _, _ -> refreshStatus(publishStatus) }
    }

    private fun refreshStatus(publish: (HoldStatus?) -> Unit) {
        val timer = holdTimer ?: run {
            publish(null)
            return
        }
        publish(
            HoldStatus(
                state = timer.state,
                elapsedMs = timer.elapsedMs,
                remainingMs = timer.getRemainingMs(),
                progress = timer.getProgress(),
                graceRemainingMs = timer.graceRemainingMs,
                formQuality = exerciseFormQuality,
                errorCount = exerciseErrorCount,
                jointErrorMap = exerciseJointErrorMap,
            ),
        )
    }
}
