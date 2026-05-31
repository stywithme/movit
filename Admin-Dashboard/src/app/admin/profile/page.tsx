'use client';

import { useEffect, useState } from 'react';
import { Button, Input, Label } from '@/components/ui';
import { FormShell, PageHeader, StatusBadge } from '@/components/common';
import { Card, CardContent } from '@/components/ui/Card';
import { useAuthStore } from '@/lib/auth/auth-store';
import { toast } from 'sonner';

interface AdminProfile {
  id: string;
  name: string;
  email: string;
  role: string;
  isActive: boolean;
  createdAt: string;
}

export default function AdminProfilePage() {
  const { setUser } = useAuthStore();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [profile, setProfile] = useState<AdminProfile | null>(null);
  const [formData, setFormData] = useState({
    name: '',
    email: '',
  });

  useEffect(() => {
    const fetchProfile = async () => {
      try {
        const res = await fetch('/api/admin/auth/profile');
        const data = await res.json();
        if (data.success) {
          setProfile(data.data);
          setFormData({
            name: data.data.name || '',
            email: data.data.email || '',
          });
        }
      } catch (error) {
        console.error('Error fetching profile:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchProfile();
  }, []);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      const res = await fetch('/api/admin/auth/profile', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData),
      });
      const data = await res.json();
      if (data.success) {
        setProfile(data.data);
        setUser(data.data); // Update the global auth store
        toast.success('Profile updated successfully');
      } else {
        toast.error(data.error || 'Error updating profile');
      }
    } catch (error) {
      console.error('Error updating profile:', error);
      toast.error('Error updating profile');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[300px]">
        <div className="text-muted-foreground">Loading...</div>
      </div>
    );
  }

  if (!profile) {
    return (
      <Card>
        <CardContent className="p-6">
          <p className="text-muted-foreground">Profile not found.</p>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Profile" description="Manage your admin profile" />

      <form onSubmit={handleSave}>
        <FormShell
          title="Account Details"
          description="Update the name and email shown across the dashboard."
          footer={
            <Button type="submit" loading={saving}>
              Save Changes
            </Button>
          }
        >
        <div className="space-y-2">
          <Label>Name</Label>
          <Input
            value={formData.name}
            onChange={(e) => setFormData((prev) => ({ ...prev, name: e.target.value }))}
            placeholder="Your name"
            required
          />
        </div>

        <div className="space-y-2">
          <Label>Email</Label>
          <Input
            type="email"
            value={formData.email}
            onChange={(e) => setFormData((prev) => ({ ...prev, email: e.target.value }))}
            placeholder="admin@example.com"
            required
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="rounded-lg border bg-muted/30 p-4">
            <p className="text-xs uppercase text-muted-foreground">Role</p>
            <p className="mt-1 text-sm font-medium">{profile.role}</p>
          </div>
          <div className="rounded-lg border bg-muted/30 p-4">
            <p className="text-xs uppercase text-muted-foreground">Status</p>
            <div className="mt-2">
              <StatusBadge status={profile.isActive ? 'active' : 'inactive'} />
            </div>
          </div>
        </div>
        </FormShell>
      </form>
    </div>
  );
}
