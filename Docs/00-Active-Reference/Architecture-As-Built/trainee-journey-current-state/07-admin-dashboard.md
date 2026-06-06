# Admin-Dashboard — ما يخص مسار المتدرب

## التنقل (Sidebar)

المصدر: `Admin-Dashboard/src/components/layout/Sidebar.tsx` — عناصر ذات صلة:

- Programs → `/admin/programs`
- Programs & map → `/admin/programs/map`
- Assessment Templates → `/admin/assessment-templates`
- Levels → `/admin/levels`؛ تحليلات مستويات → `/admin/analytics/levels`

## البرامج (Programs)

- قائمة: `src/app/admin/programs/page.tsx` — استدعاءات `/api/admin/programs`.
- إنشاء/تعديل: `src/app/admin/programs/new/page.tsx`, `src/app/admin/programs/[id]/edit/page.tsx`.
- الحقول ذات الصلة بالوصفة في الـ backend تشمل `levelRangeMin`/`Max`, `programAttributes`, `exitRecommendations`, `nextProgramId`, `autoAssignable`, `isPublished`, `weeklyPlannedWorkoutsTarget`, إلخ (انظر مخطط Prisma في `backend/prisma/schema.prisma`).

## خريطة البرامج والقوالب

- `src/app/admin/programs/map/page.tsx` — يجلب برامج وقوالب تقييم لعرض العلاقات وروابط التحرير.

## قوالب التقييم

- قائمة: `src/app/admin/assessment-templates/page.tsx` — نشر/إلغاء نشر، حذف.
- إنشاء/تعديل: `new/page.tsx`, `[id]/edit/page.tsx` — استدعاءات `/api/admin/assessment-templates`.

## المستويات (Levels)

- `src/app/admin/levels/page.tsx` — CRUD عبر `/api/admin/levels` (حسب تنفيذ مسار الـ API في المشروع).
- هذه السجلات تغذي `scoreToLevel` في الـ backend عبر جدول `Level` (`entryThreshold`, `maxThreshold`, …).

## قواعد التقدم (Progression Rules)

الإدارة من الـ backend: `backend/src/modules/progression/progression-rules-admin.controller.ts` — مسارات `admin/progression-rules`. لوحة الإدارة قد تستدعيها عبر طبقة API إن وُجدت في `Admin-Dashboard/src/app/api/...` (يُنصح بالبحث عن `progression-rules` في المشروع عند إضافة واجهة).

## صلاحيات الوصول (Middleware)

`Admin-Dashboard/src/middleware.ts` يربط المسارات بمواضيع مثل `Program`, `ProgramMap`, `AssessmentTemplate`.

## ما لا يُدار عادة من هذه الصفحات

- ملف التدريب الفردي للمستخدم (`TrainingProfile`) — لا توجد صفحة مخصّصة في نتائج البحث المستخدمة لهذا التوثيق.
