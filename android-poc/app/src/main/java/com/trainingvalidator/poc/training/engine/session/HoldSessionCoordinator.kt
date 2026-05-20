package com.trainingvalidator.poc.training.engine.session

import android.util.Log
import com.trainingvalidator.poc.training.analytics.MotionRecorder
import com.trainingvalidator.poc.training.engine.HoldStatus
import com.trainingvalidator.poc.training.engine.HoldTimer
import com.trainingvalidator.poc.training.engine.RepCounter
import com.trainingvalidator.poc.training.engine.bilateral.BilateralController
import com.trainingvalidator.poc.training.engine.observability.PipelineTrace
import com.trainingvalidator.poc.training.feedback.FeedbackEvent

/**
 * Hold FSM, session form counters, and [HoldStatus] mirroring. Extracted from
 * [com.trainingvalidator.poc.training.TrainingEngine].
 */
class HoldSessionCoordinator(
    private val tag: String,
    private val holdTimer: HoldTimer?,
    private val repCounter: RepCounter,
    private val getTargetDurationMs: () -> Long,
    private val timeProvider: () -> Long,
    private val motionRecorder: () -> MotionRecorder?,
    private val bilateral: BilateralController,
    private val pipelineTrace: PipelineTrace
) {
    var sessionFormQuality: Float = 1f
        private set
    var sessionErrorCount: Int = 0
        private set
    var sessionJointErrorMap: Map<String, Int> = emptyMap()
        private set

    fun updateHoldTimer(isInHoldZone: Boolean, publishStatus: (HoldStatus?) -> Unit) {
        val timer = holdTimer ?: return
        timer.update(isInHoldZone, timeProvider())
        refreshStatus(publishStatus)
    }

    fun resetTracking(publishStatus: (HoldStatus?) -> Unit) {
        sessionFormQuality = 1f
        sessionErrorCount = 0
        sessionJointErrorMap = emptyMap()
        refreshStatus(publishStatus)
        Log.d(tag, "Hold form tracking reset")
    }

    fun installCallbacks(
        isHoldExercise: Boolean,
        onEmit: (FeedbackEvent) -> Unit,
        publishStatus: (HoldStatus?) -> Unit,
        setSessionCompleted: (Boolean) -> Unit
    ) {
        if (!isHoldExercise || holdTimer == null) return
        val timer = holdTimer
        timer.onStateChanged = { oldState, newState ->
            Log.d(tag, "Hold state: $oldState → $newState")
        }
        timer.onHoldStarted = {
            resetTracking(publishStatus)
            onEmit(FeedbackEvent.HoldStarted())
            Log.d(tag, "Hold started!")
        }
        timer.onGraceStarted = { elapsedMs, gracePeriodMs ->
            onEmit(
                FeedbackEvent.HoldGraceStarted(
                    gracePeriodMs = gracePeriodMs,
                    elapsedBeforeGraceMs = elapsedMs
                )
            )
            Log.d(tag, "Grace period started (elapsed: ${elapsedMs}ms)")
        }
        timer.onGraceResumed = { elapsedMs, gracePeriodsUsed ->
            onEmit(
                FeedbackEvent.HoldResumed(
                    elapsedMs = elapsedMs,
                    gracePeriodsUsed = gracePeriodsUsed
                )
            )
            Log.d(tag, "Resumed from grace (elapsed: ${elapsedMs}ms, graceCount: $gracePeriodsUsed)")
        }
        timer.onCompleted = { totalMs, gracePeriodsUsed ->
            val previousCount = repCounter.count
            repCounter.completeRep()
            val finalResult = repCounter.getLastRepResult()
            val score = finalResult?.score ?: 0f
            val formQuality = score / 100f
            sessionFormQuality = formQuality
            sessionErrorCount = finalResult?.getTotalErrorCount() ?: 0
            sessionJointErrorMap = emptyMap()
            refreshStatus(publishStatus)

            if (repCounter.count > previousCount && finalResult != null) {
                pipelineTrace.record(
                    "hold complete n=${repCounter.count} score=$score worst=${finalResult.worstState}"
                )
                motionRecorder()?.finalizeRep(
                    repNumber = repCounter.count,
                    phaseTimings = finalResult.phaseTimings,
                    worstState = finalResult.worstState,
                    score = score,
                    side = bilateral.currentSideCode.takeIf { bilateral.isBilateral }
                )
                bilateral.onRepCounted(repCounter.count)
            }

            setSessionCompleted(true)
            onEmit(
                FeedbackEvent.HoldCompleted(
                    totalMs = totalMs,
                    targetMs = getTargetDurationMs(),
                    formQuality = formQuality,
                    gracePeriodsUsed = gracePeriodsUsed
                )
            )
            Log.d(tag, "★ Hold COMPLETED! (totalMs: $totalMs, formQuality: $formQuality, score: $score)")
        }
        timer.onFailed = { elapsedMs, gracePeriodsUsed ->
            onEmit(
                FeedbackEvent.HoldFailed(
                    elapsedBeforeFailMs = elapsedMs,
                    targetMs = getTargetDurationMs(),
                    gracePeriodCount = gracePeriodsUsed
                )
            )
            Log.d(tag, "✗ Hold FAILED! (elapsedMs: $elapsedMs)")
            timer.reset()
            resetTracking(publishStatus)
        }
    }

    private fun refreshStatus(publish: (HoldStatus?) -> Unit) {
        val t = holdTimer ?: run {
            publish(null)
            return
        }
        publish(
            HoldStatus(
                state = t.state.value,
                elapsedMs = t.elapsedMs.value,
                remainingMs = t.getRemainingMs(),
                progress = t.getProgress(),
                graceRemainingMs = t.graceRemainingMs.value,
                formQuality = sessionFormQuality,
                errorCount = sessionErrorCount,
                jointErrorMap = sessionJointErrorMap
            )
        )
    }
}
