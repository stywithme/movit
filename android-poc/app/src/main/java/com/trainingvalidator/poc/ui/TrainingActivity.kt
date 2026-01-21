package com.trainingvalidator.poc.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.analysis.AngleCalculator
import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.LandmarkSmoother
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.camera.CameraManager
import com.trainingvalidator.poc.databinding.ActivityTrainingBinding
import com.trainingvalidator.poc.pose.ModelType
import com.trainingvalidator.poc.pose.PoseLandmarkerHelper
import com.trainingvalidator.poc.pose.PoseResult
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.feedback.FeedbackEvent
import com.trainingvalidator.poc.training.feedback.FeedbackManager
import com.trainingvalidator.poc.training.session.PauseReason
import com.trainingvalidator.poc.training.session.SessionState
import com.trainingvalidator.poc.ui.components.AnimationUtils
import com.trainingvalidator.poc.ui.components.GlassmorphicMessageView
import com.trainingvalidator.poc.ui.training.CountdownController
import com.trainingvalidator.poc.ui.training.PoseValidator
import com.trainingvalidator.poc.ui.training.TrainingUIEvent
import com.trainingvalidator.poc.ui.training.TrainingViewModel
import com.trainingvalidator.poc.ui.training.VideoModeController
import android.net.Uri
import android.widget.SeekBar
import com.trainingvalidator.poc.training.engine.HoldState
import com.trainingvalidator.poc.training.engine.Phase
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.report.FrameCaptureManager
import com.trainingvalidator.poc.training.report.ReportGenerator
import com.trainingvalidator.poc.storage.ReportStorage
import com.trainingvalidator.poc.ui.report.ReportActivity
import com.trainingvalidator.poc.video.VideoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * TrainingActivity - Professional Training Screen
 * 
 * Now uses:
 * - SessionSupervisor via TrainingViewModel for state management (Single Source of Truth)
 * - PoseValidator for pose validation
 * - CountdownController for countdown logic
 * - VideoModeController for video mode
 * 
 * This Activity is now primarily responsible for:
 * - UI binding and updates
 * - Camera/Pose detection setup
 * - User interactions
 * - Forwarding pose data to ViewModel
 */
class TrainingActivity : AppCompatActivity(), PoseLandmarkerHelper.PoseDetectionListener {

    companion object {
        private const val TAG = "TrainingActivity"
        
        // Intent extras
        const val EXTRA_EXERCISE_NAME = "exercise_name"
        const val EXTRA_DIFFICULTY = "difficulty"
        const val EXTRA_POSE_VARIANT = "pose_variant"
        const val EXTRA_TRAINING_MODE = "training_mode"
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_MAX_REPS_THIS_SESSION = "max_reps_this_session"
        const val EXTRA_TARGET_REPS_OVERRIDE = "target_reps_override"
        const val EXTRA_TARGET_DURATION_OVERRIDE = "target_duration_override"
        const val EXTRA_WORKOUT_NAME = "workout_name"
        const val EXTRA_IS_WORKOUT_MODE = "is_workout_mode"
        const val EXTRA_INDICATOR_TYPE = "indicator_type"
        
        // Result extras
        const val RESULT_REPS_COMPLETED = "reps_completed"
        const val RESULT_DURATION_MS = "duration_ms"
        const val RESULT_ACCURACY = "accuracy"
        const val RESULT_IS_COMPLETED = "is_completed"
        
        // Training modes
        const val MODE_CAMERA = "camera"
        const val MODE_VIDEO = "video"
        
        // Defaults
        private const val DEFAULT_EXERCISE = "squat"
        private const val DEFAULT_DIFFICULTY = "beginner"
        
        // Colors
        private val COLOR_CORRECT = Color.parseColor("#00E676")
        private val COLOR_WARNING = Color.parseColor("#FFC107")
        private val COLOR_ERROR = Color.parseColor("#FF5252")
        private val COLOR_DEFAULT = Color.WHITE
    }

    // View Binding
    private lateinit var binding: ActivityTrainingBinding
    
    // ViewModel
    private val viewModel: TrainingViewModel by viewModels { 
        TrainingViewModel.Factory(assets) 
    }
    
    // Camera & Pose Detection
    private var cameraManager: CameraManager? = null
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    private lateinit var landmarkSmoother: LandmarkSmoother
    
    // Video Mode Controller
    private var videoModeController: VideoModeController? = null
    
    // State
    private var useFrontCamera = true
    private var isVideoMode = false
    private var videoUri: Uri? = null
    private var lastRepCount = 0

    // Tracks pose presence transitions to avoid leaving stale form feedback visible when pose is lost.
    // This is intentionally Activity-local (UI concern) and does not affect session state machine behavior.
    private var wasPoseDetectedLastFrame: Boolean = false
    
    // Report & Frame Capture
    private var frameCaptureManager: FrameCaptureManager? = null
    private var reportStorage: ReportStorage? = null
    private var sessionId: String = java.util.UUID.randomUUID().toString()
    private var lastCapturedPhase: Phase? = null
    private var generatedReportId: String? = null
    
    // FPS calculation
    private var frameCount = 0
    private var lastFpsUpdateTime = System.currentTimeMillis()
    private var currentFps = 0

    // Permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onCameraPermissionGranted()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        initializeSettings()
        setupFullscreen()
        
        binding = ActivityTrainingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        parseIntentExtras()
        setupUI()
        setupCountdownController()
        initializeReportSystem()
        observeViewModel()
        
        // Initialize based on mode
        if (isVideoMode) {
            setupVideoMode()
        } else {
            checkCameraPermission()
        }
    }
    
    // ==================== Initialization ====================
    
    private fun initializeSettings() {
        try {
            SettingsManager.initialize(this)
            landmarkSmoother = LandmarkSmoother.createFromSettings()
            
            val smoothingInfo = if (SettingsManager.useLegacySmoothing()) {
                "Legacy EMA (alpha=${SettingsManager.getLegacySmoothingAlpha()})"
            } else {
                "One Euro (minCutoff=${SettingsManager.getSmoothingMinCutoff()}, beta=${SettingsManager.getSmoothingBeta()})"
            }
            Log.i(TAG, "Smoothing: $smoothingInfo")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize settings, using defaults", e)
            landmarkSmoother = LandmarkSmoother.createBalanced()
        }
    }

    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    
    private fun parseIntentExtras() {
        val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: DEFAULT_EXERCISE
        val difficultyStr = intent.getStringExtra(EXTRA_DIFFICULTY) ?: DEFAULT_DIFFICULTY
        val poseVariantIndex = intent.getIntExtra(EXTRA_POSE_VARIANT, 0)
        
        // Training mode
        val trainingMode = intent.getStringExtra(EXTRA_TRAINING_MODE) ?: MODE_CAMERA
        isVideoMode = trainingMode == MODE_VIDEO
        videoUri = IntentCompat.getParcelableExtra(intent, EXTRA_VIDEO_URI, Uri::class.java)
        
        if (isVideoMode && videoUri == null) {
            Toast.makeText(this, "No video selected", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // Indicator type (from ExerciseDetailActivity or default from settings)
        val indicatorType = intent.getStringExtra(EXTRA_INDICATOR_TYPE) 
            ?: com.trainingvalidator.poc.training.config.SettingsManager.getIndicatorType()
        
        // Set indicator type in overlay
        binding.skeletonOverlay.setIndicatorType(indicatorType)
        
        // Target overrides
        val targetRepsOverride = intent.getIntExtra(EXTRA_TARGET_REPS_OVERRIDE, -1)
            .takeIf { it > 0 }
        val targetDurationOverride = intent.getIntExtra(EXTRA_TARGET_DURATION_OVERRIDE, -1)
            .takeIf { it > 0 }?.let { it * 1000L }
        
        // Workout mode
        val workoutName = intent.getStringExtra(EXTRA_WORKOUT_NAME)
        val isWorkoutMode = intent.getBooleanExtra(EXTRA_IS_WORKOUT_MODE, false) || workoutName != null
        
        // Load exercise or workout via ViewModel
        if (isWorkoutMode && workoutName != null) {
            if (!viewModel.loadWorkout(workoutName, difficultyStr)) {
                Toast.makeText(this, "Failed to load workout: $workoutName", Toast.LENGTH_LONG).show()
                finish()
                return
            }
        } else {
            if (!viewModel.loadExercise(exerciseName, difficultyStr, poseVariantIndex, 
                    targetRepsOverride, targetDurationOverride)) {
                Toast.makeText(this, "Failed to load exercise: $exerciseName", Toast.LENGTH_LONG).show()
                finish()
                return
            }
        }
        
        // Initialize feedback
        viewModel.initializeFeedback(this, isVideoMode)
    }

    private fun setupUI() {
        // Exercise name
        binding.tvExerciseName.text = viewModel.exerciseName.value
        
        // Close button
        binding.btnClose.setOnClickListener { 
            viewModel.requestStop()
            finish() 
        }
        
        // Switch camera button
        binding.btnSwitchCamera.setOnClickListener {
            useFrontCamera = !useFrontCamera
            cameraManager?.switchCamera(useFrontCamera)
            if (::landmarkSmoother.isInitialized) {
                landmarkSmoother.reset()
            }
        }
        
        // Play/Pause button
        binding.btnPlayPause.setOnClickListener {
            handlePlayPauseClick()
        }
        
        // Initial state
        updateUIForSessionState(SessionState.SETUP_POSE)
        showPoseRequirements()
    }
    
    private fun setupCountdownController() {
        viewModel.countdownController.setListener(object : CountdownController.CountdownListener {
            override fun onTick(secondsRemaining: Int) {
                AnimationUtils.animateCountdown(binding.tvCountdown, secondsRemaining.toString())
                viewModel.feedbackManager?.speakCountdown(secondsRemaining)
            }
            
            override fun onFinish() {
                AnimationUtils.animateGoText(binding.tvCountdown) {
                    // Notify supervisor that countdown finished
                    viewModel.onCountdownFinished()
                }
                viewModel.feedbackManager?.speakGo()
            }
            
            override fun onCancelled() {
                updateUIForSessionState(SessionState.SETUP_POSE)
            }
        })
    }
    
    private fun initializeReportSystem() {
        // Generate new session ID for each training
        sessionId = java.util.UUID.randomUUID().toString()
        
        // Initialize frame capture manager
        frameCaptureManager = FrameCaptureManager(this, sessionId)
        frameCaptureManager?.cleanupOldSessions(5)  // Keep last 5 sessions
        
        // Initialize report storage
        reportStorage = ReportStorage(this)
    }
    
    // ==================== ViewModel Observers ====================
    
    private fun observeViewModel() {
        // Observe supervisor state (Single Source of Truth)
        lifecycleScope.launch {
            viewModel.supervisor.state.collectLatest { state ->
                updateUIForSessionState(state)
            }
        }
        
        // Observe exercise name
        lifecycleScope.launch {
            viewModel.exerciseName.collectLatest { name ->
                AnimationUtils.crossfadeText(binding.tvExerciseName, name)
            }
        }
        
        // Observe rep count
        lifecycleScope.launch {
            viewModel.repCount.collectLatest { count ->
                if (count != lastRepCount && count > 0) {
                    AnimationUtils.animateCounterChange(
                        binding.tvRepCount,
                        count.toString(),
                        AnimationUtils.SlideDirection.UP
                    )
                } else {
                    binding.tvRepCount.text = count.toString()
                }
                lastRepCount = count
            }
        }
        
        // Observe progress
        lifecycleScope.launch {
            viewModel.progressText.collectLatest { text ->
                binding.tvProgress.text = text
            }
        }
        
        lifecycleScope.launch {
            viewModel.progressPercent.collectLatest { percent ->
                binding.progressBar.progress = percent
                binding.tvProgressPercent.text = "$percent%"
            }
        }
        
        // Observe phase - also capture peak frames
        lifecycleScope.launch {
            viewModel.currentPhase.collectLatest { phase ->
                AnimationUtils.crossfadeText(
                    binding.tvPhase,
                    getPhaseDisplayName(phase, viewModel.isHoldExercise())
                )
                
                // Capture peak frame when entering BOTTOM or EXTENDED phase
                if (phase != lastCapturedPhase) {
                    if (phase == Phase.BOTTOM || phase == Phase.EXTENDED) {
                        capturePeakFrame(phase)
                    }
                    lastCapturedPhase = phase
                }
            }
        }
        
        // Observe hold state
        if (viewModel.isHoldExercise()) {
            lifecycleScope.launch {
                viewModel.holdElapsedMs.collectLatest { elapsed ->
                    elapsed?.let {
                        binding.tvRepCount.text = formatTimeMs(it)
                        binding.tvProgress.text = "${formatTimeMs(it)} / ${formatTimeMs(viewModel.getTargetDurationMs())}"
                    }
                }
            }
            
            lifecycleScope.launch {
                viewModel.holdState.collectLatest { holdState ->
                    holdState?.let { updateUIForHoldState(it) }
                }
            }
        }
        
        // Observe UI events
        lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                handleUIEvent(event)
            }
        }
        
        // Observe feedback events
        lifecycleScope.launch {
            viewModel.feedbackEvents.collectLatest { event ->
                handleFeedbackEvent(event)
            }
        }
        
        // Observe visual messages from feedback manager
        lifecycleScope.launch {
            viewModel.feedbackManager?.visualMessages?.collectLatest { message ->
                showGlassmorphicMessage(message)
            }
        }
    }
    
    /**
     * Observe training engine jointStateInfos for visual feedback
     * Called AFTER trainingEngine is created (in startVideoTraining or after countdown)
     * 
     * UPDATED: Now uses JointStateInfo instead of deprecated JointArrowInfo
     */
    private var stateInfosObserverJob: kotlinx.coroutines.Job? = null
    
    private fun observeTrainingEngineState() {
        // Cancel previous observer to avoid duplicates
        stateInfosObserverJob?.cancel()
        
        val engine = viewModel.trainingEngine ?: run {
            Log.e(TAG, "observeTrainingEngineState: trainingEngine is null!")
            return
        }
        
        stateInfosObserverJob = lifecycleScope.launch {
            // Observe state infos for visual feedback (Line indicator, colors)
            engine.jointStateInfos.collectLatest { stateInfos ->
                binding.skeletonOverlay.setStateInfos(stateInfos)
                
                // Check for error states (DANGER or WARNING)
                val hasErrors = stateInfos.any { 
                    it.value.state == JointState.DANGER || it.value.state == JointState.WARNING 
                }
                if (hasErrors) {
                    binding.vignetteOverlay.showError()
                } else {
                    binding.vignetteOverlay.clear()
                }
                
                // Trigger low-priority random messages during quiet time
                val positionErrors = engine.positionErrors.value
                viewModel.feedbackManager?.checkAndDeliverRandomMessage(
                    hasActiveErrors = hasErrors || positionErrors.isNotEmpty()
                )
            }
        }
    }
    
    private fun handleUIEvent(event: TrainingUIEvent) {
        when (event) {
            is TrainingUIEvent.ShowSetupPose -> {
                showPoseRequirements()
                binding.tvPoseStatus.visibility = View.VISIBLE
            }
            
            is TrainingUIEvent.StartCountdown -> {
                // Countdown is now started by supervisor action
                binding.tvPoseStatus.visibility = View.GONE
                binding.tvCountdown.visibility = View.VISIBLE
                binding.tvCountdown.alpha = 1f
            }
            
            is TrainingUIEvent.CountdownCancelled -> {
                updateUIForSessionState(SessionState.SETUP_POSE)
            }
            
            is TrainingUIEvent.PoseValidationUpdate -> {
                updatePoseValidationUI(event.result)
            }
            
            is TrainingUIEvent.TrainingStarted -> {
                val trackedIndices = viewModel.getTrackedLandmarkIndices()
                binding.skeletonOverlay.setTrainingMode(true, trackedIndices, useFrontCamera)
                observeTrainingEngineState()
            }
            
            is TrainingUIEvent.ExerciseCompleted,
            is TrainingUIEvent.TrainingCompleted -> {
                completeTraining()
            }
            
            is TrainingUIEvent.AutoPaused -> {
                handleAutoPaused(event.reason)
            }
            
            is TrainingUIEvent.NoPoseWarning -> {
                handleNoPoseWarning(event.elapsedMs)
            }
            
            is TrainingUIEvent.ExerciseSwitched -> {
                showExerciseSwitchIndicator(event.fromExercise, event.toExercise, event.repsThisSession)
                
                val trackedIndices = viewModel.getTrackedLandmarkIndices()
                binding.skeletonOverlay.setTrainingMode(true, trackedIndices, useFrontCamera)
            }
            
            is TrainingUIEvent.RoundCompleted -> {
                binding.glassmorphicMessage.showMotivation(
                    "Round ${event.roundNumber} Complete! ${event.totalRounds - event.roundNumber} more to go"
                )
            }
            
            is TrainingUIEvent.WorkoutCompleted -> {
                completeWorkout(event.totalReps, event.accuracy, event.durationMs)
            }
            
            is TrainingUIEvent.PauseVideoPlayback -> {
                videoModeController?.pause()
                updatePlayPauseIcon(isPlaying = false)
            }
            
            is TrainingUIEvent.ResumeVideoPlayback -> {
                videoModeController?.play()
                updatePlayPauseIcon(isPlaying = true)
            }

            else -> {
                // Intentionally no-op: future events should not crash UI.
            }
        }
    }
    
    // ==================== UI Updates ====================
    
    /**
     * Update UI based on SessionState (from SessionSupervisor)
     */
    private fun updateUIForSessionState(state: SessionState) {
        when (state) {
            SessionState.IDLE -> {
                // Initial state - waiting for exercise to load
            }
            
            SessionState.SETUP_POSE -> {
                binding.setupPosePanel.visibility = View.VISIBLE
                binding.countdownPanel.visibility = View.GONE
                binding.heroCounterContainer.visibility = View.GONE
                binding.tvProgress.visibility = View.GONE
                binding.completedPanel.visibility = View.GONE
                binding.progressContainer.visibility = View.GONE
                updatePlayPauseIcon(isPlaying = false)
            }
            
            SessionState.COUNTDOWN -> {
                AnimationUtils.slideOutPanel(binding.setupPosePanel, AnimationUtils.Direction.BOTTOM) {
                    binding.countdownPanel.visibility = View.VISIBLE
                }
                binding.heroCounterContainer.visibility = View.GONE
                binding.tvProgress.visibility = View.GONE
                binding.completedPanel.visibility = View.GONE
            }
            
            SessionState.TRAINING -> {
                binding.setupPosePanel.visibility = View.GONE
                binding.countdownPanel.visibility = View.GONE
                AnimationUtils.bounceIn(binding.heroCounterContainer)
                binding.heroCounterContainer.visibility = View.VISIBLE
                binding.tvProgress.visibility = View.VISIBLE
                binding.completedPanel.visibility = View.GONE
                binding.progressContainer.visibility = View.VISIBLE
                updatePlayPauseIcon(isPlaying = true)
            }
            
            SessionState.PAUSED -> {
                updatePlayPauseIcon(isPlaying = false)
            }
            
            SessionState.AUTO_PAUSED -> {
                binding.heroCounterContainer.visibility = View.VISIBLE
                binding.tvProgress.visibility = View.VISIBLE
                binding.progressContainer.visibility = View.VISIBLE
                updatePlayPauseIcon(isPlaying = false)
            }
            
            SessionState.RESUME_SETUP -> {
                binding.setupPosePanel.visibility = View.VISIBLE
                binding.countdownPanel.visibility = View.GONE
                binding.heroCounterContainer.visibility = View.VISIBLE
                binding.tvProgress.visibility = View.VISIBLE
                binding.progressContainer.visibility = View.VISIBLE
                binding.completedPanel.visibility = View.GONE
                updatePlayPauseIcon(isPlaying = false)
            }
            
            SessionState.RESUME_COUNTDOWN -> {
                binding.setupPosePanel.visibility = View.GONE
                binding.countdownPanel.visibility = View.VISIBLE
                binding.heroCounterContainer.visibility = View.VISIBLE
                binding.tvProgress.visibility = View.VISIBLE
                updatePlayPauseIcon(isPlaying = false)
            }
            
            SessionState.COMPLETED -> {
                AnimationUtils.slideOutPanel(binding.heroCounterContainer, AnimationUtils.Direction.TOP)
                binding.setupPosePanel.visibility = View.GONE
                binding.countdownPanel.visibility = View.GONE
                binding.tvProgress.visibility = View.GONE
                binding.completedPanel.visibility = View.VISIBLE
                binding.progressContainer.visibility = View.GONE
                updatePlayPauseIcon(isPlaying = false)
                binding.vignetteOverlay.clear()
            }
        }
    }
    
    private fun handleAutoPaused(reason: PauseReason) {
        val message = when (reason) {
            PauseReason.VISIBILITY -> "Required joints not visible"
            PauseReason.NO_POSE -> "No pose detected for too long"
            PauseReason.MANUAL -> "Training paused"
        }
        
        binding.glassmorphicMessage.showMessage(
            message,
            if (reason == PauseReason.MANUAL) GlassmorphicMessageView.TYPE_INFO 
            else GlassmorphicMessageView.TYPE_ERROR,
            durationMs = -1
        )
        
        if (reason != PauseReason.MANUAL) {
            binding.vignetteOverlay.showError()
        }
        
        Log.d(TAG, "Auto-paused: $reason")
    }
    
    private fun handleNoPoseWarning(elapsedMs: Long) {
        val remainingSeconds = ((4000 - elapsedMs) / 1000).toInt().coerceAtLeast(0)
        val message = "⚠️ No pose detected! Return in ${remainingSeconds}s..."
        
        binding.glassmorphicMessage.showMessage(
            message,
            GlassmorphicMessageView.TYPE_WARNING,
            durationMs = 500 // Short duration as it updates frequently
        )
        
        binding.vignetteOverlay.showWarning()
    }
    
    private fun updatePoseValidationUI(result: PoseValidator.ValidationResult) {
        val statusText = result.jointStatuses.joinToString("\n") { it.getStatusText() }
        binding.tvPoseStatus.text = statusText
    }
    
    private fun showPoseRequirements() {
        val text = viewModel.poseValidator.getPoseRequirementsText(
            viewModel.exerciseConfig.value,
            viewModel.poseVariantIndex.value
        )
        binding.tvPoseRequirements.text = text
    }
    
    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        val iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        binding.btnPlayPause.setImageResource(iconRes)
    }
    
    private fun handlePlayPauseClick() {
        if (isVideoMode) {
            videoModeController?.togglePlayback()?.let { isPlaying ->
                updatePlayPauseIcon(isPlaying)
            }
        } else {
            val currentState = viewModel.supervisor.state.value
            when (currentState) {
                SessionState.TRAINING -> viewModel.requestPause()
                SessionState.PAUSED, SessionState.AUTO_PAUSED -> viewModel.requestResume()
                else -> {}
            }
        }
    }
    
    private fun showGlassmorphicMessage(message: FeedbackManager.VisualMessage) {
        val type = when (message.type) {
            FeedbackManager.MessageType.TIP -> GlassmorphicMessageView.TYPE_TIP
            FeedbackManager.MessageType.WARNING -> GlassmorphicMessageView.TYPE_WARNING
            FeedbackManager.MessageType.ERROR -> GlassmorphicMessageView.TYPE_ERROR
            FeedbackManager.MessageType.MOTIVATION -> GlassmorphicMessageView.TYPE_MOTIVATION
            FeedbackManager.MessageType.INFO -> GlassmorphicMessageView.TYPE_INFO
        }
        
        binding.glassmorphicMessage.showMessage(message.text, type, message.durationMs)
        
        when (message.type) {
            FeedbackManager.MessageType.ERROR -> binding.vignetteOverlay.showError()
            FeedbackManager.MessageType.WARNING -> binding.vignetteOverlay.showWarning()
            else -> {}
        }
    }
    
    private fun updateUIForHoldState(holdState: HoldState) {
        val color = when (holdState) {
            HoldState.IDLE -> COLOR_DEFAULT
            HoldState.HOLDING -> COLOR_CORRECT
            HoldState.GRACE_PERIOD -> COLOR_WARNING
            HoldState.COMPLETED -> COLOR_CORRECT
            HoldState.FAILED -> COLOR_ERROR
        }
        binding.tvRepCount.setTextColor(color)
    }
    
    private fun showExerciseSwitchIndicator(fromExercise: String, toExercise: String, repsThisSession: Int) {
        val message = if (repsThisSession == 1) {
            "→ $toExercise"
        } else {
            "→ $toExercise ($repsThisSession reps)"
        }
        
        binding.glassmorphicMessage.showMessage(
            message,
            GlassmorphicMessageView.TYPE_INFO,
            durationMs = 800
        )
        
        AnimationUtils.crossfadeText(binding.tvExerciseName, toExercise)
    }
    
    // ==================== Completion ====================
    
    private fun completeTraining() {
        binding.skeletonOverlay.setTrainingMode(false)
        binding.vignetteOverlay.clear()
        
        // Hide the completed panel - we'll navigate to ReportActivity instead
        binding.completedPanel.visibility = View.GONE
        
        // Log capture stats
        val captureCount = frameCaptureManager?.getCaptureCount() ?: 0
        val storageSize = frameCaptureManager?.getFormattedStorageSize() ?: "0 B"
        Log.d(TAG, "Training completed. Captured $captureCount frames ($storageSize)")
        
        // Generate report and navigate directly to ReportActivity
        generateReportAndNavigate()
    }
    
    private fun completeWorkout(totalReps: Int, accuracy: Float, durationMs: Long) {
        binding.skeletonOverlay.setTrainingMode(false)
        
        binding.tvSummaryReps.text = "$totalReps"
        binding.tvSummaryCorrect.text = "${viewModel.workoutConfig.value?.exercises?.size ?: 0} exercises"
        binding.tvSummaryAccuracy.text = "${accuracy.toInt()}%"
        
        val minutes = (durationMs / 60000).toInt()
        val seconds = ((durationMs % 60000) / 1000).toInt()
        binding.tvSummaryDuration.text = String.format("%02d:%02d", minutes, seconds)
        
        binding.btnFinish.setOnClickListener { finishWithResult() }
        
        binding.glassmorphicMessage.showMotivation("🎉 Workout Complete!")
    }
    
    private fun finishWithResult() {
        val resultIntent = android.content.Intent().apply {
            if (viewModel.isWorkoutMode.value && viewModel.workoutTrainingEngine != null) {
                val progressInfo = viewModel.workoutTrainingEngine!!.getProgressInfo()
                putExtra(RESULT_REPS_COMPLETED, progressInfo.totalRepsCompleted)
                putExtra(RESULT_DURATION_MS, viewModel.getSessionDurationMs())
                putExtra(RESULT_ACCURACY, viewModel.getWorkoutAccuracy())
                putExtra(RESULT_IS_COMPLETED, viewModel.workoutTrainingEngine!!.isWorkoutCompleted.value)
            } else {
                val engine = viewModel.trainingEngine
                putExtra(RESULT_REPS_COMPLETED, engine?.getCurrentRep() ?: 0)
                putExtra(RESULT_DURATION_MS, viewModel.getSessionDurationMs())
                putExtra(RESULT_ACCURACY, engine?.getAccuracy() ?: 0f)
                putExtra(RESULT_IS_COMPLETED, engine?.isCompleted?.value ?: false)
            }
        }
        
        setResult(RESULT_OK, resultIntent)
        finish()
    }
    
    // ==================== Feedback Events ====================
    
    private fun handleFeedbackEvent(event: FeedbackEvent) {
        when (event) {
            is FeedbackEvent.RepCompleted -> {
                AnimationUtils.repCompletedPulse(
                    binding.tvRepCount,
                    event.isCorrect,
                    COLOR_CORRECT,
                    COLOR_WARNING,
                    COLOR_DEFAULT
                )
                
                // Mark as best rep if correct
                if (event.isCorrect) {
                    frameCaptureManager?.markAsBestRep(event.repNumber)
                }
            }
            
            is FeedbackEvent.JointErrorDetected -> {
                // Capture error frame (first occurrence per error type)
                val errorKey = "${event.error.jointCode}:${event.error.state.name}"
                val currentRep = (viewModel.trainingEngine?.getCurrentRep() ?: 0) + 1
                val phase = viewModel.currentPhase.value
                
                // Check if this is DANGER state - capture DANGER frame 🚨
                if (event.error.state == com.trainingvalidator.poc.training.models.JointState.DANGER) {
                    captureDangerFrame(
                        repNumber = currentRep,
                        phase = phase,
                        jointCode = event.error.jointCode,
                        actualAngle = event.error.actualAngle
                    )
                } else {
                    captureErrorFrame(currentRep, phase, errorKey)
                }
            }
            
            is FeedbackEvent.HoldGraceStarted -> {
                AnimationUtils.shake(binding.tvRepCount)
            }
            
            is FeedbackEvent.HoldFailed -> {
                AnimationUtils.shake(binding.tvRepCount, 15f)
                binding.tvRepCount.text = "00:00"
                binding.tvRepCount.setTextColor(COLOR_DEFAULT)
            }
            
            is FeedbackEvent.VisibilityWarning -> {
                binding.glassmorphicMessage.showMessage(
                    event.message.en,
                    GlassmorphicMessageView.TYPE_WARNING,
                    durationMs = 1000
                )
                binding.vignetteOverlay.showWarning()
            }
            
            else -> {}
        }
    }
    
    // ==================== Frame Capture ====================
    
    /**
     * Capture peak frame when reaching BOTTOM/EXTENDED phase
     */
    private fun capturePeakFrame(phase: Phase) {
        // Only capture during active training
        if (viewModel.supervisor.state.value != SessionState.TRAINING) {
            return
        }
        
        // Get bitmap from camera or video mode
        val bitmap = getBitmapForCapture()
        
        bitmap?.let { bmp ->
            val currentRep = (viewModel.trainingEngine?.getCurrentRep() ?: 0) + 1
            val angles = viewModel.trainingEngine?.currentAngles?.value ?: emptyMap()
            
            frameCaptureManager?.capturePeakFrame(
                bitmap = bmp,
                repNumber = currentRep,
                phase = phase,
                angles = angles
            )
            
            Log.d(TAG, "Captured peak frame for rep $currentRep at phase ${phase.name}")
            
            // Recycle if from video mode (it's a copy)
            if (isVideoMode) {
                bmp.recycle()
            }
        }
    }
    
    /**
     * Capture error frame when error detected
     */
    private fun captureErrorFrame(repNumber: Int, phase: Phase, errorKey: String) {
        // Only capture during active training
        if (viewModel.supervisor.state.value != SessionState.TRAINING) {
            return
        }
        
        // Get bitmap from camera or video mode
        val bitmap = getBitmapForCapture()
        
        bitmap?.let { bmp ->
            val angles = viewModel.trainingEngine?.currentAngles?.value ?: emptyMap()
            
            val captured = frameCaptureManager?.captureErrorFrame(
                bitmap = bmp,
                repNumber = repNumber,
                phase = phase,
                errorKey = errorKey,
                angles = angles
            )
            
            if (captured != null) {
                Log.d(TAG, "Captured error frame for $errorKey at rep $repNumber")
            }
            
            // Recycle if from video mode (it's a copy)
            if (isVideoMode) {
                bmp.recycle()
            }
        }
    }
    
    /**
     * Capture DANGER frame when DANGER state detected 🚨
     */
    private fun captureDangerFrame(repNumber: Int, phase: Phase, jointCode: String, actualAngle: Double) {
        // Only capture during active training
        if (viewModel.supervisor.state.value != SessionState.TRAINING) {
            return
        }
        
        // Get bitmap from camera or video mode
        val bitmap = getBitmapForCapture()
        
        bitmap?.let { bmp ->
            val angles = viewModel.trainingEngine?.currentAngles?.value ?: emptyMap()
            
            val captured = frameCaptureManager?.captureDangerFrame(
                bitmap = bmp,
                repNumber = repNumber,
                phase = phase,
                jointCode = jointCode,
                actualAngle = actualAngle,
                angles = angles
            )
            
            if (captured != null) {
                Log.d(TAG, "🚨 Captured DANGER frame for $jointCode at ${actualAngle.toInt()}° (rep $repNumber)")
            }
            
            // Recycle if from video mode (it's a copy)
            if (isVideoMode) {
                bmp.recycle()
            }
        }
    }
    
    /**
     * Get bitmap for frame capture (works in both camera and video mode)
     */
    private fun getBitmapForCapture(): android.graphics.Bitmap? {
        return if (isVideoMode) {
            // Get current frame from video controller
            videoModeController?.getCurrentFrameBitmap()
        } else {
            // Get from camera preview
            binding.previewView.bitmap
        }
    }
    
    /**
     * Generate report and navigate directly to ReportActivity
     */
    private fun generateReportAndNavigate() {
        val engine = viewModel.trainingEngine
        if (engine == null) {
            Log.e(TAG, "TrainingEngine is null!")
            showFallbackSummary()
            return
        }
        
        val exerciseConfig = viewModel.exerciseConfig.value
        if (exerciseConfig == null) {
            Log.e(TAG, "ExerciseConfig is null!")
            showFallbackSummary()
            return
        }
        
        val frameCaptures = frameCaptureManager?.getAllCaptures() ?: emptyList()
        Log.d(TAG, "Frame captures count: ${frameCaptures.size}")
        
        // Show loading state
        binding.heroCounterContainer.visibility = View.GONE
        binding.progressContainer.visibility = View.GONE
        binding.completedPanel.visibility = View.GONE
        
        binding.glassmorphicMessage.showMessage(
            "📊 Generating report...",
            GlassmorphicMessageView.TYPE_INFO,
            durationMs = -1
        )
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Generating report in background...")
                
                val report = ReportGenerator.generateFromEngine(
                    engine = engine,
                    exerciseConfig = exerciseConfig,
                    sessionDurationMs = viewModel.getSessionDurationMs(),
                    frameCaptures = frameCaptures
                )
                
                Log.d(TAG, "Report generated: id=${report.id}, accuracy=${report.summary.accuracy}%")
                
                val saved = reportStorage?.save(report) ?: false
                Log.d(TAG, "Report saved: $saved")
                
                launch(Dispatchers.Main) {
                    binding.glassmorphicMessage.hide()
                    
                    if (saved) {
                        Log.d(TAG, "Navigating to ReportActivity with id: ${report.id}")
                        
                        try {
                            val intent = ReportActivity.createIntent(this@TrainingActivity, report.id).apply {
                                putExtra(ReportActivity.EXTRA_EXERCISE_NAME, viewModel.exerciseName.value)
                            }
                            startActivity(intent)
                            finish()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start ReportActivity: ${e.message}", e)
                            showFallbackSummary()
                        }
                    } else {
                        Log.e(TAG, "Failed to save report, showing fallback")
                        showFallbackSummary()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating report: ${e.message}", e)
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    binding.glassmorphicMessage.hide()
                    Toast.makeText(
                        this@TrainingActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    showFallbackSummary()
                }
            }
        }
    }
    
    /**
     * Show fallback summary screen if report generation fails
     */
    private fun showFallbackSummary() {
        val summary = viewModel.trainingEngine?.stop()
        
        binding.skeletonOverlay.setTrainingMode(false)
        
        if (viewModel.isHoldExercise()) {
            val holdElapsed = viewModel.holdElapsedMs.value ?: 0L
            binding.tvSummaryReps.text = formatTimeMs(holdElapsed)
            binding.tvSummaryCorrect.text = "Target: ${formatTimeMs(viewModel.getTargetDurationMs())}"
            binding.tvSummaryAccuracy.text = "Grace periods: ${viewModel.trainingEngine?.getGracePeriodCount() ?: 0}"
        } else {
            binding.tvSummaryReps.text = "${summary?.totalReps ?: 0}"
            binding.tvSummaryCorrect.text = "${summary?.correctReps ?: 0} correct"
            binding.tvSummaryAccuracy.text = "${String.format("%.0f", summary?.accuracy ?: 0f)}%"
        }
        
        binding.tvSummaryDuration.text = summary?.getFormattedDuration() ?: "00:00"
        binding.completedPanel.visibility = android.view.View.VISIBLE
        
        binding.btnFinish.setOnClickListener { finishWithResult() }
    }
    
    // ==================== Camera & Pose Detection ====================

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                onCameraPermissionGranted()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun onCameraPermissionGranted() {
        initializePoseDetection()
        initializeCamera()
    }

    private fun initializePoseDetection() {
        poseLandmarkerHelper = PoseLandmarkerHelper(
            context = applicationContext,
            listener = this
        )
        
        lifecycleScope.launch(Dispatchers.IO) {
            poseLandmarkerHelper?.initialize(modelType = ModelType.FULL, useGpu = true)
            
            launch(Dispatchers.Main) {
                if (poseLandmarkerHelper?.isReady() == true) {
                    Log.d(TAG, "Pose detection ready")
                }
            }
        }
    }

    private fun initializeCamera() {
        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            previewView = binding.previewView
        )
        
        cameraManager?.startCamera(useFrontCamera = useFrontCamera) { imageProxy ->
            poseLandmarkerHelper?.detectPose(imageProxy, useFrontCamera)
        }
    }

    // ==================== PoseDetectionListener ====================

    override fun onPoseDetected(result: PoseResult) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (!::landmarkSmoother.isInitialized) return@launch
            
            updateFps()
            wasPoseDetectedLastFrame = true
            
            val smoothedLandmarks = landmarkSmoother.smooth(result.landmarks, result.timestampMs)
            
            val worldLandmarks = result.worldLandmarks?.let {
                landmarkSmoother.convertWorld(it)
            }
            
            val rawAngles = if (worldLandmarks != null) {
                AngleCalculator.calculateAllAnglesSmoothed(
                    worldLandmarks,
                    visibilityThreshold = 0.3f,
                    use3D = true
                )
            } else {
                AngleCalculator.calculateAllAnglesSmoothed(
                    smoothedLandmarks,
                    visibilityThreshold = 0.3f
                )
            }
            
            val angles = if (result.isFrontCamera) rawAngles.mirrored() else rawAngles
            
            // Forward pose frame to supervisor via ViewModel
            viewModel.onPoseFrame(angles, smoothedLandmarks, result.isFrontCamera)
            
            // Update skeleton overlay with JointStateInfo
            val stateInfos = viewModel.trainingEngine?.jointStateInfos?.value ?: emptyMap()
            val positionErrors = viewModel.trainingEngine?.positionErrors?.value ?: emptyList()
            
            binding.skeletonOverlay.updateWithStateInfos(
                smoothedLandmarks = smoothedLandmarks,
                inputImageWidth = result.imageWidth,
                inputImageHeight = result.imageHeight,
                angles = angles,
                stateInfos = stateInfos,
                positionErrors = positionErrors
            )
        }
    }

    override fun onNoPoseDetected() {
        lifecycleScope.launch(Dispatchers.Main) {
            updateFps()
            binding.skeletonOverlay.clear()

            // If we just transitioned from pose → no pose, clear stale form messages immediately.
            // Otherwise, warnings shown by supervisor after 2s could be immediately cleared every frame.
            if (wasPoseDetectedLastFrame && viewModel.supervisor.state.value == SessionState.TRAINING) {
                binding.glassmorphicMessage.hide()
                binding.vignetteOverlay.clear()
            }
            wasPoseDetectedLastFrame = false
            
            // Always notify supervisor about NoPose
            viewModel.onNoPoseDetected()
            
            // Update UI for setup pose state
            if (viewModel.supervisor.state.value == SessionState.SETUP_POSE) {
                viewModel.poseValidator.reset()
                binding.tvPoseStatus.text = "❌ No pose detected\nMake sure your full body is visible"
            }
        }
    }

    override fun onError(message: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(this@TrainingActivity, message, Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Pose detection error: $message")
        }
    }

    private fun updateFps() {
        frameCount++
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastFpsUpdateTime
        
        if (elapsed >= 1000) {
            currentFps = frameCount
            frameCount = 0
            lastFpsUpdateTime = currentTime
            binding.tvFps.text = "FPS: $currentFps"
        }
    }

    // ==================== Video Mode ====================
    
    private fun setupVideoMode() {
        Log.d(TAG, "Setting up VIDEO mode with URI: $videoUri")
        
        binding.previewView.visibility = View.GONE
        binding.videoTextureView.visibility = View.VISIBLE
        binding.btnSwitchCamera.visibility = View.GONE
        binding.videoControlsPanel.visibility = View.VISIBLE
        binding.setupPosePanel.visibility = View.GONE
        binding.btnSaveResults.visibility = View.VISIBLE
        
        videoModeController = VideoModeController(this, lifecycleScope)
        videoModeController?.setListener(createVideoModeListener())
        
        val uri = videoUri ?: return
        videoModeController?.initialize(binding.videoTextureView, uri, landmarkSmoother)
        
        setupVideoControls()
    }
    
    private fun createVideoModeListener(): VideoModeController.VideoModeListener {
        return object : VideoModeController.VideoModeListener {
            override fun onVideoReady(durationMs: Long) {
                binding.tvVideoDuration.text = formatTimeMs(durationMs)
                binding.glassmorphicMessage.showMessage(
                    "Video ready. Press play to start.",
                    GlassmorphicMessageView.TYPE_INFO
                )
            }
            
            override fun onPlaybackStateChanged(state: VideoManager.PlaybackState) {
                when (state) {
                    VideoManager.PlaybackState.PLAYING -> {
                        updatePlayPauseIcon(isPlaying = true)
                        if (viewModel.supervisor.state.value != SessionState.TRAINING &&
                            viewModel.supervisor.state.value != SessionState.COMPLETED) {
                            startVideoTraining()
                        }
                    }
                    VideoManager.PlaybackState.PAUSED -> {
                        updatePlayPauseIcon(isPlaying = false)
                    }
                    VideoManager.PlaybackState.ENDED -> {
                        updatePlayPauseIcon(isPlaying = false)
                        viewModel.onVideoEnded()
                    }
                    VideoManager.PlaybackState.ERROR -> {
                        binding.glassmorphicMessage.showError("Error playing video")
                    }
                    else -> {}
                }
            }
            
            override fun onProgressChanged(currentMs: Long, durationMs: Long) {
                binding.tvVideoCurrentTime.text = formatTimeMs(currentMs)
                if (durationMs > 0) {
                    val progress = (currentMs.toFloat() / durationMs.toFloat() * 100).toInt()
                    binding.videoSeekBar.progress = progress
                }
            }
            
            override fun onSeekPerformed() {
                viewModel.onVideoSeeked()
                binding.glassmorphicMessage.showMessage("Analysis reset", GlassmorphicMessageView.TYPE_INFO)
            }
            
            override fun onVideoEnded() {
                viewModel.onVideoEnded()
            }
            
            override fun onFrameProcessed(
                angles: JointAngles,
                smoothedLandmarks: List<SmoothedLandmark>,
                imageWidth: Int,
                imageHeight: Int
            ) {
                wasPoseDetectedLastFrame = true
                viewModel.onPoseFrame(angles, smoothedLandmarks, false)
                
                // Update skeleton overlay with JointStateInfo
                val stateInfos = viewModel.trainingEngine?.jointStateInfos?.value ?: emptyMap()
                val positionErrors = viewModel.trainingEngine?.positionErrors?.value ?: emptyList()
                
                binding.skeletonOverlay.updateWithStateInfos(
                    smoothedLandmarks = smoothedLandmarks,
                    inputImageWidth = imageWidth,
                    inputImageHeight = imageHeight,
                    angles = angles,
                    stateInfos = stateInfos,
                    positionErrors = positionErrors
                )
            }
            
            override fun onNoPoseDetected() {
                binding.skeletonOverlay.clear()
                // Same transition-based clearing as camera mode:
                // clear stale form feedback once when pose disappears.
                if (wasPoseDetectedLastFrame && viewModel.supervisor.state.value == SessionState.TRAINING) {
                    binding.glassmorphicMessage.hide()
                    binding.vignetteOverlay.clear()
                }
                wasPoseDetectedLastFrame = false
                viewModel.onNoPoseDetected()
            }
            
            override fun onResultsSaved(success: Boolean) {
                if (success) {
                    binding.glassmorphicMessage.showMotivation("Results saved!")
                    binding.btnSaveResults.isEnabled = false
                    binding.btnSaveResults.text = "Saved ✓"
                } else {
                    binding.glassmorphicMessage.showError("Failed to save results")
                }
            }
            
            override fun onError(message: String) {
                Toast.makeText(this@TrainingActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setupVideoControls() {
        binding.videoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            private var wasPlaying = false
            
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = videoModeController?.getDuration() ?: 0L
                    val position = (progress / 100f * duration).toLong()
                    binding.tvVideoCurrentTime.text = formatTimeMs(position)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                wasPlaying = videoModeController?.isPlaying() ?: false
                videoModeController?.pause()
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: 0
                val duration = videoModeController?.getDuration() ?: 0L
                val position = (progress / 100f * duration).toLong()
                videoModeController?.seekTo(position)
                
                if (wasPlaying) {
                    videoModeController?.play()
                }
            }
        })
        
        binding.btnSaveResults.setOnClickListener {
            val exerciseConfig = viewModel.exerciseConfig.value ?: return@setOnClickListener
            videoModeController?.saveResults(
                viewModel.trainingEngine ?: return@setOnClickListener,
                exerciseConfig
            )
        }
    }
    
    private fun startVideoTraining() {
        // Video mode starts immediately via supervisor
        viewModel.requestVideoStart()
        
        val trackedIndices = viewModel.getTrackedLandmarkIndices()
        binding.skeletonOverlay.setTrainingMode(true, trackedIndices, useFrontCamera = false)
        
        // Set up observers on the training engine
        observeTrainingEngineState()
        
        Log.d(TAG, "Video training started")
    }
    
    // ==================== Helpers ====================
    
    private fun formatTimeMs(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
    
    private fun getPhaseDisplayName(phase: Phase, isHoldExercise: Boolean): String {
        return when (phase) {
            Phase.IDLE -> if (isHoldExercise) "Get into Position" else "Get Ready"
            Phase.START -> "Ready"
            Phase.DOWN -> "Going Down"
            Phase.BOTTOM -> "Hold"
            Phase.UP -> "Going Up"
            Phase.PUSH -> "Push"
            Phase.EXTENDED -> "Extended"
            Phase.PULL -> "Pull"
            Phase.COUNT -> if (isHoldExercise) "Holding..." else "Counting"
        }
    }

    // ==================== Lifecycle ====================

    override fun onDestroy() {
        super.onDestroy()
        viewModel.countdownController.release()
        cameraManager?.stopCamera()
        videoModeController?.release()
        poseLandmarkerHelper?.close()
        poseLandmarkerHelper?.closeVideoMode()
    }
}
