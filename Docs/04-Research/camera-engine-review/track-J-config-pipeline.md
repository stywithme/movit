# Track J — Exercise Config Pipeline

> **Scope**: `ExerciseConfigParser`, models/types, live support, serializers, `TrainingConfigRepository`, `TrainingSessionViewModel` config consumption, `TrainingPoseVariantResolver`, policy defaults.  
> **Brief**: `Camera-Engine-Review-Brief.md` §6 Track J (J1–J4), §8.  
> **Mode**: Read-only review.  
> **Verified-by**: pending

---

## J1 — `configRepository.getBySlug(slug)` caching vs re-parse

**Answer**: `getBySlug` does **not** re-parse raw JSON on each call. JSON parsing happens at **sync/seed** time; runtime reads deserialize a stored `ExerciseConfigRecord` and hit an in-memory LRU cache.

**Call chain**

1. `getBySlug(slug)` → `resolveBySlug(slug)` (`TrainingConfigRepository.kt:30`, `:57-68`)
2. **Memory LRU** (`parsedRecordCache`, max 8 entries): return on hit unless message-library fingerprint is stale (`:59-64`)
3. **Disk read**: `readRecordFromDisk` → `MovitCachePolicy.readJson(..., ExerciseConfigRecord.serializer())` (`:208-220`) — kotlinx deserialization of the persisted record, not `ExerciseConfigParser`
4. Optional **message-library merge** on read (`mergeRecordForRead`, `:222-237`)
5. Result cached in LRU (`:66`)

**When JSON is parsed**

- `applySyncExercises` → `ExerciseConfigParser.parseRecords(exercises)` (`:97`)
- `seedRecord` / tests / `SetupProbeDefaults` lazy init

**VM usage**

- Init: `exerciseConfig = configRepository.getBySlug(activeSlug)?.config` (`TrainingSessionViewModel.kt:124`)
- Flow transition: `exerciseConfig = configRepository.getBySlug(activeSlug)?.config` in `applyFlowExercise` (`:1371`)
- `reloadForNextFlowItem` calls `applyFlowExercise` then `buildEngine()` (`:1333-1339`) — second `getBySlug` benefits from LRU if slug was seen recently

**Cost on cache miss**: disk I/O + `ExerciseConfigRecord` deserialization + optional message merge — O(size of record), not full JSON tree re-parse from sync payload.

---

## J2 — Derived engine config vs `getMessagesForState` hot-path cost

**Answer (derived values)**: `primaryJointCodes`, `primaryPhaseJointConfigs`, and `phaseTimingConfig` are computed **once** at `MovitTrainingEngine` construction (`MovitTrainingEngine.kt:137`, `:212-214`, `:254-256`) via `ExerciseConfigLiveSupport.kt:43-56`. This matches the brief’s expectation.

**Answer (`getMessagesForState`)**: The brief cites `TrainingSessionViewModel.kt:697`; in the current tree that line is `prefetchAudio()`, not message lookup. Actual message resolution paths:

| Location | Trigger | Work per invocation |
|---|---|---|
| `JointEvaluator.evaluateOne` (`JointEvaluator.kt:130`) | **Every active joint, every processed frame** | `getMessagesForState` → O(1) state-map lookup in `StateMessages` (`TrackedJointExtensions.kt:20-27`) |
| `TrainingSessionViewModel.submitJointStateMessage` (`TrainingSessionViewModel.kt:862-872`) | Throttled via `FrameFeedbackEmitter.emitThrottledStateMessages` (`MovitTrainingEngine.kt:700-704`, `FrameFeedbackEmitter.kt:28-41`) | **O(J)** `trackedJoints.find { it.joint == jointCode }` then O(1) `getMessagesForState` |

`getMessagesForState` itself is **not** a linear scan over message lists; it indexes `StateMessages` by `JointState`. The VM path adds a linear joint lookup. `JointEvaluator` builds messages for all joints each frame even though only throttled states reach the VM callback — duplicate work on the engine hot path.

**Throttle context**: `emitThrottledStateMessages` skips TRANSITION/DANGER/WARNING and applies `FeedbackPolicy.shouldEmitStateMessage` cooldown (`stateMessageCooldownMs` default 2000 ms from `TimingPolicy.kt:22`, `:49`).

---

## J3 — Parser robustness, failure modes, `poseVariantIndex`, `error()` throws

**Designed failure modes**

| Stage | Behavior | Evidence |
|---|---|---|
| Sync parse | Malformed exercise elements **silently dropped** | `parseRecords` → `runCatching { parseRecord(it) }.getOrNull()` (`ExerciseConfigParser.kt:37-38`) |
| Missing slug config | `getBySlug` → `null`; VM sets `configUnavailable`, `buildEngine()` → `null` | `TrainingSessionViewModel.kt:124`, `:1636-1637`, `:1354` |
| Schema-level validation | `ExerciseConfig.validationIssues()` exists (`ExerciseConfigModels.kt:128-142`) | **Never called in production** (only `ExerciseConfigParserTest.kt:17`) |
| `sanitizeDefaults()` | Effectively identity copy (`ExerciseConfigModels.kt:173-185`) | No structural repair |

**`error()` / crash paths**

| Site | Condition | Effect |
|---|---|---|
| `MovitTrainingEngine` init (`MovitTrainingEngine.kt:131-133`) | `getPoseVariant(poseVariantIndex) == null` | `error("pose variant $poseVariantIndex missing")` — **uncaught `IllegalStateException` in `buildEngine()`** |
| `TrackedJoint.getStateUpRange/DownRange` (`ExerciseConfigModels.kt:34-38`) | PRIMARY without up/down ranges | `error(...)` when range accessors invoked |
| `TrackedJoint.getStateHoldRange` (`TrackedJointExtensions.kt:32-33`) | Hold joint without `range` | `error(...)` |
| `TrackedJointPhaseAdapter` (`TrackedJointPhaseAdapter.kt:16-25`) | Missing ranges | `joint.range!!` → NPE if hold path without `range` |

**`TrainingPoseVariantResolver`**

- Clamps `requested.coerceIn(0, variantCount - 1)` when `variantCount > 0` (`TrainingPoseVariantResolver.kt:16-18`)
- When `variantCount <= 0`, returns **0** (`:17-18`) — does **not** prevent engine throw on empty `poseVariants`
- Test documents this: `resolve_returnsZeroWhenNoVariants` (`TrainingPoseVariantResolverTest.kt:50-58`)

**Critical ordering bug (variant clamp uses stale config)**

`applyFlowExercise` resolves `activePoseVariantIndex` **before** loading the new exercise’s config:

```1365:1371:feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt
  private fun applyFlowExercise(exercise: TrainingFlowItem.Exercise, setNumber: Int) {
    activeSlug = exercise.slug
    ...
    activePoseVariantIndex = resolveActivePoseVariantIndex(exercise)
    ...
    exerciseConfig = configRepository.getBySlug(activeSlug)?.config
  }
```

`resolveActivePoseVariantIndex` uses `exerciseConfig?.poseVariants?.size` — still the **previous** exercise’s variant count (`TrainingSessionViewModel.kt:1744-1747`). Multi-exercise workouts can pass an out-of-range index into `MovitTrainingEngine`, triggering `error()` at `MovitTrainingEngine.kt:133`.

**Inverted / missing thresholds**

- `AngleRange.contains`: `angle >= min && angle <= max` (`ExerciseConfigTypes.kt:31`) — if `min > max`, no angle matches; evaluation falls through to WARNING/outermost logic
- Phase with no joints: empty `trackedJoints` — no immediate throw at engine init, but `validationIssues` would flag it; not enforced at runtime
- PRIMARY without `upRange`/`downRange`: latent `error()` when phase adapter or evaluator calls range accessors

---

## J4 — Default magic-number inventory

### Timing / rep intervals (triplicated)

| Value | `ExerciseConfigDefaults` | `TimingPolicy` companion | `ExerciseConfig.phaseTimingConfig()` fallback |
|---|---|---|---|
| Min rep interval 400 ms | `MIN_REP_INTERVAL_MS` (`ExerciseConfigLiveSupport.kt:38`) | `DEFAULT_MIN_REP_INTERVAL_MS` (`TimingPolicy.kt:43`) | via `getMinRepInterval` (`ExerciseConfigLiveSupport.kt:50`) |
| Max rep interval 5000 ms | `MAX_REP_INTERVAL_MS` (`:39`) | `DEFAULT_MAX_REP_INTERVAL_MS` (`TimingPolicy.kt:44`) | via `getMaxRepInterval` (`:51`) |
| Min phase duration 100 ms | `MIN_PHASE_DURATION_MS` (`:40`) | `DEFAULT_MIN_PHASE_DURATION_MS` (`TimingPolicy.kt:45`) | `calculateMinPhaseDuration(..., 4 phases)` (`:52-55`) |

**Single source of truth**: none — engine timing merges `RepCountingConfig` overrides with `ExerciseConfigDefaults`; global policy uses `TimingPolicy.DEFAULT` independently.

### `TimingPolicy` defaults (`TimingPolicy.kt:9-27`, `:42-49`)

| Field | Default |
|---|---|
| `defaultHoldDurationSeconds` | 30 |
| `defaultGracePeriodMs` | 3000 |
| `smoothingWindowSize` | 3 |
| `visibilityResumeCountdownMs` | 3000 |
| `visibilityMinVisibility` | **0.3f** |
| `visibilityGraceDurationMs` | 1000 |
| `visibilityWarningDurationMs` | 1000 |
| `visibilityPauseAfterMs` | 4000 |
| `minSpeakIntervalMs` | 1000 |
| `stateMessageCooldownMs` | 2000 |
| `cameraWarningEventCooldownMs` | 2000 |
| `maxRepsGuardMultiplier` | 3 |
| `minExecutionDurationFloorMs` | 180000 |
| `repExecutionMinRepTimeMultiplier` | 4 |
| `holdExecutionMaxTargetMultiplier` | 3 |

### `StabilityPolicy` defaults (`StabilityPolicy.kt:8-21`, `:29-31`)

| Field | Default |
|---|---|
| `stateHysteresisNormalPad` | 3.0° |
| `stateHysteresisPadWarning` | 2.0° |
| `stateHysteresisWarningDanger` | 2.0° |
| `minDangerFrames` | 3 |
| `minTransitionMarginDegrees` | 1.5° |
| `phaseHysteresisDegrees` | 3.0° (`DEFAULT_PHASE_HYSTERESIS`) |
| `boundaryBuffer` | 5.0° (`DEFAULT_BOUNDARY_BUFFER`) |
| `positionMinErrorFrames` | 2 |
| `positionHysteresisBuffer` | 0.01f |
| `anySideTiebreakGap` | 0.1f |
| `anySideVisibilityThreshold` | 0.5f |
| `anySideStrongMinVisibility` | 0.7f |

### `SetupValidationConfig` defaults (`SetupValidationConfig.kt:7-21`)

| Field | Default |
|---|---|
| `windowSize` / `cameraCheckWindowSize` | 12 |
| `requiredValid` / `cameraCheckRequired` | 9 |
| `closeThresholdDegrees` | 15.0° |
| `voiceCooldownMs` | 5000 |
| `countdownToleranceMs` | 150 |
| `countdownCancelMs` | 1200 |
| `countdownAngleToleranceDegrees` | `max(closeThreshold, 10)` → 15° |
| `countdownMinJointPresenceRatio` | 0.6 |
| `countdownRequireAllPrimaryPresent` | true |

### `SetupProbeDefaults` embedded JSON (`SetupProbeDefaults.kt:15-40`)

| Field | Value |
|---|---|
| Knee `startPose` | 150–180° |
| Knee `upRange.perfect` | 130–180° |
| Knee `downRange.perfect` | 60–100° |

### `ExerciseConfig` / `ExerciseConfigTypes` model defaults (selected)

| Location | Default |
|---|---|
| `RepCountingConfig.reps` | 12 (`ExerciseConfigTypes.kt:225`) |
| `PositionCheck.cooldownMs` | 2000 (`:319`) |
| `PositionCheck.minErrorFrames` | 3 (`:320`) — **differs** from `StabilityPolicy.positionMinErrorFrames` (2) |
| `TrackedJoint.startPose` | 0–180° (`ExerciseConfigModels.kt:16`) |
| `BilateralConfig.switchEvery` | 1 (`ExerciseConfigTypes.kt:219`) |
| `BilateralConfig.startSide` | `"right"` (`:220`) |
| `ReportMetricsConfig.primary` | `[FORM_SCORE]` (`:200`) |

### Cross-layer visibility inconsistency (related to J4)

| Layer | Default threshold |
|---|---|
| `TimingPolicy.visibilityMinVisibility` | **0.3f** (`TimingPolicy.kt:17`) — wired into `VisibilityMonitor` in engine (`MovitTrainingEngine.kt:318`) |
| `VisibilityMonitor` constructor default | **0.5f** (`VisibilityMonitor.kt:11`) |
| `Landmark.isVisible()` | **0.5f** (`Landmark.kt:14`) |
| `StabilityPolicy.anySideVisibilityThreshold` | **0.5f** (`StabilityPolicy.kt:19`) |
| `PoseFrameAssembler` | **0.5f** (`PoseFrameAssembler.kt:21`) |

---

## Findings

### [J-01] `applyFlowExercise` clamps pose variant using previous exercise config
- **Severity**: P1
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt:1369-1371`, `TrainingSessionViewModel.kt:1744-1747`, `core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/MovitTrainingEngine.kt:131-133`
- **Evidence**: `resolveActivePoseVariantIndex(exercise)` runs before `exerciseConfig = configRepository.getBySlug(activeSlug)?.config`, so `variantCount = exerciseConfig?.poseVariants?.size` reflects the **prior** exercise. `TrainingPoseVariantResolver.coerceIn(0, variantCount - 1)` can leave `activePoseVariantIndex` ≥ new exercise’s `poseVariants.size`, causing `error("pose variant $poseVariantIndex missing")` in `MovitTrainingEngine` init during `reloadForNextFlowItem` / flow init.
- **Impact**: Multi-exercise workout transition can crash the session (`IllegalStateException`) when consecutive exercises have different `poseVariants` counts; repro: exercise A with 4 variants → exercise B with 2 variants, flow `poseVariantIndex = 2`.
- **Fix-sketch**: Load `exerciseConfig` (or at least `variantCount` from `getBySlug(activeSlug)`) **before** calling `resolveActivePoseVariantIndex`; re-clamp after config load.
- **Effort**: S
- **Verified-by**: adversarial-grok-4.5-xhigh

### [J-02] `MovitTrainingEngine` throws uncaught `error()` on missing pose variant
- **Severity**: P0
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/MovitTrainingEngine.kt:131-133`, `feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt:1636-1652`
- **Evidence**:
```kotlin
private val poseVariant = exerciseConfig.getPoseVariant(poseVariantIndex)
    ?: error("pose variant $poseVariantIndex missing")
```
`buildEngine()` constructs `MovitTrainingEngine` without try/catch. Triggers on empty `poseVariants`, stale index (J-01), or `variantCount <= 0` path (`TrainingPoseVariantResolver.kt:17-18` returns 0; engine still fails).
- **Impact**: Session-start or inter-exercise reload crash (ANR risk if uncaught on main thread during `init` / `reloadForNextFlowItem`).
- **Fix-sketch**: Validate index against loaded config before engine construction; surface `configUnavailable` UI state instead of `error()`; or return nullable engine.
- **Effort**: M
- **Verified-by**: adversarial-grok-4.5-xhigh

### [J-03] `validationIssues()` never enforced before engine build
- **Severity**: P1
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `core/training-engine/src/commonMain/kotlin/com/movit/core/training/config/ExerciseConfigModels.kt:128-142`, `feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt:1636-1652`
- **Evidence**: `validationIssues` referenced only in `ExerciseConfigParserTest.kt:17`. `buildEngine()` passes config directly to `MovitTrainingEngine` with no preflight. Empty `poseVariants`, missing PRIMARY joints, etc. are detectable but not blocked.
- **Impact**: Invalid synced/seeded configs reach runtime; failures manifest as exceptions (`error()`, NPE in `TrackedJointPhaseAdapter`) or silent degraded counting.
- **Fix-sketch**: Call `config.validationIssues(activePoseVariantIndex)` in `buildEngine()`; if non-empty, set `configUnavailable` and skip engine creation.
- **Effort**: S
- **Verified-by**: adversarial-grok-4.5-xhigh

### [J-04] Sync parser silently drops malformed exercise JSON
- **Severity**: P2
- **Type**: Architecture
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `core/training-engine/src/commonMain/kotlin/com/movit/core/training/config/ExerciseConfigParser.kt:37-38`, `core/data/src/commonMain/kotlin/com/movit/core/data/repository/TrainingConfigRepository.kt:97`
- **Evidence**: `elements.mapNotNull { runCatching { parseRecord(it) }.getOrNull() }` — parse failures produce no log, no metric, no tombstone.
- **Impact**: Exercise missing from offline cache after sync with no user-visible explanation; `supports(slug)` false, `configUnavailable` true at runtime.
- **Fix-sketch**: Log/count failures; optionally persist parse-error entries for diagnostics.
- **Effort**: S
- **Verified-by**: pending

### [J-05] `JointEvaluator` builds state messages every frame for all joints
- **Severity**: P2
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `core/training-engine/src/commonMain/kotlin/com/movit/core/training/engine/evaluation/JointEvaluator.kt:130`, `core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/MovitTrainingEngine.kt:700-704`, `core/training-engine/src/commonMain/kotlin/com/movit/core/training/config/TrackedJointExtensions.kt:20-27`
- **Evidence**: `evaluateOne` always sets `messages = joint.getMessagesForState(...)`. VM `submitJointStateMessage` only runs on throttled `onJointStateMessage` callback. Message map lookup is O(1) but repeated per joint × ~25 fps; messages in `JointEval` are mostly discarded before feedback emit.
- **Impact**: Extra map lookups and `LocalizedText` list allocations on engine hot path (~Joints × fps); modest but avoidable given throttling downstream.
- **Fix-sketch**: Lazy message resolution in feedback path only, or pass `TrackedJoint` reference into throttled emit callback.
- **Effort**: M
- **Verified-by**: pending

### [J-06] VM `submitJointStateMessage` linear-scans `trackedJoints`
- **Severity**: P3
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `feature/training/src/commonMain/kotlin/com/movit/feature/training/TrainingSessionViewModel.kt:868-872`
- **Evidence**: `trackedJoints?.find { it.joint == jointCode }` on each throttled state message (cooldown ≥ 2 s per joint/state, not every frame).
- **Impact**: O(J) per emitted message, J typically 2–6 — negligible at throttle rate; avoidable with `associateBy { it.joint }` cached at config load.
- **Fix-sketch**: Cache `Map<String, TrackedJoint>` per `(slug, variantIndex)` when `exerciseConfig` updates.
- **Effort**: S
- **Verified-by**: pending

### [J-07] Triplicated rep-timing defaults (`ExerciseConfigDefaults` vs `TimingPolicy`)
- **Severity**: P3
- **Type**: Duplication
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `core/training-engine/src/commonMain/kotlin/com/movit/core/training/config/ExerciseConfigLiveSupport.kt:37-55`, `core/training-engine/src/commonMain/kotlin/com/movit/core/training/engine/policy/TimingPolicy.kt:42-45`
- **Evidence**: Identical literals 400 / 5000 / 100 ms defined in both `ExerciseConfigDefaults` and `TimingPolicy` companion; `phaseTimingConfig()` uses former, `TimingPolicy.DEFAULT` uses latter; drift risk on future edits.
- **Impact**: No user-visible bug today; maintenance hazard if one copy changes.
- **Fix-sketch**: Single `TrainingTimingDefaults` object referenced by both layers.
- **Effort**: S
- **Verified-by**: pending

### [J-08] Visibility threshold defaults diverge (0.3f vs 0.5f)
- **Severity**: P2
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `core/training-engine/src/commonMain/kotlin/com/movit/core/training/engine/policy/TimingPolicy.kt:17`, `core/training-engine/src/commonMain/kotlin/com/movit/core/training/visibility/VisibilityMonitor.kt:11`, `core/training-engine/src/commonMain/kotlin/com/movit/core/training/model/Landmark.kt:14`, `core/training-engine/src/commonMain/kotlin/com/movit/core/training/session/MovitTrainingEngine.kt:318`
- **Evidence**: Engine wires `timingPolicy.visibilityMinVisibility` (0.3f) into `VisibilityMonitor`; geometry/landmark visibility uses 0.5f defaults elsewhere. Joint considered visible for counting at 0.35 but invisible for angle assembly at 0.35.
- **Impact**: Potential inconsistent pause/skip vs angle quality near 0.3–0.5 visibility band; needs device validation.
- **Fix-sketch**: Document intentional split or unify to one `VisibilityDefaults` constant.
- **Effort**: S
- **Verified-by**: pending

### [J-09] `sanitizeDefaults()` is a no-op structural pass
- **Severity**: P3
- **Type**: Architecture
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `core/training-engine/src/commonMain/kotlin/com/movit/core/training/config/ExerciseConfigModels.kt:173-185`, `ExerciseConfigParser.kt:17-18`
- **Evidence**: `sanitizeDefaults()` copies lists unchanged; invoked on every `parseConfig` and disk persist (`withSanitizedConfig`). Does not fix inverted ranges, empty variants, or missing PRIMARY ranges.
- **Impact**: False sense of validation; corrupt configs persist verbatim.
- **Fix-sketch**: Implement real normalization or rename to reflect identity behavior.
- **Effort**: M
- **Verified-by**: pending

### [J-10] `getBySlug` uses bounded LRU (8) — adequate for typical session
- **Severity**: P3
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `core/data/src/commonMain/kotlin/com/movit/core/data/repository/TrainingConfigRepository.kt:28`, `:57-68`, `:327-328`
- **Evidence**: `parsedRecordCache = MovitLruCache<String, ExerciseConfigRecord>(PARSED_RECORD_CACHE_SIZE)` with `PARSED_RECORD_CACHE_SIZE = 8`. Workout with >8 distinct slugs evicts oldest parsed records; miss triggers disk deserialize only.
- **Impact**: Extra disk deserialize on slug churn — cheap relative to pose pipeline; not JSON re-parse.
- **Fix-sketch**: Monitor slug cardinality in long workouts; increase cache if profiling shows deserialize hotspots.
- **Effort**: S
- **Verified-by**: pending

---

## Coverage

### Files read (in scope)

| File | Status |
|---|---|
| `core/training-engine/.../config/ExerciseConfigParser.kt` | Read |
| `core/training-engine/.../config/ExerciseConfigModels.kt` | Read |
| `core/training-engine/.../config/ExerciseConfigTypes.kt` | Read |
| `core/training-engine/.../config/ExerciseConfigLiveSupport.kt` | Read |
| `core/training-engine/.../config/TrackedJointExtensions.kt` | Read |
| `core/training-engine/.../config/TrackedJointPhaseAdapter.kt` | Read |
| `core/training-engine/.../config/StateMessageValueSerializer.kt` | Read |
| `core/data/.../TrainingConfigRepository.kt` | Read |
| `feature/training/.../TrainingSessionViewModel.kt` (config paths) | Read (selected regions + grep) |
| `feature/training/.../TrainingPoseVariantResolver.kt` | Read |
| `core/training-engine/.../session/SetupProbeDefaults.kt` | Read |
| `core/training-engine/.../session/SetupValidationConfig.kt` | Read |
| `core/training-engine/.../engine/policy/TimingPolicy.kt` | Read |
| `core/training-engine/.../engine/policy/StabilityPolicy.kt` | Read |

### Supporting reads (call-chain evidence)

| File | Reason |
|---|---|
| `core/training-engine/.../session/MovitTrainingEngine.kt` | `error()` on pose variant; derived config init; message emit |
| `core/training-engine/.../engine/evaluation/JointEvaluator.kt` | Per-frame `getMessagesForState` |
| `core/training-engine/.../engine/feedback/FrameFeedbackEmitter.kt` | Message throttle |
| `core/data/.../cache/MovitLruCache.kt` | LRU semantics |
| `core/training-engine/.../session/PresenceSupervisorBridge.kt` | TimingPolicy → presence threshold mapping |
| Tests: `TrainingConfigRepositoryTest`, `ExerciseConfigParserTest`, `TrainingPoseVariantResolverTest` | Behavioral confirmation |

### Not read in depth (out of Track J scope)

- `ExerciseMessageLibraryMerger.kt` (message assignment resolution at sync)
- `MovitCachePolicy` / `MovitLocalStore` implementation details
- Full `TrainingSessionViewModel.kt` (1646 lines — config-adjacent regions only)
- `FeedbackPolicy` / `FeedbackScheduler` internals

### Tests touching Track J claims

| Test | Relevance |
|---|---|
| `TrainingConfigRepositoryTest` | Seed + alias; no cache-behavior assertion |
| `ExerciseConfigParserTest` | Fixture parse + `validationIssues().isEmpty()` |
| `TrainingPoseVariantResolverTest` | Clamp + `variantCount=0` → 0 |
| No production test for `applyFlowExercise` ordering or engine throw on bad variant | **Coverage gap** |

---

## Summary table

| ID | Title | Severity | Status |
|---|---|---|---|
| J-01 | `applyFlowExercise` clamps variant with stale config | P1 | CONFIRMED |
| J-02 | Engine `error()` on missing pose variant | P0 | CONFIRMED |
| J-03 | `validationIssues()` not enforced | P1 | CONFIRMED |
| J-04 | Silent parse drop on sync | P2 | CONFIRMED |
| J-05 | Per-frame message build in `JointEvaluator` | P2 | CONFIRMED |
| J-06 | VM linear `trackedJoints.find` | P3 | CONFIRMED |
| J-07 | Triplicated timing defaults | P3 | CONFIRMED |
| J-08 | Visibility 0.3f vs 0.5f split | P2 | CONFIRMED |
| J-09 | `sanitizeDefaults()` no-op | P3 | CONFIRMED |
| J-10 | LRU cache size 8 for parsed records | P3 | CONFIRMED |

**J1 answer**: Cached parsed records + disk deserialize; JSON parse at sync only.  
**J2 answer**: Engine derives once; messages resolved per-frame in evaluator, throttled in VM with O(J) joint find.  
**J3 answer**: Failures are mostly crash-or-silent; resolver does not fully protect engine; `error()` on bad `poseVariantIndex` confirmed.  
**J4 answer**: Inventory above; primary duplication in timing constants and visibility thresholds.
