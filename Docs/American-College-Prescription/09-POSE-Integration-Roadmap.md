# 09 — خارطة ربط نتائج ACSM 2026 بمشروع POSE

> مبنية على مقارنة مفصّلة بين نتائج الدراسة والحالة الفعلية للكود في `android-poc` و `backend`.

---

## أولاً: ما عندنا فعلاً ويحتاج تفعيل أو إبراز

### 1. تفعيل `intensityPercentage` (% 1RM)

**الدليل:** القوة تتحسن أكثر عند ≥80% 1RM (استجابة جرعية واضحة — Table 4, 6 reviews, QoE 79%).

**الوضع الحالي:** `est1RM` يُحسب في `MetricsCalculator`، و`intensityPercentage` معرّف في `SessionMetrics` (Prisma + Kotlin) لكنه **غير مملوء** (مُعلَّم Future/Later).

**المطلوب:**

- **Backend** — `training-sessions.service.ts`: عند حفظ `SessionMetrics`، حساب `intensityPercentage = (weightKg / est1RM) * 100` عندما يكون `est1RM > 0`.
- **Android** — `MetricsCalculator`: نفس الحساب محلياً لعرض فوري في `PostTrainingReport`.
- **UI** — مؤشر في تقرير ما بعد التدريب: "شدة الحمل 85% من 1RM — مناسب لهدف القوة".

---

### 2. استخدام `velocityLoss` كتقدير ذكي لـ RIR

**الدليل:** الرفع حتى الفشل **ليس ضرورياً** (3 مراجعات، QoE 83%)؛ RIR 2–3 كافٍ. انخفاض سرعة الحركة مؤشر موثوق للقرب من الفشل في أدبيات VBT.

**الوضع الحالي:** `velocity` و`velocityLoss` يُحسبان من زوايا الكاميرا لكل تكرار — لكن لا يُستخدمان لتقدير RIR أو لتوجيه المستخدم.

**المطلوب:**

- **Android** — `MetricsCalculator` أو `TrainingEngine`: عند تجاوز `velocityLoss` عتبة (مثلاً 20–25% من سرعة أول تكرار) → إصدار `FeedbackEvent` جديد: "الجهد كافٍ — يمكنك التوقف أو إكمال 1–2 تكرار إضافي".
- **العتبة** قابلة للضبط في `ExerciseProgressionProfile` أو ثابت عام أولاً.
- **لا يُشجَّع** على الفشل لدى المستخدمين عموماً — متسق مع نتيجة الدراسة.

---

### 3. إبراز مؤشر eccentric overload من `tempo`

**الدليل:** التضخم يتحسن بـ eccentric overload/contractions (Table 4 — Contraction type ✔ للتضخم).

**الوضع الحالي:** `tempo [eccentric, iso, concentric]` يُسجل لكل تكرار. لكن لا يُصنف "هل هذا تكرار eccentric-focused أم لا".

**المطلوب:**

- **Android** — `PostTrainingReport` / `EnhancedPerformanceMetrics`: حساب نسبة `eccentricMs / concentricMs`؛ عندما تكون >1.5 بشكل متكرر → عرض بطاقة "تدريب مع تركيز لاإرادي — مفيد للتضخم العضلي".
- لاحقاً: يمكن لمصمم البرنامج أن يضبط `trainingIntent: eccentric` على `ProgramSessionItem` لتفعيل التغذية الراجعة المناسبة.

---

### 4. تخفيف emphasis على TUT والراحة في التقارير

**الدليل:** TUT (سريع vs بطيء) و inter-set rest **لا يؤثران بشكل حاسم** على القوة أو التضخم (Table 4 — ✖ في كليهما).

**الوضع الحالي:** `totalTUT` و`restBetweenSetsMs` يُعرضان في التقارير ويُستخدمان في الـ progression.

**المطلوب:**

- **لا حذف** — لكن عند عرض التقارير: **لا يكون TUT أو الراحة "مؤشر أساسي"** — ينتقل إلى قسم "تفاصيل إضافية".
- **Progression engine** لا يُعدّل الراحة تلقائياً كمحور تقدم؛ يركز على load / volume / reps.

---

### 5. ربط `formScore` بـ ROM كتنبيه مدعوم بالدليل

**الدليل:** مدى الحركة الكامل (full ROM) يحسّن القوة (2 reviews, QoE 50%).

**الوضع الحالي:** `avgRom` يُحسب من `TrackedJoint.upRange/downRange`؛ `formScore` يعكس جودة الأداء. لكن لا رسالة تربط ROM المنخفض بنتيجة علمية.

**المطلوب:**

- **Android** — `FeedbackMessages` / `PostTrainingReport`: عند `avgRom < threshold` (مثلاً <80% من ROM المستهدف) → رسالة: "مدى حركة أقصر من المثالي — الأبحاث تظهر أن المدى الكامل يحسّن القوة".
- **Backend** — `ExerciseProgressionProfile`: لا ترقية (promotion) إذا `avgRom` أقل من `targetROM`.

---

## ثانياً: إضافات جديدة مبنية على الدراسة

### 6. إضافة حقل RPE / RIR

**الدليل:** طرق مثل RPE و RIR تُترجم عبر أنماط RT مختلفة (حر، شريط مرن، وزن جسم) — وتدعم مبدأ "جهد كافٍ بدون فشل" (مراجع 189, 190, 197).

**الوضع الحالي:** لا يوجد أي حقل RPE أو RIR في المشروع.

**المطلوب:**

- **Prisma** — إضافة `perceivedExertion Int?` (0–10) و/أو `repsInReserve Int?` (0–5) على `SessionMetrics` أو `RepMetrics`.
- **Android** — `SessionUpload` + UI: بعد إنهاء كل مجموعة → bottom sheet سريع "كم تكرار كنت تقدر تكمل؟" (اختياري).
- **Progression engine** — يُستخدم كإدخال ثانوي لقرار الترقية/التراجع: إذا `repsInReserve ≤ 1` لعدة جلسات → لا ترقية (الجهد أقصى فعلاً).

---

### 7. تجميع حجم أسبوعي per muscle group

**الدليل:** التضخم يحتاج ≥10 مجموعات/أسبوع/مجموعة عضلية (5 reviews, QoE 50%؛ dose-response حتى ~18–20 مجموعة).

**الوضع الحالي:** كل `TrainingSession` فيها `sets`، وكل `Exercise` فيه `muscles` (Json). لكن لا يوجد تجميع أسبوعي per muscle.

**المطلوب:**

- **Backend** — endpoint جديد أو توسيع `/mobile/reports`:

```typescript
// Pseudocode
weeklyVolume = groupBy(
  sessions.where(timestamp IN thisWeek),
  exercise.muscles
).map(group => ({
  muscle: group.key,
  totalSets: sum(group.sessions.sets),
  target: 10, // from ACSM
  status: totalSets >= 10 ? 'optimal' : 'below'
}))
```

- **Android** — شاشة/بطاقة في التقارير الأسبوعية: "صدر: 12 مجموعة ✔ | ظهر: 6 مجموعات — أضف 4 مجموعات".

---

### 8. تصنيف هدف المستخدم (Training Goal)

**الدليل:** المتغيرات المثالية تختلف جذرياً حسب الهدف — القوة تحتاج حمل عالٍ ومجموعات أقل؛ التضخم يحتاج حجم أعلى والحمل أقل أهمية؛ القدرة تحتاج سرعة وحمل معتدل (Table 6 بالكامل).

**الوضع الحالي:** `prescriptionService.recommend` يصنف حسب أولوية جسدية (SAFETY → WEAKNESS) لكن ليس حسب **هدف تدريبي** يختاره المستخدم.

**المطلوب:**

- **Prisma** — حقل `trainingGoal` على `User` أو `ActivePlan`:

```
enum TrainingGoal {
  STRENGTH
  HYPERTROPHY
  POWER
  GENERAL_HEALTH
  FUNCTIONAL_FITNESS
}
```

- **Onboarding / Settings** — سؤال "ما هدفك الأساسي؟"
- **Prescription + Progression** — الهدف يؤثر على:
  - اختيار البرنامج (`prescriptionService`)
  - عتبات الترقية (`ExerciseProgressionProfile` — أولوية load vs volume)
  - التنبيهات (مثلاً "لهدف التضخم: ارفع عدد المجموعات" vs "لهدف القوة: ارفع الحمل")

---

### 9. توصية ترتيب التمارين داخل الجلسة

**الدليل:** التمارين في **بداية الجلسة** تُنتج قوة أعلى (4 reviews, QoE 88% — من أعلى درجات الجودة في الورقة).

**الوضع الحالي:** `ProgramSessionItem.sortOrder` يتحكم في الترتيب، لكن لا توجد توصية أو فرز تلقائي.

**المطلوب:**

- **Backend** — عند بناء `ProgramSession`: فرز تلقائي يضع تمارين `ExerciseArchetype: weighted_strength` أو التمارين ذات الحمل الأعلى **أولاً** (قابل للتجاوز يدوياً).
- **Android** — في `SessionTrainingEngine` عند عرض تمارين الجلسة: ملاحظة صغيرة "مُرتب لأفضل نتيجة قوة" أو تنبيه إذا عكس المستخدم الترتيب.

---

### 10. تصنيف نية التدريب (Training Intent)

**الدليل:** Power RT (concentric بأقصى سرعة) يحسّن الوظيفة والأداء (Table 4 — ✔ للقدرة + وظيفة مركبة). VBT يحسّن الجري والقفز.

**الوضع الحالي:** `ExerciseArchetype` يميز بين أنماط التمرين. لكن لا يوجد حقل يحدد "هل المطلوب من المستخدم أن يرفع بسرعة أم ببطء".

**المطلوب:**

- **Prisma** — حقل `trainingIntent` على `ProgramSessionItem`:

```
enum TrainingIntent {
  STANDARD       // no specific velocity cue
  POWER          // fast concentric, controlled eccentric
  ECCENTRIC      // slow/heavy eccentric focus
  VELOCITY_BASED // autoregulate by bar speed
}
```

- **Android** — `TrainingEngine`: عند `intent = POWER` → تنبيه "ارفع بأسرع ما يمكن" + تتبع `velocity` كمؤشر أساسي بدل `formScore`.
- **Android** — عند `intent = ECCENTRIC` → تنبيه "تحكم في النزول" + إبراز `eccentricMs` في التقرير.

---

## ثالثاً: ما يمكن تبسيطه (الدراسة توفّر جهد تطوير)

### 11. لا حاجة لمحرك Periodization معقد

**الدليل:** Periodization لا يتفوق بشكل حاسم على nonperiodized عند وجود progressive overload مناسب (Table 4 — ? للقوة، ✖ للتضخم).

**التوصية:** محرك التدرج V2 الحالي (`progression.service.ts`) **كافٍ**. لا تستثمروا في بناء macrocycle/block periodization engine الآن.

---

### 12. لا حاجة لتعقيد الراحة أو بنية المجموعات

**الدليل:** inter-set rest لا يؤثر على القوة أو التضخم (✖). set structure (cluster, drop, complex) لا يؤثر بشكل متسق (✖/? في أغلب المخرجات).

**التوصية:** أبقوا `restBetweenSetsMs` كحقل **تكوين** لكن لا تبنوا خوارزمية تعديل تلقائي للراحة. ركزوا موارد التطوير على النقاط 1–10.

---

### 13. لا حاجة لقلق من نوع الجهاز

**الدليل:** Machine vs free-weight **لا يؤثر** على القوة (✖) ولا يمكن تحديد أثره على التضخم (?).

**التوصية:** حقل `equipment` على `Exercise` كافٍ لأغراض العرض/الفلترة، لكن لا يحتاج أن يكون متغير في التدرج أو الوصفة.

---

## ملخص الأولويات

| # | البند | الجهد | الأثر | يعتمد على |
|---|-------|-------|-------|-----------|
| 1 | تفعيل `intensityPercentage` | منخفض | عالٍ جداً | `est1RM` موجود |
| 2 | `velocityLoss` → RIR تقديري + تنبيه | متوسط | عالٍ | `velocity` موجود |
| 3 | تجميع Volume أسبوعي per muscle | متوسط | عالٍ | `sets` + `muscles` موجود |
| 6 | إضافة RPE/RIR كحقل | متوسط | عالٍ | Prisma migration + UI |
| 8 | `trainingGoal` على المستخدم | متوسط | عالٍ | Onboarding + Prescription |
| 5 | ربط ROM بتنبيه علمي | منخفض | متوسط | `avgRom` موجود |
| 9 | ترتيب تمارين تلقائي | منخفض | متوسط | `sortOrder` موجود |
| 3 | إبراز eccentric من `tempo` | منخفض | متوسط | `tempo` موجود |
| 10 | `trainingIntent` enum | متوسط | متوسط | Prisma migration |
| 4 | تخفيف TUT/rest في التقارير | منخفض | منخفض | UI فقط |
| 11–13 | عدم بناء periodization/rest/equipment engines | **توفير** | — | — |
