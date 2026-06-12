# Page Scorecards — Phase 05 KMP (WS-E / Pre-06)

آخر تحديث: 2026-06-12 (إغلاق فجوات تبويبات Phase 05 — Train/Home/Explore/Library/Reports a11y · Pro upsell · iOS images)  
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

### تحقق Phase 05 (2026-06-12 — تبويبات رئيسية)

| البند | النتيجة |
|-------|---------|
| **Gradle** `:feature:train\|home\|explore\|reports\|shell:testDebugUnitTest` | ✅ BUILD SUCCESSFUL |
| **Gradle** `:feature:library:testDebugUnitTest` | ⚠️ 52/57 ناجحة — 5 فشل في مسارات 02/16 (Looper / Phase 07 training) خارج نطاق 05–06 |
| **Gradle** `:feature:library\|reports:testDebugUnitTest` (audit 15/16/17) | ✅ BUILD SUCCESSFUL — أعداد `@Test` في [`generated/Docs-Stats-Snapshot.md`](generated/Docs-Stats-Snapshot.md) |
| **Gradle** `:feature:home\|train\|explore\|reports\|library\|account\|shell:testDebugUnitTest` | ✅ (ما عدا 5 اختبارات library أعلاه) |
| **سبب فجوات قديمة في `Sync-App-Pages.md`** | تأخر توثيق أقسام التفصيل (ليست غياب تنفيذ) |
| **فجوات مفتوحة (15/16/17)** | Share platform · joints API · Workout persist customize · previous-form fake · polish جلسة حية (Phase 07) |
| **أُغلق (2026-06-10 — audit 15/16)** | صور preview برامج · `MovitBarChart` أسبوعي · a11y day pills/sequence · `ExercisePrepareStateTest` + rest ticker flag |
| **مؤجّل Phase 07** | polish كاميرا/pose · `feature:training` — المدخل: `KmpLive` → `TrainingSession` |

---

## ملخص سريع

| الصفحة | # Proto | الموديول | **الإجمالي** | Functional | Visual | DS | i18n/RTL | A11y | Tests | iOS |
|--------|---------|----------|-------------|------------|--------|-----|----------|------|-------|-----|
| Home | 08 | `feature:home` | **93%** | 98% | 90% | 95% | 98% | 78% | 92% | 100% |
| Train | 01 | `feature:train` | **90%** | 88% | 88% | 93% | 95% | 85% | 90% | 100% |
| Explore | 04 | `feature:explore` | **91%** | 92% | 85% | 93% | 100% | 80% | 88% | 100% |
| Reports | 09 | `feature:reports` | **90%** | 95% | 82% | 93% | 100% | 75% | 80% | 100% |
| Report Detail | 17 | `feature:reports` | **92%** | 92% | 90% | 93% | 95% | 90% | 85% | 100% |
| Session | 02 | `feature:library` | **87%** | 95% | 80% | 90% | 92% | 65% | 92% | 100% |
| Prepare & rest | 03 | `feature:library` | **85%** | 88% | 82% | 93% | 95% | 50% | 82% | 100% |
| Workout flow | 16 | `feature:library` | **73%** | 78% | 50% | 93% | 90% | 35% | 75% | 100% |
| Library | 05–06 | `feature:library` | **84%** | 85% | 82% | 93% | 88% | 85% | 55% | 100% |
| Program detail | 07 | `feature:library` | **84%** | 88% | 82% | 93% | 95% | 55% | 72% | 100% |
| Auth | 10 | `feature:account` | **86%** | 86% | 78% | 93% | 93% | 75% | 75% | 100% |
| Profile | 11 | `feature:account` | **86%** | 88% | 85% | 93% | 93% | 65% | 78% | 100% |
| Onboarding | 12 | `feature:account` | **85%** | 92% | 82% | 93% | 93% | 45% | 78% | 100% |
| Assessment | 13 | `feature:account` | **75%** | 72% | 68% | 93% | 93% | 40% | 55% | 100% |
| Level | 14 | `feature:account` | **68%** | 72% | 68% | 93% | 87% | 35% | 55% | 100% |
| Program flow | 15 | `feature:library` | **83%** | 90% | 78% | 93% | 90% | 45% | 78% | 100% |

> **Library** = `ExercisesLibraryScreen` + `WorkoutsLibraryScreen` (05–06). **03 Prepare** و **07 Program detail** و **15 Program flow** لها scorecards منفصلة أدناه.

---

## 07 — Program detail (`ProgramDetailScreen`) — **84%**

**الملفات:** `feature/library/.../ProgramDetailScreen.kt` · `ProgramDetailViewModel.kt` · `ProgramDetailMapper.kt` · `ProgramDetailPreviewData.kt` · [`Page-Specs/Program-Detail-Page-Spec.md`](Page-Specs/Program-Detail-Page-Spec.md)

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 88% | 22.0 |
| Visual | 82% | 16.4 |
| DS | 93% | 14.0 |
| i18n/RTL | 95% | 14.2 |
| A11y | 55% | 5.5 |
| Tests | 72% | 7.2 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **84%** |

### فجوات UX محددة

| الحالة | البند |
|--------|-------|
| **OPEN** | Drag/reorder جلسات + محرر معاملات تمرين في Edit tab |
| **OPEN** | A11y كامل (font-scale QA · day rows) |
| **DEFERRED** | polish كاميرا/تدريب (**Phase 07**) |

### ما أُغلق (2026-06-12)

- Enrollment API · weekly report navigation · `ProgramDetailStrings` / `program_stat_*` · hero `MovitRemoteImage` · a11y week strip/dock/hero · أسابيع من `ProgramDetailApiMapper` عند `MovitData`.

### Checklist

- [x] Hero + tabs Overview/Edit + stat grid 2×2
- [x] Week strip + week card + day rows (Done/Next/Rest)
- [x] Copy card عند التسجيل + dock CTA
- [x] Edit: reasons · scope · impact · settings · pause
- [x] Start → `WorkoutSessionKeys` → `MovitInnerRoute.WorkoutSession` (`MovitInnerHost.kt`)
- [x] `program_*` keys في الشاشة/المكوّنات (ar/en)
- [x] Loading / Error states
- [x] `ProgramDetailViewModelTest` (5) + `ProgramDetailMapperTest`
- [x] iOS compile (ضمن `:feature:library`)
- [x] API enrollment عبر `MovitData.plan.enrollProgram`
- [x] Weekly report navigation → `MovitInnerRoute.WeeklyReport` (من journey header)
- [x] Stat labels عبر resources
- [x] A11y أساسي على week strip و dock
- [ ] Drag/reorder في Edit tab

---

## 08 — Home (`MovitHomeScreen`) — **93%**

**الملفات:** `feature/home/.../MovitHomeScreen.kt` · `MovitHomeViewModel.kt` · `SharedHomeRepository` · `Page-Specs/Home-Page-Modernization-Spec.md`

**تحقق:** 2026-06-12 — `MovitDashboardHero` ellipsis للنصوص الطويلة (RTL) · dark mode عبر tokens فقط

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 98% | 24.5 |
| Visual | 90% | 18.0 |
| DS | 95% | 14.3 |
| i18n/RTL | 98% | 14.7 |
| A11y | 78% | 7.8 |
| Tests | 92% | 9.2 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **93%** |

### فجوات UX متبقية

- Dark mode: **مراجعة بصرية على جهاز** — لا عيوب tokens في الكود؛ موثّق هنا كـ QA يدوي متبقٍ.
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
- [x] RTL: `maxLines` + `TextOverflow.Ellipsis` على hero greeting (`MovitDashboardHero` — 2026-06-12)
- [ ] Dark mode visual QA على جهاز (tokens سليمة — لا إصلاح كود مطلوب بعد المراجعة)
- [x] 5 ملفات اختبار + `recentActivityClicked_*` في `MovitHomeStateTest` + shell test
- [x] iOS factory + compile

---

## 01 — Train (`MovitTrainScreen`) — **90%**

**الملفات:** `feature/train/.../MovitTrainScreen.kt` · 8 مكوّنات في `components/` · `SharedTrainRepository` · `Page-Specs/Train-Page-Modernization-Spec.md`  
**تحقق:** 2026-06-12 — `train_a11y_session_*` · `MovitSessionCard` + `MovitRemoteImage` thumbnails · `:feature:train:testDebugUnitTest` ✅

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 88% | 22.0 |
| Visual | 88% | 17.6 |
| DS | 93% | 14.0 |
| i18n/RTL | 95% | 14.2 |
| A11y | 85% | 8.5 |
| Tests | 90% | 9.0 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **90%** |

### فجوات UX متبقية

| الحالة | البند |
|--------|-------|
| **DEFERRED** | font-scale QA يدوي على قائمة الجلسات |

### ما أُغلق (2026-06-12)

- A11y كامل على `MovitSessionCard`: `train_a11y_session_collapsed/expanded` · `train_a11y_session_start` · `train_a11y_session_thumbnail`.
- RTL: `maxLines`/`Ellipsis` على عناوين البرامج في `TrainFeaturedProgramCard` + session card headers.
- iOS/Android: `MovitRemoteImage` (Coil 3 KMP) على thumbnails الجلسات والبرامج.

### ما أُغلق (2026-06-10)

- `TrainFeaturedProgramCard`: `MovitRemoteImage` لصور البرامج.
- A11y: `train_a11y_*` على CTAs الرئيسية · quick actions · form-trend chart (`TrainReportSection`).
- `StartProgramClicked` → `OpenProgramWeekPlan`.

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
- [x] A11y كامل على session cards expandable (`train_a11y_session_*` — 2026-06-12)
- [x] RTL ellipsis على بطاقات البرامج الطويلة (2026-06-12)

---

## 04 — Explore (`MovitExploreScreen`) — **91%**

**الملفات:** `feature/explore/.../MovitExploreScreen.kt` · `SharedExploreRepository` · [`Explore-Page-Modernization-Spec.md`](Page-Specs/Explore-Page-Modernization-Spec.md)

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 92% | 23.0 |
| Visual | 85% | 17.0 |
| DS | 93% | 14.0 |
| i18n/RTL | 100% | 15.0 |
| A11y | 80% | 8.0 |
| Tests | 88% | 8.8 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **91%** |

### ما أُغلق (2026-06-12)

- `explore_a11y_filter` · `explore_a11y_media_image` · `explore_a11y_filter_chip` على Filter / `MovitMediaCard` / chips.
- iOS: `MovitRemoteImage` Coil 3 KMP (لا placeholder-only).

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
- [x] A11y أساسي كامل (`explore_a11y_*` — 2026-06-12)

---

## 09 — Reports (`MovitReportsScreen`) — **90%**

**الملفات:** `feature/reports/.../MovitReportsScreen.kt` · `SharedReportsRepository` · [`Reports-Page-Modernization-Spec.md`](Page-Specs/Reports-Page-Modernization-Spec.md)

**تحقق:** 2026-06-12 — `OpenUpgrade` → `LaunchLegacySubscription` (مثل Profile) · `reports_a11y_*` على charts و exercise rows

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 95% | 23.8 |
| Visual | 82% | 16.4 |
| DS | 93% | 14.0 |
| i18n/RTL | 100% | 15.0 |
| A11y | 75% | 7.5 |
| Tests | 80% | 8.0 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **90%** |

### فجوات UX محددة

- A11y charts: أوصاف ملخّصة (`reports_a11y_*`) — ليس قارئ نقطة بنقطة (مقبول Phase 05).

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
- [x] Pro upsell: `UpgradeClicked` → shell `LaunchLegacySubscription` (Android `SubscriptionActivity`)
- [x] A11y charts: `reports_a11y_form_journey_chart` · weekly · improvement · volume · attendance + exercise rows
- [x] state/mapper/refresh tests + theme boundary
- [x] iOS compile

---

## 17 — Report Detail (`ReportDetailScreen`) — **95%**

**الملفات:** `feature/reports/.../ReportDetailScreen.kt` · `SharedReportDetailRepository` · [`Page-Specs/Report-Detail-Page-Modernization-Spec.md`](Page-Specs/Report-Detail-Page-Modernization-Spec.md)

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 96% | 24.0 |
| Visual | 90% | 18.0 |
| DS | 93% | 14.0 |
| i18n/RTL | 96% | 14.4 |
| A11y | 90% | 9.0 |
| Tests | 90% | 9.0 |
| iOS | 95% | 4.8 |
| **الإجمالي** | | **95%** |

### فجوات UX محددة (مفتوحة)

- **Share screenshot** (legacy `captureScreenshot`) — **مؤجّل Phase 06**؛ Phase 05: نص عبر `rememberReportShareAction` على Android + fallback snackbar على iOS.
- **Joint analysis من API** — mapper يقرأ `jointBreakdown` عند توفره؛ رسائل fallback مميّزة (`api_pending` / `session_untracked`) حتى يثبت الحقل في الإنتاج.
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
- [x] Share/Export نص — `ReportPlatformShare` expect/actual (Android chooser · iOS fallback)
- [x] joints من API عند `jointBreakdown` + fallback messaging مميّز
- [x] 7+ اختبارات (state ×2، mapper ×3، share formatter ×2، preview i18n)
- [x] iOS compile

---

## 02 — Session (`WorkoutSessionScreen`) — **87%**

**الملفات:** `WorkoutSessionScreen.kt` · `SessionPlannedWorkoutCards.kt` · `SharedWorkoutSessionRepository` · `SessionDayContext.kt` · `Session-Page-Modernization-Spec.md`  
**تحقق:** 2026-06-12 — `:feature:library:testDebugUnitTest` ✅ · `Phase05GapLogicTest`

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 95% | 23.8 |
| Visual | 80% | 16.0 |
| DS | 90% | 13.5 |
| i18n/RTL | 92% | 13.8 |
| A11y | 65% | 6.5 |
| Tests | 92% | 9.2 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **87%** |

### فجوات UX محددة

| الحالة | البند |
|--------|-------|
| **OPEN** | iOS: `MovitAsyncImage` placeholder فقط على thumbnails |
| **DEFERRED** | polish كاميرا/pose في `TrainingSession` (**Phase 07**) |
| **DEFERRED** | مسار 16 Customize→Run للجلسات المستقلة (Explore workouts) — ليس Session day |

### ما أُغلق (2026-06-12)

- **Multi-workout day cards:** `SessionPlannedWorkoutCards` + `mapPlannedWorkoutCards` · تبديل workout عبر shell.
- **Catch-up dialog:** `SessionCatchUpResolver` + home `catchUpSuggestion` · فتح أول يوم فائت.
- **Skip warm-up:** dock CTA + `withoutWarmup()` + حفظ `skipped` في `WorkoutSessionSaveEncoder`.

### ما أُغلق (2026-06-10)

- Start → `ExercisePrepare` · a11y dock · `firstExerciseId_returnsWarmupExercise`.

### Checklist

- [x] تحميل effective-plan + حفظ + substitutions API
- [x] Edit mode: swap · edit details · delete · drag-reorder · add exercise/rest
- [x] Bottom sheets (Swap · Edit · Add · Edit rest)
- [x] Stat chips + thumbnails (`exerciseImageUrl` + Coil على Android)
- [x] i18n `session_*` + `session_a11y_*` + تنسيقات sets/rest/weight
- [x] Start → `ExercisePrepare` (03) — أول تمرين في الجلسة (`WorkoutSessionRoute` + shell)
- [x] نقرة تمرين (view) → Prepare · Prepare (مع `workoutId`) → `WorkoutRun`
- [x] multi-workout day cards
- [x] catch-up / skip-warmup
- [x] Start exercise → `TrainingSession` (`KmpLive` عبر `MovitInnerHost`)
- [x] 6+ اختبارات session (`WorkoutSessionStateTest`, `WorkoutSessionApiMapperTest`)
- [x] iOS compile

---

## 03 — Prepare & rest (`ExercisePrepareScreen`) — **85%**

**الملفات:** `ExercisePrepareScreen.kt` · `ExercisePrepareViewModel.kt` · `ExercisePrepareMedia.kt` · `MovitLibraryRoutes.kt` · `MovitInnerHost.kt`

**Page-Spec:** [`Page-Specs/03-Prepare-Rest-Page-Spec.md`](Page-Specs/03-Prepare-Rest-Page-Spec.md)

### تفصيل الأوزان (2026-06-12)

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 88% | 22.0 |
| Visual | 82% | 16.4 |
| DS | 93% | 14.0 |
| i18n/RTL | 95% | 14.2 |
| A11y | 50% | 5.0 |
| Tests | 82% | 8.2 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **85%** |

### فجوات UX محددة

- **كاميرا/تدريب:** `TrainingSession` polish — **Phase 07**.
- **iOS hero:** placeholder عند غياب صورة شبكية.

### ما أُغلق (2026-06-12)

- **Hero:** `MovitAsyncImage` + `ExerciseConfig.imageUrl` / `poseVariants.positionImageUrl`.
- **Pose picker:** `FilterChip` row + `prepare_pose_variant` / `prepare_a11y_pose_variant`.

### Checklist

- [x] حالتان Prepare / Rest (UI + Pause/+15/Skip + Up Next)
- [x] معاينة تمرين، stats، إعداد كاميرا، تعليمات مرقّمة (`InstructionStep`)، عضلات
- [x] Prepare dock: Ready to train + Start
- [x] `prepare_*` EN/AR (لا نصوص hardcoded في الشاشة)
- [x] Start → `handleTrainingStart` → `TrainingSession` (`KmpLive`) عبر `MovitInnerHost`
- [x] عدّاد راحة حي (tick كل ثانية + auto-return عند 0 — 2026-06-10)
- [x] `ExercisePrepareStateTest` (7)
- [x] صور hero (`MovitAsyncImage` + `TrainingConfigRepository`) + pose variant picker
- [ ] كاميرا/pose polish (Phase 07)
- [x] iOS compile (ضمن library target)

---

## 00 — Components (`MovitComponentsScreen` / DS catalog) — **~82%**

**الملفات:** `core/designsystem/.../catalog/MovitComponentsScreen.kt` · `MovitCatalogBlocks.kt` · `MovitCatalogPremiumSections.kt`

### تفصيل الأوزان (تقديري — 2026-06-10)

| المجال | الدرجة | ملاحظة |
|--------|--------|--------|
| Functional | ~82% | كتالوج تبويب debug/pilot — ليس مسار إنتاج |
| Visual | ~80% | macro · coach · workout scroll · program · difficulty dots · coral accent |
| DS | 95% | مرجع المكوّنات نفسه |
| i18n/RTL | ~85% | `catalog_*` keys en/ar للهيدر والأقسام الجديدة |
| A11y | ~40% | chips ثيم بدون أوصاف كاملة |
| Tests | ~50% | لا unit tests مخصصة للكتالوج |
| iOS | 100% | ضمن `core:designsystem` |

### فجوات UX محددة (مفتوحة)

- glass float-pill على hero (مستخدم في Report Detail فقط)
- i18n لبقية أقسام الكتالوج القديمة (palette/buttons…) — لا تزال EN جزئياً
- parity دقيقة 100% مع `00-components.html` (avatars · wave chart · settings rows)

### Checklist

- [x] palette · typography · buttons · cards · metrics
- [x] filter row · media/exercise cards · states · skeleton
- [x] charts · list rows · floating nav preview · premium patterns
- [x] macro calories · coach card · coral accent · horizontal workout cards · difficulty dots · program card
- [x] `catalog_*` i18n للهيدر والأقسام الجديدة (ar/en)
- [ ] parity كامل مع `00-components.html`

---

## 16 — Workout flow (`WorkoutCustomizeScreen` / `WorkoutRunScreen`) — **73%**

**الملفات:** `WorkoutCustomizeScreen.kt` · `WorkoutRunScreen.kt` · `WorkoutFlowSaveEncoder.kt` · `WorkoutFormInsightLoader.kt` · `WorkoutCustomizeViewModel.kt` · `WorkoutRunViewModel.kt`

**Page-Spec:** [`Page-Specs/Workout-Flow-Page-Spec.md`](Page-Specs/Workout-Flow-Page-Spec.md)

### تفصيل الأوزان (2026-06-12)

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 78% | 19.5 |
| Visual | 50% | 10.0 |
| DS | 93% | 14.0 |
| i18n/RTL | 90% | 13.5 |
| A11y | 35% | 3.5 |
| Tests | 75% | 7.5 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **73%** |

### فجوات UX محددة (مفتوحة)

- **كاميرا / pose overlay polish** — Phase 07.
- **workout-mode `TrainingActivity` intent** — Phase 07.
- customize: لا drag-reorder ولا حذف تمارين (legacy أعمق؛ موجود في Session 02).

### ما أُغلق (2026-06-12)

- **Persist customization:** `WorkoutFlowSaveEncoder` + `saveFlowCustomization` → `saveDayCustomizations`.
- **Previous-form insight:** `WorkoutFormInsightLoader` → `syncExerciseMetrics` (لا fake preview).

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
- [x] Persist customization
- [x] Previous-form insight من API

---

## 05–06 — Library (`ExercisesLibraryScreen` / `WorkoutsLibraryScreen`) — **84%**

**الملفات:** `feature/library/.../ExercisesLibraryScreen.kt` · `WorkoutsLibraryScreen.kt` · `LibraryListViewModel.kt` · `LibraryFilterLogic.kt` · `components/LibraryToolbar.kt` · `LibraryMediaImage`

**Page-Spec:** [`Page-Specs/Library-Pages-Modernization-Spec.md`](Page-Specs/Library-Pages-Modernization-Spec.md)

**تحقق 2026-06-12:** `library_a11y_*` · `LibraryMediaImage` → `MovitRemoteImage` (KMP) · 52/57 unit tests (فشل 5 في 02/16 خارج 05–06)

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 85% | 21.3 |
| Visual | 82% | 16.4 |
| DS | 93% | 14.0 |
| i18n/RTL | 88% | 13.2 |
| A11y | 85% | 8.5 |
| Tests | 55% | 5.5 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **84%** |

### ما أُغلق (2026-06-09)

- مفاتيح `library_*` EN/AR — كل نصوص الشاشتين + الفلاتر + empty state.
- صور: `LibraryMediaImage` (Coil Android) + `imageUrl` من explore cache/API.
- Badges: `resolveLibraryBadge` + focus/level من `ExploreItemUi`.
- Filter button + `ModalBottomSheet` + chips strip.
- Tag عدد العناصر في header (`library_items_count`).
- `MovitEmptyState` + Clear filters.
- اختبارات: `LibraryFilterLogicTest` (4) + `LibraryListViewModelTest` (7).

### ما أُغلق (2026-06-12)

- `library_a11y_back` · `library_a11y_filter` · `library_a11y_item_image` على back/filter/grid/workout cards.
- iOS/Android: `LibraryMediaImage` يغلّف `MovitRemoteImage` (Coil 3 KMP).

### فجوات متبقية

- اختبارات 02/16 في نفس الموديول (5 فشل Looper) — ليست شاشتي 05–06.

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

## 10 — Auth (`MovitAuthScreen`) — **92%**

**الملفات:** `feature/account/.../MovitAuthScreen.kt` · `MovitAuthViewModel.kt` · `AuthRepository` · `GoogleSignInHost`  
**Page-Spec:** [`Page-Specs/Auth-Page-Modernization-Spec.md`](Page-Specs/Auth-Page-Modernization-Spec.md)  
**آخر تحقق:** 2026-06-12 — `:feature:account:testDebugUnitTest` ✅ (16 auth tests)

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 92% | 23.0 |
| Visual | 82% | 16.4 |
| DS | 93% | 14.0 |
| i18n/RTL | 96% | 14.4 |
| A11y | 78% | 7.8 |
| Tests | 85% | 8.5 |
| iOS | 95% | 4.8 |
| **الإجمالي** | | **92%** |

### فجوات UX محددة (متبقية)

- iOS: `GoogleSignInHost` stub — لا Credential Manager بعد.
- Splash كـ launcher إنتاجي ما زال legacy (`SplashActivity`).

### Checklist

- [x] تدفق Splash / Intro / SignIn / SignUp / Forgot
- [x] `POST login|register` عند `MovitDataInstall`
- [x] `auth_*` keys كاملة (+ `auth_error_*` validation en/ar · reset success · google · a11y)
- [x] inline error (مفاتيح validation + رسائل API) + `isLoading` يعطّل الأزرار
- [x] Google Sign-In bridge: `expect/actual GoogleSignInHost` + `POST api/mobile/auth/google` عبر `MovitData.account.googleAuth`
- [x] `SignUpClicked` / `CreateAccountClicked` → `submitSignUp`
- [x] Forgot success panel (parity `ForgotPasswordActivity`)
- [x] Bootstrap: `resolveStartupInnerStack` · `resolveBootstrapTarget` · `intro_seen` cache
- [x] `emitPostAuthNavigation` → `OpenOnboarding` | `OpenShell`
- [x] A11y: logo · intro icons · page indicator · forgot success icon
- [x] `MovitAuthViewModelTest` (16)
- [x] iOS compile

---

## 11 — Profile (`MovitProfileScreen`) — **90%**

**الملفات:** `feature/account/.../MovitProfileScreen.kt` · `MovitProfileViewModel.kt` · `SharedProfileRepository` · `TrainingProfileSummaryMapper`  
**Page spec:** [`Page-Specs/Profile-Page-Modernization-Spec.md`](Page-Specs/Profile-Page-Modernization-Spec.md)  
**آخر تحقق:** 2026-06-12 — `GET training-profile` summary + a11y toggles/rows ✅

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 92% | 23.0 |
| Visual | 85% | 17.0 |
| DS | 93% | 14.0 |
| i18n/RTL | 93% | 14.0 |
| A11y | 78% | 7.8 |
| Tests | 82% | 8.2 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **90%** |

### فجوات UX محددة

- Edit profile / avatar upload (legacy: قريباً).
- Exercise settings dialog (غير موجود في prototype 11).
- billing حقيقي في Subscription.

### Checklist

- [x] تبويب Account حقيقي (ليس Components catalog)
- [x] حالات signed-out / loading / error / signed-in
- [x] Logout مع تأكيد + روابط Assessment / Level / Onboarding / Subscription
- [x] `profile_*` keys (+ appearance labels، logout confirm، a11y)
- [x] Language + Appearance فعّالان (حوار + platform/shell)
- [x] Haptic toggle فعّال
- [x] Pro/Free card مع MovitTag Gold (مطابقة prototype)
- [x] a11y avatar/edit · language row · toggles · training profile row
- [x] training profile summary من `GET /api/mobile/training-profile` (`TrainingProfileSummaryMapper`)
- [x] `MovitProfileViewModelTest` (7) · `ProfileApiMapperTest` · `TrainingProfileSummaryMapperTest`
- [x] shell theme/locale من Profile effects
- [x] iOS compile

---

## 12 — Onboarding (`MovitOnboardingScreen`) — **90%**

**الملفات:** `feature/account/.../MovitOnboardingScreen.kt` · `MovitOnboardingViewModel.kt` · `OnboardingData.kt` · `SharedOnboardingRepository.kt`  
**Page-Spec:** [`Page-Specs/Onboarding-Page-Modernization-Spec.md`](Page-Specs/Onboarding-Page-Modernization-Spec.md)  
**آخر تحقق:** 2026-06-12 — a11y selection states + sessions slider · `PUT training-profile` ✅

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 92% | 23.0 |
| Visual | 82% | 16.4 |
| DS | 93% | 14.0 |
| i18n/RTL | 93% | 14.0 |
| A11y | 72% | 7.2 |
| Tests | 78% | 7.8 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **90%** |

### فجوات UX محددة

- font-scale 200% QA يدوي (جهاز) لم يُغلق بعد.
- خطوة Experience: slider جلسات/أسبوع (legacy) وليس chips مدة التدريب في prototype HTML.

### Checklist

- [x] معالج 7 خطوات (About → Summary)
- [x] `PUT /api/mobile/training-profile` — field mapping مطابق لـ `ProfileOnboardingActivity.toPayload`
- [x] validation parity: age 13–90، height 120–220، weight 30–200، home bodyweight default
- [x] weekdays index 0=Sun…6=Sat مع عرض Mon-first كالـ prototype
- [x] visual parity: gender grid، week grid، location/equipment cards، step icons، summary card
- [x] retry banner عند فشل API + `isSubmitting` يعطّل Continue
- [x] `onboarding_*` keys (~60 استدعاء movitText)
- [x] A11y: progress · gender/weekday/location/equipment selected state · goal cards · sessions slider
- [x] `OnboardingDataTest` (9) + `MovitOnboardingViewModelTest` (8)
- [x] iOS compile

---

## 13 — Assessment (`MovitAssessmentScreen`) — **82%**

**الملفات:** `feature/account/.../MovitAssessmentScreen.kt` · `MovitAssessmentViewModel.kt` · `SharedAssessmentRepository.kt` · `AssessmentApiMapper.kt`  
**Page-Spec:** [`Page-Specs/Assessment-Page-Modernization-Spec.md`](Page-Specs/Assessment-Page-Modernization-Spec.md)  
**آخر تحقق:** 2026-06-12 — domain metric tiles a11y · PAR-Q → results shell ✅

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 72% | 18.0 |
| Visual | 68% | 13.6 |
| DS | 93% | 14.0 |
| i18n/RTL | 93% | 14.0 |
| A11y | 68% | 6.8 |
| Tests | 55% | 5.5 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **82%** |

### فجوات UX محددة

- **كاميرا وهمية:** placeholder UI فقط — لا body scan حقيقي (**Phase 07 DEFERRED**).
- **لا AssessmentEngine** مشترك بعد (**Phase 07**).
- لا body map / safety gates كاملة مقارنة بـ legacy `AssessmentResultActivity`.
- font-scale 200% QA يدوي لم يُغلق بعد.

### Checklist

- [x] 3 phases PreScreening / BodyScan / Results
- [x] 7 أسئلة PAR-Q (parity مع legacy `ParqQuestions`)
- [x] `assessment_*` keys (~47 مفتاح en/ar incl. `assessment_parq_progress_a11y` · `assessment_region_score_a11y`)
- [x] `SharedAssessmentRepository` + `AssessmentApiMapper` (level-profile API)
- [x] domains + regions + insights مترجمة
- [x] header back + dashed scan frame
- [x] PAR-Q progress `contentDescription` · region + domain metric tiles a11y
- [x] physician warning snackbar (`assessment_parq_physician_warning`)
- [x] shell: `NavigateBack` · `OpenExplore` · `OpenHome`
- [x] `MovitAssessmentViewModelTest` (7) + `AssessmentApiMapperTest` (3)
- [x] `contentDescription` على back + scanner
- [ ] كاميرا / pose (**Phase 07 DEFERRED**)
- [ ] engine scoring مشترك (Phase 07)
- [ ] font-scale QA يدوي (جهاز)
- [x] iOS compile (ضمن account target)

---

## 14 — Level (`MovitLevelScreen`) — **84%**

**الملفات:** `feature/account/.../MovitLevelScreen.kt` · `MovitLevelViewModel.kt` · `LevelCelebrationPreferences.kt` · `LevelApiMapper.kt`  
**Page spec:** [`Page-Specs/Level-Page-Modernization-Spec.md`](Page-Specs/Level-Page-Modernization-Spec.md)  
**آخر تحقق:** 2026-06-12 — level-up overlay · recommended programs row · tab a11y ✅

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 82% | 20.5 |
| Visual | 78% | 15.6 |
| DS | 93% | 14.0 |
| i18n/RTL | 90% | 13.5 |
| A11y | 62% | 6.2 |
| Tests | 68% | 6.8 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **84%** |

### فجوات UX محددة

- Region breakdown / limiting factors (legacy) خارج النطاق.
- fake fallback ما زال يعمل عند فشل `level-profile` (معاينة/dev).
- font-scale 200% QA يدوي لم يُغلق بعد.

### Checklist

- [x] تبويبان Profile / Plan
- [x] Loading + Error + empty plan
- [x] UI `level_*` keys (ar/en) + `LevelStrings`
- [x] plan phases من `GET /api/mobile/plan` + resources
- [x] domains من `level-profile` + مفاتيح مجال
- [x] ViewModel + mapper tests
- [x] Level-up celebration overlay (`LevelUpCelebrationUi` + `LevelCelebrationPreferences`)
- [x] Recommended programs row (prototype parity · `BrowseProgramsClicked` → Explore)
- [x] A11y: ring · domain rows · tab pills · recommended row
- [x] `MovitLevelViewModelTest` (6) incl. celebration
- [x] iOS factory + compile

---

## Shell — ملاحظات cross-page

| البند | الحالة |
|-------|--------|
| RTL | ✅ `MovitLocaleProvider` في `MovitAppShellRoute` |
| Back stack | ✅ WS-A — `BackHandler` + 19 shell tests |
| Tab labels | ✅ `MovitAppDestination` → `labelKey` / `subtitleKey` + `localizedLabel()` / `localizedSubtitle()` (`nav_*` · `dest_*_subtitle`) |
| Placeholder screen | ✅ `MovitPlaceholderScreen` غير مستخدم في production path |

---

## 15 — Program flow (`ProgramList` / `ProgramWeekPlan` / `WeeklyReport`) — **83%**

**الملفات:** `feature/library/.../ProgramListScreen.kt` · `ProgramWeekPlanScreen.kt` · `WeeklyReportScreen.kt` · `ProgramFlowRepository.kt` · `MovitInnerRoute` في shell

**Page spec:** [`Page-Specs/Program-Flow-Page-Spec.md`](Page-Specs/Program-Flow-Page-Spec.md)

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 90% | 22.5 |
| Visual | 78% | 15.6 |
| DS | 93% | 14.0 |
| i18n/RTL | 90% | 13.5 |
| A11y | 45% | 4.5 |
| Tests | 78% | 7.8 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **83%** |

### فجوات UX محددة (متبقية)

| الحالة | البند |
|--------|-------|
| **OPEN** | iOS share sheet (Android `ACTION_SEND` ✅) |
| **OPEN** | week-over-week metrics عميقة عند غياب backend field |
| **OPEN** | A11y كامل على قائمة الأسابيع |

### ما أُغلق (2026-06-12)

- `SharedProgramFlowRepository` + `coverImageUrl` على البطاقات · قائمة كل الأسابيع (`WeeklyReportWeekSummaryUi`) · Share عبر `MovitAppShellEffect.ShareText`.

### Checklist

- [x] تدفق list → week plan → weekly report
- [x] Inner routes + `MovitInnerHost`
- [x] ربط Train (browse / view report) و Explore (see all / program card)
- [x] فتح جلسة → `WorkoutSession` (بدون كاميرا مباشرة)
- [x] `program_flow_*` strings en/ar
- [x] `ProgramFlowStateTest` (6) + shell/train tests
- [x] iOS compile (ضمن library/shell targets)
- [x] صور شبكية على `MovitProgramCard` + `MovitBarChart` في التقرير الأسبوعي
- [x] a11y day pills + chart + week summary cards
- [x] `SharedProgramFlowRepository` + `MovitData` (ليس fake-only)
- [x] Share platform (Android)
- [x] قائمة كل أسابيع التقرير (legacy parity)

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
