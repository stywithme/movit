'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/lib/auth/auth-store';

export default function AdminLogoutPage() {
  const router = useRouter();
  const { logout } = useAuthStore();

  useEffect(() => {
    const doLogout = async () => {
      try {
        await fetch('/api/admin/auth/logout', { method: 'POST' });
      } catch (error) {
        console.error('Error logging out:', error);
      } finally {
        logout();
        router.replace('/admin/login');
      }
    };

    doLogout();
  }, [router, logout]);

  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="text-muted-foreground">Logging out...</div>
    </div>
  );
}
