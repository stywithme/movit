| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | Movit training engine frame pipeline and session orchestration (as-built) |
| **Code** | `kmp-app/core/training-engine/`, `kmp-app/feature/training/` |
| **Supersedes** | `Docs/03-Implemented-Archive/Hold/`, `Feedback/`, `Position-Checks/` |
| **Verified** | 2026-06-22 |

# Training engine (KMP)

> **Full camera-training stack (backend + mobile API + UI + sync):** [`Camera-Training-Engine-As-Built/README.md`](Camera-Training-Engine-As-Built/README.md) — 14-topic as-built index verified 2026-07-04.

> **Naming:** `com.movit.core.training.session` = orchestration for a single **workout execution** (one exercise run), not auth session or assessment session. See [`Workout-Domain-Naming.md`](../Contracts/Workout-Domain-Naming.md).

## Module map

| Layer | Package / module | Role |
|-------|------------------|------|
| Pose ingest | `com.movit.core.posecapture` (`core/pose-capture`) | CameraX → MediaPipe → `PoseFrame` |
| Engine | `com.movit.core.training` (`core/training-engine`) | Per-frame evaluation, reps, hold, position checks |
| Production UI | `com.movit.feature.training` (`feature/training`) | `TrainingSessionViewModel` wires supervisor, gate, engine, feedback |
| Debug lab | `com.movit.feature.trainingdebug` (`feature/training-debug`) | Camera / video / image pose lab — **not production training** |

---

## Session stack (production)

`TrainingSessionViewModel` owns the workout-run lifecycle. Per frame:

1. **Pose frame** arrives from `PoseDetector` (live camera in production).
2. **`SessionSupervisor`** (`com.movit.core.training.session.SessionSupervisor`) — SSOT for run state (`IDLE` → `SETUP_POSE` → `COUNTDOWN` → `TRAINING` → `PAUSED` / `AUTO_PAUSED` → `COMPLETED`). Emits `SupervisorAction` commands (start/pause engine, countdown, UI cues).
3. **`SetupReadinessGate`** — pre-run setup only: scene axes (region / posture / direction) + `StartPoseGate.isInStartPose` with rolling-window confirmation. Drives setup UI and voice hints; **not** used inside the engine during reps.
4. **`CountdownController`** — 3-2-1 before engine start; frozen when pose invalid during countdown.
5. **`MovitTrainingEngine.processFrame`** — runs only in `TRAINING` (supervisor issues `StartEngine` / `ProcessFrame`).
6. **`FeedbackRouter`** — common feedback arbiter → platform `SpeechSynthesizer` / `AudioFeedbackPlayer` / `HapticsPort`. Engine events are routed through `TrainingFeedbackEventRouter` in the ViewModel.

```
PoseFrame
  → SessionSupervisor.processSignal(...)
  → SetupReadinessGate.validate(...)     [SETUP_POSE / COUNTDOWN only]
  → MovitTrainingEngine.processFrame(...) [TRAINING only]
  → FeedbackRouter.submit(...)
```

---

## MovitTrainingEngine — per-frame pipeline

**Class:** `com.movit.core.training.session.MovitTrainingEngine`

Internal collaborators (constructed in `init`):

| Component | Package | Purpose |
|-----------|---------|---------|
| `SessionOrchestrator` | `session` | Lifecycle clock, `PauseController`, `ExecutionSafetyGuards`, `HoldTimer` |
| `FrameIngressGate` | `session` | Drop frames when previous frame still processing |
| `JointAngleTracker` | `engine` | Extract tracked joint angles; bilateral mirror via `isFlipped` |
| `VisibilityMonitor` | `visibility` | Joint visibility → auto-pause via `PauseController` |
| `FramePipelineExecutor` | `engine.pipeline` | Main evaluation path (below) |
| `RepCounter` + `RepCompletionCoordinator` | `engine` | Rep counting and deferred completion |
| `HoldExerciseCoordinator` | `session` | Hold timer FSM + `HoldStatus` publishing |
| `BilateralController` | `bilateral` | Active side + per-rep flip |
| `FrameFeedbackEmitter` | `engine.feedback` | Throttle joint-state and position feedback candidates |

### `processFrame` order

1. Skip if not running or paused.
2. No pose → `PresenceSupervisorBridge` (engine-level presence events).
3. `FrameIngressGate.tryAcquire()` — drop if busy.
4. Mirror frame if front camera.
5. `JointAngleTracker.extractTrackedAngles(..., isFlipped = bilateral.isFlipped)`.
6. `VisibilityMonitor` → `PauseController.processVisibilityResult` — may return early (skip counting).
7. **`FramePipelineExecutor.runMainPath`**:
   - `AngleSmoother.smooth`
   - `StartPoseGate.isInStartPosition` (in-run rep path — UP/hold counted bands)
   - `PhaseStateMachine.update`
   - `PositionValidator.validate` (when landmarks present; locks scene on first valid frame)
   - `FrameEvaluationPipeline.evaluate` → `JointEvaluator` per joint
8. `FrameFeedbackEmitter.emitThrottledStateMessages`
9. Scoring path: `repCounter.updateJointEvals` when phase warrants tracking.
10. `JointErrorCollection.collectJointErrors` → `repCounter.addError` + throttled `onJointErrorFeedback`.
11. Position errors/warnings/tips → `repCounter` scoring inputs.
12. **Hold:** `HoldExerciseCoordinator.updateHoldTimer(isInHoldZone)` where `isInHoldZone = (phase == COUNT)`.
13. **Rep:** `RepCompletionCoordinator.consumeIfPendingAndHandle()`.

`stop()` returns `ExerciseWorkoutSummary` via `ExerciseWorkoutSummaryBuilder`.

---

## Hold exercises

Hold mode is implemented by **`HoldExerciseCoordinator`** (`com.movit.core.training.session.HoldExerciseCoordinator`), not a separate engine class.

| Piece | Role |
|-------|------|
| `ExerciseConfig.isHoldExercise()` | `countingMethod == HOLD` |
| `SessionOrchestrator.holdTimer` | `HoldTimer` from `repCountingConfig.duration` |
| `PhaseStateMachine` | `IDLE` → `COUNT` when primary angles enter hold range |
| `HoldExerciseCoordinator` | Drives timer, grace periods, `HoldStatus`, calls `repCounter.completeRep()` on success |
| `snapshotHoldReportData()` | `MovitHoldReportData` for post-workout report |

---

## Start pose vs start position

| Concept | Where | Meaning |
|---------|-------|---------|
| `TrackedJoint.startPose` min/max | `SetupReadinessGate` / setup UI | Pre-countdown: user must match start-pose box (rolling window + scene axes). |
| `StartPoseGate.isInStartPose` | `SetupReadinessGate` | Config start-pose box for setup confirmation. |
| `StartPoseGate.isInStartPosition` | `FramePipelineExecutor` via `MovitTrainingEngine` | **During workout:** primary joints in UP + counted band, or hold counted band. |
| `StartPoseGate.isStartPoseRoughlyValid` | `SetupReadinessGate.isCountdownPoseValid` | Looser guard during countdown. |

---

## Feedback

| Layer | Class | Role |
|-------|-------|------|
| Engine (candidates) | `FrameFeedbackEmitter` | Rate-limits joint-state messages and joint-error emits per frame |
| ViewModel (delivery) | `FeedbackRouter` | `FeedbackScheduler` arbiter → voice / tone / haptics / visual |
| ViewModel (mapping) | `TrainingFeedbackEventRouter` | Engine callbacks → `FeedbackSignal` (reps, hold, visibility, countdown) |

`FeedbackRouter.submitSetup(...)` is used for setup voice; `resetSetupFeedback()` on leaving setup.

---

## Video mode

**Production training (`feature/training`) is camera-only.**

Recorded-video pose playback lives in **`feature/training-debug`** (`TrainingDebugInputMode.VIDEO`, `AndroidDebugVideoPoseSource`). `SessionSupervisor.isVideoMode` exists for debug flows; it is not wired in production `TrainingSessionViewModel`.

---

## Config ingest

- Parse: `ExerciseConfigParser` → `ExerciseConfig.sanitizeDefaults()`.
- Validation: `ExerciseConfig.validationIssues(poseVariantIndex)` — log-only at load today (empty list = OK). Checks name, variants, primary joints.
- Loaded via `TrainingConfigRepository` in `TrainingSessionViewModel`.

---

## Key types (code paths)

| Type | Path under `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/` |
|------|-------------------------------------------------------------------------------------------|
| `MovitTrainingEngine` | `session/MovitTrainingEngine.kt` |
| `FramePipelineExecutor`, `MainPathFrameResult` | `engine/pipeline/` |
| `FrameEvaluationPipeline`, `JointEvaluator` | `engine/evaluation/`, `engine/pipeline/` |
| `SessionSupervisor`, `SupervisorSignal`, `SupervisorAction` | `session/` |
| `SetupReadinessGate`, `SetupPhase` | `session/` |
| `HoldExerciseCoordinator`, `HoldStatus`, `HoldTimer` | `session/` |
| `FeedbackRouter` | `engine/feedback/FeedbackRouter.kt` |
| `FrameFeedbackEmitter` | `engine/feedback/FrameFeedbackEmitter.kt` |
| `BilateralController` | `bilateral/BilateralController.kt` |
| `PositionValidator` | `position/PositionValidator.kt` |
| `PoseSceneDetector` | `position/PoseSceneDetector.kt` |
| `PipelineTrace` | `observability/PipelineTrace.kt` (gated by `PipelineTraceConfig`) |
| `ExerciseConfig`, position check enums | `config/ExerciseConfigModels.kt`, `config/ExerciseConfigTypes.kt` |

---

## Observability

- `MovitTrainingEngine.pipelineTrace` — ring buffer; enabled via `PipelineTraceConfig.setEnabled(true)` (debug UI).
- `TrainingPipelineDiagnostics` — milestone logging from ViewModel / pose-capture.

---

## Related docs

- Position checks: [`Positions-Check-Concept.md`](Positions-Check-Concept.md)
- Scene detection: [`pose-scene-detection-how-it-works.md`](pose-scene-detection-how-it-works.md)
- Bilateral design: [`Bilateral-Design.md`](Bilateral-Design.md)
- MediaPipe integration: [`Docs/05-Guides/MediaPipe-Wiki/`](../05-Guides/MediaPipe-Wiki/README.md)
