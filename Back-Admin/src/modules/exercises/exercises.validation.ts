/**
 * Exercise Validation Schemas (Zod)
 * ==================================
 * 
 * These schemas validate exercise data at every step of the wizard.
 * They align with the Android JSON Schema contract.
 */

import { z } from 'zod';

// ============================================
// BASE SCHEMAS
// ============================================

/**
 * Localized text (ar + en)
 */
export const LocalizedTextSchema = z.object({
  ar: z.string().min(1, 'Arabic text is required'),
  en: z.string().min(1, 'English text is required'),
});

export const LocalizedTextOptionalSchema = z.object({
  ar: z.string(),
  en: z.string(),
}).optional();

/**
 * Angle range (min/max)
 */
export const AngleRangeSchema = z.object({
  min: z.number().min(0).max(180),
  max: z.number().min(0).max(180),
}).refine(data => data.min <= data.max, {
  message: 'Min must be less than or equal to max',
});

/**
 * Difficulty ranges
 */
export const DifficultyRangesSchema = z.object({
  beginner: AngleRangeSchema,
  normal: AngleRangeSchema,
  advanced: AngleRangeSchema,
});

/**
 * Difficulty thresholds (for position checks)
 */
export const DifficultyThresholdsSchema = z.object({
  beginner: z.number(),
  normal: z.number(),
  advanced: z.number(),
});

// ============================================
// STEP 1: BASIC INFO
// ============================================

export const BasicInfoSchema = z.object({
  name: LocalizedTextSchema,
  description: LocalizedTextOptionalSchema,
  instructions: LocalizedTextOptionalSchema,
  categoryId: z.string().uuid('Please select a category'),
});

export type BasicInfoData = z.infer<typeof BasicInfoSchema>;

// ============================================
// STEP 2: COUNTING METHOD
// ============================================

export const CountingMethodSchema = z.object({
  countingMethodId: z.string().uuid('Please select a counting method'),
  countingMethodCode: z.enum(['up_down', 'push_pull', 'hold']),
});

export type CountingMethodData = z.infer<typeof CountingMethodSchema>;

// ============================================
// STEP 3: CAMERA POSITION
// ============================================

export const CameraPositionSchema = z.object({
  cameraPositionIds: z.array(z.string().uuid()).min(1, 'Select at least one camera position'),
  expectedFacingDirection: z.enum([
    'facing_right',
    'facing_left',
    'facing_camera',
    'facing_away',
    'auto_detect',
  ]).default('auto_detect'),
});

export type CameraPositionData = z.infer<typeof CameraPositionSchema>;

// ============================================
// STEP 4: JOINT CONFIGURATION
// ============================================

export const JointErrorMessagesSchema = z.object({
  tooLow: LocalizedTextSchema,
  tooHigh: LocalizedTextSchema,
});

export const PrimaryTrackedJointSchema = z.object({
  joint: z.string().min(1, 'Joint is required'),
  role: z.literal('primary'),
  startPose: AngleRangeSchema,
  upRange: DifficultyRangesSchema,
  downRange: DifficultyRangesSchema,
  errorMessages: JointErrorMessagesSchema,
  pairedWith: z.string().optional(),
});

export const SecondaryTrackedJointSchema = z.object({
  joint: z.string().min(1, 'Joint is required'),
  role: z.literal('secondary'),
  startPose: AngleRangeSchema,
  Range: DifficultyRangesSchema, // Capital R for Android contract
  errorMessages: JointErrorMessagesSchema,
  pairedWith: z.string().optional(),
});

export const TrackedJointSchema = z.discriminatedUnion('role', [
  PrimaryTrackedJointSchema,
  SecondaryTrackedJointSchema,
]);

export const JointConfigSchema = z.object({
  trackedJoints: z.array(TrackedJointSchema).min(1, 'At least one joint is required'),
}).refine(data => {
  // Must have at least one primary joint
  return data.trackedJoints.some(j => j.role === 'primary');
}, {
  message: 'At least one primary joint is required for rep counting',
});

export type TrackedJointData = z.infer<typeof TrackedJointSchema>;
export type JointConfigData = z.infer<typeof JointConfigSchema>;

// ============================================
// STEP 5: POSITION CHECKS
// ============================================

export const PositionCheckLandmarksSchema = z.object({
  primary: z.string().min(1),
  secondary: z.string().min(1),
  tertiary: z.string().optional(),
  quaternary: z.string().optional(),
});

export const PositionCheckConditionSchema = z.object({
  operator: z.enum([
    'should_not_exceed',
    'should_exceed',
    'should_be_within',
    'should_equal',
  ]),
  thresholds: DifficultyThresholdsSchema,
});

export const PositionCheckSchema = z.object({
  checkId: z.string().min(1, 'Check ID is required'),
  type: z.enum([
    'forward_comparison',
    'vertical_alignment',
    'horizontal_alignment',
    'distance_ratio',
    'angle_constraint',
    'relative_position',
    'symmetry_check',
  ]),
  landmarks: PositionCheckLandmarksSchema,
  condition: PositionCheckConditionSchema,
  activePhases: z.array(z.enum([
    'idle', 'start', 'down', 'bottom', 'up',
    'push', 'extended', 'pull', 'hold', 'count',
  ])).min(1, 'Select at least one active phase'),
  errorMessage: LocalizedTextSchema,
  severity: z.enum(['error', 'warning', 'tip']).default('warning'),
  cooldownMs: z.number().min(0).default(2000),
  minErrorFrames: z.number().min(1).default(3),
});

export const PositionChecksSchema = z.object({
  positionChecks: z.array(PositionCheckSchema).default([]),
});

export type PositionCheckData = z.infer<typeof PositionCheckSchema>;
export type PositionChecksData = z.infer<typeof PositionChecksSchema>;

// ============================================
// STEP 6: REP/DURATION CONFIG
// ============================================

export const RepConfigForDifficultySchema = z.object({
  reps: z.number().min(1).optional(),
  duration: z.number().min(1).optional(),
  minRepIntervalMs: z.number().min(0).optional(),
  maxRepIntervalMs: z.number().min(0).optional(),
  gracePeriodMs: z.number().min(0).optional(),
});

export const RepConfigSchema = z.object({
  beginner: RepConfigForDifficultySchema,
  normal: RepConfigForDifficultySchema,
  advanced: RepConfigForDifficultySchema,
});

export type RepConfigData = z.infer<typeof RepConfigSchema>;

// ============================================
// STEP 7: EXTRAS (ATTRIBUTES + FEEDBACK)
// ============================================

export const FeedbackMessageSchema = z.object({
  type: z.enum(['motivational', 'common_mistake', 'tip']),
  message: LocalizedTextSchema,
  audioUrl: z.string().url().optional().or(z.literal('')),
});

export const ExtrasSchema = z.object({
  muscles: z.array(z.string().uuid()).default([]),
  equipment: z.array(z.string().uuid()).default([]),
  tags: z.array(z.string().uuid()).default([]),
  feedbackMessages: z.array(FeedbackMessageSchema).default([]),
});

export type FeedbackMessageData = z.infer<typeof FeedbackMessageSchema>;
export type ExtrasData = z.infer<typeof ExtrasSchema>;

// ============================================
// COMPLETE WIZARD DATA
// ============================================

export const WizardDataSchema = z.object({
  // Step 1
  basicInfo: BasicInfoSchema,
  // Step 2
  countingMethod: CountingMethodSchema,
  // Step 3
  cameraPosition: CameraPositionSchema,
  // Step 4 (per pose variant)
  jointConfig: JointConfigSchema,
  // Step 5
  positionChecks: PositionChecksSchema,
  // Step 6
  repConfig: RepConfigSchema,
  // Step 7
  extras: ExtrasSchema,
});

export type WizardData = z.infer<typeof WizardDataSchema>;

// ============================================
// VALIDATION HELPERS
// ============================================

/**
 * Validate a specific step
 */
export function validateStep(step: number, data: Partial<WizardData>): {
  valid: boolean;
  errors: string[];
} {
  const errors: string[] = [];
  
  try {
    switch (step) {
      case 1:
        BasicInfoSchema.parse(data.basicInfo);
        break;
      case 2:
        CountingMethodSchema.parse(data.countingMethod);
        break;
      case 3:
        CameraPositionSchema.parse(data.cameraPosition);
        break;
      case 4:
        JointConfigSchema.parse(data.jointConfig);
        break;
      case 5:
        PositionChecksSchema.parse(data.positionChecks);
        break;
      case 6:
        RepConfigSchema.parse(data.repConfig);
        break;
      case 7:
        ExtrasSchema.parse(data.extras);
        break;
    }
    return { valid: true, errors: [] };
  } catch (error) {
    if (error instanceof z.ZodError) {
      errors.push(...error.errors.map(e => e.message));
    }
    return { valid: false, errors };
  }
}

/**
 * Check if minimum required steps are complete for draft save
 */
export function canSaveAsDraft(data: Partial<WizardData>): boolean {
  // Only need basic info to save as draft
  try {
    BasicInfoSchema.parse(data.basicInfo);
    return true;
  } catch {
    return false;
  }
}

/**
 * Check if all steps are complete for publishing
 * Uses simplified validation to handle partial data from store
 */
export function canPublish(data: Partial<WizardData>): {
  valid: boolean;
  missingSteps: number[];
} {
  const missingSteps: number[] = [];
  
  // Step 1: Basic Info
  const basicInfo = data.basicInfo;
  if (!basicInfo?.name?.en || !basicInfo?.name?.ar || !basicInfo?.categoryId) {
    missingSteps.push(1);
  }
  
  // Step 2: Counting Method
  const countingMethod = data.countingMethod;
  if (!countingMethod?.countingMethodId || !countingMethod?.countingMethodCode) {
    missingSteps.push(2);
  }
  
  // Step 3: Camera Position
  const cameraPosition = data.cameraPosition;
  if (!cameraPosition?.cameraPositionIds || cameraPosition.cameraPositionIds.length === 0) {
    missingSteps.push(3);
  }
  
  // Step 4: Joint Config - must have at least one primary joint
  const jointConfig = data.jointConfig;
  const trackedJoints = jointConfig?.trackedJoints || [];
  const hasPrimaryJoint = trackedJoints.some(j => j.role === 'primary');
  if (trackedJoints.length === 0 || !hasPrimaryJoint) {
    missingSteps.push(4);
  }
  
  // Steps 5, 6, 7 are optional - no validation needed for publishing
  
  return {
    valid: missingSteps.length === 0,
    missingSteps,
  };
}
