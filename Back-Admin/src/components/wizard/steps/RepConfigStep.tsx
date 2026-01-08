'use client';

/**
 * Step 6: Rep/Duration Configuration
 * ==================================
 */

import { useWizardStore } from '../WizardContext';

export function RepConfigStep() {
  const { countingMethod, repConfig, setRepConfig } = useWizardStore();
  
  const isHold = countingMethod.countingMethodCode === 'hold';
  const difficulties = ['beginner', 'normal', 'advanced'] as const;
  const difficultyColors = {
    beginner: 'green',
    normal: 'blue',
    advanced: 'red',
  };
  const difficultyEmojis = {
    beginner: '🟢',
    normal: '🔵',
    advanced: '🔴',
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
        <h2 className="text-2xl font-bold text-gray-900 mb-2">
          {isHold ? 'Duration Configuration' : 'Rep Configuration'}
        </h2>
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
          <div
            key={diff}
            className={`
              p-6 rounded-xl border-2 bg-gradient-to-br
              ${diff === 'beginner' ? 'border-green-200 from-green-50 to-white' : ''}
              ${diff === 'normal' ? 'border-blue-200 from-blue-50 to-white' : ''}
              ${diff === 'advanced' ? 'border-red-200 from-red-50 to-white' : ''}
            `}
          >
            {/* Header */}
            <div className="flex items-center gap-2 mb-6">
              <span className="text-2xl">{difficultyEmojis[diff]}</span>
              <h3 className="text-lg font-bold text-gray-900 capitalize">{diff}</h3>
            </div>
            
            {/* Reps or Duration */}
            {isHold ? (
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Duration (seconds)
                  </label>
                  <div className="flex items-center gap-2">
                    <input
                      type="number"
                      min={1}
                      value={repConfig[diff]?.duration || ''}
                      onChange={(e) => updateDifficultyConfig(diff, 'duration', e.target.value ? Number(e.target.value) : undefined)}
                      className="w-24 px-4 py-3 text-xl font-bold text-center border-2 rounded-lg text-gray-900 placeholder:text-gray-500"
                      placeholder="30"
                    />
                    <span className="text-gray-500">sec</span>
                  </div>
                </div>
                
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Grace Period (ms)
                  </label>
                  <input
                    type="number"
                    min={0}
                    step={100}
                    value={repConfig[diff]?.gracePeriodMs || ''}
                    onChange={(e) => updateDifficultyConfig(diff, 'gracePeriodMs', e.target.value ? Number(e.target.value) : undefined)}
                    className="w-full px-3 py-2 border rounded-lg text-gray-900 placeholder:text-gray-500"
                    placeholder="2500"
                  />
                  <p className="text-xs text-gray-500 mt-1">Allowed break time</p>
                </div>
              </div>
            ) : (
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Target Reps
                  </label>
                  <div className="flex items-center gap-2">
                    <input
                      type="number"
                      min={1}
                      value={repConfig[diff]?.reps || ''}
                      onChange={(e) => updateDifficultyConfig(diff, 'reps', e.target.value ? Number(e.target.value) : undefined)}
                      className="w-24 px-4 py-3 text-xl font-bold text-center border-2 rounded-lg text-gray-900 placeholder:text-gray-500"
                      placeholder="12"
                    />
                    <span className="text-gray-500">reps</span>
                  </div>
                </div>
                
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Min Rep Interval (ms)
                  </label>
                  <input
                    type="number"
                    min={0}
                    step={100}
                    value={repConfig[diff]?.minRepIntervalMs || ''}
                    onChange={(e) => updateDifficultyConfig(diff, 'minRepIntervalMs', e.target.value ? Number(e.target.value) : undefined)}
                    className="w-full px-3 py-2 border rounded-lg text-gray-900 placeholder:text-gray-500"
                    placeholder="1500"
                  />
                </div>
                
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Max Rep Interval (ms)
                  </label>
                  <input
                    type="number"
                    min={0}
                    step={100}
                    value={repConfig[diff]?.maxRepIntervalMs || ''}
                    onChange={(e) => updateDifficultyConfig(diff, 'maxRepIntervalMs', e.target.value ? Number(e.target.value) : undefined)}
                    className="w-full px-3 py-2 border rounded-lg text-gray-900 placeholder:text-gray-500"
                    placeholder="5000"
                  />
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
      
      {/* Info */}
      <div className="bg-gray-50 rounded-lg p-4">
        <p className="text-sm text-gray-600">
          💡 These values are stored in each difficulty level's <code className="bg-gray-200 px-1 rounded">repCountingConfig</code>
          {' '}and sent to the Android app as configured.
        </p>
      </div>
    </div>
  );
}

export default RepConfigStep;
