# Program Detail Page Spec (07)

آخر تحديث: 2026-06-09

## Implementation Status

- تم تنفيذ `ProgramDetailScreen` داخل `android-poc/feature/library` مع تبويبي Overview و Customize copy.
- البيانات الحالية: Explore cache + `ProgramDetailPreviewData` للأسابيع/الأيام حتى يُربط API البرنامج الكامل (enrollment، preview، sync).
- **Start program** يفعّل التسجيل المحلي (fake enroll) ويولّد `WorkoutSessionKeys` للانتقال إلى `WorkoutSessionRoute`.
- Edit tab: reason cards، scope، impact، إعدادات أسبوعية، pause — بدون drag/reorder للجلسات (مؤجل لمرحلة 15).

## المراجع

| المصدر | المسار |
|--------|--------|
| Prototype | `Docs/.../prototypes/07-program.html` |
| Legacy | `android-poc/app/.../ProgramDetailActivity.kt` |
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
- [ ] API enrollment حقيقي (bridge legacy)
- [ ] Weekly report navigation (صفحة 15)
- [ ] Drag sessions / exercise param editor في Edit

## Gradle

```bash
./gradlew :feature:library:testDebugUnitTest
./gradlew :feature:library:compileKotlinIosSimulatorArm64
```
