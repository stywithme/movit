'use client';

/**
 * Exercise Creation Wizard Page
 * =============================
 * 
 * 8-step wizard for creating exercises that match the Android JSON schema.
 */

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { useWizardStore } from '@/components/wizard/WizardContext';
import { WizardStepper } from '@/components/wizard/WizardStepper';
import { AutoSaveIndicator } from '@/components/wizard/AutoSaveIndicator';
import {
  BasicInfoStep,
  CountingMethodStep,
  CameraPositionStep,
  JointConfigStep,
  PositionChecksStep,
  RepConfigStep,
  ExtrasStep,
  ReviewStep,
} from '@/components/wizard/steps';

// Lookup data types
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
  const { currentStep, setStep, nextStep, prevStep, resetWizard, exerciseId, setSaveStatus, markAsSaved, setExerciseId } = useWizardStore();
  
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
  
  // Reset wizard on unmount
  useEffect(() => {
    return () => {
      // Optionally reset on leave - commented out to preserve state
      // resetWizard();
    };
  }, []);
  
  // Auto-save on step change
  const handleStepChange = useCallback(async (newStep: number) => {
    const store = useWizardStore.getState();
    
    // Only auto-save if dirty and has basic info
    if (store.isDirty && store.basicInfo.name?.en) {
      setSaveStatus('saving');
      
      try {
        if (store.exerciseId) {
          // Update existing draft
          await fetch(`/api/exercises/${store.exerciseId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(buildPayload(store)),
          });
        } else {
          // Create new draft
          const response = await fetch('/api/exercises', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(buildPayload(store)),
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
  }, [setSaveStatus, markAsSaved, setExerciseId, setStep]);
  
  // Build API payload from store
  const buildPayload = (store: ReturnType<typeof useWizardStore.getState>) => {
    return {
      name: store.basicInfo.name,
      description: store.basicInfo.description,
      instructions: store.basicInfo.instructions,
      categoryId: store.basicInfo.categoryId,
      countingMethodId: store.countingMethod.countingMethodId,
      muscles: store.extras.muscles,
      equipment: store.extras.equipment,
      tags: store.extras.tags,
      poseVariants: store.cameraPosition.cameraPositionIds?.map((cameraPositionId, index) => ({
        name: store.basicInfo.name,
        cameraPositionId,
        expectedFacingDirection: store.cameraPosition.expectedFacingDirection,
        trackedJointsConfig: store.jointConfig.trackedJoints,
        positionChecks: store.positionChecks.positionChecks?.map((pc, idx) => ({
          ...pc,
          sortOrder: idx + 1,
        })),
        feedbackMessages: store.extras.feedbackMessages,
        difficultyLevels: ['beginner', 'normal', 'advanced'].map((level, dlIndex) => ({
          difficultyTypeCode: level,
          name: { ar: level === 'beginner' ? 'مبتدئ' : level === 'normal' ? 'عادي' : 'محترف', en: level.charAt(0).toUpperCase() + level.slice(1) },
          repCountingConfig: store.repConfig[level as keyof typeof store.repConfig],
        })),
        sortOrder: index + 1,
      })),
    };
  };
  
  // Save as draft
  const handleSaveDraft = async () => {
    const store = useWizardStore.getState();
    setSaveStatus('saving');
    
    try {
      if (exerciseId) {
        await fetch(`/api/exercises/${exerciseId}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(buildPayload(store)),
        });
      } else {
        const response = await fetch('/api/exercises', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(buildPayload(store)),
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
    const store = useWizardStore.getState();
    setSaveStatus('saving');
    
    try {
      let id = exerciseId;
      
      // Save first if needed
      if (!id) {
        const response = await fetch('/api/exercises', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(buildPayload(store)),
        });
        const data = await response.json();
        id = data.data?.id;
        if (id) setExerciseId(id);
      } else {
        await fetch(`/api/exercises/${id}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(buildPayload(store)),
        });
      }
      
      // Publish
      if (id) {
        await fetch(`/api/exercises/${id}/publish`, {
          method: 'POST',
        });
        
        markAsSaved();
        resetWizard();
        router.push('/admin/exercises');
      }
    } catch (err) {
      setSaveStatus('error', err instanceof Error ? err.message : 'Publish failed');
    }
  };
  
  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="w-12 h-12 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-4" />
          <p className="text-gray-500">Loading...</p>
        </div>
      </div>
    );
  }
  
  if (error || !lookupData) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <p className="text-red-500 mb-4">{error || 'Failed to load data'}</p>
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
      <header className="bg-white border-b sticky top-0 z-40">
        <div className="max-w-7xl mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <button
              onClick={() => router.push('/admin/exercises')}
              className="text-gray-500 hover:text-gray-700"
            >
              ← Back
            </button>
            <h1 className="text-xl font-bold text-gray-900">Create Exercise</h1>
          </div>
          <AutoSaveIndicator />
        </div>
      </header>
      
      {/* Stepper */}
      <div className="bg-white border-b">
        <WizardStepper />
      </div>
      
      {/* Content */}
      <main className="max-w-7xl mx-auto px-4 py-8">
        {currentStep === 1 && (
          <BasicInfoStep categories={lookupData.categories} />
        )}
        {currentStep === 2 && (
          <CountingMethodStep countingMethods={lookupData.countingMethods} />
        )}
        {currentStep === 3 && (
          <CameraPositionStep cameraPositions={lookupData.cameraPositions} />
        )}
        {currentStep === 4 && (
          <JointConfigStep joints={lookupData.joints} />
        )}
        {currentStep === 5 && (
          <PositionChecksStep />
        )}
        {currentStep === 6 && (
          <RepConfigStep />
        )}
        {currentStep === 7 && (
          <ExtrasStep 
            muscles={lookupData.muscles} 
            equipment={lookupData.equipment} 
            tags={lookupData.tags} 
          />
        )}
        {currentStep === 8 && (
          <ReviewStep onSaveDraft={handleSaveDraft} onPublish={handlePublish} />
        )}
      </main>
      
      {/* Footer Navigation */}
      {currentStep < 8 && (
        <footer className="fixed bottom-0 left-0 right-0 bg-white border-t py-4 px-4">
          <div className="max-w-7xl mx-auto flex items-center justify-between">
            <button
              type="button"
              onClick={() => handleStepChange(currentStep - 1)}
              disabled={currentStep === 1}
              className={`
                px-6 py-3 rounded-xl font-medium transition-colors
                ${currentStep === 1 
                  ? 'text-gray-300 cursor-not-allowed' 
                  : 'text-gray-700 hover:bg-gray-100'
                }
              `}
            >
              ← Previous
            </button>
            
            <span className="text-sm text-gray-500">
              Step {currentStep} of 8
            </span>
            
            <button
              type="button"
              onClick={() => handleStepChange(currentStep + 1)}
              className="px-6 py-3 bg-blue-600 text-white rounded-xl font-medium hover:bg-blue-700 transition-colors"
            >
              Next →
            </button>
          </div>
        </footer>
      )}
    </div>
  );
}
