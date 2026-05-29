# التطبيق في المشروع — Android & Kotlin

## نظرة عامة

المشروع يستخدم **MediaPipe Pose Landmarker** عبر طبقة مساعدة مخصصة (`PoseLandmarkerHelper`) مع **CameraX** و**Kotlin**.

---

## التبعيات

```kotlin
// app/build.gradle.kts
implementation("com.google.mediapipe:tasks-vision:0.10.29")
```

> **ملاحظة**: الإصدار 0.10.29 يدعم 16KB page size المطلوب لـ Android 15+.

---

## بنية الكود

### 1. PoseLandmarkerHelper

**المسار**: `com.trainingvalidator.poc.pose.PoseLandmarkerHelper`

- غلاف حول `PoseLandmarker` لإدارة التهيئة والمعالجة
- يدعم **LIVE_STREAM** (كاميرا) و**VIDEO** (فيديو مسجل)
- يحول `ImageProxy` إلى `MPImage` ويعالج الدوران والانعكاس

```kotlin
poseLandmarkerHelper = PoseLandmarkerHelper(context, listener)
poseLandmarkerHelper.initialize(modelType = ModelType.FULL, useGpu = true)
poseLandmarkerHelper.detectPose(imageProxy, isFrontCamera)
```

### 2. ModelType و RunMode

```kotlin
enum class ModelType(val fileName: String, val displayName: String) {
    FULL("pose_landmarker_full.task", "Full (Balanced)"),
    HEAVY("pose_landmarker_heavy.task", "Heavy (Accuracy)")
}

enum class RunMode {
    LIVE_STREAM,  // detectAsync
    VIDEO         // detectForVideo
}
```

### 3. PoseResult

```kotlin
data class PoseResult(
    val landmarks: List<NormalizedLandmark>,
    val worldLandmarks: List<Landmark>?,
    val timestampMs: Long,
    val inferenceTimeMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val modelType: String,
    val isFrontCamera: Boolean = false
)
```

---

## الكاميرا (CameraManager)

- **نسبة العرض**: 4:3 (`RATIO_4_3_FALLBACK_AUTO_STRATEGY`)
- **صيغة الصورة**: `OUTPUT_IMAGE_FORMAT_RGBA_8888`
- **استراتيجية الضغط**: `STRATEGY_KEEP_ONLY_LATEST`

```kotlin
ImageAnalysis.Builder()
    .setResolutionSelector(resolutionSelector)
    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
```

---

## تحويل الإطار

1. نسخ البكسلات من `ImageProxy.planes[0].buffer` إلى `Bitmap`
2. تطبيق الدوران (`rotationDegrees`) والانعكاس (كاميرا أمامية)
3. إنشاء `MPImage` عبر `BitmapImageBuilder`
4. استدعاء `detectAsync(mpImage, timestamp)`

> **مهم**: استخدام `SystemClock.uptimeMillis()` للـ timestamps في LIVE_STREAM.

---

## الكاميرا الأمامية (Front Camera)

- الصورة تُعكس قبل المعالجة
- المعالم تُرجع كما هي (بدون تبديل LEFT/RIGHT)
- المشروع يطبق **تبديل LEFT ↔ RIGHT** عند حساب الزوايا وعرض النتائج

```kotlin
// BodyLandmarks.getMirroredIndex(index)
// JointAngles.mirrored()
```

---

## التسوية (LandmarkSmoother)

- **One Euro Filter**: توازن بين الاستجابة والاستقرار
- **EMA Legacy**: خيار بديل أبسط
- إعدادات من `app_settings.json` → `SettingsManager`

```json
"smoothing": {
  "preset": "custom",
  "minCutoff": 3.0,
  "beta": 0.05,
  "useLegacyEMA": false
}
```

---

## المعالم الافتراضية (Virtual Landmarks)

- **Neck (33)**: منتصف الكتفين
- **Spine (34)**: منتصف الوركين

تُحسب في `LandmarkSmoother.appendVirtualLandmarks()` وتُستخدم في `AngleCalculator` و`JointLandmarkMapping`.

---

## ProGuard

```pro
# proguard-rules.pro
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
```

---

## المراجع الداخلية

- `PoseLandmarkerHelper.kt`
- `CameraManager.kt`
- `BodyLandmarks.kt`
- `JointLandmarkMapping.kt`
- `LandmarkSmoother.kt`
- `SkeletonOverlayView.kt`
