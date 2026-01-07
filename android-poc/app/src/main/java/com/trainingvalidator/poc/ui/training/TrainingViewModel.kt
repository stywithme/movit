package com.trainingvalidator.poc.ui.training

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
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
import com.trainingvalidator.poc.training.feedback.FeedbackConfig
import com.trainingvalidator.poc.training.feedback.FeedbackEvent
import com.trainingvalidator.poc.training.feedback.FeedbackManager
import com.trainingvalidator.poc.training.loader.ExerciseLoader
import com.trainingvalidator.poc.training.loader.WorkoutLoader
import com.trainingvalidator.poc.training.models.DifficultyType
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.WorkoutConfig
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
 * - Training state machine
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
    
    // ==================== State Managers ====================
    
    val stateManager = TrainingStateManager()
    val poseValidator = PoseValidator()
    val countdownController = CountdownController()
    
    // ==================== Training Configuration ====================
    
    private val _exerciseConfig = MutableStateFlow<ExerciseConfig?>(null)
    val exerciseConfig: StateFlow<ExerciseConfig?> = _exerciseConfig.asStateFlow()
    
    private val _workoutConfig = MutableStateFlow<WorkoutConfig?>(null)
    val workoutConfig: StateFlow<WorkoutConfig?> = _workoutConfig.asStateFlow()
    
    private val _difficulty = MutableStateFlow(DifficultyType.BEGINNER)
    val difficulty: StateFlow<DifficultyType> = _difficulty.asStateFlow()
    
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
    
    // ==================== Initialization ====================
    
    /**
     * Load single exercise
     */
    fun loadExercise(
        exerciseName: String,
        difficultyStr: String,
        poseVariantIndex: Int = 0,
        targetRepsOverride: Int? = null,
        targetDurationMsOverride: Long? = null
    ): Boolean {
        val config = ExerciseLoader.load(assets, exerciseName)
        if (config == null) {
            Log.e(TAG, "Failed to load exercise: $exerciseName")
            return false
        }
        
        _exerciseConfig.value = config
        _exerciseName.value = config.name.en
        _poseVariantIndex.value = poseVariantIndex
        _difficulty.value = parseDifficulty(difficultyStr)
        _isWorkoutMode.value = false
        
        // Create training engine
        trainingEngine = TrainingEngine(
            exerciseConfig = config,
            difficulty = _difficulty.value,
            poseVariantIndex = poseVariantIndex,
            targetRepsOverride = targetRepsOverride,
            targetDurationMsOverride = targetDurationMsOverride
        )
        
        Log.d(TAG, "Loaded exercise: ${config.name.en}")
        return true
    }
    
    /**
     * Load workout for hot-swap mode
     */
    fun loadWorkout(workoutName: String, difficultyStr: String): Boolean {
        val config = WorkoutLoader.load(assets, workoutName)
        if (config == null) {
            Log.e(TAG, "Failed to load workout: $workoutName")
            return false
        }
        
        _workoutConfig.value = config
        _difficulty.value = parseDifficulty(difficultyStr)
        _isWorkoutMode.value = true
        
        // Load all exercises
        val loadedExercises = config.exercises.mapIndexed { index, workoutExercise ->
            val exerciseConfig = ExerciseLoader.load(assets, workoutExercise.exercise)
            if (exerciseConfig == null) {
                Log.e(TAG, "Failed to load exercise: ${workoutExercise.exercise}")
                return@mapIndexed null
            }
            
            LoadedExercise(
                config = exerciseConfig,
                workoutExercise = workoutExercise,
                difficulty = workoutExercise.difficulty ?: _difficulty.value,
                round = 1,
                indexInRound = index,
                totalInRound = config.exercises.size,
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
            workoutConfig = config,
            defaultDifficulty = _difficulty.value
        )
        
        // Set first exercise
        val firstExercise = loadedExercises.first()
        _exerciseConfig.value = firstExercise.config
        _exerciseName.value = firstExercise.config.name.en
        _poseVariantIndex.value = firstExercise.workoutExercise.variantIndex
        
        Log.d(TAG, "Loaded workout: ${config.name.en} with ${loadedExercises.size} exercises")
        return true
    }
    
    /**
     * Initialize feedback manager
     */
    fun initializeFeedback(context: Context, isVideoMode: Boolean) {
        _isVideoMode.value = isVideoMode
        
        feedbackManager = FeedbackManager(
            context = context,
            config = FeedbackConfig(
                enableAudio = true,
                enableHaptic = true,
                language = "en"
            )
        ).apply {
            this.isVideoMode = isVideoMode
        }
        feedbackManager?.initialize()
    }
    
    // ==================== Training Control ====================
    
    /**
     * Start training
     */
    fun startTraining() {
        sessionStartTime = System.currentTimeMillis()
        feedbackManager?.resetMessageStates()
        
        if (_isWorkoutMode.value) {
            startWorkoutTraining()
        } else {
            trainingEngine?.start()
            observeTrainingEngine()
        }
        
        stateManager.transitionTo(TrainingStateManager.TrainingState.TRAINING)
        
        viewModelScope.launch {
            _events.emit(TrainingUIEvent.TrainingStarted)
        }
    }
    
    private fun startWorkoutTraining() {
        val workoutEngine = workoutTrainingEngine ?: return
        
        trainingEngine = workoutEngine.start()
        trainingEngine?.start()
        
        observeWorkoutTrainingEngine()
        currentRepsInSession = 0
        
        Log.d(TAG, "Workout training started: ${workoutEngine.currentExercise.value?.getDisplayName()}")
    }
    
    /**
     * Pause training
     */
    fun pauseTraining() {
        trainingEngine?.pause()
        stateManager.transitionTo(TrainingStateManager.TrainingState.PAUSED)
    }
    
    /**
     * Resume training
     */
    fun resumeTraining() {
        trainingEngine?.resume()
        stateManager.transitionTo(TrainingStateManager.TrainingState.TRAINING)
    }
    
    /**
     * Stop training and get summary
     */
    fun stopTraining(): SessionSummary? {
        val summary = trainingEngine?.stop()
        stateManager.transitionTo(TrainingStateManager.TrainingState.COMPLETED)
        
        viewModelScope.launch {
            _events.emit(TrainingUIEvent.TrainingCompleted(summary))
        }
        
        return summary
    }
    
    /**
     * Resume from visibility pause
     */
    fun resumeFromVisibilityPause() {
        trainingEngine?.resumeFromVisibilityPause()
        stateManager.transitionTo(TrainingStateManager.TrainingState.TRAINING)
    }
    
    // ==================== Frame Processing ====================
    
    /**
     * Process a pose frame
     */
    fun processFrame(
        angles: JointAngles,
        smoothedLandmarks: List<SmoothedLandmark>?,
        isFrontCamera: Boolean
    ) {
        when (stateManager.currentState) {
            TrainingStateManager.TrainingState.SETUP_POSE,
            TrainingStateManager.TrainingState.VISIBILITY_SETUP_POSE -> {
                val result = poseValidator.validate(
                    angles = angles,
                    exerciseConfig = _exerciseConfig.value,
                    poseVariantIndex = _poseVariantIndex.value
                )
                
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.PoseValidationUpdate(result))
                }
                
                if (result.isConfirmed) {
                    if (stateManager.currentState == TrainingStateManager.TrainingState.VISIBILITY_SETUP_POSE) {
                        viewModelScope.launch {
                            _events.emit(TrainingUIEvent.StartVisibilityResumeCountdown)
                        }
                    } else {
                        viewModelScope.launch {
                            _events.emit(TrainingUIEvent.StartCountdown)
                        }
                    }
                }
            }
            
            TrainingStateManager.TrainingState.COUNTDOWN -> {
                val result = poseValidator.validate(
                    angles = angles,
                    exerciseConfig = _exerciseConfig.value,
                    poseVariantIndex = _poseVariantIndex.value
                )
                
                if (!result.isValid) {
                    countdownController.cancel()
                    poseValidator.reset()
                    stateManager.transitionTo(TrainingStateManager.TrainingState.SETUP_POSE)
                    
                    viewModelScope.launch {
                        _events.emit(TrainingUIEvent.CountdownCancelled)
                    }
                }
            }
            
            TrainingStateManager.TrainingState.TRAINING -> {
                trainingEngine?.processFrame(angles, smoothedLandmarks, isFrontCamera)
            }
            
            else -> {}
        }
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
                    if (completed && stateManager.isActiveTraining()) {
                        _isCompleted.value = true
                        _events.emit(TrainingUIEvent.ExerciseCompleted)
                    }
                }
            }
            
            // Observe feedback events
            launch {
                engine.events.collect { event ->
                    feedbackManager?.emit(event)
                    _feedbackEvents.emit(event)
                    handleFeedbackEvent(event)
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
                        if (count >= repsLimit && stateManager.isActiveTraining()) {
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
                    handleFeedbackEvent(event)
                }
            }
        }
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
        stateManager.transitionTo(TrainingStateManager.TrainingState.COMPLETED)
        
        viewModelScope.launch {
            _events.emit(TrainingUIEvent.WorkoutCompleted(
                totalReps = workoutEngine.getProgressInfo().totalRepsCompleted,
                accuracy = workoutEngine.getOverallAccuracy(),
                durationMs = System.currentTimeMillis() - sessionStartTime
            ))
        }
    }
    
    private fun handleFeedbackEvent(event: FeedbackEvent) {
        when (event) {
            is FeedbackEvent.VisibilityPaused -> {
                stateManager.saveRepCountForVisibility(event.savedRepCount)
                stateManager.transitionTo(TrainingStateManager.TrainingState.VISIBILITY_PAUSED)
                
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.VisibilityPaused(event))
                }
            }
            
            is FeedbackEvent.VisibilityResumeCountdown -> {
                stateManager.saveRepCountForVisibility(event.resumeFromRep)
                stateManager.transitionTo(TrainingStateManager.TrainingState.VISIBILITY_SETUP_POSE)
                
                viewModelScope.launch {
                    _events.emit(TrainingUIEvent.VisibilityResumeStartPose(event))
                }
            }
            
            else -> {}
        }
    }
    
    // ==================== Helpers ====================
    
    private fun updateProgress(count: Int, target: Int) {
        _progressText.value = "$count / $target"
        _progressPercent.value = if (target > 0) {
            (count.toFloat() / target * 100).toInt()
        } else 0
    }
    
    private fun parseDifficulty(str: String): DifficultyType {
        return when (str.lowercase()) {
            "beginner" -> DifficultyType.BEGINNER
            "normal" -> DifficultyType.NORMAL
            "advanced" -> DifficultyType.ADVANCED
            else -> DifficultyType.BEGINNER
        }
    }
    
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
        countdownController.release()
        feedbackManager?.release()
        workoutTrainingEngine?.stop()
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
    
    /** Visibility paused - joints not visible */
    data class VisibilityPaused(val event: FeedbackEvent.VisibilityPaused) : TrainingUIEvent()
    
    /** Visibility returned - validate start pose before resume */
    data class VisibilityResumeStartPose(val event: FeedbackEvent.VisibilityResumeCountdown) : TrainingUIEvent()
    
    /** Start visibility resume countdown */
    object StartVisibilityResumeCountdown : TrainingUIEvent()
}
