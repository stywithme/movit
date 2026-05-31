'use client';

import { useEffect, useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { toast } from 'sonner';
import { LocalizedText } from '@/lib/types/localized';
import LocalizedInput from '@/components/forms/LocalizedInput';
import { FileUpload } from '@/components/forms';
import { Button, Card, CardContent, Input, Switch } from '@/components/ui';
import { PageHeader } from '@/components/common';

interface CameraPosition {
  id: string;
  code: string;
  name: LocalizedText;
  imageUrl: string | null;
  isActive: boolean;
}

export default function EditCameraPositionPage() {
  const router = useRouter();
  const params = useParams();
  const cameraPositionId = params.id as string;

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [cameraPosition, setCameraPosition] = useState<CameraPosition | null>(null);

  const [formData, setFormData] = useState({
    name: { ar: '', en: '' },
    imageUrl: '',
    isActive: true,
  });

  useEffect(() => {
    const loadData = async () => {
      try {
        const cpRes = await fetch(`/api/camera-positions/${cameraPositionId}`);
        const cpData = await cpRes.json();
        if (cpData.success) {
          const cp = cpData.data;
          setCameraPosition(cp);
          setFormData({
            name: cp.name || { ar: '', en: '' },
            imageUrl: cp.imageUrl || '',
            isActive: cp.isActive,
          });
        } else {
          toast.error('Camera position not found');
          router.push('/admin/camera-positions');
        }
      } catch (error) {
        console.error('Error loading data:', error);
        toast.error('Error loading camera position');
        router.push('/admin/camera-positions');
      } finally {
        setLoading(false);
      }
    };

    if (cameraPositionId) {
      loadData();
    }
  }, [cameraPositionId, router]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);

    try {
      const res = await fetch(`/api/camera-positions/${cameraPositionId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData),
      });

      const data = await res.json();
      if (data.success) {
        toast.success('Camera position updated');
        router.push('/admin/camera-positions');
      } else {
        toast.error(data.error || 'Failed to update camera position');
      }
    } catch (error) {
      console.error('Error saving:', error);
      toast.error('Error saving camera position');
    } finally {
      setSaving(false);
    }
  };

  const canSubmit = formData.name.en.trim().length > 0;

  if (loading) {
    return (
      <Card>
        <CardContent className="flex min-h-[320px] items-center justify-center text-sm text-muted-foreground">
          Loading camera position...
        </CardContent>
      </Card>
    );
  }

  if (!cameraPosition) {
    return (
      <Card>
        <CardContent className="flex min-h-[320px] items-center justify-center text-sm text-destructive">
          Camera position not found
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Edit Camera Position"
        description={`Code: ${cameraPosition.code}`}
        breadcrumbs={[
          { label: 'Camera Positions', href: '/admin/camera-positions' },
          { label: cameraPosition.name.en || cameraPosition.code },
        ]}
        actions={
          <Button type="button" variant="outline" onClick={() => router.push('/admin/camera-positions')}>
            Cancel
          </Button>
        }
      />

      <Card>
        <CardContent className="p-6">
          <form onSubmit={handleSubmit} className="space-y-6">
            <LocalizedInput
              label="Name"
              value={formData.name}
              onChange={(name) => setFormData({ ...formData, name })}
              required
            />

            <div className="space-y-3">
              <FileUpload
                label="Reference Image"
                value={formData.imageUrl}
                onChange={(imageUrl) => setFormData({ ...formData, imageUrl })}
                uploadType="camera-position-image"
                accept="image/*"
                helperText="This image is shared across all exercises that use this position"
              />
              <div>
                <label className="mb-1 block text-sm font-medium">Reference Image URL</label>
                <Input
                  value={formData.imageUrl}
                  onChange={(e) => setFormData({ ...formData, imageUrl: e.target.value })}
                  placeholder="https://example.com/image.jpg"
                />
              </div>
            </div>

            <div className="rounded-lg border p-4">
              <label className="flex cursor-pointer items-center justify-between gap-3">
                <span>
                  <span className="font-medium">Active</span>
                  <span className="mt-1 block text-sm text-muted-foreground">
                    Inactive positions will not appear when creating exercises.
                  </span>
                </span>
                <Switch
                  checked={formData.isActive}
                  onCheckedChange={(isActive) => setFormData({ ...formData, isActive })}
                />
              </label>
            </div>

            <div className="flex justify-end border-t pt-4">
              <Button type="submit" disabled={!canSubmit} loading={saving}>
                Save Changes
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
