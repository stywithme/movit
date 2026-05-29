| | |
|---|---|
| **Status** | `ROADMAP` |
| **SSOT for** | Metrics improvement backlog (not as-built) |
| **As-built** | [Metrics-As-Built.md](Metrics-As-Built.md) |
| **Verified** | 2026-05-29 |

# Metrics — Improvement Roadmap (علمي + backlog)

> **للوضع الفعلي في الكود:** [Metrics-As-Built.md](Metrics-As-Built.md)  
> **للتعريفات التفصيلية:** [Metrics-Complete-Reference.md](Metrics-Complete-Reference.md)

بناءً على المراجعة العلمية لمقاييس الأداء البدني + تحليل المشروع. **لا تُستخدم كمواصفة تنفيذ** دون التحقق من As-Built.

---

## Implementation status (vs code, 2026-05-29)

| Recommendation | Status |
|----------------|--------|
| Trunk stability (`calculateTrunkStability`) | **IMPLEMENTED** |
| Hip stability fallback | **IMPLEMENTED** |
| Bilateral LSI ROM (`calculateBilateralRomSymmetry`) | **IMPLEMENTED** |
| Velocity loss | **IMPLEMENTED** |
| TUT = Σ rep durations | **IMPLEMENTED** |
| Tempo consistency | **IMPLEMENTED** |
| PositionCheck-based Safety alignment | **IMPLEMENTED** (V2 builder) |
| V2 composite Safety/Control/Overall | **IMPLEMENTED** (data layer) |
| ROM Achievement % vs target pose | **PLANNED** |
| CoM-based stability | **PLANNED** |
| Unify Alignment + Form Score (single SSOT) | **PLANNED** |
| Movement smoothness (jerk) | **PLANNED** |
| Full V2 report UI | **PARTIAL** |

التفاصيل والمسارات: [Metrics-As-Built.md](Metrics-As-Built.md).

---

## 1. القيد التقني الأساسي

قبل أي شيء، نظامنا يملك **كاميرا فقط** (Pose Estimation عبر MediaPipe). هذا يعني:

| متاح لنا | غير متاح |
|-----------|----------|
| زوايا المفاصل (2D + 3D) | القوة (Force / GRF) |
| إحداثيات المعالم (x, y, z) | EMG (تنشيط العضلات) |
| الطوابع الزمنية بدقة الإطار | HR / HRV (معدل ضربات القلب) |
| مراحل الحركة (phases) | VO2 (استهلاك الأكسجين) |
| حالات المفاصل (states) | Lactate (اللاكتات) |
| الوزن المستخدم (إدخال يدوي) | RPE (الجهد المدرك) |

هذا القيد يحدد ما يمكننا قياسه **بمصداقية**. أي مقياس يتطلب حساسات غير متوفرة يجب أن يُستبعد أو يُعلن كتقدير تقريبي.

---

## 2. تقييم المقاييس الحالية مقابل المعيار العلمي

### المقاييس ذات الأساس العلمي القوي

| المقياس | الوضع الحالي | الحكم العلمي | التوصية |
|---------|-------------|-------------|---------|
| **ROM** | يحسب الفرق الخام (max-min) بدون سياق | مقياس سريري وبحثي راسخ. لكن الرقم بدون مقارنة بالنطاق المستهدف لا معنى له | **إصلاح**: ربطه بالنطاق المستهدف من config التمرين → نسبة وصول % |
| **1RM** | صيغة Epley — سليمة | موثوقية "ممتازة–جيدة" كاختبار قوة قصوى. الصيغة التقديرية مقبولة علميا (لأقل من 10 عدات) | **إبقاء كما هو** |
| **Volume** | يجمع الأوزان فقط | متغير تدريب أساسي. لكن volume = sets × reps × load وليس مجموع الأوزان | **إصلاح**: تصحيح الحساب ليكون عدد العدات × الوزن |
| **Tempo** | يقيس مدة المراحل بدقة | معترف به كمتغير برمجة. القياس الفعلي أفضل من التعليمات الشفوية (وهذا ما نفعله) | **إبقاء + تحسين**: إضافة مقارنة بالإيقاع المثالي |
| **Velocity** | سرعة زاوية (°/ث) | السرعة الخطية (m/s) هي المعيار في VBT. لكن السرعة الزاوية مقبولة كبديل عند غياب LPT/IMU | **إبقاء مع تنبيه**: وصفه بـ "Angular Velocity" وليس مقارنته بمعايير VBT |

### المقاييس التي تحتاج إعادة تعريف جذري

| المقياس | المشكلة | الحكم العلمي | التوصية |
|---------|---------|-------------|---------|
| **Stability** | يقيس تباين زاوية الورك (وهو يتغير طبيعيا أثناء الحركة) | الثبات يُقاس بـ COP/TTS عبر منصة قوة. بدون منصة قوة، البديل الأقرب هو تتبع **موقع** مركز الكتلة (x, y) وليس **زاوية** المفصل | **إعادة بناء**: استخدام إحداثيات المعالم لحساب انحراف مركز الجذع عن محور ثابت |
| **Symmetry** | مقارنة إطار بإطار. النطاق واسع جدا (1800°) | LSI المعتمد علميا يقارن ذروة أو متوسط الأداء بين الطرفين، وليس كل إطار | **إعادة بناء**: مقارنة (ذروة زاوية يسار vs ذروة زاوية يمين) + (ROM يسار vs ROM يمين). النطاق يكون max 30° لا 180°. **يجب مراعاة**: تمارين Bilateral Alternating (تبديل يمين/شمال كل عدة) — يتم حساب التماثل من بيانات العدات المنفصلة لكل جانب (leftReps vs rightReps) وليس من نفس الإطار |
| **TUT** | يستخدم مدة الجلسة الكاملة | TUT = مجموع أوقات التكرارات تحت الحمل فقط. ليس مدة الجلسة | **إصلاح**: TUT = Σ (مدة كل rep) — متاح بالفعل من phase timings |
| **Safety Score** | عدد DANGER ÷ إجمالي العدات | "Safety Score" ليس مقياسا علميا معتمدا. هو درجة مركبة تحتاج تعريفا واضحا. الحالي مبسط جدا | **إعادة بناء**: دمج (Position Check Alignment + مدة DANGER + Trunk Stability) بأوزان واضحة |
| **Alignment Accuracy** | يعتمد فقط على JointState (كل المفاصل أو لا شيء) ولا يستخدم بيانات PositionValidator | النظام يملك PositionValidator منفصل ينتج 3 مستويات (TIP/WARNING/ERROR) بناء على مقارنات مكانية حقيقية (knee-over-toe, alignment) — هذه البيانات أدق وأغنى من JointState فقط | **إعادة بناء**: Alignment يُحسب من بيانات PositionValidator (عدد ERROR + WARNING frames مقارنة بالإجمالي) ويُدمج في Safety Score. البيانات الحالية متاحة لكن غير مستغلة |
| **Control Score** | مكافآت ثابتة (+10, -20) | "Control Score" ليس مقياسا معتمدا. يمكن ترجمته لمقاييس معترفة: سلاسة الحركة، اتساق الإيقاع | **إعادة بناء**: حسابه من (تباين الإيقاع بين العدات + Fatigue detection + اتساق ROM) |

### المقاييس المركبة (Composite Scores)

| المقياس | الحكم العلمي | التوصية |
|---------|-------------|---------|
| **Form Score** | درجات الشكل المركبة ليست "مقاييس علمية معتمدة" — هي مؤشرات خوارزمية. صالحيتها تعتمد على تعريف واضح ونشر الأوزان والميزات | **إبقاء** كمؤشر إرشادي مع توثيق الأوزان. لا يُعلن كمقياس علمي |
| **Overall Quality** | نفس الملاحظة. أي composite score يحتاج تحقق خارجي ليصبح "مقياس أداء" | **إبقاء** كملخص. قيمته تعتمد على صحة المكونات |
| **Rep Score** | أفضل مقياس حالي. المتوسط المرجح منطقي ومتسق | **إبقاء كما هو** |

---

## 3. المقاييس المكررة أو المضللة — يجب إزالتها أو دمجها

| المقياس | السبب | الإجراء |
|---------|-------|---------|
| **Stability (في MetricsCalculator)** يقيس تباين زاوية الورك | و **Stability (في PerformanceMetricsBuilder)** يقيس تباين التوقيت | **دمج في مقياسين مختلفين بأسماء مختلفة**: "Body Stability" (من إحداثيات المعالم) و "Tempo Consistency" (من تباين التوقيت) |
| **Alignment Accuracy** و **Form Score** | كلاهما يقيس "هل الشكل صحيح". Alignment يحسب نسبة الإطارات الجيدة، وForm Score يحسب متوسط حالات المفاصل. التداخل كبير | **دمج**: Alignment يندمج في حساب Form Score كأحد مكوناته. لا يُعرض كمقياس مستقل |
| **Form Consistency (من DTW)** و **Form Consistency (من StdDev الدرجات)** | طريقتان مختلفتان بنفس الاسم ونتائج مختلفة | **توحيد**: استخدام DTW إذا توفرت إطارات، StdDev كبديل. لكن يُعلن أيهما استُخدم |

---

## 4. مقاييس مهمة مفقودة يمكن إضافتها تقنيا

### أولوية عالية (لها أساس علمي + ممكنة تقنيا)

#### 4.1 Velocity Loss (فقدان السرعة داخل المجموعة)

**لماذا مهم**: مؤشر تعب عصبي-عضلي داخل المجموعة. له أدلة قوية في أدبيات VBT. أدق بكثير من Fatigue Index الحالي (الذي يقارن درجات وليس سرعات).

**كيف نقيسه**: لدينا السرعة الزاوية لكل عدة بالفعل.

```
VL% = (V_best - V_current) / V_best × 100
```

حيث V_best = أعلى سرعة زاوية في المجموعة (عادة العدة الأولى أو الثانية).

**قيمته**: يكمل Fatigue Index. VL يكشف التعب **الميكانيكي** (هل الحركة بطأت؟)، بينما Fatigue Index يكشف التعب **النوعي** (هل الشكل انهار؟).

---

#### 4.2 Body Stability الحقيقي (ثبات مركز الكتلة)

**لماذا مهم**: قياس الثبات الفعلي. البحث العلمي يستخدم COP عبر منصة قوة. البديل المتاح لنا: تتبع موقع مركز الجذع.

**كيف نقيسه**: إحداثيات المعالم متاحة في smoothedLandmarks.

```
CoM_x = (left_hip.x + right_hip.x + left_shoulder.x + right_shoulder.x) / 4
CoM_y = (left_hip.y + right_hip.y + left_shoulder.y + right_shoulder.y) / 4

Stability = 1 - (StdDev(CoM_x) / threshold)
```

حيث threshold يُحدد حسب التمرين (squat يسمح بحركة عمودية لكن ليس أفقية).

**تنبيه**: هذا ليس COP الحقيقي. يجب وصفه بـ "Trunk Stability" وليس ادعاء أنه مكافئ لمنصة القوة.

---

#### 4.3 ROM Achievement (نسبة الوصول للمدى المستهدف)

**لماذا مهم**: ROM كرقم خام لا معنى له بدون سياق. 60° ROM في squat ≠ 60° في bicep curl.

**كيف نقيسه**: نطاقات المفاصل المستهدفة موجودة في exerciseConfig (upPose/downPose).

```
Target ROM = |downPose.center - upPose.center|
Achieved ROM = max_angle - min_angle
ROM Achievement % = (Achieved ROM / Target ROM) × 100
```

**قيمته**: يحول ROM من رقم مجرد إلى نسبة مئوية مفهومة. "وصلت 85% من المدى المطلوب" أوضح بكثير من "ROM = 72°".

---

#### 4.4 Tempo Consistency (اتساق الإيقاع عبر العدات)

**لماذا مهم**: يكشف هل المتدرب يتحكم في الحركة أم يعتمد على القصور الذاتي.

**كيف نقيسه**: لدينا phase timings لكل عدة.

```
Eccentric_times = [t1, t2, t3, ...]
CV_eccentric = StdDev(times) / Mean(times)

Tempo Consistency = 1 - CV_eccentric  (مرجّح لكل مرحلة)
```

**قيمته**: يحل محل "Stability" المضلل في بطاقة Safety. ويُعطي Control Score أساسا حقيقيا بدل المكافآت الثابتة.

---

### أولوية متوسطة (مفيدة لكن ليست أساسية)

#### 4.5 Movement Smoothness (سلاسة الحركة)

**لماذا مهم**: مقياس معترف به في التحكم الحركي. يكشف الرجفة/التردد في الحركة.

**كيف نقيسه**: من تغير السرعة الزاوية بين الإطارات (jerk = مشتقة التسارع).

```
smoothness = -log(Σ|jerk|² × dt / velocity_peak²)
```

**القيد**: حساس لمعدل الإطارات وللضوضاء. يحتاج ترشيح إضافي.

#### 4.6 ROM Symmetry (تماثل المدى الحركي)

**لماذا مهم**: مقارنة ذروة الزوايا بين الطرفين (وليس إطار بإطار). هذا أقرب لـ LSI المعتمد علميا.

**كيف نقيسه**:

```
LSI_ROM = min(ROM_left, ROM_right) / max(ROM_left, ROM_right) × 100
```

**قيمته**: يكشف عدم التوازن الحقيقي بين الجانبين. أدق بكثير من Symmetry الحالي.

---

## 5. الإطار النهائي المقترح

### المقاييس الأساسية (تُحسب دائما)

| # | المقياس | النوع | المصدر | جديد/موجود |
|---|---------|-------|--------|------------|
| 1 | **Rep Score** | درجة | JointStates weighted avg | موجود ✅ |
| 2 | **ROM Achievement %** | كينماتيكي | (max-min) / target ROM | **إصلاح** |
| 3 | **Tempo** (Ecc/Iso/Con) | زمني | Phase timings | موجود ✅ |
| 4 | **TUT** (فعلي) | زمني | Σ rep durations | **إصلاح** |
| 5 | **Fatigue Index** | جودة | Score drop detection | موجود ✅ |
| 6 | **Velocity Loss %** | تعب | V_best vs V_current | **جديد** |

### مقاييس الجودة (تُحسب بعد الجلسة)

| # | المقياس | النوع | المصدر | جديد/موجود |
|---|---------|-------|--------|------------|
| 7 | **Form Consistency** | جودة | DTW أو StdDev | موجود ✅ |
| 8 | **Tempo Consistency** | جودة | CV of phase timings | **جديد** (يحل محل "Stability" المضلل) |
| 9 | **Trunk Stability** | جودة | CoM position variance | **جديد** (يحل محل Stability القديم) |
| 10 | **ROM Symmetry** (bilateral فقط) | كينماتيكي | LSI from peak angles | **إعادة بناء** |

### مقاييس الحمل (عند إدخال الوزن)

| # | المقياس | النوع | المصدر | جديد/موجود |
|---|---------|-------|--------|------------|
| 11 | **Volume** (reps × weight) | حمل | User input + rep count | **إصلاح** |
| 12 | **Est. 1RM** (Epley) | حمل | Weight + reps | موجود ✅ |

### الدرجات المركبة (ملخصات عليا)

| # | المقياس | يتكون من | جديد/موجود |
|---|---------|---------|------------|
| 13 | **Form Score** | Rep Score avg + ROM Achievement | **تحسين** |
| 14 | **Safety Score** | Alignment (مدمج) + Trunk Stability + DANGER duration | **إعادة بناء** |
| 15 | **Control Score** | Tempo Consistency + VL% + Form Consistency | **إعادة بناء** |
| 16 | **Overall Quality** | Form 40% + Safety 35% + Control 25% | **تحسين** (المكونات تتحسن) |

### مقاييس أُزيلت أو أُدمجت

| المقياس القديم | السبب | أين ذهب |
|---------------|-------|--------|
| **Stability** (تباين زاوية الورك) | يقيس مقدار الحركة وليس الثبات | استُبدل بـ Trunk Stability (من إحداثيات المعالم) |
| **Symmetry** (إطار بإطار) | مضلل — فرق 10° يبدو مثاليا | استُبدل بـ ROM Symmetry (LSI من الذروات) |
| **Alignment Accuracy** (كمقياس مستقل) | يتداخل مع Form Score | أُدمج داخل حساب Form Score و Safety Score |
| **TUT** (مدة الجلسة) | ليس TUT الحقيقي | أُصلح ليكون مجموع مدد العدات الفعلية |
| **Velocity** (السرعة كرقم مطلق) | رقم بلا سياق | يُعرض مع Velocity Loss % الذي يعطيه معنى |

---

## 6. حالة كل مقياس — خريطة العمل

```
المقاييس الـ 16 النهائية:

موجود ولا يحتاج تعديل (3):
  ├── Rep Score ✅
  ├── Est. 1RM ✅  
  └── Fatigue Index ✅

يحتاج إصلاح بسيط (3):
  ├── ROM → ROM Achievement %
  ├── TUT → مجموع مدد العدات
  └── Volume → reps × weight

يحتاج إعادة بناء (6):
  ├── Symmetry → ROM Symmetry (LSI)
  ├── Stability → Trunk Stability (CoM position)
  ├── Safety Score → (Alignment + Stability + DANGER duration)
  ├── Control Score → (Tempo Consistency + VL% + Form Consistency)
  ├── Form Score → (Rep Score avg + ROM Achievement)
  └── Overall Quality → (مكونات محسّنة)

جديد تماما (2):
  ├── Velocity Loss %
  └── Tempo Consistency

مُعاد بناؤه بالكامل (1):
  └── Alignment Accuracy → يستخدم بيانات PositionValidator (ERROR/WARNING/TIP) بدلاً من JointState فقط

موجود بلا تغيير في العرض (2):
  ├── Tempo (Ecc/Iso/Con)
  └── Form Consistency
```

---

## 7. ملاحظات من البحث العلمي يجب مراعاتها

1. **الدرجات المركبة (Form/Safety/Control/Overall) ليست مقاييس علمية**. هي مؤشرات خوارزمية. يجب عدم تقديمها كأنها "مقاييس أداء معتمدة". الوصف الأمين: "مؤشر تقديري بناءً على تحليل الحركة".

2. **لا توجد عتبات عالمية لمعظم المقاييس**. الأفضل هو مقارنة الشخص بنفسه عبر الزمن (baseline شخصي) بدلا من ادعاء أن "90% = ممتاز" لكل شخص.

3. **Velocity Loss أدق من Fatigue Index** كمؤشر تعب. لكن الاثنين يكملان بعضهما: VL يكشف التعب الميكانيكي، FI يكشف انهيار الشكل.

4. **ROM بدون توحيد وضعية البداية** قد يختلف بين الجلسات. يجب أن تكون مرحلة START ثابتة لكل تمرين.

5. **Symmetry الحالي حساس جدا لتأخر بسيط بين الجانبين**. مقارنة الذروات تتجاهل هذا التأخر الطبيعي.

6. **سلاسة الحركة (Smoothness)** مقياس قوي علميا لكنه **حساس جدا** لمعدل الإطارات والضوضاء. يُضاف لاحقا بعد التحقق من استقراره مع بيانات MediaPipe.

---

## 8. ملخص التغييرات

| الفئة | العدد | التفاصيل |
|-------|-------|---------|
| لا يتغير | 3 | Rep Score, 1RM, Fatigue Index |
| إصلاح بسيط | 3 | ROM, TUT (sum of rep durations), Volume (counted reps only) |
| إعادة بناء | 7 | Symmetry (LSI), Stability (trunk/spine), Safety, Control, Form Score, Alignment (PositionCheck), Overall |
| إضافة جديدة | 2 | Velocity Loss %, Tempo Consistency |

**ملاحظات التنفيذ الفعلي:**

- **Alignment Accuracy**: أُعيد بناؤه ليستخدم بيانات PositionValidator بمستوياتها الثلاثة (ERROR: -15/rep, WARNING: -8/rep, TIP: -3/rep) + JointState violations (-10/rep). يُعرض ضمن بطاقة Safety.
- **Symmetry**: يستخدم LSI (Limb Symmetry Index) للتمارين ثنائية الجانب (bilateral alternating). يقارن متوسط أداء عدات الجانب الأيمن vs الأيسر.
- **Stability**: يفضل تباين زاوية العمود الفقري (spine) إن كان متتبعاً، ويعود لتباين الورك كبديل.
- **Safety Score**: صيغة مرجحة: 40% Position Alignment + 30% DANGER-free ratio + 30% Trunk Stability.
- **Control Score**: صيغة مرجحة: 30% Tempo Consistency + 25% (100-VL%) + 25% Form Consistency + 20% Fatigue penalty.
- **Tempo**: يستخدم بيانات المراحل الحقيقية (eccentric/isometric/concentric) من SessionMetrics بدلاً من التقدير.
- **TUT**: يستخدم مجموع مدد العدات (not session duration).
| **الإجمالي النهائي** | **16 مقياس** | بدلا من 15 — بنفس الحجم تقريبا لكن بمحتوى أدق |
