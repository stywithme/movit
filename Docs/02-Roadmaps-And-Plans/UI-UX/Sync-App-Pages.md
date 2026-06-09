## **المنهجية المتبعة**

لكل صفحة:

1. قراءة الـ prototype (أقسام، حالات `state-switch`، تدفق `data-flow`)
2. تتبع الـ Legacy في `android-poc/app/src/main`
3. مقارنة تنفيذ KMP في `feature/`* و `core/designsystem`
4. تصنيف: **مطابق / جزئي / placeholder / غائب**

### **Scorecards قابلة للقياس (Pre-06 WS-E — 2026-06-09)**

> التفاصيل الكاملة (تفصيل الأوزان، checklists، فجوات محددة): **[`Page-Scorecards.md`](Page-Scorecards.md)**

| **#** | **الصفحة** | **Scorecard %** | Functional | Visual | DS | i18n | A11y | Tests | iOS |
| ------ | ---------- | --------------- | ---------- | ------ | -- | ---- | ---- | ----- | --- |
| 08 | Home | **91%** | 96% | 88% | 95% | 95% | 75% | 90% | ✅ |
| 01 | Train | **72%** | 72% | 60% | 93% | 87% | 20% | 80% | ✅ |
| 04 | Explore | **75%** | 80% | 65% | 93% | 87% | 20% | 70% | ✅ |
| 09 | Reports | **85%** | 88% | 82% | 93% | 100% | 30% | 80% | ✅ |
| 17 | Report detail | **89%** | 92% | 82% | 93% | 87% | 50% | 75% | ✅ |
| 02 | Session | **48%** | 32% | 40% | 87% | 47% | 25% | 70% | ✅ |
| 05–06 | Library | **78%** | 85% | 78% | 93% | 88% | 55% | 55% | ✅ |
| 10 | Auth | **76%** | 76% | 75% | 93% | 93% | 30% | 50% | ✅ |
| 11 | Profile | **70%** | 68% | 72% | 93% | 93% | 30% | 50% | ✅ |
| 12 | Onboarding | **74%** | 80% | 70% | 93% | 93% | 20% | 50% | ✅ |
| 13 | Assessment | **55%** | 56% | 55% | 87% | 80% | 25% | 0% | ✅ |
| 14 | Level | **68%** | 72% | 68% | 93% | 87% | 35% | 55% | ✅ |

**أوزان المجالات:** Functional 25% · Visual 20% · DS 15% · i18n/RTL 15% · A11y 10% · Tests 10% · iOS 5%.

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
| **07** | Program detail | `ProgramDetailActivity`                                | `ProgramDetailScreen`       | **~72%**    | `WorkoutSessionRoute`     |
| **08** | Home           | `HomeFragment`                                         | `MovitHomeScreen`           | ~91% scorecard | ✅ `MovitHomeApiBridge`    |
| **09** | Reports        | `HistoryFragment` + 3 tabs                             | `MovitReportsScreen`        | ~85% scorecard | ✅ `MovitReportsApiBridge` |
| **10** | Auth           | `Splash/SignIn/SignUp`                                 | `MovitAuthScreen` (`feature:account`) | ~76% scorecard | ✅ Ktor auth API |
| **11** | Profile        | `ProfileActivity`                                      | `MovitProfileScreen` (تبويب Account) | ~70% scorecard | ✅ session read |
| **12** | Onboarding     | `ProfileOnboardingActivity` (7 خطوات)                  | `MovitOnboardingScreen`     | ~74% scorecard | ✅ training-profile PUT |
| **13** | Assessment     | `PreScreening` → `AssessmentSession`                   | `MovitAssessmentScreen` (بدون كاميرا) | ~55% scorecard | preview/fake |
| **14** | Level & plan   | `LevelProfileActivity` + `PlanOverviewActivity`        | `MovitLevelScreen`          | ~58% scorecard | ✅ / fake fallback |
| **15** | Program flow   | `ProgramList/Day/WeeklyReport`                         | **غائب** كتدفق              | ~15%        | غير مباشر                 |
| **16** | Workout flow   | `WorkoutCustomize` → `WorkoutRun` → `TrainingActivity` | **جزئي جداً**               | ~20%        | لا                        |
| **17** | Report detail  | `WorkoutReportActivity`                                | `ReportDetailScreen`        | ~92% scorecard | ✅ metrics API + a11y + visual polish |


**الخلاصة:** لا صفحة KMP مطابقة **تماماً** بعد. **الأعلى scorecard:** Report detail (89%) · Reports (85%) · Home (79%). **الأدنى:** Session (48%) · Library (55%) · Assessment (55%). Account 10–14: **أول إصدار KMP** (55–76%) — ليست «غائبة».

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

**08 — Home** (حالات: scan-only / alert / empty / active…) — **scorecard 91%**

- **Legacy:** `HomeRepository` → `/api/mobile/home`، trainMode، level، journey
- **KMP:** `MovitHomeScreen` — تحية + metrics + level + alert + program + today + journey + activity + quick actions
- **مغلق:** Level → `MovitInnerRoute.LevelProfile` · hero progress من API · icon-box quick actions · مكوّنات `components/` موصولة · a11y على CTAs الرئيسية
- **فجوات متبقية:** RTL visual QA · dark mode QA · pull-to-refresh · report detail من activity rows

**09 — Reports** (3 tabs: Overview / Exercises / Trends)

- **Legacy:** `ReportsHubViewModel` + `SwipeRefreshLayout` + TabLayout underline
- **KMP:** `MovitUnderlineTabRow` + `PullToRefreshBox` + charts + navigation → 17
- **فجوات:** upsell Pro كامل، A11y charts متقدمة

---

### **Auth & Onboarding**

**10 — Auth** (Splash / Intro / Sign in / Sign up / Forgot) — **scorecard 76%**

- **Legacy:** `SplashActivity` → `OnboardingActivity` → `SignIn/SignUp` + `ApiClient.authApi`
- **KMP:** `MovitAuthScreen` — تدفق كامل + `POST login|register` عبر `feature:account`
- **فجوات:** Google stub؛ `SignUpClicked` handler فارغ؛ a11y intro icons

**12 — Profile onboarding** (7 خطوات) — **scorecard 74%**

- **Legacy:** `ProfileOnboardingActivity` + 7 fragments → `putTrainingProfile`
- **KMP:** `MovitOnboardingScreen` — 7 خطوات + `PUT training-profile`
- **فجوات:** validation أعمق؛ a11y

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

**17 — Report detail** (4 صفحات: Overview / Form / Fatigue / Tips) — **scorecard 92%**

- **Legacy:** `WorkoutReportActivity` + `ReportViewModel` + 8+ fragments
- **KMP:** `ReportDetailScreen` — 4 صفحات + API metrics + a11y
- **فجوات:** Share/Export coming soon (shell message)؛ joints من backend غير متوفرة بعد

---

### **Library & Programs**

**05 — Exercises** | **06 — Workouts**

- **Legacy:** grid/list كامل مع صور وبادجات
- **KMP:** بحث + chips + grid/list + filter sheet + i18n + empty state + صور (Android)
- **فجوات متبقية:** iOS image loader KMP؛ A11y كامل؛ Prepare/Program detail i18n

**07 — Program detail** (Overview / Edit) — **scorecard ~72%**

- **Legacy:** `ProgramDetailActivity` + weeks accordion + enroll CTA + pause/resume
- **KMP:** `ProgramDetailScreen` — hero، stat grid 2×2، tabs، week strip، day timeline، copy card، edit (reason/scope/settings)، dock CTA → `WorkoutSessionKeys`
- **Page-Spec:** [`Page-Specs/Program-Detail-Page-Spec.md`](Page-Specs/Program-Detail-Page-Spec.md)
- **فجوات:** enroll API حقيقي، weekly report (15)، drag/reorder جلسات، محرر معاملات تمرين، صورة hero من الشبكة

**15 — Program flow** (list / week / weekly report)

- **Legacy:** `ProgramListActivity` + `ProgramDayActivity` + `WeeklyReportActivity`
- **KMP:** فقط `ProgramDetailScreen` جزئي
- **فجوات:** القائمة، خطة الأسبوع، التقرير الأسبوعي — **كلها غائبة**

---

### **Assessment & Level**

**13 — Assessment** — **scorecard 55%** | **14 — Level & plan** — **scorecard 68%**

- **Legacy:** PAR-Q → body scan → results؛ `LevelProfileActivity` + `PlanOverviewActivity`
- **KMP:** `MovitAssessmentScreen` (PAR-Q + placeholder كاميرا) · `MovitLevelScreen` (Profile/Plan)
- **فجوات:** لا كاميرا/ML؛ Assessment بدون VM tests؛ Level: celebration + recommended programs row

---

### **Account**

**11 — Profile** — **scorecard 70%**

- **Legacy:** `ProfileActivity` — avatar، Pro، settings، training profile، logout
- **KMP:** `MovitProfileScreen` — تبويب Account (إعدادات، Pro، logout، روابط)
- **فجوات:** Language/Appearance `onClick = null`؛ Haptic معطّل؛ a11y

---

## **أخطاء ربط يجب تصحيحها**


| **الخطأ**                       | **الصحيح**                              |
| ------------------------------- | --------------------------------------- |
| 02-session = `TrainingActivity` | 02 = `ProgramWorkoutActivity` (builder) |
| 03-prepare = كل التدريب         | 03 = pre-workout + rest timer فقط       |
| 16 = نفس 02                     | 16 = customize + run + camera           |
| ~~Profile → Components~~      | ✅ Profile → `MovitProfileScreen` (2026-06-09) |


---

## **ترتيب التنفيذ المقترح (للوصول لتطابق كامل)**

المرحلة A — إكمال ما بدأ (أعلى تأثير)

  1. ~~17 Report detail~~ ✅ شاشة 4 صفحات + ربط Reports → inner (bridge تفصيلي لاحقاً)

  2. 02 Session (sheets + API ProgramWorkout)

  3. ~~03 Prepare/Rest~~ ✅ حالتان + ربط `LegacyTrainingLauncher` (مؤقت ثابت)

  4. ~~07 Program detail~~ ✅ hero + tabs + weeks + edit shell + session start

  ~~5. 09 → 17 navigation~~ ✅ (2026-06-09)

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


