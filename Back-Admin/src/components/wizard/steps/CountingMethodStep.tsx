'use client';

/**
 * Step 2: Counting Method (Exercise Type)
 * =======================================
 */

import { useWizardStore } from '../WizardContext';
import { Card, Badge, Label } from '@/components/ui';
import { Check } from 'lucide-react';
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
        <div className="flex items-center gap-2 mb-2">
          <h2 className="text-2xl font-bold text-gray-900">Exercise Type</h2>
          <Label tooltip="Select how repetitions are counted for this exercise. This determines the movement phases and validation logic." />
        </div>
        <p className="text-gray-500">How is this exercise counted? This affects the phases and configuration.</p>
      </div>
      
      <div className="grid gap-4">
        {countingMethods.map((method) => {
          const details = METHOD_DETAILS[method.code as CountingMethodCode];
          const isSelected = countingMethod.countingMethodId === method.id;
          
          return (
            <Card
              key={method.id}
              interactive
              selected={isSelected}
              className="p-6"
              onClick={() => handleSelect(method)}
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
                      w-6 h-6 rounded-full border-2 flex items-center justify-center transition-colors
                      ${isSelected ? 'border-blue-500 bg-blue-500' : 'border-gray-300'}
                    `}>
                      {isSelected && <Check className="w-4 h-4 text-white" />}
                    </div>
                  </div>
                  
                  {/* Flow */}
                  {details && (
                    <div className="font-mono text-sm bg-gray-100 rounded-lg px-3 py-2 text-gray-700 inline-block">
                      {details.flow}
                    </div>
                  )}
                  
                  {/* Examples */}
                  {details && (
                    <p className="text-sm text-gray-500">
                      <span className="font-medium text-gray-700">Examples:</span> {details.examples}
                    </p>
                  )}
                  
                  {/* Config hint */}
                  {details && isSelected && (
                    <div className="flex items-center gap-2 text-sm text-blue-700 bg-blue-50 rounded-lg px-3 py-2">
                      <span>💡</span>
                      {details.configHint}
                    </div>
                  )}
                </div>
              </div>
            </Card>
          );
        })}
      </div>
    </div>
  );
}

export default CountingMethodStep;
