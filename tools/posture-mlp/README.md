# Posture MLP (Standing / Sitting / Lying)

Trains a 16-feature skeleton MLP and exports TensorFlow Lite + normalization JSON for `android-poc`.

## Sync contract

Feature order and formulas are defined in:

- `feature_engineering.py`
- `android-poc/.../PostureMlpFeatureExtractor.kt`

Change both if you add or reorder features.

## Setup

```bash
cd POSE-2
pip install -r tools/posture-mlp/requirements.txt
```

Download **the same** full pose model MediaPipe uses (place next to this README):

- File: `pose_landmarker_full.task`
- URL: https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task

Or set `MEDIAPIPE_POSE_TASK` to the absolute path of any `.task` pose landmarker.

## Train and export

Default data folder: `Docs/train/` with subfolders `Standing/`, `Sitting/`, `Lying/`.

Default output: `android-poc/app/src/main/assets/` (`posture_mlp.tflite`, `posture_mlp_norm.json`).

```bash
python tools/posture-mlp/train_posture_mlp.py
```

Options:

```bash
python tools/posture-mlp/train_posture_mlp.py --data path/to/images --out path/to/assets --epochs 150
```

## Android behavior

If `posture_mlp.tflite` and `posture_mlp_norm.json` are missing from assets, the app uses the legacy `BodyPostureDetector` only.

AVIF images require a Pillow build with AVIF support; if loading fails, convert those files to JPG.
