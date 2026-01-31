'use client';

import { useEffect, useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { Input, Select } from '@/components/ui';

interface Admin {
  id: string;
  name: string;
  email: string;
  role: string;
  isActive: boolean;
}

export default function EditAdminPage() {
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const adminId = params.id;
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    role: 'admin',
    password: '',
  });

  useEffect(() => {
    const fetchAdmin = async () => {
      try {
        const res = await fetch(`/api/admins/${adminId}`);
        const data = await res.json();
        if (data.success) {
          const admin: Admin = data.data;
          setFormData({
            name: admin.name || '',
            email: admin.email || '',
            role: admin.role || 'admin',
            password: '',
          });
        } else {
          router.push('/admin/admins');
        }
      } catch (error) {
        console.error('Error fetching admin:', error);
        router.push('/admin/admins');
      } finally {
        setLoading(false);
      }
    };

    if (adminId) {
      fetchAdmin();
    }
  }, [adminId, router]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);

    try {
      const res = await fetch(`/api/admins/${adminId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: formData.name,
          email: formData.email,
          role: formData.role,
        }),
      });

      const data = await res.json();
      if (data.success) {
        if (formData.password) {
          const passwordRes = await fetch(`/api/admins/${adminId}/password`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ password: formData.password }),
          });
          const passwordData = await passwordRes.json();
          if (!passwordData.success) {
            alert('Error: ' + passwordData.error);
            return;
          }
        }

        router.push('/admin/admins');
      } else {
        alert('Error: ' + data.error);
      }
    } catch (error) {
      console.error('Error updating admin:', error);
      alert('Error updating admin');
    } finally {
      setSaving(false);
    }
  };

  const canSubmit = formData.name && formData.email;

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[300px]">
        <div className="text-gray-500">Loading...</div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Edit Admin</h1>
          <p className="text-gray-600 mt-1">Update admin details</p>
        </div>
        <button
          type="button"
          onClick={() => router.push('/admin/admins')}
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
            placeholder="admin@example.com"
            required
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Role</label>
          <Select
            value={formData.role}
            onChange={(e) => setFormData((prev) => ({ ...prev, role: e.target.value }))}
            options={[
              { value: 'admin', label: 'Admin' },
              { value: 'super_admin', label: 'Super Admin' },
            ]}
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            New Password
          </label>
          <Input
            type="password"
            value={formData.password}
            onChange={(e) => setFormData((prev) => ({ ...prev, password: e.target.value }))}
            placeholder="Leave empty to keep current password"
            helperText="Minimum 6 characters"
          />
        </div>

        <div className="flex justify-end pt-4 border-t border-gray-200">
          <button
            type="submit"
            disabled={saving || !canSubmit}
            className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
          >
            {saving ? 'Saving...' : 'Save Changes'}
          </button>
        </div>
      </form>
    </div>
  );
}
