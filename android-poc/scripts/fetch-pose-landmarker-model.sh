#!/usr/bin/env bash
# Fetches pose_landmarker_full.task for Android assets + iOS bundle (same MediaPipe model).
#
# CI: invoked by .github/workflows/movit-android-release.yml before assembleRelease.
# Local: run from android-poc/ —  chmod +x scripts/fetch-pose-landmarker-model.sh && ./scripts/fetch-pose-landmarker-model.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
URL="https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task"
ANDROID_DIR="$ROOT/app/src/main/assets"
ANDROID_MODEL="$ANDROID_DIR/pose_landmarker_full.task"
IOS_DIR="$ROOT/iosApp/iosApp/Models"
IOS_MODEL="$IOS_DIR/pose_landmarker_full.task"

mkdir -p "$ANDROID_DIR" "$IOS_DIR"

if [[ -f "$ANDROID_MODEL" && -f "$IOS_MODEL" ]]; then
  echo "pose_landmarker_full.task already present for Android and iOS."
  exit 0
fi

if [[ ! -f "$ANDROID_MODEL" && ! -f "$IOS_MODEL" ]]; then
  echo "Downloading pose_landmarker_full.task..."
  curl -fsSL -o "$ANDROID_MODEL" "$URL"
fi

if [[ -f "$ANDROID_MODEL" && ! -f "$IOS_MODEL" ]]; then
  cp "$ANDROID_MODEL" "$IOS_MODEL"
elif [[ -f "$IOS_MODEL" && ! -f "$ANDROID_MODEL" ]]; then
  cp "$IOS_MODEL" "$ANDROID_MODEL"
fi

echo "Android: $ANDROID_MODEL"
echo "iOS:     $IOS_MODEL"
