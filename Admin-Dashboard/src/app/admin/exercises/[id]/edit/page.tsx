'use client';

/**
 * Exercise Edit Page (State-Based System)
 * =======================================
 * 
 * Edit existing exercise using the 7-step wizard.
 * Loads exercise data and maps to wizard state.
 */

import { useEffect, useState, useCallback } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { useWizardStore } from '@/components/wizard/WizardContext';
import type { LocalizedText } from '@/lib/types/localized';
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

export default function EditExercisePage() {
  const router = useRouter();
  const params = useParams();
  const exerciseId = params.id as string;
  
  const { 
    currentStep, 
    setStep, 
    resetWizard, 
    loadExercise,
    setSaveStatus, 
    markAsSaved, 
    setExerciseId,
    exerciseStatus,
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
      // Ignore JSON parsing errors and use fallback
    }
    return fallbackMessage;
  };
  
  // Fetch lookup data and exercise
  useEffect(() => {
    async function fetchData() {
      try {
        // Fetch lookup data
        const lookupResponse = await fetch('/api/attributes/lookup');
        if (!lookupResponse.ok) throw new Error('Failed to fetch lookup data');
        const lookupJson = await lookupResponse.json();
        setLookupData(lookupJson.data);
        
        // Fetch exercise data
        const exerciseResponse = await fetch(`/api/exercises/${exerciseId}`);
        if (!exerciseResponse.ok) throw new Error('Exercise not found');
        const exerciseJson = await exerciseResponse.json();
        
        if (!exerciseJson.success || !exerciseJson.data) {
          throw new Error('Exercise not found');
        }
        
        const exercise = exerciseJson.data;
        
        // Map exercise data to wizard state
        const firstVariant = exercise.poseVariants?.[0];
        const primaryImageUrl = exercise.media?.find((m: { isPrimary: boolean; type: string }) => 
          m.isPrimary && m.type === 'image'
        )?.url || '';
        const repConfig = exercise.repCountingConfig as Record<string, number> | null;
        const isHold = exercise.countingMethod?.code === 'hold';
        
        // Map tracked joints per variant
        const trackedJoints = (firstVariant?.trackedJointsConfig as TrackedJointData[]) || [];
        const jointConfigVariants = (exercise.poseVariants || []).reduce(
          (acc: Record<number, TrackedJointData[]>, variant: { trackedJointsConfig?: TrackedJointData[] }, index: number) => {
            acc[index] = (variant.trackedJointsConfig as TrackedJointData[]) || [];
            return acc;
          },
          {}
        );
        
        // Map position checks
        const positionChecks = (firstVariant?.positionChecks || []).map((pc: Record<string, unknown>) => ({
          checkId: pc.checkId as string,
          type: pc.type as string,
          landmarks: pc.landmarks as { primary: string; secondary: string; tertiary?: string; quaternary?: string },
          condition: pc.condition as { operator: string; threshold: number },
          activePhases: pc.activePhases as string[],
          errorMessage: pc.errorMessage as { ar: string; en: string },
          severity: pc.severity as string || 'warning',
          cooldownMs: (pc.cooldownMs as number) || 2000,
          minErrorFrames: (pc.minErrorFrames as number) || 3,
        }));
        
        // Map feedback assignments (library-based)
        const feedbackAssignments = (firstVariant?.messageAssignments || [])
          .filter((assignment: Record<string, unknown>) => assignment.target === 'feedback')
          .map((assignment: Record<string, unknown>) => ({
            messageId: assignment.messageId as string,
            context: assignment.context as 'motivational' | 'tip',
            message: (assignment.message as { content?: { ar?: string; en?: string; audioAr?: string; audioEn?: string } } | undefined)?.content,
          }));
        
        // Map attributes
        const muscles = exercise.attributes
          ?.filter((a: { attributeValue?: { attribute?: { code: string } } }) => 
            a.attributeValue?.attribute?.code === 'muscle')
          .map((a: { attributeValueId: string }) => a.attributeValueId) || [];
        
        const equipment = exercise.attributes
          ?.filter((a: { attributeValue?: { attribute?: { code: string } } }) => 
            a.attributeValue?.attribute?.code === 'equipment')
          .map((a: { attributeValueId: string }) => a.attributeValueId) || [];
        
        const tags = exercise.attributes
          ?.filter((a: { attributeValue?: { attribute?: { code: string } } }) => 
            a.attributeValue?.attribute?.code === 'tag')
          .map((a: { attributeValueId: string }) => a.attributeValueId) || [];
        
        // Load into wizard state
        const referenceImages = (exercise.poseVariants || []).reduce(
          (acc: Record<string, string>, pv: { cameraPositionId: string; referenceImageUrl?: string | null }) => {
            if (pv.referenceImageUrl) {
              acc[pv.cameraPositionId] = pv.referenceImageUrl;
            }
            return acc;
          },
          {}
        );
        loadExercise({
          exerciseId: exercise.id,
          exerciseStatus: exercise.status,
          currentStep: 1,
          basicInfo: {
            name: exercise.name || { ar: '', en: '' },
            description: exercise.description || { ar: '', en: '' },
            instructions: exercise.instructions || { ar: '', en: '' },
            categoryId: exercise.categoryId || '',
            imageUrl: primaryImageUrl,
          },
          countingMethod: {
            countingMethodId: exercise.countingMethodId || '',
            countingMethodCode: exercise.countingMethod?.code,
          },
          cameraPosition: {
            cameraPositionIds: exercise.poseVariants?.map((pv: { cameraPositionId: string }) => pv.cameraPositionId) || [],
            expectedFacingDirection: firstVariant?.expectedFacingDirection || 'auto_detect',
            referenceImages,
          },
          jointConfig: {
            trackedJoints,
          },
          jointConfigVariants,
          activeJointVariantIndex: 0,
          positionChecks: {
            positionChecks,
          },
          repConfig: isHold
            ? {
                duration: repConfig?.duration || 30,
                gracePeriodMs: repConfig?.gracePeriodMs || 2500,
              }
            : {
                reps: repConfig?.reps || 12,
                minRepIntervalMs: repConfig?.minRepIntervalMs || 1500,
                maxRepIntervalMs: repConfig?.maxRepIntervalMs || 5000,
              },
          extras: {
            muscles,
            equipment,
            tags,
            feedbackAssignments,
          },
          // Weight configuration
          weightConfig: {
            supportsWeight: exercise.supportsWeight ?? false,
            minWeight: exercise.minWeight ?? undefined,
            maxWeight: exercise.maxWeight ?? undefined,
            defaultWeight: exercise.defaultWeight ?? undefined,
          },
          // Report metrics configuration
          reportMetrics: {
            primary: ((exercise.reportMetrics as Record<string, string[]> | null)?.primary ?? ['form_score']) as import('@/modules/exercises/exercises.types').MetricCode[],
            optional: ((exercise.reportMetrics as Record<string, string[]> | null)?.optional ?? []) as import('@/modules/exercises/exercises.types').MetricCode[],
            excluded: ((exercise.reportMetrics as Record<string, string[]> | null)?.excluded ?? []) as import('@/modules/exercises/exercises.types').MetricCode[],
          },
          alternatingConfig: {
            enabled: Boolean(exercise.isAlternating),
            switchEvery: (exercise.alternatingConfig as { switchEvery?: number } | null)?.switchEvery || 1,
            variants:
              (exercise.alternatingConfig as { variants?: Array<{ label?: LocalizedText; variantIndex?: number }> } | null)
                ?.variants?.map((variant) => ({
                  label: variant.label || { ar: '', en: '' },
                  variantIndex: variant.variantIndex ?? 0,
                })) || [],
          },
        });
        
        setExerciseId(exercise.id);
        
      } catch (err) {
        setError(err instanceof Error ? err.message : 'An error occurred');
      } finally {
        setLoading(false);
      }
    }
    
    fetchData();
  }, [exerciseId, loadExercise, setExerciseId]);
  
  // Build API payload from store
  const buildPayload = useCallback(() => {
    const store = useWizardStore.getState();
    const isHold = store.countingMethod.countingMethodCode === 'hold';
    
    // Build tracked joints for API
    const trackedJointsConfig = (store.jointConfig.trackedJoints || []).map((joint: TrackedJointData) => {
      if (joint.role === 'primary') {
        if (isHold) {
          return {
            joint: joint.joint,
            role: 'primary',
            startPose: joint.startPose,
            range: joint.range,
            stateMessages: joint.stateMessages,
            pairedWith: joint.pairedWith,
            invertIndicator: joint.invertIndicator,
          };
        }

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
    
    // Build feedback message assignments (library-based)
    const feedbackAssignments = (store.extras.feedbackAssignments || []).map((assignment, idx) => ({
      messageId: assignment.messageId,
      target: 'feedback',
      context: assignment.context,
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
    
    const jointVariants = store.alternatingConfig.enabled
      ? {
          ...store.jointConfigVariants,
          [store.activeJointVariantIndex]: store.jointConfig.trackedJoints || [],
        }
      : {};

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
      poseVariants: store.cameraPosition.cameraPositionIds?.map((cameraPositionId, index) => {
        const jointsForVariant = store.alternatingConfig.enabled
          ? (jointVariants[index] || [])
          : (store.jointConfig.trackedJoints || []);
        const mappedJoints = jointsForVariant.map((joint: TrackedJointData) => {
          if (joint.role === 'primary') {
            if (isHold) {
              return {
                joint: joint.joint,
                role: 'primary',
                startPose: joint.startPose,
                range: joint.range,
                stateMessages: joint.stateMessages,
                pairedWith: joint.pairedWith,
                invertIndicator: joint.invertIndicator,
              };
            }

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
          }
          return {
            joint: joint.joint,
            role: 'secondary',
            startPose: joint.startPose,
            range: joint.range,
            stateMessages: joint.stateMessages,
            pairedWith: joint.pairedWith,
          };
        });
        return {
          name: store.basicInfo.name,
          cameraPositionId,
          expectedFacingDirection: store.cameraPosition.expectedFacingDirection,
          referenceImageUrl: store.cameraPosition.referenceImages?.[cameraPositionId] || undefined,
          trackedJointsConfig: mappedJoints.length > 0 ? mappedJoints : trackedJointsConfig,
          positionChecks,
          messageAssignments: feedbackAssignments.length > 0 ? feedbackAssignments : undefined,
          sortOrder: index + 1,
        };
      }),
      // Weight configuration
      supportsWeight: store.weightConfig.supportsWeight,
      minWeight: store.weightConfig.minWeight,
      maxWeight: store.weightConfig.maxWeight,
      defaultWeight: store.weightConfig.defaultWeight,
      // Report metrics configuration
      reportMetrics: {
        primary: store.reportMetrics.primary,
        optional: store.reportMetrics.optional,
        excluded: store.reportMetrics.excluded,
      },
      alternatingConfig: store.alternatingConfig.enabled && store.alternatingConfig.variants.length > 0
        ? {
            switchEvery: store.alternatingConfig.switchEvery,
            variants: store.alternatingConfig.variants.map((variant) => ({
              label: variant.label,
              variantIndex: variant.variantIndex,
            })),
          }
        : undefined,
    };
  }, []);
  
  // Handle step change with auto-save
  const handleStepChange = useCallback(async (newStep: number) => {
    if (newStep < 1 || newStep > TOTAL_STEPS) return;
    
    const store = useWizardStore.getState();
    
    // Auto-save if dirty
    if (store.isDirty && store.exerciseId) {
      setSaveStatus('saving');
      
      try {
        const response = await fetch(`/api/exercises/${store.exerciseId}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(buildPayload()),
        });
        if (!response.ok) {
          throw new Error(await extractApiError(response, 'Save failed'));
        }
        const data = await response.json();
        if (!data?.success) {
          throw new Error(data?.error || 'Save failed');
        }
        markAsSaved();
      } catch (err) {
        setSaveStatus('error', err instanceof Error ? err.message : 'Save failed');
      }
    }
    
    setStep(newStep);
  }, [setSaveStatus, markAsSaved, setStep, buildPayload]);
  
  // Save changes
  const handleSave = async () => {
    setSaveStatus('saving');
    
    try {
      const response = await fetch(`/api/exercises/${exerciseId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(buildPayload()),
      });
      if (!response.ok) {
        throw new Error(await extractApiError(response, 'Save failed'));
      }
      const data = await response.json();
      if (!data?.success) {
        throw new Error(data?.error || 'Save failed');
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
      // Save first
      const saveResponse = await fetch(`/api/exercises/${exerciseId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(buildPayload()),
      });
      if (!saveResponse.ok) {
        throw new Error(await extractApiError(saveResponse, 'Save failed'));
      }
      const saveData = await saveResponse.json();
      if (!saveData?.success) {
        throw new Error(saveData?.error || 'Save failed');
      }
      
      // Then publish
      const publishResponse = await fetch(`/api/exercises/${exerciseId}/publish`, { method: 'PUT' });
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
            onClick={() => router.push('/admin/exercises')}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
          >
            Back to Exercises
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
                  resetWizard();
                  router.push('/admin/exercises');
                }}
                className="p-2 hover:bg-gray-100 rounded-lg"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
                </svg>
              </button>
              <div>
                <div className="flex items-center gap-2">
                  <h1 className="text-xl font-bold text-gray-900">Edit Exercise</h1>
                  <span className={`text-xs px-2 py-1 rounded ${
                    exerciseStatus === 'published' ? 'bg-green-100 text-green-700' : 'bg-yellow-100 text-yellow-700'
                  }`}>
                    {exerciseStatus === 'published' ? 'Published' : 'Draft'}
                  </span>
                </div>
                <p className="text-sm text-gray-500">State-based configuration</p>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <AutoSaveIndicator />
              <button
                onClick={handleSave}
                className="px-4 py-2 text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200"
              >
                Save
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
