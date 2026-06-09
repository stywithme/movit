# Page Scorecards — Phase 05 KMP (WS-E / Pre-06)

آخر تحديث: 2026-06-09  
المرجع: [`Android-KMP-Mobile-UI-UX-Phase-Pre-06-Architecture-And-Production-Readiness-Plan.md`](Android-KMP-Mobile-UI-UX-Phase-Pre-06-Architecture-And-Production-Readiness-Plan.md) — WS-E

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

**مصادر التحقق:** `android-poc/feature/*`، `core/resources`، `feature/shell` — فحص 2026-06-09 (grep + قراءة شاشات).

---

## ملخص سريع

| الصفحة | # Proto | الموديول | **الإجمالي** | Functional | Visual | DS | i18n/RTL | A11y | Tests | iOS |
|--------|---------|----------|-------------|------------|--------|-----|----------|------|-------|-----|
| Home | 08 | `feature:home` | **79%** | 88% | 70% | 93% | 93% | 20% | 80% | 100% |
| Train | 01 | `feature:train` | **72%** | 72% | 60% | 93% | 87% | 20% | 80% | 100% |
| Explore | 04 | `feature:explore` | **86%** | 88% | 82% | 93% | 100% | 25% | 85% | 100% |
| Reports | 09 | `feature:reports` | **85%** | 88% | 82% | 93% | 100% | 30% | 80% | 100% |
| Report Detail | 17 | `feature:reports` | **92%** | 92% | 90% | 93% | 95% | 90% | 85% | 100% |
| Session | 02 | `feature:library` | **48%** | 32% | 40% | 87% | 47% | 25% | 70% | 100% |
| Library | 05–06 | `feature:library` | **78%** | 85% | 78% | 93% | 88% | 55% | 55% | 100% |
| Program detail | 07 | `feature:library` | **72%** | 76% | 70% | 93% | 85% | 25% | 65% | 100% |
| Auth | 10 | `feature:account` | **85%** | 86% | 78% | 93% | 93% | 75% | 75% | 100% |
| Profile | 11 | `feature:account` | **70%** | 68% | 72% | 93% | 93% | 30% | 50% | 100% |
| Onboarding | 12 | `feature:account` | **74%** | 80% | 70% | 93% | 93% | 20% | 50% | 100% |
| Assessment | 13 | `feature:account` | **55%** | 56% | 55% | 87% | 80% | 25% | 0% | 90% |
| Level | 14 | `feature:account` | **68%** | 72% | 68% | 93% | 87% | 35% | 55% | 100% |

> **Library** = `ExercisesLibraryScreen` + `WorkoutsLibraryScreen` (05–06). **07 Program detail** له scorecard منفصل أدناه.

---

## 08 — Home (`MovitHomeScreen`) — **91%**

**الملفات:** `feature/home/.../MovitHomeScreen.kt` · `MovitHomeViewModel.kt` · `SharedHomeRepository` · `Page-Specs/Home-Page-Modernization-Spec.md`

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 96% | 24.0 |
| Visual | 88% | 17.6 |
| DS | 95% | 14.3 |
| i18n/RTL | 95% | 14.3 |
| A11y | 75% | 7.5 |
| Tests | 90% | 9.0 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **91%** |

### فجوات UX متبقية

- مراجعة RTL يدوية (نصوص طويلة في التحية والبطاقات).
- Dark mode visual QA موثق.
- Pull-to-refresh و catch-up UI (legacy فقط — خارج نطاق Phase 05).
- Navigation لـ Report detail من صفوف النشاط الأخير.

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
- [ ] مراجعة RTL يدوية
- [ ] Dark mode visual QA موثق
- [x] 5 ملفات اختبار (`MovitHomeStateTest`, `HomeSummaryCalculatorTest`, `HomeApiMapperTest`, theme boundary)
- [x] iOS factory + compile

---

## 01 — Train (`MovitTrainScreen`) — **72%**

**الملفات:** `feature/train/.../MovitTrainScreen.kt` · 7 مكوّنات في `components/` · `SharedTrainRepository`

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 72% | 18.0 |
| Visual | 60% | 12.0 |
| DS | 93% | 14.0 |
| i18n/RTL | 87% | 13.0 |
| A11y | 20% | 2.0 |
| Tests | 80% | 8.0 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **72%** |

### فجوات UX محددة

- `TrainWeekPreview`: لا تنقل أسبوع (←/→) رغم وجود بيانات `dashboard.week`.
- حالة **No program**: بدون hero lime + صور برامج + Start لكل برنامج كما في `01-train.html`.
- بطاقات الجلسات: بدون thumbnails / media strip.
- **Program complete**: بدون trophy ring وCTAs (Continue / Browse).
- **Form trend**: بدون delta مثل «+5% vs last week».
- Quick action «preferences»: رسالة إنجليزية hardcoded في `MovitTrainViewModel.kt:85` — `"Training preferences arrive in a later phase."` (يجب نقلها لـ resources).

### Checklist

- [x] 5 حالات dashboard (`NoPlan`, `Active`, `Rest`, `DayDone`, `Complete`) منطقياً
- [x] API مشتق من home/trainMode
- [x] Loading + Error states
- [x] مكوّنات DS موحّدة (`TrainTodayCard`, `TrainStatusBanner`, …)
- [x] نصوص شاشة/مكوّنات عبر `train_*` keys
- [ ] week navigation تفاعلي
- [ ] media/thumbnails على session cards
- [ ] نقل رسالة preferences إلى `core:resources`
- [ ] A11y على charts وأزرار الأسبوع
- [x] 3 اختبارات (`MovitTrainStateTest`, `TrainApiMapperTest`, theme boundary)
- [x] iOS compile

---

## 04 — Explore (`MovitExploreScreen`) — **75%**

**الملفات:** `feature/explore/.../MovitExploreScreen.kt` · `SharedExploreRepository`

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 80% | 20.0 |
| Visual | 65% | 13.0 |
| DS | 93% | 14.0 |
| i18n/RTL | 87% | 13.0 |
| A11y | 20% | 2.0 |
| Tests | 70% | 7.0 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **75%** |

### فجوات UX محددة

- لا زر **Filter** منفصل ولا muscle-strip كما في prototype.
- بطاقات Recommended / Workouts / Exercises **بدون صور وسائط** (`imageUrl` غير معروض).
- مكوّنات `ExploreHero.kt` / `ExploreExerciseList.kt` تحتوي نصوص إنجليزية hardcoded و**غير مستخدمة** في الشاشة الحالية.
- Focus pills و workout-intro chips غائبة.

### Checklist

- [x] أقسام Recommended / Workouts / Exercises / Programs
- [x] بحث + chips فلاتر
- [x] Loading / Error / Empty
- [x] API + explore cache
- [x] نصوص الشاشة الرئيسية localized
- [ ] صور media على البطاقات
- [ ] Filter toolbar كامل
- [ ] حذف أو توصيل مكوّنات legacy غير المستخدمة
- [ ] A11y
- [x] 2 اختبارات state + theme boundary
- [x] iOS compile

---

## 09 — Reports (`MovitReportsScreen`) — **85%**

**الملفات:** `feature/reports/.../MovitReportsScreen.kt` · `SharedReportsRepository` · [`Reports-Page-Modernization-Spec.md`](Page-Specs/Reports-Page-Modernization-Spec.md)

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 88% | 22.0 |
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

### فجوات UX محددة

- Share / Export في `MovitInnerHost.kt` → `shell_report_share_coming_soon` / `shell_report_export_coming_soon` (placeholder موثق؛ legacy share screenshot فقط).
- Joint analysis من API **غير متوفرة بعد** — UI يعرض fallback + `JointMetricsDto` جاهز للربط.
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

## 02 — Session (`WorkoutSessionScreen`) — **48%**

**الملفات:** `feature/library/.../WorkoutSessionScreen.kt` · `WorkoutSessionSheets.kt` · `SharedWorkoutSessionRepository`

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 32% | 8.0 |
| Visual | 40% | 8.0 |
| DS | 87% | 13.0 |
| i18n/RTL | 47% | 7.0 |
| A11y | 25% | 2.5 |
| Tests | 70% | 7.0 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **48%** |

### فجوات UX محددة

- `SessionExerciseCard.kt:117-125`: أزرار **Swap / Edit / Delete** نصوص إنجليزية hardcoded.
- Edit mode: swap/delete/drag **غير مكتمل** مقارنة بـ `ProgramWorkoutActivity`.
- لا bottom sheets كاملة (Edit details depth) كما في prototype.
- `ExercisePrepareRoute` → `onStart = { /* Phase 07 */ }` فارغ في `MovitInnerHost.kt:64`.
- لا thumbnails + stat-chips (sets×reps، weight، rest) على البطاقات.

### Checklist

- [x] تحميل effective-plan + حفظ + substitutions API
- [x] Edit mode toggle + swap sheet (جزئي)
- [x] Loading / Error / Saving states
- [x] معظم نصوص الشاشة `session_*`
- [ ] i18n لأزرار بطاقة التمرين
- [ ] drag-reorder فعّال
- [ ] ربط Start → Prepare → legacy Training (Phase 07)
- [ ] thumbnails من explore cache
- [x] 2 اختبارات session state/mapper
- [x] iOS compile

---

## 05–06 — Library (`ExercisesLibraryScreen` / `WorkoutsLibraryScreen`) — **78%**

**الملفات:** `feature/library/.../ExercisesLibraryScreen.kt` · `WorkoutsLibraryScreen.kt` · `LibraryListViewModel.kt` · `LibraryFilterLogic.kt` · `components/LibraryToolbar.kt` · `LibraryMediaImage`

**Page-Spec:** [`Page-Specs/Library-Pages-Modernization-Spec.md`](Page-Specs/Library-Pages-Modernization-Spec.md)

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

- `ExercisePrepareScreen`: نصوص hardcoded (خارج 05–06).
- iOS: placeholder للصور (لا Coil KMP بعد).
- A11y: contentDescription جزئي (back/filter/image).

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

## 10 — Auth (`MovitAuthScreen`) — **85%**

**الملفات:** `feature/account/.../MovitAuthScreen.kt` · `MovitAuthViewModel.kt` · `AuthRepository`  
**Page-Spec:** [`Page-Specs/Auth-Page-Modernization-Spec.md`](Page-Specs/Auth-Page-Modernization-Spec.md)

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 86% | 21.5 |
| Visual | 78% | 15.6 |
| DS | 93% | 14.0 |
| i18n/RTL | 93% | 14.0 |
| A11y | 75% | 7.5 |
| Tests | 75% | 7.5 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **85%** |

### فجوات UX محددة (متبقية)

- Google OAuth فعلي (stub موثّق — زر + رسالة `auth_google_unavailable`).
- رسائل validation في ViewModel بالإنجليزية فقط (نمط account مشترك).
- Splash كـ launcher إنتاجي ما زال legacy (`SplashActivity`).

### Checklist

- [x] تدفق Splash / Intro / SignIn / SignUp / Forgot
- [x] `POST login|register` عند `MovitDataInstall`
- [x] `auth_*` keys كاملة (+ reset success · google stub · a11y)
- [x] inline error + `isLoading` يعطّل الأزرار
- [x] Google Sign-In stub موثّق (`GoogleSignInClicked` + effect)
- [x] `SignUpClicked` / `CreateAccountClicked` → `submitSignUp`
- [x] Forgot success panel (parity `ForgotPasswordActivity`)
- [x] Bootstrap: جلسة نشطة · `intro_seen` cache · splash مرتبط
- [x] A11y: logo · intro icons · page indicator · forgot success icon
- [x] `MovitAuthViewModelTest` (13+)
- [x] iOS compile

---

## 11 — Profile (`MovitProfileScreen`) — **70%**

**الملفات:** `feature/account/.../MovitProfileScreen.kt` · `MovitProfileViewModel.kt`

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 68% | 17.0 |
| Visual | 72% | 14.4 |
| DS | 93% | 14.0 |
| i18n/RTL | 93% | 14.0 |
| A11y | 30% | 3.0 |
| Tests | 50% | 5.0 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **70%** |

### فجوات UX محددة

- Language picker: `onClick = null` (`MovitProfileScreen.kt:100`).
- Appearance (light/dark): `onClick = null` (`:106`).
- Haptic switch: `enabled = false` (`:128-129`).
- Pro badge icons: `contentDescription = null`.

### Checklist

- [x] تبويب Account حقيقي (ليس Components catalog)
- [x] حالات signed-out / loading / error / signed-in
- [x] Logout + روابط Assessment / Level / Onboarding
- [x] `profile_*` keys
- [ ] Language + Appearance فعّالان
- [ ] Haptic toggle
- [ ] A11y
- [x] `MovitProfileViewModelTest` (1)
- [x] iOS compile

---

## 12 — Onboarding (`MovitOnboardingScreen`) — **74%**

**الملفات:** `feature/account/.../MovitOnboardingScreen.kt` · `MovitOnboardingViewModel.kt`

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 80% | 20.0 |
| Visual | 70% | 14.0 |
| DS | 93% | 14.0 |
| i18n/RTL | 93% | 14.0 |
| A11y | 20% | 2.0 |
| Tests | 50% | 5.0 |
| iOS | 100% | 5.0 |
| **الإجمالي** | | **74%** |

### فجوات UX محددة

- 7 خطوات موجودة لكن **بدون validation عميق** على بعض الحقول (مقارنة بـ legacy fragments).
- لا `contentDescription` على progress أو أيقونات الخطوات.
- خطأ API يظهر inline فقط — لا retry banner منفصل.

### Checklist

- [x] معالج 7 خطوات (About → Summary)
- [x] `PUT /api/mobile/training-profile`
- [x] `onboarding_*` keys (~43 استدعاء movitText)
- [x] `isSubmitting` يعطّل Continue
- [ ] validation parity مع legacy
- [ ] A11y
- [x] `MovitOnboardingViewModelTest` (1)
- [x] iOS compile

---

## 13 — Assessment (`MovitAssessmentScreen`) — **55%**

**الملفات:** `feature/account/.../MovitAssessmentScreen.kt` · `MovitAssessmentViewModel.kt`

### تفصيل الأوزان

| المجال | الدرجة | مساهمة |
|--------|--------|--------|
| Functional | 56% | 14.0 |
| Visual | 55% | 11.0 |
| DS | 87% | 13.0 |
| i18n/RTL | 80% | 12.0 |
| A11y | 25% | 2.5 |
| Tests | 0% | 0.0 |
| iOS | 90% | 4.5 |
| **الإجمالي** | | **55%** |

### فجوات UX محددة

- **كاميرا وهمية:** `assessment_camera_placeholder` + `CircularProgressIndicator` — لا body scan حقيقي (Phase 07).
- PAR-Q UI موجود؛ **لا AssessmentEngine** مشترك بعد.
- `FakeAssessmentPreviewData.kt`: نتائج إنجليزية ثابتة.
- **صفر** unit tests للـ ViewModel.
- Scanner icon: `contentDescription = null`.

### Checklist

- [x] 3 phases PreScreening / BodyScan / Results
- [x] `assessment_*` keys للـ UI
- [ ] كاميرا / pose (Phase 07)
- [ ] engine scoring مشترك (Pre-06 WS-B / Phase 07)
- [ ] ViewModel tests
- [ ] A11y
- [x] iOS compile (ضمن account target)

---

## 14 — Level (`MovitLevelScreen`) — **68%**

**الملفات:** `feature/account/.../MovitLevelScreen.kt` · `MovitLevelViewModel.kt` · `SharedLevelRepository.kt` · `LevelApiMapper.kt`

**Page spec:** [`Page-Specs/Level-Page-Modernization-Spec.md`](Page-Specs/Level-Page-Modernization-Spec.md)

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

## قواعد رفع النسبة

لا تُرفع صفحة **≥5 نقاط** بدون:

1. إغلاق loading + empty + error للحالات الرئيسية.
2. مراجعة RTL (ar) على جهاز أو emulator.
3. مراجعة dark mode.
4. iOS compile أخضر للموديول.
5. توثيق الفجوات المتبقية في هذا الملف.

---

## روابط

- [`Sync-App-Pages.md`](Sync-App-Pages.md) — خريطة prototype ↔ legacy ↔ KMP
- [`Android-UI-UX-Modernization-Status.md`](Android-UI-UX-Modernization-Status.md) — ملخص تنفيذي
- [`Android-KMP-Mobile-UI-UX-Phase-05-Page-By-Page-Modernization-Plan.md`](Android-KMP-Mobile-UI-UX-Phase-05-Page-By-Page-Modernization-Plan.md)
