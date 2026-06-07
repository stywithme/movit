import type { LucideIcon } from 'lucide-react';
import {
  BarChart3,
  Activity,
  CalendarDays,
  CalendarX,
  DollarSign,
  CreditCard,
  Dumbbell,
  FileCheck,
  Key,
  Layers,
  LayoutDashboard,
  Map,
  MessageSquare,
  PieChart,
  ShieldAlert,
  Repeat,
  Settings,
  Shield,
  Signal,
  Tag,
  Tags,
  TrendingUp,
  UserCircle,
  Users,
  Clock,
} from 'lucide-react';
import type { Subject } from '@/lib/types/permissions';

export interface NavItem {
  title: string;
  href: string;
  icon: LucideIcon;
  requiredSubject?: Subject;
  exact?: boolean;
}

export interface NavGroup {
  title: string;
  items: NavItem[];
}

export const mainNav: NavItem[] = [
  { title: 'Dashboard', href: '/admin', icon: LayoutDashboard, exact: true },
  { title: 'Profile', href: '/admin/profile', icon: UserCircle },
];

export const navGroups: NavGroup[] = [
  {
    title: 'Content',
    items: [
      { title: 'Exercises', href: '/admin/exercises', icon: Dumbbell, requiredSubject: 'Exercise' },
      { title: 'Programs', href: '/admin/programs', icon: CalendarDays, requiredSubject: 'Program' },
      { title: 'Programs Map', href: '/admin/programs/map', icon: Map, requiredSubject: 'ProgramMap' },
      { title: 'Workout Templates', href: '/admin/workout-templates', icon: Repeat, requiredSubject: 'WorkoutTemplate' },
      { title: 'Workout Phases', href: '/admin/workout-phases', icon: Layers, requiredSubject: 'WorkoutPhase' },
      { title: 'Attributes', href: '/admin/attributes', icon: Tags, requiredSubject: 'Attribute' },
      { title: 'Messages', href: '/admin/messages', icon: MessageSquare, requiredSubject: 'FeedbackMessage' },
      { title: 'Camera Positions', href: '/admin/camera-positions', icon: Layers, requiredSubject: 'PosePosition' },
    ],
  },
  {
    title: 'Training System',
    items: [
      { title: 'Levels', href: '/admin/levels', icon: Signal, requiredSubject: 'Level' },
      { title: 'Assessment Templates', href: '/admin/assessment-templates', icon: FileCheck, requiredSubject: 'AssessmentTemplate' },
      { title: 'Exercise Progression', href: '/admin/exercise-progression', icon: TrendingUp, requiredSubject: 'ProgressionRule' },
      { title: 'Families', href: '/admin/exercise-progression/families', icon: Layers, requiredSubject: 'ProgressionRule' },
      { title: 'Progression Rules', href: '/admin/progression-rules', icon: TrendingUp, requiredSubject: 'ProgressionRule' },
    ],
  },
  {
    title: 'Booking System',
    items: [
      { title: 'Bookings', href: '/admin/bookings', icon: CalendarDays, requiredSubject: 'Booking' },
      { title: 'Work Times', href: '/admin/doctor-work-time', icon: Clock, requiredSubject: 'DoctorWorkTime' },
      { title: 'Close Times', href: '/admin/close-time', icon: CalendarX, requiredSubject: 'CloseTime' },
      { title: 'Medical Reports', href: '/admin/booking-reports', icon: BarChart3, requiredSubject: 'BookingReport' },
    ],
  },
  {
    title: 'Reports',
    items: [
      { title: 'Overview', href: '/admin/analytics', icon: BarChart3, requiredSubject: 'ReportOverview', exact: true },
      { title: 'Users & Growth', href: '/admin/analytics/users', icon: Users, requiredSubject: 'ReportUsers' },
      { title: 'Activation', href: '/admin/analytics/activation', icon: TrendingUp, requiredSubject: 'ReportActivation' },
      { title: 'Retention', href: '/admin/analytics/retention', icon: Activity, requiredSubject: 'ReportRetention' },
      { title: 'Training', href: '/admin/analytics/training', icon: Dumbbell, requiredSubject: 'ReportTraining' },
      { title: 'Programs', href: '/admin/analytics/programs', icon: PieChart, requiredSubject: 'ReportProgram' },
      { title: 'Levels', href: '/admin/analytics/levels', icon: Layers, requiredSubject: 'ReportLevel' },
      { title: 'Assessments', href: '/admin/analytics/assessments', icon: BarChart3, requiredSubject: 'ReportAssessment' },
      { title: 'Progression', href: '/admin/analytics/progression', icon: TrendingUp, requiredSubject: 'ReportProgression' },
      { title: 'Revenue', href: '/admin/analytics/revenue', icon: DollarSign, requiredSubject: 'ReportRevenue' },
      { title: 'Bookings', href: '/admin/analytics/bookings', icon: CalendarDays, requiredSubject: 'ReportBooking' },
      { title: 'Safety', href: '/admin/analytics/safety', icon: ShieldAlert, requiredSubject: 'ReportSafety' },
      { title: 'Content', href: '/admin/analytics/content', icon: Tags, requiredSubject: 'ReportContent' },
    ],
  },
  {
    title: 'Administration',
    items: [
      { title: 'Users', href: '/admin/users', icon: Users, requiredSubject: 'User' },
      { title: 'Admins', href: '/admin/admins', icon: Shield, requiredSubject: 'Admin' },
      { title: 'Roles', href: '/admin/roles', icon: Key, requiredSubject: 'Role' },
      { title: 'Plans', href: '/admin/plans', icon: Tag, requiredSubject: 'Plan' },
      { title: 'Subscriptions', href: '/admin/subscriptions', icon: CreditCard, requiredSubject: 'Subscription' },
      { title: 'Settings', href: '/admin/settings', icon: Settings, requiredSubject: 'System' },
    ],
  },
];

export function isNavItemActive(pathname: string, item: NavItem) {
  if (item.exact) return pathname === item.href;
  return pathname === item.href || pathname.startsWith(`${item.href}/`);
}
