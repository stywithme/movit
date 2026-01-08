'use client';

/**
 * Step 4: Joint Configuration
 * ===========================
 */

import { useState } from 'react';
import { useWizardStore } from '../WizardContext';
import type { TrackedJointData } from '@/modules/exercises/exercises.validation';
import type { AngleJointCode, CountingMethodCode } from '@/lib/types/localized';

interface JointConfigStepProps {
  joints: Array<{
    id: string;
    code: string;
    name: { ar: string; en: string };
  }>;
}

// Joint pairs for easy left/right selection
const JOINT_PAIRS = [
  { left: 'left_knee', right: 'right_knee', label: 'Knees' },
  { left: 'left_hip', right: 'right_hip', label: 'Hips' },
  { left: 'left_elbow', right: 'right_elbow', label: 'Elbows' },
  { left: 'left_shoulder', right: 'right_shoulder', label: 'Shoulders' },
  { left: 'left_ankle', right: 'right_ankle', label: 'Ankles' },
];

// Default angle ranges
const DEFAULT_RANGES = {
  startPose: { min: 160, max: 180 },
  upRange: {
    beginner: { min: 150, max: 180 },
    normal: { min: 155, max: 180 },
    advanced: { min: 160, max: 180 },
  },
  downRange: {
    beginner: { min: 70, max: 110 },
    normal: { min: 60, max: 100 },
    advanced: { min: 50, max: 90 },
  },
  Range: {
    beginner: { min: 0, max: 20 },
    normal: { min: 0, max: 15 },
    advanced: { min: 0, max: 10 },
  },
};

export function JointConfigStep({ joints }: JointConfigStepProps) {
  const { jointConfig, countingMethod, addTrackedJoint, updateTrackedJoint, removeTrackedJoint } = useWizardStore();
  const [showAddModal, setShowAddModal] = useState(false);
  
  const trackedJoints = jointConfig.trackedJoints || [];
  const isHoldExercise = countingMethod.countingMethodCode === 'hold';
  
  const createDefaultJoint = (jointCode: string, role: 'primary' | 'secondary', pairedWith?: string): TrackedJointData => {
    const base = {
      joint: jointCode,
      role,
      startPose: DEFAULT_RANGES.startPose,
      errorMessages: {
        tooLow: { ar: 'انزل أكثر', en: 'Go lower' },
        tooHigh: { ar: 'لا تنزل كثيراً', en: 'Don\'t go too low' },
      },
      ...(pairedWith && { pairedWith }),
    };
    
    if (role === 'primary') {
      return {
        ...base,
        role: 'primary',
        upRange: DEFAULT_RANGES.upRange,
        downRange: DEFAULT_RANGES.downRange,
      };
    } else {
      return {
        ...base,
        role: 'secondary',
        Range: DEFAULT_RANGES.Range,
      };
    }
  };
  
  const addJointPair = (pair: typeof JOINT_PAIRS[number], role: 'primary' | 'secondary') => {
    const leftJoint = createDefaultJoint(pair.left, role, pair.right);
    const rightJoint = createDefaultJoint(pair.right, role, pair.left);
    addTrackedJoint(leftJoint);
    addTrackedJoint(rightJoint);
    setShowAddModal(false);
  };
  
  const addSingleJoint = (jointCode: string, role: 'primary' | 'secondary') => {
    const joint = createDefaultJoint(jointCode, role);
    addTrackedJoint(joint);
    setShowAddModal(false);
  };
  
  return (
    <div className="space-y-8 max-w-5xl mx-auto">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Joint Configuration</h2>
        <p className="text-gray-500">Configure which joints to track for angle validation.</p>
      </div>
      
      {/* Primary Joints Section */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-lg font-semibold text-gray-900 flex items-center gap-2">
              🎯 Primary Joints
              <span className="text-sm font-normal text-gray-500">(Required for rep counting)</span>
            </h3>
          </div>
        </div>
        
        <div className="space-y-4">
          {trackedJoints.filter(j => j.role === 'primary').map((joint, index) => (
            <JointCard
              key={`${joint.joint}-${index}`}
              joint={joint}
              index={trackedJoints.indexOf(joint)}
              onUpdate={updateTrackedJoint}
              onRemove={removeTrackedJoint}
              isHold={isHoldExercise}
            />
          ))}
          
          {trackedJoints.filter(j => j.role === 'primary').length === 0 && (
            <div className="border-2 border-dashed border-gray-200 rounded-xl p-8 text-center">
              <p className="text-gray-500">No primary joints added yet</p>
              <p className="text-sm text-gray-400">At least one primary joint is required for rep counting</p>
            </div>
          )}
        </div>
      </div>
      
      {/* Secondary Joints Section */}
      <div className="space-y-4 pt-6 border-t">
        <h3 className="text-lg font-semibold text-gray-900 flex items-center gap-2">
          📌 Secondary Joints
          <span className="text-sm font-normal text-gray-500">(Posture feedback only)</span>
        </h3>
        
        <div className="space-y-4">
          {trackedJoints.filter(j => j.role === 'secondary').map((joint, index) => (
            <JointCard
              key={`${joint.joint}-${index}`}
              joint={joint}
              index={trackedJoints.indexOf(joint)}
              onUpdate={updateTrackedJoint}
              onRemove={removeTrackedJoint}
              isHold={isHoldExercise}
            />
          ))}
        </div>
      </div>
      
      {/* Add Button */}
      <div className="flex gap-4">
        <button
          type="button"
          onClick={() => setShowAddModal(true)}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          + Add Joint
        </button>
      </div>
      
      {/* Add Modal */}
      {showAddModal && (
        <AddJointModal
          joints={joints}
          jointPairs={JOINT_PAIRS}
          existingJoints={trackedJoints.map(j => j.joint)}
          onAddPair={addJointPair}
          onAddSingle={addSingleJoint}
          onClose={() => setShowAddModal(false)}
        />
      )}
    </div>
  );
}

// Joint Card Component
interface JointCardProps {
  joint: TrackedJointData;
  index: number;
  onUpdate: (index: number, joint: TrackedJointData) => void;
  onRemove: (index: number) => void;
  isHold: boolean;
}

function JointCard({ joint, index, onUpdate, onRemove, isHold }: JointCardProps) {
  const isPrimary = joint.role === 'primary';
  
  const formatJointName = (code: string) => {
    return code.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase());
  };
  
  return (
    <div className="border rounded-xl p-4 bg-white shadow-sm">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <span className={`px-2 py-1 text-xs font-medium rounded ${isPrimary ? 'bg-blue-100 text-blue-700' : 'bg-gray-100 text-gray-700'}`}>
            {isPrimary ? 'PRIMARY' : 'SECONDARY'}
          </span>
          <h4 className="font-semibold text-gray-900">{formatJointName(joint.joint)}</h4>
          {joint.pairedWith && (
            <span className="text-sm text-gray-500">+ {formatJointName(joint.pairedWith)}</span>
          )}
        </div>
        <button
          type="button"
          onClick={() => onRemove(index)}
          className="text-red-500 hover:text-red-700 p-1"
        >
          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
      
      {/* Start Pose */}
      <div className="mb-4">
        <label className="text-sm font-medium text-gray-700">Start Pose Range</label>
        <div className="flex gap-2 mt-1">
          <input
            type="number"
            value={joint.startPose.min}
            onChange={(e) => onUpdate(index, { ...joint, startPose: { ...joint.startPose, min: Number(e.target.value) } })}
            className="w-20 px-2 py-1 border rounded text-center text-gray-900 placeholder:text-gray-500"
            placeholder="Min"
          />
          <span className="text-gray-400">-</span>
          <input
            type="number"
            value={joint.startPose.max}
            onChange={(e) => onUpdate(index, { ...joint, startPose: { ...joint.startPose, max: Number(e.target.value) } })}
            className="w-20 px-2 py-1 border rounded text-center text-gray-900 placeholder:text-gray-500"
            placeholder="Max"
          />
          <span className="text-gray-500 text-sm">degrees</span>
        </div>
      </div>
      
      {/* Ranges per difficulty */}
      {isPrimary && joint.role === 'primary' && (
        <div className="grid grid-cols-3 gap-4">
          <RangeInputGroup
            label="Up Range (Standing)"
            ranges={joint.upRange}
            onChange={(upRange) => onUpdate(index, { ...joint, upRange })}
          />
          <RangeInputGroup
            label="Down Range (Bent)"
            ranges={joint.downRange}
            onChange={(downRange) => onUpdate(index, { ...joint, downRange })}
          />
        </div>
      )}
      
      {!isPrimary && joint.role === 'secondary' && (
        <RangeInputGroup
          label="Range (All Phases)"
          ranges={joint.Range}
          onChange={(Range) => onUpdate(index, { ...joint, Range })}
        />
      )}
    </div>
  );
}

// Range Input Group
interface RangeInputGroupProps {
  label: string;
  ranges: {
    beginner: { min: number; max: number };
    normal: { min: number; max: number };
    advanced: { min: number; max: number };
  };
  onChange: (ranges: typeof ranges) => void;
}

function RangeInputGroup({ label, ranges, onChange }: RangeInputGroupProps) {
  const difficulties = ['beginner', 'normal', 'advanced'] as const;
  const colors = { beginner: 'green', normal: 'blue', advanced: 'red' };
  
  return (
    <div className="space-y-2">
      <label className="text-sm font-medium text-gray-700">{label}</label>
      {difficulties.map((diff) => (
        <div key={diff} className="flex items-center gap-2">
          <span className={`w-20 text-xs font-medium text-${colors[diff]}-600 capitalize`}>{diff}</span>
          <input
            type="number"
            value={ranges[diff].min}
            onChange={(e) => onChange({ ...ranges, [diff]: { ...ranges[diff], min: Number(e.target.value) } })}
            className="w-16 px-2 py-1 border rounded text-center text-sm text-gray-900"
          />
          <span className="text-gray-400 text-sm">-</span>
          <input
            type="number"
            value={ranges[diff].max}
            onChange={(e) => onChange({ ...ranges, [diff]: { ...ranges[diff], max: Number(e.target.value) } })}
            className="w-16 px-2 py-1 border rounded text-center text-sm text-gray-900"
          />
        </div>
      ))}
    </div>
  );
}

// Add Joint Modal
interface AddJointModalProps {
  joints: Array<{ id: string; code: string; name: { ar: string; en: string } }>;
  jointPairs: typeof JOINT_PAIRS;
  existingJoints: string[];
  onAddPair: (pair: typeof JOINT_PAIRS[number], role: 'primary' | 'secondary') => void;
  onAddSingle: (jointCode: string, role: 'primary' | 'secondary') => void;
  onClose: () => void;
}

function AddJointModal({ joints, jointPairs, existingJoints, onAddPair, onAddSingle, onClose }: AddJointModalProps) {
  const [role, setRole] = useState<'primary' | 'secondary'>('primary');
  
  const availablePairs = jointPairs.filter(p => !existingJoints.includes(p.left) && !existingJoints.includes(p.right));
  const availableJoints = joints.filter(j => !existingJoints.includes(j.code));
  
  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50" onClick={onClose}>
      <div className="bg-white rounded-xl p-6 max-w-lg w-full mx-4 max-h-[80vh] overflow-y-auto" onClick={e => e.stopPropagation()}>
        <h3 className="text-xl font-bold text-gray-900 mb-4">Add Joint</h3>
        
        {/* Role Selection */}
        <div className="flex gap-2 mb-6">
          <button
            type="button"
            onClick={() => setRole('primary')}
            className={`flex-1 py-2 rounded-lg border-2 text-gray-900 ${role === 'primary' ? 'border-blue-500 bg-blue-50' : 'border-gray-200'}`}
          >
            Primary
          </button>
          <button
            type="button"
            onClick={() => setRole('secondary')}
            className={`flex-1 py-2 rounded-lg border-2 text-gray-900 ${role === 'secondary' ? 'border-blue-500 bg-blue-50' : 'border-gray-200'}`}
          >
            Secondary
          </button>
        </div>
        
        {/* Joint Pairs */}
        {availablePairs.length > 0 && (
          <div className="mb-6">
            <h4 className="font-medium text-gray-700 mb-2">Quick Add (Paired)</h4>
            <div className="grid grid-cols-2 gap-2">
              {availablePairs.map((pair) => (
                <button
                  key={pair.label}
                  type="button"
                  onClick={() => onAddPair(pair, role)}
                  className="px-4 py-2 border rounded-lg hover:bg-gray-50 text-left text-gray-900"
                >
                  {pair.label} (L+R)
                </button>
              ))}
            </div>
          </div>
        )}
        
        {/* Single Joints */}
        <div>
          <h4 className="font-medium text-gray-700 mb-2">Single Joint</h4>
          <div className="grid grid-cols-2 gap-2 max-h-48 overflow-y-auto">
            {availableJoints.map((joint) => (
              <button
                key={joint.id}
                type="button"
                onClick={() => onAddSingle(joint.code, role)}
                className="px-4 py-2 border rounded-lg hover:bg-gray-50 text-left text-sm text-gray-900"
              >
                {joint.name.en}
              </button>
            ))}
          </div>
        </div>
        
        <button
          type="button"
          onClick={onClose}
          className="w-full mt-6 py-2 border rounded-lg hover:bg-gray-50 text-gray-900"
        >
          Cancel
        </button>
      </div>
    </div>
  );
}

export default JointConfigStep;
