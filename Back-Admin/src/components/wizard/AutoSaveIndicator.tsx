'use client';

/**
 * Auto-Save Indicator Component
 * =============================
 * 
 * Shows the current save status of the wizard.
 */

import { useWizardStore } from './WizardContext';

export function AutoSaveIndicator() {
  const { saveStatus, lastSaved, isDirty, exerciseId, saveError } = useWizardStore();
  
  const formatTime = (date: Date | null) => {
    if (!date) return '';
    return new Intl.DateTimeFormat('en', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }).format(date);
  };
  
  return (
    <div className="flex items-center gap-2 text-sm">
      {/* Status Icon */}
      <div className="flex items-center gap-1.5">
        {saveStatus === 'saving' && (
          <>
            <div className="w-2 h-2 bg-yellow-500 rounded-full animate-pulse" />
            <span className="text-yellow-600">Saving...</span>
          </>
        )}
        
        {saveStatus === 'saved' && (
          <>
            <div className="w-2 h-2 bg-green-500 rounded-full" />
            <span className="text-green-600">
              Saved {formatTime(lastSaved)}
            </span>
          </>
        )}
        
        {saveStatus === 'error' && (
          <>
            <div className="w-2 h-2 bg-red-500 rounded-full" />
            <span className="text-red-600" title={saveError || undefined}>
              Save failed
            </span>
          </>
        )}
        
        {saveStatus === 'idle' && isDirty && (
          <>
            <div className="w-2 h-2 bg-gray-400 rounded-full" />
            <span className="text-gray-500">Unsaved changes</span>
          </>
        )}
        
        {saveStatus === 'idle' && !isDirty && !exerciseId && (
          <>
            <div className="w-2 h-2 bg-gray-300 rounded-full" />
            <span className="text-gray-400">New exercise</span>
          </>
        )}
      </div>
      
      {/* Draft Badge */}
      {exerciseId && (
        <span className="px-2 py-0.5 text-xs bg-amber-100 text-amber-700 rounded-full">
          Draft
        </span>
      )}
    </div>
  );
}

export default AutoSaveIndicator;
