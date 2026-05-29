# Elbow Correction MLP — Complete Solution Plan

## 1. Problem Recap

MediaPipe's elbow angle measurement fails because:
- **2D angle**: Camera-dependent (18°–93° for the same 35° physical angle)
- **3D angle**: Z-noise-inflated (+29° to +58° overestimate)
- **Heuristic (current v3)**: Cannot fix side-view; limited to avoiding catastrophic errors

The root cause is physics: a single 2D image cannot uniquely determine 3D pose (depth ambiguity). No amount of `if/else` rules can solve this — but a model trained on millions of (2D → true 3D) examples can.

---

## 2. Why Elbow Correction MLP (Not Classic Lifting Model)

### 2.1 The Posture MLP Proof

We already proved this pattern works in our project:

```
Posture MLP: Image → MediaPipe → 16 features → MLP (15KB) → posture class (89.7% accuracy)
```

The Elbow Correction MLP applies the same pattern:

```
Elbow MLP: Image → MediaPipe → ~25 features → MLP (~30KB) → true elbow angle
```

### 2.2 Comparison with Classic Lifting Model

| Criterion | Classic Lifting (Martinez) | Elbow Correction MLP |
|-----------|--------------------------|---------------------|
| Input | 34 floats (17 joints × 2D) | ~25 engineered features |
| Output | 51 floats (17 joints × 3D) | 1 float (elbow angle) |
| Domain gap | **High**: trained on HRNet/CPN, deployed with MediaPipe | **None**: trained on MediaPipe outputs |
| Available signals | Only 2D positions | 2D + 3D + dzShare + facingRatio + visibility + body context |
| Model size | ~1.6MB (hidden=512) | ~30-50KB |
| Focus | All 17 joints equally | Elbow only — maximum accuracy where it matters |
| Risk to other joints | May degrade joints that already work | Zero — only overrides elbow angles |
| Infrastructure | Needs new pipeline from scratch | Adapts existing Posture MLP pipeline |
| Domain gap fix | Run MediaPipe on H3.6M (added step) | **Same step**, but features are richer |

### 2.3 Key Insight

The classic lifting model treats MediaPipe 2D as a "black box input" and tries to reconstruct full 3D from scratch. It throws away information that MediaPipe already computed (world landmarks, visibility, depth estimates).

The Elbow Correction MLP uses **ALL available signals** — including the noisy ones — and learns how to combine them. Even noisy MediaPipe Z carries partial information about depth. The model learns when to trust it and when to ignore it — something our heuristic tried to do with rules but couldn't capture the full non-linear relationship.

---

## 3. What Does the Model Predict?

### 3.1 Decision: Direct Angle Prediction

Three options were considered:

| Option | Output | Pros | Cons |
|--------|--------|------|------|
| A. Direct angle | 1 float: elbow angle in degrees | Simplest, most focused, easiest to evaluate | Cannot extract 3D positions |
| B. Corrected 3D | 9 floats: shoulder/elbow/wrist XYZ | More flexible, can compute any derived metric | Harder to train, less focused |
| C. Correction delta | 1 float: (true - ang2D) | Centered around zero, may train faster | Still needs ang2D at inference |

**Decision: Option A (direct angle prediction)**

Reasons:
1. We only need the elbow angle — not 3D positions
2. Single output = simpler model, fewer parameters, less overfitting risk
3. The features already encode all the geometric information needed
4. Easier loss function (regression on one value)
5. Easier evaluation (compare predicted angle to ground truth)

### 3.2 Left & Right: One Model, Normalized

Instead of two separate models, we use ONE model with left/right normalization:

- During data preparation: mirror left elbow data to match right elbow geometry
- At inference: extract features identically for both sides
- All position features are relative to the shoulder-elbow-wrist chain, not absolute positions
- No "side" indicator feature needed — the features are inherently side-agnostic

This doubles the training data and ensures consistent behavior for both sides.

---

## 4. Feature Engineering

### 4.1 Design Principles

1. **Scale-invariant**: Divide spatial features by `torso_len` (shoulder-hip distance)
2. **Position-invariant**: Use relative positions and angles, not absolute coordinates
3. **Include both signals**: Both 2D and 3D data, even though 3D is noisy
4. **Include reliability metadata**: visibility, dzShare — these tell the model which signals to trust
5. **Body context**: Camera orientation and body position provide disambiguation cues

### 4.2 Complete Feature Specification

#### Group A: Angle Measurements (2 features)

| # | Name | Formula | Purpose |
|---|------|---------|---------|
| 0 | `ang2D` | 2D angle from normalized landmarks (shoulder→elbow→wrist) / 180 | Primary signal, camera-dependent |
| 1 | `ang3D` | 3D angle from world landmarks (shoulder→elbow→wrist) / 180 | Secondary signal, Z-noise-dependent |

#### Group B: Arm Segment Geometry (8 features)

| # | Name | Formula | Purpose |
|---|------|---------|---------|
| 2 | `ua_len_2d` | ‖shoulder-elbow‖₂D / torso_len | Upper arm 2D projection (foreshortening indicator) |
| 3 | `fa_len_2d` | ‖elbow-wrist‖₂D / torso_len | Forearm 2D projection |
| 4 | `ua_len_3d` | ‖shoulder-elbow‖₃D / torso_len | Upper arm 3D length |
| 5 | `fa_len_3d` | ‖elbow-wrist‖₃D / torso_len | Forearm 3D length |
| 6 | `dz_share_ua` | \|Δz\| / ‖shoulder-elbow‖₃D | Upper arm depth ratio (0=in-plane, 1=depth-aligned) |
| 7 | `dz_share_fa` | \|Δz\| / ‖elbow-wrist‖₃D | Forearm depth ratio |
| 8 | `len_ratio_2d` | fa_len_2d / (ua_len_2d + ε) | Forearm/upper arm ratio — anatomically ~constant, deviations indicate foreshortening |
| 9 | `dz_sign_diff` | sign(elbow.z-shoulder.z) × sign(wrist.z-elbow.z) | +1 if both segments same depth direction, -1 if crossing — encodes Z topology |

#### Group C: Camera & Body Orientation (4 features)

| # | Name | Formula | Purpose |
|---|------|---------|---------|
| 10 | `facing_ratio` | ‖shoulders‖₂D / ‖shoulders‖₃D | 1.0=frontal, 0.0=side view — critical for understanding projection type |
| 11 | `shoulder_w_norm` | \|ls.x - rs.x\| / torso_len | Shoulder width in screen (alternative facing indicator) |
| 12 | `spine_angle` | angle of torso vector from horizontal / 180 | Body inclination (standing/leaning/lying) |
| 13 | `arm_body_angle` | angle of (shoulder→elbow) vector relative to torso vector / 180 | Where the arm is relative to the body |

#### Group D: Arm Spatial Context (4 features)

| # | Name | Formula | Purpose |
|---|------|---------|---------|
| 14 | `wrist_drop` | (wrist.y - elbow.y) / torso_len | Vertical drop of wrist relative to elbow (sign matters) |
| 15 | `wrist_reach` | (wrist.x - shoulder.x) / torso_len | Horizontal reach of wrist from shoulder |
| 16 | `elbow_drop` | (elbow.y - shoulder.y) / torso_len | Vertical drop of elbow from shoulder |
| 17 | `elbow_forward` | (elbow.z - shoulder.z) / torso_len_3d | Depth position of elbow relative to shoulder (world) |

#### Group E: Reliability Signals (4 features)

| # | Name | Formula | Purpose |
|---|------|---------|---------|
| 18 | `vis_shoulder` | shoulder landmark visibility (0-1) | Detection confidence |
| 19 | `vis_elbow` | elbow landmark visibility (0-1) | Detection confidence |
| 20 | `vis_wrist` | wrist landmark visibility (0-1) | Detection confidence |
| 21 | `torso_len` | ‖shoulder_mid - hip_mid‖₂D | Absolute scale — distance from camera indicator |

#### Group F: World Landmark Positions (Elbow-Relative, 4 features)

| # | Name | Formula | Purpose |
|---|------|---------|---------|
| 22 | `w_sh_x` | (shoulder.wx - elbow.wx) / ua_len_3d | World direction: shoulder from elbow (X) |
| 23 | `w_sh_y` | (shoulder.wy - elbow.wy) / ua_len_3d | World direction: shoulder from elbow (Y) |
| 24 | `w_wr_x` | (wrist.wx - elbow.wx) / fa_len_3d | World direction: wrist from elbow (X) |
| 25 | `w_wr_y` | (wrist.wy - elbow.wy) / fa_len_3d | World direction: wrist from elbow (Y) |

> Note: Z components are deliberately excluded from Group F — they're already captured by `dz_share` and `dz_sign_diff`. Including raw Z would give the model a direct noisy signal without the reliability context.

**Total: 26 features**

### 4.3 Features NOT Included (and Why)

| Omitted Feature | Reason |
|----------------|--------|
| Raw world Z positions | Already represented by dzShare + dz_sign; raw Z is misleading |
| Absolute screen positions | Not invariant to image resolution, person position in frame |
| Hip/knee/ankle data | Not directly relevant to elbow angle; adds noise |
| Previous frame data | Phase 1 is single-frame; temporal features are Phase 2 |
| Camera intrinsics (focal length) | Not available in current pipeline; can be added later |

---

## 5. Training Data Strategy

### 5.1 Primary Source: Human3.6M

**What it is**: 3.6 million 3D poses captured with optical motion capture, performed by 11 actors doing 15 activities across 4 camera views.

**What we get from it**:
- Ground truth 3D joint positions (MoCap) → compute **true elbow angle**
- Video frames from known cameras → run MediaPipe to get **our actual input features**

**Standard split**:
- Training: Subjects S1, S5, S6, S7, S8
- Testing: Subjects S9, S11

**Activities**: Walking, Eating, Smoking, Discussion, Greeting, Phoning, Posing, Purchases, Sitting, SittingDown, Photo, Waiting, WalkDog, WalkTogether, Directions

**Camera setup**: 4 fixed cameras around the capture area → provides camera angle diversity automatically.

### 5.2 The Critical Step: Running MediaPipe on H3.6M

This step eliminates domain gap and is what makes this approach unique:

```
For each H3.6M video frame:
  1. Run MediaPipe PoseLandmarker → get normalized_landmarks + world_landmarks
  2. From MediaPipe output → compute 26 features (Group A-F above)
  3. From MoCap ground truth → compute true elbow angle
  4. Store pair: [features] → [true_angle]
```

**Why this matters**: The model learns from the ACTUAL outputs that MediaPipe produces — including its systematic biases, noise patterns, and failure modes. This is fundamentally different from using H3.6M 2D projections directly (which wouldn't include MediaPipe's specific errors).

### 5.3 Data Processing Pipeline

```python
# Pseudocode for data preparation

for subject in [1, 5, 6, 7, 8]:           # training subjects
  for action in all_actions:
    for camera in [1, 2, 3, 4]:
      video = load_h36m_video(subject, action, camera)
      mocap = load_h36m_3d(subject, action)
      
      for frame_idx in range(0, len(video), SAMPLE_RATE):
        image = video[frame_idx]
        gt_3d = mocap[frame_idx]           # shape: (17, 3) in camera coords
        
        # Run MediaPipe
        mp_result = mediapipe.detect(image)
        if not mp_result.pose_landmarks:
          continue
        
        norm_lm = mp_result.pose_landmarks[0]
        world_lm = mp_result.pose_world_landmarks[0]
        
        # Compute ground truth elbow angle from MoCap
        # H3.6M indices: shoulder=14, elbow=15, wrist=16 (right)
        gt_angle_right = angle_3pt(gt_3d[14], gt_3d[15], gt_3d[16])
        gt_angle_left  = angle_3pt(gt_3d[11], gt_3d[12], gt_3d[13])
        
        # Compute 26 features from MediaPipe
        features_right = compute_elbow_features(norm_lm, world_lm, side='right')
        features_left  = compute_elbow_features(norm_lm, world_lm, side='left')
        
        # Mirror left to right (normalize both sides to same geometry)
        features_left_mirrored = mirror_features(features_left)
        
        dataset.append((features_right, gt_angle_right))
        dataset.append((features_left_mirrored, gt_angle_left))
```

### 5.4 SAMPLE_RATE: How Many Frames?

H3.6M has ~3.6M frames total. At 50 fps, consecutive frames are nearly identical.

| Sample rate | Total frames | Training samples (5 subjects × 4 cameras × 2 elbows) | Notes |
|------------|-------------|------------------------------------------------------|-------|
| Every 5th frame | ~720K | ~720K | Excessive, highly redundant |
| Every 25th frame | ~144K | ~144K | Good balance |
| Every 50th frame | ~72K | ~72K | Minimum recommended |

**Recommendation**: Start with every 25th frame (~144K samples). This provides enough diversity while keeping training fast.

### 5.5 Angle Distribution Concern

H3.6M contains everyday activities, not exercises. The elbow angle distribution will be:
- Heavy concentration around 140°-170° (relaxed arm)
- Some representation at 80°-120° (eating, phoning)
- Limited representation at 20°-60° (very bent, like bicep curl)

**Mitigation strategies**:
1. **Weighted sampling**: Oversample rare angle ranges during training
2. **Angle-stratified batching**: Ensure each batch contains diverse angles
3. **MPI-INF-3DHP supplement**: This dataset includes exercise-like activities
4. **Custom data collection (Phase 2)**: Record exercises with known angles

### 5.6 Additional Data Sources

| Source | Samples | Elbow Coverage | Effort |
|--------|---------|---------------|--------|
| **Human3.6M** | ~144K | Everyday (skewed to relaxed) | Medium (download + process) |
| **MPI-INF-3DHP** | ~20K | More diverse activities | Low (smaller, faster to process) |
| **3DPW** | ~10K | Outdoor, natural movements | Low |
| **Custom exercise data** | ~500-2000 | Exercises with ground truth angles | High (manual angle measurement) |

**Phase 1 recommendation**: Start with H3.6M only. Add others if accuracy is insufficient.

---

## 6. Model Architecture

### 6.1 Network Design

```
Input (26 float32) → Dense(128, ReLU, L2=1e-3) → Dropout(0.25)
                    → Dense(64, ReLU, L2=1e-3)  → Dropout(0.15)
                    → Dense(32, ReLU)
                    → Dense(1, Sigmoid) × 180    → Output (angle in degrees, 0-180)
```

**Size estimate**: ~12K parameters → ~50KB float32 (no quantization — see Q17)

**Output activation**: `sigmoid × 180` constrains output to [0, 180] — the valid angle range.

### 6.2 Why This Architecture

| Decision | Reasoning |
|----------|-----------|
| 3 hidden layers (128, 64, 32) | More capacity than Posture MLP (64, 32) because regression is harder than classification and we have more features |
| Sigmoid output × 180 | Constrains output to valid range [0°, 180°] naturally |
| L2 regularization | Prevents overfitting, critical with ~144K training samples |
| Dropout 0.25/0.15 | Progressive dropout — heavier in early layers |
| No BatchNorm | Not needed for < 200K samples; simplifies TFLite conversion |

### 6.3 Loss Function

```python
import tensorflow as tf

def elbow_angle_loss(gt, pred):
    """
    Combined loss:
    1. MAE (L1) — primary, robust to outliers
    2. Angular penalty — extra weight on large errors (> 15°)
    """
    error = tf.abs(pred - gt)
    mae = tf.reduce_mean(error)
    
    large_error = tf.where(error > 15.0, error, 0.0)
    penalty = tf.reduce_mean(tf.square(large_error))
    
    return mae + 0.01 * penalty
```

**Why MAE over MSE**: MSE penalizes large errors quadratically, which can cause the model to sacrifice accuracy on common angles to reduce rare outliers. MAE treats all errors equally. The separate penalty handles truly large errors.

### 6.4 Training Configuration

```python
config = {
    "batch_size": 256,
    "learning_rate": 1e-3,
    "epochs": 100,
    "optimizer": "adam",
    "early_stopping_patience": 15,
    "early_stopping_monitor": "val_loss",
    "lr_schedule": "ReduceLROnPlateau(patience=7, factor=0.5)",
}
```

### 6.5 Data Augmentation

```python
augmentation = {
    # Simulate MediaPipe landmark jitter
    "joint_noise_std": 0.005,       # Small Gaussian noise on 2D positions
    
    # Simulate different MediaPipe detection quality
    "visibility_noise_std": 0.05,   # Perturb visibility values
    
    # Simulate scale variation (closer/farther from camera)
    "scale_range": [0.85, 1.15],    # Scale all length-based features
    
    # Left-right mirroring (already done in data prep)
    "horizontal_flip": True,
}
```

---

## 7. Evaluation Strategy

### 7.1 Primary Metric: Mean Absolute Angular Error (MAAE)

```
MAAE = mean(|predicted_angle - true_angle|)
```

### 7.2 Success Criteria

| Metric | Current (Heuristic v3) | Target | Stretch Goal |
|--------|----------------------|--------|-------------|
| MAAE (overall) | ~15-25° | < 7° | < 5° |
| MAAE (frontal) | ~5-15° | < 5° | < 3° |
| MAAE (side view) | ~38-58° | < 10° | < 7° |
| Max error (P95) | ~60° | < 15° | < 10° |
| Angle range consistency (same pose, 4 views) | 75° span | < 15° span | < 10° span |

### 7.3 Evaluation Protocol

```
For each test sample:
  1. Run MediaPipe on frame → extract 26 features
  2. Run MLP → get predicted angle
  3. Compare with MoCap ground truth → compute error

Report:
  - Overall MAAE
  - MAAE by angle range: [0-45°], [45-90°], [90-135°], [135-180°]
  - MAAE by camera angle (if camera metadata available)
  - Error distribution (histogram)
  - Worst-case errors (P95, P99)
```

### 7.4 In-App Evaluation (Manual)

Using the existing Angle Lab debug screen:

```
Test protocol:
  1. Set up protractor/goniometer reference
  2. Hold arm at known angles: 30°, 60°, 90°, 120°, 160°
  3. For each angle, test from 3 camera directions: frontal, 45°, side
  4. Record: MLP prediction vs true angle
  5. Compare with: MediaPipe raw 3D, Heuristic v3
  
= 5 angles × 3 views = 15 test cases minimum
```

---

## 8. Mobile Deployment

### 8.1 Export Pipeline (same as Posture MLP)

```python
# TFLite conversion — NO quantization (compatibility with TFLite 2.16.1)
converter = tf.lite.TFLiteConverter.from_keras_model(model)
# DO NOT add converter.optimizations — see Posture MLP TRAINING_GUIDE
tflite_model = converter.convert()

with open("elbow_correction_mlp.tflite", "wb") as f:
    f.write(tflite_model)
```

Output files:
```
android-poc/app/src/main/assets/
  ├── elbow_correction_mlp.tflite     (~30-50 KB)
  └── elbow_correction_mlp_norm.json  (mean/std normalization)
```

### 8.2 Normalization JSON Format

```json
{
  "version": 1,
  "feature_count": 26,
  "feature_names": [
    "ang2D", "ang3D", "ua_len_2d", "fa_len_2d", "ua_len_3d", "fa_len_3d",
    "dz_share_ua", "dz_share_fa", "len_ratio_2d", "dz_sign_diff",
    "facing_ratio", "shoulder_w_norm", "spine_angle", "arm_body_angle",
    "wrist_drop", "wrist_reach", "elbow_drop", "elbow_forward",
    "vis_shoulder", "vis_elbow", "vis_wrist", "torso_len",
    "w_sh_x", "w_sh_y", "w_wr_x", "w_wr_y"
  ],
  "mean": [0.0, 0.0, "... 26 values ..."],
  "std": [1.0, 1.0, "... 26 values ..."],
  "output_description": "Elbow angle in degrees [0, 180]"
}
```

---

## 9. Android Integration

### 9.1 New Files to Create

```
android-poc/app/src/main/java/com/trainingvalidator/poc/
  ├── analysis/
  │   ├── ElbowMlpFeatureExtractor.kt   ← 26-feature computation (mirrors Python)
  │   └── ElbowMlpCorrector.kt          ← TFLite loading + inference
```

### 9.2 ElbowMlpFeatureExtractor.kt (Contract with Python)

```kotlin
object ElbowMlpFeatureExtractor {
    const val FEATURE_COUNT = 26
    
    /**
     * Computes 26 features for one elbow side.
     * MUST match tools/elbow-mlp/feature_engineering.py exactly.
     */
    fun computeFeatures(
        normLandmarks: List<SmoothedLandmark>,
        worldLandmarks: List<SmoothedLandmark>,
        side: ElbowSide
    ): FloatArray? {
        // ... implementation mirrors Python feature_engineering.py
    }
}
```

### 9.3 ElbowMlpCorrector.kt (Inference — same pattern as PostureMlpClassifier)

```kotlin
class ElbowMlpCorrector private constructor(
    private val interpreter: Interpreter,
    private val mean: FloatArray,
    private val std: FloatArray,
) {
    data class Prediction(
        val angle: Float,          // predicted angle in degrees [0, 180]
        val rawFeatures: FloatArray // for debug display
    )
    
    fun predict(features: FloatArray): Prediction {
        // 1. Normalize: (features - mean) / std
        // 2. Run TFLite interpreter
        // 3. Output × 180 = angle in degrees
    }
    
    companion object {
        const val ASSET_MODEL = "elbow_correction_mlp.tflite"
        const val ASSET_NORM = "elbow_correction_mlp_norm.json"
        
        fun getOrNull(context: Context): ElbowMlpCorrector?
        // ... singleton pattern same as PostureMlpClassifier
    }
}
```

### 9.4 Integration in Pipeline

Current pipeline:
```
MediaPipe → Smoother → AngleCalculator.calculateAllAnglesSmoothed()
         → ElbowAngleEstimator.correct() ← heuristic override
         → JointAngles → UI
```

New pipeline:
```
MediaPipe → Smoother → AngleCalculator.calculateAllAnglesSmoothed()
         → ElbowMlpCorrector.predict()   ← ML override (if model loaded)
         → fallback to ElbowAngleEstimator.correct() (if model unavailable)
         → JointAngles → UI
```

### 9.5 Integration Points (same files as current estimator)

| File | Change |
|------|--------|
| `MainActivity.kt` | Add ElbowMlpCorrector call after AngleCalculator, before ElbowAngleEstimator |
| `TrainingActivity.kt` | Same integration |
| `DebugActivity.kt` | Same + display MLP prediction vs heuristic in Angle Lab |
| `VideoModeController.kt` | Same integration |
| `PoseApp.kt` | Initialize ElbowMlpCorrector singleton |

### 9.6 Fallback Strategy

```kotlin
val angles = AngleCalculator.calculateAllAnglesSmoothed(...)

// Try MLP first
val mlpCorrector = ElbowMlpCorrector.getOrNull(context)
val correctedAngles = if (mlpCorrector != null) {
    val leftFeats = ElbowMlpFeatureExtractor.computeFeatures(norm, world, LEFT)
    val rightFeats = ElbowMlpFeatureExtractor.computeFeatures(norm, world, RIGHT)
    
    angles.copy(
        leftElbow = leftFeats?.let { mlpCorrector.predict(it).angle.toDouble() } ?: angles.leftElbow,
        rightElbow = rightFeats?.let { mlpCorrector.predict(it).angle.toDouble() } ?: angles.rightElbow,
    )
} else {
    // Fallback: use existing heuristic
    elbowAngleEstimator.correct(angles, worldLandmarks, normLandmarks, timestampMs)
}
```

---

## 10. Training Pipeline: File Structure

```
tools/elbow-mlp/
  ├── requirements.txt
  ├── README.md
  ├── TRAINING_GUIDE.md
  ├── feature_engineering.py        ← 26-feature definition (contract with Kotlin)
  ├── prepare_h36m_data.py          ← H3.6M → MediaPipe → feature extraction
  ├── train_elbow_mlp.py            ← Training + export script
  └── evaluate.py                   ← Evaluation metrics + visualization
```

### 10.1 prepare_h36m_data.py

```python
"""
Pipeline:
  1. Load H3.6M video frames + MoCap 3D poses
  2. Run MediaPipe on each frame
  3. Extract 26 features + compute ground truth elbow angle
  4. Save to .npz for training

Output: elbow_mlp_data.npz
  - X: (N, 26) float32  — features
  - y: (N,) float32     — ground truth angles (degrees)
  - meta: subject, action, camera, frame_idx for each sample
"""
```

### 10.2 train_elbow_mlp.py

Follows the same structure as `train_posture_mlp.py`:
1. Load prepared data (or extract features from images)
2. Train/val split (by H3.6M subject — never mix train/test subjects)
3. Normalize features (Z-score from training set)
4. Build model (Keras Sequential)
5. Train with early stopping
6. Evaluate
7. Export to TFLite + norm JSON

---

## 11. Decisions & Open Questions

### 11.1 Resolved Decisions

| # | Question | Decision | Rationale |
|---|----------|----------|-----------|
| Q4 | Do we need H3.6M camera parameters for ground truth? | **No** (for Phase 1) | The angle between 3 MoCap points is the same regardless of coordinate frame — angle is invariant under rigid transforms. We compute `arccos(dot(BA,BC) / (|BA|×|BC|))` directly from MoCap 3D. Camera extrinsics are only needed for camera-relative *positions*, not angles |
| Q5 | Camera-relative or world-relative ground truth? | **World 3D angle from MoCap** | Since the elbow is a hinge joint, the 3D geometric angle ≈ anatomical flexion angle. The angle value is the same in any coordinate frame. What matters is that the *features* capture enough about the camera view for the model to map them to the correct angle |
| Q9 | Normalized or World landmarks for 2D? | **Normalized** | Consistent [0,1] range, resolution-independent |
| Q10 | Regression or classification? | **Regression** | Angle is a continuous quantity; bins create artificial boundaries; all literature uses regression for angle prediction |
| Q11 | Confidence output? | **No** (Phase 1) | Use external gate: visibility < 0.5 or maxDzShare > 0.7 → fallback to heuristic. MC Dropout can be explored in Phase 2 if needed |
| Q12 | Residual connections? | **No** (start without) | Low impact at this model size; Posture MLP works fine without them |
| Q13 | TensorFlow or PyTorch? | **TensorFlow/Keras** | Same path as Posture MLP; direct TFLite export without ONNX conversion |
| Q15 | Validation split? | **Subject-based** | Train: S1,5,6,7,8 — Test: S9,11. Academic standard; prevents subject leakage |
| Q16 | Epochs / early stopping? | **100 epochs, patience 15** | Monitor val_MAE; flexible starting point |
| Q17 | Float32 or quantized TFLite? | **Float32** | Required for TFLite 2.16.1 op compatibility (same constraint as Posture MLP) |
| Q19 | Temporal smoothing on MLP output? | **Yes — EMA or OneEuro** | Same filters already in project pipeline |
| Q20 | Debug UI? | **Add MLP column in Angle Lab** | Show MLP angle, heuristic angle, raw 2D/3D, diff |
| Q21 | Extend to other joints? | **Deferred** | Same pipeline possible, but shoulder/wrist errors are less severe |
| Q22 | Temporal features in model input? | **Phase 2** | Start single-frame; add previous-angle feature only if accuracy is insufficient |
| Q23 | Camera intrinsics as features? | **Phase 2** | Requires Android Camera2 API integration; not critical for initial model |
| Q24 | In-app data collection? | **Deferred** | Complex UX; not needed unless H3.6M proves insufficient |

### 11.2 Experimental Questions (Answered During Implementation)

These questions cannot be answered theoretically — each is tied to a specific phase checkpoint where the experiment runs.

| # | Question | Phase | Experiment | Decision Gate |
|---|----------|-------|------------|---------------|
| Q1 | Does H3.6M have enough elbow angle diversity? | 1A | Plot angle histogram from MoCap data | If angles < 60° are < 5% of samples → add weighted sampling (Q14) |
| Q2 | Will MediaPipe detect poses in H3.6M reliably? | 1A | Run MediaPipe on 100 random frames | If detection rate < 90% → investigate failed frames, adjust processing |
| Q3 | H3.6M ↔ MediaPipe joint mapping compatibility? | 1A | Visual overlay of MoCap skeleton on MediaPipe skeleton | If mismatch found → fix mapping table before proceeding |
| Q6 | Are 26 features optimal? | 1C | Permutation importance after first training | Drop features with near-zero importance; keep all if no clear signal |
| Q7 | Include raw x,y positions? | 1C | Quick ablation: train with vs without | Only add if MAAE improves by > 0.5° |
| Q8 | Include lower body features? | 1C | Quick ablation: train with hip angles vs without | Only add if MAAE improves; `spine_angle` already captures body context |
| Q14 | How to handle angle imbalance? | 1B-1C | Check histogram from Q1; if imbalanced → add weighted sampling | Compare MAAE with/without weighting on rare angle bins |
| Q18 | When to disable ML and fallback? | 2A | Test MLP on low-visibility and edge-case frames | Define visibility threshold empirically from failure cases |

---

## 12. Risk Analysis

| Risk | Likelihood | Impact | Detected At | Mitigation |
|------|-----------|--------|-------------|------------|
| H3.6M angle distribution doesn't cover exercise ranges | Medium | High | **Phase 1A** (histogram) | Weighted sampling in 1B; supplement with MPI-INF-3DHP in Phase 3 |
| MediaPipe fails to detect many H3.6M frames | Low | High | **Phase 1A** (100-frame test) | H3.6M is clean indoor — expect > 95%. If < 90%: crop/resize pre-processing |
| Model overfits to H3.6M lab environment | Medium | Medium | **Phase 2A** (real-world test) | Augmentation (noise, scale) in 1C; real-exercise data in Phase 3 |
| Feature redundancy hurts accuracy | Medium | Low | **Phase 1C** (importance analysis) | Drop features with near-zero importance |
| Ground truth angle computation error | Low | High | **Phase 1A** (manual verification) | Spot-check MoCap angles on known test poses |
| TFLite ↔ Python output mismatch | Low | Medium | **Phase 1D** (comparison test) | Compare on 100 samples; max diff < 0.1° |
| MLP worse than heuristic on some real views | Medium | Medium | **Phase 2A** (Angle Lab test) | Investigate specific views; add data or adjust features |
| Model too slow on mobile | Very Low | Medium | **Phase 1D** | MLP 26→1 with ~12K params is < 0.1ms |
| Model conflicts with other joints | None | N/A | — | Only overrides elbow angles |

---

## 13. Implementation Phases (Step-by-Step with Checkpoints)

Each phase ends with a **checkpoint** — a concrete result that must be validated before moving to the next phase. This ensures we build on solid ground and catch problems early.

---

### Phase 1A: Data Foundation & Feasibility

**Goal:** Confirm H3.6M is usable and understand the data landscape before writing any training code.

| Step | Task | Output |
|------|------|--------|
| 1 | Download H3.6M (videos + MoCap annotations) | Raw data on disk |
| 2 | Set up Python environment (`requirements.txt` with mediapipe, numpy, h5py, etc.) | Working env |
| 3 | Load MoCap 3D poses for one subject/action → compute elbow angles from MoCap | Script that outputs angle values |
| 4 | Plot **angle histogram** across all subjects & actions (MoCap only — no MediaPipe yet) | **→ Answers Q1** |
| 5 | Run **MediaPipe on 100 random frames** from different subjects/cameras | Detection rate + visual inspection |
| 6 | **→ Answers Q2**: Is detection > 90%? If not, investigate failures |
| 7 | Overlay MoCap skeleton + MediaPipe skeleton on same frame → verify joint mapping | **→ Answers Q3** |

**Checkpoint 1A — Go/No-Go:**
- [ ] Angle histogram reviewed — do we have enough diversity for angles < 60°?
- [ ] MediaPipe detection rate > 90% on H3.6M frames?
- [ ] Joint mapping validated visually?
- [ ] If angle diversity insufficient: plan weighted sampling strategy for 1B

**Decision if checkpoint fails:**
- Detection rate < 90% → Try different MediaPipe model variant or pre-process images (crop/resize)
- Angle diversity too low → Plan to supplement with MPI-INF-3DHP or synthetic data in Phase 2

---

### Phase 1B: Feature Extraction Pipeline

**Goal:** Build the complete data processing pipeline and produce the training dataset.

| Step | Task | Output |
|------|------|--------|
| 1 | Implement `feature_engineering.py` — the 26-feature specification from Section 4 | Feature computation module |
| 2 | Implement `prepare_h36m_data.py` — full pipeline: video → MediaPipe → features + GT angle | Processing script |
| 3 | Run on all training subjects (S1,5,6,7,8) × 4 cameras, every 25th frame | Raw dataset |
| 4 | Check for NaN/Inf/degenerate values in features | Data quality report |
| 5 | Plot feature distributions — any constant or extreme-skew features? | Distribution plots |
| 6 | Analyze **angle distribution of successfully processed frames** (subset of Q1 answer) | Final training data profile |
| 7 | If Q1 showed imbalance: implement **weighted sampling** by angle bin | Sampler ready |

**Checkpoint 1B:**
- [ ] `elbow_mlp_data.npz` produced with (N, 26) features + (N,) angles
- [ ] N > 50K usable samples (after filtering failed detections)
- [ ] No NaN/Inf in features
- [ ] Feature distributions are reasonable (no constants, no extreme outliers)
- [ ] Angle distribution documented — weighted sampler ready if needed

**Output files:**
```
tools/elbow-mlp/
  ├── requirements.txt
  ├── feature_engineering.py
  ├── prepare_h36m_data.py
  └── data/
      └── elbow_mlp_data.npz
```

---

### Phase 1C: Model Training & Evaluation

**Goal:** Train the MLP, measure accuracy, and understand where it works and where it fails.

| Step | Task | Output |
|------|------|--------|
| 1 | Implement `train_elbow_mlp.py` (Keras Sequential, MAE loss, config from Section 6) | Training script |
| 2 | Train on subjects S1,5,6,7,8 — validate on S9,11 | Trained model |
| 3 | Compute **MAAE overall + by angle bin** ([0-45°], [45-90°], [90-135°], [135-180°]) | Accuracy report |
| 4 | Compute **MAAE by camera** (if metadata preserved) | View-dependency analysis |
| 5 | Plot **error distribution** — is it Gaussian or heavy-tailed? | Error histogram |
| 6 | Run **permutation importance** on the 26 features | **→ Answers Q6** |
| 7 | If features with near-zero importance: try training without them, compare | **→ Answers Q7, Q8** |
| 8 | If MAAE > 10°: investigate worst-case samples — what's going wrong? | Diagnosis |

**Checkpoint 1C — The Critical Gate:**
- [ ] MAAE overall < 7° on H3.6M test set?
- [ ] MAAE on side-view cameras < 12°?
- [ ] P95 error < 15°?
- [ ] Feature importance analyzed — final feature set decided

**Decision if checkpoint fails:**
- MAAE 7°–12° → Try: weighted loss on rare angles, more data (MPI-INF-3DHP), dropout tuning
- MAAE > 12° → Step back: analyze whether the feature set is capturing enough signal; consider adding raw world coordinates or switching to delta prediction (Option C from Section 3)

**Output files:**
```
tools/elbow-mlp/
  ├── train_elbow_mlp.py
  ├── evaluate.py
  ├── models/
  │   └── elbow_correction_mlp_v1.keras
  └── reports/
      ├── accuracy_report.txt
      └── feature_importance.png
```

---

### Phase 1D: Mobile Deployment

**Goal:** Export model to TFLite, implement Kotlin counterparts, integrate in the app.

| Step | Task | Output |
|------|------|--------|
| 1 | Export to TFLite (float32, no quantization) + norm JSON | `.tflite` + `.json` |
| 2 | Verify: compare Python inference vs TFLite inference on 100 test samples | Max diff should be < 0.1° |
| 3 | Implement `ElbowMlpFeatureExtractor.kt` — mirror `feature_engineering.py` exactly | Kotlin feature computation |
| 4 | Implement `ElbowMlpCorrector.kt` — TFLite loading + inference (PostureMlpClassifier pattern) | Kotlin inference class |
| 5 | Integrate in pipeline: MLP override → fallback to heuristic (Section 9.4-9.6) | Working app pipeline |
| 6 | Basic smoke test: does the app launch, does MLP produce reasonable angles? | Functional verification |

**Checkpoint 1D:**
- [ ] App runs with MLP-based elbow angles
- [ ] Python ↔ TFLite output match (< 0.1° diff)
- [ ] Python ↔ Kotlin feature parity verified on known input
- [ ] Fallback to heuristic works when model is absent

**Output files:**
```
android-poc/app/src/main/assets/
  ├── elbow_correction_mlp.tflite
  └── elbow_correction_mlp_norm.json

android-poc/app/src/main/java/.../analysis/
  ├── ElbowMlpFeatureExtractor.kt
  └── ElbowMlpCorrector.kt
```

---

### Phase 2A: Real-World Validation

**Goal:** Test on actual exercises and real camera conditions — the true test.

| Step | Task | Output |
|------|------|--------|
| 1 | Use Angle Lab: test MLP vs heuristic vs raw at 5 known angles × 3 camera views | 15 test cases documented |
| 2 | Record: where does MLP outperform heuristic? Where does it fail? | Comparison table |
| 3 | Define **fallback threshold** empirically — when should heuristic take over? | **→ Answers Q18** |
| 4 | Add EMA/OneEuro smoothing on MLP output | Smoother predictions |
| 5 | Update Angle Lab to show MLP + heuristic + diff | Debug UI ready |
| 6 | If real-world MAAE >> H3.6M MAAE: investigate domain gap | Gap analysis |

**Checkpoint 2A:**
- [ ] MLP outperforms heuristic in most test cases
- [ ] Fallback conditions defined and implemented
- [ ] No regression: cases where heuristic was good should still be good (or better)

**Decision if checkpoint fails:**
- Significant domain gap → Collect small exercise dataset (50-100 clips), retrain with mixed data
- MLP worse than heuristic in some views → Analyze which views; possibly add MPI-INF-3DHP data

---

### Phase 2B: Production Polish

**Goal:** Final integration quality and stability.

| Step | Task | Output |
|------|------|--------|
| 1 | Handle all edge cases: low visibility, lost tracking, re-detection | Robust pipeline |
| 2 | Verify reset behavior: camera switch, mode change, video seek | No stale state |
| 3 | Performance check: MLP inference time < 1ms on target device | Perf verified |
| 4 | Document final model card: accuracy, limitations, feature list | README + TRAINING_GUIDE |

**Checkpoint 2B:**
- [ ] Production-ready integration
- [ ] No crashes or edge-case failures
- [ ] Documentation complete

---

### Phase 3: Enhancement (Optional — Based on Phase 2 Results)

Only proceed if Phase 2 reveals specific deficiencies.

| Enhancement | When to Apply | Effort |
|-------------|--------------|--------|
| Add MPI-INF-3DHP training data | Angle diversity insufficient | Low |
| Weighted loss for rare angles | Specific angle bins have high MAAE | Low |
| Temporal feature (previous angle) | Frame-to-frame jitter despite smoothing | Medium |
| Camera intrinsics as features | Consistent bias at specific distances | Medium |
| Custom exercise recording data | Domain gap confirmed in Phase 2A | High |

---

## 14. Sync Contract (Feature Parity)

The 26-feature computation MUST be identical in:

| File | Language | Environment |
|------|----------|-------------|
| `tools/elbow-mlp/feature_engineering.py` | Python | Training |
| `ElbowMlpFeatureExtractor.kt` | Kotlin | Android inference |

Any change to one **MUST** be mirrored in the other, followed by re-training.

This is the same contract pattern as the Posture MLP:
- `tools/posture-mlp/feature_engineering.py` ↔ `PostureMlpFeatureExtractor.kt`

---

## 15. Summary: Why This Will Work

1. **The pattern is proven**: Posture MLP (89.7% accuracy, 15KB, < 1ms) shows that MediaPipe features → small MLP → task output works in this project.

2. **No domain gap**: Training on MediaPipe's actual outputs (run on H3.6M) means the model learns exactly what MediaPipe does — including its biases and noise patterns.

3. **Richer input than any alternative**: 26 features including angles, depths, ratios, visibility, and body context. The heuristic only uses ~5 of these signals; the classic lifting model only uses 2D positions.

4. **Focused objective**: Predicting one angle (not 51 coordinates) means fewer parameters, less overfitting, and maximum accuracy where it matters.

5. **Cheap fallback**: If the model fails to load, the existing heuristic (ElbowAngleEstimator v3) handles everything — zero regression risk.

6. **Minimal infrastructure**: Reuses the Posture MLP training pipeline, TFLite runtime (already in the APK), and singleton pattern.

---

## 16. Ground Truth: How Elbow Angle is Computed from H3.6M

H3.6M provides **3D joint positions** from professional MoCap equipment (Vicon) — not angles directly.

**We compute the ground truth angle ourselves:**

```python
shoulder_3d = mocap[SHOULDER_IDX]  # (x, y, z) in meters — MoCap precision < 1mm
elbow_3d   = mocap[ELBOW_IDX]
wrist_3d   = mocap[WRIST_IDX]

BA = shoulder_3d - elbow_3d       # upper arm vector
BC = wrist_3d   - elbow_3d        # forearm vector

cos_angle = dot(BA, BC) / (|BA| × |BC|)
true_angle = arccos(cos_angle)    # degrees — this is our ground truth
```

**Why this is correct:**
- The angle between 3 points in 3D space is **invariant under rigid transforms** (rotation, translation). It doesn't matter whether we compute in world frame or camera frame — the angle is the same.
- The elbow is primarily a **hinge joint** — the geometric angle between the arm segments closely matches the anatomical flexion/extension angle.
- **Camera extrinsics are NOT needed** for computing the angle (only needed for camera-relative *positions*).

---

*Document created: March 2026*
*Last updated: March 2026 — FIT3D real-train baseline*
*Status: Phase 3 — Consistency loss training achieved 11.6 deg MAE and reduced multi-view spread from 21.8 to 18.6 deg*

---

## Execution Results (AIST++ Adaptation) — v1

### Dataset Change: H3.6M -> AIST++
The original plan called for H3.6M, but we used **AIST++** (3D dance keypoints) because it was already downloaded and provides high-quality 3D MoCap ground truth (COCO-17 format).

### Synthetic Projection Strategy
Since AIST++ only provides 3D keypoints (no videos), we generated synthetic MediaPipe-like features by:
- Rotating 3D skeletons to simulate camera viewpoints
- Projecting to 2D (orthographic)
- Adding calibrated noise to simulate MediaPipe estimation errors
- Computing the same 26 features used at inference time

### Training Results (on synthetic test set)

| Metric | Value |
|--------|-------|
| **Overall MAE** | **8.23 degrees** |
| P95 Error | 22.94 degrees |
| P99 Error | 36.98 degrees |
| Max Error | 130.88 degrees |
| Baseline (ang3D only) | 19.46 degrees |
| Improvement over baseline | 57% |
| Training samples | 550,325 |
| Test samples | 125,014 |
| Model size (TFLite) | 57 KB |

### Per-Bin Accuracy
| Angle Range | MAE | Samples |
|------------|-----|---------|
| 0-45 | 6.55 degrees | 2,168 (1.7%) |
| 45-90 | 8.33 degrees | 26,304 (21%) |
| 90-135 | 9.51 degrees | 42,512 (34%) |
| 135-180 | 7.24 degrees | 54,030 (43.2%) |

### Feature Importance (Permutation)
| Rank | Feature | Importance |
|------|---------|------------|
| 1 | ang2D | 13.88 |
| 2 | ang3D | 10.15 |
| 3 | w_wr_x | 4.40 |
| 4 | fa_len_2d | 3.34 |
| 5 | ua_len_2d | 2.59 |
| 6 | w_sh_x | 2.56 |
| 7 | fa_len_3d | 1.34 |
| 8 | len_ratio_2d | 0.99 |
| 9 | torso_len | 0.92 |
| 10 | wrist_drop | 0.88 |
| ... | facing_ratio | **0.12** (very low!) |
| ... | vis_shoulder/elbow/wrist | ~0.001 (near-zero) |

### Data Extraction Stats
| Metric | Value |
|--------|-------|
| Sequences processed | 1,408 |
| Frames sampled | 112,833 |
| Samples created | 675,539 |
| Skipped (degenerate) | 143 |
| Skipped (features) | 1,173 |

### Exported Files
- `android-poc/app/src/main/assets/elbow_correction_mlp.tflite` (57 KB)
- `android-poc/app/src/main/assets/elbow_correction_mlp_norm.json`
- `tools/elbow-mlp/models/elbow_correction_mlp_v1.keras`
- `tools/elbow-mlp/reports/` (accuracy_report.json, evaluation.png, feature_importance.json)

### Key Bug Fixes During Training
1. **Keras 3 loss shape issue**: Custom `elbow_angle_loss` returned wrong shape, causing 42 deg MAE (worse than baseline). Fixed by using standard `mae` loss.
2. **Data outliers**: Extreme foreshortening at certain camera angles caused feature values > 200. Fixed by filtering projected torso length and capping features at 10.
3. **Windows DLL block**: Application Control Policy blocked TensorFlow DLLs. Fixed with `Unblock-File`.

### Android Integration (Phase 2)
Created Kotlin classes following the PostureMlpClassifier pattern:
- `ElbowMlpFeatureExtractor.kt` — 26-feature extraction (mirrors `feature_engineering.py`)
- `ElbowCorrectionMlpClassifier.kt` — TFLite inference with singleton pattern
- Debug tab "Elbows" in DebugActivity — shows both elbows + MLP + heuristic comparison
- MLP latency: ~0.22 ms (~4500 inf/s on test device)

---

## Phase 2A: Real-World Validation — PROBLEM FOUND

### Test Setup
Same physical pose, same person, only camera position changed (front vs side).
Elbow angle: approximately 50-60 degrees (arm bent near chest).

### Results — Same Pose, Different Camera

| Measurement | Front View | Side View | Delta |
|-------------|-----------|-----------|-------|
| **Heuristic (corrected)** | **6.7°** | **55.9°** | **49.2°** |
| **MLP v1** | **55.7°** | **52.5°** | **3.2°** |
| ang2D (raw 2D) | 6.5° | 44.9° | 38.4° |
| ang3D (raw 3D) | 79.8° | 55.6° | 24.2° |
| facing_ratio | 1.000 | 0.356 | — |
| dzShare max | 0.644 | 0.336 | — |
| Heuristic strategy | LOW_CONF | TRUST_3D | — |

### Observations

1. **MLP is significantly more stable** than heuristic (3.2° vs 49.2° delta between views)
2. **But still not accurate enough** — during exercise, model reached only ~72% correct form detection
3. **Heuristic is catastrophically bad** in front view (6.7° instead of ~55°)
4. **ang2D collapses** in front view (6.5°) due to foreshortening — arm pointing at camera
5. **ang3D is inflated** in front view (79.8°) — MediaPipe's known Z-noise problem
6. **facing_ratio is critical** but had LOW importance (0.12) in training — model didn't learn to use it properly

### Root Cause Analysis

#### PRIMARY CAUSE: Domain Gap (Synthetic vs Real MediaPipe)

The model was trained on **synthetic** data, not **real** MediaPipe outputs. This creates fundamental mismatches:

| Aspect | Synthetic Training Data | Real MediaPipe Inference |
|--------|------------------------|-------------------------|
| **2D projection** | Orthographic projection of 3D keypoints | Neural network (BlazePose) detection from image |
| **World landmarks** | Real 3D + random Gaussian noise | GHUM body model fitting to 2D detections |
| **Noise pattern** | Random, independent, Gaussian | Systematic, pose-dependent, correlated |
| **Norm/World correlation** | Same 3D source + independent noise | Different NN heads, complex correlation |
| **Foreshortening** | Smooth degradation (orthographic) | Catastrophic collapse (ang2D=6.5° for ~55° real) |
| **Visibility** | Random uniform [0.65, 1.0] | Detection confidence (drops with occlusion) |

**Key evidence**: In real front-view foreshortening, ang2D drops to 0.036 (6.5°). In synthetic training, orthographic projection never produces such extreme foreshortening because there's no perspective effect.

#### SECONDARY CAUSE: facing_ratio Learned Poorly

Feature importance shows `facing_ratio = 0.12` — almost negligible. But facing_ratio is the **single most important feature** for camera-view disambiguation:
- Front view: facing_ratio = 1.0 (should heavily discount ang2D)
- Side view: facing_ratio = 0.3 (should trust ang2D more)

Why? In synthetic data, the relationship between facing_ratio and angle error was too simple (linear orthographic projection). The model learned ang2D/ang3D are "good enough" without needing facing_ratio. In reality, the relationship is highly non-linear — ang2D can be catastrophically wrong when facing_ratio is high.

#### CONTRIBUTING CAUSES

1. **AIST++ is dance data**: Limited exercise pose diversity (mostly extended arms in dance moves)
2. **Angle distribution skewed**: 43% of samples in 135-180° (extended arm), only 1.7% in 0-45°
3. **COCO-17 to MediaPipe-33 mapping**: Synthetic filling of unmapped landmarks (e.g., wrists, hands) doesn't match real MediaPipe
4. **No perspective projection**: Orthographic projection doesn't capture how 2D angles collapse at certain depth orientations

### What Went Wrong in Our Approach

The original plan (Section 5) explicitly called for running MediaPipe on H3.6M video frames:
> "Run MediaPipe on each frame → get normalized_landmarks + world_landmarks ... The model learns from the ACTUAL outputs that MediaPipe produces"

We deviated from this by using synthetic projection. The deviation was pragmatic (AIST++ was available, H3.6M wasn't downloaded), but it introduced a fundamental domain gap that the offline MAE metric (8.23°) couldn't detect — because the test set had the same synthetic distribution.

### Path Forward — Options

#### Option A: Train on Real MediaPipe Data (Recommended)
- Download H3.6M videos OR use any video dataset with MoCap ground truth
- Run MediaPipe on actual video frames to get REAL features
- This eliminates the domain gap completely
- Estimated effort: 2-3 days (download + processing + retraining)

#### Option B: Improve Synthetic Data
- Switch to perspective projection with realistic focal lengths
- Model MediaPipe's systematic errors (not just random noise)
- Add extreme foreshortening augmentation
- Estimated effort: 1-2 days, but may not fully solve the problem

#### Option C: Hybrid Approach
- Keep current synthetic model as baseline
- Collect real MediaPipe data from the app's debug mode (record features + manually measure angles)
- Fine-tune with mixed synthetic + real data
- Estimated effort: 3-5 days (needs manual data collection)

#### Option D: Rethink the Approach
- Instead of training on synthetic projections, use a lifting model approach (Martinez et al.)
- Input: MediaPipe's raw 2D detections → Output: corrected 3D positions
- Trained directly on H3.6M with MediaPipe 2D detections as input
- More established in the literature for this exact problem

---

## FIT3D Pilot Status (March 2026)

To move away from synthetic data, a new prototype pipeline was created under:

- `tools/elbow-h3.6m/fit3d_test/feature_engineering.py`
- `tools/elbow-h3.6m/fit3d_test/prepare_fit3d_data.py`
- `tools/elbow-h3.6m/fit3d_test/train_fit3d_elbow_mlp.py`
- `tools/elbow-h3.6m/fit3d_test/README.md`

### What was implemented

1. **Real MediaPipe extraction from FIT3D videos**
   - Uses MediaPipe Tasks `PoseLandmarker`
   - Reads real video frames, not synthetic projections
   - Pairs MediaPipe detections with FIT3D metadata

2. **Revised feature design**
   - Keeps `legacy26` for comparison
   - Adds `fit3d_v2`, a body-aware feature set with:
     - local screen vectors
     - body-frame arm vectors
     - XY/XZ/YZ projection angles
     - visibility/presence reliability signals

3. **Fail-fast label validation**
   - The extraction script now aborts immediately if FIT3D labels are degenerate
   - This prevents accidental training on placeholder/template annotations

### Historical blocker (resolved)

The originally available `fit3d_template.json` was **not usable as elbow ground truth**:

- `joints3d` was effectively constant across frames/actions/subjects
- computed elbow GT collapsed to about `166.47 - 167.12 deg`
- this confirmed the local template behaved like a submission template / placeholder, not real motion annotations

The scripts still keep this fail-fast validation, but actual training is now running against the real files under `tools/elbow-h3.6m/fit3d_train/train`.

### Real FIT3D train baseline (actual run)

The pipeline was updated to support the real FIT3D train layout:

- `joints3d_25/<action>.json`
- `videos/<camera_id>/<action>.mp4`
- `camera_parameters/<camera_id>/<action>.json`
- multi-camera extraction with configurable `frame_stride`

The first real-data baseline used:

- `feature_set=fit3d_v2`
- MediaPipe Tasks `PoseLandmarker full`
- all 8 locally available train subjects: `s03,s04,s05,s07,s08,s09,s10,s11`
- all 4 FIT3D cameras
- `frame_stride=120`
- holdout subject `s11`

#### Extraction results

| Metric | Value |
|--------|-------|
| Videos found | 1504 |
| Frames requested | 15620 |
| Pose detect failed | 464 |
| Feature failed | 36 |
| Samples created | 30276 |
| GT angle range | 22.55 - 179.82 deg |

#### Training results (`fit3d_v2`, holdout=`s11`)

| Metric | Value |
|--------|-------|
| Train samples | 26002 |
| Test samples | 4274 |
| Overall MAE | 12.93 deg |
| P95 Error | 37.00 deg |
| P99 Error | 61.26 deg |
| Max Error | 114.69 deg |
| Best val MAE | 12.93 deg |
| TFLite max parity diff | 0.000021 deg |

#### Camera robustness results

| Metric | Value |
|--------|-------|
| Camera `50591643` MAE | 13.92 deg |
| Camera `58860488` MAE | 15.38 deg |
| Camera `60457274` MAE | 10.97 deg |
| Camera `65906101` MAE | 11.40 deg |
| Multi-view mean prediction spread | 21.84 deg |
| Multi-view P95 prediction spread | 57.65 deg |
| Multi-view max prediction spread | 116.64 deg |

#### Interpretation

- This run is much more trustworthy than the AIST++ synthetic result because it is trained on real MediaPipe outputs from real videos.
- However, the original camera-robustness problem is **not solved yet**: the same `(subject, action, frame, side)` still produces a mean cross-camera prediction spread of `21.84 deg`.
- The gap between camera-specific MAE values (`10.97 deg` vs `15.38 deg`) confirms that the representation remains view-sensitive.
- `ang2d_xy` dominated feature importance by a large margin (`11.05` permutation impact), far above most body-frame and depth-aware features. This strongly suggests the model is still leaning too heavily on image-plane cues.

### Post-baseline ablation experiments

To test whether the view-sensitivity problem comes mainly from one feature or from a broader group of 2D cues, two ablation runs were executed on the **same extracted dataset** and the **same holdout subject** (`s11`).

#### Ablation A: remove only `ang2d_xy`

| Metric | Baseline | No `ang2d_xy` | Delta |
|--------|----------|---------------|-------|
| Overall MAE | 12.93 deg | 12.87 deg | -0.06 |
| P95 Error | 37.00 deg | 38.10 deg | +1.10 |
| P99 Error | 61.26 deg | 63.56 deg | +2.30 |
| Multi-view mean spread | 21.84 deg | 21.72 deg | -0.12 |
| Multi-view P95 spread | 57.65 deg | 57.22 deg | -0.43 |

Interpretation:

- Removing the single dominant 2D angle feature changed very little.
- Camera robustness did **not** improve in a meaningful way.
- Therefore, the problem is not explained by `ang2d_xy` alone.

#### Ablation B: remove the core screen-space 2D feature group

Removed features:

- `ang2d_xy`
- `ua_len_2d`, `fa_len_2d`, `len_ratio_2d`
- `shoulder_w_norm`, `hip_w_norm`
- `torso_dx_norm`, `torso_dy_norm`
- `se_dx_2d`, `se_dy_2d`
- `ew_dx_2d`, `ew_dy_2d`
- `sw_dx_2d`, `sw_dy_2d`

Results:

| Metric | Baseline | No core screen-2D group | Delta |
|--------|----------|--------------------------|-------|
| Overall MAE | 12.93 deg | 14.62 deg | +1.69 |
| P95 Error | 37.00 deg | 40.84 deg | +3.85 |
| P99 Error | 61.26 deg | 63.52 deg | +2.26 |
| Multi-view mean spread | 21.84 deg | 23.61 deg | +1.77 |
| Multi-view P95 spread | 57.65 deg | 58.25 deg | +0.60 |

Per-camera MAE also worsened:

- `50591643`: `13.92 -> 16.26 deg`
- `58860488`: `15.38 -> 17.14 deg`
- `60457274`: `10.97 -> 12.57 deg`
- `65906101`: `11.40 -> 12.45 deg`

Interpretation:

- Broadly deleting 2D cues made the model worse overall **and** less camera-stable.
- This means the solution is **not** "remove 2D information".
- The model still needs some screen/image cues, but the current design is not combining them in a sufficiently view-robust way.

#### Conclusion after ablations

- The switch from synthetic AIST++ to real FIT3D + real MediaPipe was the correct move.
- The real pipeline now works end-to-end and exports a valid TFLite model.
- The remaining failure is not the old placeholder-label problem.
- The remaining failure is a **representation / feature design problem**: the model still changes too much across camera views.
- A simple feature deletion strategy is not enough.

---

### Phase 3: Multi-View Consistency Training (March 2026)

Following research review (3DPCNet, "Two Views Are Better Than One" CVPR 2025 Workshop, PriorFormer), three complementary techniques were implemented:

#### 3A. Canonicalized Feature Set (`fit3d_v3`, 35 features)

New features designed for camera-invariance:

- `ang_disagreement`: `|ang2d - ang3d| / 180` — direct ambiguity signal
- `foreshort_ua`, `foreshort_fa`: relative foreshortening per arm segment
- `sw_dist_3d`: shoulder-wrist 3D distance
- 3D lengths normalized by 3D torso (no more mixing 2D/3D scales)

#### 3B. Multi-View Consistency Loss

- custom training loop with penalty: `L = L_pred + lambda * Var(preds across cameras for same pose)`
- uses existing multi-camera FIT3D data (4 cameras per frame)
- trains with multi-view but infers from single view
- `--consistency-weight 0.5`

#### 3C. Residual Prediction Mode

- predict `delta = (true_angle - ang3d_xyz) / 180` instead of absolute angle
- output activation changes from sigmoid to linear
- `--residual-target residual`

#### Phase 3 ablation results (all holdout `s11`)

| Experiment | Features | Consist | Residual | MAE | Spread | Cam Range |
|------------|----------|---------|----------|-----|--------|-----------|
| v2 Baseline | v2(38) | -- | -- | 12.93 | 21.84 | 4.41 |
| v3 Baseline | v3(35) | -- | -- | 13.15 | 22.89 | 4.56 |
| **v3 + Consist 0.5** | v3(35) | 0.5 | -- | **11.63** | 18.73 | **2.80** |
| **v2 + Consist 0.5 + Resid** | v2(38) | 0.5 | Yes | **11.61** | 18.63 | 3.41 |
| v3 + Consist 0.5 + Resid | v3(35) | 0.5 | Yes | 11.65 | 19.84 | 3.37 |
| v3 + Consist 0.8 | v3(35) | 0.8 | -- | 11.75 | 18.98 | 3.16 |
| v3 + Consist 1.0 | v3(35) | 1.0 | -- | 11.84 | **17.78** | 3.82 |
| v2 + Consist 0.8 + Resid | v2(38) | 0.8 | Yes | 11.68 | 19.03 | 3.59 |
| v2 + Consist 1.0 + Resid | v2(38) | 1.0 | Yes | 11.83 | 19.35 | 3.43 |

#### Key findings

1. **Consistency loss is the dominant improvement** — reduces MAE by ~1.3 deg and multi-view spread by ~3 deg regardless of feature set.
2. **v3 features alone did not improve** over v2 baseline. The canonicalization helps camera invariance only when combined with consistency training.
3. **v3 + Consist 0.5** achieved the **narrowest per-camera MAE range** (2.80 deg vs 4.41 deg in v2 baseline) — best camera invariance overall.
4. **v2 + Consist 0.5 + Resid** achieved the lowest absolute MAE (11.61 deg).
5. All consistency-trained models beat all non-consistency models in both accuracy and camera robustness.
6. **Consistency weight 0.5 is the optimal value** — higher weights (0.8, 1.0) increase MAE while only marginally improving spread.
7. Higher weights push the model to over-prioritize cross-camera agreement at the expense of prediction accuracy.

#### Per-camera MAE comparison (best model: v3 + Consistency Only)

| Camera | v2 Baseline | v3 + Consist | Delta |
|--------|-------------|-------------|-------|
| 50591643 | 13.92 | 12.05 | -1.87 |
| 58860488 | 15.38 | 12.98 | -2.40 |
| 60457274 | 10.97 | 10.18 | -0.79 |
| 65906101 | 11.40 | 11.27 | -0.13 |

The worst camera improved the most, which confirms the consistency loss is correcting view-dependent bias.

### Phase 4: More data + Cross-validation (March 2026)

Trained the best config (v2 + Consist 0.5 + Resid) on 3x more data (stride=40 → 87K samples) and validated across multiple holdout subjects.

#### Cross-validation results

| Holdout | Train | Test | MAE | P95 | P99 | Cam Range | Spread |
|---------|-------|------|-----|-----|-----|-----------|--------|
| s11 | 75,230 | 12,402 | 11.91 | 35.13 | 63.70 | 3.13 | 20.01 |
| s03 | 76,186 | 11,446 | **9.09** | 31.89 | 60.81 | 4.14 | 19.75 |
| s07 | 77,762 | 9,870 | 9.66 | **29.32** | **54.16** | **2.76** | **16.69** |
| **Average** | | | **10.22** | 32.11 | 59.56 | 3.34 | 18.82 |

#### Key findings (Phase 4)

1. **Average cross-validation MAE = 10.22 deg** — the model generalizes across subjects.
2. **s11 is the hardest subject** (11.91 deg) while s03 and s07 are easier (9.09, 9.66).
3. **3x more data improved the critical 0-45 deg bin** from 18.15 to 13.70 deg on s11 (4.45 deg improvement).
4. **s07 holdout achieves best camera invariance**: 2.76 deg range and 16.69 deg spread.
5. **More data helps underrepresented angle ranges** without requiring architecture changes.

### How the current training pipeline works (plain language)

This section explains the full process in simple terms for non-specialists.

#### Step 1: collect the real files

We use three things from FIT3D:

- the real videos
- the real 3D body joints (`joints3d_25`)
- the camera folders / metadata

The key change from the old synthetic approach is that the input now comes from **real video frames**, not fake projected skeletons.

#### Step 2: choose which frames to use

We do not train on every single video frame.

Instead, we sample frames using `frame_stride`.

Example:

- `frame_stride=120` means we keep one frame, then skip many frames, then keep the next one

This keeps training practical while still covering many poses and actions.

#### Step 3: run MediaPipe on each selected frame

For every chosen frame:

- MediaPipe looks at the image
- MediaPipe predicts pose landmarks

These predicted landmarks are important because they are the same type of input the Android app will see later.

#### Step 4: compute the true elbow angle

For the same frame, FIT3D gives the real 3D joint positions.

From three joints:

- shoulder
- elbow
- wrist

we compute the real elbow angle in degrees.

This becomes the "correct answer" for training.

#### Step 5: convert landmarks into features

The raw landmarks are turned into a compact list of numbers.

Examples:

- image-plane angles
- 3D angles
- arm lengths
- depth ratios
- body-relative arm directions
- visibility / confidence values

This feature vector is what the neural network actually receives as input.

#### Step 6: save the extracted dataset

After extraction, we store:

- `X` = input features
- `y` = correct elbow angle
- metadata like subject, action, frame id, side, and camera

At this stage, extraction is done and training can start.

#### Step 7: split by subject

We do not mix the same person into both train and test.

Instead, we hold out a full subject, such as `s11`, so testing is more realistic.

That means the model is evaluated on a person it did not train on.

#### Step 8: normalize the features

Before training, we scale each feature so the neural network sees values in a stable range.

This makes learning more reliable.

#### Step 9: train the model

The model is a small MLP neural network.

In simple terms:

- it sees the input numbers
- it guesses the elbow angle
- we compare the guess to the real angle
- the model adjusts itself to make smaller mistakes next time

This happens again and again across the full dataset.

#### Step 10: evaluate from several angles

We do not look only at one accuracy number.

We also measure:

- overall MAE
- large-error tails (`P95`, `P99`)
- performance per action
- performance per camera
- cross-camera spread for the same frame

That last metric is critical because the real user problem is:

- "Will the angle stay stable even if the camera changes?"

#### Step 11: export for Android

Once training finishes, we export:

- a Keras model
- a TFLite model
- a normalization JSON file

The Android app needs the TFLite model and the same normalization values to reproduce training-time behavior during inference.
