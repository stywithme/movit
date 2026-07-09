# Admin-Dashboard — مسار المتدرب

| | |
|---|---|
| **Status** | `ACTIVE` |
| **SSOT for** | Admin pages consumed by trainee journey |
| **Code** | `Admin-Dashboard/src/app/admin/`, `Admin-Dashboard/src/components/layout/Sidebar.tsx` |
| **Verified** | 2026-06-22 |

---

## التنقل

`Sidebar.tsx` — عناصر ذات صلة:

- Programs → `/admin/programs`
- Programs & map → `/admin/programs/map`
- Assessment Templates → `/admin/assessment-templates`
- Levels → `/admin/levels` · Analytics → `/admin/analytics/levels`

**ملغى:** مسارات Booking / Doctor work-time / Close-time — أُزيلت من المنتج.

---

## البرامج

- قائمة/تحرير: `src/app/admin/programs/`
- حقول الوصفة: `levelRangeMin/Max`, `programAttributes`, `exitRecommendations`, `nextProgramId`, `autoAssignable`, `weeklyPlannedWorkoutsTarget`, …

---

## قوالب التقييم

`src/app/admin/assessment-templates/` — نشر، تحرير، حذف → `/api/admin/assessment-templates`.

---

## المستويات

`src/app/admin/levels/` — CRUD → جدول `Level` يغذي `scoreToLevel` في الخادم.

---

## قواعد التقدم

`backend/src/modules/progression/progression-rules-admin.controller.ts` — `admin/progression-rules`.

---

## ما لا يُدار من اللوحة

- `TrainingProfile` للمستخدم النهائي — لا صفحة مخصّصة.
- حجز المواعيد — ملغى.
