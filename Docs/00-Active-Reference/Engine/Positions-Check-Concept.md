| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | Position-check data model and runtime validation |
| **Code** | `com.movit.core.training.position.PositionValidator`, `com.movit.core.training.config.ExerciseConfigTypes` |
| **Verified** | 2026-06-22 |

# Position Checks — Unified Reference

Position checks validate **spatial relationships** between body landmarks during exercise execution. They complement angle-based joint evaluation by checking constraints such as knee-over-toe, shoulder level, and stance width.

Each exercise `PoseVariant` may define multiple position checks. `PositionValidator` evaluates them every frame (when landmarks are present) and produces errors, warnings, tips, and scene-axis warnings.

---

## Data model

Defined in `com.movit.core.training.config` (`ExerciseConfigTypes.kt`):

```kotlin
// Conceptual shape — see ExerciseConfigTypes.kt for full @Serializable definitions
PositionCheck(
  id: String,
  type: PositionCheckType,
  landmarks: PositionLandmarks(primary, secondary, tertiary?, quaternary?),
  condition: PositionCondition(operator, threshold),
  activePhases: List<String>,
  errorMessage: LocalizedText,
  severity: PositionSeverity,  // error | warning | tip
  cooldownMs: Long = 2000,
  minErrorFrames: Int = 3,
)
```

---

## Check types (7 — `PositionCheckType` enum)

| Type | Description | Axis | Example |
|------|-------------|------|---------|
| `FORWARD_COMPARISON` | Compare on forward axis | X (side) / Z (front) | Knee-over-toe |
| `VERTICAL_COMPARISON` | Compare heights | Y | Hands above shoulders |
| `SIDEWAYS_COMPARISON` | Lateral positions | Z (side) / X (front) | Elbow close to torso |
| `DISTANCE_RATIO` | Ratio of two distances (4 landmarks) | Euclidean | Stance vs shoulder width |
| `HORIZONTAL_ALIGNMENT` | Same horizontal line | Y similarity | Shoulders level |
| `VERTICAL_ALIGNMENT` | Same vertical line | X similarity | Wrist over elbow |
| `DEPTH_ALIGNMENT` | Same depth from camera | Z similarity | Body rotation guard |

### Camera awareness

`PositionValidator` selects axes from detected scene direction (`PoseSceneDetector` / locked scene):

- **Side view:** forward = X, sideways = Z
- **Front/back view:** forward = Z, sideways = X

Facing direction (left/right) is inferred from shoulder depth; axis interpretation flips accordingly.

---

## Operators (5 — `PositionOperator` enum)

| Operator | Behavior | Used with |
|----------|----------|-----------|
| `SHOULD_NOT_EXCEED` | `primary - secondary ≤ threshold` → pass | comparison types |
| `SHOULD_EXCEED` | `primary - secondary ≥ threshold` → pass | comparison types |
| `APPROXIMATELY_EQUAL` | `\|primary - secondary\| ≤ threshold` → pass | comparison + alignment |
| `GREATER_THAN_RATIO` | `ratio > threshold` → pass | `DISTANCE_RATIO` only |
| `LESS_THAN_RATIO` | `ratio < threshold` → pass | `DISTANCE_RATIO` only |

Threshold is a single normalized value per check. `PositionValidator` adds a **0.02 hysteresis buffer** on comparisons.

---

## Runtime integration

```
Admin Dashboard (position check editor)
        │ Zod / API
        ▼
Backend exercise JSON
        │ sync / cache
        ▼
KMP ExerciseConfig (poseVariants[].positionChecks)
        │
        ├─ SetupReadinessGate — scene axis match vs PoseSceneExpectation
        └─ MovitTrainingEngine.processFrame
              └─ FramePipelineExecutor.runMainPath
                    └─ PositionValidator.validate(landmarks, phase, isFlipped, isFrontCamera)
                          → errors / warnings / tips → RepCounter + FrameFeedbackEmitter
```

### Multiple checks per exercise

1. **Independent state** — per-check `errorFrameCounts[id]`
2. **Phase filtering** — `activePhases` gate
3. **Frame confirmation** — `minErrorFrames` consecutive failures
4. **Cooldown** — `cooldownMs` per check for repeated alerts
5. **Severity routing** — errors affect scoring; warnings/tips are feedback-only

---

## Related docs

- Engine pipeline: [`training-engine.md`](training-engine.md)
- Scene detection: [`pose-scene-detection-how-it-works.md`](pose-scene-detection-how-it-works.md)
- Bilateral mirroring of checks: [`Bilateral-Design.md`](Bilateral-Design.md)
