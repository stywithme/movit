# Training System Full Audit Report

**Date**: April 5, 2026  
**Scope**: Complete review of training exercise logic, state management, and architecture  
**Files Reviewed**: 45+ files across `training/`, `ui/training/`, `ui/train/`

---

## Fixes Applied

| Bug | Status | File | Change |
|---|---|---|---|
| BUG-1 | **FIXED** | `TrainingViewModel.kt` | Removed duplicate TargetReached from events collector; kept `isCompleted` flow as single path |
| BUG-2 | **FIXED** | `TrainingViewModel.kt` | Changed `ResumeFromVisibilityPause` from no-op to `trainingEngine?.resume()` |
| BUG-3 | **FIXED** | `TrainingEngine.kt` | Moved `_isCompleted = true` AFTER `repCounter.completeRep()` to fix ordering and eliminate double-set |
| CONFLICT-1 | **FIXED** | `TrainingEngine.kt` | `resume()` now clears stale VisibilityMonitor state, preventing double 3-2-1 countdown |
| LEGACY-1 | **DELETED** | `TrainingStateManager.kt` | Dead code — instantiated but never called; removed class + ViewModel field |
| LEGACY-2 | **DELETED** | `PoseValidator.kt` | Dead code — replaced by PoseSetupGuide; removed class + all references |
| LEGACY-3 | **DELETED** | `TrainingViewModel.kt` | Removed 4 deprecated TrainingUIEvents (PoseValidationUpdate, VisibilityPaused, VisibilityResumeStartPose, StartVisibilityResumeCountdown) |
| LEGACY-4 | **DELETED** | `TrainingEngine.kt` + `TrainingViewModel.kt` | Removed dead `resumeFromVisibilityPause()` method from both files |
| DUP-1 | **FIXED** | `TrainingViewModel.kt` | Removed dead `_isCompleted` proxy StateFlow (Activity reads engine directly) |
| DUP-3 | **FIXED** | `RepCounter.kt` | Made `minRepIntervalMs` guard conditional on `isHoldExercise` only; rep-based gated by PhaseStateMachine |
| COMPLEXITY-1 | **FIXED** | `FeedbackEvent.kt` + `TrainingEngine.kt` + `FeedbackManager.kt` | Removed 8 dead/redundant events (PhaseChanged, StartPositionGuide, MotivationalMessage, TrainingStarted, TrainingPaused, TrainingResumed, DangerDetected, VisibilityResumeCountdown) — 24→16 events; removed associated emitEvent calls, handler, and helper fields |

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Critical Bugs](#2-critical-bugs)
3. [State Management Conflicts](#3-state-management-conflicts)
4. [Redundant & Duplicate Code](#4-redundant--duplicate-code)
5. [Unnecessary Complexity](#5-unnecessary-complexity)
6. [Thread Safety Issues](#6-thread-safety-issues)
7. [Legacy Code Debt](#7-legacy-code-debt)
8. [Recommended Fixes](#8-recommended-fixes)

---

## 1. Architecture Overview

### Current Layer Structure

```
TrainingActivity (UI + Camera + Pose Detection)
    ↓ onPoseFrame()
TrainingViewModel (State coordination + Engine observation)
    ↓ processSignal()
SessionSupervisor (Session state machine: IDLE → SETUP → COUNTDOWN → TRAINING → COMPLETED)
    ↓ emits SupervisorAction
TrainingViewModel.executeAction() (Executes commands)
    ↓
TrainingEngine (Core training logic - processFrame)
    ├── JointAngleTracker (Extract angles)
    ├── AngleSmoother (Smooth angles)
    ├── PhaseStateMachine (UP → DOWN → BOTTOM → UP cycle)
    ├── FormValidator (State-based quality: PERFECT/NORMAL/PAD/WARNING/DANGER)
    ├── RepCounter (Count + Score reps)
    ├── PositionValidator (Body position checks)
    ├── VisibilityMonitor (Joint visibility tracking)
    └── HoldTimer (For HOLD exercises)
```

### Data Flow Per Frame

```
Camera → PoseLandmarkerHelper → onPoseDetected (Activity)
  → Smooth landmarks (Dispatchers.Default)
  → Calculate angles
  → withContext(Main) → viewModel.onPoseFrame()
  → supervisor.processSignal(PoseFrame)
  → emit SupervisorAction.ProcessFrame
  → viewModel.executeAction() → Dispatchers.Default
  → trainingEngine.processFrame() [synchronized(stateLock)]
    → Extract angles → Smooth → Visibility check
    → Phase machine → Form validation → Rep scoring
    → Emit FeedbackEvents
  → viewModel observes engine StateFlows → updates UI
```

---

## 2. Critical Bugs

### BUG-1: Dual TargetReached Signal (Double Completion)

**Location**: `TrainingViewModel.observeTrainingEngine()` lines 736-758

**Problem**: TargetReached is signaled to SessionSupervisor TWICE:

```
Path A: engine.isCompleted → supervisor.processSignal(TargetReached)  [line 737-739]
Path B: engine.events → FeedbackEvent.TargetReached → supervisor.processSignal(TargetReached)  [line 752-754]
```

Both fire when training completes. The first triggers TRAINING → COMPLETED transition. The second arrives at COMPLETED state and gets silently ignored. But this creates:
- Unnecessary signal processing
- Race condition risk if both arrive in same frame
- For session mode: `tryHandleSessionSetCompleted()` has a guard (`lastCompletedSessionSetRunId`) that catches duplicates, but it's a symptom fix, not a root cause fix

**Fix**: Remove one of the two paths. Keep only the `isCompleted` flow observer since it's the canonical signal.

---

### BUG-2: ResumeFromVisibilityPause is a NO-OP

**Location**: `TrainingViewModel.executeAction()` line 418-420

```kotlin
is SupervisorAction.ResumeFromVisibilityPause -> {
    // Visibility resume now handled internally by TrainingEngine
}
```

**Problem**: When `RESUME_COUNTDOWN` finishes in SessionSupervisor, it emits `ResumeFromVisibilityPause`, then transitions state to `TRAINING`. But the action does NOTHING. The engine is NOT told to resume. The engine's `isPaused` flag was set by `PauseEngine` when `AUTO_PAUSED` was entered (line 472-474 in SessionSupervisor), but no `ResumeEngine` is ever called to clear it.

**Flow**:
1. NoPose for 4s → Supervisor emits `PauseEngine` → engine.pause() → isPaused = true
2. Pose returns → RESUME_SETUP → RESUME_COUNTDOWN
3. Countdown finishes → Supervisor emits `ResumeFromVisibilityPause` → **NO-OP**
4. State goes to TRAINING, but engine still has `isPaused = true`
5. processFrame early-returns because `isPaused && !isVisibilityPaused && !isCountingSuspended`

**Result**: After NoPose auto-pause, training appears to resume (UI shows TRAINING state) but the engine is frozen - no frames are processed. The user sees the camera and skeleton but no reps are counted.

**Fix**: Change the action to call `trainingEngine?.resume()` or better, redesign to use a single pause/resume mechanism.

---

### BUG-3: Hold Exercise Double completeRep()

**Location**: `TrainingEngine.setupHoldTimerCallbacks()` lines 1088-1116

When HoldTimer.onCompleted fires:
1. `_isCompleted.value = true` (line 1089)
2. `repCounter.completeRep()` (line 1092) — this increments count to 1
3. repCounter's `onTargetReached` fires (count >= targetReps where targetReps=1 for hold)
4. `onTargetReached` callback (line 586) also sets `_isCompleted.value = true`

The flow works but `_isCompleted` is set twice, and the completion events stack:
- HoldCompleted event (from onCompleted callback)
- RepCompleted event (from repCounter.onRepCountChanged)  
- TargetReached event (from repCounter.onTargetReached)
- TargetReached signal from isCompleted observer in ViewModel
- TargetReached signal from events observer in ViewModel

This is 5 signals for a single completion.

---

## 3. State Management Conflicts

### CONFLICT-1: THREE Parallel Pause/Visibility Systems

The system has THREE separate mechanisms handling pause/visibility:

#### A. SessionSupervisor's NoPose Auto-Pause
- Triggers when `onNoPoseDetected()` is called (no pose at all)
- 4s timeout → AUTO_PAUSED state
- Flow: AUTO_PAUSED → RESUME_SETUP → RESUME_COUNTDOWN → TRAINING
- Emits: PauseEngine, ShowAutoPaused

#### B. TrainingEngine's Internal VisibilityMonitor  
- Triggers when required joints have low visibility (partial detection)
- Two-tier: WARNING (1s) → PAUSE (4s) → Auto-resume with 3-2-1 countdown
- Updates: _isCountingSuspended, _isVisibilityPaused
- Handles resume internally via `performVisibilityResume()`

#### C. TrainingStateManager (Legacy - Still Instantiated)
- Has VISIBILITY_PAUSED and VISIBILITY_SETUP_POSE states
- Marked @Deprecated but still accessible via `viewModel.stateManager`
- NOT used by current flow but can cause confusion

**Problem**: Systems A and B overlap and can trigger simultaneously. If both NoPose (Supervisor) AND VisibilityMonitor (Engine) detect issues at the same time, the state becomes inconsistent:
- Supervisor enters AUTO_PAUSED, emits PauseEngine
- Engine's VisibilityMonitor already has _isVisibilityPaused = true
- The processFrame check: `isPaused && !_isVisibilityPaused` → this means if both isPaused (from Supervisor) AND _isVisibilityPaused (from Engine) are true, frames CONTINUE processing — which contradicts the AUTO_PAUSED intent

### CONFLICT-2: Countdown State Managed in Two Places

The countdown is managed by:
1. **CountdownController** (actual timer logic)
2. **SessionSupervisor** (COUNTDOWN/RESUME_COUNTDOWN state + freeze/unfreeze)
3. **TrainingViewModel** (validates pose during countdown)

When pose is lost during countdown:
- ViewModel sends PoseInvalid signal to Supervisor
- Supervisor calls handleCountdownPoseLost() which has time-based logic
- But CountdownController has its own state (freeze/cancel)
- These can get out of sync if signals arrive in unexpected order

### CONFLICT-3: Completion State in Multiple Places

`isCompleted` is tracked in:
1. `TrainingEngine._isCompleted` (StateFlow)
2. `TrainingViewModel._isCompleted` (StateFlow)  
3. `SessionSupervisor` state (COMPLETED)
4. `RepCounter.targetReachedEmitted` (flag)
5. `HoldTimer` state (COMPLETED)

When exercise completes, ALL of these must agree. Currently they're set via cascading callbacks which creates timing dependencies.

---

## 4. Redundant & Duplicate Code

### DUP-1: State Flow Proxying in TrainingViewModel

TrainingViewModel creates duplicate StateFlows that simply mirror TrainingEngine:

| ViewModel Flow | Engine Flow | Purpose |
|---|---|---|
| `_repCount` | `engine.repCount` | Same data |
| `_currentPhase` | `engine.currentPhase` | Same data |
| `_holdElapsedMs` | `engine.holdElapsedMs` | Same data |
| `_holdState` | `engine.holdState` | Same data |
| `_isCompleted` | `engine.isCompleted` | Same data |

Each proxy adds:
- Memory allocation for new StateFlow
- Coroutine job for collection
- One frame of latency
- Potential for inconsistency if observation fails

The Activity could observe `viewModel.trainingEngine?.repCount` directly.

### DUP-2: Double Frame Drop Guard

Frame dropping is implemented TWICE:

1. **TrainingActivity**: `isProcessingPoseFrame` flag (line 211)
2. **TrainingViewModel**: `isEngineProcessingFrame` flag (line 149)

Both serve the same purpose. Frames pass through both guards sequentially.

### DUP-3: Min Rep Interval Checked Twice

1. **PhaseStateMachine**: `minRepIntervalMs` checked in `handlePhaseTransition()` (line 385)
2. **RepCounter**: `minRepIntervalMs` checked in `completeRep()` (line 317)

Both independently reject reps that arrive too fast. This means the effective min interval is the MAX of both values, which may not be intended.

### DUP-4: Multiple Score Calculation Paths

RepCounter has 4 different scoring paths in `completeRep()`:
1. Hold exercise weighted scoring (stateTimeTracking)
2. Rep accumulated states scoring (repAccumulatedStates)
3. Snapshot scoring fallback (currentJointStates)
4. Legacy worst state scoring (currentRepWorstState)

Then position error penalties are applied on top. This should be simplified to 2 paths: hold vs. rep-based.

---

## 5. Unnecessary Complexity

### COMPLEX-1: Event System is Overloaded

The FeedbackEvent system carries 20+ event types:
- TrainingStarted, TrainingPaused, TrainingResumed
- RepCompleted, TargetReached
- PhaseChanged
- JointErrorDetected, JointStateMessage, DangerDetected
- PositionErrorDetected, PositionWarningDetected, PositionTipDetected
- VisibilityWarning, VisibilityPaused, VisibilityResumed, VisibilityResumeCountdown
- HoldStarted, HoldCompleted, HoldFailed, HoldGraceStarted, HoldResumed
- SceneWarnings

These events flow through:
1. TrainingEngine → _events SharedFlow
2. TrainingViewModel → observeTrainingEngine → _feedbackEvents SharedFlow
3. FeedbackManager → processes events for audio/haptic
4. TrainingActivity → handleFeedbackEvent() → UI updates

Many events exist only for internal coordination and shouldn't be in the public event stream.

### COMPLEX-2: Bilateral Exercise Side Switching

The bilateral system adds complexity throughout:
- `BilateralSide` enum in TrainingEngine
- `isBilateralFlipped` getter computed on every access
- `JointAngleTracker.extractTrackedAngles()` mirrors angles when flipped
- Skeleton overlay mirrors indicators
- Side switches after every N reps in handleRepCompleted

This is well-designed but tightly coupled with the rep counting flow, making changes risky.

### COMPLEX-3: Position Validation Layering

Position checks go through multiple layers:
1. **PositionValidator** → validates landmarks against position checks
2. **PoseSceneDetector** → detects posture/direction/region
3. **CameraPositionDetector** → detects camera angle
4. **BodyPostureDetector** → detects body posture (standing/sitting/lying)
5. **VisibleRegionDetector** → detects which body region is visible

Results flow through:
- `_positionErrors` StateFlow (all severities combined)
- `_sceneWarnings` StateFlow (scene mismatches)
- FeedbackEvents (throttled per checkId)
- RepCounter (ERROR severity for scoring, WARNING for analytics)

### COMPLEX-4: Setup Pose Validation Has Two Systems

1. **PoseSetupGuide** (new) - Rolling window with phases (SCENE → ANGLES)
2. **PoseValidator** (old, deprecated) - Simple consecutive frame counting

Both exist in TrainingViewModel. PoseSetupGuide is used but PoseValidator is still instantiated.

---

## 6. Thread Safety Issues

### THREAD-1: Coroutine Dispatcher Mismatch

```
TrainingActivity.onPoseDetected → Dispatchers.Default (smoothing + angles)
  → withContext(Dispatchers.Main) → viewModel.onPoseFrame()
    → supervisor.processSignal() [Main thread]
      → emit ProcessFrame action
        → viewModel.executeAction() [Main thread, collected from SharedFlow]
          → Dispatchers.Default → engine.processFrame() [synchronized(stateLock)]
```

The processFrame call happens on a pool thread and uses `synchronized(stateLock)`. But `start()`, `pause()`, `resume()`, `stop()` are called from Main thread and also use `synchronized(stateLock)`. This is correct for mutual exclusion but can cause Main thread blocking if processFrame takes too long.

### THREAD-2: StateFlow Updates Inside synchronized Block

Inside `processFrame`'s synchronized block, multiple StateFlow values are updated:
```kotlin
_currentAngles.value = smoothedAngles
_isInStartPosition.value = inStartPos
_currentPhase.value = currentPhase
_jointStateInfos.value = jointStateInfos
_isDangerActive.value = hasDanger
```

StateFlow.value setter is thread-safe, but collectors on Main thread will be notified while the lock is still held. If a collector tries to call `start()` or `stop()` (which also need the lock), a deadlock could occur.

### THREAD-3: Volatile Without Synchronization

Several `@Volatile` fields are read/written without synchronization:
```kotlin
@Volatile var lastSmoothedLandmarks: List<SmoothedLandmark>? = null  // ViewModel
@Volatile var lastImageSize: Pair<Int, Int> = Pair(1, 1)  // ViewModel
```

`@Volatile` ensures visibility but not atomicity for compound operations.

---

## 7. Legacy Code Debt

### Deprecated but Still Instantiated

| Class/Field | Location | Replacement |
|---|---|---|
| `TrainingStateManager` | TrainingViewModel line 67 | SessionSupervisor |
| `PoseValidator` | TrainingViewModel line 70 | PoseSetupGuide |
| `ValidationResult` | FormValidator | JointStateInfo |
| `JointStatus` | FormValidator | JointStateInfo |
| `JointColor` | FormValidator | StateConfig colors |
| `JointZone` | FormValidator | JointState |
| `JointArrowInfo` | FormValidator | JointStateInfo |
| `_jointStatuses` | TrainingEngine line 289 | _jointStateInfos |
| `_arrowInfos` | TrainingEngine line 294 | _jointStateInfos |

### Deprecated Methods Still in API

TrainingViewModel still exposes:
- `startTraining()` 
- `startVideoModeTraining()`
- `pauseTraining()`
- `resumeTraining()`
- `stopTraining()`
- `resumeFromVisibilityPause()`
- `processFrame()`

These are all marked @Deprecated but still exist and could be called accidentally.

---

## 8. Recommended Fixes

### Priority 1: Fix BUG-2 (ResumeFromVisibilityPause NO-OP)

This is causing training to freeze after NoPose auto-pause. Change:

```kotlin
is SupervisorAction.ResumeFromVisibilityPause -> {
    trainingEngine?.resume()
}
```

Or redesign: make SessionSupervisor emit `ResumeEngine` instead of `ResumeFromVisibilityPause` when returning from AUTO_PAUSED flow.

### Priority 2: Fix BUG-1 (Double TargetReached)

Remove the duplicate signal. In `observeTrainingEngine()`, remove one of:

Option A: Remove the isCompleted observer (keep events):
```kotlin
// REMOVE this block:
launch {
    engine.isCompleted.collect { completed ->
        if (completed && supervisor.state.value == SessionState.TRAINING) {
            supervisor.processSignal(SupervisorSignal.TargetReached)
        }
    }
}
```

Option B: Remove the TargetReached from events handler (keep isCompleted):
```kotlin
// In events collector, remove:
is FeedbackEvent.TargetReached -> {
    supervisor.processSignal(SupervisorSignal.TargetReached)
}
```

### Priority 3: Unify Pause/Visibility Systems

Choose ONE system for visibility handling:

**Option A (Recommended)**: Keep TrainingEngine's internal VisibilityMonitor as the sole handler. Remove NoPose auto-pause from SessionSupervisor. The VisibilityMonitor already handles the full lifecycle including auto-resume.

**Option B**: Move all visibility logic to SessionSupervisor and remove from TrainingEngine. This is a larger change but creates cleaner separation.

### Priority 4: Remove Legacy Code

1. Delete `TrainingStateManager` class entirely
2. Delete `PoseValidator` class (keep PoseSetupGuide only)
3. Remove all @Deprecated methods from TrainingViewModel
4. Remove `_jointStatuses`, `_arrowInfos` flows from TrainingEngine
5. Remove `ValidationResult`, `JointStatus`, `JointColor`, `JointZone`, `JointArrowInfo` from FormValidator (update skeleton overlay to use JointStateInfo directly)

### Priority 5: Simplify State Flow Proxying

Instead of duplicating StateFlows in TrainingViewModel, expose the engine directly or use computed properties:

```kotlin
// Instead of duplicate flows:
val repCount: StateFlow<Int> get() = trainingEngine?.repCount ?: MutableStateFlow(0)
```

### Priority 6: Remove Double Frame Drop Guard

Keep only ONE frame drop mechanism. The TrainingActivity one is sufficient since it's the entry point.

### Priority 7: Remove Double minRepInterval Check

Keep the check in RepCounter only (it has the scoring context). Remove from PhaseStateMachine or make it purely a timing guard without rejecting reps.

---

## File-by-File Summary

### Core Engine Layer (`training/`)

| File | Lines | Role | Issues |
|---|---|---|---|
| `TrainingEngine.kt` | 1426 | Main orchestrator | BUG-2 (visibility resume), Dual visibility system |
| `engine/PhaseStateMachine.kt` | 470 | Phase detection | DUP-3 (min rep interval) |
| `engine/FormValidator.kt` | 789 | Form quality assessment | Heavy legacy code |
| `engine/RepCounter.kt` | 555 | Rep counting + scoring | DUP-4 (4 scoring paths) |
| `engine/VisibilityMonitor.kt` | 402 | Joint visibility | CONFLICT-1 (overlaps with Supervisor) |
| `engine/HoldTimer.kt` | 361 | Hold exercise timing | BUG-3 (double completion) |
| `engine/PositionValidator.kt` | ~300 | Body position checks | COMPLEX-3 |
| `engine/ScoreCalculator.kt` | ~200 | Score computation | Clean |
| `engine/AngleSmoother.kt` | ~100 | Angle smoothing | Clean |

### UI/Training Layer (`ui/training/`)

| File | Lines | Role | Issues |
|---|---|---|---|
| `TrainingViewModel.kt` | 952 | Central ViewModel | BUG-1, DUP-1, DUP-2, Legacy methods |
| `TrainingStateManager.kt` | 179 | Legacy state machine | Should be DELETED |
| `PoseValidator.kt` | ~200 | Legacy pose validation | Should be DELETED |
| `PoseSetupGuide.kt` | ~400 | New setup validation | Clean |
| `CountdownController.kt` | ~150 | Countdown timer | Clean |
| `VideoModeController.kt` | ~300 | Video mode | Clean |

### Session Layer (`training/session/`)

| File | Lines | Role | Issues |
|---|---|---|---|
| `SessionSupervisor.kt` | 501 | State machine (SSOT) | CONFLICT-1 (NoPose overlap) |
| `SessionState.kt` | 65 | State enum | Clean |
| `SupervisorAction.kt` | 110 | Output commands | BUG-2 |
| `SupervisorSignal.kt` | 85 | Input signals | Clean |
| `SessionTrainingEngine.kt` | ~500 | Multi-exercise session | Clean |

### Activity Layer (`ui/train/`)

| File | Lines | Role | Issues |
|---|---|---|---|
| `TrainingActivity.kt` | 3253 | Main UI Activity | DUP-2, THREAD-1, Too large |

---

## Summary of Most Critical Issues

1. **BUG-2**: Training freezes after NoPose auto-pause (ResumeFromVisibilityPause is NO-OP)
2. **BUG-1**: Double TargetReached signal causing potential race conditions
3. **CONFLICT-1**: Three parallel pause/visibility systems that can contradict each other
4. **DUP-1**: State flows duplicated needlessly in ViewModel
5. **Legacy**: ~1000 lines of deprecated code still present and instantiated

The most impactful fix is **BUG-2** — adding a single line (`trainingEngine?.resume()`) would fix the frozen-after-pause issue. After that, unifying the visibility systems (Priority 3) would eliminate the root cause of most state confusion.
