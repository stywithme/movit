# Metrics — As-built (matches code)

| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | What the app calculates, displays, stores, and syncs today |
| **Catalog (definitions)** | [Metrics-Complete-Reference.md](Metrics-Complete-Reference.md) |
| **Improvement backlog** | [Metrics-Final-Framework.md](../../02-Roadmaps-And-Plans/Metrics/Metrics-Final-Framework.md) |
| **Code** | `MetricsCalculator.kt`, `MotionRecorder.kt`, `ReportQualityScoring.kt`, `ReportDetailScreen.kt`, Prisma `WorkoutExecutionMetrics` / `RepMetrics` |
| **Verified** | 2026-06-22 |

Legend for pipeline columns: **Calc** = computed on device · **UI** = shown in KMP report · **DB** = Prisma relational fields · **Sync** = `POST /mobile/workout-executions` via `WorkoutUploadMapper`

---

## Code map

| Layer | Path | Role |
|-------|------|------|
| **Per-rep + session aggregate** | `kmp-app/core/training-engine/.../journal/MetricsCalculator.kt` | ROM, symmetry, stability, velocity, VL, alignment, form consistency, fatigue, tempo consistency, 1RM, volume |
| **Recording** | `kmp-app/core/training-engine/.../journal/MotionRecorder.kt` | Per-rep frames → `MetricsCalculator` / `WorkoutExecutionMetrics` |
| **Live rep score** | `kmp-app/core/training-engine/.../engine/ScoreCalculator.kt` | Joint state → rep score during workout run |
| **Report analysis** | `kmp-app/core/training-engine/.../report/MovitPostTrainingReportBuilderV2.kt` | Timeline, errors, tips, `MovitOverallQualityScore` |
| **Composite scores** | `kmp-app/core/training-engine/.../report/ReportQualityScoring.kt` | Form / Safety / Control card math (ported from legacy builder) |
| **Metric visibility config** | `kmp-app/core/training-engine/.../config/ExerciseConfigTypes.kt` → `ReportMetricsConfig.shouldShow()` | Which metrics an exercise may expose (UI not wired yet) |
| **Report UI** | `kmp-app/feature/reports/ReportDetailScreen.kt` | Compose: Overview · Form · Fatigue · Tips (4 tabs) |
| **UI mapping** | `kmp-app/feature/reports/MovitSessionReportUiMapper.kt` | `MovitPostTrainingReport` → `ReportDetailUi` |
| **Upload mapper** | `kmp-app/core/data/.../WorkoutUploadMapper.kt` | `WorkoutExecutionMetrics` → `ExecutionMetricsDto` |
| **Backend persist** | `backend/prisma/schema.prisma` → `WorkoutExecutionMetrics`, `RepMetrics` | Relational metrics (see gaps below) |
| **Backend ingest** | `backend/src/modules/workout-executions/` | Maps mobile upload payload |
| **Reports API** | `backend/src/modules/reports/` | Aggregates for program/week/day scopes |

---

## Storage scale (device → backend)

Internal models use **int × 10** for most percentages (e.g. `1000` = 100.0%). `WorkoutUploadMapper` divides by 10 before API upload.

| Field | Unit in UI/docs | Device model | Prisma / DTO |
|-------|-----------------|--------------|--------------|
| `formScore`, `alignmentAccuracy`, `stability`, `symmetry` | % | 0–1000 | int (uploaded as float %) |
| `rom` | degrees | raw ° in `Short` | int (uploaded as float; mapper ÷10) |
| `tempo` | ms per phase | `List<Int>` [E, I, C] | `Json` |
| `velocity` | °/s (angular) | ÷10 in calculator | optional int / float |
| `velocityLoss`, `tempoConsistency` | % | 0–1000 in `WorkoutExecutionMetrics` | **not in Prisma or DTO** |

---

## 18 metrics — pipeline matrix

| Code | Calc | UI | DB | Sync | Notes |
|------|:----:|:--:|:--:|:----:|-------|
| `form_score` | ✓ | ✓ | ✓ | ✓ | Live: `ScoreCalculator`. Report hero: avg rep score or `overallQuality.score` when present. `RepMetrics.score`, `avgFormScore`. |
| `rep_count` | ✓ | ✓ | ✓ | ✓ | `totalReps` / `countedReps` on `WorkoutExecution`. |
| `duration` | ✓ | ✓ | ✓ | ✓ | `durationMs` on execution. |
| `rom` | ✓ | — | ✓ | ✓ | Raw max−min°. Not shown as dedicated metric in `ReportDetailScreen`. |
| `symmetry` | ✓ | — | ✓ | ✓ | Frame diff or LSI (`calculateBilateralRomSymmetry`). |
| `stability` | ✓ | — | ✓ | ✓ | Spine variance preferred; hip midpoint fallback. |
| `tempo` | ✓ | — | ✓ | ✓ | Phase timings [ecc, iso, con] per rep + `avgTempo`. |
| `tut` | ✓ | — | ✓ | ✓ | `totalTUT` = Σ rep `durationMs`. |
| `hold_duration` | ✓ | partial | — | partial | Hold summary in report JSON / UI duration label; no dedicated Prisma column. |
| `alignment` | ✓ | — | ✓ | ✓ | `calculateAlignmentAccuracy`: % frames with all joints in good states — **not** PositionCheck severities. |
| `form_consistency` | ✓ | — | ✓ | ✓ | DTW (≥4 reps) in calculator; backend may recompute from scores. |
| `fatigue_index` | ✓ | partial | ✓ | ✓ | Calculated; UI shows timeline-derived fatigue **message**, not rep #. |
| `tempo_consistency` | ✓ | — | — | — | **Gap:** in `WorkoutExecutionMetrics` model only; omitted from `ExecutionMetricsDto` / Prisma. |
| `velocity` | ✓ | — | ✓ | ✓ | Auto from concentric phase (angular °/s). |
| `velocity_loss` | ✓ | — | — | — | **Gap:** per-rep + session max in engine; not in Prisma, DTO, or KMP upload mapper. |
| `weight` | input | — | ✓ | ✓ | `maxWeight`, per-rep `weightKg`. |
| `volume` | ✓ | — | ✓ | ✓ | `calculateVolume`: Σ weight for **counted** reps only. |
| `est_1rm` | ✓ | — | ✓ | ✓ | Epley from max weight + counted reps. |

### Composite scores (not separate DB columns)

| Score | Calc | UI | Stored | Sync |
|-------|:----:|:--:|:------:|:----:|
| **Form** (V2 card) | ✓ `ReportQualityScoring` | — | `legacyReport` JSON → `overallQuality.formScore` | optional `legacyReport` |
| **Safety** | ✓ | — | same | optional |
| **Control** | ✓ | — | same | optional |
| **Overall quality** | ✓ 40/35/25 (rep) or 35/40/25 (hold) | partial | same | optional |

`ReportDetailScreen` does **not** render Form / Safety / Control cards. Hero uses a single score (form or overall when enriched). See [Post-Training-Report-Review.md](../Product-Master/Post-Training-Report-Review.md).

---

## Report UI as-built (KMP)

| Tab | Content | Metrics surfaced |
|-----|---------|------------------|
| **Overview** | Form score (large), sets/reps/duration, insight, hero frame | `form_score`, `rep_count`, `duration`, hold duration label |
| **Form** | Joint error breakdown, best/worst rep scores, frame evidence | Derived from `errorAnalysis` / timeline — not ROM/symmetry/FC cards |
| **Fatigue** | Session-quality drop-off bar, form-by-set chart | Frame drop rate + rep timeline — **not** `fatigue_index` or `velocity_loss` |
| **Tips** | Coaching tips, export action | Text from report builder |

Post-workout navigation: `feature/reports/ReportDetailScreen.kt` (replaces legacy `ReportPagerActivity`).

---

## Known gaps

| Topic | As-built | Target (roadmap) |
|-------|----------|------------------|
| `velocity_loss` / `tempo_consistency` sync | Engine + local model only | Add to DTO, Prisma, upload mapper |
| V2 Form/Safety/Control cards | Computed in `ReportQualityScoring` + stored in report JSON | Expose in `ReportDetailScreen` |
| ROM display | Degrees in DB | ROM Achievement % vs target pose |
| Alignment | Joint-state frame % | PositionCheck-based alignment in Safety |
| `performanceMetrics` JSON field | Always `null` in serializer | Structured card payload |
| Metric filter in UI | `ReportMetricsConfig.shouldShow()` exists | Not wired in reports feature |

Backlog detail: [Metrics-Final-Framework.md](../../02-Roadmaps-And-Plans/Metrics/Metrics-Final-Framework.md).

---

## When you change code

1. Update this file (matrix row + `Verified` date).
2. Update the as-built columns in [Metrics-Complete-Reference.md](Metrics-Complete-Reference.md#فهرس-سريع) if pipeline changes.
3. Move completed roadmap items in [Metrics-Final-Framework.md](../../02-Roadmaps-And-Plans/Metrics/Metrics-Final-Framework.md).
