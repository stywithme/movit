package com.movit.feature.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.data.MovitData
import com.movit.core.data.audio.AudioPrefetchRunner
import com.movit.core.data.cache.SystemMessageRegistry
import com.movit.core.data.preferences.MovitTrainingPreferences
import com.movit.core.data.repository.TrainingConfigRepository
import com.movit.core.data.repository.TrainingSessionWriteCoordinator
import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.training.config.getMessagesForState
import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.config.TrackedJoint
import com.movit.core.training.diagnostics.TrainingPipelineDiagnostics
import com.movit.core.training.engine.Phase
import com.movit.core.training.engine.RepIncompleteReason
import com.movit.core.training.engine.shouldDiscardRepAttemptOnIncomplete
import com.movit.core.training.engine.ZoneType
import com.movit.feature.library.ReportTarget
import com.movit.feature.library.WorkoutRunStore
import com.movit.feature.reports.TrainingSessionReportCache
import com.movit.shared.training.MovitTrainingAnalytics
import com.movit.core.training.engine.ErrorType
import com.movit.core.training.boundary.TrainingFrameSnapshotPort
import com.movit.core.training.engine.JointError
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.feedback.FeedbackRouter
import com.movit.core.training.engine.feedback.FeedbackVisualMessage
import com.movit.core.training.engine.feedback.TrainingFeedbackEventRouter
import com.movit.core.training.engine.feedback.TrainingSystemMessagePort
import com.movit.core.training.engine.feedback.VignetteCue
import com.movit.core.training.session.HoldState
import com.movit.core.training.engine.policy.TimingPolicy
import com.movit.core.training.feedback.CoachIntensity
import com.movit.core.training.feedback.FeedbackInterruptPolicy
import com.movit.core.training.feedback.FeedbackKind
import com.movit.core.training.feedback.RepIncompleteFeedback
import com.movit.core.training.feedback.FeedbackSeverity
import com.movit.core.training.feedback.FeedbackSignal
import com.movit.core.training.feedback.SetupFeedbackSignals
import com.movit.core.training.journal.SessionJournalSnapshot
import com.movit.core.training.journal.WorkoutUpload
import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.PoseFrame
import com.movit.core.training.report.AssessmentTrainingResult
import com.movit.core.training.report.MovitSessionReport
import com.movit.core.training.report.MovitSessionReportBuilder
import com.movit.core.training.boundary.AcquirableDeviceTiltPort
import com.movit.core.training.boundary.DeviceTiltPort
import com.movit.core.training.position.PositionMessageResolver
import com.movit.core.training.session.CountdownController
import com.movit.core.training.session.ExerciseWorkoutSummary
import com.movit.core.training.session.HoldStatus
import com.movit.core.training.session.MovitTrainingEngine
import com.movit.core.training.session.PauseReason
import com.movit.core.training.session.PresenceSupervisorEvent
import com.movit.core.training.session.toSupervisorSignal
import com.movit.core.training.session.SessionRunState
import com.movit.core.training.session.SessionSupervisor
import com.movit.core.training.session.SetupPhase
import com.movit.core.training.session.SetupGuidanceLevel
import com.movit.core.training.session.SetupPoseTiltOwner
import com.movit.core.training.session.SetupReadinessGate
import com.movit.core.training.session.SetupReadinessResult
import com.movit.core.training.session.SetupVoiceGuidanceGate
import com.movit.core.training.session.SetupValidationConfig
import com.movit.core.training.session.SupervisorAction
import com.movit.core.training.session.SupervisorSignal
import com.movit.core.training.session.TrainingFlowItem
import com.movit.core.training.session.TrainingSessionFlowCoordinator
import com.movit.designsystem.components.GlassMessageSeverity
import com.movit.designsystem.components.SkeletonLandmarkPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.movit.core.training.report.MovitHoldReportData
import com.movit.core.training.report.SessionQualityMeta

/**
 * KMP session VM: supervisor + readiness gate + countdown + engine + feedback (WS-5/WS-7/07.8-B).
 */
class TrainingSessionViewModel(
  private val exerciseSlug: String,
  private val targetReps: Int,
  private val exerciseNameOverride: String = "",
  private val language: String = "en",
  private val sessionId: String = exerciseSlug,
  private val runId: String? = null,
  private val isAssessmentMode: Boolean = false,
  private val flowItems: List<TrainingFlowItem>? = null,
  private val startExerciseIndex: Int = 0,
  private val uploadContext: WorkoutUploadContext? = null,
  private val plannedWorkout: PlannedWorkoutContext? = null,
  private val routePoseVariantIndex: Int = 0,
  private val configRepository: TrainingConfigRepository = MovitData.trainingConfig,
  private val writeCoordinator: TrainingSessionWriteCoordinator = MovitData.trainingWrites,
  private val trainingPreferences: MovitTrainingPreferences = MovitData.trainingPreferences,
  private val audioPrefetchRunner: AudioPrefetchRunner = MovitData.audioPrefetch,
  private val setupValidation: SetupValidationConfig = SetupValidationConfig(),
  private val deviceTiltPort: DeviceTiltPort? = null,
  private val frameSnapshotPort: TrainingFrameSnapshotPort = defaultTrainingFrameSnapshotPort(),
  feedbackRouter: FeedbackRouter = FeedbackRouter(coachIntensity = CoachIntensity.STANDARD),
) : ViewModel() {
  private var activeSlug: String = exerciseSlug
  private var activeTargetReps: Int = targetReps
  private var activeTargetDurationSeconds: Int? = null
  private var activeSessionWeightKg: Float? = null
  private var activeExerciseName: String = exerciseNameOverride
  private var activePoseVariantIndex: Int = routePoseVariantIndex
  private var exerciseConfig: ExerciseConfig? = configRepository.getBySlug(activeSlug)?.config
  /** WP-08: lazy joint-message lookup; refreshed when config/variant changes. */
  private var trackedJointsByCode: Map<String, TrackedJoint> = emptyMap()

  private var timingPolicy: TimingPolicy = TimingPolicy.withCoachIntensity(
    CoachIntensity.from(trainingPreferences.snapshot().coachIntensity),
  )
  private var pendingTimingPolicy: TimingPolicy? = null

  private var lastFrameTimestampMs: Long = 0L
  @Volatile
  private var lastAbsenceSeenMs: Long = 0L
  private var writeHooks = createWriteHooks()
  private val writeDiagnostics = TrainingSessionWriteDiagnostics()
  private var phasePauseSnapshot: PhasePauseSnapshot? = null
  /** UX.3 / P1.5: orphan journal awaiting resume or discard. */
  private var pendingResumeJournal: SessionJournalSnapshot? = null
  /** P1.4: exit teardown already ran — onDispose StopSession is a no-op. */
  private var exitTornDown: Boolean = false
  private var pausedForExitPrompt: Boolean = false
  /** WP-05 / G-01: in-flight finalize on [TrainingFinalizeScope]; detach waits for it. */
  @Volatile
  private var finalizeInFlight: Job? = null

  private val flowCoordinator = flowItems?.let { TrainingSessionFlowCoordinator(it) }
  private var restTimerJob: Job? = null
  private var restNearEndAnnounced = false
  private var restPaused = false

  private val supervisor = SessionSupervisor(
    setupValidation = setupValidation,
    scope = viewModelScope,
  )
  private val readinessGate = SetupReadinessGate(setupValidation, deviceTiltPort)
  private val setupPoseTiltOwner = SetupPoseTiltOwner(deviceTiltPort as? AcquirableDeviceTiltPort)
  private val setupVoiceGate = SetupVoiceGuidanceGate(setupValidation.voiceCooldownMs)
  private var lastSetupPhase: SetupPhase? = null
  private val countdown = CountdownController()
  private val feedback = feedbackRouter
  private val feedbackEventRouter = TrainingFeedbackEventRouter(
    TrainingSystemMessagePort { key, defaultAr, defaultEn, substitutions ->
      SystemMessageRegistry
        .substitute(SystemMessageRegistry.get(key, defaultAr, defaultEn), substitutions)
        .display(language)
    },
  )
  private var previousHoldState: HoldState? = null
  private var engine: MovitTrainingEngine? = null

  private var sessionStartMs: Long = 0L
  private var lastElapsedMs: Long = 0L
  private var activeElapsedMs: Long = 0L
  private var lastTrainingTimestampMs: Long = 0L
  private var visibilityWarningActive = false
  private var visibilityPauseCount = 0
  private var cameraWarningCount = 0
  private var lastRandomMessageCheckMs = 0L
  private var plannedWorkoutStarted = false
  private var accumulatedDayReport: MovitSessionReport? = null
  private var frameCaptureCoordinator = createFrameCaptureCoordinator()
  private var latestTrainingAngles: JointAngles? = null
  private val poseFrameChannel = Channel<PoseFrame?>(capacity = Channel.CONFLATED)
  private var poseFrameWorker: Job? = null
  @Volatile
  private var poseFrameWorkerBusy = false

  private val _state = MutableStateFlow(
    TrainingSessionUiState(
      exerciseSlug = activeSlug,
      exerciseName = resolveExerciseName(),
      targetReps = activeTargetReps,
      isOffline = MovitData.isInstalled && !MovitData.requirePlatform().isNetworkAvailable(),
      configUnavailable = exerciseConfig == null,
      workoutFlowEnabled = flowCoordinator != null,
    ),
  )
  val state: StateFlow<TrainingSessionUiState> = _state.asStateFlow()

  private val _overlay = MutableStateFlow(TrainingOverlayState())
  val overlay: StateFlow<TrainingOverlayState> = _overlay.asStateFlow()

  private val _effects = MutableSharedFlow<TrainingSessionEffect>(extraBufferCapacity = 1)
  val effects: SharedFlow<TrainingSessionEffect> = _effects.asSharedFlow()

  fun onEvent(event: TrainingSessionEvent) {
    when (event) {
      TrainingSessionEvent.StartWorkoutExercise -> startWorkoutExercise()
      TrainingSessionEvent.SkipRest -> skipRest()
      TrainingSessionEvent.ToggleRestPause -> toggleRestPause()
      TrainingSessionEvent.AddRestTime -> addRestTime()
      TrainingSessionEvent.StopSession -> {
        if (!exitTornDown) {
          // Unexpected dispose (nav / process): persist when there is progress.
          if (TrainingSessionExitPolicy.hasProgressWorthSaving(_state.value) ||
            TrainingSessionExitPolicy.shouldConfirmExit(_state.value)
          ) {
            tearDownForExit(persist = true, abandon = false)
          } else {
            tearDownForExit(persist = false, abandon = false)
          }
        }
      }
      is TrainingSessionEvent.PoseFrameReceived -> onPoseFrame(event.frame)
      TrainingSessionEvent.CameraReady -> onCameraReady()
      TrainingSessionEvent.CameraSwitchStarted -> onCameraSwitchStarted()
      is TrainingSessionEvent.CameraError -> onCameraError(event.message)
      TrainingSessionEvent.Pause -> pause()
      TrainingSessionEvent.Resume -> resume()
      TrainingSessionEvent.Stop -> stop()
      is TrainingSessionEvent.HostBackgrounded -> onHostBackgrounded(event.nowMs ?: defaultLifecycleNowMs())
      is TrainingSessionEvent.HostForegrounded -> onHostForegrounded(event.nowMs ?: defaultLifecycleNowMs())
      TrainingSessionEvent.BackPressed -> onBackPressed()
      TrainingSessionEvent.ExitContinue -> onExitContinue()
      TrainingSessionEvent.ExitSaveAndExit -> onExitSaveAndExit()
      TrainingSessionEvent.ExitEndWorkout -> onExitEndWorkout()
      TrainingSessionEvent.FinishClicked -> {
        stopSession()
        _effects.tryEmit(TrainingSessionEffect.Finish(_state.value.isWorkoutComplete))
      }
      TrainingSessionEvent.ViewReportClicked -> {
        _state.value.reportDetailId?.let { reportId ->
          MovitTrainingAnalytics.trackOpenReport(
            reportId = reportId,
            scope = if (flowCoordinator != null) "workout" else "exercise",
          )
          _effects.tryEmit(TrainingSessionEffect.ViewReport(reportId))
        }
      }
      TrainingSessionEvent.ResumePriorSession -> resumePriorSession()
      TrainingSessionEvent.DiscardPriorSession -> discardPriorSession()
    }
  }

  private fun defaultLifecycleNowMs(): Long =
    lastFrameTimestampMs.takeIf { it > 0 } ?: sessionStartMs

  init {
    val resumeSet = runId?.let { WorkoutRunStore.get(it)?.progress?.currentSet } ?: 1
    flowCoordinator?.startAt(itemIndex = 0, setNumber = resumeSet)
    // Prefer flow item (resume mid-workout) over route slug/reps so engine matches the cursor.
    flowCoordinator?.currentExerciseOrNull()?.let { applyFlowExercise(it, resumeSet) }
      ?: run {
        activePoseVariantIndex = resolveActivePoseVariantIndex()
        refreshTrackedJointsByCode()
        applyFlowOverridesFromItem(
          flowItems?.firstOrNull() as? TrainingFlowItem.Exercise,
          setNumber = resumeSet,
        )
      }
    accumulatedDayReport = runId?.let { WorkoutRunStore.getAccumulatedReport(it) }
    writeHooks = createWriteHooks()
    engine = buildEngine()
    feedback.onVisualMessage = { visual -> applyVisualMessage(visual) }
    wirePreferences()
    prefetchAudio()
    wireSupervisor()
    wireCountdown()
    wireEngineCallbacks()
    startPoseFrameWorker()
    exerciseConfig?.poseVariants?.getOrNull(activePoseVariantIndex)?.feedbackMessages?.let(feedback::setRandomMessages)
    syncFlowUi()
    if (flowCoordinator == null) {
      maybePromptOrLoadExercise()
    }
    if (plannedWorkout != null) {
      viewModelScope.launch { startPlannedWorkoutIfNeeded() }
    }
  }

  fun startWorkoutExercise() {
    val setNumber = flowCoordinator?.currentSetIndex()
      ?: _state.value.currentSetNumber.coerceAtLeast(1)
    flowCoordinator?.currentExerciseOrNull()?.let { applyFlowOverridesFromItem(it, setNumber) }
    rebuildEngineIfNeeded()
    flowCoordinator?.markExercising()
    maybePromptOrLoadExercise()
    syncFlowUi()
  }

  fun skipRest() {
    restTimerJob?.cancel()
    restNearEndAnnounced = false
    restPaused = false
    flowCoordinator?.skipRest()
    syncFlowUi()
  }

  fun toggleRestPause() {
    if (flowCoordinator?.state?.value !is TrainingSessionFlowCoordinator.State.Rest) return
    restPaused = !restPaused
    _state.update { it.copy(isRestPaused = restPaused) }
  }

  fun addRestTime() {
    flowCoordinator?.extendRest(15_000L)
    syncFlowUi()
  }

  fun stopSession(): Long {
    if (exitTornDown) return 0L
    supervisor.processSignal(SupervisorSignal.StopRequested)
    return engine?.stop()?.durationMs ?: 0L
  }

  private fun onBackPressed() {
    if (_state.value.exitPrompt != null) return
    if (!TrainingSessionExitPolicy.shouldConfirmExit(_state.value)) {
      tearDownForExit(persist = false, abandon = false)
      _effects.tryEmit(TrainingSessionEffect.NavigateBack)
      return
    }
    if (_state.value.runState == SessionRunState.TRAINING) {
      pause()
      pausedForExitPrompt = true
    }
    _state.update { it.copy(exitPrompt = TrainingSessionExitPrompt) }
  }

  private fun onExitContinue() {
    _state.update { it.copy(exitPrompt = null) }
    if (pausedForExitPrompt) {
      pausedForExitPrompt = false
      resume()
    }
  }

  private fun onExitSaveAndExit() {
    _state.update { it.copy(exitPrompt = null) }
    pausedForExitPrompt = false
    MovitTrainingAnalytics.trackSaveAndExit(runId)
    tearDownForExit(persist = true, abandon = false)
    emitExitNavigation()
  }

  private fun onExitEndWorkout() {
    _state.update { it.copy(exitPrompt = null) }
    pausedForExitPrompt = false
    MovitTrainingAnalytics.trackEndWorkout(runId)
    tearDownForExit(persist = false, abandon = true)
    emitExitNavigation()
  }

  /** Save/End must drop Prepare+Training; inactive setup Back stays a single pop. */
  private fun emitExitNavigation() {
    _effects.tryEmit(TrainingSessionEffect.ExitWorkoutJourney)
  }

  /**
   * Quiet teardown for Save/exit / End / inactive-setup pop.
   * Avoids [stopSession] → ShowCompleted → finalize (which would advance the run).
   * Path C does not start finalize; if one is already in flight, detach waits for it (WP-05).
   */
  private fun tearDownForExit(persist: Boolean, abandon: Boolean) {
    if (exitTornDown) return
    exitTornDown = true
    restTimerJob?.cancel()
    restPaused = false
    if (persist) {
      persistOpenRunProgress()
    } else if (abandon) {
      abandonOpenRun()
      writeCoordinator.deleteJournal("$sessionId:$activeSlug")
    }
    detachWriteHooksAfterFinalize()
    runCatching { engine?.stop() }
    setupPoseTiltOwner.release()
    supervisor.reset()
    resetSetupVoiceState()
    readinessGate.reset()
    countdown.release()
  }

  /** WP-05: never detach while finalize/upload is still running on [TrainingFinalizeScope]. */
  private fun detachWriteHooksAfterFinalize() {
    val hooks = writeHooks
    val job = finalizeInFlight
    if (job == null || !job.isActive) {
      hooks.detach()
    } else {
      job.invokeOnCompletion { hooks.detach() }
    }
  }

  private fun persistOpenRunProgress() {
    val workoutKey = resolveWorkoutKey() ?: return
    val relativeIndex = flowCoordinator?.currentItemIndex() ?: 0
    val absoluteIndex = startExerciseIndex + relativeIndex
    val setNumber = flowCoordinator?.currentSetIndex()
      ?: _state.value.currentSetNumber.coerceAtLeast(1)
    val phase = when {
      _state.value.isResting || _state.value.workoutFlowPhase == WorkoutFlowPhase.REST -> "REST"
      _state.value.runState == SessionRunState.TRAINING -> "TRAINING"
      else -> "PRE_EXERCISE"
    }
    if (runId != null) {
      WorkoutRunStore.saveProgress(
        runId = runId,
        exerciseIndex = absoluteIndex,
        currentSet = setNumber,
        exerciseSlug = activeSlug,
        blockPhase = phase,
      )
      accumulatedDayReport?.let { WorkoutRunStore.saveAccumulatedReport(runId, it) }
    } else {
      WorkoutRunStore.saveProgressForWorkout(
        workoutId = workoutKey,
        exerciseIndex = absoluteIndex,
        currentSet = setNumber,
        exerciseSlug = activeSlug,
        blockPhase = phase,
      )
      val activeRunId = WorkoutRunStore.activeForWorkout(workoutKey)?.runId?.value
      if (activeRunId != null && accumulatedDayReport != null) {
        WorkoutRunStore.saveAccumulatedReport(activeRunId, accumulatedDayReport!!)
      }
    }
  }

  private fun abandonOpenRun() {
    when {
      runId != null -> WorkoutRunStore.abandon(runId)
      else -> resolveWorkoutKey()?.let { WorkoutRunStore.abandonActiveForWorkout(it) }
    }
  }

  private fun resolveWorkoutKey(): String? {
    if (runId != null) return WorkoutRunStore.get(runId)?.workoutId
    if (flowCoordinator != null || plannedWorkout != null || uploadContext != null) {
      return sessionId.takeIf { it.isNotBlank() && it != exerciseSlug } ?: sessionId
    }
    return null
  }

  fun onPoseFrame(frame: PoseFrame?) {
    if (!_state.value.requiresCamera()) return
    if (frame == null || !frame.hasPose) {
      lastAbsenceSeenMs = frame?.timestampMs?.takeIf { it > 0L } ?: defaultLifecycleNowMs()
    }
    TrainingPipelineDiagnostics.recordVmIngress(wasConflated = poseFrameWorkerBusy)
    poseFrameChannel.trySend(frame)
  }

  private fun startPoseFrameWorker() {
    poseFrameWorker?.cancel()
    poseFrameWorker = viewModelScope.launch(Dispatchers.Default) {
      for (frame in poseFrameChannel) {
        processPoseFrameOnWorker(frame)
      }
    }
  }

  private fun processPoseFrameOnWorker(frame: PoseFrame?) {
    poseFrameWorkerBusy = true
    try {
      if (_state.value.isCameraSwitching) return
      TrainingPipelineDiagnostics.recordVmProcessed()
      if (frame == null || !frame.hasPose) {
        clearOverlayLandmarks()
        emitPipelineDiagnostics(frame?.timestampMs ?: lastFrameTimestampMs, metrics = null)
        if (!visibilityWarningActive) {
          val timestampMs = frame?.timestampMs?.takeIf { it > 0L }
            ?: lastAbsenceSeenMs.takeIf { it > 0L }
            ?: lastFrameTimestampMs.takeIf { it > 0 }
            ?: sessionStartMs
          supervisor.processSignal(SupervisorSignal.NoPoseFrame(timestampMs))
        }
        return
      }

      val runState = supervisor.state.value
      if (runState == SessionRunState.TRAINING) {
        processTrainingFrameOnWorker(frame)
        return
      }

      // WP-03: close COUNTDOWN→TRAINING TOCTOU — if state flipped to TRAINING since the
      // read above, process on this worker; do not enqueue PoseFrame (would race Main).
      if (supervisor.state.value == SessionRunState.TRAINING) {
        processTrainingFrameOnWorker(frame)
        return
      }

      val angles = frame.angles
      val landmarks = frame.landmarks
      supervisor.processSignal(
        SupervisorSignal.PoseFrame(
          angles = angles,
          landmarks = landmarks,
          isFrontCamera = frame.isFrontCamera,
          timestampMs = frame.timestampMs,
        ),
      )
      if (runState.shouldValidatePose() && exerciseConfig != null) {
        val config = exerciseConfig!!
        val readiness = readinessGate.validate(
          angles = angles,
          landmarks = landmarks,
          exerciseConfig = config,
          poseVariantIndex = activePoseVariantIndex,
          isFrontCamera = frame.isFrontCamera,
        )
        val guidance = readiness.toSetupGuidanceUi(language)
        _state.update {
          it.copy(
            setupProgressPercent = guidance.progressPercent,
            setupPhase = guidance.phase,
            setupGuidance = guidance.actionMessage,
            setupActionMessage = guidance.actionMessage,
            setupCameraTip = guidance.cameraTip,
            setupRegionStatus = guidance.regionStatus,
            setupPostureStatus = guidance.postureStatus,
            setupDirectionStatus = guidance.directionStatus,
            setupJointRows = guidance.jointRows,
            setupReferenceImageUrl = guidance.referenceImageUrl,
            setupInStartPose = guidance.inStartPose,
          )
        }
        deliverSetupVoiceFeedback(readiness)
        val isCountdown = runState == SessionRunState.COUNTDOWN ||
          runState == SessionRunState.RESUME_COUNTDOWN
        if (isCountdown) {
          val hasSceneData = (landmarks?.size ?: 0) >= 33
          val sceneStillValid = hasSceneData && readiness.phase == SetupPhase.ANGLES
          val startPoseStillValid = readinessGate.isCountdownPoseValid(
            angles = angles,
            exerciseConfig = config,
            poseVariantIndex = activePoseVariantIndex,
            landmarks = landmarks,
            isFrontCamera = frame.isFrontCamera,
          )
          if (!sceneStillValid || !startPoseStillValid) {
            supervisor.processSignal(SupervisorSignal.PoseInvalid)
          } else {
            supervisor.processSignal(SupervisorSignal.CountdownPoseValid)
          }
        } else if (readiness.isConfirmed) {
          supervisor.processSignal(SupervisorSignal.PoseConfirmed)
        }
      }
      // WP-07: one metricsSnapshot + one overlay publish per frame (F-05/F-06); no duplicate refresh.
      val metrics = engine?.metricsSnapshot()
      publishOverlayState(frame, runState, metrics)
      maybeDeliverRandomMessage(frame.timestampMs, metrics)
      emitPipelineDiagnostics(frame.timestampMs, metrics)
    } finally {
      poseFrameWorkerBusy = false
    }
  }

  /** Sole production path into [MovitTrainingEngine.processFrame] (WP-03). */
  private fun processTrainingFrameOnWorker(frame: PoseFrame) {
    supervisor.onTrainingPoseFrameProcessed()
    updateSessionElapsed(frame.timestampMs)
    latestTrainingAngles = frame.angles
    engine?.processFrame(frame)
    val metrics = engine?.metricsSnapshot()
    publishOverlayState(frame, SessionRunState.TRAINING, metrics)
    maybeDeliverRandomMessage(frame.timestampMs, metrics)
    emitPipelineDiagnostics(frame.timestampMs, metrics)
  }

  private fun clearOverlayLandmarks() {
    if (!TrainingUiStateSplitFlags.r1OverlayFlowEnabled) {
      _overlay.value = TrainingOverlayState()
      return
    }
    _overlay.update { it.copy(landmarks = emptyList(), romIndicators = emptyList()) }
  }

  private fun publishOverlayState(
    frame: PoseFrame,
    runState: SessionRunState,
    metrics: MovitTrainingEngine.EngineMetrics?,
  ) {
    if (!TrainingUiStateSplitFlags.r1OverlayFlowEnabled) {
      _overlay.value = TrainingOverlayState()
      return
    }
    val landmarks = frame.landmarks?.map { lm ->
      SkeletonLandmarkPoint(lm.x, lm.y, lm.isVisible())
    }
    val jointStateInfos = metrics?.jointStateInfos ?: emptyMap()
    val anySideDimmed = metrics?.anySideDimmedJointCodes ?: emptySet()
    val parity = buildSkeletonOverlayParityState(
      runState = runState,
      setupPhase = _state.value.setupPhase,
      jointStateInfos = jointStateInfos,
      anySideDimmedJointCodes = anySideDimmed,
      positionErrors = metrics?.positionErrors ?: emptyList(),
      isBilateralExercise = metrics?.isBilateralExercise ?: (exerciseConfig?.isBilateral == true),
      isBilateralFlipped = metrics?.isBilateralFlipped ?: (engine?.isBilateralFlipped == true),
      bilateralSide = metrics?.bilateralSide ?: engine?.bilateralSide,
      setupJointRows = _state.value.setupJointRows,
      language = language,
    )
    val romIndicators = buildSkeletonRomIndicators(
      landmarks = landmarks,
      jointStateInfos = jointStateInfos,
      anySideDimmedJointCodes = anySideDimmed,
      isBilateralFlipped = parity.isBilateralFlipped,
      indicatorType = trainingPreferences.snapshot().indicatorType,
    )
    _overlay.value = TrainingOverlayState(
      landmarks = landmarks,
      analysisWidth = frame.analysisImageWidth,
      analysisHeight = frame.analysisImageHeight,
      mirrorPreview = frame.isFrontCamera,
      jointVisuals = parity.jointVisuals,
      romIndicators = romIndicators,
      parity = parity,
    )
  }

  private fun updateSessionElapsed(timestampMs: Long) {
    if (timestampMs > 0L) lastFrameTimestampMs = timestampMs
    if (sessionStartMs == 0L && timestampMs > 0L) {
      sessionStartMs = timestampMs
    }
    if (timestampMs > 0L) {
      if (lastTrainingTimestampMs > 0L) {
        activeElapsedMs += (timestampMs - lastTrainingTimestampMs).coerceAtLeast(0L)
      }
      lastTrainingTimestampMs = timestampMs
      lastElapsedMs = activeElapsedMs.coerceAtLeast(0L)
      val label = formatElapsed(lastElapsedMs)
      // WP-07: chrome label only when the displayed second changes.
      _state.update { current ->
        if (current.elapsedLabel == label) current else current.copy(elapsedLabel = label)
      }
    }
  }

  private fun emitPipelineDiagnostics(
    timestampMs: Long,
    metrics: MovitTrainingEngine.EngineMetrics?,
  ) {
    if (!TrainingPipelineDiagnostics.isEnabled()) return
    val nowMs = timestampMs.takeIf { it > 0L } ?: trainingWallClockMs()
    TrainingPipelineDiagnostics.maybeEmitPeriodic(
      nowMs = nowMs,
      runState = supervisor.state.value,
      phase = metrics?.phase?.name,
      repCount = metrics?.repCount ?: _state.value.repCount,
      targetReps = metrics?.targetReps ?: _state.value.targetReps,
      formScore = metrics?.liveFormScore?.toInt() ?: _state.value.liveFormPercent,
      droppedEngine = engine?.droppedFrameCount() ?: 0,
      droppedSupervisor = supervisor.droppedActionCount,
      cameraActive = _state.value.requiresCamera(),
    )
  }

  fun onCameraReady() {
    TrainingPipelineDiagnostics.logMilestone("camera bound")
    _state.update {
      it.copy(
        isCameraReady = true,
        isCameraSwitching = false,
        errorMessage = null,
      )
    }
  }

  fun onCameraSwitchStarted() {
    lastTrainingTimestampMs = 0L
    _state.update { TrainingCameraSwitchPolicy.onSwitchStarted(it) }
    _overlay.value = TrainingOverlayState()
  }

  fun onCameraError(message: String) {
    _state.update {
      it.copy(
        errorMessage = message,
        isCameraReady = false,
        isCameraSwitching = false,
      )
    }
  }

  fun pause() = supervisor.processSignal(SupervisorSignal.PauseRequested)
  fun resume() = supervisor.processSignal(SupervisorSignal.ResumeRequested)
  fun stop() = supervisor.processSignal(SupervisorSignal.StopRequested)

  /** Host lifecycle: app moved to background while training. */
  fun onHostBackgrounded(nowMs: Long = lastFrameTimestampMs.takeIf { it > 0 } ?: sessionStartMs) {
    val wasTraining = _state.value.runState == SessionRunState.TRAINING
    phasePauseSnapshot = TrainingSessionLifecyclePolicy.onHostPaused(
      wasTraining = wasTraining,
      nowMs = nowMs,
    )
    if (wasTraining) {
      pause()
    }
    // P1.4: durable enough for process recreation — checkpoint run cursor while backgrounded.
    if (TrainingSessionExitPolicy.hasProgressWorthSaving(_state.value) ||
      TrainingSessionExitPolicy.shouldConfirmExit(_state.value)
    ) {
      persistOpenRunProgress()
    }
  }

  /** Host lifecycle: app returned to foreground — always resume when training was paused (G-05). */
  fun onHostForegrounded(nowMs: Long = lastFrameTimestampMs.takeIf { it > 0 } ?: sessionStartMs) {
    when (TrainingSessionLifecyclePolicy.onHostResumed(phasePauseSnapshot, nowMs)) {
      PhaseResumeAction.RESUMED -> if (phasePauseSnapshot?.wasTraining == true) resume()
      PhaseResumeAction.NONE -> Unit
    }
    phasePauseSnapshot = null
  }

  fun buildAssessmentResult(summary: ExerciseWorkoutSummary): AssessmentTrainingResult? {
    val upload = writeHooks.finalizeUpload(lastFrameTimestampMs.takeIf { it > 0 } ?: sessionStartMs)
    return writeHooks.buildAssessmentResult(summary, upload)
  }

  fun buildSessionReport(
    upload: WorkoutUpload,
    summary: ExerciseWorkoutSummary,
  ): MovitSessionReport? {
    val config = exerciseConfig ?: return null
    return writeHooks.buildSessionReport(upload, summary, config)
  }

  private fun wirePreferences() {
    trainingPreferences.state
      .drop(1) // G-03: init already built engine from snapshot prefs
      .onEach { prefs ->
        val intensity = CoachIntensity.from(prefs.coachIntensity)
        feedback.coachIntensity = intensity
        feedback.voiceEnabled = prefs.voiceFeedbackEnabled
        val newPolicy = TimingPolicy.withCoachIntensity(intensity)
        if (canRebuildEngineNow()) {
          timingPolicy = newPolicy
          pendingTimingPolicy = null
          rebuildEngineIfNeeded()
        } else if (newPolicy != timingPolicy) {
          pendingTimingPolicy = newPolicy
        }
      }
      .launchIn(viewModelScope)
  }

  private fun prefetchAudio() {
    viewModelScope.launch {
      TrainingSessionAudioHooks.prefetchOnSessionOpen(
        prefetchRunner = audioPrefetchRunner,
        exerciseSlug = activeSlug,
        workoutTemplateId = uploadContext?.workoutTemplateId,
      )
    }
  }

  private fun wireSupervisor() {
    supervisor.actions.onEach { handleSupervisorAction(it) }.launchIn(viewModelScope)
    var previousRunState: SessionRunState? = null
    supervisor.state.onEach { runState ->
      val prev = previousRunState
      previousRunState = runState
      // WP-16 T1: hold tilt sensor during setup/countdown (legacy "setup-pose" owner).
      setupPoseTiltOwner.onRunState(runState)
      if (prev != null && prev != runState) {
        TrainingPipelineDiagnostics.logMilestone("supervisor $prev -> $runState")
      }
      if (prev?.isSetupPose() == true && !runState.isSetupPose()) {
        resetSetupVoiceState()
      }
      _state.update {
        it.copy(
          runState = runState,
          isComplete = it.isComplete || runState == SessionRunState.COMPLETED,
        )
      }
      updateReplaySampler(runState)
    }.launchIn(viewModelScope)

    flowCoordinator?.state?.onEach { syncFlowUi() }?.launchIn(viewModelScope)
  }

  private fun wireCountdown() {
    countdown.setListener(object : CountdownController.CountdownListener {
      override fun onTick(secondsRemaining: Int) {
        _state.update { it.copy(countdownValue = secondsRemaining) }
      }

      override fun onFinish() {
        supervisor.processSignal(SupervisorSignal.CountdownFinished)
      }

      override fun onCancelled() {
        _state.update { it.copy(countdownValue = null, countdownFrozen = false) }
      }

      override fun onFrozen() {
        applyVignetteCue(feedbackEventRouter.routeCountdownFrozen())
        _state.update { it.copy(countdownFrozen = true) }
      }

      override fun onUnfrozen() {
        applyVignetteCue(feedbackEventRouter.routeCountdownUnfrozen())
        _state.update { it.copy(countdownFrozen = false) }
      }
    })

    countdown.audioProvider = object : CountdownController.CountdownAudioProvider {
      override suspend fun playPoseConfirmed() {
        feedback.submit(
          FeedbackSignal(
            kind = FeedbackKind.SYSTEM,
            severity = FeedbackSeverity.SUCCESS,
            text = systemMessage("training_pose_confirmed", "ممتاز، استعد!", "Great, get ready!"),
            dedupeKey = "training_pose_confirmed",
            forceAudible = true,
            allowVisual = false,
          ),
        )
      }

      override suspend fun playCountdownNumber(secondsRemaining: Int) {
        feedback.submit(countdownNumberSignal(secondsRemaining))
      }

      override suspend fun playGo() {
        feedback.submit(countdownGoSignal())
      }
    }
  }

  private fun wireEngineCallbacks() {
    engine?.onHoldStatusChanged = { status ->
      routeHoldFeedback(status)
      _state.update { it.copy(holdStatus = status) }
      status?.let {
        frameCaptureCoordinator.onHoldStatus(
          it,
          engine?.currentPhase ?: Phase.IDLE,
          latestTrainingAngles,
        )
      }
    }
    engine?.onRepCountChanged = { count, score, isCounted ->
      frameCaptureCoordinator.onRepCompleted(count, isCounted)
      feedbackEventRouter.routeRepCompleted(count, isCounted).signals.forEach(feedback::submit)
      _state.update {
        it.copy(
          repCount = count,
          liveFormPercent = score.toInt().coerceIn(0, 100),
          progressPercent = progressPercent(count, activeTargetReps),
        )
      }
    }
    engine?.onPhaseChanged = { phase ->
      _state.update { it.copy(phaseLabel = phase.toDisplayLabel()) }
      frameCaptureCoordinator.onPhaseChanged(phase, repInProgress(), latestTrainingAngles)
    }
    engine?.onTargetReached = {
      feedback.submit(feedbackEventRouter.routeTargetReached(_state.value.repCount))
      supervisor.processSignal(SupervisorSignal.TargetReached)
    }
    engine?.onPositionIssuesChanged = { errors, _ ->
      errors.firstOrNull()?.let { top ->
        feedback.submit(
          FeedbackSignal(
            kind = FeedbackKind.POSITION_CHECK,
            severity = FeedbackSeverity.WARNING,
            text = top.message.get(language),
            dedupeKey = "position:${top.checkId}",
            messageCode = top.checkId,
            audioUrl = top.message.getAudioUrl(language),
          ),
        )
      }
    }
    engine?.onPresenceEvent = { event -> handlePresenceEvent(event) }
    engine?.onJointStateMessage = { jointCode, state, zone ->
      frameCaptureCoordinator.onJointState(
        jointCode,
        state,
        repInProgress(),
        engine?.currentPhase ?: Phase.IDLE,
        latestTrainingAngles,
      )
      submitJointStateMessage(jointCode, state, zone)
    }
    engine?.onJointErrorFeedback = { error, message ->
      frameCaptureCoordinator.onJointError(
        error,
        repInProgress(),
        engine?.currentPhase ?: Phase.IDLE,
        latestTrainingAngles,
      )
      submitJointErrorFeedback(error, message)
    }
    engine?.onRepIncomplete = ::submitRepIncompleteFeedback
  }

  private fun submitRepIncompleteFeedback(reason: RepIncompleteReason) {
    if (shouldDiscardRepAttemptOnIncomplete(reason)) {
      writeHooks.discardCurrentRepAttempt()
    }
    applyVignetteCue(VignetteCue.WARNING)
    val copy = RepIncompleteFeedback.defaultCopy(reason)
    val code = RepIncompleteFeedback.messageCode(reason)
    feedback.submit(
      RepIncompleteFeedback.toSignal(
        reason = reason,
        text = systemMessage(code, copy.ar, copy.en),
      ),
    )
  }

  private fun submitJointStateMessage(
    jointCode: String,
    state: JointState,
    zone: ZoneType,
  ) {
    val tracked = trackedJointsByCode[jointCode] ?: return
    val phaseName = engine?.currentPhase?.name
    val message = tracked.getMessagesForState(state, zone, phaseName).firstOrNull() ?: return
    val text = message.get(language)
    if (text.isBlank()) return
    val severity = when (state) {
      JointState.DANGER -> FeedbackSeverity.CRITICAL
      JointState.WARNING -> FeedbackSeverity.WARNING
      JointState.PAD -> FeedbackSeverity.TIP
      JointState.NORMAL -> FeedbackSeverity.INFO
      JointState.PERFECT -> FeedbackSeverity.MOTIVATION
      JointState.TRANSITION -> FeedbackSeverity.INFO
    }
    feedback.submit(
      FeedbackSignal(
        kind = FeedbackKind.JOINT_QUALITY,
        severity = severity,
        text = text,
        dedupeKey = "state:$jointCode:$state",
        messageCode = jointCode,
        audioUrl = message.getAudioUrl(language),
        activeKey = if (severity.priority >= FeedbackSeverity.WARNING.priority) "correction" else "state:$jointCode:$state",
      ),
    )
  }

  private fun submitJointErrorFeedback(error: JointError, message: LocalizedText?) {
    val text = message?.get(language)?.takeIf { it.isNotBlank() }
      ?: jointErrorFallbackText(error)
    val severity = when (error.state) {
      JointState.DANGER -> FeedbackSeverity.CRITICAL
      JointState.WARNING -> FeedbackSeverity.WARNING
      else -> FeedbackSeverity.ERROR
    }
    feedback.submit(
      FeedbackSignal(
        kind = FeedbackKind.JOINT_QUALITY,
        severity = severity,
        text = text,
        dedupeKey = "joint:${error.jointCode}:${error.errorType}",
        messageCode = error.jointCode,
        audioUrl = message?.getAudioUrl(language),
        activeKey = "correction",
      ),
    )
  }

  private fun jointErrorFallbackText(error: JointError): String {
    val key = "joint:${error.jointCode}:${error.errorType}"
    val (defaultAr, defaultEn) = when (error.errorType) {
      ErrorType.TOO_HIGH -> "ارفع أكثر" to "Raise higher"
      ErrorType.TOO_LOW -> "اخفض أكثر" to "Lower more"
    }
    return SystemMessageRegistry.get(key, defaultAr, defaultEn).display(language)
  }

  private suspend fun startPlannedWorkoutIfNeeded() {
    val planned = plannedWorkout ?: return
    if (plannedWorkoutStarted) return
    plannedWorkoutStarted = true
    writeHooks.startPlannedWorkout(
      workoutId = planned.plannedWorkoutId,
      programId = planned.programId,
      weekNumber = planned.weekNumber,
      dayNumber = planned.dayNumber,
    )
  }

  private fun handleSupervisorAction(action: SupervisorAction) {
    when (action) {
      is SupervisorAction.StartEngine -> {
        lastTrainingTimestampMs = 0L
        engine?.start()
        engine?.let { eng ->
          exerciseConfig?.let { config ->
            val restoredReps = writeHooks.attach(eng, config)
            if (restoredReps > 0) {
              eng.seedCompletedRepCount(restoredReps)
              _state.update {
                it.copy(
                  repCount = restoredReps,
                  progressPercent = progressPercent(restoredReps, activeTargetReps),
                )
              }
            }
          }
        }
      }
      is SupervisorAction.PauseEngine -> {
        lastTrainingTimestampMs = 0L
        engine?.pause()
      }
      is SupervisorAction.ResumeEngine -> {
        lastTrainingTimestampMs = 0L
        engine?.resume()
      }
      is SupervisorAction.ResumeFromVisibilityPause -> {
        lastTrainingTimestampMs = 0L
        engine?.resume()
      }
      is SupervisorAction.StopEngine -> {
        lastTrainingTimestampMs = 0L
        // G-06: finalizeCurrentExercise (ShowCompleted) owns engine.stop()
      }
      is SupervisorAction.ResetEngine -> {
        activeElapsedMs = 0L
        lastElapsedMs = 0L
        lastTrainingTimestampMs = 0L
        engine?.stop()
        engine?.start()
        _state.update { it.copy(elapsedLabel = formatElapsed(lastElapsedMs)) }
      }
      is SupervisorAction.StartCountdown -> countdown.start(viewModelScope)
      is SupervisorAction.CancelCountdown -> countdown.cancel()
      is SupervisorAction.FreezeCountdown -> countdown.freeze()
      is SupervisorAction.UnfreezeCountdown -> countdown.unfreeze()
      is SupervisorAction.ShowAutoPaused -> {
        _state.update {
          it.copy(
            pauseReason = action.reason,
          )
        }
        if (action.reason == PauseReason.VISIBILITY) {
          feedback.submit(
            FeedbackSignal(
              kind = FeedbackKind.VISIBILITY,
              severity = FeedbackSeverity.WARNING,
              text = systemMessage(
                "training_session_auto_pause_visibility",
                "ارجع للإطار",
                "Step back into frame",
              ),
              dedupeKey = "visibility:pause",
              allowVisual = true,
            ),
          )
        }
      }
      is SupervisorAction.ShowNoPoseWarning -> {
        if (visibilityWarningActive) return
        _state.update { it.copy(noPoseWarningMs = action.elapsedMs) }
        feedback.submit(
          FeedbackSignal(
            kind = FeedbackKind.SYSTEM,
            severity = FeedbackSeverity.WARNING,
            text = systemMessage(
              "training_session_auto_pause_nopose",
              "قف أمام الكاميرا",
              "Step into frame",
            ),
            dedupeKey = "nopose:warn",
            allowVoice = false,
            allowVisual = true,
          ),
        )
      }
      is SupervisorAction.ShowSetupNoPoseHint -> {
        _state.update {
          it.copy(
            setupGuidance = systemMessage(
              "training_session_setup_no_pose",
              "قف أمام الكاميرا وتأكد أن جسمك كاملاً ظاهر",
              "Stand in front of the camera with your full body visible",
            ),
            setupProgressPercent = 0,
          )
        }
        clearOverlayLandmarks()
      }
      is SupervisorAction.ShowCompleted -> {
        finalizeCurrentExercise()
      }
      else -> Unit
    }
  }

  private fun finalizeCurrentExercise() {
    frameCaptureCoordinator.stopReplaySampler()
    val summary = engine?.stop()
    val hooks = writeHooks
    val upload = hooks.finalizeUpload(lastFrameTimestampMs.takeIf { it > 0 } ?: sessionStartMs)
    val setsCompleted = _state.value.currentSetNumber
    val totalSets = _state.value.totalSets
    val holdData = engine?.snapshotHoldReportData()
    val sessionQuality = resolveSessionQualityMeta()
    val config = exerciseConfig
    val captures = frameCaptureCoordinator
    val job = TrainingFinalizeScope.launch {
      captures.awaitPendingCaptures()
      if (upload != null && summary != null) {
        accumulateDayReport(upload, summary, setsCompleted, totalSets)
        persistAndEnqueueExerciseResults(
          upload = upload,
          summary = summary,
          hooks = hooks,
          config = config,
          holdData = holdData,
          sessionQuality = sessionQuality,
          setNumber = setsCompleted,
        )
        hooks.finalizeExercise(upload)
      }
      if (!viewModelScope.isActive) return@launch
      advanceAfterExerciseFinalize(hooks)
    }
    trackFinalizeInFlight(job)
  }

  private fun trackFinalizeInFlight(job: Job) {
    finalizeInFlight = job
    job.invokeOnCompletion {
      if (finalizeInFlight === job) finalizeInFlight = null
    }
  }

  private suspend fun advanceAfterExerciseFinalize(hooks: TrainingSessionWriteHooks) {
    if (flowCoordinator != null) {
      supervisor.reset()
      resetSetupVoiceState()
      readinessGate.reset()
      countdown.release()
      hooks.detach()
      flowCoordinator.onExerciseCompleted()
      when (flowCoordinator.state.value) {
        TrainingSessionFlowCoordinator.State.WorkoutComplete -> {
          finalizeWorkoutRun()
          _state.update {
            it.copy(
              isComplete = true,
              isWorkoutComplete = true,
              runState = SessionRunState.COMPLETED,
            )
          }
          _state.value.reportDetailId?.let { reportId ->
            _effects.tryEmit(TrainingSessionEffect.ViewReport(reportId))
          }
        }
        is TrainingSessionFlowCoordinator.State.Rest -> {
          startRestTimer()
          syncFlowUi()
        }
        else -> {
          reloadForNextFlowItem()
          syncFlowUi()
        }
      }
    } else {
      finalizeWorkoutRun()
      _state.update {
        it.copy(
          isComplete = true,
        )
      }
      _state.value.reportDetailId?.let { reportId ->
        _effects.tryEmit(TrainingSessionEffect.ViewReport(reportId))
      }
    }
  }

  private fun accumulateDayReport(
    upload: WorkoutUpload,
    summary: ExerciseWorkoutSummary,
    setsCompleted: Int,
    totalSets: Int,
  ) {
    val config = exerciseConfig ?: return
    accumulatedDayReport = accumulatedDayReport?.let { existing ->
      MovitSessionReportBuilder.mergeExercise(
        existing = existing,
        upload = upload,
        summary = summary,
        exerciseSlug = activeSlug,
        exerciseName = config.name,
        setsCompleted = setsCompleted,
        totalSets = totalSets,
      )
    } ?: MovitSessionReportBuilder.fromExerciseExecution(
        upload = upload,
        summary = summary,
        exerciseSlug = activeSlug,
        exerciseName = config.name,
        setsCompleted = setsCompleted,
        totalSets = totalSets,
      )
    // P1: durable with the open run so Save/exit + VM recreate keep prior work.
    val persistRunId = runId
      ?: resolveWorkoutKey()?.let { WorkoutRunStore.activeForWorkout(it)?.runId?.value }
    if (persistRunId != null) {
      WorkoutRunStore.saveAccumulatedReport(persistRunId, accumulatedDayReport!!)
    }
  }

  private suspend fun finalizeWorkoutRun() {
    val report = accumulatedDayReport ?: return
    val planned = plannedWorkout
    val reportId = runId
      ?: uploadContext?.workoutGroupId
      ?: planned?.plannedWorkoutId
      ?: sessionId.takeIf { flowCoordinator != null }
      ?: return

    if (planned != null) {
      writeHooks.completePlannedDay(
        workoutId = planned.plannedWorkoutId,
        report = report,
        programId = planned.programId,
        weekNumber = planned.weekNumber,
        dayNumber = planned.dayNumber,
        workoutGroupId = uploadContext?.workoutGroupId ?: runId,
        onOutcome = { recordWriteOutcome(it, TrainingSessionWriteDiagnostics.WriteKind.PLANNED_COMPLETE) },
      )
    }

    TrainingSessionReportCache.putSession(reportId, report)
    val reportTarget = when {
      planned != null -> ReportTarget.ProgramDay(
        reportId = reportId,
        plannedWorkoutId = planned.plannedWorkoutId,
        programId = planned.programId,
        weekNumber = planned.weekNumber,
        dayNumber = planned.dayNumber,
      )
      runId != null || flowCoordinator != null ->
        ReportTarget.WorkoutRun(reportId = reportId, runId = runId ?: reportId)
      else -> ReportTarget.Exercise(reportId = reportId)
    }
    runId?.let {
      WorkoutRunStore.complete(it, reportTarget)
      MovitTrainingAnalytics.trackCompleteRun(it)
    }
    markReportAvailable(reportId, asSessionReport = true)
    applyWriteStatus()
  }

  /** @deprecated use [finalizeWorkoutRun] — kept as alias for planned-only call sites. */
  private suspend fun finalizePlannedWorkoutDay() = finalizeWorkoutRun()

  private fun recordWriteOutcome(
    result: com.movit.shared.AppResult<String>,
    kind: TrainingSessionWriteDiagnostics.WriteKind,
  ) {
    writeDiagnostics.recordEnqueue(result, kind)
    applyWriteStatus()
  }

  private fun applyWriteStatus() {
    val status = writeDiagnostics.snapshot()
    _state.update {
      it.copy(
        writeStatus = status,
        uploadNotice = status.userNoticeKey?.let { key -> resolveUploadNotice(key) },
      )
    }
  }

  private fun resolveUploadNotice(key: String): String = when (key) {
    TrainingSessionWriteDiagnostics.USER_NOTICE_UPLOAD_PENDING -> systemMessage(
      key,
      "ستُرفع النتائج عند الاتصال",
      "Results will upload when you're back online",
    )
    TrainingSessionWriteDiagnostics.USER_NOTICE_UPLOAD_FAILED -> systemMessage(
      key,
      "تعذّر حفظ النتائج — سجّل الدخول وحاول مجدداً",
      "Could not save results — sign in and try again",
    )
    else -> key
  }

  private fun resolveSessionQualityMeta() = writeHooks.resolveSessionQualityMeta(
    visibilityPauseCount = visibilityPauseCount,
    cameraWarningCount = cameraWarningCount,
    ingressFramesDropped = engine?.droppedFrameCount() ?: 0,
  )

  private fun updateReplaySampler(runState: SessionRunState) {
    if (runState == SessionRunState.TRAINING && exerciseConfig?.isHoldExercise() != true) {
      frameCaptureCoordinator.startReplaySampler { repInProgress() }
    } else {
      frameCaptureCoordinator.stopReplaySampler()
    }
  }

  private fun syncFrameEvidenceToWriteHooks(hooks: TrainingSessionWriteHooks = writeHooks) {
    hooks.peakFrameCaptures = frameCaptureCoordinator.captures()
    hooks.repReplayClips = frameCaptureCoordinator.replayClips()
  }

  /**
   * WP-05 / H-02+H-03: one [syncFrameEvidenceToWriteHooks] + one [postReport] for cache and enqueue.
   */
  private suspend fun persistAndEnqueueExerciseResults(
    upload: WorkoutUpload,
    summary: ExerciseWorkoutSummary,
    hooks: TrainingSessionWriteHooks,
    config: ExerciseConfig?,
    holdData: MovitHoldReportData?,
    sessionQuality: SessionQualityMeta?,
    setNumber: Int,
  ) {
    if (config == null) return
    syncFrameEvidenceToWriteHooks(hooks)
    val postReport = hooks.buildPostTrainingReport(
      upload,
      summary,
      config,
      sessionQuality = sessionQuality,
      holdData = holdData,
      setNumber = setNumber,
      repsTarget = activeTargetReps,
    )
    val sessionExerciseKey = "$sessionId:$activeSlug"
    TrainingSessionReportCache.put(
      upload.id,
      postReport,
      sessionExerciseKey = sessionExerciseKey,
      setNumber = setNumber,
    )
    if (viewModelScope.isActive && !isWorkoutRunSession()) {
      markReportAvailable(upload.id)
    }
    val result = hooks.enqueueExecutionUpload(
      upload = upload,
      context = uploadContext?.context,
      workoutGroupId = uploadContext?.workoutGroupId,
      workoutTemplateId = uploadContext?.workoutTemplateId,
      legacyReport = postReport,
    )
    if (viewModelScope.isActive) {
      recordWriteOutcome(result, TrainingSessionWriteDiagnostics.WriteKind.EXECUTION_UPLOAD)
    }
    when (result) {
      is com.movit.shared.AppResult.Success -> {
        val reportId = result.value
        if (reportId != upload.id) {
          TrainingSessionReportCache.rekeyPostTraining(upload.id, reportId)
        }
        TrainingSessionReportCache.put(
          reportId,
          postReport.copy(id = reportId, workoutId = reportId),
          sessionExerciseKey = sessionExerciseKey,
          setNumber = setNumber,
        )
        if (viewModelScope.isActive && !isWorkoutRunSession()) {
          markReportAvailable(reportId)
        }
      }
      is com.movit.shared.AppResult.Failure -> Unit
    }
  }

  private fun isWorkoutRunSession(): Boolean =
    flowCoordinator != null || runId != null || uploadContext?.workoutGroupId != null

  private fun markReportAvailable(reportId: String, asSessionReport: Boolean = false) {
    TrainingPipelineDiagnostics.logMilestone("report ready id=$reportId session=$asSessionReport")
    _state.update { it.copy(reportDetailId = reportId) }
  }

  private fun reloadForNextFlowItem() {
    val exercise = flowCoordinator?.currentExerciseOrNull() ?: return
    val nextSetNumber = (flowCoordinator?.state?.value as? TrainingSessionFlowCoordinator.State.PreExercise)?.setNumber
      ?: _state.value.currentSetNumber
    applyFlowExercise(exercise, nextSetNumber)
    visibilityPauseCount = 0
    cameraWarningCount = 0
    visibilityWarningActive = false
    lastRandomMessageCheckMs = 0L
    applyPendingTimingPolicyIfNeeded()
    engine?.stop()
    writeHooks.detach()
    writeHooks = createWriteHooks()
    frameCaptureCoordinator = createFrameCaptureCoordinator()
    frameCaptureCoordinator.beginSet(nextSetNumber)
    engine = buildEngine()
    wireEngineCallbacks()
    exerciseConfig?.poseVariants?.getOrNull(activePoseVariantIndex)?.feedbackMessages?.let(feedback::setRandomMessages)
    feedback.resetAll()
    feedbackEventRouter.reset()
    previousHoldState = null
    sessionStartMs = 0L
    lastElapsedMs = 0L
    activeElapsedMs = 0L
    lastTrainingTimestampMs = 0L
    _state.update {
      it.copy(
        exerciseSlug = activeSlug,
        exerciseName = resolveExerciseName(),
        targetReps = activeTargetReps,
        // WP-01: also true when buildEngine rejected invalid config.
        configUnavailable = exerciseConfig == null || engine == null,
        repCount = 0,
        liveFormPercent = 0,
        progressPercent = 0,
        runState = SessionRunState.IDLE,
        isComplete = false,
        elapsedLabel = formatElapsed(lastElapsedMs),
        // E-08: camera hosts reset elbow + sticky for the next exercise.
        angleTrackingEpoch = it.angleTrackingEpoch + 1,
      )
    }
  }

  private fun applyFlowExercise(exercise: TrainingFlowItem.Exercise, setNumber: Int) {
    activeSlug = exercise.slug
    activeTargetReps = exercise.targetReps
    activeExerciseName = exercise.displayName.ifBlank { activeExerciseName }
    // WP-01: load new config BEFORE resolving variant index (clamp against new variant count).
    exerciseConfig = configRepository.getBySlug(activeSlug)?.config
    activePoseVariantIndex = resolveActivePoseVariantIndex(exercise)
    val variantCount = exerciseConfig?.poseVariants?.size ?: 0
    if (variantCount > 0) {
      activePoseVariantIndex = activePoseVariantIndex.coerceIn(0, variantCount - 1)
    }
    refreshTrackedJointsByCode()
    applyFlowOverridesFromItem(exercise, setNumber)
  }

  private fun refreshTrackedJointsByCode() {
    trackedJointsByCode = exerciseConfig
      ?.poseVariants?.getOrNull(activePoseVariantIndex)
      ?.trackedJoints
      ?.associateBy { it.joint }
      .orEmpty()
  }

  private fun applyFlowOverridesFromItem(exercise: TrainingFlowItem.Exercise?, setNumber: Int) {
    activeTargetDurationSeconds = TrainingFlowEngineOverrides.targetDurationSeconds(exercise)
    activeSessionWeightKg = TrainingFlowEngineOverrides.weightKgForSet(exercise, setNumber)
  }

  private fun startRestTimer() {
    restTimerJob?.cancel()
    restNearEndAnnounced = false
    restPaused = false
    restTimerJob = viewModelScope.launch {
      while (isActive) {
        delay(1_000L)
        if (restPaused) continue
        val completed = flowCoordinator?.tickRest(1_000L) == true
        syncFlowUi()
        maybeAnnounceRestNearEnd()
        if (completed) {
          reloadForNextFlowItem()
          syncFlowUi()
          break
        }
      }
    }
  }

  private fun maybeAnnounceRestNearEnd() {
    val rest = flowCoordinator?.state?.value as? TrainingSessionFlowCoordinator.State.Rest ?: return
    if (restNearEndAnnounced || rest.remainingMs > TrainingSessionFlowCoordinator.REST_NEAR_END_MS) return
    restNearEndAnnounced = true
    feedback.submit(
      FeedbackSignal(
        kind = FeedbackKind.COUNTDOWN,
        severity = FeedbackSeverity.CRITICAL,
        text = systemMessage("training_rest_near_end", "استعد!", "Get ready!"),
        dedupeKey = "rest:near_end",
        activeKey = "rest",
        forceAudible = true,
        allowVisual = false,
      ),
    )
  }

  private fun syncFlowUi() {
    val flowState = flowCoordinator?.state?.value ?: return
    val flowProgress = flowCoordinator.workoutProgressPercent()
    when (flowState) {
      is TrainingSessionFlowCoordinator.State.PreExercise -> {
        frameCaptureCoordinator.beginSet(flowState.setNumber)
        _state.update {
          it.copy(
            workoutFlowPhase = WorkoutFlowPhase.PRE_EXERCISE,
            isResting = false,
            currentSetNumber = flowState.setNumber,
            totalSets = flowState.totalSets,
            restSecondsRemaining = 0,
            nextExerciseName = "",
            restTip = flowState.item.tip,
            workoutFlowProgressPercent = flowProgress,
          )
        }
      }
      is TrainingSessionFlowCoordinator.State.Rest -> {
        _state.update {
          it.copy(
            workoutFlowPhase = WorkoutFlowPhase.REST,
            isResting = true,
            isRestPaused = restPaused,
            currentSetNumber = flowState.setNumber,
            totalSets = flowState.totalSets,
            restSecondsRemaining = ((flowState.remainingMs + 999L) / 1_000L).toInt(),
            nextExerciseName = flowState.nextExercise.displayName,
            restNextTargetReps = flowState.nextExercise.targetReps,
            restNextTargetDurationSeconds = flowState.nextExercise.targetDurationSeconds?.takeIf { it > 0 },
            restNextTargetWeightKg = restTargetWeightKg(flowState),
            restPreviewImageUrl = restPreviewImageUrl(
              slug = flowState.nextExercise.slug,
              variantIndex = flowState.nextExercise.poseVariantIndex,
            ),
            restTip = flowState.tip,
            restContext = flowState.restContext.name,
            workoutFlowProgressPercent = flowProgress,
          )
        }
      }
      TrainingSessionFlowCoordinator.State.Training -> {
        _state.update {
          it.copy(
            workoutFlowPhase = WorkoutFlowPhase.TRAINING,
            isResting = false,
            workoutFlowProgressPercent = flowProgress,
          )
        }
      }
      TrainingSessionFlowCoordinator.State.WorkoutComplete -> {
        _state.update {
          it.copy(
            workoutFlowPhase = WorkoutFlowPhase.WORKOUT_COMPLETE,
            isWorkoutComplete = true,
            isComplete = true,
            workoutFlowProgressPercent = 100,
          )
        }
      }
      TrainingSessionFlowCoordinator.State.Idle -> Unit
    }
  }

  private fun handlePresenceEvent(event: PresenceSupervisorEvent) {
    event.toSupervisorSignal()?.let { supervisor.processSignal(it) }
    when (event) {
      is PresenceSupervisorEvent.VisibilityWarning -> {
        visibilityWarningActive = true
        cameraWarningCount++
        _state.update {
          it.copy(
            visibilityWarningMs = event.remainingBeforePauseMs,
          )
        }
        // F-08: vignette/glass unbound — stop hot-path writes until UI wires them.
        feedback.submit(
          FeedbackSignal(
            kind = FeedbackKind.VISIBILITY,
            severity = FeedbackSeverity.WARNING,
            text = systemMessage(
              "training_visibility_warning",
              "أبقِ المفاصل ظاهرة",
              "Keep joints visible",
            ),
            dedupeKey = "visibility:warn",
            allowVisual = true,
          ),
        )
      }
      is PresenceSupervisorEvent.VisibilityPaused -> {
        visibilityWarningActive = true
        visibilityPauseCount++
      }
      is PresenceSupervisorEvent.VisibilityResumed -> {
        visibilityWarningActive = false
        _state.update {
          it.copy(visibilityWarningMs = 0L, noPoseWarningMs = 0L)
        }
      }
      else -> Unit
    }
  }

  private fun maybeDeliverRandomMessage(
    timestampMs: Long,
    metrics: MovitTrainingEngine.EngineMetrics?,
  ) {
    if (supervisor.state.value != SessionRunState.TRAINING) return
    if (timestampMs - lastRandomMessageCheckMs < 1_000L) return
    lastRandomMessageCheckMs = timestampMs
    val hasActiveErrors = metrics?.jointStateInfos?.values?.any {
      it.state == JointState.WARNING || it.state == JointState.DANGER
    } == true || visibilityWarningActive
    feedback.tryDeliverRandomMessage(hasActiveErrors, language)
  }

  private fun routeHoldFeedback(status: HoldStatus?) {
    val current = status?.state
    val previous = previousHoldState
    previousHoldState = current
    if (current == null || previous == current) return
    val signal = when (current) {
      HoldState.GRACE_PERIOD -> if (previous != HoldState.GRACE_PERIOD) {
        feedbackEventRouter.routeHoldGraceStarted()
      } else {
        null
      }
      HoldState.HOLDING -> if (previous == HoldState.GRACE_PERIOD) {
        feedbackEventRouter.routeHoldResumed()
      } else {
        null
      }
      HoldState.COMPLETED -> if (previous != HoldState.COMPLETED) {
        feedbackEventRouter.routeHoldCompleted(status.elapsedMs)
      } else {
        null
      }
      HoldState.FAILED -> if (previous != HoldState.FAILED) {
        feedbackEventRouter.routeHoldFailed()
      } else {
        null
      }
      HoldState.IDLE -> null
    }
    signal?.let(feedback::submit)
  }

  // F-08: glass/vignette not composed on TrainingSessionScreen — no-op until wired.
  private fun applyVignetteCue(@Suppress("UNUSED_PARAMETER") cue: VignetteCue) = Unit

  // F-08: glass message unbound — stop hot-path writes until UI wires them.
  private fun applyVisualMessage(@Suppress("UNUSED_PARAMETER") visual: FeedbackVisualMessage) = Unit

  private fun buildEngine(): MovitTrainingEngine? {
    val config = exerciseConfig ?: return null
    // WP-01: gate on validation before constructing engine (avoids uncaught error()).
    val issues = config.validationIssues(activePoseVariantIndex)
    if (issues.isNotEmpty()) {
      TrainingPipelineDiagnostics.logMilestone(
        "buildEngine blocked slug=$activeSlug issues=${issues.joinToString()}",
      )
      _state.update { it.copy(configUnavailable = true) }
      return null
    }
    // Refresh weight for the current set when advancing sets of the same exercise.
    val flowExercise = flowCoordinator?.currentExerciseOrNull()
      ?: flowItems?.firstOrNull() as? TrainingFlowItem.Exercise
    val setNumber = flowCoordinator?.currentSetIndex()
      ?: _state.value.currentSetNumber.coerceAtLeast(1)
    applyFlowOverridesFromItem(flowExercise, setNumber)
    return MovitTrainingEngine(
      exerciseConfig = config,
      poseVariantIndex = activePoseVariantIndex,
      targetRepsOverride = activeTargetReps,
      sessionWeightKg = activeSessionWeightKg,
      targetDurationSecondsOverride = activeTargetDurationSeconds,
      deviceTiltPort = deviceTiltPort,
      timingPolicy = timingPolicy,
    )
  }

  private fun canRebuildEngineNow(): Boolean {
    val runState = _state.value.runState
    return runState in REBUILD_SAFE_RUN_STATES && engine?.isRunning != true
  }

  private fun applyPendingTimingPolicyIfNeeded() {
    val pending = pendingTimingPolicy ?: return
    timingPolicy = pending
    pendingTimingPolicy = null
  }

  private fun rebuildEngineIfNeeded() {
    if (!canRebuildEngineNow()) return
    applyPendingTimingPolicyIfNeeded()
    if (engine?.isRunning == true) {
      engine?.stop()
    }
    engine = buildEngine()
    wireEngineCallbacks()
  }

  /** P1.5 / UX.3: orphan cleanup + optional resume dialog before setup. */
  private fun maybePromptOrLoadExercise() {
    if (_state.value.resumePrompt != null) return
    val orphan = writeCoordinator.cleanupOrphansAndFindResume(activeSlug)
    if (orphan != null && orphan.completedRepMetrics.isNotEmpty()) {
      pendingResumeJournal = orphan
      _state.update {
        it.copy(
          resumePrompt = TrainingSessionResumePrompt(
            completedReps = orphan.completedRepMetrics.size,
            exerciseSlug = activeSlug,
          ),
        )
      }
      return
    }
    pendingResumeJournal = null
    supervisor.onExerciseLoaded()
  }

  private fun resumePriorSession() {
    val orphan = pendingResumeJournal ?: return
    val hooksSessionId = "$sessionId:$activeSlug"
    if (orphan.sessionId != hooksSessionId) {
      writeCoordinator.checkpointJournal(orphan.copy(sessionId = hooksSessionId))
      writeCoordinator.deleteJournal(orphan.sessionId)
    }
    clearResumePrompt()
    supervisor.onExerciseLoaded()
  }

  private fun discardPriorSession() {
    pendingResumeJournal?.sessionId?.let(writeCoordinator::deleteJournal)
    clearResumePrompt()
    supervisor.onExerciseLoaded()
  }

  private fun clearResumePrompt() {
    pendingResumeJournal = null
    _state.update { it.copy(resumePrompt = null) }
  }

  private fun createWriteHooks(): TrainingSessionWriteHooks = TrainingSessionWriteHooks(
    sessionId = "$sessionId:$activeSlug",
    exerciseSlug = activeSlug,
    writes = writeCoordinator,
    poseVariantIndex = activePoseVariantIndex,
    isAssessmentMode = isAssessmentMode,
    timeProvider = { lastFrameTimestampMs.takeIf { it > 0 } ?: sessionStartMs },
  )

  private fun createFrameCaptureCoordinator(): TrainingFrameCaptureCoordinator =
    TrainingFrameCaptureCoordinator(
      sessionId = "$sessionId:$activeSlug",
      // WP-05 / G-08: pending snapshot jobs must outlive viewModelScope cancel.
      scope = TrainingFinalizeScope,
      snapshotPort = frameSnapshotPort,
    )

  private fun repInProgress(): Int = (engine?.repCount ?: 0) + 1

  private fun resolveExerciseName(): String =
    activeExerciseName.ifBlank { exerciseConfig?.displayName(language).orEmpty() }

  private fun restTargetWeightKg(flowState: TrainingSessionFlowCoordinator.State.Rest): Float? {
    return TrainingFlowEngineOverrides.weightKgForRestPreview(
      exercise = flowState.nextExercise,
      upcomingSetNumber = flowState.setNumber,
      restContext = flowState.restContext,
    )
  }

  private fun restPreviewImageUrl(slug: String, variantIndex: Int): String? {
    if (!MovitData.isInstalled) return null
    val resolvedSlug = MovitData.trainingConfig.resolveAvailableSlug(slug) ?: slug
    val config = MovitData.trainingConfig.getExercise(resolvedSlug) ?: return null
    return config.poseVariants.getOrNull(variantIndex)?.positionImageUrl
      ?: config.poseVariants.firstOrNull()?.positionImageUrl
  }

  private fun resolveActivePoseVariantIndex(
    flowExercise: TrainingFlowItem.Exercise? = flowCoordinator?.currentExerciseOrNull()
      ?: flowItems?.firstOrNull() as? TrainingFlowItem.Exercise,
  ): Int = TrainingPoseVariantResolver.resolve(
    routePoseVariantIndex = routePoseVariantIndex,
    flowExercise = flowExercise,
    variantCount = exerciseConfig?.poseVariants?.size ?: 1,
  )

  private fun systemMessage(key: String, defaultAr: String, defaultEn: String): String =
    SystemMessageRegistry.get(key, defaultAr, defaultEn).display(language)

  private fun countdownNumberSignal(secondsRemaining: Int): FeedbackSignal {
    val key = TrainingFeedbackEventRouter.trainingNumeralKey(secondsRemaining)
    val text = key?.let { systemMessage(it, secondsRemaining.toString(), secondsRemaining.toString()) }
      ?: secondsRemaining.toString()
    return FeedbackSignal(
      kind = FeedbackKind.COUNTDOWN,
      severity = FeedbackSeverity.INFO,
      text = text,
      dedupeKey = "countdown:$secondsRemaining",
      activeKey = "countdown",
      cooldownGroup = "countdown:$secondsRemaining",
      forceAudible = true,
      allowTone = false,
      allowVisual = false,
      allowHaptic = false,
      interruptPolicy = FeedbackInterruptPolicy.INTERRUPT,
    )
  }

  private fun countdownGoSignal(): FeedbackSignal =
    FeedbackSignal(
      kind = FeedbackKind.COUNTDOWN,
      severity = FeedbackSeverity.SUCCESS,
      text = systemMessage("training_countdown_go", "Go!", "Go!"),
      dedupeKey = "countdown:go",
      activeKey = "countdown",
      cooldownGroup = "countdown:go",
      forceAudible = true,
      allowTone = false,
      allowVisual = false,
      allowHaptic = false,
      interruptPolicy = FeedbackInterruptPolicy.INTERRUPT,
    )

  private fun setupSceneToVisibilityMessage(): LocalizedText {
    val remote = SystemMessageRegistry.get(
      "training_setup_scene_to_visibility",
      "الوضع صحيح – جاري التحقق من الرؤية",
      "Position correct – checking visibility",
    )
    return LocalizedText(ar = remote.ar, en = remote.en)
  }

  private fun deliverSetupVoiceFeedback(readiness: SetupReadinessResult) {
    if (!supervisor.state.value.isSetupPose()) return

    val phase = readiness.phase
    val previousPhase = lastSetupPhase
    lastSetupPhase = phase

    if (phase == SetupPhase.ANGLES && previousPhase != null && previousPhase != SetupPhase.ANGLES) {
      SetupFeedbackSignals.phaseGuidance(setupSceneToVisibilityMessage(), language)?.let { signal ->
        feedback.submitSetup(signal)
      }
    }

    if (phase != SetupPhase.ANGLES) {
      val phaseMessage = readiness.phaseMessage ?: return
      if (!setupVoiceGate.shouldSpeakPhaseGuidance(phase)) return
      val signal = SetupFeedbackSignals.phaseGuidance(phaseMessage, language) ?: return
      if (feedback.submitSetup(signal).shouldDeliver) {
        setupVoiceGate.onPhaseGuidanceSpoken(phase)
      }
      return
    }

    val joint = readiness.worstJointGuidance ?: return
    if (joint.level != SetupGuidanceLevel.RED) return
    if (!setupVoiceGate.shouldSpeakJointGuidance(joint)) return
    val signal = SetupFeedbackSignals.jointGuidance(joint, language) ?: return
    if (feedback.submitSetup(signal).shouldDeliver) {
      setupVoiceGate.onJointGuidanceSpoken(joint)
    }
  }

  private fun resetSetupVoiceState() {
    lastSetupPhase = null
    setupVoiceGate.reset()
    feedback.resetSetupFeedback()
  }

  override fun onCleared() {
    restTimerJob?.cancel()
    poseFrameChannel.close()
    poseFrameWorker?.cancel()
    TrainingPipelineDiagnostics.reset()
    // G-08: stop engine (idempotent if finalize already stopped it) before scope teardown.
    runCatching { engine?.stop() }
    setupPoseTiltOwner.release()
    // Pending captures run on TrainingFinalizeScope; in-flight finalize awaits them.
    detachWriteHooksAfterFinalize()
    countdown.release()
    resetSetupVoiceState()
    readinessGate.reset()
    supervisor.reset()
    feedback.release()
    super.onCleared()
  }
}

private val REBUILD_SAFE_RUN_STATES = setOf(SessionRunState.IDLE, SessionRunState.SETUP_POSE)

data class WorkoutUploadContext(
  val workoutGroupId: String,
  val workoutTemplateId: String? = null,
  val context: String = EXPLORE_WORKOUT_CONTEXT,
) {
  companion object {
    const val EXPLORE_WORKOUT_CONTEXT = "explore_workout"
  }
}

enum class WorkoutFlowPhase {
  NONE,
  PRE_EXERCISE,
  REST,
  TRAINING,
  WORKOUT_COMPLETE,
}

data class GlassMessageState(
  val text: String,
  val severity: GlassMessageSeverity,
)

data class TrainingSessionUiState(
  val exerciseSlug: String,
  val exerciseName: String,
  val targetReps: Int,
  /** UX.5 — thin offline banner when the device has no network. */
  val isOffline: Boolean = false,
  val configUnavailable: Boolean = false,
  val workoutFlowEnabled: Boolean = false,
  val workoutFlowPhase: WorkoutFlowPhase = WorkoutFlowPhase.NONE,
  val isResting: Boolean = false,
  val isWorkoutComplete: Boolean = false,
  val currentSetNumber: Int = 1,
  val totalSets: Int = 1,
  val restSecondsRemaining: Int = 0,
  val nextExerciseName: String = "",
  val restNextTargetReps: Int = 0,
  val restNextTargetDurationSeconds: Int? = null,
  val restNextTargetWeightKg: Float? = null,
  val restPreviewImageUrl: String? = null,
  val restTip: String? = null,
  val restContext: String = "",
  val isRestPaused: Boolean = false,
  val runState: SessionRunState = SessionRunState.IDLE,
  val repCount: Int = 0,
  val liveFormPercent: Int = 0,
  val progressPercent: Int = 0,
  val phaseLabel: String = "",
  val setupProgressPercent: Int = 0,
  val setupPhase: String = "",
  val setupGuidance: String? = null,
  val setupActionMessage: String? = null,
  val setupCameraTip: String? = null,
  val setupRegionStatus: SetupAxisStatusUi = SetupAxisStatusUi.PENDING,
  val setupPostureStatus: SetupAxisStatusUi = SetupAxisStatusUi.PENDING,
  val setupDirectionStatus: SetupAxisStatusUi = SetupAxisStatusUi.PENDING,
  val setupJointRows: List<SetupJointGuidanceUi> = emptyList(),
  val setupReferenceImageUrl: String? = null,
  val setupInStartPose: Boolean = false,
  val countdownValue: Int? = null,
  val countdownFrozen: Boolean = false,
  val holdStatus: HoldStatus? = null,
  val pauseReason: PauseReason? = null,
  val noPoseWarningMs: Long = 0L,
  val visibilityWarningMs: Long = 0L,
  val isComplete: Boolean = false,
  val isCameraReady: Boolean = false,
  val isCameraSwitching: Boolean = false,
  val errorMessage: String? = null,
  val elapsedLabel: String = "0:00",
  val reportDetailId: String? = null,
  val workoutFlowProgressPercent: Int = 0,
  val writeStatus: TrainingSessionWriteStatus = TrainingSessionWriteStatus(),
  val uploadNotice: String? = null,
  /** UX.3: non-null while the resume-or-discard dialog is showing. */
  val resumePrompt: TrainingSessionResumePrompt? = null,
  /** P1.4: non-null while Continue / Save and exit / End workout is showing. */
  val exitPrompt: TrainingSessionExitPrompt? = null,
  /** E-08: incremented in reloadForNextFlowItem so camera hosts reset elbow + sticky. */
  val angleTrackingEpoch: Int = 0,
)

data class TrainingSessionResumePrompt(
  val completedReps: Int,
  val exerciseSlug: String,
)

data object TrainingSessionExitPrompt

/** Camera is only needed during live setup/training — not after completion or rest. */
fun TrainingSessionUiState.requiresCamera(): Boolean =
  !isComplete &&
    runState != SessionRunState.COMPLETED &&
    !isResting &&
    workoutFlowPhase != WorkoutFlowPhase.REST

private fun progressPercent(repCount: Int, targetReps: Int): Int =
  if (targetReps <= 0) 0 else ((repCount.toFloat() / targetReps) * 100).toInt().coerceIn(0, 100)

private fun formatElapsed(elapsedMs: Long): String {
  val seconds = (elapsedMs / 1000).coerceAtLeast(0)
  val m = seconds / 60
  val s = seconds % 60
  return "$m:${s.toString().padStart(2, '0')}"
}

private fun LocalizedNameDto.display(lang: String): String =
  if (lang == "ar") ar.ifBlank { en } else en.ifBlank { ar }
