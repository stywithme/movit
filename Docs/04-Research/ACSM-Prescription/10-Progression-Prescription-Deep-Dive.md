# 10 — التقدم والتوصيف: كل العوامل المؤثرة وأفضل الطرق للوصول للأهداف

> تحليل عميق يربط بين: دراسة ACSM 2026 ← نظام الـ Prescription ← محرك الـ Progression ← الـ Admin Dashboard

---

## الجزء الأول: خريطة النظام الحالي

### A. نظام التوصيف (Prescription Engine V1)

**الملف:** `prescription.service.ts`

**الوظيفة:** تصنيف المستخدم → اختيار برنامج مناسب تلقائياً.

**مسار القرار:**

```
BodyScanResult + UserLevelProfile
        ↓
   classifyUser()
        ↓
┌─────────────────────────────────────────┐
│ 1. SAFETY_BLOCK  — بوابات أمان/ألم/PAR-Q │
│ 2. CORRECTION_NEED — منطقة < 25 نقطة    │
│ 3. IMBALANCE — تماثل < 60%              │
│ 4. WEAKNESS — فجوة domain ≥ 2 مستوى    │
│ 5. NORMAL — متوازن                      │
└─────────────────────────────────────────┘
        ↓
   فلترة البرامج المنشورة
   (type + levelRange + contraindications + targetDomain + targetRegions)
        ↓
   أفضل برنامج (أو fallback)
```

**ما يأخذه في الاعتبار:** حالة الجسم، المستوى، المناطق الضعيفة، السلامة.
**ما لا يأخذه في الاعتبار:** هدف المستخدم (قوة/تضخم/قدرة/صحة)، تاريخ التدريب السابق، تفضيلات شخصية.

---

### B. محرك التدرج (Progression Engine V2)

**الملف:** `progression.service.ts` + `archetype-defaults.ts`

**الوظيفة:** بعد كل جلسة برنامج → تقييم → ترقية أو تراجع أو ثبات.

**مسار القرار:**

```
إكمال جلسة برنامج
        ↓
   ExerciseProgressionProfile (لكل تمرين)
        ↓
   getRecentMetrics() — آخر N جلسات
   ┌──────────────────────────────┐
   │ avgFormScore                 │
   │ completionRate               │
   │ avgROM                       │
   │ avgSymmetry                  │
   │ avgStability                 │
   └──────────────────────────────┘
        ↓
   evaluateEligibility()
   ┌──────────────────────────────────────────┐
   │ formScore < regressionPolicy.maxFormScore │ → REGRESSION
   │ formScore ≥ qualityGate.minFormScore      │
   │ + completionRate ≥ minCompletionRate      │
   │ + ROM ≥ minROM (اختياري)                  │
   │ + Symmetry ≥ minSymmetry (اختياري)        │
   │ + Stability ≥ minStability (اختياري)      │ → PROMOTION (إذا streak كافٍ)
   │ لم يستوفِ                                │ → HOLD
   └──────────────────────────────────────────┘
        ↓
   applyPromotion() — حسب priorityOrder
   ┌──────────────────────────────┐
   │ reps → load → sets → ...    │  (weighted_strength)
   │ reps → difficulty            │  (bodyweight_dynamic)
   │ duration → difficulty        │  (isometric_hold)
   │ reps → duration              │  (mobility_rom)
   │ reps → difficulty            │  (motor_control)
   └──────────────────────────────┘
        ↓
   materializeToNextItem() — تحديث PlannedWorkoutItem القادم
```

---

### C. الأنماط الافتراضية (Archetype Defaults)

| Archetype | المحاور | الأولوية | Quality Gate | Streak |
|-----------|---------|----------|-------------|--------|
| `weighted_strength` | reps, load, sets | reps → load → sets | formScore ≥ 70, completion ≥ 85% | 2 جلسات |
| `bodyweight_dynamic` | reps, difficulty | reps → difficulty | formScore ≥ 75, completion ≥ 85% | 2 |
| `isometric_hold` | duration, difficulty | duration → difficulty | formScore ≥ 70, completion ≥ 85% | 2 |
| `mobility_rom` | reps, duration | reps → duration | formScore ≥ 70, completion ≥ 80%, **ROM ≥ 70** | 3 |
| `motor_control` | reps, difficulty | reps → difficulty | formScore ≥ 80, completion ≥ 90%, **stability ≥ 70** | 3 |

**Regression** في الكل: عندما `avgFormScore < 55` (أو 50–60 حسب النمط).

---

## الجزء الثاني: العوامل المؤثرة — من الدراسة والنظام

### كل العوامل التي تؤثر على التقدم نحو الأهداف

```
┌──────────────────────────────────────────────────────────────┐
│                    عوامل مؤثرة على التقدم                      │
├──────────────┬───────────────┬────────────────────────────────┤
│   في النظام  │  في الدراسة   │       الحالة                   │
│   الحالي ✓   │  مؤثرة ✓      │                                │
├──────────────┼───────────────┼────────────────────────────────┤
│ ✓ formScore  │ ✓ (ROM+شكل)  │ بوابة الجودة الأساسية          │
│ ✓ completion │ ✓ (ضمني)     │ نسبة إتمام التكرارات            │
│ ✓ ROM        │ ✓✓ مهم جداً   │ اختياري في qualityGate          │
│ ✓ symmetry   │ — (لم تُدرس) │ اختياري في qualityGate          │
│ ✓ stability  │ — (محدود)    │ اختياري في qualityGate          │
│ ✓ reps       │ ✓ (الحجم)    │ محور تقدم أساسي                │
│ ✓ load (kg)  │ ✓✓ أهم عامل  │ محور تقدم                      │
│ ✓ sets       │ ✓✓ أسبوعياً  │ محور تقدم                      │
│ ✓ duration   │ ✓ (للثبات)   │ محور تقدم                      │
│ ✓ difficulty │ ✓ (ضمني)     │ سلم تقدم                      │
│ ✓ streak     │ — (غير مباشر)│ 2–3 جلسات ناجحة قبل الترقية    │
├──────────────┼───────────────┼────────────────────────────────┤
│   ✗ غائب     │  ✓ مؤثرة      │                                │
├──────────────┼───────────────┼────────────────────────────────┤
│ ✗ %1RM       │ ✓✓ أهم عامل  │ est1RM موجود لكن لا يدخل       │
│              │    للقوة      │ في قرار التدرج                  │
│ ✗ RPE / RIR  │ ✓✓ بديل      │ غير موجود نهائياً               │
│              │    للفشل      │                                │
│ ✗ velocity   │ ✓✓ VBT/power │ يُحسب لكن لا يدخل في           │
│   Loss       │    فعالة      │ قرار التدرج أو التوصيف          │
│ ✗ هدف        │ ✓✓ يحدد      │ لا يوجد trainingGoal             │
│   المستخدم   │    الأولويات  │ على User أو ActivePlan          │
│ ✗ حجم        │ ✓✓ ≥10/أسبوع │ لا تجميع أسبوعي                │
│   أسبوعي/    │    للتضخم     │ per muscle group                │
│   عضلة       │              │                                │
│ ✗ ترتيب      │ ✓✓ QoE 88%  │ sortOrder ثابت — لا             │
│   التمارين   │              │ توصية ذكية                     │
│ ✗ نية        │ ✓✓ power RT  │ لا trainingIntent               │
│   السرعة     │    يحسّن      │ على PlannedWorkoutItem          │
│              │    الوظيفة    │                                │
│ ✗ eccentric  │ ✓ يحسّن       │ tempo يُسجل لكن لا يُصنف       │
│   ratio      │   التضخم      │ كـ "eccentric-focused"          │
│ ✗ تردد       │ ✓ ≥2/أسبوع   │ هيكل الأسبوع موجود لكن         │
│   أسبوعي     │   للقوة       │ لا حساب تلقائي للتردد           │
├──────────────┼───────────────┼────────────────────────────────┤
│   في النظام  │  ✗ أقل أهمية  │                                │
├──────────────┼───────────────┼────────────────────────────────┤
│ ✓ rest       │ ✗ لا أثر     │ حقل تكوين — لا يحتاج تعقيد     │
│              │   حاسم        │                                │
│ ✓ TUT        │ ✗ لا أثر     │ يُسجل — لا يحتاج أولوية        │
│ — periodiz.  │ ✗ لا تفوق    │ لا محرك — وهذا صحيح            │
│ — equipment  │ ✗ لا فرق     │ —                              │
│ — failure    │ ✗ غير ضروري  │ النظام لا يشجع عليه — جيد      │
└──────────────┴───────────────┴────────────────────────────────┘
```

---

## الجزء الثالث: كيف نوصل للأهداف بأفضل الطرق

### هدف 1: القوة (Strength)

**ما تقوله الدراسة (Table 6):**

| العامل | القيمة المثالية |
|--------|-----------------|
| Frequency | ≥2 جلسات/أسبوع |
| Intensity | **≥80% 1RM** (استجابة جرعية) |
| Type | Eccentric flywheel RT |
| Technique | **Full ROM** |
| Volume | **2–3 مجموعات/تمرين** |
| Exercise order | **بداية الجلسة** |

**ما يحتاج التغيير في النظام:**

1. **Prescription** — عندما `trainingGoal = STRENGTH`:
   - اختيار برامج بحمل عالٍ (برامج مصممة بـ `weightKg` مرتفع نسبياً).
   - التحقق أن البرنامج يحتوي ≥2 جلسات/أسبوع.

2. **Progression** — لنمط `weighted_strength` عندما الهدف قوة:
   - **تغيير الأولوية**: `load → reps → sets` (بدل `reps → load → sets`).
   - السبب: الدراسة تقول الحمل أهم عامل للقوة.
   - **إضافة شرط**: لا ترقية إذا `intensityPercentage < 70%` (تحفيز على رفع الحمل أولاً).
   - **إضافة شرط ROM**: `minROM: 75` في qualityGate (لأن full ROM يحسّن القوة).

3. **ترتيب الجلسة**: فرز تمارين القوة الأساسية أولاً في `PlannedWorkout`.

**المسار المثالي للتقدم (قوة):**

```
جلسة 1: squat 60kg × 8 reps × 3 sets (formScore 78%, ROM 85%)
جلسة 2: squat 60kg × 8 reps × 3 sets (formScore 82%, ROM 88%)
→ Streak = 2 ✓ → Promotion
→ priorityOrder[0] = load (هدف قوة)
→ squat 62.5kg × 8 reps × 3 sets
→ reps تُعاد لـ floor (5) فقط إذا وصلت cap
...
عند cap load → ينتقل لـ reps → ثم sets
```

---

### هدف 2: التضخم (Hypertrophy)

**ما تقوله الدراسة:**

| العامل | القيمة المثالية |
|--------|-----------------|
| Contraction type | **Eccentric overload** |
| Volume | **≥10 مجموعات/أسبوع/مجموعة عضلية** (dose-response) |
| Load | أقل أهمية — نطاق واسع يعمل (30%–100% 1RM) |
| Failure | **غير ضروري** |
| Frequency | لا فرق عند تسوية الحجم |

**ما يحتاج التغيير:**

1. **Progression** — لنمط `weighted_strength` عندما الهدف تضخم:
   - **تغيير الأولوية**: `sets → reps → load` (الحجم أولاً).
   - **رفع cap المجموعات**: `setAxis.cap` من 5 إلى 6–7 (ليصل الأسبوعي لـ ≥10 مع التردد).
   - **إضافة eccentric metric**: إذا `eccentricRatio > 1.5` → bonus في qualityGate (تشجيع تدريب eccentric).

2. **تجميع أسبوعي** — Backend endpoint:
   - حساب `weeklySetsByMuscle` من `WorkoutExecution` + `Exercise.muscles`.
   - مقارنة بـ 10 (الحد الأدنى) و18–20 (الأمثل).
   - تنبيه: "صدر 6/10 مجموعات هذا الأسبوع — أضف جلسة أو مجموعات".

3. **Prescription** — اختيار برامج بحجم أعلى.

**المسار المثالي (تضخم):**

```
الأسبوع: 3 جلسات × 3–4 تمارين × 3 sets = 9–12 sets/muscle
جلسة 1: bench press 50kg × 10 reps × 3 sets (formScore 80%)
جلسة 2: bench press 50kg × 10 reps × 3 sets (formScore 82%)
→ Streak = 2 ✓ → Promotion
→ priorityOrder[0] = sets (هدف تضخم)
→ bench press 50kg × 10 reps × 4 sets
...
عند cap sets → ينتقل لـ reps → ثم load
```

---

### هدف 3: القدرة (Power)

**ما تقوله الدراسة:**

| العامل | القيمة المثالية |
|--------|-----------------|
| Intensity | **30%–70% 1RM** |
| Type | Eccentric flywheel, Olympic-style |
| Technique | **Power RT** (concentric بأقصى سرعة) |
| Volume | منخفض–معتدل (**reps × sets ≤ 24**) |

**ما يحتاج التغيير:**

1. **نمط جديد أو وضع**: `power_training` archetype أو `trainingIntent: POWER` على `PlannedWorkoutItem`.

2. **Progression** مختلف:
   - لا ترقية بالحجم (≤24 reps·sets).
   - الترقية تعني: زيادة **سرعة الأداء** (يُقاس بـ `velocity`) أو زيادة حمل **ضمن النطاق 30–70%**.
   - **velocity** يدخل في qualityGate: `minVelocity` threshold — إذا السرعة قلت كثيراً → regression.

3. **Android** — `TrainingEngine`: عند `intent = POWER`:
   - رسالة: "ارفع بأسرع ما يمكن!"
   - `velocityLoss > 15%` → "أوقف — سرعتك تراجعت. راحة أطول أو قلل الحمل."

---

### هدف 4: صحة عامة / وظيفة (General Health / Functional Fitness)

**ما تقوله الدراسة:**

- **أي** RT أفضل من عدم التمرين — المشاركة أهم من التحسين.
- Power RT يحسّن الوظائف الحركية (مشي، توازن، نهوض من كرسي).
- الحد الأدنى: مرتين أسبوعياً، جميع المجموعات العضلية الرئيسية.

**المطلوب:**

- **Prescription** — اختيار برامج بسيطة، full-body، 2–3 أيام/أسبوع.
- **Progression** — أبطأ (streak = 3)، تركيز على formScore والالتزام.
- **تنبيهات** — تشجيع الاستمرار أكثر من التحسين.

---

## الجزء الرابع: التعديلات المطلوبة في كل مكوّن

### A. تعديلات Prescription Service

```
الوضع الحالي:
  classify → filter by type/level/region → best match

المطلوب:
  classify → ← NEW: trainingGoal →
  filter by type/level/region
  ← NEW: + filter by goalCompatibility →
  ← NEW: + weight by frequency/volume match →
  best match
```

**تفصيل:**

| الإضافة | الشرح |
|---------|-------|
| `trainingGoal` input | يُمرَّر من `User.trainingGoal` أو `ActivePlan.trainingGoal` |
| `goalCompatibility` على `Program` | حقل Json أو enum يحدد أي أهداف يخدمها البرنامج |
| فلترة إضافية | عند `STRENGTH` → برامج بحمل عالٍ وتردد ≥2. عند `HYPERTROPHY` → حجم أعلى. |
| `weeklyFrequency` check | التحقق أن `ProgramWeek` يحتوي عدد أيام مناسب للهدف |

---

### B. تعديلات Progression Engine

#### B1. Priority Order حسب الهدف

```typescript
// archetype-defaults.ts — مقترح
// إضافة goal-specific priority
const GOAL_PRIORITY_OVERRIDE: Record<string, Record<string, string[]>> = {
  weighted_strength: {
    STRENGTH:    ['load', 'reps', 'sets'],     // ← load أولاً
    HYPERTROPHY: ['sets', 'reps', 'load'],     // ← sets أولاً
    POWER:       ['load'],                      // ← load فقط ضمن 30–70%
    GENERAL:     ['reps', 'load', 'sets'],      // ← الافتراضي الحالي
  },
};
```

#### B2. إضافة عوامل جديدة في evaluateEligibility

```typescript
// progression.service.ts — مقترح توسيع WorkoutExecutionMetricsSummary
interface WorkoutExecutionMetricsSummary {
  // الموجود:
  avgFormScore: number | null;
  completionRate: number | null;
  avgROM: number | null;
  avgSymmetry: number | null;
  avgStability: number | null;
  // الجديد:
  avgVelocity: number | null;           // ← لقرارات power
  avgVelocityLoss: number | null;       // ← لتقدير RIR
  intensityPercentage: number | null;   // ← لقرارات strength
  avgRIR: number | null;               // ← إدخال يدوي من المستخدم
  eccentricRatio: number | null;        // ← لقرارات hypertrophy
}
```

#### B3. توسيع QualityGate

```typescript
interface QualityGate {
  // الموجود:
  minFormScore: number;
  minCompletionRate: number;
  minROM?: number;
  minSymmetry?: number;
  minStability?: number;
  // الجديد:
  minIntensityPct?: number;    // ← مثلاً 75% لهدف القوة
  maxVelocityLoss?: number;    // ← مثلاً 25% — تقدير RIR
  minVelocity?: number;        // ← لهدف power
  maxRIR?: number;             // ← إذا المستخدم أدخل RIR ≤ 1 → لا ترقية
}
```

#### B4. Regression مُحسَّن

```
الحالي: formScore < maxFormScore → regression
المقترح (إضافي):
  - velocityLoss > 30% لجلستين → regression (إرهاق مفرط)
  - RIR = 0 لجلستين → regression (فشل متكرر = حمل زائد)
  - intensityPct > 95% + formScore < 70 → regression (حمل أعلى من القدرة)
```

---

### C. تعديلات Admin Dashboard

#### صفحة Exercise Progression (`[exerciseId]/page.tsx`)

**الموجود:** allowedAxes, priorityOrder, axis configs, qualityGate, promotion/regression policies.

**المطلوب إضافته:**

1. **Goal-Specific Priority Override** — قسم جديد:
   - "Priority by Goal" — يظهر بعد Priority Order.
   - لكل هدف (Strength, Hypertrophy, Power, General) → ترتيب مختلف.
   - أو: خيار "Use default for all goals" vs "Customize per goal".

2. **توسيع Quality Gate** — حقول إضافية:
   - Min Intensity % (optional) — مع tooltip: "ACSM recommends ≥80% for strength goals"
   - Max Velocity Loss % (optional) — tooltip: "Proxy for fatigue/RIR"
   - Min Velocity (optional) — tooltip: "For power-focused exercises"
   - Max RIR (optional) — tooltip: "If user reports ≤1 RIR, hold progression"

3. **Archetype Descriptions** المُحدَّثة:
   - ربط كل وصف بنتيجة من ACSM مع tooltip.
   - مثل: "Weighted Strength — Progressive overload with external load. **ACSM: Load ≥80% 1RM enhances strength (QoE 79%)**"

---

### D. إضافات قاعدة البيانات (Prisma Schema)

```prisma
// على User أو ActivePlan
enum TrainingGoal {
  STRENGTH
  HYPERTROPHY
  POWER
  GENERAL_HEALTH
  FUNCTIONAL_FITNESS
}

// حقل جديد على User
model User {
  trainingGoal  TrainingGoal?
}

// حقل جديد على ActivePlan (يتجاوز User-level)
model ActivePlan {
  trainingGoal  TrainingGoal?
}

// على PlannedWorkoutItem
enum TrainingIntent {
  STANDARD
  POWER
  ECCENTRIC
  VELOCITY_BASED
}

model PlannedWorkoutItem {
  trainingIntent  TrainingIntent  @default(STANDARD)
}

// توسيع WorkoutExecutionMetrics
model WorkoutExecutionMetrics {
  // الموجود: avgRom, avgSymmetry, avgStability, avgVelocity, ...
  // جديد:
  avgVelocityLoss     Int?    // ×10 int
  avgPerceivedExertion Int?    // 0–100 (RPE × 10)
  avgRepsInReserve    Int?    // 0–50  (RIR × 10)
  eccentricRatio      Int?    // ×10 int  (eccentric/concentric)
}

// توسيع ExerciseProgressionProfile
model ExerciseProgressionProfile {
  // الموجود: qualityGate, promotionPolicy, regressionPolicy
  // جديد:
  goalPriorityOverrides  Json?  // { STRENGTH: [...], HYPERTROPHY: [...] }
}

// توسيع Program لدعم الفلترة بالهدف
model Program {
  goalCompatibility  String[]  @default([])  // ["STRENGTH", "HYPERTROPHY"]
}
```

---

## الجزء الخامس: الملخص — خريطة شاملة «من الهدف إلى التقدم»

```
┌─────────────────┐
│   المستخدم      │
│  trainingGoal   │
│  assessment     │
│  levelProfile   │
└────────┬────────┘
         ↓
┌─────────────────────────────────────┐
│        Prescription Engine          │
│  classify + goal + filter           │
│  → recommended program              │
│  (goalCompatibility + frequency     │
│   + volume match)                   │
└────────┬────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│         Active Program              │
│  ProgramWeeks → Days → Planned Workouts    │
│  PlannedWorkoutItem:               │
│    exercise, sets, reps, weight,    │
│    trainingIntent, sortOrder        │
│  (ترتيب ذكي: strength أولاً)       │
└────────┬────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│         Planned Workout            │
│  (Android — TrainingEngine)         │
│  قياس: ROM, velocity, tempo,       │
│  formScore, velocityLoss            │
│  إدخال: RPE/RIR (اختياري)          │
│  تنبيه: "جهدك كافٍ" عند            │
│  velocityLoss > threshold           │
└────────┬────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│         Progression Engine          │
│  1. Load profile + goal             │
│  2. goal → priorityOrder override   │
│  3. metrics → eligibility           │
│     (formScore + ROM + intensity%   │
│      + velocityLoss + RIR)          │
│  4. streak check                    │
│  5. promote/regress/hold            │
│  6. materialize → next planned workout      │
└────────┬────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│         Weekly Reports              │
│  • Volume per muscle (vs ≥10)       │
│  • Intensity trend (%1RM)           │
│  • Velocity trend (power)           │
│  • formScore trend                  │
│  • Progression history              │
│  • "هدفك قوة — حملك 78% من 1RM     │
│     → ارفع 2.5kg الأسبوع القادم"   │
└─────────────────────────────────────┘
```

---

## الجزء السادس: أولويات التنفيذ

| الأولوية | البند | الأثر على التقدم | الجهد |
|---------|-------|-----------------|-------|
| **1** | إضافة `trainingGoal` + ربطه بـ priorityOrder | **يغير اتجاه التدرج كلياً** | متوسط |
| **2** | تفعيل `intensityPercentage` في الـ progression | **يحمي من حمل غير كافٍ** | منخفض |
| **3** | إضافة `velocityLoss` في qualityGate | **يمنع الإفراط ويقدّر RIR** | متوسط |
| **4** | تجميع حجم أسبوعي per muscle | **يضمن حجم كافٍ للتضخم** | متوسط |
| **5** | إضافة RPE/RIR كإدخال اختياري | **يعطي بُعداً ذاتياً للقرار** | متوسط |
| **6** | `trainingIntent` على PlannedWorkoutItem | **يُفعّل تنبيهات power/eccentric** | منخفض |
| **7** | Goal-specific priority في Admin Dashboard | **يمكّن المصمم من الضبط** | منخفض |
| **8** | ترتيب تمارين ذكي | **يحسّن القوة — QoE 88%** | منخفض |
| **9** | `goalCompatibility` على Program | **يُحسّن الـ prescription** | منخفض |
| **10** | توسيع regression policy | **يحمي من الإصابة/الإرهاق** | متوسط |
