'use client';

import { useWizardStore, type WizardStore } from './WizardContext';
import { AutoSaveIndicator } from './AutoSaveIndicator';
import { canPublish } from '@/modules/exercises/exercises.validation';
import { Button } from '@/components/ui/Button';
import { StatusBadge } from '@/components/common/StatusBadge';
import { ArrowLeft } from 'lucide-react';

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
    <div className="sticky top-0 z-10 border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/70">
      <div className="w-full px-4 sm:px-6 lg:px-8 py-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Button
              type="button"
              variant="ghost"
              size="icon"
              onClick={onBack}
            >
              <ArrowLeft className="size-4" />
            </Button>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
                <StatusBadge status={isDraft ? 'draft' : 'published'} />
              </div>
              <p className="text-sm text-muted-foreground">State-based configuration</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <AutoSaveIndicator />

            <Button
              type="button"
              variant="outline"
              onClick={onSave}
              loading={isSaving}
            >
              {isDraft ? 'Save Draft' : 'Save'}
            </Button>

            <Button
              type="button"
              onClick={onPublish}
              disabled={!canPublishNow || isSaving}
              title={!canPublishNow ? 'Complete all required steps to publish' : undefined}
              variant="success"
            >
              Publish
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
