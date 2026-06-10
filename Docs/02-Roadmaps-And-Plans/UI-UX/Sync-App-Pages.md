> **نِسب الإكمال:** المصدر الوحيد [`Page-Scorecards.md`](Page-Scorecards.md). **أرقام الكود** (مفاتيح · اختبارات · DS): [`generated/Docs-Stats-Snapshot.md`](generated/Docs-Stats-Snapshot.md).

## **المنهجية المتبعة**

لكل صفحة:

1. قراءة الـ prototype (أقسام، حالات `state-switch`، تدفق `data-flow`)
2. تتبع الـ Legacy في `android-poc/app/src/main`
3. مقارنة تنفيذ KMP في `feature/`* و `core/designsystem`
4. تصنيف: **مطابق / جزئي / placeholder / غائب**

### **Scorecards قابلة للقياس (Pre-06 WS-E — 2026-06-10)**

> التفاصيل الكاملة (تفصيل الأوزان، checklists، فجوات محددة): **[`Page-Scorecards.md`](Page-Scorecards.md)**

| **#** | **الصفحة** | **Scorecard %** | Functional | Visual | DS | i18n | A11y | Tests | iOS |
| ------ | ---------- | --------------- | ---------- | ------ | -- | ---- | ---- | ----- | --- |
| 08 | Home | **92%** | 98% | 88% | 95% | 95% | 75% | 92% | ✅ |
| 01 | Train | **86%** | 88% | 85% | 93% | 93% | 50% | 90% | ✅ |
| 04 | Explore | **87%** | 92% | 82% | 93% | 100% | 25% | 88% | ✅ |
| 09 | Reports | **85%** | 90% | 82% | 93% | 100% | 30% | 80% | ✅ |
| 17 | Report detail | **92%** | 92% | 90% | 93% | 95% | 90% | 85% | ✅ |
| 02 | Session | **84%** | 88% | 75% | 90% | 90% | 62% | 88% | ✅ |
| 03 | Prepare & rest | **80%** | 88% | 65% | 93% | 95% | 40% | 80% | ✅ |
| 16 | Workout flow | **66%** | 55% | 50% | 93% | 90% | 35% | 60% | ✅ |
| 05–06 | Library | **78%** | 85% | 78% | 93% | 88% | 55% | 55% | ✅ |
| 07 | Program detail | **72%** | 76% | 70% | 93% | 85% | 25% | 65% | ✅ |
| 10 | Auth | **85%** | 86% | 78% | 93% | 93% | 75% | 75% | ✅ |
| 11 | Profile | **86%** | 88% | 85% | 93% | 93% | 65% | 78% | ✅ |
| 12 | Onboarding | **85%** | 92% | 82% | 93% | 93% | 45% | 78% | ✅ |
| 13 | Assessment | **74%** | 72% | 68% | 93% | 93% | 40% | 55% | ✅ |
| 14 | Level | **68%** | 72% | 68% | 93% | 87% | 35% | 55% | ✅ |
| 00 | Components | **~70%** | ~75% | ~68% | 95% | ~60% | ~40% | ~50% | ✅ |
| 15 | Program flow | **77%** | 78% | 68% | 93% | 88% | 35% | 70% | ✅ |

**أوزان المجالات:** Functional 25% · Visual 20% · DS 15% · i18n/RTL 15% · A11y 10% · Tests 10% · iOS 5%.

### **تحقق Phase 05 (2026-06-10)**

- **السبب الجذري لظهور «فجوات» في هذا الملف:** معظمها **تأخر توثيق** في أقسام «تفصيل صفحة بصفحة» (لا تزال تعكس حالة ما قبل 2026-06-09)، وليس غياب التنفيذ. التحقق أُجري بقراءة `android-poc/feature/*` و `MovitInnerHost.kt`.
- **Gradle (تحقق 15/16/17):** `:feature:library:testDebugUnitTest` · `:feature:reports:testDebugUnitTest` — **BUILD SUCCESSFUL** (2026-06-10). أعداد `@Test`: [`generated/Docs-Stats-Snapshot.md`](generated/Docs-Stats-Snapshot.md).
- **Gradle (7 موديولات feature):** `:feature:home|train|explore|reports|library|account|shell:testDebugUnitTest` — **BUILD SUCCESSFUL** (2026-06-10).
- **تحقق 10–14 (2026-06-10):** `:feature:account:testDebugUnitTest` · `:feature:shell:testDebugUnitTest` — **BUILD SUCCESSFUL** بعد إصلاحات auth i18n · assessment a11y · shell onboarding routing tests.
- **مؤجّل صراحةً لـ Phase 07:** كاميرا/pose · `TrainingActivity` workout-mode · `LegacyTrainingLauncher` كجسر مؤقت لـ Prepare/Explore/Run.
- **أُغلق (2026-06-10 — audit 03/04):** Prepare rest tick حي · Explore `PullToRefreshBox`.
- **دين موثّق (ليس «فجوة تنفيذ»):** Visual QA / A11y يدوي (~20–45% على معظم الصفحات) · RTL/dark mode مراجعة جهاز.

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
| **01** | Train          | `TrainFragment`                                        | `MovitTrainScreen`          | **86%** scorecard | ✅ `MovitTrainApiBridge`   |
| **02** | Session day    | `ProgramWorkoutActivity`                               | `WorkoutSessionScreen`      | **84%** scorecard | ✅ `SharedWorkoutSessionRepository` |
| **03** | Prepare & rest | `PreWorkoutActivity`                                   | `ExercisePrepareScreen`     | 80% scorecard | `LegacyTrainingLauncher` (Phase 07) |
| **04** | Explore        | `ExploreFragment`                                      | `MovitExploreScreen`        | 87% scorecard | ✅ `MovitExploreApiBridge` |
| **05** | Exercises      | `ExerciseListActivity`                                 | `ExercisesLibraryScreen`    | ~78% scorecard | explore cache             |
| **06** | Workouts       | `WorkoutListActivity`                                  | `WorkoutsLibraryScreen`     | ~78% scorecard | explore cache             |
| **07** | Program detail | `ProgramDetailActivity`                                | `ProgramDetailScreen`       | **~72%**    | `WorkoutSessionRoute`     |
| **08** | Home           | `HomeFragment`                                         | `MovitHomeScreen`           | ~92% scorecard | ✅ `MovitHomeApiBridge`    |
| **09** | Reports        | `HistoryFragment` + 3 tabs                             | `MovitReportsScreen`        | ~85% scorecard | ✅ `MovitReportsApiBridge` |
| **10** | Auth           | `Splash/SignIn/SignUp`                                 | `MovitAuthScreen` (`feature:account`) | **85%** scorecard | ✅ Ktor auth API · `auth_error_*` i18n |
| **11** | Profile        | `ProfileActivity`                                      | `MovitProfileScreen` (تبويب Account) | ~86% scorecard | ✅ session read |
| **12** | Onboarding     | `ProfileOnboardingActivity` (7 خطوات)                  | `MovitOnboardingScreen`     | ~85% scorecard | ✅ training-profile PUT |
| **13** | Assessment     | `PreScreening` → `AssessmentSession`                   | `MovitAssessmentScreen` (بدون كاميرا) | **74%** scorecard | PAR-Q×7 · API/fake · كاميرا **Phase 07** |
| **14** | Level & plan   | `LevelProfileActivity` + `PlanOverviewActivity`        | `MovitLevelScreen`          | ~68% scorecard | ✅ / fake fallback |
| **15** | Program flow   | `ProgramList/Day/WeeklyReport`                         | `ProgramList/WeekPlan/WeeklyReport` | ~77% scorecard | **preview/fake (مفتوح)** |
| **16** | Workout flow   | `WorkoutCustomize` → `WorkoutRun` → `TrainingActivity` | `WorkoutCustomizeScreen` · `WorkoutRunScreen` | ~66% scorecard | **`LegacyTrainingLauncher` (مفتوح — Phase 07)** |
| **17** | Report detail  | `WorkoutReportActivity`                                | `ReportDetailScreen`        | ~92% scorecard | ✅ metrics API + a11y + visual polish |


**الخلاصة:** لا صفحة KMP مطابقة **تماماً** بعد. **الأعلى scorecard:** Report detail (92%) · Home (92%) · Explore (87%). **الأدنى:** Workout flow (66%) · Level (68%). **تحقق 2026-06-10:** audit 00/15/16/17 + `:feature:library|reports:testDebugUnitTest` ✅.

---

## **تفصيل صفحة بصفحة**

### **Foundation**

**00 — Components** — **scorecard ~70%**

- **Legacy:** لا شاشة واحدة؛ مكوّنات في `ui/components/` و `component_*.xml`
- **KMP:** `MovitComponentsScreen` / `MovitComponentsCatalogContent` — 25+ قسم (palette → premium)
- **✅ أُغلق Phase 05:** أغلب مكوّنات الصفحات (buttons، cards، charts، filter row، media cards، skeleton)
- **مفتوح:** macro calories · coach card · accent blue/coral · horizontal workout cards · difficulty dots · program card كامل · i18n للكتالوج

---

### **التبويبات الرئيسية (Navbar)**

**01 — Train** (`state-switch`: no program / active / rest / day done / complete) — **scorecard 86%** (2026-06-10)

- **Legacy:** `TrainFragment` — 5 حالات، week nav، browse programs، sessions expandable، charts
- **KMP:** `MovitTrainScreen` — الحالات الخمس + week nav + report section + quick actions
- **✅ أُغلق (2026-06-09):** week nav · no-program hero/cards · session thumbnails · program-complete trophy/CTAs · form-trend delta · `train_prefs_later`
- **✅ أُغلق (2026-06-10):** `StartProgramClicked` → `OpenProgramWeekPlan` · `TrainFeaturedProgramCard` → `MovitRemoteImage` · `train_a11y_*` على CTAs/chart/quick actions
- **OPEN:** RTL visual QA · A11y على `MovitSessionCard` expandable · iOS صور البرامج

**04 — Explore** (`data-flow=library`) — **scorecard 87%**

- **Legacy:** بحث + فلاتر + قوائم أفقية → `PreWorkout` / `WorkoutDetail`
- **KMP:** أقسام Recommended / Workouts / Exercises / Programs + Filter toolbar كامل
- **مغلق (2026-06-09):** Filter button، muscle-strip، workout-intro، focus pills، صور وسائط، exercise sub-chips، shell effects للتنقل
- **✅ أُغلق (2026-06-10):** pull-to-refresh (`PullToRefreshBox` + `RefreshRequested`)
- **متبقّي:** A11y · تحميل صور iOS حقيقي

**08 — Home** (حالات: scan-only / alert / empty / active…) — **scorecard 92%** (تحقق 2026-06-10)

- **Legacy:** `HomeRepository` → `/api/mobile/home`، trainMode، level، journey
- **KMP:** `MovitHomeScreen` — تحية + metrics + level + alert + program + today + journey + activity + quick actions
- **مغلق:** Level → `MovitInnerRoute.LevelProfile` · hero progress من API · icon-box quick actions · مكوّنات `components/` موصولة · a11y على CTAs الرئيسية
- **✅ أُغلق (2026-06-10):** activity rows → `RecentActivityClicked(exerciseId)` → `MovitHomeEffect.OpenReportDetail` → `MovitInnerRoute.ReportDetail` (`MovitAppShellViewModel.kt`)
- **فجوات متبقية:** RTL visual QA · dark mode QA · pull-to-refresh · `HomeReportPreview` card → Reports tab فقط

**09 — Reports** (3 tabs: Overview / Exercises / Trends) — **scorecard 85%** (تحقق 2026-06-10)

- **Legacy:** `ReportsHubViewModel` + `SwipeRefreshLayout` + TabLayout underline
- **KMP:** `MovitUnderlineTabRow` + `PullToRefreshBox` + KPI/charts + `ExerciseReportClicked` → inner route 17
- **مغلق:** Pro gate · loading/error/locked/empty · pull-to-refresh · trends parity (improvement + volume + attendance + fatigue) · score pills ملونة
- **فجوات:** upsell Pro كامل (`OpenUpgrade` placeholder) · A11y charts متقدمة (نقطة بنقطة)

---

### **Auth & Onboarding**

**10 — Auth** (Splash / Intro / Sign in / Sign up / Forgot) — **scorecard 86%** (تحقق 2026-06-10)

- **Legacy:** `SplashActivity` → `OnboardingActivity` → `SignIn/SignUp` + `ApiClient.authApi`
- **KMP:** `MovitAuthScreen` — تدفق كامل + `POST login|register` عبر `feature:account`
- **Bootstrap:** `resolveStartupInnerStack` في shell init · `emitPostAuthNavigation` → onboarding أو shell
- **✅ أُغلق (2026-06-10):** `auth_error_*` validation en/ar · Google stub عبر `ShowLocalizedMessage`
- **فجوات:** Google OAuth فعلي · launcher إنتاجي ما زال `SplashActivity` legacy

**11 — Profile** — **scorecard 86%** (تحقق 2026-06-10)

- **Legacy:** `ProfileActivity` — avatar، Pro، settings، training profile، logout
- **KMP:** `MovitProfileScreen` — hero، Pro/Free، preferences، account، subscription
- **Routing:** Assessment / Level / Onboarding / Auth عبر `MovitProfileEffect` + shell inner stack
- **فجوات:** edit avatar · exercise settings · billing حقيقي · training summary من API

**12 — Profile onboarding** (7 خطوات) — **scorecard 85%** (تحقق 2026-06-10)

- **Legacy:** `ProfileOnboardingActivity` + 7 fragments → `putTrainingProfile`
- **KMP:** `MovitOnboardingScreen` — 7 خطوات + `PUT training-profile` + validation/payload parity
- **Strings:** `onboarding_*` + `onboarding_error_*` + `onboarding_progress_a11y` en/ar
- **فجوات:** font-scale a11y؛ Experience slider (legacy) ≠ duration chips (prototype)

---

### **Training flow (*`*data-flow=training`**)**

**02 — Session day** — **scorecard 84%** (2026-06-10)

- **Legacy:** `ProgramWorkoutActivity` (محرر يوم البرنامج)
- **KMP:** `WorkoutSessionScreen` + `SharedWorkoutSessionRepository`
- **✅ مُنجز:** thumbnails · stat-chips · swap/edit/add/rest sheets · drag-reorder · save API · Start → `ExercisePrepare` (03) عبر `firstExerciseId()` · `session_a11y_start_workout`
- **OPEN:** multi-workout day cards · catch-up dialog · skip-warmup · iOS thumbnails
- **DEFERRED (Phase 07):** Prepare `onStart` → كاميرا/`TrainingActivity` workout-mode

**03 — Prepare & rest** (`state-switch`: prepare / rest) — **scorecard 80%**

- **Legacy:** `PreWorkoutActivity` → Start → `TrainingActivity`
- **KMP:** `ExercisePrepareScreen` + `ExercisePrepareViewModel` — حالتان Prepare / Rest
- **✅ أُغلق Phase 05 (2026-06-09، مُحقَّق 2026-06-10):**
  - Rest UI: Up Next tag · dock (pause · +15s · Skip) · `enterRestMode` / `skipRest`
  - Prepare dock: Ready to train + Start (`prepare_*` / `session_*` keys)
  - Instructions مرقّمة · Target muscles chips · إعداد كاميرا · stats
  - `onStart` في `MovitInnerHost`: مع `workoutId` → `WorkoutRun`؛ وإلا → `LaunchLegacyCameraTraining`
- **✅ أُغلق (2026-06-10):** عدّاد راحة حي (tick كل ثانية + auto-return عند 0)
- **متبقي (حقيقي):**
  - صور hero شبكية + pose variant picker (placeholder حرف أول التمرين)
  - كاميرا/تدريب فعلي → **Phase 07** (`LegacyTrainingLauncher`)

**16 — Workout flow** (customize / run) — **scorecard 66%**

- **Legacy:** `WorkoutCustomizeActivity` → `WorkoutRunActivity` → `TrainingActivity`
- **KMP:** `WorkoutCustomizeScreen` · `WorkoutRunScreen` — UI shell Phase 05؛ كاميرا عبر `LegacyTrainingLauncher`
- **التدفق:** Session Start → Customize → Run · Prepare (مع `workoutId`) → Run · Explore → legacy camera
- **✅ أُغلق (2026-06-10):** Customize → Run → Start exercise · `WorkoutFlowCache` · sets/rest stepper · a11y progress/sequence
- **مفتوح:** camera overlay · workout-mode `TrainingActivity` (**Phase 07**) · persist customize API · previous-form fake · drag-reorder/حذف في customize (Session 02 فقط)

**17 — Report detail** (4 صفحات: Overview / Form / Fatigue / Tips) — **scorecard 92%**

- **Legacy:** `WorkoutReportActivity` + `ReportViewModel` + 8+ fragments
- **KMP:** `ReportDetailScreen` — 4 صفحات + `SharedReportDetailRepository` → metrics API + a11y + visual polish
- **✅ أُغلق Phase 05:** 4 tabs · mapper · loading/error · float pills · chart/joint a11y
- **مفتوح:** Share/Export platform sheet (shell placeholder) · joints من backend عند توفر الحقل

---

### **Library & Programs**

**05 — Exercises** | **06 — Workouts** — **scorecard 78%**

- **Legacy:** grid/list كامل مع صور وبادجات
- **KMP:** بحث + chips + grid/list + filter sheet + i18n + empty state + صور (Android)
- **✅ أُغلق Phase 05:** `library_*` EN/AR · filter sheet · badges · `LibraryMediaImage` (Android Coil)
- **تحقق 2026-06-10:** لا فجوات تنفيذ إضافية — shell → `ExercisePrepare` / `WorkoutSession` موصول
- **متبقي:** iOS image loader KMP · A11y كامل

**07 — Program detail** (Overview / Edit) — **scorecard 72%** (تحقق 2026-06-10)

- **Legacy:** `ProgramDetailActivity` + weeks accordion + enroll CTA + pause/resume
- **KMP:** `ProgramDetailScreen` — hero، stat grid 2×2، tabs، week strip، day timeline (Done/Next/Rest)، copy card، edit (reason/scope/settings/pause)، dock CTA → `WorkoutSessionKeys` → `WorkoutSessionRoute`
- **Page-Spec:** [`Page-Specs/Program-Detail-Page-Spec.md`](Page-Specs/Program-Detail-Page-Spec.md)
- **مغلق:** shell route `MovitInnerRoute.ProgramDetail` · `program_*` i18n على الشاشة · `ProgramDetailViewModelTest` + `ProgramDetailMapperTest`
- **فجوات:** enroll عبر API (حالة محلية `sessionKeyForStart`) · weekly report header `onActionClick = {}` · أسابيع من `ProgramDetailPreviewData` · stat labels إنجليزية في mapper · drag/reorder · hero image شبكية · A11y محدود

**15 — Program flow** (list / week / weekly report) — **scorecard 77%**

- **Legacy:** `ProgramListActivity` + `ProgramDayActivity` + `WeeklyReportActivity`
- **KMP:** `ProgramListScreen` → `ProgramWeekPlanScreen` → `WeeklyReportScreen` في `feature:library` + inner routes في shell
- **الربط:** Train (تصفح البرامج / عرض التقرير) · Explore (عرض الكل / بطاقة برنامج)
- **✅ أُغلق (2026-06-10):** صور preview على بطاقات البرامج · `MovitBarChart` في التقرير الأسبوعي · a11y day pills/chart
- **مفتوح:** `FakeProgramFlowRepository` (لا API bridge) · قائمة كل أسابيع التقرير (legacy) · Share platform

---

### **Assessment & Level**

**13 — Assessment** — **scorecard 75%** (تحقق 2026-06-10) | **14 — Level & plan** — **scorecard 68%**

- **Legacy:** PAR-Q → body scan → results؛ `LevelProfileActivity` + `PlanOverviewActivity`
- **KMP:** `MovitAssessmentScreen` (PAR-Q×7 + placeholder + نتائج API/fake) · `MovitLevelScreen` (Profile/Plan)
- **✅ أُغلق (2026-06-10):** PAR-Q progress a11y · region tile a11y · shell routing (back / explore / home)
- **فجوات:** كاميرا/pose/**AssessmentEngine** (**Phase 07 DEFERRED**) · body map · Level: celebration + recommended programs row

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

  2. ~~02 Session~~ ✅ sheets + API + drag-reorder (متبقي: multi-workout day · catch-up)

  3. ~~03 Prepare/Rest~~ ✅ UI حالتان + shell wiring + rest tick (متبقي: hero media · Phase 07 camera)

  4. ~~07 Program detail~~ ✅ hero + tabs + weeks + edit shell + session start

  ~~5. 09 → 17 navigation~~ ✅ (2026-06-09)

المرحلة B — تحسين التبويبات

  6. ~~01 Train~~ ✅ week nav · program complete · form trend (متبقي: media Coil · RTL QA)

  7. ~~04/05/06 Library~~ ✅ فلاتر + badges + صور Android + Explore pull-to-refresh (متبقي: iOS images · A11y)

  8. ~~08 Home~~ ✅ hero progress · quick actions · level link · activity → report detail (2026-06-10) (متبقي: pull-to-refresh · RTL QA)

المرحلة C — تدفقات كاملة

  9. ~~15 Program flow~~ ✅ تدفق list → week → report + shell wiring

  10. 16 Workout run — UI shell ✅ (متبقي: camera/orchestration **Phase 07** · persist customize)

المرحلة D — حساب وتقييم

  11. ~~10 Auth~~ ✅ تدفق KMP + validation i18n (متبقي: Google OAuth · launcher legacy)

  12. ~~11 Profile~~ ✅ routing + preferences (متبقي: avatar · billing API)

  13. ~~12 Onboarding~~ ✅ 7 خطوات + PUT (متبقي: experience slider parity)

  14. ~~13 Assessment~~ ✅ PAR-Q + results shell (كاميرا **Phase 07 DEFERRED**)

  15. 14 Level & plan — أساس ✅ (متبقي: celebration · recommended programs row)

---

## **ما الذي أنصح به الآن؟**

المطلوب **تطابق تام** لـ 18 صفحة — عمل كبير متعدد الجلسات. الأنسب:

1. **صفحة بصفحة** حسب الترتيب أعلاه
2. لكل صفحة: spec قصير + checklist من الـ prototype + إصلاح + مقارنة بصرية
3. البدء بـ **17 Report detail** ثم **02 Session** لأنهما الأكثر وضوحاً في الفجوة  


