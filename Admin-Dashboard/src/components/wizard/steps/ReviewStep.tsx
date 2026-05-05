'use client';

/**
 * Step 7: Review & Publish
 * ========================
 * 
 * Final review of exercise configuration with JSON preview.
 */

import { useMemo } from 'react';
import { useWizardStore } from '../WizardContext';
import { Card, CardHeader, CardTitle, CardContent, Badge, Label, Select, Input, Textarea } from '@/components/ui';

const MOVEMENT_PATTERN_OPTIONS = [
  { value: '', label: '—' },
  { value: 'SQUAT', label: 'SQUAT' },
  { value: 'HINGE', label: 'HINGE' },
  { value: 'LUNGE', label: 'LUNGE' },
  { value: 'PUSH_HORIZONTAL', label: 'PUSH_HORIZONTAL' },
  { value: 'PUSH_VERTICAL', label: 'PUSH_VERTICAL' },
  { value: 'PULL_HORIZONTAL', label: 'PULL_HORIZONTAL' },
  { value: 'PULL_VERTICAL', label: 'PULL_VERTICAL' },
  { value: 'CARRY', label: 'CARRY' },
  { value: 'ROTATION', label: 'ROTATION' },
  { value: 'GAIT', label: 'GAIT' },
  { value: 'JUMP_LAND', label: 'JUMP_LAND' },
  { value: 'CORE_BRACE', label: 'CORE_BRACE' },
  { value: 'MOBILITY_DRILL', label: 'MOBILITY_DRILL' },
  { value: 'OTHER', label: 'OTHER' },
];

const LOAD_CAPABILITY_OPTIONS = [
  { value: '', label: '—' },
  { value: 'BODYWEIGHT_ONLY', label: 'BODYWEIGHT_ONLY' },
  { value: 'EXTERNAL_LOAD_OPTIONAL', label: 'EXTERNAL_LOAD_OPTIONAL' },
  { value: 'EXTERNAL_LOAD_REQUIRED', label: 'EXTERNAL_LOAD_REQUIRED' },
];

const EXERCISE_INTENT_OPTIONS = [
  { value: '', label: '—' },
  { value: 'STANDARD', label: 'STANDARD' },
  { value: 'POWER', label: 'POWER' },
  { value: 'ECCENTRIC', label: 'ECCENTRIC' },
  { value: 'VELOCITY_BASED', label: 'VELOCITY_BASED' },
];
import { JsonPreview } from '@/components/JsonPreview';
import { canPublish } from '@/modules/exercises/exercises.validation';
import { STATE_CONFIG, JOINT_STATE_NAMES } from '@/lib/types/localized';
import type { ExerciseConfig } from '@/lib/types/android-schema';

export function ReviewStep() {
  const store = useWizardStore();
  const blueprint = store.blueprintExerciseMeta ?? {
    movementPattern: '',
    loadCapability: '',
    familyKey: '',
    familyOrder: '',
    intent: '',
    coachingNotesJson: '',
  };
  const setBlueprint = store.setBlueprintExerciseMeta;
  
  // Build the final JSON config
  const exerciseConfig = useMemo((): ExerciseConfig | null => {
    try {
      const isHold = store.countingMethod.countingMethodCode === 'hold';
      
      // Build tracked joints based on counting method
      const trackedJoints = (store.jointConfig.trackedJoints || []).map((joint) => {
        if (joint.role === 'primary') {
          if (isHold) {
            // Hold mode: primary joints have single range
            return {
              joint: joint.joint,
              role: 'primary' as const,
              startPose: joint.startPose,
              range: joint.range || { perfect: { min: 85, max: 95 } },
              stateMessages: joint.stateMessages,
              pairedWith: joint.pairedWith,
              invertIndicator: joint.invertIndicator,
            };
          } else {
            // Up/Down or Push/Pull mode: primary joints have upRange and downRange
            return {
              joint: joint.joint,
              role: 'primary' as const,
              startPose: joint.startPose,
              upRange: joint.upRange || { perfect: { min: 150, max: 180 } },
              downRange: joint.downRange || { perfect: { min: 0, max: 90 } },
              stateMessages: joint.stateMessages,
              pairedWith: joint.pairedWith,
              invertIndicator: joint.invertIndicator,
            };
          }
        } else {
          return {
            joint: joint.joint,
            role: 'secondary' as const,
            startPose: joint.startPose,
            range: joint.range,
            stateMessages: joint.stateMessages,
            pairedWith: joint.pairedWith,
          };
        }
      });
      
      // Build position checks
      const positionChecks = (store.positionChecks.positionChecks || []).map((pc) => ({
        id: pc.checkId,
        type: pc.type,
        landmarks: pc.landmarks,
        condition: pc.condition,
        activePhases: pc.activePhases,
        errorMessage: pc.errorMessage,
        severity: pc.severity,
        cooldownMs: pc.cooldownMs,
        minErrorFrames: pc.minErrorFrames,
      }));
      
      // Build feedback messages (from assignments - preview only)
      const feedbackMessages = {
        motivational: (store.extras.feedbackAssignments || [])
          .filter(m => m.context === 'motivational')
          .map(m => m.message || { ar: '', en: '' }),
        tips: (store.extras.feedbackAssignments || [])
          .filter(m => m.context === 'tip')
          .map(m => m.message || { ar: '', en: '' }),
      };
      
      // Build rep counting config
      const repCountingConfig = isHold
        ? {
            duration: store.repConfig.duration || 30,
            gracePeriodMs: store.repConfig.gracePeriodMs || 2500,
          }
        : {
            reps: store.repConfig.reps || 12,
            minRepIntervalMs: store.repConfig.minRepIntervalMs || 1500,
            maxRepIntervalMs: store.repConfig.maxRepIntervalMs || 5000,
          };
      
      return {
        name: store.basicInfo.name || { ar: '', en: '' },
        description: store.basicInfo.description,
        instructions: store.basicInfo.instructions,
        category: {
          code: 'exercise', // Will be replaced with actual category
          name: { ar: 'تمرين', en: 'Exercise' },
        },
        countingMethod: store.countingMethod.countingMethodCode || 'up_down',
        muscles: [], // Will be resolved from IDs
        equipment: [],
        tags: [],
        repCountingConfig,
        poseVariants: [{
          name: store.basicInfo.name || { ar: '', en: '' },
          posePosition: 'standing_side',
          trackedJoints,
          positionChecks: positionChecks.length > 0 ? positionChecks : undefined,
          feedbackMessages: (feedbackMessages.motivational.length > 0 || feedbackMessages.tips.length > 0) 
            ? feedbackMessages 
            : undefined,
        }],
      };
    } catch {
      return null;
    }
  }, [store]);
  
  // Validation - build a compatible object for canPublish
  const validation = useMemo(() => {
    // Extract data in the format expected by canPublish
    const wizardData = {
      basicInfo: store.basicInfo,
      countingMethod: store.countingMethod,
      cameraPosition: store.cameraPosition,
      jointConfig: store.jointConfig,
      positionChecks: store.positionChecks,
      repConfig: store.repConfig,
      extras: store.extras,
    };
    return canPublish(wizardData as Parameters<typeof canPublish>[0]);
  }, [store]);
  
  // Summary stats
  const stats = useMemo(() => ({
    joints: store.jointConfig.trackedJoints?.length || 0,
    primaryJoints: store.jointConfig.trackedJoints?.filter(j => j.role === 'primary').length || 0,
    secondaryJoints: store.jointConfig.trackedJoints?.filter(j => j.role === 'secondary').length || 0,
    positionChecks: store.positionChecks.positionChecks?.length || 0,
    feedbackMessages: store.extras.feedbackAssignments?.length || 0,
    cameraPositions: store.cameraPosition.cameraPositionIds?.length || 0,
  }), [store]);
  
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Review & Publish</h2>
        <p className="text-gray-500">
          Review your exercise configuration before publishing.
        </p>
      </div>
      
      {/* Validation Status */}
      {!validation.valid && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4">
          <h4 className="font-semibold text-red-800 mb-2">⚠️ Cannot Publish Yet</h4>
          <ul className="list-disc list-inside text-sm text-red-700 space-y-1">
            {validation.errors.map((err, i) => (
              <li key={i}>{err}</li>
            ))}
          </ul>
          <p className="text-sm text-red-600 mt-3">
            Incomplete steps: {validation.incompleteSteps.join(', ')}
          </p>
        </div>
      )}
      
      {validation.valid && (
        <div className="bg-green-50 border border-green-200 rounded-xl p-4 flex items-center gap-3">
          <span className="text-2xl">✅</span>
          <div>
            <p className="font-semibold text-green-800">Ready to Publish!</p>
            <p className="text-sm text-green-700">All required fields are complete.</p>
          </div>
        </div>
      )}
      
      {/* Summary Cards */}
      <div className="grid md:grid-cols-3 gap-4">
        <Card>
          <CardContent className="pt-4">
            <div className="text-center">
              <p className="text-3xl font-bold text-blue-600">{stats.joints}</p>
              <p className="text-sm text-gray-500">Tracked Joints</p>
              <div className="text-xs text-gray-400 mt-1">
                {stats.primaryJoints} primary • {stats.secondaryJoints} secondary
              </div>
            </div>
          </CardContent>
        </Card>
        
        <Card>
          <CardContent className="pt-4">
            <div className="text-center">
              <p className="text-3xl font-bold text-purple-600">{stats.positionChecks}</p>
              <p className="text-sm text-gray-500">Position Checks</p>
            </div>
          </CardContent>
        </Card>
        
        <Card>
          <CardContent className="pt-4">
            <div className="text-center">
              <p className="text-3xl font-bold text-green-600">{stats.feedbackMessages}</p>
              <p className="text-sm text-gray-500">Feedback Messages</p>
            </div>
          </CardContent>
        </Card>
      </div>
      
      {/* Configuration Details */}
      <div className="grid md:grid-cols-2 gap-4">
        {/* Basic Info */}
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">📋 Basic Info</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            <div>
              <span className="text-sm text-gray-500">Name (EN):</span>
              <p className="font-medium">{store.basicInfo.name?.en || '-'}</p>
            </div>
            <div>
              <span className="text-sm text-gray-500">Name (AR):</span>
              <p className="font-medium" dir="rtl">{store.basicInfo.name?.ar || '-'}</p>
            </div>
            <div>
              <span className="text-sm text-gray-500">Counting Method:</span>
              <Badge className="ml-2">{store.countingMethod.countingMethodCode || '-'}</Badge>
            </div>
          </CardContent>
        </Card>
        
        {/* Rep Config */}
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">
              {store.countingMethod.countingMethodCode === 'hold' ? '⏱️ Hold Timer' : '🔄 Rep Config'}
            </CardTitle>
          </CardHeader>
          <CardContent>
            {store.countingMethod.countingMethodCode === 'hold' ? (
              <div className="space-y-2">
                <div className="flex justify-between">
                  <span className="text-sm text-gray-500">Duration:</span>
                  <span className="font-medium">{store.repConfig.duration || 30}s</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-sm text-gray-500">Grace Period:</span>
                  <span className="font-medium">{store.repConfig.gracePeriodMs || 2500}ms</span>
                </div>
              </div>
            ) : (
              <div className="space-y-2">
                <div className="flex justify-between">
                  <span className="text-sm text-gray-500">Target Reps:</span>
                  <span className="font-medium">{store.repConfig.reps || 12}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-sm text-gray-500">Min Interval:</span>
                  <span className="font-medium">{store.repConfig.minRepIntervalMs || 1500}ms</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-sm text-gray-500">Max Interval:</span>
                  <span className="font-medium">{store.repConfig.maxRepIntervalMs || 5000}ms</span>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Blueprint: taxonomy & progression ladder */}
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Blueprint — Exercise library</CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <Label>Movement pattern</Label>
            <Select
              value={blueprint.movementPattern}
              onChange={(e) => setBlueprint({ movementPattern: e.target.value })}
              options={MOVEMENT_PATTERN_OPTIONS}
            />
          </div>
          <div>
            <Label>Load capability</Label>
            <Select
              value={blueprint.loadCapability}
              onChange={(e) => setBlueprint({ loadCapability: e.target.value })}
              options={LOAD_CAPABILITY_OPTIONS}
            />
          </div>
          <div>
            <Label>Family key</Label>
            <Input
              value={blueprint.familyKey}
              onChange={(e) => setBlueprint({ familyKey: e.target.value })}
              placeholder="e.g. push_horizontal_bodyweight"
            />
          </div>
          <div>
            <Label>Family order</Label>
            <Input
              type="number"
              value={blueprint.familyOrder}
              onChange={(e) => setBlueprint({ familyOrder: e.target.value })}
              placeholder="1"
            />
          </div>
          <div>
            <Label>Intent</Label>
            <Select
              value={blueprint.intent ?? ''}
              onChange={(e) => setBlueprint({ intent: e.target.value })}
              options={EXERCISE_INTENT_OPTIONS}
            />
          </div>
          <div className="md:col-span-2">
            <Label>Coaching notes (JSON)</Label>
            <Textarea
              rows={4}
              value={blueprint.coachingNotesJson ?? ''}
              onChange={(e) => setBlueprint({ coachingNotesJson: e.target.value })}
              placeholder="{}"
              className="font-mono text-sm"
            />
          </div>
        </CardContent>
      </Card>
      
      {/* State Ranges Legend */}
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">🎯 State-Based Scoring</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-gray-600 mb-4">
            The new state-based system evaluates performance based on which angle range the user is in:
          </p>
          <div className="grid grid-cols-5 gap-2">
            {JOINT_STATE_NAMES.map((state) => {
              const config = STATE_CONFIG[state];
              return (
                <div 
                  key={state}
                  className={`p-3 rounded-lg text-center ${
                    state === 'perfect' ? 'bg-green-100 border border-green-300' :
                    state === 'normal' ? 'bg-yellow-100 border border-yellow-300' :
                    state === 'pad' ? 'bg-orange-100 border border-orange-300' :
                    state === 'warning' ? 'bg-red-100 border border-red-300' :
                    'bg-red-200 border border-red-400'
                  }`}
                >
                  <p className="font-bold text-lg">{config.rate}%</p>
                  <p className="text-xs font-medium capitalize">{state}</p>
                  <p className="text-[10px] text-gray-500">
                    {config.isRepCounted ? '✓ Counted' : '✗ Not counted'}
                  </p>
                </div>
              );
            })}
          </div>
        </CardContent>
      </Card>
      
      {/* JSON Preview */}
      {exerciseConfig && (
        <JsonPreview 
          data={exerciseConfig} 
          title="📱 Android JSON Config (Preview)"
          defaultExpanded={false}
        />
      )}
    </div>
  );
}

export default ReviewStep;
