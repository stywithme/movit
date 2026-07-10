# Track I — Dead Code & Duplication Sweep

> **Scope**: `core/pose-capture/`, `core/training-engine/`, `feature/training/` (341 `.kt` files). Cross-checked `iosApp/` and workspace `shared/` for I1/OQ-07.
>
> **Review mode**: READ-ONLY. No code changes.
>
> **Date**: 2026-07-10

---

## Executive Summary

Track I confirms **PF-14** (two dead engine fields), **PF-15** (`LiveExerciseRunner` has no production callers), and **PF-17** (diagnostics test formatter drift). Production training flows through **`MovitTrainingEngine` + `TrainingSessionViewModel`**; `LiveExerciseRunner` survives only as a **test/parity harness**. Several additional dead artifacts were found: WS-0 **Stub** classes, unused **`PoseDetector.buildPoseFrame`** call path, echo fields on **`MainPathFrameResult`**, redundant **`EngineMetrics`** derived fields, and a **`SupervisorAction.ValidatePose`** emission path the VM ignores. **NoPose** feedback is **literally duplicated** in two VM handlers; only the **supervisor path is live during TRAINING** — the presence-bridge path is effectively unreachable under current VM wiring.

---

## I1–I12 Verification Summary

| ID | Brief claim | Verdict | Primary evidence |
|----|-------------|---------|------------------|
| **I1** | `LiveExerciseRunner` — no production callers | **CONFIRMED dead (production)** | Grep: only `LiveExerciseRunnerTest.kt`, `ParityRunner.kt`, `iosApp/kmp-build-inputs.xcfilelist`. No `shared/` matches. `ExerciseLiveScreen` / `LegacyKmpTrainingSessionFactory` absent from `kmp-app/`. |
| **I2** | `executionStartMs`, `lastSmoothedAngles` dead in `MovitTrainingEngine` | **CONFIRMED** | `executionStartMs` written `:485,:606`, never read. `lastSmoothedAngles` declared `:408`, never written or read. |
| **I3** | `MainPathFrameResult` echo fields unused | **CONFIRMED (read side)** | `rawTrackedAngles`, `skippedForFrame`, `allJointsVisible` passed into `runMainPath` and copied into result; `MovitTrainingEngine` reads only `currentPhase`, `inStartPosition`, `positionResult`, `frameJoint` (`:672-698`). |
| **I4** | `StubCameraFrameSource` / `StubPoseDetector` leftovers | **CONFIRMED dead** | Only self-references + `iosApp` xcfilelist. Comments: "WS-0 placeholder — replaced by CameraX/MediaPipe in WS-4". |
| **I5** | `formatTrainingPipelinePeriodicForTest` drifted from `buildPeriodicLine` | **CONFIRMED** | Production appends `backlog=$backlog` (`TrainingPipelineDiagnostics.kt:200-201`); test helper omits it (`:267-269`). Also overlaps Track K finding [K-06]. |
| **I6** | NoPose signal duplication (C7) | **CONFIRMED duplication** | Identical `FeedbackSignal` blocks at `TrainingSessionViewModel.kt:1027-1040` vs `:1522-1535`. Active path during TRAINING no-pose: supervisor only (`:445-452` → `ShowNoPoseWarning`). Presence-bridge `NoPoseWarning` path unreachable when `runState == TRAINING` (engine never receives `!hasPose` frames). |
| **I7** | Readiness-gate layer overlap | **See matrix below** | `StartPoseGate` instantiated twice (setup gate + engine pipeline). `evaluateJointVisibility` called twice per training frame. Scene checks split between setup (`PoseSceneDetector` in gate) and training (`PositionValidator`). |
| **I8** | `EngineMetrics.positionErrorCount` redundant | **CONFIRMED** | Set to `positionErrors.size` (`MovitTrainingEngine.kt:803`). No production reader of `metricsSnapshot().positionErrorCount`; VM uses `positionErrors` list (`TrainingSessionViewModel.kt:1620`). |
| **I9** | Three parallel metric snapshots | **Partial overlap, one dead** | `EngineMetrics` — **live** (VM `metricsSnapshot()`). `LiveExerciseRunner.Metrics` — **test-only** subset. `TrainingSessionState` via `SessionOrchestrator.snapshot()` — **test-only** (`SessionOrchestratorTest.kt:29`). |
| **I10** | Report builder V1 vs V2 | **Both live — layered, not duplicate** | `MovitPostTrainingReportBuilder.build` (`MovitPostTrainingReport.kt:189`) delegates rich analysis to `MovitPostTrainingReportBuilderV2.build` when `repDetails.isNotEmpty()` (`:246-258`). `MovitSessionReportBuilder` used for session/day aggregation (`TrainingSessionWriteHooks.kt:110,168`). |
| **I11** | `PoseDetector.buildPoseFrame` — who calls outside debug? | **CONFIRMED no callers** | Implementations at `MediaPipePoseDetector.kt:265`, `IosPoseDetector.kt:92`, stubs; **zero** `.buildPoseFrame(` invocations. Live paths call `PoseFrameAssembler.assemble` directly (`CameraXFrameSource.kt:177`, `IosCameraFrameSource.kt:106`). |
| **I12** | Pattern sweep (`@Deprecated`, TODO, unused internal, expect) | **See §I12 expansion** | No `TODO`/`FIXME` in scoped modules. `@Deprecated` only in `SpeechSynthesizer.android.kt` (Java API) and out-of-scope `WorkoutRunProgress.kt`. `expect` declarations in scoped modules all have platform `actual`s. |

---

## I7 — Readiness Gate Matrix

| Component | Phase / when | What it checks | Inputs | Output / effect | Overlap with others |
|-----------|--------------|----------------|--------|-----------------|---------------------|
| **`SetupReadinessGate`** | `SETUP_POSE`, `COUNTDOWN`, `RESUME_COUNTDOWN` (VM `shouldValidatePose()` states) | Region → posture → direction → angles progression; rolling-window confirmation; countdown pose guard | Raw `angles`, `landmarks`, `ExerciseConfig`, `poseVariantIndex`, `isFrontCamera` | `SetupReadinessResult` → UI setup fields; `PoseConfirmed` / `PoseInvalid` supervisor signals (`TrainingSessionViewModel.kt:479-525`) | **Embeds `StartPoseGate`** for `isInStartPose` / `isCountdownPoseValid` (`SetupReadinessGate.kt:53-55,77,172`). **Shares `PoseSceneDetector`** logic family with `PositionValidator`. |
| **`StartPoseGate` (in `SetupReadinessGate`)** | Setup + countdown | Config `startPose` box; rough countdown tolerance | Mirrored tracking angles map | `inStartPose`, countdown validity | Same class, **different methods** than in-run gate (`isInStartPose` vs `isInStartPosition`). |
| **`StartPoseGate` (in engine `FramePipelineExecutor`)** | `TRAINING` (inside `runMainPath` after visibility gate) | In-run "start position" = primary joints in UP/hold counted bands | **Smoothed** angle map from `AngleSmoother` | `inStartPosition` → `lastInStartPosition` → `EngineMetrics` (field itself unused in VM — see [I-13]) | **Conceptual overlap** with setup start-pose checks but **different predicates** (`StartPoseGate.kt:23-27` vs `:83-103`). Not same-frame duplicate (disjoint lifecycle phases). |
| **`VisibilityMonitor`** | `TRAINING` only (`MovitTrainingEngine.processPoseFrame`) | Per-joint visibility vs `minVisibility`; grace/warn/pause state machine | Joint visibility map from landmarks | `VisibilityCheckResult` → pause/skip counting → presence events | **`evaluateJointVisibility` called twice** per frame: explicit `:628` then inside `checkVisibility` (`VisibilityMonitor.kt:37`). |
| **`PositionValidator`** | `TRAINING` inside `runMainPath` when `landmarks != null` | Config `positionChecks` (distance/angle/scene); scene lock | Landmarks, `currentPhase`, bilateral flip, `isFrontCamera` | `PositionValidationResult` → position errors/warnings on overlay | Scene axis logic **parallel** to setup `PoseSceneDetector` but **different lifecycle** (setup = guide user into frame; training = enforce checks mid-rep). |

**Same-frame real duplication (TRAINING)**:
1. `visibilityMonitor.evaluateJointVisibility` ×2 (`MovitTrainingEngine.kt:628-636`, `VisibilityMonitor.kt:37`).
2. `VirtualLandmarks.ensureAppended` potentially twice (visibility build `:819` + inside validators) — noted for Track D, not re-adjudicated here.

**Not same-frame duplication**:
- `SetupReadinessGate` vs engine `StartPoseGate` — disjoint session phases.
- `SupervisorAction.ValidatePose` emitted by `SessionSupervisor` on setup `PoseFrame` (`SessionSupervisor.kt:225`) but VM handles as **`Unit`** (`TrainingSessionViewModel.kt:996`) while doing setup validation directly on the worker (`:479-487`) — **dead action + duplicated intent**.

---

## PF Judgments (Track I scope)

| PF | Claim | Judgment | Finding |
|----|-------|----------|---------|
| **PF-14** | `executionStartMs` / `lastSmoothedAngles` dead in engine | **CONFIRMED** | [I-02] |
| **PF-15** | `LiveExerciseRunner` no production callers | **CONFIRMED** | [I-01] |
| **PF-17** | Test diagnostics formatter drift (`backlog` line) | **CONFIRMED** | [I-05] (also [K-06] in Track K) |

---

## OQ-07 — `LiveExerciseRunner`: future API or migration leftover?

| Aspect | Finding |
|--------|---------|
| **Production wiring** | `TrainingSessionViewModel` constructs `MovitTrainingEngine` directly (`TrainingSessionViewModel.kt:1644-1651`). No import or reference to `LiveExerciseRunner` in `feature/training/`, `iosApp/` Swift, or workspace `shared/`. |
| **Historical context** | Class KDoc: "Phase 07 WS-2" facade delegating to `MovitTrainingEngine` (`LiveExerciseRunner.kt:7-9`). Roadmap docs reference retired `ExerciseLiveScreen` POC; those sources are **not present** in current `kmp-app/`. |
| **Remaining consumers** | `LiveExerciseRunnerTest.kt`, `ParityRunner.kt` (golden replay harness). Compiled into iOS framework via `kmp-build-inputs.xcfilelist` but **not invoked** from iOS app code. |
| **Judgment** | **Migration leftover kept as a thin test harness**, not an active public API surface. Safe to deprecate/remove from production binaries after relocating parity tests to call `MovitTrainingEngine` directly — **needs product-owner confirmation** before deletion (parity tests are valuable). |

---

## Findings

### [I-01] `LiveExerciseRunner` has no production callers
- **Severity**: P3
- **Type**: Dead-code
- **Status**: CONFIRMED
- **Related-PF**: PF-15
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/LiveExerciseRunner.kt:10-68`, `kmp-app/core/training-engine/src/commonTest/kotlin/com/movit/core/training/session/LiveExerciseRunnerTest.kt:20`, `kmp-app/core/training-engine/src/commonTest/kotlin/com/movit/core/training/testing/ParityRunner.kt:74`
- **Evidence**: Workspace ripgrep for `LiveExerciseRunner` returns only the class file, its test, `ParityRunner`, docs, and `iosApp/kmp-build-inputs.xcfilelist`. No matches in `feature/training/`, `iosApp/*.swift`, or `shared/`. Production session uses `MovitTrainingEngine` via `TrainingSessionViewModel.buildEngine()` (`:1636-1652`).
- **Impact**: ~70 lines + callbacks maintained without runtime benefit; confuses readers searching for the "live runner" entry point. Included in release framework binary on iOS via KMP compile list.
- **Fix-sketch**: Mark `@Deprecated` with migration note; point `ParityRunner` at `MovitTrainingEngine`; delete facade after test migration.
- **Effort**: S
- **Verified-by**: pending

### [I-02] Dead engine fields `executionStartMs` and `lastSmoothedAngles`
- **Severity**: P3
- **Type**: Dead-code
- **Status**: CONFIRMED
- **Related-PF**: PF-14
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/MovitTrainingEngine.kt:404-408,485,606`
- **Evidence**: `executionStartMs` assigned in `start()` and first `processPoseFrame`; no reads in file or module. `lastSmoothedAngles` declared `private var` with `emptyMap()` initializer — never assigned or read (grep single hit is declaration only).
- **Impact**: Noise for maintainers; suggests unfinished timing/smoothing feature. No runtime cost beyond two fields per engine instance.
- **Fix-sketch**: Remove both fields or wire `executionStartMs` into `ExerciseWorkoutSummary` / reports if that was the intent.
- **Effort**: S
- **Verified-by**: pending

### [I-03] `MainPathFrameResult` echo fields never read from pipeline output
- **Severity**: P3
- **Type**: Dead-code
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/engine/pipeline/FramePipelineExecutor.kt:105-119,131-149`, `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/MovitTrainingEngine.kt:654-698`
- **Evidence**: `runMainPath` copies `skippedForFrame`, `rawTrackedAngles`, `allJointsVisible` into `MainPathFrameResult`. Consumer uses `pipelineResult.currentPhase`, `inStartPosition`, `positionResult`, `frameJoint` only. `allJointsVisible` is computed **before** the call (`:630`) and used for `sceneWarnings` (`:686`) — not read back from result.
- **Impact**: Unnecessary data-class fields and allocations in hot path (3 references held per frame in result object).
- **Fix-sketch**: Slim `MainPathFrameResult` to consumed fields; keep inputs as `runMainPath` parameters only.
- **Effort**: S
- **Verified-by**: pending

### [I-04] WS-0 stub camera/pose classes unused
- **Severity**: P3
- **Type**: Dead-code
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `kmp-app/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/StubCameraFrameSource.kt:7-18`, `kmp-app/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/StubPoseDetector.kt:9-22`
- **Evidence**: Grep shows only class definitions. `MovitPoseCaptureModule.kt` wires `MediaPipePoseDetector` / `CameraXFrameSource`, not stubs. Comments explicitly say replaced in WS-4.
- **Impact**: Dead code in androidMain; still listed in iOS xcfilelist (harmless compile inclusion).
- **Fix-sketch**: Delete stubs or move to `androidTest` fixtures if needed for future unit tests.
- **Effort**: S
- **Verified-by**: pending

### [I-05] Diagnostics periodic formatter duplicated and drifted
- **Severity**: P3
- **Type**: Duplication
- **Status**: CONFIRMED
- **Related-PF**: PF-17
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/diagnostics/TrainingPipelineDiagnostics.kt:166-210,228-277`
- **Evidence**: `buildPeriodicLine` (production) includes:
  ```kotlin
  val backlog = vmIngress - vmProcessed
  if (backlog > 0) append(" backlog=$backlog")
  ```
  at `:200-201`. `formatTrainingPipelinePeriodicForTest` rebuilds the same string manually but jumps from `proc=$vmProcessed` to engine section without `backlog` (`:267-270`). Test `TrainingPipelineDiagnosticsTest.kt:10` exercises the stale copy.
- **Impact**: Unit tests can pass while production log format diverges; triage scripts parsing test output miss backlog field.
- **Fix-sketch**: Extract shared `formatPeriodicLine(...)` used by both production and test; or call `buildPeriodicLine` from test via `@VisibleForTesting`.
- **Effort**: S
- **Verified-by**: pending

### [I-06] Duplicate NoPose feedback — supervisor path live, presence path unreachable in TRAINING
- **Severity**: P2
- **Type**: Duplication
- **Status**: CONFIRMED
- **Related-PF**: PF-16 (structural; Track C scope)
- **Files**: `kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt:445-452,1024-1040,1519-1535`, `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/MovitTrainingEngine.kt:577-583`
- **Evidence**: Identical `FeedbackSignal` (same `dedupeKey = "nopose:warn"`, same localized strings) in `handleSupervisorAction(ShowNoPoseWarning)` and `handlePresenceEvent(NoPoseWarning)`. During `TRAINING`, VM returns early on `!frame.hasPose` and sends `SupervisorSignal.NoPoseFrame` (`:445-452`) — **never** calls `engine.processFrame`. Engine `presenceBridge.onNoPoseFrame` (`MovitTrainingEngine.kt:580-581`) therefore does not run for TRAINING no-pose. `ShowNoPoseWarning` emitted from `SessionSupervisor.handleNoPoseDuringTraining` (`SessionSupervisor.kt:531-533`).
- **Impact**: ~30 lines duplicated; risk of future edit skew. Presence-bridge `NoPoseWarning` handler is **latent dead branch** under current VM wiring (would only fire if `ProcessFrame` delivered a null-landmarks frame while engine running — VM does not do this on no-pose path).
- **Fix-sketch**: Single `emitNoPoseWarning(elapsedMs)` private function; route both supervisor and presence events through it; or remove presence `NoPoseWarning` if supervisor owns all no-pose UX.
- **Effort**: S
- **Verified-by**: pending

### [I-07] `evaluateJointVisibility` invoked twice per training frame
- **Severity**: P2
- **Type**: Duplication
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/MovitTrainingEngine.kt:628-636`, `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/visibility/VisibilityMonitor.kt:29-37`
- **Evidence**: `processPoseFrame` calls `evaluateJointVisibility` for `allJointsVisible`, then `checkVisibility` which calls `evaluateJointVisibility` again internally (`VisibilityMonitor.kt:37`).
- **Impact**: Duplicate map iteration + rule evaluation per joint per frame at ~25 fps. Small but definite hot-path waste (`NEEDS-DATA` for µs estimate).
- **Fix-sketch**: Add `checkVisibility` overload accepting precomputed `List<JointVisibility>`; or return details from `checkVisibility` only.
- **Effort**: S
- **Verified-by**: pending

### [I-08] `EngineMetrics.positionErrorCount` is a redundant derived field
- **Severity**: P3
- **Type**: Dead-code
- **Status**: CONFIRMED
- **Related-PF**: PF-14 (same family)
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/MovitTrainingEngine.kt:793-803,895`, `kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt:1614-1620`
- **Evidence**: `positionErrorCount = positionErrors.size` in `metricsSnapshot()`. VM `refreshSkeletonOverlay` reads `metrics?.positionErrors` but never `positionErrorCount`. Report pipeline computes counts from `rep.positionErrors.size` in `MovitPostTrainingReportBuilderV2.kt:436`.
- **Impact**: Redundant field in snapshot struct built up to 3×/frame (Track F PF-12).
- **Fix-sketch**: Remove `positionErrorCount` from `EngineMetrics`; consumers use `positionErrors.size`.
- **Effort**: S
- **Verified-by**: pending

### [I-09] `TrainingSessionState` snapshot API unused in production
- **Severity**: P3
- **Type**: Dead-code
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/SessionOrchestrator.kt:121-135`, `kmp-app/core/training-engine/src/commonTest/kotlin/com/movit/core/training/session/SessionOrchestratorTest.kt:29`
- **Evidence**: `SessionOrchestrator.snapshot()` → `TrainingSessionState` only referenced from `SessionOrchestratorTest`. VM reads `supervisor.state` (`SessionRunState`) and `engine.metricsSnapshot()` (`EngineMetrics`), not orchestrator snapshot. `TrainingSessionStateOverlay` in UI is a **Compose widget name**, unrelated to `TrainingSessionState` data class.
- **Impact**: Parallel immutable model duplicates fields already exposed via engine metrics + supervisor state; maintenance burden.
- **Fix-sketch**: Remove `snapshot()` until a consumer needs it, or wire debug overlay to it intentionally.
- **Effort**: S
- **Verified-by**: pending

### [I-10] Report builders V1/V2 — intentional delegation, not dead duplication
- **Severity**: P3
- **Type**: Architecture
- **Status**: REFUTED (as "dead duplicate")
- **Related-PF**: —
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/report/MovitPostTrainingReport.kt:189-288`, `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/report/MovitPostTrainingReportBuilderV2.kt:20`, `kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionWriteHooks.kt:110,168`
- **Evidence**: `MovitPostTrainingReportBuilder.build` is the **facade** used by `TrainingSessionWriteHooks.buildPostTrainingReport`. When `summary.repDetails.isNotEmpty()`, delegates analysis sections to `MovitPostTrainingReportBuilderV2.build` (`MovitPostTrainingReport.kt:246-258`). `MovitSessionReportBuilder` serves **multi-exercise session** aggregation (distinct concern).
- **Impact**: None negative — layering is intentional. Empty `repDetails` skips V2 (legacy/journal-only path).
- **Fix-sketch**: Document facade relationship in KDoc; optional rename `MovitPostTrainingReportBuilder` → `MovitPostTrainingReportFacade` for clarity.
- **Effort**: S
- **Verified-by**: pending

### [I-11] `PoseDetector.buildPoseFrame` implemented but never invoked
- **Severity**: P3
- **Type**: Dead-code
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/boundary/PoseDetector.kt:16-20`, `kmp-app/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/MediaPipePoseDetector.kt:265-273`, `kmp-app/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/CameraXFrameSource.kt:177`
- **Evidence**: Zero call sites for `.buildPoseFrame(`. Live Android/iOS paths assemble frames with `PoseFrameAssembler.assemble(...)` in camera hosts after detection callbacks. Interface method duplicates that one-liner in three implementers.
- **Impact**: Misleading API surface; implementers must maintain dead override.
- **Fix-sketch**: Remove from `PoseDetector` interface; keep assembly in `PoseFrameAssembler` only, or call `detector.buildPoseFrame` from hosts to centralize.
- **Effort**: M (interface change across modules)
- **Verified-by**: pending

### [I-12] `SupervisorAction.ValidatePose` emitted but ignored by VM
- **Severity**: P3
- **Type**: Dead-code
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/SessionSupervisor.kt:225,272,438,469`, `kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt:479-487,996`
- **Evidence**: Supervisor emits `ValidatePose` on setup/countdown `PoseFrame`. VM `handleSupervisorAction` branch is `Unit`. Setup validation already runs synchronously on worker via `readinessGate.validate` before/without consuming that action.
- **Impact**: Wasted supervisor action channel traffic; dual architecture (signal + action) for same concern.
- **Fix-sketch**: Stop emitting `ValidatePose` or handle it explicitly; prefer single path through `readinessGate` on worker.
- **Effort**: S
- **Verified-by**: pending

### [I-13] `EngineMetrics.isInStartPosition` populated but never consumed
- **Severity**: P3
- **Type**: Dead-code
- **Status**: CONFIRMED
- **Related-PF**: PF-14 (extended)
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/MovitTrainingEngine.kt:678,787,879`, `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/engine/pipeline/FramePipelineExecutor.kt:73-74`
- **Evidence**: `lastInStartPosition` updated from pipeline; exposed in `metricsSnapshot()`. Grep shows no reads outside `MovitTrainingEngine` and tests. VM overlay/ROM mappers do not use it.
- **Impact**: Snapshot field built every `metricsSnapshot()` call without consumer.
- **Fix-sketch**: Remove from `EngineMetrics` or wire to UI (e.g. start-position hint).
- **Effort**: S
- **Verified-by**: pending

### [I-14] `StartPoseGate.boundaryBuffer` stored but unused
- **Severity**: P3
- **Type**: Dead-code
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/engine/StartPoseGate.kt:16-17`
- **Evidence**: Field assigned from `stabilityPolicy.boundaryBuffer` with `@Suppress("unused")`. No reads in class body.
- **Impact**: Suggests incomplete migration from legacy boundary-buffer start-pose logic.
- **Fix-sketch**: Remove field or apply buffer in `isInStartPose` / `isInStartPosition` if parity requires it.
- **Effort**: S
- **Verified-by**: pending

### [I-15] `PositionValidator.resolvedPosition` deprecated legacy accessor
- **Severity**: P3
- **Type**: Dead-code
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/position/PositionValidator.kt:50-51`
- **Evidence**: KDoc: "@deprecated Kept for legacy callers. Use sceneExpectation instead." Grep shows no reads of `resolvedPosition` in `kmp-app/`.
- **Impact**: Public val on validator exposing dead model.
- **Fix-sketch**: Remove after confirming no external ABI consumers.
- **Effort**: S
- **Verified-by**: pending

---

## I12 — Pattern Sweep Notes

| Pattern | Scoped modules result |
|---------|----------------------|
| `@Deprecated` | None in scope. Nearest: `WorkoutRunProgress.kt` in `feature/library` (out of scope) — `@Deprecated("Use WorkoutRunStore + TrainingSessionFlowCoordinator")`; `WorkoutRunProgressStore` retained for tests only per `WorkoutRunModels.kt:868`. |
| `TODO` / `FIXME` | **Zero** in `pose-capture`, `training-engine`, `feature/training`. |
| `internal fun` without consumers | `formatTrainingPipelinePeriodicForTest` — **used** by `TrainingPipelineDiagnosticsTest`. No other orphan `internal fun` found in quick sweep of `training-engine/commonMain`. |
| `expect` without `actual` | All `expect` in scoped `training-engine` boundary/diagnostics have `androidMain`/`iosMain` `actual`s. `pose-capture` uses `training-engine` boundary types (no separate expect layer). |

---

## Coverage Statement

| Area | Coverage |
|------|----------|
| **Fully verified (I1–I12)** | All twelve seed items investigated with ripgrep + targeted file reads. |
| **Deep-read hot files** | `LiveExerciseRunner.kt`, `MovitTrainingEngine.kt` (fields, `processPoseFrame`, `metricsSnapshot`), `FramePipelineExecutor.kt`, `SetupReadinessGate.kt`, `StartPoseGate.kt`, `VisibilityMonitor.kt`, `PositionValidator.kt` (header), `TrainingSessionViewModel.kt` (worker, supervisor/presence handlers, setup path), `TrainingPipelineDiagnostics.kt`, stub classes, report builder delegation, `PoseDetector` boundary. |
| **Pattern sweep** | `@Deprecated`, `TODO`/`FIXME`, `LiveExerciseRunner`, `buildPoseFrame`, `snapshot()`, `metricsSnapshot` consumers, stub classes, `ValidatePose`. |
| **Cross-module search** | `iosApp/` (xcfilelist + Swift — no runtime refs). Workspace `shared/` — **no directory / no matches**. |
| **Not exhaustively line-audited** | Every one of 341 `.kt` files in scope (e.g. full `RepCounter.kt`, all `report/*`, all `geometry/*`, iOS Swift bridges). Dead-code in those files may exist beyond this sweep. |
| **Out of scope but noted** | `feature/training-debug` (uses `MediaPipeSyncPoseDetector`, not stubs). `feature/library` deprecated `WorkoutRunProgress`. |

---

## Top Remediation Hints (Track I only)

1. **[I-06]** Consolidate NoPose feedback (S, reduces divergence risk).
2. **[I-07]** Deduplicate visibility evaluation (S, hot path).
3. **[I-01]** Deprecate/remove `LiveExerciseRunner` after parity test migration (S).
4. **[I-05]** Unify diagnostics formatter (S, test fidelity).
5. **[I-02]/[I-08]/[I-13]** Batch-remove dead engine/snapshot fields (S).

---

*Verified-by: pending (all P2/P3 findings await adversarial re-check per §8.5).*
