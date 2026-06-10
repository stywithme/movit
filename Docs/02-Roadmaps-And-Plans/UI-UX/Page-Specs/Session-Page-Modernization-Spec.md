# Session Page Modernization Spec (02 — Program Day)

آخر تحديث: 2026-06-09

## Implementation Status

- KMP: `WorkoutSessionScreen` في `feature:library` — محرر يوم البرنامج (ليس الكاميرا الحية).
- Legacy مرجع: `ProgramWorkoutActivity` — edit، swap، reorder، rest blocks، save، start workout.
- API: `SharedWorkoutSessionRepository` → effective-plan، substitutions، day customizations عبر `MovitData.workoutSession`.
- التنقل: `onStartWorkout` → `ExercisePrepareRoute` (03)؛ `onStart` في Prepare يبقى لـ Phase 07 (لا كاميرا في Phase 05).
- i18n: مفاتيح `session_*` في `:core:resources` (EN + AR).
- اختبارات: `WorkoutSessionStateTest`، `WorkoutSessionApiMapperTest`، shell navigation test.

## Current Implementation

| Legacy | KMP |
|--------|-----|
| `ProgramWorkoutActivity` | `WorkoutSessionScreen` + `WorkoutSessionViewModel` |
| `DayCustomizationStore` + API sync | `SharedWorkoutSessionRepository.saveSession` |
| Swap bottom sheet | `SessionSheet.Swap` |
| Edit exercise dialog | `SessionSheet.EditDetails` |
| Add exercise / rest | `SessionSheet.AddExercise` + `addRestBlock` |
| Drag reorder timeline | `moveBlock` + drag handle في edit mode |
| Start → `TrainingActivity` | Start → `ExercisePrepareScreen` (03) |

## User Goals

- مراجعة تمارين اليوم (warm-up / main / cool-down).
- تخصيص الخطة: swap، تعديل sets/reps/weight/rest، إضافة تمرين أو راحة، إعادة ترتيب.
- حفظ التعديلات على الخادم عند الخروج من edit mode.
- بدء التدريب → شاشة Prepare (بدون فتح الكاميرا في هذه المرحلة).

## Layout Spec (prototype `02-session.html`)

- Inner header: Back + Edit/Done.
- Day header: title، subtitle، stat strip (exercises / duration / sets).
- Sections: Warm-up · Main · Cool-down مع `MovitSectionHeader`.
- Exercise cards: thumbnail + stat chips (sets×reps أو hold، weight، rest).
- Rest blocks: coral tint row.
- Edit dock: Add exercise + Rest.
- View dock: Ready to train + Start.
- Sheets: Swap · Edit details · Add exercise · Edit rest.

## Data & API

- مفتاح الجلسة: `WorkoutSessionKeys.encode(programId, week, day, plannedWorkoutId)`.
- تحميل: `GET effective-plan` + explore cache للأسماء والصور (`exerciseImageUrl` على Android).
- حفظ: `PUT` customizations عبر `WorkoutSessionSaveEncoder`.
- Substitutions: `fetchSubstitutionCandidates(slug)`.

## Out of Scope (Phase 05)

- `TrainingActivity` / كاميرا حية (16).
- Rest timer حي في Prepare (03 — لاحقاً).
- Planned workout cards متعددة في يوم واحد (legacy يعرض عدة workouts؛ KMP يعرض workout مستهدف واحد per route).

## Acceptance Checklist

- [x] تحميل effective-plan + حفظ + substitutions
- [x] Edit mode: swap، edit details، delete، drag-reorder، add exercise/rest
- [x] Bottom sheets كاملة
- [x] Stat chips + thumbnails (من explore / legacy cache)
- [x] i18n لأزرار البطاقة والتنسيقات
- [x] Start → Prepare navigation
- [x] Unit tests + iOS compile target
