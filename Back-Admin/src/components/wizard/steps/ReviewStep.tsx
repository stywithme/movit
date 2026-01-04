'use client';

import { useState } from 'react';
import { LocalizedText } from '@/lib/types/localized';

interface ExerciseData {
  basicInfo: {
    name: LocalizedText;
    description: LocalizedText;
    instructions: LocalizedText;
    categoryId: string;
    countingMethodId: string;
  };
  attributes: {
    muscles: string[];
    equipment: string[];
    tags: string[];
  };
  poseVariants: Array<{
    id: string;
    name: LocalizedText;
    description: LocalizedText;
    cameraPositionId: string;
    referenceImageUrl: string;
  }>;
  difficultyLevels: Array<{
    id: string;
    poseVariantId: string;
    difficultyTypeId: string;
    name: LocalizedText;
    description: LocalizedText;
  }>;
  startPoseAngles: Record<string, Record<string, { min: number; max: number }>>;
  phaseRules: Record<string, Array<{
    code: string;
    name: LocalizedText;
    rules: Array<{
      jointId: string;
      minAngle: number;
      maxAngle: number;
      errorMessageOver: LocalizedText;
      errorMessageUnder: LocalizedText;
      priority: string;
    }>;
  }>>;
  repCountingConfigs: Record<string, {
    primaryJoint: string;
    eccentricThreshold: number;
    concentricThreshold: number;
  }>;
  feedbackMessages: Record<string, Array<{
    type: string;
    message: LocalizedText;
  }>>;
}

interface ReviewStepProps {
  data: ExerciseData;
  onPublish: () => void;
  onSaveDraft: () => void;
  saving: boolean;
  categories: Array<{ id: string; code: string; name: LocalizedText }>;
  countingMethods: Array<{ id: string; code: string; name: LocalizedText }>;
}

export function ReviewStep({
  data,
  onPublish,
  onSaveDraft,
  saving,
  categories,
  countingMethods,
}: ReviewStepProps) {
  const [showJson, setShowJson] = useState(false);

  const getCategoryName = () => {
    return categories.find((c) => c.id === data.basicInfo.categoryId)?.name.en || 'Not selected';
  };

  const getCountingMethodName = () => {
    return countingMethods.find((c) => c.id === data.basicInfo.countingMethodId)?.name.en || 'Not selected';
  };

  const validateExercise = () => {
    const errors: string[] = [];

    if (!data.basicInfo.name.en) errors.push('Exercise name (English) is required');
    if (!data.basicInfo.categoryId) errors.push('Category is required');
    if (!data.basicInfo.countingMethodId) errors.push('Counting method is required');
    if (data.poseVariants.length === 0) errors.push('At least one pose variant is required');
    if (data.difficultyLevels.length === 0) errors.push('At least one difficulty level is required');

    return errors;
  };

  const errors = validateExercise();
  const canPublish = errors.length === 0;

  // Build JSON output for preview
  const buildExportJson = () => {
    const countingMethod = countingMethods.find((c) => c.id === data.basicInfo.countingMethodId);
    const category = categories.find((c) => c.id === data.basicInfo.categoryId);

    return {
      name: data.basicInfo.name,
      description: data.basicInfo.description,
      instructions: data.basicInfo.instructions,
      category: category ? { code: category.code, name: category.name } : null,
      countingMethod: countingMethod?.code,
      poseVariants: data.poseVariants.map((pv) => ({
        name: pv.name,
        cameraPosition: pv.cameraPositionId,
        referenceImage: pv.referenceImageUrl || null,
        difficultyLevels: data.difficultyLevels
          .filter((dl) => dl.poseVariantId === pv.id)
          .map((dl) => ({
            name: dl.name,
            startPoseAngles: data.startPoseAngles[dl.id] || {},
            repCountingConfig: data.repCountingConfigs[dl.id] || null,
            phases: data.phaseRules[dl.id] || [],
            feedbackMessages: data.feedbackMessages[dl.id] || [],
          })),
      })),
    };
  };

  const copyToClipboard = () => {
    const json = JSON.stringify(buildExportJson(), null, 2);
    navigator.clipboard.writeText(json);
    alert('JSON copied to clipboard!');
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-gray-200 pb-4">
        <h2 className="text-lg font-semibold text-gray-900">Review & Publish</h2>
        <p className="text-sm text-gray-500 mt-1">
          Review your exercise configuration before publishing.
        </p>
      </div>

      {/* Validation Errors */}
      {errors.length > 0 && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4">
          <h3 className="font-medium text-red-800 mb-2">Please fix the following issues:</h3>
          <ul className="text-sm text-red-700 list-disc ml-4 space-y-1">
            {errors.map((error, i) => (
              <li key={i}>{error}</li>
            ))}
          </ul>
        </div>
      )}

      {/* Summary */}
      <div className="bg-white border border-gray-200 rounded-lg divide-y divide-gray-200">
        {/* Basic Info */}
        <div className="p-4">
          <h3 className="font-medium text-gray-900 mb-3">Basic Information</h3>
          <dl className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <dt className="text-gray-500">Name (EN)</dt>
              <dd className="font-medium">{data.basicInfo.name.en || '-'}</dd>
            </div>
            <div>
              <dt className="text-gray-500">Name (AR)</dt>
              <dd className="font-medium" dir="rtl">{data.basicInfo.name.ar || '-'}</dd>
            </div>
            <div>
              <dt className="text-gray-500">Category</dt>
              <dd className="font-medium">{getCategoryName()}</dd>
            </div>
            <div>
              <dt className="text-gray-500">Counting Method</dt>
              <dd className="font-medium">{getCountingMethodName()}</dd>
            </div>
          </dl>
        </div>

        {/* Attributes */}
        <div className="p-4">
          <h3 className="font-medium text-gray-900 mb-3">Attributes</h3>
          <dl className="grid grid-cols-3 gap-4 text-sm">
            <div>
              <dt className="text-gray-500">Muscles</dt>
              <dd className="font-medium">{data.attributes.muscles.length} selected</dd>
            </div>
            <div>
              <dt className="text-gray-500">Equipment</dt>
              <dd className="font-medium">{data.attributes.equipment.length} selected</dd>
            </div>
            <div>
              <dt className="text-gray-500">Tags</dt>
              <dd className="font-medium">{data.attributes.tags.length} selected</dd>
            </div>
          </dl>
        </div>

        {/* Pose Variants */}
        <div className="p-4">
          <h3 className="font-medium text-gray-900 mb-3">Pose Variants</h3>
          <p className="text-sm text-gray-600">
            {data.poseVariants.length} variant(s) configured
          </p>
          {data.poseVariants.length > 0 && (
            <ul className="mt-2 text-sm space-y-1">
              {data.poseVariants.map((pv) => (
                <li key={pv.id} className="flex justify-between">
                  <span>{pv.name.en || 'Untitled'}</span>
                  <span className="text-gray-500">
                    {data.difficultyLevels.filter((dl) => dl.poseVariantId === pv.id).length} levels
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* Configuration Stats */}
        <div className="p-4">
          <h3 className="font-medium text-gray-900 mb-3">Configuration Stats</h3>
          <dl className="grid grid-cols-4 gap-4 text-sm">
            <div>
              <dt className="text-gray-500">Difficulty Levels</dt>
              <dd className="font-medium">{data.difficultyLevels.length}</dd>
            </div>
            <div>
              <dt className="text-gray-500">Start Angles</dt>
              <dd className="font-medium">{Object.keys(data.startPoseAngles).length} configured</dd>
            </div>
            <div>
              <dt className="text-gray-500">Phase Rules</dt>
              <dd className="font-medium">{Object.keys(data.phaseRules).length} configured</dd>
            </div>
            <div>
              <dt className="text-gray-500">Messages</dt>
              <dd className="font-medium">
                {Object.values(data.feedbackMessages).reduce((acc, msgs) => acc + msgs.length, 0)}
              </dd>
            </div>
          </dl>
        </div>
      </div>

      {/* JSON Preview */}
      <div className="bg-gray-50 border border-gray-200 rounded-lg overflow-hidden">
        <button
          type="button"
          onClick={() => setShowJson(!showJson)}
          className="w-full px-4 py-3 flex justify-between items-center text-sm font-medium text-gray-700 hover:bg-gray-100"
        >
          <span>JSON Preview</span>
          <svg
            className={`w-5 h-5 transform transition-transform ${showJson ? 'rotate-180' : ''}`}
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </button>
        
        {showJson && (
          <div className="border-t border-gray-200">
            <div className="px-4 py-2 bg-gray-100 border-b border-gray-200 flex justify-end">
              <button
                type="button"
                onClick={copyToClipboard}
                className="text-sm text-blue-600 hover:text-blue-800 flex items-center gap-1"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                </svg>
                Copy JSON
              </button>
            </div>
            <pre className="p-4 text-xs overflow-x-auto max-h-96 overflow-y-auto">
              {JSON.stringify(buildExportJson(), null, 2)}
            </pre>
          </div>
        )}
      </div>

      {/* Action Buttons */}
      <div className="flex gap-4 pt-4">
        <button
          type="button"
          onClick={onSaveDraft}
          disabled={saving}
          className="flex-1 py-3 px-4 border border-gray-300 rounded-lg text-gray-700 font-medium hover:bg-gray-50 disabled:opacity-50"
        >
          {saving ? 'Saving...' : 'Save as Draft'}
        </button>
        <button
          type="button"
          onClick={onPublish}
          disabled={saving || !canPublish}
          className="flex-1 py-3 px-4 bg-green-600 text-white rounded-lg font-medium hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {saving ? 'Publishing...' : 'Publish Exercise'}
        </button>
      </div>
    </div>
  );
}

export default ReviewStep;

