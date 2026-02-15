'use client';

/**
 * Step 3: Joint Configuration (Simplified)
 * =========================================
 * 
 * Simplified joint configuration with:
 * - Dropdown to add joints (including bilateral pairs)
 * - Bilateral joints shown as single tab (but saved as left + right)
 * - State-based angle range editors
 */

import { useCallback, useState, useMemo } from 'react';
import { useWizardStore } from '../../WizardContext';
import { buildTrackedJoint, STATE_COLORS, STATE_LABELS } from './joint-templates';
import { SmartLocalizedInput } from '@/components/forms';
import { Plus, ChevronDown, Trash2, Copy, MessageSquare } from 'lucide-react';
import { MessagePickerModal, type MessageOption } from '@/components/messages';
import type { TrackedJointData, PrimaryTrackedJointData, SecondaryTrackedJointData, StateRangesData } from '@/modules/exercises/exercises.validation';
import { JOINT_STATE_NAMES, COUNTED_STATES, STATE_CONFIG } from '@/lib/types/localized';
import type { JointStateName, LocalizedText } from '@/lib/types/localized';

// ============================================
// UI TAB STRUCTURE
// ============================================
// Represents how joints appear in tabs (bilateral = 1 tab for 2 joints)
interface UITab {
  id: string;                    // e.g., "shoulders" or "left_knee"
  label: { ar: string; en: string };
  type: 'bilateral' | 'single';
  leftJointIndex?: number;       // Index in trackedJoints array
  rightJointIndex?: number;      // Index in trackedJoints array
  singleJointIndex?: number;     // For single joints
}

// ============================================
// JOINT DEFINITIONS
// ============================================

interface JointOption {
  code: string;
  label: { ar: string; en: string };
  type: 'single' | 'bilateral';
  leftJoint?: string;
  rightJoint?: string;
}

const JOINT_OPTIONS: JointOption[] = [
  // ============================================
  // BILATERAL JOINTS (Pairs) - Most Common
  // ============================================
  { code: 'divider_bilateral', label: { ar: '━━━ مفاصل مزدوجة (الأكثر استخداماً) ━━━', en: '━━━ Bilateral Joints (Most Common) ━━━' }, type: 'single' },
  { code: 'shoulders', label: { ar: 'الكتفين', en: 'Shoulders (Both)' }, type: 'bilateral', leftJoint: 'left_shoulder', rightJoint: 'right_shoulder' },
  { code: 'shoulders_cross', label: { ar: 'الكتفين (تقاطع)', en: 'Shoulders Cross (Both)' }, type: 'bilateral', leftJoint: 'left_shoulder_cross', rightJoint: 'right_shoulder_cross' },
  { code: 'elbows', label: { ar: 'الكوعين', en: 'Elbows (Both)' }, type: 'bilateral', leftJoint: 'left_elbow', rightJoint: 'right_elbow' },
  { code: 'wrists', label: { ar: 'الرسغين', en: 'Wrists (Both)' }, type: 'bilateral', leftJoint: 'left_wrist', rightJoint: 'right_wrist' },
  { code: 'hips', label: { ar: 'الوركين', en: 'Hips (Both)' }, type: 'bilateral', leftJoint: 'left_hip', rightJoint: 'right_hip' },
  { code: 'knees', label: { ar: 'الركبتين', en: 'Knees (Both)' }, type: 'bilateral', leftJoint: 'left_knee', rightJoint: 'right_knee' },
  { code: 'ankles', label: { ar: 'الكاحلين', en: 'Ankles (Both)' }, type: 'bilateral', leftJoint: 'left_ankle', rightJoint: 'right_ankle' },
  { code: 'heels', label: { ar: 'الكعبين', en: 'Heels (Both)' }, type: 'bilateral', leftJoint: 'left_heel', rightJoint: 'right_heel' },
  { code: 'foot_indexes', label: { ar: 'أصابع القدمين', en: 'Foot Indexes (Both)' }, type: 'bilateral', leftJoint: 'left_foot_index', rightJoint: 'right_foot_index' },

  // ============================================
  // SINGLE JOINTS - Upper Body
  // ============================================
  { code: 'divider_upper', label: { ar: '━━━ الجزء العلوي ━━━', en: '━━━ Upper Body ━━━' }, type: 'single' },
  // Shoulders
  { code: 'left_shoulder', label: { ar: 'الكتف الأيسر', en: 'Left Shoulder' }, type: 'single' },
  { code: 'right_shoulder', label: { ar: 'الكتف الأيمن', en: 'Right Shoulder' }, type: 'single' },
  { code: 'left_shoulder_cross', label: { ar: 'الكتف الأيسر (تقاطع)', en: 'Left Shoulder Cross' }, type: 'single' },
  { code: 'right_shoulder_cross', label: { ar: 'الكتف الأيمن (تقاطع)', en: 'Right Shoulder Cross' }, type: 'single' },
  // Elbows
  { code: 'left_elbow', label: { ar: 'المرفق الأيسر', en: 'Left Elbow' }, type: 'single' },
  { code: 'right_elbow', label: { ar: 'المرفق الأيمن', en: 'Right Elbow' }, type: 'single' },
  // Wrists
  { code: 'left_wrist', label: { ar: 'الرسغ الأيسر', en: 'Left Wrist' }, type: 'single' },
  { code: 'right_wrist', label: { ar: 'الرسغ الأيمن', en: 'Right Wrist' }, type: 'single' },

  // ============================================
  // SINGLE JOINTS - Core
  // ============================================
  { code: 'divider_core', label: { ar: '━━━ الجذع ━━━', en: '━━━ Core ━━━' }, type: 'single' },
  { code: 'neck_left', label: { ar: 'الرقبة (كتف أيسر)', en: 'Neck (Left Shoulder)' }, type: 'single' },
  { code: 'neck_right', label: { ar: 'الرقبة (كتف أيمن)', en: 'Neck (Right Shoulder)' }, type: 'single' },
  { code: 'neck_spine', label: { ar: 'الرقبة (عمودي)', en: 'Neck (Spine)' }, type: 'single' },
  { code: 'spine', label: { ar: 'العمود الفقري', en: 'Spine' }, type: 'single' },
  { code: 'left_hip', label: { ar: 'الورك الأيسر', en: 'Left Hip' }, type: 'single' },
  { code: 'right_hip', label: { ar: 'الورك الأيمن', en: 'Right Hip' }, type: 'single' },

  // ============================================
  // SINGLE JOINTS - Lower Body
  // ============================================
  { code: 'divider_lower', label: { ar: '━━━ الجزء السفلي ━━━', en: '━━━ Lower Body ━━━' }, type: 'single' },
  // Knees
  { code: 'left_knee', label: { ar: 'الركبة اليسرى', en: 'Left Knee' }, type: 'single' },
  { code: 'right_knee', label: { ar: 'الركبة اليمنى', en: 'Right Knee' }, type: 'single' },
  // Ankles
  { code: 'left_ankle', label: { ar: 'الكاحل الأيسر', en: 'Left Ankle' }, type: 'single' },
  { code: 'right_ankle', label: { ar: 'الكاحل الأيمن', en: 'Right Ankle' }, type: 'single' },
  // Heels
  { code: 'left_heel', label: { ar: 'كعب القدم الأيسر', en: 'Left Heel' }, type: 'single' },
  { code: 'right_heel', label: { ar: 'كعب القدم الأيمن', en: 'Right Heel' }, type: 'single' },
  // Foot Index
  { code: 'left_foot_index', label: { ar: 'أصبع القدم الأيسر', en: 'Left Foot Index' }, type: 'single' },
  { code: 'right_foot_index', label: { ar: 'أصبع القدم الأيمن', en: 'Right Foot Index' }, type: 'single' },

  // ============================================
  // SINGLE JOINTS - Hands (Less Common)
  // ============================================
  { code: 'divider_hands', label: { ar: '━━━ اليدين (أقل استخداماً) ━━━', en: '━━━ Hands (Less Common) ━━━' }, type: 'single' },
  // Pinky
  { code: 'left_pinky', label: { ar: 'الخنصر الأيسر', en: 'Left Pinky' }, type: 'single' },
  { code: 'right_pinky', label: { ar: 'الخنصر الأيمن', en: 'Right Pinky' }, type: 'single' },
  // Index
  { code: 'left_index', label: { ar: 'السبابة اليسرى', en: 'Left Index' }, type: 'single' },
  { code: 'right_index', label: { ar: 'السبابة اليمنى', en: 'Right Index' }, type: 'single' },
  // Thumb
  { code: 'left_thumb', label: { ar: 'الإبهام الأيسر', en: 'Left Thumb' }, type: 'single' },
  { code: 'right_thumb', label: { ar: 'الإبهام الأيمن', en: 'Right Thumb' }, type: 'single' },
];

// ============================================
// BILATERAL JOINTS MAPPING
// ============================================
// Maps bilateral code to its display name and joints
const BILATERAL_MAPPING: Record<string, { label: { ar: string; en: string }, leftJoint: string, rightJoint: string }> = {
  shoulders: { label: { ar: 'الكتفين', en: 'Shoulders' }, leftJoint: 'left_shoulder', rightJoint: 'right_shoulder' },
  shoulders_cross: { label: { ar: 'الكتفين (تقاطع)', en: 'Shoulders Cross' }, leftJoint: 'left_shoulder_cross', rightJoint: 'right_shoulder_cross' },
  elbows: { label: { ar: 'الكوعين', en: 'Elbows' }, leftJoint: 'left_elbow', rightJoint: 'right_elbow' },
  wrists: { label: { ar: 'الرسغين', en: 'Wrists' }, leftJoint: 'left_wrist', rightJoint: 'right_wrist' },
  hips: { label: { ar: 'الوركين', en: 'Hips' }, leftJoint: 'left_hip', rightJoint: 'right_hip' },
  knees: { label: { ar: 'الركبتين', en: 'Knees' }, leftJoint: 'left_knee', rightJoint: 'right_knee' },
  ankles: { label: { ar: 'الكاحلين', en: 'Ankles' }, leftJoint: 'left_ankle', rightJoint: 'right_ankle' },
  heels: { label: { ar: 'الكعبين', en: 'Heels' }, leftJoint: 'left_heel', rightJoint: 'right_heel' },
  foot_indexes: { label: { ar: 'أصابع القدمين', en: 'Foot Indexes' }, leftJoint: 'left_foot_index', rightJoint: 'right_foot_index' },
};

// Helper: Find bilateral code from joint code
function getBilateralCode(jointCode: string): string | null {
  for (const [bilateralCode, mapping] of Object.entries(BILATERAL_MAPPING)) {
    if (mapping.leftJoint === jointCode || mapping.rightJoint === jointCode) {
      return bilateralCode;
    }
  }
  return null;
}

// Helper: Build UI tabs from tracked joints
function buildUITabs(trackedJoints: TrackedJointData[]): UITab[] {
  const tabs: UITab[] = [];
  const processedBilaterals = new Set<string>();

  trackedJoints.forEach((joint, index) => {
    const bilateralCode = getBilateralCode(joint.joint);

    if (bilateralCode && joint.pairedWith) {
      // This is part of a bilateral pair
      if (!processedBilaterals.has(bilateralCode)) {
        processedBilaterals.add(bilateralCode);

        const mapping = BILATERAL_MAPPING[bilateralCode];
        const leftIndex = trackedJoints.findIndex(j => j.joint === mapping.leftJoint);
        const rightIndex = trackedJoints.findIndex(j => j.joint === mapping.rightJoint);

        if (leftIndex >= 0 && rightIndex >= 0) {
          tabs.push({
            id: bilateralCode,
            label: mapping.label,
            type: 'bilateral',
            leftJointIndex: leftIndex,
            rightJointIndex: rightIndex,
          });
        }
      }
    } else {
      // Single joint
      const singleOption = JOINT_OPTIONS.find(o => o.code === joint.joint);
      tabs.push({
        id: joint.joint,
        label: singleOption?.label || { ar: joint.joint, en: joint.joint },
        type: 'single',
        singleJointIndex: index,
      });
    }
  });

  return tabs;
}

// Helper: Get single joint display name
function getJointLabel(jointCode: string): { ar: string; en: string } {
  const option = JOINT_OPTIONS.find(o => o.code === jointCode);
  if (option) return option.label;
  const formatted = jointCode.replace(/_/g, ' ');
  return { ar: formatted, en: formatted.charAt(0).toUpperCase() + formatted.slice(1) };
}

// ============================================
// STATE RANGE EDITOR COMPONENT
// ============================================

interface StateRangeEditorProps {
  label: string;
  ranges: StateRangesData;
  onChange: (ranges: StateRangesData) => void;
  showWarningDanger?: boolean;
  /** State messages for this range (optional - enables message icon) */
  stateMessages?: TrackedJointData['stateMessages'];
  /** Callback to update state messages */
  onStateMessagesChange?: (messages: TrackedJointData['stateMessages']) => void;
  /** Zone label for zone-based messages (e.g. 'up' | 'down') */
  zone?: 'up' | 'down';
}

function StateRangeEditor({ label, ranges, onChange, showWarningDanger = true, stateMessages, onStateMessagesChange, zone }: StateRangeEditorProps) {
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerState, setPickerState] = useState<JointStateName>('perfect');

  const openMessagePicker = (state: JointStateName) => {
    setPickerState(state);
    setPickerOpen(true);
  };

  const handleMessageSelect = (selected: MessageOption[]) => {
    if (selected.length === 0 || !onStateMessagesChange) return;
    const msg = selected[0];
    const updated = { ...(stateMessages || {}) };

    if (zone) {
      // Zone-based: set message under up/down
      const existing = updated[pickerState];
      const zoneObj = (existing && typeof existing === 'object' && 'up' in existing)
        ? { ...existing }
        : { up: undefined, down: undefined };
      (zoneObj as Record<string, unknown>)[zone] = { ar: msg.content.ar, en: msg.content.en };
      (updated as Record<string, unknown>)[pickerState] = zoneObj;
    } else {
      (updated as Record<string, unknown>)[pickerState] = { ar: msg.content.ar, en: msg.content.en };
    }
    onStateMessagesChange(updated);
  };

  const getMessageForState = (state: JointStateName): { ar?: string; en?: string } | null => {
    if (!stateMessages) return null;
    const val = stateMessages[state];
    if (!val) return null;
    if (zone && typeof val === 'object' && 'up' in val) {
      return (val as Record<string, { ar?: string; en?: string }>)[zone] || null;
    }
    if (typeof val === 'object' && 'ar' in val) {
      return val as { ar?: string; en?: string };
    }
    return null;
  };
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
      onChange({
        ...safeRanges,
        [state]: { min: field === 'min' ? value : 0, max: field === 'max' ? value : 180 },
      });
    }
  };

  const toggleState = (state: JointStateName) => {
    if (state === 'perfect') return;

    if (safeRanges[state]) {
      const newRanges = { ...safeRanges };
      delete newRanges[state];
      onChange(newRanges);
    } else {
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
              className={`flex items-center gap-3 p-2 rounded-lg transition-all ${isEnabled ? `${colors.bg} ${colors.border} border` : 'bg-gray-50 border border-dashed border-gray-300'
                }`}
            >
              {!isRequired && (
                <button
                  type="button"
                  onClick={() => toggleState(state)}
                  className={`w-5 h-5 rounded border-2 flex items-center justify-center transition-colors ${isEnabled ? `${colors.border} ${colors.bg}` : 'border-gray-300 bg-white'
                    }`}
                >
                  {isEnabled && <span className="text-xs">✓</span>}
                </button>
              )}

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

              {/* Message icon (only when enabled and message support is available) */}
              {isEnabled && onStateMessagesChange && (() => {
                const msg = getMessageForState(state);
                return (
                  <button
                    type="button"
                    onClick={() => openMessagePicker(state)}
                    className="relative group flex-shrink-0"
                    title={msg ? `${msg.en}\n${msg.ar}` : 'Assign message from library'}
                  >
                    <MessageSquare className={`h-4 w-4 transition-colors ${msg ? 'text-blue-600' : 'text-gray-400 group-hover:text-blue-500'
                      }`} />
                    {msg && (
                      <span className="absolute -top-1 -right-1 w-2 h-2 bg-blue-500 rounded-full" />
                    )}
                  </button>
                );
              })()}

              {!isEnabled && (
                <span className="text-sm text-gray-400 italic">Click to enable</span>
              )}
            </div>
          );
        })}
      </div>

      {/* Message Picker Modal */}
      {onStateMessagesChange && (
        <MessagePickerModal
          open={pickerOpen}
          onOpenChange={setPickerOpen}
          onSelect={handleMessageSelect}
          categoryFilter="state"
          contextFilter={pickerState}
          title={`Pick ${STATE_LABELS[pickerState]?.en || pickerState} Message`}
          description="Choose a message from the library for this state."
          createDefaults={{ category: 'state', context: pickerState }}
        />
      )}
    </div>
  );
}

// ============================================
// JOINT TAB CONTENT
// ============================================

interface JointTabContentProps {
  joint: TrackedJointData;
  onUpdate: (joint: TrackedJointData) => void;
  onRemove: () => void;
  onCopyToMirror?: () => void;
  isHold: boolean;
  hasMirror: boolean;
}

function JointTabContent({ joint, onUpdate, onRemove, onCopyToMirror, isHold, hasMirror }: JointTabContentProps) {
  const isPrimary = joint.role === 'primary';

  const updateStartPose = (field: 'min' | 'max', value: number) => {
    onUpdate({
      ...joint,
      startPose: { ...joint.startPose, [field]: value },
    });
  };

  const updateRole = (newRole: 'primary' | 'secondary') => {
    if (newRole === joint.role) return;
    const newJoint = buildTrackedJoint(joint.joint, newRole, joint.pairedWith, isHold);
    newJoint.startPose = joint.startPose;
    newJoint.stateMessages = joint.stateMessages;
    onUpdate(newJoint);
  };

  // Invert Indicator toggle
  const toggleInvertIndicator = () => {
    onUpdate({
      ...joint,
      invertIndicator: !joint.invertIndicator,
    });
  };

  return (
    <div className="p-6 space-y-6">
      {/* Header Actions */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <span className="text-lg font-semibold text-gray-800 capitalize">
            {joint.joint.replace(/_/g, ' ')}
          </span>
          {joint.pairedWith && (
            <span className="text-xs bg-purple-100 text-purple-700 px-2 py-1 rounded-full">
              ⟷ {joint.pairedWith.replace(/_/g, ' ')}
            </span>
          )}
        </div>

        <div className="flex items-center gap-2">
          {hasMirror && onCopyToMirror && (
            <button
              type="button"
              onClick={onCopyToMirror}
              className="flex items-center gap-1 px-3 py-1.5 text-sm text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg transition-colors"
            >
              <Copy className="w-4 h-4" />
              Copy to pair
            </button>
          )}
          <button
            type="button"
            onClick={onRemove}
            className="flex items-center gap-1 px-3 py-1.5 text-sm text-red-600 bg-red-50 hover:bg-red-100 rounded-lg transition-colors"
          >
            <Trash2 className="w-4 h-4" />
            Remove
          </button>
        </div>
      </div>

      {/* Role Toggle */}
      <div className="flex items-center gap-4 p-4 bg-gray-50 rounded-lg">
        <label className="text-sm font-medium text-gray-700">Role:</label>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => updateRole('primary')}
            className={`px-4 py-2 text-sm rounded-lg transition-all ${isPrimary
              ? 'bg-blue-500 text-white font-bold shadow-md'
              : 'bg-white text-gray-600 border hover:bg-gray-50'
              }`}
          >
            🎯 Primary (Rep Counting)
          </button>
          <button
            type="button"
            onClick={() => updateRole('secondary')}
            className={`px-4 py-2 text-sm rounded-lg transition-all ${!isPrimary
              ? 'bg-purple-500 text-white font-bold shadow-md'
              : 'bg-white text-gray-600 border hover:bg-gray-50'
              }`}
          >
            📌 Secondary (Form Check)
          </button>
        </div>
      </div>

      {/* Invert Indicator */}
      <div className="flex items-center justify-between p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
        <div>
          <p className="font-medium text-gray-800">Invert Indicator</p>
          <p className="text-sm text-gray-500">Flip the angle direction (for joints like elbow where flexion is the goal)</p>
        </div>
        <button
          type="button"
          onClick={toggleInvertIndicator}
          className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${joint.invertIndicator ? 'bg-yellow-500' : 'bg-gray-200'
            }`}
        >
          <span
            className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${joint.invertIndicator ? 'translate-x-6' : 'translate-x-1'
              }`}
          />
        </button>
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
              className="w-20 px-3 py-2 font-medium text-gray-900 border-2 border-gray-200 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
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
              className="w-20 px-3 py-2 font-medium text-gray-900 border-2 border-gray-200 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <span className="text-gray-400">°</span>
          </div>
        </div>
      </div>

      {/* State Ranges */}
      {isPrimary && !isHold ? (
        <div className="grid md:grid-cols-2 gap-6">
          <StateRangeEditor
            label="⬆️ Up Range (Extended)"
            ranges={(joint as PrimaryTrackedJointData).upRange || { perfect: { min: 150, max: 180 } }}
            onChange={(upRange) => onUpdate({ ...joint, upRange } as PrimaryTrackedJointData)}
            stateMessages={joint.stateMessages}
            onStateMessagesChange={(messages) => onUpdate({ ...joint, stateMessages: messages })}
            zone="up"
          />
          <StateRangeEditor
            label="⬇️ Down Range (Contracted)"
            ranges={(joint as PrimaryTrackedJointData).downRange || { perfect: { min: 0, max: 90 } }}
            onChange={(downRange) => onUpdate({ ...joint, downRange } as PrimaryTrackedJointData)}
            stateMessages={joint.stateMessages}
            onStateMessagesChange={(messages) => onUpdate({ ...joint, stateMessages: messages })}
            zone="down"
          />
        </div>
      ) : isPrimary && isHold ? (
        <StateRangeEditor
          label="🎯 Hold Position Range"
          ranges={(joint as PrimaryTrackedJointData).range || { perfect: { min: 85, max: 95 } }}
          onChange={(range) => onUpdate({ ...joint, range } as PrimaryTrackedJointData)}
          showWarningDanger={true}
          stateMessages={joint.stateMessages}
          onStateMessagesChange={(messages) => onUpdate({ ...joint, stateMessages: messages })}
        />
      ) : (
        <StateRangeEditor
          label="📏 Valid Range"
          ranges={(joint as SecondaryTrackedJointData).range}
          onChange={(range) => onUpdate({ ...joint, range } as SecondaryTrackedJointData)}
          showWarningDanger={true}
          stateMessages={joint.stateMessages}
          onStateMessagesChange={(messages) => onUpdate({ ...joint, stateMessages: messages })}
        />
      )}
    </div>
  );
}

// ============================================
// BILATERAL TAB CONTENT (Single tab for both joints)
// ============================================

interface BilateralTabContentProps {
  leftJoint: TrackedJointData;
  rightJoint: TrackedJointData;
  bilateralCode: string;
  onUpdateBoth: (updater: (joint: TrackedJointData) => TrackedJointData) => void;
  onRemove: () => void;
  isHold: boolean;
  bilateralStartSide?: 'left' | 'right';
}

function BilateralTabContent({ leftJoint, rightJoint, bilateralCode, onUpdateBoth, onRemove, isHold, bilateralStartSide }: BilateralTabContentProps) {
  // Use left joint as the "template" for display
  const joint = leftJoint;
  const isPrimary = joint.role === 'primary';
  const mapping = BILATERAL_MAPPING[bilateralCode];

  const updateStartPose = (field: 'min' | 'max', value: number) => {
    onUpdateBoth((j) => ({
      ...j,
      startPose: { ...j.startPose, [field]: value },
    }));
  };

  const updateRole = (newRole: 'primary' | 'secondary') => {
    if (newRole === joint.role) return;
    onUpdateBoth((j) => {
      const newJoint = buildTrackedJoint(j.joint, newRole, j.pairedWith, isHold);
      newJoint.startPose = j.startPose;
      newJoint.stateMessages = j.stateMessages;
      return newJoint;
    });
  };

  const toggleInvertIndicator = () => {
    onUpdateBoth((j) => ({
      ...j,
      invertIndicator: !j.invertIndicator,
    }));
  };

  const updateUpRange = (upRange: StateRangesData) => {
    onUpdateBoth((j) => ({ ...j, upRange } as PrimaryTrackedJointData));
  };

  const updateDownRange = (downRange: StateRangesData) => {
    onUpdateBoth((j) => ({ ...j, downRange } as PrimaryTrackedJointData));
  };

  const updateRange = (range: StateRangesData) => {
    if (isPrimary) {
      onUpdateBoth((j) => ({ ...j, range } as PrimaryTrackedJointData));
    } else {
      onUpdateBoth((j) => ({ ...j, range } as SecondaryTrackedJointData));
    }
  };

  return (
    <div className="p-6 space-y-6">
      {/* Header Actions */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <span className="text-lg font-semibold text-gray-800">
            {mapping?.label.en || bilateralCode}
          </span>
          <span className="text-xs bg-purple-100 text-purple-700 px-2 py-1 rounded-full">
            ⟷ Bilateral
          </span>
          <span className="text-xs text-gray-500">
            (Left + Right)
          </span>
        </div>

        <button
          type="button"
          onClick={onRemove}
          className="flex items-center gap-1 px-3 py-1.5 text-sm text-red-600 bg-red-50 hover:bg-red-100 rounded-lg transition-colors"
        >
          <Trash2 className="w-4 h-4" />
          Remove Both
        </button>
      </div>

      {/* Info Banner */}
      <div className="bg-purple-50 border border-purple-200 rounded-lg p-3">
        <p className="text-sm text-purple-700">
          ✨ Settings below apply to <strong>both {mapping?.label.en.toLowerCase() || 'joints'}</strong> automatically.
        </p>
      </div>

      {/* Role Toggle */}
      <div className="flex items-center gap-4 p-4 bg-gray-50 rounded-lg">
        <label className="text-sm font-medium text-gray-700">Role:</label>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => updateRole('primary')}
            className={`px-4 py-2 text-sm rounded-lg transition-all ${isPrimary
              ? 'bg-blue-500 text-white font-bold shadow-md'
              : 'bg-white text-gray-600 border hover:bg-gray-50'
              }`}
          >
            🎯 Primary (Rep Counting)
          </button>
          <button
            type="button"
            onClick={() => updateRole('secondary')}
            className={`px-4 py-2 text-sm rounded-lg transition-all ${!isPrimary
              ? 'bg-purple-500 text-white font-bold shadow-md'
              : 'bg-white text-gray-600 border hover:bg-gray-50'
              }`}
          >
            📌 Secondary (Form Check)
          </button>
        </div>
      </div>

      {/* Invert Indicator */}
      <div className="flex items-center justify-between p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
        <div>
          <p className="font-medium text-gray-800">Invert Indicator</p>
          <p className="text-sm text-gray-500">Flip the angle direction (for joints like elbow where flexion is the goal)</p>
        </div>
        <button
          type="button"
          onClick={toggleInvertIndicator}
          className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${joint.invertIndicator ? 'bg-yellow-500' : 'bg-gray-200'
            }`}
        >
          <span
            className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${joint.invertIndicator ? 'translate-x-6' : 'translate-x-1'
              }`}
          />
        </button>
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
              className="w-20 px-3 py-2 font-medium text-gray-900 border-2 border-gray-200 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
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
              className="w-20 px-3 py-2 font-medium text-gray-900 border-2 border-gray-200 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <span className="text-gray-400">°</span>
          </div>
        </div>
      </div>

      {/* State Ranges */}
      {isPrimary && !isHold ? (
        <div className="grid md:grid-cols-2 gap-6">
          <StateRangeEditor
            label="⬆️ Up Range (Extended)"
            ranges={(joint as PrimaryTrackedJointData).upRange || { perfect: { min: 150, max: 180 } }}
            onChange={updateUpRange}
            stateMessages={joint.stateMessages}
            onStateMessagesChange={(messages) => onUpdateBoth((j) => ({ ...j, stateMessages: messages }))}
            zone="up"
          />
          <StateRangeEditor
            label="⬇️ Down Range (Contracted)"
            ranges={(joint as PrimaryTrackedJointData).downRange || { perfect: { min: 0, max: 90 } }}
            onChange={updateDownRange}
            stateMessages={joint.stateMessages}
            onStateMessagesChange={(messages) => onUpdateBoth((j) => ({ ...j, stateMessages: messages }))}
            zone="down"
          />
        </div>
      ) : isPrimary && isHold ? (
        <StateRangeEditor
          label="🎯 Hold Position Range"
          ranges={(joint as PrimaryTrackedJointData).range || { perfect: { min: 85, max: 95 } }}
          onChange={updateRange}
          showWarningDanger={true}
          stateMessages={joint.stateMessages}
          onStateMessagesChange={(messages) => onUpdateBoth((j) => ({ ...j, stateMessages: messages }))}
        />
      ) : (
        <StateRangeEditor
          label="📏 Valid Range"
          ranges={(joint as SecondaryTrackedJointData).range}
          onChange={updateRange}
          showWarningDanger={true}
          stateMessages={joint.stateMessages}
          onStateMessagesChange={(messages) => onUpdateBoth((j) => ({ ...j, stateMessages: messages }))}
        />
      )}

      {/* Visual Comparison */}
      <div className="bg-gray-50 rounded-lg p-4 border">
        <h4 className="font-medium text-gray-700 mb-3 text-sm">📊 Current Values</h4>
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div className={`bg-white rounded-lg p-3 border ${bilateralStartSide === 'left' ? 'ring-2 ring-blue-300' : ''}`}>
            <div className="flex items-center gap-2 mb-1">
              <span className="font-medium text-gray-800">{getJointLabel(leftJoint.joint).en}</span>
              {bilateralStartSide === 'left' && (
                <span className="text-[10px] bg-blue-100 text-blue-700 px-1.5 py-0.5 rounded">Source</span>
              )}
              {bilateralStartSide === 'right' && (
                <span className="text-[10px] bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded">Auto-mirrored</span>
              )}
            </div>
            <div className="text-xs text-gray-500">
              Role: {leftJoint.role} | Start: {leftJoint.startPose.min}°-{leftJoint.startPose.max}°
            </div>
          </div>
          <div className={`bg-white rounded-lg p-3 border ${bilateralStartSide === 'right' ? 'ring-2 ring-blue-300' : ''}`}>
            <div className="flex items-center gap-2 mb-1">
              <span className="font-medium text-gray-800">{getJointLabel(rightJoint.joint).en}</span>
              {bilateralStartSide === 'right' && (
                <span className="text-[10px] bg-blue-100 text-blue-700 px-1.5 py-0.5 rounded">Source</span>
              )}
              {bilateralStartSide === 'left' && (
                <span className="text-[10px] bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded">Auto-mirrored</span>
              )}
            </div>
            <div className="text-xs text-gray-500">
              Role: {rightJoint.role} | Start: {rightJoint.startPose.min}°-{rightJoint.startPose.max}°
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// ============================================
// MAIN COMPONENT
// ============================================

export function JointConfigStep() {
  const {
    jointConfig,
    setJointConfig,
    countingMethod,
    bilateralConfig,
  } = useWizardStore();
  const [activeTabIndex, setActiveTabIndex] = useState(0);
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);

  const trackedJoints = jointConfig.trackedJoints || [];
  const isHold = countingMethod.countingMethodCode === 'hold';
  const isBilateralEnabled = Boolean(bilateralConfig.enabled);

  // Build UI tabs from tracked joints
  const uiTabs = useMemo(() => buildUITabs(trackedJoints), [trackedJoints]);

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

    return errors;
  }, [trackedJoints, hasPrimary]);

  // Get available joints (not already added)
  const availableJoints = useMemo(() => {
    const existingCodes = trackedJoints.map(j => j.joint);
    return JOINT_OPTIONS.filter(opt => {
      if (opt.code.startsWith('divider')) return true;
      if (opt.type === 'bilateral') {
        // Hide if both joints already exist
        return !existingCodes.includes(opt.leftJoint!) || !existingCodes.includes(opt.rightJoint!);
      }
      return !existingCodes.includes(opt.code);
    });
  }, [trackedJoints]);

  // Add joint(s)
  const handleAddJoint = useCallback((option: JointOption) => {
    if (option.code.startsWith('divider')) return;

    const newJoints: TrackedJointData[] = [];
    const role = trackedJoints.length === 0 ? 'primary' : 'secondary';

    if (option.type === 'bilateral' && option.leftJoint && option.rightJoint) {
      // Add both joints as a pair
      const leftJoint = buildTrackedJoint(option.leftJoint, role, option.rightJoint, isHold);
      const rightJoint = buildTrackedJoint(option.rightJoint, role, option.leftJoint, isHold);

      // Check if either already exists
      const existingCodes = trackedJoints.map(j => j.joint);
      if (!existingCodes.includes(option.leftJoint)) newJoints.push(leftJoint);
      if (!existingCodes.includes(option.rightJoint)) newJoints.push(rightJoint);
    } else {
      // Single joint
      const newJoint = buildTrackedJoint(option.code, role, undefined, isHold);
      newJoints.push(newJoint);
    }

    if (newJoints.length > 0) {
      setJointConfig({ trackedJoints: [...trackedJoints, ...newJoints] });
      // Switch to the new tab (will be at the end)
      setActiveTabIndex(uiTabs.length);
    }
    setIsDropdownOpen(false);
  }, [trackedJoints, setJointConfig, isHold, uiTabs.length]);

  // Update joint
  const handleUpdateJoint = useCallback((index: number, joint: TrackedJointData) => {
    const newJoints = [...trackedJoints];
    newJoints[index] = joint;
    setJointConfig({ trackedJoints: newJoints });
  }, [trackedJoints, setJointConfig]);

  // Remove single joint
  const handleRemoveJoint = useCallback((index: number) => {
    const newJoints = trackedJoints.filter((_, i) => i !== index);
    setJointConfig({ trackedJoints: newJoints });

    // Adjust active tab (will be recalculated by uiTabs)
    if (activeTabIndex >= uiTabs.length - 1) {
      setActiveTabIndex(Math.max(0, uiTabs.length - 2));
    }
  }, [trackedJoints, setJointConfig, activeTabIndex, uiTabs.length]);

  // Update both bilateral joints at once
  const handleUpdateBilateral = useCallback((leftIndex: number, rightIndex: number, updater: (joint: TrackedJointData) => TrackedJointData) => {
    const newJoints = [...trackedJoints];
    newJoints[leftIndex] = updater(newJoints[leftIndex]);
    newJoints[rightIndex] = updater(newJoints[rightIndex]);
    setJointConfig({ trackedJoints: newJoints });
  }, [trackedJoints, setJointConfig]);

  // Remove bilateral pair
  const handleRemoveBilateral = useCallback((leftIndex: number, rightIndex: number) => {
    const indicesToRemove = new Set([leftIndex, rightIndex]);
    const newJoints = trackedJoints.filter((_, i) => !indicesToRemove.has(i));
    setJointConfig({ trackedJoints: newJoints });

    // Adjust active tab
    if (activeTabIndex >= uiTabs.length - 1) {
      setActiveTabIndex(Math.max(0, uiTabs.length - 2));
    }
  }, [trackedJoints, setJointConfig, activeTabIndex, uiTabs.length]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Joint Configuration</h2>
        <p className="text-gray-500">
          Add joints to track and configure their angle ranges.
          {isHold ? ' For hold exercises, angles are checked continuously.' : ' Primary joints are used for rep counting.'}
        </p>
      </div>

      {isBilateralEnabled && (
        <div className="bg-indigo-50 border border-indigo-200 rounded-lg p-4">
          <h4 className="font-semibold text-indigo-900">Bilateral Mode Active</h4>
          <p className="text-sm text-indigo-700 mt-1">
            Configure joints for the <strong>{bilateralConfig.startSide}</strong> side only.
            The opposite side will be created automatically when you save.
            Shared joints (spine, neck, nose) will be used on both sides.
          </p>
        </div>
      )}

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

      {/* Add Joint Button */}
      <div className="relative">
        <button
          type="button"
          onClick={() => setIsDropdownOpen(!isDropdownOpen)}
          className="flex items-center gap-2 px-4 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors shadow-md"
        >
          <Plus className="w-5 h-5" />
          Add Joint
          <ChevronDown className={`w-4 h-4 transition-transform ${isDropdownOpen ? 'rotate-180' : ''}`} />
        </button>

        {/* Dropdown */}
        {isDropdownOpen && (
          <>
            <div
              className="fixed inset-0 z-10"
              onClick={() => setIsDropdownOpen(false)}
            />
            <div className="absolute top-full left-0 mt-2 w-72 bg-white border border-gray-200 rounded-xl shadow-xl z-20 max-h-96 overflow-y-auto">
              {availableJoints.map((option) => {
                if (option.code.startsWith('divider')) {
                  return (
                    <div key={option.code} className="px-4 py-2 text-xs text-gray-400 bg-gray-50 font-medium">
                      {option.label.en}
                    </div>
                  );
                }

                return (
                  <button
                    key={option.code}
                    type="button"
                    onClick={() => handleAddJoint(option)}
                    className="w-full px-4 py-3 text-left hover:bg-blue-50 transition-colors flex items-center gap-3"
                  >
                    <span className={`w-8 h-8 rounded-full flex items-center justify-center text-sm ${option.type === 'bilateral'
                      ? 'bg-purple-100 text-purple-600'
                      : 'bg-gray-100 text-gray-600'
                      }`}>
                      {option.type === 'bilateral' ? '⟷' : '•'}
                    </span>
                    <div>
                      <div className="font-medium text-gray-800">{option.label.en}</div>
                      <div className="text-xs text-gray-500">{option.label.ar}</div>
                    </div>
                    {option.type === 'bilateral' && (
                      <span className="ml-auto text-xs bg-purple-100 text-purple-600 px-2 py-0.5 rounded-full">
                        Pair
                      </span>
                    )}
                  </button>
                );
              })}

              {availableJoints.filter(o => !o.code.startsWith('divider')).length === 0 && (
                <div className="px-4 py-6 text-center text-gray-500">
                  All joints added
                </div>
              )}
            </div>
          </>
        )}
      </div>

      {/* Joint Tabs */}
      {uiTabs.length > 0 && (
        <div className="bg-white rounded-xl border shadow-sm overflow-hidden">
          {/* Tab Headers */}
          <div className="flex border-b overflow-x-auto">
            {uiTabs.map((tab, index) => {
              const isActive = activeTabIndex === index;
              const isBilateral = tab.type === 'bilateral';

              // Get role from the first joint
              const firstJointIndex = tab.type === 'bilateral' ? tab.leftJointIndex! : tab.singleJointIndex!;
              const firstJoint = trackedJoints[firstJointIndex];
              const isPrimary = firstJoint?.role === 'primary';

              return (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => setActiveTabIndex(index)}
                  className={`flex-shrink-0 px-4 py-3 text-sm font-medium transition-colors border-b-2 ${isActive
                    ? isBilateral
                      ? 'border-purple-500 bg-purple-50 text-purple-700'
                      : isPrimary
                        ? 'border-blue-500 bg-blue-50 text-blue-700'
                        : 'border-gray-500 bg-gray-50 text-gray-700'
                    : 'border-transparent hover:bg-gray-50 text-gray-600'
                    }`}
                >
                  <span className="flex items-center gap-2">
                    {isBilateral ? (
                      <span className="text-purple-500">⟷</span>
                    ) : (
                      <span className={`w-2 h-2 rounded-full ${isPrimary ? 'bg-blue-500' : 'bg-gray-400'}`} />
                    )}
                    {tab.label.en}
                    {firstJoint?.invertIndicator && <span className="text-yellow-600">↕</span>}
                  </span>
                </button>
              );
            })}
          </div>

          {/* Tab Content */}
          {uiTabs[activeTabIndex] && (() => {
            const tab = uiTabs[activeTabIndex];

            if (tab.type === 'bilateral' && tab.leftJointIndex !== undefined && tab.rightJointIndex !== undefined) {
              const leftJoint = trackedJoints[tab.leftJointIndex];
              const rightJoint = trackedJoints[tab.rightJointIndex];

              return (
                <BilateralTabContent
                  leftJoint={leftJoint}
                  rightJoint={rightJoint}
                  bilateralCode={tab.id}
                  onUpdateBoth={(updater) => handleUpdateBilateral(tab.leftJointIndex!, tab.rightJointIndex!, updater)}
                  onRemove={() => handleRemoveBilateral(tab.leftJointIndex!, tab.rightJointIndex!)}
                  isHold={isHold}
                  bilateralStartSide={isBilateralEnabled ? bilateralConfig.startSide : undefined}
                />
              );
            } else if (tab.singleJointIndex !== undefined) {
              const joint = trackedJoints[tab.singleJointIndex];

              return (
                <JointTabContent
                  joint={joint}
                  onUpdate={(j) => handleUpdateJoint(tab.singleJointIndex!, j)}
                  onRemove={() => handleRemoveJoint(tab.singleJointIndex!)}
                  isHold={isHold}
                  hasMirror={false}
                />
              );
            }

            return null;
          })()}
        </div>
      )}

      {/* Empty State */}
      {uiTabs.length === 0 && (
        <div className="bg-gray-50 rounded-xl p-12 text-center border-2 border-dashed border-gray-300">
          <div className="text-4xl mb-4">🦴</div>
          <p className="text-gray-600 text-lg mb-2">No joints added yet</p>
          <p className="text-sm text-gray-400">Click &quot;Add Joint&quot; to start configuring tracked joints</p>
        </div>
      )}

      {/* Help */}
      <div className="bg-gradient-to-r from-blue-50 to-indigo-50 rounded-xl p-4 border border-blue-100">
        <h4 className="font-semibold text-blue-800 mb-2">💡 Tips</h4>
        <ul className="text-sm text-blue-700 space-y-1">
          <li>• <strong>Bilateral joints</strong> (e.g., Knees) add both left and right with linked settings</li>
          <li>• <strong>Primary joints</strong> are used for rep counting, secondary for form feedback</li>
          <li>• <strong>Invert Indicator</strong>: Use for joints where lower angle is the goal (like elbow flexion)</li>
          {!isHold && <li>• Make sure <strong>upRange min &gt; downRange max</strong> for valid transition</li>}
        </ul>
      </div>
    </div>
  );
}

export default JointConfigStep;
