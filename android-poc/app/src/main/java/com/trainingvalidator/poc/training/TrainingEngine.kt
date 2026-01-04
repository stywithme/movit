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
    private val poseVariantIndex: Int = 0
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
    private val targetReps: Int = difficultyLevel.repCountingConfig.reps
    
    // ==================== Components ====================
    
    private val jointTracker = JointAngleTracker(trackedJoints)
    
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
     */
    private val positionChecksById: Map<String, PositionCheck> =
        poseVariant.positionChecks.associateBy { it.id }

    private val lastPositionEventTimes = mutableMapOf<String, Long>()
    
    // ==================== Position Validator ====================
    
    /**
     * Position validator for position-based checks (knee-over-toe, alignment, etc.)
     * Null if no position checks are configured
     */
    private val positionValidator: PositionValidator? = 
        poseVariant.positionChecks.takeIf { it.isNotEmpty() }?.let {
            PositionValidator(
                positionChecks = it,
                expectedCameraPosition = poseVariant.cameraPosition,
                expectedFacingDirection = poseVariant.expectedFacingDirection
            )
        }
    
    // ==================== Hold Timer (for HOLD exercises only) ====================
    
    /**
     * Check if this is a hold exercise
     */
    val isHoldExercise: Boolean = exerciseConfig.countingMethod == CountingMethod.HOLD
    
    /**
     * Target duration for hold exercises (null for rep-based)
     */
    val targetDurationMs: Long? = if (isHoldExercise) {
        difficultyLevel.repCountingConfig.getDurationMs(
            SettingsManager.getDefaultHoldDuration()
        )
    } else null
    
    /**
     * Hold timer instance (null for rep-based exercises)
     */
    private val holdTimer: HoldTimer? = if (isHoldExercise) {
        HoldTimer(
            targetDurationMs = targetDurationMs!!,
            gracePeriodMs = difficultyLevel.repCountingConfig.getGracePeriod(
                SettingsManager.getDefaultGracePeriod()
            )
        )
    } else null
    
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
    
    // ==================== Events ====================
    
    private val _events = MutableSharedFlow<FeedbackEvent>(
        replay = 0,
        extraBufferCapacity = 20
    )
    val events: SharedFlow<FeedbackEvent> = _events
    
    // ==================== State ====================
    
    private var isRunning = false
    private var isPaused = false
    private var lastValidationResult: ValidationResult? = null
    
    /**
     * Flag to defer rep completion until after validation and error collection.
     * This ensures errors from the current frame are included in the rep that just completed.
     */
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
    
    // ==================== Public API ====================
    
    /**
     * Start the training session
     */
    fun start() {
        isRunning = true
        isPaused = false
        stateMachine.reset()
        repCounter.reset()
        holdTimer?.reset()
        positionValidator?.clearCooldowns()
        lastPositionEventTimes.clear()
        
        _currentPhase.value = Phase.IDLE
        _repCount.value = 0
        _isCompleted.value = false
        
        // Reset position validation state
        _positionErrors.value = emptyList()
        _cameraWarning.value = null
        
        // Reset hold-specific state
        if (isHoldExercise) {
            _holdState.value = HoldState.IDLE
            _holdElapsedMs.value = 0L
            _holdRemainingMs.value = targetDurationMs
            _holdProgress.value = 0f
            _graceRemainingMs.value = null
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
     */
    fun pause() {
        isPaused = true
        emitEvent(FeedbackEvent.TrainingPaused(repCounter.count))
        Log.d(TAG, "Training paused at rep ${repCounter.count}")
    }
    
    /**
     * Resume training
     */
    fun resume() {
        isPaused = false
        emitEvent(FeedbackEvent.TrainingResumed())
        Log.d(TAG, "Training resumed")
    }
    
    /**
     * Stop training and get summary
     */
    fun stop(): SessionSummary {
        isRunning = false
        isPaused = false
        
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
     * @param angles JointAngles from AngleCalculator
     * @param landmarks Optional smoothed landmarks for position-based validation
     */
    fun processFrame(angles: JointAngles, landmarks: List<SmoothedLandmark>? = null) {
        if (!isRunning || isPaused) return
        
        // 1. Extract tracked joint angles
        val trackedAngles = jointTracker.extractTrackedAngles(angles)
        _currentAngles.value = trackedAngles
        
        if (trackedAngles.isEmpty()) {
            return
        }
        
        // 2. Extract primary joint angles for state machine
        val primaryAngles = jointTracker.extractPrimaryAngles(angles)
        
        // 3. Check if in start position (before training starts)
        val inStartPos = formValidator.isInStartPosition(trackedAngles)
        _isInStartPosition.value = inStartPos
        
        // 4. Update state machine
        val currentPhase = stateMachine.update(primaryAngles)
        _currentPhase.value = currentPhase
        
        // 5. Validate form (angle-based)
        val validation = formValidator.validate(trackedAngles, currentPhase)
        lastValidationResult = validation
        _jointStatuses.value = validation.jointStatuses
        
        // 6. Update arrow infos for visual feedback
        _arrowInfos.value = formValidator.getJointArrowInfos(trackedAngles)
        
        // 7. Position validation (if landmarks provided and validator exists)
        val positionValidation = if (landmarks != null && positionValidator != null) {
            positionValidator.validate(landmarks, currentPhase, difficulty)
        } else null
        
        // Update position-related state flows
        positionValidation?.let {
            // Include tips too so UI overlay can visualize them (different severity)
            _positionErrors.value = it.errors + it.warnings + it.tips
            _cameraWarning.value = it.cameraWarning
        }
        
        // 8. Handle form errors (add to current rep for rep-based)
        for (error in validation.errors) {
            if (!isHoldExercise) {
                repCounter.addError(error)
            }
            // Emit error event for real-time feedback
            emitEvent(FeedbackEvent.JointErrorDetected(error))
        }
        
        // 9. Handle position errors
        positionValidation?.errors?.forEach { error ->
            // Only ERROR severity affects rep correctness
            if (!isHoldExercise) {
                repCounter.addPositionError(error)
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
            emitEvent(FeedbackEvent.CameraPositionWarning(warning))
        }
        
        // 10. Handle based on counting method
        if (isHoldExercise) {
            // Update hold timer based on current phase
            // Phase.COUNT means user is in hold zone (downRange)
            val isInHoldZone = (currentPhase == Phase.COUNT)
            updateHoldTimer(isInHoldZone)
        } else {
            // Rep-based: Handle pending rep completion AFTER validation and error collection
            // This ensures all errors from the final frame are included in the completed rep
            if (pendingRepCompletion) {
                pendingRepCompletion = false
                handleRepCompleted()
            }
        }
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
                val formQuality = calculateFormQuality()
                emitEvent(FeedbackEvent.HoldCompleted(
                    totalMs = totalMs,
                    targetMs = targetDurationMs ?: 0L,
                    formQuality = formQuality,
                    gracePeriodsUsed = gracePeriodsUsed
                ))
                Log.d(TAG, "★ Hold COMPLETED! (totalMs: $totalMs, formQuality: $formQuality)")
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
     * Currently returns 1.0 (perfect) - can be enhanced to track form errors
     */
    private fun calculateFormQuality(): Float {
        // TODO: Track form errors during hold and calculate quality
        // For now, return 1.0 (perfect form)
        return 1.0f
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
}
