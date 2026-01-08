'use client';

/**
 * Step 2: Counting Method (Exercise Type)
 * =======================================
 */

import { useWizardStore } from '../WizardContext';
import type { CountingMethodCode } from '@/lib/types/localized';

interface CountingMethodStepProps {
  countingMethods: Array<{
    id: string;
    code: string;
    name: { ar: string; en: string };
    description?: { ar: string; en: string };
  }>;
}

const METHOD_DETAILS: Record<CountingMethodCode, {
  icon: string;
  flow: string;
  examples: string;
  configHint: string;
}> = {
  up_down: {
    icon: '🔄',
    flow: 'START → DOWN → BOTTOM → UP → START',
    examples: 'Squat, Lunge, Bicep Curl, Deadlift',
    configHint: 'You\'ll configure upRange (standing) and downRange (bent position)',
  },
  push_pull: {
    icon: '🔃',
    flow: 'START → PUSH → EXTENDED → PULL → START',
    examples: 'Push-up, Pull-up, Bench Press, Shoulder Press',
    configHint: 'You\'ll configure push and pull angle ranges',
  },
  hold: {
    icon: '⏱️',
    flow: 'IDLE ↔ COUNT (timer-based)',
    examples: 'Plank, Wall Sit, Dead Hang, L-Sit',
    configHint: 'You\'ll configure hold duration and grace period',
  },
};

export function CountingMethodStep({ countingMethods }: CountingMethodStepProps) {
  const { countingMethod, setCountingMethod } = useWizardStore();
  
  const handleSelect = (method: typeof countingMethods[number]) => {
    setCountingMethod({
      countingMethodId: method.id,
      countingMethodCode: method.code as CountingMethodCode,
    });
  };
  
  return (
    <div className="space-y-8 max-w-4xl mx-auto">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Exercise Type</h2>
        <p className="text-gray-500">How is this exercise counted? This affects the phases and configuration.</p>
      </div>
      
      <div className="grid gap-6">
        {countingMethods.map((method) => {
          const details = METHOD_DETAILS[method.code as CountingMethodCode];
          const isSelected = countingMethod.countingMethodId === method.id;
          
          return (
            <button
              key={method.id}
              type="button"
              onClick={() => handleSelect(method)}
              className={`
                w-full text-left p-6 rounded-xl border-2 transition-all duration-200 text-gray-900
                ${isSelected 
                  ? 'border-blue-500 bg-blue-50 ring-2 ring-blue-200' 
                  : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50'
                }
              `}
            >
              <div className="flex items-start gap-4">
                {/* Icon */}
                <span className="text-4xl">{details?.icon || '📊'}</span>
                
                {/* Content */}
                <div className="flex-1 space-y-3">
                  {/* Header */}
                  <div className="flex items-center justify-between">
                    <h3 className="text-lg font-bold text-gray-900">
                      {method.name.en}
                      <span className="text-gray-400 font-normal ml-2">/ {method.name.ar}</span>
                    </h3>
                    <div className={`
                      w-6 h-6 rounded-full border-2 flex items-center justify-center
                      ${isSelected ? 'border-blue-500 bg-blue-500' : 'border-gray-300'}
                    `}>
                      {isSelected && (
                        <svg className="w-4 h-4 text-white" fill="currentColor" viewBox="0 0 20 20">
                          <path
                            fillRule="evenodd"
                            d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                            clipRule="evenodd"
                          />
                        </svg>
                      )}
                    </div>
                  </div>
                  
                  {/* Flow */}
                  {details && (
                    <div className="font-mono text-sm bg-gray-100 rounded-lg px-3 py-2 text-gray-700">
                      {details.flow}
                    </div>
                  )}
                  
                  {/* Examples */}
                  {details && (
                    <p className="text-sm text-gray-500">
                      <span className="font-medium">Examples:</span> {details.examples}
                    </p>
                  )}
                  
                  {/* Config hint */}
                  {details && isSelected && (
                    <p className="text-sm text-blue-600 bg-blue-50 rounded-lg px-3 py-2">
                      💡 {details.configHint}
                    </p>
                  )}
                </div>
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}

export default CountingMethodStep;
