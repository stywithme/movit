# Metrics — As-built (matches code)

| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | What the app calculates and stores today |
| **Catalog (definitions)** | [Metrics-Complete-Reference.md](Metrics-Complete-Reference.md) |
| **Improvement backlog** | [Metrics-Final-Framework.md](Metrics-Final-Framework.md) |
| **Verified** | 2026-05-29 |

---

## Code map

| Layer | Path | Role |
|-------|------|------|
| **Calculation** | `kmp-app/.../training/analytics/MetricsCalculator.kt` | ROM, symmetry, stability, velocity, VL, alignment, consistency, fatigue, tempo, 1RM, volume |
| **Recording** | `kmp-app/.../training/analytics/MotionRecorder.kt` | Per-rep frames → calls `MetricsCalculator.calculateRepMetrics` / planned workout aggregate |
| **Rep score (live)** | `kmp-app/.../training/engine/ScoreCalculator.kt` | Joint state → rep score during workout run |
| **Report aggregate** | `kmp-app/.../training/report/PostTrainingReport.kt` | `PerformanceSummary`, V2 fields, consistency helpers |
| **UI cards** | `kmp-app/.../training/report/PerformanceMetricsBuilder.kt` | Form / Safety / Control cards — **formats only**, no recalculation |
| **Display filter** | `kmp-app/.../ui/report/MetricDisplayBuilder.kt` | Which metrics show per exercise config |
| **Backend persist** | `backend/prisma/schema.prisma` → `WorkoutExecutionMetrics`, `RepMetrics` | Integers scaled ×10 (see below) |
| **Backend ingest** | `backend/src/modules/workout-executions/` | Maps mobile upload payload |
| **Reports API** | `backend/src/modules/reports/` | Aggregates for program/week/day scopes |

---

## Storage scale (Android → Backend)

Most kinematic/quality values are stored as **int × 10** (e.g. `1000` = 100.0%).

| Field | Unit in docs | Stored as |
|-------|--------------|-----------|
| `formScore`, `alignmentAccuracy`, `stability`, `symmetry` | % | 0–1000 |
| `rom` | degrees | degrees × 10 in pipeline; verify per field in upload mapper |
| `tempo` | ms phases | `Json` array |
| `velocity` | °/s (angular) | × 100 in calculator comments |

---

## Per-metric as-built

| Code | Calculated in | Shown in UI (V2 builder) | Uploaded to backend | Notes |
|------|---------------|---------------------------|---------------------|-------|
| `form_score` / rep score | `ScoreCalculator` → rep record | Form card (state %) | `RepMetrics.score`, `WorkoutExecutionMetrics.avgFormScore` | Weighted joint states |
| `rep_count` | `RepCounter` | Summary | planned workout payload | Rep-based only |
| `duration` | workout run timers | Summary | `totalDurationMs` | Always |
| `rom` | `MetricsCalculator.calculateROM` | Form card if enabled | `RepMetrics.rom`, `avgRom` | **Raw** max−min°, not % of target |
| `symmetry` | `calculateSymmetry` / `calculateBilateralRomSymmetry` | Form card (LSI path for bilateral) | `avgSymmetry` | LSI for alternating bilateral |
| `stability` | `calculateTrunkStability` (spine) or `calculateStability` (hip fallback) | Safety card | `avgStability`, per-rep `stability` | Not COP / force plate |
| `tempo` | phase timings from `PhaseStateMachine` | Control card | `tempo` JSON | Ecc / iso / con ms |
| `tut` | sum of rep durations (`PerformanceMetricsBuilder` — not full workout run time) | Control card | `totalTUT` | Fixed vs old “workout duration” |
| `hold_duration` | `HoldTimer` / hold coordinator | Hold exercises | via duration fields | Hold-only |
| `alignment` | `calculateAlignmentAccuracy` (PERFECT/NORMAL frames) + **also** PositionCheck severity in Safety builder | Safety (PositionCheck-based in V2) | `avgAlignmentAccuracy` | Dual sources — see gap below |
| `form_consistency` | `calculateFormConsistency` / `FromScores` / DTW in report | Form card | `formConsistency` | Method depends on data |
| `fatigue_index` | `calculateFatigueIndex` | Control card | `fatigueIndex` | Score-drop based |
| `tempo_consistency` | `calculateTempoConsistency*` | Control card | nullable on planned workout | Implemented |
| `velocity` | `calculateVelocity` (concentric phase) | optional | `avgVelocity` | Angular, not bar speed |
| `velocity_loss` | per-rep + `calculateVelocityLoss` | Control card | derived in report | **Implemented** |
| `weight` / `volume` / `est_1rm` | `calculateVolume`, `calculateEst1RM` | Load section | `totalVolume`, `est1RM` | Manual weight input |
| **Safety score** | `PerformanceMetricsBuilder.calculateSafetyScore` | V2 data, **V1 UI** may hide | in report JSON | Composite |
| **Control score** | `calculateControlScore` | V2 data | in report JSON | Composite |
| **Overall quality** | weights 40/35/25 Form/Safety/Control | V2 data | in report JSON | Not primary V1 UI |

**Report UI:** V1 = `ReportPagerActivity` multi-page. V2 metrics computed and stored; full card UI still incomplete — see [Post-Training-Report-Review.md](../Product-Master/Post-Training-Report-Review.md).

---

## Known gaps (doc ↔ code)

| Topic | As-built today | Documented target (roadmap) |
|-------|----------------|----------------------------|
| ROM display | Degrees / avg ROM | ROM Achievement % vs `upPose`/`downPose` — **PLANNED** |
| Alignment | Joint-state % + PositionCheck severities in Safety | Merge into single SSOT metric — **PARTIAL** |
| CoM stability | Trunk angle variance (spine) | CoM from landmarks — **PLANNED** (different formula) |
| Movement smoothness | — | **NOT IMPLEMENTED** |
| V2 report cards | Data layer ready | UI exposure — **PARTIAL** |

Full backlog with scientific rationale: [Metrics-Final-Framework.md](Metrics-Final-Framework.md#implementation-status-vs-code-2026-05-29).

---

## Recommendation implementation status

Synced from [Metrics-Final-Framework.md](Metrics-Final-Framework.md) against codebase on **2026-05-29**:

| Recommendation | Status |
|----------------|--------|
| Trunk stability (spine variance) vs hip fallback | **IMPLEMENTED** |
| Bilateral LSI ROM symmetry | **IMPLEMENTED** |
| Velocity loss (VBT-style) | **IMPLEMENTED** |
| TUT = sum of rep durations | **IMPLEMENTED** |
| Tempo consistency across reps | **IMPLEMENTED** |
| Safety/Control composite scores (V2) | **IMPLEMENTED** (data); UI **PARTIAL** |
| Alignment from PositionValidator in Safety card | **IMPLEMENTED** in builder |
| ROM Achievement % vs target config | **PLANNED** |
| CoM-based body stability | **PLANNED** |
| Merge Alignment into Form as single SSOT | **PLANNED** (product decision) |
| Movement smoothness (jerk) | **PLANNED** |
| Volume = sets × reps × load (verify mapper) | **VERIFY** on upload path |

---

## When you change code

1. Update this file (table row + `Verified` date).
2. Update [Metrics-Complete-Reference.md](Metrics-Complete-Reference.md) if user-facing definition changes.
3. Move completed roadmap items in [Metrics-Final-Framework.md](Metrics-Final-Framework.md) to **IMPLEMENTED** in the status table.
