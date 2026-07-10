package com.movit.core.training.session



import com.movit.core.training.bilateral.BilateralConfigInput
import com.movit.core.training.bilateral.completionTargetReps

import com.movit.core.training.bilateral.BilateralController

import com.movit.core.training.bilateral.BilateralSide

import com.movit.core.training.bilateral.BilateralSwitchMode as EngineBilateralSwitchMode

import com.movit.core.training.boundary.AcquirableDeviceTiltPort

import com.movit.core.training.boundary.DeviceTiltPort

import com.movit.core.training.config.BilateralSwitchMode as ConfigBilateralSwitchMode

import com.movit.core.training.config.ExerciseConfig

import com.movit.core.training.config.JointRole

import com.movit.core.training.config.LocalizedText
import com.movit.core.training.config.TrackedJoint
import com.movit.core.training.config.TrackingMode
import com.movit.core.training.engine.JointError
import com.movit.core.training.engine.RepIncompleteReason
import com.movit.core.training.engine.shouldDiscardRepAttemptOnIncomplete

import com.movit.core.training.config.phaseTimingConfig

import com.movit.core.training.config.primaryJointCodes

import com.movit.core.training.config.primaryPhaseJointConfigs

import com.movit.core.training.engine.AngleSmoother

import com.movit.core.training.engine.JointAngleTracker

import com.movit.core.training.engine.JointStateInfo

import com.movit.core.training.engine.Phase

import com.movit.core.training.engine.PhaseStateMachine

import com.movit.core.training.engine.RepCompletionSignal

import com.movit.core.training.engine.RepCounter

import com.movit.core.training.engine.StartPoseGate

import com.movit.core.training.engine.evaluation.JointEvaluator
import com.movit.core.training.observability.PipelineTrace

import com.movit.core.training.engine.feedback.FrameFeedbackEmitter

import com.movit.core.training.engine.feedback.JointErrorCollection

import com.movit.core.training.engine.pipeline.FrameEvaluationPipeline

import com.movit.core.training.engine.pipeline.FramePipelineExecutor

import com.movit.core.training.engine.policy.FeedbackPolicy

import com.movit.core.training.engine.policy.StabilityPolicy

import com.movit.core.training.engine.policy.TimingPolicy

import com.movit.core.training.geometry.JointLandmarkMapping
import com.movit.core.training.geometry.VirtualLandmarks

import com.movit.core.training.model.PoseFrame

import com.movit.core.training.journal.MotionFrameHook
import com.movit.core.training.journal.MotionRepCompletedHook

import com.movit.core.training.report.MovitHoldReportData

import com.movit.core.training.position.PositionError
import com.movit.core.training.position.PositionValidator

import com.movit.core.training.position.SceneAxisWarning

import com.movit.core.training.position.resolveSceneExpectation

import com.movit.core.training.visibility.VisibilityJointConfig

import com.movit.core.training.visibility.VisibilityJointRole

import com.movit.core.training.visibility.VisibilityCheckResult
import com.movit.core.training.visibility.VisibilityMonitor
import com.movit.core.training.visibility.VisibilityTrackingMode



/**

 * I-2 split: [FramePipeline] (smoothing/phase/joint quality) + [SessionRuntime] (lifecycle/reps).

 */

class MovitTrainingEngine(

    private val exerciseConfig: ExerciseConfig,

    val poseVariantIndex: Int = 0,

    targetRepsOverride: Int? = null,

    private val sessionWeightKg: Float? = null,

    private val sessionWeightUnit: String = "kg",

    /** Session/flow override for hold duration (seconds). Wins over config when > 0. */
    targetDurationSecondsOverride: Int? = null,

    private val deviceTiltPort: DeviceTiltPort? = null,

    private val stabilityPolicy: StabilityPolicy = StabilityPolicy.default(),

    private val timingPolicy: TimingPolicy = TimingPolicy.default(),

    wallClock: () -> Long = { com.movit.core.training.engine.currentTimeMillis() },

) {

    private val executionClock = ExecutionClock(wallClock)
    private val nowMs: () -> Long = { executionClock.nowMs() }

    private val poseVariant = exerciseConfig.getPoseVariant(poseVariantIndex)

        ?: error("pose variant $poseVariantIndex missing")

    private val trackedJoints: List<TrackedJoint> = poseVariant.trackedJoints

    private val primaryJointCodes: Set<String> = exerciseConfig.primaryJointCodes(poseVariantIndex)

    private val targetReps: Int = targetRepsOverride ?: exerciseConfig.defaultTargetReps()

    private val bilateralConfigInput = exerciseConfig.bilateralConfig?.toEngineInput()

    private val completionTargetReps: Int = completionTargetReps(
        isBilateral = exerciseConfig.isBilateral,
        config = bilateralConfigInput,
        perSideTargetReps = targetReps,
    )

    private val isHoldExercise: Boolean = exerciseConfig.isHoldExercise()

    private val targetDurationMs: Long? = (
        targetDurationSecondsOverride?.takeIf { it > 0 }
            ?: exerciseConfig.repCountingConfig.duration?.takeIf { it > 0 }
        )?.let { it * 1_000L }

    /** Test/observability: resolved hold target after session overrides. */
    internal fun resolvedTargetDurationMs(): Long? = targetDurationMs



    private val bilateral = BilateralController(

        isBilateral = exerciseConfig.isBilateral,

        config = bilateralConfigInput,

        targetReps = targetReps,

    )



    private val session = SessionOrchestrator(

        timingPolicy = timingPolicy,

        isHoldExercise = isHoldExercise,

        targetReps = completionTargetReps,

        targetDurationMs = targetDurationMs,

        wallClock = wallClock,

        executionClock = executionClock,

    )

    private val frameIngress = FrameIngressGate()

    val pipelineTrace = PipelineTrace()

    private val presenceBridge = PresenceSupervisorBridge(
        thresholds = PresenceThresholds.fromTiming(timingPolicy),
        nowMs = nowMs,
    )

    private val angleSmoother = AngleSmoother(timingPolicy.smoothingWindowSize)

    private val jointTracker = JointAngleTracker(trackedJoints, stabilityPolicy)

    private val jointEvaluator = JointEvaluator(trackedJoints, stabilityPolicy)

    private val frameEvalPipeline = FrameEvaluationPipeline(jointEvaluator)

    private val startPoseGate = StartPoseGate(trackedJoints, stabilityPolicy)

    private val stateMachine = PhaseStateMachine(

        countingMethod = exerciseConfig.countingMethod,

        primaryJoints = exerciseConfig.primaryPhaseJointConfigs(poseVariantIndex),

        timing = exerciseConfig.phaseTimingConfig(),

        phaseHysteresisDegrees = stabilityPolicy.phaseHysteresisDegrees,

        timeProvider = nowMs,

    )

    private val positionValidator: PositionValidator? =

        TrainingGateFactory.buildPositionValidatorForExercise(

            exerciseConfig = exerciseConfig,

            poseVariantIndex = poseVariantIndex,

            tiltSource = deviceTiltPort,

        )

    private val framePipeline = FramePipelineExecutor(

        angleSmoother = angleSmoother,

        startPoseGate = startPoseGate,

        stateMachine = stateMachine,

        positionValidator = positionValidator,

        frameEvalPipeline = frameEvalPipeline,

        primaryJointCodes = primaryJointCodes,

    )

    private val repCompletionSignal = RepCompletionSignal()

    private val repCounter = RepCounter(

        minRepIntervalMs = exerciseConfig.repCountingConfig

            .getMinRepInterval(exerciseConfig.phaseTimingConfig().minRepIntervalMs),

        targetReps = completionTargetReps,

        isHoldExercise = isHoldExercise,

        primaryJoints = primaryJointCodes,

        timeProvider = nowMs,

    )

    private val repCompletion = RepCompletionCoordinator(

        stateMachine = stateMachine,

        repCounter = repCounter,

        repCompletionSignal = repCompletionSignal,

        bilateral = bilateral,

    )

    private val visibilityMonitor = VisibilityMonitor(

        visibilityTrackedJoints = trackedJoints

            .filter { it.role == JointRole.PRIMARY || it.role == JointRole.SECONDARY }

            .map {

                VisibilityJointConfig(

                    joint = it.joint,

                    role = if (it.role == JointRole.PRIMARY) {

                        VisibilityJointRole.PRIMARY

                    } else {

                        VisibilityJointRole.SECONDARY

                    },

                    trackingMode = if (it.trackingMode == TrackingMode.ANY_SIDE) {

                        VisibilityTrackingMode.ANY_SIDE

                    } else {

                        VisibilityTrackingMode.BOTH_SIDES

                    },

                    pairedWith = it.pairedWith,

                )

            },

        minVisibility = timingPolicy.visibilityMinVisibility,

        graceDurationMs = timingPolicy.visibilityGraceDurationMs,

        warningDurationMs = timingPolicy.visibilityWarningDurationMs,

        pauseAfterMs = timingPolicy.visibilityPauseAfterMs,

        timeProvider = nowMs,

    )


    private val frameFeedback = FrameFeedbackEmitter(

        feedbackPolicy = FeedbackPolicy.from(timingPolicy),

        positionChecksById = poseVariant.positionChecks.associateBy { it.id },

        timeProvider = nowMs,

    )

    private val holdExercise = HoldExerciseCoordinator(

        holdTimer = session.holdTimer,

        repCounter = repCounter,

        bilateral = bilateral,

        timeProvider = nowMs,

    )



    var onRepCountChanged: ((Int, Float, Boolean) -> Unit)? = null

    var onTargetReached: (() -> Unit)? = null

    var onPhaseChanged: ((Phase) -> Unit)? = null

    var onHoldStatusChanged: ((HoldStatus?) -> Unit)? = null

    var onPositionIssuesChanged: ((List<PositionError>, List<SceneAxisWarning>) -> Unit)? = null

    var onVisibilityEvent: ((PauseControllerEvent) -> Unit)? = null
    var onVisibilityCheck: ((VisibilityCheckResult) -> Unit)? = null
    var onPresenceEvent: ((PresenceSupervisorEvent) -> Unit)? = null

    var onMotionFrameRecorded: MotionFrameHook? = null
    var onRepCompletedForMotion: MotionRepCompletedHook? = null
    var onJointStateMessage: ((jointCode: String, state: com.movit.core.training.engine.JointState, zone: com.movit.core.training.engine.ZoneType) -> Unit)? = null
    var onJointErrorFeedback: ((JointError, LocalizedText?) -> Unit)? = null
    var onRepIncomplete: ((RepIncompleteReason) -> Unit)? = null



    val repCount: Int get() = repCounter.count

    val currentPhase: Phase get() = stateMachine.currentPhase

    val bilateralSide: BilateralSide get() = bilateral.currentSide

    val isBilateralFlipped: Boolean get() = bilateral.isFlipped

    var holdStatus: HoldStatus? = null

        private set

    var positionErrors: List<PositionError> = emptyList()
        private set

    var lastAnySideDimmedJointCodes: Set<String> = emptySet()
        private set

    var sceneWarnings: List<SceneAxisWarning> = emptyList()
        private set



    private var isRunning = false

    private var isPaused = false

    private var executionStartMs: Long = 0L

    private var lastJointStateInfos: Map<String, JointStateInfo> = emptyMap()

    private var lastSmoothedAngles: Map<String, Double> = emptyMap()

    private var lastInStartPosition = false



    init {

        bilateral.onSideChanged = {
            angleSmoother.reset()
            jointEvaluator.reset()
        }

        stateMachine.onPhaseChanged = { _, current -> onPhaseChanged?.invoke(current) }

        stateMachine.onRepCompleted = { repCompletion.onPhaseMachineWantsComplete() }

        stateMachine.onRepIncomplete = { reason ->
            if (shouldDiscardRepAttemptOnIncomplete(reason)) {
                repCounter.discardCurrentRepAttempt(reason)
                stateMachine.clearTimings()
            }
            onRepIncomplete?.invoke(reason)
        }

        repCounter.onRepCountChanged = { count, score, isCounted ->

            session.updateRepCount(count)

            onRepCountChanged?.invoke(count, score, isCounted)

            repCounter.getLastRepResult()?.let { result ->
                onRepCompletedForMotion?.invoke(result, result.phaseTimings, bilateral.currentSide)
            }

        }

        repCounter.onTargetReached = {

            session.markCompleted()

            onTargetReached?.invoke()

        }

        holdExercise.installCallbacks(

            isHoldExercise = isHoldExercise,

            publishStatus = { status ->

                holdStatus = status

                onHoldStatusChanged?.invoke(status)

            },

            onHoldCompleted = {

                session.markCompleted()

                onTargetReached?.invoke()

            },

        )

    }



    fun start() {

        isRunning = true

        isPaused = false

        executionStartMs = 0L

        angleSmoother.reset()

        stateMachine.reset()

        repCounter.reset()

        jointEvaluator.reset()

        repCompletion.clear()

        bilateral.resetToConfigStart()

        visibilityMonitor.reset()

        frameIngress.reset()

        presenceBridge.reset()

        pipelineTrace.clear()

        positionValidator?.clearCooldowns()

        positionValidator?.unlockScene()

        frameFeedback.clearPositionCooldowns()

        session.start()

        lastJointStateInfos = emptyMap()

        lastInStartPosition = false

        holdStatus = null

        positionErrors = emptyList()

        sceneWarnings = emptyList()

        (deviceTiltPort as? AcquirableDeviceTiltPort)?.acquire(TILT_OWNER)

    }

    /** After [start], seed live rep count from a restored journal (P1.5). */
    fun seedCompletedRepCount(completedReps: Int) {
        if (completedReps <= 0) return
        repCounter.seedCompletedCount(completedReps)
        onRepCountChanged?.invoke(repCounter.count, repCounter.getAverageScore(), true)
    }

    fun pause() {
        isPaused = true
        session.pause()
    }

    fun resume() {
        isPaused = false
        session.resume()
        session.pauseController.onUserOrSupervisorResume(visibilityMonitor)
    }

    fun stop(): ExerciseWorkoutSummary {
        isRunning = false
        isPaused = false
        (deviceTiltPort as? AcquirableDeviceTiltPort)?.release(TILT_OWNER)
        val duration = session.stop()
        return ExerciseWorkoutSummaryBuilder.build(
            config = exerciseConfig,
            repCounter = repCounter,
            durationMs = duration,
            weightKg = sessionWeightKg,
            weightUnit = sessionWeightUnit,
            poseVariantIndex = poseVariantIndex,
        )
    }

    fun snapshotHoldReportData(): MovitHoldReportData? {
        if (!isHoldExercise) return null
        val status = holdStatus ?: return null
        val timer = session.holdTimer ?: return null
        return MovitHoldReportData(
            targetMs = timer.getTargetDurationMs(),
            achievedMs = status.elapsedMs,
            formQuality = status.formQuality,
            gracePeriodsUsed = timer.gracePeriodCount,
            jointErrorMap = status.jointErrorMap,
        )
    }



    fun processFrame(frame: PoseFrame) {
        if (!isRunning || isPaused) return

        if (!frame.hasPose) {
            presenceBridge.onNoPoseFrame(frame.timestampMs)?.let { emitPresence(it) }
            return
        }
        presenceBridge.onPoseRestored()?.let { emitPresence(it) }

        if (!frameIngress.tryAcquire()) {
            pipelineTrace.record("drop:ingress_busy")
            return
        }
        try {
            processPoseFrame(frame)
        } finally {
            frameIngress.release()
        }
    }

    fun droppedFrameCount(): Int = frameIngress.droppedFrameCount

    internal fun testPauseController(): PauseController = session.pauseController

    private fun processPoseFrame(frame: PoseFrame) {
        if (!session.shouldProcessFrame()) return

        session.onFrameClock(frame)

        if (executionStartMs == 0L) executionStartMs = frame.timestampMs.takeIf { it > 0 } ?: nowMs()



        val workingFrame = if (frame.isFrontCamera) frame.mirrored() else frame

        val angleExtract = jointTracker.extractTrackedAngles(

            angles = workingFrame.angles,

            isFlipped = bilateral.isFlipped,

            landmarks = workingFrame.landmarks,

            isFrontCamera = frame.isFrontCamera,

        )

        lastAnySideDimmedJointCodes = angleExtract.skippedJointCodes

        val visibilities = buildJointVisibilities(workingFrame.landmarks, frame.isFrontCamera)

        val visibilityDetails = visibilityMonitor.evaluateJointVisibility(visibilities)

        val allJointsVisible = visibilityDetails.isEmpty() || visibilityDetails.all { it.isVisible }

        val visibilityResult = visibilityMonitor.checkVisibility(
            jointVisibilities = visibilities,
            currentRepCount = repCounter.count,
            currentPhase = stateMachine.currentPhase,
        )
        onVisibilityCheck?.invoke(visibilityResult)
        presenceBridge.mapVisibilityCheck(visibilityResult)?.let { emitPresence(it) }
        val skipCounting = session.pauseController.processVisibilityResult(
            result = visibilityResult,
            emit = { event ->
                onVisibilityEvent?.invoke(event)
                emitPresence(presenceBridge.mapVisibilityEvent(event))
            },
            onAutoResumeComplete = {
                session.pauseController.clearAfterAutoResume(visibilityMonitor)
                val resumed = PauseControllerEvent.VisibilityResumed(repCounter.count)
                onVisibilityEvent?.invoke(resumed)
                emitPresence(presenceBridge.mapVisibilityEvent(resumed))
            },
        )
        if (skipCounting) return

        val pipelineResult = framePipeline.runMainPath(

            rawTrackedAngles = angleExtract.angles,

            skippedForFrame = angleExtract.skippedJointCodes,

            landmarks = workingFrame.landmarks,

            isBilateralFlipped = bilateral.isFlipped,

            isFrontCamera = frame.isFrontCamera,

            allJointsVisible = allJointsVisible,

        )



        val lastPhase = currentPhase
        session.updatePhase(pipelineResult.currentPhase)
        if (pipelineResult.currentPhase != lastPhase) {
            pipelineTrace.record("phase:$lastPhase->${pipelineResult.currentPhase}")
        }

        lastInStartPosition = pipelineResult.inStartPosition



        pipelineResult.positionResult?.let { pv ->

            positionErrors = pv.errors + pv.warnings + pv.tips

            sceneWarnings = if (allJointsVisible) emptyList() else pv.sceneWarnings

            onPositionIssuesChanged?.invoke(positionErrors, sceneWarnings)

        }



        val frameResult = pipelineResult.frameJoint

        val currentPhase = pipelineResult.currentPhase

        lastJointStateInfos = frameResult.jointStateInfos

        frameFeedback.emitThrottledStateMessages(frameResult.jointStateInfos) { jointCode, state ->
            val zone = frameResult.jointStateInfos[jointCode]?.currentZone
                ?: com.movit.core.training.engine.ZoneType.TRANSITION
            onJointStateMessage?.invoke(jointCode, state, zone)
        }

        val shouldTrackState = if (isHoldExercise) {

            currentPhase == Phase.COUNT

        } else {

            currentPhase != Phase.IDLE && currentPhase != Phase.START

        }



        if (shouldTrackState) {

            val scoringEvals = frameResult.forScoring(angleExtract.skippedJointCodes)

            repCounter.updateJointEvals(scoringEvals)

            onMotionFrameRecorded?.invoke(
                frame.timestampMs.takeIf { it > 0 } ?: nowMs(),
                currentPhase,
                pipelineResult.smoothedAngles,
                frameResult.jointStateInfos,
                angleExtract.skippedJointCodes,
            )

            val jointErrors = JointErrorCollection.collectJointErrors(frameResult.jointEvals)
            for (error in jointErrors) {
                repCounter.addError(error)
                if (frameFeedback.shouldEmitJointError(error)) {
                    val message = frameResult.jointEvals[error.jointCode]?.messages?.firstOrNull()
                    onJointErrorFeedback?.invoke(error, message)
                }
            }

            pipelineResult.positionResult?.let { pv ->
                pv.errors.forEach { repCounter.addPositionError(it) }
                pv.warnings.forEach { repCounter.addPositionWarning(it.checkId) }
                pv.tips.forEach { repCounter.addPositionTip(it.checkId) }
            }

        }



        if (isHoldExercise) {

            val isInHoldZone = currentPhase == Phase.COUNT

            holdExercise.updateHoldTimer(isInHoldZone) { status ->

                holdStatus = status

                onHoldStatusChanged?.invoke(status)

            }

        } else {

            repCompletion.consumeIfPendingAndHandle()

        }

    }



    fun metricsSnapshot(): EngineMetrics = EngineMetrics(

        repCount = repCounter.count,

        targetReps = completionTargetReps,

        liveFormScore = repCounter.getPendingScore(),

        averageFormScore = repCounter.getAverageScore(),

        phase = stateMachine.currentPhase,

        isTargetReached = repCounter.isTargetReached(),

        isInStartPosition = lastInStartPosition,

        jointStateInfos = lastJointStateInfos,

        holdStatus = holdStatus,

        positionErrors = positionErrors,

        anySideDimmedJointCodes = lastAnySideDimmedJointCodes,

        bilateralSide = if (exerciseConfig.isBilateral) bilateral.currentSide else null,

        isBilateralFlipped = bilateral.isFlipped,

        isBilateralExercise = exerciseConfig.isBilateral,

        positionErrorCount = positionErrors.size,

    )



    private fun buildJointVisibilities(

        landmarks: List<com.movit.core.training.model.Landmark>?,

        isFrontCamera: Boolean,

    ): Map<String, Float> {

        if (landmarks == null) return emptyMap()

        val resolved = VirtualLandmarks.ensureAppended(landmarks)

        return trackedJoints.associate { joint ->

            joint.joint to JointLandmarkMapping.computeJointVisibility(

                joint.joint,

                resolved,

                isFrontCamera,

            )

        }

    }



    private fun emitPresence(event: PresenceSupervisorEvent) {
        onPresenceEvent?.invoke(event)
    }

    private fun com.movit.core.training.config.BilateralConfig.toEngineInput(): BilateralConfigInput =

        BilateralConfigInput(

            switchMode = when (switchMode) {

                ConfigBilateralSwitchMode.EVERY_REP -> EngineBilateralSwitchMode.EVERY_REP

                ConfigBilateralSwitchMode.AFTER_ALL_REPS -> EngineBilateralSwitchMode.AFTER_ALL_REPS

                null -> null

            },

            switchEvery = switchEvery,

            startSide = startSide,

        )



    data class EngineMetrics(

        val repCount: Int,

        val targetReps: Int,

        val liveFormScore: Float,

        val averageFormScore: Float,

        val phase: Phase,

        val isTargetReached: Boolean,

        val isInStartPosition: Boolean,

        val jointStateInfos: Map<String, JointStateInfo>,

        val holdStatus: HoldStatus? = null,

        val positionErrors: List<PositionError> = emptyList(),

        val anySideDimmedJointCodes: Set<String> = emptySet(),

        val bilateralSide: BilateralSide? = null,

        val isBilateralFlipped: Boolean = false,

        val isBilateralExercise: Boolean = false,

        val positionErrorCount: Int = 0,

    )



    companion object {

        private const val TILT_OWNER = "MovitTrainingEngine"

    }

}

