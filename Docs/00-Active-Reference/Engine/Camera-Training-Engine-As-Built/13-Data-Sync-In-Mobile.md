| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | KMP offline-first data layer: SQLDelight cache, outbox, sync orchestration, exercise JSON |
| **Code** | `MovitSyncOrchestrator.kt`, `TrainingConfigRepository.kt`, `OutboxDispatcher.kt`, `OfflineWriteQueue.kt`, `kmp-app/core/data/src/commonMain/sqldelight/` |
| **API contract** | [12-Mobile-API-Contract.md](12-Mobile-API-Contract.md) |
| **Route catalog** | [API_ENDPOINTS.md](../../Contracts/API_ENDPOINTS.md) |
| **Verified** | 2026-07-04 |

# Data sync in mobile (KMP)

KMP mobile is **offline-first**: catalog and session data are cached locally in SQLDelight; writes go to a durable **outbox** and replay when connectivity returns. **`MovitSyncOrchestrator`** coordinates periodic pull sync; **`TrainingConfigRepository`** owns the exercise JSON cache consumed by the training engine.

---

## Architecture overview

```
┌─────────────────────────────────────────────────────────────┐
│                    MovitSyncOrchestrator                       │
│  fetchSync → hydrate caches → home/explore/reports refresh  │
└────────────┬───────────────────────────────┬────────────────┘
             │                               │
    ┌────────▼────────┐              ┌────────▼────────┐
    │ TrainingConfig │              │ OfflineWriteQueue│
    │   Repository   │              │  + OutboxDispatcher│
    └────────┬────────┘              └────────┬────────┘
             │                               │
    ┌────────▼───────────────────────────────▼────────┐
    │              MovitLocalStore (SQLDelight)        │
    │  json_cache_entry · outbox_entry · sync_metadata │
    │  session_journal_entry                           │
    └──────────────────────────────────────────────────┘
```

---

## SQLDelight schema

Location: `kmp-app/core/data/src/commonMain/sqldelight/com/movit/core/data/db/`

| Table | File | Purpose |
|-------|------|---------|
| `json_cache_entry` | `JsonCacheEntry.sq` | Key-value JSON blobs keyed by `(store, cache_key)` |
| `outbox_entry` | `Outbox.sq` | Durable write queue with status + retry counts |
| `sync_metadata` | `SyncMetadata.sq` | Last sync timestamp, entity counts, message stats |
| `session_journal_entry` | `SessionJournal.sq` | In-progress training journal checkpoints |

### JSON cache (`json_cache_entry`)

```1:7:kmp-app/core/data/src/commonMain/sqldelight/com/movit/core/data/db/JsonCacheEntry.sq
CREATE TABLE json_cache_entry (
    store TEXT NOT NULL,
    cache_key TEXT NOT NULL,
    json_payload TEXT NOT NULL,
    updated_at_epoch_ms INTEGER NOT NULL,
    PRIMARY KEY (store, cache_key)
);
```

Logical stores (via `MovitCacheKeys`):

| Store constant | Contents |
|----------------|----------|
| `EXERCISE_CONFIG_STORE` | Parsed exercise JSON per slug |
| `SESSION_STORE` | Effective plan, workout template training-config |
| `HOME_STORE`, `EXPLORE_STORE`, `REPORTS_STORE` | Screen-specific cached API responses |
| Others | Audio manifest, message library, preferences |

Access pattern: `MovitCachePolicy.readJson` / `writeJson` with kotlinx.serialization.

### Outbox (`outbox_entry`)

```1:10:kmp-app/core/data/src/commonMain/sqldelight/com/movit/core/data/db/Outbox.sq
CREATE TABLE outbox_entry (
    id TEXT NOT NULL PRIMARY KEY,
    operation_type TEXT NOT NULL,
    payload_json TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    created_at_epoch_ms INTEGER NOT NULL,
    attempts INTEGER NOT NULL,
    status TEXT NOT NULL,
    last_error TEXT
);
```

Statuses: `pending`, `in_flight`, `succeeded`, `failed_permanent`.

### Sync metadata (`sync_metadata`)

Tracks `last_sync_at` (ISO string from server), `version`, and scoped counters used by drift detection.

---

## MovitSyncOrchestrator

**Class:** `com.movit.core.data.sync.MovitSyncOrchestrator`

### Public API

| Method | Behavior |
|--------|----------|
| `syncIfNeeded(forceCheck, minIntervalMs)` | Skip if busy or within 5 min default interval |
| `fullRefresh()` | Force full sync (`updatedAfter = null`, `forceRefresh = true`) |
| `readColdOfflineBundle()` | Read cached home + explore + reports without network |

### Sync cycle (`runSyncCycle`)

```115:160:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/sync/MovitSyncOrchestrator.kt
    private suspend fun runSyncCycle(forceFullRefresh: Boolean): SyncOutcome {
        val bindings = platform()
        val auth = bindings.authHeader()
            ?: return readColdOfflineBundle().let { bundle ->
                if (bundle.home != null || bundle.explore != null) {
                    SyncOutcome.Offline(bundle)
                } else {
                    SyncOutcome.Error("Sign in to sync.")
                }
            }

        LegacyWorkoutSyncGate.drainLegacyExecutionsIfRegistered()
        offlineWrites.replayPending()

        val syncResponse = api.fetchSync(
            updatedAfter = if (forceFullRefresh) null else metadataStore.readLastSyncTimestamp(),
            forceRefresh = forceFullRefresh,
            authorization = auth,
        ).getOrElse {
            return SyncOutcome.Offline(readColdOfflineBundle())
        }
        // … drift detection, hydrate caches …
        trainingConfig.applySyncExercises(
            exercises = payload.exercises,
            deletedExerciseIds = payload.deletedExerciseIds,
            isFullSync = isFullSync,
        )
```

**Order of operations:**

1. Require auth (or return cold offline bundle)
2. Drain legacy OkHttp executions + replay outbox (pre-fetch)
3. `GET /mobile/sync` with incremental `updatedAfter` or full refresh
4. Backfill training config if local cache empty but server has exercises
5. Drift detection → auto full refresh if entity counts mismatch
6. Apply audio manifest + prefetch
7. Hydrate: exercises, system messages, preferences, planned workout reports, user programs, message library, explore catalog
8. Refresh home + reports dashboard
9. Replay outbox again (post-fetch)

### Sync timing

| Constant | Value | Location |
|----------|-------|----------|
| `DEFAULT_MIN_SYNC_INTERVAL_MS` | 5 minutes | `MovitSyncOrchestrator` companion |
| Background scheduler | Platform WorkManager / BGTask | `BackgroundSyncScheduler.kt` |
| Shell trigger | App foreground / pull-to-refresh | `MovitAppShellViewModel` |

### Sync outcomes

| Outcome | When |
|---------|------|
| `Success` | Network OK; caches updated |
| `Offline` | Network/auth failure but cold cache available |
| `Skipped` | Busy or within min interval |
| `Error` | No auth and no cached data |

---

## TrainingConfigRepository (exercise JSON cache)

**Class:** `com.movit.core.data.repository.TrainingConfigRepository`

Offline-safe cache for exercise training configs. Populated from:

1. **`GET /mobile/sync`** payload `data.exercises[]` (primary)
2. **`GET /mobile/workout-templates/{id}/training-config`** embedded exercises (session prefetch via `WorkoutSessionSyncRepository`)

### Key methods

| Method | Purpose |
|--------|---------|
| `getBySlug(slug)` | Resolve alias → return `ExerciseConfigRecord` |
| `getExercise(slug)` | Return typed `ExerciseConfig` for engine |
| `supports(slug)` | Fast slug index lookup |
| `applySyncExercises(exercises, deletedExerciseIds, isFullSync)` | Merge or replace cache |
| `applySyncMessageLibrary(templates)` | Merge feedback message text into cached configs |

### Full vs incremental sync

On `isFullSync = true`, repository wipes all slug keys and rebuilds index. Incremental merges by slug and removes `deletedExerciseIds`.

```67:88:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/repository/TrainingConfigRepository.kt
    fun applySyncExercises(
        exercises: List<JsonElement>,
        deletedExerciseIds: List<String> = emptyList(),
        isFullSync: Boolean = false,
    ) {
        if (isFullSync) {
            readSlugIndex().forEach { slug ->
                localStore.remove(MovitCacheKeys.EXERCISE_CONFIG_STORE, MovitCacheKeys.exerciseConfigKey(slug))
            }
            writeSlugIndex(emptyList())
            writeSlugAliasMap(emptyMap())
        }
        deletedExerciseIds.forEach { id ->
            findSlugById(id)?.let { slug ->
                localStore.remove(MovitCacheKeys.EXERCISE_CONFIG_STORE, MovitCacheKeys.exerciseConfigKey(slug))
                // …
            }
        }
```

Parsing: `ExerciseConfigParser.parseRecords(exercises)` → persisted as JSON in `EXERCISE_CONFIG_STORE`. Schema: [Exercise-JSON-Schema.md](../../Contracts/Exercise-JSON-Schema.md).

In-memory LRU (`PARSED_RECORD_CACHE_SIZE`) avoids re-parsing hot slugs during training.

---

## Outbox & offline writes

### OfflineWriteQueue

**Class:** `com.movit.core.data.outbox.OfflineWriteQueue`

Durable queue with optimistic local cache updates. Every write:

1. Insert `outbox_entry` (idempotent by `operationId`)
2. Apply optimistic cache patch (`OfflineWriteOptimisticCache`)
3. Attempt immediate replay if online

### Supported operation types

| `OutboxOperationType` | API call |
|-----------------------|----------|
| `PLANNED_WORKOUT_START` | `startPlannedWorkout` |
| `PLANNED_WORKOUT_COMPLETE` | `completePlannedWorkout` |
| `PLANNED_WORKOUT_REPORT` | `reportPlannedWorkout` |
| `WORKOUT_EXECUTION_UPLOAD` | `uploadWorkoutExecution` |
| `PLAN_COMPLETE` | `completePlan` |
| `EXERCISE_PREFERENCE_UPSERT/DELETE` | preference endpoints |
| `USER_PROGRAM_OVERRIDE_CREATE/DELETE` | override endpoints |
| `SAVE_DAY_CUSTOMIZATIONS` | `updateUserProgramCustomizations` |
| `PROGRESSION_MARK_SEEN` | `markProgressionSeen` |

### OutboxDispatcher

**Class:** `com.movit.core.data.outbox.OutboxDispatcher` (internal)

Decodes payload JSON → calls matching `MovitMobileApi` method:

```57:60:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/outbox/OutboxDispatcher.kt
                OutboxOperationType.WORKOUT_EXECUTION_UPLOAD -> {
                    val payload = MovitJson.decodeFromString<WorkoutExecutionUploadOutboxPayload>(entry.payload)
                    api.uploadWorkoutExecution(payload.request, authorization).getOrThrow()
                }
```

### Replay (`replayPending`)

```136:143:kmp-app/core/data/src/commonMain/kotlin/com/movit/core/data/outbox/OfflineWriteQueue.kt
    /** Replays pending entries; succeeded rows are skipped (idempotency). */
    suspend fun replayPending(): OutboxReplayResult {
        val auth = platform().authHeader()
            ?: return OutboxReplayResult(0, 0, 0, 0)

        if (!platform().isNetworkAvailable()) {
            return OutboxReplayResult(0, 0, 0, 0)
        }
```

Replay triggers:

- End of every sync cycle (twice: pre- and post-fetch)
- Immediately after enqueue when online
- `LegacyWorkoutSyncGate` coordination for OkHttp → KMP migration

---

## Offline behavior matrix

| Scenario | Read path | Write path |
|----------|-----------|------------|
| No network, cached catalog | `TrainingConfigRepository.getExercise(slug)` | Outbox enqueue; optimistic UI |
| No network, no cache | Error / empty state | Outbox only; replay later |
| No auth | Cold bundle if previously synced | Queue blocked until login |
| Mid-workout crash | `SessionJournalStore` checkpoint | Journal restored on relaunch |
| Server 409 conflict | — | Outbox `SERVER_WINS` → drop local op |
| Legacy OkHttp upload pending | — | `LegacyWorkoutSyncGate.awaitBeforeEnqueue()` |

### Session journal

`session_journal_entry` stores in-progress `SessionJournalSnapshot` JSON. **`TrainingSessionWriteCoordinator.checkpointJournal`** persists after each exercise; survives process death without waiting for upload.

### Week offline pack

`WeekOfflinePackPrefetcher` + `ColdOfflineBundleSeeder` pre-download effective plans and exercise configs for upcoming training days (pro users).

---

## Mobile sync payload (`GET /mobile/sync`)

DTO: `MobileSyncApiResponse` / `MobileSyncDataDto` (`PlanSyncDto.kt`)

| `data` field | Hydrated by |
|--------------|-------------|
| `exercises[]` | `TrainingConfigRepository.applySyncExercises` |
| `messageLibrary[]` | `MessageLibraryCache` + config merge |
| `systemMessages[]` | `SystemMessageCache` |
| `workoutTemplates[]`, `programs[]` | `SyncCatalogOfflineRepository`, `ExploreSyncRepository` |
| `deletedExerciseIds`, `deletedWorkoutTemplateIds`, `deletedProgramIds` | Tombstone handling |
| `userPrograms[]` | `UserProgramEnrollmentLocalStore`, day customizations |
| `userExercisePreferences[]` | `ExercisePreferenceLocalStore` |
| `plannedWorkoutReports[]` | `ReportsSyncRepository` |
| `audioManifest` | `AudioManifestCache` + `AudioPrefetchRunner` |

| `meta` field | Used for |
|--------------|----------|
| `isFullSync` | Force wipe + rebuild |
| `totalExercises`, `exercisesInResponse` | Drift + backfill detection |
| `messageLibraryStats` | Message merge validation |

---

## Training session sync (on-demand)

**Class:** `WorkoutSessionSyncRepository`

Not part of the global orchestrator — called when user opens a workout day:

| Method | API | Cache key |
|--------|-----|-----------|
| `syncEffectivePlan(userProgramId, week, day)` | `GET …/effective-plan?week=&day=` | `effectivePlanKey(…)` |
| `syncTrainingConfig(templateId)` | `GET …/workout-templates/{id}/training-config` | `workoutTemplateTrainingConfigKey(…)` |

Both fall back to cached JSON when offline (same pattern as orchestrator cold reads).

---

## Drift detection

**Class:** `MovitCacheDriftDetector`

Compares local entity counts + message library stats against `SyncMetaDto`. Mismatch triggers automatic full refresh without user action.

---

## Related docs

| Doc | Topic |
|-----|-------|
| [12-Mobile-API-Contract.md](12-Mobile-API-Contract.md) | Endpoints, DTOs, auth, scaling |
| [14-Report-And-Extracted-Metrics.md](14-Report-And-Extracted-Metrics.md) | Upload pipeline, metrics |
| [training-engine.md](../training-engine.md) | Engine consumes `ExerciseConfig` |
| [Exercise-JSON-Schema.md](../../Contracts/Exercise-JSON-Schema.md) | Exercise JSON shape |
| [KMP-Mobile-As-Built.md](../../Architecture-As-Built/KMP-Mobile-As-Built.md) | Module map |
