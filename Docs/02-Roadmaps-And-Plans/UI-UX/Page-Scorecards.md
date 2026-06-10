# Page Scorecards — Phase 05 KMP (WS-E / Pre-06)

آخر تحديث: 2026-06-10 (تحقق Phase 05)  
المرجع: [`Android-KMP-Mobile-UI-UX-Phase-Pre-06-Architecture-And-Production-Readiness-Plan.md`](Android-KMP-Mobile-UI-UX-Phase-Pre-06-Architecture-And-Production-Readiness-Plan.md) — WS-E

> **مصدر النِسب الوحيد (canonical):** هذا الملف. [`Android-UI-UX-Modernization-Status.md`](Android-UI-UX-Modernization-Status.md) · [`Sync-App-Pages.md`](Sync-App-Pages.md) · [`Android-KMP-Mobile-UI-UX-Professional-Plan.md`](Android-KMP-Mobile-UI-UX-Professional-Plan.md) **تربط هنا** ولا تكرر النسب يدوياً.  
> **أرقام الكود** (مفاتيح ar/en · اختبارات KMP · مكوّنات `Movit*`): [`generated/Docs-Stats-Snapshot.md`](generated/Docs-Stats-Snapshot.md) — `cd android-poc; .\gradlew.bat docsStats`

## المنهجية

كل صفحة تُقيَّم بـ **7 مجالات** بأوزان ثابتة؛ النسبة النهائية = مجموع `(درجة المجال × الوزن)`.

| المجال | الوزن | ما يُقاس |
|--------|-------|----------|
| **Functional flow** | 25% | تنقل، actions، API، loading/empty/error |
| **Visual parity** | 20% | مطابقة prototype، hierarchy، spacing، media |
| **Design system** | 15% | Movit components، tokens، لا raw colors |
| **i18n / RTL** | 15% | `core:resources` ar/en، اتجاه، نصوص طويلة |
| **Accessibility** | 10% | `contentDescription`، touch targets، font scale |
| **Tests** | 10% | ViewModel / mapper / state unit tests |
| **iOS readiness** | 5% | `compileKotlinIosSimulatorArm64` + factory |

**مصادر التحقق:** `android-poc/feature/*`، `core/resources`، `feature/shell` — فحص 2026-06-10 (قراءة شاشات + `MovitInnerHost.kt` + Gradle unit tests).

### تحقق Phase 05 (2026-06-10)

| البند | النتيجة |
|-------|---------|
| **Gradle** `:feature:library\|reports:testDebugUnitTest` (audit 15/16/17) | ✅ BUILD SUCCESSFUL — أعداد `@Test` في [`generated/Docs-Stats-Snapshot.md`](generated/Docs-Stats-Snapshot.md) |
| **Gradle** `:feature:home\|train\|explore\|reports\|library\|account\|shell:testDebugUnitTest` | ✅ BUILD SUCCESSFUL |
| **سبب فجوات قديمة في `Sync-App-Pages.md`** | تأخر توثيق أقسام التفصيل (ليست غياب تنفيذ) |
| **فجوات مفتوحة (15/16/17)** | Program flow **fake API** · Share platform · joints API · Workout **camera/orchestration** · previous-form fake · persist customize |
| **أُغلق (2026-06-10 — audit 15/16)** | صور preview برامج · `MovitBarChart` أسبوعي · a11y day pills/sequence · `ExercisePrepareStateTest` + rest ticker flag |
| **مؤجّل Phase 07** | كاميرا · pose · `TrainingActivity` workout-mode · `LegacyTrainingLauncher` |

---

## ملخص سريع

| الصفحة | # Proto | الموديول | **الإجمالي** | Functional | Visual | DS | i18n/RTL | A11y | Tests | iOS |
|--------|---------|----------|-------------|------------|--------|-----|----------|------|-------|-----|
| Home | 08 | `feature:home` | **92%** | 98% | 88% | 95% | 95% | 75% | 92% | 100% |
| Train | 01 | `feature:train` | **86%** | 88% | 85% | 93% | 93% | 50% | 90% | 100% |
| Explore | 04 | `feature:explore` | **87%** | 92% | 82% | 93% | 100% | 25% | 88% | 100% |
| Reports | 09 | `feature:reports` | **85%** | 90% | 82% | 93% | 100% | 30% | 80% | 100% |
| Report Detail | 17 | `feature:reports` | **92%** | 92% | 90% | 93% | 95% | 90% | 85% | 100% |
| Session | 02 | `feature:library` | **84%** | 88% | 75% | 90% | 90% | 62% | 88% | 100% |
| Prepare & rest | 03 | `feature:library` | **80%** | 88% | 65% | 93% | 95% | 40% | 80% | 100% |
| Workout flow | 16 | `feature:library` | **66%** | 55% | 50% | 93% | 90% | 35% | 60% | 100% |
| Library | 05–06 | `feature:library` | **78%** | 85% | 78% | 93% | 88% | 55% | 55% | 100% |
| Program detail | 07 | `feature:library` | **72%** | 76% | 70% | 93% | 85% | 25% | 65% | 100% |
| Auth | 10 | `feature:account` | **85%** | 86% | 78% | 93% | 93% | 75% | 75% | 100% |
| Profile | 11 | `feature:account` | **86%** | 88% | 85% | 93% | 93% | 65% | 78% | 100% |
| Onboarding | 12 | `feature:account` | **85%** | 92% | 82% | 93% | 93% | 45% | 78% | 100% |
| Assessment | 13 | `feature:account` | **74%** | 72% | 68% | 93% | 93% | 40% | 55% | 100% |
| Level | 14 | `feature:account` | **68%** | 72% | 68% | 93% | 87% | 35% | 55% | 100% |
| Program flow | 15 | `feature:library` | **77%** | 78% | 68% | 93% | 88% | 35% | 70% | 100% |

> **Library** = `ExercisesLibraryScreen` + `WorkoutsLibraryScreen` (05–06). **03 Prepare** و **07 Program detail** و **15 Program flow** لها scorecards منفصلة أدناه.

---

## 07 — Program detail (`ProgramDetailScreen`) — **72%**

**الملفات:** `feature/library/.../ProgramDetailScreen.kt` · `ProgramDetailViewModel.kt` · `ProgramDetailMapper.kt` · `ProgramDetailPreviewData.kt` · [`Page-Specs/Program-Detail-Page-Spec.md`](Page-Specs/Program-Detail-Page-Spec.md)

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 76% | 19.0 |
| Visual | 70% | 14.0 |
| DS | 93% | 14.0 |
| i18n/RTL | 85% | 12.8 |
| A11y | 25% | 2.5 |
| Tests | 65% | 6.5 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **72%** |

### فجوات UX محددة

- **Enrollment:** حالة محلية في `ProgramDetailViewModel.sessionKeyForStart()` — لا bridge API legacy بعد.
- **Weekly report:** `ProgramOverviewContent` — `onActionClick = {}` فارغ (صفحة 15 غير موصولة من هنا).
- **بيانات الأسابيع:** `ProgramDetailPreviewData` fixture — ليس API برنامج كامل.
- **Stat grid:** تسميات `ProgramDetailMapper` بالإنجليزية (`Duration`، `Weekly target`…) وليس `program_*`.
- **Hero media:** تدرّج لوني — `imageUrl` غير معروض من الشبكة.
- **Edit:** لا drag/reorder جلسات ولا محرر معاملات تمرين (مؤجل Phase 05/15).

### Checklist

- [x] Hero + tabs Overview/Edit + stat grid 2×2
- [x] Week strip + week card + day rows (Done/Next/Rest)
- [x] Copy card عند التسجيل + dock CTA
- [x] Edit: reasons · scope · impact · settings · pause
- [x] Start → `WorkoutSessionKeys` → `MovitInnerRoute.WorkoutSession` (`MovitInnerHost.kt`)
- [x] `program_*` keys في الشاشة/المكوّنات (ar/en)
- [x] Loading / Error states
- [x] `ProgramDetailViewModelTest` (4) + `ProgramDetailMapperTest`
- [x] iOS compile (ضمن `:feature:library`)
- [ ] API enrollment حقيقي
- [ ] Weekly report navigation → `MovitInnerRoute.WeeklyReport`
- [ ] Stat labels عبر resources
- [ ] A11y على week strip و dock
- [ ] Drag/reorder في Edit tab

---

## 08 — Home (`MovitHomeScreen`) — **92%**

**الملفات:** `feature/home/.../MovitHomeScreen.kt` · `MovitHomeViewModel.kt` · `SharedHomeRepository` · `Page-Specs/Home-Page-Modernization-Spec.md`

**تحقق:** 2026-06-10 — قراءة `MovitHomeScreen.kt` · `HomeApiMapper.kt` · `MovitAppShellViewModel.handleHomeEffect`

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 98% | 24.5 |
| Visual | 88% | 17.6 |
| DS | 95% | 14.3 |
| i18n/RTL | 95% | 14.3 |
| A11y | 75% | 7.5 |
| Tests | 92% | 9.2 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **92%** |

### فجوات UX متبقية

- مراجعة RTL يدوية (نصوص طويلة في التحية والبطاقات).
- Dark mode visual QA موثق.
- Pull-to-refresh و catch-up UI (legacy فقط — خارج نطاق Phase 05).
- `HomeReportPreview` card ما زال يفتح تبويب Reports (ليس `ReportDetail`) — خارج نطاق activity rows.

### Checklist

- [x] ViewModel + UDF (`MovitHomeViewModel`)
- [x] API عبر `SharedHomeRepository` + كاش offline-first
- [x] Loading / Error / Empty (`MovitLoadingState`, `MovitErrorState`, `MovitEmptyState`)
- [x] نصوص `movitText("home_*")` من `:core:resources`
- [x] RTL عبر `MovitLocaleProvider` في shell
- [x] صفر `Color()` خام في `commonMain`
- [x] Level card → `MovitInnerRoute.LevelProfile`
- [x] Hero progress من `weeklyCompletionPercent` API
- [x] مكوّنات `HomeHeroSummary` · `TodayPlanCard` · `HomeQuickActions` · `HomeProgressSection` موصولة
- [x] Quick actions مع `MovitIconBox`
- [x] `contentDescription` على Level · Explore · Reports · Start workout · View program · Body scan
- [x] Activity rows → `RecentActivityClicked` → `OpenReportDetail(exerciseId)` → `MovitInnerRoute.ReportDetail` (2026-06-10)
- [ ] مراجعة RTL يدوية
- [ ] Dark mode visual QA موثق
- [x] 5 ملفات اختبار + `recentActivityClicked_*` في `MovitHomeStateTest` + shell test
- [x] iOS factory + compile

---

## 01 — Train (`MovitTrainScreen`) — **86%**

**الملفات:** `feature/train/.../MovitTrainScreen.kt` · 8 مكوّنات في `components/` · `SharedTrainRepository` · `Page-Specs/Train-Page-Modernization-Spec.md`  
**تحقق:** 2026-06-10 — قراءة `feature/train` + `MovitInnerHost.kt` + `:feature:train:testDebugUnitTest` ✅

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 88% | 22.0 |
| Visual | 85% | 17.0 |
| DS | 93% | 14.0 |
| i18n/RTL | 93% | 14.0 |
| A11y | 50% | 5.0 |
| Tests | 90% | 9.0 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **86%** |

### فجوات UX متبقية

| الحالة | البند |
|--------|-------|
| **OPEN** | مراجعة RTL يدوية لبطاقات البرامج الطويلة |
| **OPEN** | A11y على `MovitSessionCard` expand/action داخل قائمة الجلسات |
| **OPEN** | iOS: `MovitRemoteImage` placeholder فقط على thumbnails البرامج |
| **DEFERRED** | — |

### ما أُغلق (2026-06-10)

- `TrainFeaturedProgramCard`: `MovitRemoteImage` لصور البرامج (Android Coil).
- A11y: `train_a11y_*` على CTAs الرئيسية · quick actions · form-trend chart (`TrainReportSection`).
- ~~ربط Start program → Program flow~~ ✅ `StartProgramClicked` → `OpenProgramWeekPlan`.

### Checklist

- [x] 5 حالات dashboard (`NoPlan`, `Active`, `Rest`, `DayDone`, `Complete`) منطقياً
- [x] API مشتق من home/trainMode
- [x] Loading + Error states
- [x] مكوّنات DS موحّدة (`TrainTodayCard`, `TrainStatusBanner`, `TrainFeaturedProgramCard`, …)
- [x] نصوص شاشة/مكوّنات عبر `train_*` keys (+ `train_a11y_*`)
- [x] week navigation تفاعلي (`←/→` + `weekOptions` + `train_week_previous/next`)
- [x] No program: hero lime + بطاقات برامج بصور شبكية + Start لكل برنامج
- [x] media/thumbnails على session cards (`MovitSessionCard.thumbnailUrl`)
- [x] Program complete: trophy ring + View journey / What's next
- [x] Form trend delta + chart `contentDescription`
- [x] رسالة preferences عبر `TrainStrings.prefsLater` / `train_prefs_later`
- [x] `StartWorkoutClicked` → `OpenProgramWorkout` أو `OpenSessionPreview` (shell → Session)
- [x] 10+ اختبارات (`MovitTrainStateTest`, `TrainApiMapperTest`, theme boundary)
- [x] iOS compile
- [ ] A11y كامل على session cards expandable
- [ ] RTL visual QA

---

## 04 — Explore (`MovitExploreScreen`) — **87%**

**الملفات:** `feature/explore/.../MovitExploreScreen.kt` · `SharedExploreRepository` · [`Explore-Page-Modernization-Spec.md`](Page-Specs/Explore-Page-Modernization-Spec.md)

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 92% | 23.0 |
| Visual | 82% | 16.4 |
| DS | 93% | 14.0 |
| i18n/RTL | 100% | 15.0 |
| A11y | 25% | 2.5 |
| Tests | 88% | 8.8 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **87%** |

### فجوات UX متبقية

- A11y: أوصاف صور/أزرار Filter، تركيز لوحة المفاتيح.
- iOS: تحميل صور شبكي حقيقي (placeholder حالياً في `MovitRemoteImage.ios`).

### Checklist

- [x] أقسام Recommended / Workouts / Exercises / Programs
- [x] بحث + chips فلاتر رئيسية + Filter button
- [x] muscle-strip (فلاتر جلسات) + workout-intro
- [x] exercise category chips
- [x] focus pills على بطاقات الجلسات
- [x] صور media (`MovitRemoteImage` + `imageUrl`)
- [x] Loading / Error / Empty
- [x] API + explore cache
- [x] نصوص localized (`explore_*` كاملة للشاشة)
- [x] `ExploreHero` / `ExploreExerciseList` موصولان
- [x] shell effects: `OpenWorkoutSession` · `OpenExercisePrepare`
- [x] pull-to-refresh (`PullToRefreshBox` + `RefreshRequested` — 2026-06-10)
- [x] 14+ اختبارات state/filter/effect/refresh + theme boundary + shell
- [x] iOS compile
- [ ] A11y كامل

---

## 09 — Reports (`MovitReportsScreen`) — **85%**

**الملفات:** `feature/reports/.../MovitReportsScreen.kt` · `SharedReportsRepository` · [`Reports-Page-Modernization-Spec.md`](Page-Specs/Reports-Page-Modernization-Spec.md)

**تحقق:** 2026-06-10 — `MovitUnderlineTabRow` · `PullToRefreshBox` · `ExerciseReportClicked` → shell inner route · Pro gate في `SharedReportsRepository`

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 90% | 22.5 |
| Visual | 82% | 16.4 |
| DS | 93% | 14.0 |
| i18n/RTL | 100% | 15.0 |
| A11y | 30% | 3.0 |
| Tests | 80% | 8.0 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **85%** |

### فجوات UX محددة

- Pro lock state موجود لكن **upsell flow** كامل (subscription screen) عبر shell placeholder.
- A11y على charts: `contentDescription` على البطاقات فقط — لا قارئ قيم نقطة بنقطة.

### Checklist

- [x] 3 تبويبات Overview / Exercises / Trends
- [x] Pro gate (`isProUser`)
- [x] Loading / Error / Locked / Empty
- [x] Navigation إلى Report Detail (inner route)
- [x] `reports_*` keys كاملة (+ improvement / attendance / refresh hint)
- [x] pull-to-refresh (`PullToRefreshBox` + `RefreshRequested`)
- [x] tab styling underline (`MovitUnderlineTabRow` — مطابق `09-reports.html`)
- [x] Trends parity: improvement rate + form line + volume + attendance + fatigue
- [x] Exercise score pills بألوان tint (prototype)
- [ ] A11y متقدمة على charts
- [x] state/mapper/refresh tests + theme boundary
- [x] iOS compile

---

## 17 — Report Detail (`ReportDetailScreen`) — **92%**

**الملفات:** `feature/reports/.../ReportDetailScreen.kt` · `SharedReportDetailRepository` · [`Page-Specs/Report-Detail-Page-Modernization-Spec.md`](Page-Specs/Report-Detail-Page-Modernization-Spec.md)

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 92% | 23.0 |
| Visual | 90% | 18.0 |
| DS | 93% | 14.0 |
| i18n/RTL | 95% | 14.2 |
| A11y | 90% | 9.0 |
| Tests | 85% | 8.5 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **92%** |

### فجوات UX محددة (مفتوحة)

- **Share / Export** في `MovitInnerHost.kt` → `shell_report_share_coming_soon` / `shell_report_export_coming_soon` (**مفتوح** — placeholder؛ legacy screenshot فقط).
- **Joint analysis من API** غير متوفرة بعد — UI fallback + `JointMetricsDto` جاهز (**مفتوح** حتى backend field).
- Multi-exercise vertical pager (legacy) — خارج نطاق per-exercise detail.

### Checklist

- [x] 4 صفحات Overview / Form / Fatigue / Tips
- [x] API metrics + mapper + optional `jointBreakdown`
- [x] Loading + Error
- [x] Back + Share أيقونات مع `contentDescription`
- [x] Tab semantics + chart/joint/fatigue a11y descriptions
- [x] Visual parity: score 56sp، tinted best/worst، chart highlight
- [x] `report_detail_*` + `report_detail_preview_*` keys (ar/en)
- [x] Joints empty-state message
- [ ] Share/Export platform sheet (Phase 06+)
- [ ] joints من API عند توفر backend field
- [x] 4 اختبارات (state ×2، mapper ×2، preview i18n)
- [x] iOS compile

---

## 02 — Session (`WorkoutSessionScreen`) — **84%**

**الملفات:** `feature/library/.../WorkoutSessionScreen.kt` · `WorkoutSessionSheets.kt` · `SharedWorkoutSessionRepository` · `Session-Page-Modernization-Spec.md`  
**تحقق:** 2026-06-10 — قراءة `feature/library` + `MovitInnerHost.kt` + `WorkoutSessionStateTest` ✅

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 88% | 22.0 |
| Visual | 75% | 15.0 |
| DS | 90% | 13.5 |
| i18n/RTL | 90% | 13.5 |
| A11y | 62% | 6.2 |
| Tests | 88% | 8.8 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **84%** |

### فجوات UX محددة

| الحالة | البند |
|--------|-------|
| **OPEN** | لا عرض متعدد لـ planned workouts في اليوم (legacy يعرض بطاقات قابلة للطي) |
| **OPEN** | catch-up day dialog و skip-warmup غير منقولين |
| **OPEN** | iOS: `MovitAsyncImage` placeholder فقط على thumbnails |
| **DEFERRED** | Prepare `onStart` → كاميرا/`TrainingActivity` workout-mode (**Phase 07**) |
| **DEFERRED** | مسار 16 Customize→Run للجلسات المستقلة (Explore workouts) — ليس Session day |

### ما أُغلق (2026-06-10)

- **Start** في dock → `ExercisePrepareRoute` (03) عبر `firstExerciseId()` + `MovitInnerHost` (مطابق `Session-Page-Modernization-Spec.md`).
- A11y: `session_a11y_start_workout` على زر Start · `MovitInnerPageHeader` يمرّر `backLabel`/`actionLabel` كـ `contentDescription`.
- اختبار `firstExerciseId_returnsWarmupExercise`.

### Checklist

- [x] تحميل effective-plan + حفظ + substitutions API
- [x] Edit mode: swap · edit details · delete · drag-reorder · add exercise/rest
- [x] Bottom sheets (Swap · Edit · Add · Edit rest)
- [x] Stat chips + thumbnails (`exerciseImageUrl` + Coil على Android)
- [x] i18n `session_*` + `session_a11y_*` + تنسيقات sets/rest/weight
- [x] Start → `ExercisePrepare` (03) — أول تمرين في الجلسة (`WorkoutSessionRoute` + shell)
- [x] نقرة تمرين (view) → Prepare · Prepare (مع `workoutId`) → `WorkoutRun`
- [ ] multi-workout day cards
- [ ] catch-up / skip-warmup
- [ ] workout-mode `TrainingActivity` intent (**Phase 07**)
- [x] 6+ اختبارات session (`WorkoutSessionStateTest`, `WorkoutSessionApiMapperTest`)
- [x] iOS compile

---

## 03 — Prepare & rest (`ExercisePrepareScreen`) — **80%**

**الملفات:** `ExercisePrepareScreen.kt` · `ExercisePrepareViewModel.kt` · `MovitLibraryRoutes.kt` · `MovitInnerHost.kt` (onStart)

**Page-Spec:** [`Page-Specs/03-Prepare-Rest-Page-Spec.md`](Page-Specs/03-Prepare-Rest-Page-Spec.md)

### تفصيل الأوزان (2026-06-10)

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 88% | 22.0 |
| Visual | 65% | 13.0 |
| DS | 93% | 14.0 |
| i18n/RTL | 95% | 14.2 |
| A11y | 40% | 4.0 |
| Tests | 80% | 8.0 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **80%** |

### فجوات UX محددة

- **صور hero + pose picker:** `ExerciseHeroPreview` placeholder (حرف أول التمرين).
- **كاميرا/تدريب:** `LegacyTrainingLauncher` — **Phase 07** (مؤجّل).

### Checklist

- [x] حالتان Prepare / Rest (UI + Pause/+15/Skip + Up Next)
- [x] معاينة تمرين، stats، إعداد كاميرا، تعليمات مرقّمة (`InstructionStep`)، عضلات
- [x] Prepare dock: Ready to train + Start
- [x] `prepare_*` EN/AR (لا نصوص hardcoded في الشاشة)
- [x] Start → `LaunchLegacyCameraTraining` (Explore) أو `WorkoutRun` (مع `workoutId`) عبر `MovitInnerHost`
- [x] عدّاد راحة حي (tick كل ثانية + auto-return عند 0 — 2026-06-10)
- [x] `ExercisePrepareStateTest` (7)
- [ ] صور hero + pose variant picker
- [ ] كاميرا/pose (Phase 07)
- [x] iOS compile (ضمن library target)

---

## 00 — Components (`MovitComponentsScreen` / DS catalog) — **~70%**

**الملفات:** `core/designsystem/.../catalog/MovitComponentsScreen.kt` · `MovitCatalogBlocks.kt` · `MovitCatalogPremiumSections.kt`

### تفصيل الأوزان (تقديري — 2026-06-10)

| المجال | الدرجة | ملاحظة |
|--------|--------|--------|
| Functional | ~75% | كتالوج تبويب debug/pilot — ليس مسار إنتاج |
| Visual | ~68% | أغلب أقسام `00-components.html` |
| DS | 95% | مرجع المكوّنات نفسه |
| i18n/RTL | ~60% | وصف ثنائي اللغة في الهيدر فقط |
| A11y | ~40% | chips ثيم بدون أوصاف كاملة |
| Tests | ~50% | لا unit tests مخصصة للكتالوج |
| iOS | 100% | ضمن `core:designsystem` |

### فجوات UX محددة (مفتوحة)

- macro calories card · coach card · accent blue/coral tokens في prototype
- horizontal workout cards · difficulty dots · program card كامل
- glass float-pill على hero (مستخدم في Report Detail فقط)

### Checklist

- [x] palette · typography · buttons · cards · metrics
- [x] filter row · media/exercise cards · states · skeleton
- [x] charts · list rows · floating nav preview · premium patterns
- [ ] parity كامل مع `00-components.html`
- [ ] i18n keys للكتالوج (ليس hardcoded EN)

---

## 16 — Workout flow (`WorkoutCustomizeScreen` / `WorkoutRunScreen`) — **66%**

**الملفات:** `WorkoutCustomizeScreen.kt` · `WorkoutRunScreen.kt` · `WorkoutFlowModels.kt` · `WorkoutCustomizeViewModel.kt` · `WorkoutRunViewModel.kt`

**Page-Spec:** [`Page-Specs/Workout-Flow-Page-Spec.md`](Page-Specs/Workout-Flow-Page-Spec.md)

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 55% | 13.8 |
| Visual | 50% | 10.0 |
| DS | 93% | 14.0 |
| i18n/RTL | 90% | 13.5 |
| A11y | 35% | 3.5 |
| Tests | 60% | 6.0 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **66%** |

### فجوات UX محددة (مفتوحة)

- **كاميرا / pose overlay** — Phase 07 (`LegacyTrainingLauncher` جسر مؤقت).
- **workout-mode `TrainingActivity` intent** — Phase 07 (تمرين واحد فقط اليوم).
- customize: لا drag-reorder ولا حذف تمارين (legacy أعمق؛ موجود في Session 02).
- insight «Previous form» **preview/fake** — ليس من API (**مفتوح**).
- persist customization للـ backend (**مفتوح**).

### Checklist

- [x] Customize: sets stepper + rest segmented (45/60/90) + action dock
- [x] Run: sequencer (done/active/pending) + progress + Start exercise
- [x] Session Start → Customize → Run
- [x] Prepare (`workoutId`) → Run
- [x] `workout_flow_*` EN/AR
- [x] `WorkoutFlowCache` handoff
- [x] `WorkoutFlowStateTest` (5) + shell route tests
- [x] a11y أساسي: progress bar · sequence rows (`workout_flow_a11y_*`)
- [ ] Camera / full workout orchestration (**Phase 07 — مفتوح**)
- [ ] Persist customization (**مفتوح**)
- [ ] Previous-form insight من API (**مفتوح**)

---

## 05–06 — Library (`ExercisesLibraryScreen` / `WorkoutsLibraryScreen`) — **78%**

**الملفات:** `feature/library/.../ExercisesLibraryScreen.kt` · `WorkoutsLibraryScreen.kt` · `LibraryListViewModel.kt` · `LibraryFilterLogic.kt` · `components/LibraryToolbar.kt` · `LibraryMediaImage`

**Page-Spec:** [`Page-Specs/Library-Pages-Modernization-Spec.md`](Page-Specs/Library-Pages-Modernization-Spec.md)

**تحقق 2026-06-10:** لا فجوات تنفيذ قابلة للإغلاق سريع — الشاشتان + `LibraryListViewModel` + shell navigation (`MovitInnerHost` → Prepare/Session) مطابقان للمواصفة.

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 85% | 21.3 |
| Visual | 78% | 15.6 |
| DS | 93% | 14.0 |
| i18n/RTL | 88% | 13.2 |
| A11y | 55% | 5.5 |
| Tests | 55% | 5.5 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **78%** |

### ما أُغلق (2026-06-09)

- مفاتيح `library_*` EN/AR — كل نصوص الشاشتين + الفلاتر + empty state.
- صور: `LibraryMediaImage` (Coil Android) + `imageUrl` من explore cache/API.
- Badges: `resolveLibraryBadge` + focus/level من `ExploreItemUi`.
- Filter button + `ModalBottomSheet` + chips strip.
- Tag عدد العناصر في header (`library_items_count`).
- `MovitEmptyState` + Clear filters.
- اختبارات: `LibraryFilterLogicTest` (4) + `LibraryListViewModelTest` (7).

### فجوات متبقية

- iOS: placeholder للصور (لا Coil KMP بعد — `MovitRemoteImage.ios`).
- A11y: contentDescription جزئي (back/filter/image).
- ~~Prepare hardcoded~~ — أُغلق: `prepare_*` في `ExercisePrepareScreen` (صفحة 03 منفصلة).

### Checklist

- [x] Grid/list + search + chips من Explore cache
- [x] Loading / Error
- [x] نقل نصوص 05–06 إلى `:core:resources`
- [x] empty state للبحث
- [x] media + badges
- [x] filter button + sheet
- [x] item count tag
- [x] اختبارات `LibraryListViewModel` + `LibraryFilterLogic`
- [ ] A11y كامل
- [x] iOS compile

---

## 10 — Auth (`MovitAuthScreen`) — **86%**

**الملفات:** `feature/account/.../MovitAuthScreen.kt` · `MovitAuthViewModel.kt` · `AuthRepository`  
**Page-Spec:** [`Page-Specs/Auth-Page-Modernization-Spec.md`](Page-Specs/Auth-Page-Modernization-Spec.md)  
**آخر تحقق:** 2026-06-10 — `:feature:account:testDebugUnitTest` ✅

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 86% | 21.5 |
| Visual | 78% | 15.6 |
| DS | 93% | 14.0 |
| i18n/RTL | 96% | 14.4 |
| A11y | 75% | 7.5 |
| Tests | 78% | 7.8 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **86%** |

### فجوات UX محددة (متبقية)

- Google OAuth فعلي (stub — `ShowLocalizedMessage` + `auth_google_unavailable`).
- Splash كـ launcher إنتاجي ما زال legacy (`SplashActivity`).

### Checklist

- [x] تدفق Splash / Intro / SignIn / SignUp / Forgot
- [x] `POST login|register` عند `MovitDataInstall`
- [x] `auth_*` keys كاملة (+ `auth_error_*` validation en/ar · reset success · google stub · a11y)
- [x] inline error (مفاتيح validation + رسائل API) + `isLoading` يعطّل الأزرار
- [x] Google Sign-In stub (`ShowLocalizedMessage` → shell snackbar)
- [x] `SignUpClicked` / `CreateAccountClicked` → `submitSignUp`
- [x] Forgot success panel (parity `ForgotPasswordActivity`)
- [x] Bootstrap: `resolveStartupInnerStack` · `resolveBootstrapTarget` · `intro_seen` cache
- [x] `emitPostAuthNavigation` → `OpenOnboarding` | `OpenShell`
- [x] A11y: logo · intro icons · page indicator · forgot success icon
- [x] `MovitAuthViewModelTest` (13+)
- [x] iOS compile

---

## 11 — Profile (`MovitProfileScreen`) — **86%**

**الملفات:** `feature/account/.../MovitProfileScreen.kt` · `MovitProfileViewModel.kt` · `MovitProfilePickers.kt`  
**Page spec:** [`Page-Specs/Profile-Page-Modernization-Spec.md`](Page-Specs/Profile-Page-Modernization-Spec.md)  
**آخر تحقق:** 2026-06-10 — routing Profile → Auth / Onboarding / Assessment / Level ✅

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 88% | 22.0 |
| Visual | 85% | 17.0 |
| DS | 93% | 14.0 |
| i18n/RTL | 93% | 14.0 |
| A11y | 65% | 6.5 |
| Tests | 78% | 7.8 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **86%** |

### فجوات UX محددة

- Edit profile / avatar upload (legacy: قريباً).
- Exercise settings dialog (غير موجود في prototype 11).
- billing حقيقي في Subscription.
- training profile summary من API (نص افتراضي حالياً).

### Checklist

- [x] تبويب Account حقيقي (ليس Components catalog)
- [x] حالات signed-out / loading / error / signed-in
- [x] Logout مع تأكيد + روابط Assessment / Level / Onboarding / Subscription
- [x] `profile_*` keys (+ appearance labels، logout confirm، a11y)
- [x] Language + Appearance فعّالان (حوار + platform/shell)
- [x] Haptic toggle فعّال
- [x] Pro/Free card مع MovitTag Gold (مطابقة prototype)
- [x] a11y avatar/edit contentDescription
- [x] `MovitProfileViewModelTest` (7) · `ProfileApiMapperTest`
- [x] shell theme/locale من Profile effects
- [x] iOS compile

---

## 12 — Onboarding (`MovitOnboardingScreen`) — **85%**

**الملفات:** `feature/account/.../MovitOnboardingScreen.kt` · `MovitOnboardingViewModel.kt` · `OnboardingData.kt`  
**آخر تحقق:** 2026-06-10 — `onboarding_*` + `onboarding_error_*` en/ar · `PUT training-profile` → `setOnboardingCompleted` ✅

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 92% | 23.0 |
| Visual | 82% | 16.4 |
| DS | 93% | 14.0 |
| i18n/RTL | 93% | 14.0 |
| A11y | 45% | 4.5 |
| Tests | 78% | 7.8 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **85%** |

### فجوات UX محددة

- A11y جزئي: progress + gender/weekday/location cards؛ لا font-scale QA بعد.
- خطوة Experience: slider جلسات/أسبوع (legacy) وليس chips مدة التدريب في prototype HTML.

### Checklist

- [x] معالج 7 خطوات (About → Summary)
- [x] `PUT /api/mobile/training-profile` — field mapping مطابق لـ `ProfileOnboardingActivity.toPayload`
- [x] validation parity: age 13–90، height 120–220، weight 30–200، home bodyweight default
- [x] weekdays index 0=Sun…6=Sat مع عرض Mon-first كالـ prototype
- [x] visual parity: gender grid، week grid، location/equipment cards، step icons، summary card
- [x] retry banner عند فشل API + `isSubmitting` يعطّل Continue
- [x] `onboarding_*` keys (~60 استدعاء movitText)
- [x] `OnboardingDataTest` (9) + `MovitOnboardingViewModelTest` (8)
- [x] iOS compile

---

## 13 — Assessment (`MovitAssessmentScreen`) — **75%**

**الملفات:** `feature/account/.../MovitAssessmentScreen.kt` · `MovitAssessmentViewModel.kt` · `SharedAssessmentRepository.kt` · `AssessmentApiMapper.kt`  
**Page-Spec:** [`Page-Specs/Assessment-Page-Modernization-Spec.md`](Page-Specs/Assessment-Page-Modernization-Spec.md)  
**آخر تحقق:** 2026-06-10 — تدفق PAR-Q → scan placeholder → results + shell effects ✅

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 72% | 18.0 |
| Visual | 68% | 13.6 |
| DS | 93% | 14.0 |
| i18n/RTL | 93% | 14.0 |
| A11y | 50% | 5.0 |
| Tests | 55% | 5.5 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **75%** |

### فجوات UX محددة

- **كاميرا وهمية:** placeholder UI فقط — لا body scan حقيقي (**Phase 07 DEFERRED**).
- **لا AssessmentEngine** مشترك بعد (**Phase 07**).
- لا body map / safety gates كاملة مقارنة بـ legacy `AssessmentResultActivity`.
- A11y جزئي: domain metric tiles بدون `contentDescription` بعد.

### Checklist

- [x] 3 phases PreScreening / BodyScan / Results
- [x] 7 أسئلة PAR-Q (parity مع legacy `ParqQuestions`)
- [x] `assessment_*` keys (~47 مفتاح en/ar incl. `assessment_parq_progress_a11y` · `assessment_region_score_a11y`)
- [x] `SharedAssessmentRepository` + `AssessmentApiMapper` (level-profile API)
- [x] domains + regions + insights مترجمة
- [x] header back + dashed scan frame
- [x] PAR-Q progress `contentDescription` · region tiles a11y
- [x] physician warning snackbar (`assessment_parq_physician_warning`)
- [x] shell: `NavigateBack` · `OpenExplore` · `OpenHome`
- [x] `MovitAssessmentViewModelTest` (7) + `AssessmentApiMapperTest` (3)
- [x] `contentDescription` على back + scanner
- [ ] كاميرا / pose (**Phase 07 DEFERRED**)
- [ ] engine scoring مشترك (Phase 07)
- [ ] A11y كامل (domain tiles · font-scale QA)
- [x] iOS compile (ضمن account target)

---

## 14 — Level (`MovitLevelScreen`) — **68%**

**الملفات:** `feature/account/.../MovitLevelScreen.kt` · `MovitLevelViewModel.kt` · `SharedLevelRepository.kt` · `LevelApiMapper.kt`  
**Page spec:** [`Page-Specs/Level-Page-Modernization-Spec.md`](Page-Specs/Level-Page-Modernization-Spec.md)  
**آخر تحقق:** 2026-06-10 — Profile/Plan tabs · API + fake fallback · `StartScan` → Assessment inner route ✅

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 72% | 18.0 |
| Visual | 68% | 13.6 |
| DS | 93% | 14.0 |
| i18n/RTL | 87% | 13.0 |
| A11y | 35% | 3.5 |
| Tests | 55% | 5.5 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **68%** |

### فجوات UX محددة

- Level-up celebration (legacy) غير منقول.
- صف «Recommended programs» من prototype غير مضاف.
- Region breakdown / limiting factors (legacy) خارج النطاق.
- fake fallback ما زال يعمل عند فشل `level-profile` (معاينة/dev).

### Checklist

- [x] تبويبان Profile / Plan
- [x] Loading + Error + empty plan
- [x] UI `level_*` keys (ar/en) + `LevelStrings`
- [x] plan phases من `GET /api/mobile/plan` + resources
- [x] domains من `level-profile` + مفاتيح مجال
- [x] ViewModel + mapper tests
- [x] A11y أساسي (ring + domain rows)
- [x] iOS factory + compile

---

## Shell — ملاحظات cross-page

| البند | الحالة |
|-------|--------|
| RTL | ✅ `MovitLocaleProvider` في `MovitAppShellRoute` |
| Back stack | ✅ WS-A — `BackHandler` + 19 shell tests |
| Tab labels | ⚠️ `MovitAppDestination.label` / `pageSubtitle` **إنجليزي hardcoded** |
| Placeholder screen | ✅ `MovitPlaceholderScreen` غير مستخدم في production path |

---

## 15 — Program flow (`ProgramList` / `ProgramWeekPlan` / `WeeklyReport`) — **77%**

**الملفات:** `feature/library/.../ProgramListScreen.kt` · `ProgramWeekPlanScreen.kt` · `WeeklyReportScreen.kt` · `ProgramFlowRepository.kt` · `MovitInnerRoute` في shell

**Page spec:** [`Page-Specs/Program-Flow-Page-Spec.md`](Page-Specs/Program-Flow-Page-Spec.md)

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 78% | 19.5 |
| Visual | 68% | 13.6 |
| DS | 93% | 14.0 |
| i18n/RTL | 88% | 13.2 |
| A11y | 35% | 3.5 |
| Tests | 70% | 7.0 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **77%** |

### فجوات UX محددة (مفتوحة)

- **API bridge:** `FakeProgramFlowRepository` / `defaultProgramFlowRepository()` — لا `ProgramRepository` + metrics حقيقي (**مفتوح**).
- صور البرامج من API (preview URLs في `ProgramFlowPreviewData` فقط؛ ليست شبكة إنتاج).
- التقرير الأسبوعي لأسبوع واحد فقط (legacy يعرض كل الأسابيع في قائمة).
- **Share** → `program_flow_share_coming_soon` (**مفتوح** — لا platform sheet).

### Checklist

- [x] تدفق list → week plan → weekly report
- [x] Inner routes + `MovitInnerHost`
- [x] ربط Train (browse / view report) و Explore (see all / program card)
- [x] فتح جلسة → `WorkoutSession` (بدون كاميرا مباشرة)
- [x] `program_flow_*` strings en/ar
- [x] `ProgramFlowStateTest` (5) + shell/train tests
- [x] iOS compile (ضمن library/shell targets)
- [x] صور preview على `MovitMediaCard` + `MovitBarChart` في التقرير الأسبوعي
- [x] a11y day pills + chart (`program_flow_a11y_*` / status keys)
- [ ] API bridge للبرامج والمقاييس (**مفتوح**)
- [ ] Share platform (**مفتوح**)
- [ ] قائمة كل أسابيع التقرير (legacy parity)

---

## قواعد رفع النسبة

لا تُرفع صفحة **≥5 نقاط** بدون:

1. إغلاق loading + empty + error للحالات الرئيسية.
2. مراجعة RTL (ar) على جهاز أو emulator.
3. مراجعة dark mode.
4. iOS compile أخضر للموديول.
5. توثيق الفجوات المتبقية في هذا الملف.

---

## روابط

- [`generated/Docs-Stats-Snapshot.md`](generated/Docs-Stats-Snapshot.md) — أرقام مولَّدة (i18n · tests · DS)
- [`Sync-App-Pages.md`](Sync-App-Pages.md) — خريطة prototype ↔ legacy ↔ KMP
- [`Android-UI-UX-Modernization-Status.md`](Android-UI-UX-Modernization-Status.md) — ملخص تنفيذي
- [`Android-KMP-Mobile-UI-UX-Phase-05-Page-By-Page-Modernization-Plan.md`](Android-KMP-Mobile-UI-UX-Phase-05-Page-By-Page-Modernization-Plan.md)
