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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.movit.core.data.MovitData
import com.movit.core.training.engine.feedback.FeedbackRouter
import com.movit.core.training.feedback.CoachIntensity
import com.movit.core.training.session.TrainingFlowItem

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
  val uploadContext: WorkoutUploadContext? = null,
  val language: String = "en",
)

@Composable
fun TrainingSessionRoute(
  exerciseSlug: String,
  exerciseName: String,
  targetReps: Int,
  onBack: () -> Unit,
  onFinish: () -> Unit,
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
  onFinish: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val feedbackPorts = rememberTrainingFeedbackPorts()
  val prefs = MovitData.trainingPreferences.snapshot()
  val viewModel: TrainingSessionViewModel = viewModel {
    TrainingSessionViewModel(
      exerciseSlug = args.exerciseSlug,
      targetReps = args.targetReps,
      exerciseNameOverride = args.exerciseName,
      language = args.language,
      flowItems = args.flowItems,
      uploadContext = args.uploadContext,
      feedbackRouter = FeedbackRouter(
        coachIntensity = CoachIntensity.from(prefs.coachIntensity),
        speech = feedbackPorts.speech,
        haptics = feedbackPorts.haptics,
      ),
    )
  }
  val state by viewModel.state.collectAsStateWithLifecycle()
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
      viewModel.startWorkoutExercise()
    }
  }
  DisposableEffect(viewModel) {
    onDispose { viewModel.stopSession() }
  }
  TrainingSessionScreen(
    state = state,
    onBack = {
      viewModel.stopSession()
      onBack()
    },
    onPause = viewModel::pause,
    onResume = viewModel::resume,
    onStop = viewModel::stop,
    onFinish = {
      viewModel.stopSession()
      onFinish()
    },
    onSkipRest = viewModel::skipRest,
    onFlipCamera = { useFrontCamera = !useFrontCamera },
    debugFps = debugFps.takeIf { isTrainingDebugBuild() },
    cameraSlot = {
      TrainingSessionCameraHost(
        onFrame = viewModel::onPoseFrame,
        onCameraReady = viewModel::onCameraReady,
        onError = viewModel::onCameraError,
        useFrontCamera = useFrontCamera,
        onDebugFps = if (isTrainingDebugBuild()) {
          { fps -> debugFps = fps }
        } else {
          null
        },
        modifier = Modifier.fillMaxSize(),
      )
    },
    modifier = modifier,
  )
}

/** Legacy name — same session UI (Phase 07 WS-6). */
@Composable
fun ExerciseLiveRoute(
  exerciseSlug: String,
  exerciseName: String,
  targetReps: Int,
  onBack: () -> Unit,
  onFinish: () -> Unit,
  modifier: Modifier = Modifier,
) {
  TrainingSessionRoute(
    exerciseSlug = exerciseSlug,
    exerciseName = exerciseName,
    targetReps = targetReps,
    onBack = onBack,
    onFinish = onFinish,
    modifier = modifier,
  )
}
