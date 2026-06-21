# Posture MLP — Training Guide

> Arabic version: [TRAINING_GUIDE_AR.md](./TRAINING_GUIDE_AR.md)

## 1. Overview

A tiny MLP (Multi-Layer Perceptron) classifier that takes a single image as input
and outputs one of three posture classes:

| Class | Label    | Code |
|-------|----------|------|
| 0     | Standing | `STANDING` |
| 1     | Sitting  | `SITTING`  |
| 2     | Lying    | `LYING`    |

The model does **not** process the image directly. Instead:

```
Image → MediaPipe BlazePose → 33 Landmarks → 16 Engineered Features → MLP → Class (0/1/2)
```

This design keeps the model extremely small (~15 KB) and fast enough
for real-time mobile inference on any Android device.

---

## 2. How Training Works — Step by Step

### 2.1 Data Collection (`Docs/train/`)

Images are organized into three folders:

```
Docs/train/
  ├── Standing/   (124 images)
  ├── Sitting/    (150 images)
  └── Lying/      (184 images)
```

**Total: 458 images.** Supported formats: `.jpg`, `.jpeg`, `.png`, `.webp`, `.avif`, `.bmp`.

### 2.1.1 Labels (folder-based supervision)

We do **not** use a separate label file (no per-image CSV/JSON manifest) and no
filename-based tags.

The **class label is implicit** from the **parent folder**:

| Folder | Class index | Meaning |
|--------|-------------|---------|
| `Docs/train/Standing/` | `0` | Every image in this folder is **Standing** |
| `Docs/train/Sitting/` | `1` | Every image in this folder is **Sitting** |
| `Docs/train/Lying/` | `2` | Every image in this folder is **Lying** |

`train_posture_mlp.py` maps folders to indices in `class_dirs` (Standing→0,
Sitting→1, Lying→2). For each image it extracts the **16-D feature vector**
from pose landmarks, then pairs that vector with the **label implied by the folder**.

**Implication:** putting an image in the wrong folder = **wrong training label**.
The MLP never sees raw pixels; it learns to map features to the class you chose
by placing the file under `Standing/`, `Sitting/`, or `Lying/`.

### 2.2 Feature Extraction (`feature_engineering.py`)

For each image, the training script:

1. Loads the image as RGB.
2. Runs **MediaPipe Pose Landmarker** (`pose_landmarker_full.task`) to detect
   33 body keypoints (x, y, z, visibility) in normalized coordinates.
3. Computes **16 geometric features** from those landmarks.

If no pose is detected or the torso is too short (< 0.02 normalized length),
the image is skipped.

#### The 16 Features

| # | Feature Name | How It's Computed | What It Captures |
|---|---|---|---|
| 0 | `spine_angle_norm` | Angle of shoulder-mid → hip-mid vector relative to horizontal, divided by 180 | **Primary posture signal.** Standing ≈ 0.5 (90°), Lying ≈ 0.0 or 1.0 |
| 1 | `torso_len` | Euclidean distance from shoulder center to hip center | Body scale / distance from camera |
| 2 | `cos_torso_thigh` | Cosine of angle between torso vector and thigh vector (hip→knee) | **Sitting detector.** Standing ≈ 1.0 (aligned), Sitting < 0.5 (bent) |
| 3 | `knee_ang_L` | Left hip-knee-ankle angle / 180 | Leg bend (left) |
| 4 | `knee_ang_R` | Right hip-knee-ankle angle / 180 | Leg bend (right) |
| 5 | `shoulder_w` | Horizontal shoulder width / torso length | View direction indicator (narrow = side view) |
| 6 | `hip_w` | Horizontal hip width / torso length | View direction indicator |
| 7 | `knee_drop` | Vertical distance knee-center to hip-center / torso length | Standing = positive, Lying ≈ 0 |
| 8 | `ankle_drop` | Vertical distance ankle-center to knee-center / torso length | Leg extension below knees |
| 9 | `nose_off` | Vertical nose offset from torso midpoint / torso length | Head position relative to body |
| 10 | `sh_v_sep` | Vertical distance between left & right shoulders / torso length | Side-lying indicator (shoulders stacked) |
| 11 | `hip_v_sep` | Vertical distance between left & right hips / torso length | Side-lying indicator (hips stacked) |
| 12 | `vis_knee` | Min visibility of left/right knee | Landmark reliability signal |
| 13 | `vis_hip` | Min visibility of left/right hip | Landmark reliability signal |
| 14 | `vis_sh` | Min visibility of left/right shoulder | Landmark reliability signal |
| 15 | `z_torso` | Abs Z-depth difference between shoulder center and hip center | Depth/rotation signal |

All features are designed to be **scale-invariant** (divided by torso length)
and **view-robust** (combining multiple body segment relationships).

### 2.3 Data Split

The data is split using **Stratified Sampling** (15% validation):

- Each class is split independently, ensuring proportional representation.
- Split is deterministic (`seed=42`) for reproducibility.
- With 458 samples: ~389 training, ~69 validation.

### 2.4 Normalization

Features are normalized using **Z-score normalization** computed from the
**training set only**:

```
feature_normalized = (feature - mean) / std
```

The `mean` and `std` arrays are saved to `posture_mlp_norm.json` and must be
used at inference time (on Android) with exactly the same computation.

### 2.5 Model Architecture

```
Input (16 float32) → Dense(64, ReLU, L2) → Dropout(0.30)
                    → Dense(32, ReLU, L2) → Dropout(0.15)
                    → Dense(3, Softmax) → Output (3 probabilities)
```

| Component | Purpose |
|---|---|
| `Dense(64)` | First hidden layer — learns primary feature combinations |
| `Dense(32)` | Second hidden layer — refines decision boundaries |
| `L2(1e-3)` | Weight regularization — penalizes large weights to prevent overfitting |
| `Dropout(0.30)` | Randomly zeros 30% of neurons during training — forces redundancy |
| `Dropout(0.15)` | Lighter dropout before final layer |
| `Softmax` | Converts raw scores to probabilities summing to 1.0 |

**Optimizer:** Adam (learning rate 1e-3)
**Loss:** Sparse Categorical Cross-Entropy

### 2.6 Training Loop

- **Max epochs:** 200
- **Batch size:** 32
- **Early Stopping:** Monitors `val_loss` with patience=20. If validation loss
  doesn't improve for 20 consecutive epochs, training stops and the model
  reverts to the best weights.

### 2.7 Export

The trained Keras model is converted to **TensorFlow Lite** (float32, no
quantization) for Android deployment:

```
posture_mlp.tflite      — The model (~15 KB)
posture_mlp_norm.json   — Mean/std arrays for feature normalization
```

> **Important:** We do NOT use `converter.optimizations` (dynamic range
> quantization) because it produces `FULLY_CONNECTED v12` ops that require
> TFLite runtime ≥ 2.17, while our Android dependency is 2.16.1. Plain float32
> export uses older op versions that are universally compatible.

---

## 3. What the Training Numbers Mean

### Training Output Example

```
Epoch 27/200
accuracy: 0.9383 - loss: 0.2510 - val_accuracy: 0.8971 - val_loss: 0.3204
...
Epoch 47: early stopping
Restoring model weights from the end of the best epoch: 27.
Val accuracy: 0.8971
```

### Glossary

| Metric | Meaning | Good Direction |
|---|---|---|
| **accuracy** | % of training samples correctly classified this epoch | ↑ higher |
| **loss** | Cross-entropy loss on training data (lower = model is more confident and correct) | ↓ lower |
| **val_accuracy** | % of validation samples correctly classified (unseen data) | ↑ higher |
| **val_loss** | Cross-entropy loss on validation data | ↓ lower |
| **early stopping** | Training halted because val_loss stopped improving | Prevents overfitting |
| **best epoch** | The epoch with the lowest val_loss — model weights are restored to this point | — |

### How to Read These Numbers

- **accuracy = 0.9383**: 93.8% of training images were classified correctly.
- **val_accuracy = 0.8971**: 89.7% of validation images were classified correctly.
- **Gap (93.8% vs 89.7%)**: Small gap (~4%) is healthy. A large gap (e.g., 99%
  vs 70%) means overfitting — the model memorized training data but can't
  generalize.
- **loss = 0.2510**: The model's average "surprise" when seeing training data.
  Lower is better. Perfect would be 0.0.
- **val_loss = 0.3204**: Same metric on validation data. This is what Early
  Stopping monitors.
- **Epoch 47 / patience=20**: Training ran 47 epochs but the best was epoch 27.
  Epochs 28-47 didn't improve val_loss, so training stopped.

### What is "Good" Performance?

| Metric | Our Value | Interpretation |
|---|---|---|
| Val accuracy | **89.7%** | Good for 458 images. ~10% errors are expected from ambiguous poses, bad angles, or mislabeled images |
| Model size | **15 KB** | Extremely small. No impact on app size or load time |
| Inference | **< 1ms** | The MLP itself is negligible; the bottleneck is MediaPipe landmark detection |

---

## 4. How to Improve the Model

### 4.1 More Data (Highest Impact)

The single most effective improvement. Current dataset:

| Class | Images | Status |
|---|---|---|
| Standing | 124 | Could use more variety (angles, distances) |
| Sitting | 150 | Good baseline |
| Lying | 184 | Good baseline |

**Targets for better accuracy:**

- 300+ images per class → expected ~93-95% accuracy
- 500+ images per class → expected ~95-97% accuracy
- Include diverse: lighting, camera angles, body types, clothing, backgrounds
- Include edge cases: leaning, transitioning between poses, unusual angles

**How to add images:**

1. Add `.jpg` / `.png` files to the appropriate `Docs/train/` subfolder
2. Re-run training (see Section 6)

### 4.2 Data Quality

- **Remove mislabeled images**: Even 5% mislabeled data can cap accuracy at ~95%.
- **Remove ambiguous poses**: Images where a human would disagree on the label
  (e.g., half-sitting, half-lying) confuse the model.
- **Ensure variety**: Don't just add 100 similar images. Vary the conditions.

### 4.3 Data Augmentation (Code Change)

Add synthetic variations during training:

- Small noise injection on features (simulate landmark jitter)
- Slight perturbation of visibility values
- Mirror left/right features (swap L/R angles, L/R widths)

This effectively multiplies your dataset without new images.

### 4.4 Architecture Tuning (Diminishing Returns)

The current architecture is already well-suited for 16 features and 3 classes.
Potential tweaks:

| Change | Expected Effect |
|---|---|
| More layers (3-4 hidden) | Marginal improvement, risk of overfitting |
| Wider layers (128, 64) | Marginal improvement with more data |
| Batch Normalization | May help with > 1000 samples |
| Different activations (GELU, SiLU) | Negligible for this task |

### 4.5 Feature Engineering (Medium Impact)

Additional features that could help:

- Elbow angles (arm position)
- Wrist-to-hip distance (arms resting vs raised)
- Temporal features (if using video: velocity of landmarks)
- Body aspect ratio (bounding box width / height)

> **Warning:** Adding features requires updating BOTH `feature_engineering.py`
> AND `PostureMlpFeatureExtractor.kt` to match exactly.

### 4.6 Hyperparameter Tuning

| Parameter | Current | Try |
|---|---|---|
| Learning rate | 1e-3 | 5e-4, 3e-4 |
| L2 regularization | 1e-3 | 1e-4, 5e-4 |
| Dropout | 0.30 / 0.15 | 0.20 / 0.10, 0.40 / 0.20 |
| Batch size | 32 | 16, 64 |
| Patience | 20 | 30 (with more data) |

---

## 5. Building on the Model vs. Training from Scratch

### Short Answer

**The model is trained from scratch each time.** There is no incremental /
fine-tuning step currently.

### Why?

1. **Training is fast** — the entire pipeline (feature extraction + training)
   takes ~30 seconds on a modern machine. There's no benefit to saving
   intermediate checkpoints for a 15 KB model.

2. **Feature extraction is the bottleneck** — MediaPipe processes each image
   (~200ms per image × 458 images ≈ 90 seconds). The MLP training itself is
   < 5 seconds.

3. **Deterministic reproducibility** — same data + same seed = same model.
   This is important for debugging and comparing changes.

### Can We Do Transfer Learning?

Not in the traditional sense (like fine-tuning a pre-trained ImageNet model),
because:

- The model input is **16 hand-crafted features**, not raw pixels
- The model is only 3 layers — there's nothing meaningful to "transfer"
- The features are specific to MediaPipe's landmark format

However, the **feature engineering itself is a form of transfer learning** —
we leverage MediaPipe's pre-trained pose model (trained on millions of images)
to extract landmarks, then train a tiny classifier on top.

### What Can Be Preserved Between Runs?

| What | Preserved? | How |
|---|---|---|
| Training images | Yes | Stay in `Docs/train/` |
| Feature definitions | Yes | `feature_engineering.py` (don't change without updating Kotlin) |
| Normalization stats | Regenerated | Based on training data each run |
| Model weights | Regenerated | Trained from scratch each run |
| Hyperparameters | Yes | In `train_posture_mlp.py` arguments |

### Future: Incremental Training

If the dataset grows large (1000+ images), consider:

1. **Saving the Keras model** (`.keras` file) to avoid re-extracting features
2. **Caching extracted features** to a `.npz` file
3. **Fine-tuning** the saved model on new images only

This would require modifying `train_posture_mlp.py` to support a `--resume`
flag.

---

## 6. How to Re-train

### Prerequisites

```bash
cd tools/posture-mlp
pip install -r requirements.txt
```

Download `pose_landmarker_full.task` to `tools/posture-mlp/`:
```
https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task
```

### Run Training

```bash
# From repo root
python tools/posture-mlp/train_posture_mlp.py --epochs 200
```

Output files are written directly to `kmp-app/app/src/main/assets/`:
- `posture_mlp.tflite`
- `posture_mlp_norm.json`

### Custom Options

```bash
python tools/posture-mlp/train_posture_mlp.py \
  --data path/to/images \
  --out path/to/output \
  --epochs 300 \
  --seed 123
```

### After Training

1. **Build & Run** the Android app — the new model is automatically picked up
2. Use the **MLP debug tab** in Debug Activity to verify predictions
3. If the model doesn't load, **Force Stop** the app and reopen (the singleton
   caches the first load attempt)

---

## 7. Compatibility Notes

### TFLite Runtime Version

| Component | Version | Notes |
|---|---|---|
| Python TensorFlow | 2.18.1 | Used for training |
| Android TFLite runtime | 2.16.1 | `org.tensorflow:tensorflow-lite` |
| Export mode | float32, no quantization | Avoids `FULLY_CONNECTED v12` op |

**Do NOT** add `converter.optimizations` back. Dynamic range quantization
produces op versions incompatible with the Android runtime.

### Feature Parity

The 16-feature computation must be **identical** in:

- `tools/posture-mlp/feature_engineering.py` (Python, training)
- `PostureMlpFeatureExtractor.kt` (Kotlin, Android inference)

Any change to one **must** be mirrored in the other, followed by re-training.

---

## 8. File Reference

```
tools/posture-mlp/
  ├── requirements.txt           — Python dependencies
  ├── feature_engineering.py     — 16-feature vector definition (contract)
  ├── train_posture_mlp.py       — Training + export script
  ├── TRAINING_GUIDE.md          — This document
  └── README.md                  — Quick start

kmp-app/app/src/main/
  ├── assets/
  │   ├── posture_mlp.tflite     — Trained model (~15 KB)
  │   └── posture_mlp_norm.json  — Normalization parameters
  └── java/.../training/engine/
      ├── PostureMlpFeatureExtractor.kt  — Feature computation (mirrors Python)
      ├── PostureMlpClassifier.kt        — TFLite loading & inference
      └── PoseSceneDetector.kt           — Integration with pose pipeline

Docs/train/
  ├── Standing/   — Training images (class 0)
  ├── Sitting/    — Training images (class 1)
  └── Lying/      — Training images (class 2)
```
