'use client';

/**
 * Step 5: Position Checks (Optional)
 * ==================================
 * 
 * Position-based validation with single threshold (no difficulty levels).
 */

import { useState } from 'react';
import { useWizardStore } from '../WizardContext';
import { Card, CardHeader, CardTitle, CardContent, Button, Badge, Input, Label } from '@/components/ui';
import { SmartLocalizedInput } from '@/components/forms';
import { MessagePickerModal, MessageAudioStatus, type MessageOption } from '@/components/messages';
import type { PositionCheckData } from '@/modules/exercises/exercises.validation';
import type { PhaseName, PositionCheckType, ConditionOperator, LocalizedAudio } from '@/lib/types/localized';
import { Library, Plus } from 'lucide-react';

// ============================================
// TEMPLATES
// ============================================

const TEMPLATES: Array<{
  id: string;
  name: string;
  description: string;
  data: Omit<PositionCheckData, 'checkId'>;
}> = [
    {
      id: 'knee_over_toe',
      name: 'Knee Over Toe',
      description: 'Knees don\'t go past toes (squats)',
      data: {
        type: 'forward_comparison',
        landmarks: { primary: 'left_knee', secondary: 'left_foot_index' },
        condition: { operator: 'should_not_exceed', threshold: 0.05 },
        activePhases: ['down', 'bottom'] as PhaseName[],
        errorMessage: { ar: 'لا تدع ركبتك تتجاوز أصابع قدميك', en: 'Don\'t let your knee go past your toes', audioAr: undefined, audioEn: undefined },
        severity: 'warning',
        cooldownMs: 2000,
        minErrorFrames: 3,
      },
    },
    {
      id: 'back_straight',
      name: 'Back Straight',
      description: 'Keep back aligned (form check)',
      data: {
        type: 'vertical_alignment',
        landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
        condition: { operator: 'approximately_equal', threshold: 0.08 },
        activePhases: ['all'] as PhaseName[],
        errorMessage: { ar: 'حافظ على استقامة ظهرك', en: 'Keep your back straight', audioAr: undefined, audioEn: undefined },
        severity: 'warning',
        cooldownMs: 2000,
        minErrorFrames: 4,
      },
    },
    {
      id: 'hip_hinge',
      name: 'Hip Hinge',
      description: 'Proper hip hinge pattern',
      data: {
        type: 'forward_comparison',
        landmarks: { primary: 'left_hip', secondary: 'left_knee' },
        condition: { operator: 'should_exceed', threshold: 0.03 },
        activePhases: ['down', 'bottom'] as PhaseName[],
        errorMessage: { ar: 'ادفع وركك للخلف', en: 'Push your hips back', audioAr: undefined, audioEn: undefined },
        severity: 'tip',
        cooldownMs: 3000,
        minErrorFrames: 5,
      },
    },
    {
      id: 'elbow_stable',
      name: 'Elbow Stable',
      description: 'Elbow stays close to body (curls)',
      data: {
        type: 'sideways_comparison',
        landmarks: { primary: 'left_elbow', secondary: 'left_hip' },
        condition: { operator: 'approximately_equal', threshold: 0.08 },
        activePhases: ['up', 'down'],
        errorMessage: { ar: 'ثبّت مرفقك بجانب جسمك', en: 'Keep your elbow close to your body', audioAr: undefined, audioEn: undefined },
        severity: 'warning',
        cooldownMs: 2000,
        minErrorFrames: 4,
      },
    },
  ];

const POSITION_CHECK_TYPES: Array<{ value: PositionCheckType; label: string; description: string }> = [
  { value: 'forward_comparison', label: 'Forward Comparison', description: 'Compare positions along forward axis (X in side view, Z in front view). E.g. knee-over-toe.' },
  { value: 'vertical_comparison', label: 'Vertical Comparison', description: 'Compare heights (Y axis): is one point above/below another? E.g. hands above shoulders.' },
  { value: 'sideways_comparison', label: 'Sideways Comparison', description: 'Compare lateral positions (Z in side view, X in front view). E.g. elbow staying close to torso.' },
  { value: 'distance_ratio', label: 'Distance Ratio', description: 'Compare the ratio of two distances. Requires 4 landmarks. E.g. stance width vs shoulder width.' },
  { value: 'horizontal_alignment', label: 'Horizontal Alignment', description: 'Check if points are on the same horizontal line (similar Y). E.g. shoulders level.' },
  { value: 'vertical_alignment', label: 'Vertical Alignment', description: 'Check if points are on the same vertical line (similar X). E.g. wrist over elbow in plank.' },
  { value: 'depth_alignment', label: 'Depth Alignment', description: 'Check if points are at the same depth from camera (similar Z). Advanced use.' },
];

const OPERATORS: Array<{ value: ConditionOperator; label: string; description: string }> = [
  { value: 'should_not_exceed', label: 'Should Not Exceed', description: 'Primary must NOT go past secondary by more than threshold' },
  { value: 'should_exceed', label: 'Should Exceed', description: 'Primary MUST go past secondary by at least threshold' },
  { value: 'approximately_equal', label: 'Approximately Equal', description: 'Difference between points must be less than threshold' },
  { value: 'greater_than_ratio', label: 'Greater Than Ratio', description: 'Distance ratio must be greater than threshold (for Distance Ratio type)' },
  { value: 'less_than_ratio', label: 'Less Than Ratio', description: 'Distance ratio must be less than threshold (for Distance Ratio type)' },
];

const PHASE_OPTIONS: PhaseName[] = ['all', 'top', 'down', 'bottom', 'up'];

const AVAILABLE_LANDMARKS = [
  'left_knee', 'right_knee', 'left_hip', 'right_hip',
  'left_shoulder', 'right_shoulder', 'left_ankle', 'right_ankle',
  'left_elbow', 'right_elbow', 'left_wrist', 'right_wrist',
  'left_foot_index', 'right_foot_index', 'nose', 'neck', 'spine',
];

// ============================================
// POSITION CHECK CARD
// ============================================

interface PositionCheckCardProps {
  check: PositionCheckData;
  index: number;
  onUpdate: (index: number, check: PositionCheckData) => void;
  onRemove: (index: number) => void;
  isHoldExercise?: boolean;
}

function PositionCheckCard({ check, index, onUpdate, onRemove, isHoldExercise }: PositionCheckCardProps) {
  const [expanded, setExpanded] = useState(false);
  const [messagePickerOpen, setMessagePickerOpen] = useState(false);

  const severityColors = {
    error: 'bg-red-100 border-red-400 text-red-700',
    warning: 'bg-yellow-100 border-yellow-400 text-yellow-700',
    tip: 'bg-blue-100 border-blue-400 text-blue-700',
  };

  const updateField = <K extends keyof PositionCheckData>(field: K, value: PositionCheckData[K]) => {
    onUpdate(index, { ...check, [field]: value });
  };

  const handleMessageSelect = (messages: MessageOption[]) => {
    if (messages.length === 0) return;
    const msg = messages[0];
    onUpdate(index, {
      ...check,
      messageId: msg.id,
      errorMessage: {
        ar: msg.content.ar || '',
        en: msg.content.en || '',
        audioAr: msg.content.audioAr,
        audioEn: msg.content.audioEn,
        sourceMessageCode: msg.code,
        sourceMessageId: msg.id,
      },
    });
  };

  const unlinkLibraryMessage = () => {
    const em = check.errorMessage;
    updateField('messageId', undefined);
    updateField('errorMessage', {
      ar: em?.ar ?? '',
      en: em?.en ?? '',
      audioAr: em?.audioAr,
      audioEn: em?.audioEn,
    });
  };

  const clearErrorMessage = () => {
    updateField('errorMessage', { ar: '', en: '', audioAr: undefined, audioEn: undefined });
    updateField('messageId', undefined);
  };

  const isLinked = !!check.messageId;

  return (
    <Card className={`border-l-4 ${check.severity === 'error' ? 'border-l-red-500' : check.severity === 'warning' ? 'border-l-yellow-500' : 'border-l-blue-500'}`}>
      <CardHeader className="py-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className={`px-2 py-1 rounded text-xs font-bold uppercase ${severityColors[check.severity]}`}>
              {check.severity}
            </span>
            <div>
              <h4 className="font-medium text-gray-900">
                {check.checkId.replace(/_\d+$/, '').replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase())}
              </h4>
              <p className="text-sm text-gray-500">
                {check.type.replace(/_/g, ' ')} • threshold: {check.condition.threshold}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={() => setExpanded(!expanded)}
              className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
            >
              <svg
                className={`w-5 h-5 text-gray-500 transition-transform ${expanded ? 'rotate-180' : ''}`}
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            </button>
            <button
              type="button"
              onClick={() => onRemove(index)}
              className="p-2 hover:bg-red-50 text-red-500 rounded-lg transition-colors"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>
      </CardHeader>

      {expanded && (
        <CardContent className="pt-0 space-y-4">
          {/* Type & Operator */}
          <div className="grid md:grid-cols-2 gap-4">
            <div>
              <Label>Check Type</Label>
              <select
                value={check.type}
                onChange={(e) => updateField('type', e.target.value as PositionCheckType)}
                className="w-full mt-1 px-3 py-2 text-gray-900 border rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              >
                {POSITION_CHECK_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>{t.label}</option>
                ))}
              </select>
            </div>
            <div>
              <Label>Operator</Label>
              <select
                value={check.condition.operator}
                onChange={(e) => updateField('condition', { ...check.condition, operator: e.target.value as ConditionOperator })}
                className="w-full mt-1 px-3 py-2 text-gray-900 border rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              >
                {OPERATORS.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </div>
          </div>

          {/* Threshold */}
          <div>
            <Label>Threshold</Label>
            <div className="flex items-center gap-2 mt-1">
              <Input
                type="number"
                step={0.01}
                min={0}
                max={1}
                value={check.condition.threshold}
                onChange={(e) => updateField('condition', { ...check.condition, threshold: Number(e.target.value) })}
                className="w-32"
              />
              <span className="text-sm text-gray-500">(0-1 normalized value)</span>
            </div>
          </div>

          {/* Landmarks */}
          <div className="grid md:grid-cols-2 gap-4">
            <div>
              <Label>Primary Landmark</Label>
              <select
                value={check.landmarks.primary}
                onChange={(e) => updateField('landmarks', { ...check.landmarks, primary: e.target.value })}
                className="w-full mt-1 px-3 py-2 text-gray-900 border rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              >
                {AVAILABLE_LANDMARKS.map((l) => (
                  <option key={l} value={l}>{l.replace(/_/g, ' ')}</option>
                ))}
              </select>
            </div>
            <div>
              <Label>Secondary Landmark</Label>
              <select
                value={check.landmarks.secondary}
                onChange={(e) => updateField('landmarks', { ...check.landmarks, secondary: e.target.value })}
                className="w-full mt-1 px-3 py-2 text-gray-900 border rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              >
                {AVAILABLE_LANDMARKS.map((l) => (
                  <option key={l} value={l}>{l.replace(/_/g, ' ')}</option>
                ))}
              </select>
            </div>
            {/* Tertiary & Quaternary Landmarks (Distance Ratio only) */}
            {check.type === 'distance_ratio' && (
              <>
                <div>
                  <Label>Tertiary Landmark</Label>
                  <select
                    value={check.landmarks.tertiary || ''}
                    onChange={(e) => updateField('landmarks', { ...check.landmarks, tertiary: e.target.value })}
                    className="w-full mt-1 px-3 py-2 text-gray-900 border rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  >
                    <option value="">Select landmark...</option>
                    {AVAILABLE_LANDMARKS.map((l) => (
                      <option key={l} value={l}>{l.replace(/_/g, ' ')}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <Label>Quaternary Landmark</Label>
                  <select
                    value={check.landmarks.quaternary || ''}
                    onChange={(e) => updateField('landmarks', { ...check.landmarks, quaternary: e.target.value })}
                    className="w-full mt-1 px-3 py-2 text-gray-900 border rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  >
                    <option value="">Select landmark...</option>
                    {AVAILABLE_LANDMARKS.map((l) => (
                      <option key={l} value={l}>{l.replace(/_/g, ' ')}</option>
                    ))}
                  </select>
                </div>
              </>
            )}
          </div>

          {/* Active Phases (hidden for hold exercises - always "all") */}
          {isHoldExercise ? (
            <div>
              <Label>Active Phases</Label>
              <p className="text-sm text-gray-500 mt-1">
                Hold exercises apply checks in all phases automatically.
              </p>
            </div>
          ) : (
            <div>
              <Label>Active Phases</Label>
              <div className="flex flex-wrap gap-2 mt-2">
                {PHASE_OPTIONS.map((phase) => {
                  const isActive = check.activePhases.includes(phase);
                  return (
                    <button
                      key={phase}
                      type="button"
                      onClick={() => {
                        const newPhases = isActive
                          ? check.activePhases.filter(p => p !== phase)
                          : [...check.activePhases, phase];
                        updateField('activePhases', newPhases);
                      }}
                      className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${isActive
                        ? 'bg-blue-500 text-white'
                        : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                        }`}
                    >
                      {phase}
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* Error Messages */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Label>Error Message</Label>
                {isLinked && (
                  <Badge variant="outline" className="bg-blue-50 text-blue-700 border-blue-200 flex items-center gap-1">
                    <Library className="h-3 w-3" />
                    Linked to Library
                  </Badge>
                )}
              </div>
              <div className="flex items-center gap-2">
                {!isLinked ? (
                  <Button
                    variant="ghost"
                    size="sm"
                    icon={<Library className="h-4 w-4" />}
                    onClick={() => setMessagePickerOpen(true)}
                  >
                    From Library
                  </Button>
                ) : (
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-red-600 hover:text-red-700 hover:bg-red-50"
                    onClick={unlinkLibraryMessage}
                  >
                    Unlink
                  </Button>
                )}
                <Button
                  variant="ghost"
                  size="sm"
                  icon={<Plus className="h-4 w-4" />}
                  onClick={clearErrorMessage}
                >
                  Clear
                </Button>
              </div>
            </div>

            {(isLinked || check.errorMessage?.audioAr || check.errorMessage?.audioEn) && (
              <MessageAudioStatus
                audioAr={check.errorMessage?.audioAr}
                audioEn={check.errorMessage?.audioEn}
                sourceMessageCode={
                  isLinked
                    ? check.errorMessage?.sourceMessageCode?.trim() || undefined
                    : undefined
                }
                compact
                className="mt-1"
              />
            )}

            {/* Show readonly view if linked, otherwise editable input */}
            <SmartLocalizedInput
              label=""
              value={check.errorMessage || { ar: '', en: '' }}
              onChange={(value) => !isLinked && updateField('errorMessage', value)}
              audioValue={{
                ar: check.errorMessage?.audioAr,
                en: check.errorMessage?.audioEn,
              }}
              onAudioChange={(audio) => !isLinked && updateField('errorMessage', {
                ...(check.errorMessage || { ar: '', en: '' }),
                audioAr: audio.ar,
                audioEn: audio.en,
              })}
              enableTranslation={!isLinked}
              enableTTS={!isLinked}
              readOnly={isLinked}
              translationContext="fitness exercise form correction message"
              variant="compact"
              className={isLinked ? "opacity-75 bg-gray-50" : ""}
            />
            {isLinked && (
              <p className="text-xs text-blue-600 flex items-center gap-1">
                <Library className="h-3 w-3" />
                This message is managed in the Message Library. Unlink to edit locally.
              </p>
            )}
          </div>

          <MessagePickerModal
            open={messagePickerOpen}
            onOpenChange={setMessagePickerOpen}
            onSelect={handleMessageSelect}
            categoryFilter="position"
            contextFilter="error"
            title="Pick Error Message"
            description="Choose a message from the library for this check."
            createDefaults={{ category: 'position', context: 'error' }}
          />

          {/* Severity & Timing */}
          <div className="grid md:grid-cols-3 gap-4">
            <div>
              <Label>Severity</Label>
              <select
                value={check.severity}
                onChange={(e) => updateField('severity', e.target.value as 'error' | 'warning' | 'tip')}
                className="w-full mt-1 px-3 py-2 text-gray-900 border rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              >
                <option value="error">Error (blocks rep)</option>
                <option value="warning">Warning (feedback)</option>
                <option value="tip">Tip (suggestion)</option>
              </select>
            </div>
            <div>
              <Label>Cooldown (ms)</Label>
              <Input
                type="number"
                min={0}
                step={100}
                value={check.cooldownMs}
                onChange={(e) => updateField('cooldownMs', Number(e.target.value))}
              />
            </div>
            <div>
              <Label>Min Error Frames</Label>
              <Input
                type="number"
                min={1}
                value={check.minErrorFrames}
                onChange={(e) => updateField('minErrorFrames', Number(e.target.value))}
              />
            </div>
          </div>
        </CardContent>
      )
      }
    </Card >
  );
}

// ============================================
// BILATERAL AUTO-MIRROR HELPERS
// ============================================

/**
 * Check if a landmark code is side-specific (left_ or right_ prefix)
 */
function isSidedLandmark(code: string): boolean {
  return code.startsWith('left_') || code.startsWith('right_');
}

/**
 * Mirror a single landmark code: left_ <-> right_
 */
function mirrorLandmark(code: string): string {
  if (code.startsWith('left_')) return code.replace('left_', 'right_');
  if (code.startsWith('right_')) return code.replace('right_', 'left_');
  return code;
}

/**
 * Check if a position check has any sided landmarks that need mirroring
 */
function hasSidedLandmarks(check: PositionCheckData): boolean {
  const { primary, secondary, tertiary, quaternary } = check.landmarks;
  return [primary, secondary, tertiary, quaternary].some(
    (l) => l !== undefined && isSidedLandmark(l)
  );
}

/**
 * Create a mirrored copy of a position check (swap left_ <-> right_ in all landmarks)
 */
function mirrorPositionCheck(check: PositionCheckData): PositionCheckData {
  return {
    ...check,
    checkId: `${check.checkId}_mirror`,
    landmarks: {
      primary: mirrorLandmark(check.landmarks.primary),
      secondary: mirrorLandmark(check.landmarks.secondary),
      tertiary: check.landmarks.tertiary ? mirrorLandmark(check.landmarks.tertiary) : undefined,
      quaternary: check.landmarks.quaternary ? mirrorLandmark(check.landmarks.quaternary) : undefined,
    },
  };
}

/**
 * Check if a checkId is an auto-mirrored check
 */
function isMirroredCheck(checkId: string): boolean {
  return checkId.endsWith('_mirror');
}

// ============================================
// MAIN COMPONENT
// ============================================

export function PositionChecksStep() {
  const { positionChecks, setPositionChecks, bilateralConfig, countingMethod } = useWizardStore();

  const allChecks = positionChecks.positionChecks || [];
  const isBilateralEnabled = Boolean(bilateralConfig.enabled);
  const isHoldExercise = countingMethod.countingMethodCode === 'hold';

  // Separate source checks from auto-mirrored checks
  const sourceChecks = allChecks.filter((c) => !isMirroredCheck(c.checkId));
  const mirroredChecks = allChecks.filter((c) => isMirroredCheck(c.checkId));

  /**
   * Rebuild all checks: source checks + auto-generated mirrors
   */
  const rebuildChecks = (newSourceChecks: PositionCheckData[]) => {
    if (!isBilateralEnabled) {
      setPositionChecks({ positionChecks: newSourceChecks });
      return;
    }

    // Auto-generate mirrors for sided checks
    const mirrors: PositionCheckData[] = [];
    for (const check of newSourceChecks) {
      if (hasSidedLandmarks(check)) {
        mirrors.push(mirrorPositionCheck(check));
      }
    }

    setPositionChecks({ positionChecks: [...newSourceChecks, ...mirrors] });
  };

  const addFromTemplate = (template: typeof TEMPLATES[number]) => {
    const check: PositionCheckData = {
      ...template.data,
      checkId: `${template.id}_${Date.now()}`,
      activePhases: isHoldExercise ? ['all'] : template.data.activePhases,
    };
    rebuildChecks([...sourceChecks, check]);
  };

  const addCustomCheck = () => {
    const check: PositionCheckData = {
      checkId: `custom_${Date.now()}`,
      type: 'vertical_alignment',
      landmarks: { primary: 'left_shoulder', secondary: 'left_hip' },
      condition: { operator: 'approximately_equal', threshold: 0.1 },
      activePhases: isHoldExercise ? ['all'] : ['down', 'bottom'],
      errorMessage: { ar: 'تحقق من وضعك', en: 'Check your position', audioAr: undefined, audioEn: undefined },
      severity: 'warning',
      cooldownMs: 2000,
      minErrorFrames: 3,
    };
    rebuildChecks([...sourceChecks, check]);
  };

  const handleUpdateCheck = (index: number, check: PositionCheckData) => {
    const newSourceChecks = [...sourceChecks];
    newSourceChecks[index] = check;
    rebuildChecks(newSourceChecks);
  };

  const handleRemoveCheck = (index: number) => {
    const newSourceChecks = sourceChecks.filter((_, i) => i !== index);
    rebuildChecks(newSourceChecks);
  };

  const handleClearAll = () => {
    rebuildChecks([]);
  };

  const totalChecks = isBilateralEnabled
    ? sourceChecks.length + mirroredChecks.length
    : sourceChecks.length;

  return (
    <div className="space-y-6">
      <div>
        <div className="flex items-center gap-2 mb-2">
          <h2 className="text-2xl font-bold text-gray-900">Position Checks</h2>
          <Badge variant="outline">Optional</Badge>
        </div>
        <p className="text-gray-500">
          Add position-based validations for real-time form feedback.
        </p>
      </div>

      {/* Bilateral Info */}
      {isBilateralEnabled && (
        <div className="bg-indigo-50 border border-indigo-200 rounded-xl p-4 flex items-start gap-3">
          <span className="text-2xl">⟷</span>
          <div className="text-sm text-indigo-700">
            <p className="font-medium mb-1">Bilateral Auto-Mirroring Active</p>
            <p>
              Position checks with sided landmarks (left/right) will automatically generate a mirrored
              check for the opposite side. Mirrored checks are shown below as read-only.
            </p>
          </div>
        </div>
      )}

      {/* Info */}
      <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 flex items-start gap-3">
        <span className="text-2xl">💡</span>
        <div className="text-sm text-blue-700">
          <p className="font-medium mb-1">Simplified Thresholds</p>
          <p>
            Position checks now use a single threshold value instead of per-difficulty thresholds.
            The check runs the same for all users, providing consistent form feedback.
          </p>
        </div>
      </div>

      {/* Quick Templates */}
      <div>
        <Label className="mb-3 block">Quick Templates</Label>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          {TEMPLATES.map((template) => (
            <button
              key={template.id}
              type="button"
              onClick={() => addFromTemplate(template)}
              className="p-3 text-left border rounded-xl hover:border-blue-400 hover:bg-blue-50 transition-colors"
            >
              <p className="font-medium text-gray-900 text-sm">{template.name}</p>
              <p className="text-xs text-gray-500 mt-1">{template.description}</p>
            </button>
          ))}
        </div>
      </div>

      {/* Source Checks */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <Label>Configured Checks ({totalChecks})</Label>
          {sourceChecks.length > 0 && (
            <button
              type="button"
              onClick={handleClearAll}
              className="text-xs text-red-600 hover:text-red-700"
            >
              Clear All
            </button>
          )}
        </div>

        {sourceChecks.length === 0 ? (
          <Card className="p-8 text-center border-dashed">
            <p className="text-gray-500">No position checks added</p>
            <p className="text-sm text-gray-400 mt-1">Use templates above or add a custom check</p>
          </Card>
        ) : (
          <div className="space-y-3">
            {sourceChecks.map((check, index) => (
              <PositionCheckCard
                key={check.checkId}
                check={check}
                index={index}
                onUpdate={handleUpdateCheck}
                onRemove={handleRemoveCheck}
                isHoldExercise={isHoldExercise}
              />
            ))}
          </div>
        )}
      </div>

      {/* Auto-Mirrored Checks (read-only) */}
      {isBilateralEnabled && mirroredChecks.length > 0 && (
        <div className="space-y-3">
          <div className="flex items-center gap-2">
            <Label>Auto-Mirrored Checks ({mirroredChecks.length})</Label>
            <span className="text-xs bg-gray-100 text-gray-500 px-2 py-0.5 rounded">Read-only</span>
          </div>
          <div className="space-y-3 opacity-75">
            {mirroredChecks.map((check) => (
              <Card
                key={check.checkId}
                className="border-l-4 border-l-gray-300 p-4"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="text-xs bg-indigo-100 text-indigo-700 px-2 py-0.5 rounded-full font-medium">
                      Auto-mirrored
                    </span>
                    <span className="text-sm font-medium text-gray-700">
                      {check.checkId.replace(/_mirror$/, '').replace(/_\d+$/, '').replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase())}
                    </span>
                  </div>
                  <span className={`px-2 py-1 rounded text-xs font-bold uppercase ${
                    check.severity === 'error' ? 'bg-red-100 text-red-700' :
                    check.severity === 'warning' ? 'bg-yellow-100 text-yellow-700' :
                    'bg-blue-100 text-blue-700'
                  }`}>
                    {check.severity}
                  </span>
                </div>
                <div className="mt-2 text-xs text-gray-500">
                  {check.type.replace(/_/g, ' ')} | {check.landmarks.primary} vs {check.landmarks.secondary}
                  {check.landmarks.tertiary && ` vs ${check.landmarks.tertiary}`}
                  {check.landmarks.quaternary && ` vs ${check.landmarks.quaternary}`}
                </div>
              </Card>
            ))}
          </div>
        </div>
      )}

      {/* Add Custom */}
      <Button
        variant="outline"
        className="w-full border-dashed"
        onClick={addCustomCheck}
      >
        + Add Custom Position Check
      </Button>
    </div>
  );
}

export default PositionChecksStep;


