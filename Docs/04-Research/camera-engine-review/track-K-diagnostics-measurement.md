# Track K — Diagnostics & Performance Measurement

> **Scope**: `TrainingPipelineDiagnostics`, `TrainingPipelineLogger.*`, `PipelineTrace` / `PipelineTraceConfig`, `TrainingSessionWriteDiagnostics`, `feature/training-debug` (overview), `TrainingSessionViewModel.recordVmIngress`.
>
> **Review mode**: READ-ONLY. No instrumentation implemented.
>
> **Date**: 2026-07-10

---

## Executive Summary

The training pipeline has a **unified logcat channel** (`TrainingPipeline` tag) gated to **Android DEBUG only**, with **iOS diagnostics hard-disabled**. Per-frame recording uses **`runBlocking { mutex.withLock }`** from up to three hot-path threads, which **distorts the very measurements it is meant to capture**. VM ingress **conflation is never measured** (`wasConflated` is always `false`). `PipelineTrace` is always allocated on the engine but **`record()` is a no-op in release** (single volatile read). The **`feature/training-debug` module is not build-excluded from release** on either platform; only **runtime DEBUG flags** gate routes and overlays.

---

## Answers: K1–K5

### K1 — `runBlocking` + Mutex per frame from three threads

**Answer**: **CONFIRMED.** Every `TrainingPipelineDiagnostics.record*` function (lines 77–124) wraps counter updates in `runBlocking { mutex.withLock { … } }`. Call sites span three threads per frame on Android debug:

| Thread | Call site | Function |
|--------|-----------|----------|
| `analysisExecutor` (CameraX analyzer) | `CameraXFrameSource.kt:309-316` | `recordCameraFrame` |
| MediaPipe result callback | `CameraXFrameSource.kt:185-198`, `MediaPipePoseDetector.kt:198` | `recordPoseResult`, `recordInferenceStall` |
| `poseFrameWorker` (`Dispatchers.Default`) | `TrainingSessionViewModel.kt:428,443` | `recordVmIngress`, `recordVmProcessed` |

All paths early-return when `isTrainingPipelineDiagnosticsEnabled()` is false (`TrainingPipelineLogger.android.kt:12` → `MovitGeneratedBuildConfig.DEBUG`). **Release builds pay zero cost** from this object.

**Distortion estimate** (theoretical, `NEEDS-DEVICE` to quantify):

- At 25 fps with pose on every accepted camera frame: **~75–100 `runBlocking` acquisitions/sec** across three threads, plus periodic `maybeEmitPeriodic` (also `runBlocking`, `:146-163`).
- Each `runBlocking` schedules a coroutine on the caller thread and blocks until the mutex is free. Under contention, **analyzer and MediaPipe callback threads stall**, directly lowering measured `cam`/`pose` fps in debug logs.
- Mutex is **unnecessary for monotonic counters**; `AtomicInteger` / `AtomicLong` would remove blocking and yield faithful debug readings.
- `PipelineTrace.record` (`PipelineTrace.kt:20-28`) uses the same `runBlocking`+`Mutex` pattern when `PipelineTraceConfig.isEnabled` is true (only tests enable it today).

**Proposed alternative** (suggestion only): lock-free atomics for counters; sample aggregation on a single background dispatcher every 2 s; never block capture/inference threads.

---

### K2 — iOS diagnostics permanently disabled (OQ-04)

**Answer**: **CONFIRMED — hard-disabled, intent undocumented.**

```9:9:kmp-app/core/training-engine/src/iosMain/kotlin/com/movit/core/training/diagnostics/TrainingPipelineLogger.ios.kt
internal actual fun isTrainingPipelineDiagnosticsEnabled(): Boolean = false
```

All `TrainingPipelineDiagnostics` entry points no-op on iOS. `trainingPipelineLog` prints to stdout (`:4-5`) but is never reached because of the guard above. There is **no compile-time or runtime toggle** and **no iOS call sites** for `recordCameraFrame` / `recordPoseResult` in `IosCameraFrameSource` / `IosPoseDetector` (grep confirms zero imports).

**OQ-04 judgment**: **Needs product-owner decision.** Code reads as a deliberate stub (always `false`, no TODO), but no comment documents parity intent. Functionally this is a **platform observability gap**: Android debug can triage pipeline stalls; iOS cannot.

---

### K3 — `PipelineTrace` cost in release

**Answer**: **Low per-call cost when disabled; object always allocated.**

- `MovitTrainingEngine` constructs `val pipelineTrace = PipelineTrace()` for every engine instance (`MovitTrainingEngine.kt:191`).
- `PipelineTraceConfig.isEnabled` defaults to `false` (`PipelineTraceConfig.kt:9`); **no production code calls `setEnabled(true)`** (only `PipelineTraceTest.kt`).
- `record()` (`PipelineTrace.kt:20-22`): one `@Volatile` read + early return when disabled.
- When enabled: **ring buffer** (`ArrayDeque`, capacity 200, `:15-26`) — O(1) append with eviction of oldest; not an unbounded list.
- Production call sites when enabled would be rare events: `drop:ingress_busy` (`MovitTrainingEngine.kt:587`) and phase transitions (`:675`). With `isEnabled=false`, only the volatile check runs on those paths.
- **Residual release cost**: one `PipelineTrace` object (~200-slot deque + mutex) per engine session; negligible memory (~tens of KB) but dead weight if trace is never enabled in production.

---

### K4 — `recordVmIngress(wasConflated = false)` never measures conflation

**Answer**: **CONFIRMED — metric is broken; backlog is misleading.**

```426:429:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt
  fun onPoseFrame(frame: PoseFrame?) {
    if (!_state.value.requiresCamera()) return
    TrainingPipelineDiagnostics.recordVmIngress(wasConflated = false)
    poseFrameChannel.trySend(frame)
```

- Channel is `Channel.CONFLATED` (`TrainingSessionViewModel.kt:173`). `trySend` always succeeds; kotlinx.coroutines does **not** surface whether a prior element was dropped.
- `vmConflated` (`TrainingPipelineDiagnostics.kt:39,112`) is therefore **always 0** in periodic lines.
- `backlog = vmIngress - vmProcessed` (`:200-201`) measures **worker processing lag within the 2 s window**, not channel conflation. Under conflation, ingress still increments but dropped frames never increment any counter.

**Correct measurement design** (suggestion only):

1. **`AtomicBoolean workerInFlight`**: set `true` at start of `processPoseFrameOnWorker`, `false` in `finally`. If `workerInFlight.get()` before `trySend`, increment `vmConflated`.
2. Alternatively, replace `CONFLATED` with capacity-1 `Channel` + explicit drop counting on failed `trySend` (changes backpressure semantics — coordinate with Track C).
3. Expose `Channel` buffer depth is not available on CONFLATED; do not use `vmIngress - vmProcessed` as conflation proxy.

---

### K5 — Measurement protocol deliverable

**Answer**: Full protocol in [`perf-baseline.md`](perf-baseline.md). All numeric cells marked **`NEEDS-DEVICE`** pending on-device capture.

---

## Open Questions

### OQ-04 — iOS diagnostics disabled

| Aspect | Finding |
|--------|---------|
| Code fact | `isTrainingPipelineDiagnosticsEnabled() = false` on iOS (`TrainingPipelineLogger.ios.kt:9`) |
| Parity | No iOS instrumentation hooks in pose-capture iOS sources |
| Intent | **Undocumented** — treat as gap until product confirms deliberate deferral |
| Recommendation | Either wire iOS capture callbacks to diagnostics (parity) or document exclusion in README/runbook |

### OQ-06 — Is `training-debug` excluded from release?

| Component | Android release | iOS release |
|-----------|-----------------|-------------|
| `:feature:training-debug` module | **Compiled in** via `:feature:shell` `implementation` (`shell/build.gradle.kts:45`); **not** `debugImplementation` at shell level | **Compiled in** MovitApp framework (same shell dependency) |
| `:app` direct dependency | `debugImplementation` only (`app/build.gradle.kts:277`) — `TrainingDebugActivity` in `app/src/debug/` | N/A |
| Route gate | `isTrainingDebugLabEnabled()` → `MovitGeneratedBuildConfig.DEBUG` (`TrainingDebugBuild.android.kt:5`) | `Platform.isDebugBinary` (`TrainingDebugBuild.ios.kt:7`) |
| Shell navigation | `PlatformInfo.supportsTrainingDebugLab` (`AndroidPlatformInfo.kt:9`) | `IosPlatformInfo.kt:11` |
| In-session FPS overlay | `isTrainingDebugBuild()` (`TrainingDebugBuild.android.kt:5`, overlay `TrainingDebugOverlay.kt:22`) | Always `false` (`TrainingDebugBuild.ios.kt:3`) |

**Judgment**: **NOT build-excluded from release binaries.** Exclusion is **runtime-only** via `DEBUG` / `isDebugBinary` flags. Release APK/IPA **contains** training-debug bytecode (dead routes when flags false). Profile menu entry hidden when `supportsTrainingDebugLab` is false (`MovitProfileScreen.kt:258-262`). For true exclusion, shell would need `debugImplementation` or a release source-set stub.

---

## PF Judgments (Track K scope)

| PF | Claim | Judgment | Finding |
|----|-------|----------|---------|
| **PF-05** | `runBlocking`+Mutex per frame from 3 threads (debug Android); dead on iOS | **CONFIRMED** | [K-01], [K-02] |
| **PF-06** | `recordVmIngress(wasConflated=false)` always — conflation metric broken | **CONFIRMED** | [K-04], [K-06] |

---

## Findings

### [K-01] `runBlocking`+Mutex on hot-path threads distorts debug pipeline metrics
- **Severity**: P2
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: PF-05
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/diagnostics/TrainingPipelineDiagnostics.kt:77-124`, `kmp-app/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/CameraXFrameSource.kt:309-316`, `kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt:428,443`
- **Evidence**: Each `record*` uses `runBlocking { mutex.withLock { … } }`. Three threads invoke these per frame on Android debug: CameraX `analysisExecutor`, MediaPipe callback (`CameraXFrameSource.kt:185-198`), VM worker (`TrainingSessionViewModel.kt:428,443`). Enabled only when `MovitGeneratedBuildConfig.DEBUG` (`TrainingPipelineLogger.android.kt:12`).
- **Impact**: Debug logcat fps/inference numbers are **optimistic or pessimistic relative to true pipeline behavior** because instrumentation blocks the threads it measures. Estimated **50–500 µs+ per lock** under contention × 3–5 calls/frame × 25 fps = **3.75–62 ms/s of artificial stall** (`NEEDS-DEVICE` for exact figure).
- **Fix-sketch**: Replace mutex counters with `AtomicInteger`/`AtomicLong`; flush to log on a single timer coroutine; remove `runBlocking` from capture/inference paths.
- **Effort**: M
- **Verified-by**: pending

### [K-02] iOS pipeline diagnostics fully disabled with no documented intent
- **Severity**: P3
- **Type**: Architecture
- **Status**: CONFIRMED
- **Related-PF**: PF-05
- **Files**: `kmp-app/core/training-engine/src/iosMain/kotlin/com/movit/core/training/diagnostics/TrainingPipelineLogger.ios.kt:9`
- **Evidence**: `internal actual fun isTrainingPipelineDiagnosticsEnabled(): Boolean = false` — unconditional. No iOS pose-capture imports of `TrainingPipelineDiagnostics`.
- **Impact**: Zero observability for iOS pipeline triage; cross-platform performance comparisons impossible from unified `TrainingPipeline` logs.
- **Fix-sketch**: Document as intentional deferral (OQ-04) or add iOS `record*` hooks in `IosCameraFrameSource`/`IosPoseDetector` with platform logger sink.
- **Effort**: M
- **Verified-by**: pending

### [K-03] `PipelineTrace` is allocated always; `record()` is cheap in release
- **Severity**: P3
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/MovitTrainingEngine.kt:191`, `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/observability/PipelineTrace.kt:20-28`, `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/observability/PipelineTraceConfig.kt:9`
- **Evidence**: Engine always holds `pipelineTrace`. `record()` returns immediately when `!PipelineTraceConfig.isEnabled` (default `false`). When enabled, uses fixed-capacity ring buffer (`capacity=200`, evicts oldest). Production `setEnabled(true)` only in tests.
- **Impact**: Release: ~1 volatile read per `record()` call site (phase change, ingress drop). Negligible CPU; small per-engine heap for deque+mutex.
- **Fix-sketch**: Lazy-allocate `PipelineTrace` only when `PipelineTraceConfig.setEnabled(true)`; or gate engine field behind debug flag.
- **Effort**: S
- **Verified-by**: pending

### [K-04] VM channel conflation never counted — `wasConflated` hardcoded false
- **Severity**: P3
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: PF-06
- **Files**: `kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt:173,428-429`, `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/diagnostics/TrainingPipelineDiagnostics.kt:107-114,200-202`
- **Evidence**: `recordVmIngress(wasConflated = false)` literal. `Channel.CONFLATED` + `trySend` does not report drops. `vmConflated` counter never increments in production.
- **Impact**: Periodic `conflated=` field in logcat is **always absent**; operators cannot detect VM-layer frame drops (backpressure layer 4 per §4).
- **Fix-sketch**: Track `AtomicBoolean workerInFlight` around `processPoseFrameOnWorker`; pass `wasConflated = workerInFlight.get()` to `recordVmIngress`.
- **Effort**: S
- **Verified-by**: pending

### [K-05] `backlog` metric conflates worker lag with channel conflation
- **Severity**: P3
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: PF-06
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/diagnostics/TrainingPipelineDiagnostics.kt:198-201`
- **Evidence**: `val backlog = vmIngress - vmProcessed` appended when `backlog > 0`. Both counters increment once per callback (ingress on `onPoseFrame`, processed on worker entry), not per unique frame. Under CONFLATED drops, ingress still counts dropped replacements.
- **Impact**: Misleading triage: high `backlog` may mean slow worker **or** rapid ingress with silent drops — indistinguishable.
- **Fix-sketch**: Rename to `workerLag` in logs; add separate `conflated` counter per [K-04].
- **Effort**: S
- **Verified-by**: pending

### [K-06] Test formatter drift — `backlog` line missing from `formatTrainingPipelinePeriodicForTest`
- **Severity**: P3
- **Type**: Duplication
- **Status**: CONFIRMED
- **Related-PF**: PF-17
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/diagnostics/TrainingPipelineDiagnostics.kt:200-201` vs `:267-269`
- **Evidence**: Production `buildPeriodicLine` emits `backlog=$backlog` when `vmIngress > vmProcessed`. Test helper `formatTrainingPipelinePeriodicForTest` omits backlog entirely. Test `TrainingPipelineDiagnosticsTest.kt` does not assert backlog.
- **Impact**: Unit tests cannot catch future formatter regressions on VM backlog field.
- **Fix-sketch**: Delegate test formatter to `buildPeriodicLine` or share a single builder; add assertion for backlog when ingress > processed.
- **Effort**: S
- **Verified-by**: pending

### [K-07] `metricsSnapshot()` called every processed frame on diagnostics path (debug amplification)
- **Severity**: P2
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt:443-444,559-572`, `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/MovitTrainingEngine.kt:773-798`
- **Evidence**: `processPoseFrameOnWorker` calls `emitPipelineDiagnostics` every frame (`:443-444`). That calls `engine?.metricsSnapshot()` before `maybeEmitPeriodic` (`:561-562`), which builds full `EngineMetrics` including `lastJointStateInfos` map. `maybeEmitPeriodic` only logs every 2 s but snapshot runs **every processed frame**.
- **Impact**: On Android debug, adds object allocation + map copy every frame on worker thread, **in addition to** [K-01] mutex cost — further distorts debug measurements. Release: `maybeEmitPeriodic` no-ops early but **`metricsSnapshot()` still runs** every processed frame.
- **Fix-sketch**: Gate `emitPipelineDiagnostics` body behind `isTrainingPipelineDiagnosticsEnabled()`; pass only scalar fields needed for periodic line without full snapshot.
- **Effort**: S
- **Verified-by**: pending

### [K-08] `training-debug` module ships in release binaries — runtime flags only
- **Severity**: P3
- **Type**: Architecture
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `kmp-app/feature/shell/build.gradle.kts:45`, `kmp-app/app/build.gradle.kts:277`, `kmp-app/feature/shell/src/commonMain/kotlin/com/movit/feature/shell/MovitInnerHost.kt:328-345`, `kmp-app/shared/src/androidMain/kotlin/com/movit/shared/AndroidPlatformInfo.kt:9`
- **Evidence**: Shell uses unconditional `implementation(project(":feature:training-debug"))`. App adds `debugImplementation` for standalone Activity only. Routes gated by `isTrainingDebugLabEnabled()` / `PlatformInfo.supportsTrainingDebugLab` (DEBUG checks).
- **Impact**: Release APK/IPA binary size includes debug-lab code paths (Compose screens, analyzers). No user-facing entry when DEBUG false, but attack surface / ProGuard keep rules may retain symbols.
- **Fix-sketch**: Move shell dependency to `debugImplementation`; provide no-op stubs in `release` source set for `TrainingDebugRoute` if needed.
- **Effort**: M
- **Verified-by**: pending

### [K-09] `TrainingSessionWriteDiagnostics` — session-scoped upload counters (out of hot path)
- **Severity**: P3
- **Type**: Architecture
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionWriteDiagnostics.kt:21-48`
- **Evidence**: Mutable counters updated only on `recordEnqueue` (upload outcomes). `snapshot()` builds `TrainingSessionWriteStatus` for UX notices. No per-frame or mutex contention.
- **Impact**: None on pipeline fps. Useful for session-end upload triage only.
- **Fix-sketch**: No change required for performance track; keep separate from pipeline diagnostics.
- **Effort**: —
- **Verified-by**: pending

### [K-10] No production caller enables `PipelineTraceConfig` — debug HUD path unimplemented
- **Severity**: P3
- **Type**: Dead-code
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/observability/PipelineTraceConfig.kt:7-14`, `kmp-app/core/training-engine/src/commonTest/kotlin/com/movit/core/training/observability/PipelineTraceTest.kt:12-17`
- **Evidence**: `setEnabled(true)` only in unit tests. `feature/training-debug` does not reference `PipelineTrace` or `pipelineTrace.snapshot()`. Engine exposes public `pipelineTrace` but no UI consumes it in production tree.
- **Impact**: I-10 ring buffer infrastructure exists but delivers no user-visible debug HUD today.
- **Fix-sketch**: Wire `TrainingDebugOverlay` or debug lab to `engine.pipelineTrace.snapshot()` at session start with `setEnabled(true)`.
- **Effort**: M
- **Verified-by**: pending

---

## Coverage Notes

| File | Read | Notes |
|------|------|-------|
| `TrainingPipelineDiagnostics.kt` | Yes | Full |
| `TrainingPipelineLogger.{kt,android,ios}.kt` | Yes | Full |
| `PipelineTrace.kt`, `PipelineTraceConfig.kt` | Yes | Full |
| `TrainingSessionWriteDiagnostics.kt` | Yes | Full |
| `TrainingSessionViewModel.kt` (ingress/diagnostics) | Partial | Lines 167-173, 426-573, 1834-1838 |
| `MovitTrainingEngine.kt` (pipelineTrace) | Partial | Lines 191, 505, 577-588, 672-676, 773+ |
| `CameraXFrameSource.kt` (diagnostics hooks) | Partial | Lines 185-198, 309-335 |
| `MediaPipePoseDetector.kt` | Partial | Lines 192-201 |
| `feature/training-debug/*` | Skim | Build gates, shell routing, `TrainingDebugBuild` |
| `MovitTrainingRoutes.kt`, `TrainingDebugOverlay.kt` | Partial | Debug FPS gating |
| iOS pose-capture diagnostics hooks | Grep | None found |

---

## PF Table (Track K adjudication)

| ID | Judgment | Status | Primary finding |
|----|----------|--------|-----------------|
| PF-05 | **CONFIRMED** | Debug Android: blocking mutex on 3 threads; iOS: dead | [K-01], [K-02] |
| PF-06 | **CONFIRMED** | `wasConflated` always false; backlog misleading | [K-04], [K-05] |

---

*Verified-by: pending on all findings (adversarial review not yet run).*
