# Program Flow Page Spec (15)

آخر تحديث: 2026-06-09

## Implementation Status

- تدفق KMP كامل في `feature:library`: قائمة البرامج → خطة الأسبوع (أيام) → التقرير الأسبوعي → `WorkoutSession`.
- مسارات shell داخلية: `ProgramList` · `ProgramWeekPlan` · `WeeklyReport`.
- الربط من Train (`OpenProgramList` · `OpenProgramWeekPlan` · `OpenWeeklyReport`) و Explore (`OpenProgramList` · `OpenProgramDetail` → `ProgramWeekPlan` أسبوع 1).
- **API bridge (إنتاج):** `SharedProgramFlowRepository` → `ProgramFlowSyncRepository` (`ExploreSync` · `HomeSync` · `PlanSync` · `fetchProgram` · `progress-metrics` · `ReportsSync` للمقاييس).
- **Preview/offline:** `FakeProgramFlowRepository` عند عدم تثبيت `MovitData` أو لمعرّفات `ProgramFlowPreviewData`.
- i18n: `program_flow_*` كامل (en/ar + `MovitArabicStrings` للاختبارات).
- اختبارات: `ProgramFlowStateTest` (5) · shell/train navigation tests — خضراء.
- **مفتوح:** iOS share sheet · week-over-week metrics عميقة.
- **✅ أُغلق (2026-06-12):** قائمة كل الأسابيع في `WeeklyReportScreen` · Share Android (`MovitAppShellEffect.ShareText`) · `coverImageUrl` على بطاقات البرامج.

## Legacy Reference

| الشاشة | Legacy |
|--------|--------|
| قائمة البرامج | `ProgramListActivity` |
| خطة الأسبوع | `ProgramDayActivity` |
| التقرير الأسبوعي | `WeeklyReportActivity` |

## Prototype (`15-program-flow.html`)

حالات `state-switch`:

1. **list** — فلاتر chips + بطاقات برامج
2. **week** — day pills (done / today / planned / rest) + CTA «Open today's session»
3. **weekly** — hero lime + metric tiles + bar chart + Share

## User Goals

- تصفح البرامج المتاحة وفلترتها حسب المستوى.
- رؤية أيام الأسبوع وحالة كل يوم.
- فتح جلسة اليوم أو يوم محدد (بدون كاميرا مباشرة).
- مراجعة ملخص الأسبوع ومشاركته لاحقاً.

## KMP Routes

```
Train / Explore
    → MovitInnerRoute.ProgramList
        → ProgramWeekPlan(programId, weekNumber)
            → WorkoutSession (session key)  [optional]
            → WeeklyReport(programId, weekNumber)
```

## Shell Wiring

| Effect | Route |
|--------|-------|
| `MovitTrainEffect.OpenProgramList` | `ProgramList` |
| `MovitTrainEffect.OpenWeeklyReport` | `WeeklyReport` |
| `MovitTrainEffect.OpenProgramWeekPlan` | `ProgramWeekPlan` |
| `MovitExploreEffect.OpenProgramList` | `ProgramList` |
| `MovitExploreEffect.OpenProgramDetail` | `ProgramWeekPlan` (أسبوع 1) |

## Files

- `ProgramFlowModels.kt` · `ProgramFlowRepository.kt` · `ProgramFlowPreviewData.kt`
- `ProgramListScreen.kt` · `ProgramWeekPlanScreen.kt` · `WeeklyReportScreen.kt`
- ViewModels + `MovitLibraryRoutes.kt`
- `MovitInnerRoute.kt` · `MovitInnerHost.kt` · `MovitAppShellViewModel.kt`
- `core/resources` — `program_flow_*` strings (en/ar)
- `ProgramFlowStateTest.kt` · shell/train tests

## Out of Scope (Phase 05)

- كاميرا / `TrainingActivity`
- API موحّد للمقاييس (fallback preview)
- صور الوسائط على بطاقات البرامج
- تعديل البرنامج (صفحة 07 Edit)

## Verification

```bash
./gradlew :feature:library:testDebugUnitTest
./gradlew :feature:shell:testDebugUnitTest
./gradlew :feature:train:testDebugUnitTest
./gradlew :feature:shell:compileKotlinIosSimulatorArm64
```
