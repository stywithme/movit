# Pose Landmarker Task — تقدير الوضعية

## نظرة عامة

**Pose Landmarker** هو مهمة MediaPipe لاكتشاف معالم جسم الإنسان (landmarks) في الصور أو الفيديو. يعتمد على نموذج **BlazePose** ويُخرج:

- **33 نقطة** (landmarks) على الجسم
- إحداثيات **صورة** (normalized 0–1)
- إحداثيات **عالمية 3D** (world landmarks) بالمتر

---

## النماذج المتاحة

| النموذج | الملف | السرعة | الدقة | الاستخدام |
|---------|-------|--------|-------|-----------|
| **Lite** | `pose_landmarker_lite.task` | أسرع | أقل | أجهزة ضعيفة |
| **Full** | `pose_landmarker_full.task` | متوازن | جيد | **الافتراضي في المشروع** |
| **Heavy** | `pose_landmarker_heavy.task` | أبطأ | أعلى | دقة عالية |

### روابط التحميل

```
Full:  https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task
Lite:  https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task
Heavy: https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_heavy/float16/latest/pose_landmarker_heavy.task
```

يجب وضع الملف في `app/src/main/assets/`.

---

## خيارات PoseLandmarker

```kotlin
val options = PoseLandmarker.PoseLandmarkerOptions.builder()
    .setBaseOptions(baseOptions)
    .setRunningMode(RunningMode.LIVE_STREAM)
    .setNumPoses(1)
    .setMinPoseDetectionConfidence(0.5f)
    .setMinTrackingConfidence(0.5f)
    .setMinPosePresenceConfidence(0.5f)
    .setResultListener(::onPoseResult)
    .setErrorListener(::onPoseError)
    .build()
```

| الخيار | الوصف | القيمة الافتراضية |
|--------|--------|-------------------|
| `numPoses` | عدد الأجسام المكتشفة | 1 |
| `minPoseDetectionConfidence` | عتبة اكتشاف الوضعية | 0.5 |
| `minTrackingConfidence` | عتبة التتبع | 0.5 |
| `minPosePresenceConfidence` | عتبة وجود الوضعية | 0.5 |

---

## النتائج (PoseLandmarkerResult)

```kotlin
result.landmarks()        // List<List<NormalizedLandmark>> — إحداثيات الصورة
result.worldLandmarks()   // List<List<Landmark>> — إحداثيات 3D عالمية
result.timestampMs()      // الطابع الزمني
```

### NormalizedLandmark

- `x()`, `y()`, `z()`: إحداثيات معيارية (0–1 للصورة)
- `visibility()`: درجة الرؤية (0–1)
- `presence()`: درجة الحضور (0–1)

### Landmark (World)

- إحداثيات بالمتر
- أكثر استقراراً للتحليل ثلاثي الأبعاد

---

## POSE_LANDMARKS — الروابط

MediaPipe يوفر `PoseLandmarker.POSE_LANDMARKS` كقائمة من `Connection` لرسم الهيكل العظمي:

```kotlin
PoseLandmarker.POSE_LANDMARKS.forEach { connection ->
    val startIdx = connection.start()
    val endIdx = connection.end()
    // رسم خط بين landmarks[startIdx] و landmarks[endIdx]
}
```

---

## ملاحظات VIDEO Mode

- يتطلب **timestamps متزايدة** (monotonically increasing)
- عند الرجوع في الفيديو (seek) يجب إعادة إنشاء الـ landmarker
- استخدم `detectForVideo()` وليس `detect()` أو `detectAsync()`

---

## المراجع

- [Pose Landmarker — Google AI](https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker)
- [Pose Landmarker — Android](https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker/android)
