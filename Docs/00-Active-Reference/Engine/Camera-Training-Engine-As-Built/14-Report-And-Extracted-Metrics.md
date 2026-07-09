| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | Per-frame/rep/session metrics, post-training report, upload pipeline, legacyReport |
| **Code (engine)** | `MetricsCalculator.kt`, `MotionRecorder.kt`, `MovitPostTrainingReportBuilderV2.kt`, `MovitPostTrainingReportBuilder.kt` |
| **Code (data)** | `WorkoutUploadMapper.kt`, `TrainingSessionWriteCoordinator.kt` |
| **Code (backend)** | `mobile-workout-executions.controller.ts`, `workout-executions.types.ts`, `workout-executions.service.ts` |
| **Metrics catalog** | [Metrics-As-Built.md](../../Metrics/Metrics-As-Built.md), [Metrics-Complete-Reference.md](../../Metrics/Metrics-Complete-Reference.md) |
| **Exercise schema** | [Exercise-JSON-Schema.md](../../Contracts/Exercise-JSON-Schema.md) |
| **API contract** | [12-Mobile-API-Contract.md](12-Mobile-API-Contract.md) |
| **Verified** | 2026-07-04 |

# Report and extracted metrics

This doc traces how pose frames become **per-rep metrics**, **session aggregates**, **post-training report analysis**, and the **upload payload** sent to `POST /mobile/workout-executions`.

---

## Pipeline overview

```
PoseFrame (per frame)
  → MovitTrainingEngine.processFrame
  → MotionRecorder.record (angles, states, phase)
  → Rep completion → MetricsCalculator.calculateRepMetrics
  → Session end → MetricsCalculator.calculateWorkoutExecutionMetrics
  → MovitPostTrainingReportBuilder(V2) → MovitPostTrainingReportAnalysis
  → MovitPostTrainingReportBuilder → legacy JSON (PostTrainingReportLegacyJson)
  → WorkoutUpload + WorkoutUploadMapper (÷10) + legacyReport
  → Outbox → POST /mobile/workout-executions
```

Engine frame pipeline: [training-engine.md](../training-engine.md).

---

## Per-frame recording (`MotionRecorder`)

**Class:** `com.movit.core.training.journal.MotionRecorder`

Records **metrics only** — no raw pose persistence. Each frame during `TRAINING`:

| Captured | Storage |
|----------|---------|
| Relative timestamp | `FrameSample.t` |
| Joint angles (tracked set) | `ShortArray` per frame |
| Joint states (if changed) | `ByteArray` delta |
| Phase (ecc / iso / con) | Used for tempo |

On rep completion, buffered frames feed `MetricsCalculator.calculateRepMetrics`. Completed reps accumulate in `completedRepMetrics: List<RepMetricsData>`.

At session end, `buildWorkoutUpload()` produces a `WorkoutUpload` with all reps + `WorkoutExecutionMetrics`.

---

## Per-rep metrics (`MetricsCalculator`)

**Object:** `com.movit.core.training.journal.MetricsCalculator`

### `calculateRepMetrics`

Inputs: rep frame list, joint indices (primary, bilateral L/R, stability/spine, hips), phase timings, rep score.

| Metric | Method | Device scale |
|--------|--------|--------------|
| ROM | `calculateROM` — max−min primary joint angle | degrees as `Short` |
| Symmetry | L/R angle diff or bilateral LSI | 0–1000 (×10 %) |
| Stability | Trunk variance or hip midpoint | 0–1000 |
| Tempo | Phase timings from engine | `[ecc, iso, con]` ms |
| Velocity | Concentric angular velocity | internal ÷10 |
| Form score | From `ScoreCalculator` via rep score | 0–1000 |
| Alignment accuracy | % frames with all joints in good states | 0–1000 |
| Velocity loss | vs session best velocity | 0–1000 (**not uploaded**) |

```36:49:kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/journal/MetricsCalculator.kt
        return RepMetrics(
            rom = calculateROM(frames, primaryJointIndex),
            symmetry = if (leftJointIndex != null && rightJointIndex != null) {
                calculateSymmetry(frames, leftJointIndex, rightJointIndex)
            } else {
                null
            },
            stability = stability,
            tempo = phaseTimings,
            velocity = velocity,
            formScore = score,
            alignmentAccuracy = calculateAlignmentAccuracy(frames),
            velocityLoss = velocityLoss,
        )
```

### `calculateWorkoutExecutionMetrics`

Averages rep-level metrics + session aggregates:

| Aggregate | Calculation |
|-----------|-------------|
| `avgRom`, `avgSymmetry`, … | Mean of rep values |
| `avgTempo` | Per-phase mean across reps |
| `totalTUT` | Σ rep `durationMs` |
| `totalVolume` | Σ weight × counted reps |
| `maxWeight` | Max rep weight |
| `est1RM` | Epley from max weight + counted reps |
| `formConsistency` | DTW score (≥4 reps) |
| `fatigueIndex` | Rep where velocity drops below threshold |

Full 18-metric matrix: [Metrics-As-Built.md](../../Metrics/Metrics-As-Built.md).

---

## Post-training report (`MovitPostTrainingReportBuilderV2`)

**Object:** `com.movit.core.training.report.MovitPostTrainingReportBuilderV2`

Ports legacy `ReportGenerator` analysis to KMP common. Input: `List<RepResult>`, `ExerciseConfig`, frame captures, performance summary, `WorkoutExecutionMetrics`.

### Analysis sections

| Section | Max items | Source |
|---------|-----------|--------|
| `dangerAlerts` | 2 | Reps with `JointState.DANGER` |
| `perfectMoments` | 3 | High-score reps |
| `bestReps` | 3 | Top score reps + frame captures |
| `worstRep` | 1 | Lowest score / most errors |
| `errorAnalysis` | — | Joint error frequency + percentages |
| `repTimeline` | — | Per-rep state markers for UI |
| `consistency` | — | Score variance metric |
| `improvementTips` | 3 | From errors + config tips |
| `holdSummary` | — | Hold exercises only |
| `overallQuality` | — | Weighted Form/Safety/Control composite |
| `exerciseConfig` | — | Snapshot for report rendering |

```26:35:kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/report/MovitPostTrainingReportBuilderV2.kt
    fun build(
        repDetails: List<RepResult>,
        exerciseConfig: ExerciseConfig,
        frameCaptures: List<MovitPeakFrameCapture>,
        performanceSummary: MovitPerformanceSummary,
        executionMetrics: WorkoutExecutionMetrics,
        holdData: MovitHoldReportData? = null,
        poseVariantIndex: Int = 0,
        totalReps: Int = performanceSummary.totalReps,
    ): MovitPostTrainingReportAnalysis {
```

V2 consumes **`ExerciseConfig`** from the cached exercise JSON — joint definitions, state ranges, messages, and `reportMetrics` visibility config. Schema fields: [Exercise-JSON-Schema.md](../../Contracts/Exercise-JSON-Schema.md).

### Legacy envelope (`MovitPostTrainingReportBuilder`)

Wraps V2 analysis + upload summary into **`MovitPostTrainingReport`**, then **`PostTrainingReportLegacyJson.encode`** produces the `legacyReport` JSON blob attached to uploads.

Verified round-trip: `PostTrainingReportUploadTest` — legacy JSON survives `WorkoutUploadMapper` unchanged.

---

## Exercise JSON schema consumption

Training engine reads **`ExerciseConfig`** parsed from sync cache (`TrainingConfigRepository`):

| Config section | Engine use | Report use |
|----------------|------------|------------|
| `trackedJoints[]` | Angle extraction, bilateral | Error analysis joint names |
| `stateRanges` / `stateMessages` | Per-frame evaluation | Danger alerts, tips |
| `countingMethod`, `isBilateral` | Rep counter mode | Config snapshot |
| `reportMetrics` | `shouldShow()` gates (UI TBD) | Metric visibility |
| `positionChecks[]` | Setup + in-rep checks | Timeline markers |
| `holdConfig` | Hold timer FSM | Hold summary |

Parser: `ExerciseConfigParser` in `core/training-engine/…/config/`.

On-demand fetch: `WorkoutSessionSyncRepository.syncTrainingConfig` extracts embedded exercises and calls `trainingConfig.applySyncExercises`.

---

## Upload pipeline

### 1. Build `WorkoutUpload` (engine journal)

`MotionRecorder.buildWorkoutUpload()` → id, exerciseId, timestamp, counts, repMetrics[], executionMetrics.

### 2. Build legacy report (optional but production-default)

```kotlin
// TrainingSessionWriteCoordinator / ViewModel path
val report = MovitPostTrainingReportBuilder.build(upload, summary, exerciseConfig, sessionQuality)
val legacyJson = PostTrainingReportLegacyJson.encode(report)
```

### 3. Map to API DTO (`WorkoutUploadMapper`)

```14:36:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/WorkoutUploadMapper.kt
object WorkoutUploadMapper {
    fun toUploadRequest(
        upload: WorkoutUpload,
        context: String? = null,
        workoutGroupId: String? = null,
        workoutTemplateId: String? = null,
        legacyReport: JsonElement? = null,
    ): WorkoutExecutionUploadRequestDto = WorkoutExecutionUploadRequestDto(
        id = upload.id,
        exerciseId = upload.exerciseId,
        // …
        repMetrics = upload.repMetrics.map { it.toDto() },
        executionMetrics = upload.executionMetrics.toDto(),
        legacyReport = legacyReport,
    )
```

**Scaling:** all ×10 device integers divided by 10 before wire format (see [12-Mobile-API-Contract.md](12-Mobile-API-Contract.md) § Metric scaling).

### 4. Coordinator write (`TrainingSessionWriteCoordinator`)

```42:58:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/TrainingSessionWriteCoordinator.kt
    suspend fun uploadWorkoutExecution(
        upload: WorkoutUpload,
        context: String? = null,
        workoutGroupId: String? = null,
        workoutTemplateId: String? = null,
        legacyReport: JsonElement? = null,
        operationId: String? = null,
    ): AppResult<String> {
        val request = WorkoutUploadMapper.toUploadRequest(/* … */)
        reportsSync.patchExerciseMetricsFromUpload(request)
        return mobileWrites.uploadWorkoutExecution(request, operationId = operationId ?: upload.id)
    }
```

Optimistic local patch: `ReportsSyncRepository.patchExerciseMetricsFromUpload` updates cached dashboard metrics before server ack.

### 5. Outbox → API

`OfflineWriteQueue.enqueueWorkoutExecutionUpload` → `OutboxDispatcher` → `MovitMobileApi.uploadWorkoutExecution`.

### 6. Backend persist

```15:31:backend/src/modules/workout-executions/mobile-workout-executions.controller.ts
  @Post()
  async upload(@Req() req: Request, @Body() body: WorkoutExecutionUploadPayload, @Res({ passthrough: true }) res: Response) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const payload = body;
      if (!payload?.id || !payload?.exerciseId || !payload?.executionMetrics) {
        res.status(400);
        return { success: false, error: 'Missing required fields: id, exerciseId, executionMetrics' };
      }

      const execution = await saveWorkoutExecution(authResult.userId, payload);
      return { success: true, data: execution };
```

`saveWorkoutExecution` writes relational `WorkoutExecution`, `WorkoutExecutionMetrics`, and `RepMetrics` rows (Prisma). `legacyReport` stored as JSON column for backward-compatible admin/report views.

---

## `legacyReport` shape

Backend type: `LegacyReportData` in `workout-executions.types.ts`.

| Section | Content |
|---------|---------|
| `summary` | totalReps, countedReps, averageScore, durationMs, rating |
| `dangerAlerts[]` | repNumber, jointCode, actualAngle, localized message |
| `errorAnalysis[]` | jointCode, errorCount, percentage |
| `frameCaptures[]` | peak/danger frame URIs |
| `improvementTips[]` | priority, localized message |
| *(KMP extensions)* | `overallQuality`, `sessionQuality`, `repTimeline`, `consistency`, etc. |

Relational metrics (`executionMetrics`, `repMetrics[]`) are the **canonical** store for analytics. `legacyReport` carries rich UI/analysis sections not yet normalized to Prisma columns.

Composite scores (Form / Safety / Control) live in `legacyReport.overallQuality` only — see [Metrics-As-Built.md](../../Metrics/Metrics-As-Built.md) § Composite scores.

---

## Planned workout session report

When completing a program day, summary fields go to **`PlannedWorkoutCompleteRequestDto`**:

| Field | Source |
|-------|--------|
| `totalDurationMs`, `totalReps`, set counts | Session orchestrator |
| `avgAccuracy` | Completion rate |
| `avgFormScore` | Mean rep form scores |
| `rpe` | User input post-workout |
| `report` | Full `MovitSessionReport` JSON (multi-exercise) |

Endpoint: `POST /mobile/planned-workouts/{id}/complete` — separate from per-exercise execution uploads.

---

## Metrics storage comparison

| Layer | Format | Scale |
|-------|--------|-------|
| Device journal (`RepMetrics`, `WorkoutExecutionMetrics`) | Kotlin `Short`/`Int` | ×10 for percentages |
| API DTO (`RepMetricsDto`, `ExecutionMetricsDto`) | `Float` | ÷10 from device |
| Prisma DB | `Int` / `Float` / `Json` | Same as DTO |
| `legacyReport` JSON | Mixed | Scores often 0–100 float |

### Known gaps (not in upload DTO)

| Metric | Calculated | Uploaded | DB |
|--------|:----------:|:--------:|:--:|
| `velocityLoss` | ✓ | — | — |
| `tempoConsistency` | ✓ | — | — |
| Composite Form/Safety/Control | ✓ | via `legacyReport` | JSON only |

Tracked in [Metrics-As-Built.md](../../Metrics/Metrics-As-Built.md) and [Integration-Gap-Tracker.md](../Integration-Gap-Tracker.md).

---

## Explore (free) workout upload

Multi-exercise sessions batch via `ExploreWorkoutUploadRequestDto`:

```
workoutGroupId (shared UUID)
  └── executions[]: WorkoutExecutionUploadRequestDto × N
        each with context, workoutGroupId, optional legacyReport
```

KMP: `MovitMobileApi.uploadExploreWorkout` → `POST /mobile/workout-executions/explore`.

---

## Related docs

| Doc | Topic |
|-----|-------|
| [12-Mobile-API-Contract.md](12-Mobile-API-Contract.md) | DTO fields, endpoints, errors |
| [13-Data-Sync-In-Mobile.md](13-Data-Sync-In-Mobile.md) | Outbox, exercise cache |
| [training-engine.md](../training-engine.md) | Frame pipeline, rep counting |
| [Metrics-As-Built.md](../../Metrics/Metrics-As-Built.md) | 18-metric pipeline matrix |
| [Metrics-Complete-Reference.md](../../Metrics/Metrics-Complete-Reference.md) | Metric definitions |
| [Post-Training-Report-Review.md](../../Product-Master/Post-Training-Report-Review.md) | Product spec |
| [API_ENDPOINTS.md](../../Contracts/API_ENDPOINTS.md) | REST routes |
