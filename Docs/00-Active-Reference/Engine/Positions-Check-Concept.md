# Position Checks — Unified Reference

> **Last updated:** 2026-02-12  
> **Status:** Fully synced across Admin Dashboard, Backend, and Android Mobile.

## Overview

Position Checks validate **spatial relationships** between body landmarks during exercise execution. They complement the angle-based `FormValidator` by checking things like "knee over toe", "shoulders level", and "stance width".

Each exercise's `PoseVariant` can define multiple Position Checks. The `PositionValidator` (Android) evaluates them every frame and produces errors, warnings, and tips.

---

## Data Model

```typescript
interface PositionCheck {
  id: string;                    // Unique ID, e.g. "left_knee_over_toe"
  type: PositionCheckType;       // One of 7 types (see below)
  landmarks: {
    primary: string;             // Main landmark
    secondary: string;           // Comparison landmark
    tertiary?: string;           // Optional (alignment / distance_ratio)
    quaternary?: string;         // Optional (distance_ratio second pair)
  };
  condition: {
    operator: PositionOperator;  // Comparison operator (see below)
    threshold: number;           // Normalized value (0-1 range)
  };
  activePhases: string[];        // Phases where check runs
  errorMessage: LocalizedText;   // Feedback message
  severity: 'error' | 'warning' | 'tip';
  cooldownMs: number;            // Min ms between repeated alerts (default: 2000)
  minErrorFrames: number;        // Consecutive bad frames to confirm (default: 3)
}
```

---

## Check Types (7 — synced with Android `PositionCheckType` enum)

| Type | Description | Axis | Example |
|---|---|---|---|
| `forward_comparison` | Compare on forward axis | X (side view) / Z (front view) | Knee-over-toe check |
| `vertical_comparison` | Compare heights (above/below) | Y (all views) | Hands above shoulders |
| `sideways_comparison` | Compare lateral positions | Z (side view) / X (front view) | Elbow close to torso |
| `distance_ratio` | Ratio of two distances (4 landmarks) | Euclidean | Stance width vs shoulder width |
| `horizontal_alignment` | Points on same horizontal line | Y similarity | Shoulders level |
| `vertical_alignment` | Points on same vertical line | X similarity | Wrist over elbow |
| `depth_alignment` | Points at same depth from camera | Z similarity | Advanced: body not rotated |

### Camera Awareness

The `PositionValidator` dynamically selects axes based on camera position:
- **Side view:** Forward = X, Sideways = Z
- **Front/Back view:** Forward = Z, Sideways = X

It also auto-detects facing direction (left/right) by comparing shoulder Z-values, flipping axis interpretation accordingly.

---

## Operators (5 — synced with Android `PositionOperator` enum)

| Operator | Behavior | Used With |
|---|---|---|
| `should_not_exceed` | `primary - secondary ≤ threshold` → PASS | forward / vertical / sideways comparison |
| `should_exceed` | `primary - secondary ≥ threshold` → PASS | forward / vertical / sideways comparison |
| `approximately_equal` | `|primary - secondary| < threshold` → PASS | all comparison + alignment types |
| `greater_than_ratio` | `ratio > threshold` → PASS | distance_ratio only |
| `less_than_ratio` | `ratio < threshold` → PASS | distance_ratio only |

### Threshold

- Single value per check (no difficulty levels)
- For comparison types: normalized coordinate difference (0-1 range)
- For alignment types: max allowed deviation
- For distance_ratio: the ratio itself (e.g. 1.0 = equal distances)
- **Hysteresis buffer** of 0.02 is added automatically by `PositionValidator` to prevent state oscillation

---

## Multiple Checks

The system handles multiple checks per exercise correctly:

1. **Independent state** — each check has its own `errorFrameCounts[check.id]`
2. **Phase filtering** — only active checks run per phase (`activePhases`)
3. **Frame confirmation** — `minErrorFrames` consecutive bad frames required
4. **Cooldown** — `cooldownMs` prevents repeated alerts per check
5. **Severity routing** — errors affect scoring, warnings are feedback only, tips are suggestions

---

## Architecture Summary

```
Admin Dashboard (PositionChecksStep.tsx)
        │ configures
        ▼
Backend (exercises.validation.ts → Zod schema)
        │ stores & serves
        ▼
Android Mobile
  ├─ ExerciseConfig.kt (PositionCheck, PositionCheckType, PositionOperator)
  ├─ PositionValidator.kt (runtime validation engine)
  └─ TrainingEngine.kt (integrates results into feedback flow)
```

All three layers now share the same 7 types and 5 operators.
