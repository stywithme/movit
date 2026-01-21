'use client';

/**
 * Skeleton Picker - Interactive Human Body Joint Selector
 * ========================================================
 * 
 * Simplified skeleton visualization for selecting joints.
 * Full joint configuration is done in the JointEditor.
 */

import { useState } from 'react';
import type { TrackedJointData } from '@/modules/exercises/exercises.validation';

// Joint positions for realistic body (SVG viewBox 0 0 200 400)
const JOINT_POSITIONS: Record<string, { 
  x: number; 
  y: number; 
  label: string; 
  labelAr: string;
  pair?: string;
  side?: 'left' | 'right' | 'center';
}> = {
  // Shoulders
  left_shoulder: { x: 60, y: 95, label: 'Left Shoulder', labelAr: 'الكتف الأيسر', pair: 'right_shoulder', side: 'left' },
  right_shoulder: { x: 140, y: 95, label: 'Right Shoulder', labelAr: 'الكتف الأيمن', pair: 'left_shoulder', side: 'right' },
  // Elbows
  left_elbow: { x: 35, y: 150, label: 'Left Elbow', labelAr: 'المرفق الأيسر', pair: 'right_elbow', side: 'left' },
  right_elbow: { x: 165, y: 150, label: 'Right Elbow', labelAr: 'المرفق الأيمن', pair: 'left_elbow', side: 'right' },
  // Spine
  spine: { x: 100, y: 140, label: 'Spine', labelAr: 'العمود الفقري', side: 'center' },
  // Hips
  left_hip: { x: 75, y: 195, label: 'Left Hip', labelAr: 'الورك الأيسر', pair: 'right_hip', side: 'left' },
  right_hip: { x: 125, y: 195, label: 'Right Hip', labelAr: 'الورك الأيمن', pair: 'left_hip', side: 'right' },
  // Knees
  left_knee: { x: 70, y: 270, label: 'Left Knee', labelAr: 'الركبة اليسرى', pair: 'right_knee', side: 'left' },
  right_knee: { x: 130, y: 270, label: 'Right Knee', labelAr: 'الركبة اليمنى', pair: 'left_knee', side: 'right' },
  // Ankles
  left_ankle: { x: 65, y: 345, label: 'Left Ankle', labelAr: 'الكاحل الأيسر', pair: 'right_ankle', side: 'left' },
  right_ankle: { x: 135, y: 345, label: 'Right Ankle', labelAr: 'الكاحل الأيمن', pair: 'left_ankle', side: 'right' },
};

interface SkeletonPickerProps {
  selectedJoints: TrackedJointData[];
  onSelectJoint: (jointCode: string) => void;
  onUpdateJoint: (jointCode: string, updates: Partial<TrackedJointData>) => void;
  onRemoveJoint: (jointCode: string) => void;
  onCopyToMirror: (fromJoint: string, toJoint: string) => void;
  activeJoint: string | null;
  setActiveJoint: (joint: string | null) => void;
}

export function SkeletonPicker({
  selectedJoints,
  onSelectJoint,
  onRemoveJoint,
  activeJoint,
  setActiveJoint,
}: SkeletonPickerProps) {
  const [hoveredJoint, setHoveredJoint] = useState<string | null>(null);
  
  const getJointData = (code: string) => selectedJoints.find(j => j.joint === code);
  const isSelected = (code: string) => !!getJointData(code);
  const isPrimary = (code: string) => getJointData(code)?.role === 'primary';
  
  const getJointColor = (code: string) => {
    const joint = getJointData(code);
    if (!joint) return '#e5e7eb'; // gray-200
    return joint.role === 'primary' ? '#3b82f6' : '#8b5cf6'; // blue / purple
  };
  
  const handleJointClick = (code: string, selected: boolean) => {
    if (!selected) {
      onSelectJoint(code);
    }
    setActiveJoint(code);
  };
  
  return (
    <div className="flex flex-col items-center gap-4">
      {/* Legend */}
      <div className="flex gap-4 text-xs">
        <div className="flex items-center gap-1">
          <div className="w-3 h-3 rounded-full bg-blue-500" />
          <span>Primary</span>
        </div>
        <div className="flex items-center gap-1">
          <div className="w-3 h-3 rounded-full bg-purple-500" />
          <span>Secondary</span>
        </div>
        <div className="flex items-center gap-1">
          <div className="w-3 h-3 rounded-full bg-gray-300" />
          <span>Available</span>
        </div>
      </div>
      
      {/* Skeleton SVG */}
      <div className="relative">
        <svg 
          viewBox="0 0 200 400" 
          className="w-64 h-auto"
          style={{ filter: 'drop-shadow(0 4px 6px rgba(0,0,0,0.1))' }}
        >
          {/* Body outline */}
          <defs>
            <linearGradient id="bodyGradient" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stopColor="#f8fafc" />
              <stop offset="100%" stopColor="#e2e8f0" />
            </linearGradient>
          </defs>
          
          {/* Head */}
          <circle cx="100" cy="45" r="25" fill="url(#bodyGradient)" stroke="#cbd5e1" strokeWidth="2" />
          
          {/* Torso */}
          <path
            d="M60 95 L140 95 L130 195 L70 195 Z"
            fill="url(#bodyGradient)"
            stroke="#cbd5e1"
            strokeWidth="2"
          />
          
          {/* Arms */}
          <line x1="60" y1="95" x2="35" y2="150" stroke="#cbd5e1" strokeWidth="8" strokeLinecap="round" />
          <line x1="35" y1="150" x2="25" y2="210" stroke="#cbd5e1" strokeWidth="6" strokeLinecap="round" />
          <line x1="140" y1="95" x2="165" y2="150" stroke="#cbd5e1" strokeWidth="8" strokeLinecap="round" />
          <line x1="165" y1="150" x2="175" y2="210" stroke="#cbd5e1" strokeWidth="6" strokeLinecap="round" />
          
          {/* Legs */}
          <line x1="75" y1="195" x2="70" y2="270" stroke="#cbd5e1" strokeWidth="10" strokeLinecap="round" />
          <line x1="70" y1="270" x2="65" y2="345" stroke="#cbd5e1" strokeWidth="8" strokeLinecap="round" />
          <line x1="125" y1="195" x2="130" y2="270" stroke="#cbd5e1" strokeWidth="10" strokeLinecap="round" />
          <line x1="130" y1="270" x2="135" y2="345" stroke="#cbd5e1" strokeWidth="8" strokeLinecap="round" />
          
          {/* Spine line */}
          <line x1="100" y1="70" x2="100" y2="195" stroke="#94a3b8" strokeWidth="2" strokeDasharray="4" />
          
          {/* Joint circles */}
          {Object.entries(JOINT_POSITIONS).map(([code, pos]) => {
            const selected = isSelected(code);
            const primary = isPrimary(code);
            const hovered = hoveredJoint === code;
            const active = activeJoint === code;
            
            return (
              <g key={code}>
                {/* Outer glow for active */}
                {active && (
                  <circle
                    cx={pos.x}
                    cy={pos.y}
                    r={18}
                    fill="none"
                    stroke={primary ? '#3b82f6' : selected ? '#8b5cf6' : '#9ca3af'}
                    strokeWidth="2"
                    opacity="0.5"
                  />
                )}
                
                {/* Joint circle */}
                <circle
                  cx={pos.x}
                  cy={pos.y}
                  r={hovered ? 14 : 12}
                  fill={getJointColor(code)}
                  stroke={active ? '#1d4ed8' : 'white'}
                  strokeWidth={active ? 3 : 2}
                  cursor="pointer"
                  onMouseEnter={() => setHoveredJoint(code)}
                  onMouseLeave={() => setHoveredJoint(null)}
                  onClick={() => handleJointClick(code, selected)}
                  style={{ transition: 'all 0.15s ease' }}
                />
                
                {/* Icon for selected */}
                {selected && (
                  <text
                    x={pos.x}
                    y={pos.y + 4}
                    textAnchor="middle"
                    fill="white"
                    fontSize="10"
                    fontWeight="bold"
                    pointerEvents="none"
                  >
                    ✓
                  </text>
                )}
              </g>
            );
          })}
        </svg>
        
        {/* Hover tooltip */}
        {hoveredJoint && (
          <div
            className="absolute bg-gray-900 text-white text-xs px-2 py-1 rounded shadow-lg pointer-events-none z-10"
            style={{
              left: JOINT_POSITIONS[hoveredJoint].x + 15,
              top: JOINT_POSITIONS[hoveredJoint].y - 10,
              transform: 'translateY(-50%)',
            }}
          >
            {JOINT_POSITIONS[hoveredJoint].label}
            {isSelected(hoveredJoint) && (
              <span className={`ml-1 ${isPrimary(hoveredJoint) ? 'text-blue-300' : 'text-purple-300'}`}>
                ({isPrimary(hoveredJoint) ? 'Primary' : 'Secondary'})
              </span>
            )}
          </div>
        )}
      </div>
      
      {/* Quick actions for active joint */}
      {activeJoint && isSelected(activeJoint) && (
        <div className="flex items-center gap-2 text-sm">
          <span className="text-gray-600">{JOINT_POSITIONS[activeJoint]?.label}</span>
          <button
            type="button"
            onClick={() => {
              onRemoveJoint(activeJoint);
              setActiveJoint(null);
            }}
            className="px-2 py-1 text-xs text-red-600 hover:bg-red-50 rounded"
          >
            Remove
          </button>
        </div>
      )}
    </div>
  );
}

export default SkeletonPicker;
