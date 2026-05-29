> **Status:** `ARCHIVED` â€” superseded, cancelled, or historical review only.
> **Current SSOT:** `Docs/01-Business-Planning/04-MVP-Scope.md`
> **Archived:** 2026-05-29

# 📋 Booking Feature - Tasks

## Phase 1: Database & Schema
> **الأساس - الجداول والعلاقات والـ Migration**

### Task 1.1: تعديل `schema.prisma`
- [ ] تغيير اسم `SystemConfig` → `System` (الـ model + الـ `@@map`)
- [ ] إضافة `isDoctor Boolean @default(false)` في model `Admin`
- [ ] إضافة `BookingStatus` enum
- [ ] إنشاء model `Booking` مع كل الأعمدة والـ relations والـ indexes
- [ ] إنشاء model `DoctorWorkTime` مع الـ relation مع `Admin`
- [ ] إنشاء model `CloseTime` مع `adminId` nullable
- [ ] إنشاء model `BookingReport` مع الـ relations
- [ ] إضافة الـ relations الجديدة في model `Admin` (`bookingsAsDoctor`, `workTimes`, `closeTimes`, `bookingReports`)
- [ ] إضافة relation `bookings` في model `User`
- [ ] عمل `npx prisma generate` والتأكد إنه مفيش أخطاء
- [ ] عمل `npx prisma migrate dev` لإنشاء الـ migration

### Task 1.2: Seed Data
- [ ] إضافة الـ Permissions الجديدة (16 permission) في الـ seed
- [ ] إضافة الـ System Config keys الجديدة (`allow_booking`, `booking_duration`, `booking_price`, `booking_currency`, `reschedule_allowed_time`, `max_advance_booking_days`)
- [ ] تشغيل الـ seed والتأكد إنه شغال

---

## Phase 2: Permissions & Guards
> **تحديث نظام الصلاحيات وإنشاء الـ Doctor Guard**

### Task 2.1: تحديث CASL Types
- [ ] إضافة الـ Subjects الجديدة في `casl.types.ts` (`DoctorWorkTime`, `CloseTime`, `Booking`, `BookingReport`)

### Task 2.2: إنشاء Doctor Guard
- [ ] إنشاء ملف `src/lib/guards/doctor.guard.ts`
- [ ] تنفيذ الـ Guard بحيث يتأكد إن الـ Admin عنده `isDoctor = true`
- [ ] الـ Guard يتأكد إن الدكتور بيشوف/يعدل بياناته هو بس (own resources)
- [ ] إضافة Decorator `@DoctorOnly()` لتمييز الـ endpoints الخاصة بالدكتور

---

## Phase 3: Doctor Work Time Module
> **إدارة مواعيد عمل الدكاترة**

### Task 3.1: إنشاء الموديول
- [ ] إنشاء `src/modules/doctor-work-time/doctor-work-time.module.ts`
- [ ] إنشاء `src/modules/doctor-work-time/doctor-work-time.service.ts`
- [ ] إنشاء `src/modules/doctor-work-time/doctor-work-time.controller.ts`
- [ ] تسجيل الموديول في `app.module.ts`

### Task 3.2: الـ Service
- [ ] `getAll()` — جلب كل مواعيد العمل
- [ ] `getByAdmin(adminId)` — مواعيد دكتور معين
- [ ] `create(data)` — إضافة موعد عمل مع validation (عدم التعارض في نفس اليوم)
- [ ] `update(id, data)` — تعديل موعد عمل
- [ ] `delete(id)` — حذف موعد عمل

### Task 3.3: الـ Controller (Admin)
- [ ] `GET /admin/doctor-work-time` — كل المواعيد (permission: `read:DoctorWorkTime`)
- [ ] `GET /admin/doctor-work-time/:adminId` — مواعيد دكتور معين
- [ ] `POST /admin/doctor-work-time` — إنشاء (permission: `create:DoctorWorkTime`)
- [ ] `PUT /admin/doctor-work-time/:id` — تعديل (permission: `update:DoctorWorkTime`)
- [ ] `DELETE /admin/doctor-work-time/:id` — حذف (permission: `delete:DoctorWorkTime`)
- [ ] `GET /admin/doctor-work-time/mine` — للدكتور (DoctorGuard, view only)

---

## Phase 4: Close Time Module
> **إدارة مواعيد الإجازات**

### Task 4.1: إنشاء الموديول
- [ ] إنشاء `src/modules/close-time/close-time.module.ts`
- [ ] إنشاء `src/modules/close-time/close-time.service.ts`
- [ ] إنشاء `src/modules/close-time/close-time.controller.ts`
- [ ] تسجيل الموديول في `app.module.ts`

### Task 4.2: الـ Service
- [ ] `getAll()` — كل الإجازات
- [ ] `getByAdmin(adminId)` — إجازات دكتور معين (+ الإجازات العامة null)
- [ ] `create(data)` — إنشاء إجازة (adminId nullable = إجازة عامة)
- [ ] `update(id, data)` — تعديل
- [ ] `delete(id)` — حذف
- [ ] `isTimeBlocked(adminId, date, time)` — helper: هل الوقت ده محجوز كإجازة؟

### Task 4.3: الـ Controller (Admin)
- [ ] `GET /admin/close-time` — كل الإجازات (permission: `read:CloseTime`)
- [ ] `POST /admin/close-time` — إنشاء (permission: `create:CloseTime`)
- [ ] `PUT /admin/close-time/:id` — تعديل (permission: `update:CloseTime`)
- [ ] `DELETE /admin/close-time/:id` — حذف (permission: `delete:CloseTime`)
- [ ] `GET /admin/close-time/mine` — للدكتور (DoctorGuard, view only)

---

## Phase 5: Booking Module (Core)
> **نظام الحجز الرئيسي — أهم جزء**

### Task 5.1: إنشاء الموديول
- [ ] إنشاء `src/modules/booking/booking.module.ts`
- [ ] إنشاء `src/modules/booking/booking.service.ts`
- [ ] إنشاء `src/modules/booking/booking.controller.ts`
- [ ] إنشاء `src/modules/booking/booking.types.ts`
- [ ] تسجيل الموديول في `app.module.ts`

### Task 5.2: Booking Service — Core Logic
- [ ] `getAvailableSlots(adminId, date)` — حساب المواعيد المتاحة:
  - جلب `DoctorWorkTime` لليوم المحدد
  - طرح `CloseTime` (الخاصة بالدكتور + العامة)
  - طرح الحجوزات الموجودة
  - تقسيم حسب `booking_duration`
- [ ] `getAvailableDoctors(date)` — الدكاترة المتاحين في يوم معين
- [ ] `validateBooking(data)` — التحقق من:
  - `allow_booking = true`
  - الموعد مستقبلي
  - الموعد مش أبعد من `max_advance_booking_days`
  - الموعد في وقت عمل الدكتور
  - الموعد مش في إجازة
  - مفيش تعارض مع حجز تاني
  - المدة = `booking_duration`

### Task 5.3: Booking Service — CRUD
- [ ] `create(data)` — إنشاء حجز (بعد الـ validation)
- [ ] `getAll(filters)` — كل الحجوزات مع فلترة
- [ ] `getById(id)` — تفاصيل حجز
- [ ] `update(id, data)` — تعديل حجز
- [ ] `cancel(id, cancelledBy)` — إلغاء حجز
- [ ] `reschedule(id, newStartAt, newEndAt)` — إعادة جدولة مع validation
- [ ] `getMyBookings(adminId)` — حجوزات الدكتور
- [ ] `getUserBookings(userId)` — حجوزات اليوزر
- [ ] `getUserBookingHistory(userId)` — الحجوزات السابقة
- [ ] `createFollowUp(adminId, userId, data)` — موعد متابعة (الدكتور يضيف موعد لعميل سابق)

### Task 5.4: الـ Controller (Admin)
- [ ] `GET /admin/bookings` — كل الحجوزات (permission: `read:Booking`)
- [ ] `GET /admin/bookings/:id` — تفاصيل حجز
- [ ] `POST /admin/bookings` — إنشاء حجز (permission: `create:Booking`)
- [ ] `PUT /admin/bookings/:id` — تعديل كامل (permission: `update:Booking`)
- [ ] `DELETE /admin/bookings/:id` — حذف (soft delete) (permission: `delete:Booking`)

### Task 5.5: الـ Controller (Doctor)
- [ ] `GET /admin/bookings/mine` — حجوزات الدكتور بس (DoctorGuard)
- [ ] `PUT /admin/bookings/:id/status` — تغيير حالة الحجز (DoctorGuard)
- [ ] `PUT /admin/bookings/:id/notes` — تعديل النوت (DoctorGuard)
- [ ] `POST /admin/bookings/follow-up` — إضافة موعد متابعة (DoctorGuard)

### Task 5.6: الـ Controller (User — Mobile)
- [ ] إنشاء `src/modules/booking/user-booking.controller.ts`
- [ ] `GET /bookings/available-doctors` — الدكاترة المتاحين (MobileAuth)
- [ ] `GET /bookings/available-slots/:adminId` — المواعيد المتاحة لدكتور معين
- [ ] `GET /bookings/my` — حجوزاتي الحالية والقادمة
- [ ] `GET /bookings/history` — الحجوزات السابقة
- [ ] `POST /bookings` — عمل حجز جديد
- [ ] `PUT /bookings/:id/reschedule` — إعادة جدولة
- [ ] `PUT /bookings/:id/cancel` — إلغاء حجز

---

## Phase 6: Booking Report Module
> **ريبورتات الدكتور بعد السيشن**

### Task 6.1: إنشاء الموديول
- [ ] إنشاء `src/modules/booking-report/booking-report.module.ts`
- [ ] إنشاء `src/modules/booking-report/booking-report.service.ts`
- [ ] إنشاء `src/modules/booking-report/booking-report.controller.ts`
- [ ] تسجيل الموديول في `app.module.ts`

### Task 6.2: الـ Service
- [ ] `create(data)` — إنشاء ريبورت (يتأكد إن الحجز `completed`)
- [ ] `getAll()` — كل الريبورتات
- [ ] `getById(id)` — تفاصيل ريبورت
- [ ] `getByBooking(bookingId)` — ريبورت حجز معين
- [ ] `getByDoctor(adminId)` — ريبورتات دكتور
- [ ] `update(id, data)` — تعديل
- [ ] `delete(id)` — حذف

### Task 6.3: الـ Controller (Admin)
- [ ] `GET /admin/booking-reports` — كل الريبورتات (permission: `read:BookingReport`)
- [ ] `GET /admin/booking-reports/:id` — تفاصيل ريبورت
- [ ] `POST /admin/booking-reports` — إنشاء (permission: `create:BookingReport`)
- [ ] `PUT /admin/booking-reports/:id` — تعديل (permission: `update:BookingReport`)
- [ ] `DELETE /admin/booking-reports/:id` — حذف (permission: `delete:BookingReport`)

### Task 6.4: الـ Controller (Doctor)
- [ ] `GET /admin/booking-reports/mine` — ريبورتات الدكتور (DoctorGuard)
- [ ] `POST /admin/booking-reports` — إنشاء ريبورت (DoctorGuard)
- [ ] `PUT /admin/booking-reports/:id` — تعديل ريبورت (DoctorGuard, own only)

### Task 6.5: الـ Controller (User — Mobile)
- [ ] `GET /bookings/:id/report` — مشاهدة الريبورت (MobileAuth, own booking only)

---

## Phase 7: تحديث الملفات الموجودة
> **دمج الفيتشر الجديد مع النظام الحالي**

### Task 7.1: تحديث `app.module.ts`
- [ ] import `DoctorWorkTimeModule`
- [ ] import `CloseTimeModule`
- [ ] import `BookingModule`
- [ ] import `BookingReportModule`

### Task 7.2: تحديث `casl.types.ts`
- [ ] إضافة `DoctorWorkTime` | `CloseTime` | `Booking` | `BookingReport` للـ Subject type

### Task 7.3: تحديث Admins
- [ ] تحديث Admin Service لدعم `isDoctor` filter
- [ ] تحديث Admin Controller لو محتاج

### Task 7.4: تحديث `API_ENDPOINTS.md`
- [ ] إضافة كل الـ endpoints الجديدة

---

## Phase 8: Testing & Verification
> **التأكد إن كل حاجة شغالة**

### Task 8.1: اختبار الـ Schema
- [ ] التأكد إن الـ migration اتعملت صح
- [ ] التأكد إن الـ Prisma Studio بيعرض الجداول الجديدة
- [ ] التأكد إن الـ relations شغالة

### Task 8.2: اختبار الـ APIs
- [ ] اختبار CRUD لـ `DoctorWorkTime`
- [ ] اختبار CRUD لـ `CloseTime`
- [ ] اختبار Available Slots calculation
- [ ] اختبار Booking creation مع validation
- [ ] اختبار Booking conflict detection
- [ ] اختبار Reschedule مع الـ time limit
- [ ] اختبار Cancel booking
- [ ] اختبار CRUD لـ `BookingReport`

### Task 8.3: اختبار الصلاحيات
- [ ] Admin يقدر يعمل كل حاجة
- [ ] Doctor يشوف حاجته بس
- [ ] Doctor يعدل الحالة والنوت بس
- [ ] User يشوف حجوزاته بس
- [ ] User يقدر يعيد الجدولة في الوقت المسموح
- [ ] Super Admin عنده access لكل حاجة

---

## ملخص الـ Phases

| Phase | الوصف | عدد التاسكات | الأولوية |
|-------|-------|-------------|---------|
| **1** | Database & Schema | 14 | 🔴 عالية |
| **2** | Permissions & Guards | 5 | 🔴 عالية |
| **3** | Doctor Work Time | 12 | 🟡 متوسطة |
| **4** | Close Time | 11 | 🟡 متوسطة |
| **5** | Booking (Core) | 27 | 🔴 عالية |
| **6** | Booking Report | 14 | 🟡 متوسطة |
| **7** | تحديث ملفات موجودة | 5 | 🟡 متوسطة |
| **8** | Testing | 14 | 🔴 عالية |
| | **المجموع** | **102 task** | |
