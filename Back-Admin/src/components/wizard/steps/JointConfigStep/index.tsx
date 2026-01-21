'use client';

/**
 * Step 4: Joint Configuration (State-Based)
 * ==========================================
 * 
 * Interactive configuration for tracked joints with state-based ranges.
 * Features visual angle range editors and real-time validation.
 */

import { useCallback, useState, useMemo } from 'react';
import { useWizardStore } from '../../WizardContext';
import { SkeletonPicker } from './SkeletonPicker';
import { buildTrackedJoint, getPairedJointCode, hasMirrorPair, STATE_COLORS, STATE_LABELS } from './joint-templates';
import type { TrackedJointData, PrimaryTrackedJointData, SecondaryTrackedJointData, StateRangesData } from '@/modules/exercises/exercises.validation';
import { JOINT_STATE_NAMES, COUNTED_STATES, STATE_CONFIG } from '@/lib/types/localized';
import type { JointStateName } from '@/lib/types/localized';

// ============================================
// STATE RANGE EDITOR COMPONENT
// ============================================

interface StateRangeEditorProps {
  label: string;
  ranges: StateRangesData;
  onChange: (ranges: StateRangesData) => void;
  showWarningDanger?: boolean;
}

function StateRangeEditor({ label, ranges, onChange, showWarningDanger = true }: StateRangeEditorProps) {
  // Ensure ranges has at least perfect (required)
  const safeRanges: StateRangesData = ranges?.perfect 
    ? ranges 
    : { perfect: { min: 150, max: 180 } };
  
  const updateState = (state: JointStateName, field: 'min' | 'max', value: number) => {
    const currentRange = safeRanges[state];
    if (state === 'perfect') {
      onChange({
        ...safeRanges,
        perfect: { ...safeRanges.perfect, [field]: value },
      });
    } else if (currentRange) {
      onChange({
        ...safeRanges,
        [state]: { ...currentRange, [field]: value },
      });
    } else {
      // Create new range
      onChange({
        ...safeRanges,
        [state]: { min: field === 'min' ? value : 0, max: field === 'max' ? value : 180 },
      });
    }
  };

  const toggleState = (state: JointStateName) => {
    if (state === 'perfect') return; // Perfect is always required
    
    if (safeRanges[state]) {
      // Remove state
      const newRanges = { ...safeRanges };
      delete newRanges[state];
      onChange(newRanges);
    } else {
      // Add state with defaults
      const defaults: Record<JointStateName, { min: number; max: number }> = {
        perfect: { min: 150, max: 180 },
        normal: { min: 140, max: 180 },
        pad: { min: 130, max: 180 },
        warning: { min: 100, max: 130 },
        danger: { min: 0, max: 100 },
      };
      onChange({
        ...safeRanges,
        [state]: defaults[state],
      });
    }
  };

  const statesToShow = showWarningDanger ? JOINT_STATE_NAMES : COUNTED_STATES;

  return (
    <div className="space-y-3">
      <h4 className="font-semibold text-gray-800 flex items-center gap-2">
        {label}
        <span className="text-xs font-normal text-gray-500">(degrees)</span>
      </h4>
      
      {/* Visual Range Bar */}
      <div className="relative h-8 bg-gray-200 rounded-lg overflow-hidden">
        {statesToShow.map((state) => {
          const range = safeRanges[state];
          if (!range) return null;
          
          const left = (range.min / 180) * 100;
          const width = ((range.max - range.min) / 180) * 100;
          
          return (
            <div
              key={state}
              className={`absolute h-full ${STATE_COLORS[state].bg} ${STATE_COLORS[state].border} border-l-2 border-r-2 opacity-80`}
              style={{ left: `${left}%`, width: `${width}%` }}
              title={`${STATE_LABELS[state].en}: ${range.min}° - ${range.max}°`}
            />
          );
        })}
        {/* Tick marks */}
        {[0, 45, 90, 135, 180].map((deg) => (
          <div
            key={deg}
            className="absolute top-0 h-full w-px bg-gray-400"
            style={{ left: `${(deg / 180) * 100}%` }}
          >
            <span className="absolute -bottom-5 left-1/2 -translate-x-1/2 text-[10px] text-gray-500">{deg}°</span>
          </div>
        ))}
      </div>
      
      {/* State Inputs */}
      <div className="grid grid-cols-1 gap-2 mt-6">
        {statesToShow.map((state) => {
          const range = safeRanges[state];
          const isRequired = state === 'perfect';
          const isEnabled = isRequired || !!range;
          const colors = STATE_COLORS[state];
          const config = STATE_CONFIG[state];
          
          return (
            <div
              key={state}
              className={`flex items-center gap-3 p-2 rounded-lg transition-all ${
                isEnabled ? `${colors.bg} ${colors.border} border` : 'bg-gray-50 border border-dashed border-gray-300'
              }`}
            >
              {/* Toggle */}
              {!isRequired && (
                <button
                  type="button"
                  onClick={() => toggleState(state)}
                  className={`w-5 h-5 rounded border-2 flex items-center justify-center transition-colors ${
                    isEnabled 
                      ? `${colors.border} ${colors.bg}` 
                      : 'border-gray-300 bg-white'
                  }`}
                >
                  {isEnabled && <span className="text-xs">✓</span>}
                </button>
              )}
              
              {/* Label */}
              <div className={`w-24 ${isRequired ? 'ml-8' : ''}`}>
                <span className={`font-medium text-sm ${isEnabled ? colors.text : 'text-gray-400'}`}>
                  {STATE_LABELS[state].en}
                </span>
                <div className="flex items-center gap-1 text-[10px] text-gray-500">
                  {config.isRepCounted ? (
                    <span className="text-green-600">✓ Counted</span>
                  ) : (
                    <span className="text-red-600">✗ Not counted</span>
                  )}
                  <span>• {config.rate}%</span>
                </div>
              </div>
              
              {/* Min/Max inputs */}
              {isEnabled && range && (
                <div className="flex items-center gap-2 flex-1">
                  <div className="flex items-center gap-1">
                    <label className="text-xs text-gray-500">Min:</label>
                    <input
                      type="number"
                      min={0}
                      max={180}
                      value={range.min}
                      onChange={(e) => updateState(state, 'min', Number(e.target.value))}
                      className={`w-16 px-2 py-1 text-sm font-medium text-gray-900 border rounded ${colors.border} bg-white focus:outline-none focus:ring-1 focus:ring-blue-400`}
                    />
                    <span className="text-xs text-gray-400">°</span>
                  </div>
                  <span className="text-gray-400">—</span>
                  <div className="flex items-center gap-1">
                    <label className="text-xs text-gray-500">Max:</label>
                    <input
                      type="number"
                      min={0}
                      max={180}
                      value={range.max}
                      onChange={(e) => updateState(state, 'max', Number(e.target.value))}
                      className={`w-16 px-2 py-1 text-sm font-medium text-gray-900 border rounded ${colors.border} bg-white focus:outline-none focus:ring-1 focus:ring-blue-400`}
                    />
                    <span className="text-xs text-gray-400">°</span>
                  </div>
                </div>
              )}
              
              {/* Placeholder when disabled */}
              {!isEnabled && (
                <span className="text-sm text-gray-400 italic">Click to enable</span>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ============================================
// JOINT EDITOR COMPONENT
// ============================================

interface JointEditorProps {
  joint: TrackedJointData;
  index: number;
  onUpdate: (joint: TrackedJointData) => void;
  onRemove: () => void;
  onCopyToMirror?: () => void;
  isHold?: boolean;
}

function JointEditor({ joint, index, onUpdate, onRemove, onCopyToMirror, isHold = false }: JointEditorProps) {
  const [expanded, setExpanded] = useState(true);
  const isPrimary = joint.role === 'primary';
  const hasPair = hasMirrorPair(joint.joint);
  
  const updateStartPose = (field: 'min' | 'max', value: number) => {
    onUpdate({
      ...joint,
      startPose: { ...joint.startPose, [field]: value },
    });
  };

  const updateRole = (newRole: 'primary' | 'secondary') => {
    if (newRole === joint.role) return;
    
    // Rebuild joint with new role (pass isHold to build correct structure)
    const newJoint = buildTrackedJoint(joint.joint, newRole, joint.pairedWith, isHold);
    newJoint.startPose = joint.startPose;
    newJoint.stateMessages = joint.stateMessages;
    onUpdate(newJoint);
  };
  
  const updateStateMessage = (state: JointStateName, lang: 'ar' | 'en', value: string) => {
    const currentMessages = joint.stateMessages || {};
    onUpdate({
      ...joint,
      stateMessages: {
        ...currentMessages,
        [state]: {
          ...(currentMessages[state] || {}),
          [lang]: value,
        },
      },
    });
  };

  return (
    <div className={`border-2 rounded-xl overflow-hidden ${
      isPrimary ? 'border-blue-400' : 'border-purple-400'
    }`}>
      {/* Header */}
      <div 
        className={`px-4 py-3 flex items-center justify-between cursor-pointer ${
          isPrimary ? 'bg-blue-50' : 'bg-purple-50'
        }`}
        onClick={() => setExpanded(!expanded)}
      >
        <div className="flex items-center gap-3">
          <span className={`px-2 py-1 rounded text-xs font-bold ${
            isPrimary ? 'bg-blue-500 text-white' : 'bg-purple-500 text-white'
          }`}>
            {isPrimary ? '🎯 PRIMARY' : '📌 SECONDARY'}
          </span>
          <span className="font-semibold text-gray-800 capitalize">
            {joint.joint.replace(/_/g, ' ')}
          </span>
          {joint.pairedWith && (
            <span className="text-xs text-gray-500">
              (paired with {joint.pairedWith.replace(/_/g, ' ')})
            </span>
          )}
        </div>
        
        <div className="flex items-center gap-2">
          {hasPair && onCopyToMirror && (
            <button
              type="button"
              onClick={(e) => { e.stopPropagation(); onCopyToMirror(); }}
              className="px-2 py-1 text-xs font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 hover:text-gray-900 rounded transition-colors"
              title="Copy settings to paired joint"
            >
              📋 Copy to pair
            </button>
          )}
          <button
            type="button"
            onClick={(e) => { e.stopPropagation(); onRemove(); }}
            className="p-1 text-red-500 hover:text-red-700 hover:bg-red-50 rounded transition-colors"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
          <svg 
            className={`w-5 h-5 text-gray-500 transition-transform ${expanded ? 'rotate-180' : ''}`} 
            fill="none" 
            stroke="currentColor" 
            viewBox="0 0 24 24"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </div>
      </div>
      
      {/* Expanded Content */}
      {expanded && (
        <div className="p-4 space-y-6">
          {/* Role Toggle */}
          <div className="flex items-center gap-4">
            <label className="text-sm font-medium text-gray-700">Role:</label>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => updateRole('primary')}
                className={`px-3 py-1.5 text-sm rounded-lg transition-all ${
                  isPrimary 
                    ? 'bg-blue-500 text-white font-bold' 
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}
              >
                🎯 Primary (rep counting)
              </button>
              <button
                type="button"
                onClick={() => updateRole('secondary')}
                className={`px-3 py-1.5 text-sm rounded-lg transition-all ${
                  !isPrimary 
                    ? 'bg-purple-500 text-white font-bold' 
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}
              >
                📌 Secondary (form check)
              </button>
            </div>
          </div>
          
          {/* Start Pose */}
          <div className="bg-gray-50 rounded-lg p-4">
            <h4 className="font-semibold text-gray-800 mb-3">Start Position Range</h4>
            <div className="flex items-center gap-4">
              <div className="flex items-center gap-2">
                <label className="text-sm text-gray-600">Min:</label>
                <input
                  type="number"
                  min={0}
                  max={180}
                  value={joint.startPose.min}
                  onChange={(e) => updateStartPose('min', Number(e.target.value))}
                  className="w-20 px-3 py-2 font-medium text-gray-900 border-2 border-gray-200 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                />
                <span className="text-gray-400">°</span>
              </div>
              <span className="text-gray-400">—</span>
              <div className="flex items-center gap-2">
                <label className="text-sm text-gray-600">Max:</label>
                <input
                  type="number"
                  min={0}
                  max={180}
                  value={joint.startPose.max}
                  onChange={(e) => updateStartPose('max', Number(e.target.value))}
                  className="w-20 px-3 py-2 font-medium text-gray-900 border-2 border-gray-200 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                />
                <span className="text-gray-400">°</span>
              </div>
            </div>
          </div>
          
          {/* State Ranges */}
          {isPrimary && !isHold ? (
            // Up/Down and Push/Pull mode - show upRange and downRange
            <div className="grid md:grid-cols-2 gap-6">
              <StateRangeEditor
                label="⬆️ Up Range (Extended Position)"
                ranges={(joint as PrimaryTrackedJointData).upRange || { perfect: { min: 150, max: 180 } }}
                onChange={(upRange) => onUpdate({ ...joint, upRange } as PrimaryTrackedJointData)}
              />
              <StateRangeEditor
                label="⬇️ Down Range (Contracted Position)"
                ranges={(joint as PrimaryTrackedJointData).downRange || { perfect: { min: 0, max: 90 } }}
                onChange={(downRange) => onUpdate({ ...joint, downRange } as PrimaryTrackedJointData)}
              />
            </div>
          ) : isPrimary && isHold ? (
            // Hold mode - primary has single range like secondary
            <StateRangeEditor
              label="🎯 Hold Position Range"
              ranges={(joint as PrimaryTrackedJointData).range || { perfect: { min: 85, max: 95 } }}
              onChange={(range) => onUpdate({ ...joint, range } as PrimaryTrackedJointData)}
              showWarningDanger={true}
            />
          ) : (
            // Secondary joints - always single range
            <StateRangeEditor
              label="📏 Valid Range"
              ranges={(joint as SecondaryTrackedJointData).range}
              onChange={(range) => onUpdate({ ...joint, range } as SecondaryTrackedJointData)}
              showWarningDanger={true}
            />
          )}
          
          {/* State Messages (Collapsible) */}
          <details className="group">
            <summary className="cursor-pointer font-semibold text-gray-700 hover:text-gray-900">
              💬 State Messages (Optional)
              <span className="ml-2 text-xs font-normal text-gray-400">Click to expand</span>
            </summary>
            <div className="mt-4 grid gap-3">
              {JOINT_STATE_NAMES.map((state) => {
                const message = joint.stateMessages?.[state];
                const colors = STATE_COLORS[state];
                
                return (
                  <div key={state} className={`p-3 rounded-lg ${colors.bg} ${colors.border} border`}>
                    <div className="flex items-center gap-2 mb-2">
                      <span className={`font-medium text-sm ${colors.text}`}>{STATE_LABELS[state].en}</span>
                    </div>
                    <div className="grid md:grid-cols-2 gap-2">
                      <input
                        type="text"
                        placeholder="Arabic message"
                        value={message?.ar || ''}
                        onChange={(e) => updateStateMessage(state, 'ar', e.target.value)}
                        className="px-3 py-1.5 text-sm text-gray-900 border rounded bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        dir="rtl"
                      />
                      <input
                        type="text"
                        placeholder="English message"
                        value={message?.en || ''}
                        onChange={(e) => updateStateMessage(state, 'en', e.target.value)}
                        className="px-3 py-1.5 text-sm text-gray-900 border rounded bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          </details>
        </div>
      )}
    </div>
  );
}

// ============================================
// MAIN COMPONENT
// ============================================

export function JointConfigStep() {
  const { jointConfig, setJointConfig, countingMethod } = useWizardStore();
  const [activeJoint, setActiveJoint] = useState<string | null>(null);
  
  const trackedJoints = jointConfig.trackedJoints || [];
  const isHold = countingMethod.countingMethodCode === 'hold';
  
  // Validation
  const hasPrimary = trackedJoints.some(j => j.role === 'primary');
  const validationErrors = useMemo(() => {
    const errors: string[] = [];
    
    if (trackedJoints.length === 0) {
      errors.push('Add at least one joint to track');
    }
    if (!hasPrimary && trackedJoints.length > 0) {
      errors.push('At least one primary joint is required for rep counting');
    }
    
    // Validate ranges for primary joints based on counting method
    trackedJoints.forEach((joint) => {
      if (joint.role === 'primary') {
        const primary = joint as PrimaryTrackedJointData;
        
        if (isHold) {
          // Hold mode: primary joints need a single range
          if (!primary.range || !primary.range.perfect) {
            errors.push(`${joint.joint}: Missing range configuration for hold exercise`);
          }
        } else {
          // Up/Down or Push/Pull mode: primary joints need upRange and downRange
          if (!primary.upRange || !primary.upRange.perfect) {
            errors.push(`${joint.joint}: Missing upRange configuration`);
            return;
          }
          if (!primary.downRange || !primary.downRange.perfect) {
            errors.push(`${joint.joint}: Missing downRange configuration`);
            return;
          }
          
          // Validate transition zone
          const upMin = Math.min(
            primary.upRange.perfect.min,
            primary.upRange.normal?.min ?? 999,
            primary.upRange.pad?.min ?? 999
          );
          const downMax = Math.max(
            primary.downRange.perfect.max,
            primary.downRange.normal?.max ?? 0,
            primary.downRange.pad?.max ?? 0
          );
          
          if (upMin <= downMax) {
            errors.push(`${joint.joint}: Invalid transition zone (upRange min ${upMin}° must be > downRange max ${downMax}°)`);
          }
        }
      }
    });
    
    return errors;
  }, [trackedJoints, hasPrimary]);
  
  // Handle joint selection from skeleton
  const handleSelectJoint = useCallback((jointCode: string) => {
    const existing = trackedJoints.find(j => j.joint === jointCode);
    if (existing) return;
    
    const role = trackedJoints.length === 0 ? 'primary' : 'secondary';
    const newJoint = buildTrackedJoint(jointCode, role, undefined, isHold);
    setJointConfig({
      trackedJoints: [...trackedJoints, newJoint],
    });
    setActiveJoint(jointCode);
  }, [trackedJoints, setJointConfig, isHold]);
  
  // Handle joint update
  const handleUpdateJoint = useCallback((index: number, joint: TrackedJointData) => {
    const newJoints = [...trackedJoints];
    newJoints[index] = joint;
    setJointConfig({ trackedJoints: newJoints });
  }, [trackedJoints, setJointConfig]);
  
  // Handle joint removal
  const handleRemoveJoint = useCallback((index: number) => {
    const newJoints = trackedJoints.filter((_, i) => i !== index);
    setJointConfig({ trackedJoints: newJoints });
    setActiveJoint(null);
  }, [trackedJoints, setJointConfig]);
  
  // Copy to mirror joint
  const handleCopyToMirror = useCallback((sourceIndex: number) => {
    const source = trackedJoints[sourceIndex];
    const pairedCode = getPairedJointCode(source.joint);
    if (!pairedCode) return;
    
    // Check if paired joint already exists
    const existingPairIndex = trackedJoints.findIndex(j => j.joint === pairedCode);
    
    // Build paired joint with same settings (pass isHold for correct structure)
    const pairedJoint = buildTrackedJoint(pairedCode, source.role, source.joint, isHold);
    
    // Copy ranges based on mode and role
    if (source.role === 'primary') {
      const s = source as PrimaryTrackedJointData;
      if (isHold) {
        // Hold mode: copy range
        if (s.range) {
          (pairedJoint as PrimaryTrackedJointData).range = JSON.parse(JSON.stringify(s.range));
        }
      } else {
        // Up/Down mode: copy upRange and downRange
        if (s.upRange) {
          (pairedJoint as PrimaryTrackedJointData).upRange = JSON.parse(JSON.stringify(s.upRange));
        }
        if (s.downRange) {
          (pairedJoint as PrimaryTrackedJointData).downRange = JSON.parse(JSON.stringify(s.downRange));
        }
      }
    } else {
      const s = source as SecondaryTrackedJointData;
      (pairedJoint as SecondaryTrackedJointData).range = JSON.parse(JSON.stringify(s.range));
    }
    pairedJoint.startPose = { ...source.startPose };
    
    // Update source to be paired
    const updatedSource = { ...source, pairedWith: pairedCode };
    
    if (existingPairIndex >= 0) {
      // Update existing
      const newJoints = [...trackedJoints];
      newJoints[sourceIndex] = updatedSource;
      newJoints[existingPairIndex] = pairedJoint;
      setJointConfig({ trackedJoints: newJoints });
    } else {
      // Add new
      const newJoints = [...trackedJoints];
      newJoints[sourceIndex] = updatedSource;
      setJointConfig({ trackedJoints: [...newJoints, pairedJoint] });
    }
  }, [trackedJoints, setJointConfig, isHold]);
  
  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Joint Configuration</h2>
        <p className="text-gray-500">
          Configure the joints to track and their state-based angle ranges. 
          {isHold ? ' For hold exercises, angles are checked continuously.' : ' Primary joints are used for rep counting.'}
        </p>
      </div>
      
      {/* Validation Errors */}
      {validationErrors.length > 0 && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4">
          <h4 className="font-semibold text-red-800 mb-2">⚠️ Validation Issues</h4>
          <ul className="list-disc list-inside text-sm text-red-700 space-y-1">
            {validationErrors.map((err, i) => (
              <li key={i}>{err}</li>
            ))}
          </ul>
        </div>
      )}
      
      {/* Main Layout */}
      <div className="grid lg:grid-cols-2 gap-6">
        {/* Skeleton Picker */}
        <div className="bg-white rounded-2xl border p-4">
          <h3 className="font-semibold text-gray-800 mb-3">Click to Add Joints</h3>
        <SkeletonPicker
          selectedJoints={trackedJoints}
          onSelectJoint={handleSelectJoint}
            onUpdateJoint={(jointCode, updates) => {
              const index = trackedJoints.findIndex(j => j.joint === jointCode);
              if (index >= 0) {
                const updated = { ...trackedJoints[index], ...updates } as TrackedJointData;
                handleUpdateJoint(index, updated);
              }
            }}
            onRemoveJoint={(jointCode) => {
              const index = trackedJoints.findIndex(j => j.joint === jointCode);
              if (index >= 0) handleRemoveJoint(index);
            }}
            onCopyToMirror={(fromJoint, toJoint) => {
              const index = trackedJoints.findIndex(j => j.joint === fromJoint);
              if (index >= 0) handleCopyToMirror(index);
            }}
          activeJoint={activeJoint}
            setActiveJoint={setActiveJoint}
        />
      </div>
      
        {/* Joint Editors */}
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="font-semibold text-gray-800">
              Tracked Joints ({trackedJoints.length})
            </h3>
            {trackedJoints.length > 0 && (
            <button
              type="button"
              onClick={() => setJointConfig({ trackedJoints: [] })}
              className="text-xs text-red-600 hover:text-red-700"
            >
              Clear All
            </button>
            )}
          </div>
          
          {trackedJoints.length === 0 ? (
            <div className="bg-gray-50 rounded-xl p-8 text-center border-2 border-dashed border-gray-300">
              <p className="text-gray-500 mb-2">No joints selected</p>
              <p className="text-sm text-gray-400">Click on the skeleton to add joints</p>
            </div>
          ) : (
            <div className="space-y-4 max-h-[600px] overflow-y-auto pr-2">
              {trackedJoints.map((joint, index) => (
                <JointEditor
                  key={joint.joint}
                  joint={joint}
                  index={index}
                  onUpdate={(j) => handleUpdateJoint(index, j)}
                  onRemove={() => handleRemoveJoint(index)}
                  onCopyToMirror={hasMirrorPair(joint.joint) ? () => handleCopyToMirror(index) : undefined}
                  isHold={isHold}
                />
              ))}
            </div>
          )}
        </div>
      </div>
      
      {/* Help */}
      <div className="bg-gradient-to-r from-blue-50 to-indigo-50 rounded-xl p-4 border border-blue-100">
        <h4 className="font-semibold text-blue-800 mb-2">💡 State-Based Ranges Explained</h4>
        <div className="grid md:grid-cols-2 gap-4 text-sm">
          <div>
            <p className="text-blue-700 mb-2"><strong>Counted States</strong> (contribute to rep score):</p>
            <ul className="space-y-1 text-blue-600">
              <li>🟢 <strong>Perfect</strong> (100%): Ideal angle range</li>
              <li>🟡 <strong>Normal</strong> (60%): Good, acceptable range</li>
              <li>🟠 <strong>Pad</strong> (20%): Barely acceptable</li>
            </ul>
          </div>
          <div>
            <p className="text-blue-700 mb-2"><strong>Non-Counted States</strong> (feedback only):</p>
            <ul className="space-y-1 text-blue-600">
              <li>🔴 <strong>Warning</strong>: Out of range, rep not counted</li>
              <li>⛔ <strong>Danger</strong>: Dangerous position, rep invalidated</li>
            </ul>
          </div>
        </div>
        <p className="text-xs text-blue-500 mt-3">
          {isHold 
            ? 'Tip: For Hold exercises, primary joints have a single target range to maintain during the hold.'
            : 'Tip: For Up/Down exercises, make sure upRange min > downRange max to create a valid transition zone.'
          }
        </p>
      </div>
    </div>
  );
}

export default JointConfigStep;
