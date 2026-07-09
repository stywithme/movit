-- Drop booking / doctor / Google Meet domain tables
DROP TABLE IF EXISTS "booking_payment_events";
DROP TABLE IF EXISTS "booking_payment_items";
DROP TABLE IF EXISTS "booking_payments";
DROP TABLE IF EXISTS "booking_reports";
DROP TABLE IF EXISTS "bookings";
DROP TABLE IF EXISTS "admin_google_meet_connections";
DROP TABLE IF EXISTS "doctor_work_times";
DROP TABLE IF EXISTS "close_times";

-- Drop admin doctor flag
ALTER TABLE "admins" DROP COLUMN IF EXISTS "isDoctor";

-- Drop plan doctor-session limit
ALTER TABLE "plans" DROP COLUMN IF EXISTS "freeDoctorSessionsLimit";

-- Remove booking system settings
DELETE FROM "system"
WHERE "key" IN (
  'allow_booking',
  'booking_duration',
  'booking_price',
  'booking_currency',
  'reschedule_allowed_time',
  'max_advance_booking_days',
  'min_booking_hours'
);

-- Remove orphaned permissions
DELETE FROM "role_permissions"
WHERE "permissionId" IN (
  SELECT "id" FROM "permissions"
  WHERE "subject" IN (
    'Booking',
    'BookingReport',
    'DoctorWorkTime',
    'CloseTime',
    'ReportBooking',
    'BookingAnalytics'
  )
);

DELETE FROM "permissions"
WHERE "subject" IN (
  'Booking',
  'BookingReport',
  'DoctorWorkTime',
  'CloseTime',
  'ReportBooking',
  'BookingAnalytics'
);
