'use client';

/**
 * Wizard Context - Zustand Store
 * ==============================
 * 
 * Global state management for the exercise creation wizard.
 * Handles form data, navigation, and auto-save functionality.
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { 
  BasicInfoData, 
  CountingMethodData, 
  CameraPositionData,
  JointConfigData,
  PositionChecksData,
  RepConfigData,
  ExtrasData,
  TrackedJointData,
  PositionCheckData,
  FeedbackMessageData,
} from '@/modules/exercises/exercises.validation';

// ============================================
// TYPES
// ============================================

export type SaveStatus = 'idle' | 'saving' | 'saved' | 'error';

export interface WizardState {
  // Exercise identity
  exerciseId: string | null;
  exerciseStatus: 'draft' | 'published';
  
  // Wizard UI state
  currentStep: number;
  isDirty: boolean;
  lastSaved: Date | null;
  saveStatus: SaveStatus;
  saveError: string | null;
  
  // Form data (per step)
  basicInfo: Partial<BasicInfoData>;
  countingMethod: Partial<CountingMethodData>;
  cameraPosition: Partial<CameraPositionData>;
  jointConfig: Partial<JointConfigData>;
  positionChecks: Partial<PositionChecksData>;
  repConfig: Partial<RepConfigData>;
  extras: Partial<ExtrasData>;
}

export interface WizardActions {
  // Navigation
  setStep: (step: number) => void;
  nextStep: () => void;
  prevStep: () => void;
  
  // Data updates
  setBasicInfo: (data: Partial<BasicInfoData>) => void;
  setCountingMethod: (data: Partial<CountingMethodData>) => void;
  setCameraPosition: (data: Partial<CameraPositionData>) => void;
  setJointConfig: (data: Partial<JointConfigData>) => void;
  setPositionChecks: (data: Partial<PositionChecksData>) => void;
  setRepConfig: (data: Partial<RepConfigData>) => void;
  setExtras: (data: Partial<ExtrasData>) => void;
  
  // Joint helpers
  addTrackedJoint: (joint: TrackedJointData) => void;
  updateTrackedJoint: (index: number, joint: TrackedJointData) => void;
  removeTrackedJoint: (index: number) => void;
  
  // Position check helpers
  addPositionCheck: (check: PositionCheckData) => void;
  updatePositionCheck: (index: number, check: PositionCheckData) => void;
  removePositionCheck: (index: number) => void;
  
  // Feedback message helpers
  addFeedbackMessage: (message: FeedbackMessageData) => void;
  updateFeedbackMessage: (index: number, message: FeedbackMessageData) => void;
  removeFeedbackMessage: (index: number) => void;
  
  // Save state
  setExerciseId: (id: string) => void;
  setSaveStatus: (status: SaveStatus, error?: string) => void;
  markAsSaved: () => void;
  markAsDirty: () => void;
  
  // Reset
  resetWizard: () => void;
  loadExercise: (data: Partial<WizardState>) => void;
}

export type WizardStore = WizardState & WizardActions;

// ============================================
// INITIAL STATE
// ============================================

const initialState: WizardState = {
  exerciseId: null,
  exerciseStatus: 'draft',
  currentStep: 1,
  isDirty: false,
  lastSaved: null,
  saveStatus: 'idle',
  saveError: null,
  
  basicInfo: {
    name: { ar: '', en: '' },
    description: { ar: '', en: '' },
    instructions: { ar: '', en: '' },
    categoryId: '',
  },
  countingMethod: {
    countingMethodId: '',
    countingMethodCode: undefined,
  },
  cameraPosition: {
    cameraPositionIds: [],
    expectedFacingDirection: 'auto_detect',
  },
  jointConfig: {
    trackedJoints: [],
  },
  positionChecks: {
    positionChecks: [],
  },
  repConfig: {
    beginner: { reps: 8 },
    normal: { reps: 12 },
    advanced: { reps: 16 },
  },
  extras: {
    muscles: [],
    equipment: [],
    tags: [],
    feedbackMessages: [],
  },
};

// ============================================
// STORE
// ============================================

export const useWizardStore = create<WizardStore>()(
  persist(
    (set, get) => ({
      ...initialState,
      
      // Navigation
      setStep: (step) => set({ currentStep: step }),
      nextStep: () => set((state) => ({ 
        currentStep: Math.min(state.currentStep + 1, 8) 
      })),
      prevStep: () => set((state) => ({ 
        currentStep: Math.max(state.currentStep - 1, 1) 
      })),
      
      // Data updates
      setBasicInfo: (data) => set((state) => ({
        basicInfo: { ...state.basicInfo, ...data },
        isDirty: true,
      })),
      
      setCountingMethod: (data) => set((state) => ({
        countingMethod: { ...state.countingMethod, ...data },
        isDirty: true,
      })),
      
      setCameraPosition: (data) => set((state) => ({
        cameraPosition: { ...state.cameraPosition, ...data },
        isDirty: true,
      })),
      
      setJointConfig: (data) => set((state) => ({
        jointConfig: { ...state.jointConfig, ...data },
        isDirty: true,
      })),
      
      setPositionChecks: (data) => set((state) => ({
        positionChecks: { ...state.positionChecks, ...data },
        isDirty: true,
      })),
      
      setRepConfig: (data) => set((state) => ({
        repConfig: { ...state.repConfig, ...data },
        isDirty: true,
      })),
      
      setExtras: (data) => set((state) => ({
        extras: { ...state.extras, ...data },
        isDirty: true,
      })),
      
      // Joint helpers
      addTrackedJoint: (joint) => set((state) => ({
        jointConfig: {
          ...state.jointConfig,
          trackedJoints: [...(state.jointConfig.trackedJoints || []), joint],
        },
        isDirty: true,
      })),
      
      updateTrackedJoint: (index, joint) => set((state) => {
        const joints = [...(state.jointConfig.trackedJoints || [])];
        joints[index] = joint;
        return {
          jointConfig: { ...state.jointConfig, trackedJoints: joints },
          isDirty: true,
        };
      }),
      
      removeTrackedJoint: (index) => set((state) => ({
        jointConfig: {
          ...state.jointConfig,
          trackedJoints: (state.jointConfig.trackedJoints || []).filter((_, i) => i !== index),
        },
        isDirty: true,
      })),
      
      // Position check helpers
      addPositionCheck: (check) => set((state) => ({
        positionChecks: {
          positionChecks: [...(state.positionChecks.positionChecks || []), check],
        },
        isDirty: true,
      })),
      
      updatePositionCheck: (index, check) => set((state) => {
        const checks = [...(state.positionChecks.positionChecks || [])];
        checks[index] = check;
        return {
          positionChecks: { positionChecks: checks },
          isDirty: true,
        };
      }),
      
      removePositionCheck: (index) => set((state) => ({
        positionChecks: {
          positionChecks: (state.positionChecks.positionChecks || []).filter((_, i) => i !== index),
        },
        isDirty: true,
      })),
      
      // Feedback message helpers
      addFeedbackMessage: (message) => set((state) => ({
        extras: {
          ...state.extras,
          feedbackMessages: [...(state.extras.feedbackMessages || []), message],
        },
        isDirty: true,
      })),
      
      updateFeedbackMessage: (index, message) => set((state) => {
        const messages = [...(state.extras.feedbackMessages || [])];
        messages[index] = message;
        return {
          extras: { ...state.extras, feedbackMessages: messages },
          isDirty: true,
        };
      }),
      
      removeFeedbackMessage: (index) => set((state) => ({
        extras: {
          ...state.extras,
          feedbackMessages: (state.extras.feedbackMessages || []).filter((_, i) => i !== index),
        },
        isDirty: true,
      })),
      
      // Save state
      setExerciseId: (id) => set({ exerciseId: id }),
      
      setSaveStatus: (status, error) => set({ 
        saveStatus: status, 
        saveError: error || null,
      }),
      
      markAsSaved: () => set({ 
        isDirty: false, 
        lastSaved: new Date(),
        saveStatus: 'saved',
      }),
      
      markAsDirty: () => set({ isDirty: true }),
      
      // Reset
      resetWizard: () => set(initialState),
      
      loadExercise: (data) => set({
        ...initialState,
        ...data,
        isDirty: false,
      }),
    }),
    {
      name: 'exercise-wizard-storage',
      partialize: (state) => ({
        // Only persist form data, not UI state
        exerciseId: state.exerciseId,
        basicInfo: state.basicInfo,
        countingMethod: state.countingMethod,
        cameraPosition: state.cameraPosition,
        jointConfig: state.jointConfig,
        positionChecks: state.positionChecks,
        repConfig: state.repConfig,
        extras: state.extras,
      }),
    }
  )
);

// ============================================
// HOOKS
// ============================================

/**
 * Get all form data for API submission
 */
export function useWizardFormData() {
  const store = useWizardStore();
  
  return {
    basicInfo: store.basicInfo,
    countingMethod: store.countingMethod,
    cameraPosition: store.cameraPosition,
    jointConfig: store.jointConfig,
    positionChecks: store.positionChecks,
    repConfig: store.repConfig,
    extras: store.extras,
  };
}

/**
 * Check if a step is complete
 */
export function useStepComplete(step: number): boolean {
  const store = useWizardStore();
  
  switch (step) {
    case 1:
      return !!(
        store.basicInfo.name?.en && 
        store.basicInfo.name?.ar && 
        store.basicInfo.categoryId
      );
    case 2:
      return !!store.countingMethod.countingMethodId;
    case 3:
      return (store.cameraPosition.cameraPositionIds?.length ?? 0) > 0;
    case 4:
      return (store.jointConfig.trackedJoints?.length ?? 0) > 0 &&
        (store.jointConfig.trackedJoints?.some(j => j.role === 'primary') ?? false);
    case 5:
      return true; // Optional step
    case 6:
      return true; // Has defaults
    case 7:
      return true; // Optional step
    case 8:
      return true; // Review step
    default:
      return false;
  }
}
