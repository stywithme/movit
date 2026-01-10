'use client';

/**
 * Wizard Stepper Component
 * ========================
 * 
 * Visual step indicator for the exercise creation wizard.
 */

import { useWizardStore, useStepComplete } from './WizardContext';

// Default 7 steps (Type is now combined with Basic Info)
const STEPS = [
  { id: 1, name: 'Basic Info & Type', shortName: 'Basic' },
  { id: 2, name: 'Camera Position', shortName: 'Camera' },
  { id: 3, name: 'Joint Config', shortName: 'Joints' },
  { id: 4, name: 'Position Checks', shortName: 'Checks' },
  { id: 5, name: 'Rep Config', shortName: 'Reps' },
  { id: 6, name: 'Extras', shortName: 'Extras' },
  { id: 7, name: 'Review', shortName: 'Review' },
];

interface StepIndicatorProps {
  step: typeof STEPS[number];
  isActive: boolean;
  isComplete: boolean;
  onClick: () => void;
}

function StepIndicator({ step, isActive, isComplete, onClick }: StepIndicatorProps) {
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
          step.id
        )}
      </div>
      
      {/* Label */}
      <span
        className={`
          text-xs font-medium transition-colors
          ${isActive ? 'text-blue-600' : isComplete ? 'text-green-600' : 'text-gray-400'}
        `}
      >
        {step.shortName}
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
  totalSteps?: number;
}

export function WizardStepper({ totalSteps = 7 }: WizardStepperProps) {
  const { currentStep, setStep } = useWizardStore();
  
  // Use only the steps up to totalSteps
  const stepsToShow = STEPS.slice(0, totalSteps);
  
  return (
    <div className="w-full px-4 py-6">
      <div className="flex items-center justify-between max-w-4xl mx-auto">
        {stepsToShow.map((step, index) => (
          <div key={step.id} className="flex items-center flex-1 last:flex-none">
            <StepIndicatorWrapper step={step} currentStep={currentStep} setStep={setStep} />
            {index < stepsToShow.length - 1 && (
              <ConnectorWrapper stepId={step.id} currentStep={currentStep} />
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

// Wrapper components to use hooks
function StepIndicatorWrapper({ 
  step, 
  currentStep, 
  setStep 
}: { 
  step: typeof STEPS[number]; 
  currentStep: number; 
  setStep: (step: number) => void;
}) {
  const isComplete = useStepComplete(step.id);
  
  return (
    <StepIndicator
      step={step}
      isActive={currentStep === step.id}
      isComplete={isComplete && currentStep > step.id}
      onClick={() => setStep(step.id)}
    />
  );
}

function ConnectorWrapper({ stepId, currentStep }: { stepId: number; currentStep: number }) {
  const isComplete = useStepComplete(stepId);
  return <Connector isComplete={isComplete && currentStep > stepId} />;
}

export default WizardStepper;
