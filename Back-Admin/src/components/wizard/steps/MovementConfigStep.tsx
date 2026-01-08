'use client';

import { useState, useMemo } from 'react';
import { Input } from '@/components/ui';
import { LocalizedText } from '@/lib/types/localized';
import { 
  MovementConfig, 
  TrackedJoint, 
  JointRole,
  JointErrorMessages,
  JointTolerances,
  JOINT_PAIRS,
  createDefaultErrorMessages,
  createDefaultTolerances,
  copyJointSettings,
} from '@/modules/exercises/exercises.types';

interface AttributeValue {
  id: string;
  code: string;
  name: LocalizedText;
}

interface PoseVariant {
  id: string;
  name: LocalizedText;
  cameraPositionId: string;
}

interface MovementConfigData {
  [poseVariantId: string]: MovementConfig;
}

interface MovementConfigStepProps {
  data: MovementConfigData;
  onChange: (data: MovementConfigData) => void;
  poseVariants: PoseVariant[];
  joints: AttributeValue[];
  countingMethodCode: string;
}

// Default movement config
const defaultMovementConfig: MovementConfig = {
  trackedJoints: [],
  beginnerReps: 8,
  normalReps: 12,
  advancedReps: 15,
  beginnerDuration: 10,
  normalDuration: 20,
  advancedDuration: 30,
};

// Create default tracked joint with error messages and tolerances
const createDefaultTrackedJoint = (
  jointCode: string, 
  jointId: string, 
  role: JointRole
): TrackedJoint => ({
  jointCode,
  jointId,
  role,
  startPose: { min: 160, max: 180 },
  targetAngle: 90,
  tolerances: createDefaultTolerances(),
  errorMessages: createDefaultErrorMessages(jointCode),
});

export function MovementConfigStep({
  data,
  onChange,
  poseVariants,
  joints,
  countingMethodCode,
}: MovementConfigStepProps) {
  const [selectedVariantId, setSelectedVariantId] = useState<string>(poseVariants[0]?.id || '');
  const [showAdvancedRules, setShowAdvancedRules] = useState(false);
  const [expandedJoints, setExpandedJoints] = useState<Set<number>>(new Set());

  // Get or create config for selected variant
  const getConfig = (variantId: string): MovementConfig => {
    return data[variantId] || { ...defaultMovementConfig };
  };

  const config = getConfig(selectedVariantId);
  const isCounterMethod = countingMethodCode === 'counter';

  const updateConfig = (updates: Partial<MovementConfig>) => {
    onChange({
      ...data,
      [selectedVariantId]: {
        ...config,
        ...updates,
      },
    });
  };

  // Group joints by role
  const primaryJoints = useMemo(() => 
    config.trackedJoints.filter(j => j.role === 'primary'), 
    [config.trackedJoints]
  );
  
  const secondaryJoints = useMemo(() => 
    config.trackedJoints.filter(j => j.role === 'secondary'), 
    [config.trackedJoints]
  );

  // Find available joint pairs
  const availableJointPairs = useMemo(() => {
    const usedCodes = config.trackedJoints.map(j => j.jointCode);
    return JOINT_PAIRS.filter(pair => 
      !usedCodes.includes(pair.left) && !usedCodes.includes(pair.right)
    );
  }, [config.trackedJoints]);

  // Add a single joint
  const addTrackedJoint = (role: JointRole) => {
    const existingCodes = config.trackedJoints.map(j => j.jointCode);
    const availableJoint = joints.find(j => !existingCodes.includes(j.code));
    
    if (availableJoint) {
      const newJoint = createDefaultTrackedJoint(availableJoint.code, availableJoint.id, role);
      updateConfig({
        trackedJoints: [...config.trackedJoints, newJoint],
      });
    }
  };

  // Add a joint pair (left + right)
  const addJointPair = (pair: typeof JOINT_PAIRS[0], role: JointRole) => {
    const leftJoint = joints.find(j => j.code === pair.left);
    const rightJoint = joints.find(j => j.code === pair.right);
    
    if (leftJoint && rightJoint) {
      const leftTracked = createDefaultTrackedJoint(leftJoint.code, leftJoint.id, role);
      const rightTracked = {
        ...createDefaultTrackedJoint(rightJoint.code, rightJoint.id, role),
        pairedWith: leftJoint.code,
      };
      leftTracked.pairedWith = rightJoint.code;
      
      updateConfig({
        trackedJoints: [...config.trackedJoints, leftTracked, rightTracked],
      });
    }
  };

  const updateTrackedJoint = (jointCode: string, updates: Partial<TrackedJoint>) => {
    const newJoints = config.trackedJoints.map(j => {
      if (j.jointCode === jointCode) {
        return { ...j, ...updates };
      }
      return j;
    });
    updateConfig({ trackedJoints: newJoints });
  };

  const updateJointErrorMessage = (
    jointCode: string, 
    type: 'tooHigh' | 'tooLow', 
    lang: 'ar' | 'en', 
    value: string
  ) => {
    const joint = config.trackedJoints.find(j => j.jointCode === jointCode);
    if (!joint) return;
    
    const newErrorMessages: JointErrorMessages = {
      ...joint.errorMessages,
      [type]: {
        ...joint.errorMessages[type],
        [lang]: value,
      },
    };
    
    updateTrackedJoint(jointCode, { errorMessages: newErrorMessages });
  };

  const updateJointTolerance = (
    jointCode: string,
    level: keyof JointTolerances,
    value: number
  ) => {
    const joint = config.trackedJoints.find(j => j.jointCode === jointCode);
    if (!joint) return;
    
    const newTolerances: JointTolerances = {
      ...joint.tolerances,
      [level]: value,
    };
    
    updateTrackedJoint(jointCode, { tolerances: newTolerances });
  };

  const removeTrackedJoint = (jointCode: string) => {
    const joint = config.trackedJoints.find(j => j.jointCode === jointCode);
    let newJoints = config.trackedJoints.filter(j => j.jointCode !== jointCode);
    
    // Also remove paired joint if exists
    if (joint?.pairedWith) {
      newJoints = newJoints.filter(j => j.jointCode !== joint.pairedWith);
    }
    
    updateConfig({ trackedJoints: newJoints });
  };

  const changeJointType = (oldJointCode: string, newJointId: string) => {
    const newJoint = joints.find(j => j.id === newJointId);
    if (newJoint) {
      const existingJoint = config.trackedJoints.find(j => j.jointCode === oldJointCode);
      if (existingJoint) {
        updateTrackedJoint(oldJointCode, {
          jointCode: newJoint.code,
          jointId: newJoint.id,
          errorMessages: createDefaultErrorMessages(newJoint.code),
          pairedWith: undefined, // Remove pairing when manually changing
        });
      }
    }
  };

  const changeJointRole = (jointCode: string, newRole: JointRole) => {
    const joint = config.trackedJoints.find(j => j.jointCode === jointCode);
    if (!joint) return;
    
    updateTrackedJoint(jointCode, { role: newRole });
    
    // Also update paired joint
    if (joint.pairedWith) {
      updateTrackedJoint(joint.pairedWith, { role: newRole });
    }
  };

  // Copy settings from one joint to its pair
  const copyToPairedJoint = (sourceJointCode: string) => {
    const source = config.trackedJoints.find(j => j.jointCode === sourceJointCode);
    if (!source || !source.pairedWith) return;
    
    const pairedJoint = config.trackedJoints.find(j => j.jointCode === source.pairedWith);
    if (!pairedJoint) return;
    
    const copiedJoint = copyJointSettings(source, pairedJoint.jointCode, pairedJoint.jointId);
    updateTrackedJoint(pairedJoint.jointCode, copiedJoint);
  };

  const getJointName = (jointCode: string | undefined) => {
    if (!jointCode) return 'Unknown Joint';
    const joint = joints.find((j) => j.code === jointCode);
    return joint?.name.en || jointCode.replace(/_/g, ' ');
  };

  const getFirstPrimaryJoint = (): TrackedJoint | undefined => {
    return config.trackedJoints.find(j => j.role === 'primary');
  };

  // Calculate effective range for display
  const calculateRange = (tolerance: number) => {
    const primaryJoint = getFirstPrimaryJoint();
    if (!primaryJoint) return '-- - --';
    const min = primaryJoint.targetAngle - tolerance;
    const max = primaryJoint.targetAngle + tolerance;
    return `${Math.max(0, min)}° - ${Math.min(180, max)}°`;
  };

  // Get available joints for selection (not already tracked)
  const getAvailableJoints = (currentJointCode: string) => {
    const usedCodes = config.trackedJoints.map(j => j.jointCode).filter(code => code !== currentJointCode);
    return joints.filter(j => !usedCodes.includes(j.code));
  };

  const toggleJointExpanded = (index: number) => {
    const newExpanded = new Set(expandedJoints);
    if (newExpanded.has(index)) {
      newExpanded.delete(index);
    } else {
      newExpanded.add(index);
    }
    setExpandedJoints(newExpanded);
  };

  // Render a single tracked joint card
  const renderJointCard = (joint: TrackedJoint, index: number) => {
    const isExpanded = expandedJoints.has(index);
    const isPrimary = joint.role === 'primary';
    const hasPair = !!joint.pairedWith;
    
    return (
      <div 
        key={joint.jointCode} 
        className={`border rounded-lg overflow-hidden ${
          isPrimary ? 'border-blue-300 bg-blue-50' : 'border-amber-300 bg-amber-50'
        }`}
      >
        {/* Header */}
        <div className="p-4">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-3">
              <span className={`px-2 py-1 rounded text-xs font-medium ${
                isPrimary ? 'bg-blue-200 text-blue-800' : 'bg-amber-200 text-amber-800'
              }`}>
                {isPrimary ? '🎯 Primary' : '📌 Secondary'}
              </span>
              {hasPair && (
                <span className="px-2 py-1 rounded text-xs font-medium bg-purple-200 text-purple-800">
                  🔗 Paired with {getJointName(joint.pairedWith!)}
                </span>
              )}
            </div>
            <div className="flex items-center gap-2">
              {hasPair && (
                <button
                  type="button"
                  onClick={() => copyToPairedJoint(joint.jointCode)}
                  className="text-purple-600 hover:text-purple-800 p-1 text-sm"
                  title="Copy settings to paired joint"
                >
                  📋 Copy to pair
                </button>
              )}
              <button
                type="button"
                onClick={() => removeTrackedJoint(joint.jointCode)}
                className="text-red-500 hover:text-red-700 p-1"
                title="Remove joint"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
            {/* Joint Selection */}
            <div>
              <label className="block text-sm font-semibold text-gray-800 mb-2">Joint Name</label>
              <select
                value={joint.jointId}
                onChange={(e) => changeJointType(joint.jointCode, e.target.value)}
                className="w-full rounded-lg border-2 border-gray-400 px-3 py-2.5 text-sm font-semibold text-gray-900 bg-white hover:border-gray-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-200 transition-colors"
                style={{ color: joint.jointId ? '#111827' : '#6B7280' }}
              >
                <option value="" style={{ color: '#6B7280' }}>Select joint...</option>
                {getAvailableJoints(joint.jointCode).map((j) => (
                  <option key={j.id} value={j.id} style={{ color: '#111827' }}>{j.name.en}</option>
                ))}
                {/* Include current selection */}
                {joint.jointId && (
                  <option value={joint.jointId} style={{ color: '#111827' }}>{getJointName(joint.jointCode)}</option>
                )}
              </select>
            </div>

            {/* Role Toggle */}
            <div>
              <label className="block text-sm font-semibold text-gray-800 mb-2">Role</label>
              <select
                value={joint.role}
                onChange={(e) => changeJointRole(joint.jointCode, e.target.value as JointRole)}
                className={`w-full rounded-lg border-2 px-3 py-2.5 text-sm font-bold focus:ring-2 transition-colors ${
                  isPrimary 
                    ? 'border-blue-500 bg-blue-50 text-blue-900 hover:border-blue-600 focus:ring-blue-200' 
                    : 'border-amber-500 bg-amber-50 text-amber-900 hover:border-amber-600 focus:ring-amber-200'
                }`}
                style={{ 
                  color: isPrimary ? '#1E40AF' : '#92400E',
                  fontWeight: '600'
                }}
              >
                <option value="primary" style={{ color: '#1E40AF', fontWeight: '600' }}>🎯 Primary (for counting)</option>
                <option value="secondary" style={{ color: '#92400E', fontWeight: '600' }}>📌 Secondary (feedback only)</option>
              </select>
            </div>

            {/* Start Pose Min */}
            <div>
              <label className="block text-sm font-semibold text-gray-800 mb-2">
                Start Min <span className="text-gray-500 font-normal text-xs">(degrees)</span>
              </label>
              <div className="flex items-center gap-1">
                <Input
                  type="number"
                  min="0"
                  max="180"
                  value={joint.startPose.min}
                  onChange={(e) => updateTrackedJoint(joint.jointCode, {
                    startPose: { ...joint.startPose, min: Number(e.target.value) }
                  })}
                  className="flex-1 border-2"
                />
                <span className="text-gray-600 text-sm font-medium">°</span>
              </div>
            </div>

            {/* Start Pose Max */}
            <div>
              <label className="block text-sm font-semibold text-gray-800 mb-2">
                Start Max <span className="text-gray-500 font-normal text-xs">(degrees)</span>
              </label>
              <div className="flex items-center gap-1">
                <Input
                  type="number"
                  min="0"
                  max="180"
                  value={joint.startPose.max}
                  onChange={(e) => updateTrackedJoint(joint.jointCode, {
                    startPose: { ...joint.startPose, max: Number(e.target.value) }
                  })}
                  className="flex-1 border-2"
                />
                <span className="text-gray-600 text-sm font-medium">°</span>
              </div>
            </div>

            {/* Target Angle */}
            <div>
              <label className="block text-sm font-semibold text-gray-800 mb-2">
                Target Angle <span className="text-gray-500 font-normal text-xs">(degrees)</span>
              </label>
              <div className="flex items-center gap-1">
                <Input
                  type="number"
                  min="0"
                  max="180"
                  value={joint.targetAngle}
                  onChange={(e) => updateTrackedJoint(joint.jointCode, { targetAngle: Number(e.target.value) })}
                  className="flex-1 border-2"
                />
                <span className="text-gray-600 text-sm font-medium">°</span>
              </div>
            </div>
          </div>

          {/* Summary */}
          <div className="mt-3 px-3 py-2 bg-gray-50 rounded-lg border border-gray-200">
            <p className="text-sm font-medium text-gray-700">
              <span className="text-gray-500">Angle Range:</span>{' '}
              <span className="font-semibold text-blue-700">{joint.startPose.min}° - {joint.startPose.max}°</span>
              {' → '}
              <span className="font-semibold text-green-700">Target: {joint.targetAngle}°</span>
            </p>
          </div>

          {/* Tolerance per Difficulty Level for this Joint */}
          <div className="mt-4 p-4 bg-gradient-to-r from-green-50 to-blue-50 rounded-lg border border-green-200">
            <h4 className="text-sm font-bold text-gray-800 mb-3 flex items-center gap-2">
              <span>📐</span>
              <span>Tolerance per Difficulty Level</span>
              <span className="text-xs font-normal text-gray-500">(for this joint)</span>
            </h4>
            <div className="grid grid-cols-3 gap-3">
              {/* Beginner Tolerance */}
              <div className="bg-green-100 border border-green-300 rounded-lg p-3">
                <div className="flex items-center gap-1 mb-2">
                  <span className="w-2.5 h-2.5 bg-green-500 rounded-full"></span>
                  <span className="text-xs font-bold text-green-800">Beginner</span>
                </div>
                <div className="flex items-center gap-1">
                  <span className="text-gray-700 text-sm">±</span>
                  <Input
                    type="number"
                    min="0"
                    max="90"
                    value={joint.tolerances?.beginner ?? 30}
                    onChange={(e) => updateJointTolerance(joint.jointCode, 'beginner', Number(e.target.value))}
                    className="w-16 text-center border-2"
                  />
                  <span className="text-gray-600 text-sm">°</span>
                </div>
              </div>
              {/* Normal Tolerance */}
              <div className="bg-blue-100 border border-blue-300 rounded-lg p-3">
                <div className="flex items-center gap-1 mb-2">
                  <span className="w-2.5 h-2.5 bg-blue-500 rounded-full"></span>
                  <span className="text-xs font-bold text-blue-800">Normal</span>
                </div>
                <div className="flex items-center gap-1">
                  <span className="text-gray-700 text-sm">±</span>
                  <Input
                    type="number"
                    min="0"
                    max="90"
                    value={joint.tolerances?.normal ?? 15}
                    onChange={(e) => updateJointTolerance(joint.jointCode, 'normal', Number(e.target.value))}
                    className="w-16 text-center border-2"
                  />
                  <span className="text-gray-600 text-sm">°</span>
                </div>
              </div>
              {/* Advanced Tolerance */}
              <div className="bg-red-100 border border-red-300 rounded-lg p-3">
                <div className="flex items-center gap-1 mb-2">
                  <span className="w-2.5 h-2.5 bg-red-500 rounded-full"></span>
                  <span className="text-xs font-bold text-red-800">Advanced</span>
                </div>
                <div className="flex items-center gap-1">
                  <span className="text-gray-700 text-sm">±</span>
                  <Input
                    type="number"
                    min="0"
                    max="90"
                    value={joint.tolerances?.advanced ?? 5}
                    onChange={(e) => updateJointTolerance(joint.jointCode, 'advanced', Number(e.target.value))}
                    className="w-16 text-center border-2"
                  />
                  <span className="text-gray-600 text-sm">°</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Error Messages Section (Expandable) */}
        <div className="border-t-2 border-gray-200">
          <button
            type="button"
            onClick={() => toggleJointExpanded(index)}
            className="w-full px-4 py-3 flex items-center justify-between text-sm font-semibold text-gray-700 hover:bg-gray-50 transition-colors"
          >
            <span className="flex items-center gap-2">
              <span className="text-lg">⚠️</span>
              <span>Error Messages (for user feedback)</span>
            </span>
            <svg
              className={`w-5 h-5 transition-transform ${isExpanded ? 'rotate-180' : ''}`}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
            </svg>
          </button>
          
          {isExpanded && (
            <div className="px-4 pb-4 pt-2 space-y-4">
              {/* Too High Messages */}
              <div className="bg-red-50 border-2 border-red-300 rounded-lg p-4">
                <h5 className="text-sm font-bold text-red-900 mb-3 flex items-center gap-2">
                  <span className="text-lg">⬆️</span>
                  <span>When angle is TOO HIGH (needs to bend more)</span>
                </h5>
                <p className="text-xs text-red-700 mb-3">This message appears when the joint angle is above the acceptable start pose range.</p>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-semibold text-gray-800 mb-2">Arabic (AR)</label>
                    <Input
                      value={joint.errorMessages.tooHigh.ar}
                      onChange={(e) => updateJointErrorMessage(joint.jointCode, 'tooHigh', 'ar', e.target.value)}
                      placeholder="مثال: اثني ركبتك أكثر"
                      dir="rtl"
                      className="border-2"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-semibold text-gray-800 mb-2">English (EN)</label>
                    <Input
                      value={joint.errorMessages.tooHigh.en}
                      onChange={(e) => updateJointErrorMessage(joint.jointCode, 'tooHigh', 'en', e.target.value)}
                      placeholder="Example: Bend your knee more"
                      className="border-2"
                    />
                  </div>
                </div>
              </div>

              {/* Too Low Messages */}
              <div className="bg-orange-50 border-2 border-orange-300 rounded-lg p-4">
                <h5 className="text-sm font-bold text-orange-900 mb-3 flex items-center gap-2">
                  <span className="text-lg">⬇️</span>
                  <span>When angle is TOO LOW (went too far)</span>
                </h5>
                <p className="text-xs text-orange-700 mb-3">This message appears when the joint angle goes below the target angle (too deep).</p>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-semibold text-gray-800 mb-2">Arabic (AR)</label>
                    <Input
                      value={joint.errorMessages.tooLow.ar}
                      onChange={(e) => updateJointErrorMessage(joint.jointCode, 'tooLow', 'ar', e.target.value)}
                      placeholder="مثال: لا تنزل كثيراً"
                      dir="rtl"
                      className="border-2"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-semibold text-gray-800 mb-2">English (EN)</label>
                    <Input
                      value={joint.errorMessages.tooLow.en}
                      onChange={(e) => updateJointErrorMessage(joint.jointCode, 'tooLow', 'en', e.target.value)}
                      placeholder="Example: Don't go too low"
                      className="border-2"
                    />
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    );
  };

  if (poseVariants.length === 0) {
    return (
      <div className="text-center py-12 text-gray-500">
        <p>Please add pose variants first (Step 3).</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="border-b border-gray-200 pb-4">
        <h2 className="text-xl font-bold text-gray-900">Movement Configuration</h2>
        <p className="text-sm text-gray-600 mt-2 leading-relaxed">
          Configure which joints to track during the exercise. <strong>Primary joints</strong> are required for rep counting (all must reach target). 
          <strong> Secondary joints</strong> provide posture feedback only and don&apos;t affect rep count.
        </p>
      </div>

      {/* Pose Variant Tabs */}
      <div className="border-b border-gray-200 overflow-x-auto">
        <nav className="flex gap-2">
          {poseVariants.map((pv) => (
            <button
              key={pv.id}
              type="button"
              onClick={() => setSelectedVariantId(pv.id)}
              className={`py-2 px-4 border-b-2 text-sm font-medium whitespace-nowrap transition-colors ${
                selectedVariantId === pv.id
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              {pv.name.en || 'Untitled'}
            </button>
          ))}
        </nav>
      </div>

      {/* A) TRACKED JOINTS - Unified Section */}
      <section className="bg-white border-2 border-gray-300 rounded-lg p-6 shadow-sm">
        <div className="mb-4">
          <h3 className="text-lg font-bold text-gray-900 mb-1 flex items-center gap-2">
            <span className="w-7 h-7 bg-gray-700 text-white rounded-full flex items-center justify-center text-sm font-bold">A</span>
            <span>🦴 Tracked Joints</span>
          </h3>
          <p className="text-sm text-gray-700 ml-9 mt-1">
            Add joints to track during the exercise. Set each joint&apos;s <strong>Role</strong>:
          </p>
          <div className="ml-9 mt-2 flex flex-wrap gap-4 text-sm">
            <div className="flex items-center gap-2 px-3 py-1 bg-blue-100 border border-blue-300 rounded-lg">
              <span className="text-blue-700 font-bold">🎯 Primary</span>
              <span className="text-blue-600">= Required for rep counting (all must reach target)</span>
            </div>
            <div className="flex items-center gap-2 px-3 py-1 bg-amber-100 border border-amber-300 rounded-lg">
              <span className="text-amber-700 font-bold">📌 Secondary</span>
              <span className="text-amber-600">= For posture feedback only (optional)</span>
            </div>
          </div>
        </div>
        
        <div className="space-y-4">
          {config.trackedJoints.length === 0 ? (
            <div className="bg-gray-50 border-2 border-gray-200 rounded-lg p-5 text-center">
              <p className="text-gray-900 font-medium mb-1">⚠️ No joints configured</p>
              <p className="text-gray-600 text-sm">Add at least one primary joint to enable rep counting.</p>
            </div>
          ) : (
            config.trackedJoints.map((joint, idx) => renderJointCard(joint, idx))
          )}

          {/* Add Joint Buttons */}
          <div className="flex flex-wrap items-center gap-3 pt-4 border-t border-gray-200">
            <button
              type="button"
              onClick={() => addTrackedJoint('primary')}
              disabled={config.trackedJoints.length >= joints.length}
              className="text-sm font-medium text-blue-700 hover:text-blue-900 flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed px-4 py-2 bg-blue-100 border-2 border-blue-300 rounded-lg hover:bg-blue-200 transition-colors"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              <span>+ Primary Joint</span>
            </button>

            <button
              type="button"
              onClick={() => addTrackedJoint('secondary')}
              disabled={config.trackedJoints.length >= joints.length}
              className="text-sm font-medium text-amber-700 hover:text-amber-900 flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed px-4 py-2 bg-amber-100 border-2 border-amber-300 rounded-lg hover:bg-amber-200 transition-colors"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              <span>+ Secondary Joint</span>
            </button>

            {/* Quick Add Pairs */}
            {availableJointPairs.length > 0 && (
              <div className="flex items-center gap-2 ml-4 pl-4 border-l border-gray-300">
                <span className="text-sm font-medium text-gray-600">Quick add pair:</span>
                {availableJointPairs.slice(0, 3).map((pair) => (
                  <button
                    key={pair.label}
                    type="button"
                    onClick={() => addJointPair(pair, 'primary')}
                    className="text-sm font-medium text-purple-700 hover:text-purple-900 px-3 py-2 bg-purple-100 border-2 border-purple-300 rounded-lg hover:bg-purple-200 transition-colors"
                  >
                    🔗 {pair.label}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      </section>

      {/* B) REP SETTINGS (Per Difficulty) */}
      <section className="bg-white border-2 border-purple-300 rounded-lg p-6 shadow-sm">
        <div className="mb-4">
          <h3 className="text-lg font-bold text-gray-900 mb-1 flex items-center gap-2">
            <span className="w-7 h-7 bg-purple-500 text-white rounded-full flex items-center justify-center text-sm font-bold">B</span>
            <span>{isCounterMethod ? '⏱️ Hold Duration (Per Difficulty)' : '🔢 Rep Settings (Per Difficulty)'}</span>
          </h3>
          <p className="text-sm text-gray-700 ml-9 mt-1">
            {isCounterMethod 
              ? 'Set how long the user should hold the pose for each difficulty level (in seconds).'
              : 'Set the target number of repetitions for each difficulty level. The system will count reps automatically when all primary joints reach their target angle.'
            }
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {/* Beginner */}
          <div className="bg-green-50 border-2 border-green-300 rounded-lg p-5 shadow-sm">
            <div className="flex items-center gap-2 mb-4">
              <span className="w-4 h-4 bg-green-500 rounded-full"></span>
              <span className="font-bold text-green-900 text-base">Beginner</span>
            </div>
            <label className="block text-sm font-semibold text-gray-700 mb-2">
              {isCounterMethod ? 'Duration (seconds)' : 'Target Reps'}
            </label>
            <div className="flex items-center gap-2">
              <Input
                type="number"
                min="1"
                max={isCounterMethod ? 300 : 100}
                value={isCounterMethod ? (config.beginnerDuration || 10) : (config.beginnerReps || 8)}
                onChange={(e) => {
                  if (isCounterMethod) {
                    updateConfig({ beginnerDuration: Number(e.target.value) });
                  } else {
                    updateConfig({ beginnerReps: Number(e.target.value) });
                  }
                }}
                className="w-28 border-2 font-medium"
              />
              <span className="text-gray-700 font-semibold">{isCounterMethod ? 'sec' : 'reps'}</span>
            </div>
          </div>

          {/* Normal */}
          <div className="bg-blue-50 border-2 border-blue-300 rounded-lg p-5 shadow-sm">
            <div className="flex items-center gap-2 mb-4">
              <span className="w-4 h-4 bg-blue-500 rounded-full"></span>
              <span className="font-bold text-blue-900 text-base">Normal</span>
            </div>
            <label className="block text-sm font-semibold text-gray-700 mb-2">
              {isCounterMethod ? 'Duration (seconds)' : 'Target Reps'}
            </label>
            <div className="flex items-center gap-2">
              <Input
                type="number"
                min="1"
                max={isCounterMethod ? 300 : 100}
                value={isCounterMethod ? (config.normalDuration || 20) : (config.normalReps || 12)}
                onChange={(e) => {
                  if (isCounterMethod) {
                    updateConfig({ normalDuration: Number(e.target.value) });
                  } else {
                    updateConfig({ normalReps: Number(e.target.value) });
                  }
                }}
                className="w-28 border-2 font-medium"
              />
              <span className="text-gray-700 font-semibold">{isCounterMethod ? 'sec' : 'reps'}</span>
            </div>
          </div>

          {/* Advanced */}
          <div className="bg-red-50 border-2 border-red-300 rounded-lg p-5 shadow-sm">
            <div className="flex items-center gap-2 mb-4">
              <span className="w-4 h-4 bg-red-500 rounded-full"></span>
              <span className="font-bold text-red-900 text-base">Advanced</span>
            </div>
            <label className="block text-sm font-semibold text-gray-700 mb-2">
              {isCounterMethod ? 'Duration (seconds)' : 'Target Reps'}
            </label>
            <div className="flex items-center gap-2">
              <Input
                type="number"
                min="1"
                max={isCounterMethod ? 300 : 100}
                value={isCounterMethod ? (config.advancedDuration || 30) : (config.advancedReps || 15)}
                onChange={(e) => {
                  if (isCounterMethod) {
                    updateConfig({ advancedDuration: Number(e.target.value) });
                  } else {
                    updateConfig({ advancedReps: Number(e.target.value) });
                  }
                }}
                className="w-28 border-2 font-medium"
              />
              <span className="text-gray-700 font-semibold">{isCounterMethod ? 'sec' : 'reps'}</span>
            </div>
          </div>
        </div>
      </section>

      {/* Summary */}
      <div className="bg-gradient-to-r from-blue-50 to-purple-50 border border-blue-200 rounded-lg p-4">
        <h4 className="font-medium text-gray-800 mb-3">📊 Configuration Summary</h4>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
          {/* All Tracked Joints */}
          <div className="md:col-span-2 bg-white rounded-lg p-3 border border-gray-200">
            <h5 className="font-medium text-gray-700 mb-2">🦴 Tracked Joints ({config.trackedJoints.length})</h5>
            {config.trackedJoints.length === 0 ? (
              <p className="text-gray-500">None configured</p>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                {config.trackedJoints.map(j => (
                  <div key={j.jointCode} className={`flex justify-between items-center px-2 py-1 rounded ${
                    j.role === 'primary' ? 'bg-blue-50' : 'bg-amber-50'
                  }`}>
                    <span className="flex items-center gap-2">
                      <span className={`text-xs font-bold ${j.role === 'primary' ? 'text-blue-700' : 'text-amber-700'}`}>
                        {j.role === 'primary' ? '🎯' : '📌'}
                      </span>
                      <span className="text-gray-700">{getJointName(j.jointCode)}</span>
                    </span>
                    <span className="text-gray-400 text-xs">
                      {j.startPose.min}°-{j.startPose.max}° → {j.targetAngle}° | 
                      ±{j.tolerances?.beginner ?? 30}/{j.tolerances?.normal ?? 15}/{j.tolerances?.advanced ?? 5}°
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Rep Settings */}
          <div className="md:col-span-2 bg-white rounded-lg p-3 border border-purple-100">
            <h5 className="font-medium text-purple-700 mb-2">
              {isCounterMethod ? '⏱️ Hold Duration' : '🔢 Target Reps'}
            </h5>
            <div className="grid grid-cols-3 gap-4 text-center">
              <div className="bg-green-50 rounded-lg p-2">
                <span className="inline-block w-3 h-3 bg-green-500 rounded-full mr-1"></span>
                <span className="font-medium text-green-800">Beginner</span>
                <p className="text-green-600 text-sm font-semibold">
                  {isCounterMethod ? `${config.beginnerDuration || 10}s` : `${config.beginnerReps || 8} reps`}
                </p>
              </div>
              <div className="bg-blue-50 rounded-lg p-2">
                <span className="inline-block w-3 h-3 bg-blue-500 rounded-full mr-1"></span>
                <span className="font-medium text-blue-800">Normal</span>
                <p className="text-blue-600 text-sm font-semibold">
                  {isCounterMethod ? `${config.normalDuration || 20}s` : `${config.normalReps || 12} reps`}
                </p>
              </div>
              <div className="bg-red-50 rounded-lg p-2">
                <span className="inline-block w-3 h-3 bg-red-500 rounded-full mr-1"></span>
                <span className="font-medium text-red-800">Advanced</span>
                <p className="text-red-600 text-sm font-semibold">
                  {isCounterMethod ? `${config.advancedDuration || 30}s` : `${config.advancedReps || 15} reps`}
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Rep Counting Logic Explanation */}
      {primaryJoints.length > 1 && !isCounterMethod && (
        <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
          <h4 className="font-medium text-blue-800 mb-2">ℹ️ How Rep Counting Works</h4>
          <p className="text-sm text-blue-700">
            With <strong>{primaryJoints.length} primary joints</strong>, a rep is counted only when <strong>ALL</strong> of them reach their target angle within their tolerance range. 
            This ensures proper form - for example, in a squat, both knees must bend to the target angle.
          </p>
        </div>
      )}
    </div>
  );
}

export default MovementConfigStep;
