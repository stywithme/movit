# Elbow Angle Depth Ambiguity — ML Engineer Brief

> This document is the entry point for an ML engineer evaluating solutions.
> For full details, see the referenced documents at the bottom.

---

## 1. The Problem in One Paragraph

We use **MediaPipe PoseLandmarker** (BlazePose) on mobile (Android) to estimate joint angles for exercise form validation. All joints work reliably **except the elbow**. The elbow angle error ranges from **+17° (near-frontal) to +58° (side view)** depending on camera direction, because MediaPipe's depth (Z) estimate for wrist landmarks is noisy and unreliable. We exhausted all heuristic post-processing approaches (6 iterations) and reached the limit of what can be done without a learned model. We need a model-based solution.

---

## 2. ML Task Formulation

### What We Have (Input)

MediaPipe PoseLandmarker outputs **per frame**:

| Output | Format | Reliability |
|--------|--------|-------------|
| `pose_landmarks` (Normalized) | 33 points × (x, y, z, visibility, presence) | **x,y are reliable** (image-space [0,1]), z is relative depth (unreliable) |
| `pose_world_landmarks` (World) | 33 points × (x, y, z, visibility, presence) | x,y,z in meters, hip-centered. **Z is unreliable for extremities** |

Frame rate: 30 fps. Temporal continuity is available.

### What We Need (Output)

**Corrected 3D positions for 3 landmarks**: Shoulder (11/12), Elbow (13/14), Wrist (15/16).

From these, the elbow angle is computed via dot product:
```
BA = Shoulder - Elbow
BC = Wrist - Elbow
angle = arccos(dot(BA, BC) / (|BA| × |BC|))
```

### Success Criteria

| Metric | Current (MediaPipe 3D raw) | Current (heuristic v3) | Target |
|--------|---------------------------|----------------------|---------| 
| Elbow angle error (frontal view) | +29° to +30° overestimate | ~5-15° (confidence-based, avoids catastrophic errors) | < 5° |
| Elbow angle error (side view) | +38° to +58° overestimate | Still +38-58° (unsolvable by heuristic) | < 10° |
| 2D angle range (same pose, 4 views) | 18.6°-93.4° (75° span) | Improved but still camera-dependent | < 10° variation |
| 3D angle range (same pose, 4 views) | 64.0°-92.6° (29° span) | Same — heuristic cannot fix 3D source | < 15° span |

---

## 3. Quantitative Evidence of the Problem

### Controlled Experiment: Same Pose, Different Camera Angles

Physical elbow angle: ~35° (verified visually). Camera distance: constant. Only direction changed.

| Camera Direction | 2D angle | 3D angle (MediaPipe world) | Error (3D) |
|-----------------|----------|---------------------------|------------|
| Front-side | 45.2° | 73.4° | +38° |
| Frontal | 18.6° | 65.0° | +30° |
| Front-behind | 26.0° | 64.0° | +29° |
| Side | 93.4° | 92.6° | +58° |

### Per-Segment Depth Analysis

`dzShare = |dz| / segment_length_3D` — how much of a segment lies in the depth axis.

**Knee (works correctly):**
```
Hip→Knee:    dzShare = 0.412
Knee→Ankle:  dzShare = 0.387
Imbalance: 0.025 → Balanced → 3D angle is reliable
```

**Elbow (fails):**
```
Shoulder→Elbow: dzShare = 0.205
Elbow→Wrist:    dzShare = 0.627  ← 63% of forearm is depth-noise
Imbalance: 0.422 → Severely unbalanced → 3D angle is inflated
```

### Why the Elbow Specifically

1. **Forearm range of motion**: The forearm can rotate freely in 3D — unlike the lower leg which is more constrained by hip/knee geometry. This creates extreme depth asymmetry between the two segments forming the elbow angle.
2. **Wrist is the most distal upper-body joint**: Errors accumulate along the kinematic chain (shoulder → elbow → wrist). Wrists have the worst Z estimates.
3. **Foreshortening**: When the forearm points toward/away from the camera, its 2D projection collapses. The 2D angle becomes meaningless, and MediaPipe's 3D cannot compensate because Z is noisy.

---

## 4. Root Cause — Why MediaPipe's Z Fails

MediaPipe's world landmarks are **not** true 3D reconstruction from depth sensing. They are produced by projecting 2D detections onto a learned body model (**GHUM**). This means:

1. **Z is inferred, not measured**: It's a regression output of a neural network trained to predict plausible 3D poses from 2D. The model outputs a single point estimate.
2. **The problem is ill-posed**: Infinitely many 3D poses can produce the same 2D projection. MediaPipe picks one — often wrong for extremities.
3. **No multi-hypothesis handling**: MediaPipe outputs ONE 3D pose, not a distribution over possible poses. When the depth is ambiguous (arm pointing at camera), there is no uncertainty signal.
4. **No camera intrinsics**: MediaPipe assumes a generic camera model. Without real focal length and distortion parameters, the perspective projection → 3D mapping is approximate.

These are confirmed by:
- [MediaPipe GitHub issues](https://github.com/google-ai-edge/mediapipe/issues?q=elbow) reporting arm depth anomalies
- Academic benchmarks showing monocular methods are "less accurate in depth" than multi-camera
- Biomechanics literature treating MediaPipe Z as "uncalibrated relative estimates"

---

## 5. What We Tried (All Heuristic — All Hit Limits)

| # | Approach | Result | Why It Failed |
|---|----------|--------|---------------|
| 1 | **Z Correction** (cap dzShare, repair Z outliers, bone length calibration) | Failed | Cannot distinguish real depth from noise; calibration contaminated by errors |
| 2 | **Hybrid 2D/3D blend** (auto-select 2D or 3D per frame) | Failed | 2D is NOT camera-independent; side view: both 2D and 3D agree on wrong angle |
| 3 | **Temporal smoothing** (One Euro Filter, EMA) | Partial | Reduced jitter but didn't fix accuracy |
| 4 | **Body-plane projection** + segment constraints | Failed | Error accumulation over time; artifacts in non-standard poses |
| 5 | **dzImbalance direction selector** (correct down or blend toward 3D) | Partial | dzImbalance sign doesn't correlate with error direction; blending toward 3D made things worse |
| 6 | **Confidence-based tiers** (maxDz as confidence, hold when uncertain) | Current | Best heuristic — avoids catastrophic errors. Still can't solve side-view or recover depth. |

**Key insight from 6 iterations**: `dzShare` (depth ratio of a segment) is a **confidence indicator**, not a correction-direction signal. High dzShare means "we can't trust either 2D or 3D," but it doesn't tell us which direction the truth lies.

---

## 6. Three Possible ML Approaches

### Option A: Lightweight Lifting Model (2D → 3D)

**Concept**: A small MLP that takes MediaPipe's 2D keypoints (17 joints × 2 = 34 floats) and outputs corrected 3D positions (17 joints × 3 = 51 floats). Trained on Human3.6M (3.6M poses with motion-capture ground truth).

**Architecture** (from Martinez et al. 2017, proven baseline):
```
Input(34) → FC(512) → BN → ReLU → Dropout
         → [Residual Block × 2]
         → FC(51) → Output
```

**Size**: ~1.6M params → 6.4MB float32 → **1.6MB int8 quantized**
**Latency**: < 1ms on mobile CPU

**Pros**:
- Simple, proven architecture
- Can be trained in hours on Colab
- Addresses the root cause (learns depth from data)
- Can apply to elbow only, leaving other joints unchanged

**Cons**:
- Domain gap: trained on HRNet/CPN detections, deployed with MediaPipe
- Single-frame: doesn't use temporal context
- Single hypothesis: still outputs one answer for ambiguous poses

**Literature**: Martinez et al. 2017, PoseMoE, AugLift

### Option B: Fine-tune or Replace MediaPipe's Z Head

**Concept**: Instead of post-processing, modify the model that produces the Z estimates. Either:
- Fine-tune MediaPipe's BlazePose on elbow-specific data
- Train a small "Z correction head" that takes MediaPipe's 33-landmark output and corrects Z for shoulder/elbow/wrist

**Pros**:
- Addresses the problem at the source
- Minimal pipeline changes

**Cons**:
- MediaPipe's model architecture is not easily fine-tunable (proprietary pipeline)
- BlazePose training requires specific infrastructure
- Risk of degrading other landmarks

### Option C: Multi-Hypothesis Model

**Concept**: Instead of predicting one 3D pose, predict a **distribution** of possible poses. For ambiguous views, output multiple hypotheses and select the most anatomically plausible one.

**Architecture options**:
- **MDN (Mixture Density Network)**: Output N Gaussian components, each representing a possible 3D pose
- **Diffusion-based**: D3DP (2023) — generate hypotheses via diffusion, aggregate with 2D reprojection
- **Manifold-constrained**: ManiPose — generate hypotheses on the pose manifold, enforce bone length + joint limits

**Pros**:
- Directly addresses the "infinitely many 3D poses" problem
- Can provide uncertainty estimates
- State-of-the-art accuracy

**Cons**:
- More complex to implement and deploy
- Higher latency (multiple forward passes for diffusion-based)
- May be overkill for single-joint correction

**Literature**: MDN (Li & Lee 2019), D3DP (2023), FMPose, ManiPose

---

## 7. Deployment Constraints

| Constraint | Value |
|------------|-------|
| Platform | Android (Kotlin/Java, TFLite interpreter already in project) |
| Latency budget | < 2ms per frame (MediaPipe itself takes ~15-25ms) |
| Model size | < 5MB in APK |
| Minimum API | Android 7.0 (API 24) |
| Hardware | Mid-range Snapdragon (7-series and above) |
| Input format | MediaPipe normalized landmarks (33 × 5 floats per frame) |
| Output format | Corrected elbow angle in degrees (or corrected 3D positions for shoulder/elbow/wrist) |
| Integration | Must work as post-processing step after MediaPipe — cannot modify MediaPipe pipeline |
| Fallback | If model confidence is low, fall back to current heuristic output |

---

## 8. Available Data

### For Training
- **Human3.6M**: 3.6M poses, 11 subjects, 15 activities, multi-camera MoCap ground truth. Standard split: subjects [1,5,6,7,8] train, [9,11] test. Free for research use.
- **MPI-INF-3DHP**: Smaller, more diverse environments
- **3DPW**: Outdoor videos with SMPL ground truth

### For Domain Adaptation
- Can run MediaPipe on Human3.6M video frames to get MediaPipe-specific 2D detections (closing the domain gap)
- Can collect custom data: film exercises with known angles (protractor-measured), multiple camera angles

### For Evaluation
- In-app "Angle Lab" debug screen already captures raw/smoothed landmarks, 2D/3D angles, dzShare metrics, and supports copy-to-clipboard for data extraction

---

## 9. MediaPipe Joint Mapping (33-point → 17-point H36M)

```
H36M Index → MediaPipe Index → Name
──────────────────────────────────────
0  (Hip)         → mid(23,24)  → Hip center
1  (R.Hip)       → 24          → Right Hip
2  (R.Knee)      → 26          → Right Knee
3  (R.Ankle)     → 28          → Right Ankle
4  (L.Hip)       → 23          → Left Hip
5  (L.Knee)      → 25          → Left Knee
6  (L.Ankle)     → 27          → Left Ankle
7  (Spine)       → mid(11,12,23,24) → Spine
8  (Thorax)      → mid(11,12)  → Thorax
9  (Nose)        → 0           → Nose
10 (Head)        → mid(7,8)    → Head top
11 (L.Shoulder)  → 11          → Left Shoulder  ← elbow context
12 (L.Elbow)     → 13          → Left Elbow     ← TARGET
13 (L.Wrist)     → 15          → Left Wrist     ← TARGET
14 (R.Shoulder)  → 12          → Right Shoulder  ← elbow context
15 (R.Elbow)     → 14          → Right Elbow     ← TARGET
16 (R.Wrist)     → 16          → Right Wrist     ← TARGET
```

---

## 10. Key Questions for Discussion

1. **Option A vs C**: Is a single-hypothesis lifting model sufficient, or do we need multi-hypothesis (MDN/diffusion) to handle the inherent depth ambiguity?

2. **Scope**: Should the model correct ALL joints (full lifting), or only shoulder/elbow/wrist? Full lifting gives more context but risks degrading joints that already work well.

3. **Domain gap**: How critical is it to re-run MediaPipe on Human3.6M images for training? Or is noise augmentation sufficient?

4. **Temporal**: Should we use a temporal model (TCN/GRU over N frames) or is single-frame sufficient? Temporal models help with smoothness but add complexity.

5. **Elbow-specific model**: Would a tiny model that ONLY predicts elbow angle (input: 6 landmarks × 2 = 12 floats → output: 1 angle) be viable? Or does it need full-body context to resolve ambiguity?

6. **Hybrid approach**: Can we train the model to output a confidence score alongside the prediction, so we can fall back to heuristic when confidence is low?

---

## References

| Document | Contents |
|----------|----------|
| `Joint-Debug/elbow-angle-problem-report.md` | Full investigation report: 6 solutions tried, evidence, current code state |
| `Joint-Debug/deep-research-report (3).md` | External research on MediaPipe Z instability, academic sources |
| `Reasearch/lifting-model-solution-plan.md` | Detailed lifting model implementation plan (architecture, training, deployment) |
| `Reasearch/*.pdf` | 19 academic papers on monocular 3D pose, depth ambiguity, multi-hypothesis, IK |

---

*Created: March 2026*
*For: ML Engineer consultation on model-based solution to elbow depth ambiguity*
