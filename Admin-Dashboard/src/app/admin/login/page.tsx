'use client';

import { Suspense, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Input } from '@/components/ui';
import { useAuthStore } from '@/lib/auth/auth-store';

function AdminLoginInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirectTo = searchParams.get('redirect') || '/admin';
  const [loading, setLoading] = useState(false);
  const [checking, setChecking] = useState(true);
  const [formData, setFormData] = useState({
    email: '',
    password: '',
  });

  useEffect(() => {
    const checkProfile = async () => {
      try {
        const res = await fetch('/api/admin/auth/profile', {
          credentials: 'include',
        });
        if (res.ok) {
          router.replace(redirectTo);
          return;
        }
      } catch (error) {
        console.error('Error checking admin session:', error);
      } finally {
        setChecking(false);
      }
    };

    checkProfile();
  }, [router, redirectTo]);

  const { setUser } = useAuthStore();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);

    try {
      const res = await fetch('/api/admin/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData),
        credentials: 'include',
      });

      const data = await res.json();
      if (data.success) {
        // Fetch profile immediately to update the store
        const profileRes = await fetch('/api/admin/auth/profile', {
          credentials: 'include',
        });
        if (profileRes.ok) {
          const profileData = await profileRes.json();
          if (profileData.success) {
            setUser(profileData.data);
          }
        }
        router.replace(redirectTo);
      } else {
        alert('Error: ' + data.error);
      }
    } catch (error) {
      console.error('Error logging in:', error);
      alert('Error logging in');
    } finally {
      setLoading(false);
    }
  };

  if (checking) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-gray-500">Loading...</div>
      </div>
    );
  }

  return (
    <div className="max-w-md mx-auto space-y-6">
      <div className="text-center">
        <h1 className="text-2xl font-bold text-gray-900">Admin Login</h1>
        <p className="text-gray-600 mt-1">Access the dashboard</p>
      </div>

      <form onSubmit={handleSubmit} className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 space-y-6">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Email
          </label>
          <Input
            type="email"
            value={formData.email}
            onChange={(e) => setFormData((prev) => ({ ...prev, email: e.target.value }))}
            placeholder="admin@example.com"
            required
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Password
          </label>
          <Input
            type="password"
            value={formData.password}
            onChange={(e) => setFormData((prev) => ({ ...prev, password: e.target.value }))}
            placeholder="Your password"
            required
          />
        </div>

        <button
          type="submit"
          disabled={loading}
          className="w-full px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
        >
          {loading ? 'Signing in...' : 'Login'}
        </button>

        <button
          type="button"
          onClick={() => router.push('/admin/reset-password')}
          className="w-full px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200"
        >
          Forgot Password
        </button>
      </form>
    </div>
  );
}

export default function AdminLoginPage() {
  return (
    <Suspense
      fallback={
        <div className="flex items-center justify-center min-h-[400px]">
          <div className="text-gray-500">Loading...</div>
        </div>
      }
    >
      <AdminLoginInner />
    </Suspense>
  );
}
