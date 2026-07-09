import type { Subject } from '@/lib/types/permissions';

export const publicAdminPages = ['/admin/login', '/admin/reset-password'] as const;

export const protectedAdminRoutes: { prefix: string; subject: Subject }[] = [
  { prefix: '/admin/roles', subject: 'Role' },
  { prefix: '/admin/admins', subject: 'Admin' },
  { prefix: '/admin/users', subject: 'User' },
  { prefix: '/admin/exercises', subject: 'Exercise' },
  { prefix: '/admin/workout-phases', subject: 'WorkoutPhase' },
  { prefix: '/admin/workout-templates', subject: 'WorkoutTemplate' },
  { prefix: '/admin/programs/map', subject: 'ProgramMap' },
  { prefix: '/admin/programs', subject: 'Program' },
  { prefix: '/admin/attributes', subject: 'Attribute' },
  { prefix: '/admin/messages', subject: 'FeedbackMessage' },
  { prefix: '/admin/camera-positions', subject: 'PosePosition' },
  { prefix: '/admin/levels', subject: 'Level' },
  { prefix: '/admin/assessment-templates', subject: 'AssessmentTemplate' },
  { prefix: '/admin/exercise-progression', subject: 'ExerciseProgressionProfile' },
  { prefix: '/admin/progression-rules', subject: 'ProgressionRule' },
  { prefix: '/admin/analytics/users', subject: 'ReportUsers' },
  { prefix: '/admin/analytics/workout-executions', subject: 'ReportTraining' },
  { prefix: '/admin/analytics/activation', subject: 'ReportActivation' },
  { prefix: '/admin/analytics/retention', subject: 'ReportRetention' },
  { prefix: '/admin/analytics/training', subject: 'ReportTraining' },
  { prefix: '/admin/analytics/programs', subject: 'ReportProgram' },
  { prefix: '/admin/analytics/levels', subject: 'ReportLevel' },
  { prefix: '/admin/analytics/assessments', subject: 'ReportAssessment' },
  { prefix: '/admin/analytics/progression', subject: 'ReportProgression' },
  { prefix: '/admin/analytics/revenue', subject: 'ReportRevenue' },
  { prefix: '/admin/analytics/safety', subject: 'ReportSafety' },
  { prefix: '/admin/analytics/content', subject: 'ReportContent' },
  { prefix: '/admin/analytics', subject: 'ReportOverview' },
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
