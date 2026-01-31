'use client';

/**
 * Exercise Creation Wizard Page
 * =============================
 * 
 * 7-step wizard for creating exercises with state-based JSON schema.
 * 
 * Steps:
 * 1. Basic Info + Counting Method
 * 2. Camera Position
 * 3. Joint Configuration (State-based ranges)
 * 4. Position Checks (optional)
 * 5. Rep/Duration Config
 * 6. Extras (attributes + feedback)
 * 7. Review & Publish
 */

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { useWizardStore } from '@/components/wizard/WizardContext';
import { WizardStepper } from '@/components/wizard/WizardStepper';
import { AutoSaveIndicator } from '@/components/wizard/AutoSaveIndicator';
import {
  BasicInfoStep,
  CameraPositionStep,
  JointConfigStep,
  PositionChecksStep,
  RepConfigStep,
  ExtrasStep,
  ReviewStep,
} from '@/components/wizard/steps';
import type { TrackedJointData, PositionCheckData } from '@/modules/exercises/exercises.validation';

const TOTAL_STEPS = 7;

interface LookupData {
  categories: Array<{ id: string; code: string; name: { ar: string; en: string } }>;
  countingMethods: Array<{ id: string; code: string; name: { ar: string; en: string }; description?: { ar: string; en: string } }>;
  cameraPositions: Array<{ id: string; code: string; name: { ar: string; en: string }; description?: { ar: string; en: string }; imageUrl?: string }>;
  joints: Array<{ id: string; code: string; name: { ar: string; en: string } }>;
  muscles: Array<{ id: string; code: string; name: { ar: string; en: string } }>;
  equipment: Array<{ id: string; code: string; name: { ar: string; en: string } }>;
  tags: Array<{ id: string; code: string; name: { ar: string; en: string } }>;
}

export default function NewExercisePage() {
  const router = useRouter();
  const { 
    currentStep, 
    setStep, 
    resetWizard, 
    exerciseId, 
    setSaveStatus, 
    markAsSaved, 
    setExerciseId 
  } = useWizardStore();
  
  const [lookupData, setLookupData] = useState<LookupData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  // Fetch lookup data
  useEffect(() => {
    async function fetchLookupData() {
      try {
        const response = await fetch('/api/attributes/lookup');
        if (!response.ok) throw new Error('Failed to fetch lookup data');
        const data = await response.json();
        setLookupData(data.data);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'An error occurred');
      } finally {
        setLoading(false);
      }
    }
    
    fetchLookupData();
  }, []);
  
  // Build API payload from store
  const buildPayload = useCallback(() => {
    const store = useWizardStore.getState();
    const isHold = store.countingMethod.countingMethodCode === 'hold';
    
    // Build tracked joints for API
    const trackedJointsConfig = (store.jointConfig.trackedJoints || []).map((joint: TrackedJointData) => {
      if (joint.role === 'primary') {
        return {
          joint: joint.joint,
          role: 'primary',
          startPose: joint.startPose,
          upRange: joint.upRange,
          downRange: joint.downRange,
          stateMessages: joint.stateMessages,
          pairedWith: joint.pairedWith,
          invertIndicator: joint.invertIndicator,
        };
      } else {
        return {
          joint: joint.joint,
          role: 'secondary',
          startPose: joint.startPose,
          range: joint.range,
          stateMessages: joint.stateMessages,
          pairedWith: joint.pairedWith,
        };
      }
    });
    
    // Build position checks
    const positionChecks = (store.positionChecks.positionChecks || []).map((pc: PositionCheckData, idx: number) => ({
      checkId: pc.checkId,
      type: pc.type,
      landmarks: pc.landmarks,
      condition: pc.condition,
      activePhases: pc.activePhases,
      errorMessage: pc.errorMessage,
      severity: pc.severity,
      cooldownMs: pc.cooldownMs,
      minErrorFrames: pc.minErrorFrames,
      sortOrder: idx + 1,
    }));
    
    // Build feedback messages
    const feedbackMessages = (store.extras.feedbackMessages || []).map((fm, idx) => ({
      type: fm.type,
      message: fm.message,
      sortOrder: idx + 1,
    }));
    
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
      name: store.basicInfo.name,
      description: store.basicInfo.description,
      instructions: store.basicInfo.instructions,
      categoryId: store.basicInfo.categoryId,
      countingMethodId: store.countingMethod.countingMethodId,
      imageUrl: store.basicInfo.imageUrl || undefined,
      muscles: store.extras.muscles,
      equipment: store.extras.equipment,
      tags: store.extras.tags,
      repCountingConfig,
      poseVariants: store.cameraPosition.cameraPositionIds?.map((cameraPositionId, index) => ({
        name: store.basicInfo.name,
        cameraPositionId,
        expectedFacingDirection: store.cameraPosition.expectedFacingDirection,
        referenceImageUrl: store.cameraPosition.referenceImages?.[cameraPositionId] || undefined,
        trackedJointsConfig,
        positionChecks,
        feedbackMessages,
        sortOrder: index + 1,
      })),
    };
  }, []);
  
  // Auto-save on step change
  const handleStepChange = useCallback(async (newStep: number) => {
    if (newStep < 1 || newStep > TOTAL_STEPS) return;
    
    const store = useWizardStore.getState();
    
    // Only auto-save if dirty and has basic info
    if (store.isDirty && store.basicInfo.name?.en) {
      setSaveStatus('saving');
      
      try {
        if (store.exerciseId) {
          await fetch(`/api/exercises/${store.exerciseId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(buildPayload()),
          });
        } else {
          const response = await fetch('/api/exercises', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(buildPayload()),
          });
          const data = await response.json();
          if (data.data?.id) {
            setExerciseId(data.data.id);
          }
        }
        markAsSaved();
      } catch (err) {
        setSaveStatus('error', err instanceof Error ? err.message : 'Save failed');
      }
    }
    
    setStep(newStep);
  }, [setSaveStatus, markAsSaved, setExerciseId, setStep, buildPayload]);
  
  // Save as draft
  const handleSaveDraft = async () => {
    setSaveStatus('saving');
    
    try {
      if (exerciseId) {
        await fetch(`/api/exercises/${exerciseId}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(buildPayload()),
        });
      } else {
        const response = await fetch('/api/exercises', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(buildPayload()),
        });
        const data = await response.json();
        if (data.data?.id) {
          setExerciseId(data.data.id);
        }
      }
      markAsSaved();
    } catch (err) {
      setSaveStatus('error', err instanceof Error ? err.message : 'Save failed');
    }
  };
  
  // Publish
  const handlePublish = async () => {
    setSaveStatus('saving');
    
    try {
      let id = exerciseId;
      
      if (!id) {
        const response = await fetch('/api/exercises', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(buildPayload()),
        });
        const data = await response.json();
        id = data.data?.id;
        if (id) setExerciseId(id);
      } else {
        await fetch(`/api/exercises/${id}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(buildPayload()),
        });
      }
      
      if (id) {
        await fetch(`/api/exercises/${id}/publish`, { method: 'POST' });
        markAsSaved();
        resetWizard();
        router.push('/admin/exercises');
      }
    } catch (err) {
      setSaveStatus('error', err instanceof Error ? err.message : 'Publish failed');
    }
  };
  
  // Render current step
  const renderStep = () => {
    if (!lookupData) return null;
    
    switch (currentStep) {
      case 1:
        return (
          <BasicInfoStep 
            categories={lookupData.categories} 
            countingMethods={lookupData.countingMethods} 
          />
        );
      case 2:
        return <CameraPositionStep cameraPositions={lookupData.cameraPositions} />;
      case 3:
        return <JointConfigStep />;
      case 4:
        return <PositionChecksStep />;
      case 5:
        return <RepConfigStep />;
      case 6:
        return (
          <ExtrasStep 
            muscles={lookupData.muscles} 
            equipment={lookupData.equipment} 
            tags={lookupData.tags} 
          />
        );
      case 7:
        return <ReviewStep />;
      default:
        return (
          <BasicInfoStep 
            categories={lookupData.categories} 
            countingMethods={lookupData.countingMethods} 
          />
        );
    }
  };
  
  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600" />
      </div>
    );
  }
  
  if (error) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-center">
          <p className="text-red-600 mb-4">{error}</p>
          <button 
            onClick={() => window.location.reload()}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
          >
            Retry
          </button>
        </div>
      </div>
    );
  }
  
  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b sticky top-0 z-10">
        <div className="w-full px-4 sm:px-6 lg:px-8 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <button
                onClick={() => {
                  if (confirm('Discard changes and go back?')) {
                    resetWizard();
                    router.push('/admin/exercises');
                  }
                }}
                className="p-2 hover:bg-gray-100 rounded-lg"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
                </svg>
              </button>
              <div>
                <h1 className="text-xl font-bold text-gray-900">Create Exercise</h1>
                <p className="text-sm text-gray-500">State-based configuration</p>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <AutoSaveIndicator />
              <button
                onClick={handleSaveDraft}
                className="px-4 py-2 text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200"
              >
                Save Draft
              </button>
              {currentStep === TOTAL_STEPS && (
                <button
                  onClick={handlePublish}
                  className="px-4 py-2 text-white bg-green-600 rounded-lg hover:bg-green-700"
                >
                  Publish
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
      
      {/* Stepper */}
      <div className="bg-white border-b">
        <div className="w-full px-4 sm:px-6 lg:px-8 py-4">
          <WizardStepper 
            currentStep={currentStep}
            totalSteps={TOTAL_STEPS}
            onStepClick={handleStepChange}
            stepLabels={[
              'Basic Info',
              'Camera',
              'Joints',
              'Checks',
              'Reps',
              'Extras',
              'Review',
            ]}
          />
        </div>
      </div>
      
      {/* Content */}
      <div className="w-full px-4 sm:px-6 lg:px-8 py-8">
        {renderStep()}
      </div>
      
      {/* Footer Navigation */}
      <div className="fixed bottom-0 left-0 right-0 bg-white border-t p-4">
        <div className="w-full px-4 sm:px-6 lg:px-8 flex justify-between">
          <button
            onClick={() => handleStepChange(currentStep - 1)}
            disabled={currentStep === 1}
            className={`px-6 py-2 rounded-lg transition-colors ${
              currentStep === 1 
                ? 'bg-gray-100 text-gray-400 cursor-not-allowed' 
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
          >
            ← Previous
          </button>
          
          <div className="text-sm text-gray-500">
            Step {currentStep} of {TOTAL_STEPS}
          </div>
          
          <button
            onClick={() => handleStepChange(currentStep + 1)}
            disabled={currentStep === TOTAL_STEPS}
            className={`px-6 py-2 rounded-lg transition-colors ${
              currentStep === TOTAL_STEPS 
                ? 'bg-gray-100 text-gray-400 cursor-not-allowed' 
                : 'bg-blue-600 text-white hover:bg-blue-700'
            }`}
          >
            Next →
          </button>
        </div>
      </div>
    </div>
  );
}
