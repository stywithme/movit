'use client';

import { Button } from '@/components/ui/Button';

interface WizardFooterProps {
  currentStep: number;
  totalSteps: number;
  onPrevious: () => void;
  onNext: () => void;
}

export function WizardFooter({ currentStep, totalSteps, onPrevious, onNext }: WizardFooterProps) {
  const isFirst = currentStep === 1;
  const isLast = currentStep === totalSteps;

  return (
    <div className="fixed bottom-0 left-0 right-0 z-10 border-t bg-background/95 p-4 backdrop-blur supports-[backdrop-filter]:bg-background/70">
      <div className="w-full px-4 sm:px-6 lg:px-8 flex justify-between items-center">
        <Button
          type="button"
          variant="outline"
          onClick={onPrevious}
          disabled={isFirst}
        >
          Previous
        </Button>

        <div className="text-sm text-muted-foreground">
          Step {currentStep} of {totalSteps}
        </div>

        <Button
          type="button"
          onClick={onNext}
          disabled={isLast}
        >
          Next
        </Button>
      </div>
    </div>
  );
}
