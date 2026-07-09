| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | Post-training report — as-built + target UX |
| **Code** | `MovitPostTrainingReport.kt`, `ReportQualityScoring.kt`, `feature/reports/ReportDetailScreen.kt`, `POST /mobile/workout-executions` |
| **Metrics pipeline** | [Metrics-As-Built.md](../Metrics/Metrics-As-Built.md) |
| **Verified** | 2026-06-22 |

# تقرير ما بعد التمرين — المراجعة والرؤية الجديدة

> **As-built (كود اليوم):** الأقسام 0–1 أدناه. **الرؤية (7 شاشات):** من §4 فصاعداً — `ROADMAP` UI، البيانات جزئياً جاهزة.

---

## 0. As-built — ما يعمل في KMP اليوم

| Layer | Path | الحالة |
|-------|------|--------|
| Report builder | `core/training-engine/.../report/MovitPostTrainingReportBuilderV2.kt` | ✓ timeline, errors, tips, `overallQuality` |
| Composite scores | `core/training-engine/.../report/ReportQualityScoring.kt` | ✓ Form / Safety / Control math |
| Report UI | `feature/reports/ReportDetailScreen.kt` | ✓ 4 tabs: Overview · Form · Fatigue · Tips |
| UI mapping | `feature/reports/MovitSessionReportUiMapper.kt` | ✓ maps domain report → `ReportDetailUi` |
| Upload | `core/data/.../WorkoutUploadMapper.kt` → `POST /mobile/workout-executions` | ✓ metrics except VL / tempo consistency |
| Backend | `WorkoutExecution` + `WorkoutExecutionMetrics` + `RepMetrics` | ✓ see [Metrics-As-Built](../Metrics/Metrics-As-Built.md) |

**ما يُعرض فعلاً في `ReportDetailScreen`:**

| Tab | محتوى | مقاييس V2 غير معروضة |
|-----|--------|---------------------|
| Overview | درجة واحدة (form أو overall)، sets/reps/duration، insight، hero | بطاقات Form/Safety/Control، QuickInsight، ROM، tempo، VL |
| Form | تحليل مفاصل، best/worst rep، frame evidence | ROM، symmetry، form consistency |
| Fatigue | drop-off bar، form-by-set chart | `fatigue_index`، `velocity_loss`، TUT، tempo |
| Tips | نصائح + export | weight، volume، 1RM |

**Legacy:** `ReportPagerActivity` (Android ViewPager) — **استُبدل** بـ Compose `ReportDetailScreen` في KMP.

---

## 1. الوضع الفني الحالي

### V1 vs V2 — أيهما يعمل؟

| البعد | As-built (KMP UI) | V2 data (engine) |
|-------|-------------------|------------------|
| **واجهة المستخدم** | `ReportDetailScreen`: 4 tabs scroll (Overview, Form, Fatigue, Tips) | — |
| **البيانات** | `summary`, `repTimeline`, `errorAnalysis`, best/worst, tips, hero frames | `overallQuality` (form/safety/control/overall), execution metrics in report payload |
| **البطاقات الثلاث** | **غير معروضة** | محسوبة في `ReportQualityScoring` + `MovitOverallQualityScore` |
| **QuickInsight** | insight بسيط من mapper | CELEBRATION / FOCUS / DANGER logic في builder — **غير موصول للـ UI** |
| **Overall Quality** | يُستخدم كـ hero score عند التوفر | محسوب — **لا يُعرض كبطاقات فرعية** |
| **Metric filter** | لا | `ReportMetricsConfig.shouldShow()` — **غير موصول** |

**الخلاصة:** محرك V2 و`ReportQualityScoring` **يعملان**. `ReportDetailScreen` يعرض **subset** من V1 (form-centric). بطاقات Form/Safety/Control ومقاييس kinematic/load **محسوبة ومخزنة جزئياً** لكن **لا تظهر** — انظر [Metrics-As-Built](../Metrics/Metrics-As-Built.md).

---

### حالة حفظ التقارير والباك إند

| المسار | الحالة |
|--------|--------|
| **حفظ محلي** | `ReportStorage` يحفظ `PostTrainingReport` كـ JSON — يعمل |
| **إرسال تمرين مفرد** | `POST /mobile/workout-executions` → `WorkoutExecution` + `WorkoutExecutionMetrics` + `RepMetrics[]` — يعمل |
| **إرسال جلسة متعددة** | `POST /mobile/planned-workouts/:id/complete` → `PlannedWorkoutReport` بـ JSON report كامل — يعمل |
| **استرجاع التقارير** | `GET /mobile/reports/metrics?scope=...` — يعمل (program/week/day/plannedWorkout/exercise) |
| **مزامنة** | `GET /mobile/sync` يُرجع التقارير المكتملة — يعمل |

**الخلاصة:** التقارير **تُحفظ وتُرسل للباك إند بالفعل**. البنية التحتية كاملة لكلا النوعين (تمرين مفرد + جلسة متعددة).

---

### هيكل التدريب: Exercise vs Planned Workout

```
تمرين مفرد (Standalone Exercise)
└── TrainingEngine → WorkoutRunSummary → PostTrainingReport
    └── POST /mobile/workout-executions → WorkoutExecution + Metrics

تمرين مخطط متعدد (Planned Workout)
├── WorkoutTrainingEngine يُنسق عدة تمارين
│   ├── Exercise 1 → PostTrainingReport (1)
│   ├── Exercise 2 → PostTrainingReport (2)
│   └── Exercise 3 → PostTrainingReport (3)
├── WorkoutReport (ملخص كلي)
└── POST /mobile/planned-workouts/:id/complete → PlannedWorkoutReport
```

حالياً:
- **تمرين مفرد / بعد التمرين:** `ReportDetailScreen` (KMP Compose)
- **تمرين مخطط متعدد:** قائمة تمارين في reports flow → `ReportDetailScreen` لكل تمرين

---

### مسألة الإيقاع المستهدف (Target Tempo)

**الوضع الحالي:** لا يوجد حقل `targetTempo` في إعدادات التمرين. النظام يقيس الإيقاع الفعلي فقط بدون مقارنة بمستهدف.

**الخيارات:**

| الخيار | المزايا | العيوب |
|--------|---------|--------|
| **A) إضافة `targetTempo` في ExerciseConfig** | الأدمن يحدده لكل تمرين. دقيق ومخصص. | يحتاج تعديل schema + Admin UI + JSON builder |
| **B) اشتقاقه من نوع التمرين** | تلقائي بالكامل. | قواعد عامة قد لا تناسب كل تمرين |
| **C) يحدده المتدرب كهدف شخصي** | مخصص لكل متدرب. | يحتاج UI إضافي + أغلب المتدربين لن يفهموا ما يُدخلون |

**التوصية:** الخيار A هو الأفضل. يُضاف حقل `targetTempo: [eccentric, isometric, concentric]` في إعدادات التمرين (اختياري). الأدمن يملأه لمن يريد. إذا كان فارغاً، لا تُعرض مقارنة — فقط الإيقاع الفعلي. هذا يُعطي أقصى مرونة بأقل تعقيد.

---

## 2. ما هو الجيد في الوضع الحالي

1. **التجربة البصرية الغامرة** — صورة Hero كاملة الشاشة مع تدرجات لونية. تعزيز بصري عاطفي قوي.
2. **QuickInsight المتدرج** — أولوية ذكية: DANGER → مشكلة → احتفال.
3. **المقارنة البصرية** (Best vs Worst) — أقوى أداة تعلّم بصري.
4. **رسم رحلة العدات** — يكشف نمط التعب بصرياً.
5. **نظام الحالات الخمس** — تمييز دقيق بين مستويات الأداء.
6. **بنية V2 الجاهزة** — كل المقاييس الجديدة محسوبة ومخزنة. فقط العرض يحتاج تحديث.
7. **ثنائية اللغة** — عربي + إنجليزي في كل نص.

---

## 3. ما يحتاج تحسين

| # | المشكلة | الأثر | الأولوية |
|---|---------|-------|---------|
| 1 | صفحة الملخص مكتظة (12+ قطعة معلومات) | المتدرب المتعب يضيع | عالية |
| 2 | البطاقات الثلاث (Form/Safety/Control) غير مرئية رغم أنها جاهزة | أهم ميزة V2 غير مستخدمة | عالية |
| 3 | صفحات العدات الفردية تُضخم الـ Pager (12 عدة = 12 صفحة إضافية) | لا أحد يسحب 16 صفحة | عالية |
| 4 | لا سياق مقارنة (↑↓ مقابل آخر جلسة) | أرقام بلا معنى — سيُعالج لاحقاً في سجل التمارين | متوسطة — مؤجل |
| 5 | الإيقاع بلا مقارنة بالمستهدف | رقم وصفي بلا تقييم | متوسطة |
| 6 | تحليل التعب سطحي (متى لكن ليس ماذا) | البيانات متاحة لكن غير معروضة | متوسطة |
| 7 | النصائح عامة وغير مخصصة | لا تُضيف قيمة حقيقية | متوسطة |
| 8 | لا زر مشاركة | فرصة تسويقية ضائعة | متوسطة |
| 9 | Symmetry لا يُحدد الجانب الأضعف | رقم بلا إرشاد عملي | منخفضة |
| 10 | لا تفصيل مفصلي (Joint-by-Joint) | المتدرب لا يعرف أي مفصل هو المشكلة | منخفضة |

---

## 4. الرؤية الجديدة لهيكل التقرير (`ROADMAP`)

> الأقسام 4–8 تصف UX مستهدفاً — **ليس** as-built. التنفيذ الحالي: §0.

### 4.1 الهيكل العام: Planned Workout vs Exercise

```
┌─────────────────────────────────────────────────┐
│              جلسة متعددة (Planned Workout)               │
│                                                  │
│  صفحة الجلسة (Planned Workout Summary)                   │
│  ┌──────────────────────────────────────────┐    │
│  │ اسم البرنامج + اليوم                      │    │
│  │ Overall Score + عدد التمارين + المدة الكلية │    │
│  │ ملخص كل تمرين (اسم + درجة + عدات)         │    │
│  └──────────────────────────────────────────┘    │
│                                                  │
│  ↓ Scroll Down                                   │
│                                                  │
│  ┌── Exercise 1 ─────── Scroll Left ──────┐     │
│  │ Screen 1 → 2 → 3 → 4 → 5 → 6 → 7     │     │
│  └────────────────────────────────────────┘     │
│                                                  │
│  ┌── Exercise 2 ─────── Scroll Left ──────┐     │
│  │ Screen 1 → 2 → 3 → 4 → 5 → 6 → 7     │     │
│  └────────────────────────────────────────┘     │
│                                                  │
│  ┌── Exercise 3 ─────── Scroll Left ──────┐     │
│  │ Screen 1 → 2 → 3 → 4 → 5 → 6 → 7     │     │
│  └────────────────────────────────────────┘     │
└─────────────────────────────────────────────────┘
```

**تمرين مفرد:** يدخل مباشرة في شاشات التمرين (بدون صفحة Planned Workout).

**Navigation:**
- **عمودي (Scroll Down):** التنقل بين التمارين
- **أفقي (Scroll Left):** التنقل بين شاشات التمرين الواحد

---

### 4.2 شاشات التمرين الواحد (7 شاشات)

```
← Scroll Left ←

Screen 1     Screen 2      Screen 3      Screen 4-6    Screen 7
Hero         Performance   Best/Worst    Card Details  Tips & Export
(Share)      Overview      Comparison    (3 screens)   
```

---

### الشاشة 1: Hero — الشاشة التحفيزية

```
┌──────────────────────────────────┐
│                                  │
│     [صورة Hero — أفضل عدة]       │
│     (Full Screen Background)     │
│                                  │
│                                  │
│                                  │
│  ┌────────────────────────────┐  │
│  │                            │  │
│  │     ██  85%  ██            │  │
│  │    Overall Quality         │  │
│  │                            │  │
│  │   12 عدة    ·    02:30    │  │
│  │                            │  │
│  └────────────────────────────┘  │
│                                  │
│  💬 "أداء رائع! حافظت على شكل    │
│      ممتاز في 10 من 12 عدة"     │
│                                  │
│  [📤 Share]          ← Swipe    │
│                                  │
└──────────────────────────────────┘
```

**المبادئ:**
- **3 أرقام فقط:** Overall Quality (كبير ومركزي) + عدد العدات + المدة
- التمارين الموزونة: يُضاف الوزن كرقم رابع صغير
- **QuickInsight** — رسالة واحدة ذكية (من V2)
- **زر Share** — يُنتج صورة جميلة (Hero frame + Score + Exercise name) لمشاركتها
- **لا مقاييس ثانوية** — هذه شاشة "الإنطباع الأول" فقط

---

### الشاشة 2: Performance Overview — نظرة شاملة

```
┌──────────────────────────────────┐
│                                  │
│  ┌────────────────────────────┐  │
│  │  Reps Journey Chart        │  │
│  │  █ █ █ █ █ █ █ ▓ ▓ ▒ ▒ ▒  │  │
│  │  90 92 88 91 89 87 85 78..│  │
│  │              ⬆ Fatigue #8  │  │
│  └────────────────────────────┘  │
│                                  │
│  ┌────────┐┌────────┐┌────────┐  │
│  │  Form  ││ Safety ││Control │  │
│  │  85%   ││  92%   ││  78%   │  │
│  │  🟢    ││  🟢    ││  🟡    │  │
│  │        ││        ││        │  │
│  │ROM 89% ││Alg 88% ││Tmp 2-1-2│ │
│  │Sym 93% ││Stb 95% ││TUT 45s │  │
│  │FC  91% ││⚠️ 0   ││TC  85% │  │
│  └────────┘└────────┘└────────┘  │
│                                  │
│  Press any card for details →    │
│                                  │
└──────────────────────────────────┘
```

**المبادئ:**
- **النصف العلوي:** رسم الأداء عبر العدات (Reps Journey) — يظهر نمط التعب بصرياً
- **النصف السفلي:** البطاقات الثلاث (Form / Safety / Control) من V2
- كل بطاقة تعرض: الدرجة الإجمالية + أهم 2-3 مقاييس فرعية
- **الألوان تتحدث:** أخضر / أصفر / أحمر — المتدرب يفهم الحالة بلمحة
- الضغط على أي بطاقة يفتح الشاشة التفصيلية الخاصة بها (Screens 4-6)
- المقاييس تظهر فقط حسب `ExerciseConfigSnapshot.shouldShowMetric()`

---

### الشاشة 3: Best vs Worst — المقارنة البصرية

```
┌──────────────────────────────────┐
│ ┌──────────────┬───────────────┐ │
│ │              │               │ │
│ │              │  Best Rep 🏆  │ │
│ │   [صورة      │               │ │
│ │    متحركة    │  Rep #3       │ │
│ │    للعدة]    │  Score: 95%   │ │
│ │              │  ROM: 92°     │ │
│ │              │  Tempo: 3-1-2 │ │
│ │              │               │ │
│ ├──────────────┼───────────────┤ │
│ │              │               │ │
│ │ Worst Rep ⚠️ │   [صورة       │ │
│ │              │    متحركة     │ │
│ │ Rep #11      │    للعدة]     │ │
│ │ Score: 65%   │              │ │
│ │ ROM: 72°     │              │ │
│ │ ركبة يمنى ↓  │              │ │
│ │              │               │ │
│ └──────────────┴───────────────┘ │
│                                  │
│  📊 الفرق: Score -30% · ROM -20°│
│                                  │
└──────────────────────────────────┘
```

**المبادئ:**
- تقسيم **فوق وتحت** مع عكس موضع الصورة (Layout mirrored)
- Best Rep: الصورة يسار + الأرقام يمين
- Worst Rep: الأرقام يسار + الصورة يمين
- **صورة متحركة (GIF/Short clip)** إذا توفرت الإطارات — تُظهر الحركة لا اللقطة الثابتة
- **سطر مقارنة في الأسفل:** يُلخّص الفرق الأساسي بين الأفضل والأسوأ
- الأرقام المعروضة = Score + أهم مقياس متأثر (ROM عادة) + السبب الرئيسي للخطأ

---

### الشاشات 4-6: تفاصيل البطاقات (3 شاشات)

كل بطاقة من الثلاث تحصل على شاشة تفصيلية كاملة.

#### الشاشة 4: Form Details — تفاصيل الشكل

```
┌──────────────────────────────────┐
│                                  │
│  Form Score: 85% 🟢              │
│  ─────────────────────────────   │
│                                  │
│  ┌────────────────────────────┐  │
│  │ ROM              89%  🟢  │  │
│  │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░   │  │
│  │ "مدى حركي جيد"            │  │
│  ├────────────────────────────┤  │
│  │ Symmetry          93%  🟢 │  │
│  │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░   │  │
│  │ "الجانب الأيسر أضعف بـ 7%"│  │
│  │  L: 82° | R: 90°          │  │
│  ├────────────────────────────┤  │
│  │ Form Consistency  91%  🟢 │  │
│  │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░   │  │
│  │ "ثبات ممتاز في الشكل"     │  │
│  └────────────────────────────┘  │
│                                  │
│  💡 العدات 8-12: ROM انخفض 12°  │
│     حاول تقليل العدات للحفاظ    │
│     على المدى الحركي الكامل     │
│                                  │
└──────────────────────────────────┘
```

**ما يُعرض:**
- كل مقياس فرعي في Form بتفصيل: الدرجة + شريط تقدم + نصيحة مخصصة
- **Symmetry يُحدد الجانب:** "Left: 82° | Right: 90°" — ليس مجرد نسبة
- نصيحة واحدة مبنية على البيانات في الأسفل

#### الشاشة 5: Safety Details — تفاصيل الأمان

```
┌──────────────────────────────────┐
│                                  │
│  Safety Score: 92% 🟢            │
│  ─────────────────────────────   │
│                                  │
│  ┌────────────────────────────┐  │
│  │ Alignment         88%  🟢 │  │
│  │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░   │  │
│  │ ⚠️ 2 عدات بها تحذير وضعية │  │
│  ├────────────────────────────┤  │
│  │ Stability         95%  🟢 │  │
│  │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░   │  │
│  │ "ثبات ممتاز للجذع"        │  │
│  ├────────────────────────────┤  │
│  │ Danger Events:     0  ✅  │  │
│  │ "لا أحداث خطيرة"          │  │
│  └────────────────────────────┘  │
│                                  │
│  ┌─ تنبيهات أمان ──────────────┐│
│  │ (إذا وُجدت DangerAlerts)    ││
│  │ 🚨 Rep #5: ركبة يمنى        ││
│  │ الزاوية: 45° | الآمن: 60-90°││
│  │ [صورة اللحظة الخطرة]        ││
│  └─────────────────────────────┘│
│                                  │
└──────────────────────────────────┘
```

**ما يُعرض:**
- مقاييس الأمان الثلاثة بالتفصيل
- **DangerAlerts كاملة** — الصورة + الزاوية + النطاق الآمن + النصيحة
- هذه الشاشة تحل محل عرض DANGER المبعثر في V1

#### الشاشة 6: Control + Fatigue + Load — تفاصيل التحكم والحمل

```
┌──────────────────────────────────┐
│                                  │
│  Control Score: 78% 🟡           │
│  ─────────────────────────────   │
│                                  │
│  ┌────────────────────────────┐  │
│  │ Tempo          2.1-0.8-1.9│  │
│  │ (Target: 3-1-2 ⚠️ أسرع)   │  │
│  │ "النزول أسرع من المستهدف"  │  │
│  ├────────────────────────────┤  │
│  │ Tempo Consistency  85% 🟢 │  │
│  │ "إيقاع ثابت"              │  │
│  ├────────────────────────────┤  │
│  │ TUT               45s     │  │
│  │ (مثالي للتضخم: 40-60s ✅) │  │
│  └────────────────────────────┘  │
│                                  │
│  ┌─ Fatigue & Load ────────────┐ │
│  │ 🔋 Fatigue at Rep #8       │ │
│  │ ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄  │ │
│  │ عند التعب:                  │ │
│  │ • ROM ↓ 12° (95° → 83°)    │ │
│  │ • Speed ↓ 30%               │ │
│  │ • Form محافظ عليه ✅        │ │
│  │ ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄  │ │
│  │ Velocity Loss:  30% 🟡     │ │
│  │ Fatigue Index:  Rep #8     │ │
│  │ ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄  │ │
│  │ ⚖️ Weight:      60 kg      │ │
│  │ 📦 Volume:      720 kg     │ │
│  │ 💪 Est. 1RM:    80 kg      │ │
│  │ ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄  │ │
│  │ 📊 الحكم: تمرين متوسط     │ │
│  │    الشدة — مناسب للتضخم    │ │
│  └─────────────────────────────┘ │
│                                  │
└──────────────────────────────────┘
```

**ما يُعرض:**
- مقاييس Control بالتفصيل + مقارنة الإيقاع بالمستهدف (إذا توفر)
- **تحليل التعب المفصّل:** ليس فقط "متى" بل "ماذا تغير" — يقارن أول 5 عدات بآخر 5 في ROM + Speed + Form
- مقاييس الحمل (Weight / Volume / 1RM) إذا توفر وزن
- **حكم ذكي على شدة التمرين:** بناءً على VL% + Fatigue + Reps:
  - VL < 15% + لا تعب → "سهل — يمكنك زيادة الوزن"
  - VL 15-30% + تعب متأخر → "متوسط — مناسب للتضخم"
  - VL > 30% + تعب مبكر → "شديد — قلل العدات أو الوزن"

---

### الشاشة 7: Tips & Export — النصائح والتصدير

```
┌──────────────────────────────────┐
│                                  │
│  🎯 الجلسة القادمة:              │
│  ┌────────────────────────────┐  │
│  │ "قلل إلى 8 عدات. التعب     │  │
│  │  بدأ مبكراً وأثر على ROM.  │  │
│  │  حافظ على 60 كجم."         │  │
│  └────────────────────────────┘  │
│                                  │
│  💡 نصائح تقنية:                 │
│  ┌────────────────────────────┐  │
│  │ 1. 🚨 ركبتك تتقدم أمام    │  │
│  │    أصابع القدم في العدات    │  │
│  │    الأخيرة — ادفع من كعبيك │  │
│  ├────────────────────────────┤  │
│  │ 2. ⚠️ مرحلة النزول أسرع   │  │
│  │    من اللازم (1.8s بدل 3s) │  │
│  │    — عد 1-2-3 أثناء النزول │  │
│  └────────────────────────────┘  │
│                                  │
│  ┌────────────────────────────┐  │
│  │                            │  │
│  │   [📄 تصدير PDF]           │  │
│  │                            │  │
│  │   [📤 مشاركة النتيجة]      │  │
│  │                            │  │
│  └────────────────────────────┘  │
│                                  │
└──────────────────────────────────┘
```

**المبادئ:**
- **توصية الجلسة القادمة** — نصيحة واحدة استراتيجية مبنية على نمط الأداء (ليست عامة)
- **نصائح تقنية** (حتى 2) — مبنية على ErrorAnalysis الفعلي، محددة وقابلة للتنفيذ
- **تصدير PDF** — التقرير الكامل بتنسيق احترافي
- **مشاركة** — صورة مصممة (Hero + Score + Exercise) للسوشيال ميديا

---

### 4.3 صفحة الجلسة (Planned Workout Summary) — للجلسات المتعددة فقط

```
┌──────────────────────────────────┐
│                                  │
│  💪 Chest Day — Week 3           │
│                                  │
│  ┌────────────────────────────┐  │
│  │  Overall: 82%  ·  3 تمارين │  │
│  │  المدة: 45:00  ·  32 عدة   │  │
│  └────────────────────────────┘  │
│                                  │
│  ↓ Scroll Down for Exercises     │
│                                  │
│  ┌────────────────────────────┐  │
│  │ 🏋️ Bench Press    87% 🟢  │  │
│  │ 3×10 · 70kg · VL 20%      │  │
│  │                 → Swipe    │  │
│  ├────────────────────────────┤  │
│  │ 🏋️ Incline DB     79% 🟡 │  │
│  │ 3×8 · 24kg · VL 35%       │  │
│  │                 → Swipe    │  │
│  ├────────────────────────────┤  │
│  │ 🏋️ Cable Fly      84% 🟢 │  │
│  │ 3×12 · 15kg · VL 15%      │  │
│  │                 → Swipe    │  │
│  └────────────────────────────┘  │
│                                  │
│  📦 Total Volume: 2,340 kg       │
│  🔋 Planned Workout Difficulty: Medium   │
│                                  │
│  [📤 Share Planned Workout]              │
│                                  │
└──────────────────────────────────┘
```

**المبادئ:**
- ملخص سريع للجلسة (درجة كلية + عدد تمارين + مدة)
- كل تمرين في بطاقة: الاسم + الدرجة + Sets×Reps + الوزن + VL%
- **Scroll Down** لرؤية كل التمارين
- **Swipe Left** على أي تمرين يدخل في شاشاته الـ 7 التفصيلية
- Volume كلي + حكم على شدة الجلسة في الأسفل

---

## 5. تدقيق المقاييس — هل استخدمنا كل شيء؟

### 5.1 خريطة كل مقياس ومكانه في التقرير

| # | المقياس | أين يظهر | ملاحظات |
|---|---------|---------|---------|
| 1 | `form_score` | Screen 1 (Overall) · Screen 2 (Form card) · Screen 4 (تفصيل) | مستخدم بالكامل |
| 2 | `rep_count` | Screen 1 (Hero) · Screen 2 (Chart) | مستخدم بالكامل |
| 3 | `duration` | Screen 1 (Hero) | مستخدم بالكامل |
| 4 | `rom` | Screen 2 (Form card) · Screen 3 (Best/Worst) · Screen 4 (تفصيل) · Screen 6 (تحليل التعب) | مستخدم بالكامل |
| 5 | `symmetry` | Screen 2 (Form card) · Screen 4 (L/R breakdown) | **Bilateral فقط** |
| 6 | `stability` | Screen 2 (Safety card) · Screen 5 (تفصيل) | مستخدم بالكامل |
| 7 | `tempo` | Screen 2 (Control card) · Screen 6 (مقارنة بالمستهدف) | **Rep-based فقط** |
| 8 | `tut` | Screen 2 (Control card) · Screen 6 (تفصيل + سياق تدريبي) | **Rep-based فقط** |
| 9 | `hold_duration` | Screen 1 (Hero — بدل rep_count) · Screen 2 (بدل Chart) | **Hold فقط** — يحتاج تصميم خاص |
| 10 | `alignment` | Screen 2 (Safety card) · Screen 5 (تفصيل بالشدة) | **تمارين بها Position Checks فقط** |
| 11 | `form_consistency` | Screen 2 (Form card) · Screen 4 (تفصيل) | **≥ 4 عدات فقط** |
| 12 | `fatigue_index` | Screen 2 (علامة في Chart) · Screen 6 (تحليل تفصيلي) | **≥ 4 عدات فقط** |
| 13 | `tempo_consistency` | Screen 2 (Control card) · Screen 6 (تفصيل) | **≥ 3 عدات فقط** |
| 14 | `velocity` | Screen 6 (في قسم Fatigue كرقم + في Best/Worst كمقارنة سرعة) | يظهر كسياق وليس كبطاقة مستقلة |
| 15 | `velocity_loss` | Screen 2 (Control card) · Screen 6 (تفصيل + حكم الشدة) | **≥ 3 عدات فقط** |
| 16 | `weight` | Screen 1 (رقم صغير إذا موجود) · Screen 6 (Load section) | **Weighted فقط** |
| 17 | `volume` | Screen 6 (Load section) · Planned Workout Summary | **Weighted فقط** |
| 18 | `est_1rm` | Screen 6 (Load section) | **Weighted فقط** |
| — | Safety Score | Screen 2 (Safety card) · Screen 5 | Composite — دائماً |
| — | Control Score | Screen 2 (Control card) · Screen 6 | Composite — دائماً |
| — | Overall Quality | Screen 1 (Hero — الرقم الكبير) | Composite — دائماً |

**النتيجة: جميع الـ 18 مقياس + 3 composites مستخدمة.** كل مقياس يظهر في مكانين على الأقل (ملخص + تفصيل).

---

### 5.2 تكيّف الشاشات حسب نوع التمرين

النظام يستخدم `ExerciseConfigSnapshot.shouldShowMetric()` للتحكم بما يظهر. لكن بعض أنواع التمارين تحتاج **تغييرات هيكلية** في الشاشات وليس مجرد إخفاء مقاييس:

#### تمارين Hold (Plank, Wall Sit...)

| الشاشة | التعديل عن الوضع الافتراضي |
|--------|---------------------------|
| **Screen 1 (Hero)** | بدل "12 عدة" → يُعرض **مدة الثبات** ("45 ثانية") كرقم رئيسي. بدل "02:30" → **جودة الشكل** ("Form: 88%") |
| **Screen 2** | **لا يوجد Reps Journey Chart** — يُستبدل بـ **Form Quality Timeline** (رسم بياني خطي يُظهر جودة الشكل عبر الزمن — هل تراجعت؟). البطاقات الثلاث تبقى لكن: Form card بدون ROM/Symmetry/FC, Control card بدون Tempo/TUT/TC/VL — يبقى فيها Stability فقط |
| **Screen 3 (Best/Worst)** | **لا يوجد** — يُستبدل بـ **Joint Breakdown** (كل مفصل متتبع + مدة الخروج عن النطاق) |
| **Screen 6** | بدون Tempo/TUT/VL/Fatigue. يُعرض: Hold Duration + Stability + Form over Time |

**المقاييس المخفية تلقائياً في Hold:**
`rep_count`, `tempo`, `tut`, `rom`, `form_consistency`, `fatigue_index`, `velocity`, `velocity_loss`, `tempo_consistency`

**المقاييس المُضافة في Hold:**
`hold_duration` (يحل محل rep_count كرقم رئيسي)

---

#### تمارين غير ثنائية (Non-Bilateral)

| الشاشة | التعديل |
|--------|--------|
| **Screen 2 (Form card)** | **Symmetry لا يظهر** — Form card يعرض فقط: Form Score + ROM + Form Consistency |
| **Screen 4 (Form Details)** | **بدون قسم Symmetry** — المساحة تُعطى لتفصيل ROM أكثر |

---

#### تمارين بدون وزن (Bodyweight)

| الشاشة | التعديل |
|--------|--------|
| **Screen 1 (Hero)** | لا يظهر رقم الوزن |
| **Screen 6** | **قسم Load بالكامل مخفي** (لا Weight, لا Volume, لا 1RM). المساحة تُعطى لتفصيل Fatigue + Control أكثر |
| **Planned Workout Summary** | لا يظهر Volume الكلي |

---

#### تمارين بدون Position Checks

| الشاشة | التعديل |
|--------|--------|
| **Screen 2 (Safety card)** | **Alignment لا يظهر** — Safety card يعرض: Safety Score + Stability + Danger Events فقط |
| **Screen 5 (Safety Details)** | **بدون قسم Alignment** |

---

#### عدات قليلة (< 4 رقم)

| الشرط | المقاييس المخفية |
|-------|-----------------|
| **< 4 عدات** | `form_consistency`, `fatigue_index` |
| **< 3 عدات** | `velocity_loss`, `tempo_consistency` |
| **1 عدة** | Screen 2 يعرض Chart بعمود واحد (أو يُخفى). لا Best/Worst مقارنة. Control card يعرض Tempo فقط. |

---

### 5.3 جدول التكيّف الكامل

```
Rep-based + Bilateral + Weighted + HasPositionChecks + ≥4 reps
= الحالة الكاملة: كل الـ 18 مقياس + 7 شاشات كاملة

Hold exercise:
  إخفاء: rep_count, tempo, tut, rom, symmetry, form_consistency,
          fatigue_index, velocity, velocity_loss, tempo_consistency
  إضافة: hold_duration
  تعديل: Screen 2 (timeline بدل chart), Screen 3 (joint breakdown بدل best/worst)

Non-bilateral:
  إخفاء: symmetry

Bodyweight:
  إخفاء: weight, volume, est_1rm

No position checks:
  إخفاء: alignment

< 4 reps:
  إخفاء: form_consistency, fatigue_index

< 3 reps:
  إخفاء: velocity_loss, tempo_consistency
```

---

## 6. تحسينات النصائح — من عامة إلى ذكية

### النظام الحالي:
```
"ركز على الشكل" ← عام ولا يُضيف شيئاً
"حاول النزول أعمق" ← كل متدرب يعرف هذا
```

### مصادر الرسائل المتاحة (موجودة في Exercise JSON):

النظام يملك **3 مصادر رسائل غنية** مكتوبة من مُعد التمرين — يجب استخدامها كمصدر أساسي للنصائح بدل النصوص العامة:

| المصدر | المكان في ExerciseConfig | المحتوى | مثال |
|--------|------------------------|---------|------|
| **stateMessages** | `trackedJoint.stateMessages.{state}` | رسالة مخصصة لكل حالة مفصل (PERFECT/WARNING/DANGER) **مع دعم zone-specific** (up/down) | `warning.down: "اثنِ المرفق أكثر"` · `danger: "وضعية خطيرة! انتبه لسلامتك"` |
| **feedbackMessages.tips** | `poseVariant.feedbackMessages.tips[]` | نصائح تقنية عامة للتمرين | `"ادفع من كعبيك وليس من أصابع القدم"` |
| **positionCheck.errorMessage** | `positionCheck.errorMessage` | رسالة خطأ محددة لكل فحص وضعية | `"الركبة تجاوزت أصابع القدم"` |
| **feedbackMessages.motivational** | `poseVariant.feedbackMessages.motivational[]` | رسائل تحفيزية | `"ممتاز! أداء مثالي!"` |

**هذه الرسائل مكتوبة بعناية لكل تمرين باللغتين.** يجب أن تكون هي المحتوى الأساسي للنصائح.

---

### النظام المقترح (3 طبقات):

**طبقة 1: توصية الجلسة القادمة (Strategic)**
مبنية على أنماط الأداء الكلية — يُولّدها النظام خوارزمياً:

| الشرط | التوصية |
|-------|---------|
| VL > 30% + Fatigue مبكر | "قلل إلى N عدات أو أنقص 5 كجم" |
| Form Score تراجع (vs آخر جلسة) | "ركز على الشكل — قلل الوزن" |
| ROM Achievement > 95% لعدة جلسات | "أنت جاهز لزيادة الوزن" |
| Symmetry < 85% | "أضف تمارين أحادية الجانب للجانب الأضعف" |
| Tempo أسرع من المستهدف | "أبطئ في مرحلة النزول" |

**طبقة 2: نصائح من Exercise Messages (Primary Tips)**
**مصدرها: `stateMessages` + `feedbackMessages.tips` + `positionCheck.errorMessage`** — النصائح الأصلية المكتوبة لهذا التمرين:

| المصدر | كيفية الاستخدام | مثال في التقرير |
|--------|----------------|----------------|
| `ErrorAnalysisItem.message` (من `stateMessages`) | النصيحة الأساسية لكل خطأ متكرر. **تُعرض مع عدد العدات المتأثرة** | "🚨 في 5 عدات: *اثنِ المرفق أكثر* — العدات #3, #5, #7, #9, #11" |
| `ErrorAnalysisItem.tip` (من `feedbackMessages.tips`) | الحل العملي المقترح. **يُعرض كنصيحة قابلة للتنفيذ** | "💡 *ادفع من كعبيك وليس من أصابع القدم*" |
| `DangerAlert.dangerMessage` (من `stateMessages.danger`) | تحذير DANGER. **يُعرض بأولوية قصوى** | "🚨 *وضعية خطيرة!* — تحكم في الحركة ولا تتجاوز الحدود الآمنة" |
| `DangerAlert.solutionTip` (من `feedbackMessages.tips`) | حل لمشكلة DANGER | "💡 *قلل الوزن وركز على التحكم في الحركة*" |
| `positionCheck.errorMessage` | خطأ وضعية محدد | "⚠️ *الركبة تجاوزت أصابع القدم* — في العدات 8-10" |

**طبقة 3: سياق رقمي (Data-Enriched Context)**
الأرقام من المقاييس تُضاف كسياق للنصائح من الطبقة 2:

| المصدر | مثال |
|--------|------|
| ROM drop بعد fatigue | "(ROM انخفض من 95° إلى 83° بعد العدة #8)" |
| Tempo deviation | "(النزول: 1.8s فعلي بدل 3s مستهدف)" |
| Symmetry L/R difference | "(الجانب الأيسر أضعف بـ 8°)" |
| Velocity Loss at specific rep | "(السرعة انخفضت 30% في آخر 3 عدات)" |

### مثال متكامل للنصائح في التقرير:

```
🎯 الجلسة القادمة:                          ← طبقة 1 (Strategic)
"قلل إلى 8 عدات. التعب بدأ من العدة #8 وأثر 
على المدى الحركي (-12°). حافظ على 60 كجم."

💡 نصائح من التمرين:                        ← طبقة 2 (Exercise Messages)

1. 🚨 "تحكم في الحركة ولا تتجاوز الحدود     ← من stateMessages.danger
    الآمنة" — في العدة #5 (ركبة يمنى)
    → "ادفع من كعبيك وليس أصابع القدم"      ← من feedbackMessages.tips[0]

2. ⚠️ "اثنِ المرفق أكثر"                    ← من stateMessages.warning
    — في 3 عدات (#8, #10, #12)
    (الزاوية: 45° بدل 60-90°)               ← طبقة 3 (Data Context)
```

**القاعدة:** النصيحة = **رسالة التمرين** (من Exercise JSON) + **السياق الرقمي** (من المقاييس) + **عدد العدات المتأثرة**. لا نكتب نصائح عامة أبداً إذا توفرت رسائل مخصصة.

---

## 7. ملخص القرارات التقنية

| القرار | التوصية |
|--------|---------|
| **V1 أم V2؟** | الانتقال لـ V2 UI. البيانات جاهزة. |
| **صفحات العدات الفردية؟** | إزالتها. الاكتفاء بـ Best/Worst + Reps Journey Chart. |
| **البطاقات الثلاث؟** | عرضها في الشاشة 2 + شاشة تفصيلية لكل بطاقة (Screens 4-6). |
| **Target Tempo؟** | إضافة حقل اختياري `targetTempo` في ExerciseConfig. |
| **Share؟** | صورة Hero + Score + Exercise name → Share intent. |
| **PDF Export؟** | التقرير الكامل (7 شاشات) كـ PDF قابل للتصدير. |
| **Planned Workout vs Exercise navigation؟** | Vertical scroll = بين التمارين. Horizontal scroll = داخل التمرين. |
| **تحليل التعب؟** | مقارنة أول N/2 عدات بآخر N/2 (ROM + Speed + Form). |
| **حكم شدة التمرين؟** | بناءً على VL% + Fatigue timing + Reps. |

---

## 8. ترتيب التنفيذ المقترح

### المرحلة 1: تفعيل V2 UI (الأعلى أولوية)
1. إنشاء الشاشة 1 (Hero) مع الـ 3 أرقام + QuickInsight + Share
2. إنشاء الشاشة 2 (Performance Overview) مع الـ Chart + البطاقات الثلاث
3. إنشاء الشاشة 3 (Best/Worst) بالتصميم الجديد
4. إزالة صفحات العدات الفردية من Pager

### المرحلة 2: شاشات التفاصيل
5. الشاشة 4 (Form Details) مع تحديد الجانب الأضعف في Symmetry
6. الشاشة 5 (Safety Details) مع DangerAlerts المدمجة
7. الشاشة 6 (Control + Fatigue + Load) مع تحليل التعب المفصّل + حكم الشدة
8. الشاشة 7 (Tips + Export)

### المرحلة 3: تحسينات
9. إضافة `targetTempo` في ExerciseConfig + Admin Dashboard
10. نظام النصائح الذكية (من ErrorAnalysis + patterns)
11. تصدير PDF
12. تحديث Planned Workout Summary page للجلسات المتعددة
