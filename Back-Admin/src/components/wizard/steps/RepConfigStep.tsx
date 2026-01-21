'use client';

/**
 * Step 6: Rep/Duration Configuration (Unified)
 * =============================================
 * 
 * Single configuration for reps/duration (no difficulty levels).
 */

import { useWizardStore } from '../WizardContext';
import { Card, CardHeader, CardTitle, CardContent, Input, Label } from '@/components/ui';

export function RepConfigStep() {
  const { countingMethod, repConfig, setRepConfig } = useWizardStore();
  
  const isHold = countingMethod.countingMethodCode === 'hold';
  
  const updateConfig = (field: string, value: number | undefined) => {
    setRepConfig({
      ...repConfig,
        [field]: value,
    });
  };
  
  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">
            {isHold ? 'Duration Configuration' : 'Rep Configuration'}
          </h2>
        <p className="text-gray-500">
          {isHold 
            ? 'Set the target hold duration for this exercise.'
            : 'Set the target reps and timing intervals for this exercise.'
          }
        </p>
      </div>
      
      {/* Info Banner */}
      <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 flex items-start gap-3">
        <span className="text-2xl">💡</span>
        <div className="text-sm text-blue-700">
          <p className="font-medium mb-1">State-Based System</p>
          <p>
            With the new state-based system, there are no difficulty levels. 
            The user's performance is evaluated based on how well they stay in the 
            <span className="text-green-600 font-medium"> Perfect</span>, 
            <span className="text-yellow-600 font-medium"> Normal</span>, or 
            <span className="text-orange-600 font-medium"> Pad</span> ranges.
          </p>
        </div>
      </div>
      
      {/* Main Config Card */}
      <Card className="border-t-4 border-t-blue-500">
            <CardHeader className="pb-4">
          <div className="flex items-center gap-3">
            <span className="text-3xl">{isHold ? '⏱️' : '🔄'}</span>
            <div>
              <CardTitle>{isHold ? 'Hold Timer' : 'Repetitions'}</CardTitle>
              <p className="text-sm text-gray-500">
                {isHold ? 'How long should the user hold the position?' : 'How many reps should the user complete?'}
              </p>
                </div>
              </div>
            </CardHeader>
        <CardContent className="space-y-6">
              {isHold ? (
                <>
              {/* Duration */}
                  <div>
                <Label className="text-base font-semibold mb-2 block">
                  Target Duration
                    </Label>
                <div className="flex items-center gap-3">
                      <Input
                        type="number"
                        min={1}
                    max={300}
                    value={repConfig?.duration || ''}
                    onChange={(e) => updateConfig('duration', e.target.value ? Number(e.target.value) : undefined)}
                    className="text-center font-bold text-2xl w-32 h-14"
                        placeholder="30"
                      />
                  <span className="text-gray-600 text-lg">seconds</span>
                    </div>
                <p className="text-sm text-gray-400 mt-2">
                  Recommended: 15-60 seconds for most hold exercises
                </p>
                  </div>
                  
              {/* Grace Period */}
              <div className="pt-4 border-t">
                <Label className="text-base font-semibold mb-2 block">
                  Grace Period
                    </Label>
                <div className="flex items-center gap-3">
                    <Input
                      type="number"
                      min={0}
                      step={100}
                    value={repConfig?.gracePeriodMs || ''}
                    onChange={(e) => updateConfig('gracePeriodMs', e.target.value ? Number(e.target.value) : undefined)}
                    className="w-32"
                      placeholder="2500"
                    />
                  <span className="text-gray-600">milliseconds</span>
                </div>
                <p className="text-sm text-gray-400 mt-2">
                  Allowed break time if the user briefly loses form. Default: 2500ms
                </p>
                  </div>
                </>
              ) : (
                <>
              {/* Reps */}
                  <div>
                <Label className="text-base font-semibold mb-2 block">
                      Target Reps
                    </Label>
                <div className="flex items-center gap-3">
                      <Input
                        type="number"
                        min={1}
                    max={100}
                    value={repConfig?.reps || ''}
                    onChange={(e) => updateConfig('reps', e.target.value ? Number(e.target.value) : undefined)}
                    className="text-center font-bold text-2xl w-32 h-14"
                        placeholder="12"
                      />
                  <span className="text-gray-600 text-lg">reps</span>
                    </div>
                <p className="text-sm text-gray-400 mt-2">
                  Recommended: 8-16 reps for most exercises
                </p>
                  </div>
                  
              {/* Timing */}
              <div className="pt-4 border-t">
                <Label className="text-base font-semibold mb-3 block">
                  Rep Timing Intervals
                </Label>
                <div className="grid md:grid-cols-2 gap-4">
                  <div>
                    <label className="text-sm text-gray-600 mb-1 block">Minimum Interval</label>
                    <div className="flex items-center gap-2">
                    <Input
                      type="number"
                      min={0}
                      step={100}
                        value={repConfig?.minRepIntervalMs || ''}
                        onChange={(e) => updateConfig('minRepIntervalMs', e.target.value ? Number(e.target.value) : undefined)}
                      placeholder="1500"
                    />
                      <span className="text-gray-500 text-sm">ms</span>
                    </div>
                    <p className="text-xs text-gray-400 mt-1">
                      Prevents counting too fast
                    </p>
                  </div>
                  <div>
                    <label className="text-sm text-gray-600 mb-1 block">Maximum Interval</label>
                    <div className="flex items-center gap-2">
                    <Input
                      type="number"
                      min={0}
                      step={100}
                        value={repConfig?.maxRepIntervalMs || ''}
                        onChange={(e) => updateConfig('maxRepIntervalMs', e.target.value ? Number(e.target.value) : undefined)}
                      placeholder="5000"
                    />
                      <span className="text-gray-500 text-sm">ms</span>
                    </div>
                    <p className="text-xs text-gray-400 mt-1">
                      Resets if exceeded
                    </p>
                  </div>
                </div>
                  </div>
                </>
              )}
            </CardContent>
          </Card>
      
      {/* Preview */}
      <div className="bg-gray-50 rounded-xl p-4">
        <h4 className="font-semibold text-gray-700 mb-3">📋 Configuration Preview</h4>
        <div className="bg-white rounded-lg p-3 border text-sm font-mono">
          <pre className="text-gray-700">
{JSON.stringify({
  ...(isHold ? {
    duration: repConfig?.duration || 30,
    gracePeriodMs: repConfig?.gracePeriodMs || 2500,
  } : {
    reps: repConfig?.reps || 12,
    minRepIntervalMs: repConfig?.minRepIntervalMs || 1500,
    maxRepIntervalMs: repConfig?.maxRepIntervalMs || 5000,
  })
}, null, 2)}
          </pre>
        </div>
      </div>
    </div>
  );
}

export default RepConfigStep;
