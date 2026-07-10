# Track A — Android Capture (CameraX + MediaPipe)

> مراجعة قراءة فقط — لا تعديلات على الكود.  
> التاريخ: 2026-07-10  
> النطاق: مسار الالتقاط Android الساخن + بوابات البدء/التبديل + إعدادات الإنتاجية + لقطات JPEG.

---

## 1. Executive summary

- المسار الساخن مؤكد: `TrainingSessionCameraHost` → `CameraXFrameSource` (analysisExecutor) → `MediaPipePoseDetector.detectAsync` → callback MediaPipe → `LandmarkSmoother` + `PoseFrameAssembler` → `LensSwitchFrameGate` → VM.
- الإنتاج الافتراضي **ليس 30fps**: `TrainingThroughputProfiles.STABLE` = **320×240 @ 10fps**؛ ملفات `legacy`/`high` ترفع الضغط بشكل حاد.
- **PF-01 مؤكد**: كل إطار يخصص `toBitmap()` + غالباً `Bitmap.createBitmap` عند الدوران ≠ 0، بلا pool — عند 30fps/640×480 ≈ 36MB/s churn نظري.
- **PF-04 مؤكد**: `inferenceInFlight` يبقى `true` حتى انتهاء التنعيم + التجميع + التسليم، فيطيل فترة الانشغال ويخفض معدل الإرسال الفعلي.
- **PF-24 مؤكد**: الـ Host يستدعي `stop()` فقط؛ `dispose()` **لا يُستدعى من أي مكان**؛ `CameraXFrameSource`/`MediaPipePoseDetector` هما Koin `single` فيبقيان دافئين (PoseLandmarker + executor + lastFrameBitmap) لعمر العملية.
- **PF-03 مؤكد ببطء**: `frameCameraState` يُملأ عند الإرسال ويُزال عند النتيجة؛ إسقاط/خطأ MediaPipe دون `remove` يترك مدخلات؛ النمو بطيء عملياً.
- سباق ربط خطير (جديد): `bindingInProgress` يُسقِط `bindUseCases` بصمت أثناء تبديل العدسة → `switchingCamera` + `LensSwitchFrameGate` قد يعلقان وكبت الإطارات إلى الأبد.
- سباق لقطة (جديد، مرتبط PF-02): `takeSnapshotJpeg` ينسخ bitmap **خارج** `bitmapLock` بينما خيط التحليل قد يعمل `recycle()` على نفس الكائن.
- طبقات الإسقاط الخمس كلها موجودة في الكود؛ الطبقتان 2 و3 متكاملتان أكثر من كونهما زائدتين بالكامل — قياس جهاز مطلوب (PF-22 = NEEDS-DATA).
- `AndroidPoseRefiner.isAvailable = false` — مسار refine ميت فعلياً في الإنتاج.

---

## 2. Answers to A1–A8

### A1. تكلفة تجهيز الإطار (Bitmap lifecycle)

**سلسلة الاستدعاء** (`MediaPipePoseDetector.detectAsync`):

1. `imageProxy.toBitmap()` → Bitmap جديد (CameraX دائماً يخصص).
2. إن `rotationDegrees != 0`: `rotateBitmapForAnalysis` → `Bitmap.createBitmap` + `Canvas.drawBitmap`، ثم `sourceBitmap.recycle()`.
3. إن `rotationDegrees == 0`: يُعاد استخدام `sourceBitmap` كما هو.
4. `lastFrameBitmap` يُحدَّث تحت `bitmapLock` مع `recycle()` للسابق (إن اختلف المرجع).
5. `BitmapImageBuilder(analysisBitmap).build()` — غلاف MediaPipe (لا يُنشئ نسخة بكسل إضافية عادةً؛ يعتمد على تنفيذ MP).

**عدد الـ Bitmaps المخصصة لكل إطار مقبول للاستدلال:**

| حالة الدوران | تخصيصات Bitmap/إطار | ذروة حية قبل recycle | ما يبقى بعد الإطار |
|---|---|---|---|
| `rotation == 0` | 1 (`toBitmap`) | 1 | 1 (`lastFrameBitmap`) |
| `rotation != 0` (شائع في وضع عمودي) | 2 (`toBitmap` + `createBitmap`) | 2 ثم يُعاد تدوير المصدر | 1 (`lastFrameBitmap`) |

لا يوجد bitmap pool / reuse buffer.

**تقدير الحجم (ARGB_8888 = 4 بايت/بكسل):**

| Profile | دقة تحليل مستهدفة | بايت/bitmap | @ targetFps churn تخصيص | ملاحظة |
|---|---|---|---|---|
| STABLE (إنتاج) | 320×240 | ~300 KB | ~3.0 MB/s @ 10fps (×1) أو ~6.0 MB/s إن دوران دائماً (×2) | الافتراضي |
| MEDIUM | 480×360 | ~675 KB | ~10–20 MB/s @ 15fps | |
| HIGH / LEGACY | 640×480 | ~1.23 MB | ~25–37 MB/s @ 20–30fps مع دوران | الأسوأ |

عند **30fps و640×480 مع دوران** (سيناريو brief): ≈ **2 تخصيص/إطار × 1.23MB × 30 ≈ 74MB/s** ذروة churn قبل recycle المصدر؛ بعد recycle يبقى ~37MB/s تخصيص صافٍ للـ GC + نسخة واحدة محتفظ بها.

**هل يمكن pool؟** نعم اتجاهياً: إعادة استخدام `ARGB_8888` بنفس الأبعاد للدوران، وتجنب `toBitmap` عبر `ImageProxy`→YUV/ByteBuffer مباشر إلى MediaPipe إن أمكن — غير منفّذ حالياً.

**Evidence:**

```168:184:kmp-app/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/MediaPipePoseDetector.kt
            val sourceBitmap = imageProxy.toBitmap()
            imageProxy.close()
            val analysisBitmap = if (rotationDegrees == 0) {
                sourceBitmap
            } else {
                val rotated = rotateBitmapForAnalysis(sourceBitmap, rotationDegrees)
                sourceBitmap.recycle()
                rotated
            }
            ...
            val mpImage = BitmapImageBuilder(analysisBitmap).build()
            marker.detectAsync(mpImage, frameTime)
```

```213:226:kmp-app/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/MediaPipePoseDetector.kt
    internal fun rotateBitmapForAnalysis(source: Bitmap, rotationDegrees: Int): Bitmap {
        ...
        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        Canvas(output).apply { ... drawBitmap(source, 0f, 0f, null) }
        return output
    }
```

```21:25:kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/boundary/TrainingThroughputProfile.kt
    val STABLE = TrainingThroughputProfile(
        id = "stable",
        analysisWidth = 320,
        analysisHeight = 240,
        targetFps = 10,
    )
```

---

### A2. تسلسل الاستدلال (`inferenceInFlight`)

**آلية البوابة:**

- الاكتساب: `tryAcquireInferenceSlot` عبر `compareAndSet(false, true)` قبل أي عمل bitmap.
- التحرير: في `onPoseResult` داخل `finally` **بعد** كامل سلسلة المستمع؛ وأيضاً عند فشل `detectAsync` / `ErrorListener`.

**ما يحدث والبوابة مغلقة** (`onPoseResult:275-313` ثم مستمع `CameraXFrameSource:176-190`):

1. حساب `lastInferenceTimeMs`
2. `frameCameraState.remove`
3. `MediaPipeLandmarkMapper` (قائمتا raw)
4. `LandmarkSmoother.smooth` + `smoothWorld` (One-Euro × حتى 33×2 → قوائم Landmark جديدة)
5. `poseRefiner` (no-op حالياً)
6. `listener.onPoseDetected` → `PoseFrameAssembler.assemble` (زوايا + virtual landmarks)
7. `emitPoseFrame` → `LensSwitchFrameGate` → `frameListener` → `channel.trySend` في الـ VM

**تقدير زمن ما بعد الاستدلال (نظري، NEEDS-DATA للجهاز):**

| مرحلة | تقدير تقريبي |
|---|---|
| Mapping + One-Euro 33+33 | ~0.5–2 ms |
| `PoseFrameAssembler.assemble` | ~1–4 ms |
| تسليم + trySend | ≪1 ms |
| **مجموع post** | **~2–6 ms** نموذجي |

هذا يُضاف إلى زمن الاستدلال الفعلي قبل السماح بإطار جديد من `analysisExecutor`.

**أثر على fps:**

- STABLE 10fps (فاصل 100ms): post 2–6ms نادراً ما يكون العائق إن كان inference ≪ 100ms؛ طبقة `shouldAnalyzeFrame` هي الخانق الأساسي.
- LEGACY 30fps (فاصل ~33ms): إن inference ≈ 25ms + post 5ms ≈ 30ms → البوابة مشغولة معظم الوقت → `skippedBusyFrames` يرتفع وfps النتائج ينخفض تحت الهدف.
- تحرير البوابة فور استلام النتيجة (قبل التنعيم/التجميع) سيُمكّن تداخل pipeline: analysis يجهّز الإطار التالي بينما callback يعالج — مفيد عند ≥20fps؛ يحتاج ضمان عدم تداخل `LandmarkSmoother`/assemble إن وُجدت callbacks متوازية (MediaPipe عادةً تسلسلي لنفس الـ landmarker).

**Evidence:**

```192:201:kmp-app/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/MediaPipePoseDetector.kt
    private fun tryAcquireInferenceSlot(nowMs: Long): Boolean {
        if (inferenceInFlight.compareAndSet(false, true)) return true
        ...
        skippedBusyFrames.incrementAndGet()
        return false
    }
```

```275:312:kmp-app/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/MediaPipePoseDetector.kt
    private fun onPoseResult(...): Unit {
        ...
        try {
            ...
            listener?.onPoseDetected(DetectionResult(...))
        } finally {
            inferenceInFlight.set(false)
        }
    }
```

---

### A3. تسريب `frameCameraState`

**البنية:** `ConcurrentHashMap<Long, Boolean>` — مفتاح = `frameTime` المُمرَّر إلى `detectAsync`، قيمة = `isFrontCamera`.

| حدث | سلوك الخريطة |
|---|---|
| إرسال ناجح | `frameCameraState[frameTime] = isFrontCamera` (`:165`) |
| نتيجة | `remove(frameTs)` (`:279`) — إن غاب المفتاح → `false` افتراضياً |
| `resetTrackingState` / `shutdownLandmarkerOnly` | `clear()` |
| `ErrorListener` | يحرّر `inferenceInFlight` فقط — **لا يزيل** مدخل الإطار |

**سيناريو التسريب:** MediaPipe يُسقِط إطاراً داخلياً دون `ResultListener` ولا `ErrorListener`، أو خطأ يُبلَّغ دون تطابق timestamp → المدخل يبقى.

**تقدير النمو:** كل مدخل ≈ مرجع Long + Boolean + node ConcurrentHashMap (~48–80 بايت).  
حتى إسقاط 1% @ 10fps لمدة ساعة ≈ 360 مدخل ≈ عشرات KB — **بطيء وغير كارثي** في جلسة واحدة. خطر أكبر: جلسات طويلة جداً / وضع debug عالي fps مع أخطاء متكررة دون `shutdown`. لا يوجد TTL ولا سقف.

**Evidence:** `MediaPipePoseDetector.kt:75,165,279` و`ErrorListener:128-131` بلا `remove`.

---

### A4. مسار GPU→CPU في `warmUp` + `scheduleHeavyUpgrade`

**إعادة الدخول على `initLock`:**

```101:145:kmp-app/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/MediaPipePoseDetector.kt
    override fun warmUp(configuration: PoseDetectorConfiguration) {
        lastConfiguration = configuration
        synchronized(initLock) {
            ...
            } catch (e: Exception) {
                if (useGpu) {
                    warmUp(configuration.copy(useGpu = false))  // استدعاء ذاتي داخل القفل
                } else {
                    listener?.onError(...)
                }
            }
        }
    }
```

قفل Java/`synchronized` **قابل لإعادة الدخول (reentrant)** لنفس الخيط → الاستدعاء الذاتي آمن ولن يُحدث deadlock.

**التزامن مع `scheduleHeavyUpgrade`:**

- يُطلق من داخل `warmUp` على `backgroundScope` (Dispatchers.IO).
- يعيد استدعاء `warmUp(...)` بعد اكتمال التحميل → ينتظر `initLock` ثم `shutdownLandmarkerOnly()` ويستبدل الـ landmarker.
- `heavyUpgradeInFlight` يمنع تداخلاً مزدوجاً للجدولة، لكن **لا يمنع** `detectAsync` أثناء إغلاق الـ landmarker: `detectAsync` يقرأ `landmarker` بلا قفل؛ قد يستدعي `detectAsync` على كائن أُغلق للتو → يُمسك بـ catch ويحرّر البوابة.

**الخلاصة:** re-entrancy على القفل آمن؛ نافذة سباق حية بين upgrade وanalysis موجودة (هشاشة تشغيلية أكثر من deadlock).

---

### A5. دورة الحياة: `stop()` مقابل `dispose()`

**من يستدعي ماذا؟**

| استدعاء | المصدر | ماذا يحرر |
|---|---|---|
| `stop()` | `TrainingSessionCameraHost` `onDispose` (`:102-107`)؛ أيضاً بداية `reinitializePoseDetector`/`dispose` | unbind كاميرا، clear analyzer، reset بوابات — **لا** shutdown للكاشف ولا executor |
| `reinitializePoseDetector()` | Host عند تغيّر `modelType` (`:122-124`) | `stop` + `poseDetector.shutdown()` + `detectorWarmedUp=false` |
| `dispose()` | **لا مستدعٍ إنتاجي في المستودع** (تعريف فقط `:484-494`) | كل شيء بما فيه executor + shutdown الكاشف + إعادة أعلام المزود |

**DI:** كلاهما `single` في Koin (`MovitPoseCaptureModule.kt:26-44`) → عمر العملية.

**بعد مغادرة شاشة التدريب:**

- الكاميرا غير مربوطة (جيد).
- `PoseLandmarker` (GPU/CPU native) يبقى حياً.
- `analysisExecutor` خيط واحد يبقى حياً.
- `lastFrameBitmap` (~300KB–1.2MB) يبقى حتى إطار تالٍ أو `shutdown`.
- عند العودة: `detectorWarmedUp` ما زال `true` → لا `warmUp` جديد — **إحماء مقصود لسرعة العودة** (OQ-05).

**أثر الذاكرة:** ليس تسريباً يتزايد مع كل دخول/خروج للشاشة (الـ singletons ثابتة)، لكنه **احتفاظ دائم** بموارد Native/GPU بعد آخر تدريب حتى قتل العملية. إن لم يكن مقصوداً للمنتج → يجب استدعاء `dispose()` عند الخروج النهائي من تدفق التدريب / `onCleared` للعملية ذات الصلة.

---

### A6. بوابات البدء/التبديل (5 أعلام)

**الأعلام/البوابات:**

1. `CameraStartGate` — ينسّق preview↔start ويميّز InitialBind / SwitchFacing.
2. `LensSwitchFrameGate` — يكبت إطارات العدسة القديمة حتى أول إطار جديد.
3. `switchingCamera` (AtomicBoolean)
4. `bindingInProgress` (AtomicBoolean)
5. `providerInitializing` / `providerReady` (+ `pendingProviderReady`)

**`CameraStartGate`:** منطق واضح ومغطى باختبارات (`CameraStartGateTest`). `reset()` من `stop()` يصفّر الحالة.

**`providerInitializing` بعد النجاح:** يُضبط `true` عند بدء الجلب ولا يُعاد إلى `false` عند النجاح — فقط عند الفشل أو `dispose()`. مع `providerReady=true` المسار السعيد يعمل عبر الفرع الأول لـ `ensureCameraProvider`. أثر عملي محدود ما دام الـ singleton حياً؛ يمنع إعادة تهيئة المزود إن أُعيد `providerReady` يدوياً دون تصفير `providerInitializing`.

**`pendingProviderReady` يُستبدل (`:214-216`):** إن وصل طالب ثانٍ أثناء التهيئة، يُكتب فوق الـ callback السابق — يُفقد bind معلّق. إن تغيّر الاتجاه بين الطلبين قد يُنفَّذ bind خاطئ أو يُفقد switch.

**سباق `bindingInProgress` (الأخطر):**

```236:241:kmp-app/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/CameraXFrameSource.kt
        if (!bindingInProgress.compareAndSet(false, true)) {
            ...
            return  // إسقاط صامت — بلا طابور
        }
```

سيناريو واقعي:

1. `InitialBind` يدخل `bindUseCases(front, isSwitch=false)`.
2. المستخدم يقلب العدسة → `SwitchFacing` → `prepareForLensSwitch(back)` يضبط `switchingCamera=true` و`lensSwitchGate.awaiting=back`.
3. `bindUseCases(back, isSwitch=true)` يُرفض لأن الربط الأول ما زال جارياً.
4. الربط الأول يكتمل على **front**؛ لا يصفّر `switchingCamera` (فقط `!isSwitch` يستدعي `onCameraBoundListener`).
5. كل الإطارات الأمامية تُكبَت (`Suppress`)؛ لا يصل إطار `back` أبداً → **توقف تسليم الوضعية** حتى `stop()`/إعادة دخول.

---

### A7. اختيار fps والدقة والزوم

**`chooseFpsRange` (`:396-405`):**  
يفضّل نطاقاً يحتوي `targetFps` بأضيق عرض ثم أقرب `upper`؛ وإلا أعلى `upper ≤ target`؛ وإلا أقصى `upper`. منطقي ومكتمل للحالات الشائعة. لا يفرض نطاقاً ثابتاً إن الجهاز لا يدعم الهدف — يسقط بهدوء إلى `none` في التشخيصات إن فشلت الكتابة.

**`ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER` (`:294-301`):** مناسب لدقة التحليل المستهدفة من الـ profile؛ الدقة الفعلية تُقرأ بعد الربط للتشخيصات (`:328-332`). لا يضمن 4:3 للتحليل (المعاينة فقط تستخدم RATIO_4_3) — قد ينحرف aspect قليلاً حسب الجهاز.

**`applyWidestZoom` + `postDelayed` 300ms و1000ms (`:441-452`):**  
يعيد المحاولة لأن `zoomState` قد يكون null عند الربط. الحماية: `if (camera === boundCamera)`. بعد `stop()`/`dispose()` يصبح `camera = null` → الـ callbacks لا تعمل. لا تسريب قوي؛ إن بقيت الـ PreviewView حية بعد unbind مع نفس مرجع Camera نظرياً نادر. متوافق مع PF-23 (مخفَّف).

---

### A8. التقاط snapshot أثناء التدريب

**سلسلة الاستدعاء:**

```
engine callbacks (phase/joint/hold)
  → TrainingFrameCaptureCoordinator.requestCapture / sampleReplayFrame
    → scope.launch → TrainingFrameSnapshotPort.persistSnapshot|persistReplaySnapshot
      → AndroidTrainingFrameSnapshotPort (Dispatchers.IO)
        → MediaPipePoseDetector.takeSnapshotJpeg
```

**خيط العمل:** ضغط JPEG على **`Dispatchers.IO`** — ليس على analysisExecutor ولا على MediaPipe callback مباشرة. لكن يصل إلى `lastFrameBitmap` المشترك.

**`takeSnapshotJpeg`:**

```233:241:kmp-app/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/MediaPipePoseDetector.kt
    fun takeSnapshotJpeg(maxDimension: Int, quality: Int): ByteArray? {
        val source = synchronized(bitmapLock) { lastFrameBitmap } ?: return null
        val working = source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)  // خارج القفل!
        return try {
            compressBitmapToJpeg(working, maxDimension, quality)
        } finally {
            working.recycle()
        }
    }
```

**التكرار لكل جلسة (حدود المدير + المنسّق):**

| نوع | متى | حد تقريبي |
|---|---|---|
| PEAK_FRAME | كل انتقال لطور `BOTTOM` لكل rep | 1/rep |
| DANGER_FRAME | حالة DANGER | ≤6 + cooldown 1s |
| ERROR_FRAME | WARNING/أخطاء | لكل مفتاح خطأ/rep + cooldown 2s |
| HOLD_SAMPLE | كل 5s أثناء hold | ≤3 |
| Replay | كل **180ms** أثناء التدريب النشط | ≤16 إطار/rep |

`persistSnapshot` يستدعي `takeSnapshotJpeg` **مرتين** (full 720px + thumb 200px) — نسختان كاملتان من المصدر + ضغطان؛ وقد تلتقطان إطارين مختلفين إن وصل إطار جديد بينهما.

**أثر السلاسة:**

- عند STABLE 320×240: نسخة ~300KB + JPEG على IO — عادةً مقبول، لكن سباق `recycle` (أدناه) خطر صحة.
- Replay @ ~5.5Hz يضيف نسخاً متكررة؛ قد ينافس GC مع churn الـ toBitmap على المسار الساخن.
- لا يحجز `inferenceInFlight`، لذا لا يوقف الاستدلال مباشرة — التنافس على الذاكرة/CPU وسلامة الـ bitmap.

---

## 3. Findings

### [A-01] Per-frame Bitmap allocation without reuse (toBitmap + rotate)
- **Severity**: P2
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: PF-01
- **Files**: `kmp-app/core/pose-capture/src/androidMain/kotlin/com/movit/core/posecapture/android/MediaPipePoseDetector.kt:168-184`, `.../MediaPipePoseDetector.kt:213-226`, `kmp-app/core/training-engine/src/commonMain/kotlin/com/movit/core/training/boundary/TrainingThroughputProfile.kt:21-25`
- **Evidence**: كل `detectAsync` يستدعي `imageProxy.toBitmap()`؛ عند `rotationDegrees != 0` يُنشأ `Bitmap.createBitmap` جديد ويُعاد تدوير المصدر. لا pool. الإنتاج STABLE=320×240@10fps (~3–6MB/s churn)؛ LEGACY 640×480@30fps مع دوران ≈ عشرات MB/s.
- **Impact**: ضغط GC على المسار الساخن؛ يحدّ من رفع `targetFps`/الدقة. عند 30fps/640×480 يقدَّر churn صافٍ ~37MB/s (+ذروة مضاعفة أثناء الدوران).
- **Fix-sketch**: Bitmap pool بنفس أبعاد التحليل؛ أو مسار Image→MPImage بلا ARGB وسيط؛ إعادة استخدام buffer الدوران.
- **Effort**: M
- **Verified-by**: adversarial-grok-4.5-xhigh

### [A-02] inferenceInFlight held through post-inference assemble/emit
- **Severity**: P2
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: PF-04
- **Files**: `.../MediaPipePoseDetector.kt:192-201`, `.../MediaPipePoseDetector.kt:275-312`, `.../CameraXFrameSource.kt:176-190`
- **Evidence**: `finally { inferenceInFlight.set(false) }` يقع بعد `listener.onPoseDetected` الذي يشغّل One-Euro + `PoseFrameAssembler.assemble` + `emitPoseFrame`. الإطارات الجديدة تُحسب في `skippedBusyFrames` طوال هذه المدة.
- **Impact**: يخفض معدل إرسال الاستدلال الفعلي بمقدار زمن post (~2–6ms تقديري). أثر محدود @10fps؛ ملموس أكثر @20–30fps عندما يقترب مجموع inference+post من فاصل الهدف.
- **Fix-sketch**: حرّر البوابة فور دخول `onPoseResult` (أو فور نسخ بيانات النتيجة) قبل التنعيم/التجميع؛ أبقِ معالجة ما بعد الاستدلال خارج نافذة الانشغال.
- **Effort**: S
- **Verified-by**: pending

### [A-03] frameCameraState entries can leak on dropped/error frames
- **Severity**: P3
- **Type**: Memory
- **Status**: CONFIRMED
- **Related-PF**: PF-03
- **Files**: `.../MediaPipePoseDetector.kt:75`, `:165`, `:279`, `:128-131`
- **Evidence**: الإدراج عند الإرسال؛ الإزالة فقط في `onPoseResult`. `ErrorListener` يصفّر `inferenceInFlight` دون `frameCameraState.remove`. لا TTL.
- **Impact**: نمو بطيء (KB على مدى ساعة بإسقاط نادر). خطر عملي منخفض في جلسات تدريب عادية؛ يفسد أيضاً `isFrontCamera` الافتراضي إلى `false` إن وُجدت نتيجة بلا مدخل.
- **Fix-sketch**: إزالة المفتاح في ErrorListener؛ سقف/TTL على الخريطة؛ أو حمل `isFrontCamera` عبر آلية لا تعتمد على map زمنية.
- **Effort**: S
- **Verified-by**: pending

### [A-04] takeSnapshotJpeg full-frame copy + JPEG on IO; high replay cadence
- **Severity**: P2
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: PF-02
- **Files**: `.../MediaPipePoseDetector.kt:233-262`, `.../AndroidTrainingFrameSnapshotPort.kt:17-42`, `.../TrainingFrameCaptureCoordinator.kt:50-58,129-139,206-212`, `.../MovitRepReplaySampler.kt:70-71`
- **Evidence**: كل لقطة تنسخ bitmap كاملاً ثم قد تعمل `createScaledBitmap` + `compress`. Peak عند BOTTOM لكل rep؛ replay كل 180ms حتى 16/rep. `persistSnapshot` يستدعي اللقطة مرتين (full+thumb).
- **Impact**: عمل IO/CPU متقطع أثناء التدريب؛ منافسة GC مع مسار التحليل. الكمية: عشرات اللقطات/تمرين + حتى ~5.5 نسخ/ث للـ replay أثناء النشاط.
- **Fix-sketch**: لقطة واحدة تُشتق منها thumb؛ خفض معدل replay أو نسخه من JPEG full؛ نسخ تحت قفل أو double-buffer مستقر.
- **Effort**: M
- **Verified-by**: pending

### [A-05] TOCTOU recycle race between takeSnapshotJpeg copy and detectAsync
- **Severity**: P1
- **Type**: Concurrency
- **Status**: CONFIRMED
- **Related-PF**: PF-02
- **Files**: `.../MediaPipePoseDetector.kt:179-182`, `:233-240`
- **Evidence**: `takeSnapshotJpeg` يأخذ مرجع `lastFrameBitmap` تحت القفل ثم يستدعي `source.copy(...)` **خارج** القفل. بالتوازي `detectAsync` قد ينفّذ `lastFrameBitmap?.recycle()` ثم يستبدل المرجع. نسخ بعد `recycle` → استثناء/إطار فاسد.
- **Impact**: أعطال متقطعة أو JPEG تالف عند تزامن peak/replay مع معدل إطارات عالٍ — سيناريو واقعي أثناء TRAINING مع `startReplaySampler`.
- **Fix-sketch**: نفّذ `copy` داخل `bitmapLock`؛ أو refcount/منع recycle أثناء snapshot؛ أو انسخ إلى buffer مملوك للقطة قبل تحرير القفل.
- **Effort**: S
- **Verified-by**: adversarial-grok-4.5-xhigh

### [A-06] dispose() never called; stop() leaves landmarker + executor warm
- **Severity**: P2
- **Type**: Memory
- **Status**: CONFIRMED
- **Related-PF**: PF-24
- **Files**: `.../TrainingSessionCameraHost.android.kt:102-107`, `.../CameraXFrameSource.kt:462-494`, `.../di/MovitPoseCaptureModule.kt:26-44`
- **Evidence**: Host `onDispose` يستدعي `stop()` فقط. بحث المستودع: لا استدعاء لـ `dispose()`. الكائنات Koin `single`. `stop()` لا يغلق `PoseLandmarker` ولا `analysisExecutor`.
- **Impact**: احتفاظ دائم بموارد Native/GPU + خيط تحليل + آخر bitmap بعد مغادرة التدريب (ثابت لا يتراكم لكل زيارة). يسرّع إعادة الدخول (محتمل أنه مقصود — OQ-05) لكنه يمنع تحرير الذاكرة حتى قتل العملية.
- **Fix-sketch**: توثيق سياسة الإحماء؛ أو استدعاء `dispose()` عند الخروج النهائي من تدفق التدريب / انخفاض ذاكرة؛ الإبقاء على `stop()` للخلفية القصيرة فقط.
- **Effort**: S
- **Verified-by**: pending

### [A-07] bindingInProgress silently drops lens-switch bind — frames can stall
- **Severity**: P1
- **Type**: Concurrency
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `.../CameraXFrameSource.kt:129-143`, `:236-241`, `:268-270`, `:343-352`, `.../LensSwitchFrameGate.kt:21-35`
- **Evidence**: `switchCamera` يستدعي `prepareForLensSwitch` (يضبط `switchingCamera` + بوابة العدسة) ثم `bindUseCases`. إن `bindingInProgress` أصلاً `true` يُعاد فوراً بلا طابور. الربط السابق قد يكتمل على العدسة القديمة بينما البوابة تنتظر الجديدة → `Suppress` دائم.
- **Impact**: توقف هيكل عظمي/عدّ أثناء/بعد قلب سريع للكاميرا أو تغيّر `useFrontCamera` أثناء الربط الأول — يتطلب مغادرة الشاشة للتعافي عادةً.
- **Fix-sketch**: طابور bind معلّق (pending facing)؛ لا تستدعِ `prepareForLensSwitch` قبل ضمان جدولة الربط؛ أو أعد المحاولة في `finally` بعد انتهاء الربط الحالي.
- **Effort**: M
- **Verified-by**: adversarial-grok-4.5-xhigh

### [A-08] pendingProviderReady overwritten — lost bind callback
- **Severity**: P2
- **Type**: Concurrency
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `.../CameraXFrameSource.kt:209-225`
- **Evidence**: أثناء `providerInitializing`، أي طالب إضافي يفعل `pendingProviderReady = onReady` فيستبدل السابق. عند الجاهزية يُنفَّذ `onReady` الأصلي ثم pending الأخير فقط.
- **Impact**: فقدان طلب bind/switch وسيط إن تتابعت طلبات أثناء تهيئة المزود (نافذة قصيرة عند أول فتح).
- **Fix-sketch**: قائمة callbacks أو دمج إلى آخر نية facing واحدة مع ضمان تنفيذها؛ صفّر `providerInitializing` عند النجاح أيضاً للوضوح.
- **Effort**: S
- **Verified-by**: pending

### [A-09] providerInitializing remains true after successful provider init
- **Severity**: P3
- **Type**: Architecture
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `.../CameraXFrameSource.kt:214-231`, `:484-493`
- **Evidence**: النجاح يضبط `providerReady=true` ولا يعيد `providerInitializing=false`. الفشل و`dispose()` فقط يصفّرانه. مع `providerReady` المسار يعمل؛ الحالة مزدوجة مربكة.
- **Impact**: ضعف وضوح الحالة؛ إن أُعيد ضبط `providerReady` دون `dispose` قد تُرفض إعادة التهيئة للأبد.
- **Fix-sketch**: `providerInitializing.set(false)` في فرع النجاح بعد `providerReady.set(true)`.
- **Effort**: S
- **Verified-by**: pending

### [A-10] postDelayed widest-zoom retries guarded by camera identity
- **Severity**: P3
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: PF-23
- **Files**: `.../CameraXFrameSource.kt:441-452`, `:462-469`
- **Evidence**: مؤجّلان 300ms و1000ms يستدعيان الزوم فقط إن `camera === boundCamera`. `stop()` يصفّر `camera=null` فيُبطِل المؤجلات. لا إلغاء صريح لـ callbacks الـ View.
- **Impact**: خطر تسريب/استدعاء بعد dispose منخفض عملياً بفضل فحص الهوية. متبقٍ: اعتماد على هوية الكائن لا على جيل ربط.
- **Fix-sketch**: رمز جيل bind (`bindGeneration++`) بدل مقارنة المرجع؛ أو `removeCallbacks` عند `stop`.
- **Effort**: S
- **Verified-by**: pending

### [A-11] Dual imageProxy.close paths are defensive, not harmful double-close bugs
- **Severity**: P3
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: PF-25
- **Files**: `.../MediaPipePoseDetector.kt:153-189`
- **Evidence**: إغلاق مبكر عند null landmarker / busy؛ إغلاق بعد `toBitmap` في المسار السعيد (`:169`)؛ في `catch` محاولة `close` داخل try فارغ (`:188`) لحماية حالة فشل **قبل** الإغلاق. بعد `:169` أي استثناء يؤدي لمحاولة إغلاق ثانٍ تُبتلع بصمت.
- **Impact**: لا خلل وظيفي معروف؛ ضوضاء أسلوبية فقط. CameraX يتعامل مع double-close عادةً برمي يُبتلع هنا.
- **Fix-sketch**: علم محلي `closed` أو نقل `close` إلى `finally` واحد بعد نقل الملكية من الـ proxy.
- **Effort**: S
- **Verified-by**: pending

### [A-12] Five stacked backpressure layers — roles overlap; device proof needed
- **Severity**: P2
- **Type**: Architecture
- **Status**: NEEDS-DATA
- **Related-PF**: PF-22
- **Files**: `.../CameraXFrameSource.kt:305-316`, `:407-416`, `.../MediaPipePoseDetector.kt:192-201` (+ VM CONFLATED / engine gate خارج المسار لكن جزء من §4)
- **Evidence**: (1) `STRATEGY_KEEP_ONLY_LATEST` (2) `shouldAnalyzeFrame` حسب `targetFps` (3) `inferenceInFlight` (4)/(5) في VM/engine. الطبقتان 2 و3 نشطتان معاً: @10fps الطبقة 2 هي الخانق الأساسي و3 احتياطية للبطء؛ @30fps الطبقة 3 تصبح حاسمة.
- **Impact**: تعقيد تشخيص fps؛ احتمال إسقاط مفرط عند تفعيل كل الطبقات دون قياس. لا يمكن الجزم بأن الطبقة 2 «زائدة» دون عدّادات جهاز (`skippedAnalysis` مقابل `skippedBusyFrames`).
- **Fix-sketch**: قياس 60s على جهاز لكل profile؛ إن بقيت busy≈0 عند STABLE أبْقِ 2 كسياسة هدف واعتبر 3 شبكة أمان؛ وحّد لوحة تشخيص الطبقات.
- **Effort**: M
- **Verified-by**: pending

### [A-13] persistSnapshot takes two independent JPEGs (full then thumb)
- **Severity**: P2
- **Type**: Correctness
- **Status**: CONFIRMED
- **Related-PF**: PF-02
- **Files**: `.../AndroidTrainingFrameSnapshotPort.kt:21-27`
- **Evidence**: استدعاءان متتابعان لـ `takeSnapshotJpeg`؛ كل واحد ينسخ `lastFrameBitmap` الحالي. إطار جديد بين الاستدعاءين → thumb لا يطابق full.
- **Impact**: أدلة تقرير غير متسقة نادراً؛ مضاعفة كلفة النسخ/الضغط لكل peak/error/hold.
- **Fix-sketch**: `takeSnapshotJpeg` مرة واحدة ثم اشتق thumb من نفس الـ bytes/bitmap.
- **Effort**: S
- **Verified-by**: pending

### [A-14] Heavy-model warmUp can close live PoseLandmarker under traffic
- **Severity**: P2
- **Type**: Concurrency
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `.../MediaPipePoseDetector.kt:101-146`, `:337-353`, `:153-184`
- **Evidence**: `scheduleHeavyUpgrade` يستدعي `warmUp` من IO → `shutdownLandmarkerOnly()` بينما `detectAsync` قد يمسك مرجعاً قديماً بلا قفل.
- **Impact**: أخطاء استدلال عابرة / إطارات مفقودة عند اكتمال تحميل Heavy أثناء جلسة حية (مسار HEAVY fallback→upgrade).
- **Fix-sketch**: أوقف التحليل أو اضبط بوابة أثناء الاستبدال؛ بدّل المرجع ذرياً بعد إنشاء landmarker الجديد؛ تجاهل أخطاء الإغلاق المتوقع.
- **Effort**: M
- **Verified-by**: pending

### [A-15] Post-result path allocates many Landmark lists while gate held
- **Severity**: P2
- **Type**: Performance
- **Status**: CONFIRMED
- **Related-PF**: PF-04
- **Files**: `.../MediaPipePoseDetector.kt:285-309`, `.../LandmarkSmoother.kt:20-44`, `.../MediaPipeLandmarkMapper.kt:8-12`, `.../CameraXFrameSource.kt:177-184`
- **Evidence**: لكل نتيجة وضعية: rawNormalized + rawWorld + smoothed + smoothedWorld (كل منها ~33 `Landmark`) + `PoseFrameAssembler.assemble`. يحدث قبل تحرير `inferenceInFlight`.
- **Impact**: ~130+ كائن Landmark/إطار ناجح (+ قوائم/زوايا). @10fps ≈ 1300 كائن/ث؛ @30fps ≈ 4000/ث — يضاعف ضغط GC مع Bitmaps (A-01).
- **Fix-sketch**: حرّر بوابة الاستدلال مبكراً؛ فكّر في buffers معالم قابلة لإعادة الاستخدام إن لزم القياس.
- **Effort**: M
- **Verified-by**: pending

### [A-16] AndroidPoseRefiner is permanently unavailable (dead refine path)
- **Severity**: P3
- **Type**: Dead-code
- **Status**: CONFIRMED
- **Related-PF**: —
- **Files**: `.../AndroidPoseRefiner.kt:9-12`, `.../MediaPipePoseDetector.kt:290-294`
- **Evidence**: `isAvailable = false` و`refineLandmarks` يعيد المدخل كما هو. الفرع في `onPoseResult` لا يفعّل أبداً في الإنتاج الحالي.
- **Impact**: لا أثر أداء؛ مسار/واجهة ميتة حتى تُربط أصول LiteRT.
- **Fix-sketch**: أبقِ كـ placeholder موثّق أو أزل الفرع من المسار الساخن حتى التفعيل.
- **Effort**: S
- **Verified-by**: pending

---

## 4. PF verdict table

| PF | الحكم | Finding | الدليل المختصر |
|---|---|---|---|
| **PF-01** | **CONFIRMED** | A-01 | `toBitmap` + `createBitmap` لكل إطار بلا reuse |
| **PF-02** | **CONFIRMED** | A-04, A-05, A-13 | نسخ+JPEG على IO؛ تكرار peak/replay؛ سباق recycle؛ لقطتان مستقلتان |
| **PF-03** | **CONFIRMED** | A-03 | map بلا إزالة على الخطأ/الإسقاط؛ نمو بطيء |
| **PF-04** | **CONFIRMED** | A-02, A-15 | تحرير `inferenceInFlight` بعد assemble/emit |
| **PF-22** | **NEEDS-DATA** | A-12 | الطبقات موجودة وفعّالة؛ جدوى الطبقة 2 مقابل 3 تحتاج قياس جهاز |
| **PF-23** | **CONFIRMED** | A-10 | مؤجلات الزوم موجودة؛ الحماية بهوية الكاميرا كافية عملياً (خطر متبقٍ P3) |
| **PF-24** | **CONFIRMED** | A-06 | Host→`stop()` فقط؛ `dispose()` بلا مستدعٍ؛ singletons دافئة |
| **PF-25** | **CONFIRMED** | A-11 | مساران لـ `close`؛ الإغلاق المزدوج محتمل ومبتلع — ليس عيباً ضاراً |

ملاحظات PF-23/25: الحكم **CONFIRMED** يعني أن الادعاء الوصفي في السجل صحيح؛ الضرر الفعلي منخفض (P3).

---

## 5. Coverage — files read

| ملف | قُرئ؟ | ملاحظات |
|---|---|---|
| `android/CameraXFrameSource.kt` | نعم | كامل (500) |
| `android/MediaPipePoseDetector.kt` | نعم | كامل (355) |
| `android/LandmarkSmoother.kt` | نعم | كامل |
| `android/MediaPipeLandmarkMapper.kt` | نعم | كامل |
| `android/PoseModelResolver.kt` | نعم | كامل |
| `android/PoseLandmarkerHeavyModelStore.kt` | نعم | كامل |
| `android/MediaPipeSyncPoseDetector.kt` | نعم | مسار debug sync — ليس المسار الساخن للتدريب |
| `android/AndroidPoseRefiner.kt` | نعم | no-op |
| `commonMain/CameraStartGate.kt` | نعم | + `CameraStartGateTest` |
| `commonMain/LensSwitchFrameGate.kt` | نعم | + `LensSwitchFrameGateTest` |
| `feature/training/.../TrainingSessionCameraHost.android.kt` | نعم | كامل |
| `feature/training/.../TrainingCameraSettings.kt` (+ android) | نعم | فتح إعدادات النظام فقط — ليس إعدادات الكاميرا التحليلية |
| `feature/training/.../TrainingThroughputFlags.kt` (+ android) | نعم | يقرأ flag → profile |
| `core/training-engine/.../TrainingThroughputProfile.kt` | نعم | STABLE/MEDIUM/HIGH/LEGACY |
| `core/training-engine/.../CameraSourceConfiguration.kt` | نعم | افتراضات = STABLE |
| `pose-capture/di/MovitPoseCaptureModule.kt` | نعم | Koin singles |
| `AndroidTrainingFrameSnapshotPort.kt` + binding | نعم | لـ A8/PF-02 |
| `TrainingFrameCaptureCoordinator.kt` | نعم | تكرار اللقطات |
| `MovitPeakFrameCaptureManager.kt` | نعم | حدود اللقطات |
| `PoseFrameAssembler.kt` (مقتطف) | نعم | تكلفة assemble في سلسلة A2 |
| `commonMain/PoseLandmarkSmoother.kt` (مقتطف) | نعم | ثوابت مشتركة مع Android smoother |

**لم يُعمَّق (خارج أسئلة A أو ثانوي):** `AndroidDeviceTiltPort`, `AndroidPoseModelTypePort`, `PoseModelTypePreference`, `StubCameraFrameSource`, مسار assessment host.

---

## 6. Quantitative cheat-sheet (30fps framing)

افتراض تحليل فعلي ≈ الهدف، دوران ≠ 0، وضعية مكتشفة:

| البند | STABLE 10fps 320×240 | LEGACY-ish 30fps 640×480 |
|---|---|---|
| Bitmap allocs / accepted frame | 2 | 2 |
| Bitmap bytes alloc / frame (peak before recycle) | ~0.6 MB | ~2.5 MB |
| Bitmap churn / sec (net kept path) | ~3 MB/s | ~37 MB/s |
| Landmark objects / pose result | ~130+ | ~130+ |
| Busy-gate hold | inference + ~2–6ms post | نفس + ضغط أعلى على فاصل 33ms |
| Snapshot copy (per takeSnapshotJpeg) | ~0.3 MB + JPEG | ~1.2 MB + JPEG |
| Replay snapshot rate | ≤1/180ms أثناء النشاط | نفس الضغط أعلى على الذاكرة |

القيم النظرية موسومة للتحقق على جهاز عبر Track K (`perf-baseline.md`).
