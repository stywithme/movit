# Verified Frame Flow — Camera Training Engine

> **Purpose**: Authoritative frame journey after cross-track verification (A–D, especially C).  
> **Mode**: READ-ONLY review — citations are from the current `kmp-app/` tree (verified 2026-07-10).  
> **Supersedes**: Brief §2 line numbers and several behavioral claims; see §6.

---

## 1. Android frame journey (steps 1–10)

Hot path repeats at the **configured target fps** (production default **10 fps**, 320×240 — not 20–30 fps unless a higher throughput profile flag is set).

```
[1] TrainingSessionCameraHost.android.kt
      Registers frameListener; forwards frames to VM via route event
      feature/training/src/androidMain/.../TrainingSessionCameraHost.android.kt:85-107
        ↓
[2] CameraXFrameSource.bindUseCases
      Preview (4:3) + ImageAnalysis (KEEP_ONLY_LATEST + analysis resolution from CameraSourceConfiguration)
      core/pose-capture/src/androidMain/.../CameraXFrameSource.kt:236-353
        ↓  (thread: analysisExecutor — single-thread ExecutorService)
[3] Analyzer: shouldAnalyzeFrame() manual targetFps throttle
      → MediaPipePoseDetector.detectAsync(proxy, useFrontCamera)
      CameraXFrameSource.kt:307-316, :407-416
      MediaPipePoseDetector.kt:153-190
        ↓
[4] detectAsync: inferenceInFlight gate → imageProxy.toBitmap()
      → rotateBitmapForAnalysis (new Bitmap when rotation ≠ 0) → lastFrameBitmap
      → PoseLandmarker.detectAsync (LIVE_STREAM)
      MediaPipePoseDetector.kt:160-184, :213-226
        ↓  (thread: MediaPipe result callback)
[5] onPoseResult:
      MediaPipeLandmarkMapper → LandmarkSmoother (One-Euro ×33 norm + world)
      → PoseRefiner (no-op: AndroidPoseRefiner.isAvailable = false)
      → listener.onPoseDetected
      MediaPipePoseDetector.kt:275-312
        ↓  (same MediaPipe callback thread; inferenceInFlight released in finally :310-311)
[6] CameraXFrameSource listener → PoseFrameAssembler.assemble
      → emitPoseFrame → LensSwitchFrameGate → frameListener
      CameraXFrameSource.kt:175-190, :145-158
        ↓  (still MediaPipe callback thread until Host returns)
[7] Route: PoseFrameReceived → TrainingSessionViewModel.onPoseFrame
      → poseFrameChannel.trySend (Channel.CONFLATED)
      MovitTrainingRoutes.kt:200
      TrainingSessionViewModel.kt:426-429
        ↓  (thread: poseFrameWorker on Dispatchers.Default)
[8] processPoseFrameOnWorker:
      TRAINING → engine.processFrame(frame) directly on worker + elapsed + overlay
      setup/countdown → SupervisorSignal.PoseFrame → ValidatePose (VM no-op)
                        + SetupReadinessGate.validate + state updates
      TrainingSessionViewModel.kt:441-528
        ↓
[9] MovitTrainingEngine.processFrame → processPoseFrame:
      presenceBridge → FrameIngressGate → frame.mirrored() (front cam)
      → JointAngleTracker → visibility (dual evaluate) → PauseController
      → FramePipelineExecutor.runMainPath → feedback / rep / hold hooks
      MovitTrainingEngine.kt:577-595, :601-767
        ↓  (engine callbacks invoke VM lambdas on caller thread — Default or Main)
[10] engine callbacks → FeedbackRouter + _state.update
      → MovitTrainingRoutes collectAsStateWithLifecycle → TrainingSessionScreen
      MovitTrainingRoutes.kt:112
        ↓  (on exercise complete)
      finalizeCurrentExercise → writeHooks.finalizeUpload → reports → enqueueUpload
      TrainingSessionViewModel.kt:1062-1119
```

### Step-by-step evidence (Android)

| Step | What happens | Verified citation |
|------|----------------|-------------------|
| **1** | `DisposableEffect` wires `setFrameListener`, debug FPS counter (debug only), `onFrame(frame)`; `onDispose` calls `stop()` only | `TrainingSessionCameraHost.android.kt:85-107` |
| **2** | `bindUseCases`: Preview 4:3, ImageAnalysis with `STRATEGY_KEEP_ONLY_LATEST`, resolution from `configuration.analysisWidth/Height`, analyzer on `analysisExecutor()` | `CameraXFrameSource.kt:282-318`, `:355-358` |
| **3** | `shouldAnalyzeFrame()` enforces `targetFps`; accepted frames call `poseDetector.detectAsync` | `CameraXFrameSource.kt:407-416`, `:309-316` |
| **4** | `tryAcquireInferenceSlot` (AtomicBoolean); bitmap pipeline; `marker.detectAsync` | `MediaPipePoseDetector.kt:160-184` |
| **5** | Smooth + optional refine in `onPoseResult`; `inferenceInFlight` cleared in `finally` after full listener chain | `MediaPipePoseDetector.kt:289-311` |
| **6** | Assemble in frame-source listener; `LensSwitchFrameGate.acceptFrame`; `frameListener?.invoke` | `CameraXFrameSource.kt:176-190`, `:145-158` |
| **7** | `requiresCamera()` gate; `Channel.CONFLATED` ingress | `TrainingSessionViewModel.kt:426-429`, `:173` |
| **8** | Worker branches on `supervisor.state`; TRAINING bypasses supervisor for frames | `TrainingSessionViewModel.kt:458-466`, `:471-525` |
| **9** | Engine ingress gate + full per-frame pipeline | `MovitTrainingEngine.kt:577-767` |
| **10** | UI collect + finalize/upload on completion | `MovitTrainingRoutes.kt:112`; `TrainingSessionViewModel.kt:1062-1119` |

---

## 2. iOS parallel journey

Same VM, engine, and `PoseFrameAssembler` from step 7 onward (`commonMain`). Capture differs:

```
[i1] TrainingSessionCameraHost.ios.kt
      Registers frameListener; UIKitView preview; stop() on dispose
      feature/training/src/iosMain/.../TrainingSessionCameraHost.ios.kt:47-75
        ↓
[i2] IosCameraFrameSource.start
      AVCaptureSessionPresetHigh (does NOT bind analysisWidth/Height from config)
      alwaysDiscardsLateVideoFrames + manual targetFps throttle on outputQueue
      IosCameraFrameSource.kt:94-182, :144-155
        ↓  (thread: outputQueue — serial dispatch queue)
[i3] IosPoseDetector.detectAsync(sampleBuffer, isFrontCamera)
      → IosPoseLandmarkerBridge (Swift) → MediaPipe detectAsync
      IosCameraFrameSource.kt:155
      IosPoseDetector.kt:78-89
      iosApp/.../MovitPoseLandmarkerBridge.swift:69-87
        ↓  (thread: Swift/MediaPipe result callback → Kotlin resultHandler)
[i4] PoseLandmarkFlatCodec.decode (flat → List<Landmark>)
      IosPoseDetector.kt:124-134
        ↓  (callback thread → IosCameraFrameSource listener)
[i5] PoseLandmarkSmoother.smooth (+ world) → PoseFrameAssembler.assemble
      → frameListener (no LensSwitchFrameGate / CameraStartGate on iOS)
      IosCameraFrameSource.kt:101-114
        ↓
[i6–i10] Identical to Android steps 7–10 (commonMain VM + engine + routes)
      TrainingSessionViewModel.kt:426-529, :577-767 (engine)
      MovitTrainingRoutes.kt:200, :112
```

### iOS-specific notes (Track B)

| Topic | iOS behavior | Citation |
|-------|----------------|----------|
| Smoothing location | In `IosCameraFrameSource` listener, not detector | `IosCameraFrameSource.kt:103-105` |
| Refine | Absent (NoOp) | Track B — no refine hook |
| Mirror at capture | Front camera `setVideoMirrored(true)` on connection | `IosCameraFrameSource.kt:168-171` |
| Analysis resolution | Native sensor (`PresetHigh`), config dimensions unused for capture | `IosCameraFrameSource.kt:128`, `:111-112` |
| Busy gate | **No** `inferenceInFlight` equivalent | `IosPoseDetector.kt:78-89` |
| Lens switch gates | **No** `LensSwitchFrameGate` / `CameraStartGate` | grep: none under `iosMain` |
| Timestamps | Wall clock `NSDate.timeIntervalSince1970` | `IosPoseDetector.kt:152-153` |
| Stop lifecycle | `poseDetector.shutdown()` on every `stop()` | `IosCameraFrameSource.kt:184-187` |

---

## 3. Threading table (Android + iOS)

| Stage | Android thread / dispatcher | iOS thread / dispatcher | Evidence |
|-------|----------------------------|-------------------------|----------|
| Camera frame delivery to analyzer | `analysisExecutor` (single-thread) | `outputQueue` (serial GCD) | `CameraXFrameSource.kt:355-358`; `IosCameraFrameSource.kt:55`, `:158` |
| FPS throttle (layer 2) | `analysisExecutor` | `outputQueue` | `CameraXFrameSource.kt:407-416`; `IosCameraFrameSource.kt:144-154` |
| Bitmap / buffer prep + submit inference | `analysisExecutor` | `outputQueue` → bridge | `MediaPipePoseDetector.kt:153-184`; `IosPoseDetector.kt:78-89` |
| MediaPipe result → smooth → assemble → `frameListener` | MediaPipe callback thread | Swift callback → Kotlin `resultHandler` | `MediaPipePoseDetector.kt:275-312`; `IosCameraFrameSource.kt:101-114` |
| Debug `frameCounter` (Compose) | MediaPipe callback | Same listener thread (debug path exists but `isTrainingDebugBuild()` is `false` on iOS) | `TrainingSessionCameraHost.android.kt:90-98`; `.ios.kt:58-67` |
| `onPoseFrame` / `trySend` | MediaPipe callback (via Host) | Pose delivery thread | `TrainingSessionViewModel.kt:426-429` |
| `processPoseFrameOnWorker` | `Dispatchers.Default` | `Dispatchers.Default` | `TrainingSessionViewModel.kt:434-438`, `:441-528` |
| `engine.processFrame` (steady TRAINING) | `Dispatchers.Default` | `Dispatchers.Default` | `TrainingSessionViewModel.kt:463` |
| `engine.processFrame` (`SupervisorAction.ProcessFrame`) | **Main** (`viewModelScope`) | **Main** | `TrainingSessionViewModel.kt:706-707`, `:982-994` |
| `SessionSupervisor.processSignal` | **Both** Main and Default | **Both** (commonMain) | Worker `:451`, `:471`; countdown `:736-737` |
| Countdown tick / finish | Main (`countdown.start(viewModelScope)`) | Main | `TrainingSessionViewModel.kt:997`; `CountdownController` via `wireCountdown` |
| Supervisor `actions` handling | Main | Main | `TrainingSessionViewModel.kt:706-707` |
| Engine callbacks → `_state.update` / feedback | Thread that called `processFrame` | Same | Default in steady state; Main on race path |
| Compose UI `state` collect | Main | Main | `MovitTrainingRoutes.kt:112` |
| `finalizeCurrentExercise` I/O | Main launches; `awaitPendingCaptures` on Default | Same | `TrainingSessionViewModel.kt:1068-1071` |

**Design check (PF-07)**: `MovitTrainingEngine` uses plain `var` state and `FrameIngressGate` is a non-volatile `Boolean` (`FrameIngressGate.kt:9-25`). Steady TRAINING is single-threaded on the worker; a **transition race** can overlap Main + Default — see §5.

---

## 4. Backpressure layers — active per platform

Five stacked layers (Brief §4). “Active” = implemented and exercised on the live camera path.

| # | Layer | Android | iOS | Location |
|---|--------|---------|-----|----------|
| **1** | Camera keeps latest frame only | **Yes** — `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` | **Yes** — `alwaysDiscardsLateVideoFrames = true` | `CameraXFrameSource.kt:305`; `IosCameraFrameSource.kt:149` |
| **2** | Manual `targetFps` throttle | **Yes** — `shouldAnalyzeFrame()` | **Yes** — `minFrameIntervalMs` on `outputQueue` | `CameraXFrameSource.kt:407-416`; `IosCameraFrameSource.kt:144-154` |
| **3** | Inference busy gate | **Yes** — `inferenceInFlight` (held through smooth+assemble+emit) | **No** — overlapping `detectAsync` possible | `MediaPipePoseDetector.kt:192-201`, `:310-311`; no iOS equivalent |
| **3b** | Lens switch frame gate (Android only) | **Yes** — drops/gates during lens flip | **N/A** | `CameraXFrameSource.kt:151-152` |
| **4** | VM `Channel.CONFLATED` | **Yes** | **Yes** (shared VM) | `TrainingSessionViewModel.kt:173`, `:429` |
| **5** | `FrameIngressGate` in engine | **Yes** (not cross-thread safe) | **Yes** (shared engine) | `MovitTrainingEngine.kt:586-594`; `FrameIngressGate.kt:15-25` |

**Production default throughput** (both platforms read the same config): `TrainingThroughputProfiles.STABLE` = 320×240 @ **10 fps** unless `readTrainingThroughputProfileFlag()` overrides (`TrainingThroughputProfile.kt:21-26`, `TrainingThroughputFlags.kt:8-10`). On iOS, layer 2 throttles submits but inference may still run at **native resolution** (Track B gap).

**Layer interaction (Android, Track A)**: Layers 2 and 3 both active; at 10 fps layer 2 is usually the primary limiter, layer 3 guards slow inference/post chains. At 20–30 fps profiles, layer 3 dominates. Device measurement still **NEEDS-DATA** (PF-22).

---

## 5. Dual-path `processFrame` (Track C)

### Steady state (intentional)

When `supervisor.state == TRAINING`, the worker **does not** send `SupervisorSignal.PoseFrame`. It calls `engine.processFrame` directly and only `supervisor.onTrainingPoseFrameProcessed()` to reset the NoPose timer:

```458:466:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt
    val runState = supervisor.state.value
    if (runState == SessionRunState.TRAINING) {
      supervisor.onTrainingPoseFrameProcessed()
      updateSessionElapsed(frame.timestampMs)
      latestTrainingAngles = frame.angles
      engine?.processFrame(frame)
      refreshSkeletonOverlay(runState)
      maybeDeliverRandomMessage(frame.timestampMs)
      return
    }
```

`SessionSupervisor` documents this bypass (`SessionSupervisor.kt:150-156`).

### Setup / countdown (corrected vs brief)

Non-TRAINING states send `SupervisorSignal.PoseFrame`, which maps to **`SupervisorAction.ValidatePose`** — handled as **`Unit`** in the VM. Live setup validation is **`SetupReadinessGate` in the worker**, not the supervisor action:

```271:272:kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/SessionSupervisor.kt
            is SupervisorSignal.PoseFrame -> {
                emit(SupervisorAction.ValidatePose(signal.angles, signal.landmarks, signal.isFrontCamera))
```

```996:996:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt
      is SupervisorAction.ValidatePose -> Unit
```

`SupervisorAction.ProcessFrame` is emitted **only** when `PoseFrame` arrives while supervisor state is already **`TRAINING`** (`SessionSupervisor.kt:334-343`) — i.e. the race/legacy path, not normal setup/countdown.

### Transition race (PF-07 CONFIRMED)

```mermaid
sequenceDiagram
    autonumber
    participant CD as CountdownController (Main)
    participant Sup as SessionSupervisor
    participant Main as handleSupervisorAction (Main)
    participant W as poseFrameWorker (Default)
    participant Eng as MovitTrainingEngine

    Note over CD,Eng: State = COUNTDOWN; engine.isRunning = false

    CD->>Sup: CountdownFinished
    Sup->>Sup: transitionTo(TRAINING)
    Sup->>Main: StartEngine
    Main->>Eng: start()

    Note over W: Frame N: runState still COUNTDOWN (stale read)

    W->>Sup: PoseFrame (non-TRAINING branch)
    Note over Sup: state is now TRAINING
    Sup->>Main: ProcessFrame(N)
    Main->>Eng: processFrame(N) on Main

    W->>Eng: Frame N+1: direct processFrame on Default

    Note over Eng: Overlap: FrameIngressGate is plain Boolean
```

Same pattern at `RESUME_COUNTDOWN` → `TRAINING`. `FrameIngressGate` does not provide atomic cross-thread exclusion (`FrameIngressGate.kt:9-21`). Affects **both platforms** (commonMain VM/engine).

### No-pose during TRAINING

Worker returns before `engine.processFrame` when `!frame.hasPose` (`TrainingSessionViewModel.kt:445-453`). Engine `presenceBridge.onNoPoseFrame` is therefore **dead on the camera TRAINING path**; supervisor `NoPoseFrame` timers handle full exit (unless `visibilityWarningActive` suppresses — Track C §C6).

---

## 6. Corrections to original brief §2

| # | Brief §2 claim | Verified correction | Evidence |
|---|----------------|---------------------|----------|
| **C1** | Hot path runs **20–30×/s** | Default production is **10 fps** @ 320×240 (`STABLE`); higher rates require throughput profile flag | `TrainingThroughputProfile.kt:21-26` |
| **C2** | Step [1] lines **85–127** | Listener/dispose block is **85–107**; preview bind is separate `LaunchedEffect` **118–126** | `TrainingSessionCameraHost.android.kt` |
| **C3** | Step [5] smooth/assemble both in one block on detector callback | **Split**: smooth in `MediaPipePoseDetector.onPoseResult`; **assemble** in `CameraXFrameSource` listener | `MediaPipePoseDetector.kt:289-309`; `CameraXFrameSource.kt:176-184` |
| **C4** | `PoseRefiner` optional | **Always no-op** in production: `AndroidPoseRefiner.isAvailable = false` | Track A |
| **C5** | Step [6] VM lines **257–261** | `onPoseFrame` is **426–429**; channel declared **173** | `TrainingSessionViewModel.kt` |
| **C6** | Step [7] lines **272–360** | `processPoseFrameOnWorker` is **441–528**; worker started **432–438** | `TrainingSessionViewModel.kt` |
| **C7** | Setup/countdown may emit **`SupervisorAction.ProcessFrame`** | Emits **`ValidatePose`** (no-op); setup logic is **`SetupReadinessGate` in worker** | `SessionSupervisor.kt:271-272`; `TrainingSessionViewModel.kt:479-525`, `:996` |
| **C8** | `ProcessFrame` on main is normal for countdown | **`ProcessFrame` on Main is the transition race** (or video); steady TRAINING uses worker only | Track C; `TrainingSessionViewModel.kt:458-466` vs `:982-994` |
| **C9** | Step [8] `processFrame` **567–585** | **`577–595`** (`processFrame`); **`601–767`** (`processPoseFrame` body) | `MovitTrainingEngine.kt` |
| **C10** | Step [9] routes line **107** | Full state collect at **`MovitTrainingRoutes.kt:112`**; frame wired at **:200** | `MovitTrainingRoutes.kt` |
| **C11** | Step [10] `finalizeCurrentExercise` **876–931** | **`1062–1119`** | `TrainingSessionViewModel.kt` |
| **C12** | iOS one-liner “same smoother” | iOS uses shared **`PoseLandmarkSmoother`** (not Android `LandmarkSmoother` class); smoothing runs in **`IosCameraFrameSource`**, not detector | `IosCameraFrameSource.kt:54`, `:103-105` |
| **C13** | Implicit platform parity on capture | iOS lacks layers **3**, **3b**, and config-bound analysis resolution; mirror policy differs at capture | Track B §B4–B5 |
| **C14** | `inferenceInFlight` released after `channel.trySend` in step 5–6 | Released in detector `finally` **after** assemble+`frameListener` (which triggers `trySend`) — same practical effect, location is detector **:310-311** + listener chain in frame source | `MediaPipePoseDetector.kt:310-311`; `CameraXFrameSource.kt:176-190` |

---

## 7. Related PF verdicts (frame-flow scope)

| PF | Verdict | Frame-flow note |
|----|---------|-----------------|
| **PF-06** | CONFIRMED | `recordVmIngress(wasConflated = false)` always — layer 4 drops invisible |
| **PF-07** | CONFIRMED | Dual-path race at COUNTDOWN→TRAINING; not continuous dual feed |
| **PF-08** | CONFIRMED | Debug-only Compose write off main; release gated |
| **PF-16** | CONFIRMED | Three presence layers; camera TRAINING no-pose uses supervisor only |
| **PF-22** | NEEDS-DATA | All five Android layers exist; redundancy of 2 vs 3 needs device counters |

---

## 8. Coverage

**Read for this document**: `TrainingSessionCameraHost` (android/ios), `CameraXFrameSource.kt`, `MediaPipePoseDetector.kt`, `IosCameraFrameSource.kt`, `IosPoseDetector.kt`, `TrainingSessionViewModel.kt` (ingress + supervisor wiring + finalize), `SessionSupervisor.kt`, `MovitTrainingEngine.kt` (processFrame path), `FrameIngressGate.kt`, `MovitTrainingRoutes.kt`, `TrainingThroughputProfile.kt`, track summaries A/B/C/D.

**Not re-derived here**: full `processPoseFrame` allocation table (Track D), geometry mirror correctness (Track D/E), session lifecycle stop paths (Track G), perf numbers (Track K / `perf-baseline.md`).
