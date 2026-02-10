'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { LocalizedText } from '@/lib/types/localized';
import LocalizedInput from '@/components/forms/LocalizedInput';
import { FileUpload } from '@/components/forms';
import { Input } from '@/components/ui';

interface Joint {
  id: string;
  code: string;
  name: LocalizedText;
}

export default function NewCameraPositionPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  // Available joints from API
  const [availableJoints, setAvailableJoints] = useState<Joint[]>([]);

  // Form data
  const [formData, setFormData] = useState({
    code: '',
    name: { ar: '', en: '' },
    description: { ar: '', en: '' },
    imageUrl: '',
    jointIds: [] as string[],
  });

  // Track if user manually edited the code
  const [codeManuallyEdited, setCodeManuallyEdited] = useState(false);

  // Auto-generate code from English name
  useEffect(() => {
    if (formData.name.en && !codeManuallyEdited) {
      const generatedCode = formData.name.en
        .toLowerCase()
        .trim()
        .replace(/[^a-z0-9\s]/g, '') // Remove special characters
        .replace(/\s+/g, '_') // Replace spaces with underscores
        .replace(/_+/g, '_') // Replace multiple underscores with single
        .replace(/^_|_$/g, ''); // Remove leading/trailing underscores
      
      if (generatedCode) {
        setFormData((prev) => ({ ...prev, code: generatedCode }));
      }
    }
  }, [formData.name.en, codeManuallyEdited]);

  // Load joints
  useEffect(() => {
    const loadJoints = async () => {
      try {
        const res = await fetch('/api/attributes/joint/values');
        const data = await res.json();
        console.log('Joints API response:', data); // Debug log
        if (data.success) {
          // Parse JSON fields if needed
          const joints = (data.data.values || []).map((joint: any) => ({
            ...joint,
            name: typeof joint.name === 'string' ? JSON.parse(joint.name) : joint.name,
          }));
          console.log('Parsed joints:', joints); // Debug log
          setAvailableJoints(joints);
        } else {
          console.error('Failed to load joints:', data.error);
        }
      } catch (error) {
        console.error('Error loading joints:', error);
      } finally {
        setLoading(false);
      }
    };

    loadJoints();
  }, []);

  const handleJointToggle = (jointId: string) => {
    setFormData((prev) => ({
      ...prev,
      jointIds: prev.jointIds.includes(jointId)
        ? prev.jointIds.filter((id) => id !== jointId)
        : [...prev.jointIds, jointId],
    }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);

    try {
      const res = await fetch('/api/camera-positions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData),
      });

      const data = await res.json();
      if (data.success) {
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

  const canSubmit = formData.code && formData.name.en && formData.jointIds.length > 0;

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-gray-500">Loading...</div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">New Camera Position</h1>
          <p className="text-gray-600 mt-1">Create a new camera position for exercises</p>
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
        {/* Code */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Code <span className="text-red-500">*</span>
          </label>
          <Input
            value={formData.code}
            onChange={(e) => {
              setCodeManuallyEdited(true);
              setFormData({ ...formData, code: e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, '_') });
            }}
            placeholder="Auto-generated from English name"
            required
          />
          <p className="mt-1 text-xs text-gray-500">Auto-generated from English name (can be edited)</p>
        </div>

        {/* Name */}
        <LocalizedInput
          label="Name"
          value={formData.name}
          onChange={(name) => setFormData({ ...formData, name })}
          required
        />

        {/* Description */}
        <LocalizedInput
          label="Description"
          value={formData.description}
          onChange={(description) => setFormData({ ...formData, description })}
          multiline
        />

        {/* Image */}
        <div className="space-y-3">
          <FileUpload
            label="Reference Image"
            value={formData.imageUrl}
            onChange={(imageUrl) => setFormData({ ...formData, imageUrl })}
            uploadType="camera-position-image"
            accept="image/*"
            helperText="Upload a camera position reference image"
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

        {/* Joints Selection */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            Required Joints <span className="text-red-500">*</span>
          </label>
          <p className="text-sm text-gray-500 mb-3">
            Select the joints that must be visible from this camera position to start tracking.
          </p>
          {availableJoints.length === 0 ? (
            <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
              <p className="text-sm text-yellow-800">
                No joints available. Please make sure joints are seeded in the database.
              </p>
              <p className="text-xs text-yellow-600 mt-1">
                Run: <code className="bg-yellow-100 px-1 rounded">npm run db:seed</code> or <code className="bg-yellow-100 px-1 rounded">npm run prisma:seed</code>
              </p>
            </div>
          ) : (
            <>
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
                {availableJoints.map((joint) => (
                  <label
                    key={joint.id}
                    className={`flex items-start gap-2 p-3 rounded-lg border cursor-pointer transition-colors ${
                      formData.jointIds.includes(joint.id)
                        ? 'border-blue-500 bg-blue-50'
                        : 'border-gray-200 hover:border-gray-300'
                    }`}
                  >
                    <input
                      type="checkbox"
                      checked={formData.jointIds.includes(joint.id)}
                      onChange={() => handleJointToggle(joint.id)}
                      className="h-4 w-4 text-blue-600 rounded border-gray-300 focus:ring-blue-500 mt-0.5 flex-shrink-0"
                    />
                    <div className="flex flex-col min-w-0">
                      <span className="font-medium text-gray-900 text-sm leading-tight">
                        {joint.name?.en || 'N/A'}
                      </span>
                      <span className="text-gray-500 text-xs mt-0.5 leading-tight">
                        {joint.name?.ar || ''}
                      </span>
                    </div>
                  </label>
                ))}
              </div>
              {formData.jointIds.length === 0 && (
                <p className="mt-2 text-sm text-red-500">Please select at least one joint</p>
              )}
            </>
          )}
        </div>

        {/* Submit Button */}
        <div className="flex justify-end pt-4 border-t border-gray-200">
          <button
            type="submit"
            disabled={saving || !canSubmit}
            className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
          >
            {saving ? 'Saving...' : 'Create Camera Position'}
          </button>
        </div>
      </form>
    </div>
  );
}


