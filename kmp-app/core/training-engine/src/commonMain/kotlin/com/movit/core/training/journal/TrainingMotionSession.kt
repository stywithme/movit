package com.movit.core.training.journal

import com.movit.core.training.bilateral.BilateralSide
import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.config.JointRole
import com.movit.core.training.engine.JointStateInfo
import com.movit.core.training.engine.Phase
import com.movit.core.training.engine.RepResult
import com.movit.core.training.report.SessionQualityMeta
import com.movit.core.training.session.MovitTrainingEngine

/**
 * Bridges [MovitTrainingEngine] frame/rep events to [MotionRecorder] + journal checkpoints.
 */
class TrainingMotionSession(
    exerciseConfig: ExerciseConfig,
    exerciseSlug: String,
    poseVariantIndex: Int = 0,
    defaultWeightKg: Float? = null,
    weightUnit: String = "kg",
    private val sessionId: String,
    private val isAssessmentMode: Boolean = false,
    private val timeProvider: () -> Long = { 0L },
    private val onCheckpoint: ((SessionJournalSnapshot) -> Unit)? = null,
) {
    private val trackedJointCodes: List<String> = exerciseConfig
        .getPoseVariant(poseVariantIndex)
        ?.trackedJoints
        ?.map { it.joint }
        ?: emptyList()

    private val primaryJointIndices: List<Int> = exerciseConfig
        .getPoseVariant(poseVariantIndex)
        ?.trackedJoints
        ?.mapIndexedNotNull { index, joint ->
            index.takeIf { joint.role == JointRole.PRIMARY }
        }
        ?: emptyList()

    private val recorder = MotionRecorder(
        trackedJoints = trackedJointCodes,
        primaryJointIndices = primaryJointIndices.ifEmpty { listOf(0) },
        exerciseId = exerciseSlug,
        defaultWeightKg = defaultWeightKg,
        weightUnit = weightUnit,
        timeProvider = timeProvider,
    )

    private var attachedEngine: MovitTrainingEngine? = null

    fun attach(engine: MovitTrainingEngine) {
        attachedEngine = engine
        engine.onMotionFrameRecorded = { timestampMs, phase, angles, states, skipped ->
            recorder.record(
                timestamp = timestampMs,
                phase = phase,
                angles = angles,
                states = states,
                skippedJointCodes = skipped,
            )
        }
        engine.onRepCompletedForMotion = { result, phaseTimings, bilateralSide ->
            recorder.finalizeRep(
                repNumber = result.repNumber,
                phaseTimings = phaseTimings,
                worstState = result.worstState,
                score = result.score,
                side = bilateralSide?.wireName(),
            )
            checkpoint()
        }
    }

    fun detach() {
        attachedEngine?.onMotionFrameRecorded = null
        attachedEngine?.onRepCompletedForMotion = null
        attachedEngine = null
    }

    fun start(startTimestampMs: Long = timeProvider()) {
        recorder.start(startTimestampMs)
        checkpoint()
    }

    fun restore(snapshot: SessionJournalSnapshot) {
        recorder.restore(snapshot)
    }

    fun finalize(workoutId: String? = null, endTimestampMs: Long = timeProvider()): WorkoutUpload =
        recorder.finalize(workoutId, endTimestampMs)

    fun cancel() {
        recorder.cancel()
        detach()
    }

    fun snapshot(): SessionJournalSnapshot = recorder.snapshot(sessionId, isAssessmentMode)

    fun checkpoint() {
        onCheckpoint?.invoke(snapshot())
    }

    fun isActive(): Boolean = recorder.isActive()

    fun completedRepCount(): Int = recorder.completedRepCount()

    fun discardCurrentRepAttempt() {
        recorder.discardCurrentRepAttempt()
    }

    fun sessionQualityMeta(
        visibilityPauseCount: Int = 0,
        cameraWarningCount: Int = 0,
    ): SessionQualityMeta = recorder.sessionQualityMeta(
        visibilityPauseCount = visibilityPauseCount,
        cameraWarningCount = cameraWarningCount,
    )

    private fun BilateralSide?.wireName(): String? = when (this) {
        BilateralSide.LEFT -> "left"
        BilateralSide.RIGHT -> "right"
        null -> null
    }
}

typealias MotionFrameHook = (
    timestampMs: Long,
    phase: Phase,
    angles: Map<String, Double>,
    states: Map<String, JointStateInfo>,
    skippedJointCodes: Set<String>,
) -> Unit

typealias MotionRepCompletedHook = (
    result: RepResult,
    phaseTimings: Map<String, Long>,
    bilateralSide: BilateralSide?,
) -> Unit
