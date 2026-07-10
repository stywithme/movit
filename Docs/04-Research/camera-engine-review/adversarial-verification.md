# Adversarial Verification — Camera Engine Review (P0/P1)

**Verifier**: adversarial-grok-4.5-xhigh  
**Method**: Re-read production source; attempt to refute each finding; keep CONFIRMED only with a concrete trigger.  
**Date**: 2026-07-10

---

## Critical conflict: PF-11 / D-03 vs E-04

### Final PF-11 verdict: **REFUTED** (no active left_elbow angle↔visibility desync)

Track D’s D-03 assumed `PoseFrame.mirrored()` actually swaps landmark buffers, then `isFrontCamera=true` remaps visibility to the opposite limb. Track E’s E-04 claimed `mirrorLandmarks` is a bidirectional no-op. **E-04 wins.**

### `left_elbow` front-camera trace (re-derived from source)

1. **Capture** leaves MediaPipe-indexed landmarks unmirrored (`MediaPipePoseDetector.kt:207-211`).
2. **`assemble`** computes `leftElbow` from indices 11/13/15 (anatomical left) (`PoseFrameAssembler.kt:52`).
3. **`frame.mirrored()`** (`PoseFrame.kt:22-31`):
   - Calls `PoseLandmarkMirroring.mirrorLandmarks` (`PoseLandmarkMirroring.kt:19-29`).
   - `swapMap` contains both `13→14` and `14→13`; the loop swaps then un-swaps → **landmarks unchanged**. Unit test asserts equality (`PoseFrameAssemblerTest.kt:149`).
   - `mirrorAngles` **does** swap: `workingFrame.angles.leftElbow` = anatomical **right**.
   - Sets `workingFrame.isFrontCamera = false`.
4. **`extractTrackedAngles`** reads `workingFrame.angles` → `"left_elbow"` = anatomical right (`JointAngleTracker.kt:113-118`, `MovitTrainingEngine.kt:612-620`).
5. **`buildJointVisibilities` / `computeJointVisibility("left_elbow", …, isFrontCamera=true)`** (`MovitTrainingEngine.kt:626`, `JointLandmarkMapping.kt:59-60,92-94`):
   - Raw indices `[11,13,15]` → `mirroredIndex` → `[12,14,16]` = anatomical **right** on the **unswapped** buffer.
6. **`PositionValidator`** with `isFrontCamera=true` (`PositionValidator.kt:146-147,630-633`):
   - `mirrorCheckLandmarks` renames `left_elbow` → `right_elbow` → `jointToLandmark` index **14** = anatomical **right**.

**Result**: angle stream, visibility, and position checks for config name `left_elbow` all resolve to anatomical **right** under front camera. No desync. D-03’s “visibility remaps to anatomical left” is false under the current no-op buffer swap.

**Latent hazard (not active PF-11)**: If `mirrorLandmarks` were “fixed” to a one-way swap while the engine still passes original `frame.isFrontCamera=true`, D-03 would become real. Document/fix as a unit, or pass `isFrontCamera=false` after a true buffer mirror.

---

## P0 candidates

### Verify [E-01]
- **Original Status/Severity**: CONFIRMED / P0 (track-E)
- **Adversarial attempt**: Argue elbows still get 3D via `ElbowAngleEstimator` when `world.size >= 33`, so “all world 3D angles dropped” is overstated; also argue production may intentionally run 2D.
- **Final Status**: CONFIRMED
- **Final Severity**: P0
- **Evidence**: `PoseFrameAssembler.kt:24-25` appends virtual landmarks (33→35 via `VirtualLandmarks.kt:15-38`); `calculateAngles` gate `worldLandmarks?.takeIf { it.size >= landmarks.size }` at `:50` fails for MediaPipe world length 33 vs resolved 35 → `world=null` → every limb uses 2D `angleAt`. 3D unit test calls `calculateAngles` directly with equal-sized lists (`PoseFrameAssemblerTest.kt:18-37`), never `assemble`+`ensureAppended`. Elbow estimator may still rewrite elbows (`:26-27`) but hips/knees/shoulders/ankles stay 2D-only on the hot path.
- **Verified-by**: adversarial-grok-4.5-xhigh
- **Notes**: Elbow 3D correction does not refute the finding; the `calculateAngles` 3D branch is dead for production `assemble`. Trigger: any live Android/iOS frame with 33 world + 33 norm landmarks.

### Verify [J-02]
- **Original Status/Severity**: CONFIRMED / P0 (track-J)
- **Adversarial attempt**: Argue `TrainingPoseVariantResolver` always clamps so `error()` is unreachable.
- **Final Status**: CONFIRMED
- **Final Severity**: P0
- **Evidence**: `MovitTrainingEngine.kt:131-133` — `getPoseVariant(poseVariantIndex) ?: error(...)`. `buildEngine()` constructs without try/catch (`TrainingSessionViewModel.kt:1644-1652`). Resolver clamps using `variantCount` (`TrainingPoseVariantResolver.kt:16-18`) but J-01 feeds the **previous** exercise’s count, so index can exceed the new exercise’s `poseVariants`. Empty `poseVariants` → resolver returns 0 → engine still `error`s. Trigger: flow exercise A with ≥3 variants → exercise B with 2 variants and flow `poseVariantIndex=2`.
- **Verified-by**: adversarial-grok-4.5-xhigh
- **Notes**: Paired with J-01; uncaught `IllegalStateException` on session start / inter-exercise reload.

---

## P1 candidates

### Verify [A-01]
- **Original Status/Severity**: CONFIRMED / P1 (track-A)
- **Adversarial attempt**: Production default is STABLE 320×240@10fps (`TrainingThroughputProfile.kt:21-25`) ≈ ~3MB/s ARGB churn — argue P1 overstates vs crash/correctness bugs; no measured jank in review.
- **Final Status**: CONFIRMED (mechanism) / **DOWNGRADED** (severity)
- **Final Severity**: P2
- **Evidence**: `MediaPipePoseDetector.kt:168-175,213-220` — every `detectAsync` does `imageProxy.toBitmap()`; non-zero rotation allocates `Bitmap.createBitmap` with no pool/reuse.
- **Verified-by**: adversarial-grok-4.5-xhigh
- **Notes**: Allocation without reuse is real and blocks higher throughput profiles; at STABLE defaults it is a perf debt, not a P1 user-facing defect. Keep as P2 unless device traces show GC stalls.

### Verify [A-05]
- **Original Status/Severity**: CONFIRMED / P1 (track-A)
- **Adversarial attempt**: Argue `bitmapLock` around reference read is enough; Bitmap.copy might be safe if recycle is synchronized.
- **Final Status**: CONFIRMED
- **Final Severity**: P1
- **Evidence**: `takeSnapshotJpeg` reads `lastFrameBitmap` under lock then `source.copy(...)` **outside** lock (`MediaPipePoseDetector.kt:233-235`). Concurrent `detectAsync` can `recycle()` the same bitmap under lock (`:179-181`) before/during copy. Trigger: TRAINING with replay/peak snapshots while analysis continues.
- **Verified-by**: adversarial-grok-4.5-xhigh
- **Notes**: Classic TOCTOU; copy must occur under the same lock (or refcount).

### Verify [A-07]
- **Original Status/Severity**: CONFIRMED / P1 (track-A)
- **Adversarial attempt**: Argue lens switch cannot overlap an in-progress bind in practice.
- **Final Status**: CONFIRMED
- **Final Severity**: P1
- **Evidence**: `switchCamera` always `prepareForLensSwitch` (sets gate + `switchingCamera`) then `bindUseCases` (`CameraXFrameSource.kt:129-136,138-142`). `bindUseCases` returns immediately if `bindingInProgress` CAS fails (`:237-241`) with **no pending facing queue**. Prior bind can finish on the old lens while the gate awaits the new facing → suppressed frames. Trigger: flip camera during initial bind or rapid double-flip.
- **Verified-by**: adversarial-grok-4.5-xhigh
- **Notes**: Silent drop + gate await is a concrete stall path.

### Verify [B-01]
- **Original Status/Severity**: CONFIRMED / P1 (track-B)
- **Adversarial attempt**: Argue FPS throttle alone is enough parity; overlay FILL_CENTER may still align at native size.
- **Final Status**: CONFIRMED
- **Final Severity**: P1
- **Evidence**: iOS hardcodes `AVCaptureSessionPresetHigh` (`IosCameraFrameSource.kt:127-128`); only uses `targetFps` for interval throttle (`:144-155`). Android applies `configuration.analysisWidth/Height` to ImageAnalysis (`CameraXFrameSource.kt:290-301`). STABLE expects 320×240 (`TrainingThroughputProfile.kt:21-25`).
- **Verified-by**: adversarial-grok-4.5-xhigh
- **Notes**: Resolution ignore is unambiguous code divergence; overlay letterbox parity risk remains plausible when analysis dims ≠ Android’s.

### Verify [B-02]
- **Original Status/Severity**: NEEDS-DATA / P1 (track-B)
- **Adversarial attempt**: Confirm code divergence; try to prove runtime L/R break from source alone.
- **Final Status**: NEEDS-DATA
- **Final Severity**: P1 (provisional until device proof)
- **Evidence**: iOS `setVideoMirrored(true)` on front connection (`IosCameraFrameSource.kt:168-171`). Android explicitly does **not** mirror before MediaPipe (`MediaPipePoseDetector.kt:207-211`); draw-time mirror only. Engine still runs `frame.mirrored()` for front (`MovitTrainingEngine.kt:610`).
- **Verified-by**: adversarial-grok-4.5-xhigh
- **Notes**: Divergence is CONFIRMED in code; whether MediaPipe L/R + engine mirrorAngles double-corrects on iOS needs a side-by-side landmark dump. Do not ship a “fix” without that data.

### Verify [C-01]
- **Original Status/Severity**: CONFIRMED / P1 (track-C)
- **Adversarial attempt**: Argue countdown finish only emits StartEngine and PoseFrame under COUNTDOWN only emits ValidatePose — no dual processFrame.
- **Final Status**: CONFIRMED
- **Final Severity**: P1
- **Evidence**: Worker snapshots `runState` then branches (`TrainingSessionViewModel.kt:458-478`). If snapshot is COUNTDOWN but `CountdownFinished` already set TRAINING (`SessionSupervisor.kt:259-267`), the same frame still `processSignal(PoseFrame)` → `handleTraining` emits `ProcessFrame` (`:334-343`) handled on Main (`TrainingSessionViewModel.kt:982-994`) while subsequent frames call `engine?.processFrame` on `Dispatchers.Default` (`:463`). Gate is non-atomic (C-02). Trigger: countdown completion while pose frames continue.
- **Verified-by**: adversarial-grok-4.5-xhigh
- **Notes**: Refutation fails: ValidatePose is only for COUNTDOWN *state*; after transition, PoseFrame hits TRAINING handler.

### Verify [C-02]
- **Original Status/Severity**: CONFIRMED / P1 (track-C)
- **Adversarial attempt**: Argue single-threaded call sites make atomicity unnecessary.
- **Final Status**: CONFIRMED
- **Final Severity**: P1
- **Evidence**: `FrameIngressGate.kt:9-25` — plain `var processing` check-then-set; comment claims single processFrame. C-01 provides two threads. No memory barrier / AtomicBoolean.
- **Verified-by**: adversarial-grok-4.5-xhigh
- **Notes**: Severity tied to C-01; alone it is latent until dual callers exist (they do at countdown→training).

### Verify [C-10]
- **Original Status/Severity**: CONFIRMED / P1 (track-C)
- **Adversarial attempt**: Argue `MutableStateFlow` makes supervisor safe.
- **Final Status**: CONFIRMED
- **Final Severity**: P1
- **Evidence**: `processSignal` unsynchronized (`SessionSupervisor.kt:94+`); mutable fields `noPoseStartTime`, `countdownFrozen`, etc. (`:69-78`). Called from Default worker (`TrainingSessionViewModel.kt:451,471-478`) and Main (countdown `onFinish` `:736-737`, UI pause/resume). StateFlow assignment is atomic per write; compound read-modify of other fields is not.
- **Verified-by**: adversarial-grok-4.5-xhigh
- **Notes**: Same boundary as C-01; lost/double transitions are plausible.

### Verify [D-03]
- **Original Status/Severity**: CONFIRMED / P1 (track-D) — **conflicts with E-04 REFUTED**
- **Adversarial attempt**: Re-trace left_elbow; prove landmarks are not actually swapped.
- **Final Status**: **REFUTED**
- **Final Severity**: n/a (was P1; latent P2 architecture if mirrorLandmarks “fixed” incorrectly)
- **Evidence**: See PF-11 section. `PoseLandmarkMirroring.kt:8-29`, `PoseFrame.kt:22-31`, `PoseFrameAssemblerTest.kt:134-152`, `JointLandmarkMapping.kt:92-94`, `PositionValidator.kt:146-147,630-633`, `MovitTrainingEngine.kt:610-664`.
- **Verified-by**: adversarial-grok-4.5-xhigh
- **Notes**: Track D’s mechanism description matches a **hypothetical** one-way landmark swap; current code does not swap buffers. Align with E-04.

### Verify [F-02]
- **Original Status/Severity**: CONFIRMED / P1 (track-F)
- **Adversarial attempt**: Argue Compose skips unchanged subtrees / smart recomposition limits impact.
- **Final Status**: CONFIRMED
- **Final Severity**: P1
- **Evidence**: Single `collectAsStateWithLifecycle()` of full UI state (`MovitTrainingRoutes.kt:112`) passed into `TrainingSessionScreen` (`:167-168`, `TrainingSessionScreen.kt:61-62`). Landmarks/joint state change every pose frame → full-screen parameter invalidation. Exact Layout Inspector counts NEEDS-DATA; structural issue stands.
- **Verified-by**: adversarial-grok-4.5-xhigh
- **Notes**: Cannot refute the monolithic subscription; measured fps loss still NEEDS-DATA but P1 for architecture/perf risk is fair.

### Verify [F-03]
- **Original Status/Severity**: CONFIRMED / **P2** (track-F) — listed as P1 candidate in verify set
- **Adversarial attempt**: Argue `remember` makes this cheap / not P1.
- **Final Status**: CONFIRMED
- **Final Severity**: **P2** (not P1)
- **Evidence**: `TrainingSessionScreen.kt:86-101` keys `remember` on `state.landmarks` (new list each frame) → `buildSkeletonRomIndicators` (`TrainingRomIndicatorMapper.kt:28-77`) every frame on main.
- **Verified-by**: adversarial-grok-4.5-xhigh
- **Notes**: Real cost but secondary to F-02; keep track’s P2.

### Verify [G-01]
- **Original Status/Severity**: CONFIRMED / P1 (track-G)
- **Adversarial attempt**: Argue `finalizeUpload` is synchronous before UI shows Finish, so navigation is safe.
- **Final Status**: CONFIRMED
- **Final Severity**: P1
- **Evidence**: `COMPLETED` sets `isComplete` via state collector (`TrainingSessionViewModel.kt:718-722`) when `ShowCompleted` is only `tryEmit`’d (`SessionSupervisor.kt:357-360,560-563`) — Finish can appear before/without finalize completing. Finish UI (`TrainingSessionScreen.kt:644-663`) → `FinishClicked` (`:222-224`) navigates; `DisposableEffect` dispose → `StopSession` → `tearDownForExit` → `writeHooks.detach()` (`MovitTrainingRoutes.kt:152-153`, `TrainingSessionViewModel.kt:197-206,357-368`). `finalizeUpload` needs `motionSession` (`TrainingSessionWriteHooks.kt:161-162`); `onCleared` also `detach()` (`:1834-1839`) and cancels `viewModelScope` work inside `finalizeCurrentExercise` (`:1068-1076`). Trigger: tap Finish quickly after target-reached.
- **Verified-by**: adversarial-grok-4.5-xhigh
- **Notes**: Sync snapshot can win the race, but enqueue/report path is still cancellable; detach-before-finalize nulls upload.

### Verify [G-02]
- **Original Status/Severity**: CONFIRMED / P1 (track-G)
- **Adversarial attempt**: Argue preference changes only affect feedback, not engine identity; or rebuild is rare.
- **Final Status**: CONFIRMED
- **Final Severity**: P1
- **Evidence**: `rebuildEngineIfNeeded` skips only `isTrainingActive()` == TRAINING (`TrainingSessionViewModel.kt:1655-1659`, `SessionRunState.kt:46`). Prefs `onEach` calls it (`:684-693`). New engine starts with `isRunning=false`; `resume()` clears pause but **does not** set `isRunning=true` (`MovitTrainingEngine.kt:541-545,479-481`). After PAUSED rebuild, `ResumeEngine` → `resume()` → `processFrame` early-returns on `!isRunning` (`:578`). No writeHooks re-attach.
- **Verified-by**: adversarial-grok-4.5-xhigh
- **Notes**: Concrete trigger: pause → change coach intensity in settings → resume → silent no counting.

### Verify [J-01]
- **Original Status/Severity**: CONFIRMED / P1 (track-J)
- **Adversarial attempt**: Argue flow items always carry valid indices for the target exercise.
- **Final Status**: CONFIRMED
- **Final Severity**: P1
- **Evidence**: `applyFlowExercise` calls `resolveActivePoseVariantIndex(exercise)` **before** `exerciseConfig = configRepository.getBySlug(...)` (`TrainingSessionViewModel.kt:1365-1371`). Resolver uses `exerciseConfig?.poseVariants?.size ?: 1` (`:1741-1748`) — prior exercise’s count. Trigger: multi-exercise flow with decreasing variant counts (feeds J-02).
- **Verified-by**: adversarial-grok-4.5-xhigh
- **Notes**: Ordering bug is unambiguous.

### Verify [J-03]
- **Original Status/Severity**: CONFIRMED / P1 (track-J)
- **Adversarial attempt**: Argue validation is only for tests / parser already sanitizes; severity should be P2 defense-in-depth.
- **Final Status**: CONFIRMED
- **Final Severity**: P1
- **Evidence**: `validationIssues()` defined (`ExerciseConfigModels.kt:128-142`); production grep shows use only in `ExerciseConfigParserTest`. `buildEngine()` passes config straight through (`TrainingSessionViewModel.kt:1636-1652`). Would catch empty variants / missing PRIMARY before J-02 `error()`.
- **Verified-by**: adversarial-grok-4.5-xhigh
- **Notes**: Kept P1 because it is the practical guardrail for J-02 and bad synced configs.

### Verify [E-05]
- **Original Status/Severity**: CONFIRMED / P1 (track-E, PF-18)
- **Adversarial attempt**: Argue single training session owns the assembler so reset/assemble never overlap; debug module is release-gated.
- **Final Status**: CONFIRMED
- **Final Severity**: P1
- **Evidence**: `PoseFrameAssembler` is a process-wide `object` with mutable `elbowEstimator` (`PoseFrameAssembler.kt:11-12,26-27,40`). `assemble` is called from MediaPipe/iOS capture paths (`CameraXFrameSource.kt:177`, `MediaPipePoseDetector.kt:269`, `IosCameraFrameSource.kt:106`). `resetElbowEstimator()` is called from lens-switch on Android (`CameraXFrameSource.kt:142`) and from `training-debug` (`AndroidDebugCameraPoseSource.kt:85`, `TrainingDebugAnalyzer.kt:27`) which can run concurrently with training. No lock around estimator state. Trigger: lens switch during live frames, or open training-debug while a session is warm.
- **Verified-by**: adversarial-grok-4.5-xhigh
- **Notes**: Cross-consumer bleed is the stronger half; pure reset-vs-assemble race on one session is narrower but still unsynchronized.

### Verify [F-01]
- **Original Status/Severity**: CONFIRMED / P1 (track-F, PF-13)
- **Adversarial attempt**: Argue Compose batches updates in one frame so 2–3 `_state.update` collapse; severity should be P2.
- **Final Status**: CONFIRMED
- **Final Severity**: P1
- **Evidence**: TRAINING worker path issues landmarks update + elapsed + overlay refresh as separate `_state.update` calls on a **62-field** data class (`TrainingSessionViewModel` pose worker / `applyPoseLandmarksToUi` / `updateSessionElapsed` / `refreshSkeletonOverlay`). Even if Compose coalesces some invalidations, each update allocates a full UiState copy on the hot path. Paired with F-02 monolithic collect.
- **Verified-by**: adversarial-grok-4.5-xhigh
- **Notes**: Severity stays P1 as part of the PF-13 hot-path pressure story; exact recomposition counts remain NEEDS-DATA.

---

## Summary table

| ID | Original | Final Status | Final Severity |
|----|----------|--------------|----------------|
| E-01 | CONFIRMED P0 | **CONFIRMED** | P0 |
| J-02 | CONFIRMED P0 | **CONFIRMED** | P0 |
| A-01 | CONFIRMED P1 | **DOWNGRADED** | P2 |
| A-05 | CONFIRMED P1 | **CONFIRMED** | P1 |
| A-07 | CONFIRMED P1 | **CONFIRMED** | P1 |
| B-01 | CONFIRMED P1 | **CONFIRMED** | P1 |
| B-02 | NEEDS-DATA P1 | **NEEDS-DATA** | P1 (provisional) |
| C-01 | CONFIRMED P1 | **CONFIRMED** | P1 |
| C-02 | CONFIRMED P1 | **CONFIRMED** | P1 |
| C-10 | CONFIRMED P1 | **CONFIRMED** | P1 |
| D-03 | CONFIRMED P1 | **REFUTED** | — |
| F-02 | CONFIRMED P1 | **CONFIRMED** | P1 |
| F-03 | CONFIRMED P2 | **CONFIRMED** | P2 (not P1) |
| G-01 | CONFIRMED P1 | **CONFIRMED** | P1 |
| G-02 | CONFIRMED P1 | **CONFIRMED** | P1 |
| J-01 | CONFIRMED P1 | **CONFIRMED** | P1 |
| J-03 | CONFIRMED P1 | **CONFIRMED** | P1 |
| E-05 | CONFIRMED P1 | **CONFIRMED** | P1 |
| F-01 | CONFIRMED P1 | **CONFIRMED** | P1 |
| E-04 / PF-11 | REFUTED (track-E) | **REFUTED** (agreed) | — |

### PF-11 final verdict

**REFUTED** as an active front-camera `left_elbow` angle vs visibility/position desync. `mirrorLandmarks` is a no-op; angle swap + `isFrontCamera` index/name remap stay on the same anatomical side. Prefer E-04 over D-03. Treat one-way landmark-swap “fixes” without clearing the `isFrontCamera` pass-through as a latent regression risk.
