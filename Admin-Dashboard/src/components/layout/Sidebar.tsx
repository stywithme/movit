'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { cn } from '@/lib/utils';
import { useAuthStore } from '@/lib/auth/auth-store';
import { usePermissions } from '@/hooks/usePermissions';
import type { Subject } from '@/hooks/usePermissions';
import {
  LayoutDashboard,
  Activity,
  CheckSquare,
  Layers,
  Settings,
  Users,
  Shield,
  Search,
  LogOut,
  LifeBuoy,
  ChevronDown,
  ChevronRight,
  Menu,
  X,
  Dumbbell,
  Tags,
  Repeat,
  MessageSquare,
  CalendarDays,
  BarChart3,
  PieChart,
  Signal,
  FileCheck,
  TrendingUp,
  Map,
  Key,
  Clock,
  CalendarX,
  Tag,
  CreditCard,
} from 'lucide-react';

interface NavItem {
  title: string;
  href: string;
  icon: React.ElementType;
  badge?: number;
  /** CASL subject needed to read. If omitted = always visible */
  requiredSubject?: Subject;
}

interface NavGroup {
  title: string;
  items: NavItem[];
}

export function Sidebar() {
  const pathname = usePathname();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [openGroups, setOpenGroups] = useState<string[]>(['Content', 'Training System', 'Analytics', 'Administration', 'Booking System']);

  const toggleGroup = (title: string) => {
    setOpenGroups((prev) =>
      prev.includes(title) ? prev.filter((t) => t !== title) : [...prev, title]
    );
  };

  const { user } = useAuthStore();
  const { can, isSuperAdmin } = usePermissions();

  const mainNav: NavItem[] = [
    { title: 'Dashboard', href: '/admin', icon: LayoutDashboard },
    { title: 'Activity', href: '/admin/activity', icon: Activity, badge: 10 },
    { title: 'My tasks', href: '/admin/tasks', icon: CheckSquare, badge: 8 },
  ];

  const contentGroup: NavGroup = {
    title: 'Content',
    items: [
      { title: 'Exercises', href: '/admin/exercises', icon: Dumbbell, requiredSubject: 'Exercise' },
      { title: 'Programs', href: '/admin/programs', icon: CalendarDays, requiredSubject: 'Program' },
      { title: 'Programs Map', href: '/admin/programs/map', icon: Map, requiredSubject: 'ProgramMap' },
      { title: 'Workouts', href: '/admin/workouts', icon: Repeat, requiredSubject: 'Workout' },
      { title: 'Attributes', href: '/admin/attributes', icon: Tags, requiredSubject: 'Attribute' },
      { title: 'Messages', href: '/admin/messages', icon: MessageSquare, requiredSubject: 'FeedbackMessage' },
      { title: 'Camera Positions', href: '/admin/camera-positions', icon: Layers, requiredSubject: 'PosePosition' },
    ],
  };

  const trainingSystemGroup: NavGroup = {
    title: 'Training System',
    items: [
      { title: 'Levels', href: '/admin/levels', icon: Signal, requiredSubject: 'Level' },
      { title: 'Assessment Templates', href: '/admin/assessment-templates', icon: FileCheck, requiredSubject: 'AssessmentTemplate' },
      { title: 'Progression Rules', href: '/admin/progression-rules', icon: TrendingUp, requiredSubject: 'ProgressionRule' },
    ],
  };

  const analyticsGroup: NavGroup = {
    title: 'Analytics',
    items: [
      { title: 'Overview', href: '/admin/analytics', icon: BarChart3, requiredSubject: 'Analytics' },
      { title: 'Programs', href: '/admin/analytics/programs', icon: PieChart, requiredSubject: 'ProgramAnalytics' },
      { title: 'Levels', href: '/admin/analytics/levels', icon: Layers, requiredSubject: 'LevelAnalytics' },
      { title: 'Assessments', href: '/admin/analytics/assessments', icon: Activity, requiredSubject: 'AssessmentAnalytics' },
    ],
  };

  const adminGroup: NavGroup = {
    title: 'Administration',
    items: [
      { title: 'Users', href: '/admin/users', icon: Users, requiredSubject: 'User' },
      { title: 'Admins', href: '/admin/admins', icon: Shield, requiredSubject: 'Admin' },
      { title: 'Roles', href: '/admin/roles', icon: Key, requiredSubject: 'Role' },
      { title: 'Plans', href: '/admin/plans', icon: Tag, requiredSubject: 'Plan' },
      { title: 'Subscriptions', href: '/admin/subscriptions', icon: CreditCard, requiredSubject: 'Subscription' },
      { title: 'Settings', href: '/admin/settings', icon: Settings, requiredSubject: 'System' },
    ],
  };

  const bookingGroup: NavGroup = {
    title: 'Booking System',
    items: [
      { title: 'Bookings', href: '/admin/bookings', icon: CalendarDays, requiredSubject: 'Booking' },
      { title: 'Work Times', href: '/admin/doctor-work-time', icon: Clock, requiredSubject: 'DoctorWorkTime' },
      { title: 'Close Times', href: '/admin/close-time', icon: CalendarX, requiredSubject: 'CloseTime' },
      { title: 'Medical Reports', href: '/admin/booking-reports', icon: BarChart3, requiredSubject: 'BookingReport' },
    ],
  };

  /** Filter a single nav item — visible if no requiredSubject OR user can read it OR if it's booking related and user is a Doctor */
  const canSeeItem = (item: NavItem): boolean => {
    // If no permission required, anyone can see it
    if (!item.requiredSubject) return true;

    // If it's a doctor and it's a booking-related menu item, show it
    const bookingSubjects = ['Booking', 'DoctorWorkTime', 'CloseTime', 'BookingReport'];
    if (user?.isDoctor && bookingSubjects.includes(item.requiredSubject as string)) {
      return true;
    }

    // Otherwise, rely on CASL permissions
    return can('read', item.requiredSubject);
  };

  const NavItemComponent = ({ item }: { item: NavItem }) => {
    const isActive = pathname === item.href;
    return (
      <Link
        href={item.href}
        className={cn(
          'flex items-center justify-between px-3 py-2 rounded-md text-sm font-medium transition-colors',
          isActive
            ? 'bg-gray-100 text-gray-900'
            : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
        )}
      >
        <div className="flex items-center gap-3">
          <item.icon className={cn('h-5 w-5', isActive ? 'text-gray-900' : 'text-gray-500')} />
          <span>{item.title}</span>
        </div>
        {item.badge && (
          <span className="bg-gray-100 text-gray-900 py-0.5 px-2 rounded-full text-xs font-medium">
            {item.badge}
          </span>
        )}
      </Link>
    );
  };

  return (
    <>
      {/* Mobile Menu Button */}
      <button
        onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
        className="lg:hidden fixed top-4 left-4 z-50 p-2 bg-white rounded-md shadow-md border border-gray-200"
      >
        {isMobileMenuOpen ? <X className="h-6 w-6" /> : <Menu className="h-6 w-6" />}
      </button>

      {/* Sidebar Container */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 w-72 bg-white border-r border-gray-200 transform transition-transform duration-200 ease-in-out lg:translate-x-0 lg:static flex flex-col',
          isMobileMenuOpen ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        {/* Header */}
        <div className="p-6">
          <div className="flex items-center gap-3 mb-6">
            <div className="h-8 w-8 bg-blue-600 rounded-lg flex items-center justify-center">
              <span className="text-white font-bold text-lg">V</span>
            </div>
            <span className="text-xl font-bold text-gray-900">Validator</span>
          </div>

          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
            <input
              type="text"
              placeholder="Search"
              className="w-full pl-9 pr-4 py-2 bg-gray-50 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all"
            />
          </div>
        </div>

        {/* Scrollable Navigation */}
        <div className="flex-1 overflow-y-auto px-4 pb-4 space-y-6">
          {/* Main Nav */}
          <div className="space-y-1">
            {mainNav.map((item) => (
              <NavItemComponent key={item.href} item={item} />
            ))}
          </div>

          {/* Groups */}
          {[contentGroup, trainingSystemGroup, bookingGroup, analyticsGroup, adminGroup].map((group) => {
            const visibleItems = group.items.filter(canSeeItem);
            if (visibleItems.length === 0) return null;
            return (
              <div key={group.title} className="space-y-1">
                <button
                  onClick={() => toggleGroup(group.title)}
                  className="w-full flex items-center justify-between px-3 py-2 text-sm font-semibold text-gray-500 hover:text-gray-900 uppercase tracking-wider"
                >
                  <span>{group.title}</span>
                  {openGroups.includes(group.title) ? (
                    <ChevronDown className="h-4 w-4" />
                  ) : (
                    <ChevronRight className="h-4 w-4" />
                  )}
                </button>

                {openGroups.includes(group.title) && (
                  <div className="space-y-1 mt-1">
                    {visibleItems.map((item) => (
                      <NavItemComponent key={item.href} item={item} />
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-gray-200 space-y-4">
          <div className="space-y-1">
            <NavItemComponent
              item={{ title: 'Documentation', href: '/admin/docs', icon: LifeBuoy }}
            />
          </div>

          <div className="flex items-center gap-3 px-3 py-2 rounded-lg hover:bg-gray-50 transition-colors cursor-pointer">
            <div className="h-10 w-10 rounded-full bg-blue-100 flex items-center justify-center overflow-hidden">
              <span className="text-blue-600 font-medium">
                {user?.name?.slice(0, 2).toUpperCase() || 'AD'}
              </span>
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-gray-900 truncate">{user?.name || 'Admin User'}</p>
              <p className="text-xs text-gray-500 truncate">{user?.email || 'admin@validator.com'}</p>
            </div>
            <Link href="/admin/logout" title="Logout">
              <LogOut className="h-4 w-4 text-gray-400 hover:text-red-500" />
            </Link>
          </div>
        </div>
      </aside>

      {/* Overlay for mobile */}
      {isMobileMenuOpen && (
        <div
          className="fixed inset-0 bg-black/50 z-30 lg:hidden"
          onClick={() => setIsMobileMenuOpen(false)}
        />
      )}
    </>
  );
}
