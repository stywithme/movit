'use client';

import { useState, useEffect } from 'react';
import { LocalizedText } from '@/lib/types/localized';

interface CameraPosition {
  id: string;
  code: string;
  name: LocalizedText;
  description?: LocalizedText | null;
  imageUrl?: string | null;
  joints?: {
    id: string;
    code: string;
    name: LocalizedText;
  }[];
}

interface PoseVariant {
  id: string;
  name: LocalizedText;
  description: LocalizedText;
  cameraPositionId: string;
  referenceImageUrl: string;
}

interface PoseVariantsStepProps {
  data: PoseVariant[];
  onChange: (data: PoseVariant[]) => void;
  cameraPositions: CameraPosition[];
}

export function PoseVariantsStep({
  data,
  onChange,
  cameraPositions,
}: PoseVariantsStepProps) {
  // Track selected camera position IDs
  const [selectedIds, setSelectedIds] = useState<string[]>(() => 
    data.map((v) => v.cameraPositionId)
  );

  // Update variants when selection changes
  useEffect(() => {
    const newVariants: PoseVariant[] = selectedIds.map((cpId) => {
      const cp = cameraPositions.find((c) => c.id === cpId);
      if (!cp) return null;

      // Check if variant already exists
      const existing = data.find((v) => v.cameraPositionId === cpId);
      if (existing) {
        return existing;
      }

      // Create new variant from camera position
      return {
        id: `temp-${Date.now()}-${cpId}`,
        name: { ...cp.name },
        description: cp.description ? { ...cp.description } : { ar: '', en: '' },
        cameraPositionId: cp.id,
        referenceImageUrl: cp.imageUrl || '',
      };
    }).filter((v): v is PoseVariant => v !== null);

    onChange(newVariants);
  }, [selectedIds, cameraPositions]);

  const toggleSelection = (cpId: string) => {
    setSelectedIds((prev) =>
      prev.includes(cpId)
        ? prev.filter((id) => id !== cpId)
        : [...prev, cpId]
    );
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-gray-200 pb-4">
        <h2 className="text-lg font-semibold text-gray-900">Pose Variants</h2>
        <p className="text-sm text-gray-500 mt-1">
          Select one or more camera positions for recording this exercise. Each selected position will create a pose variant.
        </p>
      </div>

      {cameraPositions.length === 0 ? (
        <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4 text-sm text-yellow-800">
          <p className="font-medium">No camera positions available</p>
          <p className="mt-1">
            Please create camera positions first from the{' '}
            <a href="/admin/camera-positions" className="underline text-blue-600">Camera Positions</a> page.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {cameraPositions.map((cp) => {
            const isSelected = selectedIds.includes(cp.id);
            return (
              <label
                key={cp.id}
                className={`relative border-2 rounded-lg p-4 cursor-pointer transition-all ${
                  isSelected
                    ? 'border-blue-500 bg-blue-50 ring-2 ring-blue-200'
                    : 'border-gray-200 hover:border-gray-300 hover:shadow-sm'
                }`}
              >
                <div className="flex items-start gap-3 w-full">
                  <div className="flex-1 min-w-0">
                    {/* Name with Image */}
                    <div className="flex items-center gap-3 mb-2">
                      {/* Square Thumbnail */}
                      <div className="w-16 h-16 rounded-md bg-gray-100 overflow-hidden border border-gray-200 flex items-center justify-center flex-shrink-0">
                        {cp.imageUrl ? (
                          <img src={cp.imageUrl} alt="" className="w-full h-full object-cover" />
                        ) : (
                          <svg className="w-6 h-6 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 13a3 3 0 11-6 0 3 3 0 016 0z" />
                          </svg>
                        )}
                      </div>
                      
                      {/* Name */}
                      <div className="flex-1 min-w-0">
                        <div className="flex justify-between items-start">
                          <div>
                            <h4 className="font-bold text-gray-900 text-base mb-0.5">{cp.name.en}</h4>
                            <p className="text-sm text-gray-500">{cp.name.ar}</p>
                          </div>
                          
                          {/* Checkbox moved here (right side) */}
                          <input
                            type="checkbox"
                            checked={isSelected}
                            onChange={() => toggleSelection(cp.id)}
                            className="mt-1 h-5 w-5 text-blue-600 border-gray-300 rounded focus:ring-blue-500 flex-shrink-0 ml-2"
                          />
                        </div>
                      </div>
                    </div>

                    {/* Description */}
                    {cp.description && (cp.description.en || cp.description.ar) && (
                      <p className="text-xs text-gray-600 line-clamp-2">
                        {cp.description.en || cp.description.ar}
                      </p>
                    )}

                    {/* Joints Info */}
                    {cp.joints && cp.joints.length > 0 && (
                      <div className="mt-2 pt-2 border-t border-gray-200">
                        <p className="text-xs text-gray-500">
                          Required joints: {cp.joints.map((j) => j.name.en).join(', ')}
                        </p>
                      </div>
                    )}
                  </div>
                </div>

                {/* Selected Indicator - Removed as checkbox is now visible */}
                {/* {isSelected && (
                  <div className="absolute top-2 right-2">
                    ...
                  </div>
                )} */}
              </label>
            );
          })}
        </div>
      )}

      {/* Selection Summary */}
      {selectedIds.length > 0 && (
        <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
          <p className="text-sm text-blue-800">
            <strong>{selectedIds.length}</strong> camera position{selectedIds.length > 1 ? 's' : ''} selected
          </p>
        </div>
      )}

      {/* Help Text */}
      {selectedIds.length === 0 && cameraPositions.length > 0 && (
        <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4 text-sm text-yellow-800">
          <p className="font-medium">No camera positions selected</p>
          <p className="mt-1">Please select at least one camera position to continue.</p>
        </div>
      )}
    </div>
  );
}

export default PoseVariantsStep;
