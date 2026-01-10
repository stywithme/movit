'use client';

/**
 * Step 8: Review & Publish
 * ========================
 */

import { useState } from 'react';
import { useWizardStore, useStepComplete } from '../WizardContext';
import { canPublish } from '@/modules/exercises/exercises.validation';
import { Card, CardHeader, CardTitle, CardContent, Button, Badge, Label } from '@/components/ui';
import { Check, AlertTriangle, ChevronDown, ChevronUp, Copy } from 'lucide-react';

interface ReviewStepProps {
  onSaveDraft: () => Promise<void>;
  onPublish: () => Promise<void>;
}

export function ReviewStep({ onSaveDraft, onPublish }: ReviewStepProps) {
  const store = useWizardStore();
  const [showJson, setShowJson] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  
  // Check step completion
  const stepsComplete = [1, 2, 3, 4, 5, 6, 7].map(step => ({
    step,
    complete: useStepComplete(step),
  }));
  
  const { valid: canPublishNow, missingSteps } = canPublish({
    basicInfo: store.basicInfo as Parameters<typeof canPublish>[0]['basicInfo'],
    countingMethod: store.countingMethod as Parameters<typeof canPublish>[0]['countingMethod'],
    cameraPosition: store.cameraPosition as Parameters<typeof canPublish>[0]['cameraPosition'],
    jointConfig: store.jointConfig as Parameters<typeof canPublish>[0]['jointConfig'],
    positionChecks: store.positionChecks as Parameters<typeof canPublish>[0]['positionChecks'],
    repConfig: store.repConfig as Parameters<typeof canPublish>[0]['repConfig'],
    extras: store.extras as Parameters<typeof canPublish>[0]['extras'],
  });
  
  const stepNames = ['Basic Info', 'Exercise Type', 'Camera', 'Joints', 'Position Checks', 'Reps', 'Extras'];
  
  // Build summary JSON
  const buildSummaryJson = () => {
    return {
      name: store.basicInfo.name,
      countingMethod: store.countingMethod.countingMethodCode,
      cameraPositions: store.cameraPosition.cameraPositionIds?.length || 0,
      expectedFacingDirection: store.cameraPosition.expectedFacingDirection,
      trackedJoints: store.jointConfig.trackedJoints?.map(j => ({
        joint: j.joint,
        role: j.role,
      })),
      positionChecks: store.positionChecks.positionChecks?.length || 0,
      repConfig: store.repConfig,
      muscles: store.extras.muscles?.length || 0,
      equipment: store.extras.equipment?.length || 0,
      feedbackMessages: store.extras.feedbackMessages?.length || 0,
    };
  };
  
  const handleSaveDraft = async () => {
    setIsSubmitting(true);
    try {
      await onSaveDraft();
    } finally {
      setIsSubmitting(false);
    }
  };
  
  const handlePublish = async () => {
    setIsSubmitting(true);
    try {
      await onPublish();
    } finally {
      setIsSubmitting(false);
    }
  };
  
  return (
    <div className="space-y-8 max-w-4xl mx-auto">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Review & Publish</h2>
        <p className="text-gray-500">Review your exercise configuration before publishing.</p>
      </div>
      
      {/* Exercise Summary */}
      <Card>
        <CardHeader>
          <CardTitle>Exercise Summary</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 gap-6">
            <div>
              <span className="text-sm text-gray-500 block mb-1">Name</span>
              <p className="font-medium text-gray-900">{store.basicInfo.name?.en || '-'} / {store.basicInfo.name?.ar || '-'}</p>
            </div>
            <div>
              <span className="text-sm text-gray-500 block mb-1">Type</span>
              <Badge variant="primary" className="uppercase">{store.countingMethod.countingMethodCode || '-'}</Badge>
            </div>
            <div>
              <span className="text-sm text-gray-500 block mb-1">Camera Positions</span>
              <p className="font-medium text-gray-900">{store.cameraPosition.cameraPositionIds?.length || 0} selected</p>
            </div>
            <div>
              <span className="text-sm text-gray-500 block mb-1">Facing Direction</span>
              <p className="font-medium capitalize text-gray-900">{store.cameraPosition.expectedFacingDirection?.replace(/_/g, ' ') || 'Auto'}</p>
            </div>
            <div>
              <span className="text-sm text-gray-500 block mb-1">Tracked Joints</span>
              <div className="flex gap-2">
                <Badge variant="primary">{store.jointConfig.trackedJoints?.filter(j => j.role === 'primary').length || 0} Primary</Badge>
                <Badge variant="outline">{store.jointConfig.trackedJoints?.filter(j => j.role === 'secondary').length || 0} Secondary</Badge>
              </div>
            </div>
            <div>
              <span className="text-sm text-gray-500 block mb-1">Position Checks</span>
              <p className="font-medium text-gray-900">{store.positionChecks.positionChecks?.length || 0}</p>
            </div>
          </div>
        </CardContent>
      </Card>
      
      {/* Validation Status */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <CardTitle>Validation</CardTitle>
            {canPublishNow ? (
              <Badge variant="success">Ready to Publish</Badge>
            ) : (
              <Badge variant="warning">Incomplete</Badge>
            )}
          </div>
        </CardHeader>
        <CardContent>
          <div className="space-y-3">
            {stepsComplete.map(({ step, complete }) => (
              <div key={step} className="flex items-center justify-between p-2 rounded-lg hover:bg-gray-50">
                <div className="flex items-center gap-3">
                  <div className={`w-6 h-6 rounded-full flex items-center justify-center ${complete ? 'bg-green-100' : 'bg-amber-100'}`}>
                    {complete ? (
                      <Check className="w-4 h-4 text-green-600" />
                    ) : (
                      <AlertTriangle className="w-4 h-4 text-amber-600" />
                    )}
                  </div>
                  <span className={complete ? 'text-gray-900' : 'text-amber-700 font-medium'}>
                    Step {step}: {stepNames[step - 1]}
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  {!complete && step <= 4 && <Badge variant="warning">Required</Badge>}
                  {!complete && step > 4 && <Badge variant="outline">Optional</Badge>}
                  {complete && <Badge variant="success">Complete</Badge>}
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
      
      {/* JSON Preview */}
      <Card className="overflow-hidden">
        <div 
          className="flex items-center justify-between p-4 bg-gray-50 cursor-pointer border-b hover:bg-gray-100 transition-colors"
          onClick={() => setShowJson(!showJson)}
        >
          <div className="flex items-center gap-2">
            <span className="font-semibold text-gray-900">Android JSON Preview</span>
            <Label tooltip="Preview the exact JSON structure that will be sent to the Android app." />
          </div>
          {showJson ? <ChevronUp className="h-5 w-5 text-gray-500" /> : <ChevronDown className="h-5 w-5 text-gray-500" />}
        </div>
        
        {showJson && (
          <CardContent className="p-0">
            <div className="relative bg-gray-900">
              <pre className="text-green-400 p-6 text-sm overflow-x-auto max-h-96 font-mono">
                {JSON.stringify(buildSummaryJson(), null, 2)}
              </pre>
              <button
                type="button"
                onClick={() => navigator.clipboard.writeText(JSON.stringify(buildSummaryJson(), null, 2))}
                className="absolute top-4 right-4 p-2 bg-white/10 hover:bg-white/20 rounded-lg text-white transition-colors"
                title="Copy JSON"
              >
                <Copy className="h-4 w-4" />
              </button>
            </div>
          </CardContent>
        )}
      </Card>
      
      {/* Actions */}
      <div className="flex items-center justify-between pt-4">
        <Button
          variant="outline"
          onClick={handleSaveDraft}
          disabled={isSubmitting}
          loading={isSubmitting}
          size="lg"
        >
          Save as Draft
        </Button>
        
        <div className="flex flex-col items-end gap-2">
          <Button
            variant={canPublishNow ? 'success' : 'secondary'}
            onClick={handlePublish}
            disabled={isSubmitting || !canPublishNow}
            loading={isSubmitting}
            size="lg"
            icon={<Check className="h-5 w-5" />}
          >
            Publish Exercise
          </Button>
          {!canPublishNow && (
            <p className="text-sm text-amber-600">
              Complete required steps to publish
            </p>
          )}
        </div>
      </div>
    </div>
  );
}

export default ReviewStep;
