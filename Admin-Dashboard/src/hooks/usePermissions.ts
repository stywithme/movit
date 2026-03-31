'use client';

import { useAuthStore } from '@/lib/auth/auth-store';
import { useCallback } from 'react';

export type Action = 'manage' | 'create' | 'read' | 'update' | 'delete' | 'publish' | 'all';
export type Subject = 'all' | 'Admin' | 'Role' | 'User' | 'Exercise' | 'Workout' | 'Program' | 'ProgramMap' | 'ProgramAnalytics' | 'Recipe' | 'MealPlan' | 'TrainingProvider' | 'Muscle' | 'Equipment' | 'Level' | 'LevelAnalytics' | 'AssessmentTemplate' | 'AssessmentAnalytics' | 'Reassessment' | 'ProgressionRule' | 'ExerciseProgressionProfile' | 'Config' | 'Reports' | 'Analytics' | 'Attribute' | 'PosePosition' | 'FeedbackMessage' | 'Upload' | 'DoctorWorkTime' | 'CloseTime' | 'Booking' | 'BookingReport' | 'System' | 'Plan' | 'Subscription';

export function usePermissions() {
    const { user } = useAuthStore();

    const can = useCallback(
        (action: Action | Action[], subject: Subject): boolean => {
            if (!user) return false;
            if (user.isSuperAdmin) return true;

            const actions = Array.isArray(action) ? action : [action];

            return user.permissions.some((p) => {
                // 'manage' on 'all' overrides everything (double check for safety)
                if (p.action === 'manage' && p.subject === 'all') return true;

                // Check for specific action match or 'manage' on the subject
                const actionMatches = actions.includes(p.action as Action) || p.action === 'manage';
                const subjectMatches = p.subject === subject || p.subject === 'all';

                return actionMatches && subjectMatches;
            });
        },
        [user]
    );

    return { can, user, isSuperAdmin: user?.isSuperAdmin || false };
}
