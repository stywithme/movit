'use client';

interface Step {
  id: string;
  title: string;
  description?: string;
}

interface WizardStepperProps {
  steps: Step[];
  currentStep: number;
  onStepClick?: (stepIndex: number) => void;
}

export function WizardStepper({ steps, currentStep, onStepClick }: WizardStepperProps) {
  return (
    <nav aria-label="Progress" className="mb-8">
      <ol className="flex items-center">
        {steps.map((step, index) => {
          const isCompleted = index < currentStep;
          const isCurrent = index === currentStep;

          return (
            <li key={step.id} className={`relative ${index !== steps.length - 1 ? 'pr-8 sm:pr-20 flex-1' : ''}`}>
              {/* Connector Line */}
              {index !== steps.length - 1 && (
                <div
                  className="absolute top-4 left-8 -ml-px mt-0.5 h-0.5 w-full"
                  aria-hidden="true"
                >
                  <div
                    className={`h-full ${isCompleted ? 'bg-blue-600' : 'bg-gray-200'}`}
                  />
                </div>
              )}

              <button
                type="button"
                onClick={() => onStepClick?.(index)}
                disabled={!onStepClick || index > currentStep}
                className="group relative flex items-start"
              >
                <span className="flex h-9 items-center" aria-hidden="true">
                  <span
                    className={`relative z-10 flex h-8 w-8 items-center justify-center rounded-full ${
                      isCompleted
                        ? 'bg-blue-600 hover:bg-blue-800'
                        : isCurrent
                        ? 'border-2 border-blue-600 bg-white'
                        : 'border-2 border-gray-300 bg-white'
                    }`}
                  >
                    {isCompleted ? (
                      <svg
                        className="h-5 w-5 text-white"
                        viewBox="0 0 20 20"
                        fill="currentColor"
                      >
                        <path
                          fillRule="evenodd"
                          d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                          clipRule="evenodd"
                        />
                      </svg>
                    ) : (
                      <span
                        className={`text-sm ${
                          isCurrent ? 'text-blue-600' : 'text-gray-500'
                        }`}
                      >
                        {index + 1}
                      </span>
                    )}
                  </span>
                </span>
                <span className="ml-3 flex min-w-0 flex-col">
                  <span
                    className={`text-sm font-medium ${
                      isCurrent ? 'text-blue-600' : isCompleted ? 'text-gray-900' : 'text-gray-500'
                    }`}
                  >
                    {step.title}
                  </span>
                  {step.description && (
                    <span className="text-xs text-gray-500 hidden sm:block">{step.description}</span>
                  )}
                </span>
              </button>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

export default WizardStepper;

