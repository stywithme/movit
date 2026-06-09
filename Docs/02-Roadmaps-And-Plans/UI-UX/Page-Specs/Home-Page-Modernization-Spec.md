# Home Page Modernization Spec

آخر تحديث: 2026-06-09

## Implementation Status

- تم تنفيذ `MovitHomeScreen` داخل `android-poc/feature/home` وربطه في `feature:shell`.
- البيانات عبر `SharedHomeRepository` → `GET /api/mobile/home` مع كاش offline-first.
- بطاقة المستوى تفتح `MovitInnerRoute.LevelProfile` عبر `MovitHomeEffect.OpenLevel`.
- مكوّنات `HomeHeroSummary`، `TodayPlanCard`، `HomeQuickActions`، `HomeProgressSection`، `HomeLevelCard` موصولة في الشاشة.
- Hero progress يعكس `weeklyCompletionPercent` من API (أسبوع البرنامج النشط).
- Quick actions تستخدم `MovitIconBox` كما في `08-home.html`.

## Current Implementation (Legacy)

- **المدخل:** `HomeFragment` + `fragment_home.xml`
- **المستودع:** `HomeRepository` → `/api/mobile/home` (SharedPreferences cache)
- **حالات trainMode.status:**
  - `no_assessment` — CTA فحص الجسم
  - `no_plan` — خطة قيد الإنشاء
  - `active` — برنامج نشط + تمرين اليوم + START
  - `rest_day` — يوم راحة
  - `program_complete` — إعادة تقييم
  - `reassessment_due` — بانر إعادة تقييم
- **التنقل القديم:**
  - Level card → `LevelProfileActivity`
  - Body scan → `PreScreeningActivity`
  - Start workout → `ProgramWorkoutActivity`
  - View program → `PlanOverviewActivity`
  - Avatar → `ProfileActivity`
  - Reports → تبويب Reports
  - Explore → تبويب Explore
  - Alerts: `reassessment_due` → PreScreening · `progression_applied` → Train tab

## KMP Mapping

| Legacy | KMP |
|--------|-----|
| `HomeFragment` | `MovitHomeScreen` + `MovitHomeViewModel` |
| `HomeRepository` | `SharedHomeRepository` + `HomeSyncRepository` |
| Level card click | `MovitHomeEvent.LevelCardClicked` → `OpenLevel` |
| Body scan CTA | `BodyScanClicked` → `OpenAssessment` |
| Start workout | `StartTodayPlanClicked` → `OpenTrain` |
| View program | `ViewProgramClicked` → `OpenTrain` |
| View plan (journey) | `ViewPlanClicked` → `OpenLevel` |
| Quick Explore/Reports | `QuickActionClicked` → shell tabs |

## Content Inventory (API)

- `user`: name, avatar, level, levelCode, bodyScore, levelProgress
- `stats`: thisWeekExecutions, avgFormScore, streak, totalMinutes
- `trainMode`: status, activeProgram, todayWorkout, nextReassessment
- `alerts[]`: type, title/message (ar/en)
- `recentWorkouts[]`: exercise, formScore, reps, date
- `levelProfile` (fallback قديم)

## UX Target (prototype `08-home.html`)

- تحية + metric row (أسبوع / شكل / سلسلة)
- بطاقة مستوى قابلة للنقر مع شريط تقدم
- تنبيه plan adjusted (حالة alert)
- برنامج نشط + خطة اليوم + CTA
- Body scan CTA (حالة scan)
- Empty no program
- Journey: timeline + reassessment
- Recent activity + quick actions (Explore · Reports)

## Layout (compact)

```text
MovitScaffold (avatar → Profile)
  HomeHeroSummary (greeting + week progress bar)
  MovitStatTileRow (metrics)
  HomeLevelCard
  MovitInsightCard (alert)
  Active program card
  TodayPlanCard
  MovitAccentBlock (body scan)
  MovitEmptyState (no program)
  HomeProgressSection (week % · form · streak · minutes)
  Journey list + View plan → Level
  Recent activity
  HomeQuickActions (icon-box grid)
```

## Acceptance Criteria

- [x] UDF: `MovitHomeViewModel` + events/effects
- [x] Level card → `MovitInnerRoute.LevelProfile`
- [x] Hero progress من API وليس 0% ثابت
- [x] مكوّنات `components/` موصولة
- [x] Quick actions مع `MovitIconBox`
- [x] نصوص `home_*` من `:core:resources`
- [x] `contentDescription` على CTAs الرئيسية
- [x] اختبارات ViewModel + mapper
- [x] `:feature:home:testDebugUnitTest` · `:feature:shell:testDebugUnitTest` · `:app:assembleDebug`

## خارج النطاق

- Pull-to-refresh (legacy فقط)
- Catch-up / paused plan UI
- كاميرا Assessment حية
- Navigation لـ Report detail من صفوف النشاط
