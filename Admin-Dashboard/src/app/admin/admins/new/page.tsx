'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { toast } from 'sonner';
import { Button, Input, Label, Select } from '@/components/ui';
import { FormShell, PageHeader } from '@/components/common';

interface RoleOption {
  id: string;
  name: string;
}

export default function NewAdminPage() {
  const router = useRouter();
  const [saving, setSaving] = useState(false);
  const [roles, setRoles] = useState<RoleOption[]>([]);
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    password: '',
    roleId: '',
  });

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

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);

    try {
      const res = await fetch('/api/admins', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: formData.name,
          email: formData.email,
          password: formData.password,
          roleId: formData.roleId || null,
        }),
      });

      const data = await res.json();
      if (data.success) {
        toast.success('Admin created');
        router.push('/admin/admins');
      } else {
        toast.error(data.error || 'Error creating admin');
      }
    } catch (error) {
      console.error('Error creating admin:', error);
      toast.error('Error creating admin');
    } finally {
      setSaving(false);
    }
  };

  const canSubmit = formData.name && formData.email && formData.password;

  return (
    <div className="space-y-6">
      <PageHeader
        title="New Admin"
        description="Create a new dashboard admin"
        breadcrumbs={[
          { label: 'Admins', href: '/admin/admins' },
          { label: 'New Admin' },
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
          description="Assign dashboard access and role."
          footer={
            <Button type="submit" loading={saving} disabled={!canSubmit}>
              Create Admin
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
          <Label>
            Password <span className="text-destructive">*</span>
          </Label>
          <Input
            type="password"
            value={formData.password}
            onChange={(e) => setFormData((prev) => ({ ...prev, password: e.target.value }))}
            placeholder="At least 6 characters"
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
        </div>
        </FormShell>
      </form>
    </div>
  );
}
