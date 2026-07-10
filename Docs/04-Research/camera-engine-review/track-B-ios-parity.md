# Track B — iOS Capture + Cross-Platform Parity

**Reviewer**: Track B agent (read-only)  
**Date**: 2026-07-10  
**Scope**: `kmp-app/core/pose-capture/src/iosMain/`, shared `PoseLandmarkSmoother` / `PoseLandmarkFlatCodec`, `feature/training` + `core/training-engine` iOS actuals, `iosApp/MovitPoseLandmarkerBridge.swift`

---

## Summary

The iOS capture path is structurally aligned with Android through the **shared** `PoseFrameAssembler` and **matching One-Euro constants** (`minCutoff=1.0`, `beta=1.5`). The hot path converges at `TrainingSessionViewModel.onPoseFrame` via the same `PoseFrame` type.

Material parity gaps remain:

1. **Analysis resolution** — Android binds `ImageAnalysis` to `configuration.analysisWidth/Height` (default 320×240); iOS uses `AVCaptureSessionPresetHigh` and never reads those fields, so inference runs at native sensor resolution.
2. **Front-camera mirroring** — iOS mirrors at the `AVCaptureConnection` layer; Android deliberately feeds **unmirrored** bitmaps to MediaPipe and mirrors only at display/engine layers.
3. **Backpressure** — Android has `inferenceInFlight`, `LensSwitchFrameGate`, and `CameraStartGate`; iOS has only `alwaysDiscardsLateVideoFrames` + manual FPS throttle.
4. **Timestamps** — Android uses `SystemClock.uptimeMillis()` (monotonic); iOS uses `NSDate.timeIntervalSince1970` (wall clock), affecting One-Euro `dt`.
5. **Diagnostics** — `isTrainingPipelineDiagnosticsEnabled()` is hardcoded `false` on iOS; all `TrainingPipelineDiagnostics` calls are no-ops (PF-05 iOS half **CONFIRMED**).

No source edits were made. Several P1 findings need device validation (`NEEDS-DATA`) for user-visible rep-count impact.

---

## B1 — Pipeline parity (smooth → refine → assemble, One-Euro)

### Android reference (§2)

```
CameraX analyzer → MediaPipePoseDetector.detectAsync
  → onPoseResult: LandmarkSmoother.smooth (+ world)
  → PoseRefiner.refine (optional)
  → CameraXFrameSource listener → PoseFrameAssembler.assemble → frameListener
```

Evidence: `MediaPipePoseDetector.kt:289-309` (smooth/refine in detector), `CameraXFrameSource.kt:176-190` (assemble in frame source).

### iOS actual

```
AVCaptureVideoDataOutput (outputQueue)
  → fps throttle (minFrameIntervalMs)
  → IosPoseDetector.detectAsync → Swift MovitPoseLandmarkerBridge
  → resultHandler: PoseLandmarkFlatCodec.decode
  → IosCameraFrameSource listener: PoseLandmarkSmoother.smooth (+ world)
  → PoseFrameAssembler.assemble → frameListener
```

Evidence:

- Throttle + submit: `IosCameraFrameSource.kt:144-155`
- Decode in detector: `IosPoseDetector.kt:124-134`
- Smooth + assemble in frame source: `IosCameraFrameSource.kt:101-114`
- Swift inference: `MovitPoseLandmarkerBridge.swift:69-87`, callback `131-158`

### One-Euro constants — **MATCH**

| Constant | Shared `PoseLandmarkSmoother` | Android `LandmarkSmoother` |
|---|---|---|
| `minCutoff` | `1.0f` (`PoseLandmarkSmoother.kt:58`) | `PoseLandmarkSmoother.DEFAULT_MIN_CUTOFF` (`LandmarkSmoother.kt:14`) |
| `beta` | `1.5f` (`PoseLandmarkSmoother.kt:59`) | `PoseLandmarkSmoother.DEFAULT_BETA` (`LandmarkSmoother.kt:15`) |
| Landmark count | 33 (`PoseLandmarkSmoother.kt:60`) | 33 (`LandmarkSmoother.kt:74`) |
| Filter type | `OneEuroFilter3D` (`PoseLandmarkSmoother.kt:20-21`) | `OneEuroFilter3D` (`LandmarkSmoother.kt:17-18`) |

Cross-platform determinism is tested in `PoseLandmarkSmootherTest.kt:22-39` (`deterministicAcrossInstances_guaranteesAndroidIosParity`).

### Refine step — **functionally equivalent (both NoOp today)**

- Android: `poseRefiner.refineLandmarks(smoothed)` when `poseRefiner.isAvailable` (`MediaPipePoseDetector.kt:290-294`); `AndroidPoseRefiner.isAvailable = false` (`AndroidPoseRefiner.kt:10-11`).
- iOS: no refine hook in pipeline; `IosPoseRefiner` is a `NoOpPoseRefiner` typealias (`NoOpPoseRefiner.kt:6-12`).

### Ordering difference (non-functional today)

| Stage | Android location | iOS location |
|---|---|---|
| Smooth | `MediaPipePoseDetector` | `IosCameraFrameSource` |
| Refine | `MediaPipePoseDetector` | absent (NoOp) |
| Assemble | `CameraXFrameSource` listener | `IosCameraFrameSource` listener |

**Verdict**: Pipeline stages match in effect; only placement of smoothing differs. One-Euro parameters are identical by construction.

---

## B2 — Flat codec cost (Swift ↔ Kotlin)

### Call chain per pose frame

1. Swift `flat(from:)` builds a Swift `[Float]` (165 elements), then `KotlinFloatArray` with per-index closure (`MovitPoseLandmarkerBridge.swift:170-186`).
2. Kotlin `PoseLandmarkFlatCodec.decode` allocates `List(33) { Landmark(...) }` (`PoseLandmarkFlatCodec.kt:36-45`).
3. `PoseLandmarkSmoother.smooth` allocates another `List<Landmark>` via `mapIndexed` (`PoseLandmarkSmoother.kt:41-49`).

### Cost estimate (theoretical, `NEEDS-DATA` on device)

| Step | Allocations / frame |
|---|---|
| Swift `[Float]` + `KotlinFloatArray` | ~165 floats + bridge object |
| `decode` | 33 `Landmark` objects + `List` |
| `smooth` | up to 33 more `Landmark` objects + `List` |
| `smoothWorld` (when present) | same again for world bank |

Android avoids flat codec entirely — MediaPipe Java types map in-process (`MediaPipePoseDetector.kt:285-289`).

`PoseLandmarkFlatCodecTest.kt` covers round-trip correctness only; no perf test.

**Verdict**: Double (triple with world) copy Swift→flat→objects→smoothed objects every frame. Acceptable at 10 fps target but wasteful vs Android; scales poorly if iOS runs at native resolution/fps.

---

## B3 — Memory & threading (`CVPixelBuffer` / sample buffers)

### Ownership

- Capture delegate runs on serial `outputQueue` (`IosCameraFrameSource.kt:55`, `158`).
- `CMSampleBuffer` passed to Swift via `Unmanaged...takeUnretainedValue()` (`MovitPoseLandmarkerBridge.swift:75-76`) — no extra retain in Kotlin; relies on AVFoundation buffer lifetime through `detectAsync`.
- Swift retains `lastFrameImage = image.image` for JPEG snapshots (`MovitPoseLandmarkerBridge.swift:85`) — one `UIImage` until replaced.

### Delivery thread

- Sample buffers: `outputQueue` (`IosCameraFrameSource.kt:151-158`).
- MediaPipe results: Swift delegate callback → Kotlin `resultHandler` (`IosPoseDetector.kt:112-137`) → `IosCameraFrameSource` listener (`IosCameraFrameSource.kt:101-114`) → `frameListener` → VM channel.

No explicit main-thread hop on iOS pose delivery (Android also delivers from MediaPipe callback thread into `frameListener`).

### Lifecycle

- `stop()` → `stopSessionOnly()` tears down session, delegate, preview layer (`IosCameraFrameSource.kt:184-197`); `poseDetector.shutdown()` (`IosCameraFrameSource.kt:186-187`).
- Unlike Android `CameraXFrameSource.stop()` (detector can stay warm — PF-24), iOS calls `poseDetector.shutdown()` every stop.

### Missing busy gate

Android `inferenceInFlight` blocks overlapping inference (`MediaPipePoseDetector.kt:192-201`, `311`). iOS `detectAsync` has no equivalent (`IosPoseDetector.kt:78-89`) — multiple in-flight MediaPipe calls possible under load.

**Verdict**: No obvious `CVPixelBuffer` zombie retain in Kotlin path; snapshot `UIImage` is the main extra retention. Overlapping inference is the larger structural risk.

---

## B4 — Mirror / rotation / analysis dimensions

### Mirror policy — **MISMATCH (P1 candidate)**

| Platform | Capture-time mirror | ML input | Display mirror |
|---|---|---|---|
| Android | not mirrored in bitmap (`MediaPipePoseDetector.kt:207-211`) | unmirrored landmarks | `DisplayLandmarkTransform.mirrorX = isFrontCamera` (`DisplayLandmarkTransform.kt:67`) |
| iOS | `connection.setVideoMirrored(true)` for front (`IosCameraFrameSource.kt:168-171`) | mirrored pixel buffer → `MPImage(..., orientation: .up)` (`MovitPoseLandmarkerBridge.swift:84`) | same `DisplayLandmarkTransform` via VM (`TrainingSessionViewModel.kt:537-539`) |

Engine still applies `frame.mirrored()` for front camera in `MovitTrainingEngine` (brief §2 step 8). If iOS landmarks are already mirror-transformed at capture, **double-mirror** relative to Android is plausible — needs device validation.

### Rotation

- iOS: portrait orientation on connection (`IosCameraFrameSource.kt:165-166`); `MPImage` uses `.up` (`MovitPoseLandmarkerBridge.swift:84`).
- Android: explicit `rotateBitmapForAnalysis` from `ImageProxy` rotation (`MediaPipePoseDetector.kt:167-176`).

### Analysis dimensions — **MISMATCH**

- Android binds analysis to `configuration.analysisWidth/Height` (`CameraXFrameSource.kt:290-301`).
- iOS ignores `analysisWidth`/`analysisHeight` on `CameraSourceConfiguration`; uses `AVCaptureSessionPresetHigh` (`IosCameraFrameSource.kt:127-128`).
- Reported `analysisImageWidth/Height` come from `lastFrameImage.size` after `MPImage` (`MovitPoseLandmarkerBridge.swift:148-149`) — typically full sensor, not 320×240.

Overlay projection uses `frame.analysisImageWidth/Height` (`TrainingSessionViewModel.kt:537-538`, `SkeletonOverlayMapper.kt:44`). Wrong dimensions → letterbox/crop math diverges from Android.

### FPS throttle — **partial match**

Both use `minIntervalMs = 1000 / targetFps`:

- iOS: `IosCameraFrameSource.kt:144-154`
- Android: `CameraXFrameSource.kt:407-415`

iOS also sets `alwaysDiscardsLateVideoFrames = true` (`IosCameraFrameSource.kt:149`) ≈ CameraX `KEEP_ONLY_LATEST`.

**Verdict**: FPS throttle matches; resolution and mirror policy do not. Overlay alignment risk is **CONFIRMED** structurally; rep-count impact **NEEDS-DATA**.

---

## B5 — Parity gap table (Android vs iOS)

| Capability | Android | iOS | Severity | Evidence |
|---|---|---|---|---|
| One-Euro `minCutoff` / `beta` | 1.0 / 1.5 via `LandmarkSmoother` | 1.0 / 1.5 via `PoseLandmarkSmoother` | ✅ Match | `PoseLandmarkSmoother.kt:58-59`, `LandmarkSmoother.kt:14-15` |
| `PoseFrameAssembler` | shared | shared | ✅ Match | `IosCameraFrameSource.kt:106-113`, `CameraXFrameSource.kt:177-184` |
| Pose refine (MLP) | hook present, NoOp | no hook, documented NoOp | ✅ Match (today) | `AndroidPoseRefiner.kt:10-11`, `NoOpPoseRefiner.kt:6-12` |
| Analysis resolution | `analysisWidth×Height` (320×240 default) | `PresetHigh` (native) | ❌ Gap | `CameraXFrameSource.kt:290-301`, `IosCameraFrameSource.kt:127-128` |
| Throughput profile flag | `readTrainingThroughputProfileFlag()` | always `null` → STABLE | ⚠️ Same default | `TrainingThroughputFlags.ios.kt:3`, `TrainingThroughputFlags.kt:8-10` |
| FPS manual throttle | `shouldAnalyzeFrame()` | `minFrameIntervalMs` | ✅ Match | `CameraXFrameSource.kt:407-415`, `IosCameraFrameSource.kt:144-154` |
| Drop late frames | `KEEP_ONLY_LATEST` | `alwaysDiscardsLateVideoFrames` | ✅ Match | `CameraXFrameSource.kt:305`, `IosCameraFrameSource.kt:149` |
| `inferenceInFlight` busy gate | yes | **no** | ❌ Gap | `MediaPipePoseDetector.kt:192-201`, absent in `IosPoseDetector.kt` |
| `LensSwitchFrameGate` | yes | **no** | ❌ Gap | `CameraXFrameSource.kt:151-152`, not in `IosCameraFrameSource.kt` |
| `CameraStartGate` | yes | **no** (full restart) | ⚠️ Different | `CameraXFrameSource.kt:63-64`, `IosCameraFrameSource.kt:94-98` |
| `resetElbowEstimator` on lens change | yes | **no** | ❌ Gap | `CameraXFrameSource.kt:142`, not in iOS start path |
| `landmarkSmoother.reset` on start | yes | yes | ✅ Match | `CameraXFrameSource.kt:141`, `IosCameraFrameSource.kt:97` |
| Front-camera mirror at capture | no (by design) | yes (`videoMirrored`) | ❌ Gap | `MediaPipePoseDetector.kt:207-211`, `IosCameraFrameSource.kt:168-171` |
| Timestamp clock | `SystemClock.uptimeMillis()` | `NSDate.timeIntervalSince1970` | ❌ Gap | `MediaPipePoseDetector.kt:159`, `IosPoseDetector.kt:152-153` |
| GPU→CPU warmUp fallback | yes | **no** | ❌ Gap | `MediaPipePoseDetector.kt:138-143`, `MovitPoseLandmarkerBridge.swift:39-66` |
| Heavy model upgrade | `scheduleHeavyUpgrade` | full model only | ❌ Gap | `MediaPipePoseDetector.kt:135-137`, `MovitPoseLandmarkerBridge.swift:29` |
| `minPosePresenceConfidence` | 0.5f explicit | not set in Swift warmUp | ⚠️ Gap | `MediaPipePoseDetector.kt:126`, `MovitPoseLandmarkerBridge.swift:53-55` |
| `modelType` / detector reinit | `reinitializePoseDetector()` | ignored in host | ❌ Gap | `TrainingSessionCameraHost.android.kt:122-124`, `TrainingSessionCameraHost.ios.kt:78-82` |
| Flat landmark bridge | in-process MediaPipe | Swift flat arrays | ❌ Gap | `MovitPoseLandmarkerBridge.swift:161-186` |
| `takeSnapshotJpeg` | bitmap copy + compress | `UIImage` JPEG | ✅ Both exist | `MediaPipePoseDetector.kt:233-241`, `MovitPoseLandmarkerBridge.swift:95-106` |
| `TrainingPipelineDiagnostics` | DEBUG-gated | **always off** | ❌ Gap | `TrainingPipelineLogger.android.kt:12`, `TrainingPipelineLogger.ios.kt:9` |
| Debug FPS overlay | debug build only | `isTrainingDebugBuild()=false` | ⚠️ Inert on iOS | `TrainingDebugBuild.ios.kt:3`, `TrainingSessionCameraHost.ios.kt:58` |
| `runBlocking` diagnostics mutex | debug Android only | N/A (disabled) | ✅ No iOS perf hit | `TrainingPipelineDiagnostics.kt:77-84`, `TrainingPipelineLogger.ios.kt:9` |

---

## PF / OQ verdicts (Track B scope)

### PF-05 (iOS half) — **CONFIRMED**

| Claim | Verdict | Evidence |
|---|---|---|
| iOS diagnostics completely disabled | **CONFIRMED** | `TrainingPipelineLogger.ios.kt:9` — `isTrainingPipelineDiagnosticsEnabled(): Boolean = false` |
| All diagnostic recording is no-op on iOS | **CONFIRMED** | Every `TrainingPipelineDiagnostics` method guards with `if (!isTrainingPipelineDiagnosticsEnabled()) return` (e.g. `TrainingPipelineDiagnostics.kt:77-78`, `86-87`) |
| `runBlocking`+Mutex hurts iOS capture | **REFUTED** | Guard prevents entry; iOS never executes `runBlocking` in diagnostics |

Android debug half of PF-05 is out of Track B scope (see Track K).

### OQ-03 — What parity level is required?

**Answer (documentation, not code):** The codebase **aspires to behavioral parity** for training evaluation (shared assembler, shared One-Euro constants, explicit comments in `PoseLandmarkSmoother.kt:7-14` and `IosCameraFrameSource.kt:53-54`) but **does not enforce numeric parity** for capture (resolution, mirror, timestamps, gates). Tests cover smoother determinism (`PoseLandmarkSmootherTest.kt:22-39`) and flat codec round-trip (`PoseLandmarkFlatCodecTest.kt:10-23`), not end-to-end Android-vs-iOS rep counting.

**Recommendation for product owner**: Decide whether parity means (a) same perceived UX, (b) same joint angles ±ε, or (c) bit-identical landmarks. Current implementation fits (a) intent but misses (b) on resolution/mirror without measurement.

### OQ-04 — iOS diagnostics off by design or gap?

**Answer**: **Likely intentional v1 stub, undocumented.**

- Hardcoded `false` with no `#if DEBUG` equivalent (`TrainingPipelineLogger.ios.kt:9`).
- Android uses `MovitGeneratedBuildConfig.DEBUG` (`TrainingPipelineLogger.android.kt:12`).
- iOS debug build flag also hardcoded false (`TrainingDebugBuild.ios.kt:3`), so debug tooling is inert on iOS.
- Comments in `IosTrainingFrameSnapshotPort.kt:9` and `MovitPoseCaptureIosBindings.kt:17` frame iOS as Phase 07 / device-validation path — diagnostics deprioritized.

**Needs product confirmation** whether iOS should gate on a build flag before release.

---

## Findings

### [B-01] iOS ignores throughput analysis resolution (runs at PresetHigh native size)
- **Severity**: P1
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `IosCameraFrameSource.kt:127-128`, `CameraSourceConfiguration.kt:6-7`, `CameraXFrameSource.kt:290-301`, `TrainingThroughputProfile.kt:21-26`
- **Evidence**: `resolveTrainingCameraConfiguration` sets `analysisWidth=320, analysisHeight=240, targetFps=10` for STABLE profile (`TrainingThroughputProfile.kt:21-26`, `TrainingThroughputFlags.kt:8-10`). Android `bindUseCases` applies those dimensions to `ImageAnalysis` (`CameraXFrameSource.kt:290-301`). iOS `start()` only uses `targetFps` for throttle (`IosCameraFrameSource.kt:144`) and sets `sessionPreset = AVCaptureSessionPresetHigh` (`127-128`) without downscaling.
- **Impact**: Higher inference cost per frame on iOS; `analysisImageWidth/Height` passed to overlay (`TrainingSessionViewModel.kt:537-538`) reflect native size, breaking letterbox parity with Android 320×240 — skeleton misalignment risk.
- **Fix-sketch**: Add `AVCaptureVideoDataOutput` pixel-format + `AVCaptureSession` preset or `AVVideoSettings` downscale to `configuration.analysisWidth/Height`; set bridge dimensions from config not `UIImage.size`.
- **Effort**: M
- **Verified-by**: adversarial-grok-4.5-xhigh

### [B-02] Front-camera capture mirroring diverges from Android unmirrored ML path
- **Severity**: P1
- **Type**: Correctness
- **Status**: NEEDS-DATA
- **Related-PF**: —
- **Files**: `IosCameraFrameSource.kt:168-171`, `MediaPipePoseDetector.kt:207-211`, `DisplayLandmarkTransform.kt:10-11`, `MovitPoseLandmarkerBridge.swift:84`
- **Evidence**: iOS enables `setVideoMirrored(true)` on front camera connection (`IosCameraFrameSource.kt:168-171`). Android comment explicitly states front mirror is **not** applied before MediaPipe (`MediaPipePoseDetector.kt:207-211`); display mirror is draw-time only (`DisplayLandmarkTransform.kt:10-11`). Engine applies `frame.mirrored()` for front camera (brief §2 step 8).
- **Impact**: Potential left/right flip or double-mirror in joint angles and rep detection on iOS front camera vs Android. User-visible form feedback asymmetry.
- **Fix-sketch**: Remove capture-time mirroring on iOS to match Android; keep preview mirroring in `DisplayLandmarkTransform` only. Validate with side-by-side landmark dump on same pose.
- **Effort**: S
- **Verified-by**: adversarial-grok-4.5-xhigh

### [B-03] No inference-in-flight gate on iOS pose detection
- **Severity**: P2
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: PF-04 (Android-side; related pattern)
- **Files**: `IosPoseDetector.kt:78-89`, `MediaPipePoseDetector.kt:192-201`
- **Evidence**: Android `tryAcquireInferenceSlot` returns false when `inferenceInFlight` is set (`MediaPipePoseDetector.kt:192-201`). iOS `detectAsync` always forwards to bridge (`IosPoseDetector.kt:89`) with no busy check.
- **Impact**: Under CPU/GPU pressure, stacked MediaPipe live-stream callbacks may increase latency variance and memory bandwidth; effective fps may exceed Android's serialized inference on same hardware class.
- **Fix-sketch**: Port `AtomicBoolean inferenceInFlight` + skip counter to `IosPoseDetector` or Swift bridge; release in result handler `finally`.
- **Effort**: S
- **Verified-by**: pending

### [B-04] Pose timestamps use wall-clock on iOS vs monotonic uptime on Android
- **Severity**: P2
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `IosPoseDetector.kt:152-153`, `MediaPipePoseDetector.kt:159`, `PoseLandmarkSmoother.kt:24-28`
- **Evidence**: iOS `uptimeMillis()` = `NSDate().timeIntervalSince1970 * 1000` (`IosPoseDetector.kt:152-153`). Android `detectAsync` uses `SystemClock.uptimeMillis()` (`MediaPipePoseDetector.kt:159`). Both feed One-Euro `timestampMs` (`IosCameraFrameSource.kt:103-105`, `MediaPipePoseDetector.kt:289`).
- **Impact**: Wall-clock can jump (NTP, backgrounding), producing abnormal `dt` in One-Euro filters — transient smoothing differences vs Android after resume or clock adjust.
- **Fix-sketch**: Use `mach_absolute_time` / `CACurrentMediaTime`-derived monotonic ms on iOS to mirror `SystemClock.uptimeMillis()`.
- **Effort**: S
- **Verified-by**: pending

### [B-05] Lens-switch hygiene missing `LensSwitchFrameGate` and `resetElbowEstimator`
- **Severity**: P2
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: PF-18 (elbow estimator shared state)
- **Files**: `IosCameraFrameSource.kt:94-98`, `CameraXFrameSource.kt:138-143`, `CameraXFrameSource.kt:151-152`, `IosPoseDetector.kt:73`
- **Evidence**: Android `prepareForLensSwitch` calls `lensSwitchGate.beginAwaitingFrames`, `poseDetector.resetTrackingState()`, `PoseFrameAssembler.resetElbowEstimator()` (`CameraXFrameSource.kt:138-143`); frames filtered by `lensSwitchGate.acceptFrame` (`151-152`). iOS `start()` resets only `landmarkSmoother` (`IosCameraFrameSource.kt:97`); `resetTrackingState()` is `Unit` (`IosPoseDetector.kt:73`); no elbow reset.
- **Impact**: Stale elbow correction state may persist across camera restart on iOS; brief wrong-facing frames during switch not suppressed (mitigated by full session restart on `useFrontCamera` change — `TrainingSessionCameraHost.ios.kt:78-82`).
- **Fix-sketch**: Call `PoseFrameAssembler.resetElbowEstimator()` in `IosCameraFrameSource.start()`; add `LensSwitchFrameGate` if incremental switch is added later.
- **Effort**: S
- **Verified-by**: pending

### [B-06] Swift→Kotlin flat landmark bridge allocates per frame
- **Severity**: P2
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `MovitPoseLandmarkerBridge.swift:170-186`, `PoseLandmarkFlatCodec.kt:32-45`, `PoseLandmarkSmoother.kt:41-49`
- **Evidence**: Swift builds `[Float]` then `KotlinFloatArray` with per-index lambda (`MovitPoseLandmarkerBridge.swift:170-186`). Kotlin `decode` allocates 33 `Landmark` instances (`PoseLandmarkFlatCodec.kt:36-45`). `smooth` allocates another list (`PoseLandmarkSmoother.kt:41-49`).
- **Impact**: ~2–3 short-lived lists + 165-float bridge array per pose frame; amplified if B-01 runs inference at native resolution/fps.
- **Fix-sketch**: Decode directly into reusable buffers; or pass typed native array without per-element `KotlinFloat` boxing; consider cinterop struct array.
- **Effort**: M
- **Verified-by**: pending

### [B-07] Training pipeline diagnostics permanently disabled on iOS
- **Severity**: P3
- **Type**: Architecture
- **Status**: CONFIRMED
- **Related-PF**: PF-05
- **Files**: `TrainingPipelineLogger.ios.kt:9`, `TrainingPipelineDiagnostics.kt:77-78`, `TrainingPipelineLogger.android.kt:12`
- **Evidence**: `internal actual fun isTrainingPipelineDiagnosticsEnabled(): Boolean = false` (`TrainingPipelineLogger.ios.kt:9`). Android gates on `MovitGeneratedBuildConfig.DEBUG` (`TrainingPipelineLogger.android.kt:12`). VM still calls `TrainingPipelineDiagnostics.recordVmIngress` etc. on both platforms (`TrainingSessionViewModel.kt:428`) — no-op on iOS.
- **Impact**: No on-device iOS pipeline metrics (fps layers, inference ms, conflation) for perf triage; PF-05 iOS half confirmed.
- **Fix-sketch**: Mirror Android DEBUG gate or add iOS build-config actual; wire `setCameraConfig` equivalent in `IosCameraFrameSource.start`.
- **Effort**: S
- **Verified-by**: pending

### [B-08] iOS MediaPipe warmUp lacks GPU→CPU fallback retry
- **Severity**: P2
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `MovitPoseLandmarkerBridge.swift:39-66`, `MediaPipePoseDetector.kt:138-143`, `IosPoseDetector.kt:57-64`
- **Evidence**: Android catches GPU init failure and recurses with `useGpu = false` (`MediaPipePoseDetector.kt:138-143`). Swift `warmUp` single try/catch returns `false` (`MovitPoseLandmarkerBridge.swift:59-61`); `IosPoseDetector` marks `INSTALLED_UNAVAILABLE` (`IosPoseDetector.kt:62-63`).
- **Impact**: Devices where GPU delegate fails get permanent no-pose on iOS while Android may fall back to CPU.
- **Fix-sketch**: On warmUp failure with `useGpu=true`, retry `PoseDetectorConfiguration(useGpu=false)` before marking unavailable.
- **Effort**: S
- **Verified-by**: pending

### [B-09] `modelType` parameter ignored on iOS training camera host
- **Severity**: P3
- **Type**: Architecture
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `TrainingSessionCameraHost.ios.kt:78-82`, `TrainingSessionCameraHost.android.kt:122-124`, `IosCameraFrameSource.kt:126`
- **Evidence**: Android calls `reinitializePoseDetector()` when `modelType` changes (`TrainingSessionCameraHost.android.kt:122-124`). iOS `LaunchedEffect` lists `modelType` but only calls `start(resolveTrainingCameraConfiguration(...))` (`TrainingSessionCameraHost.ios.kt:78-82`); warmUp always uses default `PoseDetectorConfiguration(useGpu = true)` (`IosCameraFrameSource.kt:126`).
- **Impact**: No runtime model switching on iOS; always `pose_landmarker_full.task` (`MovitPoseLandmarkerBridge.swift:29`). Low impact until multiple models ship on iOS.
- **Fix-sketch**: Plumb `modelType` into Swift bridge warmUp / model path selection to match `PoseModelResolver` behavior.
- **Effort**: M
- **Verified-by**: pending

### [B-10] `minPosePresenceConfidence` not configured on iOS MediaPipe options
- **Severity**: P2
- **Type**: Correctness
- **Status**: NEEDS-DATA
- **Related-PF**: —
- **Files**: `MediaPipePoseDetector.kt:126`, `MovitPoseLandmarkerBridge.swift:53-55`, `PoseDetectorConfiguration.kt:5-6`
- **Evidence**: Android sets `.setMinPosePresenceConfidence(MIN_PRESENCE)` with `MIN_PRESENCE = 0.5f` (`MediaPipePoseDetector.kt:68`, `126`). Swift warmUp sets detection and tracking confidence from configuration but not presence (`MovitPoseLandmarkerBridge.swift:53-55`). `PoseDetectorConfiguration` has no presence field (`PoseDetectorConfiguration.kt:3-8`).
- **Impact**: Default MediaPipe iOS presence threshold may differ → different no-pose dropout rate vs Android.
- **Fix-sketch**: Set `options.minPosePresenceConfidence = 0.5` in Swift; add field to `PoseDetectorConfiguration` if tunable.
- **Effort**: S
- **Verified-by**: pending

### [B-11] iOS debug FPS path exists but `isTrainingDebugBuild()` is always false
- **Severity**: P3
- **Type**: Dead-code
- **Status**: CONFIRMED
- **Related-PF**: PF-08
- **Files**: `TrainingSessionCameraHost.ios.kt:56-67`, `TrainingDebugBuild.ios.kt:3`
- **Evidence**: Host increments `frameCounter` from frame listener when `isTrainingDebugBuild()` (`TrainingSessionCameraHost.ios.kt:58-59`); iOS actual returns `false` (`TrainingDebugBuild.ios.kt:3`).
- **Impact**: No functional issue in release; debug FPS overlay never active on iOS. PF-08 thread-safety concern is **REFUTED** for production iOS (branch never taken).
- **Fix-sketch**: Wire iOS debug build detection to match Android `DEBUG` when training-debug builds are needed on device.
- **Effort**: S
- **Verified-by**: pending

---

## Coverage statement

### Files read (Track B)

| Area | Files |
|---|---|
| **pose-capture iosMain** | All 13 files under `core/pose-capture/src/iosMain/` |
| **commonMain (assigned)** | `PoseLandmarkSmoother.kt`, `PoseLandmarkFlatCodec.kt`, tests |
| **Android comparators** | `LandmarkSmoother.kt`, `MediaPipePoseDetector.kt` (partial), `CameraXFrameSource.kt` (partial), `AndroidPoseRefiner.kt` |
| **feature/training iosMain** | All 14 files |
| **training-engine iosMain** | All 8 files (incl. `TrainingPipelineLogger.ios.kt`) |
| **iosApp Swift** | `MovitPoseLandmarkerBridge.swift`, `iOSApp.swift` (bridge install) |
| **Shared config** | `CameraSourceConfiguration.kt`, `TrainingThroughputProfile.kt`, `TrainingThroughputFlags.kt`, `DisplayLandmarkTransform.kt` |

### Not read / out of scope

- `CameraXFrameSource.kt` full 500 lines (read bind/throttle/gate sections only)
- `MediaPipePoseDetector.kt` dispose/heavy-upgrade tail
- `feature/training-debug` iOS debug host (mentioned only for modelType parity)
- Runtime device traces (all perf/correctness P1 items marked `NEEDS-DATA` where appropriate)
- Tracks C–K findings (PF-07, PF-08 Android-only paths, etc.) except where they touch iOS parity

### Tests cited

- `PoseLandmarkSmootherTest.kt` — cross-instance One-Euro determinism
- `PoseLandmarkFlatCodecTest.kt` — encode/decode correctness
- No iOS-specific capture integration test found in reviewed paths

---

## Suggested remediation order (Track B only)

1. **[B-02]** Mirror policy — highest correctness risk, small fix  
2. **[B-01]** Analysis resolution — aligns cost + overlay  
3. **[B-04]** Monotonic timestamps — cheap One-Euro parity win  
4. **[B-03]** Inference busy gate — perf stability  
5. **[B-05]** Elbow reset on start — pairs with PF-18  
6. **[B-07]** Enable DEBUG diagnostics on iOS — unblocks measurement (OQ-04)
