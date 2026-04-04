package com.trainingvalidator.poc.training

import android.util.Log
import android.os.SystemClock
import com.trainingvalidator.poc.analysis.JointAngles
import com.trainingvalidator.poc.analysis.SmoothedLandmark
import com.trainingvalidator.poc.training.analytics.MotionRecorder
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
 * - FormValidator: Validates form and detects errors (STATE-BASED)
 * - RepCounter: Counts repetitions with scores
 * 
 * STATE-BASED ARCHITECTURE:
 * - Quality assessment uses JointState (PERFECT/NORMAL/PAD/WARNING/DANGER)
 * - Rep scoring based on worst state reached
 * - Single source of truth from StateConfig
 * 
 * Usage:
 * 1. Create instance with exercise config (no difficulty needed)
 * 2. Call processFrame() for each camera frame
 * 3. Observe state flows for UI updates
 */
class TrainingEngine(
    private val exerciseConfig: ExerciseConfig,
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
        
        // State message throttling (prevents excessive message spam)
        // NOTE: This is overridden by SettingsManager.getStateMessageCooldown() at runtime
        private const val STATE_MESSAGE_COOLDOWN_MS_DEFAULT = 2000L
    }
    
    // ==================== Bilateral Side ====================
    
    /**
     * Bilateral side enum for per-rep left/right alternation
     */
    enum class BilateralSide {
        LEFT, RIGHT;
        fun flip(): BilateralSide = if (this == LEFT) RIGHT else LEFT
    }
    
    // ==================== Configuration ====================
    
    private val poseVariant: PoseVariant = exerciseConfig.poseVariants[poseVariantIndex]
    private val repCountingConfig: RepCountingConfig = exerciseConfig.repCountingConfig
    
    private val trackedJoints: List<TrackedJoint> = poseVariant.trackedJoints
    private val primaryJoints: List<TrackedJoint> = poseVariant.getPrimaryJoints()
    
    // OPTIMIZED: Pre-computed Set for O(1) lookup instead of O(n) any{} on every frame
    private val primaryJointCodes: Set<String> = primaryJoints.map { it.joint }.toSet()
    
    // ==================== Bilateral Runtime Flipping ====================
    
    private val isBilateral: Boolean = exerciseConfig.isBilateral
    private val bilateralConfig: BilateralConfig? = exerciseConfig.bilateralConfig
    
    /** The startSide from config (e.g., "right") — this is the side the joints are configured for */
    private val bilateralStartSide: BilateralSide = when (bilateralConfig?.startSide) {
        "left" -> BilateralSide.LEFT
        else -> BilateralSide.RIGHT
    }
    
    /** Current active side for bilateral exercises */
    private var _currentBilateralSide: BilateralSide = bilateralStartSide
    
    /** Observable current bilateral side */
    private val _bilateralSide = MutableStateFlow(_currentBilateralSide)
    val bilateralSide: StateFlow<BilateralSide> = _bilateralSide
    
    /**
     * Whether the current bilateral side is flipped relative to the configured side.
     * When true, JointAngleTracker reads the OPPOSITE side's angles and
     * the overlay should draw indicators on the mirrored landmarks.
     */
    val isBilateralFlipped: Boolean
        get() = isBilateral && _currentBilateralSide != bilateralStartSide
    
    /**
     * Effective target reps: override takes precedence, then exercise config
     */
    private val targetReps: Int = targetRepsOverride 
        ?: repCountingConfig.reps
    
    /**
     * Feedback messages for random delivery (LOW priority)
     * 
     * Access this to configure FeedbackManager with exercise-specific messages.
     * Call feedbackManager.setRandomMessages(engine.feedbackMessages) when starting.
     */
    val feedbackMessages: FeedbackMessages
        get() = poseVariant.feedbackMessages
    
    // ==================== Components ====================
    
    private val jointTracker = JointAngleTracker(trackedJoints)
    
    /**
     * Motion recorder for analytics (optional)
     * 
     * When set, records frame-by-frame motion data for post-session analysis.
     * Set this before calling start() to enable motion recording.
     * Call getMotionRecord() after stop() to retrieve the session data.
     */
    var motionRecorder: MotionRecorder? = null
    
    /**
     * Centralized angle smoother - Single Source of Truth for smoothed angles
     * All components use smoothed angles from here for consistency
     */
    private val angleSmoother = AngleSmoother()
    
    private val stateMachine = PhaseStateMachine(
        countingMethod = exerciseConfig.countingMethod,
        primaryJoints = primaryJoints,
        repCountingConfig = repCountingConfig,
        numberOfPhases = 4,  // Default phases: START → DOWN → BOTTOM → UP
        timeProvider = { nowMs() }
    )
    
    private val formValidator = FormValidator(
        trackedJoints = trackedJoints
    )
    
    private val repCounter = RepCounter(
        targetReps = targetReps,
        repCountingConfig = repCountingConfig,
        isHoldExercise = exerciseConfig.countingMethod == CountingMethod.HOLD,
        primaryJoints = exerciseConfig.getPrimaryJoints().map { it.joint }.toSet(),
        timeProvider = { nowMs() }
    )

    /**
     * Per-check cooldown for emitting feedback events (visual overlay stays active, but events are throttled).
     */
    private val configuredPositionChecks: List<PositionCheck> = poseVariant.positionChecks

    private val hasPositionChecksConfigured: Boolean = configuredPositionChecks.isNotEmpty()

    private val positionChecksById: Map<String, PositionCheck> =
        configuredPositionChecks.associateBy { it.id }

    private val lastPositionEventTimes = mutableMapOf<String, Long>()
    
    // Camera warning event throttle (UI overlay uses StateFlow, but FeedbackEvent is throttled)
    private var lastCameraWarningEventTime = 0L
    private val CAMERA_WARNING_EVENT_COOLDOWN_MS = 2000L  // Only emit event every 2s

    // Scene lock: freeze axis selection after first valid detection to avoid mid-training noise
    private var sceneLocked = false
    
    // ==================== Position Validator ====================
    
    /**
     * Position validator for position-based checks (knee-over-toe, alignment, etc.)
     * Always initialized (scene checks run even when positionChecks is empty)
     */
    private val resolvedPosePositionCode: String =
        poseVariant.posePosition ?: poseVariant.cameraPosition ?: "standing_side"

    private val resolvedExpectation: PoseSceneExpectation =
        if (poseVariant.expectedPostures != null) {
            PoseSceneExpectation.fromJson(poseVariant.expectedPostures, poseVariant.expectedDirections, poseVariant.expectedRegions)
        } else {
            PoseSceneExpectation.fromLegacyCode(resolvedPosePositionCode)
        }

    private val positionValidator: PositionValidator = PositionValidator(
        positionChecks = configuredPositionChecks,
        posePositionCode = resolvedPosePositionCode,
        sceneExpectation = resolvedExpectation
    )
    
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
        pauseAfterMs = 4000,        // 4s - pause training (was 3s)
        timeProvider = { nowMs() }
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
            ?: repCountingConfig.getDurationMs(
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
            gracePeriodMs = repCountingConfig.getGracePeriod(
                SettingsManager.getDefaultGracePeriod()
            )
        )
    }

    // ==================== Safety Guardrails ====================

    private val minRepIntervalMs: Long = repCountingConfig.getMinRepInterval(
        SettingsManager.getDefaultMinRepInterval()
    )

    private val maxRepsGuard: Int = if (isHoldExercise) {
        1
    } else {
        if (targetReps > 0) maxOf(targetReps * 3, targetReps + 12) else 60
    }

    private val maxSessionDurationGuardMs: Long = if (isHoldExercise) {
        maxOf((targetDurationMs ?: 0L) * 3L, 180_000L)
    } else {
        maxOf(targetReps.coerceAtLeast(1).toLong() * minRepIntervalMs * 4L, 180_000L)
    }
    @Volatile
    private var cameraWarningCount: Int = 0

    @Volatile
    private var safetyStopTriggered: Boolean = false
    
    // ==================== Hold Form Quality Tracking ====================
    
    // NOTE: Legacy tracking variables removed.
    // Form quality is now calculated by RepCounter using weighted average of time in states.
    // See RepCounter.calculateHoldScore()

    
    // ==================== State Flows ====================
    
    private val _currentPhase = MutableStateFlow(Phase.IDLE)
    val currentPhase: StateFlow<Phase> = _currentPhase
    
    private val _repCount = MutableStateFlow(0)
    val repCount: StateFlow<Int> = _repCount
    
    @Suppress("DEPRECATION")
    private val _jointStatuses = MutableStateFlow<Map<String, JointStatus>>(emptyMap())
    @Suppress("DEPRECATION")
    val jointStatuses: StateFlow<Map<String, JointStatus>> = _jointStatuses
    
    @Suppress("DEPRECATION")
    private val _arrowInfos = MutableStateFlow<Map<String, JointArrowInfo>>(emptyMap())
    @Suppress("DEPRECATION")
    val arrowInfos: StateFlow<Map<String, JointArrowInfo>> = _arrowInfos
    
    /** NEW: State-based joint info for modern UI components */
    private val _jointStateInfos = MutableStateFlow<Map<String, JointStateInfo>>(emptyMap())
    val jointStateInfos: StateFlow<Map<String, JointStateInfo>> = _jointStateInfos
    
    /** Flag indicating if any joint is currently in DANGER state */
    private val _isDangerActive = MutableStateFlow(false)
    val isDangerActive: StateFlow<Boolean> = _isDangerActive
    
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
     * Per-axis scene warnings (posture/direction/region mismatches).
     */
    private val _sceneWarnings = MutableStateFlow<List<SceneAxisWarning>>(emptyList())
    val sceneWarnings: StateFlow<List<SceneAxisWarning>> = _sceneWarnings
    
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
    
    @Suppress("DEPRECATION")
    @Volatile
    private var lastValidationResult: ValidationResult? = null
    
    /**
     * Session start timestamp for duration tracking
     */
    @Volatile
    private var sessionStartTimeMs: Long = 0L
    
    /**
     * Total paused duration (accumulated when paused)
     */
    @Volatile
    private var totalPausedDurationMs: Long = 0L
    
    /**
     * Timestamp when pause started (for calculating pause duration)
     */
    @Volatile
    private var pauseStartTimeMs: Long = 0L
    
    /**
     * Latest frame timestamp (monotonic) for deterministic timing
     * Used to keep video analysis consistent across runs.
     */
    @Volatile
    private var currentFrameTimeMs: Long = 0L

    /**
     * True when frame timestamps come from an external timeline (e.g. video position)
     * rather than uptime. Pause duration must follow the same timeline domain.
     */
    @Volatile
    private var usesExternalFrameTimeline: Boolean = false
    
    /**
     * Flag to defer rep completion until after validation and error collection.
     * This ensures errors from the current frame are included in the rep that just completed.
     * Thread safety: marked @Volatile as it's read/written from different threads.
     */
    @Volatile
    private var pendingRepCompletion = false
    
    /**
     * Throttling for DANGER events to prevent spamming
     */
    @Volatile
    private var lastDangerEventTime: Long = 0L
    private val dangerEventCooldownMs: Long = 2000L  // 2 seconds between DANGER events

    /**
     * State message throttling per joint
     * Tracks last emitted state and timestamp to avoid spamming
     */
    private val lastStateMessageTimes = mutableMapOf<String, Long>()
    private val lastEmittedStates = mutableMapOf<String, JointState>()
    
    /**
     * Current time source (monotonic when timestamps are provided)
     */
    private fun nowMs(): Long {
        return if (currentFrameTimeMs > 0L) currentFrameTimeMs else SystemClock.uptimeMillis()
    }

    private fun pauseClockNowMs(): Long {
        return if (usesExternalFrameTimeline) nowMs() else SystemClock.uptimeMillis()
    }

    private fun settlePauseDurationLocked() {
        if (pauseStartTimeMs > 0L) {
            val pausedFor = pauseClockNowMs() - pauseStartTimeMs
            totalPausedDurationMs += maxOf(0L, pausedFor)
            pauseStartTimeMs = 0L
        }
    }
    
    /**
     * Check if we should emit a DANGER event (throttling)
     */
    private fun shouldEmitDangerEvent(): Boolean {
        val now = nowMs()
        return now - lastDangerEventTime >= dangerEventCooldownMs
    }
    
    private fun getActiveSessionDurationMs(now: Long = nowMs()): Long {
        if (sessionStartTimeMs <= 0L) return 0L

        val pendingPause = if (isPaused && pauseStartTimeMs > 0L) {
            pauseClockNowMs() - pauseStartTimeMs
        } else 0L

        val elapsed = now - sessionStartTimeMs
        return maxOf(0L, elapsed - totalPausedDurationMs - maxOf(0L, pendingPause))
    }

    private fun isRepMovementPhase(phase: Phase): Boolean {
        return phase != Phase.IDLE && phase != Phase.START
    }

    private fun triggerSafetyStop(reason: String) {
        if (safetyStopTriggered || _isCompleted.value) return

        safetyStopTriggered = true
        _isCompleted.value = true

        Log.w(
            TAG,
            "Safety stop triggered: $reason, reps=${repCounter.count}, counted=${repCounter.countedCount}, duration=${getActiveSessionDurationMs()}ms"
        )
    }

    private fun evaluateSafetyStop(now: Long = nowMs()): Boolean {
        if (safetyStopTriggered || _isCompleted.value) return true

        if (repCounter.count >= maxRepsGuard) {
            triggerSafetyStop("max reps guard reached ($maxRepsGuard)")
            return true
        }
        // NOTE: Danger state invalidates reps, but does not auto-end the session.
        val activeDurationMs = getActiveSessionDurationMs(now)
        if (activeDurationMs >= maxSessionDurationGuardMs) {
            triggerSafetyStop("max session duration guard reached (${activeDurationMs}ms)")
            return true
        }

        return false
    }

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
        
        repCounter.onRepCountChanged = { count, score, isCounted ->
            _repCount.value = count
            // Use errors from the completed rep (accumulated during the entire rep)
            val completedRep = repCounter.getLastRepResult()
            val completedRepErrors = completedRep?.errors ?: emptyList()
            emitEvent(FeedbackEvent.RepCompleted(
                repNumber = count,
                isCorrect = isCounted,  // Legacy compatibility
                errors = completedRepErrors,
                score = score,
                worstState = completedRep?.worstState
            ))

            evaluateSafetyStop()
        }
        
        repCounter.onTargetReached = onTargetReached@ {
            if (safetyStopTriggered) return@onTargetReached
            _isCompleted.value = true
            emitEvent(FeedbackEvent.TargetReached(
                totalReps = repCounter.count,
                correctReps = repCounter.correctCount,
                accuracy = repCounter.getAccuracy()
            ))
        }
        
        // Setup Hold Timer callbacks (if hold exercise)
        setupHoldTimerCallbacks()
        
        Log.d(TAG, "TrainingEngine initialized (STATE-BASED)")
        Log.d(TAG, "Exercise: ${exerciseConfig.name.en}")
        Log.d(TAG, "Counting Method: ${exerciseConfig.countingMethod}")
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
            currentFrameTimeMs = 0L
            usesExternalFrameTimeline = false
            sessionStartTimeMs = 0L
            totalPausedDurationMs = 0L
            pauseStartTimeMs = 0L
            angleSmoother.reset()  // Reset smoothing history for fresh start
            stateMachine.reset()
            repCounter.reset()
            holdTimer?.reset()
            positionValidator.clearCooldowns()
            formValidator.reset()  // Reset zone hysteresis state
            lastPositionEventTimes.clear()
            lastCameraWarningEventTime = 0L
            lastDangerEventTime = 0L
            pendingRepCompletion = false
            cameraWarningCount = 0
            safetyStopTriggered = false
            sceneLocked = false
            positionValidator.unlockScene()
            
            // Reset state message throttling
            lastStateMessageTimes.clear()
            lastEmittedStates.clear()
            
            _currentPhase.value = Phase.IDLE
            _repCount.value = 0
            _isCompleted.value = false
            _isDangerActive.value = false
            _isInStartPosition.value = false
            _currentAngles.value = emptyMap()
            _jointStateInfos.value = emptyMap()
            
            // Reset position validation state
            _positionErrors.value = emptyList()
            _sceneWarnings.value = emptyList()
            
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
            
            // Start motion recording if enabled
            motionRecorder?.start(0L)
        }
        
        emitEvent(FeedbackEvent.TrainingStarted(
            exerciseName = exerciseConfig.name,
            targetReps = targetReps
        ))
        
        Log.d(TAG, "Training started (${if (isHoldExercise) "HOLD" else "REPS"} mode)")
        if (hasPositionChecksConfigured) {
            Log.d(TAG, "Position checks enabled: ${configuredPositionChecks.size} checks")
        }
    }
    
    /**
     * Pause training
     * Thread safety: Uses synchronized block to coordinate with processFrame
     */
    fun pause() {
        synchronized(stateLock) {
            if (!isPaused) {
                isPaused = true
                pauseStartTimeMs = pauseClockNowMs()
            }
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
            settlePauseDurationLocked()
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
            settlePauseDurationLocked()

            // IMPORTANT:
            // Auto-pause is orchestrated by SessionSupervisor via PauseEngine -> TrainingEngine.pause()
            // So we must explicitly unpause here, otherwise processFrame() will keep returning early.
            isPaused = false

            // Reset state machine to START (like beginning of training)
            // User needs to get back into start position
            stateMachine.reset()
            
            // Reset smoothing for fresh data
            angleSmoother.reset()
            
            // Reset form validator state
            formValidator.reset()
            
            // Clear position validation cooldowns & unlock scene for re-detection
            positionValidator.clearCooldowns()
            positionValidator.unlockScene()
            sceneLocked = false
            lastPositionEventTimes.clear()
            pendingRepCompletion = false
            
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
            _sceneWarnings.value = emptyList()
        }
        
        emitEvent(FeedbackEvent.VisibilityResumed(repCount = repCounter.count))
        Log.d(TAG, "Resumed from visibility pause at rep ${repCounter.count}")
    }
    
    /**
     * Stop training and get summary
     * Thread safety: Uses synchronized block to coordinate with processFrame
     */
    fun stop(): SessionSummary {
        val actualDurationMs: Long
        synchronized(stateLock) {
            // Calculate actual duration (excluding paused time)
            val now = nowMs()
            val totalElapsed = if (sessionStartTimeMs > 0) now - sessionStartTimeMs else 0L
            
            // If currently paused, add pending pause duration
            val pendingPause = if (isPaused && pauseStartTimeMs > 0) {
                pauseClockNowMs() - pauseStartTimeMs
            } else 0L
            
            actualDurationMs = maxOf(0L, totalElapsed - totalPausedDurationMs - pendingPause)
            
            isRunning = false
            isPaused = false
        }
        
        val summary = SessionSummary(
            exerciseName = exerciseConfig.name.en,
            totalReps = repCounter.count,
            countedReps = repCounter.countedCount,
            invalidatedReps = repCounter.invalidatedCount,
            averageScore = repCounter.getAverageScore(),
            countedRatio = if (repCounter.count > 0) repCounter.countedCount.toFloat() / repCounter.count else 0f,
            durationMs = actualDurationMs,
            stateBreakdown = repCounter.getStateBreakdown(),
            commonErrors = repCounter.getMostCommonErrors(),
            repDetails = repCounter.repResults
        )
        
        Log.d(TAG, "Training stopped. Duration: ${actualDurationMs}ms, Summary: $summary")
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
    fun processFrame(
        angles: JointAngles,
        landmarks: List<SmoothedLandmark>? = null,
        isFrontCamera: Boolean = false,
        timestampMs: Long = SystemClock.uptimeMillis()
    ) {
        // Early return outside lock for performance
        if (!isRunning || isPaused || _isCompleted.value) return
        
        // Synchronized block to prevent concurrent frame processing
        // Uses same lock as start/pause/resume/stop for proper coordination
        synchronized(stateLock) {
            // Double-check after acquiring lock
            if (!isRunning || isPaused || _isCompleted.value) return
            
            // Update current frame time for deterministic timing
            val frameTimeMs = if (timestampMs > 0L) timestampMs else SystemClock.uptimeMillis()
            currentFrameTimeMs = frameTimeMs
            usesExternalFrameTimeline =
                timestampMs > 0L && kotlin.math.abs(SystemClock.uptimeMillis() - frameTimeMs) > 30_000L
            if (sessionStartTimeMs == 0L) {
                sessionStartTimeMs = frameTimeMs
            }
            if (evaluateSafetyStop(frameTimeMs)) {
                return
            }
            
            
            // 1. Extract tracked joint angles (raw) - MUST happen first for arrowInfos
            // For bilateral exercises, reads the OPPOSITE side's angles when flipped
            val rawTrackedAngles = jointTracker.extractTrackedAngles(angles, isBilateralFlipped)
            
            if (rawTrackedAngles.isEmpty()) {
                return
            }
            
            // 2. Apply centralized smoothing - SINGLE SOURCE OF TRUTH
            // All components use these smoothed angles for consistency
            val smoothedAngles = angleSmoother.smooth(rawTrackedAngles)
            _currentAngles.value = smoothedAngles
            
            // 3. Extract primary joint angles (from smoothed)
            // Config keys are the same regardless of bilateral flip
            val primaryAngles = smoothedAngles.filterKeys { jointCode ->
                primaryJointCodes.contains(jointCode)
            }
            
            // 4. Check if in start position (using smoothed angles)
            val inStartPos = formValidator.isInStartPosition(smoothedAngles)
            _isInStartPosition.value = inStartPos
            
            // 5. Update state machine (using smoothed angles - no internal smoothing needed)
            val currentPhase = stateMachine.update(primaryAngles)
            _currentPhase.value = currentPhase
            
            // 6. Validate form using STATE-BASED system (using same smoothed angles)
            // Get JointStateInfos - the NEW unified state system
            val jointStateInfos = formValidator.getJointStateInfos(smoothedAngles)
            
            // Update state flows for UI
            _jointStateInfos.value = jointStateInfos
            
            // 6.0 Emit state-based messages (warning/pad/normal/perfect)
            emitStateMessages(jointStateInfos)
            
            // 6.0.1 Record frame for motion analytics (if enabled)
            motionRecorder?.record(
                timestamp = frameTimeMs,
                phase = currentPhase,
                angles = smoothedAngles,
                states = jointStateInfos
            )
            
            // Legacy validation from pre-computed state infos (single call — no redundant recalculation)
            @Suppress("DEPRECATION")
            val validation = formValidator.validateFromStateInfos(jointStateInfos)
            @Suppress("DEPRECATION")
            lastValidationResult = validation
            @Suppress("DEPRECATION")
            _jointStatuses.value = validation.jointStatuses
            
            // 6.1. Check for DANGER state (always, for UI feedback)
            val hasDanger = formValidator.hasDangerState(jointStateInfos)
            _isDangerActive.value = hasDanger
            
            // 6.2. Update state tracking for rep scoring
            // REP exercises: track all movement phases (exclude IDLE/START)
            // HOLD exercises: track only COUNT phase
            val shouldTrackState = if (isHoldExercise) {
                currentPhase == Phase.COUNT
            } else {
                isRepMovementPhase(currentPhase)
            }
            
            if (shouldTrackState) {
                // Use weighted scoring with full joint state information
                // This also updates worst state internally, no need for separate call
                repCounter.updateJointStates(jointStateInfos)
                
                // Emit DANGER event with throttling (only when entering DANGER)
                if (hasDanger && shouldEmitDangerEvent()) {
                    val dangerJoints = jointStateInfos.filter { it.value.state == JointState.DANGER }
                    emitEvent(FeedbackEvent.DangerDetected(
                        joints = dangerJoints.keys.toList(),
                        message = dangerJoints.values.firstOrNull()?.messages?.firstOrNull()
                    ))
                    lastDangerEventTime = nowMs()
                }
            }
            
            // 7. Update arrow infos for visual feedback (from pre-computed state infos)
            @Suppress("DEPRECATION")
            _arrowInfos.value = formValidator.buildJointArrowInfos(jointStateInfos)
            
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
            
            // 8. Position validation (if landmarks provided)
            val positionValidation = if (landmarks != null) {
                positionValidator.validate(landmarks, currentPhase, isBilateralFlipped, isFrontCamera)
            } else null
            
            // Lock scene on first valid detection for stable axis selection
            if (!sceneLocked && positionValidation != null) {
                positionValidator.lockScene()
                sceneLocked = true
            }

            // Update position-related state flows
            if (positionValidation != null) {
                _positionErrors.value = positionValidation.errors + positionValidation.warnings + positionValidation.tips
                _sceneWarnings.value = positionValidation.sceneWarnings
            } else {
                _positionErrors.value = emptyList()
                _sceneWarnings.value = emptyList()
            }
            
            // 9. Handle form errors (add to current rep)
            for (error in validation.errors) {
                // Add error to rep counter - it handles both REP and HOLD logic
                repCounter.addError(error)
                
                // Emit error event for real-time feedback
                emitEvent(FeedbackEvent.JointErrorDetected(error))
            }
            
            // 10. Handle position errors
            positionValidation?.errors?.forEach { error ->
                // Add error to rep counter - it handles both REP and HOLD logic
                repCounter.addPositionError(error)
                
                if (shouldEmitPositionEvent(error.checkId)) {
                    emitEvent(FeedbackEvent.PositionErrorDetected(error))
                }
            }
            
            positionValidation?.warnings?.forEach { error ->
                // Track for alignment metrics (does NOT affect rep scoring)
                repCounter.addPositionWarning(error)
                
                if (shouldEmitPositionEvent(error.checkId)) {
                    emitEvent(FeedbackEvent.PositionWarningDetected(error))
                }
            }

            positionValidation?.tips?.forEach { tip ->
                // Track for alignment metrics (does NOT affect rep scoring)
                repCounter.addPositionTip(tip)
                
                if (shouldEmitPositionEvent(tip.checkId)) {
                    emitEvent(FeedbackEvent.PositionTipDetected(tip))
                }
            }
            
            positionValidation?.sceneWarnings?.takeIf { it.isNotEmpty() }?.let { warnings ->
                val now = nowMs()
                if (now - lastCameraWarningEventTime >= CAMERA_WARNING_EVENT_COOLDOWN_MS) {
                    lastCameraWarningEventTime = now
                    cameraWarningCount++
                    emitEvent(FeedbackEvent.SceneWarnings(warnings))
                }
            }
            
            // 11. Handle based on counting method
            if (isHoldExercise) {
                // Update hold timer based on current phase
                // Phase.COUNT means user is in hold zone (downRange)
                val isInHoldZone = (currentPhase == Phase.COUNT)
                updateHoldTimer(isInHoldZone)
                
                // Form quality is now tracked internally by RepCounter via updateWorstState/stateTimeTracking
                // We update the state flow for UI purposes using intermediate calculation
                if (_holdState.value == HoldState.HOLDING) {
                    // Estimate current quality from RepCounter (if exposed) or keep 1.0 until completion
                    // For now, we'll rely on the final score at completion
                }
            } else {
                // Rep-based: Handle pending rep completion AFTER validation and error collection
                // This ensures all errors from the final frame are included in the completed rep
                if (pendingRepCompletion) {
                    pendingRepCompletion = false
                    handleRepCompleted()
                }
            }

            evaluateSafetyStop(frameTimeMs)
        } // End synchronized(stateLock)
    }
    
    /**
     * Handle rep completion (called by state machine)
     */
    private fun handleRepCompleted() {
        // Record phase timings
        val phaseTimings = stateMachine.getPhaseTimings()
        repCounter.setPhaseTimings(phaseTimings)
        
        // Get current state before completing rep
        val worstState = repCounter.getCurrentWorstState()
        val score = repCounter.getPendingScore()
        
        // Complete the rep
        val previousCount = repCounter.count
        repCounter.completeRep()

        // Clear phase timings for next cycle regardless of completion
        stateMachine.clearTimings()

        val repCompleted = repCounter.count > previousCount
        if (!repCompleted) {
            Log.w(TAG, "Rep completion ignored by RepCounter (likely min interval guard)")
            return
        }
        
        // Finalize motion recording for this rep
        motionRecorder?.finalizeRep(
            repNumber = repCounter.count,
            phaseTimings = phaseTimings.mapKeys { it.key.name.lowercase() },
            worstState = worstState,
            score = score
        )
        
        // Bilateral: Switch side after every N reps
        if (isBilateral) {
            val switchEvery = bilateralConfig?.switchEvery ?: 1
            if (repCounter.count % switchEvery == 0) {
                _currentBilateralSide = _currentBilateralSide.flip()
                _bilateralSide.value = _currentBilateralSide
                Log.d(TAG, "Bilateral side switched to: $_currentBilateralSide")
            }
        }
        
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
                
                // Manually complete the rep to ensure scoring
                repCounter.completeRep()
                
                // Get the final result from repCounter (Single Source of Truth)
                val finalResult = repCounter.getLastRepResult()
                val score = finalResult?.score ?: 0f
                
                // Form quality is now essentially the score / 100
                // Score is 0-100, so divide by 100 to get 0.0-1.0 range for legacy compatibility
                val formQuality = score / 100f
                
                _holdFormQuality.value = formQuality
                // Error count is no longer tracked separately in TrainingEngine
                // But we can get it from RepResult if needed
                _holdErrorCount.value = finalResult?.getTotalErrorCount() ?: 0
                // Joint map is complex to reconstruct from list, but less critical for immediate feedback
                _holdJointErrorMap.value = emptyMap() 
                
                emitEvent(FeedbackEvent.HoldCompleted(
                    totalMs = totalMs,
                    targetMs = targetDurationMs ?: 0L,
                    formQuality = formQuality,
                    gracePeriodsUsed = gracePeriodsUsed
                ))
                Log.d(TAG, "★ Hold COMPLETED! (totalMs: $totalMs, formQuality: $formQuality, score: $score)")
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
            val currentTimeMs = nowMs()
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
     * Reset hold form quality tracking
     * Called when hold starts or resets
     */
    private fun resetHoldTracking() {
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
     * Emit state messages for joints (WARNING/PAD/NORMAL/PERFECT)
     * Uses throttling per joint to avoid spam.
     */
    private fun emitStateMessages(stateInfos: Map<String, JointStateInfo>) {
        val now = nowMs()
        
        for ((jointCode, info) in stateInfos) {
            val state = info.state
            
            // Skip transition and danger (danger handled by DangerDetected)
            if (state == JointState.TRANSITION || state == JointState.DANGER) continue
            
            val message = info.messages.firstOrNull() ?: continue
            val lastState = lastEmittedStates[jointCode]
            val lastTime = lastStateMessageTimes[jointCode] ?: 0L
            val cooldown = SettingsManager.getStateMessageCooldown()
            
            val shouldEmit = (lastState != state) || (now - lastTime >= cooldown)
            if (!shouldEmit) continue
            
            emitEvent(FeedbackEvent.JointStateMessage(
                jointCode = jointCode,
                state = state,
                zone = info.currentZone,
                message = message
            ))
            
            lastEmittedStates[jointCode] = state
            lastStateMessageTimes[jointCode] = now
        }
    }

    /**
     * Throttle position feedback events per check using the check's cooldownMs.
     * Visual overlay is driven by state flows and remains visible while the issue persists.
     */
    private fun shouldEmitPositionEvent(checkId: String): Boolean {
        val now = nowMs()
        val cooldown = positionChecksById[checkId]?.cooldownMs ?: 1500L
        val lastTime = lastPositionEventTimes[checkId] ?: 0L
        if (now - lastTime < cooldown) return false
        lastPositionEventTimes[checkId] = now
        return true
    }
    
    // NOTE: shouldEmitCameraWarning() removed - throttling now handled by MessageOrchestrator
    
    // ==================== Getters ====================
    
    fun getExerciseConfig(): ExerciseConfig = exerciseConfig
    fun getTargetReps(): Int = targetReps
    fun getCurrentRep(): Int = repCounter.count
    fun getCountedReps(): Int = repCounter.countedCount
    fun getAverageScore(): Float = repCounter.getAverageScore()
    fun getAccuracy(): Float = repCounter.getAccuracy()
    fun getProgress(): Float = repCounter.getProgress()
    fun isTrainingActive(): Boolean = isRunning && !isPaused

    /** Get all rep results for detailed reporting. */
    fun getRepResults(): List<RepResult> = repCounter.repResults
    
    /** Legacy compatibility */
    fun getCorrectReps(): Int = repCounter.countedCount
    
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
    fun hasPositionChecks(): Boolean = hasPositionChecksConfigured

    /**
     * Number of throttled scene-warning events emitted during this session.
     */
    fun getCameraWarningCount(): Int = cameraWarningCount

    
    /**
     * Get current position errors
     */
    fun getCurrentPositionErrors(): List<PositionError> = _positionErrors.value
    
    /**
     * Get last detected camera result
     */
    fun getLastCameraResult(): CameraPositionDetector.CameraDetectionResult? = 
        positionValidator.getLastCameraResult()
    
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

