'use client';

import { useState } from 'react';
import { Input } from '@/components/ui';

export default function AdminResetPasswordPage() {
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
        alert('If the email exists, a reset link will be sent.');
        setRequestMode('reset');
      } else {
        alert('Error: ' + data.error);
      }
    } catch (error) {
      console.error('Error requesting reset:', error);
      alert('Error requesting reset');
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
        alert('Password reset successfully. You can login now.');
        window.location.href = '/admin/login';
      } else {
        alert('Error: ' + data.error);
      }
    } catch (error) {
      console.error('Error resetting password:', error);
      alert('Error resetting password');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-md mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Reset Password</h1>
        <p className="text-gray-600 mt-1">Recover your admin account</p>
      </div>

      {requestMode === 'request' ? (
        <form onSubmit={handleRequest} className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 space-y-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Email
            </label>
            <Input
              type="email"
              value={requestData.email}
              onChange={(e) => setRequestData({ email: e.target.value })}
              placeholder="admin@example.com"
              required
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
          >
            {loading ? 'Sending...' : 'Send Reset Link'}
          </button>
        </form>
      ) : (
        <form onSubmit={handleReset} className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 space-y-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Reset Token
            </label>
            <Input
              value={resetData.token}
              onChange={(e) => setResetData((prev) => ({ ...prev, token: e.target.value }))}
              placeholder="Paste reset token"
              required
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              New Password
            </label>
            <Input
              type="password"
              value={resetData.password}
              onChange={(e) => setResetData((prev) => ({ ...prev, password: e.target.value }))}
              placeholder="At least 6 characters"
              required
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
          >
            {loading ? 'Saving...' : 'Reset Password'}
          </button>
        </form>
      )}
    </div>
  );
}
