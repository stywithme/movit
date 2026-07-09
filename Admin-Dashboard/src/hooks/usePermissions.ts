'use client';

import { useAuthStore } from '@/lib/auth/auth-store';
import { useCallback } from 'react';
import type { Action, Subject } from '@/lib/types/permissions';

export type { Action, Subject };

/** Legacy analytics subjects still stored on some roles — map to Report* subjects */
const LEGACY_SUBJECTS: Record<string, Subject> = {
  Workout: 'WorkoutTemplate',
};

const LEGACY_REPORT_SUBJECTS: Record<string, Subject> = {
  Analytics: 'ReportOverview',
  OverviewAnalytics: 'ReportOverview',
  UserAnalytics: 'ReportUsers',
  ActivationAnalytics: 'ReportActivation',
  EngagementAnalytics: 'ReportRetention',
  TrainingAnalytics: 'ReportTraining',
  ProgramAnalytics: 'ReportProgram',
  LevelAnalytics: 'ReportLevel',
  AssessmentAnalytics: 'ReportAssessment',
  ProgressionAnalytics: 'ReportProgression',
  RevenueAnalytics: 'ReportRevenue',
  SafetyAnalytics: 'ReportSafety',
  ContentAnalytics: 'ReportContent',
};

function subjectMatches(permissionSubject: string, requested: Subject): boolean {
  if (permissionSubject === requested || permissionSubject === 'all') return true;
  const legacy = LEGACY_SUBJECTS[permissionSubject];
  if (legacy === requested) return true;
  const migrated = LEGACY_REPORT_SUBJECTS[permissionSubject];
  return migrated === requested;
}

function actionMatches(permissionAction: string, requested: Action): boolean {
  if (permissionAction === requested || permissionAction === 'manage') return true;
  // Legacy: publish/duplicate permissions grant edit (update)
  if (requested === 'update' && (permissionAction === 'publish' || permissionAction === 'duplicate')) {
    return true;
  }
  return false;
}

export function usePermissions() {
  const { user } = useAuthStore();

  const can = useCallback(
    (action: Action | Action[], subject: Subject): boolean => {
      if (!user) return false;
      if (user.isSuperAdmin) return true;

      const actions = Array.isArray(action) ? action : [action];

      return user.permissions.some((p) => {
        if (p.action === 'manage' && p.subject === 'all') return true;

        const actionOk = actions.some((a) => actionMatches(p.action, a));
        const subjectOk = subjectMatches(p.subject, subject);

        return actionOk && subjectOk;
      });
    },
    [user],
  );

  return { can, user, isSuperAdmin: user?.isSuperAdmin || false };
}
