# Elbow Angle Measurement Problem — Full Investigation Report

## 1. Problem Statement

MediaPipe PoseLandmarker's world landmark Z coordinate for wrist landmarks (15/16) is unreliable, causing large errors in 3D elbow angle calculations. Additionally, 2D angle measurements are camera-angle-dependent by nature. Neither method provides a reliable, camera-independent elbow angle from a single RGB camera.

**Core symptom**: The same physical elbow angle produces wildly different measurements depending on camera direction.

---

## 2. Technical Background

### 2.1 MediaPipe Landmark Types

| Type | Coordinates | Origin | Notes |
|------|------------|--------|-------|
| `pose_landmarks` (Normalized) | x,y in [0,1] image space, z relative to hip | Image frame | 2D detection — reliable for x,y |
| `pose_world_landmarks` (World) | x,y,z in meters | Hip midpoint | Model-estimated 3D — Z is unreliable for extremities |

### 2.2 How Angles Are Calculated

- **2D angle**: `atan2` of vectors in screen space (Normalized x,y only)
- **3D angle**: Dot product of vectors using World x,y,z
- **Joint definition**: Elbow = Shoulder→Elbow→Wrist (landmarks 11/12→13/14→15/16)

### 2.3 The Root Cause (Confirmed by Research)

MediaPipe's world landmarks are NOT true camera-space 3D reconstructions. They are produced by mapping 2D detections onto an internal learned body model (GHUM). The Z component:

- Is the least reliable dimension in monocular pose estimation
- Has disproportionately high error for distal joints (wrists, ankles)
- Exhibits high frame-to-frame jitter
- Changes inconsistently across camera viewing angles

Reference: [MediaPipe Issue #3555](https://github.com/google-ai-edge/mediapipe/issues/3555)

---

## 3. Detailed Problem Analysis — Why the Elbow Specifically

### 3.1 Why Not Other Joints?

Knees have balanced depth contributions across both segments (hip→knee and knee→ankle typically share similar dzShare ~0.39-0.41). The elbow is uniquely problematic because:

1. **Forearm freedom of movement**: The forearm can rotate independently in 3D space much more than lower-leg segments, creating extreme depth asymmetry between upper arm and forearm.
2. **Wrist Z instability**: Wrists are at the end of a kinematic chain (shoulder → elbow → wrist). Errors accumulate along the chain, and wrists are the most distal upper-body joint.
3. **Foreshortening sensitivity**: When the forearm points toward/away from the camera, its 2D projection collapses to near-zero length, making the 2D angle meaningless. No other commonly-measured joint (knee, hip, shoulder) suffers this degree of foreshortening in typical exercise poses.

### 3.2 The Two Failure Modes

**Failure Mode A — 3D Inflation (most common)**:
MediaPipe's Z noise adds a phantom depth component to the forearm vector. This inflates `ang3D` well above the true angle. Example: real angle ~40°, ang3D reports 95°.

**Failure Mode B — 2D Foreshortening**:
When the arm extends in the camera's depth direction, the 2D projection collapses. `ang2D` becomes meaningless. Example: real angle ~35°, ang2D reports 93° (side view) or 18° (frontal view).

**The fundamental dilemma**: Each failure mode is the "solution" to the other. When 3D inflates, 2D is more accurate. When 2D foreshortens, only 3D can help — but 3D is noisy. No single signal is reliable across all camera angles.

### 3.3 The Metric: dzShare

`dzShare = |dz| / lenXYZ` — measures how much of a segment's length comes from the Z component.

- **dzShare ≈ 0**: Segment lies in the camera's XY plane → 2D and 3D agree
- **dzShare ≈ 0.5**: Significant depth → both signals become unreliable
- **dzShare ≈ 1.0**: Segment points directly at/away from camera → 2D is useless, 3D is pure noise

**Critical insight discovered during investigation**: `dzShare` does NOT indicate which DIRECTION the angle error goes. It only indicates how CONFIDENT we should be in any measurement. Previous approaches that used `dzImbalance` (difference between forearm and upper-arm dzShare) to decide correction direction were fundamentally flawed — the sign of the imbalance doesn't correlate with whether the true angle is above or below the measured angle.

---

## 4. Evidence: Raw Diagnostic Data

### 4.1 Knee — Works Correctly (right_knee, squat pose)

```
2D: 36.6° | 3D: 45.7° | Real: ~42-48°
Segment Hip→Knee:    |dz|/XYZ = 0.412
Segment Knee→Ankle:  |dz|/XYZ = 0.387
Imbalance: 0.025 → Balanced → 3D is reliable
```

### 4.2 Elbow — Fails (right_elbow, gym side view)

```
Sample 1 (arm bent, overhead):
  2D: 47.7° | 3D: 78.5° | Real: ~40-50°
  Shoulder→Elbow: |dz|/XYZ = 0.205
  Elbow→Wrist:    |dz|/XYZ = 0.627  ← Z dominates 63% of forearm
  2D is accurate, 3D overestimates by +30°

Sample 2 (arm at side, ~90°):
  2D: 89.0° | 3D: 105.9° | Real: ~85-90°
  Shoulder→Elbow: |dz|/XYZ = 0.032
  Elbow→Wrist:    |dz|/XYZ = 0.326
  2D is accurate, 3D overestimates by +17°
```

### 4.3 Elbow — Camera Angle Test (CRITICAL FINDING)

Same person, same elbow position (~30-35°), same distance. Only camera direction changed:

| Camera Direction | 2D | 3D (raw) | Real |
|-----------------|-----|----------|------|
| Front-side | 45.2° | 73.4° | ~35° |
| Frontal | 18.6° | 65.0° | ~35° |
| Front-behind | 26.0° | 64.0° | ~35° |
| Side | 93.4° | 92.6° | ~35° |

**Results**:
- 2D varies from 18.6° to 93.4° for the same physical angle (range: 75°!)
- 3D varies from 64.0° to 92.6° (range: 29°, consistently overestimates by +29 to +58°)
- Neither method gives a camera-independent measurement
- From side view: both 2D and 3D are catastrophically wrong (93° instead of ~35°)
- From frontal views: 2D is closer to reality but varies ±16°

### 4.4 Extreme Cases (arm bending toward camera)

```
Arm bent toward camera (selfie):
  2D: 8.5° | 3D: 74.7° | Real: ~35-45°
  BA dzShare: 0.695, BC dzShare: 0.507
  Both segments dominated by Z → 2D completely collapsed

Arm extended toward camera:
  2D: 157.4° | 3D: 117.0° | Real: ~160-170°
  BC dzShare: 0.960 (forearm is 96% Z!)
  Visibility: 0.021 → Gate correctly blocked this
```

---

## 5. Solutions Attempted and Results

### 5.1 Solution 1: DepthCorrector (3-layer Z Correction) — FAILED

**File**: `DepthCorrector.kt` (new file, later removed)

**Approach**: Correct the Z component of world landmark segments before angle calculation using three layers:

- **Layer 0 — Depth Cap**: Hard max on dzShare per segment (cap at 0.45).
- **Layer 1 — Depth Imbalance Detection**: Compares dzShare of the two segments around a joint.
- **Layer 2 — Bone Length Calibration**: Running median of segment lengths across frames.

**Why it failed**:
1. Cannot distinguish real depth from corrupted Z.
2. From frontal views, reducing Z pushes the angle toward the already-incorrect 2D value.
3. Calibration contamination: running median learns from corrupted lengths.
4. Threshold oscillation near cap values introduces its own jitter.

---

### 5.2 Solution 2: Hybrid 2D/3D Adaptive Blend — FAILED

**File**: `AngleCalculator.kt` — `calculateAngleHybrid()` (later removed)

**Approach**: Use depth metrics and foreshortening detection to automatically choose between 2D and 3D.

**Why it failed**:
1. 2D is not camera-independent — the fundamental assumption was wrong.
2. Delta between 3D and 2D is not a reliable foreshortening signal.
3. Side view catastrophe: both 2D (93°) and 3D (93°) agree on a completely wrong angle.

---

### 5.3 Solution 3: Smoothing & Stability Fixes — PARTIALLY SUCCESSFUL (stability only)

**Files**: `OneEuroFilter.kt`, `LandmarkSmoother.kt`, `DebugActivity.kt`, `MainActivity.kt`

| Fix | Description |
|-----|-------------|
| Frame dropping | `isProcessingPoseFrame` flag to prevent queue buildup |
| OneEuro dt clamping | Increased upper bound 0.1s→0.2s, auto-reset for >0.5s gaps |
| OneEuro beta tuning | Increased beta to enable adaptive smoothing behavior |
| updateParameters() bug fix | `val` → `var` for minCutoff/beta — function was silently discarding values |

**Result**: Reduced jitter. Did not fix angle accuracy.

---

### 5.4 Solution 4: Body-Plane Projection + Segment Constraint + Calibration — FAILED

**File**: `ElbowAngleEstimator.kt` (first version)

**Approach**: Project arm landmarks onto a body plane, then constrain segment lengths using calibrated bone lengths, with confidence gating.

**Why it failed**:
1. Stateful calibration (running maximums) accumulated errors over time.
2. Body-plane projection introduced artifacts in non-standard poses.
3. User reported "severe inconsistencies and variance" and "error accumulation over time."

---

### 5.5 Solution 5: Stateless dzImbalance Correction — PARTIALLY SUCCESSFUL

**File**: `ElbowAngleEstimator.kt` (second version)

**Approach**: Removed stateful calibration entirely. Used `dzImbalance` (forearm dzShare minus upper-arm dzShare) to decide correction direction:
- `dzImbalance ≤ 0` → 2D overestimates → correct DOWN
- `dzImbalance > 0` → 2D underestimates → blend TOWARD 3D

Added `sideStrength` modulation for frontal views and `STRAIGHT_ARM_BOOST` for extended arms.

**What worked**: Small angles (very bent) and large angles (nearly straight) measured reasonably well.

**What failed**: Mid-range angles (60-100°) were systematically wrong. Analysis revealed two fundamental bugs:

1. **Branch 4 (BLEND toward 3D) was catastrophically wrong**: When `dzImbalance > 0`, the code blended toward ang3D — but ang3D was INFLATED. Testing with user data: real angle ~40°, ang2D = 59°, ang3D = 95°. Branch 4 output: **77°** — worse than raw ang2D.
2. **`dzImbalance` sign doesn't correlate with error direction**: The assumption that "forearm has more depth → 2D underestimates" was incorrect. In practice, high forearm depth causes BOTH 2D and 3D to overestimate.

**User feedback**: "Much better, but I don't believe it can be built upon long-term or produce reliable measurements."

---

### 5.6 Solution 6: Confidence-Based Architecture — CURRENT (Final)

**File**: `ElbowAngleEstimator.kt` (third version — complete rewrite)

**Core philosophical change**: `maxDzShare` is treated as a **confidence indicator**, NOT a correction-direction selector. The code no longer tries to guess whether the true angle is above or below the measurement. Instead, it assesses HOW MUCH it can trust any signal, and acts accordingly.

**Architecture**:

```
Input: ang2D (from normalized landmarks), ang3D (from world landmarks)
       maxDz (max depth-share of either arm segment)
       facingRatio (shoulder planar ratio — frontal=1, side=0)
       sideStrength = f(facingRatio)

Strategy selection (first match wins):

┌─────────────────────────────────────────────────────────────────┐
│ 1. STRAIGHT   │ ang2D > 150°          │ Boost toward 180°      │
│ 2. TRUST_3D   │ ang3D ≤ ang2D + 12°   │ 3D resolved correctly  │
│ 3. TRUST_2D   │ maxDz < 0.15          │ No foreshortening      │
│ 4. MILD_DOWN  │ 0.15 ≤ maxDz < 0.40  │ Small correction ↓     │
│ 5. DEEP_DOWN  │ 0.40 ≤ maxDz < 0.60  │ Stronger correction ↓  │
│ 6. LOW_CONF   │ maxDz ≥ 0.60         │ Hold last stable value │
└─────────────────────────────────────────────────────────────────┘

All downward corrections are gated by sideStrength:
  - Frontal view (facingRatio > 0.85): sideStrength ≈ 0 → no correction
  - Side view (facingRatio < 0.40): sideStrength = 1 → full correction
```

**Key fixes over previous versions**:

| Bug Fixed | Old Behavior | New Behavior |
|-----------|-------------|-------------|
| `STRAIGHT_ARM_GATE` used `maxOf(ang2D, ang3D)` | Inflated ang3D (e.g., 155°) could trigger straight-arm boost on a bent arm | Uses `ang2D` only — immune to 3D noise |
| Branch 4 blended TOWARD ang3D | Made angles worse when 3D was inflated (77° instead of 40°) | **Removed entirely** — never blends toward inflated 3D |
| `INFLATION_GATE` was 5° | Noise of 6° triggered unnecessary corrections | Increased to 12° — wider tolerance |
| `DZ_SMOOTH_ALPHA` was 0.08 (12-frame lag) | dzShare reflected pose from 400ms ago → wrong branch selection | Increased to 0.18 (5-frame convergence) |
| No `sideStrength` on blend branch | Full correction applied in frontal views where 2D is accurate | All correction branches gated by sideStrength |
| `lastStable` updated every frame | Hold mechanism held potentially wrong values | Updated only when confidence is not LOW |
| No `reset()` method | Stale EMA state after camera switch caused artifacts | `reset()` called alongside `LandmarkSmoother.reset()` everywhere |
| `MAX_CORRECTION` was 0.35 | Aggressive corrections on already-reasonable angles | Reduced to 0.25 — more conservative |
| No strategy label in diagnostics | Debugging required reverse-engineering which branch fired | `strategy` field shows exact branch name |

**Constants**:

```
DZ_SMOOTH_ALPHA  = 0.18     OUTPUT_SMOOTH      = 0.25
STRAIGHT_ARM_GATE = 150.0   STRAIGHT_ARM_BOOST = 0.50
LOW_DEPTH        = 0.15     MID_DEPTH          = 0.40     HIGH_DEPTH = 0.60
INFLATION_GATE   = 12.0     CORRECTION_SCALE   = 0.55     MAX_CORRECTION = 0.25
SIDE_GATE_HIGH   = 0.85     SIDE_GATE_LOW      = 0.40
HOLD_TIMEOUT_MS  = 500
```

---

## 6. Fundamental Limitation

### Why This Cannot Be Fully Solved with a Single RGB Camera

1. **Physics**: A 2D image is a projection of 3D space. Depth information is inherently lost. A joint bending in the camera's depth direction produces the same 2D image as a straight joint.

2. **MediaPipe's Z estimation**: World landmarks use a learned body model (GHUM) to infer Z from 2D. This is an ill-posed inverse problem — multiple 3D poses can produce the same 2D projection. The model makes educated guesses that are often wrong for extremities.

3. **Camera-angle dependency is inherent to 2D**: The same 3D angle produces different 2D projections from different camera angles. This is not a bug — it's geometry.

4. **World landmarks are not camera-independent**: Testing shows that even world landmark positions (supposedly body-centric) change significantly with camera angle. Upper arm segment length changed from 0.28m to 0.16m (43% difference!) between side and frontal views of the same pose.

### What the Current Solution Achieves

The confidence-based `ElbowAngleEstimator` does NOT solve the fundamental limitation. What it does:

- **Stops making things worse**: By never blending toward inflated 3D, and by gating corrections with sideStrength, it avoids the catastrophic errors of previous approaches.
- **Provides honest confidence**: When depth is too high to trust any measurement, it holds the last known good value instead of outputting garbage.
- **Works best for**: Frontal and near-frontal camera views where 2D is naturally accurate. Mid-range angles (60-100°) remain the weakest point but are no longer catastrophically wrong.
- **Does NOT provide**: Camera-independent measurements. The side-view problem (93° measured for 35° real) cannot be solved by post-processing — it requires a fundamentally different approach (lifting model, multi-view, or depth sensor).

---

## 7. Current State of the Code

### Active Code Path

```
MediaPipe → PoseResult
    → LandmarkSmoother.smooth() (One Euro Filter on normalized landmarks)
    → LandmarkSmoother.convertWorld() (One Euro Filter on world landmarks)
    → AngleCalculator.calculateAllAnglesSmoothed(use3D=true)
    → ElbowAngleEstimator.correct() ← overrides elbow angles only
    → JointAngles.mirrored() (if front camera)
    → UI
```

`ElbowAngleEstimator.correct()` is called from:
- `MainActivity.kt`
- `TrainingActivity.kt`
- `DebugActivity.kt`
- `VideoModeController.kt`

`ElbowAngleEstimator.reset()` is called alongside `LandmarkSmoother.reset()` in all camera switch, mode switch, video seek, and image load contexts.

### All Files Modified During This Investigation

| File | Changes |
|------|---------|
| `ElbowAngleEstimator.kt` | **3 rewrites**: Body-plane → dzImbalance → Confidence-based (current) |
| `AngleCalculator.kt` | Added then removed `calculateAngleHybrid()` — current: `calculateAllAnglesSmoothed(use3D=true)` |
| `DepthCorrector.kt` | Created then removed — 3-layer Z correction experiment |
| `OneEuroFilter.kt` | Fixed dt clamping, auto-reset for time gaps |
| `LandmarkSmoother.kt` | Fixed val→var bug, updated beta defaults |
| `DebugActivity.kt` | Angle Lab tab, frame dropping, elbow diagnostics display, `reset()` integration |
| `MainActivity.kt` | Frame dropping, `reset()` integration |
| `TrainingActivity.kt` | `reset()` integration |
| `VideoModeController.kt` | `reset()` integration |

---

## 8. Future Directions (from Research)

19 academic papers were reviewed (in `Docs/Reasearch/`). The consensus:

| Approach | Feasibility | Impact on Elbow |
|----------|-------------|-----------------|
| **Lightweight Lifting Model (2D→3D)** | High effort (4-8 weeks), needs training data | Only real solution to depth ambiguity |
| **Simplified HybrIK** (analytical IK) | Medium effort, complex integration | Constrains anatomically impossible poses |
| **Perspective Correction** (camera intrinsics) | Low effort, incremental improvement | Helps at image edges, not at center |
| **Temporal Smoothing + Bone Constraints** | Low effort, quick improvement | Smooths output but doesn't fix accuracy |

The only path to camera-independent elbow measurement is **Path 1: a trained lifting model** that learns the mapping from 2D joints to 3D pose, with multi-hypothesis handling for depth ambiguity (concepts from MDN, ManiPose, BLAPose papers). This requires significant infrastructure (training pipeline, data collection, mobile deployment) but is the only approach that addresses the fundamental physics limitation.

---

## 9. Raw Data References

- `Docs/New-Project/Joint-Debug/Joints-debug-result.md` — First diagnostic captures
- `Docs/New-Project/Joint-Debug/Elbow-hybrid-solution.md` — Camera angle rotation test
- `Docs/New-Project/Joint-Debug/deep-research-report (3).md` — External research on MediaPipe Z instability
- `Docs/New-Project/Joint-Debug/Smart-solution-result.md` — Body-plane projection results
- `Docs/New-Project/Joint-Debug/Current-debug.md` — Raw baseline measurements
- `Docs/Reasearch/` — 19 academic papers on monocular 3D pose estimation

---

*Report last updated: March 2026*
*Status: Current solution (confidence-based ElbowAngleEstimator v3) is stable and avoids catastrophic errors, but does not provide camera-independent measurements. Full solution requires a trained lifting model.*
