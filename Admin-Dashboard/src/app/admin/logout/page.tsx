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
    <div className="flex items-center justify-center min-h-[300px]">
      <div className="text-gray-500">Logging out...</div>
    </div>
  );
}
