'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';

export default function AdminLogoutPage() {
  const router = useRouter();

  useEffect(() => {
    const doLogout = async () => {
      try {
        await fetch('/api/admin/auth/logout', { method: 'POST' });
      } catch (error) {
        console.error('Error logging out:', error);
      } finally {
        router.replace('/admin/login');
      }
    };

    doLogout();
  }, [router]);

  return (
    <div className="flex items-center justify-center min-h-[300px]">
      <div className="text-gray-500">Logging out...</div>
    </div>
  );
}
