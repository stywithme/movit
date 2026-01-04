package com.trainingvalidator.poc.ui

import android.Manifest
import android.content.pm.PackageManager
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
import com.trainingvalidator.poc.analysis.JointAngles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * TrainingActivity - Main training screen with exercise validation
 * 
 * Flow:
 * 1. SETUP_POSE: Show required pose, validate user is in correct position
 * 2. COUNTDOWN: 3-2-1 countdown when pose is correct
 * 3. TRAINING: Active training with rep counting
 * 4. COMPLETED: Show summary
 */
class TrainingActivity : AppCompatActivity(), PoseLandmarkerHelper.PoseDetectionListener {

    companion object {
        private const val TAG = "TrainingActivity"
        
        // Intent extras
        const val EXTRA_EXERCISE_NAME = "exercise_name"
        const val EXTRA_DIFFICULTY = "difficulty"
        const val EXTRA_POSE_VARIANT = "pose_variant"
        
        // Defaults
        private const val DEFAULT_EXERCISE = "squat"
        private const val DEFAULT_DIFFICULTY = "beginner"
        
        // Countdown
        private const val COUNTDOWN_SECONDS = 3
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
    
    // Landmark smoothing
    private val landmarkSmoother = LandmarkSmoother()
    
    // Current angles for pose validation
    private var currentAngles: JointAngles? = null
    
    // State machine
    private enum class TrainingState {
        SETUP_POSE,     // Waiting for user to get into position
        COUNTDOWN,      // 3-2-1 countdown
        TRAINING,       // Active training
        PAUSED,         // Paused
        COMPLETED       // Training completed
    }
    private var trainingState = TrainingState.SETUP_POSE
    
    // Countdown
    private var countdownTimer: CountDownTimer? = null
    private var countdownValue = COUNTDOWN_SECONDS
    
    // State
    private var useFrontCamera = true
    
    // FPS calculation
    private var frameCount = 0
    private var lastFpsUpdateTime = System.currentTimeMillis()
    private var currentFps = 0
    
    // Pose validation
    private var poseValidFrames = 0
    private val requiredValidFrames = 10 // ~0.3 seconds at 30fps

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
        
        // Load exercise and initialize
        loadExercise(exerciseName, difficultyStr)
        
        setupUI()
        checkCameraPermission()
    }

    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    
    private fun loadExercise(exerciseName: String, difficultyStr: String) {
        // Load exercise config from assets
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
        
        // Initialize feedback manager
        feedbackManager = FeedbackManager(
            context = this,
            config = FeedbackConfig(
                enableAudio = true,
                enableHaptic = true,
                language = "en"
            )
        )
        feedbackManager?.initialize()
        
        Log.d(TAG, "Loaded exercise: ${exerciseConfig!!.name.en}")
        Log.d(TAG, "Difficulty: $difficulty")
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
        
        // Pause/Resume button
        binding.btnPauseResume.setOnClickListener {
            when (trainingState) {
                TrainingState.TRAINING -> pauseTraining()
                TrainingState.PAUSED -> resumeTraining()
                else -> {}
            }
        }
        
        // Initial state
        updateUIForState(TrainingState.SETUP_POSE)
        showPoseRequirements()
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
                binding.trainingPanel.visibility = View.GONE
                binding.completedPanel.visibility = View.GONE
                binding.btnPauseResume.visibility = View.GONE
            }
            
            TrainingState.COUNTDOWN -> {
                binding.setupPosePanel.visibility = View.GONE
                binding.countdownPanel.visibility = View.VISIBLE
                binding.trainingPanel.visibility = View.GONE
                binding.completedPanel.visibility = View.GONE
                binding.btnPauseResume.visibility = View.GONE
            }
            
            TrainingState.TRAINING -> {
                binding.setupPosePanel.visibility = View.GONE
                binding.countdownPanel.visibility = View.GONE
                binding.trainingPanel.visibility = View.VISIBLE
                binding.completedPanel.visibility = View.GONE
                binding.btnPauseResume.visibility = View.VISIBLE
                binding.btnPauseResume.text = "Pause"
            }
            
            TrainingState.PAUSED -> {
                binding.btnPauseResume.text = "Resume"
            }
            
            TrainingState.COMPLETED -> {
                binding.setupPosePanel.visibility = View.GONE
                binding.countdownPanel.visibility = View.GONE
                binding.trainingPanel.visibility = View.GONE
                binding.completedPanel.visibility = View.VISIBLE
                binding.btnPauseResume.visibility = View.GONE
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
            val angle = trainingEngine?.let { engine ->
                val trackedAngles = mutableMapOf<String, Double>()
                // Get angle for this joint
                val angleValue = when (joint.joint) {
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
                angleValue
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
                binding.tvCountdown.text = countdownValue.toString()
                
                // Vibrate
                feedbackManager?.speak(countdownValue.toString())
            }
            
            override fun onFinish() {
                startTraining()
            }
        }.start()
    }
    
    private fun startTraining() {
        updateUIForState(TrainingState.TRAINING)
        trainingEngine?.start()
        
        // Enable training mode in skeleton overlay with moving segments
        val trackedIndices = trainingEngine?.getTrackedLandmarkIndices()?.toSet() ?: emptySet()
        val movingSegments = getMovingSegments()
        binding.skeletonOverlay.setTrainingMode(true, trackedIndices, movingSegments)
        
        // Observe training state
        observeTrainingState()
        
        feedbackManager?.speak("Go!")
    }
    
    /**
     * Get moving segments from exercise config
     */
    private fun getMovingSegments(): Map<String, com.trainingvalidator.poc.training.models.MovingSegment> {
        val variant = exerciseConfig?.poseVariants?.getOrNull(poseVariantIndex) ?: return emptyMap()
        val segments = mutableMapOf<String, com.trainingvalidator.poc.training.models.MovingSegment>()
        
        for (joint in variant.trackedJoints) {
            joint.movingSegment?.let { segment ->
                segments[joint.joint] = segment
            }
        }
        
        return segments
    }
    
    private fun pauseTraining() {
        updateUIForState(TrainingState.PAUSED)
        trainingEngine?.pause()
    }
    
    private fun resumeTraining() {
        updateUIForState(TrainingState.TRAINING)
        trainingEngine?.resume()
    }
    
    private fun completeTraining() {
        val engine = trainingEngine ?: return
        val summary = engine.stop()
        
        updateUIForState(TrainingState.COMPLETED)
        
        // Disable training mode in skeleton overlay
        binding.skeletonOverlay.setTrainingMode(false)
        
        // Show summary based on exercise type
        if (engine.isHoldExercise) {
            // Hold exercise summary
            val holdElapsed = engine.holdElapsedMs.value ?: 0L
            val targetMs = engine.getTargetDurationMs()
            
            binding.tvSummaryReps.text = formatTimeMs(holdElapsed)
            binding.tvSummaryCorrect.text = "Target: ${formatTimeMs(targetMs)}"
            binding.tvSummaryAccuracy.text = "Grace periods: ${engine.getGracePeriodCount()}"
            binding.tvSummaryDuration.text = summary.getFormattedDuration()
        } else {
            // Rep-based exercise summary
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
            // Hold exercise: Observe hold-specific flows
            observeHoldState(engine)
        } else {
            // Rep-based exercise: Observe rep count
            observeRepState(engine)
        }
        
        // Common observations for both modes
        
        // Observe phase
        lifecycleScope.launch {
            engine.currentPhase.collectLatest { phase ->
                binding.tvPhase.text = getPhaseDisplayName(phase, engine.isHoldExercise)
            }
        }
        
        // Observe arrow infos for visual feedback
        lifecycleScope.launch {
            engine.arrowInfos.collectLatest { arrowInfos ->
                binding.skeletonOverlay.setArrowInfos(arrowInfos)
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
    
    /**
     * Observe rep-based exercise state
     */
    private fun observeRepState(engine: TrainingEngine) {
        lifecycleScope.launch {
            engine.repCount.collectLatest { count ->
                binding.tvRepCount.text = count.toString()
                
                // Update progress
                val target = engine.getTargetReps()
                binding.tvProgress.text = "$count / $target"
            }
        }
    }
    
    /**
     * Observe hold exercise state
     */
    private fun observeHoldState(engine: TrainingEngine) {
        // Observe hold elapsed/remaining time
        lifecycleScope.launch {
            engine.holdElapsedMs.collectLatest { elapsedMs ->
                elapsedMs?.let {
                    val remainingMs = engine.holdRemainingMs.value ?: 0L
                    val targetMs = engine.getTargetDurationMs()
                    
                    // Update time display
                    binding.tvRepCount.text = formatTimeMs(it)
                    binding.tvProgress.text = "${formatTimeMs(it)} / ${formatTimeMs(targetMs)}"
                }
            }
        }
        
        // Observe hold progress
        lifecycleScope.launch {
            engine.holdProgress.collectLatest { progress ->
                progress?.let {
                    // Could update a progress bar here if available
                    // binding.holdProgressBar.progress = (it * 100).toInt()
                }
            }
        }
        
        // Observe hold state for UI updates
        lifecycleScope.launch {
            engine.holdState.collectLatest { holdState ->
                holdState?.let {
                    updateUIForHoldState(it)
                }
            }
        }
        
        // Observe grace period
        lifecycleScope.launch {
            engine.graceRemainingMs.collectLatest { graceMs ->
                if (graceMs != null && graceMs > 0) {
                    // Show grace period warning
                    binding.tvPhase.text = "⚠️ Return! ${String.format("%.1f", graceMs / 1000f)}s"
                    binding.tvPhase.setTextColor(getColor(android.R.color.holo_orange_light))
                } else {
                    // Reset phase text color
                    binding.tvPhase.setTextColor(getColor(android.R.color.white))
                }
            }
        }
    }
    
    /**
     * Update UI based on hold state
     */
    private fun updateUIForHoldState(holdState: HoldState) {
        when (holdState) {
            HoldState.IDLE -> {
                binding.tvRepCount.setTextColor(getColor(android.R.color.white))
            }
            HoldState.HOLDING -> {
                binding.tvRepCount.setTextColor(getColor(android.R.color.holo_green_light))
            }
            HoldState.GRACE_PERIOD -> {
                binding.tvRepCount.setTextColor(getColor(android.R.color.holo_orange_light))
            }
            HoldState.COMPLETED -> {
                binding.tvRepCount.setTextColor(getColor(android.R.color.holo_green_light))
            }
            HoldState.FAILED -> {
                binding.tvRepCount.setTextColor(getColor(android.R.color.holo_red_light))
            }
        }
    }
    
    /**
     * Format milliseconds to mm:ss format
     */
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
                val color = if (event.isCorrect) {
                    getColor(android.R.color.holo_green_light)
                } else {
                    getColor(android.R.color.holo_orange_light)
                }
                binding.tvRepCount.setTextColor(color)
                
                // Reset color after 500ms
                binding.tvRepCount.postDelayed({
                    binding.tvRepCount.setTextColor(getColor(android.R.color.white))
                }, 500)
            }
            
            is FeedbackEvent.JointErrorDetected -> {
                // DISABLED: Text messages replaced with visual arrows
                // The SkeletonOverlayView now shows:
                // - Green arrows for correct movement direction
                // - Red arrows for error direction
                // - Red colored connections for error joints
            }
            
            // ==================== Hold Events ====================
            
            is FeedbackEvent.HoldStarted -> {
                Log.d(TAG, "Hold started!")
            }
            
            is FeedbackEvent.HoldGraceStarted -> {
                // Grace period warning - visual feedback handled by observeHoldState
                Log.d(TAG, "Grace period started: ${event.gracePeriodMs}ms")
            }
            
            is FeedbackEvent.HoldResumed -> {
                Log.d(TAG, "Hold resumed from grace period")
            }
            
            is FeedbackEvent.HoldCompleted -> {
                Log.d(TAG, "Hold completed! Total: ${event.totalMs}ms, Quality: ${event.formQuality}")
            }
            
            is FeedbackEvent.HoldFailed -> {
                Log.d(TAG, "Hold failed at ${event.elapsedBeforeFailMs}ms")
                // Reset UI for retry
                binding.tvRepCount.text = "00:00"
                binding.tvRepCount.setTextColor(getColor(android.R.color.white))
            }
            
            else -> {}
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
            
            // Smooth landmarks
            val smoothedLandmarks = landmarkSmoother.smooth(
                result.landmarks,
                result.timestampMs
            )
            
            // Get world landmarks for 3D angle calculation
            val worldLandmarks = result.worldLandmarks?.let {
                landmarkSmoother.convertWorld(it)
            }
            
            // Calculate angles
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
            
            // Handle based on current state
            when (trainingState) {
                TrainingState.SETUP_POSE -> {
                    // Validate pose
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
                    // Check pose is still valid during countdown
                    val isValid = validateStartPose()
                    if (!isValid) {
                        countdownTimer?.cancel()
                        poseValidFrames = 0
                        updateUIForState(TrainingState.SETUP_POSE)
                    }
                }
                
                TrainingState.TRAINING -> {
                    // Process frame through training engine
                    trainingEngine?.processFrame(angles)
                }
                
                else -> {}
            }
            
            // Get arrow infos for visual feedback
            val arrowInfos = trainingEngine?.arrowInfos?.value ?: emptyMap()
            
            // Update skeleton overlay with arrow infos
            binding.skeletonOverlay.updateWithArrowInfos(
                smoothedLandmarks = smoothedLandmarks,
                inputImageWidth = result.imageWidth,
                inputImageHeight = result.imageHeight,
                angles = angles,
                arrowInfos = arrowInfos
            )
        }
    }

    override fun onNoPoseDetected() {
        lifecycleScope.launch(Dispatchers.Main) {
            updateFps()
            binding.skeletonOverlay.clear()
            
            // Reset pose validation
            if (trainingState == TrainingState.SETUP_POSE) {
                poseValidFrames = 0
                binding.tvPoseStatus.text = "❌ No pose detected\nMake sure your full body is visible"
            }
            
            // Cancel countdown if pose lost
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

    // ==================== Lifecycle ====================

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
        cameraManager?.stopCamera()
        poseLandmarkerHelper?.close()
        feedbackManager?.release()
    }
}
