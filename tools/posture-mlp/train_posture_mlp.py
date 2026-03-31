#!/usr/bin/env python3
"""
Extract BlazePose landmarks from Docs/train/* and train a tiny MLP.
Exports:
  - posture_mlp.tflite  (float32, DEFAULT opt — reliable on all devices)
  - posture_mlp_norm.json (mean/std for Android inference)

Usage (from repo root):
  pip install -r tools/posture-mlp/requirements.txt
  python tools/posture-mlp/train_posture_mlp.py

Requires pose_landmarker_full.task next to this script or set MEDIAPIPE_POSE_TASK.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

import numpy as np

# Repo root
SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent

sys.path.insert(0, str(SCRIPT_DIR))
from feature_engineering import (  # noqa: E402
    FEATURE_COUNT,
    FEATURE_NAMES,
    compute_features_from_landmarks,
)


def stratified_train_val_split(
    X: np.ndarray,
    y: np.ndarray,
    val_frac: float = 0.15,
    seed: int = 42,
) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    rng = np.random.default_rng(seed)
    X_train_parts: list[np.ndarray] = []
    X_val_parts: list[np.ndarray] = []
    y_train_parts: list[np.ndarray] = []
    y_val_parts: list[np.ndarray] = []
    for c in (0, 1, 2):
        idx = np.where(y == c)[0]
        if len(idx) == 0:
            continue
        rng.shuffle(idx)
        n = len(idx)
        if n == 1:
            n_val = 0
        elif n == 2:
            n_val = 1
        else:
            n_val = max(1, min(n - 1, int(round(val_frac * n))))
        val_idx = idx[:n_val]
        train_idx = idx[n_val:]
        if len(train_idx) == 0:
            continue
        X_val_parts.append(X[val_idx])
        y_val_parts.append(y[val_idx])
        X_train_parts.append(X[train_idx])
        y_train_parts.append(y[train_idx])

    if not X_train_parts:
        raise SystemExit("No training samples after stratified split.")

    X_train = np.vstack(X_train_parts)
    y_train = np.concatenate(y_train_parts)

    val_rows = sum(len(a) for a in X_val_parts)
    if val_rows == 0:
        X_val = np.empty((0, FEATURE_COUNT), dtype=np.float32)
        y_val = np.empty((0,), dtype=np.int32)
    else:
        X_val = np.vstack(X_val_parts)
        y_val = np.concatenate(y_val_parts)

    return X_train, X_val, y_train, y_val

try:
    import mediapipe as mp
    from mediapipe.tasks import python as mp_python
    from mediapipe.tasks.python import vision as mp_vision
except ImportError:
    print("Install mediapipe: pip install -r tools/posture-mlp/requirements.txt", file=sys.stderr)
    raise

try:
    import tensorflow as tf
except ImportError:
    print("Install tensorflow: pip install -r tools/posture-mlp/requirements.txt", file=sys.stderr)
    raise

try:
    from PIL import Image
except ImportError:
    print("Install Pillow", file=sys.stderr)
    raise


def default_task_path() -> Path:
    p = SCRIPT_DIR / "pose_landmarker_full.task"
    if p.exists():
        return p
    env = os.environ.get("MEDIAPIPE_POSE_TASK")
    if env and Path(env).exists():
        return Path(env)
    return p


def load_rgb(path: Path) -> np.ndarray:
    im = Image.open(path).convert("RGB")
    return np.asarray(im, dtype=np.uint8)


def collect_samples(
    data_root: Path,
    task_path: Path,
) -> tuple[np.ndarray, np.ndarray, list[str]]:
    base_options = mp_python.BaseOptions(model_asset_path=str(task_path))
    options = mp_vision.PoseLandmarkerOptions(
        base_options=base_options,
        running_mode=mp_vision.RunningMode.IMAGE,
        num_poses=1,
        min_pose_detection_confidence=0.3,
        min_pose_presence_confidence=0.3,
        min_tracking_confidence=0.3,
    )
    detector = mp_vision.PoseLandmarker.create_from_options(options)

    class_dirs = [
        (data_root / "Standing", 0),
        (data_root / "Sitting", 1),
        (data_root / "Lying", 2),
    ]
    exts = {".jpg", ".jpeg", ".png", ".webp", ".avif", ".bmp"}

    X_list: list[np.ndarray] = []
    y_list: list[int] = []
    skipped: list[str] = []

    for folder, label in class_dirs:
        if not folder.is_dir():
            print(f"Warning: missing folder {folder}", file=sys.stderr)
            continue
        for fp in sorted(folder.iterdir()):
            if fp.suffix.lower() not in exts:
                continue
            try:
                rgb = load_rgb(fp)
            except Exception as e:
                skipped.append(f"{fp}: load error {e}")
                continue
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
            result = detector.detect(mp_image)
            if not result.pose_landmarks:
                skipped.append(f"{fp}: no pose")
                continue
            lm = result.pose_landmarks[0]
            feats = compute_features_from_landmarks(lm)
            if feats is None:
                skipped.append(f"{fp}: feature fail")
                continue
            X_list.append(feats)
            y_list.append(label)

    detector.close()
    if not X_list:
        raise SystemExit("No training samples — check images and pose model path.")

    X = np.stack(X_list, axis=0)
    y = np.asarray(y_list, dtype=np.int32)
    return X, y, skipped


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument(
        "--data",
        type=Path,
        default=REPO_ROOT / "Docs" / "train",
        help="Folder with Standing/, Sitting/, Lying/",
    )
    ap.add_argument(
        "--out",
        type=Path,
        default=REPO_ROOT / "android-poc" / "app" / "src" / "main" / "assets",
        help="Android assets output directory",
    )
    ap.add_argument("--epochs", type=int, default=120)
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    task_path = default_task_path()
    if not task_path.exists():
        print(
            "Download pose_landmarker_full.task to tools/posture-mlp/:\n"
            "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task",
            file=sys.stderr,
        )
        raise SystemExit(1)

    print("Collecting features from", args.data)
    X, y, skipped = collect_samples(args.data, task_path)
    print(f"Samples: {X.shape[0]}, dim={X.shape[1]}, skipped={len(skipped)}")
    if skipped[:5]:
        for s in skipped[:5]:
            print("  skip:", s)
        if len(skipped) > 5:
            print(f"  ... and {len(skipped) - 5} more")

    X_train, X_val, y_train, y_val = stratified_train_val_split(
        X, y, val_frac=0.15, seed=args.seed
    )

    mean = X_train.mean(axis=0)
    std = X_train.std(axis=0) + 1e-6
    X_train_n = (X_train - mean) / std
    X_val_n = (X_val - mean) / std

    tf.random.set_seed(args.seed)
    reg = tf.keras.regularizers.l2(1e-3)
    model = tf.keras.Sequential(
        [
            tf.keras.layers.Input(shape=(FEATURE_COUNT,)),
            tf.keras.layers.Dense(64, activation="relu", kernel_regularizer=reg),
            tf.keras.layers.Dropout(0.30),
            tf.keras.layers.Dense(32, activation="relu", kernel_regularizer=reg),
            tf.keras.layers.Dropout(0.15),
            tf.keras.layers.Dense(3, activation="softmax"),
        ]
    )
    model.compile(
        optimizer=tf.keras.optimizers.Adam(1e-3),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )

    callbacks = []
    if len(y_val) > 0:
        callbacks.append(
            tf.keras.callbacks.EarlyStopping(
                monitor="val_loss",
                patience=20,
                restore_best_weights=True,
                verbose=1,
            )
        )

    fit_kwargs = dict(
        epochs=args.epochs,
        batch_size=min(32, len(X_train_n)),
        verbose=1,
        callbacks=callbacks,
    )
    if len(y_val) > 0:
        fit_kwargs["validation_data"] = (X_val_n, y_val)
    model.fit(X_train_n, y_train, **fit_kwargs)

    if len(y_val) > 0:
        val_loss, val_acc = model.evaluate(X_val_n, y_val, verbose=0)
        print(f"Val accuracy: {val_acc:.4f}")
    else:
        print("No validation split — skipped val metrics.")

    args.out.mkdir(parents=True, exist_ok=True)

    norm = {
        "version": 1,
        "feature_count": FEATURE_COUNT,
        "feature_names": FEATURE_NAMES,
        "mean": mean.astype(np.float32).tolist(),
        "std": std.astype(np.float32).tolist(),
        "labels": ["standing", "sitting", "lying"],
    }
    norm_path = args.out / "posture_mlp_norm.json"
    with open(norm_path, "w", encoding="utf-8") as f:
        json.dump(norm, f, indent=2)
    print("Wrote", norm_path)

    # Float32 TFLite — plain float32, no quantization; maximizes runtime compatibility.
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_float = converter.convert()
    float_path = args.out / "posture_mlp.tflite"
    float_path.write_bytes(tflite_float)
    print("Wrote", float_path, f"({len(tflite_float)} bytes)")


if __name__ == "__main__":
    main()
