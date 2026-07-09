| | |
|---|---|
| **Status** | `EVOLUTION` |
| **Date** | 2026-06-22 |
| **Migration** | [`backend/prisma/migrations/20260622120000_remove_booking_doctor_feature/migration.sql`](../../../backend/prisma/migrations/20260622120000_remove_booking_doctor_feature/migration.sql) |
| **Verified** | 2026-06-22 |

# إزالة حجز الطبيب وجلسات Google Meet

## السياق

Movit يركّز على التدريب الموجّه بالذكاء الاصطناعي والاشتراكات — وليس على حجز استشارات طبيب أو جلسات فيديو منفصلة. المجال كان موروثاً من نموذج سابق ولم يعد ضمن نطاق المنتج.

## ما تغيّر

### Database (migration `20260622120000`)

جداول محذوفة:

| الجدول | الوصف السابق |
|--------|--------------|
| `bookings` | حجوزات المستخدم |
| `booking_payments`, `booking_payment_items`, `booking_payment_events` | دفع الحجز (MyFatoorah) |
| `booking_reports` | تقارير الحجز |
| `doctor_work_times` | أوقات عمل الطبيب |
| `close_times` | أوقات إغلاق الحجز |
| `admin_google_meet_connections` | ربط Google Meet للأدمن |

أعمدة وإعدادات:

- `admins.isDoctor` — محذوف
- `plans.freeDoctorSessionsLimit` — محذوف
- مفاتيح `system`: `allow_booking`, `booking_duration`, `booking_price`, `booking_currency`, `reschedule_allowed_time`, `max_advance_booking_days`, `min_booking_hours`

صلاحيات محذوفة (`permissions.subject`):

`Booking`, `BookingReport`, `DoctorWorkTime`, `CloseTime`, `ReportBooking`, `BookingAnalytics`

### Backend

وحدات NestJS محذوفة: `booking`, `booking-payments`, `booking-report`, `close-time`, `doctor-work-time`, `google-meet`؛ حارس `doctor.guard.ts`.

عميل MyFatoorah نُقل إلى `backend/src/lib/payments/` للاشتراكات (ليس حجزاً).

### Admin Dashboard

صفحات محذوفة: الحجوزات، تقارير الحجز، أوقات الإغلاق، أوقات عمل الطبيب، تحليلات الحجوزات.

### Docs

- `PAYMENT-RUNBOOK.md` (حجز) — محذوف؛ الدفع الموثّق في [`00-Active-Reference/Operations/Payment-gateway/`](../00-Active-Reference/Operations/Payment-gateway/) للاشتراكات
- ميزة الحجز — أُزيلت من الكود؛ أي وثائق قديمة في `99-Archive/` للتاريخ فقط

## المرجع الحالي

| الموضوع | SSOT |
|---------|------|
| الدفع | [`Operations/Payment-gateway/README.md`](../00-Active-Reference/Operations/Payment-gateway/README.md) |
| API | [`Contracts/API_ENDPOINTS.md`](../00-Active-Reference/Contracts/API_ENDPOINTS.md) |
| رحلة المستخدم | [`Product-Master/Journey-Index.md`](../00-Active-Reference/Product-Master/Journey-Index.md) |

## متابعة

- [x] Migration Prisma
- [x] حذف وحدات Backend / Dashboard
- [ ] تحديث `Journey-Index` صف Payment (اشتراك vs حجز) عند تفعيل الدفع
