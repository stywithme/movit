/**
 * Android JSON Schema Types - State-Based System
 * ================================================
 * 
 * These types EXACTLY match the Android app's expected JSON format.
 * This is the SINGLE SOURCE OF TRUTH for the mobile contract.
 * 
 * Updated: State-based system (no difficulty levels)
 * Reference: /Docs/State-Machine-Unified-Plan.md
 * 
 * IMPORTANT RULES:
 * 1. Never send `null` for any field - omit the key instead
 * 2. All LocalizedText must have both `ar` and `en` keys
 * 3. StateRanges follow priority: DANGER > WARNING > PERFECT > NORMAL > PAD
 */

// ============================================
// BASE TYPES
// ============================================

/**
 * Localized text for multi-language support
 */
export interface LocalizedText {
  ar: string;
  en: string;
}

/**
 * Counting method types (as expected by Android)
 */
export type CountingMethod = 'up_down' | 'push_pull' | 'hold';

/**
 * Camera position codes (as expected by Android)
 */
export type CameraPosition = 'side_view' | 'front_view' | 'back_view';

/**
 * Expected facing direction
 */
export type FacingDirection = 
  | 'facing_right' 
  | 'facing_left' 
  | 'facing_camera' 
  | 'facing_away' 
  | 'auto_detect';

/**
 * Joint role in tracking
 */
export type JointRole = 'primary' | 'secondary';

/**
 * Position check types
 */
export type PositionCheckType = 
  | 'forward_comparison'
  | 'vertical_alignment'
  | 'horizontal_alignment'
  | 'distance_ratio'
  | 'angle_constraint'
  | 'relative_position'
  | 'symmetry_check';

/**
 * Condition operators for position checks
 */
export type ConditionOperator = 
  | 'should_not_exceed'
  | 'should_exceed'
  | 'should_be_within'
  | 'should_equal'
  | 'approximately_equal';

/**
 * Severity levels for errors
 */
export type Severity = 'error' | 'warning' | 'tip';

/**
 * Phase names (used in activePhases)
 */
export type PhaseName = 
  | 'idle'
  | 'start' 
  | 'down' 
  | 'bottom' 
  | 'up' 
  | 'push' 
  | 'extended' 
  | 'pull'
  | 'hold'
  | 'count';

/**
 * Joint state types (new state-based system)
 */
export type JointStateName = 'perfect' | 'normal' | 'pad' | 'warning' | 'danger';

// ============================================
// RANGE TYPES
// ============================================

/**
 * Min/Max range for angles
 */
export interface AngleRange {
  min: number;
  max: number;
}

/**
 * State-based ranges (replaces DifficultyRanges)
 * Only 'perfect' is required, others are optional
 */
export interface StateRanges {
  /** Required: The ideal angle range (highest priority after danger/warning) */
  perfect: AngleRange;
  /** Optional: Good range, overlaps with perfect (lower priority) */
  normal?: AngleRange;
  /** Optional: Acceptable range, overlaps with normal (lowest counted priority) */
  pad?: AngleRange;
  /** Optional: Warning zone - rep not counted */
  warning?: AngleRange;
  /** Optional: Danger zone - rep invalidated, strong alert */
  danger?: AngleRange;
}

/**
 * State messages - one message per state
 */
export interface StateMessages {
  perfect?: LocalizedText;
  normal?: LocalizedText;
  pad?: LocalizedText;
  warning?: LocalizedText;
  danger?: LocalizedText;
}

// ============================================
// TRACKED JOINTS
// ============================================

/**
 * Base tracked joint properties
 */
interface BaseTrackedJoint {
  joint: string;
  role: JointRole;
  startPose: AngleRange;
  stateMessages?: StateMessages;
  pairedWith?: string;
  /** When true, visual indicator direction is inverted */
  invertIndicator?: boolean;
}

/**
 * Primary tracked joint for Up/Down and Push/Pull - has upRange and downRange
 */
export interface UpDownPrimaryTrackedJoint extends BaseTrackedJoint {
  role: 'primary';
  upRange: StateRanges;
  downRange: StateRanges;
}

/**
 * Primary tracked joint for Hold exercises - has single range
 */
export interface HoldPrimaryTrackedJoint extends BaseTrackedJoint {
  role: 'primary';
  range: StateRanges;
}

/**
 * Combined primary joint type (supports both modes)
 */
export type PrimaryTrackedJoint = UpDownPrimaryTrackedJoint | HoldPrimaryTrackedJoint;

/**
 * Secondary tracked joint - has single range for form checking
 */
export interface SecondaryTrackedJoint extends BaseTrackedJoint {
  role: 'secondary';
  range: StateRanges;
}

/**
 * Union type for tracked joints
 */
export type TrackedJoint = PrimaryTrackedJoint | SecondaryTrackedJoint;

// ============================================
// POSITION CHECKS
// ============================================

/**
 * Landmarks for position check
 */
export interface PositionCheckLandmarks {
  primary: string;
  secondary: string;
  tertiary?: string;
  quaternary?: string;
}

/**
 * Condition for position check (single threshold, not per-difficulty)
 */
export interface PositionCheckCondition {
  operator: ConditionOperator;
  threshold: number;
}

/**
 * Complete position check configuration
 */
export interface PositionCheck {
  id: string;
  type: PositionCheckType;
  landmarks: PositionCheckLandmarks;
  condition: PositionCheckCondition;
  activePhases: PhaseName[];
  errorMessage: LocalizedText;
  severity: Severity;
  cooldownMs: number;
  minErrorFrames: number;
}

// ============================================
// REP COUNTING CONFIG
// ============================================

/**
 * Rep counting configuration (unified - no difficulty levels)
 */
export interface RepCountingConfig {
  /** Target reps for rep-based exercises */
  reps?: number;
  /** Duration in seconds for hold exercises */
  duration?: number;
  /** Minimum time between reps in ms */
  minRepIntervalMs?: number;
  /** Maximum time between reps in ms */
  maxRepIntervalMs?: number;
  /** Grace period for hold exercises in ms */
  gracePeriodMs?: number;
}

// ============================================
// FEEDBACK MESSAGES
// ============================================

/**
 * Grouped feedback messages (simplified)
 */
export interface FeedbackMessages {
  motivational: LocalizedText[];
  tips: LocalizedText[];
}

// ============================================
// POSE VARIANT
// ============================================

/**
 * Pose variant configuration for Android
 */
export interface PoseVariantConfig {
  name: LocalizedText;
  cameraPosition: CameraPosition;
  expectedFacingDirection?: FacingDirection;
  trackedJoints: TrackedJoint[];
  positionChecks?: PositionCheck[];
  feedbackMessages?: FeedbackMessages;
}

// ============================================
// CATEGORY
// ============================================

/**
 * Category information
 */
export interface Category {
  code: string;
  name: LocalizedText;
}

// ============================================
// ROOT EXERCISE CONFIG
// ============================================

/**
 * Complete exercise configuration for Android
 * This is the ROOT object that gets exported to the mobile app
 */
export interface ExerciseConfig {
  name: LocalizedText;
  description?: LocalizedText;
  instructions?: LocalizedText;
  category: Category;
  countingMethod: CountingMethod;
  muscles: string[];
  equipment: string[];
  tags: string[];
  /** Rep counting config at exercise level (not per difficulty) */
  repCountingConfig: RepCountingConfig;
  poseVariants: PoseVariantConfig[];
}

// ============================================
// PHASE TEMPLATES
// ============================================

/**
 * Get phases array based on counting method
 */
export function getPhasesForCountingMethod(method: CountingMethod): PhaseName[] {
  switch (method) {
    case 'up_down':
      return ['start', 'down', 'bottom', 'up'];
    case 'push_pull':
      return ['start', 'push', 'extended', 'pull'];
    case 'hold':
      return ['hold'];
    default:
      return ['start', 'down', 'bottom', 'up'];
  }
}

// ============================================
// TYPE GUARDS
// ============================================

/**
 * Check if a tracked joint is primary
 */
export function isPrimaryJoint(joint: TrackedJoint): joint is PrimaryTrackedJoint {
  return joint.role === 'primary';
}

/**
 * Check if a tracked joint is secondary
 */
export function isSecondaryJoint(joint: TrackedJoint): joint is SecondaryTrackedJoint {
  return joint.role === 'secondary';
}

/**
 * Check if rep counting config is for HOLD exercise
 */
export function isHoldConfig(config: RepCountingConfig): boolean {
  return config.duration !== undefined && config.duration > 0;
}

// ============================================
// VALIDATION HELPERS
// ============================================

/**
 * Validate that an AngleRange is valid
 */
export function validateAngleRange(range: AngleRange, name: string): string[] {
  const errors: string[] = [];
  
  if (range.min < 0 || range.min > 180) {
    errors.push(`${name}: min must be between 0 and 180`);
  }
  if (range.max < 0 || range.max > 180) {
    errors.push(`${name}: max must be between 0 and 180`);
  }
  if (range.min > range.max) {
    errors.push(`${name}: min (${range.min}) cannot be greater than max (${range.max})`);
  }
  
  return errors;
}

/**
 * Validate StateRanges structure and coverage
 */
export function validateStateRanges(ranges: StateRanges, name: string): string[] {
  const errors: string[] = [];
  
  // Perfect is required
  if (!ranges.perfect) {
    errors.push(`${name}: 'perfect' range is required`);
    return errors;
  }
  
  errors.push(...validateAngleRange(ranges.perfect, `${name}.perfect`));
  
  // Validate optional ranges
  if (ranges.normal) {
    errors.push(...validateAngleRange(ranges.normal, `${name}.normal`));
    // Normal should extend beyond or equal perfect
    if (ranges.normal.min > ranges.perfect.min && ranges.normal.max < ranges.perfect.max) {
      errors.push(`${name}.normal: should extend at least one boundary of perfect range`);
    }
  }
  
  if (ranges.pad) {
    errors.push(...validateAngleRange(ranges.pad, `${name}.pad`));
    const outerRef = ranges.normal || ranges.perfect;
    if (ranges.pad.min > outerRef.min && ranges.pad.max < outerRef.max) {
      errors.push(`${name}.pad: should extend at least one boundary of normal/perfect range`);
    }
  }
  
  if (ranges.warning) {
    errors.push(...validateAngleRange(ranges.warning, `${name}.warning`));
  }
  
  if (ranges.danger) {
    errors.push(...validateAngleRange(ranges.danger, `${name}.danger`));
  }
  
  return errors;
}

/**
 * Check if a primary joint is in Hold mode (has range) vs Up/Down mode (has upRange/downRange)
 */
function isHoldPrimaryJoint(joint: PrimaryTrackedJoint): joint is HoldPrimaryTrackedJoint {
  return 'range' in joint && !('upRange' in joint);
}

/**
 * Validate that a primary joint has required fields
 * Supports both Hold mode (range) and Up/Down mode (upRange/downRange)
 */
export function validatePrimaryJoint(joint: TrackedJoint): string[] {
  const errors: string[] = [];
  
  if (joint.role !== 'primary') return errors;
  
  const primary = joint as PrimaryTrackedJoint;
  
  // Check if it's a Hold mode joint (has range instead of upRange/downRange)
  if (isHoldPrimaryJoint(primary)) {
    // Hold mode: validate single range
    if (!primary.range) {
      errors.push(`Primary joint ${primary.joint} is missing range (for hold exercise)`);
    } else {
      errors.push(...validateStateRanges(primary.range, `${primary.joint}.range`));
    }
  } else {
    // Up/Down mode: validate upRange and downRange
    const upDownPrimary = primary as UpDownPrimaryTrackedJoint;
    
    if (!upDownPrimary.upRange) {
      errors.push(`Primary joint ${upDownPrimary.joint} is missing upRange`);
    } else {
      errors.push(...validateStateRanges(upDownPrimary.upRange, `${upDownPrimary.joint}.upRange`));
    }
    
    if (!upDownPrimary.downRange) {
      errors.push(`Primary joint ${upDownPrimary.joint} is missing downRange`);
    } else {
      errors.push(...validateStateRanges(upDownPrimary.downRange, `${upDownPrimary.joint}.downRange`));
    }
    
    // Validate transition zone (upRange min should be > downRange max)
    if (upDownPrimary.upRange && upDownPrimary.downRange) {
      const upMin = getOuterMin(upDownPrimary.upRange);
      const downMax = getOuterMax(upDownPrimary.downRange);
      if (upMin <= downMax) {
        errors.push(`${upDownPrimary.joint}: upRange min (${upMin}) must be greater than downRange max (${downMax}) for valid transition zone`);
      }
    }
  }
  
  return errors;
}

/**
 * Validate that a secondary joint has required fields
 */
export function validateSecondaryJoint(joint: TrackedJoint): string[] {
  const errors: string[] = [];
  
  if (joint.role !== 'secondary') return errors;
  
  const secondary = joint as SecondaryTrackedJoint;
  
  if (!secondary.range) {
    errors.push(`Secondary joint ${secondary.joint} is missing range`);
  } else {
    errors.push(...validateStateRanges(secondary.range, `${secondary.joint}.range`));
  }
  
  return errors;
}

/**
 * Get the outer minimum from StateRanges
 */
export function getOuterMin(ranges: StateRanges): number {
  let min = ranges.perfect.min;
  if (ranges.normal) min = Math.min(min, ranges.normal.min);
  if (ranges.pad) min = Math.min(min, ranges.pad.min);
  if (ranges.warning) min = Math.min(min, ranges.warning.min);
  if (ranges.danger) min = Math.min(min, ranges.danger.min);
  return min;
}

/**
 * Get the outer maximum from StateRanges
 */
export function getOuterMax(ranges: StateRanges): number {
  let max = ranges.perfect.max;
  if (ranges.normal) max = Math.max(max, ranges.normal.max);
  if (ranges.pad) max = Math.max(max, ranges.pad.max);
  if (ranges.warning) max = Math.max(max, ranges.warning.max);
  if (ranges.danger) max = Math.max(max, ranges.danger.max);
  return max;
}

/**
 * Validate complete exercise config before export
 */
export function validateExerciseConfig(config: ExerciseConfig): string[] {
  const errors: string[] = [];
  
  // Basic validation
  if (!config.name?.en) {
    errors.push('Exercise must have an English name');
  }
  
  if (!config.countingMethod) {
    errors.push('Exercise must have a counting method');
  }
  
  if (!config.repCountingConfig) {
    errors.push('Exercise must have repCountingConfig');
  } else {
    const isHold = config.countingMethod === 'hold';
    if (isHold && !config.repCountingConfig.duration) {
      errors.push('Hold exercises must have duration in repCountingConfig');
    }
    if (!isHold && !config.repCountingConfig.reps) {
      errors.push('Rep-based exercises must have reps in repCountingConfig');
    }
  }
  
  // Must have at least one pose variant
  if (!config.poseVariants || config.poseVariants.length === 0) {
    errors.push('Exercise must have at least one pose variant');
    return errors;
  }
  
  for (const variant of config.poseVariants) {
    // Must have at least one tracked joint
    if (!variant.trackedJoints || variant.trackedJoints.length === 0) {
      errors.push(`Pose variant "${variant.name.en}" must have at least one tracked joint`);
      continue;
    }
    
    // Must have at least one primary joint
    const primaryJoints = variant.trackedJoints.filter(j => j.role === 'primary');
    if (primaryJoints.length === 0) {
      errors.push(`Pose variant "${variant.name.en}" must have at least one primary joint`);
    }
    
    // Validate each joint
    for (const joint of variant.trackedJoints) {
      if (joint.role === 'primary') {
        errors.push(...validatePrimaryJoint(joint));
      } else {
        errors.push(...validateSecondaryJoint(joint));
      }
    }
    
    // Validate position checks if present
    if (variant.positionChecks) {
      for (const check of variant.positionChecks) {
        if (!check.id) {
          errors.push('Position check must have an id');
    }
        if (!check.landmarks?.primary || !check.landmarks?.secondary) {
          errors.push(`Position check "${check.id}" must have primary and secondary landmarks`);
    }
        if (check.condition?.threshold === undefined) {
          errors.push(`Position check "${check.id}" must have a threshold`);
        }
      }
    }
  }
  
  return errors;
}
