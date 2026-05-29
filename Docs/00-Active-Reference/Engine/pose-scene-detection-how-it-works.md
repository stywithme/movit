# Pose Scene Detection — How Each State Is Detected

> Current implementation as of Feb 2026.  
> Source files: `CameraPositionDetector.kt`, `BodyPostureDetector.kt`, `VisibleRegionDetector.kt`, `PoseSceneDetector.kt`, `PosePosition.kt`

---

## Architecture Overview

Detection runs on **3 independent axes** every frame:

| Axis | Detector | Output |
|------|----------|--------|
| **Direction** | `CameraPositionDetector` → `StableCameraDetector` | FRONT / BACK / SIDE_LEFT / SIDE_RIGHT / DIAGONAL |
| **Posture** | `BodyPostureDetector` | STANDING / LYING_PRONE / LYING_SUPINE / LYING_SIDE / SITTING |
| **Region** | `VisibleRegionDetector` | FULL_BODY / UPPER_BODY / LOWER_BODY |

`PoseSceneDetector` wraps all three with **majority-vote rolling windows** (7 frames, 5 required) and a warm-up mode that trusts raw results for the first 4 frames.

---

## Axis 1: Direction (Camera Position)

### Primary Signal: Combined X/Z Ratio

```
shoulderXZRatio = shoulderXDiff / (shoulderXDiff + shoulderZDiff + ε)
hipXZRatio      = hipXDiff      / (hipXDiff      + hipZDiff      + ε)
combinedRatio   = average(shoulderXZRatio, hipXZRatio)
```

- **Facing front/back**: shoulders spread horizontally → large X diff, small Z diff → **ratio ≈ 1.0**
- **Facing side**: shoulders at different depths → small X diff, large Z diff → **ratio ≈ 0.0**
- **Diagonal**: moderate both → **ratio ≈ 0.5**

### Secondary Signal: Face Visibility Score

```
faceScore = nose × 0.30 + leftEye × 0.20 + rightEye × 0.20 + leftEar × 0.15 + rightEar × 0.15
```

Used only to distinguish FRONT from BACK (both have high combinedRatio).

### Side Z Difference

```
avgLeftZ  = (leftShoulder.z + leftHip.z + leftKnee.z) / 3
avgRightZ = (rightShoulder.z + rightHip.z + rightKnee.z) / 3
sideZDiff = avgLeftZ - avgRightZ
```

Used to distinguish SIDE_LEFT from SIDE_RIGHT.

---

### FRONT_VIEW

| Condition | Value |
|-----------|-------|
| `combinedRatio` | > 0.70 (enter) or > 0.60 (exit hysteresis) |
| `faceScore` | > 0.50 |

**How it works**: Both shoulder pairs and hip pairs are spread wide horizontally with little depth difference. Face landmarks (nose, eyes, ears) are clearly visible.

**Known issue**: Works reasonably but not with maximum precision. The `faceScore > 0.50` threshold is moderate — arms raised over head can partially occlude the face and lower the score. The system defaults to FRONT when `faceScore` is in the ambiguous zone (0.25–0.50).

---

### BACK_VIEW

| Condition | Value |
|-----------|-------|
| `combinedRatio` | > 0.70 (enter) or > 0.60 (exit hysteresis) |
| `faceScore` | < 0.25 |

**How it works**: Same horizontal spread as FRONT, but face landmarks should be invisible or barely visible (person facing away from camera).

**Known issue — DOES NOT WORK**: MediaPipe almost never returns `faceScore < 0.25` even when the person has their back to the camera. MediaPipe's pose model "hallucinates" facial landmarks from behind with low but non-zero visibility (~0.10–0.40 per landmark). The weighted average almost always exceeds 0.25. Result: **BACK is always classified as FRONT**.

The ambiguous zone (0.25–0.50) falls back to the previous state; if no previous state exists, defaults to FRONT.

---

### SIDE_VIEW_LEFT

| Condition | Value |
|-----------|-------|
| `combinedRatio` | < 0.30 (enter) or < 0.40 (exit hysteresis) |
| `sideZDiff` | < 0 (left body side closer to camera) |

**How it works**: Shoulders nearly overlap in X (small X diff) but have large Z depth difference (one shoulder behind the other). The left body side has a lower average Z (closer to camera).

**Status**: Works very well. The ratio-based approach is robust and distance-independent.

---

### SIDE_VIEW_RIGHT

| Condition | Value |
|-----------|-------|
| `combinedRatio` | < 0.30 (enter) or < 0.40 (exit hysteresis) |
| `sideZDiff` | > 0 (right body side closer to camera) |

**How it works**: Same as SIDE_LEFT but mirrored.

**Status**: Works very well.

---

### DIAGONAL

| Condition | Value |
|-----------|-------|
| `combinedRatio` | Between side and front thresholds (0.30–0.70 range) |

**How it works**: Neither clearly front/back nor clearly side — the ratio lands in the middle zone. Confidence is calculated based on how close to 0.50 the ratio is.

**Status**: Works as a "catch-all" for ambiguous angles. Not frequently triggered in practice.

---

## Axis 2: Posture (Body Orientation)

### Primary Signal: Body Axis Angle

```
dx = hipMidX - shoulderMidX
dy = hipMidY - shoulderMidY
absAngle = abs(atan2(dy, dx)) → degrees from horizontal (0°–180°)
```

- **Standing**: hips directly below shoulders → dy >> dx → angle ≈ **90°**
- **Lying flat**: hips beside shoulders → dx >> dy → angle ≈ **0°** or **180°**
- **Leaning**: intermediate angles

---

### STANDING

| Condition | Value |
|-----------|-------|
| `absAngle` | 35° – 145° |
| Confidence | Peaks at 90° (perfectly upright), degrades toward edges |

**How it works**: The torso axis is mostly vertical. The range (35°–145°) covers exercises with significant forward lean (deadlifts at ~45° from vertical = 45° from horizontal = within range).

**Status**: Works well for all standing exercises including forward-lean moves.

---

### LYING_PRONE (face down)

| Condition | Value |
|-----------|-------|
| `absAngle` | < 25° or > 155° (body is horizontal) |
| `shoulderZDiff` | < 0.07 (not side-lying) |
| `faceScore` | < 0.20 (face not visible → facing down) |

**How it works**: Body is horizontal AND face is not visible (person is face-down).

**Known issue — DOES NOT WORK**: Two compounding problems:
1. `absAngle < 25°` is very strict — the person must be almost perfectly horizontal. Many "lying" positions with even a slight incline exceed 25°.
2. Even when the angle qualifies, `faceScore < 0.20` is rarely achieved. MediaPipe hallucinates face landmarks with moderate visibility even for prone positions. The score typically stays in the 0.20–0.40 range, which falls into the "ambiguous defaults to PRONE" path — but the angle threshold blocks it first.

---

### LYING_SUPINE (face up)

| Condition | Value |
|-----------|-------|
| `absAngle` | < 25° or > 155° (body is horizontal) |
| `shoulderZDiff` | < 0.07 (not side-lying) |
| `faceScore` | > 0.40 (face visible → facing up) |

**How it works**: Body is horizontal AND face is visible (person is face-up).

**Known issue — DOES NOT WORK**: Same angle threshold problem as PRONE. Additionally, when lying face-up and filmed from the side (the typical gym setup), the camera sees the body in profile — `faceScore` depends entirely on the face's visibility from that angle, which is moderate at best. The primary blocker is the strict 25° angle threshold.

---

### LYING_SIDE

| Condition | Value |
|-----------|-------|
| `absAngle` | < 25° or > 155° (body is horizontal) |
| `shoulderZDiff` | > 0.07 (one shoulder significantly behind the other) |

**How it works**: Body is horizontal AND there's a large depth difference between shoulders (one shoulder is stacked on top of the other when lying on side).

**Known issue**: Same angle threshold problem. Also, Z-depth values from MediaPipe are noisy, making `shoulderZDiff > 0.07` unreliable.

---

### SITTING (transitional)

| Condition | Value |
|-----------|-------|
| `absAngle` | 25° – 35° or 145° – 155° (gap between standing and lying) |

**How it works**: The body axis angle falls in the 10° gap between the STANDING and LYING ranges. The system leans toward the nearer stable state (standing if closer to 35°, lying if closer to 25°).

**Status**: Rarely triggered in practice because the gap is narrow (10° each side).

---

## Axis 3: Visible Region

### Signal: Landmark Presence Score

For each body group, computes average "presence" = visibility score × position penalty (landmarks outside the image frame 0–1 range get 70% penalty).

| Group | Landmarks |
|-------|-----------|
| **Upper** | nose, shoulders, elbows, wrists (7 landmarks) |
| **Core** | shoulders, hips (4 landmarks) |
| **Lower** | knees, ankles, heels (6 landmarks) |

A group is "present" if its average presence > **0.45**.

---

### FULL_BODY

Upper present AND core present AND lower present.

### UPPER_BODY

Upper present, lower NOT present (core may or may not be present).

### LOWER_BODY

Lower present, upper NOT present (core may or may not be present).

**Status**: Region detection works well thanks to the position-bounds penalty that filters out hallucinated off-screen landmarks.

---

## Temporal Smoothing (PoseSceneDetector)

All 3 axes are smoothed independently:

| Phase | Behavior |
|-------|----------|
| **Warm-up** (frames 1–4) | Raw single-frame result trusted directly |
| **Steady state** (frame 5+) | Majority vote: requires 5 of last 7 frames to agree to change state |

The warm-up ensures that **image mode** (single frame) and **early video frames** get immediate real results instead of UNKNOWN.

---

## Summary of Issues

| Detection | Status | Root Cause |
|-----------|--------|------------|
| **FRONT** | Reasonable | faceScore ambiguity (0.25–0.50 zone defaults to FRONT) |
| **BACK** | **Broken** | MediaPipe hallucinates face landmarks from behind → faceScore never drops below 0.25 |
| **SIDE_LEFT / RIGHT** | Excellent | Ratio-based detection is robust and distance-independent |
| **DIAGONAL** | Works | Catch-all for intermediate ratios |
| **STANDING** | Works | Wide angle range covers most exercise positions |
| **LYING_PRONE** | **Broken** | absAngle < 25° is too strict; faceScore < 0.20 is never achieved |
| **LYING_SUPINE** | **Broken** | Same angle problem; faceScore unreliable from side camera |
| **LYING_SIDE** | **Broken** | Same angle problem; Z-depth is noisy |
| **SITTING** | Rarely triggers | Narrow 10° transitional gap, usually smoothed out |
| **Region** | Good | Position-bounds penalty handles hallucinated landmarks |

### The Two Fundamental Problems

1. **FRONT vs BACK**: The only discriminator is `faceScore`. MediaPipe's pose model does not truly "see" the face — it infers facial landmark positions from the body model even when the face is not visible. The visibility scores it returns are model confidence, not actual visibility. This makes `faceScore` an unreliable signal for front/back discrimination.

2. **Standing vs Lying**: The `absAngle < 25°` threshold for lying detection is too strict. In real gym scenarios with a side camera, even a person lying flat on the ground has some torso angle due to camera perspective and body curvature. The threshold should be closer to 35–40° (or the detection approach needs to change entirely).
