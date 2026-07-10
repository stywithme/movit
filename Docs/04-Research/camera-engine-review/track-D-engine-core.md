# Track D — Engine Core Per-Frame Path

**Scope**: `kmp-app/core/training-engine/src/commonMain/` hot path from `MovitTrainingEngine.processPoseFrame` through pipeline, phase/rep, visibility, position, feedback.
**Mode**: READ ONLY — evidence-backed review; no source edits.
**Date**: 2026-07-10

---

## Summary

The per-frame engine path allocates on the order of **~40–90+ heap objects/frame** on the front-camera default path (dominated by `PoseFrame.mirrored()`, map copies, and `PositionValidationResult`), with a **confirmed left/right contract bug**: landmarks/angles are mirrored once, then `isFrontCamera=true` is passed again into visibility/position helpers that re-apply L/R remapping — so `left_elbow` **angles** track the opposite anatomical side from **visibility** (and from position-check landmark resolution). Dead fields `executionStartMs` / `lastSmoothedAngles` are confirmed. Triple smoothing (One-Euro upstream → MA window → phase hysteresis) is structurally confirmed; end-to-end latency needs device measurement. `SessionOrchestrator.snapshot()` is test-only in production tree.

---

## D1 — Allocation-per-frame table

Assumptions for counts: front camera (default), pose present, `N` tracked joints (typical 2–6), `P` primary joints, position validator present, not skip-counting, `shouldTrackState=true`. Kotlin/Native object counts are approximate (maps/lists as 1 container + entries).

| # | Site | What is allocated | Est. objects/frame | Notes |
|---|---|---|---|---|
| 1 | `PoseFrame.mirrored()` | New `List`×2 (norm+world) via `toMutableList` + swaps; new `JointAngles` via `copy`; new `PoseFrame` via `copy` | **~75–110** | 35 Landmark refs swapped in-place in new list (landmarks themselves not cloned); world list same; `isFrontCamera→false`. Rear camera: **0**. |
| 2 | `extractTrackedAngles` | `MutableMap` angles; optional `MutableSet` skipped + `processedPairs`; `TrackedAnglesExtractResult` | **~N+3–8** | Always. |
| 3 | `buildJointVisibilities` | `associate` → `Map<String,Float>`; `ensureAppended` | **~N+1** (append **0** if size≥35) | Assembler already appended virtuals. |
| 4 | `evaluateJointVisibility` | `List<JointVisibility>` + one object per strict/lenient entry | **~N+1** | |
| 5 | `checkVisibility` | Calls `evaluateJointVisibility` **again** → second list; `VisibilityCheckResult` | **~N+2** | Duplicate of (4). |
| 6 | `AngleSmoother.smooth` | `mapValues` → new `Map<String,Double>` | **~N+1** | Buffers reused. |
| 7 | `filterKeys` primary | New map of primaries | **~P+1** | |
| 8 | `PhaseStateMachine.update` | Usually none; `getPhaseTimings().toMap()` only on rep complete | **0** typical | |
| 9 | `PositionValidator.validate` | Tilt-corrected list (if tilt≠0); error/warning/tip/debug lists; `PositionValidationResult`; per-check `PositionCheckDebug`; possible `mirrorCheckLandmarks` copies | **~15–40+** | Scene detector may allocate internally. |
| 10 | `FrameEvaluationPipeline.evaluate` | `jointEvaluator.evaluate` → `reusable.toMap()`; `mapValues` → `JointStateInfo`s; `FrameJointEvaluationResult` | **~2N+3** | `JointEval` instances reused then copied into new map. |
| 11 | `MainPathFrameResult` | One data class holding refs | **1** | |
| 12 | `forScoring` | Same map or `filterKeys` copy | **0 or ~N** | |
| 13 | `JointErrorCollection.collectJointErrors` | `ArrayList` + `JointError` per bad joint | **~1–N** | |
| 14 | Position→rep hooks | Snapshots / set inserts | **0–few** | |
| 15 | `metricsSnapshot()` | **Not** called inside `processPoseFrame` | **0** in engine frame | VM may call 1–3× separately (PF-12). |

**Totals (front cam, validator on, N≈4)**: roughly **55–95 objects/frame** engine-side; at **25 fps** → **~1.4k–2.4k objects/s** from this path alone (GC pressure **NEEDS-DATA** on device). Rear camera drops row (1) (~70 objects).

Evidence anchors: `PoseFrame.kt:22-32`, `MovitTrainingEngine.kt:610-668`, `FramePipelineExecutor.kt:61-123`, `FrameEvaluationPipeline.kt:14-17`, `VisibilityMonitor.kt:29-37`, `AngleSmoother.kt:30-33`.

---

## D2 — Dual visibility evaluation

**Answer**: Yes — `checkVisibility` **recomputes** the same joint-visibility list that the caller already built.

1. Engine: `visibilityDetails = visibilityMonitor.evaluateJointVisibility(visibilities)` then derives `allJointsVisible` — `MovitTrainingEngine.kt:628-630`.
2. Engine: `visibilityMonitor.checkVisibility(jointVisibilities = visibilities, …)` — `:632-636`.
3. Inside `checkVisibility`: `val visibilityDetails = evaluateJointVisibility(jointVisibilities)` — `VisibilityMonitor.kt:37`.

Same inputs → duplicate `VisibilityJointRules.evaluate` + duplicate `List`/`JointVisibility` allocations. Merge into one pass (return details + state machine result together) is straightforward.

---

## D3 — Skip-counting order vs position work

**Skip order**: `skipCounting` is decided **after** angle extract + both visibility passes, **before** `runMainPath` — `MovitTrainingEngine.kt:612-652`. If true, frame returns without phase/eval/rep updates. That is **correct** for “do not advance counting while visibility-paused,” but still pays extract+visibility cost on skipped frames.

**Position work**: Inside `runMainPath`, if `landmarks != null && validator != null`, `validate(...)` **always** runs — `FramePipelineExecutor.kt:81-101` — including IDLE/START. Per-check activity is gated by `isActiveInPhase` (`PositionValidator.kt:121-140`), but **live scene detection** (`sceneDetector.detect`) always runs (`:94-95`). Separately, engine only **stores** position issues into rep scoring when `shouldTrackState` (`MovitTrainingEngine.kt:706-745`) — so scene/check work can run in phases that never feed `RepCounter`.

---

## D4 — Front-camera mirror path (`left_elbow` trace)

### Pipeline contract (capture)

Analysis bitmaps are **not** horizontally mirrored; engine is expected to use unmirrored MediaPipe landmarks + `PoseFrame.mirrored()` — `MediaPipePoseDetector.kt:207-211`.

### Engine steps

| Step | Code | Effect on `left_elbow` |
|---|---|---|
| A | `workingFrame = frame.mirrored()` when `frame.isFrontCamera` — `MovitTrainingEngine.kt:610` | `mirrorLandmarks`: swap idx 13↔14 (etc.); `mirrorAngles`: `leftElbow ↔ rightElbow`; copy sets `isFrontCamera=false` — `PoseFrame.kt:22-31`, `PoseLandmarkMirroring.kt:19-51` |
| B | `extractTrackedAngles(..., landmarks=workingFrame.landmarks, isFrontCamera=frame.isFrontCamera)` — `:612-620` | Angle map uses **mirrored** `JointAngles` **without** camera flag (`buildAngleMap` only uses bilateral `isFlipped`) — `JointAngleTracker.kt:113-120`. Camera flag only hits `computeJointVisibility` in any-side branch — `:90-91`. |
| C | `buildJointVisibilities(workingFrame.landmarks, frame.isFrontCamera)` — `:626` | Always `computeJointVisibility(joint, resolved, isFrontCamera=true)` — `:821-831` |
| D | `runMainPath(..., landmarks=workingFrame.landmarks, isFrontCamera=frame.isFrontCamera)` — `:654-664` | `PositionValidator.validate` gets **mirrored** landmarks + **original** `isFrontCamera=true` — `FramePipelineExecutor.kt:83-91` |

### Numerical / logical trace

Let anatomical (pre-mirror) values be:

- MediaPipe idx 13 (anatomical L elbow) visibility **0.9**, `angles.leftElbow = 45°`
- MediaPipe idx 14 (anatomical R elbow) visibility **0.2**, `angles.rightElbow = 160°`

After `mirrored()`:

- List slot 13 holds former 14 → vis **0.2**; slot 14 holds former 13 → vis **0.9**
- `angles.leftElbow = 160°`, `angles.rightElbow = 45°`

**Angle path** for config joint `left_elbow` (no bilateral flip):

- `getAngleForJoint(..., "left_elbow")` → `160°` = **anatomical RIGHT** elbow angle.

**Visibility path** `computeJointVisibility("left_elbow", mirroredLandmarks, isFrontCamera=true)`:

- Base indices `[11,13,15]` — `JointLandmarkMapping.kt:60`
- With `isFrontCamera`: each index remapped via `mirroredIndex` → `[12,14,16]` — `:93`
- Slot 14 on mirrored list = anatomical L elbow → min visibility uses **anatomical LEFT** (0.9)

**Result**: for the same joint code, **angle = anatomical right**, **visibility = anatomical left**. That is a **double application** of L/R correction (buffer already swapped; flag swaps indices again).

If `isFrontCamera=false` were passed with already-mirrored landmarks, visibility would read slots `[11,13,15]` → anatomical right → **match angles**.

**PositionValidator** (`PositionValidator.kt:142-147`):

- Comment documents XOR of bilateral × front camera.
- Front cam, not flipped: `shouldMirrorLandmarks = true` → check `left_elbow` renamed to `right_elbow` → `jointToLandmark` idx **14** on mirrored buffer → **anatomical LEFT** landmark.
- So position checks for `left_elbow` also disagree with the mirrored angle stream (anatomical right).

**Scene detection** (`CameraPositionDetector.kt:422-426`): `isFrontCamera` flips side labels. Combined with pre-swapped landmarks this is a second L/R transform on scene axes — likely intended as a cancel for facing/side, but it is the same undocumented dual-contract as visibility (mirrored buffer + flag).

**Virtual 33/34**: `swapMap` has no 33/34 — `PoseLandmarkMirroring.kt:8-17`. Neck/spine midpoints are symmetric in L/R shoulders/hips, so values remain valid after limb swaps without recompute.

**Verdict for double-flip risk**: **CONFIRMED** for visibility (and position-check landmark resolution) vs angle/phase stream. Not a no-op cancel for joint visibility.

---

## D5 — Phase machine, rep count, bilateral

**Completion path** (single-flight per frame):

1. `PhaseStateMachine` UP→START → `onRepCompleted` once if interval OK and `!repCountedThisCycle` — `PhaseStateMachine.kt:252-298`, `:308-310`.
2. Wired to `repCompletion.onPhaseMachineWantsComplete()` — `MovitTrainingEngine.kt:423`.
3. `RepCompletionSignal` sets `pending=true` — `RepCompletionSignal.kt:6-7`.
4. End of frame: `consumeIfPendingAndHandle()` → `completeRep()` once, then `bilateral.onRepCounted` only if count increased — `RepCompletionCoordinator.kt:18-27`, `MovitTrainingEngine.kt:765`.

**Incomplete**: `onRepIncomplete` may `discardCurrentRepAttempt` + `clearTimings` — `MovitTrainingEngine.kt:425-430`; discard only clears in-progress tracking (`RepCounter.kt:258-260`), does not decrement completed reps.

**Bilateral flip**: after successful count, `onSideChanged` resets smoother + evaluator — `MovitTrainingEngine.kt:416-418`; does not signal another rep. `repCountedThisCycle` blocks a second complete in the same cycle.

**Double-count / lost-rep**: No evidence of double `completeRep` from phase+bilateral on one frame. Lost rep possible only via TOO_FAST/TOO_SLOW incomplete or hold `minRepInterval` early return (`RepCounter.kt:284-286`) — by design. Non-hold `completeRep` does **not** re-check `minRepInterval` (phase machine already did).

---

## D6 — Dead / unused engine state

| Symbol | Evidence | Verdict |
|---|---|---|
| `executionStartMs` | Declared `:404`; set in `start()` `:485` and first frame `:606`; **no reads** in class | **Dead write** |
| `lastSmoothedAngles` | Declared `:408`; **never assigned** (grep: no `lastSmoothedAngles =`) | **Dead field** |
| `MainPathFrameResult.rawTrackedAngles` | Packed `:109`; never read by `processPoseFrame` | **Unused result field** |
| `MainPathFrameResult.skippedForFrame` | Used inside `runMainPath` before pack; result field unused by caller | **Unused on result** |
| `MainPathFrameResult.allJointsVisible` | Packed `:119`; caller uses **local** `allJointsVisible` for sceneWarnings `:686` | **Redundant on result** |
| `allJointsVisible` local | **Used** `:630`, `:686` | Live |

---

## D7 — Clock equivalence

| Consumer | Time source | Wiring |
|---|---|---|
| `ExecutionClock.onFrame` | Prefers `frame.timestampMs`, else wall | `SessionOrchestrator.onFrameClock` ← `processPoseFrame` `:604` |
| `executionClock.nowMs()` | Last frame time if set, else wall | `MovitTrainingEngine.kt:128-129` |
| `PhaseStateMachine` | `timeProvider = nowMs` | `:218` — phase durations / `minRepIntervalMs` / `maxRepIntervalMs` |
| `RepCounter` | `timeProvider = nowMs` | `:264` — hold interval + timestamps |
| `VisibilityMonitor` / `FrameFeedbackEmitter` / `PresenceSupervisorBridge` | `nowMs` | same clock |
| Motion record timestamp | `frame.timestampMs` or `nowMs()` | `:725` |

When frames stall, `nowMs()` stays at **last frame timestamp** until a new frame updates the clock (`ExecutionClock.kt:42`) — phase/`minRepInterval` clocks **freeze** with the frame timeline (not wall). When frames resume with large gaps, one transition can see a huge `movementMs` → TOO_SLOW incomplete (`PhaseStateMachine.kt:291-293`). Wall vs frame divergence >30s flips pause accounting to frame timeline (`ExecutionClock.kt:35-36`).

**Impact on counting under jank**: structurally real; magnitude **NEEDS-DATA**.

---

## D8 — `SessionOrchestrator.snapshot()`

Production `commonMain` / `feature/training` have **no** callers of `SessionOrchestrator.snapshot()`. Only `SessionOrchestratorTest.kt:29` uses it. **Test/observability API only** (not on the live training hot path).

---

## Findings

### [D-01] Front-camera path allocates full mirrored PoseFrame every frame
- **Severity**: P2
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: PF-09
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/model/PoseFrame.kt:22-32`, `.../session/MovitTrainingEngine.kt:610`
- **Evidence**: `mirrored()` builds new landmark lists (norm + optional world), `mirrorAngles` → new `JointAngles`, `copy` → new `PoseFrame` with `isFrontCamera=false`. Front camera is the documented default training lens.
- **Impact**: ~70+ objects/frame on the default path; at 25 fps adds sustained allocator traffic (exact GC **NEEDS-DATA**).
- **Fix-sketch**: Mirror in-place into reusable buffers, or mirror once at assemble and thread a single “engine space” flag; avoid per-frame list copy when only index semantics change.
- **Effort**: M
- **Verified-by**: pending

### [D-02] `checkVisibility` re-runs `evaluateJointVisibility`
- **Severity**: P2
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `.../session/MovitTrainingEngine.kt:628-636`, `.../visibility/VisibilityMonitor.kt:29-37`
- **Evidence**: Engine computes `visibilityDetails` then `checkVisibility` calls `evaluateJointVisibility` again on the same map.
- **Impact**: Duplicate list/~N `JointVisibility` objects every frame; redundant CPU on visibility rules.
- **Fix-sketch**: Overload `checkVisibility` to accept precomputed details, or return `(details, result)` from one method.
- **Effort**: S
- **Verified-by**: pending

### [D-03] Mirrored landmarks + `isFrontCamera=true` desync `left_elbow` angle vs visibility
- **Severity**: P1
- **Type**: Correctness
- **Status**: REFUTED
- **Related-PF**: PF-11
- **Files**: `.../session/MovitTrainingEngine.kt:610-664`, `.../geometry/JointLandmarkMapping.kt:83-99`, `.../engine/JointAngleTracker.kt:113-120`, `.../position/PositionValidator.kt:142-147`, `.../posecapture/android/MediaPipePoseDetector.kt:207-211`
- **Evidence**: Capture leaves landmarks unmirrored; engine mirrors buffer/angles once, then passes **original** `frame.isFrontCamera` into `computeJointVisibility` / `PositionValidator`. Trace (D4): mirrored `left_elbow` angle = anatomical right; visibility with flag remaps indices to anatomical left. Position checks XOR-mirror names onto the already-swapped buffer → anatomical left for a `left_elbow` check.
- **Impact**: Visibility pause/warn and position issues can track the **opposite** limb from phase/scoring angles on front camera — false pauses or missed occlusions; form feedback on wrong side.
- **Fix-sketch**: After `mirrored()`, pass `isFrontCamera=false` into extract/visibility/validator **or** stop pre-mirroring landmarks and keep a single remapping layer. Align with `SetupReadinessGate` once contract is chosen. Add a parity unit test with asymmetric L/R visibility.
- **Effort**: M
- **Verified-by**: adversarial-grok-4.5-xhigh

### [D-04] Position validation always runs scene detection even when phase checks are inactive
- **Severity**: P2
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `.../engine/pipeline/FramePipelineExecutor.kt:81-101`, `.../position/PositionValidator.kt:94-95`, `.../session/MovitTrainingEngine.kt:706-745`
- **Evidence**: `runMainPath` always calls `validator.validate` when landmarks exist; `shouldTrackState` only gates feeding errors into `RepCounter`, not computation.
- **Impact**: Extra scene/check work every frame in IDLE/START (and whenever scoring is off).
- **Fix-sketch**: Gate `validate` on phase / `shouldTrackState`, or split cheap scene warn vs full checks.
- **Effort**: S
- **Verified-by**: pending

### [D-05] Dead fields `executionStartMs` and `lastSmoothedAngles`
- **Severity**: P3
- **Type**: Dead-code
- **Status**: CONFIRMED
- **Related-PF**: PF-14
- **Files**: `.../session/MovitTrainingEngine.kt:404-408`, `:485`, `:606`
- **Evidence**: `executionStartMs` written, never read; `lastSmoothedAngles` never written or read.
- **Impact**: Noise / confusion for maintainers; no runtime cost beyond two fields.
- **Fix-sketch**: Remove both (or wire `lastSmoothedAngles` if diagnostics need it).
- **Effort**: S
- **Verified-by**: pending

### [D-06] `MainPathFrameResult` carries unused `rawTrackedAngles` / result-level `skippedForFrame` / `allJointsVisible`
- **Severity**: P3
- **Type**: Dead-code
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `.../engine/pipeline/FramePipelineExecutor.kt:105-147`, `.../session/MovitTrainingEngine.kt:654-686`
- **Evidence**: Caller never reads `pipelineResult.rawTrackedAngles` or `.skippedForFrame`; uses local `allJointsVisible` instead of `pipelineResult.allJointsVisible`.
- **Impact**: Extra field plumbing; minor retained refs for one frame.
- **Fix-sketch**: Slim the result type to what callers use.
- **Effort**: S
- **Verified-by**: pending

### [D-07] Triple smoothing stack (MA + phase hysteresis; One-Euro upstream)
- **Severity**: P2
- **Type**: Performance
- **Status**: CONFIRMED (stack) / NEEDS-DATA (latency)
- **Related-PF**: PF-19
- **Files**: `.../engine/AngleSmoother.kt:5-33`, `.../engine/PhaseStateMachine.kt:9-15,102-112`, `TimingPolicy.kt:48` (`DEFAULT_SMOOTHING_WINDOW_SIZE = 3`), `StabilityPolicy` default hysteresis 3°
- **Evidence**: Per-frame MA window (size 3) on angles after landmark One-Euro (capture); PSM applies ±3° hysteresis on phase bands. Cumulative motion-to-phase delay not measured here.
- **Impact**: Softer jitter, added lag before phase transitions / rep complete — user-visible delay unknown without device trace.
- **Fix-sketch**: Measure phase lag vs window/hysteresis; consider shrinking window or making one stage optional (OQ-02).
- **Effort**: M
- **Verified-by**: pending

### [D-08] `ensureAppended` second call per frame is a no-op when assemble already appended
- **Severity**: P3
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: PF-10
- **Files**: `.../geometry/VirtualLandmarks.kt:15-17`, `.../geometry/PoseFrameAssembler.kt:24`, `.../session/MovitTrainingEngine.kt:819`
- **Evidence**: `ensureAppended` returns input when `size >= 35`; assembler already appends before engine. Second call is reference return — negligible cost.
- **Impact**: None material; documents safe idempotency.
- **Fix-sketch**: Optional: skip call when size known ≥35; keep as safety net.
- **Effort**: S
- **Verified-by**: pending

### [D-09] Virtual landmarks 33/34 survive limb swap without corruption
- **Severity**: P3
- **Type**: Correctness
- **Status**: CONFIRMED (safe)
- **Related-PF**: PF-10
- **Files**: `.../geometry/PoseLandmarkMirroring.kt:8-17,19-29`, `.../geometry/VirtualLandmarks.kt:24-38`
- **Evidence**: Midpoints of L/R shoulders and hips are invariant under L↔R swap; indices 33/34 are not in `swapMap` and need no index swap.
- **Impact**: No correctness bug found for 33/34 under current mirror implementation.
- **Fix-sketch**: Document invariance; Track E may still want an explicit test.
- **Effort**: S
- **Verified-by**: pending

### [D-10] Engine `metricsSnapshot()` allocates on demand; not 3× inside `processPoseFrame`
- **Severity**: P2
- **Type**: Performance
- **Status**: CONFIRMED (API cost) — per-frame ×3 is VM-side
- **Related-PF**: PF-12 (engine half)
- **Files**: `.../session/MovitTrainingEngine.kt:773-805`, `feature/training/.../TrainingSessionViewModel.kt:1553-1554`, `:1614`
- **Evidence**: `processPoseFrame` never calls `metricsSnapshot`. Each call builds a new `EngineMetrics` holding current maps/lists. VM `refreshSkeletonOverlay` / random-message paths invoke it.
- **Impact**: Engine half: O(1) snapshot object + retained map refs per call. Multiplicity belongs to Track F.
- **Fix-sketch**: Cache last snapshot invalidated on frame end; or expose `lastJointStateInfos` without full metrics.
- **Effort**: S
- **Verified-by**: pending

### [D-11] Frame-tied `nowMs` can freeze phase timing when frames stop
- **Severity**: P2
- **Type**: Correctness
- **Status**: CONFIRMED (mechanism) / NEEDS-DATA (user impact)
- **Related-PF**: —
- **Files**: `.../session/ExecutionClock.kt:32-42`, `.../session/MovitTrainingEngine.kt:128-129,218,264`, `.../engine/PhaseStateMachine.kt:281-293`
- **Evidence**: `nowMs()` returns last frame time; PSM uses it for movement duration vs `minRepIntervalMs`/`maxRepIntervalMs`.
- **Impact**: Under camera stalls, timers freeze; on resume, a large dt may mark TOO_SLOW.
- **Fix-sketch**: Use wall clock for interval guards, or clamp dt; document frame-timeline policy.
- **Effort**: M
- **Verified-by**: pending

### [D-12] `SessionOrchestrator.snapshot()` unused in production
- **Severity**: P3
- **Type**: Dead-code
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `.../session/SessionOrchestrator.kt:121-135`, `.../commonTest/.../SessionOrchestratorTest.kt:29`
- **Evidence**: Repo-wide production call sites absent; only unit test.
- **Impact**: None at runtime.
- **Fix-sketch**: Keep for tests/debug or mark explicitly as test API.
- **Effort**: S
- **Verified-by**: pending

---

## PF verdicts (Track D)

| ID | Verdict | Finding | Notes |
|---|---|---|---|
| **PF-09** | **CONFIRMED** | D-01 | `frame.mirrored()` full copy each front-camera frame. |
| **PF-10** | **CONFIRMED** (cheap double `ensureAppended`); **REFUTED** as 33/34 corruption | D-08, D-09 | Second append no-op; midpoints invariant. Geometry Track E may deepen. |
| **PF-11** | **CONFIRMED** (engine half) | D-03 | Mirrored buffer + original `isFrontCamera` → visibility/position L/R ≠ angle stream. |
| **PF-12** | **CONFIRMED** API cost; **×3/frame not in engine** | D-10 | Engine builds snapshot only when called; VM multiplicity → Track F. |
| **PF-14** | **CONFIRMED** | D-05 | `executionStartMs` write-only; `lastSmoothedAngles` unused. |
| **PF-19** | **CONFIRMED** stack / **NEEDS-DATA** latency | D-07 | MA(3) + hysteresis(3°) + upstream One-Euro; measure on device. |

---

## Coverage

### Read (Track D brief list)

| File | Status |
|---|---|
| `session/MovitTrainingEngine.kt` (`processPoseFrame` + metrics/vis helpers) | Read |
| `engine/pipeline/FramePipelineExecutor.kt` | Read |
| `engine/pipeline/FrameEvaluationPipeline.kt` | Read |
| `engine/JointAngleTracker.kt` | Read |
| `engine/AngleSmoother.kt` | Read |
| `engine/evaluation/JointEvaluator.kt` | Read |
| `engine/evaluation/JointEval.kt` | Read |
| `engine/PhaseStateMachine.kt` | Read |
| `engine/StartPoseGate.kt` | Read |
| `engine/RepCounter.kt` | Read |
| `session/RepCompletionCoordinator.kt` | Read |
| `engine/RepCompletionSignal.kt` | Read |
| `session/SessionOrchestrator.kt` | Read |
| `session/ExecutionClock.kt` | Read |
| `position/PositionValidator.kt` | Read |
| `visibility/VisibilityMonitor.kt` + `VisibilityJointRules.kt` | Read |
| `engine/feedback/FrameFeedbackEmitter.kt`, `JointErrorCollection.kt`, `FeedbackRouter.kt` (skim) | Read |
| `bilateral/BilateralController.kt` | Read |
| `model/PoseFrame.kt` | Read |
| Supporting: `PoseLandmarkMirroring.kt`, `JointLandmarkMapping.kt`, `VirtualLandmarks.kt`, `PoseFrameAssembler.kt`, `CameraPositionDetector` (front flip), `SetupReadinessGate.trackedSetupAngles`, `MediaPipePoseDetector` mirror comment, `TimingPolicy` | Read |

### Not fully line-audited

- `TrainingFeedbackEventRouter.kt` (feedback routing beyond per-frame emitter)
- Full `PoseSceneDetector` internals beyond `detect(..., isFrontCamera)` call
- Entire `HoldExerciseCoordinator` body (hold branch only spot-checked at call site)

### Answers checklist

- [x] D1 allocation table
- [x] D2 dual visibility
- [x] D3 skip / position order
- [x] D4 left_elbow front-camera mirror trace
- [x] D5 phase/rep/bilateral
- [x] D6 dead vars
- [x] D7 clocks
- [x] D8 snapshot usage
- [x] PF-09, PF-10, PF-11, PF-12 (engine), PF-14, PF-19
