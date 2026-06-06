package com.trainingvalidator.poc.training

import android.util.Log
import android.os.SystemClock
import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.training.analytics.MotionRecorder
import com.trainingvalidator.poc.training.engine.*
import com.trainingvalidator.poc.training.engine.bilateral.BilateralController
import com.trainingvalidator.poc.training.engine.bilateral.BilateralSide
import com.trainingvalidator.poc.training.engine.evaluation.JointEvaluator
import com.trainingvalidator.poc.training.engine.evaluation.hasAnyDangerState
import com.trainingvalidator.poc.training.engine.feedback.FrameFeedbackEmitter
import com.trainingvalidator.poc.training.engine.observability.PipelineTrace
import com.trainingvalidator.poc.training.engine.session.HoldExerciseCoordinator
import com.trainingvalidator.poc.training.engine.session.RepCompletionCoordinator
import com.trainingvalidator.poc.training.engine.session.ExerciseWorkoutSummaryBuilder
import com.trainingvalidator.poc.training.engine.pipeline.FrameEvaluationPipeline
import com.trainingvalidator.poc.training.engine.pipeline.FrameInput
import com.trainingvalidator.poc.training.engine.pipeline.FramePipelineExecutor
import com.trainingvalidator.poc.training.engine.policy.FeedbackPolicy
import com.trainingvalidator.poc.training.engine.policy.StabilityPolicy
import com.trainingvalidator.poc.training.engine.policy.TimingPolicy
import com.trainingvalidator.poc.training.feedback.FeedbackEvent
import com.trainingvalidator.poc.training.feedback.FeedbackPriority
import com.trainingvalidator.poc.training.feedback.JointQualityContent
import com.trainingvalidator.poc.training.models.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Workout execution facade for one exercise run.
 *
 * Milestones owned here:
 * - lifecycle/time/locks and public StateFlows
 * - frame entry: extract + visibility, then [FramePipelineExecutor]
 * - side effects: UI flows, recorder, feedback, rep/hold completion, safety guards
 *
 * Joint quality remains state-based ([JointState]); scoring/summary live in the lower-level coordinators.
 */
class TrainingEngine(
    private val exerciseConfig: ExerciseConfig,
    val poseVariantIndex: Int = 0,
    private val targetRepsOverride: Int? = null,
    private val targetDurationMsOverride: Long? = null,
    private val stabilityPolicy: StabilityPolicy = StabilityPolicy.default(),
    private val timingPolicy: TimingPolicy = TimingPolicy.default(),
    private val tiltSource: AcquirableTiltSource? = null
) {
    
    companion object {
        private const val TAG = "TrainingEngine"
    }
    
    private val cameraWarningEventCooldownMs: Long = timingPolicy.cameraWarningEventCooldownMs

    private val pauseController: PauseController = PauseController.fromTiming(timingPolicy) { nowMs() }
    private val feedbackPolicy: FeedbackPolicy = FeedbackPolicy.from(timingPolicy)
    
    val pipelineTrace: PipelineTrace = PipelineTrace()
    
    // Milestone: config and active variant.
    
    private val poseVariant: PoseVariant = exerciseConfig.poseVariants[poseVariantIndex]
    private val repCountingConfig: RepCountingConfig = exerciseConfig.repCountingConfig
    private val targetReps: Int = targetRepsOverride
        ?: repCountingConfig.reps
    private val isAfterAllRepsBilateral: Boolean =
        exerciseConfig.isBilateral && exerciseConfig.bilateralConfig?.let { config ->
            config.switchMode == BilateralSwitchMode.AFTER_ALL_REPS ||
                (config.switchMode == null && config.switchEvery == targetReps)
        } == true
    // "After all reps" means completing the configured target on both sides.
    private val completionTargetReps: Int =
        if (isAfterAllRepsBilateral) targetReps * 2 else targetReps

    private val bilateral = BilateralController(
        isBilateral = exerciseConfig.isBilateral,
        config = exerciseConfig.bilateralConfig,
        targetReps = targetReps
    )
    val bilateralSide: StateFlow<BilateralSide> = bilateral.side

    private val minRepIntervalMs: Long = timingPolicy.minRepIntervalFor(repCountingConfig)
    
    private val trackedJoints: List<TrackedJoint> = poseVariant.trackedJoints
    private val trackedJointsByCode: Map<String, TrackedJoint> = trackedJoints.associateBy { it.joint }
    private val primaryJoints: List<TrackedJoint> = poseVariant.getPrimaryJoints()
    
    private val primaryJointCodes: Set<String> = primaryJoints.map { it.joint }.toSet()
    
    val isBilateralFlipped: Boolean get() = bilateral.isFlipped
    
    val feedbackMessages: FeedbackMessages
        get() = poseVariant.feedbackMessages
    
    // Milestone: frame components and exercise-run coordinators.
    
    private val jointTracker = JointAngleTracker(trackedJoints, stabilityPolicy)
    
    var motionRecorder: MotionRecorder? = null
    
    private val angleSmoother = AngleSmoother(timingPolicy.smoothingWindowSize)
    
    private val stateMachine = PhaseStateMachine(
        countingMethod = exerciseConfig.countingMethod,
        primaryJoints = primaryJoints,
        repCountingConfig = repCountingConfig,
        numberOfPhases = 4,
        timeProvider = { nowMs() },
        phaseHysteresisDegrees = stabilityPolicy.phaseHysteresisDegrees,
        timingPolicy = timingPolicy
    )
    
    private val jointEvaluator = JointEvaluator(trackedJoints, stabilityPolicy)
    private val frameEvalPipeline = FrameEvaluationPipeline(jointEvaluator)
    private val startPoseGate = StartPoseGate(trackedJoints, stabilityPolicy)
    private val repCompletionSignal = RepCompletionSignal()
    
    private val repCounter = RepCounter(
        minRepIntervalMs = minRepIntervalMs,
        targetReps = completionTargetReps,
        isHoldExercise = exerciseConfig.countingMethod == CountingMethod.HOLD,
        primaryJoints = primaryJointCodes,
        timeProvider = { nowMs() }
    )

    private val configuredPositionChecks: List<PositionCheck> = poseVariant.positionChecks

    private val hasPositionChecksConfigured: Boolean = exerciseConfig.hasAnyPositionChecks(poseVariantIndex)

    private val activeTiltSource: AcquirableTiltSource? =
        tiltSource?.takeIf { hasPositionChecksConfigured }

    private val tiltOwnerKey: String =
        "engine:${exerciseConfig.fileName.ifBlank { exerciseConfig.name.en }}:$poseVariantIndex:${System.identityHashCode(this)}"

    private val positionChecksById: Map<String, PositionCheck> =
        configuredPositionChecks.associateBy { it.id }

    private val frameFeedback: FrameFeedbackEmitter = FrameFeedbackEmitter(
        feedbackPolicy = feedbackPolicy,
        positionChecksById = positionChecksById,
        timeProvider = { nowMs() },
        jointErrorCooldownMs = timingPolicy.stateMessageCooldownMs
    )
    
    private var lastCameraWarningEventTime = 0L

    private val resolvedPosePositionCode: String =
        poseVariant.posePosition ?: poseVariant.cameraPosition ?: "standing_side"

    private val resolvedExpectation: PoseSceneExpectation =
        if (poseVariant.expectedPostures != null) {
            PoseSceneExpectation.fromJson(poseVariant.expectedPostures, poseVariant.expectedDirections, poseVariant.expectedRegions)
        } else {
            PoseSceneExpectation.fromLegacyCode(resolvedPosePositionCode)
        }

    private val positionValidator: PositionValidator = PositionValidator(
        positionChecks = configuredPositionChecks,
        posePositionCode = resolvedPosePositionCode,
        sceneExpectation = resolvedExpectation,
        tiltSource = activeTiltSource
    )

    private val framePipelineExecutor: FramePipelineExecutor = FramePipelineExecutor(
        angleSmoother = angleSmoother,
        startPoseGate = startPoseGate,
        stateMachine = stateMachine,
        positionValidator = positionValidator,
        frameEvalPipeline = frameEvalPipeline,
        primaryJointCodes = primaryJointCodes
    )
    
    private val visibilityMonitor: VisibilityMonitor = VisibilityMonitor(
        visibilityTrackedJoints = poseVariant.trackedJoints
            .filter { it.role == JointRole.PRIMARY || it.role == JointRole.SECONDARY },
        minVisibility = timingPolicy.visibilityMinVisibility,
        graceDurationMs = timingPolicy.visibilityGraceDurationMs,
        warningDurationMs = timingPolicy.visibilityWarningDurationMs,
        pauseAfterMs = timingPolicy.visibilityPauseAfterMs,
        timeProvider = { nowMs() }
    )
    
    // Milestone: hold/repetition targets and guardrails.

    val isHoldExercise: Boolean = exerciseConfig.countingMethod == CountingMethod.HOLD
    
    val targetDurationMs: Long? = if (isHoldExercise) {
        targetDurationMsOverride
            ?: repCountingConfig.getDurationMs(
                timingPolicy.defaultHoldDurationSeconds
            )
    } else null
    
    private val holdTimer: HoldTimer? = targetDurationMs?.let { duration ->
        HoldTimer(
            targetDurationMs = duration,
            gracePeriodMs = repCountingConfig.getGracePeriod(
                timingPolicy.defaultGracePeriodMs
            )
        )
    }

    private val repCompletion: RepCompletionCoordinator = RepCompletionCoordinator(
        tag = TAG,
        stateMachine = stateMachine,
        repCounter = repCounter,
        repCompletionSignal = repCompletionSignal,
        motionRecorder = { motionRecorder },
        bilateral = bilateral,
        pipelineTrace = pipelineTrace
    )

    private val holdExercise: HoldExerciseCoordinator = HoldExerciseCoordinator(
        tag = TAG,
        holdTimer = holdTimer,
        repCounter = repCounter,
        getTargetDurationMs = { targetDurationMs ?: 0L },
        timeProvider = { nowMs() },
        motionRecorder = { motionRecorder },
        bilateral = bilateral,
        pipelineTrace = pipelineTrace
    )

    private val executionSafety = ExecutionSafetyGuards(
        timingPolicy = timingPolicy,
        isHoldExercise = isHoldExercise,
        targetReps = completionTargetReps,
        targetDurationMs = targetDurationMs,
        minRepIntervalMs = minRepIntervalMs
    )
    @Volatile
    private var cameraWarningCount: Int = 0

    @Volatile
    private var safetyStopTriggered: Boolean = false
    
    // Milestone: public state streams consumed by ViewModel/UI.
    
    private val _currentPhase = MutableStateFlow(Phase.IDLE)
    val currentPhase: StateFlow<Phase> = _currentPhase
    
    private val _repCount = MutableStateFlow(0)
    val repCount: StateFlow<Int> = _repCount
    
    private val _jointStateInfos = MutableStateFlow<Map<String, JointStateInfo>>(emptyMap())
    val jointStateInfos: StateFlow<Map<String, JointStateInfo>> = _jointStateInfos
    
    private val _isDangerActive = MutableStateFlow(false)
    val isDangerActive: StateFlow<Boolean> = _isDangerActive
    
    private val _isInStartPosition = MutableStateFlow(false)
    val isInStartPosition: StateFlow<Boolean> = _isInStartPosition
    
    private val _isCompleted = MutableStateFlow(false)
    val isCompleted: StateFlow<Boolean> = _isCompleted
    
    private val _currentAngles = MutableStateFlow<Map<String, Double>>(emptyMap())
    val currentAngles: StateFlow<Map<String, Double>> = _currentAngles

    private val _anySideDimmedJointCodes = MutableStateFlow<Set<String>>(emptySet())
    val anySideDimmedJointCodes: StateFlow<Set<String>> = _anySideDimmedJointCodes.asStateFlow()
    
    private val _positionErrors = MutableStateFlow<List<PositionError>>(emptyList())
    val positionErrors: StateFlow<List<PositionError>> = _positionErrors
    
    private val _sceneWarnings = MutableStateFlow<List<SceneAxisWarning>>(emptyList())
    val sceneWarnings: StateFlow<List<SceneAxisWarning>> = _sceneWarnings
    
    private val _visibilityState = MutableStateFlow(VisibilityState.VISIBLE)
    val visibilityState: StateFlow<VisibilityState> = _visibilityState
    
    val isCountingSuspended: StateFlow<Boolean> = pauseController.isCountingSuspended

    val isVisibilityPaused: StateFlow<Boolean> = pauseController.isVisibilityPaused

    val visibilityResumeCountdown: StateFlow<Int?> = pauseController.visibilityResumeCountdown

    private val _holdStatus = MutableStateFlow<HoldStatus?>(null)
    val holdStatus: StateFlow<HoldStatus?> = _holdStatus.asStateFlow()
    
    private val _events = MutableSharedFlow<FeedbackEvent>(
        replay = 0,
        extraBufferCapacity = 20
    )
    val events: SharedFlow<FeedbackEvent> = _events
    
    // Milestone: lifecycle/time state. All mutations are synchronized by [stateLock].
    
    @Volatile
    private var isRunning = false
    
    @Volatile
    private var isPaused = false
    
    @Volatile
    private var executionStartTimeMs: Long = 0L
    
    @Volatile
    private var totalPausedDurationMs: Long = 0L
    
    @Volatile
    private var pauseStartTimeMs: Long = 0L
    
    @Volatile
    private var currentFrameTimeMs: Long = 0L

    @Volatile
    private var usesExternalFrameTimeline: Boolean = false
    
    
    private fun nowMs(): Long {
        return if (currentFrameTimeMs > 0L) currentFrameTimeMs else SystemClock.uptimeMillis()
    }

    private fun pauseClockNowMs(): Long {
        return if (usesExternalFrameTimeline) nowMs() else SystemClock.uptimeMillis()
    }

    private fun settlePauseDurationLocked() {
        if (pauseStartTimeMs > 0L) {
            val pausedFor = pauseClockNowMs() - pauseStartTimeMs
            totalPausedDurationMs += maxOf(0L, pausedFor)
            pauseStartTimeMs = 0L
        }
    }
    
    private fun getActiveExecutionDurationMs(now: Long = nowMs()): Long {
        if (executionStartTimeMs <= 0L) return 0L

        val pendingPause = if (isPaused && pauseStartTimeMs > 0L) {
            pauseClockNowMs() - pauseStartTimeMs
        } else 0L

        val elapsed = now - executionStartTimeMs
        return maxOf(0L, elapsed - totalPausedDurationMs - maxOf(0L, pendingPause))
    }

    private fun isRepMovementPhase(phase: Phase): Boolean {
        return phase != Phase.IDLE && phase != Phase.START
    }

    private fun triggerSafetyStop(reason: String) {
        if (safetyStopTriggered || _isCompleted.value) return

        safetyStopTriggered = true
        _isCompleted.value = true

        Log.w(
            TAG,
            "Safety stop triggered: $reason, reps=${repCounter.count}, counted=${repCounter.countedCount}, duration=${getActiveExecutionDurationMs()}ms"
        )
        pipelineTrace.record("safety stop: $reason (reps=${repCounter.count})")
    }

    private fun evaluateSafetyStop(now: Long = nowMs()): Boolean {
        if (safetyStopTriggered || _isCompleted.value) return true

        if (repCounter.count >= executionSafety.maxRepsGuard) {
            triggerSafetyStop("max reps guard reached (${executionSafety.maxRepsGuard})")
            return true
        }
        val activeDurationMs = getActiveExecutionDurationMs(now)
        if (activeDurationMs >= executionSafety.maxExecutionDurationGuardMs) {
            triggerSafetyStop("max execution duration guard reached (${activeDurationMs}ms)")
            return true
        }

        return false
    }

    // Milestone: callbacks translate lower-level completion into public events/state.

    init {
        stateMachine.onPhaseChanged = { previous, current ->
            _currentPhase.value = current
        }
        
        stateMachine.onRepCompleted = {
            repCompletion.onPhaseMachineWantsComplete()
        }

        stateMachine.onRepIncomplete = { reason ->
            emitEvent(
                FeedbackEvent.RepIncomplete(
                    reason = reason,
                    attemptNumber = repCounter.count + 1
                )
            )
        }
        
        repCounter.onRepCountChanged = { count, score, isCounted ->
            _repCount.value = count
            val completedRep = repCounter.getLastRepResult()
            val completedRepErrors = completedRep?.errors ?: emptyList()
            emitEvent(FeedbackEvent.RepCompleted(
                repNumber = count,
                isCorrect = isCounted,
                errors = completedRepErrors,
                score = score,
                worstState = completedRep?.worstState
            ))

            evaluateSafetyStop()
        }
        
        repCounter.onTargetReached = onTargetReached@ {
            if (safetyStopTriggered) return@onTargetReached
            _isCompleted.value = true
            emitEvent(FeedbackEvent.TargetReached(
                totalReps = repCounter.count,
                correctReps = repCounter.correctCount,
                accuracy = repCounter.getAccuracy()
            ))
        }
        
        holdExercise.installCallbacks(
            isHoldExercise = isHoldExercise,
            onEmit = { event -> emitEvent(event) },
            publishStatus = { s -> _holdStatus.value = s },
            setExerciseCompleted = { completed -> if (completed) _isCompleted.value = true }
        )
        
        Log.d(TAG, "TrainingEngine initialized (STATE-BASED)")
        Log.d(TAG, "Exercise: ${exerciseConfig.name.en}")
        Log.d(TAG, "Counting Method: ${exerciseConfig.countingMethod}")
        if (isHoldExercise) {
            Log.d(TAG, "Target Duration: ${targetDurationMs}ms")
            Log.d(TAG, "Grace Period: ${holdTimer?.getGracePeriodMs()}ms")
        } else {
            Log.d(TAG, "Target Reps: $completionTargetReps")
        }
        Log.d(TAG, "Tracked Joints: ${trackedJoints.map { it.joint }}")
        Log.d(TAG, "Primary Joints: ${primaryJoints.map { it.joint }}")
    }
    
    private val stateLock = Any()

    // Milestone: lifecycle API. These methods reset/settle exercise-run state under the same lock as frames.

    fun start() {
        synchronized(stateLock) {
            isRunning = true
            isPaused = false
            currentFrameTimeMs = 0L
            usesExternalFrameTimeline = false
            executionStartTimeMs = 0L
            totalPausedDurationMs = 0L
            pauseStartTimeMs = 0L
            angleSmoother.reset()
            stateMachine.reset()
            repCounter.reset()
            holdTimer?.reset()
            positionValidator.clearCooldowns()
            jointEvaluator.reset()
            bilateral.resetToConfigStart()
            lastCameraWarningEventTime = 0L
            repCompletion.clear()
            frameFeedback.clearPositionCooldowns()
            pipelineTrace.clear()
            if (isHoldExercise) {
                pipelineTrace.record("start hold targetMs=$targetDurationMs")
            } else {
                pipelineTrace.record("start reps target=$targetReps")
            }
            cameraWarningCount = 0
            safetyStopTriggered = false
            positionValidator.unlockScene()
            
            feedbackPolicy.resetExecution()
            
            _currentPhase.value = Phase.IDLE
            _repCount.value = 0
            _isCompleted.value = false
            _isDangerActive.value = false
            _isInStartPosition.value = false
            _currentAngles.value = emptyMap()
            _anySideDimmedJointCodes.value = emptySet()
            _jointStateInfos.value = emptyMap()
            
            _positionErrors.value = emptyList()
            _sceneWarnings.value = emptyList()
            
            visibilityMonitor.reset()
            visibilityMonitor.resetStats()
            _visibilityState.value = VisibilityState.VISIBLE
            pauseController.resetExecution()
            
            if (isHoldExercise) {
                holdExercise.resetTracking { _holdStatus.value = it }
            } else {
                _holdStatus.value = null
            }
            
            // Wall-clock start so finalize() can compute (end - start) without Int overflow.
            // start(0L) made (nowMs - 0) exceed Int.MAX_VALUE when cast to Int → negative durations in DB/UI.
            motionRecorder?.start(System.currentTimeMillis())
        }
        acquireTiltCorrection()
        
        Log.d(TAG, "Training started (${if (isHoldExercise) "HOLD" else "REPS"} mode)")
        if (hasPositionChecksConfigured) {
            Log.d(TAG, "Position checks enabled: ${configuredPositionChecks.size} checks")
        }
    }
    
    fun pause() {
        synchronized(stateLock) {
            if (!isPaused) {
                isPaused = true
                pauseStartTimeMs = pauseClockNowMs()
            }
        }
        releaseTiltCorrection()
        Log.d(TAG, "Training paused at rep ${repCounter.count}")
    }
    
    fun resume() {
        synchronized(stateLock) {
            settlePauseDurationLocked()
            isPaused = false

            if (pauseController.onUserOrSupervisorResume(visibilityMonitor)) {
                _visibilityState.value = VisibilityState.VISIBLE
            }
        }
        acquireTiltCorrection()
        Log.d(TAG, "Training resumed")
    }
    
    private fun performVisibilityResume() {
        stateMachine.reset()
        angleSmoother.reset()
        jointEvaluator.reset()
        positionValidator.clearCooldowns()
        positionValidator.unlockScene()
        repCompletion.clear()
        frameFeedback.clearPositionCooldowns()

        pauseController.clearAfterAutoResume(visibilityMonitor)
        _visibilityState.value = VisibilityState.VISIBLE

        _currentPhase.value = Phase.IDLE
        _positionErrors.value = emptyList()
        _sceneWarnings.value = emptyList()

        emitEvent(FeedbackEvent.VisibilityResumed(repCount = repCounter.count))
        pipelineTrace.record("visibility auto-resume rep=${repCounter.count}")
        Log.d(TAG, "Auto-resumed from visibility pause at rep ${repCounter.count}")
    }
    
    fun stop(): ExerciseWorkoutSummary {
        val actualDurationMs: Long
        synchronized(stateLock) {
            val now = nowMs()
            val totalElapsed = if (executionStartTimeMs > 0) now - executionStartTimeMs else 0L
            
            val pendingPause = if (isPaused && pauseStartTimeMs > 0) {
                pauseClockNowMs() - pauseStartTimeMs
            } else 0L
            
            actualDurationMs = maxOf(0L, totalElapsed - totalPausedDurationMs - pendingPause)
            
            isRunning = false
            isPaused = false
            pauseController.resetExecution()
            _anySideDimmedJointCodes.value = emptySet()
        }
        releaseTiltCorrection()
        
        val summary = ExerciseWorkoutSummaryBuilder.build(
            config = exerciseConfig,
            repCounter = repCounter,
            durationMs = actualDurationMs
        )
        
        Log.d(TAG, "Training stopped. Duration: ${actualDurationMs}ms, Summary: $summary")
        return summary
    }
    
    fun processFrame(input: FrameInput) {
        processFrame(
            angles = input.angles,
            landmarks = input.landmarks,
            isFrontCamera = input.isFrontCamera,
            timestampMs = input.timestampMs
        )
    }

    /*
     * Milestone: frame runner.
     * 1) Set the frame clock and safety guard.
     * 2) Extract raw tracked angles, then run visibility/pause even while counting is suspended.
     * 3) If counting can continue, delegate smooth/start/phase/position/joint quality to FramePipelineExecutor.
     * 4) Publish flows, accumulate rep/hold state, emit throttled feedback, then complete rep/hold after errors.
     */
    fun processFrame(
        angles: JointAngles,
        landmarks: List<SmoothedLandmark>? = null,
        isFrontCamera: Boolean = false,
        timestampMs: Long = SystemClock.uptimeMillis()
    ) {
        if (!isRunning || _isCompleted.value) return
        if (isPaused && !pauseController.isVisibilityPaused.value && !pauseController.isCountingSuspended.value) return

        synchronized(stateLock) {
            if (!isRunning || _isCompleted.value) return
            if (isPaused && !pauseController.isVisibilityPaused.value && !pauseController.isCountingSuspended.value) return
            
            val frameTimeMs = if (timestampMs > 0L) timestampMs else SystemClock.uptimeMillis()
            currentFrameTimeMs = frameTimeMs
            usesExternalFrameTimeline =
                timestampMs > 0L && kotlin.math.abs(SystemClock.uptimeMillis() - frameTimeMs) > 30_000L
            if (executionStartTimeMs == 0L) {
                executionStartTimeMs = frameTimeMs
            }
            if (evaluateSafetyStop(frameTimeMs)) {
                return
            }
            val angleExtract = jointTracker.extractTrackedAngles(
                angles = angles,
                isFlipped = isBilateralFlipped,
                landmarks = landmarks,
                isFrontCamera = isFrontCamera
            )
            val rawTrackedAngles = angleExtract.angles
            val skippedForFrame = angleExtract.skippedJointCodes

            var skipCounting = false
            var allJointsVisible = false
            if (landmarks != null) {
                val visibilityResult = visibilityMonitor.checkVisibility(
                    landmarks = landmarks,
                    currentRepCount = repCounter.count,
                    currentPhase = stateMachine.currentPhase,
                    isFrontCamera = isFrontCamera
                )
                _visibilityState.value = visibilityMonitor.state.value
                skipCounting = pauseController.processVisibilityResult(
                    visibilityResult,
                    emit = { emitEvent(it) },
                    onAutoResumeComplete = { performVisibilityResume() }
                )
                allJointsVisible = visibilityMonitor.state.value == VisibilityState.VISIBLE
            }

            if (skipCounting || rawTrackedAngles.isEmpty()) {
                _anySideDimmedJointCodes.value = skippedForFrame
                if (landmarks != null) {
                    val pv = positionValidator.validate(
                        landmarks, stateMachine.currentPhase, isBilateralFlipped, isFrontCamera
                    )
                    if (!positionValidator.isSceneLocked) { positionValidator.lockScene() }
                    _positionErrors.value = pv.errors + pv.warnings + pv.tips
                    _sceneWarnings.value = if (allJointsVisible) emptyList() else pv.sceneWarnings
                    if (!allJointsVisible) {
                        pv.sceneWarnings.takeIf { it.isNotEmpty() }?.let { warnings ->
                            val now = nowMs()
                            if (now - lastCameraWarningEventTime >= cameraWarningEventCooldownMs) {
                                lastCameraWarningEventTime = now
                                cameraWarningCount++
                                emitEvent(FeedbackEvent.SceneWarnings(warnings))
                            }
                        }
                    }
                }
                return
            }

            val m = framePipelineExecutor.runMainPath(
                rawTrackedAngles = rawTrackedAngles,
                skippedForFrame = skippedForFrame,
                landmarks = landmarks,
                isBilateralFlipped = isBilateralFlipped,
                isFrontCamera = isFrontCamera,
                allJointsVisible = allJointsVisible
            )
            val lastPhase = _currentPhase.value
            if (m.currentPhase != lastPhase) {
                pipelineTrace.record("phase: $lastPhase -> ${m.currentPhase}")
            }
            _currentAngles.value = m.smoothedAngles
            _anySideDimmedJointCodes.value = m.skippedForFrame
            _isInStartPosition.value = m.inStartPosition
            _currentPhase.value = m.currentPhase
            m.positionResult?.let { cachedPositionValidation ->
                _positionErrors.value = cachedPositionValidation.errors +
                    cachedPositionValidation.warnings + cachedPositionValidation.tips
                _sceneWarnings.value = if (allJointsVisible) emptyList()
                    else cachedPositionValidation.sceneWarnings
                if (!allJointsVisible) {
                    cachedPositionValidation.sceneWarnings.takeIf { it.isNotEmpty() }?.let { warnings ->
                        val now = nowMs()
                        if (now - lastCameraWarningEventTime >= cameraWarningEventCooldownMs) {
                            lastCameraWarningEventTime = now
                            cameraWarningCount++
                            emitEvent(FeedbackEvent.SceneWarnings(warnings))
                        }
                    }
                }
            }
            val frameResult = m.frameJoint
            val currentPhase = m.currentPhase
            val jointEvals = frameResult.jointEvals
            val jointStateInfos = frameResult.jointStateInfos
            val cachedPositionValidation = m.positionResult
            _jointStateInfos.value = jointStateInfos
            frameFeedback.emitThrottledStateMessages(jointStateInfos, ::emitEvent)

            motionRecorder?.record(
                timestamp = frameTimeMs,
                phase = currentPhase,
                angles = m.smoothedAngles,
                states = jointStateInfos,
                skippedJointCodes = m.skippedForFrame
            )

            val hasDanger = jointEvals.hasAnyDangerState()
            _isDangerActive.value = hasDanger

            val shouldTrackState = if (isHoldExercise) {
                currentPhase == Phase.COUNT
            } else {
                isRepMovementPhase(currentPhase)
            }

            if (shouldTrackState) {
                repCounter.updateJointEvals(
                    frameResult.forScoring(m.skippedForFrame)
                )
            }

            val jointErrors = JointErrorCollection.collectJointErrors(trackedJointsByCode, jointStateInfos)
            for (error in jointErrors) {
                repCounter.addError(error)
                if (!frameFeedback.shouldEmitJointError(error)) continue
                emitEvent(
                    FeedbackEvent.JointQuality(
                        content = JointQualityContent.Error(error),
                        priority = FeedbackPriority.HIGH
                    )
                )
            }

            cachedPositionValidation?.let { pv ->
                pv.errors.forEach { error ->
                    repCounter.addPositionError(error)
                    if (frameFeedback.shouldEmitPositionEvent(error.checkId)) {
                        emitEvent(FeedbackEvent.PositionCheckFeedback(error))
                    }
                }
                pv.warnings.forEach { error ->
                    repCounter.addPositionWarning(error)
                    if (frameFeedback.shouldEmitPositionEvent(error.checkId)) {
                        emitEvent(FeedbackEvent.PositionCheckFeedback(error))
                    }
                }
                pv.tips.forEach { tip ->
                    repCounter.addPositionTip(tip)
                    if (frameFeedback.shouldEmitPositionEvent(tip.checkId)) {
                        emitEvent(FeedbackEvent.PositionCheckFeedback(tip))
                    }
                }
            }

            if (isHoldExercise) {
                val isInHoldZone = (currentPhase == Phase.COUNT)
                holdExercise.updateHoldTimer(isInHoldZone) { s -> _holdStatus.value = s }
            } else {
                repCompletion.consumeIfPendingAndHandle()
            }

            evaluateSafetyStop(frameTimeMs)
        }
    }
    
    private fun emitEvent(event: FeedbackEvent) {
        _events.tryEmit(event)
    }

    // Milestone: compatibility getters used by UI/reporting while the internals keep moving out.
    
    fun getExerciseConfig(): ExerciseConfig = exerciseConfig
    fun getTargetReps(): Int = completionTargetReps
    fun getCurrentRep(): Int = repCounter.count
    fun getCountedReps(): Int = repCounter.countedCount
    fun getAverageScore(): Float = repCounter.getAverageScore()
    fun getAccuracy(): Float = repCounter.getAccuracy()
    fun getProgress(): Float = repCounter.getProgress()
    fun isTrainingActive(): Boolean = isRunning && !isPaused

    fun getRepResults(): List<RepResult> = repCounter.repResults
    
    fun getCorrectReps(): Int = repCounter.countedCount

    fun getTrackedJointCodes(): Set<String> = jointTracker.trackedJointCodes
    fun getPrimaryJointCodes(): Set<String> = jointTracker.primaryJointCodes
    fun getLandmarkIndex(jointCode: String): Int? = jointTracker.getLandmarkIndex(jointCode)
    fun getTrackedLandmarkIndices(): List<Int> = jointTracker.getTrackedLandmarkIndices()

    private fun acquireTiltCorrection() {
        activeTiltSource?.acquire(tiltOwnerKey)
    }

    private fun releaseTiltCorrection() {
        activeTiltSource?.release(tiltOwnerKey)
    }

    fun canHotSwapTo(newConfig: ExerciseConfig): Boolean {
        return newConfig.countingMethod == exerciseConfig.countingMethod
    }

    fun getCurrentExerciseConfig(): ExerciseConfig = exerciseConfig

    fun getTargetDurationMs(): Long = targetDurationMs ?: 0L
    fun getTargetDurationSeconds(): Int = ((targetDurationMs ?: 0L) / 1000L).toInt()
    fun getHoldTimer(): HoldTimer? = holdTimer
    fun isHoldCompleted(): Boolean = holdTimer?.isCompleted() ?: false
    fun isHoldFailed(): Boolean = holdTimer?.isFailed() ?: false
    fun isInGracePeriod(): Boolean = holdTimer?.isInGracePeriod() ?: false
    fun getGracePeriodCount(): Int = holdTimer?.getGracePeriodCount() ?: 0

    fun hasPositionChecks(): Boolean = hasPositionChecksConfigured
    fun getCameraWarningCount(): Int = cameraWarningCount
    fun getCurrentPositionErrors(): List<PositionError> = _positionErrors.value
    fun getLastCameraResult(): CameraPositionDetector.CameraDetectionResult? = 
        positionValidator.getLastCameraResult()

    fun isVisibilityPausedNow(): Boolean = pauseController.isVisibilityPaused.value
    fun getVisibilityState(): VisibilityState = _visibilityState.value
    fun getVisibilityStats(): VisibilityStats = visibilityMonitor.getStats()
}

