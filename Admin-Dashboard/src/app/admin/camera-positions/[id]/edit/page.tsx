'use client';

import { useEffect, useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { LocalizedText } from '@/lib/types/localized';
import LocalizedInput from '@/components/forms/LocalizedInput';
import { FileUpload } from '@/components/forms';
import { Input } from '@/components/ui';

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
          alert('Camera position not found');
          router.push('/admin/camera-positions');
        }
      } catch (error) {
        console.error('Error loading data:', error);
        alert('Error loading camera position');
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
        alert('Camera position updated successfully!');
        router.push('/admin/camera-positions');
      } else {
        alert('Error: ' + data.error);
      }
    } catch (error) {
      console.error('Error saving:', error);
      alert('Error saving camera position');
    } finally {
      setSaving(false);
    }
  };

  const canSubmit = formData.name.en.trim().length > 0;

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-gray-500">Loading...</div>
      </div>
    );
  }

  if (!cameraPosition) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-red-500">Camera position not found</div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Edit Camera Position</h1>
          <p className="text-gray-600 mt-1">Code: {cameraPosition.code}</p>
        </div>
        <button
          type="button"
          onClick={() => router.push('/admin/camera-positions')}
          className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200"
        >
          Cancel
        </button>
      </div>

      <form onSubmit={handleSubmit} className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 space-y-6">
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
            <label className="block text-sm font-medium text-gray-700 mb-1">Reference Image URL</label>
            <Input
              value={formData.imageUrl}
              onChange={(e) => setFormData({ ...formData, imageUrl: e.target.value })}
              placeholder="https://example.com/image.jpg"
            />
          </div>
        </div>

        <div>
          <label className="flex items-center gap-3 cursor-pointer">
            <input
              type="checkbox"
              checked={formData.isActive}
              onChange={(e) => setFormData({ ...formData, isActive: e.target.checked })}
              className="h-4 w-4 text-blue-600 rounded border-gray-300 focus:ring-blue-500"
            />
            <span className="font-medium text-gray-900">Active</span>
          </label>
          <p className="text-sm text-gray-500 mt-1 ml-7">
            Inactive positions will not appear when creating exercises
          </p>
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
