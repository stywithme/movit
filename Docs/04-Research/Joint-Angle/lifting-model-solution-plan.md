# الحل الحقيقي: نموذج Lifting خفيف (2D → 3D)

## لماذا الحل الحالي لا يكفي؟

### المشكلة الجوهرية

MediaPipe يعطينا نوعين من الإحداثيات:

- **Normalized Landmarks (2D)**: دقيقة في x,y لكن بدون عمق حقيقي — زاوية الكوع تبدو مختلفة حسب زاوية الكاميرا.
- **World Landmarks (3D)**: تحتوي z لكنها **غير موثوقة** خصوصاً للأطراف (المعصم/الكوع) لأن MediaPipe يقدّر العمق من صورة واحدة بدون أي معلومات كاميرا حقيقية.

### ما نفعله الآن (Heuristic)

```
MediaPipe 2D/3D → حساب زاوية → ElbowAngleEstimator v3 (confidence-based: maxDzShare → ثقة) → زاوية معدّلة
```

الإصدار الحالي (v3 — confidence-based) يعمل بشكل أفضل من سابقيه لكن:
- القواعد مبنية على ملاحظات تجريبية وليس على فهم هندسي حقيقي
- كل حالة جديدة تحتاج ضبط يدوي جديد
- لا يمكن أن يغطي كل تركيبات (زاوية كوع × زاوية كاميرا × اتجاه الجسم)
- المنظر الجانبي لا يمكن إصلاحه بالكامل (خطأ +38° إلى +58°)

### ما تفعله الأبحاث الناجحة

```
2D keypoints → نموذج مُدرّب على ملايين الأوضاع → إحداثيات 3D دقيقة → زاوية صحيحة
```

النموذج "تعلّم" من بيانات حقيقية (motion capture) كيف يحول أي تكوين 2D إلى الشكل 3D الصحيح.

---

## ما هو نموذج Lifting؟

### المفهوم

نموذج Lifting هو شبكة عصبية صغيرة:

```
المدخل:  [x₁,y₁, x₂,y₂, ... x₁₇,y₁₇]     ← 17 مفصل × 2 = 34 رقم
المخرج:  [X₁,Y₁,Z₁, X₂,Y₂,Z₂, ... X₁₇,Y₁₇,Z₁₇]  ← 17 مفصل × 3 = 51 رقم
```

المدخل هو إحداثيات المفاصل ثنائية الأبعاد (من MediaPipe أو أي كاشف).
المخرج هو الإحداثيات ثلاثية الأبعاد المقدّرة.

### لماذا يعمل؟

- **مدرّب على بيانات motion capture حقيقية**: يعرف أن الكوع المثني بزاوية 90° له شكل معين في 2D حسب زاوية الكاميرا
- **تعلّم القيود التشريحية ضمنياً**: يعرف أن العضد والساعد لهما أطوال ثابتة
- **يعرف غموض العمق**: تعلّم من ملايين الأمثلة أي تكوينات 3D تنتج نفس الإسقاط 2D

### أبسط مثال عملي (من ورقة Martinez et al. 2017)

النموذج الأصلي في الورقة يستخدم `hidden=1024` (~4.2M params, 17MB):
```
Input (34) → FC(1024) → BN → ReLU → Dropout
          → FC(1024) → BN → ReLU → Dropout    [× 2 residual blocks]
          → FC(51) → Output
```

**النسخة المقترحة للموبايل** تستخدم `hidden=512` أو `256` (انظر §2.1 أدناه) للحصول على حجم < 2MB مع أداء مقبول.

---

## كيف يحل مشكلة الكوع تحديداً؟

### السيناريو الحالي

```
المستخدم يثني كوعه 75° بزاوية جانبية
   ↓
MediaPipe 2D: 70° (الإسقاط المنظوري يُقلّص الزاوية الحقيقية)
MediaPipe 3D: 104° (الـ Z خاطئ تماماً)
   ↓
Heuristic: يحاول التوسط → يعطي 47° أو 51° (خطأ كبير)
```

### مع نموذج Lifting

```
المستخدم يثني كوعه 75° بزاوية جانبية
   ↓
MediaPipe 2D: 70° (نفس الإسقاط)
   ↓
Lifting Model: "رأيت هذا النمط آلاف المرات في بيانات التدريب.
               مع هذا الشكل العام للجسم من هذه الزاوية،
               الكوع الحقيقي حوالي 74°"
   ↓
النتيجة: 74° (قريبة جداً من الحقيقة)
```

### لماذا هو أفضل من Heuristic؟

| الجانب | Heuristic الحالي | Lifting Model |
|--------|-----------------|---------------|
| مصدر المعرفة | قواعد يدوية | ملايين الأمثلة الحقيقية |
| التعميم | يفشل في حالات جديدة | يتعامل مع حالات لم يرها بالضبط |
| الكوع تحديداً | يعالج بمعادلات تقريبية | تعلّم العلاقة الحقيقية |
| التأثير على بقية المفاصل | معزول للكوع فقط | يمكن تطبيقه على الكوع فقط أو كل المفاصل |
| الصيانة | تعديل مستمر للثوابت | تدريب مرة + ثبات |

---

## خطوات التنفيذ التفصيلية

### المرحلة 1: تجهيز البيانات (أسبوع 1-2)

#### 1.1 الحصول على بيانات التدريب

أفضل مجموعة بيانات متاحة مجاناً: **Human3.6M**

- 3.6 مليون وضعية ثلاثية الأبعاد
- 11 ممثل يؤدون 15 نشاط
- كاميرات متعددة مع إحداثيات 3D حقيقية (motion capture)
- **مجانية للاستخدام البحثي** (تحتاج تسجيل)

```
الموقع: http://vision.imar.ro/human3.6m/
التسجيل مطلوب → تنزيل الـ Poses (D3 Positions) + Camera Parameters
```

بدائل إضافية للتدريب/الاختبار:
- **MPI-INF-3DHP**: بيانات أصغر لكن بيئات متنوعة أكثر
- **3DPW**: فيديوهات خارجية (outdoor) مع ground truth 3D

#### 1.2 معالجة البيانات

```python
# الخطوات الأساسية لتجهيز البيانات

# 1. تحميل بيانات Human3.6M
#    - الإحداثيات ثلاثية الأبعاد (ground truth): shape = (N, 17, 3)
#    - معاملات الكاميرا (K, R, t): لكل كاميرا

# 2. إسقاط 3D إلى 2D باستخدام معاملات الكاميرا
#    x_2d = K @ (R @ x_3d + t)
#    ← هذا يعطينا أزواج (2D, 3D) للتدريب

# 3. تطبيع (Normalization)
#    - 2D: طرح مركز الحوض (hip center) ثم قسمة على حجم الصورة
#    - 3D: طرح مركز الحوض ثم قسمة على المسافة بين الكتفين (أو std)

# 4. تقسيم البيانات
#    - تدريب: subjects [1, 5, 6, 7, 8]
#    - اختبار: subjects [9, 11]
#    ← هذا هو التقسيم المعياري في كل الأبحاث
```

#### 1.3 ربط المفاصل (Joint Mapping)

Human3.6M يستخدم 17 مفصل، MediaPipe يستخدم 33 نقطة.
نحتاج ربط (mapping) بينهما:

```
H36M Index → MediaPipe Index → الاسم
─────────────────────────────────────
0  (Hip)         → 23,24 mid  → مركز الحوض
1  (R.Hip)       → 24        → الورك الأيمن
2  (R.Knee)      → 26        → الركبة اليمنى
3  (R.Ankle)     → 28        → الكاحل الأيمن
4  (L.Hip)       → 23        → الورك الأيسر
5  (L.Knee)      → 25        → الركبة اليسرى
6  (L.Ankle)     → 27        → الكاحل الأيسر
7  (Spine)       → mid(11,12,23,24) → العمود الفقري
8  (Thorax)      → mid(11,12) → الصدر
9  (Neck/Nose)   → 0         → الأنف
10 (Head)        → mid(7,8)  → الرأس
11 (L.Shoulder)  → 11        → الكتف الأيسر
12 (L.Elbow)     → 13        → الكوع الأيسر  ← هذا ما يهمنا
13 (L.Wrist)     → 15        → المعصم الأيسر ← وهذا
14 (R.Shoulder)  → 12        → الكتف الأيمن
15 (R.Elbow)     → 14        → الكوع الأيمن  ← وهذا
16 (R.Wrist)     → 16        → المعصم الأيمن ← وهذا
```

---

### المرحلة 2: تصميم وتدريب النموذج (أسبوع 2-4)

#### 2.1 بنية النموذج المقترحة

نريد نموذج خفيف يعمل على الموبايل. التصميم المقترح:

```python
import torch
import torch.nn as nn

class LiftingModel(nn.Module):
    """
    Simple residual MLP for 2D → 3D pose lifting.
    Input:  17 joints × 2 (x,y) = 34
    Output: 17 joints × 3 (X,Y,Z) = 51
    """
    def __init__(self, num_joints=17, hidden=512, num_blocks=2, dropout=0.25):
        super().__init__()
        in_dim = num_joints * 2
        out_dim = num_joints * 3

        self.input_proj = nn.Sequential(
            nn.Linear(in_dim, hidden),
            nn.BatchNorm1d(hidden),
            nn.ReLU(),
            nn.Dropout(dropout)
        )

        self.blocks = nn.ModuleList([
            ResBlock(hidden, dropout) for _ in range(num_blocks)
        ])

        self.output_proj = nn.Linear(hidden, out_dim)

    def forward(self, x):
        # x: (B, 34) → normalized 2D joints
        h = self.input_proj(x)
        for block in self.blocks:
            h = block(h)
        return self.output_proj(h)  # (B, 51)


class ResBlock(nn.Module):
    def __init__(self, hidden, dropout):
        super().__init__()
        self.layers = nn.Sequential(
            nn.Linear(hidden, hidden),
            nn.BatchNorm1d(hidden),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(hidden, hidden),
            nn.BatchNorm1d(hidden),
            nn.ReLU(),
            nn.Dropout(dropout)
        )

    def forward(self, x):
        return x + self.layers(x)
```

**حجم النموذج:**
- `hidden=512, blocks=2`: ~1.6M parameters ≈ 6.4 MB (float32) ≈ 1.6 MB (int8 quantized)
- `hidden=256, blocks=2`: ~0.4M parameters ≈ 1.6 MB → 0.4 MB (int8)

#### 2.2 دالة الخسارة (Loss Function)

```python
def lifting_loss(pred_3d, gt_3d, bone_pairs):
    """
    Combined loss:
    1. MPJPE: Mean Per-Joint Position Error (L2)
    2. Bone Length Consistency: penalize varying bone lengths
    3. Elbow Angle Loss: extra weight on elbow accuracy
    """

    # 1. Position loss (MPJPE)
    pos_loss = torch.mean(torch.norm(pred_3d - gt_3d, dim=-1))

    # 2. Bone length loss
    bone_loss = 0
    for (j1, j2) in bone_pairs:
        pred_len = torch.norm(pred_3d[:, j1] - pred_3d[:, j2], dim=-1)
        gt_len = torch.norm(gt_3d[:, j1] - gt_3d[:, j2], dim=-1)
        bone_loss += torch.mean(torch.abs(pred_len - gt_len))
    bone_loss /= len(bone_pairs)

    # 3. Elbow angle loss (extra emphasis)
    elbow_angle_loss = compute_angle_loss(pred_3d, gt_3d,
        shoulder_idx=[11,14], elbow_idx=[12,15], wrist_idx=[13,16])

    return pos_loss + 0.5 * bone_loss + 2.0 * elbow_angle_loss
```

#### 2.3 التدريب

```python
# إعدادات التدريب الموصى بها
config = {
    "batch_size": 1024,
    "learning_rate": 1e-3,
    "weight_decay": 1e-5,
    "epochs": 80,
    "optimizer": "AdamW",
    "scheduler": "CosineAnnealing",
    "augmentation": {
        "horizontal_flip": True,        # عكس أفقي
        "joint_noise_std": 0.01,        # ضوضاء على 2D inputs
        "scale_range": [0.9, 1.1],      # تغيير حجم
    }
}

# التدريب الأساسي
optimizer = torch.optim.AdamW(model.parameters(), lr=1e-3, weight_decay=1e-5)
scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=80)

for epoch in range(80):
    for batch_2d, batch_3d in dataloader:
        pred_3d = model(batch_2d)
        loss = lifting_loss(pred_3d, batch_3d, bone_pairs)

        optimizer.zero_grad()
        loss.backward()
        optimizer.step()

    scheduler.step()
```

#### 2.4 معيار النجاح

```
المعيار الأساسي: MPJPE (Mean Per-Joint Position Error)
- هدف عام: < 50mm (ممتاز) أو < 60mm (جيد)
- هدف الكوع: < 40mm (لأنه المفصل الأهم لنا)

معيار إضافي: خطأ زاوية الكوع
- هدف: < 5° متوسط الخطأ (mean angular error)
- المقارنة: MediaPipe World الحالي ≈ +17° (أمامي) إلى +58° (جانبي) خطأ
```

---

### المرحلة 3: تحويل النموذج للموبايل (أسبوع 4-5)

#### 3.1 تصدير إلى TensorFlow Lite

```python
# الخطوة 1: تحويل PyTorch → ONNX
dummy = torch.randn(1, 34)
torch.onnx.export(model, dummy, "lifting_model.onnx",
                  input_names=["input_2d"],
                  output_names=["output_3d"],
                  opset_version=13)

# الخطوة 2: ONNX → TensorFlow
import onnx
from onnx_tf.backend import prepare
onnx_model = onnx.load("lifting_model.onnx")
tf_rep = prepare(onnx_model)
tf_rep.export_graph("lifting_model_tf")

# الخطوة 3: TensorFlow → TFLite (مع تكميم int8)
import tensorflow as tf
converter = tf.lite.TFLiteConverter.from_saved_model("lifting_model_tf")
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.int8]
tflite_model = converter.convert()

with open("lifting_model.tflite", "wb") as f:
    f.write(tflite_model)

# الحجم المتوقع: 0.4 - 1.6 MB
```

**بديل مباشر: PyTorch → TFLite عبر ai.google.dev/edge/litert**

```python
# أو التحويل المباشر إذا كان LiteRT يدعم العمليات
import ai_edge_torch
edge_model = ai_edge_torch.convert(model, (dummy,))
edge_model.export("lifting_model.tflite")
```

#### 3.2 اختبار الأداء

```
الهدف:
- زمن الاستدلال: < 2ms على هاتف متوسط (Snapdragon 7-series)
- الحجم: < 2MB في APK

المتوقع مع MLP بسيط (hidden=256, blocks=2):
- زمن: ~0.5ms على CPU، ~0.2ms على GPU/NNAPI
- حجم: ~0.4MB (int8 quantized)
← هذا لن يؤثر على الأداء الإجمالي للتطبيق إطلاقاً
```

---

### المرحلة 4: الدمج في التطبيق (أسبوع 5-6)

#### 4.1 إضافة ملف النموذج

```
kmp-app/app/src/main/assets/
  └── lifting_model.tflite    ← ملف النموذج
```

#### 4.2 إنشاء LiftingModelHelper

```kotlin
// ---- الملف الجديد ----
// com/trainingvalidator/poc/analysis/LiftingModelHelper.kt

class LiftingModelHelper(context: Context) {

    private val interpreter: Interpreter

    init {
        val model = FileUtil.loadMappedFile(context, "lifting_model.tflite")
        val options = Interpreter.Options().apply {
            setNumThreads(2)
            // اختياري: تفعيل GPU delegate
            // addDelegate(GpuDelegate())
        }
        interpreter = Interpreter(model, options)
    }

    /**
     * يأخذ 17 مفصل 2D (مطبّعة) ويُرجع 17 مفصل 3D.
     *
     * @param joints2d  FloatArray بحجم 34 (17 مفصل × [x,y])
     * @return FloatArray بحجم 51 (17 مفصل × [X,Y,Z])
     */
    fun lift(joints2d: FloatArray): FloatArray {
        val input = arrayOf(joints2d)           // shape [1, 34]
        val output = Array(1) { FloatArray(51) } // shape [1, 51]
        interpreter.run(input, output)
        return output[0]
    }

    fun close() {
        interpreter.close()
    }
}
```

#### 4.3 إنشاء MediaPipeToH36MMapper

```kotlin
// ---- الملف الجديد ----
// com/trainingvalidator/poc/analysis/PoseMapper.kt

object PoseMapper {

    // ربط H36M 17-joint مع MediaPipe 33-point
    private val H36M_TO_MP = intArrayOf(
        -1,  // 0: Hip center = mid(23,24)
        24,  // 1: R.Hip
        26,  // 2: R.Knee
        28,  // 3: R.Ankle
        23,  // 4: L.Hip
        25,  // 5: L.Knee
        27,  // 6: L.Ankle
        -2,  // 7: Spine = mid(11,12,23,24)
        -3,  // 8: Thorax = mid(11,12)
        0,   // 9: Nose
        -4,  // 10: Head = mid(7,8)
        11,  // 11: L.Shoulder
        13,  // 12: L.Elbow
        15,  // 13: L.Wrist
        12,  // 14: R.Shoulder
        14,  // 15: R.Elbow
        16   // 16: R.Wrist
    )

    /**
     * يحوّل 33 نقطة MediaPipe (normalized) إلى 17 نقطة H36M format.
     * ثم يطبّع حول مركز الحوض (root-relative).
     *
     * @return FloatArray بحجم 34 (17 × 2) جاهز للنموذج
     */
    fun mediaPipeToH36M(landmarks: List<SmoothedLandmark>): FloatArray {
        val joints = FloatArray(34)  // 17 × 2

        for (h36m_idx in 0 until 17) {
            val (x, y) = when (val mp_idx = H36M_TO_MP[h36m_idx]) {
                -1 -> midpoint(landmarks, 23, 24)             // Hip center
                -2 -> midpoint4(landmarks, 11, 12, 23, 24)   // Spine
                -3 -> midpoint(landmarks, 11, 12)             // Thorax
                -4 -> midpoint(landmarks, 7, 8)               // Head top
                else -> Pair(landmarks[mp_idx].x, landmarks[mp_idx].y)
            }
            joints[h36m_idx * 2]     = x
            joints[h36m_idx * 2 + 1] = y
        }

        // Root-relative normalization
        val rootX = joints[0]
        val rootY = joints[1]
        for (i in joints.indices step 2) {
            joints[i]     -= rootX
            joints[i + 1] -= rootY
        }

        return joints
    }

    private fun midpoint(lm: List<SmoothedLandmark>, a: Int, b: Int): Pair<Float, Float> =
        Pair((lm[a].x + lm[b].x) / 2f, (lm[a].y + lm[b].y) / 2f)

    private fun midpoint4(lm: List<SmoothedLandmark>, a: Int, b: Int, c: Int, d: Int): Pair<Float, Float> =
        Pair((lm[a].x + lm[b].x + lm[c].x + lm[d].x) / 4f,
             (lm[a].y + lm[b].y + lm[c].y + lm[d].y) / 4f)
}
```

#### 4.4 تعديل Pipeline الحالي

التعديل المطلوب في `MainActivity.kt` وبقية الأماكن:

```kotlin
// --- الوضع الحالي ---
val rawAngles = AngleCalculator.calculateAllAnglesSmoothed(worldLandmarks, ...)
val correctedAngles = elbowAngleEstimator.correct(rawAngles, worldLandmarks, ...)

// --- الوضع الجديد ---
// 1. تحويل MediaPipe → H36M format
val h36mInput = PoseMapper.mediaPipeToH36M(smoothedLandmarks)

// 2. تشغيل Lifting Model
val lifted3D = liftingModel.lift(h36mInput)

// 3. حساب الزوايا من الإحداثيات المرفوعة (3D الجديدة)
val liftedAngles = AngleCalculator.calculateFromLifted(lifted3D)

// 4. دمج: استخدام الزوايا المرفوعة للكوع فقط، وبقية الزوايا من World
val finalAngles = rawAngles.copy(
    leftElbow = liftedAngles.leftElbow ?: rawAngles.leftElbow,
    rightElbow = liftedAngles.rightElbow ?: rawAngles.rightElbow
)
```

**ملاحظة مهمة**: يمكن البدء بتطبيق النموذج على **الكوع فقط** (كما في الخطوة 4 أعلاه) وترك بقية المفاصل على النظام الحالي. هذا يقلل المخاطر ويسمح بالمقارنة.

---

### المرحلة 5: الاختبار والتحسين (أسبوع 6-8)

#### 5.1 اختبار A/B

```
اختبار مقارنة بين ثلاث طرق:
┌─────────────────────────────────────────────────┐
│ A) MediaPipe World 3D (الأصلي)                   │
│ B) Heuristic الحالي (ElbowAngleEstimator)        │
│ C) Lifting Model (الجديد)                        │
└─────────────────────────────────────────────────┘

لكل طريقة، قياس الخطأ في:
- 5 زوايا كوع مختلفة (30°, 60°, 90°, 120°, 160°)
- 3 اتجاهات كاميرا (أمامي، جانبي 45°، جانبي 90°)
= 15 حالة اختبار
```

#### 5.2 التحسين التكراري

```
إذا النتائج أقل من المتوقع:

1. Data Augmentation أقوى
   - إضافة ضوضاء تحاكي خطأ MediaPipe
   - augment بأخطاء نظامية (systematic bias) مشابهة

2. Fine-tuning ببيانات خاصة
   - تسجيل 100-200 وضعية بزوايا كوع معروفة
   - قياس الزاوية بمنقلة حقيقية
   - إضافتها كبيانات fine-tuning

3. Architecture search
   - تجربة hidden=384 أو 512
   - تجربة 3-4 residual blocks
   - تجربة temporal model (GRU) للفيديو
```

---

## المتطلبات التقنية

### الأدوات

```
التدريب:
- Python 3.9+
- PyTorch 2.x
- Google Colab (مجاني) أو GPU محلي
- ~4 ساعات تدريب على Colab T4

التحويل:
- ONNX Runtime
- TensorFlow + TFLite Converter
  (أو ai-edge-torch للتحويل المباشر)

الموبايل:
- TensorFlow Lite Android dependency (موجود أصلاً مع MediaPipe)
- لا تبعيات إضافية
```

### البيانات

```
Human3.6M:
- حجم التنزيل: ~2GB (الأوضاع فقط بدون فيديو)
- التسجيل: مجاني لكن يحتاج طلب وصول
- البديل: يمكن البدء بـ MPI-INF-3DHP (أسرع في الحصول عليها)
```

---

## الجدول الزمني المقترح

```
الأسبوع 1-2: تجهيز البيانات + بناء data pipeline
الأسبوع 2-4: تصميم وتدريب النموذج + اختبار على الكمبيوتر
الأسبوع 4-5: تحويل TFLite + دمج أولي في التطبيق
الأسبوع 5-6: دمج كامل + اختبار على الموبايل
الأسبوع 6-8: تحسين + fine-tuning + اختبارات نهائية

المجموع: 6-8 أسابيع
```

---

## المخاطر والتحديات

### 1. فجوة التوزيع (Domain Gap)
**المشكلة**: النموذج مدرب على 2D من كاشف معين (CPN/HRNet) لكن سيستقبل 2D من MediaPipe.
**الحل**: 
- إضافة ضوضاء عشوائية على المدخلات أثناء التدريب (noise augmentation)
- إعادة تشغيل MediaPipe على صور/فيديوهات Human3.6M واستخدام مخرجاته كمدخلات تدريب (domain adaptation)

### 2. عدد المفاصل المختلف
**المشكلة**: H36M = 17 مفصل، MediaPipe = 33 نقطة.
**الحل**: PoseMapper (تم شرحه في 4.3) — نأخذ فقط النقاط المتطابقة.

### 3. الأداء على الموبايل
**المشكلة**: أي تأثير على الـ FPS؟
**الحل**: MLP بسيط ~0.5ms ← **صفر تأثير عملي** (MediaPipe نفسه يأخذ ~15-25ms).

### 4. الحالات النادرة
**المشكلة**: أوضاع غير موجودة في بيانات التدريب.
**الحل**: fallback — إذا كان خطأ إعادة الإسقاط (reprojection error) كبير، نرجع لـ heuristic الحالي.

---

## الخلاصة

| السؤال | الإجابة |
|--------|---------|
| هل هذا حل حقيقي؟ | **نعم** — هذا ما تفعله كل الأوراق البحثية الناجحة |
| هل يعمل على الموبايل؟ | **نعم** — MLP صغير < 2MB، < 1ms استدلال |
| هل يحل مشكلة الكوع؟ | **نعم** — لأنه تعلّم العلاقة الحقيقية من بيانات motion capture |
| هل يؤثر على بقية المفاصل؟ | **لا** — يمكن تطبيقه على الكوع فقط مع ترك الباقي كما هو |
| ما الثمن؟ | 6-8 أسابيع عمل + تدريب على GPU (Colab مجاني يكفي) |
| ما البديل؟ | الاستمرار في تعديل ثوابت heuristic — يعمل جزئياً لكن لن يصل لدقة حقيقية |
