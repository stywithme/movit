# MediaPipe Wiki — مرجع احترافي

توثيق شامل لـ **MediaPipe** و**Pose Estimation** في مشروع POSE-2، مع التركيز على التطبيق العملي بلغة **Kotlin** على منصة **Android**.

---

## المحتويات

| الملف | الوصف |
|-------|--------|
| [01-MediaPipe-Overview.md](./01-MediaPipe-Overview.md) | المفاهيم الأساسية لـ MediaPipe وبنية Tasks API |
| [02-Pose-Landmarker-Task.md](./02-Pose-Landmarker-Task.md) | Pose Landmarker Task بالتحديد — المعالم، النماذج، الأنماط |
| [03-Android-Kotlin-Implementation.md](./03-Android-Kotlin-Implementation.md) | التطبيق في المشروع — Kotlin، CameraX، التكامل |
| [04-Landmarks-Reference.md](./04-Landmarks-Reference.md) | مرجع المعالم (33 نقطة) والروابط والاستخدام |

---

## مراجع خارجية

- [MediaPipe Pose Landmarker — Google AI](https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker)
- [MediaPipe Pose Landmarker — Android](https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker/android)
- [MediaPipe Tasks API](https://ai.google.dev/edge/mediapipe/solutions/tasks)
- [MediaPipe Samples — GitHub](https://github.com/google-ai-edge/mediapipe-samples)
- [Code Wiki — MediaPipe](https://codewiki.google/github.com/google-ai-edge/mediapipe#mediapipe-tasks)

---

## مسار الكود في المشروع

```
kmp-app/
├── app/
│   ├── build.gradle.kts          # tasks-vision:0.10.29
│   └── src/main/
│       ├── assets/
│       │   ├── app_settings.json # إعدادات الرؤية والتسوية
│       │   └── *.task            # نماذج pose_landmarker
│       └── java/.../poc/
│           ├── pose/             # PoseLandmarkerHelper, BodyLandmarks, JointLandmarkMapping
│           ├── analysis/         # LandmarkSmoother, AngleCalculator
│           ├── overlay/         # SkeletonOverlayView
│           └── camera/           # CameraManager
```
