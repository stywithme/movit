package com.movit.feature.training

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.movit.core.data.MovitData
import com.movit.core.posecapture.boundary.trainingdebug.PoseModelType
import com.movit.core.posecapture.boundary.trainingdebug.PoseModelTypePort
import com.movit.core.training.engine.feedback.FeedbackRouter
import com.movit.core.training.feedback.CoachIntensity
import com.movit.core.training.session.TrainingFlowItem
import org.koin.core.component.get

/**
 * Typed session args (I-24) — shell uses [com.movit.feature.shell.MovitInnerRoute.TrainingSession]
 * instead of legacy `MODE_*` Intent extras.
 */
data class TrainingSessionRouteArgs(
  val exerciseSlug: String,
  val exerciseName: String,
  val targetReps: Int,
  val workoutId: String? = null,
  val flowItems: List<TrainingFlowItem>? = null,
  val startExerciseIndex: Int = 0,
  val uploadContext: WorkoutUploadContext? = null,
  val plannedWorkout: PlannedWorkoutContext? = null,
  val language: String = "en",
  val poseVariantIndex: Int = 0,
)

@Composable
fun TrainingSessionRoute(
  exerciseSlug: String,
  exerciseName: String,
  targetReps: Int,
  onBack: () -> Unit,
  onFinish: (isWorkoutFlowComplete: Boolean) -> Unit,
  modifier: Modifier = Modifier,
  language: String = "en",
  flowItems: List<TrainingFlowItem>? = null,
  uploadContext: WorkoutUploadContext? = null,
) {
  TrainingSessionRoute(
    args = TrainingSessionRouteArgs(
      exerciseSlug = exerciseSlug,
      exerciseName = exerciseName,
      targetReps = targetReps,
      workoutId = null,
      flowItems = flowItems,
      uploadContext = uploadContext,
      language = language,
    ),
    onBack = onBack,
    onFinish = onFinish,
    modifier = modifier,
  )
}

@Composable
fun TrainingSessionRoute(
  args: TrainingSessionRouteArgs,
  onBack: () -> Unit,
  onFinish: (isWorkoutFlowComplete: Boolean) -> Unit,
  onViewReport: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val feedbackPorts = rememberTrainingFeedbackPorts(language = args.language)
  val deviceTiltPort = remember { resolveTrainingDeviceTiltPort() }
  val prefs = MovitData.trainingPreferences.snapshot()
  val trainingPrefs by MovitData.trainingPreferences.state.collectAsStateWithLifecycle(initialValue = prefs)
  val poseModelPort = remember {
    runCatching { MovitData.koin().get<PoseModelTypePort>() }.getOrNull()
  }
  val viewModel: TrainingSessionViewModel = viewModel {
    TrainingSessionViewModel(
      exerciseSlug = args.exerciseSlug,
      targetReps = args.targetReps,
      exerciseNameOverride = args.exerciseName,
      language = args.language,
      sessionId = args.workoutId ?: args.exerciseSlug,
      flowItems = args.flowItems,
      uploadContext = args.uploadContext,
      plannedWorkout = args.plannedWorkout,
      startExerciseIndex = args.startExerciseIndex,
      routePoseVariantIndex = args.poseVariantIndex,
      deviceTiltPort = deviceTiltPort,
      feedbackRouter = FeedbackRouter(
        coachIntensity = CoachIntensity.from(prefs.coachIntensity),
        speech = feedbackPorts.speech,
        haptics = feedbackPorts.haptics,
        audioPlayer = feedbackPorts.audioPlayer,
      ),
    )
  }
  val state by viewModel.state.collectAsStateWithLifecycle()
  TrainingKeepScreenOnEffect()
  var useFrontCamera by remember { mutableStateOf(true) }
  var debugFps by remember { mutableIntStateOf(0) }
  var previousFlowPhase by remember { mutableStateOf<WorkoutFlowPhase?>(null) }
  LaunchedEffect(state.workoutFlowPhase) {
    val prev = previousFlowPhase
    previousFlowPhase = state.workoutFlowPhase
    if (
      prev == WorkoutFlowPhase.REST &&
      state.workoutFlowPhase == WorkoutFlowPhase.PRE_EXERCISE
    ) {
      viewModel.onEvent(TrainingSessionEvent.StartWorkoutExercise)
    }
  }
  LaunchedEffect(viewModel) {
    viewModel.effects.collect { effect ->
      when (effect) {
        is TrainingSessionEffect.ViewReport -> onViewReport(effect.reportId)
        is TrainingSessionEffect.Finish -> onFinish(effect.isWorkoutFlowComplete)
        TrainingSessionEffect.NavigateBack -> onBack()
      }
    }
  }
  LaunchedEffect(state.isComplete, state.reportDetailId) {
    val reportId = state.reportDetailId ?: return@LaunchedEffect
    if (!state.isComplete) return@LaunchedEffect
    onViewReport(reportId)
  }
  DisposableEffect(viewModel) {
    onDispose { viewModel.onEvent(TrainingSessionEvent.StopSession) }
  }
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, viewModel) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_STOP -> viewModel.onEvent(TrainingSessionEvent.HostBackgrounded())
        Lifecycle.Event.ON_START -> viewModel.onEvent(TrainingSessionEvent.HostForegrounded())
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
  TrainingSessionScreen(
    state = state,
    onBack = { viewModel.onEvent(TrainingSessionEvent.BackPressed) },
    onPause = { viewModel.onEvent(TrainingSessionEvent.Pause) },
    onResume = { viewModel.onEvent(TrainingSessionEvent.Resume) },
    onStop = { viewModel.onEvent(TrainingSessionEvent.Stop) },
    onFinish = { viewModel.onEvent(TrainingSessionEvent.FinishClicked) },
    onViewReport = { viewModel.onEvent(TrainingSessionEvent.ViewReportClicked) },
    onSkipRest = { viewModel.onEvent(TrainingSessionEvent.SkipRest) },
    onFlipCamera = {
      viewModel.onEvent(TrainingSessionEvent.CameraSwitchStarted)
      useFrontCamera = !useFrontCamera
    },
    trainingPreferences = trainingPrefs,
    useFrontCamera = useFrontCamera,
    onApplyTrainingSettings = { selection ->
      MovitData.trainingPreferences.setIndicatorType(selection.indicatorType)
      MovitData.trainingPreferences.setVoiceFeedbackEnabled(selection.voiceFeedbackEnabled)
      MovitData.trainingPreferences.setCoachIntensity(selection.coachIntensity)
      MovitData.trainingPreferences.setModelType(selection.modelType)
      poseModelPort?.setSelectedModel(PoseModelType.fromPreference(selection.modelType))
    },
    debugFps = debugFps.takeIf { isTrainingDebugBuild() },
    cameraSlot = {
      if (state.requiresCamera()) {
        TrainingSessionCameraHost(
          onFrame = { viewModel.onEvent(TrainingSessionEvent.PoseFrameReceived(it)) },
          onCameraReady = { viewModel.onEvent(TrainingSessionEvent.CameraReady) },
          onError = { viewModel.onEvent(TrainingSessionEvent.CameraError(it)) },
          useFrontCamera = useFrontCamera,
          modelType = trainingPrefs.modelType,
          onDebugFps = if (isTrainingDebugBuild()) {
            { fps -> debugFps = fps }
          } else {
            null
          },
          modifier = Modifier.fillMaxSize(),
        )
      }
    },
    modifier = modifier,
  )
}
