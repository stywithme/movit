'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { toast } from 'sonner';
import { Button, Card, CardContent, CardDescription, CardHeader, CardTitle, Input, Label } from '@/components/ui';

export default function AdminResetPasswordPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [requestMode, setRequestMode] = useState<'request' | 'reset'>('request');
  const [requestData, setRequestData] = useState({ email: '' });
  const [resetData, setResetData] = useState({ token: '', password: '' });

  const handleRequest = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      const res = await fetch('/api/admin/auth/request-reset', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestData),
      });
      const data = await res.json();
      if (data.success) {
        toast.success('If the email exists, a reset link will be sent.');
        setRequestMode('reset');
      } else {
        toast.error(data.error || 'Unable to request reset');
      }
    } catch (error) {
      console.error('Error requesting reset:', error);
      toast.error('Unable to request reset');
    } finally {
      setLoading(false);
    }
  };

  const handleReset = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      const res = await fetch('/api/admin/auth/reset-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(resetData),
      });
      const data = await res.json();
      if (data.success) {
        toast.success('Password reset successfully. You can login now.');
        router.push('/admin/login');
      } else {
        toast.error(data.error || 'Unable to reset password');
      }
    } catch (error) {
      console.error('Error resetting password:', error);
      toast.error('Unable to reset password');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-10">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>Reset Password</CardTitle>
          <CardDescription>Recover your admin account</CardDescription>
        </CardHeader>
        <CardContent>

      {requestMode === 'request' ? (
        <form onSubmit={handleRequest} className="space-y-5">
          <div>
            <Label>
              Email
            </Label>
            <Input
              type="email"
              value={requestData.email}
              onChange={(e) => setRequestData({ email: e.target.value })}
              placeholder="admin@example.com"
              required
            />
          </div>

          <Button
            type="submit"
            loading={loading}
            className="w-full"
          >
            {loading ? 'Sending...' : 'Send Reset Link'}
          </Button>
        </form>
      ) : (
        <form onSubmit={handleReset} className="space-y-5">
          <div>
            <Label>
              Reset Token
            </Label>
            <Input
              value={resetData.token}
              onChange={(e) => setResetData((prev) => ({ ...prev, token: e.target.value }))}
              placeholder="Paste reset token"
              required
            />
          </div>

          <div>
            <Label>
              New Password
            </Label>
            <Input
              type="password"
              value={resetData.password}
              onChange={(e) => setResetData((prev) => ({ ...prev, password: e.target.value }))}
              placeholder="At least 6 characters"
              required
            />
          </div>

          <Button
            type="submit"
            loading={loading}
            className="w-full"
          >
            {loading ? 'Saving...' : 'Reset Password'}
          </Button>
        </form>
      )}
        </CardContent>
      </Card>
    </div>
  );
}
