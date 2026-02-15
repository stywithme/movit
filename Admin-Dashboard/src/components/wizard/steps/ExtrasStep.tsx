'use client';

/**
 * Step 6: Extras (Attributes + Feedback Messages + Weight & Metrics)
 * ===================================================================
 */

import { useState } from 'react';
import { useWizardStore } from '../WizardContext';
import { Card, CardHeader, CardTitle, CardContent, Button, Badge, Label, Input } from '@/components/ui';
import { MessagePickerModal, MessageFormModal, type MessageOption, type MessageFormData } from '@/components/messages';
import { Plus, X, Dumbbell, BarChart3, Check, Library } from 'lucide-react';
import type { FeedbackAssignmentData } from '@/modules/exercises/exercises.validation';
import { METRIC_DEFINITIONS, type MetricCode } from '@/modules/exercises/exercises.types';

interface ExtrasStepProps {
  muscles: Array<{ id: string; code: string; name: { ar: string; en: string } }>;
  equipment: Array<{ id: string; code: string; name: { ar: string; en: string } }>;
  tags: Array<{ id: string; code: string; name: { ar: string; en: string } }>;
}

export function ExtrasStep({ muscles, equipment, tags }: ExtrasStepProps) {
  const { 
    extras, 
    setExtras, 
    addFeedbackAssignment, 
    updateFeedbackAssignment, 
    removeFeedbackAssignment,
    weightConfig,
    setWeightConfig,
    reportMetrics,
    setReportMetrics,
    countingMethod,
    jointConfig,
    positionChecks,
    bilateralConfig,
    setBilateralConfig,
  } = useWizardStore();
  
  // Determine exercise type for auto-suggestions
  const isHold = countingMethod.countingMethodCode === 'hold';
  const hasPairedJoints = (jointConfig.trackedJoints || []).some((j) => j.pairedWith);
  const isBilateral = bilateralConfig.enabled || hasPairedJoints;
  const hasPositionChecks = (positionChecks.positionChecks || []).length > 0;
  
  const toggleAttribute = (type: 'muscles' | 'equipment' | 'tags', id: string) => {
    const current = extras[type] || [];
    const updated = current.includes(id)
      ? current.filter(i => i !== id)
      : [...current, id];
    setExtras({ [type]: updated });
  };
  
  const feedbackAssignments = extras.feedbackAssignments || [];
  
  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Attributes & Feedback</h2>
        <p className="text-gray-500">Add metadata and user feedback messages.</p>
      </div>
      
      {/* Attributes Section */}
      <div className="grid gap-6">
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <CardTitle>Target Muscles</CardTitle>
              <Label tooltip="Select the primary muscles worked by this exercise." />
            </div>
          </CardHeader>
          <CardContent>
            <div className="flex flex-wrap gap-2">
              {muscles.map((muscle) => {
                const isSelected = (extras.muscles || []).includes(muscle.id);
                return (
                  <Badge
                    key={muscle.id}
                    variant={isSelected ? 'purple' : 'default'}
                    className={`cursor-pointer px-3 py-1.5 ${!isSelected && 'hover:bg-gray-200'}`}
                    onClick={() => toggleAttribute('muscles', muscle.id)}
                  >
                    {isSelected && '✓ '}{muscle.name.en}
                  </Badge>
                );
              })}
            </div>
          </CardContent>
        </Card>
        
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <CardTitle>Equipment</CardTitle>
              <Label tooltip="Select any equipment required to perform this exercise." />
            </div>
          </CardHeader>
          <CardContent>
            <div className="flex flex-wrap gap-2">
              {equipment.map((eq) => {
                const isSelected = (extras.equipment || []).includes(eq.id);
                return (
                  <Badge
                    key={eq.id}
                    variant={isSelected ? 'orange' : 'default'}
                    className={`cursor-pointer px-3 py-1.5 ${!isSelected && 'hover:bg-gray-200'}`}
                    onClick={() => toggleAttribute('equipment', eq.id)}
                  >
                    {isSelected && '✓ '}{eq.name.en}
                  </Badge>
                );
              })}
            </div>
          </CardContent>
        </Card>
        
        {tags.length > 0 && (
          <Card>
            <CardHeader>
              <div className="flex items-center gap-2">
                <CardTitle>Tags</CardTitle>
                <Label tooltip="Add tags to help users filter and find this exercise." />
              </div>
            </CardHeader>
            <CardContent>
              <div className="flex flex-wrap gap-2">
                {tags.map((tag) => {
                  const isSelected = (extras.tags || []).includes(tag.id);
                  return (
                    <Badge
                      key={tag.id}
                      variant={isSelected ? 'teal' : 'default'}
                      className={`cursor-pointer px-3 py-1.5 ${!isSelected && 'hover:bg-gray-200'}`}
                      onClick={() => toggleAttribute('tags', tag.id)}
                    >
                      {isSelected && '✓ '}{tag.name.en}
                    </Badge>
                  );
                })}
              </div>
            </CardContent>
          </Card>
        )}
      </div>
      
      {/* Weight Configuration */}
      <div className="space-y-4 pt-6 border-t">
        <div className="flex items-center gap-2">
          <Dumbbell className="h-5 w-5 text-orange-500" />
          <h3 className="text-xl font-bold text-gray-900">Weight Configuration</h3>
          <Label tooltip="Configure if this exercise uses weights and set weight limits." />
        </div>
        
        <Card>
          <CardContent className="pt-6">
            <div className="space-y-6">
              {/* Supports Weight Toggle */}
              <div className="flex items-center justify-between">
                <div>
                  <p className="font-medium text-gray-900">Supports Weight</p>
                  <p className="text-sm text-gray-500">Enable if this exercise uses dumbbells, barbells, etc.</p>
                </div>
                <button
                  type="button"
                  onClick={() => setWeightConfig({ supportsWeight: !weightConfig.supportsWeight })}
                  className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                    weightConfig.supportsWeight ? 'bg-orange-500' : 'bg-gray-200'
                  }`}
                >
                  <span
                    className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                      weightConfig.supportsWeight ? 'translate-x-6' : 'translate-x-1'
                    }`}
                  />
                </button>
              </div>
              
              {/* Weight Range - Only show if supports weight */}
              {weightConfig.supportsWeight && (
                <div className="grid grid-cols-3 gap-4 pt-4 border-t">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      Min Weight (kg)
                    </label>
                    <input
                      type="number"
                      min="0"
                      step="0.5"
                      value={weightConfig.minWeight ?? ''}
                      onChange={(e) => setWeightConfig({ minWeight: e.target.value ? parseFloat(e.target.value) : undefined })}
                      placeholder="e.g., 5"
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-orange-500 placeholder:text-gray-400 text-gray-900"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      Max Weight (kg)
                    </label>
                    <input
                      type="number"
                      min="0"
                      step="0.5"
                      value={weightConfig.maxWeight ?? ''}
                      onChange={(e) => setWeightConfig({ maxWeight: e.target.value ? parseFloat(e.target.value) : undefined })}
                      placeholder="e.g., 100"
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-orange-500 placeholder:text-gray-400 text-gray-900"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      Default Weight (kg)
                    </label>
                    <input
                      type="number"
                      min="0"
                      step="0.5"
                      value={weightConfig.defaultWeight ?? ''}
                      onChange={(e) => setWeightConfig({ defaultWeight: e.target.value ? parseFloat(e.target.value) : undefined })}
                      placeholder="e.g., 20"
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-orange-500 placeholder:text-gray-400 text-gray-900"
                    />
                  </div>
                </div>
              )}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Bilateral Configuration */}
      <div className="space-y-4 pt-6 border-t">
        <div className="flex items-center gap-2">
          <Library className="h-5 w-5 text-indigo-500" />
          <h3 className="text-xl font-bold text-gray-900">Bilateral Configuration</h3>
          <Label tooltip="Enable if this exercise alternates between left and right sides per rep (e.g., alternating lunges, alternating bicep curls)." />
        </div>

        <Card>
          <CardContent className="pt-6 space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="font-medium text-gray-900">Bilateral Exercise</p>
                <p className="text-sm text-gray-500">
                  Alternates between left and right sides per rep. Paired joints will be auto-mirrored.
                </p>
              </div>
              <button
                type="button"
                onClick={() => setBilateralConfig({ enabled: !bilateralConfig.enabled })}
                className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                  bilateralConfig.enabled ? 'bg-indigo-500' : 'bg-gray-200'
                }`}
              >
                <span
                  className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                    bilateralConfig.enabled ? 'translate-x-6' : 'translate-x-1'
                  }`}
                />
              </button>
            </div>

            {bilateralConfig.enabled && (
              <div className="space-y-4 border-t pt-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <Label>Switch Every (reps)</Label>
                    <Input
                      type="number"
                      min={1}
                      value={bilateralConfig.switchEvery}
                      onChange={(e) =>
                        setBilateralConfig({ switchEvery: Number.parseInt(e.target.value, 10) || 1 })
                      }
                    />
                    <p className="text-xs text-gray-400 mt-1">How many reps before switching sides</p>
                  </div>
                  <div>
                    <Label>Start Side</Label>
                    <select
                      value={bilateralConfig.startSide}
                      onChange={(e) => setBilateralConfig({ startSide: e.target.value as 'left' | 'right' })}
                      className="w-full px-3 py-2 border border-gray-200 rounded-lg bg-white text-gray-900"
                    >
                      <option value="right">Right</option>
                      <option value="left">Left</option>
                    </select>
                    <p className="text-xs text-gray-400 mt-1">Which side starts first</p>
                  </div>
                </div>

                <div className="bg-indigo-50 border border-indigo-200 rounded-lg p-3">
                  <p className="text-sm text-indigo-800">
                    When bilateral is enabled, configure joints for the <strong>{bilateralConfig.startSide}</strong> side
                    in the Joint Config step. The opposite side will be auto-mirrored automatically.
                    Shared joints (spine, neck, nose) will be used on both sides.
                  </p>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
      
      {/* Report Metrics Configuration */}
      <div className="space-y-4 pt-6 border-t">
        <div className="flex items-center gap-2">
          <BarChart3 className="h-5 w-5 text-blue-500" />
          <h3 className="text-xl font-bold text-gray-900">Report Metrics</h3>
          <Label tooltip="Configure which metrics to show in the post-training report." />
        </div>
        
        {/* Auto-detected info */}
        <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
          <p className="text-sm text-blue-800 font-medium mb-2">Auto-detected exercise type:</p>
          <div className="flex flex-wrap gap-2">
            <Badge variant={isHold ? 'teal' : 'default'}>{isHold ? 'Hold Exercise' : 'Rep-based Exercise'}</Badge>
            {isBilateral && (
              <Badge variant="purple">
                {bilateralConfig.enabled && hasPairedJoints
                  ? 'Bilateral + Paired Joints (Symmetry enabled)'
                  : bilateralConfig.enabled
                    ? 'Bilateral (Symmetry enabled)'
                    : 'Paired Joints (Symmetry enabled)'}
              </Badge>
            )}
            {weightConfig.supportsWeight && <Badge variant="orange">Weighted (Volume & 1RM enabled)</Badge>}
            {hasPositionChecks && <Badge variant="primary">Has Position Checks (Alignment enabled)</Badge>}
          </div>
        </div>
        
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">All Metrics</CardTitle>
            <p className="text-sm text-gray-500 mt-1">
              <span className="inline-flex items-center gap-1 text-blue-600"><Check className="h-3 w-3" /> Included</span>
              <span className="mx-3">|</span>
              <span className="inline-flex items-center gap-1 text-red-600"><X className="h-3 w-3" /> Excluded</span>
              <span className="mx-3">|</span>
              <span className="text-gray-400">Not applicable</span>
            </p>
          </CardHeader>
          <CardContent>
            <div className="flex flex-wrap gap-2">
              {METRIC_DEFINITIONS.map((metric) => {
                // Check if metric is applicable for this exercise type
                let isDisabled = false;
                let disabledReason = '';
                
                // Hold exercise restrictions
                if (isHold) {
                  if (['rep_count', 'tempo', 'tut', 'rom', 'form_consistency', 'fatigue_index', 'velocity'].includes(metric.code)) {
                    isDisabled = true;
                    disabledReason = 'Not for Hold exercises';
                  }
                } else {
                  // Rep-based exercise restrictions
                  if (metric.code === 'hold_duration') {
                    isDisabled = true;
                    disabledReason = 'Only for Hold exercises';
                  }
                }
                
                // Weight restrictions
                if (!weightConfig.supportsWeight && ['weight', 'volume', 'est_1rm'].includes(metric.code)) {
                  isDisabled = true;
                  disabledReason = 'Enable weight first';
                }
                
                // Bilateral / paired-joints restrictions
                if (!isBilateral && metric.code === 'symmetry') {
                  isDisabled = true;
                  disabledReason = 'Enable bilateral mode or add paired joints';
                }
                
                // Position Checks restrictions (Alignment)
                if (!hasPositionChecks && metric.code === 'alignment') {
                  isDisabled = true;
                  disabledReason = 'Add Position Checks first';
                }
                
                // Check current state
                const isExcluded = reportMetrics.excluded.includes(metric.code);
                
                // Disabled = gray, Excluded = red, else = blue
                if (isDisabled) {
                  return (
                    <Badge
                      key={metric.code}
                      variant="default"
                      className="px-3 py-1.5 cursor-not-allowed bg-gray-100 text-gray-400 border-gray-200"
                      title={disabledReason}
                    >
                      {metric.label.en}
                    </Badge>
                  );
                }
                
                if (isExcluded) {
                  return (
                    <Badge
                      key={metric.code}
                      variant="error"
                      className="px-3 py-1.5 cursor-pointer"
                      title="Click to include"
                      onClick={() => {
                        // Red → Blue: remove from excluded
                        const newExcluded = reportMetrics.excluded.filter(c => c !== metric.code);
                        setReportMetrics({ excluded: newExcluded });
                      }}
                    >
                      <X className="h-3 w-3 mr-1 inline" />
                      {metric.label.en}
                    </Badge>
                  );
                }
                
                // Included (blue)
                return (
                  <Badge
                    key={metric.code}
                    variant="primary"
                    className="px-3 py-1.5 cursor-pointer"
                    title="Click to exclude"
                    onClick={() => {
                      // Blue → Red: add to excluded
                      const newExcluded = [...reportMetrics.excluded, metric.code];
                      setReportMetrics({ excluded: newExcluded });
                    }}
                  >
                    <Check className="h-3 w-3 mr-1 inline" />
                    {metric.label.en}
                  </Badge>
                );
              })}
            </div>
            <p className="text-xs text-gray-500 mt-3">Click to toggle between Included and Excluded.</p>
          </CardContent>
        </Card>
      </div>
      
      {/* Feedback Messages */}
      <FeedbackMessagesSection
        feedbackAssignments={feedbackAssignments}
        addFeedbackAssignment={addFeedbackAssignment}
        updateFeedbackAssignment={updateFeedbackAssignment}
        removeFeedbackAssignment={removeFeedbackAssignment}
      />
    </div>
  );
}

// ============================================
// FEEDBACK MESSAGES SECTION (Library-based)
// ============================================

interface FeedbackMessagesSectionProps {
  feedbackAssignments: FeedbackAssignmentData[];
  addFeedbackAssignment: (assignment: FeedbackAssignmentData) => void;
  updateFeedbackAssignment: (index: number, assignment: FeedbackAssignmentData) => void;
  removeFeedbackAssignment: (index: number) => void;
}

function FeedbackMessagesSection({
  feedbackAssignments,
  addFeedbackAssignment,
  updateFeedbackAssignment,
  removeFeedbackAssignment,
}: FeedbackMessagesSectionProps) {
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerType, setPickerType] = useState<'motivational' | 'tip'>('motivational');
  const [replaceIndex, setReplaceIndex] = useState<number | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [createType, setCreateType] = useState<'motivational' | 'tip'>('motivational');

  const openPicker = (type: 'motivational' | 'tip', index?: number) => {
    setPickerType(type);
    setReplaceIndex(index ?? null);
    setPickerOpen(true);
  };

  const openCreate = (type: 'motivational' | 'tip') => {
    setCreateType(type);
    setCreateOpen(true);
  };

  const handlePickerSelect = (messages: MessageOption[]) => {
    if (messages.length === 0) return;
    if (replaceIndex !== null) {
      const msg = messages[0];
      updateFeedbackAssignment(replaceIndex, {
        messageId: msg.id,
        context: pickerType,
        message: {
          ar: msg.content.ar || '',
          en: msg.content.en || '',
          audioAr: msg.content.audioAr,
          audioEn: msg.content.audioEn,
        },
      });
      return;
    }
    for (const msg of messages) {
      addFeedbackAssignment({
        messageId: msg.id,
        context: pickerType,
        message: {
          ar: msg.content.ar || '',
          en: msg.content.en || '',
          audioAr: msg.content.audioAr,
          audioEn: msg.content.audioEn,
        },
      });
    }
  };

  const handleCreated = (message: MessageFormData) => {
    if (!message.id) return;
    addFeedbackAssignment({
      messageId: message.id,
      context: createType,
      message: {
        ar: message.content?.ar || '',
        en: message.content?.en || '',
        audioAr: message.content?.audioAr,
        audioEn: message.content?.audioEn,
      },
    });
  };

  return (
    <div className="space-y-4 pt-6 border-t">
      <div className="flex items-center gap-2">
        <h3 className="text-xl font-bold text-gray-900">Feedback Messages</h3>
        <Label tooltip="Pick reusable messages from the library for this exercise." />
      </div>

      <div className="grid gap-4">
        {(['motivational', 'tip'] as const).map((type) => {
          const typeAssignments = feedbackAssignments.filter((m) => m.context === type);
          const typeConfig = {
            motivational: { label: 'Motivational', icon: '💪' },
            tip: { label: 'Tips', icon: '💡' },
          };
          const { label, icon } = typeConfig[type];

          return (
            <Card key={type}>
              <CardHeader className="pb-3">
                <div className="flex items-center justify-between">
                  <CardTitle className="text-lg">
                    {icon} {label}
                  </CardTitle>
                  <div className="flex items-center gap-2">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => openPicker(type)}
                      icon={<Library className="h-4 w-4" />}
                    >
                      From Library
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => openCreate(type)}
                      icon={<Plus className="h-4 w-4" />}
                    >
                      New Message
                    </Button>
                  </div>
                </div>
              </CardHeader>
              <CardContent className="space-y-3">
                {typeAssignments.map((assignment, idx) => {
                  const globalIndex = feedbackAssignments.indexOf(assignment);
                  const message = assignment.message;
                  return (
                    <div key={idx} className="flex gap-2 items-start">
                      <div className="flex-1">
                        <div className="text-sm text-gray-900 truncate">{message?.en || '—'}</div>
                        <div className="text-sm text-gray-600 truncate" dir="rtl">{message?.ar || '—'}</div>
                      </div>
                      <div className="flex items-center gap-2">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => openPicker(type, globalIndex)}
                        >
                          Change
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => removeFeedbackAssignment(globalIndex)}
                          className="text-red-500 hover:text-red-700"
                        >
                          <X className="h-4 w-4" />
                        </Button>
                      </div>
                    </div>
                  );
                })}

                {typeAssignments.length === 0 && (
                  <p className="text-sm text-gray-400 italic text-center py-2">
                    No {type.replace('_', ' ')} messages
                  </p>
                )}
              </CardContent>
            </Card>
          );
        })}
      </div>

      <MessagePickerModal
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        onSelect={handlePickerSelect}
        multiple={replaceIndex === null}
        categoryFilter={pickerType}
        title={`Pick ${pickerType === 'motivational' ? 'Motivational' : 'Tip'} Messages`}
        description="Select messages from the library to add to this exercise."
        createDefaults={{ category: pickerType }}
      />

      <MessageFormModal
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSaved={handleCreated}
        defaults={{ category: createType, context: createType }}
      />
    </div>
  );
}

export default ExtrasStep;
