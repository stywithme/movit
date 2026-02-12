/**
 * Exercise Validation Schemas (Zod) - State-Based System
 * ========================================================
 * 
 * These schemas validate exercise data at every step of the wizard.
 * Aligned with Android JSON Schema - State-based (no difficulty levels).
 */

import { z } from 'zod';

// ============================================
// BASE SCHEMAS
// ============================================

/**
 * Localized text (ar + en) with optional audio URLs
 */
export const LocalizedTextSchema = z.object({
  ar: z.string().min(1, 'Arabic text is required'),
  en: z.string().min(1, 'English text is required'),
  audioAr: z.string().optional(),
  audioEn: z.string().optional(),
});

export const LocalizedTextOptionalSchema = z.object({
  ar: z.string(),
  en: z.string(),
  audioAr: z.string().optional(),
  audioEn: z.string().optional(),
}).optional();

/**
 * Angle range (min/max)
 */
export const AngleRangeSchema = z.object({
  min: z.number().min(0, 'Min must be >= 0').max(180, 'Min must be <= 180'),
  max: z.number().min(0, 'Max must be >= 0').max(180, 'Max must be <= 180'),
}).refine(data => data.min <= data.max, {
  message: 'Min must be less than or equal to max',
});

/**
 * Optional angle range
 */
export const AngleRangeOptionalSchema = z.object({
  min: z.number().min(0).max(180),
  max: z.number().min(0).max(180),
}).refine(data => data.min <= data.max, {
  message: 'Min must be less than or equal to max',
}).optional();

// ============================================
// STATE RANGES SCHEMA (replaces DifficultyRanges)
// ============================================

/**
 * StateRanges - State-based angle ranges
 * Only 'perfect' is required, others are optional
 */
export const StateRangesSchema = z.object({
  perfect: AngleRangeSchema,
  normal: AngleRangeOptionalSchema,
  pad: AngleRangeOptionalSchema,
  warning: AngleRangeOptionalSchema,
  danger: AngleRangeOptionalSchema,
}).superRefine((data, ctx) => {
  // Validate that normal extends beyond perfect if present
  if (data.normal) {
    const extendsLeft = data.normal.min <= data.perfect.min;
    const extendsRight = data.normal.max >= data.perfect.max;
    if (!extendsLeft && !extendsRight) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Normal range must extend at least one boundary of Perfect range',
        path: ['normal'],
      });
    }
  }

  // Validate that pad extends beyond normal (or perfect) if present
  if (data.pad) {
    const outerRef = data.normal || data.perfect;
    const extendsLeft = data.pad.min <= outerRef.min;
    const extendsRight = data.pad.max >= outerRef.max;
    if (!extendsLeft && !extendsRight) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Pad range must extend at least one boundary of Normal/Perfect range',
        path: ['pad'],
      });
    }
  }

  // Warning/Danger should be outside counted ranges
  if (data.warning && data.danger) {
    // Check they don't overlap incorrectly
    if (data.warning.max > data.danger.min && data.warning.min < data.danger.max) {
      // This is OK - they can be adjacent
    }
  }
});

/**
 * Zone-based message (for up_down and push_pull)
 */
export const ZoneBasedMessageSchema = z.object({
  up: LocalizedTextSchema.optional(),
  down: LocalizedTextSchema.optional(),
});

/**
 * State message value - can be simple (LocalizedText) or zone-based
 * Simple: { ar: "...", en: "..." }
 * Zone: { up: { ar: "...", en: "..." }, down: { ar: "...", en: "..." } }
 */
export const StateMessageValueSchema = z.union([
  LocalizedTextSchema,
  ZoneBasedMessageSchema,
]).optional();

/**
 * State messages - supports both simple and zone-based formats
 * All messages are optional
 */
export const StateMessagesSchema = z.object({
  perfect: StateMessageValueSchema,
  normal: StateMessageValueSchema,
  pad: StateMessageValueSchema,
  warning: StateMessageValueSchema,
  danger: StateMessageValueSchema,
}).optional();

export type ZoneBasedMessageData = z.infer<typeof ZoneBasedMessageSchema>;
export type StateMessageValueData = z.infer<typeof StateMessageValueSchema>;

// ============================================
// STEP 1: BASIC INFO
// ============================================

export const BasicInfoSchema = z.object({
  name: LocalizedTextSchema,
  description: LocalizedTextOptionalSchema,
  instructions: LocalizedTextOptionalSchema,
  categoryId: z.string().uuid('Please select a category'),
  imageUrl: z.string().url().optional().or(z.literal('')),
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
  referenceImages: z.record(z.string(), z.string().url().optional().or(z.literal(''))).optional(),
});

export type CameraPositionData = z.infer<typeof CameraPositionSchema>;

// ============================================
// STEP 4: JOINT CONFIGURATION (State-based)
// ============================================

/**
 * Primary joint for Up/Down and Push/Pull - has upRange and downRange
 */
export const UpDownPrimaryTrackedJointSchema = z.object({
  joint: z.string().min(1, 'Joint is required'),
  role: z.literal('primary'),
  startPose: AngleRangeSchema,
  upRange: StateRangesSchema,
  downRange: StateRangesSchema,
  stateMessages: StateMessagesSchema,
  pairedWith: z.string().optional(),
  invertIndicator: z.boolean().optional(),
}).superRefine((data, ctx) => {
  // Validate transition zone: upRange min should be > downRange max
  const upMin = getOuterMinFromRanges(data.upRange);
  const downMax = getOuterMaxFromRanges(data.downRange);

  if (upMin <= downMax) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: `Invalid transition zone: upRange min (${upMin}) must be > downRange max (${downMax})`,
      path: ['upRange'],
    });
  }
});

/**
 * Primary joint for Hold exercises - has single range
 */
export const HoldPrimaryTrackedJointSchema = z.object({
  joint: z.string().min(1, 'Joint is required'),
  role: z.literal('primary'),
  startPose: AngleRangeSchema,
  range: StateRangesSchema,
  stateMessages: StateMessagesSchema,
  pairedWith: z.string().optional(),
  invertIndicator: z.boolean().optional(),
});

/**
 * Combined Primary joint schema (supports both modes)
 * For flexibility in the wizard, we allow either upRange/downRange OR range
 */
export const PrimaryTrackedJointSchema = z.object({
  joint: z.string().min(1, 'Joint is required'),
  role: z.literal('primary'),
  startPose: AngleRangeSchema,
  // For Up/Down and Push/Pull
  upRange: StateRangesSchema.optional(),
  downRange: StateRangesSchema.optional(),
  // For Hold
  range: StateRangesSchema.optional(),
  stateMessages: StateMessagesSchema,
  pairedWith: z.string().optional(),
  invertIndicator: z.boolean().optional(),
}).superRefine((data, ctx) => {
  const hasUpDown = data.upRange && data.downRange;
  const hasRange = data.range;

  // Must have either upRange/downRange OR range
  if (!hasUpDown && !hasRange) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'Primary joint must have either upRange/downRange (for rep exercises) or range (for hold exercises)',
      path: ['range'],
    });
  }

  // Validate transition zone for up/down mode
  if (hasUpDown) {
    const upMin = getOuterMinFromRanges(data.upRange!);
    const downMax = getOuterMaxFromRanges(data.downRange!);

    if (upMin <= downMax) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: `Invalid transition zone: upRange min (${upMin}) must be > downRange max (${downMax})`,
        path: ['upRange'],
      });
    }
  }
});

/**
 * Secondary joint - has single range with StateRanges
 */
export const SecondaryTrackedJointSchema = z.object({
  joint: z.string().min(1, 'Joint is required'),
  role: z.literal('secondary'),
  startPose: AngleRangeSchema,
  range: StateRangesSchema,
  stateMessages: StateMessagesSchema,
  pairedWith: z.string().optional(),
  invertIndicator: z.boolean().optional(),
});

/**
 * Union type for tracked joints
 */
export const TrackedJointSchema = z.discriminatedUnion('role', [
  PrimaryTrackedJointSchema,
  SecondaryTrackedJointSchema,
]);

/**
 * Joint configuration step
 */
export const JointConfigSchema = z.object({
  trackedJoints: z.array(TrackedJointSchema).min(1, 'At least one joint is required'),
}).refine(data => {
  // Must have at least one primary joint
  return data.trackedJoints.some(j => j.role === 'primary');
}, {
  message: 'At least one primary joint is required for rep counting',
});

export type TrackedJointData = z.infer<typeof TrackedJointSchema>;
export type PrimaryTrackedJointData = z.infer<typeof PrimaryTrackedJointSchema>;
export type SecondaryTrackedJointData = z.infer<typeof SecondaryTrackedJointSchema>;
export type JointConfigData = z.infer<typeof JointConfigSchema>;
export type StateRangesData = z.infer<typeof StateRangesSchema>;

// ============================================
// STEP 5: POSITION CHECKS
// ============================================

export const PositionCheckLandmarksSchema = z.object({
  primary: z.string().min(1),
  secondary: z.string().min(1),
  tertiary: z.string().optional(),
  quaternary: z.string().optional(),
});

/**
 * Position check condition (single threshold - not per difficulty)
 */
export const PositionCheckConditionSchema = z.object({
  operator: z.enum([
    'should_not_exceed',
    'should_exceed',
    'approximately_equal',
    'greater_than_ratio',
    'less_than_ratio',
  ]),
  threshold: z.number().min(0, 'Threshold must be >= 0'),
});

export const PositionCheckSchema = z.object({
  checkId: z.string().min(1, 'Check ID is required'),
  type: z.enum([
    'forward_comparison',
    'vertical_comparison',
    'sideways_comparison',
    'distance_ratio',
    'horizontal_alignment',
    'vertical_alignment',
    'depth_alignment',
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
}).superRefine((data, ctx) => {
  if (data.type === 'distance_ratio') {
    if (!data.landmarks.tertiary) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Tertiary landmark is required for Distance Ratio check',
        path: ['landmarks', 'tertiary'],
      });
    }
    if (!data.landmarks.quaternary) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Quaternary landmark is required for Distance Ratio check',
        path: ['landmarks', 'quaternary'],
      });
    }
  }
});

export const PositionChecksSchema = z.object({
  positionChecks: z.array(PositionCheckSchema).default([]),
});

export type PositionCheckData = z.infer<typeof PositionCheckSchema>;
export type PositionChecksData = z.infer<typeof PositionChecksSchema>;

// ============================================
// STEP 6: REP/DURATION CONFIG (Unified - no difficulty)
// ============================================

export const RepCountingConfigSchema = z.object({
  reps: z.number().min(1).optional(),
  duration: z.number().min(1).optional(),
  minRepIntervalMs: z.number().min(0).optional(),
  maxRepIntervalMs: z.number().min(0).optional(),
  gracePeriodMs: z.number().min(0).optional(),
}).refine(data => {
  // Must have either reps or duration
  return data.reps !== undefined || data.duration !== undefined;
}, {
  message: 'Either reps or duration is required',
});

export type RepCountingConfigData = z.infer<typeof RepCountingConfigSchema>;

// ============================================
// STEP 7: EXTRAS (ATTRIBUTES + FEEDBACK)
// ============================================

export const FeedbackAssignmentSchema = z.object({
  messageId: z.string().min(1, 'Message is required'),
  context: z.enum(['motivational', 'tip']),
  message: LocalizedTextSchema.optional(), // UI preview only
});

export const ExtrasSchema = z.object({
  muscles: z.array(z.string().uuid()).default([]),
  equipment: z.array(z.string().uuid()).default([]),
  tags: z.array(z.string().uuid()).default([]),
  feedbackAssignments: z.array(FeedbackAssignmentSchema).default([]),
});

export type FeedbackAssignmentData = z.infer<typeof FeedbackAssignmentSchema>;
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
  // Step 6 (unified)
  repConfig: RepCountingConfigSchema,
  // Step 7
  extras: ExtrasSchema,
});

export type WizardData = z.infer<typeof WizardDataSchema>;

// ============================================
// VALIDATION HELPERS
// ============================================

/**
 * Get outer min from StateRanges (for validation)
 */
function getOuterMinFromRanges(ranges: z.infer<typeof StateRangesSchema>): number {
  let min = ranges.perfect.min;
  if (ranges.normal) min = Math.min(min, ranges.normal.min);
  if (ranges.pad) min = Math.min(min, ranges.pad.min);
  return min;
}

/**
 * Get outer max from StateRanges (for validation)
 */
function getOuterMaxFromRanges(ranges: z.infer<typeof StateRangesSchema>): number {
  let max = ranges.perfect.max;
  if (ranges.normal) max = Math.max(max, ranges.normal.max);
  if (ranges.pad) max = Math.max(max, ranges.pad.max);
  return max;
}

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
        RepCountingConfigSchema.parse(data.repConfig);
        break;
      case 7:
        ExtrasSchema.parse(data.extras);
        break;
    }
    return { valid: true, errors: [] };
  } catch (error) {
    if (error instanceof z.ZodError) {
      const zodError = error as z.ZodError<unknown>;
      errors.push(...zodError.issues.map(e => `${e.path.join('.')}: ${e.message}`));
    }
    return { valid: false, errors };
  }
}

/**
 * Check if minimum required steps are complete for draft save
 */
export function canSaveAsDraft(data: Partial<WizardData>): boolean {
  try {
    BasicInfoSchema.parse(data.basicInfo);
    return true;
  } catch {
    return false;
  }
}

/**
 * Check if all steps are complete for publishing
 */
export function canPublish(data: Partial<WizardData>): {
  valid: boolean;
  errors: string[];
  incompleteSteps: number[];
} {
  const errors: string[] = [];
  const incompleteSteps: number[] = [];

  // Step 1: Basic info
  try {
    BasicInfoSchema.parse(data.basicInfo);
  } catch (e) {
    incompleteSteps.push(1);
    if (e instanceof z.ZodError) {
      errors.push(...e.issues.map(err => `Step 1: ${err.message}`));
    }
  }

  // Step 2: Counting method
  try {
    CountingMethodSchema.parse(data.countingMethod);
  } catch (e) {
    incompleteSteps.push(2);
    if (e instanceof z.ZodError) {
      errors.push(...e.issues.map(err => `Step 2: ${err.message}`));
    }
  }

  // Step 3: Camera position
  try {
    CameraPositionSchema.parse(data.cameraPosition);
  } catch (e) {
    incompleteSteps.push(3);
    if (e instanceof z.ZodError) {
      errors.push(...e.issues.map(err => `Step 3: ${err.message}`));
    }
  }

  // Step 4: Joint config
  try {
    JointConfigSchema.parse(data.jointConfig);
  } catch (e) {
    incompleteSteps.push(4);
    if (e instanceof z.ZodError) {
      errors.push(...e.issues.map(err => `Step 4: ${err.message}`));
    }
  }

  // Step 5: Position checks (optional)
  try {
    PositionChecksSchema.parse(data.positionChecks);
  } catch (e) {
    incompleteSteps.push(5);
    if (e instanceof z.ZodError) {
      errors.push(...e.issues.map(err => `Step 5: ${err.message}`));
    }
  }

  // Step 6: Rep config
  try {
    // Validate based on counting method
    const isHold = data.countingMethod?.countingMethodCode === 'hold';
    if (isHold) {
      if (!data.repConfig?.duration || data.repConfig.duration < 1) {
        throw new Error('Duration is required for hold exercises');
      }
    } else {
      if (!data.repConfig?.reps || data.repConfig.reps < 1) {
        throw new Error('Reps are required for rep-based exercises');
      }
    }
  } catch (e) {
    incompleteSteps.push(6);
    errors.push(`Step 6: ${e instanceof Error ? e.message : 'Invalid config'}`);
  }

  // Step 7: Extras (optional but should be valid if present)
  try {
    ExtrasSchema.parse(data.extras);
  } catch (e) {
    incompleteSteps.push(7);
    if (e instanceof z.ZodError) {
      errors.push(...e.issues.map(err => `Step 7: ${err.message}`));
    }
  }

  return {
    valid: incompleteSteps.length === 0,
    errors,
    incompleteSteps,
  };
}

// ============================================
// STATE RANGES DEFAULTS
// ============================================

/**
 * Create default StateRanges for a primary joint (up_down exercise)
 */
export function createDefaultPrimaryStateRanges(): {
  upRange: z.infer<typeof StateRangesSchema>;
  downRange: z.infer<typeof StateRangesSchema>;
} {
  return {
    upRange: {
      perfect: { min: 150, max: 180 },
      normal: { min: 140, max: 180 },
      pad: { min: 130, max: 180 },
    },
    downRange: {
      perfect: { min: 60, max: 90 },
      normal: { min: 50, max: 100 },
      pad: { min: 40, max: 110 },
    },
  };
}

/**
 * Create default StateRanges for a secondary joint
 */
export function createDefaultSecondaryStateRanges(): z.infer<typeof StateRangesSchema> {
  return {
    perfect: { min: 160, max: 180 },
    normal: { min: 150, max: 180 },
    warning: { min: 0, max: 150 },
  };
}

/**
 * Create default state messages
 */
export function createDefaultStateMessages(jointName: string): z.infer<typeof StateMessagesSchema> {
  return {
    perfect: { ar: 'ممتاز!', en: 'Perfect!' },
    normal: { ar: 'جيد، استمر', en: 'Good, keep going' },
    pad: { ar: 'مقبول', en: 'Acceptable' },
    warning: { ar: `تحقق من وضع ${jointName}`, en: `Check your ${jointName} position` },
    danger: { ar: 'توقف! وضع خطير', en: 'Stop! Dangerous position' },
  };
}
