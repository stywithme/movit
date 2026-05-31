import type { Subject } from '@/lib/types/permissions';

export const publicAdminPages = ['/admin/login', '/admin/reset-password'] as const;

export const protectedAdminRoutes: { prefix: string; subject: Subject }[] = [
  { prefix: '/admin/roles', subject: 'Role' },
  { prefix: '/admin/admins', subject: 'Admin' },
  { prefix: '/admin/users', subject: 'User' },
  { prefix: '/admin/exercises', subject: 'Exercise' },
  { prefix: '/admin/workouts', subject: 'Workout' },
  { prefix: '/admin/programs/map', subject: 'ProgramMap' },
  { prefix: '/admin/programs', subject: 'Program' },
  { prefix: '/admin/attributes', subject: 'Attribute' },
  { prefix: '/admin/messages', subject: 'FeedbackMessage' },
  { prefix: '/admin/camera-positions', subject: 'PosePosition' },
  { prefix: '/admin/levels', subject: 'Level' },
  { prefix: '/admin/assessment-templates', subject: 'AssessmentTemplate' },
  { prefix: '/admin/exercise-progression', subject: 'ProgressionRule' },
  { prefix: '/admin/progression-rules', subject: 'ProgressionRule' },
  { prefix: '/admin/analytics/programs', subject: 'ProgramAnalytics' },
  { prefix: '/admin/analytics/levels', subject: 'LevelAnalytics' },
  { prefix: '/admin/analytics/assessments', subject: 'AssessmentAnalytics' },
  { prefix: '/admin/analytics', subject: 'Analytics' },
  { prefix: '/admin/bookings', subject: 'Booking' },
  { prefix: '/admin/doctor-work-time', subject: 'DoctorWorkTime' },
  { prefix: '/admin/close-time', subject: 'CloseTime' },
  { prefix: '/admin/booking-reports', subject: 'BookingReport' },
  { prefix: '/admin/plans', subject: 'Plan' },
  { prefix: '/admin/subscriptions', subject: 'Subscription' },
  { prefix: '/admin/settings', subject: 'System' },
  { prefix: '/admin/uploads', subject: 'Upload' },
];

export function matchProtectedAdminRoute(pathname: string) {
  return protectedAdminRoutes.find((route) => pathname.startsWith(route.prefix));
}

export function isPublicAdminPage(pathname: string) {
  return publicAdminPages.some((path) => pathname.startsWith(path));
}
