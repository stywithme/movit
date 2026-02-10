'use client';

/**
 * Wizard Context - Zustand Store
 * ==============================
 * 
 * Global state management for the exercise creation wizard.
 * Updated: State-based system (no difficulty levels)
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { 
  BasicInfoData, 
  CountingMethodData, 
  CameraPositionData,
  JointConfigData,
  PositionChecksData,
  RepCountingConfigData,
  ExtrasData,
  TrackedJointData,
  PositionCheckData,
  FeedbackAssignmentData,
} from '@/modules/exercises/exercises.validation';
import type { MetricCode } from '@/modules/exercises/exercises.types';

// ============================================
// WEIGHT & METRICS TYPES
// ============================================

export interface WeightConfigData {
  supportsWeight: boolean;
  minWeight?: number;
  maxWeight?: number;
  defaultWeight?: number;
}

export interface ReportMetricsData {
  primary: MetricCode[];
  optional: MetricCode[];
  excluded: MetricCode[];
}

export interface AlternatingVariantForm {
  label: { ar: string; en: string };
  variantIndex: number;
}

export interface AlternatingConfigData {
  enabled: boolean;
  switchEvery: number;
  variants: AlternatingVariantForm[];
}

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
  jointConfigVariants: Record<number, TrackedJointData[]>;
  activeJointVariantIndex: number;
  positionChecks: Partial<PositionChecksData>;
  repConfig: Partial<RepCountingConfigData>;
  extras: Partial<ExtrasData>;
  
  // Weight & Metrics configuration
  weightConfig: WeightConfigData;
  reportMetrics: ReportMetricsData;
  alternatingConfig: AlternatingConfigData;
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
  setJointConfigVariants: (variants: Record<number, TrackedJointData[]>) => void;
  setActiveJointVariantIndex: (index: number) => void;
  setPositionChecks: (data: Partial<PositionChecksData>) => void;
  setRepConfig: (data: Partial<RepCountingConfigData>) => void;
  setExtras: (data: Partial<ExtrasData>) => void;
  
  // Weight & Metrics
  setWeightConfig: (data: Partial<WeightConfigData>) => void;
  setReportMetrics: (data: Partial<ReportMetricsData>) => void;

  // Alternating config
  setAlternatingConfig: (data: Partial<AlternatingConfigData>) => void;
  addAlternatingVariant: (variant: AlternatingVariantForm) => void;
  updateAlternatingVariant: (index: number, variant: AlternatingVariantForm) => void;
  removeAlternatingVariant: (index: number) => void;
  
  // Joint helpers
  addTrackedJoint: (joint: TrackedJointData) => void;
  updateTrackedJoint: (index: number, joint: TrackedJointData) => void;
  removeTrackedJoint: (index: number) => void;
  
  // Position check helpers
  addPositionCheck: (check: PositionCheckData) => void;
  updatePositionCheck: (index: number, check: PositionCheckData) => void;
  removePositionCheck: (index: number) => void;
  
  // Feedback assignment helpers
  addFeedbackAssignment: (assignment: FeedbackAssignmentData) => void;
  updateFeedbackAssignment: (index: number, assignment: FeedbackAssignmentData) => void;
  removeFeedbackAssignment: (index: number) => void;
  
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
    imageUrl: '',
  },
  countingMethod: {
    countingMethodId: '',
    countingMethodCode: undefined,
  },
  cameraPosition: {
    cameraPositionIds: [],
    expectedFacingDirection: 'auto_detect',
    referenceImages: {},
  },
  jointConfig: {
    trackedJoints: [],
  },
  jointConfigVariants: {},
  activeJointVariantIndex: 0,
  positionChecks: {
    positionChecks: [],
  },
  repConfig: {
    reps: 12,
    minRepIntervalMs: 1500,
    maxRepIntervalMs: 5000,
  },
  extras: {
    muscles: [],
    equipment: [],
    tags: [],
    feedbackAssignments: [],
  },
  
  // Weight & Metrics defaults
  weightConfig: {
    supportsWeight: false,
    minWeight: undefined,
    maxWeight: undefined,
    defaultWeight: undefined,
  },
  reportMetrics: {
    primary: ['form_score'],
    optional: [],
    excluded: [],
  },
  alternatingConfig: {
    enabled: false,
    switchEvery: 1,
    variants: [],
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
        currentStep: Math.min(state.currentStep + 1, 7) 
      })),
      prevStep: () => set((state) => ({ 
        currentStep: Math.max(state.currentStep - 1, 1) 
      })),
      
      // Data updates
      setBasicInfo: (data) => set((state) => ({
        basicInfo: { ...state.basicInfo, ...data },
        isDirty: true,
      })),
      
      setCountingMethod: (data) => set((state) => {
        const newState: Partial<WizardState> = {
        countingMethod: { ...state.countingMethod, ...data },
        isDirty: true,
        };
        
        // Update repConfig defaults when counting method changes
        if (data.countingMethodCode) {
          if (data.countingMethodCode === 'hold') {
            newState.repConfig = {
              duration: 30,
              gracePeriodMs: 2500,
            };
          } else {
            newState.repConfig = {
              reps: 12,
              minRepIntervalMs: 1500,
              maxRepIntervalMs: 5000,
            };
          }
        }
        
        return newState;
      }),
      
      setCameraPosition: (data) => set((state) => ({
        cameraPosition: { ...state.cameraPosition, ...data },
        isDirty: true,
      })),
      
      setJointConfig: (data) => set((state) => ({
        jointConfig: { ...state.jointConfig, ...data },
        isDirty: true,
      })),

      setJointConfigVariants: (variants) => set(() => ({
        jointConfigVariants: variants,
        isDirty: true,
      })),

      setActiveJointVariantIndex: (index) => set(() => ({
        activeJointVariantIndex: index,
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
      
      // Weight & Metrics
      setWeightConfig: (data) => set((state) => ({
        weightConfig: { ...state.weightConfig, ...data },
        isDirty: true,
      })),
      
      setReportMetrics: (data) => set((state) => ({
        reportMetrics: { ...state.reportMetrics, ...data },
        isDirty: true,
      })),

      setAlternatingConfig: (data) => set((state) => ({
        alternatingConfig: { ...state.alternatingConfig, ...data },
        isDirty: true,
      })),

      addAlternatingVariant: (variant) => set((state) => ({
        alternatingConfig: {
          ...state.alternatingConfig,
          variants: [...state.alternatingConfig.variants, variant],
        },
        isDirty: true,
      })),

      updateAlternatingVariant: (index, variant) => set((state) => {
        const variants = [...state.alternatingConfig.variants];
        variants[index] = variant;
        return {
          alternatingConfig: { ...state.alternatingConfig, variants },
          isDirty: true,
        };
      }),

      removeAlternatingVariant: (index) => set((state) => ({
        alternatingConfig: {
          ...state.alternatingConfig,
          variants: state.alternatingConfig.variants.filter((_, i) => i !== index),
        },
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
      
      // Feedback assignment helpers
      addFeedbackAssignment: (assignment) => set((state) => ({
        extras: {
          ...state.extras,
          feedbackAssignments: [...(state.extras.feedbackAssignments || []), assignment],
        },
        isDirty: true,
      })),
      
      updateFeedbackAssignment: (index, assignment) => set((state) => {
        const assignments = [...(state.extras.feedbackAssignments || [])];
        assignments[index] = assignment;
        return {
          extras: { ...state.extras, feedbackAssignments: assignments },
          isDirty: true,
        };
      }),
      
      removeFeedbackAssignment: (index) => set((state) => ({
        extras: {
          ...state.extras,
          feedbackAssignments: (state.extras.feedbackAssignments || []).filter((_, i) => i !== index),
        },
        isDirty: true,
      })),
      
      // Save state
      setExerciseId: (id) => set({ exerciseId: id }),
      
      setSaveStatus: (status, error) => set({ 
        saveStatus: status, 
        saveError: error ?? null,
      }),
      
      markAsSaved: () => set({ 
        isDirty: false, 
        lastSaved: new Date(),
        saveStatus: 'saved',
        saveError: null,
      }),
      
      markAsDirty: () => set({ isDirty: true }),
      
      // Reset
      resetWizard: () => set({ ...initialState }),
      
      loadExercise: (data) => set({
        ...initialState,
        ...data,
        isDirty: false,
      }),
    }),
    {
      name: 'exercise-wizard-storage',
      partialize: (state) => ({
        exerciseId: state.exerciseId,
        exerciseStatus: state.exerciseStatus,
        currentStep: state.currentStep,
        basicInfo: state.basicInfo,
        countingMethod: state.countingMethod,
        cameraPosition: state.cameraPosition,
        jointConfig: state.jointConfig,
        jointConfigVariants: state.jointConfigVariants,
        activeJointVariantIndex: state.activeJointVariantIndex,
        positionChecks: state.positionChecks,
        repConfig: state.repConfig,
        extras: state.extras,
        weightConfig: state.weightConfig,
        reportMetrics: state.reportMetrics,
        alternatingConfig: state.alternatingConfig,
      }),
    }
  )
);
