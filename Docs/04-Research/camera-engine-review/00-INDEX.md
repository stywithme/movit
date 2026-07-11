# Camera Training Engine — Review Index (`00-INDEX`)

> **Date**: 2026-07-10  
> **Scope**: `kmp-app` pose-capture + training-engine + feature/training (read-only)  
> **Agents**: Tracks A,C,D,E,F,G → `grok-4.5-xhigh`; Tracks B,H,I,J,K + frame-flow → `composer-2.5`; Adversarial P0/P1 → `grok-4.5-xhigh`  
> **Remediation (2026-07-11)**: WP-01…WP-14 applied — see `Camera-Engine-Remediation-Plan.md` §14/§16. B-02 open (M6); K-08 partial (Android debug lab stub).
> **Single consolidated deliverable**: [`CAMERA-ENGINE-COMPREHENSIVE-REVIEW.md`](./CAMERA-ENGINE-COMPREHENSIVE-REVIEW.md)

## 1. All findings (Severity → Track)

| ID | Title | Severity | Type | Status | Effort | Verified-by |
|----|-------|----------|------|--------|--------|-------------|
| E-01 | `assemble` drops all world 3D angles after virtual landmark append | P0 | Correctness | FIXED (WP-02) | S | adversarial-grok-4.5-xhigh |
| J-02 | `MovitTrainingEngine` throws uncaught `error()` on missing pose var... | P0 | Correctness | FIXED (WP-01) | M | adversarial-grok-4.5-xhigh |
| A-05 | TOCTOU recycle race between takeSnapshotJpeg copy and detectAsync | P1 | Concurrency | FIXED (WP-04) | S | adversarial-grok-4.5-xhigh |
| A-07 | bindingInProgress silently drops lens-switch bind — frames can stall | P1 | Concurrency | FIXED (WP-04) | M | adversarial-grok-4.5-xhigh |
| B-01 | iOS ignores throughput analysis resolution (runs at PresetHigh nati... | P1 | Correctness | FIXED (WP-11) | M | adversarial-grok-4.5-xhigh |
| B-02 | Front-camera capture mirroring diverges from Android unmirrored ML ... | P1 | Correctness | NEEDS-DATA | S | adversarial-grok-4.5-xhigh |
| C-01 | Dual-thread `processFrame` race at countdown→training transition | P1 | Concurrency | FIXED (WP-03) | M | adversarial-grok-4.5-xhigh |
| C-02 | `FrameIngressGate` non-atomic / no memory visibility | P1 | Concurrency | FIXED (WP-03) | S | adversarial-grok-4.5-xhigh |
| C-10 | Unsynchronized `SessionSupervisor.processSignal` from Main + Default | P1 | Concurrency | FIXED (WP-03) | M | adversarial-grok-4.5-xhigh |
| E-05 | Shared `PoseFrameAssembler` elbow estimator race / cross-consumer b... | P1 | Concurrency | FIXED (WP-04) | M | adversarial-grok-4.5-xhigh |
| F-01 | Multiple UiState copies per pose frame (TRAINING 2–3, setup 3–4) | P1 | Performance | FIXED (WP-07) | M | adversarial-grok-4.5-xhigh |
| F-02 | Monolithic `collectAsState` → full-screen recomposition at pose rate | P1 | Performance | FIXED (WP-07) | L | adversarial-grok-4.5-xhigh |
| G-01 | Finish can navigate/detach before finalize upload completes | P1 | Correctness | FIXED (WP-05) | M | adversarial-grok-4.5-xhigh |
| G-02 | Preference rebuild replaces engine outside TRAINING and orphans jou... | P1 | Correctness | FIXED (WP-06) | M | adversarial-grok-4.5-xhigh |
| J-01 | `applyFlowExercise` clamps pose variant using previous exercise config | P1 | Correctness | FIXED (WP-01) | S | adversarial-grok-4.5-xhigh |
| J-03 | `validationIssues()` never enforced before engine build | P1 | Correctness | FIXED (WP-01) | S | adversarial-grok-4.5-xhigh |
| A-01 | Per-frame Bitmap allocation without reuse (toBitmap + rotate) *(severity downgraded P1→P2)* | P2 | Performance | FIXED (WP-09) | M | adversarial-grok-4.5-xhigh |
| A-02 | inferenceInFlight held through post-inference assemble/emit | P2 | Performance | FIXED (WP-09) | S | pending |
| A-04 | takeSnapshotJpeg full-frame copy + JPEG on IO; high replay cadence | P2 | Performance | CONFIRMED | M | pending |
| A-06 | dispose() never called; stop() leaves landmarker + executor warm | P2 | Memory | CONFIRMED | S | pending |
| A-08 | pendingProviderReady overwritten — lost bind callback | P2 | Concurrency | FIXED (WP-04) | S | pending |
| A-12 | Five stacked backpressure layers — roles overlap; device proof needed | P2 | Architecture | NEEDS-DATA | M | pending |
| A-13 | persistSnapshot takes two independent JPEGs (full then thumb) | P2 | Correctness | FIXED (WP-09) | S | pending |
| A-14 | Heavy-model warmUp can close live PoseLandmarker under traffic | P2 | Concurrency | FIXED (WP-04) | M | pending |
| A-15 | Post-result path allocates many Landmark lists while gate held | P2 | Performance | CONFIRMED | M | pending |
| B-03 | No inference-in-flight gate on iOS pose detection | P2 | Performance | FIXED (WP-11) | S | pending |
| B-04 | Pose timestamps use wall-clock on iOS vs monotonic uptime on Android | P2 | Correctness | FIXED (WP-11) | S | pending |
| B-05 | Lens-switch hygiene missing `LensSwitchFrameGate` and `resetElbowEs... | P2 | Correctness | FIXED (WP-04) | S | pending |
| B-06 | Swift→Kotlin flat landmark bridge allocates per frame | P2 | Performance | CONFIRMED | M | pending |
| B-08 | iOS MediaPipe warmUp lacks GPU→CPU fallback retry | P2 | Correctness | FIXED (WP-11) | S | pending |
| B-10 | `minPosePresenceConfidence` not configured on iOS MediaPipe options | P2 | Correctness | FIXED (WP-11) | S | pending |
| C-04 | CONFLATED ingress can delay NoPose detection | P2 | Correctness | FIXED (WP-13) | M | pending |
| C-06 | Parallel presence stacks + duplicated NoPose warning handlers | P2 | Architecture | FIXED (WP-13) | M | pending |
| C-07 | Engine `PresenceSupervisorBridge.onNoPoseFrame` unreachable on came... | P2 | Dead-code | FIXED (WP-13) | M | pending |
| C-08 | Elapsed freezes without frames; lens switch can jump elapsed | P2 | Correctness | FIXED (WP-06) | S | pending |
| D-01 | Front-camera path allocates full mirrored PoseFrame every frame | P2 | Performance | FIXED (WP-08) | M | pending |
| D-02 | `checkVisibility` re-runs `evaluateJointVisibility` | P2 | Performance | FIXED (WP-08) | S | pending |
| D-04 | Position validation always runs scene detection even when phase che... | P2 | Performance | FIXED (WP-08) | S | pending |
| D-07 | Triple smoothing stack (MA + phase hysteresis; One-Euro upstream) | P2 | Performance | CONFIRMED (stack) / NEEDS-DATA (latency) | M | pending |
| D-10 | Engine `metricsSnapshot()` allocates on demand; not 3× inside `proc... | P2 | Performance | FIXED (WP-07) | S | pending |
| D-11 | Frame-tied `nowMs` can freeze phase timing when frames stop | P2 | Correctness | CONFIRMED (mechanism) / NEEDS-DATA (user impact) | M | pending |
| E-03 | Virtual landmarks 33/34 survive mirroring; `mirrorLandmarks` is a n... | P2 | Correctness | FIXED (WP-08) | M | pending |
| E-06 | Triple smoothing stack latency unmeasured (PF-19 math) | P2 | Performance | NEEDS-DATA | M | pending |
| E-07 | Visibility threshold split 0.5 vs 0.3 (E5) | P2 | Correctness | FIXED (WP-13) | S | pending |
| E-08 | Elbow estimator not reset on new exercise (E4) | P2 | Correctness | FIXED (WP-04) | S | pending |
| E-10 | Overlay FILL_CENTER + front mirror aligned in code; device aspect m... | P2 | Correctness | NEEDS-DATA | M | pending |
| E-11 | 3D/2D mode switch has no hysteresis (E1 residual) | P2 | Correctness | FIXED (WP-02) | M | pending |
| F-03 | `buildSkeletonRomIndicators` recomputed every frame on main via `re... | P2 | Performance | FIXED (WP-07) | S | adversarial-grok-4.5-xhigh |
| F-04 | `FeedbackSignal` allocated before scheduler cooldown drop | P2 | Performance | CONFIRMED | S | pending |
| F-06 | Setup path double `refreshSkeletonOverlay` + triple `metricsSnapshot` | P2 | Performance | FIXED (WP-07) | S | pending |
| F-07 | Skeleton Canvas redraws without `drawWithCache`; ARC ROM ~90 draw o... | P2 | Performance | FIXED (WP-10) | M | pending |
| G-03 | Immediate engine rebuild on first preferences emission | P2 | Performance | FIXED (WP-06) | S | pending |
| G-04 | Session quality counters not reset per exercise/set | P2 | Correctness | FIXED (WP-06) | S | pending |
| G-05 | Background phase-timeout restart never armed in production | P2 | Architecture | FIXED (WP-06) | S (doc) / M (wire limits) | pending |
| G-08 | `onCleared` omits `engine.stop` and pending capture join | P2 | Memory | FIXED (WP-05) | S | pending |
| G-09 | Supervisor action buffer can drop under ValidatePose spam | P2 | Concurrency | FIXED (WP-03) | S | pending |
| G-11 | Duplicate NoPose warning paths (overlap with Track C / PF-16) | P2 | Duplication | FIXED (WP-13) | M | pending |
| H-02 | Post-training report built twice on every exercise finalize | P2 | Performance | FIXED (WP-05) | S | pending |
| H-05 | Report cache LRU (10) can evict sibling set reports before merge | P2 | Correctness | FIXED (WP-05) | M | pending |
| H-06 | Cross-set aggregator uses score-only best/worst; omits rich analysi... | P2 | Correctness | CONFIRMED | M | pending |
| H-07 | Peak JPEG capture copies bitmap twice per peak (full + thumb) on IO... | P2 | Performance | FIXED (WP-09) | M | pending |
| I-06 | Duplicate NoPose feedback — supervisor path live, presence path unr... | P2 | Duplication | FIXED (WP-13) | S | pending |
| I-07 | `evaluateJointVisibility` invoked twice per training frame | P2 | Duplication | FIXED (WP-08) | S | pending |
| J-04 | Sync parser silently drops malformed exercise JSON | P2 | Architecture | FIXED (WP-01) | S | pending |
| J-05 | `JointEvaluator` builds state messages every frame for all joints | P2 | Performance | FIXED (WP-08) | M | pending |
| J-08 | Visibility threshold defaults diverge (0.3f vs 0.5f) | P2 | Correctness | FIXED (WP-13) | S | pending |
| K-01 | `runBlocking`+Mutex on hot-path threads distorts debug pipeline met... | P2 | Performance | FIXED (WP-14) | M | pending |
| K-07 | `metricsSnapshot()` called every processed frame on diagnostics pat... | P2 | Performance | FIXED (WP-07) | S | pending |
| A-03 | frameCameraState entries can leak on dropped/error frames | P3 | Memory | FIXED (WP-04) | S | pending |
| A-09 | providerInitializing remains true after successful provider init | P3 | Architecture | FIXED (WP-04) | S | pending |
| A-10 | postDelayed widest-zoom retries guarded by camera identity | P3 | Correctness | CONFIRMED | S | pending |
| A-11 | Dual imageProxy.close paths are defensive, not harmful double-close... | P3 | Correctness | CONFIRMED | S | pending |
| A-16 | AndroidPoseRefiner is permanently unavailable (dead refine path) | P3 | Dead-code | FIXED (WP-12) | S | pending |
| B-07 | Training pipeline diagnostics permanently disabled on iOS | P3 | Architecture | FIXED (WP-11) | S | pending |
| B-09 | `modelType` parameter ignored on iOS training camera host | P3 | Architecture | CONFIRMED | M | pending |
| B-11 | iOS debug FPS path exists but `isTrainingDebugBuild()` is always false | P3 | Dead-code | CONFIRMED | S | pending |
| C-03 | `ProcessFrame` action is TRAINING-only; setup uses dead `ValidatePo... | P3 | Architecture | FIXED (WP-03) | S | pending |
| C-05 | Debug Compose state written from MediaPipe callback thread | P3 | Concurrency | FIXED (WP-12) | S | pending |
| C-09 | `recordVmIngress(wasConflated = false)` always — conflation metric ... | P3 | Performance | FIXED (WP-14) | S | pending |
| D-05 | Dead fields `executionStartMs` and `lastSmoothedAngles` | P3 | Dead-code | FIXED (WP-12) | S | pending |
| D-06 | `MainPathFrameResult` carries unused `rawTrackedAngles` / result-le... | P3 | Dead-code | FIXED (WP-08) | S | pending |
| D-08 | `ensureAppended` second call per frame is a no-op when assemble alr... | P3 | Performance | CONFIRMED | S | pending |
| D-09 | Virtual landmarks 33/34 survive limb swap without corruption | P3 | Correctness | CONFIRMED (safe) | S | pending |
| D-12 | `SessionOrchestrator.snapshot()` unused in production | P3 | Dead-code | CONFIRMED | S | pending |
| E-02 | `ensureAppended` second call per frame is a cheap no-op (PF-10 part 1) | P3 | Performance | CONFIRMED | S | pending |
| E-09 | One-Euro equal/backward timestamp edges uncovered (E6) | P3 | Correctness | FIXED (WP-02 (partial)) | S | pending |
| F-05 | Redundant `metricsSnapshot()` in `maybeDeliverRandomMessage` | P3 | Performance | FIXED (WP-07) | S | pending |
| F-08 | High-frequency UiState fields updated but not composed (`glassMessa... | P3 | Architecture | FIXED (WP-07) | M | pending |
| G-06 | Triple stop on completion path; summary duration not zeroed | P3 | Architecture | FIXED (WP-06) | S | pending |
| G-07 | `stopAndFinalize()` has zero callers | P3 | Dead-code | FIXED (WP-06) | S | pending |
| G-10 | Rest timer 1s delay loop drift | P3 | Performance | CONFIRMED | S | pending |
| H-01 | MotionRecorder memory is capped per rep, not linear in session fram... | P3 | Memory | CONFIRMED | S | pending |
| H-03 | `syncFrameEvidenceToWriteHooks` duplicated in cache and upload paths | P3 | Duplication | FIXED (WP-05) | S | pending |
| H-04 | WorkoutExecutionBatchCoordinator removed; immediate per-set upload | P3 | Architecture | FIXED (WP-12) | S | pending |
| H-08 | Replay sampler can register up to 16 JPEGs/rep × 10 tracked reps on... | P3 | Memory | CONFIRMED | S | pending |
| H-09 | Journal checkpoint on every completed rep may amplify disk I/O | P3 | Performance | CONFIRMED | M | pending |
| I-01 | `LiveExerciseRunner` has no production callers | P3 | Dead-code | CONFIRMED | S | pending |
| I-02 | Dead engine fields `executionStartMs` and `lastSmoothedAngles` | P3 | Dead-code | FIXED (WP-12) | S | pending |
| I-03 | `MainPathFrameResult` echo fields never read from pipeline output | P3 | Dead-code | FIXED (WP-08) | S | pending |
| I-04 | WS-0 stub camera/pose classes unused | P3 | Dead-code | FIXED (WP-12) | S | pending |
| I-05 | Diagnostics periodic formatter duplicated and drifted | P3 | Duplication | FIXED (WP-14) | S | pending |
| I-08 | `EngineMetrics.positionErrorCount` is a redundant derived field | P3 | Dead-code | FIXED (WP-07) | S | pending |
| I-09 | `TrainingSessionState` snapshot API unused in production | P3 | Dead-code | CONFIRMED | S | pending |
| I-10 | Report builders V1/V2 — intentional delegation, not dead duplication | P3 | Architecture | REFUTED (as "dead duplicate") | S | pending |
| I-11 | `PoseDetector.buildPoseFrame` implemented but never invoked | P3 | Dead-code | FIXED (WP-12) | M (interface change across modules) | pending |
| I-12 | `SupervisorAction.ValidatePose` emitted but ignored by VM | P3 | Dead-code | FIXED (WP-03) | S | pending |
| I-13 | `EngineMetrics.isInStartPosition` populated but never consumed | P3 | Dead-code | FIXED (WP-07) | S | pending |
| I-14 | `StartPoseGate.boundaryBuffer` stored but unused | P3 | Dead-code | FIXED (WP-12) | S | pending |
| I-15 | `PositionValidator.resolvedPosition` deprecated legacy accessor | P3 | Dead-code | FIXED (WP-12) | S | pending |
| J-06 | VM `submitJointStateMessage` linear-scans `trackedJoints` | P3 | Performance | FIXED (WP-08) | S | pending |
| J-07 | Triplicated rep-timing defaults (`ExerciseConfigDefaults` vs `Timin... | P3 | Duplication | FIXED (WP-13) | S | pending |
| J-09 | `sanitizeDefaults()` is a no-op structural pass | P3 | Architecture | FIXED (WP-01 (partial)) | M | pending |
| J-10 | `getBySlug` uses bounded LRU (8) — adequate for typical session | P3 | Performance | CONFIRMED | S | pending |
| K-02 | iOS pipeline diagnostics fully disabled with no documented intent | P3 | Architecture | FIXED (WP-11) | M | pending |
| K-03 | `PipelineTrace` is allocated always; `record()` is cheap in release | P3 | Performance | FIXED (WP-14) | S | pending |
| K-04 | VM channel conflation never counted — `wasConflated` hardcoded false | P3 | Correctness | FIXED (WP-14) | S | pending |
| K-05 | `backlog` metric conflates worker lag with channel conflation | P3 | Correctness | FIXED (WP-14) | S | pending |
| K-06 | Test formatter drift — `backlog` line missing from `formatTrainingP... | P3 | Duplication | FIXED (WP-14) | S | pending |
| K-08 | `training-debug` module ships in release binaries — runtime flags only | P3 | Architecture | PARTIAL (WP-14) | M | pending |
| K-09 | `TrainingSessionWriteDiagnostics` — session-scoped upload counters ... | P3 | Architecture | CONFIRMED | — | pending |
| K-10 | No production caller enables `PipelineTraceConfig` — debug HUD path... | P3 | Dead-code | CONFIRMED | M | pending |
| D-03 | Mirrored landmarks + `isFrontCamera=true` desync `left_elbow` angle... *(REFUTED — PF-11; see adversarial-verification.md)* | P1 | Correctness | REFUTED (WP-08 (contract)) | M | adversarial-grok-4.5-xhigh |
| E-04 | PF-11 double-mirror: REFUTED under current landmark no-op; undocume... | P2 | Architecture | REFUTED (WP-08 (contract)) | M | pending |

**Total findings**: 123

## 2. PF-01 → PF-25 verdicts

| ID | Verdict | Finding(s) | Notes |
|----|---------|------------|-------|
| PF-01 | CONFIRMED | A-01 | Bitmap alloc/frame; severity P2 after adversarial |
| PF-02 | CONFIRMED | A-04/A-05/A-13/H-07 | Snapshot copy+JPEG; TOCTOU P1 |
| PF-03 | CONFIRMED | A-03 | frameCameraState leak on drop |
| PF-04 | CONFIRMED | A-02/A-15 | inferenceInFlight held through post-path |
| PF-05 | CONFIRMED | K-01/K-02/B-07 | runBlocking debug Android; dead iOS |
| PF-06 | CONFIRMED | C-09/K-04 | wasConflated always false |
| PF-07 | CONFIRMED | C-01/C-02/C-10 | dual-thread processFrame at transition |
| PF-08 | CONFIRMED | C-05 | debug Compose write off-main |
| PF-09 | CONFIRMED | D-01 | mirrored() full copy front camera |
| PF-10 | CONFIRMED (cheap)× / REFUTED (33/34 corrupt) | D-08/D-09/E-02/E-03 | double ensureAppended cheap; midpoints OK |
| PF-11 | REFUTED | E-04 / D-03 refuted | mirrorLandmarks no-op; L/R consistent via angles+flag |
| PF-12 | CONFIRMED | F-05/F-06/D-10 | metricsSnapshot multi-call in VM |
| PF-13 | CONFIRMED | F-01/F-02 | UiState updates + monolithic collect; fps NEEDS-DATA |
| PF-14 | CONFIRMED | I-02/D-05 | executionStartMs / lastSmoothedAngles dead |
| PF-15 | CONFIRMED | I-01 | LiveExerciseRunner test-only |
| PF-16 | CONFIRMED (arch) | C-06/C-07/I-06/G-11 | layers+dup handlers; dual warn largely prevented |
| PF-17 | CONFIRMED | I-05/K-06 | test formatter missing backlog |
| PF-18 | CONFIRMED | E-05 | PoseFrameAssembler singleton elbow race |
| PF-19 | CONFIRMED stack / NEEDS-DATA latency | D-07/E-06 | One-Euro→MA→hysteresis |
| PF-20 | CONFIRMED | G-02/G-03 | prefs rebuild; orphan journal outside TRAINING |
| PF-21 | CONFIRMED | C-08/G-10 | elapsed freeze / rest delay drift |
| PF-22 | NEEDS-DATA | A-12 | 5 backpressure layers — device proof |
| PF-23 | CONFIRMED | A-10 | postDelayed zoom; identity guard OK practically |
| PF-24 | CONFIRMED | A-06 | dispose never called; warm after stop |
| PF-25 | CONFIRMED | A-11 | dual close paths defensive |

## 3. OQ-01 → OQ-07

| ID | Question | Answer |
|----|----------|--------|
| OQ-01 | UiState monolith including landmarks | Migration debt with documented R1 overlay split preferred (Track F). Needs product/eng decision to schedule. |
| OQ-02 | Triple smoothing required for legacy? | Stack CONFIRMED; latency NEEDS-DATA. Measure before simplifying. |
| OQ-03 | Android/iOS parity bar | Code aims behavioral training parity (shared assembler/One-Euro) not numeric capture parity. Product must define bar. |
| OQ-04 | iOS diagnostics off | Hardcoded false stub; no documented intent. Product decision needed. |
| OQ-05 | Keep detector/executor warm after stop() | Likely intentional for fast re-entry (Track A). Confirm policy + add explicit dispose on process death path. |
| OQ-06 | training-debug excluded from release? | NO — module ships via shell; gated only by DEBUG/runtime flags (Track K). |
| OQ-07 | LiveExerciseRunner future or leftover? | Phase 07 WS-2 leftover; production uses MovitTrainingEngine (Track I). |

## 4. Top-10 Remediation

| # | IDs | Sev | Effort | Action | Dependencies |
|---|-----|-----|--------|--------|--------------|
| 1 | J-02 + J-01 | P0/P1 | S–M | Fix pose-variant resolve order then replace error() with safe fallback | J-01 before J-02 |
| 2 | E-01 | P0 | S | Fix assemble world-size gate (compare to raw 33 or pad world) | Unblocks true 3D angles |
| 3 | C-01 + C-02 + C-10 | P1 | M | Single-thread engine ingress; AtomicBoolean gate; serialize supervisor | Do together |
| 4 | G-01 | P1 | M | Block Finish/detach until finalizeUpload completes | Independent |
| 5 | G-02 | P1 | M | Defer prefs rebuild until idle; re-wire isRunning/journal | Independent |
| 6 | A-05 | P1 | S | Copy bitmap under same lock as recycle | Independent |
| 7 | A-07 | P1 | M | Queue pending lens facing when bindingInProgress | Independent |
| 8 | B-01 | P1 | M | Honor analysisWidth/Height on iOS session preset | Parity |
| 9 | F-02 | P1 | L | Split overlay StateFlow from session UiState (R1) | Depends on OQ-01 |
| 10 | J-03 | P1 | S | Enforce validationIssues() before buildEngine | Pairs with J-02 |

## 5. Coverage / gaps

- Tracks A–K delivered; `01-frame-flow-verified.md` and `perf-baseline.md` (NEEDS-DEVICE) present.
- Adversarial pass covered all P0/P1 findings (including E-05 / F-01 follow-up).
- Device measurements (fps/GC/RSS) not run in this environment — see `perf-baseline.md`.
- Brief H4 `WorkoutExecutionBatchCoordinator` path is **gone** in current tree (Track H).
- Not every `.kt` line-audited; Track I pattern-swept 341 files.

## Artifact map

| File | Role |
|------|------|
| `CAMERA-ENGINE-COMPREHENSIVE-REVIEW.md` | **Single consolidated report (user deliverable)** |
| `00-INDEX.md` | This index |
| `01-frame-flow-verified.md` | Verified frame journey + threading |
| `perf-baseline.md` | Measurement protocol (NEEDS-DEVICE) |
| `adversarial-verification.md` | P0/P1 refute pass |
| `track-A`…`track-K` | Per-track deep dives |
