'use client';

import { useWizardStore, type WizardStore } from './WizardContext';
import { AutoSaveIndicator } from './AutoSaveIndicator';
import { canPublish } from '@/modules/exercises/exercises.validation';

interface WizardHeaderProps {
  title: string;
  onBack: () => void;
  onSave: () => void;
  onPublish: () => void;
  isSaving?: boolean;
}

export function WizardHeader({ title, onBack, onSave, onPublish, isSaving }: WizardHeaderProps) {
  const exerciseStatus = useWizardStore((s) => s.exerciseStatus);

  const canPublishNow = useWizardStore((s: WizardStore) =>
    canPublish({
      basicInfo: s.basicInfo,
      countingMethod: s.countingMethod,
      cameraPosition: s.cameraPosition,
      jointConfig: s.jointConfig,
      positionChecks: s.positionChecks,
      repConfig: s.repConfig,
      extras: s.extras,
    } as Parameters<typeof canPublish>[0]).valid
  );

  const isDraft = exerciseStatus === 'draft';

  return (
    <div className="bg-white border-b sticky top-0 z-10">
      <div className="w-full px-4 sm:px-6 lg:px-8 py-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <button
              onClick={onBack}
              className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
              </svg>
            </button>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-xl font-bold text-gray-900">{title}</h1>
                <span className={`text-xs px-2 py-1 rounded font-medium ${
                  isDraft
                    ? 'bg-yellow-100 text-yellow-700'
                    : 'bg-green-100 text-green-700'
                }`}>
                  {isDraft ? 'Draft' : 'Published'}
                </span>
              </div>
              <p className="text-sm text-gray-500">State-based configuration</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <AutoSaveIndicator />

            <button
              onClick={onSave}
              disabled={isSaving}
              className="px-4 py-2 text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors disabled:opacity-50"
            >
              {isDraft ? 'Save Draft' : 'Save'}
            </button>

            <button
              type="button"
              onClick={onPublish}
              disabled={!canPublishNow || isSaving}
              title={!canPublishNow ? 'Complete all required steps to publish' : undefined}
              className={`px-4 py-2 rounded-lg font-medium transition-colors ${
                canPublishNow && !isSaving
                  ? 'bg-green-600 text-white hover:bg-green-700'
                  : 'bg-gray-200 text-gray-400 cursor-not-allowed'
              }`}
            >
              Publish
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
