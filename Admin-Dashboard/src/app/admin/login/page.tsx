'use client';

import { Suspense, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { toast } from 'sonner';
import { Button, Card, CardContent, CardDescription, CardHeader, CardTitle, Input, Label } from '@/components/ui';
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
        toast.error(data.error || 'Unable to sign in');
      }
    } catch (error) {
      console.error('Error logging in:', error);
      toast.error('Unable to sign in');
    } finally {
      setLoading(false);
    }
  };

  if (checking) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <div className="text-muted-foreground">Loading...</div>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-10">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <div className="mx-auto mb-2 flex size-10 items-center justify-center rounded-xl bg-primary text-sm font-semibold text-primary-foreground">
            FF
          </div>
          <CardTitle>Admin Login</CardTitle>
          <CardDescription>Access the Fix Fit dashboard</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
          <Label>
            Email
          </Label>
          <Input
            type="email"
            value={formData.email}
            onChange={(e) => setFormData((prev) => ({ ...prev, email: e.target.value }))}
            placeholder="admin@example.com"
            required
          />
        </div>

        <div>
          <Label>
            Password
          </Label>
          <Input
            type="password"
            value={formData.password}
            onChange={(e) => setFormData((prev) => ({ ...prev, password: e.target.value }))}
            placeholder="Your password"
            required
          />
        </div>

        <Button
          type="submit"
          loading={loading}
          className="w-full"
        >
          {loading ? 'Signing in...' : 'Login'}
        </Button>

        <Button
          type="button"
          variant="outline"
          onClick={() => router.push('/admin/reset-password')}
          className="w-full"
        >
          Forgot Password
        </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

export default function AdminLoginPage() {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-screen items-center justify-center">
          <div className="text-muted-foreground">Loading...</div>
        </div>
      }
    >
      <AdminLoginInner />
    </Suspense>
  );
}
