# Explore Page Modernization Spec

آخر تحديث: 2026-06-09

## Implementation Status

- تم تنفيذ `MovitExploreScreen` داخل `android-poc/feature/explore` وربطها بـ `feature:shell`.
- أُغلقت فجوات Phase 05 مقابل `04-explore.html` و`ExploreFragment`: زر Filter، muscle-strip، workout-intro، focus pills، صور الوسائط، chips فرعية للتمارين.
- التنقل يطابق legacy عبر effects: تمرين → `ExercisePrepare` (PreWorkout)، جلسة → `WorkoutSession` (WorkoutDetail).
- «عرض الكل» داخل القسم يغيّر الفلتر الرئيسي ويمرّر التمرير داخل التبويب (لا يفتح Library مباشرة).

## Current Implementation

- **Legacy:** `ExploreFragment` · `fragment_explore.xml`
- **KMP:** `MovitExploreRoute` · `MovitExploreScreen` · `MovitExploreViewModel`
- **بيانات:** `SharedExploreRepository` + `/api/mobile/explore` مع fallback إلى `FakeExploreRepository`

## User Goals

- تصفّح التمارين والجلسات والبرامج من تبويب واحد.
- بحث سريع + فلاتر رئيسية (All / Exercises / Workouts / Programs).
- فلاتر ثانوية للجلسات (مستوى، مدة قصيرة) وللتمارين (فئة/عضلة).
- فتح تفاصيل الجلسة أو شاشة الإعداد للتمرين بنقرة واحدة.

## Layout Spec (compact)

1. `MovitScaffold` + عنوان/وصف.
2. شريط بحث + زر Filter (toggle للفلاتر الثانوية).
3. صف chips رئيسي (All · Exercises · Workouts · Programs).
4. ملخص النتائج (`N workouts • M exercises`).
5. Recommended hero (بطاقة كبيرة + صورة).
6. قسم Workouts: intro · muscle-strip · بطاقات wide-media مع focus pill.
7. قسم Exercises: category chips · شبكة 2×2 مع صور.
8. قسم Programs: بطاقة برنامج واحدة مع صورة.

## Visual Spec

- ألوان/spacing من Design System فقط.
- صور عبر `MovitRemoteImage` (Coil على Android، placeholder على iOS حتى دعم كامل).
- focus pill = `focusLabel` على `MovitMediaCard`.
- لا نصوص hardcoded في المكوّنات — مفاتيح `explore_*` في `:core:resources`.

## Effects (shell)

| حدث | Effect | Shell route |
|-----|--------|-------------|
| نقرة جلسة | `OpenWorkoutSession(id)` | `WorkoutSession` |
| نقرة تمرين | `OpenExercisePrepare(id)` | `ExercisePrepare` |
| نقرة برنامج | `OpenProgramDetail(id)` | `ProgramDetail` |

## Acceptance Criteria

- [x] Filter button + muscle-strip + workout-intro + exercise sub-chips
- [x] `imageUrl` على Hero / Media / Exercise cards
- [x] focus pills على بطاقات الجلسات
- [x] `ExploreHero` / `ExploreExerciseList` موصولان ومترجمان
- [x] اختبارات state + shell navigation effects
- [x] iOS compile (designsystem `expect/actual` للصور)
- [ ] A11y كامل (content descriptions، تركيز لوحة المفاتيح) — فجوة مشتركة Phase 05

## Tests

- `MovitExploreStateTest` — فلاتر، effects، toggle Filter
- `ExploreThemeBoundaryTest` — لا `MovitTheme` داخل الشاشة
- `MovitAppShellStateTest` — `OpenWorkoutSession` / `OpenExercisePrepare`
