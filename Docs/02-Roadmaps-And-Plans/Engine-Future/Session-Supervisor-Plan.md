# Workout Run Supervisor - Unified State Machine Plan

## Overview

### Problem Statement
The current system has **two separate layers** managing training state:
1. **UI Layer** (`TrainingStateManager`): Controls frame forwarding to engine
2. **Engine Layer** (`TrainingEngine` + `VisibilityMonitor`): Processes frames and emits events

This creates **inconsistencies**:
- Auto-pause from visibility doesn't call `engine.pause()`, it just stops forwarding frames
- Resume after visibility loss incorrectly calls `startTraining()` instead of `resumeFromVisibilityPause()`
- `onNoPoseDetected()` during TRAINING has no auto-pause path

### Solution: Unified Workout Run Supervisor
A single **`WorkoutRunSupervisor`** component that:
- Owns the **Single Source of Truth** for planned workout state
- Receives signals from UI, Pose Detection, and Engine
- Issues commands to Engine and UI
- Works for both **Camera** and **Video** modes

---

## Design Principles

1. **Simplicity First**: No over-engineering. Use existing components where possible.
2. **Reuse Engine's VisibilityMonitor**: Don't recreate visibility logic. Supervisor listens to Engine events.
3. **No Mismatch Detection (Phase 1)**: Focus on core issues first. Mismatch can be added later.
4. **No Complex Threading**: All signals arrive from Main thread / viewModelScope.
5. **Mode-Aware Behavior**: Simple `if (isVideoMode)` checks, not complex Policy objects.

---

## State Diagram

```
                              ┌──────────────┐
                              │    IDLE      │
                              └──────┬───────┘
                                     │ loadExercise()
                                     ▼
                              ┌──────────────┐
                              │ SETUP_POSE   │ ◄─── PoseValidator (10 valid frames)
                              └──────┬───────┘
                                     │
                    ┌────────────────┼────────────────┐
                    │                │                │
              [VideoMode]      [PoseConfirmed]   [NoPose]
                    │                │                │
                    ▼                ▼                │
             ┌──────────────┐ ┌──────────────┐        │
             │  TRAINING    │ │  COUNTDOWN   │        │
             │ (immediate)  │ └──────┬───────┘        │
             └──────┬───────┘        │                │
                    │          [CountdownDone]        │
                    │                │                │
                    └────────────────┴────────────────┘
                                     │
                              ┌──────────────┐
                              │   TRAINING   │
                              └──────┬───────┘
                                     │
          ┌──────────────────────────┼──────────────────────────┐
          │                          │                          │
    [ManualPause]          [VisibilityPaused]            [TargetReached]
          │                   [NoPose 4s]                       │
          ▼                          ▼                          ▼
   ┌──────────────┐          ┌──────────────┐           ┌──────────────┐
   │    PAUSED    │          │ AUTO_PAUSED  │           │  COMPLETED   │
   │   (Manual)   │          └──────┬───────┘           └──────────────┘
   └──────┬───────┘                 │
          │                  [VisibilityRestored]
    [ManualResume]                  │
          │                         ▼
          │                  ┌──────────────┐
          │                  │RESUME_SETUP  │ ◄─── PoseValidator again
          │                  └──────┬───────┘
          │                         │
          │                   [PoseConfirmed]
          │                         │
          ▼                         ▼
   ┌──────────────┐          ┌──────────────┐
   │  COUNTDOWN   │          │RESUME_COUNT  │
   └──────┬───────┘          └──────┬───────┘
          │                         │
          │                   [CountdownDone]
          │                         │
          └─────────────────────────┴─────────────────────────────┐
                                    │                             │
                                    ▼                             │
                         Return to TRAINING ◄─────────────────────┘
                         (preserving rep count)
```

---

## States Definition

| State | Description | Engine State | Process Frames |
|-------|-------------|--------------|----------------|
| `IDLE` | No exercise loaded | stopped | No |
| `SETUP_POSE` | Validating start position | stopped | PoseValidator only |
| `COUNTDOWN` | 3-2-1-GO | stopped | PoseValidator only |
| `TRAINING` | Active training | running | Yes - Engine.processFrame() |
| `PAUSED` | Manual pause | paused | No |
| `AUTO_PAUSED` | Auto pause (visibility/noPose) | paused | No |
| `RESUME_SETUP` | Validating pose before resume | paused | PoseValidator only |
| `RESUME_COUNTDOWN` | 3-2-1-GO before resume | paused | PoseValidator only |
| `COMPLETED` | Training finished | stopped | No |

---

## Signals & Actions

### Input Signals

```kotlin
sealed class SupervisorSignal {
    // UI Commands
    object StartRequested : SupervisorSignal()
    object PauseRequested : SupervisorSignal()
    object ResumeRequested : SupervisorSignal()
    object StopRequested : SupervisorSignal()
    
    // Pose Data (from Activity)
    data class PoseFrame(
        val angles: JointAngles,
        val landmarks: List<SmoothedLandmark>?,
        val isFrontCamera: Boolean
    ) : SupervisorSignal()
    
    object NoPoseFrame : SupervisorSignal()

    // Pose validation result (from ViewModel after PoseValidator confirms)
    object PoseConfirmed : SupervisorSignal()
    
    // Engine Events (forwarded from TrainingEngine)
    object TargetReached : SupervisorSignal()
    object VisibilityPaused : SupervisorSignal()  // From Engine's VisibilityMonitor
    object VisibilityRestored : SupervisorSignal()
    
    // Countdown Events
    object CountdownFinished : SupervisorSignal()
    
    // Video Events
    object VideoEnded : SupervisorSignal()
    object VideoSeeked : SupervisorSignal()
}
```

### Output Actions

```kotlin
sealed class SupervisorAction {
    // Engine Commands
    object StartEngine : SupervisorAction()
    object PauseEngine : SupervisorAction()
    object ResumeEngine : SupervisorAction()
    object StopEngine : SupervisorAction()
    object ResumeFromVisibilityPause : SupervisorAction()
    
    // Frame Processing
    data class ProcessFrame(
        val angles: JointAngles,
        val landmarks: List<SmoothedLandmark>?,
        val isFrontCamera: Boolean
    ) : SupervisorAction()
    
    data class ValidatePose(
        val angles: JointAngles
    ) : SupervisorAction()
    
    // UI Commands
    object ShowSetupPose : SupervisorAction()
    object StartCountdown : SupervisorAction()
    object CancelCountdown : SupervisorAction()
    data class ShowAutoPaused(val reason: PauseReason) : SupervisorAction()
    object ShowCompleted : SupervisorAction()
    
    // Video-specific
    object PauseVideo : SupervisorAction()
}

enum class PauseReason { MANUAL, VISIBILITY, NO_POSE }
```

---

## Key Transitions

| State | Signal | Next State | Actions |
|-------|--------|------------|---------|
| IDLE | loadExercise() | SETUP_POSE | ShowSetupPose |
| SETUP_POSE | PoseConfirmed | COUNTDOWN | StartCountdown |
| SETUP_POSE [VideoMode] | StartRequested | TRAINING | StartEngine |
| COUNTDOWN | CountdownFinished | TRAINING | StartEngine |
| COUNTDOWN | PoseFrame (invalid) | SETUP_POSE | CancelCountdown |
| COUNTDOWN | NoPoseFrame | SETUP_POSE | CancelCountdown |
| TRAINING | PoseFrame | TRAINING | ProcessFrame |
| TRAINING | PauseRequested | PAUSED | PauseEngine |
| TRAINING | VisibilityPaused | AUTO_PAUSED | PauseEngine, ShowAutoPaused |
| TRAINING | NoPoseFrame (4s) | AUTO_PAUSED | PauseEngine, ShowAutoPaused |
| TRAINING | TargetReached | COMPLETED | StopEngine, ShowCompleted |
| TRAINING | VideoEnded | COMPLETED | StopEngine, ShowCompleted |
| PAUSED | ResumeRequested | COUNTDOWN | StartCountdown |
| AUTO_PAUSED | VisibilityRestored | RESUME_SETUP | ShowSetupPose |
| RESUME_SETUP | PoseConfirmed | RESUME_COUNTDOWN | StartCountdown |
| RESUME_COUNTDOWN | CountdownFinished | TRAINING | **ResumeFromVisibilityPause** |
| RESUME_COUNTDOWN | PoseFrame (invalid) | AUTO_PAUSED | CancelCountdown |
| * | StopRequested | COMPLETED | StopEngine [+PauseVideo if VideoMode] |

---

## Implementation

### File Structure

> **ملاحظة:** اسم مجلد `session/` في Android تاريخي لمكوّنات workout-run (Rep/Hold coordinators). المشرف الموحّد هو `WorkoutRunSupervisor` — لا يُخلط مع `PlannedWorkout` أو assessment session.

```
training/
├── session/   <!-- legacy package segment: workout-run coordinators only -->
│   ├── WorkoutRunSupervisor.kt      # State machine
│   ├── WorkoutRunState.kt           # State enum  
│   ├── SupervisorSignal.kt       # Input signals
│   └── SupervisorAction.kt       # Output actions
└── engine/
    ├── TrainingEngine.kt         # UNCHANGED - keeps VisibilityMonitor
    └── ...
```

### WorkoutRunSupervisor.kt

```kotlin
class WorkoutRunSupervisor {
    
    private val _state = MutableStateFlow(WorkoutRunState.IDLE)
    val state: StateFlow<WorkoutRunState> = _state.asStateFlow()
    
    private val _actions = MutableSharedFlow<SupervisorAction>(extraBufferCapacity = 16)
    val actions: SharedFlow<SupervisorAction> = _actions
    
    var isVideoMode: Boolean = false
    
    // NoPose tracking (for 4s auto-pause)
    private var noPoseStartTime: Long = 0L
    private val noPoseGraceMs = 1000L
    private val noPoseWarnMs = 2000L  
    private val noPosePauseMs = 4000L
    
    // Pause reason
    private var pauseReason: PauseReason? = null
    
    fun processSignal(signal: SupervisorSignal) {
        when (_state.value) {
            WorkoutRunState.IDLE -> handleIdle(signal)
            WorkoutRunState.SETUP_POSE -> handleSetupPose(signal)
            WorkoutRunState.COUNTDOWN -> handleCountdown(signal)
            WorkoutRunState.TRAINING -> handleTraining(signal)
            WorkoutRunState.PAUSED -> handlePaused(signal)
            WorkoutRunState.AUTO_PAUSED -> handleAutoPaused(signal)
            WorkoutRunState.RESUME_SETUP -> handleResumeSetup(signal)
            WorkoutRunState.RESUME_COUNTDOWN -> handleResumeCountdown(signal)
            WorkoutRunState.COMPLETED -> handleCompleted(signal)
        }
    }
    
    private fun handleTraining(signal: SupervisorSignal) {
        when (signal) {
            is SupervisorSignal.PoseFrame -> {
                noPoseStartTime = 0L  // Reset NoPose timer
                emit(SupervisorAction.ProcessFrame(signal.angles, signal.landmarks, signal.isFrontCamera))
            }
            
            is SupervisorSignal.NoPoseFrame -> {
                handleNoPose()
            }
            
            is SupervisorSignal.VisibilityPaused -> {
                pauseReason = PauseReason.VISIBILITY
                transitionTo(WorkoutRunState.AUTO_PAUSED)
                emit(SupervisorAction.PauseEngine)
                emit(SupervisorAction.ShowAutoPaused(PauseReason.VISIBILITY))
            }
            
            is SupervisorSignal.PauseRequested -> {
                pauseReason = PauseReason.MANUAL
                transitionTo(WorkoutRunState.PAUSED)
                emit(SupervisorAction.PauseEngine)
                if (isVideoMode) emit(SupervisorAction.PauseVideo)
            }
            
            is SupervisorSignal.TargetReached,
            is SupervisorSignal.VideoEnded -> {
                transitionTo(WorkoutRunState.COMPLETED)
                emit(SupervisorAction.StopEngine)
                emit(SupervisorAction.ShowCompleted)
            }
            
            is SupervisorSignal.StopRequested -> {
                transitionTo(WorkoutRunState.COMPLETED)
                if (isVideoMode) emit(SupervisorAction.PauseVideo)
                emit(SupervisorAction.StopEngine)
                emit(SupervisorAction.ShowCompleted)
            }
            
            else -> {}
        }
    }
    
    private fun handleNoPose() {
        val now = System.currentTimeMillis()
        
        if (noPoseStartTime == 0L) {
            noPoseStartTime = now
            return
        }
        
        val duration = now - noPoseStartTime
        
        when {
            duration >= noPosePauseMs -> {
                pauseReason = PauseReason.NO_POSE
                transitionTo(WorkoutRunState.AUTO_PAUSED)
                emit(SupervisorAction.PauseEngine)
                emit(SupervisorAction.ShowAutoPaused(PauseReason.NO_POSE))
                if (isVideoMode) emit(SupervisorAction.PauseVideo)
                noPoseStartTime = 0L
            }
            duration >= noPoseWarnMs -> {
                // Warning handled by UI (already shows "No pose detected")
            }
            // < graceMs: ignore
        }
    }
    
    private fun handleResumeCountdown(signal: SupervisorSignal) {
        when (signal) {
            is SupervisorSignal.CountdownFinished -> {
                transitionTo(WorkoutRunState.TRAINING)
                emit(SupervisorAction.ResumeFromVisibilityPause)  // KEY: preserves rep count
            }
            
            is SupervisorSignal.PoseFrame -> {
                // Validate pose during countdown
                emit(SupervisorAction.ValidatePose(signal.angles))
            }
            
            is SupervisorSignal.NoPoseFrame -> {
                transitionTo(WorkoutRunState.AUTO_PAUSED)
                emit(SupervisorAction.CancelCountdown)
            }
            
            else -> {}
        }
    }
    
    // ... other handlers follow same pattern
}
```

### Integration with TrainingViewModel

```kotlin
class TrainingViewModel(private val assets: AssetManager) : ViewModel() {
    
    val supervisor = WorkoutRunSupervisor()
    val poseValidator = PoseValidator()
    
    init {
        // Listen to supervisor actions
        viewModelScope.launch {
            supervisor.actions.collect { action ->
                executeAction(action)
            }
        }
    }
    
    // Called from Activity for every frame
    fun onPoseFrame(angles: JointAngles, landmarks: List<SmoothedLandmark>?, isFrontCamera: Boolean) {
        supervisor.processSignal(SupervisorSignal.PoseFrame(angles, landmarks, isFrontCamera))
    }
    
    fun onNoPoseDetected() {
        supervisor.processSignal(SupervisorSignal.NoPoseFrame)
    }
    
    private fun executeAction(action: SupervisorAction) {
        when (action) {
            is SupervisorAction.StartEngine -> trainingEngine?.start()
            is SupervisorAction.PauseEngine -> trainingEngine?.pause()
            is SupervisorAction.StopEngine -> trainingEngine?.stop()
            is SupervisorAction.ResumeFromVisibilityPause -> trainingEngine?.resumeFromVisibilityPause()
            
            is SupervisorAction.ProcessFrame -> {
                trainingEngine?.processFrame(action.angles, action.landmarks, action.isFrontCamera)
            }
            
            is SupervisorAction.ValidatePose -> {
                val result = poseValidator.validate(action.angles, exerciseConfig.value, poseVariantIndex.value)
                // Handle validation result...
                if (result.isConfirmed) {
                    supervisor.processSignal(SupervisorSignal.PoseConfirmed)
                }
            }
            
            // UI actions forwarded to Activity via events
            is SupervisorAction.ShowSetupPose -> emitUIEvent(...)
            is SupervisorAction.StartCountdown -> countdownController.start()
            // ...
        }
    }
    
    // Forward Engine events to Supervisor
    private fun observeTrainingEngine() {
        viewModelScope.launch {
            engine.events.collect { event ->
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
                    else -> {
                        // Forward to FeedbackManager as before
                        feedbackManager?.emit(event)
                    }
                }
            }
        }
    }
}
```

### TrainingActivity Changes

```kotlin
// Replace direct stateManager calls with supervisor signals

override fun onPoseDetected(result: PoseResult) {
    // ... calculate angles ...
    viewModel.onPoseFrame(angles, smoothedLandmarks, result.isFrontCamera)
}

override fun onNoPoseDetected() {
    binding.skeletonOverlay.clear()
    viewModel.onNoPoseDetected()  // NEW: Always notify supervisor
}

// Observe supervisor state instead of stateManager
lifecycleScope.launch {
    viewModel.supervisor.state.collectLatest { state ->
        updateUIForState(state)
    }
}

// Countdown signals supervisor
viewModel.countdownController.setListener(object : CountdownController.CountdownListener {
    override fun onFinish() {
        viewModel.supervisor.processSignal(SupervisorSignal.CountdownFinished)
    }
    // ...
})
```

---

## What Stays Unchanged

1. **`TrainingEngine`** - Keeps `VisibilityMonitor` and all existing logic
2. **`FeedbackManager`** - Continues handling form feedback from Engine
3. **`ReportGenerator`** - Uses `engine.getVisibilityStats()` as before
4. **`PoseValidator`** - Same logic, just called via Supervisor actions
5. **`CountdownController`** - Same component, signals routed through Supervisor

---

## Migration Steps

### Step 1: Create Planned Workout Package
- Create `training/session/` with `WorkoutRunSupervisor`, `WorkoutRunState`, `SupervisorSignal`, `SupervisorAction`
- Pure logic, no dependencies on UI

### Step 2: Add Supervisor to ViewModel
- Add `supervisor` property
- Add action execution logic
- Add Engine event forwarding to Supervisor
- **Keep** `stateManager` temporarily for comparison

### Step 3: Update Activity
- Change `onNoPoseDetected()` to always call `viewModel.onNoPoseDetected()`
- Route countdown finish through Supervisor
- Observe `supervisor.state` instead of `stateManager.state`

### Step 4: Remove Old Code
- Remove `TrainingStateManager`
- Remove state-gating in `processFrame()` (Supervisor handles it)

---

## Video Mode Behavior

| Event | Camera Mode | Video Mode |
|-------|-------------|------------|
| NoPose 4s | AUTO_PAUSE | AUTO_PAUSE + Pause Video |
| VisibilityPaused | AUTO_PAUSE | AUTO_PAUSE + Pause Video |
| Manual Pause | PAUSE | PAUSE + Pause Video |
| Manual Stop | COMPLETED | Pause Video + COMPLETED |
| Seek | N/A | Reset Engine (stop+start) |
| Video End | N/A | COMPLETED |

---

## Testing Checklist

### Core Flow
- [ ] SETUP_POSE → valid pose → COUNTDOWN → TRAINING
- [ ] TRAINING → manual pause → PAUSED → resume → COUNTDOWN → TRAINING
- [ ] TRAINING → NoPose 4s → AUTO_PAUSED
- [ ] AUTO_PAUSED → visibility restored → RESUME_SETUP → valid pose → RESUME_COUNTDOWN → TRAINING (**rep count preserved**)
- [ ] TRAINING → target reached → COMPLETED

### Video Mode
- [ ] Video start → immediate TRAINING
- [ ] NoPose → AUTO_PAUSE + video paused
- [ ] Manual stop → video paused + COMPLETED

### Edge Cases
- [ ] Pose lost during COUNTDOWN → back to SETUP_POSE
- [ ] NoPose < 1s (grace) → ignored
- [ ] Rapid pause/resume → stable

---

## Future Enhancements (Phase 2)

1. **Mismatch Detection**: Detect wrong exercise based on phase progression stagnation
2. **Quality Metrics in Supervisor**: Track planned workout quality independent of Engine
3. **Configurable Timings**: Make grace/warn/pause times configurable per exercise

---

## Summary

This plan:
1. **Fixes core issues**: Resume after visibility preserves rep count, NoPose during TRAINING triggers auto-pause
2. **Single Source of Truth**: Supervisor owns planned workout state
3. **Reuses existing code**: VisibilityMonitor, PoseValidator, FeedbackManager unchanged
4. **Simple implementation**: No complex threading, no over-abstraction
5. **Clean separation**: Supervisor decides → ViewModel executes → UI displays
