| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | Backend metric storage, aggregation, progression consumption |
| **Code** | `backend/src/lib/metrics/`, `backend/src/modules/workout-executions/`, `backend/src/modules/reports/`, `backend/src/modules/progression/` |
| **Verified** | 2026-07-04 |

# Backend metrics & reports

Camera training produces metrics in **two parallel shapes**: relational int×10 tables for per-exercise runs, and JSON/float aggregates for planned-workout reports. This doc maps how each is written, read, and consumed.

Cross-reference: [Metrics-As-Built.md](../../Metrics/Metrics-As-Built.md).

---

## Scale contract

**SSOT:** `backend/src/lib/metrics/metrics-contract.ts`

| Layer | WorkoutExecutionMetrics / RepMetrics | PlannedWorkoutReport |
|-------|-------------------------------------|----------------------|
| **DB storage** | Int × 10 (850 = 85.0%) | Float 0–100 + JSON blob |
| **Mobile API upload** | Float 0–100 (via `WorkoutUploadMapper ÷10`) | Float 0–100 in payload + nested JSON |
| **Progression engine** | Reads relational → `intX10ToFloat()` | Uses report JSON snapshots indirectly |

```typescript
// backend/src/lib/metrics/metrics-contract.ts
export function intX10ToFloat(value: number | null | undefined): number {
  if (value == null) return 0;
  return value / 10;
}

export function floatToIntX10(value: number | null | undefined): number {
  if (value == null) return 0;
  return Math.round(value * 10);
}
```

**Gap:** `floatToIntX10` is **not called** in `saveWorkoutExecution()` — API floats are stored directly into Int columns. Downstream code often assumes int×10 and divides by 10 on read, which only works if mobile accidentally sent scaled integers as floats.

---

## Relational metrics (per exercise run)

### Tables

**`WorkoutExecutionMetrics`** — one row per execution:

| Column | Meaning |
|--------|---------|
| `avgRom`, `avgStability`, `avgFormScore`, `avgAlignmentAccuracy` | Session averages |
| `avgSymmetry` | Bilateral only |
| `avgVelocity`, `avgTempo` (JSON) | Tempo / velocity |
| `totalTUT` | Time under tension (ms) |
| `totalVolume`, `maxWeight`, `est1RM`, … | Load-derived |

**`RepMetrics`** — one row per rep:

| Column | Meaning |
|--------|---------|
| `repNumber`, `durationMs`, `worstState`, `score` | Rep identity + quality |
| `rom`, `symmetry`, `stability`, `formScore`, `alignmentAccuracy` | Kinematic |
| `tempo` (JSON), `velocity` | Timing |
| `side` | `left` / `right` for bilateral |

### Write path

```
MovitTrainingEngine.stop() → ExerciseWorkoutSummary / MotionRecorder
  → WorkoutUpload (journal, internal int×10)
  → WorkoutUploadMapper.toUploadRequest (÷10 → API floats)
  → POST /mobile/workout-executions
  → saveWorkoutExecution() → Prisma create (no floatToIntX10)
```

### Read path

`mapWorkoutExecutionToResponse()` returns metrics as stored. History helpers use `avgFormScore / 10` for display averages (`getExerciseHistory`, home stats).

---

## JSON report metrics (planned workout)

### `PlannedWorkoutReport` model

| Column | Type | Role |
|--------|------|------|
| `avgFormScore`, `avgAccuracy` | Float? | Block-level rollups |
| `totalReps`, `completedSets`, … | Int? | Counts |
| `report` | Json? | Full nested report |

### Normalization — `normalizePlannedWorkoutReport()`

`workout-executions.service.ts` (~line 788):

1. Parse `report.exerciseReports[]`
2. Per exercise: `normalizeExerciseReport()` → snapshot + `averageFormScore`
3. `aggregateSnapshots()` across exercises (state breakdown, position error counts)
4. Compute fallbacks for `totalSetsCompleted`, `averageFormScore`, etc.
5. Write normalized JSON back into `report` column

Snapshots include counting metadata (`CountingSnapshot`): position error/warning/tip rep counts, state breakdown — used for admin analytics, not re-derived from `RepMetrics` rows.

### `legacyReport` on `WorkoutExecution`

Optional JSON on single-exercise upload — PostTrainingReport-compatible blob for backward compatibility. Admin report views may prefer this when present.

---

## reports.service aggregation

**Path:** `backend/src/modules/reports/reports.service.ts`

Used by admin dashboard and analytics APIs (not mobile camera hot path).

| Function area | Input | Output |
|---------------|-------|--------|
| `buildExerciseMetrics` | Stored exercise report JSON | `ExerciseMetricsOutput` with float metrics |
| `buildWorkoutExecutionTrends` | `WorkoutExecution[]` + includes | Trend series (form, volume, …) |
| Planned workout report readers | `PlannedWorkoutReport.report` JSON | Normalized exercise/set breakdown |

Reports service treats **JSON floats as authoritative** for block reports; relational tables for per-exercise drill-down and progression.

---

## Progression consumption

**Path:** `backend/src/modules/progression/progression.service.ts`

Triggered on **`completePlannedWorkoutReport`** (not on `/report` legacy endpoint).

### Metric resolution — `getRecentMetrics()`

```typescript
// Last N program-context executions for one exercise
const recentExecutions = await prisma.workoutExecution.findMany({
  where: { userId, exerciseId, context: WorkoutExecutionContext.program },
  include: { executionMetrics: true },
  orderBy: { timestamp: 'desc' },
  take: executionCount,
});

// Converts int×10 → float 0–100
avgFormScore: avg(metrics.map((m) => intX10ToFloat(m.avgFormScore))),
avgROM: avg(metrics.map((m) => intX10ToFloat(m.avgRom))),
```

Also computes `completionRate` from `countedReps / totalReps` on executions (not from JSON report).

### Eligibility

`evaluateEligibility()` compares `ExecutionMetricsSummary` against exercise `ExerciseProgressionProfile` quality gates (form, ROM, symmetry, stability thresholds).

### Outputs

- Writes `ProgressionHistory` rows
- Updates prescription / planned workout item targets (reps, weight, duration)
- Mobile polls `GET /mobile/progression/recent` after complete

---

## Dual-store comparison

| Question | Relational (`RepMetrics`) | JSON report |
|----------|---------------------------|-------------|
| Granularity | Per rep + session aggregate | Per exercise/set in block |
| Best for | Progression, history charts | Post-workout UI, admin review |
| Scale | Intended int×10 | Float 0–100 |
| Produced by | Engine journal on device | `MovitSessionReport` builder on device |
| Server recompute? | No — stored as uploaded | Normalized, not re-scored from video |

---

## Known gaps

| Gap | Impact |
|-----|--------|
| Missing `floatToIntX10` on ingest | Relational metrics may be 10× too small if floats stored literally |
| Progression uses relational only | Block JSON quality ignored for gate evaluation |
| `normalizeFormScore()` safety net unused in save path | Ambiguous values if dual format mixed |
| `formConsistency`, `fatigueIndex` | Nullable; engine may not populate all clients |
| Admin vs mobile read paths | Different divide-by-10 assumptions in places |

---

## Related docs

- [Metrics-As-Built.md](../../Metrics/Metrics-As-Built.md) — 18-metric framework
- [14-Report-And-Extracted-Metrics.md](14-Report-And-Extracted-Metrics.md) — on-device report extraction
- [02-Backend-API-Sessions-Reports.md](02-Backend-API-Sessions-Reports.md) — upload endpoints
