## **المنهجية المتبعة**

لكل صفحة:

1. قراءة الـ prototype (أقسام، حالات `state-switch`، تدفق `data-flow`)
2. تتبع الـ Legacy في `android-poc/app/src/main`
3. مقارنة تنفيذ KMP في `feature/`* و `core/designsystem`
4. تصنيف: **مطابق / جزئي / placeholder / غائب**

---

## **خريطة التدفقات (من** `_nav-pages.html`**)**


| `data-flow`     | **الصفحات**                                               |
| --------------- | --------------------------------------------------------- |
| *(تبويب رئيسي)* | 01 Train، 04 Explore، 08 Home، 09 Reports                 |
| `training`      | 02 Session، 03 Prepare، 16 Workout flow، 17 Report detail |
| `library`       | 05 Exercises، 06 Workouts                                 |
| `program`       | 07 Program، 15 Program flow                               |
| `onboarding`    | 10 Auth، 12 Profile onboarding                            |
| `assessment`    | 13 Assessment، 14 Level & plan                            |


---

## **الجدول الرئيسي — 18 صفحة**


| **#**  | **Prototype**  | **Legacy الرئيسي**                                     | **KMP الحالي**              | **التطابق** | **API Bridge**            |
| ------ | -------------- | ------------------------------------------------------ | --------------------------- | ----------- | ------------------------- |
| **00** | Components     | `component_*.xml` + views مبعثرة                       | `MovitComponentsScreen`     | ~70% جزئي   | لا                        |
| **01** | Train          | `TrainFragment`                                        | `MovitTrainScreen`          | ~65% جزئي   | ✅ `MovitTrainApiBridge`   |
| **02** | Session day    | `ProgramWorkoutActivity`                               | `WorkoutSessionScreen`      | ~40% جزئي   | غير مباشر                 |
| **03** | Prepare & rest | `PreWorkoutActivity`                                   | `ExercisePrepareScreen`     | ~35% جزئي   | غير مباشر                 |
| **04** | Explore        | `ExploreFragment`                                      | `MovitExploreScreen`        | ~70% جزئي   | ✅ `MovitExploreApiBridge` |
| **05** | Exercises      | `ExerciseListActivity`                                 | `ExercisesLibraryScreen`    | ~60% جزئي   | غير مباشر                 |
| **06** | Workouts       | `WorkoutListActivity`                                  | `WorkoutsLibraryScreen`     | ~55% جزئي   | غير مباشر                 |
| **07** | Program detail | `ProgramDetailActivity`                                | `ProgramDetailScreen`       | ~45% جزئي   | غير مباشر                 |
| **08** | Home           | `HomeFragment`                                         | `MovitHomeScreen`           | ~75% جزئي   | ✅ `MovitHomeApiBridge`    |
| **09** | Reports        | `HistoryFragment` + 3 tabs                             | `MovitReportsScreen`        | ~70% جزئي   | ✅ `MovitReportsApiBridge` |
| **10** | Auth           | `Splash/SignIn/SignUp`                                 | **غائب**                    | 0%          | لا                        |
| **11** | Profile        | `ProfileActivity`                                      | **غائب** (يفتح Components!) | 0%          | لا                        |
| **12** | Onboarding     | `ProfileOnboardingActivity` (7 خطوات)                  | **غائب**                    | 0%          | لا                        |
| **13** | Assessment     | `PreScreening` → `AssessmentSession`                   | **غائب**                    | 0%          | لا                        |
| **14** | Level & plan   | `LevelProfileActivity` + `PlanOverviewActivity`        | **غائب**                    | 0%          | لا                        |
| **15** | Program flow   | `ProgramList/Day/WeeklyReport`                         | **غائب** كتدفق              | ~15%        | غير مباشر                 |
| **16** | Workout flow   | `WorkoutCustomize` → `WorkoutRun` → `TrainingActivity` | **جزئي جداً**               | ~20%        | لا                        |
| **17** | Report detail  | `WorkoutReportActivity`                                | `ReportDetailScreen`        | ~85% جزئي   | preview + slug fallback   |


**الخلاصة:** لا صفحة KMP مطابقة **تماماً** بعد. الأقرب: **08 Home** و **04 Explore** و **09 Reports**. الأبعد: **10–14** و **17**.

---

## **تفصيل صفحة بصفحة**

### **Foundation**

**00 — Components**

- **Legacy:** لا شاشة واحدة؛ مكوّنات في `ui/components/` و `component_*.xml`
- **KMP:** كتالوج DS كامل تقريباً
- **فجوات:** macro calories card، coach card، accent blue/coral، horizontal workout cards، difficulty dots، program card كامل، glass float-pill على hero

---

### **التبويبات الرئيسية (Navbar)**

**01 — Train** (`state-switch`: no program / active / rest / day done / complete)

- **Legacy:** `TrainFragment` — 5 حالات، week nav، browse programs، sessions expandable، charts
- **KMP:** الحالات الخمس موجودة منطقياً
- **فجوات حرجة:**
  - لا تنقل أسبوع (←/→) في `TrainWeekPreview`
  - No program: بدون hero lime + صور + Start لكل برنامج
  - Session cards: بدون thumbnails
  - Program complete: بدون trophy ring وCTAs
  - Form trend: بدون delta "+5% vs last week"

**04 — Explore** (`data-flow=library`)

- **Legacy:** بحث + فلاتر + قوائم أفقية → `PreWorkout` / `WorkoutDetail`
- **KMP:** أقسام Recommended / Workouts / Exercises / Programs
- **فجوات:** زر Filter، muscle-strip، workout-intro، focus pills، **صور الوسائط**، chips فرعية للتمارين

**08 — Home** (حالات: scan-only / alert / empty / active…)

- **Legacy:** `HomeRepository` → `/api/mobile/home`، trainMode، level، journey
- **KMP:** أقسام مطابقة تقريباً
- **فجوات:** hero progress مثبت 0%، quick actions بدون icon-box، recent activity styling، Level card لا يفتح 14

**09 — Reports** (3 tabs: Overview / Exercises / Trends)

- **Legacy:** `ReportsHubViewModel` + locked Pro state
- **KMP:** 3 تبويبات + charts
- **فجوات:** tabs pill بدل underline، لا pull-to-refresh، **لا navigation لـ 17 Report detail**

---

### **Auth & Onboarding**

**10 — Auth** (Splash / Intro / Sign in / Sign up / Forgot)

- **Legacy:** `SplashActivity` → `OnboardingActivity` → `SignIn/SignUp` + `ApiClient.authApi`
- **KMP:** **لا شيء**

**12 — Profile onboarding** (7 خطوات)

- **Legacy:** `ProfileOnboardingActivity` + 7 fragments → `putTrainingProfile`
- **KMP:** **لا شيء**

---

### **Training flow (*`*data-flow=training`**)**

**02 — Session day** ⚠️ **تصحيح مهم**

- **Legacy الصحيح:** `ProgramWorkoutActivity` (بناء يوم البرنامج: edit، swap، reorder، rest blocks)
- **ليس** `TrainingActivity` (الكاميرا الحية — هذا جزء من 16)
- **KMP:** `WorkoutSessionScreen` — هيكل عام موجود
- **فجوات كبيرة:**
  - لا thumbnails + stat-chips (sets×reps، weight، rest)
  - Edit: swap/delete/drag **غير فعّال**
  - لا bottom sheets (Swap exercise، Edit details)
  - لا ربط بـ `DayCustomizationStore` / `ProgramRepository`

**03 — Prepare & rest** (`state-switch`: prepare / rest)

- **Legacy:** `PreWorkoutActivity` → Start → `TrainingActivity`
- **KMP:** `ExercisePrepareScreen` — prepare فقط
- **فجوات:**
  - **لا Rest timer state** (timer حي، pause، +15s، Skip، Up Next)
  - لا Instructions مرقّمة ولا Target muscles chips
  - `onStart` **فارغ** في `MovitInnerHost`
  - Action dock خاطئ (timer 0:00 بدل Ready to train + Start)

**16 — Workout flow** (customize / run)

- **Legacy:** `WorkoutCustomizeActivity` → `WorkoutRunActivity` → `TrainingActivity`
- **KMP:** جزء customize في Session + Prepare جزئي
- **فجوات:** لا customize screen منفصلة، **لا workout run sequencer**، **لا camera overlay**

**17 — Report detail** (4 صفحات: Overview / Form / Fatigue / Tips)

- **Legacy:** `WorkoutReportActivity` + `ReportViewModel` + 8+ fragments
- **KMP:** `InnerPlaceholderScreen` فقط
- **فجوات:** **كل المحتوى غائب** — hero score، joint analysis، best/worst rep، fatigue index، tips، share، dots navigation

---

### **Library & Programs**

**05 — Exercises** | **06 — Workouts**

- **Legacy:** grid/list كامل مع صور وبادجات
- **KMP:** بحث + chips + grid/list
- **فجوات:** صور، badge keys (Beginner/Equipment)، filter button، tag عدد العناصر، wide media layout في 06

**07 — Program detail** (Overview / Edit)

- **Legacy:** `ProgramDetailActivity` + weeks accordion + enroll CTA
- **KMP:** Overview بسيط + Edit panel نصي
- **فجوات:** Edit tab في النموذج **أعمق بكثير** (week strip، day timeline، reason cards، toggles، drag sessions)

**15 — Program flow** (list / week / weekly report)

- **Legacy:** `ProgramListActivity` + `ProgramDayActivity` + `WeeklyReportActivity`
- **KMP:** فقط `ProgramDetailScreen` جزئي
- **فجوات:** القائمة، خطة الأسبوع، التقرير الأسبوعي — **كلها غائبة**

---

### **Assessment & Level**

**13 — Assessment** | **14 — Level & plan**

- **Legacy:** PAR-Q → body scan → results؛ `LevelProfileActivity` + `PlanOverviewActivity`
- **KMP:** Home فيه CTA "Body scan" لكن **لا شاشات**
- **فجوات:** التدفق بالكامل خارج KMP

---

### **Account**

**11 — Profile**

- **Legacy:** `ProfileActivity` — avatar، Pro، settings، training profile، logout
- **KMP:** النقر على البروفايل يفتح **Components catalog** — خطأ واضح
- **فجوات:** الصفحة بالكامل

---

## **أخطاء ربط يجب تصحيحها**


| **الخطأ**                       | **الصحيح**                              |
| ------------------------------- | --------------------------------------- |
| 02-session = `TrainingActivity` | 02 = `ProgramWorkoutActivity` (builder) |
| 03-prepare = كل التدريب         | 03 = pre-workout + rest timer فقط       |
| 16 = نفس 02                     | 16 = customize + run + camera           |
| Profile → Components            | Profile → `11-profile.html`             |


---

## **ترتيب التنفيذ المقترح (للوصول لتطابق كامل)**

المرحلة A — إكمال ما بدأ (أعلى تأثير)

  1. ~~17 Report detail~~ ✅ شاشة 4 صفحات + ربط Reports → inner (bridge تفصيلي لاحقاً)

  2. 02 Session (sheets + API ProgramWorkout)

  3. 03 Prepare/Rest (حالتان + ربط TrainingActivity legacy)

  4. 07 Program Edit tab

  5. 09 → 17 navigation

المرحلة B — تحسين التبويبات

  6. 01 Train (week nav، media، program complete hero)

  7. 04/05/06 Library (صور + فلاتر + badges)

  8. 08 Home (progress hero، quick actions، level link)

المرحلة C — تدفقات كاملة

  9. 15 Program flow

  10. 16 Workout run (camera خارج نطاق Phase 05 لكن UI run مطلوب)

المرحلة D — حساب وتقييم

  11. 10 Auth

  12. 11 Profile (إصلاح routing)

  13. 12 Onboarding

  14. 13 Assessment

  15. 14 Level & plan

---

## **ما الذي أنصح به الآن؟**

المطلوب **تطابق تام** لـ 18 صفحة — عمل كبير متعدد الجلسات. الأنسب:

1. **صفحة بصفحة** حسب الترتيب أعلاه
2. لكل صفحة: spec قصير + checklist من الـ prototype + إصلاح + مقارنة بصرية
3. البدء بـ **17 Report detail** ثم **02 Session** لأنهما الأكثر وضوحاً في الفجوة  


