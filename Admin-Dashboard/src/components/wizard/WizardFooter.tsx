'use client';

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
    <div className="fixed bottom-0 left-0 right-0 bg-white border-t p-4 z-10">
      <div className="w-full px-4 sm:px-6 lg:px-8 flex justify-between items-center">
        <button
          onClick={onPrevious}
          disabled={isFirst}
          className={`px-6 py-2 rounded-lg transition-colors ${
            isFirst
              ? 'bg-gray-100 text-gray-400 cursor-not-allowed'
              : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
          }`}
        >
          ← Previous
        </button>

        <div className="text-sm text-gray-500">
          Step {currentStep} of {totalSteps}
        </div>

        <button
          onClick={onNext}
          disabled={isLast}
          className={`px-6 py-2 rounded-lg transition-colors ${
            isLast
              ? 'bg-gray-100 text-gray-400 cursor-not-allowed'
              : 'bg-blue-600 text-white hover:bg-blue-700'
          }`}
        >
          Next →
        </button>
      </div>
    </div>
  );
}
