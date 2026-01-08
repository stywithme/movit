'use client';

/**
 * Step 8: Review & Publish
 * ========================
 */

import { useState } from 'react';
import { useWizardStore, useStepComplete } from '../WizardContext';
import { canPublish } from '@/modules/exercises/exercises.validation';

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
      <div className="bg-white border rounded-xl p-6 space-y-4">
        <h3 className="font-semibold text-gray-900 text-lg">Exercise Summary</h3>
        
        <div className="grid grid-cols-2 gap-4">
          <div>
            <span className="text-sm text-gray-500">Name</span>
            <p className="font-medium">{store.basicInfo.name?.en || '-'} / {store.basicInfo.name?.ar || '-'}</p>
          </div>
          <div>
            <span className="text-sm text-gray-500">Type</span>
            <p className="font-medium uppercase">{store.countingMethod.countingMethodCode || '-'}</p>
          </div>
          <div>
            <span className="text-sm text-gray-500">Camera Positions</span>
            <p className="font-medium">{store.cameraPosition.cameraPositionIds?.length || 0} selected</p>
          </div>
          <div>
            <span className="text-sm text-gray-500">Facing Direction</span>
            <p className="font-medium capitalize">{store.cameraPosition.expectedFacingDirection?.replace(/_/g, ' ') || 'Auto'}</p>
          </div>
          <div>
            <span className="text-sm text-gray-500">Tracked Joints</span>
            <p className="font-medium">
              {store.jointConfig.trackedJoints?.filter(j => j.role === 'primary').length || 0} primary, 
              {' '}{store.jointConfig.trackedJoints?.filter(j => j.role === 'secondary').length || 0} secondary
            </p>
          </div>
          <div>
            <span className="text-sm text-gray-500">Position Checks</span>
            <p className="font-medium">{store.positionChecks.positionChecks?.length || 0}</p>
          </div>
        </div>
      </div>
      
      {/* Validation Status */}
      <div className="bg-white border rounded-xl p-6 space-y-4">
        <h3 className="font-semibold text-gray-900 text-lg">Validation</h3>
        
        <div className="space-y-2">
          {stepsComplete.map(({ step, complete }) => (
            <div key={step} className="flex items-center gap-3">
              <div className={`w-5 h-5 rounded-full flex items-center justify-center ${complete ? 'bg-green-100' : 'bg-amber-100'}`}>
                {complete ? (
                  <svg className="w-3 h-3 text-green-600" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                  </svg>
                ) : (
                  <svg className="w-3 h-3 text-amber-600" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                  </svg>
                )}
              </div>
              <span className={complete ? 'text-gray-700' : 'text-amber-700'}>
                Step {step}: {stepNames[step - 1]}
              </span>
              {!complete && step <= 4 && (
                <span className="text-xs text-amber-600">(Required)</span>
              )}
              {!complete && step > 4 && (
                <span className="text-xs text-gray-400">(Optional)</span>
              )}
            </div>
          ))}
        </div>
      </div>
      
      {/* JSON Preview */}
      <div className="bg-white border rounded-xl overflow-hidden">
        <button
          type="button"
          onClick={() => setShowJson(!showJson)}
          className="w-full px-6 py-4 flex items-center justify-between hover:bg-gray-50"
        >
          <span className="font-semibold text-gray-900">Android JSON Preview</span>
          <svg className={`w-5 h-5 text-gray-400 transition-transform ${showJson ? 'rotate-180' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </button>
        
        {showJson && (
          <div className="px-6 pb-6">
            <pre className="bg-gray-900 text-green-400 p-4 rounded-lg text-sm overflow-x-auto max-h-96">
              {JSON.stringify(buildSummaryJson(), null, 2)}
            </pre>
            <button
              type="button"
              onClick={() => navigator.clipboard.writeText(JSON.stringify(buildSummaryJson(), null, 2))}
              className="mt-2 text-sm text-blue-600 hover:text-blue-700"
            >
              📋 Copy JSON
            </button>
          </div>
        )}
      </div>
      
      {/* Actions */}
      <div className="flex items-center justify-between pt-6 border-t">
        <button
          type="button"
          onClick={handleSaveDraft}
          disabled={isSubmitting}
          className="px-6 py-3 border-2 border-gray-300 rounded-xl text-gray-700 hover:bg-gray-50 disabled:opacity-50"
        >
          Save as Draft
        </button>
        
        <button
          type="button"
          onClick={handlePublish}
          disabled={isSubmitting || !canPublishNow}
          className={`
            px-8 py-3 rounded-xl font-semibold transition-colors
            ${canPublishNow 
              ? 'bg-green-600 text-white hover:bg-green-700' 
              : 'bg-gray-200 text-gray-500 cursor-not-allowed'
            }
            disabled:opacity-50
          `}
        >
          {isSubmitting ? 'Publishing...' : '✓ Publish'}
        </button>
      </div>
      
      {!canPublishNow && (
        <p className="text-sm text-amber-600 text-center">
          Complete required steps ({missingSteps.map(s => stepNames[s - 1]).join(', ')}) to publish
        </p>
      )}
    </div>
  );
}

export default ReviewStep;
