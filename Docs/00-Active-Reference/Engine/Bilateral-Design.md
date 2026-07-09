| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | Bilateral exercise config and runtime side switching |
| **Code** | `com.movit.core.training.bilateral.BilateralController`, `MovitTrainingEngine` |
| **Verified** | 2026-06-22 |

# Bilateral Exercise Design

## Overview

Bilateral exercises alternate between left and right sides per rep (e.g., alternating lunges, alternating bicep curls). This document defines the complete design for implementing bilateral support.

---

## Core Concept

```
Exercise: Alternating Lunge
├── bilateral: true
├── bilateralConfig: { switchEvery: 1, startSide: "right" }
├── poseVariant[0]: "Side View" (camera position)
│   ├── trackedJoints: [                         ← ALL joints (both sides + shared)
│   │   { joint: "right_knee",  role: PRIMARY, pairedWith: "left_knee",  ...ranges },
│   │   { joint: "left_knee",   role: PRIMARY, pairedWith: "right_knee", ...ranges },  ← auto-mirrored
│   │   { joint: "right_hip",   role: SECONDARY, pairedWith: "left_hip", ...ranges },
│   │   { joint: "left_hip",    role: SECONDARY, pairedWith: "right_hip",...ranges },  ← auto-mirrored
│   │   { joint: "spine",       role: SECONDARY, pairedWith: null,       ...ranges },  ← shared
│   │ ]
│   └── positionChecks: [ ...all checks including mirrored ones ]
```

**Key distinction from current alternating**: Bilateral does NOT use separate poseVariants. All joints live in ONE poseVariant. The TrainingEngine filters which joints are active per rep.

---

## Auto-Mirror Rules

### Joint Mirroring Map

| Paired Joints (auto-mirror) | Shared Joints (both sides) |
|-----------------------------|----------------------------|
| `left_shoulder` ↔ `right_shoulder` | `nose` |
| `left_shoulder_cross` ↔ `right_shoulder_cross` | `neck` |
| `left_elbow` ↔ `right_elbow` | `neck_left` |
| `left_wrist` ↔ `right_wrist` | `neck_right` |
| `left_hip` ↔ `right_hip` | `neck_spine` |
| `left_knee` ↔ `right_knee` | `spine` |
| `left_ankle` ↔ `right_ankle` | |

> **Rule**: Any joint starting with `left_` or `right_` is paired. Everything else is shared.

### Auto-Mirror Behavior in Admin

When bilateral is enabled and user configures the **Right Side**:

1. **User adds `right_knee` as PRIMARY** with upRange/downRange
   - System auto-creates `left_knee` as PRIMARY with **same ranges**
   - Both get `pairedWith` pointing to each other

2. **User adds `spine` as SECONDARY** with range
   - No mirror created — spine is shared
   - Used on BOTH sides during execution

3. **User changes ranges on `right_knee`**
   - `left_knee` ranges auto-update to match

4. **User adds position check**: "right_knee should_not_exceed right_ankle (Y axis)"
   - System auto-creates mirrored check: "left_knee should_not_exceed left_ankle (Y axis)"

5. **User adds position check**: "spine should_not_exceed nose"
   - No mirror — landmarks don't have left/right → shared on both sides

### Position Check Mirroring

A position check is mirrored when **any** of its landmarks are paired joints:

```
Original:  { primary: "right_knee", secondary: "right_ankle" }
Mirrored:  { primary: "left_knee",  secondary: "left_ankle"  }

Original:  { primary: "right_knee", secondary: "right_hip", tertiary: "spine" }
Mirrored:  { primary: "left_knee",  secondary: "left_hip",  tertiary: "spine" }

Not mirrored: { primary: "spine", secondary: "nose" }  ← no paired landmarks
```

Rule: For each landmark in the check, if it starts with `left_`/`right_`, swap it. Otherwise keep as-is.

---

## Data Model Changes

### Backend: Exercise Schema

```typescript
// REMOVE from Exercise
- isAlternating: boolean
- alternatingConfig: JSON

// ADD to Exercise
+ isBilateral: boolean           // default: false
+ bilateralConfig: JSON          // { switchEvery: number, startSide: 'left' | 'right' }
```

### Backend: BilateralConfig Type

```typescript
export interface BilateralConfig {
  switchEvery: number;           // Switch side every N reps (default: 1)
  startSide: 'left' | 'right';  // Which side starts (default: 'right')
}
```

### Android: ExerciseConfig

```kotlin
data class ExerciseConfig(
    // REMOVE:
    // val isAlternating: Boolean = false,
    // val alternatingConfig: AlternatingConfig? = null,
    
    // ADD:
    val isBilateral: Boolean = false,
    val bilateralConfig: BilateralConfig? = null,
    // ...
)

data class BilateralConfig(
    val switchEvery: Int = 1,
    val startSide: String = "right"  // "left" or "right"
)
```

### No change to TrackedJoint or PoseVariant

The `trackedJoints` array continues to hold ALL joints. The TrainingEngine filters at runtime based on the active side.

---

## Runtime behavior (KMP — as-built)

**Engine:** `MovitTrainingEngine` + `BilateralController` (`com.movit.core.training.bilateral`).

All tracked joints remain in config. The active side is applied at runtime by **mirroring angle lookup**, not by filtering the joint list.

### Side state (`BilateralController`)

| Field / method | Behavior |
|----------------|----------|
| `startSide` | From `bilateralConfig.startSide` (`"left"` / `"right"`) |
| `currentSide` | Active anatomical side for this rep segment |
| `isFlipped` | `true` when `currentSide != startSide` |
| `onRepCounted(newCount)` | Flips side every N reps: `switchMode` `EVERY_REP` → 1; `AFTER_ALL_REPS` → `targetReps`; else `switchEvery` |
| `resetToConfigStart()` | Called on `MovitTrainingEngine.start()` |

`completionTargetReps()` may double the per-side target when `AFTER_ALL_REPS` (both sides complete one block).

### Per-frame processing

```
PoseFrame
├── JointAngleTracker.extractTrackedAngles(..., isFlipped = bilateral.isFlipped)
│     └── buildAngleMap mirrors lookup codes via MIRROR_MAP when flipped
├── FramePipelineExecutor → PhaseStateMachine, JointEvaluator (all configured joints)
├── PositionValidator.validate(..., isBilateralFlipped, ...)
├── RepCompletionCoordinator → bilateral.onRepCounted(repCount) on counted rep
└── HoldExerciseCoordinator → bilateral.onRepCounted on hold completion
```

`JointAngleTracker` also dims the weaker side of `ANY_SIDE` pairs using landmark visibility (independent of bilateral flip).

### What does not change at runtime

- Pose config still lists **both** sides' joints and mirrored position checks.
- `PhaseStateMachine` averages primary joint angles present in the smoothed map.
- Pose capture (`MediaPipePoseDetector`, landmark smoothing) is side-agnostic.

---

## Admin Dashboard Changes

### Extras Step

**Remove**: Entire "Alternating Configuration" section  
**Add**: Simple "Bilateral" toggle (only shown when exercise has paired joints)

```
☐ Bilateral Exercise
    Switch every: [1] rep(s)
    Start side: [Right ▼]
```

### Joint Config Step

When bilateral is enabled:
1. Show joints for ONE side only (the `startSide`, e.g., "Right Side")
2. Auto-mirror to the other side in the background
3. Show a read-only preview of the mirrored side
4. Shared joints (spine, neck, etc.) shown normally without side label

### Position Checks Step

When bilateral is enabled:
1. User configures checks normally
2. Checks with paired landmarks auto-mirror
3. Show mirrored checks as read-only with "Auto-generated" label

---

## Reports & Symmetry

### Per-Side Rep Data

```kotlin
data class BilateralRepData(
    val leftReps: List<RepResult>,
    val rightReps: List<RepResult>
)
```

### Symmetry Calculation

```
Symmetry = 1 - |avgLeftScore - avgRightScore| / max(avgLeftScore, avgRightScore)
```

Measured per metric:
- Form score symmetry
- ROM symmetry
- Tempo symmetry

---

## Implementation Order

| Step | Layer | Description | Depends On |
|------|-------|-------------|------------|
| 1 | Backend Types | Add `BilateralConfig`, remove `AlternatingConfig` | — |
| 2 | Backend Service | Update create/update exercise logic | Step 1 |
| 3 | Backend JSON Builder | Generate bilateral-aware JSON for Android | Step 2 |
| 4 | Admin Types | Mirror backend type changes | Step 1 |
| 5 | Admin Extras Step | Replace alternating UI with bilateral toggle | Step 4 |
| 6 | Admin Joint Config | Auto-mirror logic for joints | Step 4 |
| 7 | Admin Position Checks | Auto-mirror logic for checks | Step 6 |
| 8 | Admin Submission | Map bilateral config on create/edit | Steps 5-7 |
| 9 | Android Models | Add `BilateralConfig`, remove `AlternatingConfig` | Step 3 |
| 10 | Android TrainingEngine | Per-rep side switching, per-side data | Step 9 |
| 11 | Android TrainingActivity | Remove set-level variant switching | Step 10 |
| 12 | Android Reports | Per-side metrics, symmetry calculation | Step 10 |

---

## Migration

Existing exercises with `isAlternating: true`:
- If they have 2 poseVariants with paired joints → convert to bilateral
- The bilateralConfig is generated from alternatingConfig
- Data migration script in backend

Existing exercises without alternating:
- No change needed (bilateral defaults to false)
