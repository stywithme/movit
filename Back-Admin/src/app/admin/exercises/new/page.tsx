'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { WizardStepper } from '@/components/wizard/WizardStepper';
import { BasicInfoStep } from '@/components/wizard/steps/BasicInfoStep';
import { AttributesStep } from '@/components/wizard/steps/AttributesStep';
import { PoseVariantsStep } from '@/components/wizard/steps/PoseVariantsStep';
import { MovementConfigStep } from '@/components/wizard/steps/MovementConfigStep';
import { MessagesStep } from '@/components/wizard/steps/MessagesStep';
import { ReviewStep } from '@/components/wizard/steps/ReviewStep';
import { JsonPreview } from '@/components/JsonPreview';
import { LocalizedText } from '@/lib/types/localized';
import { 
  MovementConfig, 
  buildDifficultyLevelsFromMovementConfig,
  trackedJointsToStartPoseAngles,
  getPrimaryJointCodes,
} from '@/modules/exercises/exercises.types';

interface AttributeValue {
  id: string;
  code: string;
  name: LocalizedText;
  description?: LocalizedText | null;
}

interface CameraPosition {
  id: string;
  code: string;
  name: LocalizedText;
  description?: LocalizedText | null;
  imageUrl?: string | null;
  joints?: { id: string; code: string; name: LocalizedText }[];
}

interface PoseVariant {
  id: string;
  name: LocalizedText;
  description: LocalizedText;
  cameraPositionId: string;
  referenceImageUrl: string;
}

interface FeedbackMessage {
  id: string;
  type: string;
  message: LocalizedText;
}

// Simplified wizard: 6 steps
const WIZARD_STEPS = [
  { id: 'basic', title: 'Basic Info', description: 'Name, category' },
  { id: 'attributes', title: 'Attributes', description: 'Muscles, equipment' },
  { id: 'poses', title: 'Pose Variants', description: 'Camera positions' },
  { id: 'movement', title: 'Movement Config', description: 'Angles & reps' },
  { id: 'messages', title: 'Messages', description: 'Feedback' },
  { id: 'review', title: 'Review', description: 'Publish' },
];

// Fixed difficulty levels
const DIFFICULTY_LEVELS = ['beginner', 'normal', 'advanced'];

export default function NewExercisePage() {
  const router = useRouter();
  const [currentStep, setCurrentStep] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  // Attribute data
  const [categories, setCategories] = useState<AttributeValue[]>([]);
  const [countingMethods, setCountingMethods] = useState<AttributeValue[]>([]);
  const [muscles, setMuscles] = useState<AttributeValue[]>([]);
  const [equipment, setEquipment] = useState<AttributeValue[]>([]);
  const [tags, setTags] = useState<AttributeValue[]>([]);
  const [cameraPositions, setCameraPositions] = useState<CameraPosition[]>([]);
  const [joints, setJoints] = useState<AttributeValue[]>([]);

  // Form state
  const [basicInfo, setBasicInfo] = useState({
    name: { ar: '', en: '' },
    description: { ar: '', en: '' },
    instructions: { ar: '', en: '' },
    categoryId: '',
    countingMethodId: '',
  });

  const [attributes, setAttributes] = useState({
    muscles: [] as string[],
    equipment: [] as string[],
    tags: [] as string[],
  });

  const [poseVariants, setPoseVariants] = useState<PoseVariant[]>([]);
  const [movementConfigs, setMovementConfigs] = useState<Record<string, MovementConfig>>({});
  const [feedbackMessages, setFeedbackMessages] = useState<Record<string, FeedbackMessage[]>>({});

  const getCountingMethodCode = () => {
    const method = countingMethods.find((m) => m.id === basicInfo.countingMethodId);
    return method?.code || 'up_down';
  };

  useEffect(() => {
    const loadAttributes = async () => {
      try {
        const fetchValues = async (code: string) => {
          const res = await fetch(`/api/attributes/${code}/values`);
          const data = await res.json();
          if (data.success) {
            return (data.data.values || []).map((item: Record<string, unknown>) => ({
              ...item,
              name: typeof item.name === 'string' ? JSON.parse(item.name as string) : item.name,
              description: item.description && typeof item.description === 'string'
                ? JSON.parse(item.description as string)
                : item.description,
            }));
          }
          return [];
        };

        const fetchCameraPositions = async () => {
          const res = await fetch('/api/camera-positions');
          const data = await res.json();
          return data.success ? data.data : [];
        };

        const [cats, methods, muscleList, equipList, tagList, cameras, jointList] = await Promise.all([
          fetchValues('category'),
          fetchValues('counting_method'),
          fetchValues('muscle'),
          fetchValues('equipment'),
          fetchValues('tag'),
          fetchCameraPositions(),
          fetchValues('joint'),
        ]);

        setCategories(cats);
        setCountingMethods(methods);
        setMuscles(muscleList);
        setEquipment(equipList);
        setTags(tagList);
        setCameraPositions(cameras);
        setJoints(jointList);
      } catch (error) {
        console.error('Error loading attributes:', error);
      } finally {
        setLoading(false);
      }
    };

    loadAttributes();
  }, []);

  const handleNext = () => {
    if (currentStep < WIZARD_STEPS.length - 1) setCurrentStep(currentStep + 1);
  };

  const handlePrevious = () => {
    if (currentStep > 0) setCurrentStep(currentStep - 1);
  };

  // Build exercise data for saving
  const buildExerciseData = () => {
    const countingMethodCode = getCountingMethodCode();
    
    // Build difficulty levels from movement configs
    const difficultyLevels = poseVariants.flatMap((pv) => {
      const config = movementConfigs[pv.id];
      if (!config || config.trackedJoints.length === 0) return [];
      
      // Get feedback messages for this variant
      const variantMessages = feedbackMessages[pv.id] || [];
      const feedbackMessagesInput = variantMessages.map((msg) => ({
        type: msg.type,
        message: msg.message,
        audioUrl: undefined,
        sortOrder: undefined,
      }));

      // Build levels with phases, angle rules, and feedback messages
      const levels = buildDifficultyLevelsFromMovementConfig(
        pv.id,
        config,
        countingMethodCode,
        feedbackMessagesInput
      );

      return levels;
    });

    const exerciseData = {
      ...basicInfo,
      ...attributes,
      poseVariants: poseVariants.map((pv) => {
        const config = movementConfigs[pv.id];
        return {
          id: pv.id,
          tempId: pv.id,
          name: pv.name,
          description: pv.description,
          cameraPositionId: pv.cameraPositionId,
          referenceImageUrl: pv.referenceImageUrl,
          startPoseAngles: config ? trackedJointsToStartPoseAngles(config.trackedJoints) : {},
          primaryJoint: config ? getPrimaryJointCodes(config.trackedJoints).join(',') : '',
          // Store full tracked joints configuration for complete data
          trackedJointsConfig: config?.trackedJoints || [],
        };
      }),
      difficultyLevels,
    };

    // Log full data structure for debugging
    console.log('📦 Exercise Data Structure:', JSON.stringify(exerciseData, null, 2));
    
    return exerciseData;
  };

  const handleSaveDraft = async () => {
    setSaving(true);
    try {
      const exerciseData = buildExerciseData();
      const res = await fetch('/api/exercises', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(exerciseData),
      });

      const data = await res.json();
      if (data.success) {
        router.push(`/admin/exercises/${data.data.id}/edit`);
      } else {
        alert('Error saving: ' + data.error);
      }
    } catch (error) {
      console.error('Error saving:', error);
      alert('Error saving exercise');
    } finally {
      setSaving(false);
    }
  };

  const handlePublish = async () => {
    setSaving(true);
    try {
      const exerciseData = buildExerciseData();
      const createRes = await fetch('/api/exercises', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(exerciseData),
      });

      const createData = await createRes.json();
      if (!createData.success) {
        alert('Error creating: ' + createData.error);
        return;
      }

      const publishRes = await fetch(`/api/exercises/${createData.data.id}/publish`, { method: 'POST' });
      
      // Check if response is ok
      if (!publishRes.ok) {
        const errorText = await publishRes.text();
        console.error('Publish failed:', publishRes.status, errorText);
        alert(`Error publishing: ${publishRes.status} ${publishRes.statusText}`);
        return;
      }

      // Check if response has content
      const contentType = publishRes.headers.get('content-type');
      if (!contentType || !contentType.includes('application/json')) {
        const text = await publishRes.text();
        console.error('Invalid response format:', text);
        alert('Error: Invalid response from server');
        return;
      }

      const publishData = await publishRes.json();
      if (publishData.success) {
        router.push('/admin/exercises');
      } else {
        alert('Error publishing: ' + (publishData.error || 'Unknown error'));
      }
    } catch (error) {
      console.error('Error publishing:', error);
      alert('Error publishing exercise');
    } finally {
      setSaving(false);
    }
  };

  const canProceed = () => {
    switch (currentStep) {
      case 0: return basicInfo.name.en && basicInfo.categoryId && basicInfo.countingMethodId;
      case 2: return poseVariants.length > 0 && poseVariants.every((pv) => pv.name.en && pv.cameraPositionId);
      case 3: {
        // Check if at least one tracked joint with primary role is set for at least one variant
        return poseVariants.some((pv) => {
          const config = movementConfigs[pv.id];
          return config && config.trackedJoints.length > 0 && config.trackedJoints.some(j => j.role === 'primary');
        });
      }
      default: return true;
    }
  };

  const buildPreviewJson = () => {
    const countingMethod = countingMethods.find((c) => c.id === basicInfo.countingMethodId);
    const category = categories.find((c) => c.id === basicInfo.categoryId);
    const countingMethodCode = getCountingMethodCode();

    return {
      name: basicInfo.name,
      category: category ? { code: category.code, name: category.name } : null,
      countingMethod: countingMethod?.code,
      muscles: attributes.muscles.map(id => muscles.find(m => m.id === id)?.code).filter(Boolean),
      equipment: attributes.equipment.map(id => equipment.find(e => e.id === id)?.code).filter(Boolean),
      tags: attributes.tags.map(id => tags.find(t => t.id === id)?.code).filter(Boolean),
      poseVariants: poseVariants.map((pv) => {
        const camera = cameraPositions.find((c) => c.id === pv.cameraPositionId);
        const config = movementConfigs[pv.id];
        const variantMessages = feedbackMessages[pv.id] || [];

        return {
          name: pv.name,
          cameraPosition: camera?.code || pv.cameraPositionId,
          trackedJoints: config?.trackedJoints.map(j => ({
            joint: j.jointCode,
            role: j.role,
            startPose: j.startPose,
            targetAngle: j.targetAngle,
            tolerances: j.tolerances,
            errorMessages: j.errorMessages,
            pairedWith: j.pairedWith,
          })) || [],
          feedbackMessages: {
            motivational: variantMessages.filter(m => m.type === 'motivational').map(m => m.message),
            common_mistake: variantMessages.filter(m => m.type === 'common_mistake').map(m => m.message),
            tip: variantMessages.filter(m => m.type === 'tip').map(m => m.message),
          },
          difficultyLevels: DIFFICULTY_LEVELS.map((level) => {
            const firstPrimary = config?.trackedJoints.find(j => j.role === 'primary');
            const toleranceKey = level as 'beginner' | 'normal' | 'advanced';
            const tolerance = firstPrimary?.tolerances?.[toleranceKey] ?? 
              (level === 'beginner' ? 30 : level === 'normal' ? 15 : 5);
            
            return {
              level,
              romConfig: {
                targetAngle: firstPrimary?.targetAngle ?? 90,
                tolerance,
              },
              repCountingConfig: {
                reps: level === 'beginner' ? config?.beginnerReps :
                     level === 'normal' ? config?.normalReps :
                     config?.advancedReps,
                duration: countingMethodCode === 'counter' ? (
                  level === 'beginner' ? config?.beginnerDuration :
                  level === 'normal' ? config?.normalDuration :
                  config?.advancedDuration
                ) : undefined,
              },
              phases: countingMethodCode === 'up_down' 
                ? ['start', 'down', 'bottom', 'up']
                : countingMethodCode === 'push_pull'
                ? ['start', 'push', 'extended', 'pull']
                : ['start', 'count', 'end'],
            };
          }),
        };
      }),
    };
  };

  // Create dummy difficulty levels for MessagesStep and ReviewStep compatibility
  const dummyDifficultyLevels = poseVariants.flatMap((pv) =>
    DIFFICULTY_LEVELS.map((level) => ({
      id: `${pv.id}-${level}`,
      poseVariantId: pv.id,
      difficultyTypeId: level,
      name: { ar: level === 'beginner' ? 'مبتدئ' : level === 'normal' ? 'عادي' : 'محترف', en: level },
      description: { ar: '', en: '' },
    }))
  );

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-gray-500">Loading...</div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">New Exercise</h1>
          <p className="text-gray-600 mt-1">Create a new training exercise with 3 difficulty levels</p>
        </div>
        <button
          type="button"
          onClick={handleSaveDraft}
          disabled={saving || !basicInfo.name.en}
          className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 disabled:opacity-50"
        >
          {saving ? 'Saving...' : 'Save Draft'}
        </button>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
        <WizardStepper
          steps={WIZARD_STEPS}
          currentStep={currentStep}
          onStepClick={(step) => step <= currentStep && setCurrentStep(step)}
        />

        <div className="mt-8">
          {currentStep === 0 && (
            <BasicInfoStep
              data={basicInfo}
              onChange={setBasicInfo}
              categories={categories}
              countingMethods={countingMethods}
              loading={loading}
            />
          )}

          {currentStep === 1 && (
            <AttributesStep
              data={attributes}
              onChange={setAttributes}
              muscles={muscles}
              equipment={equipment}
              tags={tags}
            />
          )}

          {currentStep === 2 && (
            <PoseVariantsStep
              data={poseVariants}
              onChange={setPoseVariants}
              cameraPositions={cameraPositions}
            />
          )}

          {currentStep === 3 && (
            <MovementConfigStep
              data={movementConfigs}
              onChange={setMovementConfigs}
              poseVariants={poseVariants}
              joints={joints}
              countingMethodCode={getCountingMethodCode()}
            />
          )}

          {currentStep === 4 && (
            <MessagesStep
              data={feedbackMessages}
              onChange={setFeedbackMessages}
              poseVariants={poseVariants}
              difficultyLevels={dummyDifficultyLevels}
              difficultyTypes={[]}
            />
          )}

          {currentStep === 5 && (
            <ReviewStep
              data={{
                basicInfo,
                attributes,
                poseVariants,
                difficultyLevels: dummyDifficultyLevels,
                startPoseAngles: {},
                phaseRules: {},
                repCountingConfigs: {},
                feedbackMessages,
              }}
              onPublish={handlePublish}
              onSaveDraft={handleSaveDraft}
              saving={saving}
              categories={categories}
              countingMethods={countingMethods}
            />
          )}
        </div>

        <div className="mt-8 pt-6 border-t border-gray-200 flex justify-between">
          <button
            type="button"
            onClick={handlePrevious}
            disabled={currentStep === 0}
            className="px-4 py-2 text-gray-600 hover:text-gray-900 disabled:opacity-50"
          >
            Previous
          </button>
          <div className="flex gap-3">
            {currentStep < WIZARD_STEPS.length - 1 && (
              <button
                type="button"
                onClick={handleNext}
                disabled={!canProceed()}
                className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
              >
                Next
              </button>
            )}
          </div>
        </div>
      </div>

      <JsonPreview
        data={buildPreviewJson()}
        title="Exercise Configuration (JSON Preview)"
        collapsible={true}
        defaultExpanded={false}
      />
    </div>
  );
}
