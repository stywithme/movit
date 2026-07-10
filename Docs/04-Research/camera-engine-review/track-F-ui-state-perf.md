# Track F — Compose / UI State Performance

> **Scope**: `TrainingSessionUiState` hot-path updates, Compose collection/recomposition, overlay/ROM cost, feedback channel allocation.  
> **Mode**: READ ONLY — no code changes.  
> **Date**: 2026-07-10  
> **Line map note**: Brief §6 F cites older line numbers (`UiState :1568-1626`). Current file is ~1968 lines; `TrainingSessionUiState` is at `:1872-1941`, hot path at `:441-528`.

---

## Executive summary

Per-frame UI work funnels through a single **62-field** `TrainingSessionUiState` `MutableStateFlow`, collected wholesale in `MovitTrainingRoutes` and passed as one `state` parameter into `TrainingSessionScreen`. On the TRAINING hot path the worker issues **2–3 `_state.update` calls per pose frame** (landmarks + elapsed + skeleton overlay), plus event-driven callback updates; setup/countdown issues **3–4** (landmarks + setup guidance + `refreshSkeletonOverlay` ×2). `metricsSnapshot()` is built **2×/frame** routinely and **3×** when the 1 Hz random-message check runs or when setup double-refreshes. Overlay ROM indicators are recomputed on the main thread every frame because `remember` keys include `state.landmarks`. **PF-12** and **PF-13** are **CONFIRMED** (structural). Device Layout Inspector / fps delta remains **NEEDS-DATA**.

---

## F1 — `_state.update` count per frame

### TRAINING (`SessionRunState.TRAINING`)

Call chain in `processPoseFrameOnWorker`:

| # | Site | Always? | Notes |
|---|---|---|---|
| 1 | `applyPoseLandmarksToUi` → `_state.update` | Yes (has pose) | New `List<SkeletonLandmarkPoint>` (~33–35 allocs) + UiState copy |
| 2 | `updateSessionElapsed` → `_state.update` | Yes if `timestampMs > 0` | `formatElapsed` is `m:ss`; **StateFlow may skip emission** when label string unchanged (`equals`) — copy still allocated |
| 3 | `engine?.processFrame(frame)` | Yes | May synchronously fire callbacks → extra updates (rep/phase/hold/vignette/visual) |
| 4 | `refreshSkeletonOverlay` → `_state.update` | Yes | Always new `SkeletonOverlayParityState` / `jointVisuals` maps when content changes |
| 5 | `maybeDeliverRandomMessage` | ≤1 Hz | No `_state.update` unless feedback delivers visual |

**Steady-state count (no phase/rep event):** **2–3 updates/frame** (landmarks + elapsed ± overlay).  
**With engine events in same frame:** **+1–3** (`onRepCountChanged`, `onPhaseChanged`, `onHoldStatusChanged`, `applyVignetteCue`, `applyVisualMessage`).

At ~25 pose fps: **~50–75 UiState copies/s** steady; **~75–150/s** if elapsed always emits and events are frequent.

### Setup / countdown (`shouldValidatePose()` = SETUP_POSE, COUNTDOWN, RESUME_*)

| # | Site | Always? |
|---|---|---|
| 1 | `applyPoseLandmarksToUi` | Yes |
| 2 | setup guidance `_state.update` (`:489-503`) | When `shouldValidatePose && exerciseConfig != null` |
| 3 | `refreshSkeletonOverlay` (`:504`) | Same branch |
| 4 | `refreshSkeletonOverlay` again (`:527`) | **Always** after setup branch (and also when validation skipped — only #1+#4) |

**Typical setup frame:** **3–4 `_state.update`**. Second overlay refresh often **content-equal** → StateFlow may suppress the 4th emission, but `metricsSnapshot()` + `buildSkeletonOverlayParityState` still run twice.

**Countdown + `SupervisorAction.ProcessFrame`:** worker updates above, then main-thread handler (`:982-994`) may call `updateSessionElapsed` again + `engine.processFrame` → additional callback updates.

### `metricsSnapshot()` per frame (feeds PF-12)

| Path | Calls |
|---|---|
| Every frame | `emitPipelineDiagnostics` (`:561`) |
| Every frame (TRAINING + setup) | `refreshSkeletonOverlay` (`:1614`); **×2 on setup** (`:504`, `:527`) |
| ≤1 Hz TRAINING | `maybeDeliverRandomMessage` (`:1553`) |

→ **TRAINING: 2×/frame + 1×/s**; **setup: 3×/frame** when validation branch runs.

---

## F2 — Recomposition blast radius

```112:112:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/MovitTrainingRoutes.kt
  val state by viewModel.state.collectAsStateWithLifecycle()
```

```167:168:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/MovitTrainingRoutes.kt
  TrainingSessionScreen(
    state = state,
```

- One `StateFlow` → one `state: TrainingSessionUiState` parameter.
- Any per-frame field change (`landmarks`, `skeletonOverlayParity`, `elapsedLabel`, setup fields) invalidates **`TrainingSessionScreen`** and every child that reads `state` without further splitting:
  - `MovitSkeletonOverlay` (landmarks/parity/ROM)
  - `TrainingSessionLiveBottomBar` / `liveStatusValue` / `liveProgressMetric`
  - `TrainingSessionTopChrome` (stable props mostly — still recomposed as child of invalidated parent unless skipped)
  - `TrainingSessionStateOverlay`
  - `cameraSlot` lambda recreated each parent recomposition (camera host may survive if it does not read changing state internally)

**Structural verdict:** landmarks-on-UiState **forces full-screen recomposition at pose rate**. Preferred split (separate overlay `StateFlow` / local overlay state) is documented as migration **R1** (see OQ-01).  
**Measured recomposition counts:** NEEDS-DATA (Layout Inspector).

---

## F3 — `romIndicators` / projector `remember` cost

```86:113:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionScreen.kt
    val romIndicators = remember(
        state.landmarks,
        state.jointStateInfos,
        state.skeletonOverlayParity.isBilateralFlipped,
        state.skeletonOverlayParity.jointVisuals,
        trainingPreferences.indicatorType,
    ) {
        buildSkeletonRomIndicators(...)
    }
    val landmarkProjector = remember(
        state.skeletonAnalysisWidth,
        state.skeletonAnalysisHeight,
        state.skeletonMirrorPreview,
    ) { ... }
```

- `state.landmarks` is a **new list every frame** → `remember` keys miss every frame → `buildSkeletonRomIndicators` runs on **main thread** every pose frame.
- Mapper walks primary `jointStateInfos`, builds anchors from landmarks, allocates `SkeletonRomIndicator` + range objects (`TrainingRomIndicatorMapper.kt:28-77`).
- Cost is O(primary joints) with small constants — **P2 waste**, not heavy math; correct home is VM worker (or derive inside overlay draw from landmarks + joint infos without Compose `remember` invalidation).
- Projector keys (analysis size / mirror) are stable across frames → cheap after first frame.

---

## F4 — Feedback channels (Router / Arbiter / Scheduler)

| Component | Role | Thread |
|---|---|---|
| `FeedbackRouter` | Production path from VM (`MovitTrainingRoutes` constructs it; VM `feedback.submit`) | Caller thread: **worker** for engine callbacks during `processFrame`; **main** for supervisor/countdown |
| `FeedbackScheduler` | Cooldown / priority / audible plan | Same as caller (no dispatcher) |
| `FeedbackArbiter` | Parallel WS-7 helper; **not** wired as the live session router | N/A for hot path |

**Allocation order:** callers **build `FeedbackSignal` first**, then `scheduler.schedule` may return `silent` (cooldown / max repeats / no channel). Examples: `submitJointStateMessage` (`:883-893`), `submitJointErrorFeedback` (`:904-913`), position issues (`:812-821`). Frame path is partially gated by `FrameFeedbackEmitter` throttles **before** VM signal build; scheduler cooldowns still drop many already-built signals.

Scheduler intentionally sets `showVisual = false` (`FeedbackScheduler.kt:98`) for voice-first camera mode; `FeedbackRouter.deliver` can still invoke `onVisualMessage` via `shouldShowVisual` (WARNING+) → `applyVisualMessage` → another `_state.update` even though **`glassMessage` / vignette are not composed** on `TrainingSessionScreen` (as-built gap).

---

## F5 — `maybeDeliverRandomMessage` + `metricsSnapshot`

```1549:1556:kmp-app/feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt
  private fun maybeDeliverRandomMessage(timestampMs: Long) {
    if (supervisor.state.value != SessionRunState.TRAINING) return
    if (timestampMs - lastRandomMessageCheckMs < 1_000L) return
    lastRandomMessageCheckMs = timestampMs
    val hasActiveErrors = engine?.metricsSnapshot()?.jointStateInfos?.values?.any {
      it.state == JointState.WARNING || it.state == JointState.DANGER
    } == true || visibilityWarningActive
```

- Runs only in TRAINING, ≤1 Hz.
- Immediately preceded by `refreshSkeletonOverlay`, which already called `metricsSnapshot()` and wrote `jointStateInfos` into UiState (`:1613-1633`).
- Engine keeps the same map in private `lastJointStateInfos` (`MovitTrainingEngine.kt:406,698,789`).
- **Redundant:** third snapshot/sec can use `_state.value.jointStateInfos` or a value returned from `refreshSkeletonOverlay`.

---

## F6 — `MovitSkeletonOverlay` draw efficiency

- Uses `Canvas { ... }` (`MovitSkeletonOverlay.kt:105`); **no `drawWithCache`**, no path cache keyed on landmark snapshot.
- Reuses one `remember { Path() }` for line ROM tracks (`:93`) — good for that path only.
- TRAINING mode draws ROM only (`:131-138`); ARC style loops **0→180° in 2° steps** (~90 `drawArc` calls) per primary indicator per frame (`:313-339`).
- SETUP_ANGLES allocates text layouts via `textMeasurer.measure` per highlight per frame (`:708-721`).
- PREVIEW draws connections + joints with per-segment `drawLine` / `drawCircle` (no cached Path for skeleton).

**Verdict:** functional but **per-frame object/draw churn**; ARC ROM is the hottest overlay CPU on main.

---

## Findings

### [F-01] Multiple UiState copies per pose frame (TRAINING 2–3, setup 3–4)
- **Severity**: P1
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: PF-13
- **Files**: `kmp-app/feature/training/.../TrainingSessionViewModel.kt:456-528`, `:531-541`, `:544-556`, `:1613-1633`, `:1872-1941`
- **Evidence**: TRAINING path calls `applyPoseLandmarksToUi`, `updateSessionElapsed`, `refreshSkeletonOverlay` sequentially after `engine.processFrame`. Setup path adds guidance update (`:489-503`) and **two** `refreshSkeletonOverlay` calls (`:504`, `:527`). `TrainingSessionUiState` has **62** `val` fields (brief’s “~45” is stale).
- **Impact**: ~50–75+ full data-class copies/s at 25 fps before Compose; GC + StateFlow notifications on the UI hot path. Exact fps loss NEEDS-DATA.
- **Fix-sketch**: Coalesce per-frame fields into one `_state.update`; split overlay landmarks into a dedicated high-frequency flow.
- **Effort**: M
- **Verified-by**: adversarial-grok-4.5-xhigh

### [F-02] Monolithic `collectAsState` → full-screen recomposition at pose rate
- **Severity**: P1
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: PF-13
- **Files**: `MovitTrainingRoutes.kt:112`, `:167-168`; `TrainingSessionScreen.kt:61-62`, `:162-220`
- **Evidence**: Single `state` collection passed into screen; overlay, bottom bar, chrome, and overlays all sit under that parameter. Landmark list identity changes every frame (`:534-536`).
- **Impact**: HUD/chrome recompose with skeleton even when only landmarks moved; adds main-thread Compose work each pose frame. Layout Inspector counts NEEDS-DATA.
- **Fix-sketch**: `overlayState: StateFlow` collected only inside `MovitSkeletonOverlay` (or `movableContent` / nested subscribe); keep session chrome on low-frequency UiState.
- **Effort**: L
- **Verified-by**: adversarial-grok-4.5-xhigh

### [F-03] `buildSkeletonRomIndicators` recomputed every frame on main via `remember(landmarks)`
- **Severity**: P2
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: PF-13
- **Files**: `TrainingSessionScreen.kt:86-101`; `TrainingRomIndicatorMapper.kt:28-77`
- **Evidence**: `remember` keys include `state.landmarks` (new list/frame). Builder maps primary joints to `SkeletonRomIndicator` with range copies.
- **Impact**: Extra main-thread allocations/frame proportional to primary joints; smaller than full recomposition but unnecessary on UI thread.
- **Fix-sketch**: Build ROM indicators on the worker next to `refreshSkeletonOverlay`, or compute inside Canvas from stable joint configs + current angles.
- **Effort**: S
- **Verified-by**: adversarial-grok-4.5-xhigh

### [F-04] `FeedbackSignal` allocated before scheduler cooldown drop
- **Severity**: P2
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `FeedbackRouter.kt:48-59`; `FeedbackScheduler.kt:42-82`; `TrainingSessionViewModel.kt:883-913`, `:812-821`
- **Evidence**: `submit` always constructs/receives a full `FeedbackSignal`, then `schedule` may return `FeedbackDeliveryPlan.silent(...)` for cooldown/active/max_repeats. `FrameFeedbackEmitter` throttles some engine candidates earlier; VM joint/position paths still build signals for scheduler rejection.
- **Impact**: Short-lived objects on worker/main when coaching is noisy; usually dwarfed by UiState/overlay cost.
- **Fix-sketch**: Cheap pre-check (`scheduler.wouldAccept(dedupeKey, severity)`) or pass lazy text builders; keep building after pass.
- **Effort**: S
- **Verified-by**: pending

### [F-05] Redundant `metricsSnapshot()` in `maybeDeliverRandomMessage`
- **Severity**: P3
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: PF-12
- **Files**: `TrainingSessionViewModel.kt:464-465`, `:1549-1556`, `:1613-1633`; `MovitTrainingEngine.kt:773-805`, `:406`, `:698`
- **Evidence**: TRAINING order is `refreshSkeletonOverlay` (snapshot #2) then `maybeDeliverRandomMessage` (snapshot #3 at 1 Hz) only to read `jointStateInfos` already published to `_state` / held as `lastJointStateInfos`.
- **Impact**: One extra `EngineMetrics` alloc/sec during training (plus diagnostics snapshot every frame).
- **Fix-sketch**: Read `_state.value.jointStateInfos` or return infos from `refreshSkeletonOverlay`.
- **Effort**: S
- **Verified-by**: pending

### [F-06] Setup path double `refreshSkeletonOverlay` + triple `metricsSnapshot`
- **Severity**: P2
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: PF-12
- **Files**: `TrainingSessionViewModel.kt:479-528`, `:1613-1633`, `:559-572`
- **Evidence**: Validation branch calls `refreshSkeletonOverlay` at `:504` and unconditionally again at `:527`. Each call builds `EngineMetrics` + parity maps. Plus `emitPipelineDiagnostics` snapshot at frame start → **3 snapshots/setup frame**.
- **Impact**: Duplicate overlay mapping work every setup/countdown frame; second StateFlow emit often equals-skipped but CPU/alloc still paid.
- **Fix-sketch**: Single refresh after guidance update; share one metrics local across diagnostics + overlay.
- **Effort**: S
- **Verified-by**: pending

### [F-07] Skeleton Canvas redraws without `drawWithCache`; ARC ROM ~90 draw ops/indicator
- **Severity**: P2
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: PF-13
- **Files**: `core/designsystem/.../MovitSkeletonOverlay.kt:105-156`, `:280-339`, `:93-94`
- **Evidence**: Plain `Canvas` lambda; ARC path iterates `startAngle` 0..180 step 2 (`:313-339`). Only line-track `Path` is remembered. No `drawWithCache` anywhere in designsystem overlay.
- **Impact**: Main-thread draw CPU scales with indicators × ~90 arcs at pose rate; contributes to jank when Compose already recomposing.
- **Fix-sketch**: Cache arc segments in `drawWithCache` keyed by ranges; invalidate marker-only on angle change.
- **Effort**: M
- **Verified-by**: pending

### [F-08] High-frequency UiState fields updated but not composed (`glassMessage`, `showVignette`, setup panel)
- **Severity**: P3
- **Type**: Architecture
- **Status**: CONFIRMED
- **Related-PF**: PF-13
- **Files**: `TrainingSessionViewModel.kt:1590-1610`, `:1930-1931`; `TrainingSessionScreen.kt:364-398`; `TrainingSessionPanels.kt:52+`; `Docs/00-Active-Reference/Engine/Camera-Training-Engine-As-Built/09-Camera-Training-UI-UX.md:124-139`
- **Evidence**: As-built doc: `SetupPosePanel` / `CountdownOverlay` / vignette **not composed** from `TrainingSessionScreen`; `debugFps` is `@Suppress("UNUSED_PARAMETER")`. Setup guidance fields still update every validation frame (`:489-503`).
- **Impact**: State churn and recomposition without user-visible chrome for those fields; setup still drives bottom-bar phase text.
- **Fix-sketch**: Either wire panels/vignette or stop writing unused fields on the hot path.
- **Effort**: M
- **Verified-by**: pending

---

## PF verdicts (Track F)

| ID | Claim (brief) | Verdict | Finding |
|---|---|---|---|
| **PF-12** | `metricsSnapshot()` up to 3×/frame in VM; `refreshSkeletonOverlay` twice per setup frame | **CONFIRMED** | F-05, F-06 — TRAINING routinely **2×/frame** (+1×/s random); setup validation **3×/frame** with double refresh at `:504`+`:527` |
| **PF-13** | ~4–5 `_state.update`/frame on ~45-field UiState + full Route collect → recomposition pressure | **CONFIRMED** (counts nuanced) | F-01, F-02 — **62** fields; TRAINING steady **2–3** updates (not always 4–5); setup **3–4**; full `collectAsStateWithLifecycle` confirmed. **fps delta NEEDS-DATA** on device |

---

## OQ-01 — Monolithic UiState including per-frame landmarks

**Question:** Conscious choice or migration legacy? Preferred alternative?

**Evidence of design intent:**

1. **Known debt, preferred fix documented** in Phase 07 migration plan §16:
   - **S3**: delivery via full UiState + recomposition called out as a major lag cause vs legacy custom View `invalidate()`.
   - **R1**: “فصل مسار الرسم عن مسار المحرك” — latest points channel → overlay-only state / custom view — **without UiState**.
   - Post-hotfix table (2026-06-14): **`S1/S3/R1/R3 … UiState … مفتوح`** — still open, cited as remaining perf gap (~7 vs ~15 fps).
   - Source: `Docs/02-Roadmaps-And-Plans/UI-UX/Android-KMP-Mobile-UI-UX-Phase-07-Training-Engine-Migration-Plan.md:1383`, `:1393`, `:1410`.
2. **Legacy audit** still lists R1 (separate draw path from UiState) as later perf work: `Android-KMP-Training-Engine-Legacy-MO-vs-Current-Difference-Audit.md:1392`.
3. **Code comments** on the VM emphasize WS/parity migration (`TrainingSessionViewModel.kt:94` “WS-5/WS-7/07.8-B”) but **do not** defend monolithic landmarks-in-UiState as a final architecture.
4. **As-built UI doc** describes one UiState feeding screen as current fact, not as an optimized end state (`09-Camera-Training-UI-UX.md:103-118`).

**Answer for index:** Current design is **migration inheritance with explicit intent to split later (R1)** — not a documented “keep forever” decision. Product/owner confirmation still needed to prioritize R1 vs other engine work; technical preferred alternative is already written: **separate overlay landmark flow**.

---

## Coverage

| File | Read? | Notes |
|---|---|---|
| `TrainingSessionViewModel.kt` (UiState, all hot-path `_state.update`, callbacks, feedback hooks) | Yes | `:441-572`, `:779-845`, `:982-994`, `:1549-1634`, `:1872-1941`; full `_state.update` grep |
| `MovitTrainingRoutes.kt` | Yes | `:112`, `:167-215` |
| `TrainingSessionScreen.kt` | Yes | remember/ROM/overlay/bottom bar |
| `TrainingSessionPanels.kt` | Yes | panels exist; not wired from Screen |
| `SkeletonOverlayMapper.kt` | Yes | parity + projector |
| `TrainingRomIndicatorMapper.kt` | Yes | `buildSkeletonRomIndicators` |
| `MovitSkeletonOverlay.kt` (designsystem) | Yes | Canvas / ARC / no drawWithCache |
| `TrainingDebugOverlay.kt` (feature/training) | Yes | `TrainingDebugFpsOverlay`; unused from Screen |
| `FeedbackRouter.kt` | Yes | submit/deliver/visual |
| `FeedbackScheduler.kt` | Yes | cooldown before deliver |
| `FeedbackArbiter.kt` | Yes | not on live session path |
| `FrameFeedbackEmitter.kt` | Yes | pre-throttle for some engine emits |
| `MovitTrainingEngine.metricsSnapshot` / `lastJointStateInfos` | Yes | `:406`, `:698`, `:773-805` |
| Phase 07 plan + as-built UI doc (OQ-01) | Yes | design intent |

**Not in Track F scope / not deeply re-read:** iOS-specific Compose hosts, `feature/training-debug` full UI (only noted sibling overlay), Layout Inspector device capture.

---

## Question checklist

| ID | Answer |
|---|---|
| **F1** | TRAINING: **2–3** `_state.update`/frame steady (+events); setup: **3–4** with double overlay refresh; ~**50–75+** UiState copies/s @25fps |
| **F2** | Full-screen blast via single `collectAsStateWithLifecycle` → `TrainingSessionScreen(state)`; split overlay flow recommended; counts NEEDS-DATA |
| **F3** | `remember(landmarks…)` misses every frame → `buildSkeletonRomIndicators` on main; belongs on worker |
| **F4** | Router on caller thread (worker/main); signal built then often dropped by scheduler cooldown |
| **F5** | Yes — use `_state.jointStateInfos` / `lastJointStateInfos` instead of extra snapshot |
| **F6** | `Canvas` without `drawWithCache`; ARC ~90 draws/indicator/frame |
| **PF-12** | **CONFIRMED** |
| **PF-13** | **CONFIRMED** (update-count nuance; fps NEEDS-DATA) |
| **OQ-01** | Migration debt with documented R1 preferred split — not a final “keep monolith” decision |

---

*Verified-by: pending (all P1 findings await adversarial second agent per brief §5.8 / §8.5).*
