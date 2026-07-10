# Track E — Geometry & Math Correctness

> **Scope**: `kmp-app/core/training-engine` (+ pose-capture) geometry/filter: `JointAngleCalculator`, `PoseFrameAssembler`, `ElbowAngleEstimator`, `VirtualLandmarks`, `PoseLandmarkMirroring`, `JointLandmarkMapping`, `DisplayLandmarkTransform`, `OneEuroFilter`, overlay projector; PF-10/11/18/19 (math half).
>
> **Review mode**: READ-ONLY. Report only.
>
> **Date**: 2026-07-10
>
> **Parity tests consulted**: `PoseFrameAssemblerTest`, `PoseFrameAssemblerDebugParityTest`, `JointAngleCalculatorParityTest`, `ElbowAngleEstimatorTest`, `DisplayLandmarkTransformTest`, `OneEuroFilterParityTest`, `OneEuroFilterTest`, `SkeletonOverlayMapperTest`.

---

## Executive Summary

Production `PoseFrameAssembler.assemble` **silently discards world (3D) landmarks** after appending virtual points 33/34, because `calculateAngles` requires `world.size >= landmarks.size` (33 ≱ 35). Limb angles on the hot path are therefore **2D-only**, despite unit tests that exercise 3D via `calculateAngles` directly. `PoseLandmarkMirroring.mirrorLandmarks` is a **no-op** (bidirectional `swapMap` double-swaps); front-camera correctness relies on **angle swap + `isFrontCamera` index remap**. Virtual landmarks 33/34 are safe under current mirroring. Shared `PoseFrameAssembler` elbow state is a real concurrency/session-contamination hazard (PF-18). Visibility thresholds are **not** uniformly 0.5 (`TimingPolicy.visibilityMinVisibility = 0.3`). Triple smoothing latency (PF-19) is **unmeasured**.

---

## Answers: E1–E7

### E1 — 3D→2D fallback oscillation

**Answer**: **Two issues.** (1) **Production assemble never selects 3D** for limb angles (see [E-01]). (2) If the size gate were fixed, per-joint `angleAt3D` still switches 3D↔2D whenever any of the three world landmarks crosses `visibilityThreshold` (default 0.5) with **no hysteresis** (`PoseFrameAssembler.kt:73-83`). `AngleSmoother` (MA window=3) only averages scalars — it does **not** lock the 3D/2D mode — so a large 3D/2D jump (parity test expects >1° difference for bent knee) still enters the phase machine; `phaseHysteresisDegrees=3.0` may not absorb it.

---

### E2 — `PoseFrameAssembler` singleton / elbow race (PF-18)

**Answer**: **CONFIRMED.** `object PoseFrameAssembler` holds a mutable `ElbowAngleEstimator` (`PoseFrameAssembler.kt:11-12`). `assemble` mutates it on the MediaPipe callback thread; `resetElbowEstimator()` is called from lens-switch prep on the camera/UI path (`CameraXFrameSource.kt:138-142`) and from training-debug (`TrainingDebugAnalyzer.kt:27`, `AndroidDebugCameraPoseSource.kt:85`) with **no synchronization**. Concurrent reset mid-`correct` can tear `smUaDz` / `smoothOut` / `lastStable*`. Debug + training sharing the same process contaminates elbow temporal state. iOS has **no** `resetElbowEstimator` call on lens switch.

---

### E3 — Mirror of virtual landmarks 33/34 (PF-10)

**Answer**: **33/34 are not corrupted by current mirroring.**

| Check | Result |
|-------|--------|
| In `swapMap`? | No — `mirroredIndex(33\|34) = identity` (`PoseLandmarkMirroring.kt:17`) |
| x-flip in `mirrorLandmarks`? | Never — index swap only (`:19-29`) |
| Midpoint geometry | Neck/spine = mid(L,R); L/R swap (if it worked) preserves midpoint |
| `ensureAppended` ×2 / frame | Second call no-op when `size >= 35` (`VirtualLandmarks.kt:15-16`; engine `:819`) — cheap |
| Actual landmark swap | **No-op** due to bidirectional map iteration (see [E-03]) |

Parity lock: `poseFrameMirrored_appliesAngleSwap_only` asserts `frame.landmarks == mirrored.landmarks` (`PoseFrameAssemblerTest.kt:134-152`).

---

### E4 — Elbow correction algorithm / reset policy

**Answer**: Algorithm is stateful and time-based (`HOLD_TIMEOUT_MS=500`, `OUTPUT_SMOOTH=0.25`, dz EMA). Requires `worldLandmarks.size >= 33` and per-side visibility 0.5 (`ElbowAngleEstimator.kt:46,93`). **Reset only on Android lens switch / debug start** — **not** on `MovitTrainingEngine.start()` (`:479-503` resets smoother/PSM/visibility but not elbow). Cross-exercise temporal bleed is possible. Covered by `ElbowAngleEstimatorTest` (strategies + hold + reset).

---

### E5 — Visibility threshold inventory

**Answer**: **Not consistent at 0.5.** See table below. Most geometry uses 0.5; **pause/visibility monitor defaults to 0.3** via `TimingPolicy`.

---

### E6 — One-Euro vs paper / parity edges

**Answer**: Implementation matches common One-Euro form (adaptive cutoff from filtered derivative; α from cutoff+dt). First sample pass-through; default dt=`1/30` when `lastTime==0`; dt coerced to `[0.001, 0.1]` — equal or backward timestamps become **1 ms**, not a reset (`OneEuroFilter.kt:19-24`). Lens switch resets smoother banks (Android `LandmarkSmoother.reset` / iOS `PoseLandmarkSmoother.reset`) but **parity tests do not cover** equal/backward timestamps or post-reset dt. `OneEuroFilterParityTest` only checks sequential ~33 ms samples and 3D axis independence.

---

### E7 — Display transform / overlay projector

**Answer**: Math is coherent for same-aspect FILL_CENTER. Preview uses `RATIO_4_3` + `PreviewView.FILL_CENTER`; analysis profiles are 4:3 (320×240 / 480×360 / 640×480). Overlay uses `DisplayLandmarkTransform.fromLayout` + `skeletonMirrorPreview = frame.isFrontCamera` (raw frame, unmirrored landmarks) — matches comment that ML stays unmirrored and x-flip is draw-time only. Projector returns `null` if analysis dims are 0. **Residual risk**: if CameraX analysis fallback yields a non-4:3 buffer while preview stays 4:3, overlay drifts — **NEEDS-DEVICE**.

---

## E5 — Visibility-threshold inventory

| Location | Threshold | Role |
|----------|-----------|------|
| `Landmark.isVisible()` / `isPresent()` default | **0.5** | Default landmark gate |
| `PoseFrameAssembler.assemble` / `calculateAngles` | **0.5** | 3D/2D angle eligibility |
| `ElbowAngleEstimator` joint visibility | **0.5** | Per-side correction gate |
| `ElbowAngleEstimator.computeFacingRatio` | **0.3** | Shoulder facing ratio fallback |
| `PositionValidator` | **0.5** | Position-check landmark visibility |
| `StabilityPolicy.anySideVisibilityThreshold` | **0.5** | ANY_SIDE joint drop |
| `StabilityPolicy.anySideStrongMinVisibility` | **0.7** | ANY_SIDE strong side |
| `VisibilityMonitor` class default | **0.5** | Constructor default |
| `TimingPolicy.visibilityMinVisibility` → engine monitor | **0.3** | **Production pause/visibility** |
| `BodyPostureDetector.SITTING_MIN_VISIBILITY` | **0.3** | Posture sitting heuristic |
| `PoseDetectorConfiguration` min detection/tracking | **0.5** | MediaPipe conf |
| MediaPipe Android `MIN_DETECTION/TRACKING/PRESENCE` | **0.5** | Detector constants |
| UI `lm.isVisible()` in `TrainingSessionViewModel` | **0.5** (default) | Skeleton point visibility |

**Unintended skew**: joints can be “visible enough” for angle math (0.5) yet the session pause path treats them as invisible sooner (0.3), or the reverse depending on which map is consulted — monitor uses **pre-aggregated joint visibility floats** compared to 0.3, while angle assembly uses per-landmark 0.5.

---

## PF judgments (Track E)

| PF | Judgment | Finding |
|----|----------|---------|
| **PF-10** | **CONFIRMED** (cheap double `ensureAppended`); **REFUTED** (33/34 mirror corruption under current code) | [E-02], [E-03] |
| **PF-11** (math half) | **REFUTED** as active double-mirror bug | [E-04] — landmarks not actually swapped; flag remap is the real L/R path |
| **PF-18** | **CONFIRMED** | [E-05] |
| **PF-19** (math half) | **NEEDS-DATA** | [E-06] — stack exists; latency unmeasured |

---

## Findings

### [E-01] `assemble` drops all world 3D angles after virtual landmark append
- **Severity**: P0
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/geometry/PoseFrameAssembler.kt:24-25`, `:50`, `VirtualLandmarks.kt:15-38`, `CameraXFrameSource.kt:177-184`, `IosCameraFrameSource.kt:106-112`
- **Evidence**: `assemble` does `resolvedLandmarks = ensureAppended(landmarks)` (33→35) then `calculateAngles(resolvedLandmarks, …, worldLandmarks)`. Gate is `worldLandmarks?.takeIf { it.size >= landmarks.size }` — MediaPipe world lists are length 33, so `33 >= 35` fails and `world` becomes `null`; every limb uses 2D `angleAt`. Unit tests that prove 3D (`PoseFrameAssemblerTest.limbAngles_use3DWorldWhenAvailable`) call `calculateAngles` with equal-sized lists and **never** go through `assemble`+`ensureAppended`. `PoseFrameAssemblerDebugParityTest` compares two `assemble` calls (both 2D) — false parity confidence. Elbows may still be rewritten by `ElbowAngleEstimator` (separate 3D path); hips/knees/shoulders/ankles stay 2D-only on the hot path.
- **Impact**: Systematic loss of intended 3D joint geometry for non-elbow limbs on Android and iOS; phase/rep decisions can diverge from legacy 3D behavior (parity fixtures already show 2D≠3D for bent knee).
- **Fix-sketch**: Change gate to `world.size >= 33` (or max limb index+1); or `ensureAppended(world)` too; add assemble-path test with 33-world + 33-norm asserting 3D knee.
- **Effort**: S
- **Verified-by**: pending

### [E-02] `ensureAppended` second call per frame is a cheap no-op (PF-10 part 1)
- **Severity**: P3
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: PF-10
- **Files**: `PoseFrameAssembler.kt:24`, `MovitTrainingEngine.kt:819`, `VirtualLandmarks.kt:15-16`
- **Evidence**: First append in `assemble` yields size 35. `buildJointVisibilities` calls `ensureAppended` again; early return when `size >= TOTAL_WITH_VIRTUAL` — no allocation.
- **Impact**: Negligible per-frame cost; not a hot-path concern.
- **Fix-sketch**: Optional: skip second call or pass a flag that landmarks are already resolved.
- **Effort**: S
- **Verified-by**: pending

### [E-03] Virtual landmarks 33/34 survive mirroring; `mirrorLandmarks` is a no-op (PF-10 part 2)
- **Severity**: P2
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: PF-10
- **Files**: `PoseLandmarkMirroring.kt:8-29`, `PoseFrame.kt:22-31`, `PoseFrameAssemblerTest.kt:134-152`, `VirtualLandmarks.kt:11-13`
- **Evidence**: `swapMap` lists both `11→12` and `12→11`. Loop swaps then un-swaps every pair → landmarks unchanged. Test name/asserts document angle-swap-only. Indices 33/34 absent from map → identity. Midpoints need no x-flip for index-swap semantics; display x-flip is separate (`DisplayLandmarkTransform.kt:37-38`).
- **Impact**: 33/34 not corrupted today. Latent hazard: “fixing” `mirrorLandmarks` to one-way swap without clearing `isFrontCamera` on consumers would double-remap visibility/position (see [E-04]).
- **Fix-sketch**: Iterate unique undirected pairs once; add test that asymmetric L/R positions actually swap; document contract for 33/34 (identity + optional x-flip only at draw).
- **Effort**: M
- **Verified-by**: pending

### [E-04] PF-11 double-mirror: REFUTED under current landmark no-op; undocumented contract
- **Severity**: P2
- **Type**: Architecture
- **Status**: REFUTED
- **Related-PF**: PF-11
- **Files**: `MovitTrainingEngine.kt:610-664`, `PoseFrame.kt:22-31`, `JointLandmarkMapping.kt:92-94`, `SetupReadinessGate.kt:191-202`, `PositionValidator.kt:142-147`
- **Evidence**: Engine does `workingFrame = frame.mirrored()` (angles swapped, landmarks unchanged, copy sets `isFrontCamera=false`) then passes **original** `frame.isFrontCamera` into `extractTrackedAngles`, `buildJointVisibilities`, and `FramePipelineExecutor`. `computeJointVisibility` applies `mirroredIndex` when flag is true — **single** remap on unswapped landmarks. `mirrorAngles` runs once. PositionValidator XOR comment assumes name-level front-camera mirror on unmirrored landmark buffers. Active double-mirror of landmark **buffers** does not occur.
- **Impact**: No proven wrong L/R today; contract is implicit and fragile. Coverage gap: no engine-level front-camera visibility test with asymmetric landmark visibility.
- **Fix-sketch**: Document “angles mirrored; landmarks MediaPipe-indexed; lookups use `isFrontCamera`”; or make `mirrored()` a true one-way landmark swap and pass `isFrontCamera=false` everywhere after.
- **Effort**: M
- **Verified-by**: pending

### [E-05] Shared `PoseFrameAssembler` elbow estimator race / cross-consumer bleed (PF-18)
- **Severity**: P1
- **Type**: Concurrency
- **Status**: CONFIRMED
- **Related-PF**: PF-18
- **Files**: `PoseFrameAssembler.kt:11-12,26-27,40`, `CameraXFrameSource.kt:138-142`, `TrainingDebugAnalyzer.kt:27`, `ElbowAngleEstimator.kt:31-37,59-67`
- **Evidence**: Singleton mutable estimator; `correct` updates arrays without locks; `reset` from lens-switch/debug while `assemble` may run on MediaPipe thread. Training-debug and training both call `PoseFrameAssembler.assemble`. `MovitTrainingEngine.start` does not reset elbow state. iOS never calls `resetElbowEstimator` on lens change.
- **Impact**: Realistic lens-switch race; debug+training contamination; stale HOLD/smooth state across exercises → wrong elbow angles for up to hundreds of ms.
- **Fix-sketch**: Per-detector or per-session `ElbowAngleEstimator` instance; reset on engine `start` and iOS lens switch; synchronize or confine to one thread.
- **Effort**: M
- **Verified-by**: pending

### [E-06] Triple smoothing stack latency unmeasured (PF-19 math)
- **Severity**: P2
- **Type**: Performance
- **Status**: NEEDS-DATA
- **Related-PF**: PF-19
- **Files**: `PoseLandmarkSmoother.kt` / `LandmarkSmoother.kt` (One-Euro), `AngleSmoother.kt:6-32` (window=3 via `TimingPolicy`), `PhaseStateMachine.kt:15` / `StabilityPolicy.phaseHysteresisDegrees=3.0`
- **Evidence**: Three sequential temporal filters on the path landmarks→angles→phase. No device measurement of end-to-end lag to phase flip. Rough lower bound: MA alone ≈ 2 frames delay at steady state (~67–200 ms depending on fps profile 10–30). One-Euro + hysteresis add more under slow motion.
- **Impact**: Possible sluggish rep/phase response; unknown if required for legacy parity (OQ-02).
- **Fix-sketch**: Instrument phase-flip lag vs raw angle crossing on device; then consider collapsing MA or reducing window if One-Euro suffices.
- **Effort**: M
- **Verified-by**: pending

### [E-07] Visibility threshold split 0.5 vs 0.3 (E5)
- **Severity**: P2
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `TimingPolicy.kt:17`, `MovitTrainingEngine.kt:318`, `Landmark.kt:14`, `PoseFrameAssembler.kt:21`, `StabilityPolicy.kt:19-20`, `ElbowAngleEstimator.kt:73,93`
- **Evidence**: Inventory table above. Production `VisibilityMonitor.minVisibility` is **0.3** while angle/position gates are **0.5**.
- **Impact**: Pause/resume and “joints visible” messaging can disagree with whether angles are even computed; confusing UX and possible early pause while angles still flow (or vice versa for ANY_SIDE at 0.5).
- **Fix-sketch**: Single source-of-truth threshold policy; document intentional 0.3 pause sensitivity if legacy-required; align tests.
- **Effort**: S
- **Verified-by**: pending

### [E-08] Elbow estimator not reset on new exercise (E4)
- **Severity**: P2
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: PF-18
- **Files**: `MovitTrainingEngine.kt:479-503`, `ElbowAngleEstimator.kt:26-27,146-168`, `CameraXFrameSource.kt:142`
- **Evidence**: Engine `start()` resets angle smoother, PSM, visibility, ingress — not `PoseFrameAssembler.resetElbowEstimator()`. Only lens switch / debug reset it. HOLD can replay `lastStable` for 500 ms into a new movement pattern.
- **Impact**: First reps after exercise change may use stale elbow correction, especially after side-view / low-confidence segments.
- **Fix-sketch**: Call `resetElbowEstimator()` from engine `start()` / exercise boundary; mirror on iOS lens switch.
- **Effort**: S
- **Verified-by**: pending

### [E-09] One-Euro equal/backward timestamp edges uncovered (E6)
- **Severity**: P3
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `OneEuroFilter.kt:19-24`, `OneEuroFilterParityTest.kt`, `OneEuroFilterTest.kt`
- **Evidence**: `dt` coerced with `coerceIn(0.001f, 0.1f)` — negative or zero Δt becomes 1 ms and still updates `lastTime`. No parity test for `t2==t1`, `t2<t1`, or behavior immediately after smoother `reset` with a large timestamp jump.
- **Impact**: Brief velocity spike / overshoot after lens switch or clock quirks; likely rare if reset clears filters (first sample pass-through after reset).
- **Fix-sketch**: On `dt<=0`, reset filters or skip update; extend `OneEuroFilterParityTest`.
- **Effort**: S
- **Verified-by**: pending

### [E-10] Overlay FILL_CENTER + front mirror aligned in code; device aspect mismatch NEEDS-DATA (E7)
- **Severity**: P2
- **Type**: Correctness
- **Status**: NEEDS-DATA
- **Related-PF**: —
- **Files**: `DisplayLandmarkTransform.kt:43-69`, `SkeletonOverlayMapper.kt:29-71`, `TrainingSessionScreen.kt:103-112`, `TrainingCameraSurface.android.kt:17-18`, `CameraXFrameSource.kt:282-306`, `TrainingThroughputProfile.kt:21-42`, `TrainingSessionViewModel.kt:531-539`
- **Evidence**: Analysis profiles and preview strategy are both 4:3; overlay uses analysis dims from `PoseFrame` with FILL_CENTER and `mirrorX=isFrontCamera`. Unit tests cover center mapping and mirrorX. No automated test binds real PreviewView resolution vs analysis `resolutionInfo`.
- **Impact**: If analysis fallback aspect ≠ preview 4:3, skeleton drifts vs body — user-visible only.
- **Fix-sketch**: Log/assert analysis vs preview aspect in diagnostics; device screenshot test; refuse non-4:3 analysis or letterbox consistently.
- **Effort**: M
- **Verified-by**: pending

### [E-11] 3D/2D mode switch has no hysteresis (E1 residual)
- **Severity**: P2
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `PoseFrameAssembler.kt:65-84`, `AngleSmoother.kt:30-32`, `StabilityPolicy.kt:14`
- **Evidence**: Hard visibility gate per frame; smoother averages values only. Relevant **after** [E-01] fix restores 3D on assemble path. `PoseFrameAssemblerTest.limbAngles_fallbackTo2D_whenWorldLandmarkNotVisible` documents the flip but not temporal oscillation.
- **Impact**: Flicker at visibility≈0.5 → angle jumps → possible phase chatter if jump ≫ 3° hysteresis.
- **Fix-sketch**: Sticky 3D mode with N-frame grace, or hysteresis band on visibility; clear angle buffer on mode change.
- **Effort**: M
- **Verified-by**: pending

---

## Coverage notes

| Area | Read? | Parity/tests |
|------|-------|--------------|
| `JointAngleCalculator` / `PosePoint` | Yes | `JointAngleCalculatorParityTest` |
| `PoseFrameAssembler` / `VirtualLandmarks` | Yes | Assembler tests; **gap**: assemble+world size |
| `PoseLandmarkMirroring` / `PoseFrame.mirrored` | Yes | Angle-swap-only test |
| `ElbowAngleEstimator` / diagnostics | Yes | Strategy/hold/reset tests |
| `JointLandmarkMapping` | Yes | Indirect via engine/overlay tests |
| `LandmarkTiltCorrector` | Yes | No dedicated parity test |
| `DisplayLandmarkTransform` / skeleton projector | Yes | Unit + mapper tests |
| `OneEuroFilter` / smoothers | Yes | Basic parity; edge gaps |
| `AngleSmoother` / PSM hysteresis | Yes (math half of PF-19) | Policy defaults |
| iOS assemble path | Yes | Same assembler; no elbow reset |

---

## Suggested verification (adversarial)

1. **E-01**: Reproduce with 33 norm + 33 world through `assemble`; assert `leftKnee` equals `angleDegrees3D` (expect fail today).
2. **E-03**: Asymmetric landmarks through `mirrorLandmarks`; expect no change today; after one-way fix, expect swap.
3. **E-05**: Concurrent `assemble` + `resetElbowEstimator` stress; debug+training dual consumer.
4. **PF-11**: Front-camera frame with only right-side high visibility; check which joint ANY_SIDE keeps.
