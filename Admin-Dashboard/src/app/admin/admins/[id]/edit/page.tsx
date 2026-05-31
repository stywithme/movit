'use client';

import { useEffect, useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { toast } from 'sonner';
import { Button, Checkbox, Input, Label, Select } from '@/components/ui';
import { FormShell, PageHeader } from '@/components/common';

interface Admin {
  id: string;
  name: string;
  email: string;
  roleId: string | null;
  role?: { id: string; name: string } | null;
  isActive: boolean;
  isDoctor: boolean;
}

interface RoleOption {
  id: string;
  name: string;
}

export default function EditAdminPage() {
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const adminId = params.id;
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [roles, setRoles] = useState<RoleOption[]>([]);
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    roleId: '',
    password: '',
    isDoctor: false,
  });

  // Fetch roles
  useEffect(() => {
    const fetchRoles = async () => {
      try {
        const res = await fetch('/api/admin/permissions/roles');
        const data = await res.json();
        if (data.success) setRoles(data.data);
      } catch (err) {
        console.error('Error fetching roles:', err);
      }
    };
    fetchRoles();
  }, []);

  // Fetch admin
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
            roleId: admin.roleId || '',
            password: '',
            isDoctor: admin.isDoctor || false,
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

    if (adminId) fetchAdmin();
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
          roleId: formData.roleId || null,
          isDoctor: formData.isDoctor,
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
            toast.error(passwordData.error || 'Error updating password');
            return;
          }
        }
        toast.success('Admin updated');
        router.push('/admin/admins');
      } else {
        toast.error(data.error || 'Error updating admin');
      }
    } catch (error) {
      console.error('Error updating admin:', error);
      toast.error('Error updating admin');
    } finally {
      setSaving(false);
    }
  };

  const canSubmit = formData.name && formData.email;

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[300px]">
        <div className="text-muted-foreground">Loading...</div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Edit Admin"
        description="Update admin details"
        breadcrumbs={[
          { label: 'Admins', href: '/admin/admins' },
          { label: 'Edit Admin' },
        ]}
        actions={
          <Button type="button" variant="outline" onClick={() => router.push('/admin/admins')}>
            Cancel
          </Button>
        }
      />

      <form onSubmit={handleSubmit}>
        <FormShell
          title="Admin Details"
          description="Manage profile, role assignment, and doctor access."
          footer={
            <Button type="submit" loading={saving} disabled={!canSubmit}>
              Save Changes
            </Button>
          }
        >
        <div className="space-y-2">
          <Label>
            Name <span className="text-destructive">*</span>
          </Label>
          <Input
            value={formData.name}
            onChange={(e) => setFormData((prev) => ({ ...prev, name: e.target.value }))}
            placeholder="Full name"
            required
          />
        </div>

        <div className="space-y-2">
          <Label>
            Email <span className="text-destructive">*</span>
          </Label>
          <Input
            type="email"
            value={formData.email}
            onChange={(e) => setFormData((prev) => ({ ...prev, email: e.target.value }))}
            placeholder="admin@example.com"
            required
          />
        </div>

        <div className="space-y-2">
          <Label>Role</Label>
          <Select
            value={formData.roleId}
            onChange={(e) => setFormData((prev) => ({ ...prev, roleId: e.target.value }))}
            options={[
              { value: '', label: '- No Role -' },
              ...roles.map((role) => ({ value: role.id, label: role.name })),
            ]}
          />
          {formData.roleId && (
            <button
              type="button"
              onClick={() => setFormData((prev) => ({ ...prev, roleId: '' }))}
              className="text-xs text-destructive underline-offset-4 hover:underline"
            >
              Remove role
            </button>
          )}
        </div>

        <div className="flex items-center gap-3">
          <Checkbox
            checked={formData.isDoctor}
            onCheckedChange={(checked) => setFormData((prev) => ({ ...prev, isDoctor: Boolean(checked) }))}
          />
          <Label>Is Doctor</Label>
        </div>

        <div className="space-y-2">
          <Label>New Password</Label>
          <Input
            type="password"
            value={formData.password}
            onChange={(e) => setFormData((prev) => ({ ...prev, password: e.target.value }))}
            placeholder="Leave empty to keep current password"
            helperText="Minimum 6 characters"
          />
        </div>
        </FormShell>
      </form>
    </div>
  );
}
