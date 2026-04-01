'use client';

import { useEffect } from 'react';
import { useAuthStore } from '@/lib/auth/auth-store';
import { useRouter, usePathname } from 'next/navigation';
import { usePermissions, type Subject } from '@/hooks/usePermissions';

const PROTECTED_ROUTES: { pattern: RegExp; subject: Subject }[] = [
    { pattern: /^\/admin\/roles/, subject: 'Role' },
    { pattern: /^\/admin\/admins/, subject: 'Admin' },
    { pattern: /^\/admin\/users/, subject: 'User' },
    { pattern: /^\/admin\/exercises/, subject: 'Exercise' },
    { pattern: /^\/admin\/workouts/, subject: 'Workout' },
    { pattern: /^\/admin\/programs\/map/, subject: 'ProgramMap' },
    { pattern: /^\/admin\/programs/, subject: 'Program' },
    { pattern: /^\/admin\/attributes/, subject: 'Attribute' },
    { pattern: /^\/admin\/messages/, subject: 'FeedbackMessage' },
    { pattern: /^\/admin\/camera-positions/, subject: 'PosePosition' },
    { pattern: /^\/admin\/levels/, subject: 'Level' },
    { pattern: /^\/admin\/assessment-templates/, subject: 'AssessmentTemplate' },
    { pattern: /^\/admin\/exercise-progression/, subject: 'ProgressionRule' },
    { pattern: /^\/admin\/progression-rules/, subject: 'ProgressionRule' },
    { pattern: /^\/admin\/analytics\/programs/, subject: 'ProgramAnalytics' },
    { pattern: /^\/admin\/analytics\/levels/, subject: 'LevelAnalytics' },
    { pattern: /^\/admin\/analytics\/assessments/, subject: 'AssessmentAnalytics' },
    { pattern: /^\/admin\/analytics/, subject: 'Analytics' },
    { pattern: /^\/admin\/uploads/, subject: 'Upload' },
];

export function AuthProvider({ children }: { children: React.ReactNode }) {
    const { user, initialized, loading, setUser, setInitialized, setLoading } = useAuthStore();
    const router = useRouter();
    const pathname = usePathname();
    const { can } = usePermissions();

    useEffect(() => {
        const fetchProfile = async () => {
            try {
                setLoading(true);
                const res = await fetch('/api/admin/auth/profile', {
                    credentials: 'include',
                });

                if (res.ok) {
                    const result = await res.json();
                    if (result.success) {
                        setUser(result.data);
                    } else {
                        setUser(null);
                    }
                } else {
                    setUser(null);
                }
            } catch (error) {
                console.error('Failed to fetch admin profile:', error);
                setUser(null);
            } finally {
                setInitialized(true);
                setLoading(false);
            }
        };

        fetchProfile();
    }, [setUser, setInitialized, setLoading]);

    // Client-side route guard based on detailed permissions
    useEffect(() => {
        if (!initialized || loading || !user || pathname === '/admin/unauthorized' || pathname === '/admin') return;

        // Skip check if super admin (they have access to everything)
        if (user.isSuperAdmin) return;

        const matchedRoute = PROTECTED_ROUTES.find(route => route.pattern.test(pathname));

        if (matchedRoute) {
            // Check if user has 'read' permission for this subject
            if (!can('read', matchedRoute.subject)) {
                router.replace('/admin/unauthorized');
            }
        }
    }, [pathname, initialized, loading, user, can, router]);

    // Don't render content until auth is checked to avoid flashes of unauthorized Content
    if (!initialized || loading) {
        return (
            <div className="flex h-screen items-center justify-center bg-gray-50">
                <div className="animate-spin h-8 w-8 border-4 border-blue-600 border-t-transparent rounded-full"></div>
            </div>
        );
    }

    return <>{children}</>;
}
