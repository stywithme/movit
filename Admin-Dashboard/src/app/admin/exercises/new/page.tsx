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
import { WizardHeader } from '@/components/wizard/WizardHeader';
import { WizardFooter } from '@/components/wizard/WizardFooter';
import {
  BasicInfoStep,
  CameraPositionStep,
  JointConfigStep,
  PositionChecksStep,
  RepConfigStep,
  ExtrasStep,
  ReviewStep,
} from '@/components/wizard/steps';
import { buildExercisePayload } from '@/modules/exercises/build-payload';

const TOTAL_STEPS = 7;

interface LookupData {
  categories: Array<{ id: string; code: string; name: { ar: string; en: string } }>;
  countingMethods: Array<{ id: string; code: string; name: { ar: string; en: string }; description?: { ar: string; en: string } }>;
  posePositions?: Array<{
    id: string;
    code: string;
    name: { ar: string; en: string };
    description?: { ar: string; en: string };
    imageUrl?: string;
    postures?: string[];
    directions?: string[];
    regions?: string[];
  }>;
  cameraPositions?: Array<{
    id: string;
    code: string;
    name: { ar: string; en: string };
    description?: { ar: string; en: string };
    imageUrl?: string;
    postures?: string[];
    directions?: string[];
    regions?: string[];
  }>;
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
    setExerciseId,
    saveStatus,
  } = useWizardStore();

  const [lookupData, setLookupData] = useState<LookupData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const extractApiError = async (response: Response, fallbackMessage: string) => {
    try {
      const data = await response.json();
      if (typeof data?.error === 'string' && data.error.trim()) return data.error;
      if (Array.isArray(data?.errors) && data.errors.length > 0) return data.errors.join(', ');
    } catch {
      // fallback
    }
    return fallbackMessage;
  };

  useEffect(() => {
    resetWizard();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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

  /** Create or update the exercise draft */
  const saveOrCreate = useCallback(async (): Promise<string | null> => {
    const store = useWizardStore.getState();
    const payload = buildExercisePayload();

    if (store.exerciseId) {
      const res = await fetch(`/api/exercises/${store.exerciseId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (!res.ok) throw new Error(await extractApiError(res, 'Save failed'));
      const json = await res.json();
      if (!json?.success) throw new Error(json?.error || 'Save failed');
      return store.exerciseId;
    }

    const res = await fetch('/api/exercises', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error(await extractApiError(res, 'Save failed'));
    const json = await res.json();
    if (!json?.success) throw new Error(json?.error || 'Save failed');
    const newId = json.data?.id as string | undefined;
    if (newId) setExerciseId(newId);
    return newId ?? null;
  }, [setExerciseId]);

  const handleStepChange = useCallback(async (newStep: number) => {
    if (newStep < 1 || newStep > TOTAL_STEPS) return;

    const store = useWizardStore.getState();

    if (store.isDirty && store.basicInfo.name?.en) {
      setSaveStatus('saving');
      try {
        await saveOrCreate();
        markAsSaved();
      } catch (err) {
        setSaveStatus('error', err instanceof Error ? err.message : 'Save failed');
      }
    }

    setStep(newStep);
  }, [setSaveStatus, markAsSaved, setStep, saveOrCreate]);

  const handleSaveDraft = async () => {
    setSaveStatus('saving');
    try {
      await saveOrCreate();
      markAsSaved();
    } catch (err) {
      setSaveStatus('error', err instanceof Error ? err.message : 'Save failed');
    }
  };

  const handlePublish = async () => {
    setSaveStatus('saving');
    try {
      const id = await saveOrCreate();

      if (id) {
        const publishResponse = await fetch(`/api/exercises/${id}/publish`, { method: 'PUT' });
        if (!publishResponse.ok) {
          throw new Error(await extractApiError(publishResponse, 'Publish failed'));
        }
        const publishData = await publishResponse.json();
        if (!publishData?.success) {
          throw new Error(publishData?.error || 'Publish failed');
        }
        markAsSaved();
        resetWizard();
        router.push('/admin/exercises');
      }
    } catch (err) {
      setSaveStatus('error', err instanceof Error ? err.message : 'Publish failed');
    }
  };

  const renderStep = () => {
    if (!lookupData) return null;

    switch (currentStep) {
      case 1:
        return <BasicInfoStep categories={lookupData.categories} countingMethods={lookupData.countingMethods} />;
      case 2:
        return <CameraPositionStep cameraPositions={lookupData.posePositions || lookupData.cameraPositions || []} />;
      case 3:
        return <JointConfigStep />;
      case 4:
        return <PositionChecksStep />;
      case 5:
        return <RepConfigStep />;
      case 6:
        return <ExtrasStep muscles={lookupData.muscles} equipment={lookupData.equipment} tags={lookupData.tags} />;
      case 7:
        return <ReviewStep />;
      default:
        return <BasicInfoStep categories={lookupData.categories} countingMethods={lookupData.countingMethods} />;
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
      <WizardHeader
        title="Create Exercise"
        onBack={() => {
          if (confirm('Discard changes and go back?')) {
            resetWizard();
            router.push('/admin/exercises');
          }
        }}
        onSave={handleSaveDraft}
        onPublish={handlePublish}
        isSaving={saveStatus === 'saving'}
      />

      <div className="bg-white border-b">
        <div className="w-full px-4 sm:px-6 lg:px-8 py-4">
          <WizardStepper
            currentStep={currentStep}
            totalSteps={TOTAL_STEPS}
            onStepClick={handleStepChange}
            stepLabels={['Basic Info', 'Pose Axes', 'Joints', 'Checks', 'Reps', 'Extras', 'Review']}
          />
        </div>
      </div>

      <div className="w-full px-4 sm:px-6 lg:px-8 py-8">
        {renderStep()}
      </div>

      <WizardFooter
        currentStep={currentStep}
        totalSteps={TOTAL_STEPS}
        onPrevious={() => handleStepChange(currentStep - 1)}
        onNext={() => handleStepChange(currentStep + 1)}
      />
    </div>
  );
}
