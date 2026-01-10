'use client';

/**
 * Step 4: Joint Configuration (Visual Skeleton)
 * =============================================
 * 
 * Interactive human body skeleton for joint selection and configuration.
 */

import { useCallback, useState } from 'react';
import { useWizardStore } from '../../WizardContext';
import { SkeletonPicker } from './SkeletonPicker';
import { buildTrackedJoint } from './joint-templates';
import type { TrackedJointData } from '@/modules/exercises/exercises.validation';

export function JointConfigStep() {
  const { jointConfig, setJointConfig } = useWizardStore();
  const [activeJoint, setActiveJoint] = useState<string | null>(null);

  const handleSetActiveJoint = useCallback((joint: string | null) => {
    setActiveJoint(joint);
  }, []);
  
  const trackedJoints = jointConfig.trackedJoints || [];
  
  // Handle joint selection (add new)
  const handleSelectJoint = useCallback((jointCode: string) => {
    const existing = trackedJoints.find(j => j.joint === jointCode);
    if (existing) return; // Already selected
    
    // Add new joint - primary if first, secondary otherwise
    const role = trackedJoints.length === 0 ? 'primary' : 'secondary';
    const newJoint = buildTrackedJoint(jointCode, role) as TrackedJointData;
    setJointConfig({
      trackedJoints: [...trackedJoints, newJoint],
    });
  }, [trackedJoints, setJointConfig]);
  
  // Handle joint update
  const handleUpdateJoint = useCallback((jointCode: string, updates: Partial<TrackedJointData>) => {
    const existing = trackedJoints.find(j => j.joint === jointCode);
    
    if (!existing) {
      // Creating new joint
      const role = updates.role || (trackedJoints.length === 0 ? 'primary' : 'secondary');
      const newJoint = buildTrackedJoint(jointCode, role) as TrackedJointData;
      setJointConfig({
        trackedJoints: [...trackedJoints, { ...newJoint, ...updates }],
      });
    } else {
      // Updating existing joint
      if (updates.role && updates.role !== existing.role) {
        // Role changed - rebuild with new role
        const rebuilt = buildTrackedJoint(jointCode, updates.role, existing.pairedWith) as TrackedJointData;
        setJointConfig({
          trackedJoints: trackedJoints.map(j => 
            j.joint === jointCode 
              ? { ...rebuilt, startPose: existing.startPose, errorMessages: existing.errorMessages }
              : j
          ),
        });
      } else {
        // Normal update
        setJointConfig({
          trackedJoints: trackedJoints.map(j => 
            j.joint === jointCode ? { ...j, ...updates } : j
          ),
        });
      }
    }
  }, [trackedJoints, setJointConfig]);
  
  // Handle joint removal
  const handleRemoveJoint = useCallback((jointCode: string) => {
    setJointConfig({
      trackedJoints: trackedJoints.filter(j => j.joint !== jointCode),
    });
  }, [trackedJoints, setJointConfig]);
  
  // Copy settings to mirror joint
  const handleCopyToMirror = useCallback((fromJoint: string, toJoint: string) => {
    const source = trackedJoints.find(j => j.joint === fromJoint);
    if (!source) return;
    
    const targetExists = trackedJoints.find(j => j.joint === toJoint);
    
    // Create copy with same settings
    const copy: TrackedJointData = {
      ...source,
      joint: toJoint,
      pairedWith: fromJoint,
    };
    
    if (targetExists) {
      // Update existing
      setJointConfig({
        trackedJoints: trackedJoints.map(j => j.joint === toJoint ? copy : j),
      });
    } else {
      // Add new
      setJointConfig({
        trackedJoints: [...trackedJoints, copy],
      });
    }
    
    // Also update source to pair with target
    setJointConfig({
      trackedJoints: [...trackedJoints.filter(j => j.joint !== toJoint), copy].map(j => 
        j.joint === fromJoint ? { ...j, pairedWith: toJoint } : j
      ),
    });
  }, [trackedJoints, setJointConfig]);
  
  const hasPrimary = trackedJoints.some(j => j.role === 'primary');
  
  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      {/* Header */}
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Joint Configuration</h2>
        <p className="text-gray-500">Click on the body to select and configure joints for tracking.</p>
      </div>
      
      {/* Validation Warning */}
      {trackedJoints.length > 0 && !hasPrimary && (
        <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 flex items-center gap-2 text-amber-800">
          <svg className="w-5 h-5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          <span className="text-sm font-medium">At least one Primary joint is required for rep counting</span>
        </div>
      )}
      
      {/* Main Content */}
      <div className="bg-white rounded-2xl border p-6">
        <SkeletonPicker
          selectedJoints={trackedJoints}
          onSelectJoint={handleSelectJoint}
          onUpdateJoint={handleUpdateJoint}
          onRemoveJoint={handleRemoveJoint}
          onCopyToMirror={handleCopyToMirror}
          activeJoint={activeJoint}
          setActiveJoint={handleSetActiveJoint}
        />
      </div>
      
      {/* Summary */}
      {trackedJoints.length > 0 && (
        <div className="bg-gray-50 rounded-xl p-4">
          <div className="flex items-center justify-between">
            <div className="text-sm text-gray-600">
              <span className="font-medium">{trackedJoints.length}</span> joints selected
              <span className="mx-2">•</span>
              <span className="text-blue-600">{trackedJoints.filter(j => j.role === 'primary').length} Primary</span>
              <span className="mx-2">•</span>
              <span className="text-purple-600">{trackedJoints.filter(j => j.role === 'secondary').length} Secondary</span>
            </div>
            <button
              type="button"
              onClick={() => setJointConfig({ trackedJoints: [] })}
              className="text-xs text-red-600 hover:text-red-700"
            >
              Clear All
            </button>
          </div>
        </div>
      )}
      
      {/* Help */}
      <div className="text-sm text-gray-500 bg-blue-50 rounded-lg p-4">
        <p className="font-medium text-blue-700 mb-1">💡 Tips:</p>
        <ul className="list-disc list-inside space-y-1 text-blue-600">
          <li><strong>Primary:</strong> Main joints used to count reps (angle change detection)</li>
          <li><strong>Secondary:</strong> Joints checked for correct posture only</li>
          <li><strong>Copy:</strong> Use the copy button to mirror settings to the paired joint (e.g. Left → Right)</li>
        </ul>
      </div>
    </div>
  );
}

export default JointConfigStep;
