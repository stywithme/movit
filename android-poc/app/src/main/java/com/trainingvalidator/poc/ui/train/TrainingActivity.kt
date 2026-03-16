package com.trainingvalidator.poc.ui.train

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.training.analytics.SessionUpload
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.analysis.AngleCalculator
import com.trainingvalidator.poc.analysis.ElbowAngleEstimator
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
import com.trainingvalidator.poc.ui.training.CameraGuidance
import com.trainingvalidator.poc.ui.training.CountdownController
import com.trainingvalidator.poc.ui.training.Direction
import com.trainingvalidator.poc.ui.training.GuidanceLevel
import com.trainingvalidator.poc.ui.training.JointGuidance
import com.trainingvalidator.poc.ui.training.PoseValidator
import com.trainingvalidator.poc.ui.training.SetupPhase
import com.trainingvalidator.poc.ui.training.SetupResult
import com.trainingvalidator.poc.ui.training.TrainingUIEvent
import com.trainingvalidator.poc.ui.training.TrainingViewModel
import com.trainingvalidator.poc.ui.training.VideoModeController
import android.widget.SeekBar
import android.widget.TextView
import com.trainingvalidator.poc.training.engine.HoldState
import com.trainingvalidator.poc.training.engine.Phase
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.report.FrameCaptureManager
import com.trainingvalidator.poc.training.report.ReportGenerator
import com.trainingvalidator.poc.storage.ReportStorage
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.storage.AnalyticsStorage
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.network.SessionSyncService
import com.trainingvalidator.poc.network.ApiConfig
import com.trainingvalidator.poc.ui.report.ReportPagerActivity
import com.trainingvalidator.poc.ui.utils.currentLanguage
import com.trainingvalidator.poc.video.VideoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

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
class TrainingActivity : AppCompatActivity(), PoseLandmarkerHelper.PoseDetectionListener,
    com.trainingvalidator.poc.training.session.SessionTrainingEngine.OnExerciseCompletedListener {

    companion object {
        private const val TAG = "TrainingActivity"
        
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

        // Session mode extras
        const val EXTRA_IS_SESSION_MODE = "is_session_mode"
        const val EXTRA_SESSION_ITEMS_JSON = "session_items_json"

        // Assessment mode: skip report page, return report ID via result
        const val EXTRA_ASSESSMENT_MODE = "assessment_mode"
        
        // Result extras
        const val RESULT_REPORT_ID = "report_id"
        const val RESULT_REPS_COMPLETED = "reps_completed"
        const val RESULT_DURATION_MS = "duration_ms"
        const val RESULT_ACCURACY = "accuracy"
        const val RESULT_IS_COMPLETED = "is_completed"
        const val RESULT_SESSION_SETS_COMPLETED = "session_sets_completed"
        const val RESULT_SESSION_SETS_PLANNED = "session_sets_planned"
        const val RESULT_SESSION_TOTAL_REPS = "session_total_reps"
        const val RESULT_SESSION_AVG_ACCURACY = "session_avg_accuracy"
        const val RESULT_SESSION_AVG_FORM_SCORE = "session_avg_form_score"
        const val RESULT_SESSION_REPORT_JSON = "session_report_json"
        const val RESULT_SESSION_REPORT_IDS = "session_report_ids"
        
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
    private val elbowAngleEstimator = ElbowAngleEstimator()
    
    // Video Mode Controller
    private var videoModeController: VideoModeController? = null
    
    // State
    private var useFrontCamera = true
    private var isVideoMode = false
    private var videoUri: Uri? = null
    private var lastRepCount = 0
    private var currentSessionSetRunId: Long = 0L
    private var lastCompletedSessionSetRunId: Long = -1L
    private var isWeightDialogVisible = false
    private var hasShownWeightDialog = false

    // Assessment mode (suppresses report page, returns report ID)
    private var isAssessmentMode = false

    // Session mode state
    private var isSessionMode = false
    private var sessionTrainingEngine: com.trainingvalidator.poc.training.session.SessionTrainingEngine? = null
    private var sessionRestTimer: android.os.CountDownTimer? = null
    private var sessionRestRemainingMs: Long = 0L
    private var sessionSetStartTimeMs: Long = 0L
    private val sessionExerciseConfigMap = mutableMapOf<String, com.trainingvalidator.poc.training.models.ExerciseConfig>()

    // Alternating variant info removed - bilateral side management is now handled by TrainingEngine

    // Tracks pose presence transitions to avoid leaving stale form feedback visible when pose is lost.
    // This is intentionally Activity-local (UI concern) and does not affect session state machine behavior.
    private var wasPoseDetectedLastFrame: Boolean = false
    
    // Frame processing guard - drops frames if previous is still processing
    // to prevent Main Thread queue buildup and skeleton lag
    @Volatile
    private var isProcessingPoseFrame = false
    
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
    
    // Elapsed time tracking
    private var trainingStartTime: Long = 0L
    private var elapsedTimeJob: kotlinx.coroutines.Job? = null

    // Permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onCameraPermissionGranted()
        } else {
            Toast.makeText(
                this,
                getString(R.string.camera_permission_required),
                Toast.LENGTH_LONG
            ).show()
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

        // Handle back press with modern OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSessionMode && sessionTrainingEngine != null) {
                    val currentState = sessionTrainingEngine?.state?.value
                    if (currentState !is com.trainingvalidator.poc.training.session.SessionTrainingEngine.State.SessionComplete) {
                        showExitSessionDialog()
                        return
                    }
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
        
        parseIntentExtras()
        setupUI()
        setupCountdownController()
        initializeReportSystem()
        observeViewModel()
        
        // Sync pending sessions before starting new training
        syncPendingSessionsOnTrainingStart()
        
        // Initialize based on mode
        if (isVideoMode) {
            setupVideoMode()
        } else {
            checkCameraPermission()
        }
    }

    private fun showExitSessionDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.session_exit_title))
            .setMessage(getString(R.string.session_exit_message))
            .setPositiveButton(getString(R.string.session_exit_keep)) { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.session_exit_exit)) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(true)
            .show()
    }
    
    /**
     * Sync pending sessions when starting a new training
     */
    private fun syncPendingSessionsOnTrainingStart() {
        val token = AuthManager.getAccessToken(this) ?: run {
            Log.d(TAG, "No auth token, skipping pending sync")
            return
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val syncService = SessionSyncService.getInstance(this@TrainingActivity, ApiConfig.getEffectiveBaseUrl())
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
    
    /**
     * Initialize Exercise Repository to ensure cached/synced data is available.
     * This is needed when TrainingActivity is launched directly (e.g., from deep link).
     * 
     * Strategy: Cache First, then Backend Sync if cache is empty.
     * Uses lifecycleScope to avoid blocking the main thread (ANR risk).
     */
    private fun initializeExerciseRepository(onReady: () -> Unit = {}) {
        lifecycleScope.launch {
            try {
                val exerciseRepo = ExerciseRepository.getInstance(this@TrainingActivity)
                val exerciseSuccess = withContext(Dispatchers.IO) {
                    exerciseRepo.initialize(autoSync = true)
                }
                if (exerciseSuccess) {
                    Log.d(TAG, "ExerciseRepository initialized successfully")
                } else {
                    Log.w(TAG, "ExerciseRepository initialized but no exercises available")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize repositories", e)
            } finally {
                onReady()
            }
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
        // Ensure ExerciseRepository is initialized before loading exercise
        initializeExerciseRepository()
        
        // Training mode
        val trainingMode = intent.getStringExtra(EXTRA_TRAINING_MODE) ?: MODE_CAMERA
        isVideoMode = trainingMode == MODE_VIDEO
        videoUri = IntentCompat.getParcelableExtra(intent, EXTRA_VIDEO_URI, Uri::class.java)
        
        if (isVideoMode && videoUri == null) {
            Toast.makeText(this, getString(R.string.no_video_selected), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // Indicator type
        val indicatorType = intent.getStringExtra(EXTRA_INDICATOR_TYPE) 
            ?: com.trainingvalidator.poc.training.config.SettingsManager.getIndicatorType()
        binding.skeletonOverlay.setIndicatorType(indicatorType)

        // Assessment mode flag
        isAssessmentMode = intent.getBooleanExtra(EXTRA_ASSESSMENT_MODE, false)

        // ── SESSION MODE ──
        isSessionMode = intent.getBooleanExtra(EXTRA_IS_SESSION_MODE, false)
        if (isSessionMode) {
            initializeSessionMode()
            viewModel.initializeFeedback(this, isVideoMode)
            return
        }
        
        // ── SINGLE EXERCISE MODE ──
        val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: DEFAULT_EXERCISE
        val difficultyStr = intent.getStringExtra(EXTRA_DIFFICULTY) ?: DEFAULT_DIFFICULTY
        val poseVariantIndex = intent.getIntExtra(EXTRA_POSE_VARIANT, 0)
        
        // Target overrides
        val targetRepsOverride = intent.getIntExtra(EXTRA_TARGET_REPS_OVERRIDE, -1)
            .takeIf { it > 0 }
        val targetDurationOverride = intent.getIntExtra(EXTRA_TARGET_DURATION_OVERRIDE, -1)
            .takeIf { it > 0 }?.let { it * 1000L }

        // Weight overrides (optional)
        val weightKg = if (intent.hasExtra(EXTRA_WEIGHT_KG)) {
            intent.getFloatExtra(EXTRA_WEIGHT_KG, 0f)
        } else {
            null
        }
        val weightUnit = intent.getStringExtra(EXTRA_WEIGHT_UNIT) ?: "kg"
        
        // Load exercise via ViewModel
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
        
        maybeShowWeightDialog()

        // Initialize feedback
        viewModel.initializeFeedback(this, isVideoMode)
    }

    private fun setupUI() {
        // Exercise name
        binding.tvExerciseName.text = viewModel.exerciseName.value
        
        // Close button
        binding.btnClose.setOnClickListener { 
            sessionRestTimer?.cancel()
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
        
        // Initial state
        updateUIForSessionState(SessionState.SETUP_POSE)
        showSetupPoseUI()
    }

    private fun maybeShowWeightDialog() {
        val config = viewModel.exerciseConfig.value ?: return
        if (!config.supportsWeight || hasShownWeightDialog) return

        hasShownWeightDialog = true
        isWeightDialogVisible = true

        val dialogView = layoutInflater.inflate(R.layout.dialog_weight_confirm, null)
        val dialog = android.app.AlertDialog.Builder(this, R.style.Theme_WayToFix_Dialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val inputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.inputWeightLayout)
        val inputField = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.inputWeight)
        val tvRange = dialogView.findViewById<TextView>(R.id.tvWeightRange)
        val btnStart = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWeightStart)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWeightCancel)

        val defaultWeight = viewModel.getWeightKg() ?: config.defaultWeight
        val minWeight = config.minWeight
        val maxWeight = config.maxWeight

        inputField.setText(defaultWeight?.toString().orEmpty())

        val rangeText = when {
            minWeight != null && maxWeight != null ->
                getString(R.string.weight_min_max_format, minWeight, maxWeight)
            minWeight != null ->
                getString(R.string.weight_min_only_format, minWeight)
            maxWeight != null ->
                getString(R.string.weight_max_only_format, maxWeight)
            else -> ""
        }
        tvRange.text = rangeText
        tvRange.visibility = if (rangeText.isNotEmpty()) View.VISIBLE else View.GONE

        btnStart.setOnClickListener {
            val rawInput = inputField.text?.toString()?.trim().orEmpty()
            val parsed = rawInput.toFloatOrNull() ?: defaultWeight

            if (parsed != null) {
                if (minWeight != null && parsed < minWeight) {
                    inputLayout.error = getString(R.string.weight_min_error, minWeight)
                    return@setOnClickListener
                }
                if (maxWeight != null && parsed > maxWeight) {
                    inputLayout.error = getString(R.string.weight_max_error, maxWeight)
                    return@setOnClickListener
                }
            }

            inputLayout.error = null
            viewModel.updateSessionWeight(parsed, "kg")
            isWeightDialogVisible = false
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            viewModel.updateSessionWeight(null, "kg")
            isWeightDialogVisible = false
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            isWeightDialogVisible = false
        }

        dialog.show()
    }
    
    // ==================== Session Mode ====================

    /**
     * Initialize session mode: parse items, create SessionTrainingEngine,
     * resolve exercise names, observe state, and start the session.
     */
    private fun initializeSessionMode() {
        val json = intent.getStringExtra(EXTRA_SESSION_ITEMS_JSON)
        if (json.isNullOrBlank()) {
            Log.e(TAG, "Session mode but no items JSON provided")
            finish()
            return
        }

        val gson = com.google.gson.Gson()
        val itemsType = object : com.google.gson.reflect.TypeToken<List<com.trainingvalidator.poc.training.models.ProgramSessionItem>>() {}.type
        val items: List<com.trainingvalidator.poc.training.models.ProgramSessionItem> = try {
            gson.fromJson(json, itemsType)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse session items JSON", e)
            finish()
            return
        }

        if (items.isEmpty()) {
            Log.w(TAG, "Session items list is empty")
            finish()
            return
        }

        val invalidExerciseItem = items.firstOrNull { it.type == "exercise" && it.exerciseSlug.isNullOrBlank() }
        if (invalidExerciseItem != null) {
            Log.e(TAG, "Invalid session payload: exercise item without exerciseSlug")
            Toast.makeText(
                this,
                getString(R.string.session_invalid_payload),
                Toast.LENGTH_LONG
            ).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        // Create engine
        val engine = com.trainingvalidator.poc.training.session.SessionTrainingEngine(items)

        // Resolve exercise names
        val exerciseRepo = ExerciseRepository.getInstance(this)
        val language = currentLanguage
        sessionExerciseConfigMap.clear()
        items.filter { it.type == "exercise" && it.exerciseSlug != null }.forEach { item ->
            val slug = item.exerciseSlug ?: return@forEach
            val config = exerciseRepo.getExercise(slug)
            if (config != null) {
                sessionExerciseConfigMap[slug] = config
                val name = config.name.get(language).ifBlank { config.name.en }
                engine.setExerciseName(slug, name)
            }
        }

        sessionTrainingEngine = engine

        // Register for exercise completion callbacks (rich report generation)
        engine.onExerciseCompletedListener = this

        // Observe session engine state
        observeSessionEngineState()

        // Start the session
        engine.start()
    }

    /**
     * Observe SessionTrainingEngine state changes and update UI accordingly.
     */
    private fun observeSessionEngineState() {
        val engine = sessionTrainingEngine ?: return
        lifecycleScope.launch {
            engine.state.collectLatest { sessionState ->
                when (sessionState) {
                    is com.trainingvalidator.poc.training.session.SessionTrainingEngine.State.Idle -> {
                        // Waiting
                    }
                    is com.trainingvalidator.poc.training.session.SessionTrainingEngine.State.PreExercise -> {
                        showSessionPreExercise(sessionState)
                    }
                    is com.trainingvalidator.poc.training.session.SessionTrainingEngine.State.Training -> {
                        // Training handled by supervisor - panels already hidden
                    }
                    is com.trainingvalidator.poc.training.session.SessionTrainingEngine.State.SetRest -> {
                        showCelebrationMessage(getString(R.string.session_celebrate_set))
                        showSessionRest(
                            durationMs = sessionState.durationMs,
                            title = getString(R.string.session_rest_between_sets),
                            nextInfo = getString(
                                R.string.session_rest_next_set_format,
                                sessionState.exerciseName,
                                sessionState.nextSetNumber,
                                sessionState.totalSets
                            )
                        )
                    }
                    is com.trainingvalidator.poc.training.session.SessionTrainingEngine.State.ExerciseRest -> {
                        showCelebrationMessage(getString(R.string.session_celebrate_exercise))
                        showSessionRest(
                            durationMs = sessionState.durationMs,
                            title = getString(R.string.session_rest_between_exercises),
                            nextInfo = getString(
                                R.string.session_rest_next_exercise_format,
                                sessionState.nextExerciseName
                            )
                        )
                    }
                    is com.trainingvalidator.poc.training.session.SessionTrainingEngine.State.SessionComplete -> {
                        showCelebrationMessage(getString(R.string.session_celebrate_session))
                        showSessionComplete(sessionState.report)
                    }
                }
            }
        }
    }

    /**
     * Show pre-exercise overlay with exercise info and "Start Set" button.
     */
    private fun showSessionPreExercise(
        state: com.trainingvalidator.poc.training.session.SessionTrainingEngine.State.PreExercise
    ) {
        hideSessionPanels()
        binding.sessionPreExercisePanel.visibility = View.VISIBLE

        // Hide normal training UI
        binding.setupPosePanel.visibility = View.GONE
        binding.countdownPanel.visibility = View.GONE
        binding.heroCounterContainer.visibility = View.GONE
        binding.completedPanel.visibility = View.GONE
        binding.bottomStatsBar.visibility = View.GONE
        binding.progressContainer.visibility = View.GONE

        val engine = sessionTrainingEngine ?: return
        val item = state.item

        // Exercise label: "EXERCISE 2/5"
        binding.tvSessionExerciseLabel.text = getString(
            R.string.session_exercise_label,
            state.exerciseIndex + 1,
            engine.getExerciseCount()
        )

        // Exercise name
        binding.tvSessionExerciseName.text = state.exerciseName

        hideAlternatingLabels()
        updateSessionSetIndicator(state.setNumber, state.totalSets)

        // Set info: "Set 1 of 3 · 12 reps" or "Set 1 of 3 · 30s hold"
        val targetReps = item.targetReps
        val targetDuration = item.targetDuration
        binding.tvSessionSetInfo.text = when {
            targetReps != null && targetReps > 0 -> getString(
                R.string.session_set_reps_format,
                state.setNumber, state.totalSets, targetReps
            )
            targetDuration != null && targetDuration > 0 -> getString(
                R.string.session_set_duration_format,
                state.setNumber, state.totalSets, targetDuration
            )
            else -> getString(
                R.string.session_set_only_format,
                state.setNumber, state.totalSets
            )
        }

        // Weight info
        val weight = engine.getCurrentSetWeight()
        if (weight != null && weight > 0f) {
            binding.tvSessionWeightInfo.text = getString(R.string.session_weight_format, weight)
            binding.tvSessionWeightInfo.visibility = View.VISIBLE
        } else {
            binding.tvSessionWeightInfo.visibility = View.GONE
        }

        // "Start Set" button
        binding.btnSessionStartSet.setOnClickListener {
            onSessionStartSetClicked(state)
        }
    }

    /**
     * Handle "Start Set" click: load exercise into ViewModel and start training.
     */
    private fun onSessionStartSetClicked(
        state: com.trainingvalidator.poc.training.session.SessionTrainingEngine.State.PreExercise
    ) {
        val engine = sessionTrainingEngine ?: return
        val item = state.item
        val slug = item.exerciseSlug ?: return

        // Hide pre-exercise panel
        hideSessionPanels()

        // Show normal training UI
        binding.bottomStatsBar.visibility = View.VISIBLE
        updateSessionSetIndicator(state.setNumber, state.totalSets)

        // Reset supervisor for fresh exercise flow
        viewModel.supervisor.reset()
        hasShownWeightDialog = false

        // Prepare target overrides
        val targetReps = item.targetReps?.takeIf { it > 0 }
        val targetDurationMs = item.targetDuration?.takeIf { it > 0 }?.let { it * 1000L }
        val weight = engine.getCurrentSetWeight()
        val poseVariantIndex = 0  // Bilateral uses single poseVariant, side switching handled by TrainingEngine

        // Load exercise into ViewModel (triggers supervisor → SETUP_POSE → COUNTDOWN → TRAINING)
        if (!viewModel.loadExercise(
                exerciseName = slug,
                poseVariantIndex = poseVariantIndex,
                targetRepsOverride = targetReps,
                targetDurationMsOverride = targetDurationMs,
                context = this,
                weightKg = weight,
                weightUnit = "kg"
            )) {
            Log.e(TAG, "Failed to load exercise for session: $slug")
            Toast.makeText(
                this,
                getString(R.string.session_exercise_load_failed),
                Toast.LENGTH_LONG
            ).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        updateCounterLabelForCurrentExercise()

        // Mark session engine as training
        engine.startTraining()

        // Track a unique run id for this set attempt.
        // Both ExerciseCompleted and TrainingCompleted can arrive for the same set,
        // so completion must be accepted once per run id only.
        currentSessionSetRunId += 1L

        // Record set start time for duration tracking
        sessionSetStartTimeMs = System.currentTimeMillis()

        // Show weight dialog if applicable
        maybeShowWeightDialog()

        // Initialize feedback if not yet done
        if (viewModel.feedbackManager == null) {
            viewModel.initializeFeedback(this, isVideoMode)
        }
    }

    /**
     * Hide alternating labels (legacy UI elements).
     * Bilateral side display is now driven by TrainingEngine.bilateralSide StateFlow.
     */
    private fun hideAlternatingLabels() {
        binding.tvSessionAlternatingLabel.visibility = View.GONE
        binding.tvAlternatingLabel.visibility = View.GONE
    }

    private fun updateSessionSetIndicator(setNumber: Int, totalSets: Int) {
        binding.tvSessionSetIndicator.text = getString(
            R.string.session_set_indicator_format,
            setNumber,
            totalSets
        )
        binding.tvSessionSetIndicator.visibility = View.VISIBLE
    }

    /**
     * Show rest countdown overlay (between sets or between exercises).
     */
    private fun showSessionRest(durationMs: Long, title: String, nextInfo: String) {
        hideSessionPanels()
        binding.sessionRestPanel.visibility = View.VISIBLE

        // Hide normal training UI
        binding.setupPosePanel.visibility = View.GONE
        binding.countdownPanel.visibility = View.GONE
        binding.heroCounterContainer.visibility = View.GONE
        binding.completedPanel.visibility = View.GONE
        binding.bottomStatsBar.visibility = View.GONE
        binding.progressContainer.visibility = View.GONE
        binding.vignetteOverlay.clear()
        binding.skeletonOverlay.setTrainingMode(false)

        binding.tvSessionRestTitle.text = title
        binding.tvSessionRestNext.text = nextInfo
        binding.tvSessionRestTip.text = getRestTip(title)

        startSessionRestTimer(durationMs)

        binding.btnSessionAddTime.setOnClickListener {
            startSessionRestTimer(sessionRestRemainingMs + 20000L)
        }

        binding.btnSessionEditRest.setOnClickListener {
            showEditRestDialog()
        }

        // Skip rest button
        binding.btnSessionSkipRest.setOnClickListener {
            sessionRestTimer?.cancel()
            sessionTrainingEngine?.onRestCompleted()
        }
    }

    private fun startSessionRestTimer(durationMs: Long) {
        sessionRestTimer?.cancel()
        sessionRestRemainingMs = durationMs
        sessionRestTimer = object : android.os.CountDownTimer(durationMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secs = (millisUntilFinished / 1000).toInt()
                val m = secs / 60
                val s = secs % 60
                binding.tvSessionRestCountdown.text = String.format("%02d:%02d", m, s)
                sessionRestRemainingMs = millisUntilFinished
            }

            override fun onFinish() {
                binding.tvSessionRestCountdown.text = "00:00"
                sessionRestRemainingMs = 0L
                playRestEndAlert()
                sessionTrainingEngine?.onRestCompleted()
            }
        }.start()
    }

    private fun showEditRestDialog() {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.rest_seconds_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText((sessionRestRemainingMs / 1000L).toString())
        }

        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_rest))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { dialog, _ ->
                val seconds = input.text.toString().toLongOrNull()
                if (seconds != null && seconds > 0) {
                    startSessionRestTimer(seconds * 1000L)
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showCelebrationMessage(message: String) {
        binding.glassmorphicMessage.showMessage(message, GlassmorphicMessageView.TYPE_MOTIVATION, 800)
        vibrateShort()
    }

    private fun vibrateShort() {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val manager = getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(80, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(80)
        }
    }

    private fun getRestTip(title: String): String {
        val tips = if (title.contains(getString(R.string.session_rest_between_sets), true)) {
            listOf(
                getString(R.string.rest_tip_breathe),
                getString(R.string.rest_tip_quality),
                getString(R.string.rest_tip_focus)
            )
        } else {
            listOf(
                getString(R.string.rest_tip_next),
                getString(R.string.rest_tip_recovery),
                getString(R.string.rest_tip_consistency)
            )
        }
        return tips.random()
    }

    private fun playRestEndAlert() {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
        tone.release()

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(android.os.VibratorManager::class.java)
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        }
    }

    /**
     * Show session complete overlay with summary.
     */
    private fun showSessionComplete(
        report: com.trainingvalidator.poc.training.session.SessionTrainingEngine.SessionReport
    ) {
        hideSessionPanels()
        binding.sessionCompletePanel.visibility = View.VISIBLE

        // Hide normal training UI
        binding.setupPosePanel.visibility = View.GONE
        binding.countdownPanel.visibility = View.GONE
        binding.heroCounterContainer.visibility = View.GONE
        binding.completedPanel.visibility = View.GONE
        binding.bottomStatsBar.visibility = View.GONE
        binding.progressContainer.visibility = View.GONE
        binding.vignetteOverlay.clear()
        binding.skeletonOverlay.setTrainingMode(false)

        // Stop elapsed timer
        stopElapsedTimeTimer()

        val minutes = (report.totalDurationMs / 60000).toInt()
        val seconds = ((report.totalDurationMs % 60000) / 1000).toInt()

        binding.tvSessionCompleteSets.text = getString(
            R.string.session_complete_sets_format,
            report.totalSetsCompleted,
            report.totalSetsPlanned
        )
        binding.tvSessionCompleteReps.text = getString(
            R.string.session_complete_reps_format,
            report.totalReps
        )
        binding.tvSessionCompleteDuration.text = getString(
            R.string.session_complete_duration_format,
            minutes, seconds
        )
        binding.tvSessionCompleteAccuracy.text = getString(
            R.string.session_complete_accuracy_format,
            report.averageAccuracy.toInt()
        )

        binding.btnSessionDone.setOnClickListener {
            finishSessionWithResult(report)
        }
    }

    /**
     * Called when a set completes during session mode.
     * Collects metrics and passes to SessionTrainingEngine.
     */
    private fun onSessionSetCompleted() {
        val engine = sessionTrainingEngine ?: return
        val currentItem = engine.getCurrentExerciseItem() ?: return
        val trainingEng = viewModel.trainingEngine

        val reps = trainingEng?.getCurrentRep() ?: 0
        val accuracy = trainingEng?.getAccuracy() ?: 0f
        val durationMs = System.currentTimeMillis() - sessionSetStartTimeMs
        val weight = engine.getCurrentSetWeight()
        val targetReps = currentItem.targetReps ?: reps

        // Stop the training engine for this set
        trainingEng?.stop()
        stopElapsedTimeTimer()

        // Build per-rep details from the training engine's rep results
        val repDetails = trainingEng?.getRepResults()?.map { repResult ->
            val repDurationMs = repResult.phaseTimings.values.sum()
            com.trainingvalidator.poc.training.session.SessionTrainingEngine.RepDetail(
                repNumber = repResult.repNumber,
                score = repResult.score,
                worstState = repResult.worstState.ordinal,
                isCounted = repResult.isCounted,
                durationMs = repDurationMs
            )
        } ?: emptyList()

        // Form score = average rep score (quality of movement, 0-100)
        val formScore = if (repDetails.isNotEmpty()) {
            repDetails.map { it.score }.average().toFloat()
        } else {
            accuracy // Fallback to completion rate if no rep data
        }

        val metrics = com.trainingvalidator.poc.training.session.SessionTrainingEngine.SetMetrics(
            exerciseSlug = currentItem.exerciseSlug ?: "",
            exerciseIndex = engine.getCurrentExerciseIndex(),
            setNumber = engine.getCurrentSetNumber(),
            repsCompleted = reps,
            repsTarget = targetReps,
            durationMs = durationMs,
            accuracy = accuracy,
            formScore = formScore,
            weightKg = weight,
            repDetails = repDetails
        )

        Log.d(TAG, "Session set completed: ${metrics.exerciseSlug} " +
                "set ${metrics.setNumber}, reps=$reps, formScore=${formScore.toInt()}")

        engine.onSetCompleted(metrics)
    }

    /**
     * Finish session mode and return result to calling activity.
     */
    private fun finishSessionWithResult(
        report: com.trainingvalidator.poc.training.session.SessionTrainingEngine.SessionReport
    ) {
        val reportJson = com.google.gson.Gson().toJson(report)
        val resultIntent = android.content.Intent().apply {
            putExtra(RESULT_IS_COMPLETED, true)
            putExtra(RESULT_DURATION_MS, report.totalDurationMs)
            putExtra(RESULT_SESSION_SETS_COMPLETED, report.totalSetsCompleted)
            putExtra(RESULT_SESSION_SETS_PLANNED, report.totalSetsPlanned)
            putExtra(RESULT_SESSION_TOTAL_REPS, report.totalReps)
            putExtra(RESULT_SESSION_AVG_ACCURACY, report.averageAccuracy)
            putExtra(RESULT_SESSION_AVG_FORM_SCORE, report.averageFormScore)
            putExtra(RESULT_SESSION_REPORT_JSON, reportJson)
            putStringArrayListExtra(
                RESULT_SESSION_REPORT_IDS,
                ArrayList(report.reportIds)
            )
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    // ==================== OnExerciseCompletedListener ====================

    /**
     * Called by SessionTrainingEngine when the LAST set of an exercise completes.
     * Generates a rich PostTrainingReport using the current TrainingEngine data
     * (which still has this exercise loaded) and saves it to ReportStorage.
     */
    override fun onExerciseCompleted(
        exerciseIndex: Int,
        exerciseSlug: String,
        sets: List<com.trainingvalidator.poc.training.session.SessionTrainingEngine.SetMetrics>
    ) {
        val engine = viewModel.trainingEngine ?: run {
            Log.w(TAG, "onExerciseCompleted: TrainingEngine is null, skipping report generation")
            return
        }
        val exerciseConfig = sessionExerciseConfigMap[exerciseSlug] ?: run {
            Log.w(TAG, "onExerciseCompleted: ExerciseConfig not found for $exerciseSlug")
            return
        }

        val frameCaptures = frameCaptureManager?.getAllCaptures() ?: emptyList()
        val sessionDurationMs = sets.sumOf { it.durationMs }

        Log.d(TAG, "onExerciseCompleted: Generating rich report for $exerciseSlug " +
                "(${sets.size} sets, ${frameCaptures.size} frames)")

        // Generate report in background to avoid blocking the main thread
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sessionUpload = viewModel.finalizeAndGetSessionUpload()
                val sessionMetrics = sessionUpload?.sessionMetrics

                val report = ReportGenerator.generateFromEngine(
                    engine = engine,
                    exerciseConfig = exerciseConfig,
                    sessionDurationMs = sessionDurationMs,
                    frameCaptures = frameCaptures,
                    sessionMetrics = sessionMetrics,
                    weightKg = viewModel.getWeightKg(),
                    weightUnit = viewModel.getWeightUnit()
                )

                val saved = reportStorage?.save(report) ?: false
                Log.d(TAG, "onExerciseCompleted: Report saved for $exerciseSlug: " +
                        "id=${report.id}, saved=$saved")

                if (saved) {
                    sessionTrainingEngine?.setExerciseReportId(exerciseSlug, report.id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "onExerciseCompleted: Failed to generate report for $exerciseSlug", e)
            }
        }

        // Reset frame capture manager for the next exercise
        resetFrameCaptureManagerForNextExercise()
    }

    /**
     * Reset the FrameCaptureManager with a new session ID for the next exercise.
     * Each exercise in a session gets its own set of frame captures.
     */
    private fun resetFrameCaptureManagerForNextExercise() {
        val newSessionId = java.util.UUID.randomUUID().toString()
        Log.d(TAG, "Resetting FrameCaptureManager for next exercise: sessionId=$newSessionId")
        frameCaptureManager = FrameCaptureManager(this, newSessionId)
    }

    /**
     * Hide all session overlay panels.
     */
    private fun hideSessionPanels() {
        binding.sessionPreExercisePanel.visibility = View.GONE
        binding.sessionRestPanel.visibility = View.GONE
        binding.sessionCompletePanel.visibility = View.GONE
        binding.tvSessionAlternatingLabel.visibility = View.GONE
        binding.tvAlternatingLabel.visibility = View.GONE
        binding.tvSessionSetIndicator.visibility = View.GONE
        sessionRestTimer?.cancel()
    }

    /**
     * Show training settings dialog
     */
    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_training_settings, null)
        val dialog = android.app.AlertDialog.Builder(this, R.style.Theme_WayToFix_Dialog)
            .setView(dialogView)
            .create()
        
        // Get current settings
        var selectedIndicator = SettingsManager.getIndicatorType()
        var voiceFeedbackEnabled = SettingsManager.isVoiceFeedbackEnabled()
        var selectedModel = SettingsManager.getModelType()
        
        // Setup indicator buttons
        val btnLine = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnIndicatorLine)
        val btnArc = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnIndicatorArc)
        
        fun updateIndicatorButtons() {
            if (selectedIndicator == "line") {
                btnLine.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
                btnLine.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
                btnArc.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                btnArc.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            } else {
                btnArc.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
                btnArc.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
                btnLine.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                btnLine.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }
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
        
        // Setup model buttons
        val btnModelFull = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnModelFull)
        val btnModelHeavy = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnModelHeavy)
        
        fun updateModelButtons() {
            if (selectedModel == "full") {
                btnModelFull.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
                btnModelFull.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
                btnModelHeavy.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                btnModelHeavy.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            } else {
                btnModelHeavy.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
                btnModelHeavy.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
                btnModelFull.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                btnModelFull.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }
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
        val btnSwitchCameraDialog = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSwitchCameraDialog)
        
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
                useFrontCamera = !useFrontCamera
                cameraManager?.switchCamera(useFrontCamera)
                if (::landmarkSmoother.isInitialized) {
                    landmarkSmoother.reset()
                }
                // Keep overlay mirroring in sync with the active camera
                binding.skeletonOverlay.updateFrontCameraState(useFrontCamera)
                tvCurrentCamera.text = if (useFrontCamera) getString(R.string.front_camera) else getString(R.string.back_camera)
            }
        }
        
        // Apply button
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnApplySettings).setOnClickListener {
            // Save settings
            SettingsManager.setIndicatorType(selectedIndicator)
            SettingsManager.setVoiceFeedbackEnabled(voiceFeedbackEnabled)
            SettingsManager.setModelType(selectedModel)
            
            // Apply indicator change immediately
            binding.skeletonOverlay.setIndicatorType(selectedIndicator)
            
            // Update feedback manager
            viewModel.feedbackManager?.setVoiceEnabled(voiceFeedbackEnabled)
            
            dialog.dismiss()
            
            Toast.makeText(this, getString(R.string.settings) + " " + getString(R.string.save), Toast.LENGTH_SHORT).show()
        }
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
    
    private fun setupCountdownController() {
        viewModel.countdownController.setListener(object : CountdownController.CountdownListener {
            override fun onTick(secondsRemaining: Int) {
                AnimationUtils.animateCountdown(binding.tvCountdown, secondsRemaining.toString())
                viewModel.feedbackManager?.speakCountdown(secondsRemaining)
            }

            override fun onFinish() {
                // Notify supervisor IMMEDIATELY - animation runs in parallel
                viewModel.onCountdownFinished()
                viewModel.feedbackManager?.speakGo()
                AnimationUtils.animateGoText(binding.tvCountdown) { /* cosmetic only */ }
            }

            override fun onCancelled() {
                // UI update handled by TrainingUIEvent.CountdownCancelled — no-op here
            }

            override fun onFrozen() {
                // Dim the countdown number + show pulsing warning colour
                binding.tvCountdown.alpha = 0.45f
                binding.tvCountdown.setTextColor(
                    ContextCompat.getColor(this@TrainingActivity, R.color.warning)
                )
            }

            override fun onUnfrozen() {
                binding.tvCountdown.alpha = 1f
                binding.tvCountdown.setTextColor(
                    ContextCompat.getColor(this@TrainingActivity, R.color.text_primary)
                )
                binding.vignetteOverlay.clear()
            }
        })
    }
    
    /**
     * Start elapsed time timer
     */
    private fun startElapsedTimeTimer() {
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
    private fun stopElapsedTimeTimer() {
        elapsedTimeJob?.cancel()
        elapsedTimeJob = null
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
        lifecycleScope.launch {
            viewModel.holdElapsedMs.collectLatest { elapsed ->
                if (!viewModel.isHoldExercise()) return@collectLatest
                elapsed?.let {
                    binding.tvRepCount.text = formatTimeMs(it)
                    binding.tvProgress.text = "${formatTimeMs(it)} / ${formatTimeMs(viewModel.getTargetDurationMs())}"
                }
            }
        }

        lifecycleScope.launch {
            viewModel.holdState.collectLatest { holdState ->
                if (!viewModel.isHoldExercise()) return@collectLatest
                holdState?.let { updateUIForHoldState(it) }
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
                
                // Update form status based on worst state
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
                
                // Trigger low-priority random messages during quiet time
                val positionErrors = engine.positionErrors.value
                viewModel.feedbackManager?.checkAndDeliverRandomMessage(
                    hasActiveErrors = hasErrors || positionErrors.isNotEmpty()
                )
            }
        }
    }
    
    @Suppress("DEPRECATION")
    private fun handleUIEvent(event: TrainingUIEvent) {
        when (event) {
            is TrainingUIEvent.ShowSetupPose -> {
                showSetupPoseUI()
            }

            is TrainingUIEvent.SetupGuidanceUpdate -> {
                updateSetupGuidanceUI(event.result)
            }

            is TrainingUIEvent.StartCountdown -> {
                binding.setupPosePanel.visibility = View.GONE
                binding.countdownPanel.visibility = View.VISIBLE
                binding.tvCountdown.visibility = View.VISIBLE
                binding.tvCountdown.alpha = 1f
                binding.tvCountdown.setTextColor(
                    ContextCompat.getColor(this, R.color.text_primary)
                )
                // Switch bottom bar FORM card back to "FORM" label
                switchBottomBarToFormMode()
                // Exit skeleton setup mode once countdown starts
                binding.skeletonOverlay.clearSetupMode()
            }

            is TrainingUIEvent.CountdownCancelled -> {
                updateUIForSessionState(SessionState.SETUP_POSE)
                showSetupPoseUI()
            }

            is TrainingUIEvent.CountdownFrozen -> {
                // Handled directly by CountdownController listener callbacks above
            }

            is TrainingUIEvent.CountdownPoseIssue -> {
                showCountdownPoseIssue(event.result)
            }

            is TrainingUIEvent.CountdownUnfrozen -> {
                // Handled directly by CountdownController listener callbacks above
            }

            is TrainingUIEvent.PoseValidationUpdate -> {
                // Legacy no-op: SetupGuidanceUpdate handles everything now
            }
            
            is TrainingUIEvent.TrainingStarted -> {
                binding.skeletonOverlay.clearSetupMode()
                val trackedIndices = viewModel.getTrackedLandmarkIndices()
                binding.skeletonOverlay.setTrainingMode(true, trackedIndices, useFrontCamera)
                observeTrainingEngineState()
            }
            
            is TrainingUIEvent.ExerciseCompleted,
            is TrainingUIEvent.TrainingCompleted -> {
                if (isSessionMode) {
                    tryHandleSessionSetCompleted()
                } else {
                    completeTraining()
                }
            }
            
            is TrainingUIEvent.AutoPaused -> {
                handleAutoPaused(event.reason)
            }
            
            is TrainingUIEvent.NoPoseWarning -> {
                handleNoPoseWarning(event.elapsedMs)
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

    private fun tryHandleSessionSetCompleted() {
        if (currentSessionSetRunId <= 0L) {
            Log.w(TAG, "Ignoring completion event without active set run id")
            return
        }

        if (lastCompletedSessionSetRunId == currentSessionSetRunId) {
            Log.d(
                TAG,
                "Ignoring duplicate session completion event for runId=$currentSessionSetRunId"
            )
            return
        }

        lastCompletedSessionSetRunId = currentSessionSetRunId
        onSessionSetCompleted()
    }

    private fun updateCounterLabelForCurrentExercise() {
        val labelRes = if (viewModel.isHoldExercise()) R.string.time else R.string.reps
        binding.tvRepsLabel.text = getString(labelRes)
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
                if (::landmarkSmoother.isInitialized) {
                    landmarkSmoother.reset()
                }

                binding.skeletonOverlay.clearSetupMode()
                switchBottomBarToFormMode()

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
                binding.bottomStatsBar.visibility = View.VISIBLE
                updatePlayPauseIcon(isPlaying = true)

                // Ensure setup overlay is off and bottom bar shows FORM
                binding.skeletonOverlay.clearSetupMode()
                switchBottomBarToFormMode()

                // Start elapsed time timer
                if (trainingStartTime == 0L) {
                    startElapsedTimeTimer()
                }
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
                binding.skeletonOverlay.clearSetupMode()
                switchBottomBarToFormMode()
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
    
    // ──────────────────────────────────────────────────────────────────────
    // Setup Pose UI
    // ──────────────────────────────────────────────────────────────────────

    /** Show the setup panel and activate skeleton setup mode. */
    private fun showSetupPoseUI() {
        binding.setupPosePanel.visibility = View.VISIBLE
        binding.countdownPanel.visibility = View.GONE
        binding.skeletonOverlay.clearSetupMode()
        binding.jointGuidanceContainer.removeAllViews()
        binding.tvPhaseInstruction.visibility = View.GONE
        binding.tvPhaseStep.visibility = View.GONE
        binding.jointGuidanceContainer.visibility = View.VISIBLE
        switchBottomBarToSetupMode()
    }

    /** Show a concise reason while countdown is frozen/canceling due pose drift. */
    private fun showCountdownPoseIssue(result: SetupResult) {
        val lang = viewModel.poseSetupGuide.language
        val message = when {
            result.phase != SetupPhase.ANGLES -> result.phaseMessage?.get(lang)
            result.worstJoint != null -> result.worstJoint.message.get(lang)
            else -> "Return to the start pose"
        } ?: return

        binding.glassmorphicMessage.showMessage(
            message,
            GlassmorphicMessageView.TYPE_WARNING,
            1000
        )
        binding.vignetteOverlay.showWarning()
    }

    /**
     * Phase-aware setup guidance. Shows one thing at a time:
     * REGION/POSTURE/DIRECTION → prominent instruction text
     * ANGLES → per-joint guidance rows
     */
    private fun updateSetupGuidanceUI(result: SetupResult) {
        val state = viewModel.supervisor.state.value
        if (state != SessionState.SETUP_POSE && state != SessionState.RESUME_SETUP) return

        val lang = viewModel.poseSetupGuide.language
        val isAr = lang == "ar"
        val phase = result.phase

        // ── Header: title + step indicator ────────────────────────────────
        val stepNum = phase.ordinal + 1
        binding.tvPhaseStep.visibility = View.VISIBLE
        binding.tvPhaseStep.text = if (isAr) "$stepNum / 4" else "$stepNum / 4"
        binding.tvSetupTitle.text = when (phase) {
            SetupPhase.REGION -> if (isAr) "منطقة الجسم" else "Body Region"
            SetupPhase.POSTURE -> if (isAr) "وضعية الجسم" else "Body Posture"
            SetupPhase.DIRECTION -> if (isAr) "اتجاه الكاميرا" else "Camera Direction"
            SetupPhase.ANGLES -> if (isAr) "ضبط الزوايا" else "Adjust Angles"
        }

        // ── Phase instruction (scene phases) vs joint rows (angles phase) ─
        if (phase != SetupPhase.ANGLES) {
            val msg = result.phaseMessage?.get(lang) ?: ""
            binding.tvPhaseInstruction.text = msg
            binding.tvPhaseInstruction.visibility = View.VISIBLE
            binding.jointGuidanceContainer.visibility = View.GONE
            binding.skeletonOverlay.clearSetupMode()

            // Voice guidance for scene phases
            if (result.phaseMessage != null &&
                viewModel.poseSetupGuide.shouldSpeakPhaseGuidance(phase)
            ) {
                viewModel.feedbackManager?.speakSetupPhaseGuidance(result.phaseMessage)
                viewModel.poseSetupGuide.onPhaseGuidanceSpoken(phase)
            }
        } else {
            binding.tvPhaseInstruction.visibility = View.GONE
            binding.jointGuidanceContainer.visibility = View.VISIBLE

            // Skeleton overlay for joint angles
            val landmarks = viewModel.lastSmoothedLandmarks
            val imageSize = viewModel.lastImageSize
            if (landmarks != null) {
                binding.skeletonOverlay.updateSetupGuidance(
                    guidances = result.joints,
                    smoothedLandmarks = landmarks,
                    imageW = imageSize.first,
                    imageH = imageSize.second
                )
            }

            updateJointGuidanceRows(result.joints)

            // Voice guidance for worst joint
            val worstJoint = result.worstJoint
            if (worstJoint != null && viewModel.poseSetupGuide.shouldSpeakGuidance(worstJoint)) {
                viewModel.feedbackManager?.speakSetupGuidance(worstJoint)
                viewModel.poseSetupGuide.onVoiceGuidanceSpoken(worstJoint)
            }
        }

        // ── Progress (bottom bar READY card) ────────────────────────────
        updateReadyPercent(result.progress.percent)

        // ── Bottom bar VIEW card ──────────────────────────────────────────
        updateViewCard(result.camera)
    }

    /**
     * Build/update the per-joint guidance rows inside [jointGuidanceContainer].
     *
     * We recycle existing TextViews by tag to avoid layout churn.
     */
    private fun updateJointGuidanceRows(joints: List<JointGuidance>) {
        val container = binding.jointGuidanceContainer

        joints.forEachIndexed { i, guidance ->
            val row = container.findViewWithTag<android.widget.TextView>("joint_row_$i")
                ?: android.widget.TextView(this).also { tv ->
                    tv.tag = "joint_row_$i"
                    tv.textSize = 16f
                    tv.setPadding(0, 8, 0, 8)
                    tv.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    container.addView(tv)
                }

            row.visibility = View.VISIBLE
            val colorRes = when (guidance.level) {
                GuidanceLevel.GREEN  -> R.color.success
                GuidanceLevel.YELLOW -> R.color.warning
                GuidanceLevel.RED    -> R.color.error
            }
            val icon = when (guidance.level) {
                GuidanceLevel.GREEN  -> "✓"
                GuidanceLevel.YELLOW -> "⚠"
                GuidanceLevel.RED    -> "✗"
            }
            val arrow = when (guidance.direction) {
                Direction.LOWER -> " ↓"
                Direction.RAISE -> " ↑"
                else            -> ""
            }
            val angleStr = "%.0f°".format(guidance.currentAngle)

            row.text = "$icon  ${guidance.jointName}  $angleStr$arrow"
            row.setTextColor(ContextCompat.getColor(this, colorRes))
        }

        for (i in joints.size until container.childCount) {
            container.getChildAt(i).visibility = View.GONE
        }
    }

    /**
     * Update the bottom bar FORM/VIEW card with camera-position tip or form quality.
     */
    private fun updateViewCard(cameraGuidance: CameraGuidance?) {
        if (cameraGuidance == null) return
        val lang = viewModel.poseSetupGuide.language
        val (text, colorRes) = if (cameraGuidance.isCorrect) {
            (if (lang == "ar") "✓" else "✓ OK") to R.color.success
        } else {
            val label = cameraGuidance.tip?.get(lang) ?: "—"
            label to R.color.warning
        }
        binding.tvFormStatus.text = text
        binding.tvFormStatus.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    /** Switch bottom bar to setup mode: TIME→READY, FORM→VIEW */
    private fun switchBottomBarToSetupMode() {
        val isAr = viewModel.poseSetupGuide.language == "ar"
        // TIME card → READY card
        binding.tvTimeCardLabel.text = if (isAr) "جاهز" else "READY"
        binding.tvTimeElapsed.text = "0%"
        binding.tvTimeElapsed.visibility = View.VISIBLE
        binding.progressSetupReadyBar.visibility = View.VISIBLE
        binding.progressSetupReadyBar.progress = 0
        // FORM card → VIEW card
        binding.tvFormCardLabel.text = if (isAr) "الوضعية" else "VIEW"
    }

    /** Update the READY percent in the bottom bar TIME card. */
    private fun updateReadyPercent(percent: Int) {
        binding.tvTimeElapsed.text = "$percent%"
        binding.progressSetupReadyBar.progress = percent
    }

    /** Switch bottom bar back to training layout. */
    private fun switchBottomBarToFormMode() {
        binding.tvTimeCardLabel.text = getString(R.string.time)
        binding.tvFormCardLabel.text = getString(R.string.form)
        binding.progressSetupReadyBar.visibility = View.GONE
        binding.tvTimeElapsed.text = "00:00"
        binding.tvTimeElapsed.visibility = View.VISIBLE
        binding.tvFormStatus.text = getString(R.string.good)
        binding.tvFormStatus.setTextColor(ContextCompat.getColor(this, R.color.primary))
    }
    
    private fun updatePlayPauseIcon(isPlaying: Boolean) {
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
        
        // Hide the completed panel - we'll navigate to ReportPagerActivity instead
        binding.completedPanel.visibility = View.GONE
        
        // Log capture stats
        val captureCount = frameCaptureManager?.getCaptureCount() ?: 0
        val storageSize = frameCaptureManager?.getFormattedStorageSize() ?: "0 B"
        Log.d(TAG, "Training completed. Captured $captureCount frames ($storageSize)")
        
        // Generate report and navigate directly to ReportActivity
        generateReportAndNavigate()
    }
    
    private fun finishWithResult() {
        val resultIntent = android.content.Intent().apply {
            val engine = viewModel.trainingEngine
            putExtra(RESULT_REPS_COMPLETED, engine?.getCurrentRep() ?: 0)
            putExtra(RESULT_DURATION_MS, viewModel.getSessionDurationMs())
            putExtra(RESULT_ACCURACY, engine?.getAccuracy() ?: 0f)
            putExtra(RESULT_IS_COMPLETED, engine?.isCompleted?.value ?: false)
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
                
                val sessionUpload = viewModel.finalizeAndGetSessionUpload()
                val sessionMetrics = sessionUpload?.sessionMetrics
                
                val report = ReportGenerator.generateFromEngine(
                    engine = engine,
                    exerciseConfig = exerciseConfig,
                    sessionDurationMs = viewModel.getSessionDurationMs(),
                    frameCaptures = frameCaptures,
                    sessionMetrics = sessionMetrics,
                    weightKg = viewModel.getWeightKg(),
                    weightUnit = viewModel.getWeightUnit()
                )
                
                Log.d(TAG, "Report generated: id=${report.id}, accuracy=${report.summary.accuracy}%")
                
                val saved = reportStorage?.save(report) ?: false
                Log.d(TAG, "Report saved locally: $saved")
                
                // Navigate to report IMMEDIATELY (Offline-First)
                launch(Dispatchers.Main) {
                    binding.glassmorphicMessage.hide()
                    
                    if (isAssessmentMode) {
                        // Assessment mode: return report ID without showing report page
                        val resultIntent = android.content.Intent().apply {
                            putExtra(RESULT_REPORT_ID, report.id)
                            putExtra(RESULT_IS_COMPLETED, true)
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                        return@launch
                    }

                    if (saved) {
                        Log.d(TAG, "Navigating to ReportPagerActivity with id: ${report.id}")
                        
                        try {
                            // Use new full-screen pager report activity
                            val intent = ReportPagerActivity.createIntent(this@TrainingActivity, report.id)
                            startActivity(intent)
                            finish()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start ReportPagerActivity: ${e.message}", e)
                            showFallbackSummary()
                        }
                    } else {
                        Log.e(TAG, "Failed to save report, showing fallback")
                        showFallbackSummary()
                    }
                }
                
                // Sync to backend in background (non-blocking, lifecycle-safe)
                // ProcessLifecycleOwner survives Activity finish since navigation already happened
                androidx.lifecycle.ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        syncSessionToBackendStandalone(report.id, sessionUpload)
                    } catch (e: Exception) {
                        Log.w(TAG, "Background sync failed, will retry later: ${e.message}")
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
     * Sync session data to backend. Accepts the already-finalized upload to avoid
     * calling finalizeAndGetSessionUpload() twice (race condition risk).
     * Uses applicationContext to survive Activity destruction.
     */
    private suspend fun syncSessionToBackendStandalone(
        sessionId: String,
        sessionUpload: SessionUpload?
    ) {
        val appContext = applicationContext
        
        try {
            if (sessionUpload == null) {
                Log.w(TAG, "No session data to sync (MotionRecorder not active)")
                return
            }
            
            Log.d(TAG, "Syncing session to backend: ${sessionUpload.id}, " +
                       "${sessionUpload.totalReps} reps, " +
                       "avgScore=${sessionUpload.sessionMetrics.avgFormScore / 10f}%")
            
            // Refresh token if needed (after 20 hours)
            if (AuthManager.shouldRefreshToken(appContext)) {
                Log.d(TAG, "Token needs refresh, attempting...")
                refreshTokenStandalone(appContext)
            }
            
            // Get sync service
            val syncService = SessionSyncService.getInstance(
                appContext,
                ApiConfig.getEffectiveBaseUrl()
            )
            
            // Get auth token from AuthManager
            val token = AuthManager.getAccessToken(appContext)
            
            if (token != null) {
                syncService.setAuthToken(token)
                
                // Upload session
                val result = syncService.uploadSession(sessionUpload)
                
                if (result.success) {
                    Log.d(TAG, "Session synced successfully!")
                } else {
                    Log.w(TAG, "Session sync failed (saved for later): ${result.error}")
                    // Session is saved locally in AnalyticsStorage for retry
                }
            } else {
                Log.w(TAG, "No auth token, saving session for later sync")
                // Save locally for later sync
                val storage = AnalyticsStorage(appContext)
                storage.savePending(sessionUpload)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing session: ${e.message}", e)
            // Don't fail the whole flow, just log the error
        }
    }
    
    /**
     * Standalone token refresh that uses Context instead of Activity
     */
    private suspend fun refreshTokenStandalone(context: android.content.Context) {
        try {
            val refreshTokenValue = AuthManager.getRefreshToken(context)
            if (refreshTokenValue == null) {
                Log.w(TAG, "No refresh token available")
                return
            }
            
            val client = okhttp3.OkHttpClient()
            val json = """{"refreshToken": "$refreshTokenValue"}"""
            val body = json.toRequestBody("application/json".toMediaType())
            
            val request = okhttp3.Request.Builder()
                .url("${ApiConfig.getEffectiveBaseUrl()}api/mobile/auth/refresh")
                .post(body)
                .build()
            
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        responseBody?.let { bodyStr ->
                            val gson = com.google.gson.Gson()
                            val result = gson.fromJson(bodyStr, RefreshResponse::class.java)
                            if (result.tokens != null) {
                                AuthManager.saveNewTokens(
                                    context,
                                    result.tokens.accessToken,
                                    result.tokens.refreshToken,
                                    result.tokens.expiresIn
                                )
                                Log.d(TAG, "Token refreshed successfully (standalone)")
                            }
                        }
                    } else {
                        Log.w(TAG, "Token refresh failed: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Token refresh error: ${e.message}")
        }
    }
    
    /**
     * Refresh access token using refresh token
     */
    private suspend fun refreshTokenIfNeeded() {
        try {
            val refreshToken = AuthManager.getRefreshToken(this@TrainingActivity)
            if (refreshToken == null) {
                Log.w(TAG, "No refresh token available")
                return
            }
            
            val client = okhttp3.OkHttpClient()
            val json = """{"refreshToken": "$refreshToken"}"""
            val body = json.toRequestBody("application/json".toMediaType())
            
            val request = okhttp3.Request.Builder()
                .url("${ApiConfig.getEffectiveBaseUrl()}api/mobile/auth/refresh")
                .post(body)
                .build()
            
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        // Parse response and save new tokens
                        responseBody?.let { body ->
                            val gson = com.google.gson.Gson()
                            val result = gson.fromJson(body, RefreshResponse::class.java)
                            if (result.tokens != null) {
                                AuthManager.saveNewTokens(
                                    this@TrainingActivity,
                                    result.tokens.accessToken,
                                    result.tokens.refreshToken,
                                    result.tokens.expiresIn
                                )
                                Log.d(TAG, "Token refreshed successfully")
                            }
                        }
                    } else {
                        Log.w(TAG, "Token refresh failed: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh error: ${e.message}")
        }
    }
    
    // Helper class for parsing refresh response
    private data class RefreshResponse(
        val tokens: TokenData? = null
    )
    
    private data class TokenData(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long
    )
    
    /**
     * Show fallback summary screen if report generation fails
     */
    private fun showFallbackSummary() {
        val summary = viewModel.trainingEngine?.stop()
        
        binding.skeletonOverlay.setTrainingMode(false)
        
        if (viewModel.isHoldExercise()) {
            val holdElapsed = viewModel.holdElapsedMs.value ?: 0L
            binding.tvSummaryReps.text = formatTimeMs(holdElapsed)
            binding.tvSummaryCorrect.text = getString(R.string.training_target_format, formatTimeMs(viewModel.getTargetDurationMs()))
            binding.tvSummaryAccuracy.text = getString(R.string.training_grace_periods_format, viewModel.trainingEngine?.getGracePeriodCount() ?: 0)
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
        if (isWeightDialogVisible) return

        // In session mode, skip pose processing when session panels are visible
        if (isSessionMode && (
            binding.sessionPreExercisePanel.visibility == View.VISIBLE ||
            binding.sessionRestPanel.visibility == View.VISIBLE ||
            binding.sessionCompletePanel.visibility == View.VISIBLE
        )) return

        // Drop frame if previous is still processing (prevents Main Thread queue buildup)
        if (isProcessingPoseFrame) return
        isProcessingPoseFrame = true
        
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                if (!::landmarkSmoother.isInitialized) return@launch
                
                // === Heavy computation on background thread ===
                val smoothedLandmarks = landmarkSmoother.smooth(result.landmarks, result.timestampMs)
                
                val worldLandmarks = result.worldLandmarks?.let {
                    landmarkSmoother.convertWorld(it, result.timestampMs)
                }
                
                val rawAngles = if (worldLandmarks != null) {
                    AngleCalculator.calculateAllAnglesSmoothed(
                        worldLandmarks,
                        visibilityThreshold = 0.5f,
                        use3D = true
                    )
                } else {
                    AngleCalculator.calculateAllAnglesSmoothed(
                        smoothedLandmarks,
                        visibilityThreshold = 0.5f
                    )
                }

                val correctedAngles = if (worldLandmarks != null) {
                    elbowAngleEstimator.correct(rawAngles, worldLandmarks, smoothedLandmarks, result.timestampMs)
                } else rawAngles

                val angles = if (result.isFrontCamera) correctedAngles.mirrored() else correctedAngles
                
                // === Minimal work on Main Thread: supervisor signal + UI update ===
                withContext(Dispatchers.Main) {
                    updateFps()
                    wasPoseDetectedLastFrame = true
                    
                    // Forward pose frame to supervisor via ViewModel
                    viewModel.onPoseFrame(
                        angles, smoothedLandmarks, result.isFrontCamera, result.timestampMs,
                        imageWidth = result.imageWidth, imageHeight = result.imageHeight
                    )

                    // Update skeleton overlay with JointStateInfo
                    val stateInfos = viewModel.trainingEngine?.jointStateInfos?.value ?: emptyMap()
                    val positionErrors = viewModel.trainingEngine?.positionErrors?.value ?: emptyList()
                    val bilateralFlipped = viewModel.trainingEngine?.isBilateralFlipped ?: false
                    
                    binding.skeletonOverlay.updateWithStateInfos(
                        smoothedLandmarks = smoothedLandmarks,
                        inputImageWidth = result.imageWidth,
                        inputImageHeight = result.imageHeight,
                        angles = angles,
                        stateInfos = stateInfos,
                        positionErrors = positionErrors,
                        bilateralFlipped = bilateralFlipped
                    )
                }
            } finally {
                isProcessingPoseFrame = false
            }
        }
    }

    override fun onNoPoseDetected() {
        if (isWeightDialogVisible) return

        // In session mode, skip when session panels are visible
        if (isSessionMode && (
            binding.sessionPreExercisePanel.visibility == View.VISIBLE ||
            binding.sessionRestPanel.visibility == View.VISIBLE ||
            binding.sessionCompletePanel.visibility == View.VISIBLE
        )) return

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
            viewModel.onNoPoseDetected(SystemClock.uptimeMillis())
            
            // Update UI for setup pose state - show no-pose warning in the guidance container
            if (viewModel.supervisor.state.value == SessionState.SETUP_POSE) {
                viewModel.poseSetupGuide.reset()
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
            binding.tvFps.text = getString(R.string.fps_format, currentFps)
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
                        binding.glassmorphicMessage.showError(getString(R.string.error_video_playback))
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
                imageHeight: Int,
                timestampMs: Long
            ) {
                wasPoseDetectedLastFrame = true
                viewModel.onPoseFrame(
                    angles, smoothedLandmarks, false, timestampMs,
                    imageWidth = imageWidth, imageHeight = imageHeight
                )

                // Update skeleton overlay with JointStateInfo
                val stateInfos = viewModel.trainingEngine?.jointStateInfos?.value ?: emptyMap()
                val positionErrors = viewModel.trainingEngine?.positionErrors?.value ?: emptyList()
                val bilateralFlipped = viewModel.trainingEngine?.isBilateralFlipped ?: false
                
                binding.skeletonOverlay.updateWithStateInfos(
                    smoothedLandmarks = smoothedLandmarks,
                    inputImageWidth = imageWidth,
                    inputImageHeight = imageHeight,
                    angles = angles,
                    stateInfos = stateInfos,
                    positionErrors = positionErrors,
                    bilateralFlipped = bilateralFlipped
                )
            }
            
            override fun onNoPoseDetected(timestampMs: Long) {
                binding.skeletonOverlay.clear()
                // Same transition-based clearing as camera mode:
                // clear stale form feedback once when pose disappears.
                if (wasPoseDetectedLastFrame && viewModel.supervisor.state.value == SessionState.TRAINING) {
                    binding.glassmorphicMessage.hide()
                    binding.vignetteOverlay.clear()
                }
                wasPoseDetectedLastFrame = false
                viewModel.onNoPoseDetected(timestampMs)
            }
            
            override fun onResultsSaved(success: Boolean) {
                if (success) {
                    binding.glassmorphicMessage.showMotivation(getString(R.string.results_saved))
                    binding.btnSaveResults.isEnabled = false
                    binding.btnSaveResults.text = getString(R.string.btn_saved)
                } else {
                    binding.glassmorphicMessage.showError(getString(R.string.error_save_failed))
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
        sessionRestTimer?.cancel()
        viewModel.countdownController.release()
        cameraManager?.stopCamera()
        videoModeController?.release()
        poseLandmarkerHelper?.close()
        poseLandmarkerHelper?.closeVideoMode()
    }
}

