'use client';

import { useEffect } from 'react';
import { useAuthStore } from '@/lib/auth/auth-store';
import { useRouter, usePathname } from 'next/navigation';
import { usePermissions } from '@/hooks/usePermissions';
import { isPublicAdminPage, matchProtectedAdminRoute } from '@/lib/admin-routes';

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
        if (!initialized || loading || !user || isPublicAdminPage(pathname) || pathname === '/admin/unauthorized' || pathname === '/admin') return;

        // Skip check if super admin (they have access to everything)
        if (user.isSuperAdmin) return;

        const matchedRoute = matchProtectedAdminRoute(pathname);

        if (matchedRoute) {
            if (!can('read', matchedRoute.subject)) {
                router.replace('/admin/unauthorized');
            }
        }
    }, [pathname, initialized, loading, user, can, router]);

    // Don't render content until auth is checked to avoid flashes of unauthorized Content
    if (!initialized || loading) {
        return (
            <div className="flex h-screen items-center justify-center bg-background">
                <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent"></div>
            </div>
        );
    }

    return <>{children}</>;
}
