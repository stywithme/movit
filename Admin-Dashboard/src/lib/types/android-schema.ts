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
 * Optionally includes pre-generated audio URLs for TTS replacement
 */
export interface LocalizedText {
  ar: string;
  en: string;
  /** Pre-generated Arabic audio file URL */
  audioAr?: string;
  /** Pre-generated English audio file URL */
  audioEn?: string;
}

/**
 * Counting method types (as expected by Android)
 */
export type CountingMethod = 'up_down' | 'hold';

/**
 * Pose position codes (single code; mobile derives direction/posture/region)
 */
export type PosePosition =
  | 'standing_front' | 'standing_back' | 'standing_side'
  | 'standing_side_left' | 'standing_side_right' | 'standing_diagonal'
  | 'standing_front_upper' | 'standing_back_upper' | 'standing_side_upper'
  | 'standing_front_lower' | 'standing_back_lower' | 'standing_side_lower'
  | 'prone_side' | 'prone_front'
  | 'supine_side' | 'supine_front'
  | 'side_lying';

/**
 * Joint role in tracking
 */
export type JointRole = 'primary' | 'secondary';

/**
 * Position check types (synced with Android PositionCheckType enum)
 */
export type PositionCheckType =
  | 'forward_comparison'
  | 'vertical_comparison'
  | 'sideways_comparison'
  | 'distance_ratio'
  | 'horizontal_alignment'
  | 'vertical_alignment'
  | 'depth_alignment';

/**
 * Condition operators for position checks (synced with Android PositionOperator enum)
 */
export type ConditionOperator =
  | 'should_not_exceed'
  | 'should_exceed'
  | 'approximately_equal'
  | 'greater_than_ratio'
  | 'less_than_ratio';

/**
 * Severity levels for errors
 */
export type Severity = 'error' | 'warning' | 'tip';

/**
 * Message assignment targets
 */
export type MessageAssignmentTarget = 'joint_state' | 'feedback' | 'position';

/**
 * Phase names (used in activePhases)
 */
export type PhaseName =
  | 'all'
  | 'top'
  | 'down'
  | 'bottom'
  | 'up';

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
 * Zone-based message (for up_down exercises)
 * Allows different messages for up and down positions
 */
export interface ZoneBasedMessage {
  up?: LocalizedText;
  down?: LocalizedText;
}

/**
 * State messages - supports two formats:
 * 
 * 1. Simple format (for hold exercises): Single message per state
 *    { perfect: { ar: "...", en: "..." } }
 * 
 * 2. Zone format (for up_down): Different messages per zone
 *    { perfect: { up: { ar: "...", en: "..." }, down: { ar: "...", en: "..." } } }
 * 
 * All messages are optional - can have just up, just down, both, or none
 */
export type StateMessageValue = LocalizedText | ZoneBasedMessage;

export interface StateMessages {
  perfect?: StateMessageValue;
  normal?: StateMessageValue;
  pad?: StateMessageValue;
  warning?: StateMessageValue;
  danger?: StateMessageValue;
}

/**
 * Check if a message value is zone-based (has up/down) or simple (has ar/en)
 */
export function isZoneBasedMessage(msg: StateMessageValue | undefined): msg is ZoneBasedMessage {
  if (!msg) return false;
  return 'up' in msg || 'down' in msg;
}

/**
 * Check if a message value is a simple LocalizedText
 */
export function isSimpleMessage(msg: StateMessageValue | undefined): msg is LocalizedText {
  if (!msg) return false;
  return 'ar' in msg || 'en' in msg;
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
  errorMessage?: LocalizedText;
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

/**
 * Message assignment (library-based)
 */
export interface MessageAssignment {
  messageId: string;
  target: MessageAssignmentTarget;
  context?: string;
  jointCode?: string;
  zone?: 'up' | 'down';
  checkId?: string;
  sortOrder?: number;
}

// ============================================
// POSE VARIANT
// ============================================

/**
 * Pose variant configuration for Android
 */
export interface PoseVariantConfig {
  name: LocalizedText;
  posePosition: PosePosition;
  trackedJoints: TrackedJoint[];
  positionChecks?: PositionCheck[];
  feedbackMessages?: FeedbackMessages;
  messageAssignments?: MessageAssignment[];
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
  imageUrl?: string;
  category: Category;
  countingMethod: CountingMethod;
  muscles: string[];
  equipment: string[];
  tags: string[];
  /** Rep counting config at exercise level (not per difficulty) */
  repCountingConfig: RepCountingConfig;
  poseVariants: PoseVariantConfig[];

  // ═══════════════════════════════════════════════════════════════
  // WEIGHT & METRICS CONFIGURATION
  // ═══════════════════════════════════════════════════════════════

  /** Does this exercise support weights? */
  supportsWeight?: boolean;

  /** Weight limits (kg) */
  minWeight?: number;
  maxWeight?: number;
  defaultWeight?: number;

  /** Report metrics configuration */
  reportMetrics?: ReportMetricsConfig;

  /** Is this exercise bilateral (has paired joints)? - auto-detected */
  isBilateral?: boolean;

  /** Does this exercise have position checks? - auto-detected */
  hasPositionChecks?: boolean;

  /** Bilateral configuration (per-rep side alternation) */
  bilateralConfig?: BilateralConfig;
}

// ============================================
// BILATERAL EXERCISE CONFIGURATION
// ============================================

export interface BilateralConfig {
  switchEvery: number;           // Switch side every N reps (default: 1)
  startSide: string;             // 'left' or 'right'
}

// ============================================
// REPORT METRICS CONFIGURATION
// ============================================

/**
 * Available metric codes for mobile app
 */
export type MetricCode =
  // Core
  | 'FORM_SCORE'
  | 'REP_COUNT'
  | 'DURATION'
  // Kinematic
  | 'ROM'
  | 'SYMMETRY'
  | 'STABILITY'
  // Temporal
  | 'TEMPO'
  | 'TUT'
  | 'HOLD_DURATION'
  // Quality
  | 'ALIGNMENT'
  | 'FORM_CONSISTENCY'
  | 'FATIGUE_INDEX'
  // Power
  | 'VELOCITY'
  // Load
  | 'WEIGHT'
  | 'VOLUME'
  | 'EST_1RM';

/**
 * Report metrics configuration for mobile display
 */
export interface ReportMetricsConfig {
  /** Primary metrics (shown as main cards) - 2-3 max */
  primary: MetricCode[];
  /** Optional metrics (available in expanded view) */
  optional?: MetricCode[];
  /** Excluded metrics (hidden from report) */
  excluded?: MetricCode[];
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
      return ['top', 'down', 'bottom', 'up'];
    case 'hold':
      return ['all'];
    default:
      return ['top', 'down', 'bottom', 'up'];
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
