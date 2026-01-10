'use client';

/**
 * Step 6: Rep/Duration Configuration
 * ==================================
 */

import { useWizardStore } from '../WizardContext';
import { Card, CardHeader, CardTitle, CardContent, Input, Label, Badge } from '@/components/ui';

export function RepConfigStep() {
  const { countingMethod, repConfig, setRepConfig } = useWizardStore();
  
  const isHold = countingMethod.countingMethodCode === 'hold';
  const difficulties = ['beginner', 'normal', 'advanced'] as const;
  const difficultyConfig = {
    beginner: { color: 'success' as const, emoji: '🟢' },
    normal: { color: 'primary' as const, emoji: '🔵' },
    advanced: { color: 'error' as const, emoji: '🔴' },
  };
  
  const updateDifficultyConfig = (
    difficulty: typeof difficulties[number],
    field: string,
    value: number | undefined
  ) => {
    setRepConfig({
      ...repConfig,
      [difficulty]: {
        ...repConfig[difficulty],
        [field]: value,
      },
    });
  };
  
  return (
    <div className="space-y-8 max-w-4xl mx-auto">
      <div>
        <div className="flex items-center gap-2 mb-2">
          <h2 className="text-2xl font-bold text-gray-900">
            {isHold ? 'Duration Configuration' : 'Rep Configuration'}
          </h2>
          <Label tooltip={isHold ? 'Set hold times for isometric exercises.' : 'Set target repetitions for dynamic exercises.'} />
        </div>
        <p className="text-gray-500">
          {isHold 
            ? 'Set the target hold duration for each difficulty level.'
            : 'Set the target reps and timing for each difficulty level.'
          }
        </p>
      </div>
      
      {/* Main Config Grid */}
      <div className="grid md:grid-cols-3 gap-6">
        {difficulties.map((diff) => (
          <Card key={diff} className={`border-t-4 ${
            diff === 'beginner' ? 'border-t-green-500' : 
            diff === 'normal' ? 'border-t-blue-500' : 
            'border-t-red-500'
          }`}>
            <CardHeader className="pb-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="text-xl">{difficultyConfig[diff].emoji}</span>
                  <CardTitle className="capitalize">{diff}</CardTitle>
                </div>
                <Badge variant={difficultyConfig[diff].color}>{diff}</Badge>
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              {/* Reps or Duration */}
              {isHold ? (
                <>
                  <div>
                    <Label tooltip="How long should the user hold this position">
                      Duration (seconds)
                    </Label>
                    <div className="flex items-center gap-2 mt-1">
                      <Input
                        type="number"
                        min={1}
                        value={repConfig[diff]?.duration || ''}
                        onChange={(e) => updateDifficultyConfig(diff, 'duration', e.target.value ? Number(e.target.value) : undefined)}
                        className="text-center font-bold text-lg"
                        placeholder="30"
                      />
                      <span className="text-gray-500 text-sm">sec</span>
                    </div>
                  </div>
                  
                  <div>
                    <Label tooltip="Allowed break time if the user briefly loses form.">
                      Grace Period (ms)
                    </Label>
                    <Input
                      type="number"
                      min={0}
                      step={100}
                      value={repConfig[diff]?.gracePeriodMs || ''}
                      onChange={(e) => updateDifficultyConfig(diff, 'gracePeriodMs', e.target.value ? Number(e.target.value) : undefined)}
                      placeholder="2500"
                    />
                  </div>
                </>
              ) : (
                <>
                  <div>
                    <Label tooltip="Target number of repetitions">
                      Target Reps
                    </Label>
                    <div className="flex items-center gap-2 mt-1">
                      <Input
                        type="number"
                        min={1}
                        value={repConfig[diff]?.reps || ''}
                        onChange={(e) => updateDifficultyConfig(diff, 'reps', e.target.value ? Number(e.target.value) : undefined)}
                        className="text-center font-bold text-lg"
                        placeholder="12"
                      />
                      <span className="text-gray-500 text-sm">reps</span>
                    </div>
                  </div>
                  
                  <div>
                    <Label tooltip="Minimum time between reps (prevents counting too fast)">
                      Min Rep Interval (ms)
                    </Label>
                    <Input
                      type="number"
                      min={0}
                      step={100}
                      value={repConfig[diff]?.minRepIntervalMs || ''}
                      onChange={(e) => updateDifficultyConfig(diff, 'minRepIntervalMs', e.target.value ? Number(e.target.value) : undefined)}
                      placeholder="1500"
                    />
                  </div>
                  
                  <div>
                    <Label tooltip="Maximum time between reps (resets if exceeded)">
                      Max Rep Interval (ms)
                    </Label>
                    <Input
                      type="number"
                      min={0}
                      step={100}
                      value={repConfig[diff]?.maxRepIntervalMs || ''}
                      onChange={(e) => updateDifficultyConfig(diff, 'maxRepIntervalMs', e.target.value ? Number(e.target.value) : undefined)}
                      placeholder="5000"
                    />
                  </div>
                </>
              )}
            </CardContent>
          </Card>
        ))}
      </div>
      
      {/* Info */}
      <div className="flex items-start gap-2 bg-gray-50 rounded-lg p-4 text-sm text-gray-600">
        <span className="text-lg">💡</span>
        <p>
          These values are stored in each difficulty level's <code className="bg-gray-200 px-1 rounded">repCountingConfig</code> JSON field
          and are sent directly to the Android app engine for validation.
        </p>
      </div>
    </div>
  );
}

export default RepConfigStep;
