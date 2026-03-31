# Posture MLP — دليل التدريب (عربي)

> النسخة الإنجليزية: [TRAINING_GUIDE.md](./TRAINING_GUIDE.md)

---

## 1. نظرة عامة

مصنّف **MLP** (Multi-Layer Perceptron) صغير جداً يأخذ صورة واحدة كمدخل ويخرج واحدة من ثلاث فئات وضعية:

| Class | Label | Code |
|-------|-------|------|
| 0 | Standing | `STANDING` |
| 1 | Sitting | `SITTING` |
| 2 | Lying | `LYING` |

النموذج **لا** يعالج الصورة مباشرة. المسار الفعلي:

```
Image → MediaPipe BlazePose → 33 Landmarks → 16 Engineered Features → MLP → Class (0/1/2)
```

هذا التصميم يبقي حجم النموذج صغيراً جداً (~15 KB) وسريعاً لـ **inference** في الوقت الفعلي على أجهزة Android.

---

## 2. كيف يتم التدريب — خطوة بخطوة

### 2.1 جمع البيانات (`Docs/train/`)

الصور مرتبة في ثلاث مجلدات (مثال على أعداد تقريبية وقت كتابة الدليل):

```
Docs/train/
  ├── Standing/   (~124 صورة)
  ├── Sitting/    (~150 صورة)
  └── Lying/      (~184 صورة)
```

**المجموع:** حوالي 458 صورة. صيغ مدعومة: `.jpg`, `.jpeg`, `.png`, `.webp`, `.avif`, `.bmp`.

### 2.1.1 التسميات (Labels) — المجلد = الـ class

**لا** نستخدم ملف تسميات منفصل (مثل CSV أو JSON) ولا annotation لكل صورة باسمها.

التسمية **ضمنية** من **مسار المجلد**:

| المجلد | Class index | المعنى |
|--------|-------------|--------|
| `Docs/train/Standing/` | `0` | كل الصور داخل المجلد تُعتبر **Standing** |
| `Docs/train/Sitting/` | `1` | كل الصور داخل المجلد تُعتبر **Sitting** |
| `Docs/train/Lying/` | `2` | كل الصور داخل المجلد تُعتبر **Lying** |

السكربت يربط المجلد بالرقم في `train_posture_mlp.py` (`class_dirs`: Standing→0، Sitting→1، Lying→2). لكل صورة يُستخرج متجه الـ **16 feature** من الـ landmarks، ثم يُقترن هذا المتجه بالـ **label** الخاص بمجلدها فقط.

**النتيجة:** وضع صورة في مجلد خاطئ = **تسمية خاطئة** للتدريب. النموذج **لا يرى البكسلات**؛ يتعلم ربط الـ features بالـ label الذي فرضته أنت عبر اختيار المجلد.

### 2.2 استخراج الـ Features (`feature_engineering.py`)

لكل صورة، سكربت التدريب:

1. يحمّل الصورة كـ **RGB**.
2. يشغّل **MediaPipe Pose Landmarker** (`pose_landmarker_full.task`) لاكتشاف
   33 نقطة جسم (x, y, z, visibility) بإحداثيات **normalized**.
3. يحسب **16 geometric features** من هذه الـ **landmarks**.

إذا لم يُكتشف **pose** أو كان **torso** قصيراً جداً (< 0.02 طول normalized)، تُتخطى الصورة.

#### الـ 16 Features

| # | Feature Name | كيف تُحسب | ماذا تمثل |
|---|--------------|-----------|-----------|
| 0 | `spine_angle_norm` | زاوية متجه منتصف الكتف → منتصف الورك نسبة للأفقي، مقسومة على 180 | **إشارة الوضعية الأساسية.** Standing ≈ 0.5 (90°)، Lying ≈ 0.0 أو 1.0 |
| 1 | `torso_len` | مسافة Euclidean من مركز الكتف إلى مركز الورك | مقياس الجسم / بعد الكاميرا |
| 2 | `cos_torso_thigh` | جيب تمام زاوية بين متجه الجذع ومتجه الفخذ (hip→knee) | **كشف الجلوس.** Standing ≈ 1.0 (محاذاة)، Sitting < 0.5 (انحناء) |
| 3 | `knee_ang_L` | زاوية hip–knee–ankle يسار / 180 | انحناء الساق (يسار) |
| 4 | `knee_ang_R` | زاوية hip–knee–ankle يمين / 180 | انحناء الساق (يمين) |
| 5 | `shoulder_w` | عرض الكتفين أفقياً / طول الجذع | مؤشر اتجاه الرؤية (ضيق = side view) |
| 6 | `hip_w` | عرض الوركين أفقياً / طول الجذع | مؤشر اتجاه الرؤية |
| 7 | `knee_drop` | فرق رأسي مركز الركبة–مركز الورك / طول الجذع | Standing موجب، Lying ≈ 0 |
| 8 | `ankle_drop` | فرق رأسي مركز الكاحل–مركز الركبة / طول الجذع | امتداد الساق تحت الركبة |
| 9 | `nose_off` | إزاحة الأنف الرأسية عن منتصف الجذع / طول الجذع | موضع الرأس نسبة للجسم |
| 10 | `sh_v_sep` | فرق رأسي بين الكتفين / طول الجذع | مؤشر الاستلقاء على الجانب (كتفان فوق بعض) |
| 11 | `hip_v_sep` | فرق رأسي بين الوركين / طول الجذع | مؤشر الاستلقاء على الجانب |
| 12 | `vis_knee` | أقل visibility للركبتين | موثوقية الـ landmark |
| 13 | `vis_hip` | أقل visibility للوركين | موثوقية الـ landmark |
| 14 | `vis_sh` | أقل visibility للكتفين | موثوقية الـ landmark |
| 15 | `z_torso` | فرق عمق Z مطلق بين مركز الكتف ومركز الورك | إشارة العمق/الدوران |

كل الـ features مصممة لتكون **scale-invariant** (تقسيم على طول الجذع) و**view-robust** (دمج علاقات أجزاء متعددة من الجسم).

### 2.3 تقسيم البيانات (Data Split)

البيانات تُقسَّم بـ **Stratified Sampling** (حوالي 15% لـ **validation**):

- كل **class** يُقسَّم لوحده لضمان تمثيل نسبي.
- التقسيم حتمي (`seed=42`) لإعادة الإنتاج (**reproducibility**).
- مع ~458 عينة: تقريباً ~389 **training** و~69 **validation**.

### 2.4 Normalization

الـ features تُطبَّع بـ **Z-score normalization** تُحسب من **training set فقط**:

```
feature_normalized = (feature - mean) / std
```

المصفوفات `mean` و `std` تُحفظ في `posture_mlp_norm.json` ويجب استخدامها عند **inference** (على Android) بنفس المعادلة تماماً.

### 2.5 Model Architecture

```
Input (16 float32) → Dense(64, ReLU, L2) → Dropout(0.30)
                    → Dense(32, ReLU, L2) → Dropout(0.15)
                    → Dense(3, Softmax) → Output (3 probabilities)
```

| Component | الغرض |
|-----------|--------|
| `Dense(64)` | أول طبقة مخفية — تتعلم تركيبات أساسية من الـ features |
| `Dense(32)` | ثانية — تضبط حدود القرار |
| `L2(1e-3)` | **Weight regularization** — يعاقب الأوزان الكبيرة لتقليل **overfitting** |
| `Dropout(0.30)` | يصفّر عشوائياً 30% من الـ neurons أثناء التدريب |
| `Dropout(0.15)` | **Dropout** أخف قبل الطبقة الأخيرة |
| `Softmax` | يحول الدرجات الخام إلى احتمالات مجموعها 1.0 |

**Optimizer:** Adam (**learning rate** 1e-3)  
**Loss:** Sparse Categorical Cross-Entropy

### 2.6 Training Loop

- **Max epochs:** 200  
- **Batch size:** 32  
- **Early Stopping:** يراقب `val_loss` مع **patience**=20. إذا لم يتحسن **validation loss** لمدة 20 **epoch** متتالية، يتوقف التدريع ويُعاد تحميل أفضل **weights**.

### 2.7 Export

نموذج **Keras** المدرب يُحوَّل إلى **TensorFlow Lite** (**float32**، بدون **quantization**) لنشر Android:

```
posture_mlp.tflite      — النموذج (~15 KB)
posture_mlp_norm.json   — mean/std للـ normalization
```

> **مهم:** لا نستخدم `converter.optimizations` (**dynamic range quantization**) لأنها تنتج ops مثل `FULLY_CONNECTED v12` تحتاج **TFLite runtime** ≥ 2.17، بينما تبعية Android الحالية `2.16.1`. التصدير **plain float32** يستخدم إصدارات ops أقدم ومتوافقة على نطاق أوسع.

---

## 3. معنى الأرقام التي تظهر أثناء التدريب

### مثال على مخرجات التدريب

```
Epoch 27/200
accuracy: 0.9383 - loss: 0.2510 - val_accuracy: 0.8971 - val_loss: 0.3204
...
Epoch 47: early stopping
Restoring model weights from the end of the best epoch: 27.
Val accuracy: 0.8971
```

### معجم المصطلحات (Metrics)

| Metric | المعنى | الاتجاه الجيد |
|--------|--------|----------------|
| **accuracy** | نسبة عينات **training** المصنّفة صحيحاً في هذا **epoch** | ↑ أعلى |
| **loss** | **Cross-entropy loss** على بيانات التدريب (أقل = ثقة وصحة أعلى) | ↓ أقل |
| **val_accuracy** | نسبة عينات **validation** المصنّفة صحيحاً (بيانات غير مُرَاَنة) | ↑ أعلى |
| **val_loss** | **Cross-entropy loss** على **validation** | ↓ أقل |
| **early stopping** | توقف التدريع لأن `val_loss` توقف عن التحسن | يقلل **overfitting** |
| **best epoch** | الـ **epoch** الذي فيه أقل `val_loss` — تُستعاد **weights** إليه | — |

### كيف تقرأ هذه الأرقام

- **accuracy = 0.9383**: حوالي 93.8% من صور **training** صُنّفت بشكل صحيح.
- **val_accuracy = 0.8971**: حوالي 89.7% من صور **validation** صُنّفت بشكل صحيح.
- **الفجوة (93.8% مقابل 89.7%)**: فجوة صغيرة (~4%) صحية. فجوة كبيرة (مثلاً 99% مقابل 70%) تعني **overfitting** — النموذج حفظ **training** ولا يعمم جيداً.
- **loss = 0.2510**: متوسط «مفاجأة» النموذج عند رؤية **training**. الأقل أفضل؛ المثالي نظرياً 0.0.
- **val_loss = 0.3204**: نفس المقياس على **validation**. هذا ما يراقبه **Early Stopping**.
- **Epoch 47 / patience=20**: التدريع وصل لـ 47 **epoch** لكن الأفضل كان **epoch** 27. من 28 إلى 47 لم يتحسن `val_loss` فتوقف التدريع.

### ما هو أداء «جيد»؟

| Metric | قيمتنا (مثال) | التفسير |
|--------|----------------|---------|
| **Val accuracy** | **~89.7%** | جيد لحجم ~458 صورة. ~10% أخطاء متوقعة من أوضاع غامضة، زوايا سيئة، أو **mislabeled** |
| **Model size** | **~15 KB** | صغير جداً؛ لا يؤثر تقريباً على حجم التطبيق أو زمن التحميل |
| **Inference** | **< 1ms** (للـ MLP وحده) | الـ MLP تافه؛ **bottleneck** هو **MediaPipe landmark detection** |

---

## 4. كيف نحسّن النموذج لاحقاً

### 4.1 المزيد من البيانات (أعلى أثر)

أقوى تحسين عملياً. مثال على الوضع الحالي:

| Class | Images (تقريبي) | ملاحظة |
|-------|------------------|--------|
| Standing | ~124 | يفيد تنويع الزوايا والمسافات |
| Sitting | ~150 | baseline جيد |
| Lying | ~184 | baseline جيد |

**أهداف تقريبية لـ accuracy أعلى:**

- 300+ صورة لكل **class** → متوقع ~93–95% **accuracy**
- 500+ صورة لكل **class** → متوقع ~95–97% **accuracy**
- تنويع: إضاءة، زوايا كاميرا، أنواع أجسام، ملابس، خلفيات
- **Edge cases:** ميلان، انتقال بين أوضاع، زوايا غير معتادة

**إضافة صور:**

1. أضف ملفات `.jpg` / `.png` إلى المجلد المناسب تحت `Docs/train/`
2. أعد تشغيل التدريع (القسم 6)

### 4.2 جودة البيانات

- إزالة الصور **mislabeled**: حتى 5% تسميات خاطئة قد تحد **accuracy** عند ~95%.
- إزالة الأوضاع الغامضة: صور قد يختلف فيها البشر على التسمية (نصف جلوس، نصف استلقاء) تربك النموذج.
- التنويع: لا تضف 100 صورة متشابهة؛ غيّر الظروف.

### 4.3 Data Augmentation (يتطلب تعديل كود)

أثناء التدريع:

- حقن ضوضاء صغيرة على الـ features (محاكاة **landmark jitter**)
- تعديل طفيف لقيم **visibility**
- **Mirror** يسار/يمين (تبديل زوايا وعروض L/R)

هذا يضاعف فعالية مجموعة البيانات دون صور جديدة.

### 4.4 ضبط الـ Architecture (عائد متناقص)

البنية الحالية مناسبة لـ 16 **feature** و3 **classes**. تعديلات محتملة:

| التغيير | الأثر المتوقع |
|---------|----------------|
| المزيد من الطبقات المخفية (3–4) | تحسين هامشي، خطر **overfitting** |
| طبقات أوسع (128, 64) | هامشي مع بيانات أكثر |
| **Batch Normalization** | قد يساعد مع > 1000 عينة |
| **Activations** أخرى (GELU, SiLU) | ضئيل لهذه المهمة |

### 4.5 Feature Engineering (أثر متوسط)

**Features** إضافية قد تساعد:

- زوايا **Elbow**
- مسافة **Wrist-to-hip**
- **Temporal features** (فيديو: سرعة حركة **landmarks**)
- **Body aspect ratio** (عرض/ارتفاع **bounding box**)

> **تحذير:** أي **feature** جديد يتطلب تحديث `feature_engineering.py` **و** `PostureMlpFeatureExtractor.kt` بنفس المنطق تماماً، ثم إعادة التدريع.

### 4.6 Hyperparameter Tuning

| Parameter | الحالي | جرّب |
|-----------|--------|------|
| Learning rate | 1e-3 | 5e-4, 3e-4 |
| L2 regularization | 1e-3 | 1e-4, 5e-4 |
| Dropout | 0.30 / 0.15 | 0.20 / 0.10, 0.40 / 0.20 |
| Batch size | 32 | 16, 64 |
| Patience | 20 | 30 (مع بيانات أكثر) |

---

## 5. البناء على النموذج أم التدريع من الصفر؟

### إجابة مختصرة

**في الإعداد الحالي النموذج يُدرَّب من الصفر في كل مرة.** لا يوجد خط **incremental** أو **fine-tuning** جاهز.

### لماذا؟

1. **التدريع سريع** — الـ pipeline كاملاً (**feature extraction** + تدريع الـ MLP) حوالي ~30 ثانية على جهاز حديث؛ لا فائدة كبيرة من حفظ **checkpoints** لنموذج 15 KB.
2. **Feature extraction هو الـ bottleneck** — **MediaPipe** يعالج كل صورة (~200ms × عدد الصور). تدريع الـ MLP نفسه < ~5 ثوانٍ.
3. **إعادة إنتاج حتمية** — نفس البيانات + نفس **seed** = نفس النموذج؛ مفيد للمقارنة والتصحيح.

### هل يمكن Transfer Learning بالمعنى الكلاسيكي؟

ليس كما في **ImageNet** + **fine-tuning** على البكسلات، لأن:

- مدخل النموذج هو **16 hand-crafted features** وليس بكسلات خام.
- النموذج 3 طبقات فقط — لا يوجد «تمثيل عميق» ينقل بسهولة.
- الـ features مرتبطة بتنسيق **landmarks** الخاص بـ **MediaPipe**.

لكن **استخراج الـ landmarks من MediaPipe** هو شكل من **transfer learning**: نستفيد من نموذج **pose** مدرب على ملايين الصور، ثم ندرّب مصنّفاً صغيراً فوقه.

### ماذا يبقى بين التشغيلات؟

| العنصر | يُحفظ؟ | كيف |
|--------|--------|-----|
| صور التدريع | نعم | تبقى في `Docs/train/` |
| تعريف الـ features | نعم | `feature_engineering.py` (مع مزامنة Kotlin) |
| إحصاءات **normalization** | تُعاد حسابها | من **training** في كل مرة |
| **Model weights** | تُعاد توليدها | تدريع من الصفر |
| **Hyperparameters** | نعم | في وسائط `train_posture_mlp.py` |

### مستقبلاً: Incremental Training

إذا كبرت مجموعة البيانات (1000+ صورة)، يمكن النظر في:

1. حفظ نموذج **Keras** (`.keras`) لتقليل إعادة استخراج كل الصور
2. تخزين الـ features المستخرجة في `.npz`
3. **Fine-tuning** النموذج المحفوظ على صور جديدة فقط

يتطلب ذلك تعديل `train_posture_mlp.py` (مثلاً علَم `--resume`).

---

## 6. كيف تعيد التدريع

### المتطلبات

```bash
cd tools/posture-mlp
pip install -r requirements.txt
```

حمّل `pose_landmarker_full.task` إلى `tools/posture-mlp/`:

```
https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task
```

### تشغيل التدريع

```bash
# من جذر المستودع
python tools/posture-mlp/train_posture_mlp.py --epochs 200
```

الملفات تُكتب مباشرة إلى `android-poc/app/src/main/assets/`:

- `posture_mlp.tflite`
- `posture_mlp_norm.json`

### خيارات مخصصة

```bash
python tools/posture-mlp/train_posture_mlp.py \
  --data path/to/images \
  --out path/to/output \
  --epochs 300 \
  --seed 123
```

### بعد التدريع

1. **Build & Run** لتطبيق Android — يُحمَّل النموذج الجديد تلقائياً من **assets**
2. استخدم تبويب **MLP** في **Debug Activity** للتحقق من التنبؤات
3. إذا لم يُحمَّل النموذج، **Force Stop** للتطبيق ثم افتحه من جديد (**singleton** يخزن أول محاولة تحميل)

---

## 7. ملاحظات التوافق

### إصدار TFLite Runtime

| Component | Version | ملاحظات |
|-----------|---------|----------|
| Python TensorFlow | مثلاً 2.18.1 | للتدريع |
| Android TFLite runtime | 2.16.1 | `org.tensorflow:tensorflow-lite` |
| Export mode | float32, no quantization | لتجنب op `FULLY_CONNECTED v12` |

**لا** تعيد `converter.optimizations` — **dynamic range quantization** ينتج **ops** غير متوافقة مع **runtime** Android الحالي.

### Feature Parity

حساب الـ 16 **feature** يجب أن يكون **متطابقاً** بين:

- `tools/posture-mlp/feature_engineering.py` (Python، تدريع)
- `PostureMlpFeatureExtractor.kt` (Kotlin، **inference**)

أي تغيير في أحدهما يُنسخ في الآخر، ثم إعادة التدريع.

---

## 8. مرجع الملفات

```
tools/posture-mlp/
  ├── requirements.txt
  ├── feature_engineering.py     — تعريف متجه الـ 16 feature (عقد)
  ├── train_posture_mlp.py       — التدريع + التصدير
  ├── TRAINING_GUIDE.md          — الدليل (إنجليزي)
  ├── TRAINING_GUIDE_AR.md       — هذا الملف (عربي)
  └── README.md

android-poc/app/src/main/
  ├── assets/
  │   ├── posture_mlp.tflite
  │   └── posture_mlp_norm.json
  └── java/.../training/engine/
      ├── PostureMlpFeatureExtractor.kt
      ├── PostureMlpClassifier.kt
      └── PoseSceneDetector.kt

Docs/train/
  ├── Standing/   — class 0
  ├── Sitting/    — class 1
  └── Lying/      — class 2
```
