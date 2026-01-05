package com.trainingvalidator.poc.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.analysis.AngleCalculator
import com.trainingvalidator.poc.analysis.LandmarkSmoother
import com.trainingvalidator.poc.camera.CameraManager
import com.trainingvalidator.poc.databinding.ActivityTrainingBinding
import com.trainingvalidator.poc.pose.ModelType
import com.trainingvalidator.poc.pose.PoseLandmarkerHelper
import com.trainingvalidator.poc.pose.PoseResult
import com.trainingvalidator.poc.training.TrainingEngine
import com.trainingvalidator.poc.training.engine.HoldState
import com.trainingvalidator.poc.training.engine.Phase
import com.trainingvalidator.poc.training.feedback.FeedbackConfig
import com.trainingvalidator.poc.training.feedback.FeedbackEvent
import com.trainingvalidator.poc.training.feedback.FeedbackManager
import com.trainingvalidator.poc.training.loader.ExerciseLoader
import com.trainingvalidator.poc.training.models.CountingMethod
import com.trainingvalidator.poc.training.models.DifficultyType
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.JointRole
import com.trainingvalidator.poc.training.engine.PositionError
import com.trainingvalidator.poc.training.engine.CameraPositionWarning
import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.ui.components.AnimationUtils
import com.trainingvalidator.poc.ui.components.GlassmorphicMessageView
import com.trainingvalidator.poc.ui.components.VignetteOverlayView
import com.trainingvalidator.poc.video.VideoManager
import com.trainingvalidator.poc.video.VideoAnalysisResult
import com.trainingvalidator.poc.video.toVideoAnalysisResult
import com.trainingvalidator.poc.storage.AnalysisResultStorage
import com.trainingvalidator.poc.R
import android.net.Uri
import android.widget.SeekBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * TrainingActivity - Professional Training Screen with Glassmorphic UI
 * 
 * Features:
 * - Mode-aware feedback (Camera: Audio + Haptic, Video: Glassmorphic)
 * - Smart animations (Slot counter, Ambient alerts, Countdown)
 * - Minimalist design with focus system
 * 
 * Flow:
 * 1. SETUP_POSE: Show required pose, validate user is in correct position
 * 2. COUNTDOWN: 3-2-1 countdown with animated numbers
 * 3. TRAINING: Active training with visual/audio feedback
 * 4. COMPLETED: Show summary with celebration
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
        
        // Training modes
        const val MODE_CAMERA = "camera"
        const val MODE_VIDEO = "video"
        
        // Defaults
        private const val DEFAULT_EXERCISE = "squat"
        private const val DEFAULT_DIFFICULTY = "beginner"
        
        // Countdown
        private const val COUNTDOWN_SECONDS = 3
        
        // Colors
        private val COLOR_CORRECT = Color.parseColor("#00E676")
        private val COLOR_WARNING = Color.parseColor("#FFC107")
        private val COLOR_ERROR = Color.parseColor("#FF5252")
        private val COLOR_DEFAULT = Color.WHITE
    }

    private lateinit var binding: ActivityTrainingBinding
    private var cameraManager: CameraManager? = null
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    
    // Training components
    private var exerciseConfig: ExerciseConfig? = null
    private var trainingEngine: TrainingEngine? = null
    private var feedbackManager: FeedbackManager? = null
    private var difficulty: DifficultyType = DifficultyType.BEGINNER
    private var poseVariantIndex: Int = 0
    
    // Video mode components
    private var trainingMode: String = MODE_CAMERA
    private var videoUri: Uri? = null
    private var videoManager: VideoManager? = null
    private var analysisResultStorage: AnalysisResultStorage? = null
    private var isVideoMode: Boolean = false
    
    // Landmark smoothing
    private val landmarkSmoother = LandmarkSmoother()
    
    // Current angles for pose validation
    private var currentAngles: JointAngles? = null
    
    // State machine
    private enum class TrainingState {
        SETUP_POSE,
        COUNTDOWN,
        TRAINING,
        PAUSED,
        COMPLETED
    }
    private var trainingState = TrainingState.SETUP_POSE
    
    // Countdown
    private var countdownTimer: CountDownTimer? = null
    private var countdownValue = COUNTDOWN_SECONDS
    
    // State
    private var useFrontCamera = true
    private var lastRepCount = 0
    
    // FPS calculation
    private var frameCount = 0
    private var lastFpsUpdateTime = System.currentTimeMillis()
    private var currentFps = 0
    
    // Pose validation
    private var poseValidFrames = 0
    private val requiredValidFrames = 10

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
        
        setupFullscreen()
        
        binding = ActivityTrainingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Get exercise parameters from intent
        val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: DEFAULT_EXERCISE
        val difficultyStr = intent.getStringExtra(EXTRA_DIFFICULTY) ?: DEFAULT_DIFFICULTY
        poseVariantIndex = intent.getIntExtra(EXTRA_POSE_VARIANT, 0)
        
        // Get training mode (camera or video)
        trainingMode = intent.getStringExtra(EXTRA_TRAINING_MODE) ?: MODE_CAMERA
        isVideoMode = trainingMode == MODE_VIDEO
        videoUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_VIDEO_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_VIDEO_URI)
        }
        
        // Validate video mode
        if (isVideoMode && videoUri == null) {
            Toast.makeText(this, "No video selected", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // Load exercise and initialize
        loadExercise(exerciseName, difficultyStr)
        
        setupUI()
        
        // Initialize based on mode
        if (isVideoMode) {
            setupVideoMode()
        } else {
            checkCameraPermission()
        }
        
        // Observe visual messages for Glassmorphic UI
        observeVisualMessages()
    }

    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    
    private fun loadExercise(exerciseName: String, difficultyStr: String) {
        exerciseConfig = ExerciseLoader.load(assets, exerciseName)
        
        if (exerciseConfig == null) {
            Toast.makeText(this, "Failed to load exercise: $exerciseName", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        difficulty = when (difficultyStr.lowercase()) {
            "beginner" -> DifficultyType.BEGINNER
            "normal" -> DifficultyType.NORMAL
            "advanced" -> DifficultyType.ADVANCED
            else -> DifficultyType.BEGINNER
        }
        
        // Initialize training engine
        trainingEngine = TrainingEngine(
            exerciseConfig = exerciseConfig!!,
            difficulty = difficulty,
            poseVariantIndex = poseVariantIndex
        )
        
        // Initialize feedback manager with mode awareness
        feedbackManager = FeedbackManager(
            context = this,
            config = FeedbackConfig(
                enableAudio = true,
                enableHaptic = true,
                language = "en"
            )
        ).apply {
            this.isVideoMode = this@TrainingActivity.isVideoMode
        }
        feedbackManager?.initialize()
        
        Log.d(TAG, "Loaded exercise: ${exerciseConfig!!.name.en}, Mode: $trainingMode")
    }

    private fun setupUI() {
        // Exercise name
        binding.tvExerciseName.text = exerciseConfig?.name?.en ?: "Exercise"
        
        // Close button
        binding.btnClose.setOnClickListener {
            finish()
        }
        
        // Switch camera button
        binding.btnSwitchCamera.setOnClickListener {
            useFrontCamera = !useFrontCamera
            cameraManager?.switchCamera(useFrontCamera)
            landmarkSmoother.reset()
        }
        
        // Play/Pause button (unified for camera and video modes)
        binding.btnPlayPause.setOnClickListener {
            if (isVideoMode) {
                // Video mode: toggle video playback + analysis
                toggleVideoPlayback()
            } else {
                // Camera mode: toggle training pause/resume
                when (trainingState) {
                    TrainingState.TRAINING -> pauseTraining()
                    TrainingState.PAUSED -> resumeTraining()
                    else -> {}
                }
            }
        }
        
        // Initial state
        updateUIForState(TrainingState.SETUP_POSE)
        showPoseRequirements()
    }
    
    /**
     * Observe visual messages from FeedbackManager for Glassmorphic UI
     */
    private fun observeVisualMessages() {
        lifecycleScope.launch {
            feedbackManager?.visualMessages?.collectLatest { message ->
                showGlassmorphicMessage(message)
            }
        }
    }
    
    /**
     * Show message using Glassmorphic UI component
     */
    private fun showGlassmorphicMessage(message: FeedbackManager.VisualMessage) {
        val type = when (message.type) {
            FeedbackManager.MessageType.TIP -> GlassmorphicMessageView.TYPE_TIP
            FeedbackManager.MessageType.WARNING -> GlassmorphicMessageView.TYPE_WARNING
            FeedbackManager.MessageType.ERROR -> GlassmorphicMessageView.TYPE_ERROR
            FeedbackManager.MessageType.MOTIVATION -> GlassmorphicMessageView.TYPE_MOTIVATION
            FeedbackManager.MessageType.INFO -> GlassmorphicMessageView.TYPE_INFO
        }
        
        binding.glassmorphicMessage.showMessage(message.text, type, message.durationMs)
        
        // Also trigger vignette for warnings/errors
        when (message.type) {
            FeedbackManager.MessageType.ERROR -> binding.vignetteOverlay.showError()
            FeedbackManager.MessageType.WARNING -> binding.vignetteOverlay.showWarning()
            else -> {}
        }
    }
    
    private fun showPoseRequirements() {
        val variant = exerciseConfig?.poseVariants?.getOrNull(poseVariantIndex) ?: return
        val primaryJoints = variant.getPrimaryJoints()
        
        val requirements = buildString {
            appendLine("Get into starting position:")
            appendLine()
            primaryJoints.forEach { joint ->
                val name = formatJointName(joint.joint)
                appendLine("• $name: ${joint.startPose.min.toInt()}° - ${joint.startPose.max.toInt()}°")
            }
        }
        
        binding.tvPoseRequirements.text = requirements
    }
    
    private fun formatJointName(jointCode: String): String {
        return jointCode
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
    
    private fun updateUIForState(state: TrainingState) {
        trainingState = state
        
        when (state) {
            TrainingState.SETUP_POSE -> {
                binding.setupPosePanel.visibility = View.VISIBLE
                binding.countdownPanel.visibility = View.GONE
                binding.heroCounterContainer.visibility = View.GONE
                binding.tvProgress.visibility = View.GONE
                binding.completedPanel.visibility = View.GONE
                binding.progressContainer.visibility = View.GONE
                // Show play button in setup state
                updatePlayPauseIcon(isPlaying = false)
            }
            
            TrainingState.COUNTDOWN -> {
                AnimationUtils.slideOutPanel(binding.setupPosePanel, AnimationUtils.Direction.BOTTOM) {
                    binding.countdownPanel.visibility = View.VISIBLE
                }
                binding.heroCounterContainer.visibility = View.GONE
                binding.tvProgress.visibility = View.GONE
                binding.completedPanel.visibility = View.GONE
            }
            
            TrainingState.TRAINING -> {
                binding.setupPosePanel.visibility = View.GONE
                binding.countdownPanel.visibility = View.GONE
                
                // Show hero counter with animation
                AnimationUtils.bounceIn(binding.heroCounterContainer)
                binding.heroCounterContainer.visibility = View.VISIBLE
                binding.tvProgress.visibility = View.VISIBLE
                
                binding.completedPanel.visibility = View.GONE
                binding.progressContainer.visibility = View.VISIBLE
                
                // Show pause icon when training is active
                updatePlayPauseIcon(isPlaying = true)
            }
            
            TrainingState.PAUSED -> {
                // Show play icon when paused
                updatePlayPauseIcon(isPlaying = false)
            }
            
            TrainingState.COMPLETED -> {
                AnimationUtils.slideOutPanel(binding.heroCounterContainer, AnimationUtils.Direction.TOP)
                binding.setupPosePanel.visibility = View.GONE
                binding.countdownPanel.visibility = View.GONE
                binding.tvProgress.visibility = View.GONE
                binding.completedPanel.visibility = View.VISIBLE
                binding.progressContainer.visibility = View.GONE
                
                // Show play icon (can restart or is finished)
                updatePlayPauseIcon(isPlaying = false)
                
                // Clear any alerts
                binding.vignetteOverlay.clear()
            }
        }
    }
    
    private fun validateStartPose(): Boolean {
        val variant = exerciseConfig?.poseVariants?.getOrNull(poseVariantIndex) ?: return false
        val primaryJoints = variant.getPrimaryJoints()
        val angles = currentAngles ?: return false
        
        var allValid = true
        val statusText = StringBuilder()
        
        for (joint in primaryJoints) {
            val angle = when (joint.joint) {
                "left_shoulder" -> angles.leftShoulder
                "right_shoulder" -> angles.rightShoulder
                "left_elbow" -> angles.leftElbow
                "right_elbow" -> angles.rightElbow
                "left_hip" -> angles.leftHip
                "right_hip" -> angles.rightHip
                "left_knee" -> angles.leftKnee
                "right_knee" -> angles.rightKnee
                "left_ankle" -> angles.leftAnkle
                "right_ankle" -> angles.rightAnkle
                else -> null
            }
            
            if (angle == null) {
                statusText.appendLine("❌ ${formatJointName(joint.joint)}: Not visible")
                allValid = false
            } else {
                val inRange = angle >= joint.startPose.min && angle <= joint.startPose.max
                val status = if (inRange) "✅" else "❌"
                val current = "${angle.toInt()}°"
                val expected = "${joint.startPose.min.toInt()}°-${joint.startPose.max.toInt()}°"
                statusText.appendLine("$status ${formatJointName(joint.joint)}: $current (need $expected)")
                
                if (!inRange) allValid = false
            }
        }
        
        binding.tvPoseStatus.text = statusText.toString()
        
        return allValid
    }
    
    private fun startCountdown() {
        updateUIForState(TrainingState.COUNTDOWN)
        countdownValue = COUNTDOWN_SECONDS
        
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer((COUNTDOWN_SECONDS * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdownValue = (millisUntilFinished / 1000).toInt() + 1
                
                // Animate countdown number
                AnimationUtils.animateCountdown(binding.tvCountdown, countdownValue.toString())
                
                // Audio feedback
                feedbackManager?.speakCountdown(countdownValue)
            }
            
            override fun onFinish() {
                // Animate "GO!"
                AnimationUtils.animateGoText(binding.tvCountdown) {
                    startTraining()
                }
                feedbackManager?.speakGo()
            }
        }.start()
    }
    
    private fun startTraining() {
        updateUIForState(TrainingState.TRAINING)
        
        // Reset message anti-spam state at the start of each training session
        feedbackManager?.resetMessageStates()
        trainingEngine?.start()
        
        // Enable training mode in skeleton overlay
        val trackedIndices = trainingEngine?.getTrackedLandmarkIndices()?.toSet() ?: emptySet()
        binding.skeletonOverlay.setTrainingMode(true, trackedIndices)
        
        // Observe training state
        observeTrainingState()
    }
    
    private fun pauseTraining() {
        updateUIForState(TrainingState.PAUSED)
        trainingEngine?.pause()
    }
    
    private fun resumeTraining() {
        updateUIForState(TrainingState.TRAINING)
        trainingEngine?.resume()
    }
    
    /**
     * Update Play/Pause button icon based on current state
     */
    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        val iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        binding.btnPlayPause.setImageResource(iconRes)
    }
    
    /**
     * Toggle video playback and analysis (Video mode)
     */
    private fun toggleVideoPlayback() {
        val isPlaying = videoManager?.isPlaying() ?: false
        if (isPlaying) {
            videoManager?.pause()
            updatePlayPauseIcon(false)
        } else {
            videoManager?.play()
            updatePlayPauseIcon(true)
        }
    }
    
    private fun completeTraining() {
        val engine = trainingEngine ?: return
        val summary = engine.stop()
        
        updateUIForState(TrainingState.COMPLETED)
        
        // Disable training mode in skeleton overlay
        binding.skeletonOverlay.setTrainingMode(false)
        
        // Show summary based on exercise type
        if (engine.isHoldExercise) {
            val holdElapsed = engine.holdElapsedMs.value ?: 0L
            val targetMs = engine.getTargetDurationMs()
            
            binding.tvSummaryReps.text = formatTimeMs(holdElapsed)
            binding.tvSummaryCorrect.text = "Target: ${formatTimeMs(targetMs)}"
            binding.tvSummaryAccuracy.text = "Grace periods: ${engine.getGracePeriodCount()}"
            binding.tvSummaryDuration.text = summary.getFormattedDuration()
        } else {
            binding.tvSummaryReps.text = "${summary.totalReps}"
            binding.tvSummaryCorrect.text = "${summary.correctReps} correct"
            binding.tvSummaryAccuracy.text = "${String.format("%.0f", summary.accuracy)}%"
            binding.tvSummaryDuration.text = summary.getFormattedDuration()
        }
        
        binding.btnFinish.setOnClickListener {
            finish()
        }
    }
    
    private fun observeTrainingState() {
        val engine = trainingEngine ?: return
        
        if (engine.isHoldExercise) {
            observeHoldState(engine)
        } else {
            observeRepState(engine)
        }
        
        // Observe phase with animation
        lifecycleScope.launch {
            engine.currentPhase.collectLatest { phase ->
                AnimationUtils.crossfadeText(
                    binding.tvPhase,
                    getPhaseDisplayName(phase, engine.isHoldExercise)
                )
            }
        }
        
        // Observe arrow infos for visual feedback
        lifecycleScope.launch {
            engine.arrowInfos.collectLatest { arrowInfos ->
                binding.skeletonOverlay.setArrowInfos(arrowInfos)
                
                // Update vignette based on error state
                val hasErrors = arrowInfos.any { it.value.isError }
                if (hasErrors) {
                    binding.vignetteOverlay.showError()
                } else {
                    binding.vignetteOverlay.clear()
                }
            }
        }
        
        // Observe completion
        lifecycleScope.launch {
            engine.isCompleted.collectLatest { isCompleted ->
                if (isCompleted && trainingState == TrainingState.TRAINING) {
                    completeTraining()
                }
            }
        }
        
        // Observe feedback events
        lifecycleScope.launch {
            engine.events.collectLatest { event ->
                feedbackManager?.emit(event)
                handleFeedbackEvent(event)
            }
        }
    }
    
    private fun observeRepState(engine: TrainingEngine) {
        lifecycleScope.launch {
            engine.repCount.collectLatest { count ->
                // Animate counter change (Slot Machine effect)
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
                
                // Update progress
                val target = engine.getTargetReps()
                binding.tvProgress.text = "$count / $target"
                
                // Update progress bar
                val progress = if (target > 0) (count.toFloat() / target * 100).toInt() else 0
                binding.progressBar.progress = progress
                binding.tvProgressPercent.text = "$progress%"
            }
        }
    }
    
    private fun observeHoldState(engine: TrainingEngine) {
        lifecycleScope.launch {
            engine.holdElapsedMs.collectLatest { elapsedMs ->
                elapsedMs?.let {
                    val targetMs = engine.getTargetDurationMs()
                    
                    binding.tvRepCount.text = formatTimeMs(it)
                    binding.tvProgress.text = "${formatTimeMs(it)} / ${formatTimeMs(targetMs)}"
                    
                    // Update progress
                    val progress = if (targetMs > 0) (it.toFloat() / targetMs * 100).toInt().coerceAtMost(100) else 0
                    binding.progressBar.progress = progress
                    binding.tvProgressPercent.text = "$progress%"
                }
            }
        }
        
        lifecycleScope.launch {
            engine.holdState.collectLatest { holdState ->
                holdState?.let {
                    updateUIForHoldState(it)
                }
            }
        }
        
        lifecycleScope.launch {
            engine.graceRemainingMs.collectLatest { graceMs ->
                if (graceMs != null && graceMs > 0) {
                    binding.tvPhase.text = "⚠️ Return! ${String.format("%.1f", graceMs / 1000f)}s"
                    binding.tvPhase.setTextColor(COLOR_WARNING)
                    binding.vignetteOverlay.showWarning()
                } else {
                    binding.tvPhase.setTextColor(COLOR_DEFAULT)
                }
            }
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
    
    private fun formatTimeMs(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
    
    private fun getPhaseDisplayName(phase: Phase, isHoldExercise: Boolean = false): String {
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
    
    private fun handleFeedbackEvent(event: FeedbackEvent) {
        when (event) {
            is FeedbackEvent.RepCompleted -> {
                // Pulse animation on rep complete
                val color = if (event.isCorrect) COLOR_CORRECT else COLOR_WARNING
                AnimationUtils.repCompletedPulse(
                    binding.tvRepCount,
                    event.isCorrect,
                    COLOR_CORRECT,
                    COLOR_WARNING,
                    COLOR_DEFAULT
                )
            }
            
            is FeedbackEvent.JointErrorDetected -> {
                // Visual feedback handled by SkeletonOverlay and Vignette
            }
            
            is FeedbackEvent.HoldStarted -> {
                Log.d(TAG, "Hold started!")
            }
            
            is FeedbackEvent.HoldGraceStarted -> {
                AnimationUtils.shake(binding.tvRepCount)
            }
            
            is FeedbackEvent.HoldResumed -> {
                Log.d(TAG, "Hold resumed from grace period")
            }
            
            is FeedbackEvent.HoldCompleted -> {
                Log.d(TAG, "Hold completed! Total: ${event.totalMs}ms")
            }
            
            is FeedbackEvent.HoldFailed -> {
                Log.d(TAG, "Hold failed at ${event.elapsedBeforeFailMs}ms")
                AnimationUtils.shake(binding.tvRepCount, 15f)
                binding.tvRepCount.text = "00:00"
                binding.tvRepCount.setTextColor(COLOR_DEFAULT)
            }
            
            is FeedbackEvent.PositionErrorDetected -> {
                Log.d(TAG, "Position error: ${event.error.checkId}")
            }
            
            is FeedbackEvent.PositionWarningDetected -> {
                Log.d(TAG, "Position warning: ${event.error.checkId}")
            }
            
            is FeedbackEvent.CameraPositionWarning -> {
                showCameraWarning(event.warning)
            }
            
            else -> {}
        }
    }
    
    private fun showCameraWarning(warning: CameraPositionWarning) {
        if (isVideoMode) {
            binding.glassmorphicMessage.showMessage(
                warning.message.en,
                GlassmorphicMessageView.TYPE_INFO
            )
        } else {
            feedbackManager?.speak(warning.message.en)
        }
    }

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
            updateFps()
            
            val smoothedLandmarks = landmarkSmoother.smooth(
                result.landmarks,
                result.timestampMs
            )
            
            val worldLandmarks = result.worldLandmarks?.let {
                landmarkSmoother.convertWorld(it)
            }
            
            val angles = if (worldLandmarks != null) {
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
            
            currentAngles = angles
            
            when (trainingState) {
                TrainingState.SETUP_POSE -> {
                    val isValid = validateStartPose()
                    
                    if (isValid) {
                        poseValidFrames++
                        if (poseValidFrames >= requiredValidFrames) {
                            startCountdown()
                        }
                    } else {
                        poseValidFrames = 0
                    }
                }
                
                TrainingState.COUNTDOWN -> {
                    val isValid = validateStartPose()
                    if (!isValid) {
                        countdownTimer?.cancel()
                        poseValidFrames = 0
                        updateUIForState(TrainingState.SETUP_POSE)
                    }
                }
                
                TrainingState.TRAINING -> {
                    trainingEngine?.processFrame(angles, smoothedLandmarks)
                }
                
                else -> {}
            }
            
            val arrowInfos = trainingEngine?.arrowInfos?.value ?: emptyMap()
            val positionErrors = trainingEngine?.positionErrors?.value ?: emptyList()
            
            binding.skeletonOverlay.updateWithArrowInfos(
                smoothedLandmarks = smoothedLandmarks,
                inputImageWidth = result.imageWidth,
                inputImageHeight = result.imageHeight,
                angles = angles,
                arrowInfos = arrowInfos,
                positionErrors = positionErrors
            )
        }
    }

    override fun onNoPoseDetected() {
        lifecycleScope.launch(Dispatchers.Main) {
            updateFps()
            binding.skeletonOverlay.clear()
            
            if (trainingState == TrainingState.SETUP_POSE) {
                poseValidFrames = 0
                binding.tvPoseStatus.text = "❌ No pose detected\nMake sure your full body is visible"
            }
            
            if (trainingState == TrainingState.COUNTDOWN) {
                countdownTimer?.cancel()
                poseValidFrames = 0
                updateUIForState(TrainingState.SETUP_POSE)
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

    // ==================== VIDEO MODE ====================
    
    private fun setupVideoMode() {
        Log.d(TAG, "Setting up VIDEO mode with URI: $videoUri")
        
        binding.previewView.visibility = View.GONE
        binding.videoTextureView.visibility = View.VISIBLE
        binding.btnSwitchCamera.visibility = View.GONE
        binding.videoControlsPanel.visibility = View.VISIBLE
        binding.setupPosePanel.visibility = View.GONE
        binding.btnSaveResults.visibility = View.VISIBLE
        
        analysisResultStorage = AnalysisResultStorage(this)
        
        initializePoseDetectionForVideo()
        setupVideoControls()
        initializeVideoManager()
    }
    
    private fun initializePoseDetectionForVideo() {
        poseLandmarkerHelper = PoseLandmarkerHelper(
            context = applicationContext,
            listener = this
        )
        
        lifecycleScope.launch(Dispatchers.IO) {
            poseLandmarkerHelper?.initializeForVideo(modelType = ModelType.FULL, useGpu = true)
            
            launch(Dispatchers.Main) {
                if (poseLandmarkerHelper?.isVideoModeReady() == true) {
                    Log.d(TAG, "Pose detection VIDEO mode ready")
                }
            }
        }
    }
    
    private fun setupVideoControls() {
        // Play/Pause is now handled by the unified btnPlayPause in bottomDeck
        // Rewind/Forward buttons removed - use SeekBar instead
        
        binding.videoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            private var wasPlaying = false
            
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = videoManager?.getDuration() ?: 0L
                    val position = (progress / 100f * duration).toLong()
                    binding.tvVideoCurrentTime.text = formatTimeMs(position)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                wasPlaying = videoManager?.isPlaying() ?: false
                videoManager?.pause()
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: 0
                val duration = videoManager?.getDuration() ?: 0L
                val position = (progress / 100f * duration).toLong()
                videoManager?.seekTo(position)
                
                if (wasPlaying) {
                    videoManager?.play()
                }
            }
        })
        
        binding.btnSaveResults.setOnClickListener {
            saveVideoAnalysisResults()
        }
    }
    
    private fun initializeVideoManager() {
        val uri = videoUri ?: return
        
        videoManager = VideoManager(
            context = this,
            textureView = binding.videoTextureView,
            onFrameAvailable = { bitmap, timestampMs ->
                processVideoFrame(bitmap, timestampMs)
            },
            onPlaybackStateChanged = { state ->
                handleVideoPlaybackState(state)
            },
            onProgressChanged = { currentMs, durationMs ->
                updateVideoProgress(currentMs, durationMs)
            },
            onSeekPerformed = {
                handleVideoSeek()
            },
            onVideoEnded = {
                handleVideoEnded()
            }
        )
        
        videoManager?.loadVideo(uri)
    }
    
    private fun processVideoFrame(bitmap: android.graphics.Bitmap, timestampMs: Long) {
        if (trainingState != TrainingState.TRAINING) return
        
        val poseResult = poseLandmarkerHelper?.detectPoseFromBitmap(bitmap, timestampMs)
        
        if (poseResult != null) {
            lifecycleScope.launch(Dispatchers.Main) {
                updateFps()
                
                val smoothedLandmarks = landmarkSmoother.smooth(
                    poseResult.landmarks,
                    timestampMs
                )
                
                val worldLandmarks = poseResult.worldLandmarks?.let {
                    landmarkSmoother.convertWorld(it)
                }
                
                val angles = if (worldLandmarks != null) {
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
                
                currentAngles = angles
                
                trainingEngine?.processFrame(angles, smoothedLandmarks)
                
                val arrowInfos = trainingEngine?.arrowInfos?.value ?: emptyMap()
                val positionErrors = trainingEngine?.positionErrors?.value ?: emptyList()
                
                binding.skeletonOverlay.updateWithArrowInfos(
                    smoothedLandmarks = smoothedLandmarks,
                    inputImageWidth = poseResult.imageWidth,
                    inputImageHeight = poseResult.imageHeight,
                    angles = angles,
                    arrowInfos = arrowInfos,
                    positionErrors = positionErrors
                )
            }
        } else {
            lifecycleScope.launch(Dispatchers.Main) {
                binding.skeletonOverlay.clear()
            }
        }
    }
    
    private fun handleVideoPlaybackState(state: VideoManager.PlaybackState) {
        Log.d(TAG, "Video playback state: $state")
        
        when (state) {
            VideoManager.PlaybackState.READY -> {
                val duration = videoManager?.getDuration() ?: 0L
                binding.tvVideoDuration.text = formatTimeMs(duration)
                
                binding.glassmorphicMessage.showMessage(
                    "Video ready. Press play to start.",
                    GlassmorphicMessageView.TYPE_INFO
                )
            }
            
            VideoManager.PlaybackState.PLAYING -> {
                updatePlayPauseIcon(isPlaying = true)
                
                if (trainingState != TrainingState.TRAINING && trainingState != TrainingState.COMPLETED) {
                    startVideoTraining()
                }
            }
            
            VideoManager.PlaybackState.PAUSED -> {
                updatePlayPauseIcon(isPlaying = false)
            }
            
            VideoManager.PlaybackState.ENDED -> {
                updatePlayPauseIcon(isPlaying = false)
            }
            
            VideoManager.PlaybackState.ERROR -> {
                binding.glassmorphicMessage.showError("Error playing video")
            }
            
            else -> {}
        }
    }
    
    private fun startVideoTraining() {
        updateUIForState(TrainingState.TRAINING)
        trainingEngine?.start()
        
        val trackedIndices = trainingEngine?.getTrackedLandmarkIndices()?.toSet() ?: emptySet()
        binding.skeletonOverlay.setTrainingMode(true, trackedIndices)
        
        observeTrainingState()
        
        Log.d(TAG, "Video training started")
    }
    
    private fun updateVideoProgress(currentMs: Long, durationMs: Long) {
        binding.tvVideoCurrentTime.text = formatTimeMs(currentMs)
        
        if (durationMs > 0) {
            val progress = (currentMs.toFloat() / durationMs.toFloat() * 100).toInt()
            binding.videoSeekBar.progress = progress
        }
    }
    
    private fun handleVideoSeek() {
        Log.d(TAG, "Video seek performed - resetting analysis state")
        
        poseLandmarkerHelper?.resetForVideo()
        landmarkSmoother.reset()
        
        trainingEngine?.stop()
        trainingEngine?.start()
        
        binding.glassmorphicMessage.showMessage("Analysis reset", GlassmorphicMessageView.TYPE_INFO)
    }
    
    private fun handleVideoEnded() {
        Log.d(TAG, "Video ended - completing training")
        
        if (trainingState == TrainingState.TRAINING) {
            completeTraining()
        }
    }
    
    private fun saveVideoAnalysisResults() {
        val engine = trainingEngine ?: return
        val config = exerciseConfig ?: return
        val uri = videoUri?.toString() ?: return
        
        val summary = engine.stop()
        
        val result = summary.toVideoAnalysisResult(
            exerciseId = config.fileName,
            exerciseName = config.name,
            videoUri = uri,
            videoDurationMs = videoManager?.getDuration() ?: 0L,
            difficulty = difficulty,
            holdDurationMs = if (engine.isHoldExercise) engine.holdElapsedMs.value else null,
            holdTargetMs = if (engine.isHoldExercise) engine.getTargetDurationMs() else null,
            gracePeriodsUsed = if (engine.isHoldExercise) engine.getGracePeriodCount() else null,
            holdCompleted = if (engine.isHoldExercise) engine.isHoldCompleted() else null
        )
        
        val saved = analysisResultStorage?.save(result) ?: false
        
        if (saved) {
            binding.glassmorphicMessage.showMotivation("Results saved!")
            binding.btnSaveResults.isEnabled = false
            binding.btnSaveResults.text = "Saved ✓"
        } else {
            binding.glassmorphicMessage.showError("Failed to save results")
        }
    }

    // ==================== Lifecycle ====================

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
        cameraManager?.stopCamera()
        videoManager?.release()
        poseLandmarkerHelper?.close()
        poseLandmarkerHelper?.closeVideoMode()
        feedbackManager?.release()
    }
}
