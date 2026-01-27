'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Checkbox, Input } from '@/components/ui';

export default function NewUserPage() {
  const router = useRouter();
  const [saving, setSaving] = useState(false);
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    password: '',
    avatarUrl: '',
    isPro: false,
    subscriptionExpiry: '',
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);

    try {
      const payload = {
        name: formData.name,
        email: formData.email,
        password: formData.password,
        avatarUrl: formData.avatarUrl || undefined,
        isPro: formData.isPro,
        subscriptionExpiry: formData.subscriptionExpiry || undefined,
      };

      const res = await fetch('/api/users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      const data = await res.json();
      if (data.success) {
        router.push('/admin/users');
      } else {
        alert('Error: ' + data.error);
      }
    } catch (error) {
      console.error('Error creating user:', error);
      alert('Error creating user');
    } finally {
      setSaving(false);
    }
  };

  const canSubmit = formData.name && formData.email && formData.password;

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">New User</h1>
          <p className="text-gray-600 mt-1">Create a new mobile app user</p>
        </div>
        <button
          type="button"
          onClick={() => router.push('/admin/users')}
          className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200"
        >
          Cancel
        </button>
      </div>

      <form onSubmit={handleSubmit} className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 space-y-6">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Name <span className="text-red-500">*</span>
          </label>
          <Input
            value={formData.name}
            onChange={(e) => setFormData((prev) => ({ ...prev, name: e.target.value }))}
            placeholder="Full name"
            required
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Email <span className="text-red-500">*</span>
          </label>
          <Input
            type="email"
            value={formData.email}
            onChange={(e) => setFormData((prev) => ({ ...prev, email: e.target.value }))}
            placeholder="user@example.com"
            required
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Password <span className="text-red-500">*</span>
          </label>
          <Input
            type="password"
            value={formData.password}
            onChange={(e) => setFormData((prev) => ({ ...prev, password: e.target.value }))}
            placeholder="At least 6 characters"
            required
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Avatar URL</label>
          <Input
            value={formData.avatarUrl}
            onChange={(e) => setFormData((prev) => ({ ...prev, avatarUrl: e.target.value }))}
            placeholder="https://example.com/avatar.jpg"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Subscription Expiry</label>
          <Input
            type="date"
            value={formData.subscriptionExpiry}
            onChange={(e) => setFormData((prev) => ({ ...prev, subscriptionExpiry: e.target.value }))}
          />
        </div>

        <div className="flex items-center gap-3">
          <Checkbox
            checked={formData.isPro}
            onCheckedChange={(checked) => setFormData((prev) => ({ ...prev, isPro: Boolean(checked) }))}
          />
          <label className="text-sm text-gray-700">Pro Subscription</label>
        </div>

        <div className="flex justify-end pt-4 border-t border-gray-200">
          <button
            type="submit"
            disabled={saving || !canSubmit}
            className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
          >
            {saving ? 'Saving...' : 'Create User'}
          </button>
        </div>
      </form>
    </div>
  );
}
