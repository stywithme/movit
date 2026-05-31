'use client';

import { useAuthStore } from '@/lib/auth/auth-store';
import { useCallback } from 'react';
import type { Action, Subject } from '@/lib/types/permissions';

export type { Action, Subject };

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
