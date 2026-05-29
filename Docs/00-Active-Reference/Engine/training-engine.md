| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | TrainingEngine frame pipeline (as-built) |
| **Code** | `android-poc/.../TrainingEngine.kt`, `FramePipelineExecutor.kt` |
| **Verified** | 2026-05-29 |

# Training engine (Android)

## Config ingest policy (current)

- **Log-only:** after [ExerciseConfig.sanitizeGsonDefaults()](../app/src/main/java/com/trainingvalidator/poc/training/models/ExerciseConfig.kt), [validationIssues()](../app/src/main/java/com/trainingvalidator/poc/training/models/ExerciseConfig.kt) runs at network/cache/offline load; any non-empty list is logged (`Log.w`) but the config is still used. Tightening to “reject bad configs” is a product decision; keep a single place if you add it.
- **Extra checks (examples):** `startPose.min` vs `max`, `pairedWith` must refer to a tracked joint, `bilateralConfig.switchEvery` ≥ 1, negative hold duration, negative rep target.

## Start pose vs start position (runtime)

| Concept | Where | Meaning |
| ------ | ------ | ------ |
| `TrackedJoint.startPose` min/max | [PoseSetupGuide](../app/src/main/java/com/trainingvalidator/poc/ui/training/PoseSetupGuide.kt) / pre-training | “Stand here before countdown” – all required primary joints must be GREEN in the start-pose box. |
| [StartPoseGate.isInStartPosition](../app/src/main/java/com/trainingvalidator/poc/training/engine/StartPoseGate.kt) | [TrainingEngine](../app/src/main/java/com/trainingvalidator/poc/training/TrainingEngine.kt) | In-session: PRIMARY in **UP** + counted band, or **hold** counted band. **Not** the same as setup. |
| [StartPoseGate.isInStartPose](../app/src/main/java/com/trainingvalidator/poc/training/engine/StartPoseGate.kt) | Optional | `startPose` box; not used in the main `processFrame` path by default. |

## Role and frame flow (actual code)

1. [TrainingEngine](../app/src/main/java/com/trainingvalidator/poc/training/TrainingEngine.kt) `processFrame` locks `stateLock` and: extract raw angles (JointAngleTracker) → **visibility** / PauseController (always) → on skip/empty, position overlay for UI and return.  
2. [FramePipelineExecutor.runMainPath](../app/src/main/java/com/trainingvalidator/poc/training/engine/pipeline/FramePipelineExecutor.kt) runs **smooth → isInStartPosition (flow) → PhaseStateMachine.update → position validate (if landmarks) → [FrameEvaluationPipeline]**.  
3. [JointErrorCollection](../app/src/main/java/com/trainingvalidator/poc/training/engine/JointErrorCollection.kt) and position issues feed [RepCounter](../app/src/main/java/com/trainingvalidator/poc/training/engine/RepCounter.kt).  
4. [RepCompletionCoordinator](../app/src/main/java/com/trainingvalidator/poc/training/engine/session/RepCompletionCoordinator.kt) consumes deferred rep completion **after** errors on that frame.  
5. [HoldSessionCoordinator](../app/src/main/java/com/trainingvalidator/poc/training/engine/session/HoldSessionCoordinator.kt) + [HoldTimer](../app/src/main/java/com/trainingvalidator/poc/training/engine/HoldTimer.kt) for hold mode; on completion it finalizes recorder metrics and bilateral side bookkeeping just like the rep coordinator.  
6. [SessionSummaryBuilder](../app/src/main/java/com/trainingvalidator/poc/training/engine/session/SessionSummaryBuilder.kt) builds the end [SessionSummary](../app/src/main/java/com/trainingvalidator/poc/training/models/TrainingSession.kt) in `stop()`.  
7. [FrameFeedbackEmitter](../app/src/main/java/com/trainingvalidator/poc/training/engine/feedback/FrameFeedbackEmitter.kt) throttles state messages and position feedback events.  

[JointEvaluator](../app/src/main/java/com/trainingvalidator/poc/training/engine/evaluation/JointEvaluator.kt) remains the per-joint quality source; it is used inside [FrameEvaluationPipeline](../app/src/main/java/com/trainingvalidator/poc/training/engine/pipeline/FrameEvaluationPipeline.kt) and its `FrameJointEvaluationResult` (same file).

## Key types (roadmap 6.x–8)

- **FrameInput** — DTO for `processFrame` ([FrameInput.kt](../app/src/main/java/com/trainingvalidator/poc/training/engine/pipeline/FrameInput.kt)).
- **FramePipelineExecutor** + **MainPathFrameResult** — main path after extract/visibility ([FramePipelineExecutor.kt](../app/src/main/java/com/trainingvalidator/poc/training/engine/pipeline/FramePipelineExecutor.kt), [FramePipelineModels.kt](../app/src/main/java/com/trainingvalidator/poc/training/engine/pipeline/FramePipelineModels.kt)).
- **FrameEvaluationPipeline** — `JointEvaluator` → [JointStateInfo] + [FrameJointEvaluationResult.forScoring] ([FrameEvaluationPipeline.kt](../app/src/main/java/com/trainingvalidator/poc/training/engine/pipeline/FrameEvaluationPipeline.kt)).
- **FrameFeedbackEmitter** — throttles state messages, position feedback, and repeated joint-error feedback ([FrameFeedbackEmitter.kt](../app/src/main/java/com/trainingvalidator/poc/training/engine/feedback/FrameFeedbackEmitter.kt)).
- **SessionSafetyGuards** — hard caps on reps/session time ([SessionSafetyGuards.kt](../app/src/main/java/com/trainingvalidator/poc/training/engine/SessionSafetyGuards.kt)).
- **BilateralController** — side and mirroring ([bilateral/](../app/src/main/java/com/trainingvalidator/poc/training/engine/bilateral/)).
- **StartPoseGate** — gating and optional feedback list builders ([StartPoseGate.kt](../app/src/main/java/com/trainingvalidator/poc/training/engine/StartPoseGate.kt)).
- **JointErrorCollection** — stateless `buildList` per call ([JointErrorCollection.kt](../app/src/main/java/com/trainingvalidator/poc/training/engine/JointErrorCollection.kt)); unit: [JointErrorCollectionTest](../app/src/test/java/com/trainingvalidator/poc/training/engine/JointErrorCollectionTest.kt).
- **RepCompletionSignal** — one-bit order guard inside [RepCompletionCoordinator](../app/src/main/java/com/trainingvalidator/poc/training/engine/session/RepCompletionCoordinator.kt).
- **HoldStatus** — one snapshot for hold ([HoldStatus.kt](../app/src/main/java/com/trainingvalidator/poc/training/engine/HoldStatus.kt)); [TrainingViewModel.holdStatus](../app/src/main/java/com/trainingvalidator/poc/ui/training/TrainingViewModel.kt) mirrors the engine.
- **PipelineTrace** — ring buffer ([PipelineTrace.kt](../app/src/main/java/com/trainingvalidator/poc/training/engine/observability/PipelineTrace.kt)); [TrainingViewModel.getPipelineTraceSnapshot()](../app/src/main/java/com/trainingvalidator/poc/ui/training/TrainingViewModel.kt) reads it. **Debug:** long-press **rep count** in [TrainingActivity](../app/src/main/java/com/trainingvalidator/poc/ui/train/TrainingActivity.kt) to show the last 50 lines.

## Config validation (sources)

Gson: always [sanitizeGsonDefaults()](...) then [validationIssues()](...). Ingest: [toExerciseConfig](../app/src/main/java/com/trainingvalidator/poc/network/MobileSyncModels.kt), [ExerciseCacheManager](...), [OfflineFallbackLoader](...).

## Manual checks (for you, Android Studio)

- Build; run unit tests; run parity tests if you use them.
- One **rep** exercise, one **hold** exercise, **long-press** `tvRepCount` and confirm `PipelineTrace` is non-empty after a few seconds of training.
- If you change the frame pipeline, re-run **parity** fixtures to catch event-order regressions.
