# Performance Baseline — Measurement Protocol

> **Owner**: Track K (Diagnostics & Performance Measurement)  
> **Status**: Protocol defined; **all metric cells `NEEDS-DEVICE`** until captured on physical hardware.  
> **Date**: 2026-07-10

---

## Purpose

Establish reproducible fps, latency, GC, and memory baselines for the camera training hot path (§2 frame journey) across Android and iOS. Compare **release** (no pipeline diagnostics) vs **debug** (with `TrainingPipeline` logcat) to quantify instrumentation distortion ([K-01]).

---

## Standard Exercise Scenario

| Parameter | Value |
|-----------|-------|
| Exercise | Single primary-joint rep exercise (e.g. squat or push-up), 12 reps, 1 set |
| Duration | **60 s** active TRAINING phase (or full set if shorter) |
| Camera | Front-facing, default throughput profile (`STABLE`) |
| Device tier | **Tier A**: flagship (e.g. Pixel 8 / iPhone 15); **Tier B**: mid-range (e.g. Pixel 6a / iPhone SE 3) |
| Build | **Release** primary; repeat subset on **Debug** for distortion check |
| Environment | Indoor, good lighting, user full-body in frame |

---

## Measurement Protocol

### Pre-run setup

1. Install build; grant camera permission.
2. **Release run**: confirm `adb logcat -s TrainingPipeline` emits **nothing** during session.
3. **Debug run** (Android only): `adb logcat -s TrainingPipeline` to capture periodic lines.
4. Enable systrace/Perfetto (Android) or Instruments Time Profiler (iOS) for one run per tier.
5. Clear app data or cold-start between A/B comparisons.

### Run procedure (per device × build variant)

1. Navigate to training session; wait for camera bound (`milestone | camera bound` on debug).
2. Complete setup → countdown → **60 s TRAINING** (or until 12 reps).
3. Record screen + logcat/trace for window.
4. Note exercise end → report screen → exit.
5. Repeat **3 runs**; report median and p95.

---

## Metrics Table

> Fill **Value** columns after device capture. Until then: **`NEEDS-DEVICE`**.

### A. Frame rate by pipeline stage (60 s TRAINING window)

| Metric ID | Stage | Source / method | Android Tier A | Android Tier B | iOS Tier A | iOS Tier B | Notes |
|-----------|-------|-----------------|---------------|---------------|------------|------------|-------|
| FPS-01 | Camera accepted for analysis | `cam=Nfps` in `TrainingPipeline` periodic line (debug) OR trace counter at `CameraXFrameSource.kt:315` | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | Android debug only unless trace added |
| FPS-02 | Camera throttled (skip) | `skipThrottle=N` in periodic line | NEEDS-DEVICE | NEEDS-DEVICE | N/A | N/A | No iOS hook today |
| FPS-03 | Pose results (body+no-pose) | `pose=Nfps` in periodic line | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | iOS needs new instrumentation |
| FPS-04 | VM ingress | `vm=in N` in periodic line | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | Over-counts vs unique frames |
| FPS-05 | VM processed | `vm=proc N` in periodic line | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | |
| FPS-06 | VM conflated (dropped) | `conflated=N` in periodic line | **0 (broken)** | **0 (broken)** | N/A | N/A | Fix [K-04] before meaningful measure |
| FPS-07 | Engine dropped (ingress busy) | `drop=N` in periodic line | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | `FrameIngressGate` |
| FPS-08 | Pose busy skip | `busySkip=N` in periodic line | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | `inferenceInFlight` gate |
| FPS-09 | Debug overlay FPS | `TrainingDebugFpsOverlay` on-screen | NEEDS-DEVICE | NEEDS-DEVICE | N/A | N/A | Android debug only |

### B. Latency (ms)

| Metric ID | Stage | Source / method | Android Tier A | Android Tier B | iOS Tier A | iOS Tier B | Notes |
|-----------|-------|-----------------|---------------|---------------|------------|------------|-------|
| LAT-01 | MediaPipe inference avg | `inferMs=N` in periodic line | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | Per-result avg over 2 s window |
| LAT-02 | MediaPipe inference p95 | Perfetto slice `MediaPipePoseDetector.detectAsync` → result callback | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | Proposed trace point T-02 |
| LAT-03 | Post-inference smooth+assemble | Perfetto: callback start → `emitPoseFrame` | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | Proposed T-03 |
| LAT-04 | VM worker `processPoseFrameOnWorker` | Perfetto: worker entry → exit | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | Proposed T-05 |
| LAT-05 | Engine `processPoseFrame` | Perfetto: `MovitTrainingEngine.processPoseFrame` | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | Proposed T-06 |
| LAT-06 | Motion-to-photon (approx) | Frame timestamp → Choreographer `onFrame` after landmark UI update | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | Proposed T-09; high jitter expected |
| LAT-07 | Inference stall events | `stalls=N` in periodic line | NEEDS-DEVICE | NEEDS-DEVICE | N/A | N/A | `INFERENCE_STALL_TIMEOUT_MS` |

### C. GC & CPU

| Metric ID | Metric | Source / method | Android Tier A | Android Tier B | iOS Tier A | iOS Tier B |
|-----------|--------|-----------------|---------------|---------------|------------|------------|
| GC-01 | Minor GC count / min | `adb logcat -s art` or Perfetto `HeapGC` | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE |
| GC-02 | Major GC count / min | Same | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE |
| CPU-01 | `analysisExecutor` CPU % | Perfetto process stats | NEEDS-DEVICE | NEEDS-DEVICE | N/A | N/A |
| CPU-02 | MediaPipe callback thread CPU % | Perfetto | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE |
| CPU-03 | `poseFrameWorker` CPU % | Perfetto | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE |

### D. Memory (RSS / footprint)

| Checkpoint | Source / method | Android Tier A | Android Tier B | iOS Tier A | iOS Tier B |
|------------|-----------------|---------------|---------------|------------|------------|
| MEM-01 Screen open (pre-camera) | `adb shell dumpsys meminfo <pkg>` / Xcode gauge | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE |
| MEM-02 After 1 set complete | Same | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE |
| MEM-03 After 3 exercises (workout) | Same | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE |
| MEM-04 After report viewed + exit | Same | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE |

### E. Debug vs release distortion (Android only)

| Comparison | Metric | Release | Debug | Delta | Status |
|------------|--------|---------|-------|-------|--------|
| DIST-01 | FPS-03 pose fps | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | Quantifies [K-01] impact |
| DIST-02 | LAT-04 VM worker p95 | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | |
| DIST-03 | GC-01 minor GC/min | NEEDS-DEVICE | NEEDS-DEVICE | NEEDS-DEVICE | |

---

## Proposed Trace Points (suggestion only — not implemented)

These sections would be added to Perfetto/macOS Instruments via `android.os.Trace` / `os_signpost` wrappers. **Do not implement in this review pass.**

| ID | Label | Location | Thread | Measures |
|----|-------|----------|--------|----------|
| **T-01** | `movit/camera/analyze` | `CameraXFrameSource.kt` analyzer lambda entry/exit (`:308-317`) | `analysisExecutor` | Camera throttle + `detectAsync` submit |
| **T-02** | `movit/pose/inference` | `MediaPipePoseDetector.detectAsync` submit → `onPoseResult` (`MediaPipePoseDetector.kt:153-313`) | analysis → callback | Pure inference + bitmap prep |
| **T-03** | `movit/pose/postprocess` | `onPoseResult` smoother/assemble → `emitPoseFrame` (`CameraXFrameSource.kt:177-190`) | MediaPipe callback | One-Euro + assemble + emit |
| **T-04** | `movit/vm/ingress` | `TrainingSessionViewModel.onPoseFrame` (`:426-429`) | MediaPipe callback | Channel send latency |
| **T-05** | `movit/vm/worker` | `processPoseFrameOnWorker` full body (`:441-529`) | `Dispatchers.Default` | VM processing + state updates |
| **T-06** | `movit/engine/frame` | `MovitTrainingEngine.processPoseFrame` (`:601-759`) | same as T-05 | Engine per-frame path |
| **T-07** | `movit/engine/mainpath` | `FramePipelineExecutor.runMainPath` | same as T-05 | Angle smooth + phase machine |
| **T-08** | `movit/engine/drop` | `frameIngress.tryAcquire` failure branch (`:586-588`) | same as T-05 | Ingress gate drops |
| **T-09** | `movit/ui/landmarks` | `applyPoseLandmarksToUi` + next Choreographer frame | worker → main | Motion-to-photon approx |
| **T-10** | `movit/snapshot/jpeg` | `takeSnapshotJpeg` (`MediaPipePoseDetector.kt:233-241`) | caller thread | Peak capture spikes |

### iOS parity trace points

| ID | Label | Location |
|----|-------|----------|
| **T-i01** | `movit/ios/camera/frame` | `IosCameraFrameSource` frame delivery |
| **T-i02** | `movit/ios/pose/detect` | `IosPoseDetector` / bridge |
| **T-i03** | `movit/ios/pose/assemble` | Shared `PoseFrameAssembler.assemble` |

---

## Logcat Capture Commands (Android debug)

```bash
# Pipeline periodic lines (2 s interval)
adb logcat -s TrainingPipeline

# GC events
adb logcat -s art

# Perfetto (10 s slice during TRAINING)
adb shell perfetto -c - --txt <<EOF
buffers { size_kb: 63488 }
data_sources { config { name: "linux.process_stats" } }
data_sources { config { name: "android.surfaceflinger.frametimeline" } }
duration_ms: 10000
EOF
```

---

## Acceptance Criteria for Baseline Complete

- [ ] All **FPS-** and **LAT-** cells filled for Tier A Android release (minimum bar).
- [ ] Tier B Android + Tier A iOS filled for cross-tier comparison.
- [ ] **DIST-** table filled to quantify debug instrumentation skew.
- [ ] Conflation metric fixed ([K-04]) and **FPS-06** re-measured.
- [ ] iOS instrumentation decision (OQ-04) resolved before iOS FPS/LAT columns are mandatory.

---

## Related Findings

| Finding | Impact on measurement |
|---------|----------------------|
| [K-01] | Debug runs understate true fps — use release for authoritative baseline |
| [K-04] | FPS-06 invalid until fixed |
| [K-07] | Use **release** APK for production baseline; debug APK for diagnostics only |
| [K-02] | iOS logcat columns require new hooks |

---

*Verified-by: pending — awaiting device capture.*
