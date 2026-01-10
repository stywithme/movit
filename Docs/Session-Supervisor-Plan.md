# Session Supervisor - Unified State Machine Plan

## Overview

### Problem Statement
The current system has **two separate layers** managing training state:
1. **UI Layer** (`TrainingStateManager`): Controls frame forwarding to engine
2. **Engine Layer** (`TrainingEngine` + `VisibilityMonitor`): Processes frames and emits events

This creates **inconsistencies**:
- Auto-pause from visibility doesn't call `engine.pause()`, it just stops forwarding frames
- Resume after visibility loss incorrectly calls `startTraining()` instead of `resumeFromVisibilityPause()`
- `onNoPoseDetected()` during TRAINING has no auto-pause path
- No mechanism to detect/handle exercise mismatch

### Solution: Unified Session Supervisor
A single **`SessionSupervisor`** component that:
- Owns the **Single Source of Truth** for session state
- Receives **all signals** (UI commands, pose signals, engine events)
- Issues **all commands** to engine and UI
- Handles both **Camera** and **Video** modes with appropriate behavior

---

## State Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SESSION SUPERVISOR STATES                          │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌──────────────┐
                              │    IDLE      │ ◄─── Initial state
                              └──────┬───────┘
                                     │ loadExercise()
                                     ▼
                              ┌──────────────┐
                              │ SETUP_POSE   │ ◄─── Waiting for valid start pose
                              └──────┬───────┘      (10 consecutive valid frames)
                                     │
                    ┌────────────────┼────────────────┐
                    │                │                │
              [VideoMode]      [PoseConfirmed]   [NoPose/Exit]
                    │                │                │
                    ▼                ▼                │
             ┌──────────────┐ ┌──────────────┐        │
             │  TRAINING    │ │  COUNTDOWN   │        │
             │ (immediate)  │ └──────┬───────┘        │
             └──────┬───────┘        │                │
                    │          [CountdownDone]        │
                    │                │                │
                    └────────────────┼────────────────┘
                                     ▼
                              ┌──────────────┐
                              │   TRAINING   │ ◄─── Active training/analysis
                              └──────┬───────┘
                                     │
          ┌──────────────────────────┼──────────────────────────┐
          │                          │                          │
    [ManualPause]          [AutoPause Triggers]          [TargetReached]
          │                          │                          │
          ▼                          ▼                          ▼
   ┌──────────────┐          ┌──────────────┐           ┌──────────────┐
   │    PAUSED    │          │ AUTO_PAUSED  │           │  COMPLETED   │
   │   (Manual)   │          │   (Auto)     │           └──────────────┘
   └──────┬───────┘          └──────┬───────┘
          │                          │
    [ManualResume]           [SignalRestored]
          │                          │
          ▼                          ▼
   ┌──────────────┐          ┌──────────────┐
   │  COUNTDOWN   │          │RESUME_SETUP  │ ◄─── Validate pose before resume
   │  (Resume)    │          └──────┬───────┘
   └──────┬───────┘                 │
          │                   [PoseConfirmed]
          │                         │
          │                         ▼
          │                  ┌──────────────┐
          │                  │RESUME_COUNT  │ ◄─── 3-2-1-GO countdown
          │                  └──────┬───────┘
          │                         │
          └─────────────────────────┼─────────────────────────────┐
                                    │                             │
                              [CountdownDone]              [PoseLost/Cancel]
                                    │                             │
                                    ▼                             │
                         Return to TRAINING ◄─────────────────────┘
                         (preserving rep count)            Back to AUTO_PAUSED
```

---

## States Definition

| State | Description | Engine State | Frames Processed |
|-------|-------------|--------------|------------------|
| `IDLE` | No exercise loaded | stopped | No |
| `SETUP_POSE` | Waiting for valid start position | stopped | Validation only |
| `COUNTDOWN` | 3-2-1-GO before training starts | stopped | Validation only |
| `TRAINING` | Active training/analysis | running | Yes |
| `PAUSED` | Manual pause by user | paused | No |
| `AUTO_PAUSED` | Automatic pause (visibility/noPose/mismatch) | paused | No |
| `RESUME_SETUP` | After auto-pause, validating pose before resume | paused | Validation only |
| `RESUME_COUNTDOWN` | 3-2-1-GO before resume | paused | Validation only |
| `COMPLETED` | Target reached or video ended | stopped | No |

---

## Auto-Pause Triggers (with timings)

### 1. Visibility Loss (joints not visible)
```
Timeline:  0s ──────── 1s ──────── 2s ──────── 4s
           │           │           │           │
        [Invisible] [Grace OK]  [Warning]   [AUTO_PAUSE]
                       │           │           │
                    Continue    Show msg    Pause + Save state
```

### 2. No Pose Detected (MediaPipe returns no pose)
```
Timeline:  0s ──────── 1s ──────── 2s ──────── 4s
           │           │           │           │
        [NoPose]    [Grace OK]  [Warning]   [AUTO_PAUSE]
                       │           │           │
                    Continue   "Step back    Pause + Save state
                               into frame"
```

### 3. Exercise Mismatch (wrong exercise detected)
```
Timeline:  0s ──────── 2s ──────── 4s ──────── 6s
           │           │           │           │
        [Mismatch] [Grace OK]  [Warning]   [AUTO_PAUSE]
                       │           │           │
                    Continue   "Wrong        Pause + Show
                               position"     correction guide
```

---

## Events & Actions

### Input Events (Signals to Supervisor)

```kotlin
sealed class SupervisorSignal {
    // ==================== UI Commands ====================
    object StartRequested : SupervisorSignal()
    object PauseRequested : SupervisorSignal()
    object ResumeRequested : SupervisorSignal()
    object StopRequested : SupervisorSignal()
    
    // ==================== Pose Signals ====================
    data class PoseDetected(
        val angles: JointAngles,
        val landmarks: List<SmoothedLandmark>,
        val confidence: Float
    ) : SupervisorSignal()
    
    object NoPoseDetected : SupervisorSignal()
    
    // ==================== Quality Signals ====================
    data class TrackingQuality(
        val level: QualityLevel,        // GOOD, DEGRADED, BAD
        val invisibleJoints: List<String>,
        val mismatchScore: Float        // 0.0 = match, 1.0 = mismatch
    ) : SupervisorSignal()
    
    // ==================== Engine Signals ====================
    object TargetReached : SupervisorSignal()
    object HoldCompleted : SupervisorSignal()
    
    // ==================== Countdown Signals ====================
    data class CountdownTick(val remaining: Int) : SupervisorSignal()
    object CountdownFinished : SupervisorSignal()
    object CountdownCancelled : SupervisorSignal()
    
    // ==================== Video Signals ====================
    object VideoEnded : SupervisorSignal()
    object VideoSeeked : SupervisorSignal()
}

enum class QualityLevel { GOOD, DEGRADED, BAD }
```

### Output Actions (Commands from Supervisor)

```kotlin
sealed class SupervisorAction {
    // ==================== Engine Commands ====================
    object StartEngine : SupervisorAction()
    object PauseEngine : SupervisorAction()
    object ResumeEngine : SupervisorAction()
    object StopEngine : SupervisorAction()
    object ResumeFromVisibilityPause : SupervisorAction()
    object ResetEngineKeepReps : SupervisorAction()
    
    // ==================== UI Commands ====================
    data class ShowState(val state: SessionState) : SupervisorAction()
    data class ShowWarning(val type: WarningType, val message: String) : SupervisorAction()
    object ShowSetupPose : SupervisorAction()
    object StartCountdown : SupervisorAction()
    object CancelCountdown : SupervisorAction()
    data class ShowPauseReason(val reason: PauseReason) : SupervisorAction()
    object ShowCompleted : SupervisorAction()
    
    // ==================== Feedback Commands ====================
    data class SpeakMessage(val text: String) : SupervisorAction()
    data class Vibrate(val pattern: VibratePattern) : SupervisorAction()
}

enum class WarningType { VISIBILITY, NO_POSE, MISMATCH }
enum class PauseReason { MANUAL, VISIBILITY, NO_POSE, MISMATCH }
enum class VibratePattern { WARNING, ERROR, SUCCESS }
```

---

## State Transitions Table

| Current State | Signal | Condition | Next State | Actions |
|---------------|--------|-----------|------------|---------|
| IDLE | loadExercise | success | SETUP_POSE | ShowSetupPose |
| SETUP_POSE | PoseDetected | isStartPoseValid × 10 frames | COUNTDOWN | StartCountdown |
| SETUP_POSE | NoPoseDetected | - | SETUP_POSE | ShowWarning(NO_POSE) |
| COUNTDOWN | CountdownFinished | - | TRAINING | StartEngine, ShowState |
| COUNTDOWN | PoseDetected | !isStartPoseValid | SETUP_POSE | CancelCountdown |
| COUNTDOWN | NoPoseDetected | - | SETUP_POSE | CancelCountdown |
| TRAINING | PauseRequested | - | PAUSED | PauseEngine |
| TRAINING | TrackingQuality | level=BAD for 4s | AUTO_PAUSED | PauseEngine, ShowPauseReason |
| TRAINING | NoPoseDetected | for 4s (with grace/warn) | AUTO_PAUSED | PauseEngine, ShowPauseReason |
| TRAINING | TargetReached | - | COMPLETED | StopEngine, ShowCompleted |
| TRAINING | VideoEnded | isVideoMode | COMPLETED | StopEngine, ShowCompleted |
| PAUSED | ResumeRequested | - | COUNTDOWN | ShowSetupPose, then StartCountdown |
| AUTO_PAUSED | TrackingQuality | level=GOOD | RESUME_SETUP | ShowSetupPose |
| RESUME_SETUP | PoseDetected | isStartPoseValid × 10 frames | RESUME_COUNTDOWN | StartCountdown |
| RESUME_SETUP | TrackingQuality | level=BAD | AUTO_PAUSED | ShowPauseReason |
| RESUME_COUNTDOWN | CountdownFinished | - | TRAINING | ResumeFromVisibilityPause |
| RESUME_COUNTDOWN | PoseDetected | !isStartPoseValid | AUTO_PAUSED | CancelCountdown |
| * | StopRequested | - | COMPLETED | StopEngine |

---

## Implementation Plan

### Phase 1: Create SessionSupervisor Class

**File:** `training/session/SessionSupervisor.kt`

```kotlin
class SessionSupervisor(
    private val config: SupervisorConfig = SupervisorConfig()
) {
    // ==================== State ====================
    private val _state = MutableStateFlow(SessionState.IDLE)
    val state: StateFlow<SessionState> = _state.asStateFlow()
    
    private val _actions = MutableSharedFlow<SupervisorAction>(extraBufferCapacity = 20)
    val actions: SharedFlow<SupervisorAction> = _actions.asSharedFlow()
    
    // ==================== Tracking ====================
    private var pauseReason: PauseReason? = null
    private var savedRepCount: Int = 0
    private var isVideoMode: Boolean = false
    
    // ==================== Quality Monitors ====================
    private val qualityMonitor = TrackingQualityMonitor(
        graceMs = config.graceMs,
        warnMs = config.warnMs,
        pauseMs = config.pauseMs
    )
    
    // ==================== Public API ====================
    fun setVideoMode(enabled: Boolean)
    fun processSignal(signal: SupervisorSignal)
    fun getCurrentState(): SessionState
    fun getSavedRepCount(): Int
}
```

### Phase 2: Create TrackingQualityMonitor

**File:** `training/session/TrackingQualityMonitor.kt`

```kotlin
class TrackingQualityMonitor(
    private val graceMs: Long = 1000,
    private val warnMs: Long = 2000,
    private val pauseMs: Long = 4000
) {
    private val _qualityLevel = MutableStateFlow(QualityLevel.GOOD)
    val qualityLevel: StateFlow<QualityLevel> = _qualityLevel.asStateFlow()
    
    private var degradedStartTime: Long = 0
    
    /**
     * Update quality based on current frame data
     * Returns action suggestion (NONE, WARN, PAUSE)
     */
    fun update(
        hasLandmarks: Boolean,
        requiredJointsVisible: Boolean,
        mismatchScore: Float
    ): QualityAction
    
    fun reset()
}

enum class QualityAction { NONE, WARN, PAUSE }
```

### Phase 3: Integrate with TrainingViewModel

**Changes to:** `ui/training/TrainingViewModel.kt`

```kotlin
class TrainingViewModel(private val assets: AssetManager) : ViewModel() {
    
    // ==================== NEW: Session Supervisor ====================
    val sessionSupervisor = SessionSupervisor()
    
    // DEPRECATE: Replace TrainingStateManager with SessionSupervisor
    // val stateManager = TrainingStateManager()  // Remove
    
    init {
        // Observe supervisor actions
        viewModelScope.launch {
            sessionSupervisor.actions.collect { action ->
                executeAction(action)
            }
        }
    }
    
    // ==================== Frame Processing ====================
    fun processFrame(angles: JointAngles, landmarks: List<SmoothedLandmark>?, isFrontCamera: Boolean) {
        // Always send signal to supervisor (it decides what to do)
        sessionSupervisor.processSignal(
            SupervisorSignal.PoseDetected(angles, landmarks, calculateConfidence(landmarks))
        )
    }
    
    fun onNoPoseDetected() {
        sessionSupervisor.processSignal(SupervisorSignal.NoPoseDetected)
    }
    
    // ==================== Execute Actions ====================
    private fun executeAction(action: SupervisorAction) {
        when (action) {
            is SupervisorAction.StartEngine -> trainingEngine?.start()
            is SupervisorAction.PauseEngine -> trainingEngine?.pause()
            is SupervisorAction.ResumeEngine -> trainingEngine?.resume()
            is SupervisorAction.ResumeFromVisibilityPause -> trainingEngine?.resumeFromVisibilityPause()
            is SupervisorAction.StopEngine -> trainingEngine?.stop()
            // ... UI actions forwarded to Activity via events
        }
    }
}
```

### Phase 4: Update TrainingActivity

**Changes to:** `ui/TrainingActivity.kt`

```kotlin
// Replace stateManager observation with sessionSupervisor.state
lifecycleScope.launch {
    viewModel.sessionSupervisor.state.collectLatest { state ->
        updateUIForSessionState(state)
    }
}

// Handle NoPoseDetected properly
override fun onNoPoseDetected() {
    lifecycleScope.launch(Dispatchers.Main) {
        binding.skeletonOverlay.clear()
        // NEW: Always notify supervisor (not just in SETUP_POSE)
        viewModel.onNoPoseDetected()
    }
}

// Countdown now respects pause reason
private fun setupCountdownController() {
    viewModel.countdownController.setListener(object : CountdownController.CountdownListener {
        override fun onFinish() {
            // NEW: Supervisor decides start vs resume
            viewModel.sessionSupervisor.processSignal(SupervisorSignal.CountdownFinished)
        }
        // ...
    })
}
```

### Phase 5: Remove VisibilityMonitor from TrainingEngine

**Changes to:** `training/TrainingEngine.kt`

```kotlin
// REMOVE: VisibilityMonitor (now handled by SessionSupervisor)
// private val visibilityMonitor: VisibilityMonitor = ...

fun processFrame(angles: JointAngles, landmarks: List<SmoothedLandmark>?, isFrontCamera: Boolean) {
    if (!isRunning || isPaused) return
    
    synchronized(stateLock) {
        // REMOVE: Visibility checking (handled by supervisor)
        // if (landmarks != null) {
        //     val visibilityResult = visibilityMonitor.checkVisibility(...)
        // }
        
        // Keep: Phase detection, form validation, rep counting
        // ...
    }
}
```

---

## File Structure

```
training/
├── session/
│   ├── SessionSupervisor.kt          # Main state machine
│   ├── SessionState.kt               # State enum
│   ├── SupervisorSignal.kt           # Input events
│   ├── SupervisorAction.kt           # Output actions
│   ├── SupervisorConfig.kt           # Timing configuration
│   └── TrackingQualityMonitor.kt     # Quality tracking with timers
├── engine/
│   ├── TrainingEngine.kt             # MODIFIED: Remove visibility logic
│   ├── PhaseStateMachine.kt          # Unchanged
│   ├── FormValidator.kt              # Unchanged
│   └── ...
└── ...
```

---

## Migration Strategy

### Step 1: Add New Classes (Non-breaking)
- Create `session/` package with all new classes
- Add `SessionSupervisor` to `TrainingViewModel` alongside existing `TrainingStateManager`

### Step 2: Parallel Operation
- Both `TrainingStateManager` and `SessionSupervisor` run
- Log discrepancies to validate new logic
- UI still follows `TrainingStateManager`

### Step 3: Switch UI to Supervisor
- Change UI observers from `stateManager.state` to `sessionSupervisor.state`
- Remove `TrainingStateManager` usage from `TrainingViewModel`

### Step 4: Clean Up Engine
- Remove `VisibilityMonitor` from `TrainingEngine`
- Remove visibility-related state flows from engine
- Engine becomes pure "processor"

### Step 5: Delete Deprecated Code
- Remove `TrainingStateManager.kt`
- Remove `VisibilityMonitor.kt` (logic moved to `TrackingQualityMonitor`)

---

## Video Mode Specifics

| Scenario | Camera Mode | Video Mode |
|----------|-------------|------------|
| No Pose | Grace → Warn → AUTO_PAUSE | Grace → Warn → AUTO_PAUSE (same) |
| Visibility Loss | Grace → Warn → AUTO_PAUSE | Grace → Warn → AUTO_PAUSE (same) |
| Mismatch | Grace → Warn → AUTO_PAUSE | Grace → Warn → AUTO_PAUSE (same) |
| Manual Pause | Pause immediately | Pause video playback |
| Seek | N/A | Reset engine state, continue from seek point |
| Video End | N/A | COMPLETED state, calculate final frame |

---

## Configuration

```kotlin
data class SupervisorConfig(
    // Quality timing
    val graceMs: Long = 1000,           // 1s - ignore brief glitches
    val warnMs: Long = 2000,            // 2s - show warning
    val pauseMs: Long = 4000,           // 4s - auto pause
    
    // Mismatch timing (longer grace for movement variations)
    val mismatchGraceMs: Long = 2000,   // 2s grace
    val mismatchWarnMs: Long = 4000,    // 4s warning
    val mismatchPauseMs: Long = 6000,   // 6s pause
    
    // Start pose validation
    val requiredValidFrames: Int = 10,  // Frames to confirm pose
    
    // Countdown
    val countdownSeconds: Int = 3       // 3-2-1-GO
)
```

---

## Testing Checklist

### Camera Mode Tests
- [ ] Start → SETUP_POSE → valid pose → COUNTDOWN → TRAINING
- [ ] TRAINING → manual pause → PAUSED → resume → COUNTDOWN → TRAINING
- [ ] TRAINING → visibility loss → warning at 2s → AUTO_PAUSED at 4s
- [ ] AUTO_PAUSED → visibility restored → RESUME_SETUP → valid pose → RESUME_COUNTDOWN → TRAINING (rep count preserved)
- [ ] TRAINING → no pose → warning at 2s → AUTO_PAUSED at 4s
- [ ] TRAINING → mismatch → warning at 4s → AUTO_PAUSED at 6s
- [ ] TRAINING → target reached → COMPLETED
- [ ] Any state → stop button → COMPLETED

### Video Mode Tests
- [ ] Video start → immediate TRAINING (skip SETUP_POSE)
- [ ] TRAINING → visibility loss → same behavior as camera
- [ ] TRAINING → manual pause → pause playback
- [ ] TRAINING → seek → reset engine, continue from seek point
- [ ] TRAINING → video end → COMPLETED

### Edge Cases
- [ ] Pose lost during COUNTDOWN → back to SETUP_POSE
- [ ] Pose lost during RESUME_COUNTDOWN → back to AUTO_PAUSED
- [ ] Multiple rapid pause/resume → stable state
- [ ] Very fast visibility flicker (< 1s) → ignored (grace period)

---

## Summary

This plan introduces a **unified SessionSupervisor** that:

1. **Single Source of Truth**: One component owns session state
2. **Clear Separation**: Supervisor decides, Engine processes, UI displays
3. **Proper Resume**: Distinguishes between fresh start and resume from pause
4. **Consistent Auto-Pause**: Same path for visibility, noPose, and mismatch
5. **Mode-Aware**: Appropriate behavior for Camera vs Video mode
6. **Testable**: Clear events/actions make unit testing straightforward
7. **Minimal Disruption**: Gradual migration path, reuses existing components

The implementation follows the existing project patterns (Kotlin, StateFlow, coroutines) while fixing the fundamental issues in session lifecycle management.
