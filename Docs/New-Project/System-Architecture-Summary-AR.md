# ملخص خطة معمارية منصة التدريب

> نظام تدريب منظم يقدّم تقدماً حقيقياً وقابلاً للقياس للمتدربين من خلال مسارات واضحة، وتطوّر مدفوع بالبيانات، ووصفة برامج ذكية.

---

## 1. نظرة عامة على النظام

### الفلسفة الأساسية

النظام يعمل كحلقة تغذية راجعة مغلقة:

```
Assess → Classify → Prescribe → Train → Measure → Progress → Reassess
```

كل قرار في هذه الحلقة يعتمد على **بيانات أداء حقيقية** تُجمَع عبر Pose Estimation (ROM، Form Score، Symmetry، Stability، Tempo، Velocity). لا تخمين.

### طبقات المعمارية

| الطبقة | المحتوى |
|--------|---------|
| **User Interface** | تطبيق Android + لوحة تحكم Admin |
| **Application Layer** | Prescription Engine، Progression Engine، Reports |
| **Data Layer** | Level Profile، Programs، Sessions، Assessments |
| **Measurement Layer** | Pose Estimation، حساب الزوايا، التحقق من الشكل، عد التكرارات، جمع المقاييس |

---

## 2. هيكل الكيانات (Entity Hierarchy)

### الشجرة الكاملة

```
User
 ├── UserLevelProfile          ← لقطة متعددة الأبعاد لمستوى المستخدم
 │     ├── overallLevel        (رقم بسيط 1–5 يُعرض للمستخدم)
 │     ├── domainLevels[]      (تفصيل لكل domain)
 │     └── regionLevels[]      (تفصيل لكل منطقة في الجسم)
 │
 ├── ActivePlan                ← جدول التدريب الحالي للمستخدم
 │     └── ActivePlanProgram[] (قائمة مرتبة بالبرامج المسجلة)
 │           └── Program
 │                 └── ProgramWeek[]
 │                       └── ProgramDay[]
 │                             ├── ProgramSession[]
 │                             │     └── ProgramSessionItem[]
 │                             │           ├── Exercise
 │                             │           └── TimedRest
 │                             └── RestDay
 │
 ├── BodyScanResult[]          ← سجل التقييمات
 └── TrainingSession[]        ← سجل الجلسات التدريبية
       ├── SessionMetrics
       └── RepMetrics[]
```

### خريطة العلاقات

```
Assessment ──ينتج──▸ BodyScanResult ──يغذي──▸ UserLevelProfile
                                                      │
UserLevelProfile ──يغذي──▸ PrescriptionEngine         │
                                 │                    │
                          يختار ويضبط                 │
                                 │                    │
                                 ▼                    │
                          ActivePlan (Programs)        │
                                 │                    │
                          المستخدم يتدرب               │
                                 │                    │
                                 ▼                    │
                          TrainingSession + Metrics    │
                                 │                    │
                          ProgressionEngine يقيّم      │
                                 │                    │
                          ┌─────┴──────┐               │
                          ▼            ▼               │
                   يعدّل الجلسة   يُطلق                │
                    التالية    reassessment ───────────┘
```

---

## 3. تعريفات الكيانات الأساسية

### Level (المستوى)

مرحلة مُسماة في رحلة التدريب. يحدد درجة الصعوبة والمعايير الافتراضية للتدريب. المستويات عامة (ليست لكل برنامج).

| المستوى | الرقم | نطاق BodyScore | الوصف |
|---------|-------|----------------|-------|
| Foundation | 1 | 0–24 | السلامة أولاً. تركيز تصحيحي. وزن الجسم فقط. |
| Building | 2 | 25–44 | أنماط حركة أساسية. مقاومة خفيفة. |
| Intermediate | 3 | 45–64 | زيادة تدريجية في الحمل. شدة متوسطة. |
| Advanced | 4 | 65–84 | حركات معقدة. أحمال ثقيلة. |
| Elite | 5 | 85–100 | تحسين الأداء. تدريب ذروة. |

**LevelDefaults:** معايير افتراضية للتدريب (عدد المجموعات، التكرارات، الراحة، مدة الجلسة، التكرار الأسبوعي).

---

### UserLevelProfile

لقطة من مستوى المستخدم الحالي عبر كل الأبعاد. تُحدَّث بعد كل Assessment.

- **overallLevel:** الرقم البسيط (1–5) الذي يراه المستخدم
- **bodyScore:** آخر نتيجة تقييم (0–100)
- **domainLevels:** مستوى لكل domain (mobility، control، symmetry، safety)
- **regionLevels:** مستوى لكل منطقة (shoulder، hip، knee، ankle، spine، core، balance)
- **limitingFactors:** المجالات أو المناطق التي تعيق تقدم المستخدم

---

### Program

خطة تدريب منظمة تمتد لعدة أسابيع.

**أنواع البرامج:**

| النوع | الوصف | مثال |
|-------|-------|------|
| training | تدريب قوة/لياقة عادي | "4-Week Full Body Training" |
| mobility | تحسين المرونة ونطاق الحركة | "Shoulder Mobility Program" |
| therapeutic | إصلاح موجه لمشكلة محددة | "Lower Back Pain Relief" |

**حقول الوصفة:** targetDomain، targetRegions، levelRange، entryCriteria، exitCriteria، contraindications، prescriptionPriority، prerequisiteProgramId، nextProgramId.

---

### ActivePlan

جدول التدريب الحالي للمستخدم — أي البرامج نشطة وبأي ترتيب.

- **ActivePlanProgram:** برنامج واحد داخل الخطة، له status (upcoming / active / completed / skipped)
- البرامج مرتبة تسلسلياً؛ عند انتهاء البرنامج النشط ينتقل للنظام التالي
- يمكن جدولة Reassessment بين البرامج

---

### ProgressionRule

يحدد كيف تتغير معايير التدريب بناءً على بيانات الأداء.

- **Trigger:** متى تُقيَّم القاعدة (session_completed، week_completed، set_completed)
- **Conditions:** الشروط (مثلاً avgFormScore >= 75، completionRate >= 90%)
- **Action:** الإجراء (increase_weight، decrease_weight، increase_reps، suggest_reassessment، إلخ)

---

### ReassessmentSchedule

يتتبع متى يجب إعادة تقييم المستخدم.

**المحفزات:** program_complete، periodic timer، progression_trigger، manual request.

---

## 4. نظام المستويات (Level System)

### تدفق حساب المستوى

```
BodyScanResult
  │
  ├── bodyScore (0–100) ──▸ overallLevel (1–5)
  │
  ├── domainScores ──▸ domainLevels (mobility، control، symmetry، safety)
  │
  └── regions[] ──▸ regionLevels (shoulder، hip، knee، إلخ)
```

**scoreToLevel():** دالة واحدة تُستخدم في كل مكان لتحويل النقاط إلى مستوى.

### كشف العوامل المحددة (Limiting Factors)

- إذا كان domainLevel أو regionLevel أقل من overallLevel بمقدار 2 أو أكثر → يُعتبر Limiting Factor
- هذه العوامل توجه Prescription Engine في اختيار البرامج

### تأثير المستوى على التدريب

عند عدم تحديد قيم صريحة في ProgramSessionItem، تُملأ من:

1. قيم العنصر نفسه
2. levelOverride إن وُجد
3. مستوى domain للتمرين
4. overallLevel (الخيار الأخير)

---

## 5. Prescription Engine (محرك الوصفة)

### الفكرة

نظام **قائم على القواعد** يحوّل نتائج التقييم إلى خطة تدريب شخصية. بدون AI/ML — حتمي، قابل للتتبع، وقابل لضبط الخبراء.

### خطوة التصنيف (Classification)

بعد كل Assessment يُصنَّف المستخدم حسب أولوية:

| الأولوية | الفئة | الشرط | الإجراء |
|----------|------|-------|---------|
| 1 | SAFETY_BLOCK | safetyGates أو painFlags أو فشل PAR-Q | برامج علاجية، حظر تمارين معينة |
| 2 | CORRECTION_NEED | أي region score < 25 | برنامج تصحيحي + تعديل باقي التدريب |
| 3 | IMBALANCE | تناظر < 60% أو symmetryLevel أقل من overallLevel | تدريب مع عمل أحادي إضافي للجهة الضعيفة |
| 4 | WEAKNESS | أي domain level أقل من overallLevel بمقدار 2+ | برنامج مستهدف للـ domain الضعيف |
| 5 | NORMAL | كل الـ domains متوازنة | برنامج تدريب عادي يناسب overallLevel |

### خطوات المحرك

1. **Classification:** تحديد فئة المستخدم
2. **Program Selection:** تصفية البرامج المناسبة (type، levelRange، targetDomain، contraindications، prerequisite)
3. **Parameter Personalization:** ضبط sets، reps، weight، rest حسب المستوى والأمان
4. **Plan Assembly:** بناء ActivePlan من البرامج المختارة

---

## 6. Progression Engine (محرك التطوّر)

### الفكرة

يقيّم بيانات الأداء بعد كل جلسة تدريب ويعدّل الجلسات القادمة.

### المقاييس المستخدمة

avgFormScore، avgROM، avgSymmetry، avgStability، completionRate، totalVolume، إلخ.

### تدفق التقييم

```
انتهاء الجلسة
  │
  ▼
تحميل ProgressionRules المناسبة
  │
  ▼
لكل قاعدة: هل الشروط محققة؟
  │
  ├── نعم → تنفيذ الإجراء (تعديل الوزن/التكرارات/إلخ) + تسجيل + إشعار
  └── لا → تخطي
  │
  ▼
التحقق من محفزات Reassessment
```

### Progression History

كل تغيير من Progression Engine يُسجَّل (ruleId، sessionId، field، previousValue، newValue، reason).

---

## 7. نظام إعادة التقييم (Reassessment)

### المحفزات

| المحفز | الأولوية |
|--------|----------|
| Program Complete | عالية |
| Periodic Timer (مثلاً كل 6 أسابيع) | متوسطة |
| Progression Rule (suggest_reassessment) | متوسطة |
| Manual Request | منخفضة |

### التدفق

```
المحفز يُطلق
  │
  ▼
إنشاء ReassessmentSchedule (pending)
  │
  ▼
إشعار المستخدم
  │
  ▼
المستخدم يكمل Assessment (PAR-Q+ → تمارين → BodyScanResult)
  │
  ▼
حساب UserLevelProfile جديد
  │
  ├── Level UP → احتفال + فتح برامج جديدة
  ├── نفس المستوى → تعديل مناطق التركيز
  └── Level DOWN → تحوّل لبرامج تصحيحية
  │
  ▼
تشغيل Prescription Engine → تحديث ActivePlan
```

---

## 8. رحلة المستخدم

### مستخدم جديد

1. تسجيل / دخول
2. PAR-Q+ Pre-screening
3. Initial Assessment (BodyScan)
4. إنشاء Level Profile (المستخدم يرى مستواه ومجالاته و Limiting Factors)
5. تشغيل Prescription Engine
6. إنشاء ActivePlan
7. بدء التدريب

### التدريب اليومي

1. فتح التطبيق → عرض خطة اليوم
2. بدء الجلسة → feedback حي + جمع مقاييس لكل rep
3. انتهاء الجلسة → تقرير الأداء
4. رفع البيانات للـ backend
5. Progression Engine يقيّم → تعديل الجلسة القادمة + إشعار

### إكمال البرنامج وإعادة التقييم

1. إكمال آخر جلسة
2. عرض تقرير إكمال البرنامج
3. إطلاق Reassessment
4. المستخدم يكمل Assessment
5. مقارنة BodyScanResult الجديد بالقديم
6. حساب UserLevelProfile جديد
7. Prescription Engine → ActivePlan جديد

---

## 9. الموجود مقابل الجديد

### الموجود (مبني بالفعل)

- Exercise، Pose variants، Form validation، Rep counting، Bilateral support
- Per-rep metrics، Session metrics
- Program → Week → Day → Session → Item
- UserProgram، ProgramSessionReport، Reports system
- Assessment engine، BodyScanResult، Domain scores، Safety gates
- PAR-Q+، Mobile sync، Admin Dashboard
- Training flow، Report UI

### الجديد (يُبنى)

| المرحلة | المكونات |
|---------|----------|
| **Phase 0** | ربط رفع BodyScanResult، توحيد مقاييس Metrics، إزالة قفل active-program من Reports، استبدال mock data في Home |
| **Phase 1** | Level entity، UserLevelProfile، Level Profile API + Mobile UI |
| **Phase 2** | Program prescription metadata، Prescription Engine V1، Admin fields |
| **Phase 3** | ActivePlan، ReassessmentSchedule، Plan Overview UI |
| **Phase 4** | ProgressionRule، Progression Engine، ProgramSessionItem fields |
| **Phase 5** | Level-up celebration، Admin ProgressionRule، تحليلات، تحسينات |

---

## 10. مقاييس المنتج

### North Star Metric

**نسبة المستخدمين الذين يحققون تحسناً قابلاً للقياس خلال 28 يوماً** (مثلاً +5 نقاط bodyScore).

### مؤشرات رائدة

- معدل إكمال الجلسات الأسبوعي
- D7 و D30 retention
- معدل إكمال Reassessment
- متوسط الجلسات أسبوعياً
- الوقت حتى أول جلسة

### حواجز الأمان

- تكرار pain flags
- انخفاض Form score مع زيادة الوزن
- نسبة DANGER reps
- محاولات تجاوز safety gates

---

## 11. مراحل التنفيذ (ملخص)

| المرحلة | الهدف |
|---------|-------|
| **Phase 0** | إصلاح حلقة Assess → Prescribe → Train (أساس حرج) |
| **Phase 1** | نظام المستويات (Level System) — شريحة رأسية |
| **Phase 2** | تحسين البرامج + Prescription V1 (برنامج واحد موصى به) |
| **Phase 3** | ActivePlan + دورة الحياة (تسلسل برامج + انتقالات تلقائية) |
| **Phase 4** | Progression Engine (3 قواعد فقط في البداية) |
| **Phase 5** | تحسينات + احتفال Level-up + تحليلات Admin |

---

## 12. قرارات تصميم رئيسية

| القرار | السبب |
|--------|-------|
| **Rule-Based وليس AI** | برمجة اللياقة معروفة، قرارات الأمان يجب أن تكون متوقعة، لا بيانات تدريب كافية بعد، قابل للتتبع |
| **مستويات متعددة الأبعاد لكن رقم واحد للمستخدم** | المستخدم يرى "Level 3" بسيط؛ النظام يستخدم تفاصيل دقيقة للوصفة |
| **Assessment منفصل عن Program** | له تدفق خاص (PAR-Q+ → حركات اختبار → BodyScanResult)، ونتائجه تُدخل للنظام وليست نوع برنامج |
| **ActivePlan بدل UserProgram فقط** | UserProgram = تسجيل واحد؛ ActivePlan = جدول برامج مرتبة مع انتقالات (Therapeutic → Training → Reassessment → Next) |
| **Phase 0 قبل كل شيء** | رفع BodyScanResult غير مربوط، Reports مقفلة على active فقط، مقاييس غير موحدة — البناء فوق ذلك يسبب مشاكل |
| **3 قواعد فقط في Progression في البداية** | أقل = أسهل للتحقق، تغطي 90% من السيناريوهات، إضافة قواعد لاحقاً أسهل من إزالة قواعد سيئة |

---

*تم الاحتفاظ بجميع المصطلحات والأسماء كما هي بالإنجليزية.*
