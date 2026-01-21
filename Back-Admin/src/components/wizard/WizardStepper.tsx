'use client';

/**
 * Wizard Stepper Component
 * ========================
 * 
 * Visual step indicator for the exercise creation wizard.
 * Updated for state-based system.
 */

interface StepIndicatorProps {
  stepNumber: number;
  label: string;
  isActive: boolean;
  isComplete: boolean;
  onClick: () => void;
}

function StepIndicator({ stepNumber, label, isActive, isComplete, onClick }: StepIndicatorProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`
        flex flex-col items-center gap-1 transition-all duration-200
        ${isActive ? 'scale-110' : 'hover:scale-105'}
      `}
    >
      {/* Circle */}
      <div
        className={`
          w-10 h-10 rounded-full flex items-center justify-center
          font-semibold text-sm transition-all duration-200
          ${isActive 
            ? 'bg-blue-600 text-white ring-4 ring-blue-200' 
            : isComplete 
              ? 'bg-green-500 text-white' 
              : 'bg-gray-200 text-gray-500'
          }
        `}
      >
        {isComplete && !isActive ? (
          <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
            <path
              fillRule="evenodd"
              d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
              clipRule="evenodd"
            />
          </svg>
        ) : (
          stepNumber
        )}
      </div>
      
      {/* Label */}
      <span
        className={`
          text-xs font-medium transition-colors whitespace-nowrap
          ${isActive ? 'text-blue-600' : isComplete ? 'text-green-600' : 'text-gray-400'}
        `}
      >
        {label}
      </span>
    </button>
  );
}

interface ConnectorProps {
  isComplete: boolean;
}

function Connector({ isComplete }: ConnectorProps) {
  return (
    <div
      className={`
        flex-1 h-0.5 mx-1 transition-colors duration-200
        ${isComplete ? 'bg-green-500' : 'bg-gray-200'}
      `}
    />
  );
}

interface WizardStepperProps {
  currentStep: number;
  totalSteps: number;
  onStepClick: (step: number) => void;
  stepLabels?: string[];
}

export function WizardStepper({ 
  currentStep, 
  totalSteps, 
  onStepClick,
  stepLabels = ['Basic', 'Camera', 'Joints', 'Checks', 'Reps', 'Extras', 'Review'],
}: WizardStepperProps) {
  const steps = Array.from({ length: totalSteps }, (_, i) => ({
    id: i + 1,
    label: stepLabels[i] || `Step ${i + 1}`,
  }));

  return (
    <div className="flex items-center justify-between">
      {steps.map((step, index) => (
        <div key={step.id} className="flex items-center flex-1 last:flex-none">
          <StepIndicator
            stepNumber={step.id}
            label={step.label}
            isActive={currentStep === step.id}
            isComplete={currentStep > step.id}
            onClick={() => onStepClick(step.id)}
          />
          
          {index < steps.length - 1 && (
            <Connector isComplete={currentStep > step.id} />
          )}
        </div>
      ))}
    </div>
  );
}

export default WizardStepper;
