> **Status:** `ARCHIVED` — implemented or superseded; not current product truth.
> **Current SSOT:** [`Docs/00-Active-Reference/Engine/training-engine.md`](../../00-Active-Reference/Engine/training-engine.md) (`HoldExerciseCoordinator`)
> **Archived:** 2026-06-22

# Hold Exercise - Count doesn't start (Analysis Report)

## Problem Description
العد لا يبدأ في تمارين Hold رغم الوصول للزاوية المطلوبة.

## Architecture Flow (Hold Exercise)

```
1. PhaseStateMachine.update(primaryAngles)
   └─> For HOLD: updateHold(angle)
       └─> IDLE → COUNT when isInDownRange(angle)
       
2. isInDownRange(angle) uses:
   - downRangeMin = holdRange.outermostMin (or fallback: 0)
   - downRangeMax = holdRange.effectiveMax (or fallback: 80)
   - Zone: angle >= downRangeMin - hysteresis AND angle <= downRangeMax

3. TrainingEngine: isInHoldZone = (currentPhase == Phase.COUNT)
   └─> updateHoldTimer(isInHoldZone)
       └─> HoldTimer.update(isInHoldZone, currentTimeMs)
           └─> IDLE → HOLDING when isInHoldZone=true (starts count)
```

## Root Cause: JSON Builder Mismatch

### Critical Bug in `parseTrackedJoints` (backend/json-builder.ts)

For **Hold exercises**, primary joints in DB have **`range`** (single StateRanges).
For **Rep exercises**, primary joints have **`upRange`** and **`downRange`**.

**Current code (lines 356-362):**
```typescript
if (joint.role === 'primary') {
  return {
    ...baseJoint,
    role: 'primary' as const,
    upRange: joint.upRange as StateRanges,  // null for hold!
    downRange: joint.downRange as StateRanges,  // null for hold!
  } as PrimaryTrackedJoint;
}
```

**Result for Hold exercises:**
- Primary joint receives: `upRange: undefined`, `downRange: undefined`, `range: undefined`
- Android: `hasStateUpDownRanges()` = false, `hasStateHoldRange()` = false
- PhaseStateMachine falls back to **defaults**: `downRangeMin=0`, `downRangeMax=80`
- For Plank (hip angle ~160-180°): User is **never** in zone (0-80°)!

## Secondary Causes (to verify)

### 1. PhaseStateMachine uses first primary joint only
- Uses `primaryJoints.first()` for range bounds
- Uses `calculateAverageAngle(primaryAngles)` for angle
- If first joint has wrong config, entire hold logic fails

### 2. minPhaseDurationMs check
- When transitioning IDLE → COUNT, requires `phaseDuration >= minPhaseDurationMs`
- At start: phaseEntryTime=0 → uses minPhaseDurationMs as duration → passes
- When returning from leaving zone: may delay re-entry by ~200-400ms (acceptable)

### 3. Hysteresis
- isInDownRange uses hysteresis for exit only (exiting=true)
- Entry: `angle >= downRangeMin - hysteresis` (slightly more lenient)
- Should not block entry

## Fix Required

**File:** `backend/src/modules/exercises/json-builder.ts` (and Back-Admin if same)

**Change:** In `parseTrackedJoints`, for primary joints, support BOTH formats:
- If `joint.range` exists → output `range` (Hold exercise)
- If `joint.upRange` and `joint.downRange` exist → output `upRange`, `downRange` (Rep exercise)

```typescript
if (joint.role === 'primary') {
  // Hold exercises: primary has single range
  if (joint.range) {
    return {
      ...baseJoint,
      role: 'primary' as const,
      range: joint.range as StateRanges,
    } as HoldPrimaryTrackedJoint;
  }
  // Rep exercises: primary has upRange/downRange
  return {
    ...baseJoint,
    role: 'primary' as const,
    upRange: joint.upRange as StateRanges,
    downRange: joint.downRange as StateRanges,
  } as UpDownPrimaryTrackedJoint;
}
```

## Android Model Compatibility

TrackedJoint (ExerciseConfig.kt) uses:
- `@SerializedName("range", alternate = ["stateRange"])` - supports "range" from JSON
- `hasStateHoldRange() = range != null`
- `hasStateUpDownRanges() = upRange != null && downRange != null`

So Android already supports both. The fix is **backend-only** (json-builder).

## Verification Steps

1. After fix: Sync exercises from backend - Hold primary joints should have `range` in JSON
2. Log in PhaseStateMachine: Check "downRange: X - Y" at init - should show actual hold range, not 0-80
3. Log in TrainingEngine: When angle in zone, "Entered HOLD zone" should appear
4. Test with actual Plank exercise

## Files to Modify

1. `backend/src/modules/exercises/json-builder.ts` - parseTrackedJoints
2. `Back-Admin/src/modules/exercises/json-builder.ts` - same (if exists and used for sync)
