package com.trainingvalidator.poc.ui.training

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trainingvalidator.poc.PoseApp
import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.training.TrainingEngine
import com.trainingvalidator.poc.training.analytics.MotionRecorder
import com.trainingvalidator.poc.training.analytics.WorkoutUpload
import com.trainingvalidator.poc.training.models.ExerciseWorkoutSummary
import com.trainingvalidator.poc.training.engine.HoldStatus
import com.trainingvalidator.poc.training.engine.Phase
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.ui.utils.feedbackLanguageCode
import com.trainingvalidator.poc.training.feedback.FeedbackConfig
import com.trainingvalidator.poc.training.feedback.FeedbackEvent
import com.trainingvalidator.poc.training.feedback.FeedbackManager
// NOTE: DifficultyType has been REMOVED - quality is now assessed via JointState
// NOTE: ExerciseLoader and WorkoutLoader removed - using repository only (no assets fallback)
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.JointRole
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.training.workout.PauseReason
import com.trainingvalidator.poc.training.workout.WorkoutRunState
import com.trainingvalidator.poc.training.workout.WorkoutRunSupervisor
import com.trainingvalidator.poc.training.workout.SupervisorAction
import com.trainingvalidator.poc.training.workout.SupervisorSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * TrainingViewModel - Central state management for training
 * 
 * Manages:
 * - Workout state via WorkoutRunSupervisor (Single Source of Truth)
 * - Exercise/Workout loading
 * - Training engine coordination
 * - UI state updates
 */
class TrainingViewModel(
    private val assets: AssetManager
) : ViewModel() {
    
    companion object {
        private const val TAG = "TrainingViewModel"
    }
    
    // ==================== Workout Supervisor (Single Source of Truth) ====================
    
    val supervisor = WorkoutRunSupervisor()
    
    /** New rolling-window guided pose validation for SETUP_POSE. */
    val poseSetupGuide = PoseSetupGuide(
        tiltSource = PoseApp.instance.tiltProvider
    )
    val countdownController = CountdownController()
    
    // ==================== Training Configuration ====================
    
    private val _exerciseConfig = MutableStateFlow<ExerciseConfig?>(null)
    val exerciseConfig: StateFlow<ExerciseConfig?> = _exerciseConfig.asStateFlow()
    
    // NOTE: difficulty has been REMOVED - all users get the same exercise
    
    private val _poseVariantIndex = MutableStateFlow(0)
    val poseVariantIndex: StateFlow<Int> = _poseVariantIndex.asStateFlow()
    
    // ==================== Training State ====================
    
    private val _repCount = MutableStateFlow(0)
    val repCount: StateFlow<Int> = _repCount.asStateFlow()
    
    private val _currentPhase = MutableStateFlow(Phase.IDLE)
    val currentPhase: StateFlow<Phase> = _currentPhase.asStateFlow()
    
    /** Engine snapshot for hold UI (null for rep exercises or before first tick). */
    private val _holdStatus = MutableStateFlow<HoldStatus?>(null)
    val holdStatus: StateFlow<HoldStatus?> = _holdStatus.asStateFlow()

    /** For debug: last [com.trainingvalidator.poc.training.engine.observability.PipelineTrace] lines. */
    fun getPipelineTraceSnapshot(): List<String> =
        trainingEngine?.pipelineTrace?.snapshot().orEmpty()
    
    // Weight for weighted exercises (kg)
    private var _weightKg: Float? = null
    private var _weightUnit: String = "kg"
    
    // Motion recorder for analytics
    private var motionRecorder: MotionRecorder? = null
    
    // ==================== UI State ====================
    
    private val _exerciseName = MutableStateFlow("")
    val exerciseName: StateFlow<String> = _exerciseName.asStateFlow()
    
    private val _progressText = MutableStateFlow("0 / 0")
    val progressText: StateFlow<String> = _progressText.asStateFlow()
    
    private val _progressPercent = MutableStateFlow(0)
    val progressPercent: StateFlow<Int> = _progressPercent.asStateFlow()
    
    // ==================== Events ====================
    
    private val _events = MutableSharedFlow<TrainingUIEvent>()
    val events: SharedFlow<TrainingUIEvent> = _events.asSharedFlow()
    
    private val _feedbackEvents = MutableSharedFlow<FeedbackEvent>()
    val feedbackEvents: SharedFlow<FeedbackEvent> = _feedbackEvents.asSharedFlow()
    
    // ==================== Engines ====================
    
    var trainingEngine: TrainingEngine? = null
        private set
    
    var feedbackManager: FeedbackManager? = null
        private set
    
    // ==================== Internal State ====================
    
    private var workoutStartTime: Long = 0L
    
    // Flag to prevent concurrent frame processing on background thread
    // This ensures frames are processed one at a time, dropping excess frames
    @Volatile
    private var isEngineProcessingFrame = false
    
    private var engineObserverJob: Job? = null
    private var supervisorObserverJob: Job? = null

    private val setupTiltOwner = "setup-pose"

    // Countdown pose issue message throttling (prevents per-frame UI spam)
    private var lastCountdownIssueTimeMs: Long = 0L
    private var lastCountdownIssueKey: String? = null
    private val countdownIssueCooldownMs: Long = 1200L
    
    // ==================== Initialization ====================
    
    init {
        // Observe supervisor actions and execute them
        supervisorObserverJob = viewModelScope.launch {
            supervisor.actions.collect { action ->
                executeAction(action)
            }
        }
    }
    
    /**
     * Load single exercise
     * 
     * Attempts to load from ExerciseRepository first (for cached/synced exercises),
     * falls back to bundled assets if not available.
     * 
     * NOTE: difficultyStr is kept for API compatibility but is ignored.
     * Quality is now assessed via JointState (PERFECT/NORMAL/PAD/WARNING/DANGER).
     */
    fun loadExercise(
        exerciseName: String,
        difficultyStr: String = "",  // Kept for API compatibility, ignored
        poseVariantIndex: Int = 0,
        targetRepsOverride: Int? = null,
        targetDurationMsOverride: Long? = null,
        context: Context? = null,  // Optional context for repository access
        weightKg: Float? = null,   // Weight for weighted exercises
        weightUnit: String = "kg"  // kg or lbs
    ): Boolean {
        // Load from repository (cached/synced data from backend)
        // No fallback to assets - repository is the single source of truth
        var config: ExerciseConfig? = null
        
        if (context != null) {
            try {
                val repository = ExerciseRepository.getInstance(context)
                config = repository.getExercise(exerciseName)
                if (config != null) {
                    Log.d(TAG, "Loaded exercise from repository: $exerciseName")
                } else {
                    Log.e(TAG, "Exercise not found in repository: $exerciseName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to access repository for exercise: $exerciseName", e)
            }
        } else {
            Log.e(TAG, "Context is required to load exercise from repository")
        }
        
        if (config == null) {
            Log.e(TAG, "Failed to load exercise: $exerciseName - not available in repository")
            return false
        }
        
        _exerciseConfig.value = config
        _exerciseName.value = config.name.en
        _poseVariantIndex.value = poseVariantIndex
        
        // Store weight settings
        _weightKg = weightKg
        _weightUnit = weightUnit
        
        // Create training engine (no difficulty needed)
        trainingEngine = TrainingEngine(
            exerciseConfig = config,
            poseVariantIndex = poseVariantIndex,
            targetRepsOverride = targetRepsOverride,
            targetDurationMsOverride = targetDurationMsOverride,
            tiltSource = PoseApp.instance.tiltProvider.takeIf {
                config.hasAnyPositionChecks(poseVariantIndex)
            }
        )
        
        // Create motion recorder for analytics
        val trackedJoints = config.getTrackedJoints(poseVariantIndex)
            .map { it.joint }
        
        val exerciseId = config.fileName.ifEmpty { exerciseName }
        
        motionRecorder = MotionRecorder(
            trackedJoints = trackedJoints,
            exerciseId = exerciseId,
            defaultWeightKg = weightKg,
            weightUnit = weightUnit
        )
        
        // Link motion recorder to training engine
        trainingEngine?.motionRecorder = motionRecorder
        
        Log.d(TAG, "MotionRecorder created for $exerciseId, weight: $weightKg $weightUnit")
        
        // Configure random feedback messages (if feedback manager is ready)
        updateRandomMessagesFromEngine()
        
        // Notify supervisor that exercise is loaded
        supervisor.onExerciseLoaded()
        
        Log.d(TAG, "Loaded exercise: ${config.name.en}")
        return true
    }
    
    /**
     * Initialize feedback manager
     * 
     * @param context Android context
     * @param useRepository If true, uses ExerciseRepository for audio caching support
     */
    fun initializeFeedback(context: Context, useRepository: Boolean = true) {
        // Align with app UI language (Profile / AppCompatDelegate), not app_settings.json alone
        val language = context.feedbackLanguageCode()
        poseSetupGuide.language = language
        
        feedbackManager = FeedbackManager(
            context = context,
            config = FeedbackConfig(
                enableAudio = true,
                enableHaptic = true,
                language = language
            )
        )
        
        // Initialize with audio cache if using repository
        if (useRepository) {
            try {
                val repository = ExerciseRepository.getInstance(context)
                val audioCache = repository.getAudioCache()
                audioCache.rescanCache()
                audioCache.logDiagnosticSummary()
                feedbackManager?.initializeWithAudioCache(audioCache)
                Log.d(TAG, "Feedback initialized with audio cache support")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize audio cache, falling back to TTS", e)
                feedbackManager?.initialize()
            }
        } else {
            feedbackManager?.initialize()
        }
        
        // Configure random messages if engine already exists
        updateRandomMessagesFromEngine()
        
        // Diagnostic: log audio URL availability in the current exercise
        logExerciseAudioDiagnostic()

        countdownController.audioProvider = object : CountdownController.CountdownAudioProvider {
            override suspend fun playPoseConfirmed() {
                feedbackManager?.speakPoseConfirmedAndAwait() ?: delay(1200)
            }

            override suspend fun playCountdownNumber(secondsRemaining: Int) {
                feedbackManager?.speakCountdownAndAwait(secondsRemaining) ?: delay(850)
            }

            override suspend fun playGo() {
                feedbackManager?.speakGo()
            }
        }
    }
    
    // ==================== Supervisor Signal Methods ====================

    // Cached last frame data - used by TrainingActivity to drive skeleton setup guidance
    @Volatile var lastSmoothedLandmarks: List<SmoothedLandmark>? = null
        private set
    @Volatile var lastImageSize: Pair<Int, Int> = Pair(1, 1)
        private set

    /**
     * Called from Activity for every frame with detected pose
     */
    fun onPoseFrame(
        angles: JointAngles,
        landmarks: List<SmoothedLandmark>?,
        isFrontCamera: Boolean,
        timestampMs: Long,
        imageWidth: Int = 1,
        imageHeight: Int = 1
    ) {
        lastSmoothedLandmarks = landmarks
        if (imageWidth > 1) lastImageSize = Pair(imageWidth, imageHeight)
        supervisor.processSignal(SupervisorSignal.PoseFrame(angles, landmarks, isFrontCamera, timestampMs))
    }
    
    /**
     * Called from Activity when no pose detected
     */
    fun onNoPoseDetected(timestampMs: Long) {
        supervisor.processSignal(SupervisorSignal.NoPoseFrame(timestampMs))
    }
    
    /**
     * Called when countdown finishes
     */
    fun onCountdownFinished() {
        supervisor.processSignal(SupervisorSignal.CountdownFinished)
    }
    
    /**
     * Request pause (from UI)
     */
    fun requestPause() {
        supervisor.processSignal(SupervisorSignal.PauseRequested)
    }
    
    /**
     * Request resume (from UI)
     */
    fun requestResume() {
        supervisor.processSignal(SupervisorSignal.ResumeRequested)
    }

    fun handleActivityPause() {
        supervisor.processSignal(SupervisorSignal.ActivityPaused)
        releaseSetupTiltCorrection()
    }

    fun requestVideoStart() {
        supervisor.processSignal(SupervisorSignal.StartRequested)
    }

    fun onVideoEnded() {
        supervisor.processSignal(SupervisorSignal.VideoEnded)
    }

    fun onVideoSeeked() {
        supervisor.processSignal(SupervisorSignal.VideoSeeked)
    }

    fun handleActivityResume() {
        supervisor.processSignal(SupervisorSignal.ActivityResumed)
        if (supervisor.state.value.shouldValidatePose()) {
            acquireSetupTiltCorrection()
        }
    }
    
    /**
     * Request stop (from UI)
     */
    fun requestStop() {
        supervisor.processSignal(SupervisorSignal.StopRequested)
    }
    
    // ==================== Action Execution ====================
    
    /**
     * Execute actions from WorkoutRunSupervisor
     */
    private fun executeAction(action: SupervisorAction) {
        when (action) {
            // Engine Commands
            is SupervisorAction.StartEngine -> {
                releaseSetupTiltCorrection()
                workoutStartTime = System.currentTimeMillis()
                feedbackManager?.resetMessageStates()
                isEngineProcessingFrame = false  // Reset frame processing flag
                
                trainingEngine?.start()
                observeTrainingEngine()
                
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.TrainingStarted)
                }
            }
            
            is SupervisorAction.PauseEngine -> {
                trainingEngine?.pause()
            }
            
            is SupervisorAction.ResumeEngine -> {
                releaseSetupTiltCorrection()
                trainingEngine?.resume()
            }
            
            is SupervisorAction.StopEngine -> {
                releaseSetupTiltCorrection()
                val summary = trainingEngine?.stop()
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.TrainingCompleted(summary))
                }
            }
            
            is SupervisorAction.ResumeFromVisibilityPause -> {
                releaseSetupTiltCorrection()
                trainingEngine?.resume()
            }

            is SupervisorAction.ResetEngine -> {
                releaseSetupTiltCorrection()
                trainingEngine?.stop()
                trainingEngine?.start()
            }
            
            // Frame Processing - Run on background thread to keep UI responsive
            is SupervisorAction.ProcessFrame -> {
                // Only process if previous frame is done (drop frames to prevent accumulation)
                if (!isEngineProcessingFrame) {
                    isEngineProcessingFrame = true
                    
                    viewModelScope.launch(Dispatchers.Default) {
                        try {
                            trainingEngine?.processFrame(
                                action.angles,
                                action.landmarks,
                                action.isFrontCamera,
                                action.timestampMs
                            )
                        } finally {
                            isEngineProcessingFrame = false
                        }
                    }
                }
                // else: frame dropped - keeps skeleton tracking smooth
            }
            
            is SupervisorAction.ValidatePose -> {
                val state = supervisor.state.value

                when (state) {
                    WorkoutRunState.SETUP_POSE, WorkoutRunState.RESUME_SETUP -> {
                        val setupResult = poseSetupGuide.validate(
                            angles = action.angles,
                            landmarks = action.landmarks,
                            exerciseConfig = _exerciseConfig.value,
                            poseVariantIndex = _poseVariantIndex.value,
                            isFrontCamera = action.isFrontCamera
                        )

                        viewModelScope.launch {
                            _events.emit(TrainingUIEvent.SetupGuidanceUpdate(setupResult))
                        }

                        if (setupResult.isConfirmed) {
                            supervisor.processSignal(SupervisorSignal.PoseConfirmed)
                        }
                    }

                    WorkoutRunState.COUNTDOWN, WorkoutRunState.RESUME_COUNTDOWN -> {
                        // During countdown we re-check scene (region/posture/direction)
                        // and start pose to prevent starting from a drifted camera position.
                        val setupResult = poseSetupGuide.validate(
                            angles = action.angles,
                            landmarks = action.landmarks,
                            exerciseConfig = _exerciseConfig.value,
                            poseVariantIndex = _poseVariantIndex.value,
                            isFrontCamera = action.isFrontCamera
                        )
                        val hasSceneData = (action.landmarks?.size ?: 0) >= 33
                        val sceneStillValid = hasSceneData && setupResult.phase == SetupPhase.ANGLES
                        val startPoseStillValid = isStartPoseRoughlyValid(action.angles)
                        val stillValid = sceneStillValid && startPoseStillValid

                        if (!stillValid) {
                            if (shouldEmitCountdownPoseIssue(setupResult)) {
                                viewModelScope.launch {
                                    _events.emit(TrainingUIEvent.CountdownPoseIssue(setupResult))
                                }
                            }
                            supervisor.processSignal(SupervisorSignal.PoseInvalid)
                        } else {
                            resetCountdownPoseIssueThrottle()
                        }
                    }

                    else -> { /* TRAINING, COMPLETED etc. — ignore */ }
                }
            }
            
            // UI Commands
            is SupervisorAction.ShowSetupPose -> {
                acquireSetupTiltCorrection()
                resetCountdownPoseIssueThrottle()
                poseSetupGuide.reset()
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.ShowSetupPose)
                }
            }

            is SupervisorAction.StartCountdown -> {
                acquireSetupTiltCorrection()
                resetCountdownPoseIssueThrottle()
                countdownController.start(viewModelScope)
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.StartCountdown)
                }
            }

            is SupervisorAction.CancelCountdown -> {
                acquireSetupTiltCorrection()
                resetCountdownPoseIssueThrottle()
                feedbackManager?.abortCountdownAudio()
                countdownController.cancel()
                poseSetupGuide.reset()
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.CountdownCancelled)
                }
            }

            is SupervisorAction.FreezeCountdown -> {
                feedbackManager?.abortCountdownAudio()
                countdownController.freeze()
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.CountdownFrozen)
                }
            }

            is SupervisorAction.UnfreezeCountdown -> {
                countdownController.unfreeze()
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.CountdownUnfrozen)
                }
            }
            
            is SupervisorAction.ShowAutoPaused -> {
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.AutoPaused(action.reason))
                }
            }
            
            is SupervisorAction.ShowNoPoseWarning -> {
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.NoPoseWarning(action.elapsedMs))
                }
            }
            
            is SupervisorAction.ShowCompleted -> {
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.ExerciseCompleted)
                }
            }

            is SupervisorAction.PauseVideo -> {
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.PauseVideoPlayback)
                }
            }

            is SupervisorAction.ResumeVideo -> {
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.ResumeVideoPlayback)
                }
            }
        }
    }
    
    // ==================== Legacy Training Control (Deprecated) ====================
    
    @Deprecated("Use supervisor signals instead")
    fun startTraining() {
        supervisor.processSignal(SupervisorSignal.StartRequested)
    }
    
    @Deprecated("Use requestPause() instead")
    fun pauseTraining() {
        requestPause()
    }
    
    @Deprecated("Use requestResume() instead")
    fun resumeTraining() {
        requestResume()
    }
    
    @Deprecated("Use requestStop() instead")
    fun stopTraining(): ExerciseWorkoutSummary? {
        requestStop()
        return trainingEngine?.stop()
    }
    
    // ==================== Legacy Frame Processing (Deprecated) ====================
    
    @Deprecated("Use onPoseFrame() instead")
    fun processFrame(
        angles: JointAngles,
        smoothedLandmarks: List<SmoothedLandmark>?,
        isFrontCamera: Boolean
    ) {
        onPoseFrame(angles, smoothedLandmarks, isFrontCamera, SystemClock.uptimeMillis())
    }
    
    // ==================== Countdown Pose Check ====================

    /**
     * Lightweight check: are ALL tracked joints still roughly inside startPose range?
     * Uses a generous tolerance (closeThreshold from settings) so only gross violations
     * trigger PoseInvalid during the countdown.
     */
    private fun isStartPoseRoughlyValid(angles: JointAngles): Boolean {
        val config = _exerciseConfig.value ?: return true
        val variant = config.poseVariants.getOrNull(_poseVariantIndex.value) ?: return true
        val tolerance = com.trainingvalidator.poc.training.config.SettingsManager
            .settings.setupValidation.closeThresholdDegrees.coerceAtLeast(10.0)

        val totalTracked = variant.trackedJoints.size
        if (totalTracked == 0) return true

        val primaryJointCodes = variant.trackedJoints
            .filter { it.role == JointRole.PRIMARY }
            .map { it.joint }
            .toSet()

        // Require a reasonable portion of tracked joints to stay visible during countdown.
        val minVisibleJoints = if (totalTracked == 1) 1 else {
            ceil(totalTracked * 0.6).toInt().coerceAtLeast(2)
        }

        var checkedCount = 0
        var visiblePrimaryCount = 0
        for (joint in variant.trackedJoints) {
            val angle = angles.getAngle(joint.joint) ?: continue  // skip invisible joints
            checkedCount++

            if (joint.joint in primaryJointCodes) {
                visiblePrimaryCount++
            }

            val min = joint.startPose.min - tolerance
            val max = joint.startPose.max + tolerance
            if (angle < min || angle > max) return false
        }

        if (primaryJointCodes.isNotEmpty() && visiblePrimaryCount < primaryJointCodes.size) {
            return false
        }

        return checkedCount >= minVisibleJoints
    }

    private fun shouldEmitCountdownPoseIssue(result: SetupResult): Boolean {
        val key = when {
            result.phase != SetupPhase.ANGLES -> "phase:${result.phase.name}"
            result.worstJoint != null -> "joint:${result.worstJoint.jointCode}:${result.worstJoint.level.name}"
            else -> "countdown:generic"
        }

        val now = SystemClock.uptimeMillis()
        val sameKey = key == lastCountdownIssueKey
        val cooldownPassed = (now - lastCountdownIssueTimeMs) >= countdownIssueCooldownMs

        return if (!sameKey || cooldownPassed) {
            lastCountdownIssueKey = key
            lastCountdownIssueTimeMs = now
            true
        } else {
            false
        }
    }

    private fun resetCountdownPoseIssueThrottle() {
        lastCountdownIssueTimeMs = 0L
        lastCountdownIssueKey = null
    }

    // ==================== Engine Observers ====================
    
    private fun observeTrainingEngine() {
        engineObserverJob?.cancel()
        
        val engine = trainingEngine ?: return
        
        engineObserverJob = viewModelScope.launch {
            // Observe rep count
            launch {
                engine.repCount.collect { count ->
                    _repCount.value = count
                    updateProgress(count, engine.getTargetReps())
                }
            }
            
            // Observe phase
            launch {
                engine.currentPhase.collect { phase ->
                    _currentPhase.value = phase
                }
            }
            
            // Observe hold snapshot
            if (engine.isHoldExercise) {
                launch {
                    engine.holdStatus.collect { s -> _holdStatus.value = s }
                }
            } else {
                _holdStatus.value = null
            }
            
            // Observe completion
            launch {
                engine.isCompleted.collect { completed ->
                    if (completed && supervisor.state.value == WorkoutRunState.TRAINING) {
                        supervisor.processSignal(SupervisorSignal.TargetReached)
                    }
                }
            }
            
            // Observe feedback events (audio/haptic/UI only — no supervisor signals here)
            launch {
                engine.events.collect { event ->
                    feedbackManager?.emit(event)
                    _feedbackEvents.emit(event)
                }
            }
        }
    }
    
    /**
     * Configure random feedback messages from the current engine
     * Called when engine or feedback manager is initialized.
     */
    private fun updateRandomMessagesFromEngine() {
        val engine = trainingEngine ?: return
        feedbackManager?.setRandomMessages(engine.feedbackMessages)
    }
    
    /**
     * Log a diagnostic breakdown of audio URL availability for the loaded exercise.
     * Helps pinpoint whether TTS fallback is caused by missing URLs vs missing cache files.
     */
    private fun logExerciseAudioDiagnostic() {
        val config = _exerciseConfig.value ?: return
        val lang = feedbackManager?.feedbackLanguage ?: "en"
        val variant = config.poseVariants.getOrNull(_poseVariantIndex.value) ?: return

        var withAudio = 0
        var withoutAudio = 0

        fun checkText(lt: com.trainingvalidator.poc.training.models.LocalizedText) {
            if (lt.hasAudio(lang)) withAudio++ else withoutAudio++
        }

        // State messages
        for (joint in variant.trackedJoints) {
            val sm = joint.stateMessages ?: continue
            for (state in listOf(
                com.trainingvalidator.poc.training.models.JointState.PERFECT,
                com.trainingvalidator.poc.training.models.JointState.NORMAL,
                com.trainingvalidator.poc.training.models.JointState.PAD,
                com.trainingvalidator.poc.training.models.JointState.WARNING,
                com.trainingvalidator.poc.training.models.JointState.DANGER
            )) {
                sm.getMessage(state)?.let { checkText(it) }
            }
        }

        // Position checks
        for (pc in variant.positionChecks) {
            checkText(pc.errorMessage)
        }

        // Feedback messages
        val fm = variant.feedbackMessages
        fm.motivational.forEach { checkText(it) }
        fm.tips.forEach { checkText(it) }

        Log.i("AUDIO_TRACE", "──── EXERCISE AUDIO DIAGNOSTIC ($lang) ────")
        Log.i(
            "AUDIO_TRACE",
            "[LOADED] exercise=${config.name.en.ifBlank { config.name.ar }} variant=${variant.name.en.ifBlank { variant.name.ar }}"
        )
        Log.i("AUDIO_TRACE", "[LOADED] withAudioUrl=$withAudio, withoutAudioUrl=$withoutAudio, total=${withAudio + withoutAudio}")
        Log.i("AUDIO_TRACE", "[LOADED] assignments=${variant.messageAssignments.size}")
        if (withAudio == 0 && (withAudio + withoutAudio) > 0) {
            Log.w("AUDIO_TRACE", "⚠ ZERO messages have audio URLs — ALL will fall back to TTS!")
        }
        // Sample first state message to check structure
        variant.trackedJoints.firstOrNull()?.stateMessages?.let { sm ->
            val sample = sm.getMessage(com.trainingvalidator.poc.training.models.JointState.WARNING)
            Log.d("AUDIO_TRACE", "[LOADED] sample stateMsg: ar=${sample?.ar?.take(20)} audioAr=${sample?.audioAr?.takeLast(25)}")
        }
        Log.i("AUDIO_TRACE", "──────────────────────────────────────────")
    }
    
    // ==================== Helpers ====================
    
    private fun updateProgress(count: Int, target: Int) {
        _progressText.value = "$count / $target"
        _progressPercent.value = if (target > 0) {
            (count.toFloat() / target * 100).toInt()
        } else 0
    }
    
    // NOTE: parseDifficulty has been REMOVED - difficulty levels are no longer used
    // Quality is now assessed via JointState (PERFECT/NORMAL/PAD/WARNING/DANGER)
    
    /**
     * Get tracked landmark indices for skeleton overlay
     */
    fun getTrackedLandmarkIndices(): Set<Int> {
        return trainingEngine?.getTrackedLandmarkIndices()?.toSet() ?: emptySet()
    }
    
    /**
     * Get target reps
     */
    fun getTargetReps(): Int = trainingEngine?.getTargetReps() ?: 0
    
    /**
     * Get target duration for hold exercises
     */
    fun getTargetDurationMs(): Long = trainingEngine?.getTargetDurationMs() ?: 0L
    
    /**
     * Check if this is a hold exercise
     */
    fun isHoldExercise(): Boolean = trainingEngine?.isHoldExercise ?: false
    
    /**
     * Get session duration
     */
    fun getWorkoutDurationMs(): Long = System.currentTimeMillis() - workoutStartTime
    
    // ==================== Analytics ====================
    
    /**
     * Finalize motion recording and get session upload data
     * Call this after training completes to get metrics for sync
     * 
     * @param workoutId Optional workout execution ID (generated if null)
     * @return WorkoutUpload ready for backend sync, or null if no recording
     */
    fun finalizeAndGetWorkoutUpload(workoutId: String? = null): WorkoutUpload? {
        return try {
            val upload = motionRecorder?.finalize(workoutId)
            Log.d(TAG, "Workout finalized: ${upload?.totalReps} reps, ${upload?.executionMetrics?.avgFormScore?.div(10f)}% avg score")
            upload
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalize workout execution: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get current weight configuration
     */
    fun getWeightKg(): Float? = _weightKg
    fun getWeightUnit(): String = _weightUnit
    
    /**
     * Update weight (can be changed during training)
     */
    fun setWeight(weightKg: Float?, unit: String = "kg") {
        _weightKg = weightKg
        _weightUnit = unit
        // Note: MotionRecorder uses default weight, individual reps can have different weights
    }

    /**
     * Update weight for this session and refresh MotionRecorder defaults.
     */
    fun updateWorkoutWeight(weightKg: Float?, unit: String = "kg") {
        _weightKg = weightKg
        _weightUnit = unit

        val config = _exerciseConfig.value ?: return
        val exerciseId = config.fileName.ifEmpty { config.name.en }
        val trackedJoints = config.getTrackedJoints(_poseVariantIndex.value).map { it.joint }

        motionRecorder = MotionRecorder(
            trackedJoints = trackedJoints,
            exerciseId = exerciseId,
            defaultWeightKg = weightKg,
            weightUnit = unit
        )

        trainingEngine?.motionRecorder = motionRecorder
    }

    /**
     * Recreate [TrainingEngine] and [MotionRecorder] with new target/weight overrides
     * (e.g. after pre-training dialog). Call only before training has started.
     */
    fun rebuildTrainingEngineWithOverrides(
        targetRepsOverride: Int?,
        targetDurationMsOverride: Long?,
        weightKg: Float?,
        weightUnit: String = "kg"
    ) {
        val config = _exerciseConfig.value ?: return
        val poseVariantIndex = _poseVariantIndex.value
        engineObserverJob?.cancel()
        trainingEngine?.stop()
        _weightKg = weightKg
        _weightUnit = weightUnit
        trainingEngine = TrainingEngine(
            exerciseConfig = config,
            poseVariantIndex = poseVariantIndex,
            targetRepsOverride = targetRepsOverride,
            targetDurationMsOverride = targetDurationMsOverride,
            tiltSource = PoseApp.instance.tiltProvider.takeIf {
                config.hasAnyPositionChecks(poseVariantIndex)
            }
        )
        val exerciseId = config.fileName.ifEmpty { config.name.en }
        val trackedJoints = config.getTrackedJoints(poseVariantIndex).map { it.joint }
        motionRecorder = MotionRecorder(
            trackedJoints = trackedJoints,
            exerciseId = exerciseId,
            defaultWeightKg = weightKg,
            weightUnit = weightUnit
        )
        trainingEngine?.motionRecorder = motionRecorder
        updateRandomMessagesFromEngine()
        Log.d(TAG, "Rebuilt engine: reps=$targetRepsOverride durationMs=$targetDurationMsOverride weight=$weightKg")
    }

    private fun acquireSetupTiltCorrection() {
        PoseApp.instance.tiltProvider.acquire(setupTiltOwner)
    }

    private fun releaseSetupTiltCorrection() {
        PoseApp.instance.tiltProvider.release(setupTiltOwner)
    }
    
    // ==================== Lifecycle ====================
    
    override fun onCleared() {
        super.onCleared()
        engineObserverJob?.cancel()
        supervisorObserverJob?.cancel()
        countdownController.release()
        feedbackManager?.release()
        releaseSetupTiltCorrection()
        trainingEngine?.stop()
        supervisor.reset()
    }
    
    // ==================== Factory ====================
    
    class Factory(private val assets: AssetManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TrainingViewModel::class.java)) {
                return TrainingViewModel(assets) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

/**
 * UI Events emitted by TrainingViewModel
 */
sealed class TrainingUIEvent {
    /** Training has started */
    object TrainingStarted : TrainingUIEvent()
    
    /** Show setup pose panel */
    object ShowSetupPose : TrainingUIEvent()
    
    /** Start countdown timer */
    object StartCountdown : TrainingUIEvent()
    
    /** Countdown was cancelled (pose lost) */
    object CountdownCancelled : TrainingUIEvent()

    /** Countdown was frozen (user temporarily left position) */
    object CountdownFrozen : TrainingUIEvent()

    /** Specific countdown guidance when pose drifts (phase/joint reason). */
    data class CountdownPoseIssue(val result: SetupResult) : TrainingUIEvent()

    /** Countdown was unfrozen (user returned to position) */
    object CountdownUnfrozen : TrainingUIEvent()
    
    /** Setup guidance update from PoseSetupGuide (new rolling-window system) */
    data class SetupGuidanceUpdate(val result: SetupResult) : TrainingUIEvent()
    
    /** Exercise completed (target reached) */
    object ExerciseCompleted : TrainingUIEvent()
    
    /** Training completed (with summary) */
    data class TrainingCompleted(val summary: ExerciseWorkoutSummary?) : TrainingUIEvent()
    
    /** Auto-paused (visibility or no pose) */
    data class AutoPaused(val reason: PauseReason) : TrainingUIEvent()
    
    /** No pose warning (before auto-pause) */
    data class NoPoseWarning(val elapsedMs: Long) : TrainingUIEvent()

    /** Pause video playback (video mode) */
    object PauseVideoPlayback : TrainingUIEvent()

    /** Resume video playback (video mode) */
    object ResumeVideoPlayback : TrainingUIEvent()
}
