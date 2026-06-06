package com.trainingvalidator.poc.ui.train

import android.Manifest
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.analysis.ElbowAngleEstimator
import com.trainingvalidator.poc.analysis.LandmarkSmoother
import com.trainingvalidator.poc.databinding.ActivityTrainingBinding
import com.trainingvalidator.poc.pose.PoseLandmarkerHelper
import com.trainingvalidator.poc.pose.PoseResult
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.workout.WorkoutRunState
import com.trainingvalidator.poc.ui.components.AnimationUtils
import com.trainingvalidator.poc.ui.training.TrainingUIEvent
import com.trainingvalidator.poc.ui.training.TrainingViewModel
import com.trainingvalidator.poc.ui.training.VideoModeController
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.trainingvalidator.poc.training.engine.HoldState
import com.trainingvalidator.poc.training.engine.Phase
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.report.FrameCaptureManager
import com.trainingvalidator.poc.storage.ReportStorage
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.storage.UserExercisePreferenceStore
import com.trainingvalidator.poc.network.WorkoutSyncService
import com.trainingvalidator.poc.network.ApiConfig
import com.trainingvalidator.poc.training.models.CheckSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * TrainingActivity - Professional Training Screen
 * 
 * Now uses:
 * - WorkoutRunSupervisor via TrainingViewModel for state management (Single Source of Truth)
 * - PoseSetupGuide for rolling-window pose validation
 * - CountdownController for countdown logic
 * - VideoModeController for video mode
 * 
 * This Activity is primarily a thin host for:
 * - [TrainingLaunchCoordinator], [TrainingPreferenceDialogs], [TrainingWorkoutModeController],
 *   [CameraTrainingInputController], [VideoTrainingInputController],
 *   [TrainingFeedbackBinder], [SetupCountdownBinder], [TrainingFrameCaptureController],
 *   [TrainingReportCoordinator]
 * - Android lifecycle, [ActivityTrainingBinding], and delegating
 *   [PoseLandmarkerHelper.PoseDetectionListener] to [CameraTrainingInputController] in camera mode
 */
class TrainingActivity : AppCompatActivity(), PoseLandmarkerHelper.PoseDetectionListener,
    com.trainingvalidator.poc.training.workout.WorkoutTrainingEngine.OnExerciseCompletedListener {

    companion object {
        const val TAG = "TrainingActivity"
        
        // Intent extras
        const val EXTRA_EXERCISE_NAME = "exercise_name"
        const val EXTRA_DIFFICULTY = "difficulty"
        const val EXTRA_POSE_VARIANT = "pose_variant"
        const val EXTRA_TRAINING_MODE = "training_mode"
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_TARGET_REPS_OVERRIDE = "target_reps_override"
        const val EXTRA_TARGET_DURATION_OVERRIDE = "target_duration_override"
        const val EXTRA_INDICATOR_TYPE = "indicator_type"
        const val EXTRA_WEIGHT_KG = "weight_kg"           // Weight in kilograms (optional)
        const val EXTRA_WEIGHT_UNIT = "weight_unit"       // "kg" or "lbs" (default: kg)

        // Workout mode extras
        const val EXTRA_IS_WORKOUT_MODE = "is_workout_mode"
        const val EXTRA_WORKOUT_ITEMS_JSON = "workout_items_json"
        /** When set, used for session-level progress rules (warmup/cooldown vs main). */
        const val EXTRA_WORKOUT_ROLE = "workout_role"

        // Assessment mode: skip report page, return report ID via result
        const val EXTRA_ASSESSMENT_MODE = "assessment_mode"
        
        // Result extras
        const val RESULT_REPORT_ID = "report_id"
        const val RESULT_REPS_COMPLETED = "reps_completed"
        const val RESULT_DURATION_MS = "duration_ms"
        const val RESULT_ACCURACY = "accuracy"
        const val RESULT_IS_COMPLETED = "is_completed"
        const val RESULT_WORKOUT_SETS_COMPLETED = "workout_sets_completed"
        const val RESULT_WORKOUT_SETS_PLANNED = "workout_sets_planned"
        const val RESULT_WORKOUT_TOTAL_REPS = "workout_total_reps"
        const val RESULT_WORKOUT_AVG_ACCURACY = "workout_avg_accuracy"
        const val RESULT_WORKOUT_AVG_FORM_SCORE = "workout_avg_form_score"
        const val RESULT_WORKOUT_REPORT_JSON = "workout_report_json"
        const val RESULT_WORKOUT_REPORT_IDS = "workout_report_ids"
        const val RESULT_WORKOUT_EXECUTION_IDS = "workout_execution_ids"
        
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

    // View Binding (host packages access launch/coordinators in this module)
    internal lateinit var binding: ActivityTrainingBinding

    // ViewModel
    internal val viewModel: TrainingViewModel by viewModels {
        TrainingViewModel.Factory(assets)
    }

    private val launchCoordinator = TrainingLaunchCoordinator(this)
    
    // Camera / video input (ML Kit + camera, or [VideoModeController] — see controllers)
    internal lateinit var cameraInput: CameraTrainingInputController
    internal lateinit var videoInput: VideoTrainingInputController

    // Video (owned by [VideoTrainingInputController] when in video mode)
    internal var videoModeController: VideoModeController? = null

    internal lateinit var landmarkSmoother: LandmarkSmoother
    internal val elbowAngleEstimator = ElbowAngleEstimator()
    
    // Scene-to-visibility transition: [SetupCountdownBinder]

    // State
    internal var useFrontCamera = true
    internal var isVideoMode = false
    internal var currentIndicatorType: String = "line"
    internal var currentModelType: String = "full"
    internal var videoUri: Uri? = null
    private var lastRepCount = 0

    // Assessment mode (suppresses report page, returns report ID)
    internal var isAssessmentMode = false

    // Workout run mode (see [TrainingWorkoutModeController])
    internal var isWorkoutMode = false
    internal lateinit var preferenceDialogs: TrainingPreferenceDialogs
    internal lateinit var workoutModeController: TrainingWorkoutModeController

    // Alternating variant info removed - bilateral side management is now handled by TrainingEngine

    // Tracks pose presence transitions to avoid leaving stale form feedback visible when pose is lost.
    // This is intentionally Activity-local (UI concern) and does not affect session state machine behavior.
    /**
     * Pose present this frame; used to clear form UI once on pose→no-pose transition (camera and video).
     */
    @Volatile
    internal var wasPoseDetectedLastFrame: Boolean = false

    // Prevents spamming TTS for NoPose/AutoPause (speak once, not every frame) — used by [TrainingFeedbackBinder]
    internal var noPoseTtsSpoken: Boolean = false

    internal lateinit var feedbackBinder: TrainingFeedbackBinder
    private lateinit var setupCountdownBinder: SetupCountdownBinder
    
    // Report & Frame Capture (init / capture in [TrainingFrameCaptureController])
    internal var frameCaptureManager: FrameCaptureManager? = null
    internal var reportStorage: ReportStorage? = null

    internal lateinit var frameCaptureController: TrainingFrameCaptureController
    internal lateinit var reportCoordinator: TrainingReportCoordinator

    // FPS calculation
    
    // Elapsed time tracking
    private var trainingStartTime: Long = 0L
    private var elapsedTimeJob: kotlinx.coroutines.Job? = null

    // Permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraInput.onPermissionGranted()
        } else {
            cameraInput.onPermissionDeniedShowToastAndFinish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        initializeSettings()
        setupFullscreen()
        
        binding = ActivityTrainingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        feedbackBinder = TrainingFeedbackBinder(this)
        setupCountdownBinder = SetupCountdownBinder(this)
        preferenceDialogs = TrainingPreferenceDialogs(this)
        workoutModeController = TrainingWorkoutModeController(this, preferenceDialogs)
        cameraInput = CameraTrainingInputController(this)
        videoInput = VideoTrainingInputController(this)
        frameCaptureController = TrainingFrameCaptureController(this)
        reportCoordinator = TrainingReportCoordinator(this)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Handle back press with modern OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isWorkoutMode && workoutModeController.workoutEngine != null) {
                    val currentState = workoutModeController.workoutEngine?.state?.value
                    if (currentState !is com.trainingvalidator.poc.training.workout.WorkoutTrainingEngine.State.WorkoutComplete) {
                        workoutModeController.showExitWorkoutDialog()
                        return
                    }
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
        
        parseIntentExtras()
        setupUI()
        setupCountdownBinder.installCountdownListener()
        frameCaptureController.initializeReportSystem()
        observeViewModel()
        
        // Sync pending sessions before starting new training
        syncPendingWorkoutExecutionsOnTrainingStart()
        
        // Initialize based on mode
        if (isVideoMode) {
            videoInput.setupVideoMode()
        } else {
            checkCameraPermission()
        }
    }
    
    /**
     * Sync pending sessions when starting a new training
     */
    private fun syncPendingWorkoutExecutionsOnTrainingStart() {
        val token = AuthManager.getAccessToken(this) ?: run {
            Log.d(TAG, "No auth token, skipping pending sync")
            return
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val syncService = WorkoutSyncService.getInstance(this@TrainingActivity, ApiConfig.getEffectiveBaseUrl())
                syncService.setAuthToken(token)
                val result = syncService.syncPending()
                if (result.total > 0) {
                    Log.d(TAG, "Synced ${result.successCount}/${result.total} pending sessions before training")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pending sync error: ${e.message}")
            }
        }
    }
    
    // ==================== Initialization ====================
    
    private fun initializeSettings() {
        try {
            SettingsManager.initialize(this)
            currentModelType = SettingsManager.getModelType()
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
        if (!launchCoordinator.applyEarlyIntentOrFinish()) return
        launchCoordinator.runAfterRepositoryReady { parseIntentExtrasAfterRepositoryReady() }
    }

    private fun parseIntentExtrasAfterRepositoryReady() {
        if (isWorkoutMode) {
            workoutModeController.initializeFromIntent()
            viewModel.initializeFeedback(this, isVideoMode)
            feedbackBinder.rebindVisualMessageFlow()
            return
        }
        
        // ── SINGLE EXERCISE MODE ──
        val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: DEFAULT_EXERCISE
        val difficultyStr = intent.getStringExtra(EXTRA_DIFFICULTY) ?: DEFAULT_DIFFICULTY
        val poseVariantIndex = intent.getIntExtra(EXTRA_POSE_VARIANT, 0)
        
        val prefStore = UserExercisePreferenceStore(this)
        val storedPref = prefStore.get(exerciseName)

        // Target overrides — intent wins, then saved user preference
        var targetRepsOverride = intent.getIntExtra(EXTRA_TARGET_REPS_OVERRIDE, -1).takeIf { it > 0 }
        if (targetRepsOverride == null) {
            targetRepsOverride = storedPref?.customReps?.takeIf { it > 0 }
        }

        var targetDurationOverride = intent.getIntExtra(EXTRA_TARGET_DURATION_OVERRIDE, -1)
            .takeIf { it > 0 }?.let { it * 1000L }
        if (targetDurationOverride == null) {
            targetDurationOverride = storedPref?.customDurationSec?.takeIf { it > 0 }?.let { it * 1000L }
        }

        val weightKg = when {
            intent.hasExtra(EXTRA_WEIGHT_KG) -> intent.getFloatExtra(EXTRA_WEIGHT_KG, 0f)
            storedPref?.customWeightKg != null -> storedPref.customWeightKg
            else -> null
        }
        val weightUnit = intent.getStringExtra(EXTRA_WEIGHT_UNIT) ?: "kg"
        
        viewModel.supervisor.isVideoMode = isVideoMode

        if (!viewModel.loadExercise(
                exerciseName,
                difficultyStr,
                poseVariantIndex,
                targetRepsOverride,
                targetDurationOverride,
                context = this,
                weightKg = weightKg,
                weightUnit = weightUnit
            )) {
            Toast.makeText(
                this,
                getString(R.string.failed_to_load_exercise_format, exerciseName),
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }
        
        if (!isAssessmentMode) {
            preferenceDialogs.maybeShowPreTrainingDialog()
        }

        // Initialize feedback
        viewModel.initializeFeedback(this, isVideoMode)
        feedbackBinder.rebindVisualMessageFlow()
    }

    private fun setupUI() {
        // Exercise name
        binding.tvExerciseName.text = viewModel.exerciseName.value
        
        // Close button
        binding.btnClose.setOnClickListener { 
            workoutModeController.cancelWorkoutRestOnClose()
            viewModel.requestStop()
            finish() 
        }
        
        // Settings button
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }
        
        // Play/Pause button
        binding.btnPlayPause.setOnClickListener {
            handlePlayPauseClick()
        }
        
        // Initialize elapsed time display
        binding.tvTimeElapsed.text = "00:00"
        
        // Initialize form status
        binding.tvFormStatus.text = getString(R.string.good)
        binding.tvFormStatus.setTextColor(ContextCompat.getColor(this, R.color.primary))
        
        // Initial state (camera mode only - video mode sets its own UI in setupVideoMode)
        if (!isVideoMode) {
            updateUIForWorkoutRunState(WorkoutRunState.SETUP_POSE)
            setupCountdownBinder.showSetupPoseUI()
        }
    }


    /**
     * Show training settings dialog
     */
    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_training_settings, null)
        val dialog = android.app.AlertDialog.Builder(this, R.style.Theme_WayToFix_Dialog)
            .setView(dialogView)
            .create()
        
        // Read from actual runtime state, not from SharedPreferences,
        // so the dialog always reflects what is currently active.
        var selectedIndicator = currentIndicatorType
        var voiceFeedbackEnabled = SettingsManager.isVoiceFeedbackEnabled()
        var selectedModel = currentModelType
        var selectedCoachIntensity = SettingsManager.getCoachIntensity()

        fun setChoiceButtonSelected(button: MaterialButton, isSelected: Boolean) {
            if (isSelected) {
                button.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
                button.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
            } else {
                button.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                button.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }
        }
        
        // Setup indicator buttons
        val btnLine = dialogView.findViewById<MaterialButton>(R.id.btnIndicatorLine)
        val btnArc = dialogView.findViewById<MaterialButton>(R.id.btnIndicatorArc)
        
        fun updateIndicatorButtons() {
            setChoiceButtonSelected(btnLine, selectedIndicator == "line")
            setChoiceButtonSelected(btnArc, selectedIndicator != "line")
        }
        updateIndicatorButtons()
        
        btnLine.setOnClickListener {
            selectedIndicator = "line"
            updateIndicatorButtons()
        }
        btnArc.setOnClickListener {
            selectedIndicator = "arc"
            updateIndicatorButtons()
        }
        
        // Setup voice feedback switch
        val switchVoice = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchVoiceFeedback)
        switchVoice.isChecked = voiceFeedbackEnabled
        switchVoice.setOnCheckedChangeListener { _, isChecked ->
            voiceFeedbackEnabled = isChecked
        }

        // Setup coach intensity buttons
        val btnCoachCalm = dialogView.findViewById<MaterialButton>(R.id.btnCoachCalm)
        val btnCoachStandard = dialogView.findViewById<MaterialButton>(R.id.btnCoachStandard)
        val btnCoachStrict = dialogView.findViewById<MaterialButton>(R.id.btnCoachStrict)

        fun updateCoachButtons() {
            setChoiceButtonSelected(btnCoachCalm, selectedCoachIntensity == "calm")
            setChoiceButtonSelected(btnCoachStandard, selectedCoachIntensity == "standard")
            setChoiceButtonSelected(btnCoachStrict, selectedCoachIntensity == "strict")
        }
        updateCoachButtons()

        btnCoachCalm.setOnClickListener {
            selectedCoachIntensity = "calm"
            updateCoachButtons()
        }
        btnCoachStandard.setOnClickListener {
            selectedCoachIntensity = "standard"
            updateCoachButtons()
        }
        btnCoachStrict.setOnClickListener {
            selectedCoachIntensity = "strict"
            updateCoachButtons()
        }

        // Setup model buttons
        val btnModelFull = dialogView.findViewById<MaterialButton>(R.id.btnModelFull)
        val btnModelHeavy = dialogView.findViewById<MaterialButton>(R.id.btnModelHeavy)
        
        fun updateModelButtons() {
            setChoiceButtonSelected(btnModelFull, selectedModel == "full")
            setChoiceButtonSelected(btnModelHeavy, selectedModel != "full")
        }
        updateModelButtons()
        
        btnModelFull.setOnClickListener {
            selectedModel = "full"
            updateModelButtons()
        }
        btnModelHeavy.setOnClickListener {
            selectedModel = "heavy"
            updateModelButtons()
        }
        
        // Close button
        dialogView.findViewById<android.widget.ImageButton>(R.id.btnCloseSettings).setOnClickListener {
            dialog.dismiss()
        }
        
        // Camera section (only visible in camera mode)
        val cameraSectionContainer = dialogView.findViewById<android.widget.LinearLayout>(R.id.cameraSectionContainer)
        val dividerCamera = dialogView.findViewById<View>(R.id.dividerCamera)
        val tvCurrentCamera = dialogView.findViewById<android.widget.TextView>(R.id.tvCurrentCamera)
        val btnSwitchCameraDialog = dialogView.findViewById<MaterialButton>(R.id.btnSwitchCameraDialog)
        val feedbackChannelInfoViews = listOf(
            dialogView.findViewById<View>(R.id.dividerCameraCue),
            dialogView.findViewById<View>(R.id.cameraCueSectionTitle),
            dialogView.findViewById<View>(R.id.tvCameraCueDesc)
        )
        dialogView.findViewById<View>(R.id.cameraCueSection).visibility = View.GONE
        feedbackChannelInfoViews.forEach { it.visibility = View.VISIBLE }
        
        if (isVideoMode) {
            // Hide camera section in video mode
            cameraSectionContainer.visibility = View.GONE
            dividerCamera.visibility = View.GONE
        } else {
            // Show camera section and update current camera text
            cameraSectionContainer.visibility = View.VISIBLE
            dividerCamera.visibility = View.VISIBLE
            tvCurrentCamera.text = if (useFrontCamera) getString(R.string.front_camera) else getString(R.string.back_camera)
            
            btnSwitchCameraDialog.setOnClickListener {
                cameraInput.applySwitchCameraFromSettings()
                tvCurrentCamera.text = if (useFrontCamera) getString(R.string.front_camera) else getString(R.string.back_camera)
            }
        }
        
        // Apply button
        dialogView.findViewById<MaterialButton>(R.id.btnApplySettings).setOnClickListener {
            val modelChanged = selectedModel != currentModelType

            // Sync local state
            currentIndicatorType = selectedIndicator
            currentModelType = selectedModel

            // Persist to SharedPreferences
            SettingsManager.setIndicatorType(selectedIndicator)
            SettingsManager.setVoiceFeedbackEnabled(voiceFeedbackEnabled)
            SettingsManager.setCoachIntensity(selectedCoachIntensity)
            SettingsManager.setCameraCueMode("voice")
            SettingsManager.setModelType(selectedModel)
            
            // Apply indicator change immediately
            binding.skeletonOverlay.setIndicatorType(selectedIndicator)
            
            // Update feedback manager
            viewModel.feedbackManager?.setVoiceEnabled(voiceFeedbackEnabled)

            // Reinitialize pose detector when model type changes
            if (modelChanged) {
                cameraInput.reinitializePoseDetector()
            }
            
            dialog.dismiss()
            
            Toast.makeText(this, getString(R.string.settings) + " " + getString(R.string.save), Toast.LENGTH_SHORT).show()
        }
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
    
    /**
     * Start elapsed time timer
     */
    internal fun startElapsedTimeTimer() {
        trainingStartTime = System.currentTimeMillis()
        elapsedTimeJob?.cancel()
        elapsedTimeJob = lifecycleScope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - trainingStartTime
                binding.tvTimeElapsed.text = formatElapsedTime(elapsed)
                kotlinx.coroutines.delay(1000)
            }
        }
    }
    
    /**
     * Stop elapsed time timer
     */
    internal fun stopElapsedTimeTimer() {
        elapsedTimeJob?.cancel()
        elapsedTimeJob = null
        trainingStartTime = 0L
    }
    
    /**
     * Format elapsed time to MM:SS
     */
    private fun formatElapsedTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    /**
     * Update form status based on current state
     */
    private fun updateFormStatus(state: JointState) {
        val (text, color) = when (state) {
            JointState.PERFECT -> getString(R.string.excellent) to R.color.primary
            JointState.NORMAL -> getString(R.string.good) to R.color.success
            JointState.PAD -> "OK" to R.color.warning
            JointState.WARNING -> getString(R.string.needs_work) to R.color.warning
            JointState.DANGER -> getString(R.string.error) to R.color.error
            JointState.TRANSITION -> getString(R.string.good) to R.color.text_secondary
        }
        binding.tvFormStatus.text = text
        binding.tvFormStatus.setTextColor(ContextCompat.getColor(this, color))
    }
    
    // ==================== ViewModel Observers ====================
    
    private fun observeViewModel() {
        // Observe supervisor state (Single Source of Truth)
        lifecycleScope.launch {
            viewModel.supervisor.state.collectLatest { state ->
                updateUIForWorkoutRunState(state)
            }
        }
        
        // Observe exercise name
        lifecycleScope.launch {
            viewModel.exerciseName.collectLatest { name ->
                AnimationUtils.crossfadeText(binding.tvExerciseName, name)
                updateCounterLabelForCurrentExercise()
            }
        }
        
        // Observe rep count
        lifecycleScope.launch {
            viewModel.repCount.collectLatest { count ->
                if (viewModel.isHoldExercise()) return@collectLatest
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

                // Capture peak frame when entering BOTTOM phase (de-dupe + repNumber in [TrainingFrameCaptureController])
                frameCaptureController.onCurrentPhaseForPeakCapture(phase)
            }
        }
        
        // Observe hold (single flow: [HoldStatus] from engine)
        lifecycleScope.launch {
            viewModel.holdStatus.collectLatest { s ->
                if (!viewModel.isHoldExercise()) return@collectLatest
                s ?: return@collectLatest
                binding.tvRepCount.text = formatTimeMs(s.elapsedMs)
                binding.tvProgress.text =
                    "${formatTimeMs(s.elapsedMs)} / ${formatTimeMs(viewModel.getTargetDurationMs())}"
                updateUIForHoldState(s.state)
            }
        }
        feedbackBinder.registerPipelineTraceShortcut()
        feedbackBinder.startFeedbackObservers()
        // Visual glassmorphic: [TrainingFeedbackBinder.rebindVisualMessageFlow] after [TrainingViewModel.initializeFeedback]
        feedbackBinder.rebindVisualMessageFlow()
        
        // Observe UI events
        lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                handleUIEvent(event)
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
    
    internal fun observeTrainingEngineState() {
        // Cancel previous observer to avoid duplicates
        stateInfosObserverJob?.cancel()
        
        val engine = viewModel.trainingEngine ?: run {
            Log.e(TAG, "observeTrainingEngineState: trainingEngine is null!")
            return
        }
        
        stateInfosObserverJob = lifecycleScope.launch {
            launch {
                engine.jointStateInfos.collectLatest { stateInfos ->
                    binding.skeletonOverlay.setStateInfos(stateInfos)

                    val hasErrors = stateInfos.any {
                        it.value.state == JointState.DANGER || it.value.state == JointState.WARNING
                    }
                    if (hasErrors) {
                        binding.vignetteOverlay.showError()
                    } else if (!engine.isVisibilityPaused.value) {
                        binding.vignetteOverlay.clear()
                    }

                    val worstState = stateInfos.values.maxByOrNull { info ->
                        when (info.state) {
                            JointState.DANGER -> 5
                            JointState.WARNING -> 4
                            JointState.PAD -> 3
                            JointState.NORMAL -> 2
                            JointState.PERFECT -> 1
                            JointState.TRANSITION -> 0
                        }
                    }?.state ?: JointState.PERFECT
                    updateFormStatus(worstState)

                    val hasBlockingPositionIssue = engine.positionErrors.value.any {
                        it.severity == CheckSeverity.ERROR || it.severity == CheckSeverity.WARNING
                    }
                    viewModel.feedbackManager?.checkAndDeliverRandomMessage(
                        hasActiveErrors = hasErrors || hasBlockingPositionIssue
                    )
                }
            }
            feedbackBinder.collectEngineVisibilityInScope(this, engine)
        }
    }
    
    @Suppress("DEPRECATION")
    private fun handleUIEvent(event: TrainingUIEvent) {
        when (event) {
            is TrainingUIEvent.ShowSetupPose -> {
                setupCountdownBinder.showSetupPoseUI()
            }

            is TrainingUIEvent.SetupGuidanceUpdate -> {
                setupCountdownBinder.updateSetupGuidanceUI(event.result)
            }

            is TrainingUIEvent.StartCountdown -> {
                binding.setupPosePanel.visibility = View.GONE
                binding.setupIndicatorBar.visibility = View.GONE
                binding.countdownPanel.visibility = View.VISIBLE
                binding.tvCountdown.visibility = View.VISIBLE
                binding.tvCountdown.alpha = 1f
                binding.tvCountdown.setTextColor(
                    ContextCompat.getColor(this, R.color.text_primary)
                )
                setupCountdownBinder.switchBottomBarToFormMode()
                binding.skeletonOverlay.clearSetupMode()
            }

            is TrainingUIEvent.CountdownCancelled -> {
                updateUIForWorkoutRunState(WorkoutRunState.SETUP_POSE)
                setupCountdownBinder.showSetupPoseUI()
            }

            is TrainingUIEvent.CountdownFrozen -> {
                // Handled directly by CountdownController listener callbacks above
            }

            is TrainingUIEvent.CountdownPoseIssue -> {
                feedbackBinder.showCountdownPoseIssue(event.result)
            }

            is TrainingUIEvent.CountdownUnfrozen -> {
                // Handled directly by CountdownController listener callbacks above
            }

            is TrainingUIEvent.TrainingStarted -> {
                binding.skeletonOverlay.clearSetupMode()
                val trackedIndices = viewModel.getTrackedLandmarkIndices()
                binding.skeletonOverlay.setTrainingMode(true, trackedIndices, useFrontCamera)
                observeTrainingEngineState()
            }
            
            is TrainingUIEvent.ExerciseCompleted,
            is TrainingUIEvent.TrainingCompleted -> {
                if (isWorkoutMode) {
                    workoutModeController.tryHandleWorkoutSetCompleted()
                } else {
                    completeTraining()
                }
            }
            
            is TrainingUIEvent.AutoPaused -> {
                feedbackBinder.handleAutoPaused(event.reason)
            }
            
            is TrainingUIEvent.NoPoseWarning -> {
                feedbackBinder.handleNoPoseWarning(event.elapsedMs)
            }
            
            is TrainingUIEvent.PauseVideoPlayback -> {
                videoModeController?.pause()
                updatePlayPauseIcon(isPlaying = false)
            }
            
            is TrainingUIEvent.ResumeVideoPlayback -> {
                videoModeController?.play()
                updatePlayPauseIcon(isPlaying = true)
            }
        }
    }

    internal fun updateCounterLabelForCurrentExercise() {
        val labelRes = if (viewModel.isHoldExercise()) R.string.time else R.string.reps
        binding.tvRepsLabel.text = getString(labelRes)
    }
    
    // ==================== UI Updates ====================
    
    /**
     * Update UI based on WorkoutRunState (from WorkoutRunSupervisor)
     */
    private fun updateUIForWorkoutRunState(state: WorkoutRunState) {
        if (state == WorkoutRunState.TRAINING && !viewModel.isHoldExercise()) {
            frameCaptureController.startRepWideReplaySampler()
        } else {
            frameCaptureController.stopRepWideReplaySampler()
        }
        when (state) {
            WorkoutRunState.IDLE -> {
                // Initial state - waiting for exercise to load
            }
            
            WorkoutRunState.SETUP_POSE -> {
                binding.setupPosePanel.visibility = View.GONE
                binding.setupIndicatorBar.visibility = View.VISIBLE
                binding.bottomStatsBar.visibility = View.GONE
                binding.countdownPanel.visibility = View.GONE
                binding.glassmorphicMessage.clearAll()
                binding.heroCounterContainer.visibility = View.GONE
                binding.tvProgress.visibility = View.GONE
                binding.completedPanel.visibility = View.GONE
                binding.progressContainer.visibility = View.GONE
                updatePlayPauseIcon(isPlaying = false)
            }
            
            WorkoutRunState.COUNTDOWN -> {
                if (::landmarkSmoother.isInitialized) {
                    landmarkSmoother.reset()
                }
                elbowAngleEstimator.reset()

                binding.skeletonOverlay.clearSetupMode()
                setupCountdownBinder.switchBottomBarToFormMode()

                // Hide indicator bar, show countdown
                binding.setupIndicatorBar.visibility = View.GONE
                binding.setupPosePanel.visibility = View.GONE
                binding.countdownPanel.visibility = View.VISIBLE
                binding.glassmorphicMessage.clearAll()
                binding.heroCounterContainer.visibility = View.GONE
                binding.tvProgress.visibility = View.GONE
                binding.completedPanel.visibility = View.GONE
            }
            
            WorkoutRunState.TRAINING -> {
                binding.setupPosePanel.visibility = View.GONE
                binding.setupIndicatorBar.visibility = View.GONE
                binding.countdownPanel.visibility = View.GONE
                AnimationUtils.bounceIn(binding.heroCounterContainer)
                binding.heroCounterContainer.visibility = View.VISIBLE
                binding.tvProgress.visibility = View.VISIBLE
                binding.completedPanel.visibility = View.GONE
                binding.progressContainer.visibility = View.VISIBLE
                binding.bottomStatsBar.visibility = View.VISIBLE
                updatePlayPauseIcon(isPlaying = true)

                binding.skeletonOverlay.clearSetupMode()
                setupCountdownBinder.switchBottomBarToFormMode()

                // Start elapsed time timer
                if (trainingStartTime == 0L) {
                    startElapsedTimeTimer()
                }
            }
            
            WorkoutRunState.PAUSED -> {
                updatePlayPauseIcon(isPlaying = false)
            }
            
            WorkoutRunState.AUTO_PAUSED -> {
                binding.heroCounterContainer.visibility = View.VISIBLE
                binding.tvProgress.visibility = View.VISIBLE
                binding.progressContainer.visibility = View.VISIBLE
                updatePlayPauseIcon(isPlaying = false)
            }
            
            WorkoutRunState.RESUME_SETUP -> {
                binding.setupPosePanel.visibility = View.GONE
                binding.setupIndicatorBar.visibility = View.VISIBLE
                binding.bottomStatsBar.visibility = View.GONE
                binding.countdownPanel.visibility = View.GONE
                binding.glassmorphicMessage.clearAll()
                binding.heroCounterContainer.visibility = View.VISIBLE
                binding.tvProgress.visibility = View.VISIBLE
                binding.progressContainer.visibility = View.VISIBLE
                binding.completedPanel.visibility = View.GONE
                updatePlayPauseIcon(isPlaying = false)
            }
            
            WorkoutRunState.RESUME_COUNTDOWN -> {
                binding.skeletonOverlay.clearSetupMode()
                setupCountdownBinder.switchBottomBarToFormMode()
                binding.setupPosePanel.visibility = View.GONE
                binding.setupIndicatorBar.visibility = View.GONE
                binding.countdownPanel.visibility = View.VISIBLE
                binding.glassmorphicMessage.clearAll()
                binding.heroCounterContainer.visibility = View.VISIBLE
                binding.tvProgress.visibility = View.VISIBLE
                updatePlayPauseIcon(isPlaying = false)
            }
            
            WorkoutRunState.COMPLETED -> {
                AnimationUtils.slideOutPanel(binding.heroCounterContainer, AnimationUtils.Direction.TOP)
                binding.setupPosePanel.visibility = View.GONE
                binding.setupIndicatorBar.visibility = View.GONE
                binding.countdownPanel.visibility = View.GONE
                binding.tvProgress.visibility = View.GONE
                binding.bottomStatsBar.visibility = View.GONE
                binding.completedPanel.visibility = View.VISIBLE
                
                // Stop elapsed time timer
                stopElapsedTimeTimer()
                binding.progressContainer.visibility = View.GONE
                updatePlayPauseIcon(isPlaying = false)
                binding.vignetteOverlay.clear()
            }
        }
    }
    
    internal fun isTextlessSetupState(): Boolean = setupCountdownBinder.isTextlessSetupState()
    
    internal fun updatePlayPauseIcon(isPlaying: Boolean) {
        // stopIcon is now a View with background, not an ImageView
        // Toggle visibility or background based on state
        if (isPlaying) {
            binding.stopIcon.setBackgroundResource(R.drawable.bg_stop_button)
        } else {
            binding.stopIcon.setBackgroundResource(R.drawable.ic_play)
        }
    }
    
    private fun handlePlayPauseClick() {
        if (isVideoMode) {
            val currentState = viewModel.supervisor.state.value
            val isPlaying = videoModeController?.isPlaying() ?: false
            
            if (isPlaying) {
                videoModeController?.pause()
                updatePlayPauseIcon(isPlaying = false)
                if (currentState == WorkoutRunState.TRAINING) {
                    viewModel.requestPause()
                }
            } else {
                videoModeController?.play()
                updatePlayPauseIcon(isPlaying = true)
                when (currentState) {
                    WorkoutRunState.PAUSED, WorkoutRunState.AUTO_PAUSED -> viewModel.requestResume()
                    else -> {}
                }
            }
        } else {
            val currentState = viewModel.supervisor.state.value
            when (currentState) {
                WorkoutRunState.TRAINING -> viewModel.requestPause()
                WorkoutRunState.PAUSED, WorkoutRunState.AUTO_PAUSED -> viewModel.requestResume()
                else -> {}
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
    
    // ==================== Completion ====================
    
    private var hasCompletedTraining = false  // Prevent duplicate calls
    
    private fun completeTraining() {
        // Prevent duplicate completion (ExerciseCompleted + TrainingCompleted events)
        if (hasCompletedTraining) {
            Log.d(TAG, "completeTraining already called, skipping duplicate")
            return
        }
        hasCompletedTraining = true
        
        binding.skeletonOverlay.setTrainingMode(false)
        binding.vignetteOverlay.clear()
        
        // Hide the completed panel - we'll navigate to WorkoutReportActivity instead
        binding.completedPanel.visibility = View.GONE
        
        // Log capture stats
        val captureCount = frameCaptureManager?.getCaptureCount() ?: 0
        val storageSize = frameCaptureManager?.getFormattedStorageSize() ?: "0 B"
        Log.d(TAG, "Training completed. Captured $captureCount frames ($storageSize)")
        
        // Generate report and navigate directly to ReportActivity
        generateReportAndNavigate()
    }
    
    internal fun finishWithResult() {
        val resultIntent = android.content.Intent().apply {
            val engine = viewModel.trainingEngine
            putExtra(RESULT_REPS_COMPLETED, engine?.getCurrentRep() ?: 0)
            putExtra(RESULT_DURATION_MS, viewModel.getWorkoutDurationMs())
            putExtra(RESULT_ACCURACY, engine?.getAccuracy() ?: 0f)
            putExtra(RESULT_IS_COMPLETED, engine?.isCompleted?.value ?: false)
        }
        
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun generateReportAndNavigate() = reportCoordinator.generateReportAndNavigate()
    
    // Camera: ML Kit wiring lives in [CameraTrainingInputController]; this activity implements
    // [PoseLandmarkerHelper.PoseDetectionListener] and delegates here.
    private fun checkCameraPermission() {
        if (cameraInput.hasCameraPermission()) {
            cameraInput.onPermissionGranted()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onPoseDetected(result: PoseResult) = cameraInput.onPoseDetected(result)

    override fun onNoPoseDetected() = cameraInput.onNoPoseDetected()

    override fun onError(message: String) = cameraInput.onPoseError(message)

    override fun onExerciseCompleted(
        exerciseIndex: Int,
        exerciseSlug: String,
        sets: List<com.trainingvalidator.poc.training.workout.WorkoutTrainingEngine.SetMetrics>
    ) {
        workoutModeController.onExerciseCompletedFromEngine(exerciseIndex, exerciseSlug, sets)
    }

    internal fun isLandmarkSmootherReady() = ::landmarkSmoother.isInitialized

    // ==================== Helpers ====================
    
    internal fun formatTimeMs(ms: Long): String {
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
            Phase.COUNT -> if (isHoldExercise) "Holding..." else "Counting"
        }
    }

    // ==================== Lifecycle ====================

    override fun onResume() {
        super.onResume()
        viewModel.handleActivityResume()
    }

    override fun onPause() {
        viewModel.handleActivityPause()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        frameCaptureController.onDestroy()
        workoutModeController.onDestroy()
        viewModel.countdownController.release()
        videoInput.release()
        cameraInput.onDestroy()
    }
}
