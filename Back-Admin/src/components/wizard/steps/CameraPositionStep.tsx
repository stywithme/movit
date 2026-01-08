'use client';

/**
 * Step 3: Camera Position
 * =======================
 */

import { useWizardStore } from '../WizardContext';
import type { FacingDirection } from '@/lib/types/localized';

interface CameraPositionStepProps {
  cameraPositions: Array<{
    id: string;
    code: string;
    name: { ar: string; en: string };
    description?: { ar: string; en: string };
    imageUrl?: string;
  }>;
}

const FACING_DIRECTIONS: Array<{
  value: FacingDirection;
  label: string;
  description: string;
}> = [
  { value: 'auto_detect', label: 'Auto Detect', description: 'Automatically detect user orientation' },
  { value: 'facing_right', label: 'Facing Right', description: 'User should face right side' },
  { value: 'facing_left', label: 'Facing Left', description: 'User should face left side' },
  { value: 'facing_camera', label: 'Facing Camera', description: 'User should face the camera' },
  { value: 'facing_away', label: 'Facing Away', description: 'User should face away from camera' },
];

export function CameraPositionStep({ cameraPositions }: CameraPositionStepProps) {
  const { cameraPosition, setCameraPosition } = useWizardStore();
  
  const togglePosition = (id: string) => {
    const current = cameraPosition.cameraPositionIds || [];
    const updated = current.includes(id)
      ? current.filter(p => p !== id)
      : [...current, id];
    setCameraPosition({ cameraPositionIds: updated });
  };
  
  const setFacingDirection = (direction: FacingDirection) => {
    setCameraPosition({ expectedFacingDirection: direction });
  };
  
  return (
    <div className="space-y-8 max-w-4xl mx-auto">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Camera Position</h2>
        <p className="text-gray-500">Select one or more camera positions for this exercise.</p>
      </div>
      
      {/* Camera Positions Grid */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {cameraPositions.map((pos) => {
          const isSelected = (cameraPosition.cameraPositionIds || []).includes(pos.id);
          
          return (
            <button
              key={pos.id}
              type="button"
              onClick={() => togglePosition(pos.id)}
              className={`
                relative p-4 rounded-xl border-2 transition-all duration-200
                flex flex-col items-center gap-3 text-gray-900
                ${isSelected 
                  ? 'border-blue-500 bg-blue-50 ring-2 ring-blue-200' 
                  : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50'
                }
              `}
            >
              {/* Image placeholder */}
              <div className="w-20 h-20 bg-gray-100 rounded-lg flex items-center justify-center">
                {pos.imageUrl ? (
                  <img src={pos.imageUrl} alt={pos.name.en} className="w-full h-full object-cover rounded-lg" />
                ) : (
                  <svg className="w-10 h-10 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
                  </svg>
                )}
              </div>
              
              {/* Name */}
              <div className="text-center">
                <p className="font-medium text-gray-900">{pos.name.en}</p>
                <p className="text-sm text-gray-500">{pos.name.ar}</p>
              </div>
              
              {/* Check mark */}
              {isSelected && (
                <div className="absolute top-2 right-2 w-6 h-6 bg-blue-500 rounded-full flex items-center justify-center">
                  <svg className="w-4 h-4 text-white" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                  </svg>
                </div>
              )}
            </button>
          );
        })}
      </div>
      
      {/* Facing Direction */}
      <div className="space-y-4 pt-6 border-t">
        <label className="block text-sm font-semibold text-gray-700">
          Expected Facing Direction
        </label>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
          {FACING_DIRECTIONS.map((dir) => {
            const isSelected = cameraPosition.expectedFacingDirection === dir.value;
            
            return (
              <button
                key={dir.value}
                type="button"
                onClick={() => setFacingDirection(dir.value)}
                className={`
                  p-3 rounded-lg border-2 text-left transition-all text-gray-900
                  ${isSelected 
                    ? 'border-blue-500 bg-blue-50' 
                    : 'border-gray-200 hover:border-gray-300'
                  }
                `}
              >
                <div className="flex items-center gap-2">
                  <div className={`
                    w-4 h-4 rounded-full border-2
                    ${isSelected ? 'border-blue-500 bg-blue-500' : 'border-gray-300'}
                  `}>
                    {isSelected && (
                      <div className="w-full h-full flex items-center justify-center">
                        <div className="w-2 h-2 bg-white rounded-full" />
                      </div>
                    )}
                  </div>
                  <span className="font-medium text-gray-900">{dir.label}</span>
                </div>
                <p className="text-xs text-gray-500 mt-1 ml-6">{dir.description}</p>
              </button>
            );
          })}
        </div>
        <p className="text-sm text-gray-500 bg-gray-50 rounded-lg p-3">
          💡 <strong>Auto Detect</strong> works for most exercises. Only specify direction for exercises requiring specific orientation.
        </p>
      </div>
    </div>
  );
}

export default CameraPositionStep;
