'use client';

import { usePermissions, type Action, type Subject } from '@/hooks/usePermissions';

interface CanProps {
    action: Action | Action[];
    subject: Subject;
    children: React.ReactNode;
    fallback?: React.ReactNode;
}

export function Can({ action, subject, children, fallback = null }: CanProps) {
    const { can } = usePermissions();

    if (!can(action, subject)) {
        return <>{fallback}</>;
    }

    return <>{children}</>;
}
