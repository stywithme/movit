# التطبيق في المشروع — Android & Kotlin (KMP)

> **Status:** `ACTIVE` — as-built MediaPipe integration in `core/pose-capture`.  
> **Verified:** 2026-06-22

## نظرة عامة

المشروع يستخدم **MediaPipe Pose Landmarker** عبر `MediaPipePoseDetector` (`com.movit.core.posecapture.android`) مع **CameraX** في مسار التدريب الإنتاجي. وضع الفيديو متاح فقط في **`feature/training-debug`** (ليس إنتاجاً).

---

## التبعيات

```kotlin
// kmp-app/gradle/libs.versions.toml
mediapipe-tasks-vision = "0.10.33"  // com.google.mediapipe:tasks-vision
```

---

## بنية الكود

### 1. MediaPipePoseDetector

**المسار:** `kmp-app/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/MediaPipePoseDetector.kt`

- يطبّق `com.movit.core.training.boundary.PoseDetector`
- يغلّف `PoseLandmarker` (Tasks Vision API)
- **LIVE_STREAM:** `detectAsync` من إطارات CameraX
- يحوّل `ImageProxy` → `Bitmap` → `MPImage` مع دوران وانعكاس الكاميرا الأمامية
- يمرّر النتيجة عبر `MediaPipeLandmarkMapper` و`PoseLandmarkSmoother` → `PoseFrameAssembler` → `PoseFrame`

```kotlin
// Typical wiring (Android DI: MovitPoseCaptureModule)
val detector = MediaPipePoseDetector(context, poseRefiner, modelPort)
detector.detect(imageProxy, isFrontCamera)
```

### 2. CameraXFrameSource

**المسار:** `.../android/CameraXFrameSource.kt`

- `ImageAnalysis` بنسبة 4:3، `RGBA_8888`، `STRATEGY_KEEP_ONLY_LATEST`
- يغذّي `MediaPipePoseDetector` في مسار `feature/training`

### 3. PoseFrame (common)

**المسار:** `com.movit.core.training.model.PoseFrame` في `core/training-engine`

```kotlin
PoseFrame(
    hasPose: Boolean,
    landmarks: List<Landmark>?,
    angles: JointAngles,
    timestampMs: Long,
    isFrontCamera: Boolean,
    analysisImageWidth: Int,
    analysisImageHeight: Int,
)
```

يستهلكه `MovitTrainingEngine.processFrame` و`SetupReadinessGate.validate`.

### 4. نماذج Pose (debug)

**المسار:** `com.movit.core.posecapture.boundary.trainingdebug.PoseModelType`

- `FULL`, `HEAVY` — ملفات `.task` في assets
- `AndroidPoseModelTypePort` / `PoseLandmarkerHeavyModelStore` — تفضيل النموذج على Android

---

## تحويل الإطار

1. نسخ بكسلات `ImageProxy` إلى `Bitmap`
2. تطبيق `rotationDegrees` وانعكاس الكاميرا الأمامية
3. `BitmapImageBuilder` → `MPImage`
4. `detectAsync(mpImage, timestamp)` — استخدم `SystemClock.uptimeMillis()` في LIVE_STREAM

---

## الكاميرا الأمامية

- الإطار يُعكس في `MovitTrainingEngine` (`frame.mirrored()`) قبل استخراج الزوايا
- `JointAngleTracker` و`VirtualLandmarks` تتعامل مع معالم MediaPipe كما هي (بدون swap LEFT/RIGHT في الطبقة السفلية)

---

## التسوية (PoseLandmarkSmoother)

**المسار:** `com.movit.core.posecapture.PoseLandmarkSmoother`

- One Euro Filter (افتراضي) مع خيارات legacy EMA
- يضيف معالم افتراضية: **neck (33)**, **spine (34)** عبر `VirtualLandmarks` في `core/training-engine`

---

## وضع الفيديو (debug فقط)

| Module | Classes |
|--------|---------|
| `feature/training-debug` | `AndroidDebugVideoPoseSource`, `TrainingDebugViewModel` |
| `core/pose-capture` | `TrainingDebugInputMode.VIDEO`, `TrainingDebugVideoFrameSelector` |

لا يوجد تشغيل فيديو في `feature/training` الإنتاجي.

---

## ProGuard

```pro
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
```

---

## مراجع داخلية

| File | Role |
|------|------|
| `MediaPipePoseDetector.kt` | Landmarker + frame conversion |
| `CameraXFrameSource.kt` | Production camera |
| `MediaPipeLandmarkMapper.kt` | Normalized → app `Landmark` |
| `PoseLandmarkSmoother.kt` | Temporal smoothing |
| `MovitPoseCaptureModule.kt` | Android DI wiring |
| [`training-engine.md`](../../00-Active-Reference/Engine/training-engine.md) | Engine consumption of `PoseFrame` |
