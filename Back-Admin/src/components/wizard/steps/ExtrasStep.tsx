'use client';

/**
 * Step 6: Extras (Attributes + Feedback Messages + Weight & Metrics)
 * ===================================================================
 */

import { useWizardStore } from '../WizardContext';
import { Card, CardHeader, CardTitle, CardContent, Button, Badge, Label } from '@/components/ui';
import { SmartLocalizedInput } from '@/components/forms';
import { Plus, X, Dumbbell, BarChart3, Check } from 'lucide-react';
import type { FeedbackMessageData } from '@/modules/exercises/exercises.validation';
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
    addFeedbackMessage, 
    updateFeedbackMessage, 
    removeFeedbackMessage,
    weightConfig,
    setWeightConfig,
    reportMetrics,
    setReportMetrics,
    countingMethod,
    jointConfig,
    positionChecks,
  } = useWizardStore();
  
  // Determine exercise type for auto-suggestions
  const isHold = countingMethod.countingMethodCode === 'hold';
  const hasPairedJoints = (jointConfig.trackedJoints || []).some((j) => j.pairedWith);
  const isBilateral = hasPairedJoints;
  const hasPositionChecks = (positionChecks.positionChecks || []).length > 0;
  
  const toggleAttribute = (type: 'muscles' | 'equipment' | 'tags', id: string) => {
    const current = extras[type] || [];
    const updated = current.includes(id)
      ? current.filter(i => i !== id)
      : [...current, id];
    setExtras({ [type]: updated });
  };
  
  const feedbackMessages = extras.feedbackMessages || [];
  
  const addNewMessage = (type: FeedbackMessageData['type']) => {
    addFeedbackMessage({
      type,
      message: { ar: '', en: '' },
    });
  };
  
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
            {isBilateral && <Badge variant="purple">Bilateral (Symmetry enabled)</Badge>}
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
                
                // Bilateral restrictions
                if (!isBilateral && metric.code === 'symmetry') {
                  isDisabled = true;
                  disabledReason = 'Only for bilateral exercises';
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
      <div className="space-y-4 pt-6 border-t">
        <div className="flex items-center gap-2">
          <h3 className="text-xl font-bold text-gray-900">Feedback Messages</h3>
          <Label tooltip="Messages shown to the user during or after the exercise." />
        </div>
        
        <div className="grid gap-4">
          {/* Message Types */}
          {(['motivational', 'tip'] as const).map((type) => {
            const typeMessages = feedbackMessages.filter(m => m.type === type);
            const typeConfig = {
              motivational: { label: '💪 Motivational', variant: 'success' as const },
              tip: { label: '💡 Tips', variant: 'primary' as const },
            };
            const { label } = typeConfig[type];
            
            return (
              <Card key={type}>
                <CardHeader className="pb-3">
                  <div className="flex items-center justify-between">
                    <CardTitle className="text-lg">{label}</CardTitle>
                    <Button variant="ghost" size="sm" onClick={() => addNewMessage(type)} icon={<Plus className="h-4 w-4" />}>
                      Add
                    </Button>
                  </div>
                </CardHeader>
                <CardContent className="space-y-3">
                  {typeMessages.map((msg, idx) => {
                    const globalIndex = feedbackMessages.indexOf(msg);
                    return (
                      <div key={idx} className="flex gap-2 items-start">
                        <div className="flex-1">
                          <SmartLocalizedInput
                            label=""
                            value={msg.message}
                            onChange={(value) => updateFeedbackMessage(globalIndex, {
                              ...msg,
                              message: value,
                            })}
                            audioValue={{
                              ar: msg.message.audioAr,
                              en: msg.message.audioEn,
                            }}
                            onAudioChange={(audio) => updateFeedbackMessage(globalIndex, {
                              ...msg,
                              message: {
                                ...msg.message,
                                audioAr: audio.ar,
                                audioEn: audio.en,
                              },
                            })}
                            enableTranslation
                            enableTTS
                            translationContext={type === 'motivational' ? 'fitness motivational message' : 'fitness exercise tip'}
                            variant="inline"
                          />
                        </div>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => removeFeedbackMessage(globalIndex)}
                          className="text-red-500 hover:text-red-700 mt-1"
                        >
                          <X className="h-4 w-4" />
                        </Button>
                      </div>
                    );
                  })}
                  
                  {typeMessages.length === 0 && (
                    <p className="text-sm text-gray-400 italic text-center py-2">No {type.replace('_', ' ')} messages</p>
                  )}
                </CardContent>
              </Card>
            );
          })}
        </div>
      </div>
    </div>
  );
}

export default ExtrasStep;
