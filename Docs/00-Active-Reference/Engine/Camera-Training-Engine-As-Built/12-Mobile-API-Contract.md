| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | KMP mobile ↔ backend HTTP contract for training, sync, and uploads |
| **Code (KMP)** | `kmp-app/core/network/MovitMobileApi.kt`, `kmp-app/core/network/dto/TrainingApiDto.kt`, `kmp-app/core/data/repository/WorkoutUploadMapper.kt` |
| **Code (backend)** | `backend/src/modules/workout-executions/mobile-workout-executions.controller.ts`, `backend/src/modules/workout-executions/mobile-planned-workouts.controller.ts`, `backend/src/modules/workout-executions/workout-executions.types.ts` |
| **Route catalog** | [API_ENDPOINTS.md](../../Contracts/API_ENDPOINTS.md) § Mobile Training |
| **Metrics scaling** | [Metrics-As-Built.md](../../Metrics/Metrics-As-Built.md) § Storage scale |
| **Verified** | 2026-07-04 |

# Mobile API contract (KMP ↔ backend)

> **Naming:** Domain terms follow [Workout-Domain-Naming.md](../../Contracts/Workout-Domain-Naming.md) (`PlannedWorkout`, `WorkoutTemplate`, `WorkoutExecution`).

KMP mobile talks to the NestJS backend through **`MovitMobileApi`** — a Ktor client wrapper over `/api/mobile/*` routes. DTOs live in `kmp-app/core/network/dto/`; training-specific types are in **`TrainingApiDto.kt`**. Backend mirrors the upload shapes in **`workout-executions.types.ts`**.

Contract parity is enforced by `MovitMobileApiContractTest`, `LegacyKmpContractParityTest`, and `MobileApiContractRegistry`.

---

## Auth

| Concern | KMP | Backend |
|---------|-----|---------|
| Header | `Authorization: Bearer <accessToken>` via `MovitPlatformBindings.authHeader()` | `verifyMobileToken(req)` in each mobile controller |
| Token refresh | `POST api/mobile/auth/refresh` → `RefreshTokenRequestDto` | Returns new `AuthTokensDto` |
| Unauthenticated sync | `MovitSyncOrchestrator` returns `SyncOutcome.Offline` or `Error("Sign in to sync.")` | 401 `{ success: false, error: "Unauthorized" }` |

All training upload endpoints require a valid bearer token. KMP does not send cookies.

---

## Client entry point

**Class:** `com.movit.core.network.MovitMobileApi`

Base URL comes from `MovitPlatformBindings.apiBaseUrl()`. Paths are normalized under `/api/…`:

```72:80:kmp-app/core/network/src/commonMain/kotlin/com/movit/core/network/MovitMobileApi.kt
class MovitMobileApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String,
) {
    private fun base(path: String): String {
        val root = baseUrlProvider().trimEnd('/')
        val normalized = path.removePrefix("/")
        return "$root/$normalized"
    }
```

---

## Training endpoints (this doc's scope)

Full route list: [API_ENDPOINTS.md](../../Contracts/API_ENDPOINTS.md) § **Mobile Training**.

### Catalog sync

| Method | Path | KMP method | Response DTO |
|--------|------|------------|--------------|
| GET | `/mobile/sync` | `fetchSync` / `fetchMobileSync` | `MobileSyncApiResponse` |
| GET | `/mobile/workout-templates/{id}/training-config` | `fetchWorkoutTrainingConfig` | `TrainingConfigApiResponse` |
| GET | `/mobile/workout-templates/{slug}/audio-manifest` | `fetchWorkoutAudioManifest` | `EntityAudioManifestApiResponse` |
| GET | `/mobile/exercises/{slug}/audio-manifest` | `fetchExerciseAudioManifest` | `EntityAudioManifestApiResponse` |

### Planned workout lifecycle

| Method | Path | KMP method | Request DTO | Response DTO |
|--------|------|------------|-------------|--------------|
| POST | `/mobile/planned-workouts/{id}/start` | `startPlannedWorkout` | `PlannedWorkoutStartRequestDto` | `PlannedWorkoutApiResponse` |
| POST | `/mobile/planned-workouts/{id}/complete` | `completePlannedWorkout` | `PlannedWorkoutCompleteRequestDto` | `PlannedWorkoutApiResponse` |
| POST | `/mobile/planned-workouts/{id}/report` | `reportPlannedWorkout` | `PlannedWorkoutCompleteRequestDto` | `PlannedWorkoutApiResponse` |

Backend controller: `MobilePlannedWorkoutsController` — `/report` is a legacy alias; new clients should prefer `/complete`.

### Workout execution upload

| Method | Path | KMP method | Request DTO | Response DTO |
|--------|------|------------|-------------|--------------|
| POST | `/mobile/workout-executions` | `uploadWorkoutExecution` | `WorkoutExecutionUploadRequestDto` | `WorkoutExecutionApiResponse` |
| POST | `/mobile/workout-executions/explore` | `uploadExploreWorkout` | `ExploreWorkoutUploadRequestDto` | `ExploreWorkoutApiResponse` |
| GET | `/mobile/workout-executions` | *(not wired in KMP yet)* | — | list + meta |
| GET | `/mobile/workout-executions/:exerciseId` | *(deferred)* | — | per-exercise history |
| GET | `/mobile/workout-executions/stats` | *(deferred)* | — | home stats aggregate |

Backend controller: `MobileWorkoutExecutionsController`.

---

## DTO reference (`TrainingApiDto.kt`)

### Training config

```8:13:kmp-app/core/network/src/commonMain/kotlin/com/movit/core/network/dto/TrainingApiDto.kt
@Serializable
data class TrainingConfigApiResponse(
    val success: Boolean = false,
    val data: JsonElement? = null,
    val error: String? = null,
)
```

`data` is opaque JSON at the network layer; `TrainingConfigRepository` + `ExerciseConfigParser` deserialize it into typed `ExerciseConfigRecord` objects (see [13-Data-Sync-In-Mobile.md](13-Data-Sync-In-Mobile.md)).

### Planned workout lifecycle

**Start request** — required fields enforced by backend (`weekNumber`, `dayNumber`):

| Field | Type | Notes |
|-------|------|-------|
| `programId` | `String?` | Optional program context |
| `weekNumber` | `Int` | Required |
| `dayNumber` | `Int` | Required |
| `startedAt` | `Long?` | Unix ms |

**Complete / report request** — summary fields for the planned-workout report row:

| Field | Type | Notes |
|-------|------|-------|
| `completedAt` | `Long?` | Unix ms |
| `totalDurationMs` | `Int?` | Session wall time |
| `totalExercises` / `totalSets` / `completedSets` / `totalReps` | `Int?` | Counts |
| `avgAccuracy` | `Float?` | Completion rate (reps done / planned × 100) |
| `avgFormScore` | `Float?` | Form quality 0–100 |
| `rpe` | `Int?` | User perceived exertion 1–10 |
| `report` | `JsonElement?` | Full session report blob |

Backend types mirror these in `PlannedWorkoutStartPayload` / `PlannedWorkoutCompletePayload` (`workout-executions.types.ts`).

### Workout execution upload

**Per-rep metrics** (`RepMetricsDto` / `RepMetricsDataDto`):

| Field | DTO type | Backend type | Scale on wire |
|-------|----------|--------------|---------------|
| `metrics.rom` | `Float` | `number` | ÷10 from device (see below) |
| `metrics.symmetry` | `Float?` | `number \| null` | ÷10 |
| `metrics.stability` | `Float` | `number` | ÷10 |
| `metrics.tempo` | `List<Int>` | `number[]` | ms [ecc, iso, con] — no scaling |
| `metrics.velocity` | `Float?` | `number \| null` | raw float (device stores ÷10 internally) |
| `metrics.formScore` | `Float` | `number` | ÷10 |
| `metrics.alignmentAccuracy` | `Float` | `number` | ÷10 |
| `score` | `Float` | `number` | ÷10 |
| `worstState` | `Int` | `number` | 0=PERFECT … 4=DANGER |
| `durationMs` | `Int` | `number` | ms |
| `side` | `String?` | `'left' \| 'right' \| null` | bilateral only |

**Session aggregate** (`ExecutionMetricsDto`):

| Field | Notes |
|-------|-------|
| `avgRom`, `avgSymmetry`, `avgStability`, `avgFormScore`, `avgAlignmentAccuracy` | Averages; ÷10 on upload |
| `avgTempo` | `[ecc, iso, con]` ms |
| `avgVelocity` | Optional |
| `totalTUT` | Σ rep duration ms |
| `totalVolume`, `maxWeight`, `est1RM` | Load metrics; nullable |
| `formConsistency`, `fatigueIndex` | Quality; ÷10 where applicable |
| `relativeStrength`, `intensityPercentage` | Future; nullable |

**Upload envelope** (`WorkoutExecutionUploadRequestDto`):

| Field | Required | Notes |
|-------|:--------:|-------|
| `id` | ✓ | Client-generated execution UUID |
| `exerciseId` | ✓ | Backend exercise slug or ID |
| `timestamp` | ✓ | Unix ms |
| `durationMs`, `totalReps`, `countedReps`, `invalidReps` | ✓ | Session counts |
| `executionMetrics` | ✓ | Backend validates presence |
| `repMetrics` | | Per-rep array |
| `weightKg`, `weightUnit` | | Default `"kg"` |
| `context` | | `WorkoutExecutionContext` enum string |
| `workoutGroupId` | | Shared ID for multi-exercise explore runs |
| `workoutTemplateId` | | Source template |
| `legacyReport` | | Optional PostTrainingReport JSON (see [14-Report-And-Extracted-Metrics.md](14-Report-And-Extracted-Metrics.md)) |

**Explore batch** (`ExploreWorkoutUploadRequestDto`):

| Field | Required |
|-------|:--------:|
| `workoutGroupId` | ✓ |
| `context` | ✓ |
| `executions[]` | ✓ (non-empty) |
| `workoutTemplateId`, `isCustomized` | |

Each execution in `executions[]` repeats the single-upload shape with shared `workoutGroupId`.

---

## Metric scaling (×10)

The training engine stores most percentage-like values as **integers × 10** on device (`Short` / `Int`, e.g. `850` = 85.0%). **`WorkoutUploadMapper`** divides by 10 before serialization:

```48:56:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/WorkoutUploadMapper.kt
    private fun RepMetrics.toDto(): RepMetricsDto = RepMetricsDto(
        rom = rom / 10f,
        symmetry = symmetry?.let { it / 10f },
        stability = stability / 10f,
        tempo = tempo.toList(),
        velocity = velocity?.toFloat(),
        formScore = formScore / 10f,
        alignmentAccuracy = alignmentAccuracy / 10f,
    )
```

Backend comments document the same contract:

```15:23:backend/src/modules/workout-executions/workout-executions.types.ts
export interface RepMetrics {
  rom: number;              // Range of Motion × 10
  symmetry: number | null;  // Bilateral symmetry × 10
  stability: number;        // Core stability × 10
  tempo: number[];          // [eccentric, iso, concentric] in ms
  velocity: number | null;  // Mean velocity × 100
  formScore: number;        // Form score × 10
  alignmentAccuracy: number; // Alignment × 10
}
```

> **Note:** Backend comments say velocity × 100; KMP sends `velocity` as a raw float from the device model. See [Metrics-As-Built.md](../../Metrics/Metrics-As-Built.md) for the full pipeline matrix and known gaps (`velocityLoss`, `tempoConsistency` not in DTO).

---

## Planned workout lifecycle (sequence)

```mermaid
sequenceDiagram
    participant VM as TrainingSessionViewModel
    participant TW as TrainingSessionWriteCoordinator
    participant OB as OfflineWriteQueue
    participant API as MovitMobileApi
    participant BE as MobilePlannedWorkoutsController

    VM->>TW: startPlannedWorkout(workoutId, week, day)
    TW->>OB: enqueue PLANNED_WORKOUT_START
    OB->>API: POST /planned-workouts/{id}/start
    API->>BE: PlannedWorkoutStartPayload
    BE-->>API: PlannedWorkoutReportDto (status=in_progress)

    Note over VM: User completes exercises; per-exercise uploads via WORKOUT_EXECUTION_UPLOAD

    VM->>TW: completePlannedWorkout(workoutId, summary + report JSON)
    TW->>OB: enqueue PLANNED_WORKOUT_COMPLETE
    OB->>API: POST /planned-workouts/{id}/complete
    BE-->>API: PlannedWorkoutReportDto (status=completed)
```

KMP paths:

- **Start:** `MovitMobileApi.startPlannedWorkout` → `OutboxDispatcher` → `POST api/mobile/planned-workouts/{workoutId}/start`
- **Complete:** `completePlannedWorkout` → `POST …/complete`
- **Report (legacy):** `reportPlannedWorkout` → `POST …/report` — same payload as complete; backend calls `updatePlannedWorkoutReport` instead of `completePlannedWorkoutReport`

Offline: all three operations go through **`OfflineWriteQueue`** first; replay happens on next sync cycle or when network returns ([13-Data-Sync-In-Mobile.md](13-Data-Sync-In-Mobile.md)).

---

## Error handling

### KMP client

`MovitMobileApi` methods return `Result<T>`. Non-2xx HTTP status throws; callers map to `AppResult.Failure` or outbox retry.

Sync-specific: `fetchSync` additionally checks `body.success`:

```445:449:kmp-app/core/network/src/commonMain/kotlin/com/movit/core/network/MovitMobileApi.kt
        val body = response.body<MobileSyncApiResponse>()
        if (!body.success) {
            error(body.error ?: "Sync request failed.")
        }
        body
```

### Backend controllers

Standard envelope: `{ success: boolean, data?: T, error?: string }`.

| HTTP | When | Example `error` |
|------|------|-----------------|
| 401 | Missing/invalid token | `"Unauthorized"` |
| 400 | Validation | `"Missing required fields: id, exerciseId, executionMetrics"` |
| 500 | Server/Prisma failure | Exception message |

### Outbox conflict policy

`OutboxDispatcher` classifies failures:

| Outcome | Meaning | Outbox action |
|---------|---------|---------------|
| `SUCCESS` | 2xx | Mark succeeded |
| `SERVER_WINS` | 409-style server authority | Mark succeeded (drop local) |
| `PERMANENT_FAILURE` | 4xx client error | `FAILED_PERMANENT` |
| `RETRYABLE` | Network/5xx | Stay `PENDING`, increment attempts |

---

## Upload path (single exercise)

```
MotionRecorder → WorkoutUpload (engine journal)
  → WorkoutUploadMapper.toUploadRequest (÷10 scaling + optional legacyReport)
  → TrainingSessionWriteCoordinator.uploadWorkoutExecution
  → OfflineWriteQueue.enqueueWorkoutExecutionUpload
  → OutboxDispatcher → MovitMobileApi.uploadWorkoutExecution
  → MobileWorkoutExecutionsController.upload → saveWorkoutExecution
```

See [14-Report-And-Extracted-Metrics.md](14-Report-And-Extracted-Metrics.md) for metrics calculation and `legacyReport` construction.

---

## Related docs

| Doc | Topic |
|-----|-------|
| [13-Data-Sync-In-Mobile.md](13-Data-Sync-In-Mobile.md) | SQLDelight cache, outbox, offline sync |
| [14-Report-And-Extracted-Metrics.md](14-Report-And-Extracted-Metrics.md) | Metrics pipeline, report upload |
| [training-engine.md](../training-engine.md) | Per-frame engine pipeline |
| [API_ENDPOINTS.md](../../Contracts/API_ENDPOINTS.md) | Full REST catalog |
| [Metrics-As-Built.md](../../Metrics/Metrics-As-Built.md) | 18-metric pipeline matrix |
| [Exercise-JSON-Schema.md](../../Contracts/Exercise-JSON-Schema.md) | Exercise config schema |
| [Backend-Contract-Matrix.md](../../../02-Roadmaps-And-Plans/UI-UX/Backend-Contract-Matrix.md) | Legacy ↔ KMP parity tracker |
