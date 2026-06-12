package com.movit.feature.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.data.MovitData
import com.movit.core.data.audio.AudioPrefetchRunner
import com.movit.core.data.cache.AudioManifestCache
import com.movit.core.data.cache.SystemMessageRegistry
import com.movit.core.data.preferences.MovitTrainingPreferences
import com.movit.core.data.repository.TrainingConfigRepository
import com.movit.core.data.repository.TrainingSessionWriteCoordinator
import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.training.config.getMessagesForState
import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.engine.ZoneType
import com.movit.feature.reports.TrainingSessionReportCache
import com.movit.core.training.engine.ErrorType
import com.movit.core.training.engine.JointError
import com.movit.core.training.engine.JointState
import com.movit.core.training.engine.feedback.FeedbackRouter
import com.movit.core.training.engine.feedback.FeedbackVisualMessage
import com.movit.core.training.engine.policy.TimingPolicy
import com.movit.core.training.feedback.CoachIntensity
import com.movit.core.training.feedback.FeedbackKind
import com.movit.core.training.feedback.FeedbackSeverity
import com.movit.core.training.feedback.FeedbackSignal
import com.movit.core.training.journal.WorkoutUpload
import com.movit.core.training.model.PoseFrame
import com.movit.core.training.report.AssessmentTrainingResult
import com.movit.core.training.report.MovitSessionReport
import com.movit.core.training.report.MovitSessionReportBuilder
import com.movit.core.training.session.CountdownController
import com.movit.core.training.session.ExerciseWorkoutSummary
import com.movit.core.training.session.HoldStatus
import com.movit.core.training.session.MovitTrainingEngine
import com.movit.core.training.session.PauseReason
import com.movit.core.training.session.PresenceSupervisorEvent
import com.movit.core.training.session.toSupervisorSignal
import com.movit.core.training.session.SessionRunState
import com.movit.core.training.session.SessionSupervisor
import com.movit.core.training.session.SetupReadinessGate
import com.movit.core.training.session.SetupValidationConfig
import com.movit.core.training.session.SupervisorAction
import com.movit.core.training.session.SupervisorSignal
import com.movit.core.training.session.TrainingFlowItem
import com.movit.core.training.session.TrainingSessionFlowCoordinator
import com.movit.designsystem.components.GlassMessageSeverity
import com.movit.designsystem.components.SkeletonJointQuality
import com.movit.designsystem.components.SkeletonJointVisual
import com.movit.designsystem.components.SkeletonLandmarkPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
  private val configRepository: TrainingConfigRepository = MovitData.trainingConfig,
  private val writeCoordinator: TrainingSessionWriteCoordinator = MovitData.trainingWrites,
  private val trainingPreferences: MovitTrainingPreferences = MovitData.trainingPreferences,
  private val audioPrefetchRunner: AudioPrefetchRunner = MovitData.audioPrefetch,
  private val audioManifestCache: AudioManifestCache = MovitData.audioManifest,
  private val setupValidation: SetupValidationConfig = SetupValidationConfig(),
  feedbackRouter: FeedbackRouter = FeedbackRouter(coachIntensity = CoachIntensity.STANDARD),
) : ViewModel() {
  private var activeSlug: String = exerciseSlug
  private var activeTargetReps: Int = targetReps
  private var activeExerciseName: String = exerciseNameOverride
  private var exerciseConfig: ExerciseConfig? = configRepository.getBySlug(activeSlug)?.config

  private var timingPolicy: TimingPolicy = TimingPolicy.withCoachIntensity(
    CoachIntensity.from(trainingPreferences.snapshot().coachIntensity),
  )

  private var lastFrameTimestampMs: Long = 0L
  private var writeHooks = createWriteHooks()
  private val exploreBatch = uploadContext?.let {
    WorkoutExecutionBatchCoordinator(writeCoordinator, context = it.context)
  }

  private val flowCoordinator = flowItems?.let { TrainingSessionFlowCoordinator(it) }
  private var restTimerJob: Job? = null
  private var restNearEndAnnounced = false

  private val supervisor = SessionSupervisor(setupValidation = setupValidation)
  private val readinessGate = SetupReadinessGate(setupValidation)
  private val countdown = CountdownController()
  private val feedback = feedbackRouter
  private var engine: MovitTrainingEngine? = buildEngine()

  private var sessionStartMs: Long = 0L
  private var lastElapsedMs: Long = 0L
  private var visibilityWarningActive = false
  private var lastRandomMessageCheckMs = 0L
  private var plannedWorkoutStarted = false
  private var accumulatedDayReport: MovitSessionReport? = null

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

  init {
    feedback.onVisualMessage = { visual -> applyVisualMessage(visual) }
    wirePreferences()
    prefetchAudio()
    wireSupervisor()
    wireCountdown()
    wireEngineCallbacks()
    exerciseConfig?.poseVariants?.firstOrNull()?.feedbackMessages?.let(feedback::setRandomMessages)
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

  fun onPoseFrame(frame: PoseFrame) {
    if (_state.value.isResting) return

    if (frame.hasPose) {
      _state.update {
        it.copy(
          landmarks = frame.landmarks?.map { lm ->
            SkeletonLandmarkPoint(lm.x, lm.y, lm.isVisible())
          },
        )
      }
    }

    if (!frame.hasPose) {
      if (!visibilityWarningActive) {
        supervisor.processSignal(SupervisorSignal.NoPoseFrame(frame.timestampMs))
      }
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
    val runState = supervisor.state.value
    if (runState.shouldValidatePose() && exerciseConfig != null) {
      val readiness = readinessGate.validate(
        angles = angles,
        landmarks = landmarks,
        exerciseConfig = exerciseConfig!!,
        poseVariantIndex = 0,
        isFrontCamera = frame.isFrontCamera,
      )
      _state.update {
        it.copy(
          setupProgressPercent = readiness.progressPercent,
          setupPhase = readiness.phase.name,
          setupGuidance = readiness.phaseMessage?.get(language),
        )
      }
      if (readiness.isConfirmed) {
        supervisor.processSignal(SupervisorSignal.PoseConfirmed)
      } else if (runState == SessionRunState.COUNTDOWN || runState == SessionRunState.RESUME_COUNTDOWN) {
        supervisor.processSignal(SupervisorSignal.PoseInvalid)
      }
    }
    refreshSkeletonJointStates()
    maybeDeliverRandomMessage(frame.timestampMs)
  }

  fun onCameraReady() {
    _state.update { it.copy(isCameraReady = true, errorMessage = null) }
  }

  fun onCameraError(message: String) {
    _state.update { it.copy(errorMessage = message, isCameraReady = false) }
  }

  fun pause() = supervisor.processSignal(SupervisorSignal.PauseRequested)
  fun resume() = supervisor.processSignal(SupervisorSignal.ResumeRequested)
  fun stop() = supervisor.processSignal(SupervisorSignal.StopRequested)

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
      TrainingSessionAudioHooks.prefetchOnSessionOpen(audioPrefetchRunner, audioManifestCache)
    }
  }

  private fun wireSupervisor() {
    supervisor.actions.onEach { handleSupervisorAction(it) }.launchIn(viewModelScope)
    supervisor.state.onEach { runState ->
      _state.update {
        it.copy(
          runState = runState,
          isComplete = it.isComplete || runState == SessionRunState.COMPLETED,
        )
      }
    }.launchIn(viewModelScope)

    flowCoordinator?.state?.onEach { syncFlowUi() }?.launchIn(viewModelScope)
  }

  private fun wireCountdown() {
    countdown.setListener(object : CountdownController.CountdownListener {
      override fun onTick(secondsRemaining: Int) {
        _state.update { it.copy(countdownValue = secondsRemaining) }
        if (secondsRemaining in 1..3) {
          val key = "training_countdown_$secondsRemaining"
          feedback.submit(
            FeedbackSignal(
              kind = FeedbackKind.COUNTDOWN,
              severity = FeedbackSeverity.CRITICAL,
              text = systemMessage(key, secondsRemaining.toString(), secondsRemaining.toString()),
              dedupeKey = key,
              activeKey = "countdown",
              forceAudible = true,
              allowVisual = false,
            ),
          )
        }
      }

      override fun onFinish() {
        feedback.submit(
          FeedbackSignal(
            kind = FeedbackKind.COUNTDOWN,
            severity = FeedbackSeverity.SUCCESS,
            text = systemMessage("training_countdown_go", "ابدأ!", "Go!"),
            dedupeKey = "training_countdown_go",
            activeKey = "countdown",
            forceAudible = true,
            allowVisual = false,
          ),
        )
        supervisor.processSignal(SupervisorSignal.CountdownFinished)
      }

      override fun onCancelled() {
        _state.update { it.copy(countdownValue = null, countdownFrozen = false) }
      }

      override fun onFrozen() {
        _state.update { it.copy(countdownFrozen = true) }
      }

      override fun onUnfrozen() {
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

      override suspend fun playCountdownNumber(secondsRemaining: Int) = Unit
      override suspend fun playGo() = Unit
    }
  }

  private fun wireEngineCallbacks() {
    engine?.onHoldStatusChanged = { status ->
      _state.update { it.copy(holdStatus = status) }
    }
    engine?.onRepCountChanged = { count, score, _ ->
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
    }
    engine?.onTargetReached = {
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
          ),
        )
      }
    }
    engine?.onPresenceEvent = { event -> handlePresenceEvent(event) }
    engine?.onJointStateMessage = { jointCode, state, zone ->
      submitJointStateMessage(jointCode, state, zone)
    }
    engine?.onJointErrorFeedback = { error, message ->
      submitJointErrorFeedback(error, message)
    }
  }

  private fun submitJointStateMessage(
    jointCode: String,
    state: JointState,
    zone: ZoneType,
  ) {
    val config = exerciseConfig ?: return
    val tracked = config.poseVariants.firstOrNull()?.trackedJoints
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
        engine?.start()
        engine?.let { eng ->
          exerciseConfig?.let { config -> writeHooks.attach(eng, config) }
        }
      }
      is SupervisorAction.PauseEngine -> engine?.pause()
      is SupervisorAction.ResumeEngine -> engine?.resume()
      is SupervisorAction.ResumeFromVisibilityPause -> engine?.resume()
      is SupervisorAction.StopEngine -> engine?.stop()
      is SupervisorAction.ResetEngine -> {
        engine?.stop()
        engine?.start()
      }
      is SupervisorAction.ProcessFrame -> {
        if (sessionStartMs == 0L && action.timestampMs > 0L) {
          sessionStartMs = action.timestampMs
        }
        if (action.timestampMs > 0L) lastFrameTimestampMs = action.timestampMs
        if (sessionStartMs > 0L) {
          lastElapsedMs = (action.timestampMs - sessionStartMs).coerceAtLeast(0L)
          _state.update { it.copy(elapsedLabel = formatElapsed(lastElapsedMs)) }
        }
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
      is SupervisorAction.ShowCompleted -> {
        finalizeCurrentExercise()
      }
      else -> Unit
    }
  }

  private fun finalizeCurrentExercise() {
    val summary = engine?.stop()
    val upload = writeHooks.finalizeUpload(lastFrameTimestampMs.takeIf { it > 0 } ?: sessionStartMs)
    val setsCompleted = _state.value.currentSetNumber
    val totalSets = _state.value.totalSets
    viewModelScope.launch {
      if (upload != null && summary != null) {
        accumulateDayReport(upload, summary, setsCompleted, totalSets)
        cachePostTrainingReport(upload, summary)
        enqueueUpload(upload, summary)
        writeHooks.finalizeExercise(upload)
      }
      if (flowCoordinator != null) {
        supervisor.reset()
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
    )
    writeHooks.reportPlannedDay(
      workoutId = planned.plannedWorkoutId,
      report = report,
      programId = planned.programId,
      weekNumber = planned.weekNumber,
      dayNumber = planned.dayNumber,
    )
  }

  private fun cachePostTrainingReport(upload: WorkoutUpload, summary: ExerciseWorkoutSummary) {
    val config = exerciseConfig ?: return
    val report = writeHooks.buildPostTrainingReport(upload, summary, config)
    TrainingSessionReportCache.put(upload.id, report)
    markReportAvailable(upload.id)
  }

  private suspend fun enqueueUpload(upload: WorkoutUpload, summary: ExerciseWorkoutSummary) {
    val config = exerciseConfig
    val postReport = config?.let { writeHooks.buildPostTrainingReport(upload, summary, it) }
    val batch = exploreBatch
    if (batch != null) {
      batch.record(upload)
      return
    }
    writeHooks.enqueueExecutionUpload(
      upload = upload,
      scope = viewModelScope,
      context = uploadContext?.context,
      workoutGroupId = uploadContext?.workoutGroupId,
      workoutTemplateId = uploadContext?.workoutTemplateId,
      legacyReport = postReport,
      onEnqueued = { reportId ->
        postReport?.let { TrainingSessionReportCache.put(reportId, it) }
        markReportAvailable(reportId)
      },
    )
  }

  private fun markReportAvailable(reportId: String) {
    _state.update { it.copy(reportDetailId = reportId) }
  }

  private fun flushExploreBatchIfNeeded() {
    val batch = exploreBatch ?: return
    val groupId = uploadContext?.workoutGroupId ?: return
    batch.flush(
      scope = viewModelScope,
      workoutGroupId = groupId,
      workoutTemplateId = uploadContext.workoutTemplateId,
      onEachEnqueued = { markReportAvailable(activeSlug) },
    )
  }

  private fun reloadForNextFlowItem() {
    val exercise = flowCoordinator?.currentExerciseOrNull() ?: return
    activeSlug = exercise.slug
    activeTargetReps = exercise.targetReps
    activeExerciseName = exercise.displayName
    exerciseConfig = configRepository.getBySlug(activeSlug)?.config
    engine?.stop()
    writeHooks.detach()
    writeHooks = createWriteHooks()
    engine = buildEngine()
    wireEngineCallbacks()
    exerciseConfig?.poseVariants?.firstOrNull()?.feedbackMessages?.let(feedback::setRandomMessages)
    feedback.resetAll()
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
          )
        }
      }
      TrainingSessionFlowCoordinator.State.Training -> {
        _state.update { it.copy(workoutFlowPhase = WorkoutFlowPhase.TRAINING, isResting = false) }
      }
      TrainingSessionFlowCoordinator.State.WorkoutComplete -> {
        _state.update {
          it.copy(
            workoutFlowPhase = WorkoutFlowPhase.WORKOUT_COMPLETE,
            isWorkoutComplete = true,
            isComplete = true,
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

  private fun refreshSkeletonJointStates() {
    val metrics = engine?.metricsSnapshot() ?: return
    val joints = metrics.jointStateInfos.mapValues { (_, info) ->
      SkeletonJointVisual(
        jointCode = info.jointCode,
        quality = when (info.state) {
          JointState.PERFECT -> SkeletonJointQuality.PERFECT
          JointState.DANGER -> SkeletonJointQuality.DANGER
          JointState.WARNING -> SkeletonJointQuality.WARNING
          else -> SkeletonJointQuality.NORMAL
        },
      )
    }
    _state.update { it.copy(jointVisuals = joints) }
  }

  private fun buildEngine(): MovitTrainingEngine? {
    val config = exerciseConfig ?: return null
    return MovitTrainingEngine(
      exerciseConfig = config,
      targetRepsOverride = activeTargetReps,
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
    isAssessmentMode = isAssessmentMode,
    timeProvider = { lastFrameTimestampMs.takeIf { it > 0 } ?: sessionStartMs },
  )

  private fun resolveExerciseName(): String =
    activeExerciseName.ifBlank { exerciseConfig?.displayName(language).orEmpty() }

  private fun systemMessage(key: String, defaultAr: String, defaultEn: String): String =
    SystemMessageRegistry.get(key, defaultAr, defaultEn).display(language)

  override fun onCleared() {
    restTimerJob?.cancel()
    writeHooks.detach()
    countdown.release()
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
  val countdownValue: Int? = null,
  val countdownFrozen: Boolean = false,
  val holdStatus: HoldStatus? = null,
  val pauseReason: PauseReason? = null,
  val noPoseWarningMs: Long = 0L,
  val visibilityWarningMs: Long = 0L,
  val isComplete: Boolean = false,
  val isCameraReady: Boolean = false,
  val errorMessage: String? = null,
  val landmarks: List<SkeletonLandmarkPoint>? = null,
  val jointVisuals: Map<String, SkeletonJointVisual> = emptyMap(),
  val glassMessage: GlassMessageState? = null,
  val showVignette: Boolean = false,
  val elapsedLabel: String = "0:00",
  val reportDetailId: String? = null,
)

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
