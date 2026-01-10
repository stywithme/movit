'use client';

/**
 * Skeleton Picker - Interactive Human Body Joint Selector
 * ========================================================
 * 
 * Realistic skeleton visualization with joint configuration panel.
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
  onUpdateJoint,
  onRemoveJoint,
  onCopyToMirror,
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
    if (typeof setActiveJoint === 'function') {
      setActiveJoint(code);
    } else {
      console.warn('SkeletonPicker: setActiveJoint is not a function', setActiveJoint);
    }
  };
  
  return (
    <div className="flex gap-6">
      {/* Skeleton SVG */}
      <div className="relative flex-shrink-0">
        <svg 
          viewBox="0 0 200 400" 
          className="w-72 h-auto"
          style={{ filter: 'drop-shadow(0 4px 6px rgba(0,0,0,0.1))' }}
        >
          {/* Background */}
          <defs>
            <linearGradient id="bodyGradient" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stopColor="#f8fafc" />
              <stop offset="100%" stopColor="#e2e8f0" />
            </linearGradient>
            <filter id="glow">
              <feGaussianBlur stdDeviation="3" result="coloredBlur"/>
              <feMerge>
                <feMergeNode in="coloredBlur"/>
                <feMergeNode in="SourceGraphic"/>
              </feMerge>
            </filter>
          </defs>
          
          <rect x="0" y="0" width="200" height="400" fill="url(#bodyGradient)" rx="16" />
          
          {/* === REALISTIC BODY SHAPE === */}
          
          {/* Head */}
          <ellipse cx="100" cy="35" rx="25" ry="30" fill="#fcd34d" stroke="#f59e0b" strokeWidth="2" />
          {/* Face features */}
          <circle cx="90" cy="30" r="3" fill="#78350f" /> {/* Left eye */}
          <circle cx="110" cy="30" r="3" fill="#78350f" /> {/* Right eye */}
          <path d="M 95 42 Q 100 47 105 42" fill="none" stroke="#78350f" strokeWidth="2" /> {/* Smile */}
          
          {/* Neck */}
          <path d="M 90 60 L 90 75 Q 90 80 95 82 L 105 82 Q 110 80 110 75 L 110 60" 
                fill="#fed7aa" stroke="#f97316" strokeWidth="1.5" />
          
          {/* Torso */}
          <path d="M 60 95 
                   Q 55 100 55 120 
                   L 55 180 
                   Q 55 195 75 195 
                   L 125 195 
                   Q 145 195 145 180 
                   L 145 120 
                   Q 145 100 140 95 
                   Z" 
                fill="#93c5fd" stroke="#3b82f6" strokeWidth="2" />
          
          {/* Chest detail */}
          <path d="M 80 100 Q 100 115 120 100" fill="none" stroke="#60a5fa" strokeWidth="1.5" />
          <line x1="100" y1="100" x2="100" y2="180" stroke="#60a5fa" strokeWidth="1" strokeDasharray="4 2" />
          
          {/* === ARMS === */}
          {/* Left Upper Arm */}
          <path d="M 60 95 Q 45 100 40 120 L 35 150" 
                fill="none" stroke="#fed7aa" strokeWidth="16" strokeLinecap="round" />
          <path d="M 60 95 Q 45 100 40 120 L 35 150" 
                fill="none" stroke="#f97316" strokeWidth="2" strokeLinecap="round" />
          
          {/* Left Forearm */}
          <path d="M 35 150 Q 30 170 25 195" 
                fill="none" stroke="#fed7aa" strokeWidth="14" strokeLinecap="round" />
          <path d="M 35 150 Q 30 170 25 195" 
                fill="none" stroke="#f97316" strokeWidth="2" strokeLinecap="round" />
          
          {/* Left Hand */}
          <ellipse cx="22" cy="205" rx="10" ry="14" fill="#fed7aa" stroke="#f97316" strokeWidth="1.5" />
          
          {/* Right Upper Arm */}
          <path d="M 140 95 Q 155 100 160 120 L 165 150" 
                fill="none" stroke="#fed7aa" strokeWidth="16" strokeLinecap="round" />
          <path d="M 140 95 Q 155 100 160 120 L 165 150" 
                fill="none" stroke="#f97316" strokeWidth="2" strokeLinecap="round" />
          
          {/* Right Forearm */}
          <path d="M 165 150 Q 170 170 175 195" 
                fill="none" stroke="#fed7aa" strokeWidth="14" strokeLinecap="round" />
          <path d="M 165 150 Q 170 170 175 195" 
                fill="none" stroke="#f97316" strokeWidth="2" strokeLinecap="round" />
          
          {/* Right Hand */}
          <ellipse cx="178" cy="205" rx="10" ry="14" fill="#fed7aa" stroke="#f97316" strokeWidth="1.5" />
          
          {/* === LEGS === */}
          {/* Shorts/Hips area */}
          <path d="M 75 195 L 65 230 L 100 230 L 135 230 L 125 195 Z" 
                fill="#60a5fa" stroke="#3b82f6" strokeWidth="2" />
          
          {/* Left Thigh */}
          <path d="M 75 230 Q 68 250 70 270" 
                fill="none" stroke="#fed7aa" strokeWidth="22" strokeLinecap="round" />
          <path d="M 75 230 Q 68 250 70 270" 
                fill="none" stroke="#f97316" strokeWidth="2" strokeLinecap="round" />
          
          {/* Left Shin */}
          <path d="M 70 270 Q 67 305 65 345" 
                fill="none" stroke="#fed7aa" strokeWidth="18" strokeLinecap="round" />
          <path d="M 70 270 Q 67 305 65 345" 
                fill="none" stroke="#f97316" strokeWidth="2" strokeLinecap="round" />
          
          {/* Left Foot */}
          <ellipse cx="60" cy="360" rx="15" ry="8" fill="#fed7aa" stroke="#f97316" strokeWidth="1.5" />
          
          {/* Right Thigh */}
          <path d="M 125 230 Q 132 250 130 270" 
                fill="none" stroke="#fed7aa" strokeWidth="22" strokeLinecap="round" />
          <path d="M 125 230 Q 132 250 130 270" 
                fill="none" stroke="#f97316" strokeWidth="2" strokeLinecap="round" />
          
          {/* Right Shin */}
          <path d="M 130 270 Q 133 305 135 345" 
                fill="none" stroke="#fed7aa" strokeWidth="18" strokeLinecap="round" />
          <path d="M 130 270 Q 133 305 135 345" 
                fill="none" stroke="#f97316" strokeWidth="2" strokeLinecap="round" />
          
          {/* Right Foot */}
          <ellipse cx="140" cy="360" rx="15" ry="8" fill="#fed7aa" stroke="#f97316" strokeWidth="1.5" />
          
          {/* === JOINT CIRCLES === */}
          {Object.entries(JOINT_POSITIONS).map(([code, pos]) => {
            const selected = isSelected(code);
            const hovered = hoveredJoint === code;
            const active = activeJoint === code;
            const color = getJointColor(code);
            
            return (
              <g key={code}>
                {/* Glow for selected/hovered */}
                {(selected || hovered) && (
                  <circle
                    cx={pos.x}
                    cy={pos.y}
                    r={16}
                    fill={color}
                    opacity={0.3}
                    filter="url(#glow)"
                  />
                )}
                
                {/* Active ring */}
                {active && (
                  <circle
                    cx={pos.x}
                    cy={pos.y}
                    r={18}
                    fill="none"
                    stroke="#1d4ed8"
                    strokeWidth="3"
                    strokeDasharray="4 2"
                    className="animate-spin"
                    style={{ transformOrigin: `${pos.x}px ${pos.y}px`, animationDuration: '3s' }}
                  />
                )}
                
                {/* Main joint circle */}
                <circle
                  cx={pos.x}
                  cy={pos.y}
                  r={12}
                  fill={selected ? color : 'white'}
                  stroke={selected ? color : '#9ca3af'}
                  strokeWidth={hovered || active ? 3 : 2}
                  className="cursor-pointer transition-all duration-150"
                  onMouseEnter={() => setHoveredJoint(code)}
                  onMouseLeave={() => setHoveredJoint(null)}
                  onClick={() => handleJointClick(code, selected)}
                />
                
                {/* Role indicator */}
                {selected && (
                  <text
                    x={pos.x}
                    y={pos.y + 4}
                    textAnchor="middle"
                    fill="white"
                    fontSize="10"
                    fontWeight="bold"
                    className="pointer-events-none select-none"
                  >
                    {isPrimary(code) ? 'P' : 'S'}
                  </text>
                )}
              </g>
            );
          })}
        </svg>
        
        {/* Hover tooltip */}
        {hoveredJoint && !activeJoint && (
          <div 
            className="absolute bg-gray-900 text-white text-xs px-2 py-1 rounded shadow-lg pointer-events-none z-20 whitespace-nowrap"
            style={{
              left: `${(JOINT_POSITIONS[hoveredJoint].x / 200) * 100}%`,
              top: `${(JOINT_POSITIONS[hoveredJoint].y / 400) * 100 - 5}%`,
              transform: 'translateX(-50%)',
            }}
          >
            {JOINT_POSITIONS[hoveredJoint].label}
            <br />
            <span className="text-gray-300">{JOINT_POSITIONS[hoveredJoint].labelAr}</span>
          </div>
        )}
        
        {/* Legend */}
        <div className="flex justify-center gap-4 mt-4 text-xs">
          <div className="flex items-center gap-1.5">
            <div className="w-4 h-4 rounded-full bg-blue-500 flex items-center justify-center text-white text-[8px] font-bold">P</div>
            <span className="text-gray-600">Primary</span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="w-4 h-4 rounded-full bg-purple-500 flex items-center justify-center text-white text-[8px] font-bold">S</div>
            <span className="text-gray-600">Secondary</span>
          </div>
        </div>
      </div>
      
      {/* Joint Settings Panel */}
      <div className="flex-1 min-w-0">
        {activeJoint ? (
          <JointSettingsPanel
            jointCode={activeJoint}
            jointData={getJointData(activeJoint)}
            jointInfo={JOINT_POSITIONS[activeJoint]}
            onUpdate={(updates) => onUpdateJoint(activeJoint, updates)}
            onRemove={() => {
              onRemoveJoint(activeJoint);
              if (typeof setActiveJoint === 'function') setActiveJoint(null);
            }}
            onCopyToMirror={() => {
              const pair = JOINT_POSITIONS[activeJoint].pair;
              if (pair)             onCopyToMirror(activeJoint, pair);
            }}
            onClose={() => {
              if (typeof setActiveJoint === 'function') setActiveJoint(null);
            }}
            hasPair={!!JOINT_POSITIONS[activeJoint].pair}
            pairLabel={JOINT_POSITIONS[activeJoint].pair ? JOINT_POSITIONS[JOINT_POSITIONS[activeJoint].pair!].label : undefined}
          />
        ) : (
          <div className="h-full flex flex-col items-center justify-center text-center p-8 bg-gray-50 rounded-xl border-2 border-dashed border-gray-200">
            <div className="text-6xl mb-4">👆</div>
            <h3 className="text-lg font-semibold text-gray-700 mb-2">Select a Joint</h3>
            <p className="text-gray-500 text-sm max-w-xs">
              Click on any joint on the body to add it or configure its settings
            </p>
            
            {selectedJoints.length > 0 && (
              <div className="mt-6 pt-6 border-t w-full">
                <p className="text-xs text-gray-400 mb-3">Selected joints:</p>
                <div className="flex flex-wrap justify-center gap-2">
                  {selectedJoints.map(j => (
                    <button
                      key={j.joint}
                      onClick={() => setActiveJoint(j.joint)}
                      className={`px-3 py-1 rounded-full text-xs font-medium ${
                        j.role === 'primary' 
                          ? 'bg-blue-100 text-blue-700' 
                          : 'bg-purple-100 text-purple-700'
                      }`}
                    >
                      {JOINT_POSITIONS[j.joint]?.label || j.joint}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

// Joint Settings Panel Component
interface JointSettingsPanelProps {
  jointCode: string;
  jointData: TrackedJointData | undefined;
  jointInfo: typeof JOINT_POSITIONS[string];
  onUpdate: (updates: Partial<TrackedJointData>) => void;
  onRemove: () => void;
  onCopyToMirror: () => void;
  onClose: () => void;
  hasPair: boolean;
  pairLabel?: string;
}

function JointSettingsPanel({
  jointCode,
  jointData,
  jointInfo,
  onUpdate,
  onRemove,
  onCopyToMirror,
  onClose,
  hasPair,
  pairLabel,
}: JointSettingsPanelProps) {
  const isSelected = !!jointData;
  const isPrimary = jointData?.role === 'primary';
  
  return (
    <div className="bg-white rounded-xl border shadow-lg overflow-hidden">
      {/* Header */}
      <div className={`px-4 py-3 ${isPrimary ? 'bg-blue-500' : isSelected ? 'bg-purple-500' : 'bg-gray-500'} text-white`}>
        <div className="flex items-center justify-between">
          <div>
            <h3 className="font-bold text-lg">{jointInfo.label}</h3>
            <p className="text-sm opacity-80">{jointInfo.labelAr}</p>
          </div>
          <button onClick={onClose} className="p-1 hover:bg-white/20 rounded">
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      </div>
      
      <div className="p-4 space-y-4">
        {/* Role Selection */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">Role</label>
          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={() => onUpdate({ role: 'primary' })}
              className={`p-3 rounded-lg border-2 transition-all ${
                isPrimary 
                  ? 'border-blue-500 bg-blue-50 text-blue-700' 
                  : 'border-gray-200 hover:border-gray-300'
              }`}
            >
              <div className="font-semibold">🎯 Primary</div>
              <div className="text-xs text-gray-500 mt-1">Used for rep counting</div>
            </button>
            <button
              type="button"
              onClick={() => onUpdate({ role: 'secondary' })}
              className={`p-3 rounded-lg border-2 transition-all ${
                jointData?.role === 'secondary' 
                  ? 'border-purple-500 bg-purple-50 text-purple-700' 
                  : 'border-gray-200 hover:border-gray-300'
              }`}
            >
              <div className="font-semibold">📌 Secondary</div>
              <div className="text-xs text-gray-500 mt-1">Posture feedback only</div>
            </button>
          </div>
        </div>
        
        {isSelected && (
          <>
            {/* Start Pose */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Start Pose Range (degrees)</label>
              <div className="flex items-center gap-2">
                <input
                  type="number"
                  value={jointData.startPose.min}
                  onChange={(e) => onUpdate({ startPose: { ...jointData.startPose, min: Number(e.target.value) } })}
                  className="w-20 px-3 py-2 border rounded-lg text-center"
                  min={0}
                  max={180}
                />
                <span className="text-gray-400">to</span>
                <input
                  type="number"
                  value={jointData.startPose.max}
                  onChange={(e) => onUpdate({ startPose: { ...jointData.startPose, max: Number(e.target.value) } })}
                  className="w-20 px-3 py-2 border rounded-lg text-center"
                  min={0}
                  max={180}
                />
                <span className="text-gray-500 text-sm">°</span>
              </div>
            </div>
            
            {/* Difficulty Ranges */}
            {isPrimary && 'upRange' in jointData && (
              <div className="space-y-3">
                <RangeEditor
                  label="Up Range (Standing position)"
                  ranges={jointData.upRange}
                  onChange={(upRange) => onUpdate({ upRange })}
                />
                <RangeEditor
                  label="Down Range (Bent position)"
                  ranges={jointData.downRange}
                  onChange={(downRange) => onUpdate({ downRange })}
                />
              </div>
            )}
            
            {!isPrimary && 'Range' in jointData && (
              <RangeEditor
                label="Acceptable Range"
                ranges={jointData.Range}
                onChange={(Range) => onUpdate({ Range })}
              />
            )}
            
            {/* Error Messages */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Error Messages</label>
              <div className="space-y-2">
                <div>
                  <label className="text-xs text-gray-500">Too High</label>
                  <div className="grid grid-cols-2 gap-2">
                    <input
                      type="text"
                      value={jointData.errorMessages.tooHigh.en}
                      onChange={(e) => onUpdate({ 
                        errorMessages: { 
                          ...jointData.errorMessages, 
                          tooHigh: { ...jointData.errorMessages.tooHigh, en: e.target.value } 
                        } 
                      })}
                      className="px-2 py-1.5 border rounded text-sm"
                      placeholder="English"
                    />
                    <input
                      type="text"
                      dir="rtl"
                      value={jointData.errorMessages.tooHigh.ar}
                      onChange={(e) => onUpdate({ 
                        errorMessages: { 
                          ...jointData.errorMessages, 
                          tooHigh: { ...jointData.errorMessages.tooHigh, ar: e.target.value } 
                        } 
                      })}
                      className="px-2 py-1.5 border rounded text-sm"
                      placeholder="العربية"
                    />
                  </div>
                </div>
                <div>
                  <label className="text-xs text-gray-500">Too Low</label>
                  <div className="grid grid-cols-2 gap-2">
                    <input
                      type="text"
                      value={jointData.errorMessages.tooLow.en}
                      onChange={(e) => onUpdate({ 
                        errorMessages: { 
                          ...jointData.errorMessages, 
                          tooLow: { ...jointData.errorMessages.tooLow, en: e.target.value } 
                        } 
                      })}
                      className="px-2 py-1.5 border rounded text-sm"
                      placeholder="English"
                    />
                    <input
                      type="text"
                      dir="rtl"
                      value={jointData.errorMessages.tooLow.ar}
                      onChange={(e) => onUpdate({ 
                        errorMessages: { 
                          ...jointData.errorMessages, 
                          tooLow: { ...jointData.errorMessages.tooLow, ar: e.target.value } 
                        } 
                      })}
                      className="px-2 py-1.5 border rounded text-sm"
                      placeholder="العربية"
                    />
                  </div>
                </div>
              </div>
            </div>
          </>
        )}
        
        {/* Actions */}
        <div className="flex gap-2 pt-4 border-t">
          {isSelected ? (
            <>
              {hasPair && (
                <button
                  type="button"
                  onClick={onCopyToMirror}
                  className="flex-1 px-3 py-2 bg-gray-100 hover:bg-gray-200 rounded-lg text-sm font-medium text-gray-700 flex items-center justify-center gap-2"
                >
                  <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7v8a2 2 0 002 2h6M8 7V5a2 2 0 012-2h4.586a1 1 0 01.707.293l4.414 4.414a1 1 0 01.293.707V15a2 2 0 01-2 2h-2M8 7H6a2 2 0 00-2 2v10a2 2 0 002 2h8a2 2 0 002-2v-2" />
                  </svg>
                  Copy to {pairLabel}
                </button>
              )}
              <button
                type="button"
                onClick={onRemove}
                className="px-3 py-2 bg-red-50 hover:bg-red-100 rounded-lg text-sm font-medium text-red-600"
              >
                Remove
              </button>
            </>
          ) : (
            <button
              type="button"
              onClick={() => onUpdate({ role: 'primary' })}
              className="flex-1 px-3 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg text-sm font-medium text-white"
            >
              + Add Joint
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

// Range Editor Component
interface RangeLevels {
  beginner: { min: number; max: number };
  normal: { min: number; max: number };
  advanced: { min: number; max: number };
}

interface RangeEditorProps {
  label: string;
  ranges: RangeLevels;
  onChange: (ranges: RangeLevels) => void;
}

function RangeEditor({ label, ranges, onChange }: RangeEditorProps) {
  const levels = [
    { key: 'beginner' as const, label: 'Beginner', color: 'green' },
    { key: 'normal' as const, label: 'Normal', color: 'blue' },
    { key: 'advanced' as const, label: 'Advanced', color: 'red' },
  ];
  
  return (
    <div>
      <label className="block text-sm font-medium text-gray-700 mb-2">{label}</label>
      <div className="bg-gray-50 rounded-lg p-3 space-y-2">
        {levels.map(({ key, label, color }) => (
          <div key={key} className="flex items-center gap-2">
            <span className={`w-20 text-xs font-medium text-${color}-600`}>{label}</span>
            <input
              type="number"
              value={ranges[key].min}
              onChange={(e) => onChange({ ...ranges, [key]: { ...ranges[key], min: Number(e.target.value) } })}
              className="w-14 px-2 py-1 border rounded text-center text-sm"
              min={0}
              max={180}
            />
            <span className="text-gray-400 text-xs">-</span>
            <input
              type="number"
              value={ranges[key].max}
              onChange={(e) => onChange({ ...ranges, [key]: { ...ranges[key], max: Number(e.target.value) } })}
              className="w-14 px-2 py-1 border rounded text-center text-sm"
              min={0}
              max={180}
            />
            <span className="text-gray-400 text-xs">°</span>
          </div>
        ))}
      </div>
    </div>
  );
}

export default SkeletonPicker;
