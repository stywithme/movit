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
import com.movit.core.training.diagnostics.TrainingPipelineDiagnostics
import com.movit.core.training.engine.Phase
import com.movit.core.training.engine.RepIncompleteReason
import com.movit.core.training.engine.ZoneType
import com.movit.feature.reports.TrainingSessionReportCache
import com.movit.core.training.engine.ErrorType
import com.movit.core.training.boundary.TrainingFrameSnapshotPort
import com.movit.core.training.engine.JointError
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.JointStateInfo
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
import com.movit.core.training.journal.WorkoutUpload
import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.PoseFrame
import com.movit.core.training.report.AssessmentTrainingResult
import com.movit.core.training.report.MovitSessionReport
import com.movit.core.training.report.MovitSessionReportBuilder
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
import com.movit.core.training.session.SetupReadinessGate
import com.movit.core.training.session.SetupReadinessResult
import com.movit.core.training.session.SetupVoiceGuidanceGate
import com.movit.core.training.session.SetupValidationConfig
import com.movit.core.training.session.SupervisorAction
import com.movit.core.training.session.SupervisorSignal
import com.movit.core.training.session.TrainingFlowItem
import com.movit.core.training.session.TrainingSessionFlowCoordinator
import com.movit.designsystem.components.GlassMessageSeverity
import com.movit.designsystem.components.SkeletonJointQuality
import com.movit.designsystem.components.SkeletonJointVisual
import com.movit.designsystem.components.SkeletonLandmarkPoint
import com.movit.designsystem.components.SkeletonOverlayParityState
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * KMP session VM: supervisor + readiness gate + countdown + engine + feedback (WS-5/WS-7/07.8-B).
 */
class TrainingSessionViewModel(
  private val exerciseSlug: String,
  private val targetReps: Int,
  private val exerciseNameOverride: String = "",
  private val language: String = "en",
  private val sessionId: String = exerciseSlug,
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
  private var activeExerciseName: String = exerciseNameOverride
  private var activePoseVariantIndex: Int = routePoseVariantIndex
  private var exerciseConfig: ExerciseConfig? = configRepository.getBySlug(activeSlug)?.config

  private var timingPolicy: TimingPolicy = TimingPolicy.withCoachIntensity(
    CoachIntensity.from(trainingPreferences.snapshot().coachIntensity),
  )

  private var lastFrameTimestampMs: Long = 0L
  private var writeHooks = createWriteHooks()
  private val writeDiagnostics = TrainingSessionWriteDiagnostics()
  private val exploreBatch = uploadContext?.let {
    WorkoutExecutionBatchCoordinator(
      writes = writeCoordinator,
      context = it.context,
      onWriteOutcome = ::recordWriteOutcome,
    )
  }
  private var phasePauseSnapshot: PhasePauseSnapshot? = null

  private val flowCoordinator = flowItems?.let { TrainingSessionFlowCoordinator(it) }
  private var restTimerJob: Job? = null
  private var restNearEndAnnounced = false

  private val supervisor = SessionSupervisor(setupValidation = setupValidation)
  private val readinessGate = SetupReadinessGate(setupValidation, deviceTiltPort)
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

  private val _state = MutableStateFlow(
    TrainingSessionUiState(
      exerciseSlug = activeSlug,
      exerciseName = resolveExerciseName(),
      targetReps = activeTargetReps,
      configUnavailable = exerciseConfig == null,
      workoutFlowEnabled = flowCoordinator != null,
    ),
  )
  val state: StateFlow<TrainingSessionUiState> = _state.asStateFlow()

  private val _effects = MutableSharedFlow<TrainingSessionEffect>(extraBufferCapacity = 1)
  val effects: SharedFlow<TrainingSessionEffect> = _effects.asSharedFlow()

  fun onEvent(event: TrainingSessionEvent) {
    when (event) {
      TrainingSessionEvent.StartWorkoutExercise -> startWorkoutExercise()
      TrainingSessionEvent.SkipRest -> skipRest()
      TrainingSessionEvent.StopSession -> stopSession()
      is TrainingSessionEvent.PoseFrameReceived -> onPoseFrame(event.frame)
      TrainingSessionEvent.CameraReady -> onCameraReady()
      TrainingSessionEvent.CameraSwitchStarted -> onCameraSwitchStarted()
      is TrainingSessionEvent.CameraError -> onCameraError(event.message)
      TrainingSessionEvent.Pause -> pause()
      TrainingSessionEvent.Resume -> resume()
      TrainingSessionEvent.Stop -> stop()
      is TrainingSessionEvent.HostBackgrounded -> onHostBackgrounded(event.nowMs ?: defaultLifecycleNowMs())
      is TrainingSessionEvent.HostForegrounded -> onHostForegrounded(event.nowMs ?: defaultLifecycleNowMs())
      TrainingSessionEvent.BackPressed -> {
        stopSession()
        _effects.tryEmit(TrainingSessionEffect.NavigateBack)
      }
      TrainingSessionEvent.FinishClicked -> {
        stopSession()
        _effects.tryEmit(TrainingSessionEffect.Finish(_state.value.isWorkoutComplete))
      }
      TrainingSessionEvent.ViewReportClicked -> {
        _state.value.reportDetailId?.let { reportId ->
          _effects.tryEmit(TrainingSessionEffect.ViewReport(reportId))
        }
      }
    }
  }

  private fun defaultLifecycleNowMs(): Long =
    lastFrameTimestampMs.takeIf { it > 0 } ?: sessionStartMs

  init {
    activePoseVariantIndex = resolveActivePoseVariantIndex()
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
    flowCoordinator?.start()
    syncFlowUi()
    if (flowCoordinator == null) {
      supervisor.onExerciseLoaded()
    }
    if (plannedWorkout != null) {
      viewModelScope.launch { startPlannedWorkoutIfNeeded() }
    }
  }

  fun startWorkoutExercise() {
    flowCoordinator?.markExercising()
    supervisor.onExerciseLoaded()
    syncFlowUi()
  }

  fun skipRest() {
    restTimerJob?.cancel()
    restNearEndAnnounced = false
    flowCoordinator?.skipRest()
    syncFlowUi()
  }

  fun stopSession(): Long {
    supervisor.processSignal(SupervisorSignal.StopRequested)
    return engine?.stop()?.durationMs ?: 0L
  }

  fun onPoseFrame(frame: PoseFrame?) {
    if (!_state.value.requiresCamera()) return
    TrainingPipelineDiagnostics.recordVmIngress(wasConflated = false)
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
    if (_state.value.isCameraSwitching) return
    TrainingPipelineDiagnostics.recordVmProcessed()
    emitPipelineDiagnostics(frame?.timestampMs ?: lastFrameTimestampMs)
    if (frame == null || !frame.hasPose) {
      _state.update { it.copy(landmarks = emptyList()) }
      if (!visibilityWarningActive) {
        val timestampMs = frame?.timestampMs?.takeIf { it > 0L }
          ?: lastFrameTimestampMs.takeIf { it > 0 }
          ?: sessionStartMs
        supervisor.processSignal(SupervisorSignal.NoPoseFrame(timestampMs))
      }
      return
    }

    applyPoseLandmarksToUi(frame)

    val runState = supervisor.state.value
    if (runState == SessionRunState.TRAINING) {
      supervisor.onTrainingPoseFrameProcessed()
      updateSessionElapsed(frame.timestampMs)
      latestTrainingAngles = frame.angles
      engine?.processFrame(frame)
      refreshSkeletonOverlay(runState)
      maybeDeliverRandomMessage(frame.timestampMs)
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
      refreshSkeletonOverlay(runState)
      deliverSetupVoiceFeedback(readiness)
      val isCountdown = runState == SessionRunState.COUNTDOWN ||
        runState == SessionRunState.RESUME_COUNTDOWN
      if (isCountdown) {
        val hasSceneData = (landmarks?.size ?: 0) >= 33
        val sceneStillValid = hasSceneData && readiness.phase == SetupPhase.ANGLES
        val startPoseStillValid = readinessGate.isCountdownPoseValid(angles, config, activePoseVariantIndex)
        if (!sceneStillValid || !startPoseStillValid) {
          supervisor.processSignal(SupervisorSignal.PoseInvalid)
        } else {
          supervisor.processSignal(SupervisorSignal.CountdownPoseValid)
        }
      } else if (readiness.isConfirmed) {
        supervisor.processSignal(SupervisorSignal.PoseConfirmed)
      }
    }
    refreshSkeletonOverlay(runState)
    maybeDeliverRandomMessage(frame.timestampMs)
  }

  private fun applyPoseLandmarksToUi(frame: PoseFrame) {
    _state.update {
      it.copy(
        landmarks = frame.landmarks?.map { lm ->
          SkeletonLandmarkPoint(lm.x, lm.y, lm.isVisible())
        },
        skeletonAnalysisWidth = frame.analysisImageWidth,
        skeletonAnalysisHeight = frame.analysisImageHeight,
        skeletonMirrorPreview = frame.isFrontCamera,
      )
    }
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
      _state.update { it.copy(elapsedLabel = formatElapsed(lastElapsedMs)) }
    }
  }

  private fun emitPipelineDiagnostics(timestampMs: Long) {
    val nowMs = timestampMs.takeIf { it > 0L } ?: trainingWallClockMs()
    val engineSnapshot = engine?.metricsSnapshot()
    TrainingPipelineDiagnostics.maybeEmitPeriodic(
      nowMs = nowMs,
      runState = supervisor.state.value,
      phase = engineSnapshot?.phase?.name,
      repCount = engineSnapshot?.repCount ?: _state.value.repCount,
      targetReps = engineSnapshot?.targetReps ?: _state.value.targetReps,
      formScore = engineSnapshot?.liveFormScore?.toInt() ?: _state.value.liveFormPercent,
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
    _state.update { TrainingCameraSwitchPolicy.onSwitchStarted(it) }
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
  }

  /** Host lifecycle: app returned to foreground — apply legacy-style phase continue policy. */
  fun onHostForegrounded(nowMs: Long = lastFrameTimestampMs.takeIf { it > 0 } ?: sessionStartMs) {
    when (
      TrainingSessionLifecyclePolicy.onHostResumed(phasePauseSnapshot, nowMs)
    ) {
      PhaseResumeAction.RESUMED -> if (phasePauseSnapshot?.wasTraining == true) resume()
      PhaseResumeAction.PHASE_RESTARTED_NO_CONTINUE,
      PhaseResumeAction.PHASE_RESTARTED_TIMEOUT,
      -> {
        supervisor.processSignal(SupervisorSignal.StopRequested)
        supervisor.reset()
        resetSetupVoiceState()
        readinessGate.reset()
        countdown.release()
        flowCoordinator?.currentExerciseOrNull()?.let {
          _state.update { state ->
            state.copy(
              runState = SessionRunState.IDLE,
              glassMessage = GlassMessageState(
                text = systemMessage(
                  "training_session_phase_restarted",
                  "أُعيدت الجولة — ابدأ من جديد",
                  "Set restarted — begin again",
                ),
                severity = GlassMessageSeverity.WARNING,
              ),
            )
          }
        }
      }
      PhaseResumeAction.NONE -> Unit
    }
    phasePauseSnapshot = null
  }

  fun stopAndFinalize(): ExerciseWorkoutSummary? {
    val summary = engine?.stop() ?: return null
    val upload = writeHooks.finalizeUpload(lastFrameTimestampMs.takeIf { it > 0 } ?: sessionStartMs)
    if (upload != null) {
      viewModelScope.launch {
        cachePostTrainingReport(upload, summary)
        enqueueUpload(upload, summary)
        writeHooks.finalizeExercise(upload)
      }
    }
    writeHooks.detach()
    return summary
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
      .onEach { prefs ->
        val intensity = CoachIntensity.from(prefs.coachIntensity)
        feedback.coachIntensity = intensity
        feedback.voiceEnabled = prefs.voiceFeedbackEnabled
        timingPolicy = TimingPolicy.withCoachIntensity(intensity)
        rebuildEngineIfNeeded()
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
    val config = exerciseConfig ?: return
    val tracked = config.poseVariants.getOrNull(activePoseVariantIndex)?.trackedJoints
      ?.find { it.joint == jointCode }
      ?: return
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
          exerciseConfig?.let { config -> writeHooks.attach(eng, config) }
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
        engine?.stop()
      }
      is SupervisorAction.ResetEngine -> {
        activeElapsedMs = 0L
        lastElapsedMs = 0L
        lastTrainingTimestampMs = 0L
        engine?.stop()
        engine?.start()
        _state.update { it.copy(elapsedLabel = formatElapsed(lastElapsedMs)) }
      }
      is SupervisorAction.ProcessFrame -> {
        if (sessionStartMs == 0L && action.timestampMs > 0L) {
          sessionStartMs = action.timestampMs
        }
        updateSessionElapsed(action.timestampMs)
        engine?.processFrame(
          PoseFrame(
            angles = action.angles,
            landmarks = action.landmarks,
            isFrontCamera = action.isFrontCamera,
            timestampMs = action.timestampMs,
          ),
        )
      }
      is SupervisorAction.ValidatePose -> Unit
      is SupervisorAction.StartCountdown -> countdown.start(viewModelScope)
      is SupervisorAction.CancelCountdown -> countdown.cancel()
      is SupervisorAction.FreezeCountdown -> countdown.freeze()
      is SupervisorAction.UnfreezeCountdown -> countdown.unfreeze()
      is SupervisorAction.ShowAutoPaused -> {
        _state.update {
          it.copy(
            pauseReason = action.reason,
            showVignette = action.reason != PauseReason.MANUAL,
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
            landmarks = emptyList(),
          )
        }
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
    val upload = writeHooks.finalizeUpload(lastFrameTimestampMs.takeIf { it > 0 } ?: sessionStartMs)
    val setsCompleted = _state.value.currentSetNumber
    val totalSets = _state.value.totalSets
    viewModelScope.launch {
      withContext(Dispatchers.Default) {
        frameCaptureCoordinator.awaitPendingCaptures()
      }
      if (upload != null && summary != null) {
        accumulateDayReport(upload, summary, setsCompleted, totalSets)
        cachePostTrainingReport(upload, summary)
        enqueueUpload(upload, summary)
        writeHooks.finalizeExercise(upload)
      }
      if (flowCoordinator != null) {
        supervisor.reset()
        resetSetupVoiceState()
        readinessGate.reset()
        countdown.release()
        writeHooks.detach()
        flowCoordinator.onExerciseCompleted()
        when (flowCoordinator.state.value) {
          TrainingSessionFlowCoordinator.State.WorkoutComplete -> {
            flushExploreBatchIfNeeded()
            finalizePlannedWorkoutDay()
            _state.update {
              it.copy(
                isComplete = true,
                isWorkoutComplete = true,
                runState = SessionRunState.COMPLETED,
                reportDetailId = it.reportDetailId,
              )
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
        finalizePlannedWorkoutDay()
        _state.update {
          it.copy(
            isComplete = true,
            reportDetailId = it.reportDetailId,
          )
        }
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
    } ?: writeHooks.buildSessionReport(upload, summary, config)
  }

  private suspend fun finalizePlannedWorkoutDay() {
    val planned = plannedWorkout ?: return
    val report = accumulatedDayReport ?: return
    writeHooks.completePlannedDay(
      workoutId = planned.plannedWorkoutId,
      report = report,
      programId = planned.programId,
      weekNumber = planned.weekNumber,
      dayNumber = planned.dayNumber,
      onOutcome = { recordWriteOutcome(it, TrainingSessionWriteDiagnostics.WriteKind.PLANNED_COMPLETE) },
    )
    TrainingSessionReportCache.putSession(planned.plannedWorkoutId, report)
    markReportAvailable(planned.plannedWorkoutId)
    applyWriteStatus()
  }

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

  private fun syncFrameEvidenceToWriteHooks() {
    writeHooks.peakFrameCaptures = frameCaptureCoordinator.captures()
    writeHooks.repReplayClips = frameCaptureCoordinator.replayClips()
  }

  private suspend fun cachePostTrainingReport(upload: WorkoutUpload, summary: ExerciseWorkoutSummary) {
    val config = exerciseConfig ?: return
    syncFrameEvidenceToWriteHooks()
    val report = writeHooks.buildPostTrainingReport(
      upload,
      summary,
      config,
      sessionQuality = resolveSessionQualityMeta(),
      holdData = engine?.snapshotHoldReportData(),
    )
    TrainingSessionReportCache.put(upload.id, report)
    if (exploreBatch == null) {
      markReportAvailable(upload.id)
    }
  }

  private suspend fun enqueueUpload(upload: WorkoutUpload, summary: ExerciseWorkoutSummary) {
    val config = exerciseConfig
    syncFrameEvidenceToWriteHooks()
    val postReport = config?.let {
      writeHooks.buildPostTrainingReport(
        upload,
        summary,
        it,
        sessionQuality = resolveSessionQualityMeta(),
        holdData = engine?.snapshotHoldReportData(),
      )
    }
    val legacyJson = postReport?.let { writeCoordinator.encodePostTrainingReport(it) }
    val batch = exploreBatch
    if (batch != null) {
      batch.record(upload, legacyJson)
      return
    }
    val result = writeHooks.enqueueExecutionUpload(
      upload = upload,
      context = uploadContext?.context,
      workoutGroupId = uploadContext?.workoutGroupId,
      workoutTemplateId = uploadContext?.workoutTemplateId,
      legacyReport = postReport,
    )
    recordWriteOutcome(result, TrainingSessionWriteDiagnostics.WriteKind.EXECUTION_UPLOAD)
    when (result) {
      is com.movit.shared.AppResult.Success -> {
        val reportId = result.value
        TrainingSessionReportCache.rekeyPostTraining(upload.id, reportId)
        postReport?.let { TrainingSessionReportCache.put(reportId, it.copy(id = reportId, workoutId = reportId)) }
        markReportAvailable(reportId)
      }
      is com.movit.shared.AppResult.Failure -> Unit
    }
  }

  private fun markReportAvailable(reportId: String) {
    TrainingPipelineDiagnostics.logMilestone("report ready id=$reportId")
    _state.update { it.copy(reportDetailId = reportId) }
  }

  private suspend fun flushExploreBatchIfNeeded() {
    val batch = exploreBatch ?: return
    val groupId = uploadContext?.workoutGroupId ?: return
    batch.flushAwait(
      workoutGroupId = groupId,
      workoutTemplateId = uploadContext.workoutTemplateId,
      onEachEnqueued = { uploadId, reportId ->
        TrainingSessionReportCache.rekeyPostTraining(uploadId, reportId)
        markReportAvailable(reportId)
      },
      onComplete = { _, _ -> applyWriteStatus() },
    )
  }

  private fun reloadForNextFlowItem() {
    val exercise = flowCoordinator?.currentExerciseOrNull() ?: return
    activeSlug = exercise.slug
    activeTargetReps = exercise.targetReps
    activeExerciseName = exercise.displayName
    activePoseVariantIndex = resolveActivePoseVariantIndex(exercise)
    exerciseConfig = configRepository.getBySlug(activeSlug)?.config
    engine?.stop()
    writeHooks.detach()
    writeHooks = createWriteHooks()
    frameCaptureCoordinator = createFrameCaptureCoordinator()
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
        configUnavailable = exerciseConfig == null,
        repCount = 0,
        liveFormPercent = 0,
        progressPercent = 0,
        runState = SessionRunState.IDLE,
        isComplete = false,
        elapsedLabel = formatElapsed(lastElapsedMs),
      )
    }
  }

  private fun startRestTimer() {
    restTimerJob?.cancel()
    restNearEndAnnounced = false
    restTimerJob = viewModelScope.launch {
      while (isActive) {
        delay(1_000L)
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
            currentSetNumber = flowState.setNumber,
            totalSets = flowState.totalSets,
            restSecondsRemaining = ((flowState.remainingMs + 999L) / 1_000L).toInt(),
            nextExerciseName = flowState.nextExercise.displayName,
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
            glassMessage = GlassMessageState(
              text = systemMessage(
                "training_visibility_warning",
                "أبقِ المفاصل ظاهرة",
                "Keep joints visible",
              ),
              severity = GlassMessageSeverity.WARNING,
            ),
          )
        }
        applyVignetteCue(feedbackEventRouter.routeVisibilityWarning())
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
      is PresenceSupervisorEvent.NoPoseWarning -> {
        if (visibilityWarningActive) return
        _state.update { it.copy(noPoseWarningMs = event.elapsedMs) }
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
      is PresenceSupervisorEvent.VisibilityResumed,
      PresenceSupervisorEvent.PoseRestored,
      -> {
        visibilityWarningActive = false
        _state.update {
          it.copy(showVignette = false, visibilityWarningMs = 0L, noPoseWarningMs = 0L)
        }
      }
      else -> Unit
    }
  }

  private fun maybeDeliverRandomMessage(timestampMs: Long) {
    if (supervisor.state.value != SessionRunState.TRAINING) return
    if (timestampMs - lastRandomMessageCheckMs < 1_000L) return
    lastRandomMessageCheckMs = timestampMs
    val hasActiveErrors = engine?.metricsSnapshot()?.jointStateInfos?.values?.any {
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

  private fun applyVignetteCue(cue: VignetteCue) {
    when (cue) {
      VignetteCue.WARNING, VignetteCue.ERROR -> _state.update { it.copy(showVignette = true) }
      VignetteCue.CLEAR -> _state.update { it.copy(showVignette = false) }
    }
  }

  private fun applyVisualMessage(visual: FeedbackVisualMessage) {
    _state.update {
      it.copy(
        glassMessage = GlassMessageState(
          text = visual.text,
          severity = when (visual.severity) {
            FeedbackSeverity.CRITICAL, FeedbackSeverity.ERROR -> GlassMessageSeverity.ERROR
            FeedbackSeverity.WARNING -> GlassMessageSeverity.WARNING
            FeedbackSeverity.SUCCESS -> GlassMessageSeverity.SUCCESS
            else -> GlassMessageSeverity.INFO
          },
        ),
      )
    }
  }

  private fun refreshSkeletonOverlay(runState: SessionRunState = supervisor.state.value) {
    val metrics = engine?.metricsSnapshot()
    val parity = buildSkeletonOverlayParityState(
      runState = runState,
      setupPhase = _state.value.setupPhase,
      jointStateInfos = metrics?.jointStateInfos ?: emptyMap(),
      anySideDimmedJointCodes = metrics?.anySideDimmedJointCodes ?: emptySet(),
      positionErrors = metrics?.positionErrors ?: emptyList(),
      isBilateralExercise = metrics?.isBilateralExercise ?: (exerciseConfig?.isBilateral == true),
      isBilateralFlipped = metrics?.isBilateralFlipped ?: (engine?.isBilateralFlipped == true),
      bilateralSide = metrics?.bilateralSide ?: engine?.bilateralSide,
      setupJointRows = _state.value.setupJointRows,
      language = language,
    )
    _state.update {
      it.copy(
        jointStateInfos = metrics?.jointStateInfos ?: emptyMap(),
        skeletonOverlayParity = parity,
        jointVisuals = parity.jointVisuals,
      )
    }
  }

  private fun buildEngine(): MovitTrainingEngine? {
    val config = exerciseConfig ?: return null
    return MovitTrainingEngine(
      exerciseConfig = config,
      poseVariantIndex = activePoseVariantIndex,
      targetRepsOverride = activeTargetReps,
      deviceTiltPort = deviceTiltPort,
      timingPolicy = timingPolicy,
    )
  }

  private fun rebuildEngineIfNeeded() {
    if (supervisor.state.value.isTrainingActive()) return
    engine?.stop()
    engine = buildEngine()
    wireEngineCallbacks()
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
      scope = viewModelScope,
      snapshotPort = frameSnapshotPort,
    )

  private fun repInProgress(): Int = (engine?.repCount ?: 0) + 1

  private fun resolveExerciseName(): String =
    activeExerciseName.ifBlank { exerciseConfig?.displayName(language).orEmpty() }

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
    writeHooks.detach()
    countdown.release()
    resetSetupVoiceState()
    readinessGate.reset()
    supervisor.reset()
    feedback.release()
    super.onCleared()
  }
}

data class WorkoutUploadContext(
  val workoutGroupId: String,
  val workoutTemplateId: String? = null,
  val context: String = WorkoutExecutionBatchCoordinator.EXPLORE_WORKOUT_CONTEXT,
)

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
  val configUnavailable: Boolean = false,
  val workoutFlowEnabled: Boolean = false,
  val workoutFlowPhase: WorkoutFlowPhase = WorkoutFlowPhase.NONE,
  val isResting: Boolean = false,
  val isWorkoutComplete: Boolean = false,
  val currentSetNumber: Int = 1,
  val totalSets: Int = 1,
  val restSecondsRemaining: Int = 0,
  val nextExerciseName: String = "",
  val restTip: String? = null,
  val restContext: String = "",
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
  val landmarks: List<SkeletonLandmarkPoint>? = null,
  /** Upright analysis frame width for skeleton overlay projection (0 = stretch fallback). */
  val skeletonAnalysisWidth: Int = 0,
  /** Upright analysis frame height for skeleton overlay projection (0 = stretch fallback). */
  val skeletonAnalysisHeight: Int = 0,
  /** When true, mirror normalized X to match front-camera PreviewView. */
  val skeletonMirrorPreview: Boolean = true,
  val jointVisuals: Map<String, SkeletonJointVisual> = emptyMap(),
  val jointStateInfos: Map<String, JointStateInfo> = emptyMap(),
  val skeletonOverlayParity: SkeletonOverlayParityState = SkeletonOverlayParityState(),
  val glassMessage: GlassMessageState? = null,
  val showVignette: Boolean = false,
  val elapsedLabel: String = "0:00",
  val reportDetailId: String? = null,
  val workoutFlowProgressPercent: Int = 0,
  val writeStatus: TrainingSessionWriteStatus = TrainingSessionWriteStatus(),
  val uploadNotice: String? = null,
)

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
