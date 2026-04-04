'use client';

/**
 * Step 3: Camera Position
 * =======================
 */

import { useWizardStore } from '../WizardContext';
import { Card, Label, Badge } from '@/components/ui';
import { Check, Camera } from 'lucide-react';
import { useMemo, useState } from 'react';

interface CameraPositionStepProps {
  cameraPositions: Array<{
    id: string;
    code: string;
    name: { ar: string; en: string };
    description?: { ar: string; en: string };
    imageUrl?: string;
    postures?: string[];
    directions?: string[];
    regions?: string[];
  }>;
}

export function CameraPositionStep({ cameraPositions = [] }: CameraPositionStepProps) {
  const { cameraPosition, setCameraPosition } = useWizardStore();
  const [postureFilter, setPostureFilter] = useState('any');
  const [directionFilter, setDirectionFilter] = useState('any');
  const [regionFilter, setRegionFilter] = useState('any');

  const postureLabel: Record<string, string> = {
    any: 'Any',
    standing: 'Standing',
    sitting: 'Sitting',
    lying_prone: 'Prone',
    lying_supine: 'Supine',
    lying_side: 'Side Lying',
  };
  const directionLabel: Record<string, string> = {
    any: 'Any',
    front: 'Front',
    back: 'Back',
    side: 'Side',
    side_left: 'Side Left',
    side_right: 'Side Right',
    diagonal: 'Diagonal',
  };
  const regionLabel: Record<string, string> = {
    any: 'Any',
    full_body: 'Full Body',
    upper_body: 'Upper Body',
    lower_body: 'Lower Body',
  };

  const axisOptions = useMemo(() => {
    const postures = Array.from(
      new Set(
        cameraPositions.flatMap((p) => p.postures || []).filter(Boolean)
      )
    );
    const directions = Array.from(
      new Set(
        cameraPositions.flatMap((p) => p.directions || []).filter(Boolean)
      )
    );
    const regions = Array.from(
      new Set(
        cameraPositions.flatMap((p) => p.regions || []).filter(Boolean)
      )
    );
    return {
      postures: ['any', ...postures],
      directions: ['any', ...directions],
      regions: ['any', ...regions],
    };
  }, [cameraPositions]);

  const filteredPositions = useMemo(() => {
    return cameraPositions.filter((p) => {
      const postureOk = postureFilter === 'any' || (p.postures || []).includes(postureFilter);
      const directionOk = directionFilter === 'any' || (p.directions || []).includes(directionFilter);
      const regionOk = regionFilter === 'any' || (p.regions || []).includes(regionFilter);
      return postureOk && directionOk && regionOk;
    });
  }, [cameraPositions, postureFilter, directionFilter, regionFilter]);

  const togglePosition = (id: string) => {
    const current = cameraPosition.cameraPositionIds || [];
    const isSelected = current.includes(id);
    const updated = isSelected ? current.filter(p => p !== id) : [...current, id];
    setCameraPosition({ cameraPositionIds: updated });
  };

  const addFilteredToSelection = () => {
    const current = cameraPosition.cameraPositionIds || [];
    const merged = Array.from(new Set([...current, ...filteredPositions.map((p) => p.id)]));
    setCameraPosition({ cameraPositionIds: merged });
  };

  const replaceSelectionWithFiltered = () => {
    const nextIds = filteredPositions.map((p) => p.id);
    setCameraPosition({ cameraPositionIds: nextIds });
  };

  const selectedCount = (cameraPosition.cameraPositionIds || []).length;

  const renderAxisBadge = (axis: string) => (
    <span className="text-[10px] px-2 py-0.5 rounded-full bg-gray-100 text-gray-600 border border-gray-200">
      {axis}
    </span>
  );

  return (
    <div className="space-y-8">
      <div>
        <div className="flex items-center gap-2 mb-2">
          <h2 className="text-2xl font-bold text-gray-900">Pose Axes & Pose Positions</h2>
          <Label tooltip="Use the 3 axes filters (Posture, Direction, Region) to narrow positions, then select one or more Pose Positions. Each selected position becomes a pose variant." />
        </div>
        <p className="text-gray-500">
          New flow: choose by axes first, then confirm the matching Pose Positions.
        </p>
      </div>

      {/* Axis Filters */}
      <Card className="p-4 space-y-4">
        <div className="grid md:grid-cols-3 gap-4">
          <div>
            <p className="text-sm font-semibold text-gray-700 mb-2">Posture Axis</p>
            <div className="flex flex-wrap gap-2">
              {axisOptions.postures.map((opt) => (
                <button
                  key={opt}
                  type="button"
                  onClick={() => setPostureFilter(opt)}
                  className={`px-2.5 py-1 rounded-full text-xs border ${
                    postureFilter === opt
                      ? 'bg-blue-600 text-white border-blue-600'
                      : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
                  }`}
                >
                  {postureLabel[opt] || opt}
                </button>
              ))}
            </div>
          </div>

          <div>
            <p className="text-sm font-semibold text-gray-700 mb-2">Direction Axis</p>
            <div className="flex flex-wrap gap-2">
              {axisOptions.directions.map((opt) => (
                <button
                  key={opt}
                  type="button"
                  onClick={() => setDirectionFilter(opt)}
                  className={`px-2.5 py-1 rounded-full text-xs border ${
                    directionFilter === opt
                      ? 'bg-blue-600 text-white border-blue-600'
                      : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
                  }`}
                >
                  {directionLabel[opt] || opt}
                </button>
              ))}
            </div>
          </div>

          <div>
            <p className="text-sm font-semibold text-gray-700 mb-2">Region Axis</p>
            <div className="flex flex-wrap gap-2">
              {axisOptions.regions.map((opt) => (
                <button
                  key={opt}
                  type="button"
                  onClick={() => setRegionFilter(opt)}
                  className={`px-2.5 py-1 rounded-full text-xs border ${
                    regionFilter === opt
                      ? 'bg-blue-600 text-white border-blue-600'
                      : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
                  }`}
                >
                  {regionLabel[opt] || opt}
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="flex flex-wrap gap-2 items-center">
          <button
            type="button"
            onClick={addFilteredToSelection}
            className="px-3 py-1.5 rounded-lg text-sm bg-blue-600 text-white hover:bg-blue-700"
          >
            Add Filtered to Selection
          </button>
          <button
            type="button"
            onClick={replaceSelectionWithFiltered}
            className="px-3 py-1.5 rounded-lg text-sm bg-gray-100 text-gray-700 hover:bg-gray-200"
          >
            Replace Selection with Filtered
          </button>
          <span className="text-xs text-gray-500">
            Matching positions: {filteredPositions.length}
          </span>
        </div>
      </Card>

      {/* Pose Positions Grid */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {filteredPositions.map((pos) => {
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
              <div className="text-center space-y-1">
                <p className="font-medium text-gray-900">{pos.name.en}</p>
                <p className="text-sm text-gray-500">{pos.name.ar}</p>
                <p className="text-[11px] text-gray-400">{pos.code}</p>
              </div>

              {/* Axes */}
              <div className="w-full space-y-1.5">
                <div className="flex flex-wrap gap-1 justify-center">
                  {(pos.postures || []).map((a) => (
                    <span key={`${pos.id}_p_${a}`}>{renderAxisBadge(postureLabel[a] || a)}</span>
                  ))}
                </div>
                <div className="flex flex-wrap gap-1 justify-center">
                  {(pos.directions || []).map((a) => (
                    <span key={`${pos.id}_d_${a}`}>{renderAxisBadge(directionLabel[a] || a)}</span>
                  ))}
                </div>
                <div className="flex flex-wrap gap-1 justify-center">
                  {(pos.regions || []).map((a) => (
                    <span key={`${pos.id}_r_${a}`}>{renderAxisBadge(regionLabel[a] || a)}</span>
                  ))}
                </div>
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
          {selectedCount} selected
        </Badge>
        {selectedCount === 0 && (
          <span className="text-sm text-amber-600">Select at least one pose position</span>
        )}
      </div>

      
    </div>
  );
}

export default CameraPositionStep;
