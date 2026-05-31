'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { toast } from 'sonner';
import { Button, Checkbox, Input, Label } from '@/components/ui';
import { FormShell, PageHeader } from '@/components/common';

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
        toast.success('User created');
        router.push('/admin/users');
      } else {
        toast.error(data.error || 'Error creating user');
      }
    } catch (error) {
      console.error('Error creating user:', error);
      toast.error('Error creating user');
    } finally {
      setSaving(false);
    }
  };

  const canSubmit = formData.name && formData.email && formData.password;

  return (
    <div className="space-y-6">
      <PageHeader
        title="New User"
        description="Create a new mobile app user"
        breadcrumbs={[
          { label: 'Users', href: '/admin/users' },
          { label: 'New User' },
        ]}
        actions={
          <Button type="button" variant="outline" onClick={() => router.push('/admin/users')}>
            Cancel
          </Button>
        }
      />

      <form onSubmit={handleSubmit}>
        <FormShell
          title="User Details"
          description="Basic account information and optional subscription metadata."
          footer={
            <Button type="submit" loading={saving} disabled={!canSubmit}>
              Create User
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
            placeholder="user@example.com"
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
          <Label>Avatar URL</Label>
          <Input
            value={formData.avatarUrl}
            onChange={(e) => setFormData((prev) => ({ ...prev, avatarUrl: e.target.value }))}
            placeholder="https://example.com/avatar.jpg"
          />
        </div>

        <div className="space-y-2">
          <Label>Subscription Expiry</Label>
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
          <Label>Pro Subscription</Label>
        </div>
        </FormShell>
      </form>
    </div>
  );
}
