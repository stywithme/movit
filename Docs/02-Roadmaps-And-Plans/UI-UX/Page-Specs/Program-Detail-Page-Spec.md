# Program Detail Page Spec (07)

آخر تحديث: 2026-06-12

## Implementation Status

- تم تنفيذ `ProgramDetailScreen` داخل `kmp-app/feature/library` مع تبويبي Overview و Customize copy.
- البيانات: Explore cache + `ProgramDetailApiMapper` من `ProgramExportDto` عند `MovitData`؛ preview fixture عند عدم التثبيت.
- **Start program:** `MovitData.plan.enrollProgram` عند أول Start + `sessionKeyForProgram` من export/home.
- **Weekly report:** journey header → `MovitInnerRoute.WeeklyReport(selectedWeekNumber)`.
- Hero: `MovitRemoteImage` + overlay؛ stat grid عبر `ProgramDetailStrings` / `program_stat_*`.
- Edit tab: reason/scope/settings — **drag/reorder مؤجّل**.

## المراجع

| المصدر | المسار |
|--------|--------|
| Prototype | `Docs/.../prototypes/07-program.html` |
| Legacy | `kmp-app/app/.../ProgramDetailActivity.kt` |
| KMP | `feature/library/ProgramDetailScreen.kt` |

## أهداف المستخدم

- فهم البرنامج (مدة، تكرار، حمل) قبل الالتزام.
- استعراض هيكل الأسابيع والأيام والتقدم.
- بدء البرنامج أو متابعة الجلسة التالية.
- تخصيص نسخة شخصية دون المساس بالقالب الأصلي.

## تسلسل المحتوى (Visual hierarchy)

1. Header: رجوع + Customize/Save
2. Hero: kickers، عنوان، وصف
3. Tabs: Overview | Customize copy
4. Stat grid 2×2: Duration، Weekly target، Session time، Plan load
5. Copy card (عند التسجيل): نسخة شخصية + Resume
6. Overview: week strip → week card(s) + day timeline → detail cards
7. Edit: note → reasons → scope → impact → settings
8. Bottom dock: معلومات الجلسة التالية + CTA

## الحالات

| الحالة | العرض |
|--------|--------|
| Loading | `MovitLoadingState` |
| Error | `MovitErrorState` + retry |
| Not enrolled | بدون copy card؛ CTA «ابدأ البرنامج» |
| Enrolled | copy card + dock «ابدأ الجلسة التالية» |
| Edit tab | شريط Cancel/Save أسفل الشاشة |

## الملفات

- `ProgramDetailModels.kt` — نماذج UI
- `ProgramDetailMapper.kt` — تحويل Explore → state
- `ProgramDetailPreviewData.kt` — fixture أسابيع/أيام
- `ProgramDetailViewModel.kt`
- `ProgramDetailScreen.kt`
- `components/ProgramDetailComponents.kt`
- `MovitLibraryRoutes.kt` — `ProgramDetailRoute`
- `core/resources` — مفاتيح `program_*`

## Acceptance Criteria

- [x] Hero + tabs + stat grid يطابق ترتيب النموذج
- [x] Week strip + week card + day rows بحالات Done/Next/Rest
- [x] Edit tab بهيكل النموذج (reason/scope/settings)
- [x] Start → `WorkoutSessionKeys` → `WorkoutSessionRoute`
- [x] نصوص `program_*` في ar/en
- [x] `ProgramDetailViewModelTest` + `ProgramDetailMapperTest`
- [x] API enrollment (`MovitData.plan.enrollProgram`)
- [x] Weekly report navigation (صفحة 15)
- [ ] Drag sessions / exercise param editor في Edit

## Gradle

```bash
./gradlew :feature:library:testDebugUnitTest
./gradlew :feature:library:compileKotlinIosSimulatorArm64
```
