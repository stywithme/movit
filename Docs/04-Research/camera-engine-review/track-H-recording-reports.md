# Track H — Recording, Reports & Uploads

> **Scope**: `MotionRecorder`, report builders, frame capture coordinators, write hooks, upload/cache paths in `feature/training` and `core/training-engine/report/`.  
> **Mode**: Read-only review. **Date**: 2026-07-10.  
> **Brief**: `Camera-Engine-Review-Brief.md` §6 Track H, §7 PF-02 (consumer half), §8.

---

## Summary Answers (H1–H7)

| ID | Question | Verdict |
|---|---|---|
| **H1** | Memory growth over long session | **Bounded, not linear in frame count.** `MotionRecorder` retains at most `MAX_FRAMES_PER_REP` (300) in-flight samples plus `MAX_REPS` (100) aggregated `RepMetricsData`. ~13,500 offered frames ≈ **&lt;100 KB** retained journal RAM (estimate below), not MB. |
| **H2** | Peak/replay capture timing, thread, in-memory JPEGs | Peak on `Phase.BOTTOM`, joint WARNING/DANGER, errors, hold every 5 s; replay every **180 ms** during active rep. JPEG encode runs on **`Dispatchers.IO`**; coordinator jobs use `viewModelScope` (main-started, suspends to IO). Metadata only in RAM; JPEG bytes transient during persist. |
| **H3** | Double report build | **CONFIRMED** — `buildPostTrainingReport` invoked in both `cachePostTrainingReport` and `enqueueUpload` with identical inputs per exercise/set finalize. |
| **H4** | Batch vs direct upload | **`WorkoutExecutionBatchCoordinator` absent** (referenced only in stale `iosApp/kmp-build-inputs.xcfilelist`). Explore/planned paths enqueue **immediately** via `enqueueExecutionUpload`. Brief `flushExploreBatchIfNeeded` / silent null `workoutGroupId` return **not present** in current VM. |
| **H5** | Rekey / cache races | Per-set explore caches report **before** upload; `rekeyPostTraining` only when server id ≠ `upload.id` (comment: usually no-op). Workout-run UI id deferred to `finalizeWorkoutRun`. LRU cap (10) can evict sibling set reports. |
| **H6** | Multi-set aggregation correctness | **`accumulateDayReport`** (session totals) covered by `PlannedMultiSetReportTotalsTest`. **Cross-set rich merge** (`MovitPostTrainingReportCrossSetAggregator`) covered by `MultiSetRichReportCrossSetIntegrationTest` for best/worst/timeline/set summaries; gaps for merged danger/error sections and BuilderV2 worst-rep heuristic. |
| **H7** | Duplicate `syncFrameEvidenceToWriteHooks` | **CONFIRMED** — called at start of both finalize paths; `captures()` / `clips()` copy lists each call. |

---

## H1 — Memory Estimate (Long Session)

### What is stored per frame

`onMotionFrameRecorded` fires when `shouldTrackState` is true (`MovitTrainingEngine.kt:718-730`). `MotionRecorder.record` (`MotionRecorder.kt:56-96`):

- Converts `Map<String, Double>` → `ShortArray` (angles ×10, capped 0–1800).
- Optional `ByteArray` joint states with **run-length-style dedup** (`states == lastStates` → store `null`).
- Appends `FrameSample` to **`currentRepBuffer` only** (not a session-long list).

On `finalizeRep` (`MotionRecorder.kt:105-178`), buffer frames are **discarded** after metrics extraction; only `RepMetricsData` is appended to `completedRepMetrics`.

### Caps

```361:362:kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/journal/MotionRecorder.kt
        const val MAX_FRAMES_PER_REP = 300
        const val MAX_REPS = 100
```

Overflow: drop oldest frame in current rep (`removeAt(0)`, `MotionRecorder.kt:67-71`). Reps beyond 100: buffer cleared, rep ignored (`MotionRecorder.kt:114-117`).

`SessionJournalSnapshot` explicitly stores **metrics only, no raw frames** (`MotionDataModels.kt:162-163`).

### Scenario: 3 exercises × 3 sets × 60 s @ 25 fps ≈ 13,500 frames offered

Assume 8 tracked joints, ~10 reps/set, ~75 frames/rep average:

| Structure | Count | Bytes (est.) |
|---|---|---|
| `currentRepBuffer` (peak) | ≤300 `FrameSample` | 300 × (~48 obj + 16 array hdr + 16 angles + 8 states) ≈ **26 KB** |
| `completedRepMetrics` | ≤100 reps × ~200 B | **~20 KB** |
| Counters / coverage | scalars | &lt;1 KB |
| **Total MotionRecorder retained** | | **~50 KB** (order of magnitude) |

**Not retained**: 13,500 frame maps from engine — transient per-frame allocations (`anglesToShortArray`, `MotionDataModels.kt:196-212`) create GC churn at 25 fps but do not accumulate.

**Separate from journal**: frame-capture JPEG files on disk (H2), post-training report objects in LRU cache (≤10 reports, `TrainingSessionReportCache.kt:15-16`), and `accumulatedDayReport` session aggregate.

---

## H2 — Peak Capture & Replay

### Triggers

| Event | Handler | Capture type |
|---|---|---|
| `Phase.BOTTOM` | `TrainingFrameCaptureCoordinator.onPhaseChanged:50-58` | `PEAK_FRAME` (1/rep/set) |
| `JointState.DANGER` / `WARNING` | `onJointState:68-84` | `DANGER_FRAME` / `ERROR_FRAME` |
| `JointError` | `onJointError:87-105` | danger or error |
| Hold elapsed ≥5 s | `onHoldStatus:107-121` | `HOLD_SAMPLE` (max 3/set) |
| Counted rep complete | `onRepCompleted:123-127` | upgrades peak → `BEST_REP` (max 3/set) |
| Active rep | `startReplaySampler` loop `TrainingFrameCaptureCoordinator.kt:129-139` | replay JPEG every **180 ms** |

Limits: `MovitPeakFrameCaptureManager` — `MAX_DANGER_FRAMES=6`, `MAX_HOLD_SAMPLES=3`, `MAX_BEST_REPS=3`, error cooldown 2 s (`MovitPeakFrameCaptureManager.kt:139-145`). Replay: `MAX_FRAMES_PER_REP=16`, `MAX_TRACKED_REPS=10` (`MovitRepReplaySampler.kt:69-73`).

### Threading (PF-02 consumer)

```186:201:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingFrameCaptureCoordinator.kt
        val job = scope.launch {
            val persisted = snapshotPort.persistSnapshot(sessionId, captureId) ?: return@launch
            manager.tryRegister(...)
        }
```

- Coordinator `scope` = `viewModelScope` (`TrainingSessionViewModel.kt:1713-1718`) — coroutine **starts** on main; `persistSnapshot` suspends to IO.
- Android port:

```17:31:kmp-app/feature/training/src/androidMain/kotlin/com/movit/feature/training/AndroidTrainingFrameSnapshotPort.kt
    ): PersistedFrameSnapshot? = withContext(Dispatchers.IO) {
        val fullJpeg = detector.takeSnapshotJpeg(FULL_MAX_DIMENSION, FULL_JPEG_QUALITY) ?: return@withContext null
        val thumbJpeg = detector.takeSnapshotJpeg(THUMB_MAX_DIMENSION, THUMB_JPEG_QUALITY) ?: fullJpeg
```

- `takeSnapshotJpeg` copies `lastFrameBitmap`, optionally scales, compresses (`MediaPipePoseDetector.kt:233-241`). **Two** snapshot calls per peak (720 px + 200 px thumb).

**In-memory JPEG count before write**: 0 held in coordinator; 1–2 `ByteArray` transient on IO thread per job. `MovitPeakFrameCaptureManager` stores **paths + metadata** only (`MovitPeakFrameCaptureManager.kt:76-91`).

**Contention**: `takeSnapshotJpeg` reads `lastFrameBitmap` under `bitmapLock` while MediaPipe callback may write — short lock; encode off analysis thread. Replay at 180 ms during rep can issue **~5–6 IO jobs/s** if port available (`TrainingFrameCaptureCoordinatorTest` confirms burst collection).

iOS parity: same dimensions/quality, `Dispatchers.IO` (`IosTrainingFrameSnapshotPort.kt:35-63`).

---

## H3 — Double Report Build

```1251:1287:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt
  private suspend fun cachePostTrainingReport(...) {
    syncFrameEvidenceToWriteHooks()
    val report = writeHooks.buildPostTrainingReport(...)
    TrainingSessionReportCache.put(...)
    ...
  }

  private suspend fun enqueueUpload(...) {
    syncFrameEvidenceToWriteHooks()
    val postReport = config?.let {
      writeHooks.buildPostTrainingReport(...)  // same args
    }
```

Both paths run sequentially in `finalizeCurrentExercise` (`:1073-1075`) and `stopAndFinalize` (`:662-663`).

`writeHooks.buildPostTrainingReport` → `MovitPostTrainingReportBuilder.build` → when `repDetails` non-empty, **`MovitPostTrainingReportBuilderV2.build`** (`MovitPostTrainingReport.kt:246-258`) — multiple passes over reps (danger, perfect, best, worst, errors, timeline, tips).

**Cost**: For typical 8–15 reps, CPU cost is likely **single-digit ms** per build (NEEDS-DATA on device). At 100 reps, V2 work scales ~O(reps × sections) — **duplication is wasteful but not hot-path** (once per set finalize). Caching first build and reusing for upload would halve work (Fix-sketch in [H-02]).

---

## H4 — Upload Path (Batch vs Direct)

Current code at `enqueueUpload:1288-1295`:

```1288:1295:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt
    // P1.7: enqueue immediately (including Explore) — no RAM batch.
    val result = writeHooks.enqueueExecutionUpload(
      upload = upload,
      context = uploadContext?.context,
      workoutGroupId = uploadContext?.workoutGroupId,
      ...
```

`TrainingSessionWriteHooks.enqueueExecutionUpload` → `TrainingSessionWriteCoordinator.uploadWorkoutExecution` (`TrainingSessionWriteHooks.kt:139-155`, `TrainingSessionWriteCoordinator.kt:42-58`) with optional `legacyReport` JSON.

**`WorkoutExecutionBatchCoordinator.kt`**: listed in `iosApp/kmp-build-inputs.xcfilelist:1051` but **file missing** from workspace — dead build input.

Brief concern `flushExploreBatchIfNeeded` + silent return when `workoutGroupId == null`: **not found** in `TrainingSessionViewModel.kt`. `workoutGroupId` is passed through; null is valid optional field on upload request.

---

## H5 — Rekey & Cache Semantics

Flow per set (explore / single exercise):

1. `cachePostTrainingReport` → `put(upload.id, report, sessionExerciseKey, setNumber)` (`:1263-1268`)
2. `markReportAvailable(upload.id)` if not workout run (`:1269-1271`)
3. `enqueueUpload` → on success, `rekeyPostTraining` only if `reportId != upload.id` (`:1302-1304`); then `put` again with server id (`:1306-1311`)

`rekeyPostTraining` moves LRU + disk index (`TrainingSessionReportCache.kt:67-80`). Test: `TrainingSessionReportCacheTest.rekeyPostTraining_movesReportToServerId`.

**Race notes**:

- UI can navigate to report on `upload.id` before upload completes — report object already cached (step 1).
- Workout-run sessions **skip** per-set `markReportAvailable`; id set once in `finalizeWorkoutRun` (`:1193`) after all sets — avoids premature navigation to partial session report.
- `reloadForNextFlowItem` replaces `writeHooks` and `frameCaptureCoordinator` (`:1336-1338`) — old capture lists not leaked into next set; new hooks start with empty `peakFrameCaptures` until next `syncFrameEvidenceToWriteHooks`.
- LRU `MAX_REPORTS=10` may evict earlier set uploads for same session if &gt;10 exercises/sets cached — `getMergedForDisplay` may lose siblings (`TrainingSessionReportCacheTest.put_evictsOldestWhenOverCapacity`).

---

## H6 — Multi-Set Aggregation (Tests)

### Session-level (`accumulateDayReport`)

`TrainingSessionViewModel.kt:1122-1152` merges via `MovitSessionReportBuilder.mergeExercise`. Verified:

- `PlannedMultiSetReportTotalsTest.threeSets_sameExercise_totalsThreeOfThree` — `totalSetsCompleted`, `totalReps`, single `exerciseReports` entry with `setsCompleted=3`.

### Cross-set post-training display merge

`TrainingSessionReportCache.getMergedForDisplay` → `MovitPostTrainingReportCrossSetAggregator.merge` (`TrainingSessionReportCache.kt:43-52`).

Verified by tests:

- `MultiSetRichReportCrossSetIntegrationTest.threeSets_merge_producesCrossSetBestWorstAndFormBySet` — best set 2 rep 2 (95), worst set 3 rep 1 (65).
- `TrainingSessionReportCacheTest.getMergedForDisplay_mergesIndexedSiblingSetReports` — cache index by `sessionExerciseKey` + `setNumber`.

**Gaps (not covered by tests / merge logic)**:

- Aggregator picks best/worst timeline entries by **`score` only** (`MovitPostTrainingReportCrossSetAggregator.kt:37-38`), not `MovitPostTrainingReportBuilderV2.findWorstRep` composite fitness (`MovitPostTrainingReportBuilderV2.kt:258-270`).
- Does not merge `dangerAlerts`, `perfectMoments`, `errorAnalysis`, `improvementTips` — only timeline flags, `bestReps`/`worstRep`, `setSummaries`, weighted `averageScore` (`:65-73`).
- `accumulateDayReport` vs per-set `MovitPostTrainingReport` are **different report types** (session vs execution) — no automatic cross-link except cache merge on read.

---

## H7 — Duplicate `syncFrameEvidenceToWriteHooks`

```1246:1248:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt
  private fun syncFrameEvidenceToWriteHooks() {
    writeHooks.peakFrameCaptures = frameCaptureCoordinator.captures()
    writeHooks.repReplayClips = frameCaptureCoordinator.replayClips()
```

```148:151:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingFrameCaptureCoordinator.kt
    fun captures(): List<MovitPeakFrameCapture> =
        manager.captures().filter { it.setNumber == currentSetNumber }

```

`manager.captures()` = `captures.toList()` (`MovitPeakFrameCaptureManager.kt:126`). Called twice per finalize → **two list copies** of metadata (typically &lt;30 entries, negligible vs double V2 build).

---

## PF-02 Judgment (Consumer Half — Track H)

| Aspect | Status | Evidence |
|---|---|---|
| Full bitmap copy per snapshot | **CONFIRMED** | `MediaPipePoseDetector.kt:234-235` `source.copy(...)` |
| JPEG encode thread | **IO, not analysis** | `AndroidTrainingFrameSnapshotPort.kt:20`, `IosTrainingFrameSnapshotPort.kt:35` |
| Call frequency | **Bounded per rep + replay burst** | Peak manager limits; replay 180 ms (`MovitRepReplaySampler.kt:70`) |
| Competes with pose hot path | **Low direct contention**; lock on `lastFrameBitmap` | Encode off callback thread; replay can stack multiple IO jobs |
| In-RAM JPEG retention | **REFUTED** (long-lived) | Paths only in manager; bytes written then released |

**Overall PF-02 (consumer)**: **CONFIRMED** as P2 performance concern (duplicate bitmap copy per peak = 2× per capture, replay adds sustained IO). Not P0/P1 unless replay + dense errors on low-end device (NEEDS-DATA).

---

## Findings

### [H-01] MotionRecorder memory is capped per rep, not linear in session frame count
- **Severity**: P3
- **Type**: Memory
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/journal/MotionRecorder.kt:56-96`, `MotionRecorder.kt:105-178`, `MotionRecorder.kt:361-362`, `MotionDataModels.kt:162-163`
- **Evidence**: Frames live only in `currentRepBuffer` until `finalizeRep` clears them; `MAX_FRAMES_PER_REP=300`, `MAX_REPS=100`. Comment at `MotionRecorder.kt:9-10`: "metrics only, no raw frame persistence."
- **Impact**: 13,500-frame session retains ~50 KB journal RAM (estimate above), not multi-MB linear growth. GC pressure remains from per-frame `ShortArray` allocation.
- **Fix-sketch**: Document bounded model; optional object pooling for `FrameSample` if profiling shows GC spikes.
- **Effort**: S
- **Verified-by**: pending

### [H-02] Post-training report built twice on every exercise finalize
- **Severity**: P2
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt:1251-1287`, `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/report/MovitPostTrainingReport.kt:246-258`
- **Evidence**: `cachePostTrainingReport` and `enqueueUpload` each call `writeHooks.buildPostTrainingReport` with identical parameters before cache put and outbox enqueue.
- **Impact**: 2× CPU for `MovitPostTrainingReportBuilderV2` (7+ sections × reps) at end of each set; noticeable only for high-rep sets or low-end devices (NEEDS-DATA).
- **Fix-sketch**: Build once in `finalizeCurrentExercise`, pass `MovitPostTrainingReport` to cache + `enqueueExecutionUpload(legacyReport=…)`.
- **Effort**: S
- **Verified-by**: pending

### [H-03] `syncFrameEvidenceToWriteHooks` duplicated in cache and upload paths
- **Severity**: P3
- **Type**: Duplication
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt:1253`, `:1276`, `TrainingFrameCaptureCoordinator.kt:148-151`, `MovitPeakFrameCaptureManager.kt:126`
- **Evidence**: Both finalize functions call `syncFrameEvidenceToWriteHooks()`; `captures()` allocates new filtered list via `toList()`.
- **Impact**: Minor — small list copies vs double report build.
- **Fix-sketch**: Single sync + one `buildPostTrainingReport` before cache/upload.
- **Effort**: S
- **Verified-by**: pending

### [H-04] WorkoutExecutionBatchCoordinator removed; immediate per-set upload
- **Severity**: P3
- **Type**: Architecture
- **Status**: CONFIRMED (brief outdated)
- **Related-PF**: —
- **Files**: `kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt:1288-1295`, `iosApp/kmp-build-inputs.xcfilelist:1051`
- **Evidence**: Comment "P1.7: enqueue immediately (including Explore) — no RAM batch." No `WorkoutExecutionBatchCoordinator.kt` in repo; xcfilelist stale reference.
- **Impact**: No RAM batch data loss risk from missing `flushExploreBatchIfNeeded`; each set enqueues independently. Stale xcfilelist may break iOS build path validation.
- **Fix-sketch**: Remove dead xcfilelist entry; update brief H4 to reflect immediate enqueue design.
- **Effort**: S
- **Verified-by**: pending

### [H-05] Report cache LRU (10) can evict sibling set reports before merge
- **Severity**: P2
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `kmp-app/feature/reports/src/commonMain/kotlin/com/movit/feature/reports/TrainingSessionReportCache.kt:15-16`, `TrainingSessionReportCache.kt:43-52`, `feature/reports/.../TrainingSessionReportCacheTest.kt:34-42`
- **Evidence**: `MovitLruCache` evicts oldest on 11th `put`; `getMergedForDisplay` loads siblings by id from cache/disk — evicted ids unavailable for merge.
- **Impact**: Multi-set exercise with &gt;10 cached uploads in one app session may show incomplete merged report (missing sets).
- **Fix-sketch**: Key LRU by `sessionExerciseKey`, bump cap, or persist all set reports to disk only and merge from SQL.
- **Effort**: M
- **Verified-by**: pending

### [H-06] Cross-set aggregator uses score-only best/worst; omits rich analysis sections
- **Severity**: P2
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/report/MovitPostTrainingReportCrossSetAggregator.kt:37-73`, `MovitPostTrainingReportBuilderV2.kt:246-302`
- **Evidence**: Merge `maxBy { it.score }` / `minBy { it.score }` on timeline; returns `primary.copy(...)` without merging `dangerAlerts`, `errorAnalysis`, etc. Tests assert score-based best/worst only (`MultiSetRichReportCrossSetIntegrationTest.kt:37-42`).
- **Impact**: Multi-set UI may show best/worst rep by score that differs from per-set BuilderV2 worst-rep heuristic; danger/error narrative from non-primary set may be dropped.
- **Fix-sketch**: Re-run BuilderV2 on merged `repDetails` or port `findWorstRep` fitness to aggregator.
- **Effort**: M
- **Verified-by**: pending

### [H-07] Peak JPEG capture copies bitmap twice per peak (full + thumb) on IO thread
- **Severity**: P2
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: PF-02
- **Files**: `kmp-app/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/MediaPipePoseDetector.kt:233-241`, `kmp-app/feature/training/src/androidMain/kotlin/com/movit/feature/training/AndroidTrainingFrameSnapshotPort.kt:20-27`, `TrainingFrameCaptureCoordinator.kt:186-201`
- **Evidence**: `takeSnapshotJpeg` copies bitmap then compresses; port calls twice per peak. Jobs launched from `viewModelScope`, work on `Dispatchers.IO`.
- **Impact**: ~2× bitmap copy + encode per peak; replay adds ~5 Hz IO during active rep. Disk footprint ~10–20 JPEGs/set (50–150 KB each, NEEDS-DATA).
- **Fix-sketch**: Single encode + scale for thumb; throttle replay; share one snapshot bytes for thumb resize.
- **Effort**: M
- **Verified-by**: pending

### [H-08] Replay sampler can register up to 16 JPEGs/rep × 10 tracked reps on disk
- **Severity**: P3
- **Type**: Memory
- **Status**: CONFIRMED
- **Related-PF**: PF-02
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/report/MovitRepReplaySampler.kt:69-73`, `TrainingFrameCaptureCoordinator.kt:129-139`, `MovitRepReplaySamplerTest.kt`
- **Evidence**: `SAMPLE_INTERVAL_MS=180`, `MAX_FRAMES_PER_REP=16`, `MAX_TRACKED_REPS=10`; test `replaySampler_collectsBurstFrames` validates collection.
- **Impact**: Up to **160 replay files** per set on disk (540 px); not held in RAM. Rolling eviction drops oldest rep keys when &gt;10 reps tracked.
- **Fix-sketch**: Tie replay to counted reps only; lower `MAX_TRACKED_REPS` if storage is a concern.
- **Effort**: S
- **Verified-by**: pending

### [H-09] Journal checkpoint on every completed rep may amplify disk I/O
- **Severity**: P3
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/journal/TrainingMotionSession.kt:62-71`, `TrainingSessionWriteHooks.kt:57`, `TrainingSessionViewModel.kt:1686`
- **Evidence**: `onRepCompletedForMotion` → `checkpoint()` → `onCheckpoint?.invoke(snapshot())` after each rep. Snapshot copies `completedRepMetrics.toList()` (`MotionRecorder.kt:222`).
- **Impact**: 10-rep set → 10 journal checkpoints (metrics-only payload, small). Could stack with upload outbox on same store.
- **Fix-sketch**: Debounce checkpoints (e.g., every N reps or on pause/background).
- **Effort**: M
- **Verified-by**: pending

---

## Test Coverage Referenced

| Test | Validates |
|---|---|
| `MotionRecorderQualityTest` | Frame stats, snapshot restore |
| `MotionRecorderAnySideRomTest` | ROM/velocity joint selection |
| `PlannedMultiSetReportTotalsTest` | `accumulateDayReport` / session merge totals |
| `MultiSetRichReportCrossSetIntegrationTest` | Cross-set best/worst/timeline merge |
| `TrainingSessionReportCacheTest` | LRU, rekey, `getMergedForDisplay` |
| `TrainingFrameCaptureCoordinatorTest` | Peak/danger/replay, `awaitPendingCaptures`, set cancel |
| `MovitPeakFrameCaptureManagerTest` | Per-rep peak dedupe, danger cap/cooldown |
| `MovitRepReplaySamplerTest` | 16-frame cap per rep |

**Not covered**: End-to-end VM `finalizeCurrentExercise` double-build; device JPEG/memory profiling; iOS snapshot under load.

---

## Files Read vs Brief List

| File | Status |
|---|---|
| `MotionRecorder.kt`, `MotionDataModels.kt`, `MetricsCalculator.kt`, `TrainingMotionSession.kt` | Read |
| `report/*` (12 production files) | Read (V2, aggregator, peak, replay, builder entry) |
| `TrainingSessionWriteHooks.kt`, `TrainingFrameCaptureCoordinator.kt`, `TrainingSessionWriteDiagnostics.kt` | Read |
| `AndroidTrainingFrameSnapshotPort.kt`, `IosTrainingFrameSnapshotPort.kt` | Read |
| `TrainingSessionViewModel.kt` (finalize/cache/upload/accumulate) | Read (grep + sections) |
| `TrainingSessionWriteCoordinator.kt` | Read (consumer) |
| `TrainingSessionReportCache.kt` | Read |
| **`WorkoutExecutionBatchCoordinator.kt`** | **Missing** (stale reference only) |

---

## PF Register (Track H scope)

| PF | Judgment | Finding |
|---|---|---|
| **PF-02** (consumer) | **CONFIRMED** (P2) | [H-07], [H-08] — bitmap copy + IO encode off hot path but replay/duplicate thumb costly |

---

*End of Track H report. All P0/P1 findings: **Verified-by: pending**.*
