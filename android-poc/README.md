# Training Validator - Android PoC

## 🎯 Purpose
This is a **Proof of Concept (PoC)** Android application to validate the core technology stack for the Training Validator project.

## 🧪 What This PoC Validates

| Question | Test |
|----------|------|
| Does MediaPipe BlazePose detect poses accurately? | Visual skeleton overlay |
| Are joint angles calculated correctly? | Real-time angle display |
| Is the performance acceptable (FPS)? | FPS counter |
| Does it work with front/back camera? | Camera switch button |

## 📁 Project Structure

```
android-poc/
├── app/
│   ├── src/main/
│   │   ├── java/com/trainingvalidator/poc/
│   │   │   ├── ui/
│   │   │   │   └── MainActivity.kt          # Main activity
│   │   │   ├── camera/
│   │   │   │   └── CameraManager.kt         # CameraX handler
│   │   │   ├── pose/
│   │   │   │   ├── PoseLandmarkerHelper.kt  # MediaPipe wrapper
│   │   │   │   └── BodyLandmarks.kt         # Landmark constants
│   │   │   ├── analysis/
│   │   │   │   └── AngleCalculator.kt       # Angle calculation
│   │   │   └── overlay/
│   │   │       └── SkeletonOverlayView.kt   # Skeleton drawing
│   │   └── res/
│   │       ├── layout/
│   │       ├── drawable/
│   │       └── values/
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34
- JDK 17
- Physical Android device (emulator won't work well for camera)

### Setup

1. **Clone and open in Android Studio**

2. **Download MediaPipe Model**
   
   Download the pose landmarker model from:
   https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task
   
   Place it in:
   ```
   app/src/main/assets/pose_landmarker_full.task
   ```

3. **Sync Gradle and Run**

### Usage

1. Grant camera permission when prompted
2. Stand in front of the camera (2-3 meters away)
3. Ensure good lighting
4. Observe the skeleton overlay and angle values

## 📊 Expected Results

### Skeleton Detection
- ✅ All 33 body landmarks should be detected
- ✅ Skeleton should track smoothly without jitter
- ✅ Works with both front and back camera

### Angle Calculation
- ✅ Angles should be between 0-180 degrees
- ✅ Values should be stable (minimal jitter)
- ✅ Angles should match visual body position

### Performance
- ✅ Target FPS: 15-20 fps
- ✅ No visible lag between movement and skeleton update

## 🛠 Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| Camera | CameraX 1.3.1 |
| Pose Detection | MediaPipe Tasks Vision 0.10.9 |
| Min SDK | API 24 (Android 7.0) |
| Target SDK | API 34 (Android 14) |

## 📐 Angle Calculations

The app calculates angles at these joints:

| Joint | Points Used |
|-------|-------------|
| Elbow | Shoulder → Elbow → Wrist |
| Shoulder | Elbow → Shoulder → Hip |
| Hip | Shoulder → Hip → Knee |
| Knee | Hip → Knee → Ankle |
| Ankle | Knee → Ankle → Foot |
| Spine | Shoulder midpoint → Hip midpoint (vs vertical) |

## 🎨 Color Coding

| Color | Meaning |
|-------|---------|
| 🟢 Green | Correct / Visible |
| 🔵 Blue | Connection lines |
| 🔴 Red | Error (in future: incorrect angle) |

## 📝 Next Steps (After PoC Validation)

1. [ ] Add exercise configuration (angle thresholds)
2. [ ] Implement rep counting logic
3. [ ] Add error detection and feedback
4. [ ] Integrate with Flutter via Method Channels
5. [ ] Add audio feedback

## 🐛 Known Limitations

- Single person detection only
- Requires good lighting
- Best results with full body visible
- Front camera is mirrored

## 📄 License

Private - Training Validator Project
