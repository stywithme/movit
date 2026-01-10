package com.trainingvalidator.poc.training

import android.util.Log
import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.training.engine.*
import com.trainingvalidator.poc.training.feedback.FeedbackEvent
import com.trainingvalidator.poc.training.models.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * TrainingEngine - The main orchestrator for exercise training
 * 
 * This is the "brain" of the training system. It coordinates:
 * - JointAngleTracker: Extracts relevant joint angles
 * - PhaseStateMachine: Tracks exercise phases
 * - FormValidator: Validates form and detects errors
 * - RepCounter: Counts repetitions
 * 
 * Usage:
 * 1. Create instance with exercise config and difficulty
 * 2. Call processFrame() for each camera frame
 * 3. Observe state flows for UI updates
 */
class TrainingEngine(
    private val exerciseConfig: ExerciseConfig,
    private val difficulty: DifficultyType,
    private val poseVariantIndex: Int = 0,
    /**
     * Override target reps from workout config (null = use exercise default)
     * Used in Workout Mode to apply WorkoutExercise.target.reps
     */
    private val targetRepsOverride: Int? = null,
    /**
     * Override target duration from workout config in milliseconds (null = use exercise default)
     * Used in Workout Mode to apply WorkoutExercise.target.durationSec
     */
    private val targetDurationMsOverride: Long? = null
) {
    
    companion object {
        private const val TAG = "TrainingEngine"
    }
    
    // ==================== Configuration ====================
    
    private val poseVariant: PoseVariant = exerciseConfig.poseVariants[poseVariantIndex]
    private val difficultyLevel: DifficultyLevel = poseVariant.difficultyLevels
        .find { it.level == difficulty }
        ?: throw IllegalArgumentException("Difficulty level not found: $difficulty")
    
    private val trackedJoints: List<TrackedJoint> = poseVariant.trackedJoints
    private val primaryJoints: List<TrackedJoint> = poseVariant.getPrimaryJoints()
    
    /**
     * Effective target reps: override takes precedence, then exercise config
     */
    private val targetReps: Int = targetRepsOverride 
        ?: difficultyLevel.repCountingConfig.reps
    
    // ==================== Components ====================
    
    private val jointTracker = JointAngleTracker(trackedJoints)
    
    /**
     * Centralized angle smoother - Single Source of Truth for smoothed angles
     * All components use smoothed angles from here for consistency
     */
    private val angleSmoother = AngleSmoother()
    
    private val stateMachine = PhaseStateMachine(
        countingMethod = exerciseConfig.countingMethod,
        primaryJoints = primaryJoints,
        difficulty = difficulty,
        repCountingConfig = difficultyLevel.repCountingConfig,
        numberOfPhases = difficultyLevel.phases.size
    )
    
    private val formValidator = FormValidator(
        trackedJoints = trackedJoints,
        difficulty = difficulty
    )
    
    private val repCounter = RepCounter(
        targetReps = targetReps,
        repCountingConfig = difficultyLevel.repCountingConfig
    )

    /**
     * Per-check cooldown for emitting feedback events (visual overlay stays active, but events are throttled).
     * Note: positionChecks may be null from Gson parsing even with default value
     */
    @Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
    private val positionChecksById: Map<String, PositionCheck> =
        (poseVariant.positionChecks ?: emptyList()).associateBy { it.id }

    private val lastPositionEventTimes = mutableMapOf<String, Long>()
    
    // NOTE: Camera warning throttling is now handled exclusively by MessageOrchestrator
    // in FeedbackManager for consistent message management
    
    // ==================== Position Validator ====================
    
    /**
     * Position validator for position-based checks (knee-over-toe, alignment, etc.)
     * Null if no position checks are configured
     */
    @Suppress("UNNECESSARY_SAFE_CALL")
    private val positionValidator: PositionValidator? = 
        poseVariant.positionChecks?.takeIf { it.isNotEmpty() }?.let {
            PositionValidator(
                positionChecks = it,
                expectedCameraPosition = poseVariant.cameraPosition,
                expectedFacingDirection = poseVariant.expectedFacingDirection
            )
        }
    
    // ==================== Visibility Monitor ====================
    
    /**
     * Visibility monitor for tracking required joints visibility
     * Handles auto-pause when joints become invisible and resume when visible again
     */
    private val visibilityMonitor: VisibilityMonitor = VisibilityMonitor(
        requiredJoints = poseVariant.trackedJoints
            .filter { it.role == JointRole.PRIMARY }
            .map { it.joint },
        minVisibility = 0.3f,       // Lowered from 0.5f - more tolerant
        graceDurationMs = 1000,     // 1s - ignore brief glitches (was 0.5s)
        warningDurationMs = 2000,   // 2s - show warning (was 1.5s)
        pauseAfterMs = 4000         // 4s - pause training (was 3s)
    )
    
    // ==================== Hold Timer (for HOLD exercises only) ====================
    
    /**
     * Check if this is a hold exercise
     */
    val isHoldExercise: Boolean = exerciseConfig.countingMethod == CountingMethod.HOLD
    
    /**
     * Target duration for hold exercises (null for rep-based)
     */
    /**
     * Effective target duration for hold exercises: override takes precedence, then exercise config
     */
    val targetDurationMs: Long? = if (isHoldExercise) {
        targetDurationMsOverride 
            ?: difficultyLevel.repCountingConfig.getDurationMs(
                SettingsManager.getDefaultHoldDuration()
            )
    } else null
    
    /**
     * Hold timer instance (null for rep-based exercises)
     * Uses safe null-check: holdTimer exists only if targetDurationMs is not null
     */
    private val holdTimer: HoldTimer? = targetDurationMs?.let { duration ->
        HoldTimer(
            targetDurationMs = duration,
            gracePeriodMs = difficultyLevel.repCountingConfig.getGracePeriod(
                SettingsManager.getDefaultGracePeriod()
            )
        )
    }
    
    // ==================== Hold Form Quality Tracking ====================
    
    /**
     * Track form errors during hold exercises
     * These are used to calculate form quality (percentage of time with correct form)
     */
    private var holdErrorFrameCount: Int = 0
    private var holdTotalFrameCount: Int = 0
    private val holdJointErrors = mutableMapOf<String, Int>() // joint -> error count
    
    // ==================== State Flows ====================
    
    private val _currentPhase = MutableStateFlow(Phase.IDLE)
    val currentPhase: StateFlow<Phase> = _currentPhase
    
    private val _repCount = MutableStateFlow(0)
    val repCount: StateFlow<Int> = _repCount
    
    private val _jointStatuses = MutableStateFlow<Map<String, JointStatus>>(emptyMap())
    val jointStatuses: StateFlow<Map<String, JointStatus>> = _jointStatuses
    
    private val _arrowInfos = MutableStateFlow<Map<String, JointArrowInfo>>(emptyMap())
    val arrowInfos: StateFlow<Map<String, JointArrowInfo>> = _arrowInfos
    
    private val _isInStartPosition = MutableStateFlow(false)
    val isInStartPosition: StateFlow<Boolean> = _isInStartPosition
    
    private val _isCompleted = MutableStateFlow(false)
    val isCompleted: StateFlow<Boolean> = _isCompleted
    
    private val _currentAngles = MutableStateFlow<Map<String, Double>>(emptyMap())
    val currentAngles: StateFlow<Map<String, Double>> = _currentAngles
    
    // ==================== Position Validation State Flows ====================
    
    /**
     * Position errors from PositionValidator (severity: ERROR + WARNING)
     */
    private val _positionErrors = MutableStateFlow<List<PositionError>>(emptyList())
    val positionErrors: StateFlow<List<PositionError>> = _positionErrors
    
    /**
     * Camera position warning (if detected camera doesn't match expected)
     */
    private val _cameraWarning = MutableStateFlow<CameraPositionWarning?>(null)
    val cameraWarning: StateFlow<CameraPositionWarning?> = _cameraWarning
    
    // ==================== Visibility State Flows ====================
    
    /**
     * Current visibility state
     */
    private val _visibilityState = MutableStateFlow(VisibilityState.VISIBLE)
    val visibilityState: StateFlow<VisibilityState> = _visibilityState
    
    /**
     * Whether training is paused due to visibility (not manual pause)
     */
    private val _isVisibilityPaused = MutableStateFlow(false)
    val isVisibilityPaused: StateFlow<Boolean> = _isVisibilityPaused
    
    // ==================== Hold-specific State Flows ====================
    
    /**
     * Current hold state (null for rep-based exercises)
     */
    private val _holdState = MutableStateFlow<HoldState?>(null)
    val holdState: StateFlow<HoldState?> = _holdState
    
    /**
     * Elapsed hold time in milliseconds (null for rep-based exercises)
     */
    private val _holdElapsedMs = MutableStateFlow<Long?>(null)
    val holdElapsedMs: StateFlow<Long?> = _holdElapsedMs
    
    /**
     * Remaining hold time in milliseconds (null for rep-based exercises)
     */
    private val _holdRemainingMs = MutableStateFlow<Long?>(null)
    val holdRemainingMs: StateFlow<Long?> = _holdRemainingMs
    
    /**
     * Hold progress (0.0 - 1.0, null for rep-based exercises)
     */
    private val _holdProgress = MutableStateFlow<Float?>(null)
    val holdProgress: StateFlow<Float?> = _holdProgress
    
    /**
     * Grace period remaining in milliseconds (null when not in grace period)
     */
    private val _graceRemainingMs = MutableStateFlow<Long?>(null)
    val graceRemainingMs: StateFlow<Long?> = _graceRemainingMs
    
    /**
     * Form quality during hold (0.0 - 1.0, null for rep-based exercises)
     * Represents percentage of time with correct form
     */
    private val _holdFormQuality = MutableStateFlow<Float?>(null)
    val holdFormQuality: StateFlow<Float?> = _holdFormQuality
    
    /**
     * Number of frames with errors during hold (null for rep-based exercises)
     */
    private val _holdErrorCount = MutableStateFlow<Int?>(null)
    val holdErrorCount: StateFlow<Int?> = _holdErrorCount
    
    /**
     * Map of joint codes to their error counts during hold (null for rep-based exercises)
     */
    private val _holdJointErrorMap = MutableStateFlow<Map<String, Int>?>(null)
    val holdJointErrorMap: StateFlow<Map<String, Int>?> = _holdJointErrorMap
    
    // ==================== Events ====================
    
    private val _events = MutableSharedFlow<FeedbackEvent>(
        replay = 0,
        extraBufferCapacity = 20
    )
    val events: SharedFlow<FeedbackEvent> = _events
    
    // ==================== State ====================
    // NOTE: These flags are marked @Volatile for thread safety.
    // processFrame() can be called from background threads (video mode)
    // while start/pause/resume/stop are called from Main thread.
    
    @Volatile
    private var isRunning = false
    
    @Volatile
    private var isPaused = false
    
    @Volatile
    private var lastValidationResult: ValidationResult? = null
    
    /**
     * Flag to defer rep completion until after validation and error collection.
     * This ensures errors from the current frame are included in the rep that just completed.
     * Thread safety: marked @Volatile as it's read/written from different threads.
     */
    @Volatile
    private var pendingRepCompletion = false
    
    // ==================== Initialization ====================
    
    init {
        // Setup callbacks
        stateMachine.onPhaseChanged = { previous, current ->
            _currentPhase.value = current
            emitEvent(FeedbackEvent.PhaseChanged(previous, current))
        }
        
        stateMachine.onRepCompleted = {
            // Don't handle immediately - set flag to process after validation
            // This ensures errors from the current frame are included in this rep
            pendingRepCompletion = true
        }
        
        repCounter.onRepCountChanged = { count, isCorrect ->
            _repCount.value = count
            // Use errors from the completed rep (accumulated during the entire rep)
            // instead of lastValidationResult which only has current frame errors
            val completedRepErrors = repCounter.getLastRepResult()?.errors ?: emptyList()
            emitEvent(FeedbackEvent.RepCompleted(
                repNumber = count,
                isCorrect = isCorrect,
                errors = completedRepErrors
            ))
        }
        
        repCounter.onTargetReached = {
            _isCompleted.value = true
            emitEvent(FeedbackEvent.TargetReached(
                totalReps = repCounter.count,
                correctReps = repCounter.correctCount,
                accuracy = repCounter.getAccuracy()
            ))
        }
        
        // Setup Hold Timer callbacks (if hold exercise)
        setupHoldTimerCallbacks()
        
        Log.d(TAG, "TrainingEngine initialized")
        Log.d(TAG, "Exercise: ${exerciseConfig.name.en}")
        Log.d(TAG, "Counting Method: ${exerciseConfig.countingMethod}")
        Log.d(TAG, "Difficulty: $difficulty")
        if (isHoldExercise) {
            Log.d(TAG, "Target Duration: ${targetDurationMs}ms")
            Log.d(TAG, "Grace Period: ${holdTimer?.getGracePeriodMs()}ms")
        } else {
            Log.d(TAG, "Target Reps: $targetReps")
        }
        Log.d(TAG, "Tracked Joints: ${trackedJoints.map { it.joint }}")
        Log.d(TAG, "Primary Joints: ${primaryJoints.map { it.joint }}")
    }
    
    // ==================== Thread Safety ====================
    
    // Lock object for thread-safe state modifications
    // Used by start/pause/resume/stop and processFrame
    private val stateLock = Any()
    
    // ==================== Public API ====================
    
    /**
     * Start the training session
     * Thread safety: Uses synchronized block to coordinate with processFrame
     */
    fun start() {
        synchronized(stateLock) {
            isRunning = true
            isPaused = false
            angleSmoother.reset()  // Reset smoothing history for fresh start
            stateMachine.reset()
            repCounter.reset()
            holdTimer?.reset()
            positionValidator?.clearCooldowns()
            formValidator.reset()  // Reset zone hysteresis state
            lastPositionEventTimes.clear()
            // Camera warning throttle is now handled by MessageOrchestrator
            
            _currentPhase.value = Phase.IDLE
            _repCount.value = 0
            _isCompleted.value = false
            
            // Reset position validation state
            _positionErrors.value = emptyList()
            _cameraWarning.value = null
            
            // Reset visibility monitor
            visibilityMonitor.reset()
            visibilityMonitor.resetStats()
            _visibilityState.value = VisibilityState.VISIBLE
            _isVisibilityPaused.value = false
            
            // Reset hold-specific state
            if (isHoldExercise) {
                _holdState.value = HoldState.IDLE
                _holdElapsedMs.value = 0L
                _holdRemainingMs.value = targetDurationMs
                _holdProgress.value = 0f
                _graceRemainingMs.value = null
                resetHoldTracking()
            }
        }
        
        emitEvent(FeedbackEvent.TrainingStarted(
            exerciseName = exerciseConfig.name,
            targetReps = targetReps
        ))
        
        Log.d(TAG, "Training started (${if (isHoldExercise) "HOLD" else "REPS"} mode)")
        if (positionValidator != null) {
            Log.d(TAG, "Position checks enabled: ${poseVariant.positionChecks.size} checks")
        }
    }
    
    /**
     * Pause training
     * Thread safety: Uses synchronized block to coordinate with processFrame
     */
    fun pause() {
        synchronized(stateLock) {
            isPaused = true
        }
        emitEvent(FeedbackEvent.TrainingPaused(repCounter.count))
        Log.d(TAG, "Training paused at rep ${repCounter.count}")
    }
    
    /**
     * Resume training
     * Thread safety: Uses synchronized block to coordinate with processFrame
     */
    fun resume() {
        synchronized(stateLock) {
            isPaused = false
        }
        emitEvent(FeedbackEvent.TrainingResumed())
        Log.d(TAG, "Training resumed")
    }
    
    /**
     * Resume from visibility pause
     * Called when user's joints become visible again after auto-pause
     * 
     * This method:
     * 1. Resets the state machine to START phase (user needs to get back in position)
     * 2. Resets form validator and smoothers for fresh tracking
     * 3. PRESERVES the rep count (continues from where paused)
     * 4. Notifies visibility monitor that resume is complete
     * 
     * Thread safety: Uses synchronized block to coordinate with processFrame
     */
    fun resumeFromVisibilityPause() {
        synchronized(stateLock) {
            // Reset state machine to START (like beginning of training)
            // User needs to get back into start position
            stateMachine.reset()
            
            // Reset smoothing for fresh data
            angleSmoother.reset()
            
            // Reset form validator state
            formValidator.reset()
            
            // Clear position validation cooldowns
            positionValidator?.clearCooldowns()
            lastPositionEventTimes.clear()
            
            // Reset visibility-related state
            _isVisibilityPaused.value = false
            _visibilityState.value = VisibilityState.VISIBLE
            
            // Notify visibility monitor
            visibilityMonitor.onResumeCountdownComplete()
            
            // DON'T reset repCounter - keep the count!
            // The user continues from where they paused
            
            // Reset phase to IDLE (will transition to START when in position)
            _currentPhase.value = Phase.IDLE
            
            // Clear current errors (fresh start)
            _positionErrors.value = emptyList()
            _cameraWarning.value = null
        }
        
        emitEvent(FeedbackEvent.VisibilityResumed(repCount = repCounter.count))
        Log.d(TAG, "Resumed from visibility pause at rep ${repCounter.count}")
    }
    
    /**
     * Stop training and get summary
     * Thread safety: Uses synchronized block to coordinate with processFrame
     */
    fun stop(): SessionSummary {
        synchronized(stateLock) {
            isRunning = false
            isPaused = false
        }
        
        val summary = SessionSummary(
            exerciseName = exerciseConfig.name.en,
            difficulty = difficulty,
            totalReps = repCounter.count,
            correctReps = repCounter.correctCount,
            incorrectReps = repCounter.incorrectCount,
            accuracy = repCounter.getAccuracy(),
            durationMs = 0, // TODO: Track actual duration
            commonErrors = repCounter.getMostCommonErrors(),
            repDetails = repCounter.repResults
        )
        
        Log.d(TAG, "Training stopped. Summary: $summary")
        return summary
    }
    
    /**
     * Process a frame with joint angles and optional landmarks
     * This is called every frame from the camera/pose detection pipeline
     * 
     * Thread Safety: Uses synchronized block to prevent concurrent modifications
     * when called from background thread (video mode) or camera callback thread.
     * 
     * @param angles JointAngles from AngleCalculator (already mirrored for front camera)
     * @param landmarks Optional smoothed landmarks for position-based validation
     * @param isFrontCamera Whether using front camera (for visibility check mirroring)
     */
    fun processFrame(angles: JointAngles, landmarks: List<SmoothedLandmark>? = null, isFrontCamera: Boolean = false) {
        // Early return outside lock for performance
        if (!isRunning || isPaused) return
        
        // Synchronized block to prevent concurrent frame processing
        // Uses same lock as start/pause/resume/stop for proper coordination
        synchronized(stateLock) {
            // Double-check after acquiring lock
            if (!isRunning || isPaused) return
            
            // 1. Extract tracked joint angles (raw) - MUST happen first for arrowInfos
            val rawTrackedAngles = jointTracker.extractTrackedAngles(angles)
            
            if (rawTrackedAngles.isEmpty()) {
                return
            }
            
            // 2. Apply centralized smoothing - SINGLE SOURCE OF TRUTH
            // All components use these smoothed angles for consistency
            val smoothedAngles = angleSmoother.smooth(rawTrackedAngles)
            _currentAngles.value = smoothedAngles
            
            // 3. Extract primary joint angles (from smoothed)
            val primaryAngles = smoothedAngles.filterKeys { jointCode ->
                primaryJoints.any { it.joint == jointCode }
            }
            
            // 4. Check if in start position (using smoothed angles)
            val inStartPos = formValidator.isInStartPosition(smoothedAngles)
            _isInStartPosition.value = inStartPos
            
            // 5. Update state machine (using smoothed angles - no internal smoothing needed)
            val currentPhase = stateMachine.update(primaryAngles)
            _currentPhase.value = currentPhase
            
            // 6. Validate form (using same smoothed angles - consistent with state machine)
            val validation = formValidator.validate(smoothedAngles, currentPhase)
            lastValidationResult = validation
            _jointStatuses.value = validation.jointStatuses
            
            // 7. Update arrow infos for visual feedback (using smoothed angles)
            // This MUST happen before visibility check so skeleton overlay always shows correct joints
            _arrowInfos.value = formValidator.getJointArrowInfos(smoothedAngles)
            
            // 7.5. Check visibility of required joints (after arrowInfos update for UI)
            if (landmarks != null) {
                val visibilityResult = visibilityMonitor.checkVisibility(
                    landmarks = landmarks,
                    currentRepCount = repCounter.count,
                    currentPhase = stateMachine.currentPhase,
                    isFrontCamera = isFrontCamera
                )
                
                // Update visibility state
                _visibilityState.value = visibilityMonitor.state.value
                
                // Handle visibility result
                when (visibilityResult) {
                    is VisibilityCheckResult.PauseTraining -> {
                        // Pause training due to visibility - but arrowInfos already updated
                        _isVisibilityPaused.value = true
                        emitEvent(FeedbackEvent.VisibilityPaused(
                            savedRepCount = visibilityResult.savedRepCount,
                            savedPhase = visibilityResult.savedPhase,
                            message = visibilityResult.message
                        ))
                        return  // Don't process rep counting while paused
                    }
                    
                    is VisibilityCheckResult.ShowWarning -> {
                        // Show warning but continue processing
                        emitEvent(FeedbackEvent.VisibilityWarning(
                            message = visibilityResult.message,
                            remainingBeforePauseMs = visibilityResult.remainingBeforePause,
                            invisibleJoints = visibilityResult.invisibleJoints
                        ))
                        // Continue processing below...
                    }
                    
                    is VisibilityCheckResult.StartResumeCountdown -> {
                        // Joints visible again after pause - trigger resume countdown
                        emitEvent(FeedbackEvent.VisibilityResumeCountdown(
                            resumeFromRep = visibilityResult.resumeFromRep,
                            resumeFromPhase = visibilityResult.resumeFromPhase
                        ))
                        return  // Don't process frame during countdown
                    }
                    
                    is VisibilityCheckResult.ContinueCountdown -> {
                        // In resume countdown, don't process frame
                        return
                    }
                    
                    is VisibilityCheckResult.ContinueTraining -> {
                        // Normal flow - continue processing
                        _isVisibilityPaused.value = false
                    }
                }
            }
            
            // 8. Position validation (if landmarks provided and validator exists)
            val positionValidation = if (landmarks != null && positionValidator != null) {
                positionValidator.validate(landmarks, currentPhase, difficulty)
            } else null
            
            // Update position-related state flows
            positionValidation?.let {
                // Include tips too so UI overlay can visualize them (different severity)
                _positionErrors.value = it.errors + it.warnings + it.tips
                _cameraWarning.value = it.cameraWarning
            }
            
            // 9. Handle form errors (add to current rep for rep-based, track for hold)
            for (error in validation.errors) {
                if (!isHoldExercise) {
                    repCounter.addError(error)
                } else {
                    // Track errors for hold exercises to calculate form quality
                    if (_holdState.value == HoldState.HOLDING) {
                        holdErrorFrameCount++
                        holdJointErrors[error.jointCode] = 
                            (holdJointErrors[error.jointCode] ?: 0) + 1
                    }
                }
                // Emit error event for real-time feedback
                emitEvent(FeedbackEvent.JointErrorDetected(error))
            }
            
            // 10. Handle position errors
            positionValidation?.errors?.forEach { error ->
                // Only ERROR severity affects rep correctness
                if (!isHoldExercise) {
                    repCounter.addPositionError(error)
                } else {
                    // Track position errors for hold exercises too
                    if (_holdState.value == HoldState.HOLDING) {
                        holdErrorFrameCount++
                        // Track by check ID for position errors
                        val jointKey = "position:${error.checkId}"
                        holdJointErrors[jointKey] = 
                            (holdJointErrors[jointKey] ?: 0) + 1
                    }
                }
                if (shouldEmitPositionEvent(error.checkId)) {
                    emitEvent(FeedbackEvent.PositionErrorDetected(error))
                }
            }
            
            positionValidation?.warnings?.forEach { error ->
                if (shouldEmitPositionEvent(error.checkId)) {
                    emitEvent(FeedbackEvent.PositionWarningDetected(error))
                }
            }

            positionValidation?.tips?.forEach { tip ->
                if (shouldEmitPositionEvent(tip.checkId)) {
                    emitEvent(FeedbackEvent.PositionTipDetected(tip))
                }
            }
            
            positionValidation?.cameraWarning?.let { warning ->
                // Always emit - throttling is handled by MessageOrchestrator in FeedbackManager
                emitEvent(FeedbackEvent.CameraPositionWarning(warning))
            }
            
            // 11. Handle based on counting method
            if (isHoldExercise) {
                // Update hold timer based on current phase
                // Phase.COUNT means user is in hold zone (downRange)
                val isInHoldZone = (currentPhase == Phase.COUNT)
                updateHoldTimer(isInHoldZone)
                
                // Track form quality during hold
                if (_holdState.value == HoldState.HOLDING) {
                    holdTotalFrameCount++
                    // Update form quality state flows
                    val quality = calculateFormQuality()
                    _holdFormQuality.value = quality
                    _holdErrorCount.value = holdErrorFrameCount
                    _holdJointErrorMap.value = holdJointErrors.toMap()
                }
            } else {
                // Rep-based: Handle pending rep completion AFTER validation and error collection
                // This ensures all errors from the final frame are included in the completed rep
                if (pendingRepCompletion) {
                    pendingRepCompletion = false
                    handleRepCompleted()
                }
            }
        } // End synchronized(stateLock)
    }
    
    /**
     * Handle rep completion (called by state machine)
     */
    private fun handleRepCompleted() {
        // Record phase timings
        repCounter.setPhaseTimings(stateMachine.getPhaseTimings())
        
        // Complete the rep
        repCounter.completeRep()
        
        // Clear phase timings for next rep
        stateMachine.clearTimings()
        
        Log.d(TAG, "Rep ${repCounter.count} completed. Correct: ${repCounter.correctCount}/${repCounter.count}")
    }
    
    // ==================== Hold Timer Methods ====================
    
    /**
     * Setup callbacks for HoldTimer
     */
    private fun setupHoldTimerCallbacks() {
        holdTimer?.let { timer ->
            timer.onStateChanged = { oldState, newState ->
                _holdState.value = newState
                Log.d(TAG, "Hold state: $oldState → $newState")
            }
            
            timer.onHoldStarted = {
                // Reset tracking when hold starts
                resetHoldTracking()
                emitEvent(FeedbackEvent.HoldStarted())
                Log.d(TAG, "Hold started!")
            }
            
            timer.onGraceStarted = { elapsedMs, gracePeriodMs ->
                emitEvent(FeedbackEvent.HoldGraceStarted(
                    gracePeriodMs = gracePeriodMs,
                    elapsedBeforeGraceMs = elapsedMs
                ))
                Log.d(TAG, "Grace period started (elapsed: ${elapsedMs}ms)")
            }
            
            timer.onGraceResumed = { elapsedMs, gracePeriodsUsed ->
                emitEvent(FeedbackEvent.HoldResumed(
                    elapsedMs = elapsedMs,
                    gracePeriodsUsed = gracePeriodsUsed
                ))
                Log.d(TAG, "Resumed from grace (elapsed: ${elapsedMs}ms, graceCount: $gracePeriodsUsed)")
            }
            
            timer.onCompleted = { totalMs, gracePeriodsUsed ->
                _isCompleted.value = true
                // Calculate final form quality before completion
                val formQuality = calculateFormQuality()
                _holdFormQuality.value = formQuality
                _holdErrorCount.value = holdErrorFrameCount
                _holdJointErrorMap.value = holdJointErrors.toMap()
                
                emitEvent(FeedbackEvent.HoldCompleted(
                    totalMs = totalMs,
                    targetMs = targetDurationMs ?: 0L,
                    formQuality = formQuality,
                    gracePeriodsUsed = gracePeriodsUsed
                ))
                Log.d(TAG, "★ Hold COMPLETED! (totalMs: $totalMs, formQuality: $formQuality, errorFrames: $holdErrorFrameCount/$holdTotalFrameCount)")
            }
            
            timer.onFailed = { elapsedMs, gracePeriodsUsed ->
                emitEvent(FeedbackEvent.HoldFailed(
                    elapsedBeforeFailMs = elapsedMs,
                    targetMs = targetDurationMs ?: 0L,
                    gracePeriodCount = gracePeriodsUsed
                ))
                Log.d(TAG, "✗ Hold FAILED! (elapsedMs: $elapsedMs)")
                
                // Auto-reset after failure
                timer.reset()
                _holdState.value = HoldState.IDLE
                _holdElapsedMs.value = 0L
                _holdRemainingMs.value = targetDurationMs
                _holdProgress.value = 0f
                _graceRemainingMs.value = null
                resetHoldTracking()
            }
        }
    }
    
    /**
     * Update hold timer and state flows
     */
    private fun updateHoldTimer(isInHoldZone: Boolean) {
        holdTimer?.let { timer ->
            val currentTimeMs = System.currentTimeMillis()
            timer.update(isInHoldZone, currentTimeMs)
            
            // Update state flows
            _holdState.value = timer.state.value
            _holdElapsedMs.value = timer.elapsedMs.value
            _holdRemainingMs.value = timer.getRemainingMs()
            _holdProgress.value = timer.getProgress()
            _graceRemainingMs.value = timer.graceRemainingMs.value
        }
    }
    
    /**
     * Calculate form quality for hold exercises
     * Returns percentage of frames with correct form (0.0 - 1.0)
     * 
     * Formula: formQuality = (totalFrames - errorFrames) / totalFrames
     * 
     * @return Form quality as float between 0.0 (all errors) and 1.0 (perfect)
     */
    private fun calculateFormQuality(): Float {
        if (holdTotalFrameCount == 0) return 1.0f // No frames yet = perfect (initial state)
        val correctFrames = holdTotalFrameCount - holdErrorFrameCount
        return (correctFrames.toFloat() / holdTotalFrameCount.toFloat()).coerceIn(0f, 1f)
    }
    
    /**
     * Reset hold form quality tracking
     * Called when hold starts or resets
     */
    private fun resetHoldTracking() {
        holdErrorFrameCount = 0
        holdTotalFrameCount = 0
        holdJointErrors.clear()
        _holdFormQuality.value = 1.0f // Start with perfect quality
        _holdErrorCount.value = 0
        _holdJointErrorMap.value = emptyMap()
        Log.d(TAG, "Hold form tracking reset")
    }
    
    /**
     * Emit feedback event
     */
    private fun emitEvent(event: FeedbackEvent) {
        _events.tryEmit(event)
    }

    /**
     * Throttle position feedback events per check using the check's cooldownMs.
     * Visual overlay is driven by state flows and remains visible while the issue persists.
     */
    private fun shouldEmitPositionEvent(checkId: String): Boolean {
        val now = System.currentTimeMillis()
        val cooldown = positionChecksById[checkId]?.cooldownMs ?: 1500L
        val lastTime = lastPositionEventTimes[checkId] ?: 0L
        if (now - lastTime < cooldown) return false
        lastPositionEventTimes[checkId] = now
        return true
    }
    
    // NOTE: shouldEmitCameraWarning() removed - throttling now handled by MessageOrchestrator
    
    // ==================== Getters ====================
    
    fun getExerciseConfig(): ExerciseConfig = exerciseConfig
    fun getDifficulty(): DifficultyType = difficulty
    fun getTargetReps(): Int = targetReps
    fun getCurrentRep(): Int = repCounter.count
    fun getCorrectReps(): Int = repCounter.correctCount
    fun getAccuracy(): Float = repCounter.getAccuracy()
    fun getProgress(): Float = repCounter.getProgress()
    fun isTrainingActive(): Boolean = isRunning && !isPaused
    
    /**
     * Get tracked joint codes (for UI to know which joints to highlight)
     */
    fun getTrackedJointCodes(): Set<String> = jointTracker.trackedJointCodes
    
    /**
     * Get primary joint codes
     */
    fun getPrimaryJointCodes(): Set<String> = jointTracker.primaryJointCodes
    
    /**
     * Get landmark index for a joint code (for skeleton overlay)
     */
    fun getLandmarkIndex(jointCode: String): Int? = jointTracker.getLandmarkIndex(jointCode)
    
    /**
     * Get all tracked landmark indices
     */
    fun getTrackedLandmarkIndices(): List<Int> = jointTracker.getTrackedLandmarkIndices()
    
    // ==================== Hot-Swap Support ====================
    
    /**
     * Check if this engine can be hot-swapped to a new exercise
     * Hot-swap is supported when the new exercise uses the same counting method
     */
    fun canHotSwapTo(newConfig: ExerciseConfig): Boolean {
        return newConfig.countingMethod == exerciseConfig.countingMethod
    }
    
    /**
     * Get current exercise config (for comparison during hot-swap)
     */
    fun getCurrentExerciseConfig(): ExerciseConfig = exerciseConfig
    
    // ==================== Hold-specific Getters ====================
    
    /**
     * Get target duration for hold exercises (returns 0 for rep-based)
     */
    fun getTargetDurationMs(): Long = targetDurationMs ?: 0L
    
    /**
     * Get target duration in seconds for hold exercises (returns 0 for rep-based)
     */
    fun getTargetDurationSeconds(): Int = ((targetDurationMs ?: 0L) / 1000L).toInt()
    
    /**
     * Get current hold timer instance (null for rep-based)
     */
    fun getHoldTimer(): HoldTimer? = holdTimer
    
    /**
     * Check if hold is completed
     */
    fun isHoldCompleted(): Boolean = holdTimer?.isCompleted() ?: false
    
    /**
     * Check if hold has failed
     */
    fun isHoldFailed(): Boolean = holdTimer?.isFailed() ?: false
    
    /**
     * Check if currently in grace period
     */
    fun isInGracePeriod(): Boolean = holdTimer?.isInGracePeriod() ?: false
    
    /**
     * Get number of grace periods used
     */
    fun getGracePeriodCount(): Int = holdTimer?.getGracePeriodCount() ?: 0
    
    // ==================== Position Validation Getters ====================
    
    /**
     * Check if position validation is enabled for this exercise
     */
    fun hasPositionChecks(): Boolean = positionValidator != null
    
    /**
     * Get current position errors
     */
    fun getCurrentPositionErrors(): List<PositionError> = _positionErrors.value
    
    /**
     * Get last detected camera result
     */
    fun getLastCameraResult(): CameraPositionDetector.CameraDetectionResult? = 
        positionValidator?.getLastCameraResult()
    
    // ==================== Visibility Getters ====================
    
    /**
     * Check if currently paused due to visibility
     */
    fun isVisibilityPausedNow(): Boolean = _isVisibilityPaused.value
    
    /**
     * Get current visibility state
     */
    fun getVisibilityState(): VisibilityState = _visibilityState.value
    
    /**
     * Get visibility statistics
     */
    fun getVisibilityStats(): VisibilityStats = visibilityMonitor.getStats()
}
