# الشاشة الرئيسية للموبايل: `GET /mobile/home` و`trainMode`

## المتحكم

`backend/src/modules/mobile-sync/mobile-home.controller.ts` — دالة `buildHomeData` و`buildTrainMode`.

## بيانات المستخدم في الاستجابة

- من `UserLevelProfile` الأحدث: `user.level`, `levelCode` (عبر `buildLevelProgress`), `bodyScore` من آخر `BodyScanResult`.
- إحصاءات و`recentSessions` من جداول التدريب.

## حالات `trainMode.status`

المذكورة في تعليق الملف ورمز `buildTrainMode`:

| الحالة | الشرط (من الكود) |
|--------|-------------------|
| `no_assessment` | لا يوجد `BodyScanResult` للمستخدم |
| `reassessment_due` | يوجد `ReassessmentSchedule` بحالة `pending` و`scheduledDate <= now` |
| `no_plan` | يوجد تقييم لكن لا فتحة خطة نشطة أو لا برنامج مرتبط بالفتحة النشطة |
| `program_complete` | منطق الموضع في البرنامج يعتبر البرنامج مكتملًا (`position.isProgramComplete`) |
| `rest_day` | يوم راحة في القالب، أو يوم خارج جدول المستخدم، أو كل الجلسات لليوم مكتملة (`day_complete`) |
| `active` | يوم تدريب مع جلسة غير مكتملة |

**ملاحظة:** استعلام `pendingReassessment` في `buildHomeData` يستخدم `status: 'pending'` فقط (لا يشمل `overdue` في هذا الاستعلام)، بينما فرع `reassessment_due` يقارن التاريخ لجدول `pending`.

## مصادر البيانات داخل `buildTrainMode`

- `activePlan` مع `programs where status: 'active'`.
- `resolveTrainingPositionMeta`, `countTrainingDaySlots`, `getLastProgramSessionCompletedAt` — من وحدات الخطة.
- `effectivePlanService.getEffectivePlan` لعدّ التمارين الفعلية للجلسة الحالية عند الحالة `active`.

## التنبيهات (`alerts`)

دالة `buildAlerts` — أنواع مثل `reassessment_due`, `progression_applied`, `level_up`, `streak_at_risk` حسب حالة التدريب والتقدم غير المقروء.

## android-poc

`MobileSyncApi.getHomeData` → `GET api/mobile/home`؛ الاستهلاك في `HomeFragment` و`HomeRepository` (بحث في المشروع).
