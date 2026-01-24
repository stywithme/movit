package com.trainingvalidator.poc.ui.training

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.training.TrainingEngine
import com.trainingvalidator.poc.training.models.SessionSummary
import com.trainingvalidator.poc.training.engine.HoldState
import com.trainingvalidator.poc.training.engine.Phase
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.feedback.FeedbackConfig
import com.trainingvalidator.poc.training.feedback.FeedbackEvent
import com.trainingvalidator.poc.training.feedback.FeedbackManager
import com.trainingvalidator.poc.training.loader.ExerciseLoader
import com.trainingvalidator.poc.training.loader.WorkoutLoader
// NOTE: DifficultyType has been REMOVED - quality is now assessed via JointState
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.training.models.WorkoutConfig
import com.trainingvalidator.poc.training.session.PauseReason
import com.trainingvalidator.poc.training.session.SessionState
import com.trainingvalidator.poc.training.session.SessionSupervisor
import com.trainingvalidator.poc.training.session.SupervisorAction
import com.trainingvalidator.poc.training.session.SupervisorSignal
import com.trainingvalidator.poc.training.workout.LoadedExercise
import com.trainingvalidator.poc.training.workout.SwitchResult
import com.trainingvalidator.poc.training.workout.WorkoutTrainingEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * TrainingViewModel - Central state management for training
 * 
 * Manages:
 * - Session state via SessionSupervisor (Single Source of Truth)
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
    
    // ==================== Session Supervisor (Single Source of Truth) ====================
    
    val supervisor = SessionSupervisor()
    
    // ==================== State Managers (Legacy - kept for compatibility) ====================
    
    @Deprecated("Use supervisor.state instead")
    val stateManager = TrainingStateManager()
    
    val poseValidator = PoseValidator()
    val countdownController = CountdownController()
    
    // ==================== Training Configuration ====================
    
    private val _exerciseConfig = MutableStateFlow<ExerciseConfig?>(null)
    val exerciseConfig: StateFlow<ExerciseConfig?> = _exerciseConfig.asStateFlow()
    
    private val _workoutConfig = MutableStateFlow<WorkoutConfig?>(null)
    val workoutConfig: StateFlow<WorkoutConfig?> = _workoutConfig.asStateFlow()
    
    // NOTE: difficulty has been REMOVED - all users get the same exercise
    
    private val _poseVariantIndex = MutableStateFlow(0)
    val poseVariantIndex: StateFlow<Int> = _poseVariantIndex.asStateFlow()
    
    private val _isVideoMode = MutableStateFlow(false)
    val isVideoMode: StateFlow<Boolean> = _isVideoMode.asStateFlow()
    
    private val _isWorkoutMode = MutableStateFlow(false)
    val isWorkoutMode: StateFlow<Boolean> = _isWorkoutMode.asStateFlow()
    
    // ==================== Training State ====================
    
    private val _repCount = MutableStateFlow(0)
    val repCount: StateFlow<Int> = _repCount.asStateFlow()
    
    private val _currentPhase = MutableStateFlow(Phase.IDLE)
    val currentPhase: StateFlow<Phase> = _currentPhase.asStateFlow()
    
    private val _holdElapsedMs = MutableStateFlow<Long?>(null)
    val holdElapsedMs: StateFlow<Long?> = _holdElapsedMs.asStateFlow()
    
    private val _holdState = MutableStateFlow<HoldState?>(null)
    val holdState: StateFlow<HoldState?> = _holdState.asStateFlow()
    
    private val _isCompleted = MutableStateFlow(false)
    val isCompleted: StateFlow<Boolean> = _isCompleted.asStateFlow()
    
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
    
    var workoutTrainingEngine: WorkoutTrainingEngine? = null
        private set
    
    var feedbackManager: FeedbackManager? = null
        private set
    
    // ==================== Internal State ====================
    
    private var sessionStartTime: Long = 0L
    private var currentRepsInSession = 0
    
    @Volatile
    private var isSwitchingExercise = false
    
    private var engineObserverJob: Job? = null
    private var supervisorObserverJob: Job? = null
    
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
        context: Context? = null  // Optional context for repository access
    ): Boolean {
        // Try to load from repository first (has cached/synced data)
        var config: ExerciseConfig? = null
        
        if (context != null) {
            try {
                val repository = ExerciseRepository.getInstance(context)
                config = repository.getExercise(exerciseName)
                if (config != null) {
                    Log.d(TAG, "Loaded exercise from repository: $exerciseName")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Repository not available, falling back to assets", e)
            }
        }
        
        // Fall back to bundled assets
        if (config == null) {
            config = ExerciseLoader.load(assets, exerciseName)
        }
        
        if (config == null) {
            Log.e(TAG, "Failed to load exercise: $exerciseName")
            return false
        }
        
        _exerciseConfig.value = config
        _exerciseName.value = config.name.en
        _poseVariantIndex.value = poseVariantIndex
        _isWorkoutMode.value = false
        
        // Create training engine (no difficulty needed)
        trainingEngine = TrainingEngine(
            exerciseConfig = config,
            poseVariantIndex = poseVariantIndex,
            targetRepsOverride = targetRepsOverride,
            targetDurationMsOverride = targetDurationMsOverride
        )
        
        // Configure random feedback messages (if feedback manager is ready)
        updateRandomMessagesFromEngine()
        
        // Notify supervisor that exercise is loaded
        supervisor.onExerciseLoaded()
        
        Log.d(TAG, "Loaded exercise: ${config.name.en}")
        return true
    }
    
    /**
     * Load workout for hot-swap mode
     * 
     * NOTE: difficultyStr is kept for API compatibility but is ignored.
     * 
     * @param workoutName Name of the workout to load
     * @param difficultyStr Ignored (legacy parameter)
     * @param context Optional context for repository access (enables cached/synced data)
     */
    fun loadWorkout(
        workoutName: String, 
        difficultyStr: String = "",
        context: Context? = null
    ): Boolean {
        val workoutConfig = WorkoutLoader.load(assets, workoutName)
        if (workoutConfig == null) {
            Log.e(TAG, "Failed to load workout: $workoutName")
            return false
        }
        
        _workoutConfig.value = workoutConfig
        _isWorkoutMode.value = true
        
        // Try to get repository for cached/synced exercises
        val repository = context?.let {
            try {
                ExerciseRepository.getInstance(it)
            } catch (e: Exception) {
                Log.w(TAG, "Repository not available, falling back to assets", e)
                null
            }
        }
        
        // Load all exercises (prioritize repository, fallback to assets)
        val loadedExercises = workoutConfig.exercises.mapIndexed { index, workoutExercise ->
            // Try repository first (has synced data with audio URLs)
            var exerciseConfig = repository?.getExercise(workoutExercise.exercise)
            
            // Fallback to bundled assets
            if (exerciseConfig == null) {
                exerciseConfig = ExerciseLoader.load(assets, workoutExercise.exercise)
            }
            
            if (exerciseConfig == null) {
                Log.e(TAG, "Failed to load exercise: ${workoutExercise.exercise}")
                return@mapIndexed null
            }
            
            if (repository != null) {
                Log.d(TAG, "Loaded exercise from repository: ${workoutExercise.exercise}")
            }
            
            LoadedExercise(
                config = exerciseConfig,
                workoutExercise = workoutExercise,
                round = 1,
                indexInRound = index,
                totalInRound = workoutConfig.exercises.size,
                maxRepsThisSession = null
            )
        }.filterNotNull()
        
        if (loadedExercises.isEmpty()) {
            Log.e(TAG, "No valid exercises in workout")
            return false
        }
        
        // Create workout engine
        workoutTrainingEngine = WorkoutTrainingEngine(
            exercises = loadedExercises,
            workoutConfig = workoutConfig
        )
        
        // Set first exercise
        val firstExercise = loadedExercises.first()
        _exerciseConfig.value = firstExercise.config
        _exerciseName.value = firstExercise.config.name.en
        _poseVariantIndex.value = firstExercise.workoutExercise.variantIndex
        
        // Notify supervisor
        supervisor.onExerciseLoaded()
        
        Log.d(TAG, "Loaded workout: ${workoutConfig.name.en} with ${loadedExercises.size} exercises")
        return true
    }
    
    /**
     * Initialize feedback manager
     * 
     * @param context Android context
     * @param isVideoMode Whether in video analysis mode
     * @param useRepository If true, uses ExerciseRepository for audio caching support
     */
    fun initializeFeedback(context: Context, isVideoMode: Boolean, useRepository: Boolean = true) {
        _isVideoMode.value = isVideoMode
        supervisor.isVideoMode = isVideoMode
        
        // Get language from app settings
        val language = SettingsManager.getFeedbackLanguage()
        
        feedbackManager = FeedbackManager(
            context = context,
            config = FeedbackConfig(
                enableAudio = true,
                enableHaptic = true,
                language = language
            )
        ).apply {
            this.isVideoMode = isVideoMode
        }
        
        // Initialize with audio cache if using repository
        if (useRepository) {
            try {
                val repository = ExerciseRepository.getInstance(context)
                val audioCache = repository.getAudioCache()
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
    }
    
    // ==================== Supervisor Signal Methods ====================
    
    /**
     * Called from Activity for every frame with detected pose
     */
    fun onPoseFrame(angles: JointAngles, landmarks: List<SmoothedLandmark>?, isFrontCamera: Boolean) {
        supervisor.processSignal(SupervisorSignal.PoseFrame(angles, landmarks, isFrontCamera))
    }
    
    /**
     * Called from Activity when no pose detected
     */
    fun onNoPoseDetected() {
        supervisor.processSignal(SupervisorSignal.NoPoseFrame)
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
    
    /**
     * Request stop (from UI)
     */
    fun requestStop() {
        supervisor.processSignal(SupervisorSignal.StopRequested)
    }
    
    /**
     * Video mode start request
     */
    fun requestVideoStart() {
        supervisor.processSignal(SupervisorSignal.StartRequested)
    }
    
    /**
     * Video ended
     */
    fun onVideoEnded() {
        supervisor.processSignal(SupervisorSignal.VideoEnded)
    }
    
    /**
     * Video seeked
     */
    fun onVideoSeeked() {
        supervisor.processSignal(SupervisorSignal.VideoSeeked)
    }
    
    // ==================== Action Execution ====================
    
    /**
     * Execute actions from SessionSupervisor
     */
    private fun executeAction(action: SupervisorAction) {
        when (action) {
            // Engine Commands
            is SupervisorAction.StartEngine -> {
                sessionStartTime = System.currentTimeMillis()
                feedbackManager?.resetMessageStates()
                
                if (_isWorkoutMode.value) {
                    startWorkoutTraining()
                } else {
                    trainingEngine?.start()
                    observeTrainingEngine()
                }
                
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.TrainingStarted)
                }
            }
            
            is SupervisorAction.PauseEngine -> {
                trainingEngine?.pause()
            }
            
            is SupervisorAction.ResumeEngine -> {
                trainingEngine?.resume()
            }
            
            is SupervisorAction.StopEngine -> {
                val summary = trainingEngine?.stop()
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.TrainingCompleted(summary))
                }
            }
            
            is SupervisorAction.ResumeFromVisibilityPause -> {
                trainingEngine?.resumeFromVisibilityPause()
            }
            
            is SupervisorAction.ResetEngine -> {
                trainingEngine?.stop()
                trainingEngine?.start()
            }
            
            // Frame Processing
            is SupervisorAction.ProcessFrame -> {
                trainingEngine?.processFrame(action.angles, action.landmarks, action.isFrontCamera)
            }
            
            is SupervisorAction.ValidatePose -> {
                val result = poseValidator.validate(
                    angles = action.angles,
                    exerciseConfig = _exerciseConfig.value,
                    poseVariantIndex = _poseVariantIndex.value
                )
                
                // Emit validation update for UI
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.PoseValidationUpdate(result))
                }
                
                // If pose confirmed, notify supervisor
                if (result.isConfirmed) {
                    supervisor.processSignal(SupervisorSignal.PoseConfirmed)
                } else if (!result.isValid && supervisor.state.value == SessionState.COUNTDOWN) {
                    // Pose became invalid during countdown
                    supervisor.processSignal(SupervisorSignal.PoseInvalid)
                } else if (!result.isValid && supervisor.state.value == SessionState.RESUME_COUNTDOWN) {
                    supervisor.processSignal(SupervisorSignal.PoseInvalid)
                }
            }
            
            // UI Commands
            is SupervisorAction.ShowSetupPose -> {
                poseValidator.reset()
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.ShowSetupPose)
                }
            }
            
            is SupervisorAction.StartCountdown -> {
                countdownController.start()
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.StartCountdown)
                }
            }
            
            is SupervisorAction.CancelCountdown -> {
                countdownController.cancel()
                poseValidator.reset()
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.CountdownCancelled)
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
                _isCompleted.value = true
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.ExerciseCompleted)
                }
            }
            
            // Video Commands
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
    
    @Deprecated("Use supervisor signals instead")
    fun startVideoModeTraining() {
        supervisor.isVideoMode = true
        supervisor.processSignal(SupervisorSignal.StartRequested)
    }
    
    private fun startWorkoutTraining() {
        val workoutEngine = workoutTrainingEngine ?: return
        
        trainingEngine = workoutEngine.start()
        trainingEngine?.start()
        updateRandomMessagesFromEngine()
        
        observeWorkoutTrainingEngine()
        currentRepsInSession = 0
        
        Log.d(TAG, "Workout training started: ${workoutEngine.currentExercise.value?.getDisplayName()}")
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
    fun stopTraining(): SessionSummary? {
        requestStop()
        return trainingEngine?.stop()
    }
    
    @Deprecated("Use supervisor flow instead")
    fun resumeFromVisibilityPause() {
        trainingEngine?.resumeFromVisibilityPause()
    }
    
    // ==================== Legacy Frame Processing (Deprecated) ====================
    
    @Deprecated("Use onPoseFrame() instead")
    fun processFrame(
        angles: JointAngles,
        smoothedLandmarks: List<SmoothedLandmark>?,
        isFrontCamera: Boolean
    ) {
        onPoseFrame(angles, smoothedLandmarks, isFrontCamera)
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
            
            // Observe hold state
            if (engine.isHoldExercise) {
                launch {
                    engine.holdElapsedMs.collect { elapsed ->
                        _holdElapsedMs.value = elapsed
                    }
                }
                
                launch {
                    engine.holdState.collect { state ->
                        _holdState.value = state
                    }
                }
            }
            
            // Observe completion
            launch {
                engine.isCompleted.collect { completed ->
                    if (completed && supervisor.state.value == SessionState.TRAINING) {
                        supervisor.processSignal(SupervisorSignal.TargetReached)
                    }
                }
            }
            
            // Observe feedback events and forward to supervisor
            launch {
                engine.events.collect { event ->
                    // Forward to FeedbackManager for audio/haptic
                    feedbackManager?.emit(event)
                    _feedbackEvents.emit(event)
                    
                    // Forward visibility events to supervisor
                    when (event) {
                        is FeedbackEvent.VisibilityPaused -> {
                            supervisor.processSignal(SupervisorSignal.VisibilityPaused)
                        }
                        is FeedbackEvent.VisibilityResumeCountdown -> {
                            supervisor.processSignal(SupervisorSignal.VisibilityRestored)
                        }
                        is FeedbackEvent.TargetReached -> {
                            supervisor.processSignal(SupervisorSignal.TargetReached)
                        }
                        else -> {}
                    }
                }
            }
        }
    }
    
    private fun observeWorkoutTrainingEngine() {
        engineObserverJob?.cancel()
        
        val engine = trainingEngine ?: return
        val workoutEngine = workoutTrainingEngine ?: return
        
        engineObserverJob = viewModelScope.launch {
            // Observe rep count for workout
            launch {
                engine.repCount.collect { count ->
                    val progressInfo = workoutEngine.getProgressInfo()
                    val displayCount = progressInfo.totalRepsCompleted + count
                    
                    _repCount.value = displayCount
                    _progressText.value = "$displayCount / ${progressInfo.totalRepsTarget}"
                    
                    // Check for exercise switch
                    if (count > 0 && count > currentRepsInSession && !isSwitchingExercise) {
                        currentRepsInSession = count
                        
                        val repsLimit = workoutEngine.getRepsForCurrentSession()
                        if (count >= repsLimit && supervisor.state.value == SessionState.TRAINING) {
                            isSwitchingExercise = true
                            delay(500)
                            handleWorkoutRepLimitReached(count)
                        }
                    }
                }
            }
            
            // Observe phase
            launch {
                engine.currentPhase.collect { phase ->
                    _currentPhase.value = phase
                }
            }
            
            // Observe feedback events
            launch {
                engine.events.collect { event ->
                    feedbackManager?.emit(event)
                    _feedbackEvents.emit(event)
                    
                    // Forward visibility events to supervisor
                    when (event) {
                        is FeedbackEvent.VisibilityPaused -> {
                            supervisor.processSignal(SupervisorSignal.VisibilityPaused)
                        }
                        is FeedbackEvent.VisibilityResumeCountdown -> {
                            supervisor.processSignal(SupervisorSignal.VisibilityRestored)
                        }
                        else -> {}
                    }
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
    
    private fun handleWorkoutRepLimitReached(completedReps: Int) {
        val workoutEngine = workoutTrainingEngine ?: return
        val engine = trainingEngine
        
        val correctReps = engine?.getCorrectReps() ?: completedReps
        val result = workoutEngine.onRepsCompleted(completedReps, correctReps)
        
        when (result) {
            is SwitchResult.Continue -> {}
            
            is SwitchResult.SwitchNow -> {
                performHotSwap(result.nextExerciseName, result.repsThisSession)
            }
            
            is SwitchResult.RoundComplete -> {
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.RoundCompleted(result.roundNumber, result.totalRounds))
                }
            }
            
            is SwitchResult.WorkoutComplete -> {
                completeWorkout()
            }
        }
    }
    
    private fun performHotSwap(nextExerciseName: String, repsThisSession: Int) {
        val workoutEngine = workoutTrainingEngine ?: return
        val previousName = trainingEngine?.getExerciseConfig()?.name?.en ?: ""
        
        trainingEngine?.stop()
        
        val newEngine = workoutEngine.switchToNextExercise()
        if (newEngine == null) {
            completeWorkout()
            return
        }
        
        trainingEngine = newEngine
        newEngine.start()
        updateRandomMessagesFromEngine()
        
        currentRepsInSession = 0
        isSwitchingExercise = false
        
        val currentExercise = workoutEngine.currentExercise.value
        _exerciseConfig.value = currentExercise?.config
        _exerciseName.value = currentExercise?.config?.name?.en ?: ""
        _poseVariantIndex.value = currentExercise?.workoutExercise?.variantIndex ?: 0
        
        observeWorkoutTrainingEngine()
        
        viewModelScope.launch {
            _events.emit(TrainingUIEvent.ExerciseSwitched(previousName, nextExerciseName, repsThisSession))
        }
        
        Log.d(TAG, "Hot-swapped from $previousName to $nextExerciseName")
    }
    
    private fun completeWorkout() {
        val workoutEngine = workoutTrainingEngine ?: return
        
        isSwitchingExercise = false
        
        viewModelScope.launch {
            _events.emit(TrainingUIEvent.WorkoutCompleted(
                totalReps = workoutEngine.getProgressInfo().totalRepsCompleted,
                accuracy = workoutEngine.getOverallAccuracy(),
                durationMs = System.currentTimeMillis() - sessionStartTime
            ))
        }
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
    fun getSessionDurationMs(): Long = System.currentTimeMillis() - sessionStartTime
    
    /**
     * Get workout overall accuracy
     */
    fun getWorkoutAccuracy(): Float = workoutTrainingEngine?.getOverallAccuracy() ?: 0f
    
    // ==================== Lifecycle ====================
    
    override fun onCleared() {
        super.onCleared()
        engineObserverJob?.cancel()
        supervisorObserverJob?.cancel()
        countdownController.release()
        feedbackManager?.release()
        workoutTrainingEngine?.stop()
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
    
    /** Pose validation update */
    data class PoseValidationUpdate(val result: PoseValidator.ValidationResult) : TrainingUIEvent()
    
    /** Exercise completed (target reached) */
    object ExerciseCompleted : TrainingUIEvent()
    
    /** Training completed (with summary) */
    data class TrainingCompleted(val summary: SessionSummary?) : TrainingUIEvent()
    
    /** Auto-paused (visibility or no pose) */
    data class AutoPaused(val reason: PauseReason) : TrainingUIEvent()
    
    /** No pose warning (before auto-pause) */
    data class NoPoseWarning(val elapsedMs: Long) : TrainingUIEvent()
    
    /** Exercise switched in workout mode */
    data class ExerciseSwitched(
        val fromExercise: String,
        val toExercise: String,
        val repsThisSession: Int
    ) : TrainingUIEvent()
    
    /** Round completed in workout mode */
    data class RoundCompleted(val roundNumber: Int, val totalRounds: Int) : TrainingUIEvent()
    
    /** Workout completed */
    data class WorkoutCompleted(
        val totalReps: Int,
        val accuracy: Float,
        val durationMs: Long
    ) : TrainingUIEvent()
    
    /** Pause video playback (video mode) */
    object PauseVideoPlayback : TrainingUIEvent()
    
    /** Resume video playback (video mode) */
    object ResumeVideoPlayback : TrainingUIEvent()
    
    // ==================== Legacy Events (for backward compatibility) ====================
    
    @Deprecated("Use AutoPaused instead")
    data class VisibilityPaused(val event: FeedbackEvent.VisibilityPaused) : TrainingUIEvent()
    
    @Deprecated("Use ShowSetupPose instead")
    data class VisibilityResumeStartPose(val event: FeedbackEvent.VisibilityResumeCountdown) : TrainingUIEvent()
    
    @Deprecated("Use StartCountdown instead")
    object StartVisibilityResumeCountdown : TrainingUIEvent()
}
