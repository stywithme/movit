# MediaPipe — المفاهيم الأساسية

## ما هو MediaPipe؟

**MediaPipe** هو إطار عمل مفتوح المصدر من Google لبناء خطوط ML (Machine Learning) قابلة للتطبيق على أجهزة متعددة. يوفر واجهات برمجية جاهزة لمهام مثل:

- **Vision**: Pose Estimation، Face Detection، Object Detection، Image Segmentation
- **Text**: Text Classification، Language Detection
- **Audio**: Audio Classification

---

## MediaPipe Tasks API

الطبقة الحديثة للتعامل مع MediaPipe هي **Tasks API**، وهي واجهة موحدة عبر المنصات (Android، iOS، Web، Python).

### المكونات الأساسية

| المكون | الوصف |
|-------|--------|
| **BaseOptions** | إعدادات النموذج والأجهزة (مسار الملف، GPU/CPU) |
| **Delegate** | نوع المعالج: `GPU` أو `CPU` |
| **RunningMode** | نمط التشغيل: `IMAGE`، `VIDEO`، `LIVE_STREAM` |

### BaseOptions

```kotlin
val baseOptions = BaseOptions.builder()
    .setModelAssetPath("pose_landmarker_full.task")
    .setDelegate(Delegate.GPU)  // أو Delegate.CPU
    .build()
```

- **model_asset_path**: مسار ملف `.task` داخل `assets`
- **model_asset_buffer**: بديل عند تحميل النموذج من الذاكرة
- **Delegate.GPU**: أسرع، يُفضّل على الأجهزة المدعومة
- **Delegate.CPU**: احتياطي عند فشل GPU

### RunningMode

| النمط | الاستخدام | الدالة |
|-------|-----------|--------|
| **IMAGE** | صورة واحدة | `detect()` |
| **VIDEO** | فيديو مسجل (إطارات متتالية) | `detectForVideo()` |
| **LIVE_STREAM** | كاميرا مباشرة | `detectAsync()` |

---

## بنية الحزم (Packages)

في Android/Kotlin:

```
com.google.mediapipe
├── framework.image          # MPImage, BitmapImageBuilder
├── tasks.core               # BaseOptions, Delegate
├── tasks.vision.core        # RunningMode
├── tasks.vision.poselandmarker  # PoseLandmarker, PoseLandmarkerResult
└── tasks.components.containers   # NormalizedLandmark, Landmark
```

---

## تنسيق الصورة المدخل

- **الصيغة**: `RGBA_8888`
- **نسبة العرض**: 4:3 موصى بها (قريبة من تدريب النماذج)
- **الدقة**: تُحدد حسب الكاميرا، مع الحفاظ على 4:3

---

## المراجع

- [MediaPipe Tasks](https://ai.google.dev/edge/mediapipe/solutions/tasks)
- [Code Wiki — MediaPipe Tasks](https://codewiki.google/github.com/google-ai-edge/mediapipe#mediapipe-tasks)
