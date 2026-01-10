'use client';

/**
 * Step 3: Camera Position
 * =======================
 */

import { useWizardStore } from '../WizardContext';
import { Card, Label, Badge } from '@/components/ui';
import { Check, Camera } from 'lucide-react';
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
        <div className="flex items-center gap-2 mb-2">
          <h2 className="text-2xl font-bold text-gray-900">Camera Position</h2>
          <Label tooltip="Select which camera angles are valid for this exercise. You can select multiple if the exercise can be performed from different angles." />
        </div>
        <p className="text-gray-500">Select one or more camera positions for this exercise.</p>
      </div>
      
      {/* Camera Positions Grid */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {cameraPositions.map((pos) => {
          const isSelected = (cameraPosition.cameraPositionIds || []).includes(pos.id);
          
          return (
            <Card
              key={pos.id}
              interactive
              selected={isSelected}
              className="relative p-4 flex flex-col items-center gap-3"
              onClick={() => togglePosition(pos.id)}
            >
              {/* Image placeholder */}
              <div className="w-20 h-20 bg-gray-100 rounded-lg flex items-center justify-center overflow-hidden">
                {pos.imageUrl ? (
                  <img src={pos.imageUrl} alt={pos.name.en} className="w-full h-full object-cover" />
                ) : (
                  <Camera className="w-8 h-8 text-gray-400" />
                )}
              </div>
              
              {/* Name */}
              <div className="text-center">
                <p className="font-medium text-gray-900">{pos.name.en}</p>
                <p className="text-sm text-gray-500">{pos.name.ar}</p>
              </div>
              
              {/* Check mark */}
              {isSelected && (
                <div className="absolute top-2 right-2 w-6 h-6 bg-blue-500 rounded-full flex items-center justify-center shadow-sm">
                  <Check className="w-4 h-4 text-white" />
                </div>
              )}
            </Card>
          );
        })}
      </div>
      
      {/* Selected count */}
      <div className="flex items-center gap-2">
        <Badge variant="primary">
          {(cameraPosition.cameraPositionIds || []).length} selected
        </Badge>
        {(cameraPosition.cameraPositionIds || []).length === 0 && (
          <span className="text-sm text-amber-600">Select at least one camera position</span>
        )}
      </div>
      
      {/* Facing Direction */}
      <div className="space-y-4 pt-6 border-t border-gray-200">
        <Label tooltip="Specify how the user should be oriented relative to the camera. 'Auto Detect' is recommended for most cases.">
          Expected Facing Direction
        </Label>
        
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
          {FACING_DIRECTIONS.map((dir) => {
            const isSelected = cameraPosition.expectedFacingDirection === dir.value;
            
            return (
              <Card
                key={dir.value}
                interactive
                selected={isSelected}
                className="p-3"
                onClick={() => setFacingDirection(dir.value)}
              >
                <div className="flex items-center gap-2">
                  <div className={`
                    w-4 h-4 rounded-full border-2 transition-colors
                    ${isSelected ? 'border-blue-500 bg-blue-500' : 'border-gray-300'}
                  `}>
                    {isSelected && (
                      <div className="w-full h-full flex items-center justify-center">
                        <div className="w-1.5 h-1.5 bg-white rounded-full" />
                      </div>
                    )}
                  </div>
                  <span className="font-medium text-gray-900">{dir.label}</span>
                </div>
                <p className="text-xs text-gray-500 mt-1 ml-6">{dir.description}</p>
              </Card>
            );
          })}
        </div>
        
        <div className="flex items-start gap-2 bg-gray-50 rounded-lg p-3 text-sm text-gray-600">
          <span className="text-lg">💡</span>
          <p>
            <strong>Auto Detect</strong> works for most exercises. Only specify a fixed direction if the exercise strictly requires a specific orientation.
          </p>
        </div>
      </div>
    </div>
  );
}

export default CameraPositionStep;
