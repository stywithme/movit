# خطة إعادة تصميم مكوّن التقويم في شاشة Train

> **Train Calendar Component — Full UX & Engineering Redesign Plan**
>
> النطاق: المكوّن الأسبوعي (`MovitWeekStrip` / `TrainWeekPreview`) في أعلى شاشة **Train** بتطبيق `android-poc` (KMP).
> الجمهور: مستخدم نهائي عربي/إنجليزي. الهدف أن يصبح هذا المكوّن "بوصلة" المتدرب — يفهم منه فوراً: **ماذا أنجزت؟ ما اليوم؟ ماذا بعد، ومتى؟**
>
> وثائق ذات صلة: [Train-Page-Redesign-Plan.md](./Train-Page-Redesign-Plan.md) · [Week-Plan-Redesign-Plan.md](./Week-Plan-Redesign-Plan.md) · [Flexibility-Upgrade-Plan.md](./Flexibility-Upgrade-Plan.md) · [Backend-Contract-Matrix.md](./Backend-Contract-Matrix.md)

---

## 1. الخلاصة التنفيذية (TL;DR)

المكوّن الحالي **يخترع** حالة كل يوم من رقمين فقط (عدّاد إنجاز + رقم اليوم الحالي)، بينما الـ backend يملك الحقيقة الكاملة لكل يوم (تدريب/راحة/استشفاء، مكتمل/غير مكتمل، عدد الجلسات، التمارين، الدقائق). والأسوأ: المكوّن يفرض شبكة 7 أيام ثابتة ومنطق "Missed" عقابي **يناقض** طبيعة البرنامج المرنة (Completion-based، لا يعاقب على التأخير).

**القرار:** لا حلول وسطية. ننقل مصدر الحقيقة إلى الـ backend (per-day week calendar)، ونعيد بناء المكوّن كـ **تقويم تفاعلي** بحالات صادقة، تواريخ حقيقية، ولمسة تفتح معاينة/بدء الجلسة.

---

## 2. الوضع الحالي — تشريح المشكلة

### 2.1 ما يراه المستخدم اليوم (من اللقطة)
- بطاقة بيضاء، عنوان "Week 1"، وسبعة مربعات `D1…D7`.
- اليوم (1) خانة باهتة فاتحة، والبقية (2–7) مربعات فارغة متطابقة بلا أي معلومة.
- Legend أسفل: Done / Today / Missed / Rest — **بينما الحالة الأكثر شيوعاً (Planned) غير موجودة في الـ Legend إطلاقاً**.
- "0 day streak" و "0%" بألوان باهتة تبدو كأنها معطّلة/مكسورة.
- لا تاريخ، لا يوم أسبوع، لا نوع تمرين، لا مدة، لا شيء قابل للنقر.

### 2.2 الجذر التقني — الواجهة

**ملف المنطق:** [`TrainApiMapper.buildWeekPreview`](../../../android-poc/feature/train/src/commonMain/kotlin/com/movit/feature/train/TrainApiMapper.kt) (الأسطر ~227–260):

```kotlin
val days = (1..7).map { dayNumber ->            // ① شبكة 7 ثابتة دائماً
    val state = when {
        status == RestDay && dayNumber == todayDay -> Rest        // ② الراحة لليوم الحالي فقط
        dayNumber < todayDay && dayNumber <= completed -> Done     // ③ الإنجاز كبادئة متّصلة
        dayNumber == todayDay && status == CompletedToday -> Done  // ④ اليوم يفقد تمييزه بعد الإنجاز
        dayNumber == todayDay -> Today
        dayNumber < todayDay -> Missed                             // ⑤ "فائت" مُختلَق وعقابي
        else -> Planned
    }
    ...
}
```

| # | الخلل | لماذا هو خطأ |
|---|------|--------------|
| ① | شبكة `(1..7)` ثابتة | أسبوع البرنامج قد يحتوي **عدد أيام مختلف** (التحقق في `calendar-program-structure.ts` يسمح بـ `dayNumber 1..N`، و`N` قد تكون 3 أو 5). فيظهر 7 خانات بينما `weekProgress.total` قد يساوي 3 → تضارب. |
| ② | الراحة لليوم الحالي فقط | أيام الراحة المُعرّفة في القالب (بين أيام التدريب، أو نهاية الأسبوع) **لا تظهر أبداً كراحة**؛ تُلوَّن خطأً Done/Missed/Planned. حالة `Rest` في الـ design system لا تُستخدم عملياً إلا في "اليوم راحة". |
| ③ | الإنجاز كبادئة متّصلة (`<= completed`) | الإنجاز في الـ backend **لكل (week,day,plannedWorkout)** وقد يكون غير متّصل. العدّاد لا يخبرنا *أي* الأيام أُنجزت. |
| ④ | اليوم يصبح Done | بعد إكمال اليوم تختفي علامة "أنت هنا"، فلا يعرف المستخدم أي خانة تمثّل اليوم. |
| ⑤ | "Missed" مُختلَق | البرنامج **Completion-based**: "اليوم" = أول يوم تدريبي غير مكتمل. البرنامج لا يعلّم أي يوم كـ "فائت" — بل ينتظرك، أو يقوم بـ catch-up snap. تلوين الأيام السابقة بالأحمر يخلق ضغطاً نفسياً يناقض فلسفة المرونة (انظر §3). |

**مشاكل إضافية في الـ mapper:**
- `buildWeekOptions` (الأسطر 262–288): الأسابيع الأخرى تُبنى بـ `dayNumber=1, status=ActivePlan` → **كل أسبوع غير حالي يعرض "اليوم 1 = Today"**. أي أسبوع تتصفّحه يبدو وكأن فيه "يوماً حالياً" وهمياً.

### 2.3 الجذر التقني — مكوّن الـ Design System

**ملف:** [`MovitWeekStrip.kt`](../../../android-poc/core/designsystem/src/commonMain/kotlin/com/movit/designsystem/components/MovitWeekStrip.kt)

| الخلل | التفصيل |
|------|---------|
| **تباين ضعيف** | `Planned` = خلفية `surface` + حدّ رفيع `stroke` فوق بطاقة بيضاء مرتفعة → الخانات تذوب وتبدو فارغة (كما في اللقطة). لا تدرّج بصري. |
| **Legend ناقص ومضلّل** | يعرض 4 حالات وينقص `Planned`؛ والنقاط ملوّنة بـ `tertiary`/`quaternary` خافتة لا تربط بصرياً بالخانات. |
| **غير تفاعلي** | `WeekDayCell` بلا `onClick`. أهم مكوّن في التطبيق **مجرد ديكور** — لا يمكن لمس يوم لمعاينة/بدء تمرينه. |
| **بلا سياق زمني** | لا أحرف أيام الأسبوع (Sun/Mon…)، لا تاريخ، لا "اليوم/غداً"، لا ربط بالتقويم الحقيقي. `D1…D7` أرقام مجرّدة. |
| **بلا معلومة لكل يوم** | لا نقطة/أيقونة لنوع اليوم (قوة/كارديو/استشفاء)، لا عدد تمارين، لا مدة، لا تقدّم للأيام متعددة الجلسات (`completedWorkoutsCount/allWorkoutsCount` موجود في الـ DTO لكنه غير مستخدم هنا). |
| **`active_recovery` مدموج** | يُعامَل كراحة عادية بلا تمييز بصري. |

### 2.4 الجذر التقني — عقد البيانات (Data Contract)

**ملف DTO:** [`HomeDto.kt`](../../../android-poc/core/network/src/commonMain/kotlin/com/movit/core/network/dto/HomeDto.kt)

1. **عدم تطابق صارخ في `CatchUpSuggestionDto`:** الموبايل يتوقّع `missedTrainingDays / message / missedSlots[]`، بينما الـ backend ([`program-catchup.ts`](../../../backend/src/modules/programs/program-catchup.ts)) يرسل `resetType / resetToWeek / resetToDay / calendarDaysSinceLastWorkout / messageAr / messageEn`. النتيجة: **بيانات الـ catch-up لا تصل عملياً للموبايل** (كل الحقول تأخذ قيمها الافتراضية).
2. **لا يوجد per-day calendar في الـ DTO:** الـ backend يملك الحقيقة الكاملة داخل `program.weeks[].days[]` (`isRestDay`, `dayType`, `plannedWorkouts[].reports`) لكنه **يسطّحها** إلى `weekProgress {completed,total}` فقط. فيُضطر الموبايل للتخمين.
3. **لا ربط بالتاريخ:** الموقع في الـ backend completion-based بحت. لا يُرسَل تحويل "يوم البرنامج → تاريخ تقويمي"، رغم توفّر `UserProgram.startDate` و`TrainingProfile.trainingWeekdays` لبنائه.

---

## 3. الحقيقة من الـ Backend — كيف يعمل البرنامج فعلاً

> فهم هذا القسم شرط لأي تصميم صادق. المصدر: [`mobile-home.controller.ts`](../../../backend/src/modules/mobile-sync/mobile-home.controller.ts) · [`plan-position.ts`](../../../backend/src/modules/active-plan/plan-position.ts) · [`schema.prisma`](../../../backend/prisma/schema.prisma).

### 3.1 بنية البرنامج
```
Program (durationWeeks) → ProgramWeek (weekNumber) → ProgramDay (dayNumber, dayType, isRestDay)
                                                          → PlannedWorkout[] (name, estimatedDurationMin)
                                                               → PlannedWorkoutItem[] (exercise / rest)
```
- `ProgramDay.dayType`: `"training"` (افتراضي) · `"rest"` · `"active_recovery"`. و`isRestDay: Boolean`.
- يوم تدريبي حقيقي = `!isRestDay && dayType ∉ {rest, active_recovery}` (الدالة `isProgramTrainingDaySlot`).
- عدد أيام الأسبوع **متغيّر** ومتسلسل `1..N` (وليس بالضرورة 7).

### 3.2 الإنجاز والموقع (Completion-based)
- يُسجَّل الإنجاز في `UserProgramProgress (weekNumber, dayNumber, plannedWorkoutId, status)`. إكمال **اليوم كاملاً** يُعلَّم بسجل خاص `plannedWorkoutId = "__day__"`.
- "اليوم الحالي" = **أول يوم تدريبي (بترتيب القالب) لم تكتمل كل جلساته** (`findNaturalTrainingDayIndex`). أي أن **"اليوم" مفهوم إنجاز، لا تاريخ**.
- `weekProgress = { completed: عدد الأيام المكتملة في الأسبوع الهدف, total: عدد الأيام التدريبية في الأسبوع }`.

### 3.3 المرونة وعدم العقاب (جوهري للـ UX)
- **لا يوجد مفهوم "يوم فائت" منفصل.** إذا تأخّرت، يبقى "اليوم" هو نفس اليوم التدريبي التالي — البرنامج **ينتظرك**.
- **Catch-up snap** (`applyCatchUpSnapIndex`): الفجوة بالأيام التقويمية منذ آخر تمرين:
  - `≤ 2 أيام`: لا شيء.
  - `3–29 يوماً`: يُعاد ضبط الموقع لبداية **أسبوع** البرنامج الحالي.
  - `≥ 30 يوماً`: يُعاد لبداية البرنامج.
- إذن "الفائت" الوحيد المشروع هو **رسالة catch-up لطيفة** عند حدوث snap — وليس تلوين أيام بالأحمر.

### 3.4 جدول المستخدم الأسبوعي
- `TrainingProfile.trainingWeekdays` (`0=أحد … 6=سبت`): الأيام التي يتدرّب فيها المستخدم. في يوم خارج جدوله → `isTrainingDay=false` و`dayType="off_schedule"` (يُعرض كراحة، لكنه ليس راحة القالب).

### 3.5 الحالات الكبرى لليوم (status من `trainMode`)
`no_assessment` · `no_plan` · `reassessment_due` · `rest_day` (راحة قالب / off_schedule / يوم مكتمل) · `active` · `program_complete`.

---

## 4. الرؤية التصميمية (كمصمم تجربة محترف)

### 4.1 المبادئ
1. **الصدق قبل الجمال:** كل خانة تعكس حقيقة الـ backend، لا تخميناً.
2. **بوصلة لا جدول امتحانات:** نلهم الاستمرارية لا نعاقب التأخّر. لا أحمر "Missed" — بل كهرماني لطيف "تحتاج لحاق" فقط عند snap حقيقي.
3. **الزمن ملموس:** ربط كل يوم بتاريخ/يوم أسبوع حقيقي + "اليوم/غداً".
4. **اللمس = نية:** لمس أي يوم يفتح معاينة (ماذا فيه) أو يبدأ الجلسة.
5. **معلومة بنظرة:** نوع اليوم + الحالة + التقدّم مفهومة في < ثانية، وبدون الاعتماد على اللون وحده (أيقونة/شكل + لون) لإمكانية الوصول و عمى الألوان.

### 4.2 مجموعة الحالات القانونية (Day State Set)

> نستبدل `{Planned, Today, Done, Missed, Rest}` بمجموعة صادقة وكاملة:

| الحالة | المعنى | البصري | المصدر من الـ backend |
|--------|--------|--------|------------------------|
| `Completed` | يوم تدريبي أُنجز بالكامل | تعبئة خضراء + ✓ (+ score مصغّر اختياري) | كل `plannedWorkouts` لليوم لها report `completed` |
| `Today` | اليوم التدريبي الحالي (غير مبدوء) | حلقة Primary بارزة + رقم/تاريخ | `weekNumber/dayNumber` = الموقع، `status=active` |
| `InProgress` | اليوم الحالي بدأ ولم يكتمل (متعدّد الجلسات) | حلقة Primary + قوس تقدّم جزئي | `completedWorkoutsCount > 0 && < allWorkoutsCount` |
| `Upcoming` | يوم تدريبي قادم | خانة صلبة بحدّ واضح + نقطة نوع | بعد الموقع الحالي بترتيب القالب |
| `Rest` | راحة من القالب | شكل هادئ مميّز (أيقونة هلال/Z) لا "فارغ" | `isRestDay` أو `dayType="rest"` |
| `ActiveRecovery` | استشفاء نشِط | أيقونة مشي/تمدّد، لون هادئ مختلف عن الراحة | `dayType="active_recovery"` |
| `OffSchedule` | راحة بحسب جدول المستخدم | خانة محايدة + تلميح "راحة بجدولك" | `isTrainingDay=false` |
| `NeedsCatchUp` | فجوة حقيقية أدّت لـ snap | كهرماني لطيف + شيفرون لحاق (ليس أحمر) | `catchUpSuggestion != null` |
| `Locked` (اختياري) | أسبوع لم يُفتح بعد | خانة باهتة + قفل صغير | أسابيع بعد الأسبوع الحالي عند تصفّحها |

ملاحظات:
- **"Today" يبقى مرئياً حتى بعد الإكمال:** نضيف ✓ *داخل* حلقة Today بدل تحويلها إلى Completed صرف — فلا يضيع مرجع "أنت هنا".
- لا حالة `Missed` إطلاقاً.

### 4.3 تشريح الخانة (Day Cell)
```
        ┌──────────────┐
  السبت  │   14         │  ← أعلى: حرف يوم الأسبوع + تاريخ اليوم (لا "D1")
  Sat   │   ●  ✓/⟳     │  ← المنتصف: حالة (رقم/✓/حلقة) + نقطة نوع التمرين
        │  قوة · ٢٥د    │  ← أسفل (في الموسّع): نوع + مدة، أو "راحة"
        └──────────────┘
```
- **نقطة النوع:** قوة=دائرة ممتلئة، كارديو=نبضة، استشفاء=ورقة/مشي، راحة=هلال. لون + شكل (لا لون وحده).
- **اليوم الحالي:** حلقة + توهّج خفيف (pulse مرة واحدة عند الدخول).
- **متعدّد الجلسات:** قوس تقدّم حول الخانة يعكس `completed/all`.

### 4.4 الترويسة (Header)
بدل "Week 1" المجرّدة:
```
الأسبوع ٢ من ٦            ‹  ›
١٤–٢٠ يونيو · ٢ من ٤ تمارين · تركيز: الجزء السفلي
[▓▓▓░░░░░] 50%
```
- نطاق تواريخ الأسبوع + تقدّم الأسبوع (نص + شريط) + تركيز الأسبوع (`ProgramWeek.target`).
- أسهم `‹ ›` بحالات واضحة: ماضٍ (قابل) / حالي / مستقبل (مقفل أو "نظرة مسبقة").

### 4.5 التفاعل (Interaction)
- **لمس يوم قادم/اليوم:** Bottom-sheet معاينة (اسم الجلسة، التمارين، الدقائق، العضلات) + زر "ابدأ" (يعيد استخدام `TrainWorkoutLaunchUi`).
- **لمس يوم مكتمل:** يفتح ملخّص/تقرير اليوم.
- **لمس يوم راحة:** بطاقة لطيفة عن أهمية الاستشفاء + (إن `active_recovery`) اقتراح نشاط خفيف.
- **لمس يوم لاحق ()مقفل:** تلميح "يُفتح بعد إكمال اليوم الحالي".
- **التنقّل بين الأسابيع:** يحدّث الترويسة والخانات؛ الأسبوع الحالي يبقى مميّزاً.

### 4.6 الحركة وإمكانية الوصول و RTL
- **Motion:** ✓ يُرسم بحركة عند الإكمال، Today يَنبض مرة، انتقال الأسبوع slide.
- **A11y:** `contentDescription` لكل خانة ("السبت ١٤ يونيو، يوم قوة، مكتمل")، أهداف لمس ≥ 48dp، تمييز بالشكل+الأيقونة لا اللون فقط، دعم تكبير الخط.
- **RTL:** ترتيب الأيام من اليمين لليسار في العربية، الأسهم mirrored (متوفر أصلاً عبر `AutoMirrored`)، تنسيق التواريخ/الأرقام بحسب اللغة.

### 4.7 معالجة الحالات الفارغة/الحدّية
| الحالة | عرض المكوّن |
|--------|-------------|
| `no_plan` / `no_assessment` / `reassessment_due` | إخفاء التقويم (كما الآن) واستبداله بدعوة واضحة (Explore / Body Scan). |
| `program_complete` | شريط احتفالي: كل الأيام `Completed` + شارة "اكتمل البرنامج" + CTA لإعادة التقييم. |
| `rest_day` (اليوم) | اليوم خانة Rest واضحة، وبقية الأسبوع صادقة. |
| لا اتصال (offline) | عرض آخر تقويم مُخزَّن + شارة "غير متّصل" بدل خانات فارغة. |

---

## 5. التغييرات الهندسية (Engineering Plan)

> فلسفة: **لا تخمين على الواجهة.** مصدر الحقيقة per-day ينتقل للـ backend، والموبايل يعرض ما يصله. (يتماشى مع مبدأ "لا حلول انتقالية".)

### 5.1 Backend — عقد جديد per-day
أضف إلى `trainMode` (أو endpoint مخصّص `GET /mobile/plan/week?week=N`) حقل `weekCalendar`:

```ts
weekCalendar: {
  weekNumber: number;
  weekFocus: Record<string,string> | null;     // من ProgramWeek.target
  startDate: string | null;                     // تاريخ أول يوم بالأسبوع (من UserProgram.startDate + trainingWeekdays)
  days: {
    dayNumber: number;
    date: string | null;                        // تاريخ تقويمي إن أمكن اشتقاقه
    weekdayIndex: number | null;                // 0..6
    dayType: 'training' | 'rest' | 'active_recovery';
    isRestDay: boolean;
    status: 'completed' | 'today' | 'in_progress' | 'upcoming' | 'rest' | 'active_recovery' | 'off_schedule';
    workout: {
      plannedWorkoutId: string;
      name: Record<string,string>;
      exerciseCount: number;
      estimatedMinutes: number | null;
      allWorkoutsCount: number;
      completedWorkoutsCount: number;
      targetMuscles?: string[];
    } | null;
  }[];
}
```
- يُبنى من نفس البيانات المحمّلة أصلاً في `buildHomeData` (لا استعلامات جديدة): `program.weeks[].days[]` + `progress` + `resolveTrainingPositionMeta`.
- **إصلاح `CatchUpSuggestion`:** توحيد الـ DTO على الموبايل ليطابق الـ backend (`resetType/resetToWeek/resetToDay/calendarDaysSinceLastWorkout/messageAr/messageEn`)، وعرض رسالة لطيفة عند وجوده. حذف `missedSlots/missedTrainingDays` غير المستخدمة.

### 5.2 KMP — النماذج والـ Mapper
- `MovitTrainModels.kt`: توسيع `TrainWeekDayUi` بـ `date`, `weekdayLabel`, `dayType`, `workoutSummary`, `launchTarget`, `progress(completed/all)`. وتوسيع `TrainWeekDayState` بالحالات الجديدة (§4.2) وحذف `Missed`.
- `HomeDto.kt`: إضافة `WeekCalendarDto` المطابق لعقد §5.1، وتصحيح `CatchUpSuggestionDto`.
- `TrainApiMapper.buildWeekPreview`: **يستهلك `weekCalendar` مباشرة** بدل اشتقاق `(1..7)`. إزالة منطق الـ "Missed/contiguous-prefix" بالكامل. إصلاح `buildWeekOptions` لتعكس حالة كل أسبوع فعلياً (لا "today" وهمي).

### 5.3 Design System — مكوّن جديد
- تطوير `MovitWeekStrip` → `MovitWeekCalendar`:
  - خانات تفاعلية (`onDayClick: (dayNumber) -> Unit`).
  - حالات §4.2 مع ألوان من tokens: `Completed`=secondary/أخضر، `Today`=primary ring، `Upcoming`=صلبة+stroke واضح، `Rest`=surface2+أيقونة، `ActiveRecovery`=tint مختلف، `NeedsCatchUp`=amber tint.
  - أحرف أيام الأسبوع + تاريخ، نقطة نوع التمرين، قوس تقدّم، Legend كامل (يشمل كل الحالات الظاهرة).
  - وضعان: **Compact** (شريط في Train) و**Expanded** (تفاصيل اختيارية لكل يوم).
- ملاحظة: مكوّن مشترك commonMain → يجب التحقّق على iOS أيضاً (`:feature:shell:compileKotlinIosSimulatorArm64`).

### 5.4 الشاشة والأحداث
- `MovitTrainEvent`: إضافة `DayClicked(weekNumber, dayNumber)`، `DayPreviewDismissed`.
- `MovitTrainScreen`: ربط لمس اليوم بـ bottom-sheet المعاينة/البدء، وإصلاح حالات تنقّل الأسبوع (ماضٍ/حالي/مستقبل).

### 5.5 النصوص (Strings) — EN + AR
أحرف أيام الأسبوع، تنسيقات التاريخ/النطاق، "اليوم/غداً"، نسخ الراحة/الاستشفاء/اللحاق، نص تقدّم الأسبوع، نص الأيام المقفلة. تُضاف في `TrainStrings.kt` + `strings.xml` + `values-ar/strings.xml`.

### 5.6 الاختبارات
- اختبارات mapper لكل حالة في §4.2، خصوصاً: **إنجاز غير متّصل**، **راحة بين أيام تدريب**، **يوم متعدّد الجلسات جزئي**، **catch-up snap**، **أسبوع بعدد أيام ≠ 7**. (توسيع [`MovitTrainStateTest.kt`](../../../android-poc/feature/train/src/commonTest/kotlin/com/movit/feature/train/MovitTrainStateTest.kt) و [`TrainApiMapperTest.kt`](../../../android-poc/feature/train/src/commonTest/kotlin/com/movit/feature/train/TrainApiMapperTest.kt)).
- اختبار عقد الـ backend الجديد (fixtures + snapshot).

---

## 6. المراحل (Phasing)

| المرحلة | المحتوى | المخرج |
|---------|---------|--------|
| **P0 — مصدر الحقيقة** | عقد `weekCalendar` في الـ backend + إصلاح `CatchUpSuggestion` + fixtures | بيانات per-day صادقة تصل للموبايل |
| **P1 — النماذج والـ mapper** | توسيع نماذج KMP، إعادة كتابة `buildWeekPreview`/`buildWeekOptions`، حذف منطق Missed، اختبارات | حالات صحيحة بلا تخمين (حتى بدون UI جديد) |
| **P2 — المكوّن البصري** | `MovitWeekCalendar` بالحالات الكاملة، التواريخ، النقاط، Legend الكامل، التباين | تقويم واضح احترافي (read-only) + تحقّق iOS |
| **P3 — التفاعل** | لمس اليوم → معاينة/بدء، تنقّل الأسابيع الصحيح، رسالة catch-up | المكوّن يصبح "بوصلة" تفاعلية |
| **P4 — اللمسات** | Motion، A11y، RTL، الحالات الفارغة/offline، score مصغّر | تجربة مكتملة ومصقولة |

---

## 7. معايير القبول (Definition of Done)

- [ ] كل خانة تعكس حقيقة الـ backend per-day (تدريب/راحة/استشفاء/مكتمل/اليوم/قادم) — **صفر تخمين** على الواجهة.
- [ ] لا توجد حالة "Missed" عقابية؛ التأخّر يظهر كرسالة لحاق لطيفة فقط عند snap حقيقي.
- [ ] أيام الراحة والاستشفاء أولى من الدرجة الأولى ومميّزة بصرياً.
- [ ] كل يوم مرتبط بتاريخ/يوم أسبوع حقيقي + "اليوم/غداً".
- [ ] لمس اليوم يفتح معاينة/بدء/ملخّص حسب حالته.
- [ ] Legend كامل يغطّي كل الحالات الظاهرة فعلاً.
- [ ] أسبوع بعدد أيام ≠ 7 يُعرض صحيحاً؛ الأسابيع الأخرى لا تُظهر "today" وهمياً.
- [ ] A11y (وصف لكل خانة، ≥48dp، تمييز شكل+لون) و RTL صحيحان.
- [ ] أخضر على Android **و** iOS (تحقّق `compileKotlinIosSimulatorArm64`)؛ اختبارات mapper خضراء لكل الحالات.

---

## 7.1 حالة التنفيذ (تم — 2026-06-15)

نُفِّذت المراحل P0→P3 بالكامل، وأجزاء من P4:

| الطبقة | التغيير | الحالة |
|--------|---------|--------|
| **Backend P0** | `weekCalendars` per-week/per-day في `trainMode` ([`mobile-home.controller.ts`](../../../backend/src/modules/mobile-sync/mobile-home.controller.ts)) — يُبنى من نفس البيانات المحمّلة (صفر استعلامات جديدة) | ✅ `tsc` نظيف (الأخطاء الـ3 المتبقّية سابقة في `progression-rules-admin`) |
| **DTO** | `WeekCalendarDto/DayDto/WorkoutDto` + حقل في `TrainModeDto` ([`HomeDto.kt`](../../../android-poc/core/network/src/commonMain/kotlin/com/movit/core/network/dto/HomeDto.kt)) | ✅ Android + **iOS** أخضر |
| **Models** | `TrainWeekDayState` صادق (حذف `Missed`) + `TrainWeekDayUi`/`TrainWeekDayDetailUi` | ✅ |
| **Mapper** | `buildCurrentWeek`/`buildWeekOptions`/`mapWeekCalendar` تستهلك الـ backend مباشرة؛ fallback صادق بلا تخمين ([`TrainApiMapper.kt`](../../../android-poc/feature/train/src/commonMain/kotlin/com/movit/feature/train/TrainApiMapper.kt)) | ✅ + اختبار جديد |
| **DS Component** | `MovitWeekStrip` تفاعلي: حالات جديدة، حلقة "اليوم"، شريط تقدّم، نقاط راحة/استشفاء، أحرف أيام، Legend كامل، contentDescription ([`MovitWeekStrip.kt`](../../../android-poc/core/designsystem/src/commonMain/kotlin/com/movit/designsystem/components/MovitWeekStrip.kt)) | ✅ Android + **iOS** أخضر |
| **Interaction** | لمس اليوم → بطاقة تفاصيل inline + بدء/عرض ملخّص (events/VM/UiState/Screen) | ✅ |
| **Strings** | EN map + EN/AR XML: أيام الأسبوع، تقدّم الأسبوع، حالات، Legend `upcoming` | ✅ |
| **Tests** | `feature:train` host tests خضراء (شامل اختبار `weekCalendars` الجديد + fallback) | ✅ |

**تحقّق iOS:** `:core:network` و `:core:designsystem` يُترجمان لـ `iosSimulatorArm64` بنجاح (شمل إصلاح `String.format` سابق في `MovitPoseAnnotationOverlay`). كود الـ mapper/models نقي Kotlin. الحاجز المتبقّي لترجمة `feature:train` على iOS هو كسر **سابق الوجود** في `core:training-engine` (استخدام `synchronized` غير المتاح على Native) — خارج نطاق هذا المكوّن (مُتابَع كمهمة منفصلة).

**مُؤجَّل (مهام منفصلة):**
- توحيد `CatchUpSuggestionDto` مع شكل الـ backend الحقيقي (يمسّ Home banner + Session + ٣ contract tests).
- إصلاح كسر iOS في `core:training-engine` (`synchronized` → expect/actual lock).

## 8. مخاطر ومحاذير

- **اشتقاق التاريخ:** البرنامج completion-based؛ ربط "يوم البرنامج → تاريخ" تقريبي (يعتمد `startDate` + `trainingWeekdays`). الحل الآمن: عرض نسبي ("اليوم/غداً/السبت") عند غياب تاريخ دقيق، لا تواريخ مضلِّلة.
- **اللحاق (Catch-up):** يجب أن تبقى الرسالة تشجيعية لا لومية، ومتوافقة مع snap الـ backend الفعلي.
- **iOS:** المكوّن في commonMain — أي اعتماد JVM-only ممنوع (تذكير سابق: Koin `GlobalContext` JVM-only). تحقّق مبكّر.
- **الواقعية:** بعض التدريب ما زال يرتدّ للـ legacy؛ تأكّد أن مسار البيانات الحيّ (لا `Fake*`) هو ما يغذّي المكوّن قبل اعتبار المرحلة مكتملة.
