# Library Pages Modernization Spec (05 Exercises + 06 Workouts)

آخر تحديث: 2026-06-09

## Implementation Status

- تم تنفيذ `ExercisesLibraryScreen` و`WorkoutsLibraryScreen` داخل `android-poc/feature/library`.
- البيانات تُحمَّل عبر `LibraryRepository` → `ExploreRepository` (cache/API مشترك مع Explore).
- الصور: `LibraryMediaImage` — Coil على Android، placeholder على iOS حتى دمج محمّل KMP.
- النصوص: مفاتيح `library_*` في `:core:resources` (EN + AR).
- الاختبارات: `LibraryFilterLogicTest` + `LibraryListViewModelTest` في `:feature:library:testDebugUnitTest`.

## Current Implementation

### Legacy

| صفحة | مدخل Legacy | سلوك |
|------|-------------|------|
| 05 Exercises | `ExerciseListActivity` → `ExercisesFragment` | grid 2 أعمدة، بحث، chips فئات، صور، badges |
| 06 Workouts | `WorkoutListActivity` | قائمة wide cards، صور، مدة، مستوى، metadata |

### KMP

| ملف | دور |
|-----|-----|
| `ExercisesLibraryScreen.kt` | grid + toolbar + empty state |
| `WorkoutsLibraryScreen.kt` | wide list + featured أول بطاقة |
| `LibraryListViewModel.kt` | بحث، فلاتر، see-more، sheet |
| `LibraryFilterLogic.kt` | منطق الفلترة (قابل للاختبار) |
| `components/ExerciseGridCard.kt` | بطاقة media grid |
| `components/WideWorkoutCard.kt` | بطاقة wide media |
| `components/LibraryToolbar.kt` | بحث + filter button + chips + sheet |
| `MovitLibraryRoutes.kt` | routes مع ViewModel |

## User Goals

- تصفّح كامل مكتبة التمارين أو الجلسات بعد Explore → See all.
- البحث السريع بالاسم/العضلة/الفئة.
- فلترة بـ chips أفقية + sheet من زر Filter.
- معرفة عدد النتائج (`Showing X of Y`).
- فتح تمرين → Prepare (03) أو جلسة → Session (02).

## Content Inventory

- Header: رجوع إلى Explore + tag `N items`.
- Toolbar: search، filter button، chips، result summary.
- Exercises: grid 2×N، badge (Beginner/Equipment/…)، صورة، عنوان، metadata.
- Workouts: wide cards، focus pill، level tag، description، caps (exercises · min · focus).
- See more: يوسّع من 6 إلى الكل.
- Empty: `MovitEmptyState` + Clear filters.

## UX Target

- مطابق لـ prototypes `05-exercises.html` و`06-workouts.html`.
- أول بطاقة workout = featured (`Best for legs` / focus lime).
- Chip ثاني (Lower body / Legs) = accent في الـ strip.
- Loading / Error / Empty recoverable.

## Layout Spec

- Compact: single column scroll؛ exercises grid داخل `LazyVerticalGrid`.
- Header `MovitInnerPageHeader` + `MovitTag` للعدد.
- DS: `MovitSearchBar`, `MovitFilterRow`, `MovitTag`, `MovitEmptyState`, `MovitButton`.

## Data & Navigation

```text
Explore cache/API
  → LibraryRepository.loadContent()
  → LibraryListViewModel (filter + paginate)
  → Screen
  → onItemClick → WorkoutSessionRoute | ExercisePrepareRoute
```

## Verification

```text
:feature:library:testDebugUnitTest
:feature:library:compileKotlinIosSimulatorArm64
:app:assembleDebug
```

Visual QA: [`Android-KMP-Mobile-Visual-QA-Checklist.md`](../Android-KMP-Mobile-Visual-QA-Checklist.md) — Library 05–06.

## Known Gaps (post this pass)

- `ProgramDetailScreen` / `ExercisePrepareScreen`: نصوص hardcoded (07 / 03).
- iOS: لا تحميل صور شبكة حقيقي بعد (placeholder فقط).
- Filter sheet: chips فقط؛ لا muscle-strip متقدم كـ legacy ExploreFragment.

## References

- Prototypes: `prototypes/05-exercises.html`, `06-workouts.html`
- Scorecard: [`Page-Scorecards.md`](../Page-Scorecards.md)
- Sync map: [`Sync-App-Pages.md`](../Sync-App-Pages.md)
