/**
 * Exercise Types - State-Based System
 * ====================================
 * 
 * Types for exercise creation and management.
 * Aligned with Android JSON Schema - State-based (no difficulty levels).
 */

import type { LocalizedText, CountingMethodCode, JointStateName } from '@/lib/types/localized';

// ============================================
// ANGLE RANGE TYPES
// ============================================

/**
 * Basic angle range (min/max)
 */
export interface AngleRange {
  min: number;
  max: number;
}

/**
 * State-based ranges (replaces DifficultyRanges)
 */
export interface StateRanges {
  perfect: AngleRange;
  normal?: AngleRange;
  pad?: AngleRange;
  warning?: AngleRange;
  danger?: AngleRange;
}

/**
 * Localized text with optional audio URLs
 */
export interface LocalizedTextWithAudio {
  ar: string;
  en: string;
  audioAr?: string;
  audioEn?: string;
}

/**
 * Zone-based message (for up_down exercises)
 */
export interface ZoneBasedMessage {
  up?: LocalizedTextWithAudio;
  down?: LocalizedTextWithAudio;
}

/**
 * State message value - can be simple or zone-based
 * Simple (for hold): { ar: "...", en: "...", audioAr?: "...", audioEn?: "..." }
 * Zone (for up_down): { up: {...}, down: {...} }
 */
export type StateMessageValue = LocalizedTextWithAudio | ZoneBasedMessage;

/**
 * State messages - supports both formats
 */
export interface StateMessages {
  perfect?: StateMessageValue;
  normal?: StateMessageValue;
  pad?: StateMessageValue;
  warning?: StateMessageValue;
  danger?: StateMessageValue;
}

/**
 * Check if a message value is zone-based
 */
export function isZoneBasedMessage(msg: StateMessageValue | undefined): msg is ZoneBasedMessage {
  if (!msg) return false;
  return 'up' in msg || 'down' in msg;
}

/**
 * Check if a message value is simple LocalizedText
 */
export function isSimpleMessage(msg: StateMessageValue | undefined): msg is LocalizedText {
  if (!msg) return false;
  return 'ar' in msg || 'en' in msg;
}

// ============================================
// TRACKED JOINT TYPES
// ============================================

/**
 * Joint role in the exercise
 */
export type JointRole = 'primary' | 'secondary';

/**
 * Base tracked joint properties
 */
interface BaseTrackedJoint {
  joint: string;
  role: JointRole;
  startPose: AngleRange;
  stateMessages?: StateMessages;
  pairedWith?: string;
  invertIndicator?: boolean;
}

/**
 * Primary tracked joint - for rep counting
 */
export interface PrimaryTrackedJoint extends BaseTrackedJoint {
  role: 'primary';
  upRange: StateRanges;
  downRange: StateRanges;
}

/**
 * Secondary tracked joint - for form feedback only
 */
export interface SecondaryTrackedJoint extends BaseTrackedJoint {
  role: 'secondary';
  range: StateRanges;
}

/**
 * Union type for tracked joints
 */
export type TrackedJoint = PrimaryTrackedJoint | SecondaryTrackedJoint;

/**
 * Joint pair for mirroring settings (left/right)
 */
export interface JointPair {
  left: string;
  right: string;
  label: string;
}

/**
 * Predefined joint pairs (matching MediaPipe Pose landmarks)
 */
export const JOINT_PAIRS: JointPair[] = [
  // Upper body
  { left: 'left_shoulder', right: 'right_shoulder', label: 'Shoulders' },
  { left: 'left_elbow', right: 'right_elbow', label: 'Elbows' },
  { left: 'left_wrist', right: 'right_wrist', label: 'Wrists' },
  // Lower body
  { left: 'left_hip', right: 'right_hip', label: 'Hips' },
  { left: 'left_knee', right: 'right_knee', label: 'Knees' },
  { left: 'left_ankle', right: 'right_ankle', label: 'Ankles' },
  // Feet
  { left: 'left_heel', right: 'right_heel', label: 'Heels' },
  { left: 'left_foot_index', right: 'right_foot_index', label: 'Foot Index' },
];

// ============================================
// BILATERAL JOINTS (Virtual joints for paired tracking)
// ============================================

/**
 * Bilateral joint - represents a pair of joints (left + right)
 * When admin selects "Knees", it automatically creates both left_knee and right_knee
 */
export interface BilateralJoint {
  code: string;           // e.g., "knees"
  label: LocalizedText;   // { ar: "الركبتين", en: "Knees" }
  leftJoint: string;      // e.g., "left_knee"
  rightJoint: string;     // e.g., "right_knee"
}

/**
 * Predefined bilateral joints
 */
export const BILATERAL_JOINTS: BilateralJoint[] = [
  { code: 'shoulders', label: { ar: 'الكتفين', en: 'Shoulders' }, leftJoint: 'left_shoulder', rightJoint: 'right_shoulder' },
  { code: 'elbows', label: { ar: 'الكوعين', en: 'Elbows' }, leftJoint: 'left_elbow', rightJoint: 'right_elbow' },
  { code: 'wrists', label: { ar: 'الرسغين', en: 'Wrists' }, leftJoint: 'left_wrist', rightJoint: 'right_wrist' },
  { code: 'hips', label: { ar: 'الوركين', en: 'Hips' }, leftJoint: 'left_hip', rightJoint: 'right_hip' },
  { code: 'knees', label: { ar: 'الركبتين', en: 'Knees' }, leftJoint: 'left_knee', rightJoint: 'right_knee' },
  { code: 'ankles', label: { ar: 'الكاحلين', en: 'Ankles' }, leftJoint: 'left_ankle', rightJoint: 'right_ankle' },
];

/**
 * Get bilateral joint by code
 */
export function getBilateralJoint(code: string): BilateralJoint | undefined {
  return BILATERAL_JOINTS.find(j => j.code === code);
}

/**
 * Check if a joint code is bilateral
 */
export function isBilateralJointCode(code: string): boolean {
  return BILATERAL_JOINTS.some(j => j.code === code);
}

/**
 * Expand bilateral joint to left and right joints
 */
export function expandBilateralJoint(code: string): { left: string; right: string } | null {
  const bilateral = getBilateralJoint(code);
  if (!bilateral) return null;
  return { left: bilateral.leftJoint, right: bilateral.rightJoint };
}

// ============================================
// REPORT METRICS CONFIGURATION
// ============================================

/**
 * Available metrics for reports
 */
export type MetricCode =
  // Core (always available)
  | 'form_score'
  | 'rep_count'
  | 'duration'
  // Kinematic
  | 'rom'
  | 'symmetry'
  | 'stability'
  // Temporal
  | 'tempo'
  | 'tut'
  | 'hold_duration'
  // Quality
  | 'alignment'
  | 'form_consistency'
  | 'fatigue_index'
  | 'tempo_consistency'
  // Power
  | 'velocity'
  | 'velocity_loss'
  // Load
  | 'weight'
  | 'volume'
  | 'est_1rm';

/**
 * Metric definition
 */
export interface MetricDefinition {
  code: MetricCode;
  label: LocalizedText;
  unit: string;
  category: 'core' | 'kinematic' | 'temporal' | 'quality' | 'power' | 'load';
  autoInclude: {
    repBased: boolean;
    hold: boolean;
    bilateral: boolean;
    weighted: boolean;
    hasPositionChecks?: boolean;  // Only show if exercise has position checks
  };
  minReps?: number;  // Minimum reps required to show this metric
}

/**
 * All available metrics with their definitions
 */
export const METRIC_DEFINITIONS: MetricDefinition[] = [
  // Core
  {
    code: 'form_score', label: { ar: 'جودة الأداء', en: 'Form Score' }, unit: '%', category: 'core',
    autoInclude: { repBased: true, hold: true, bilateral: true, weighted: true }
  },
  {
    code: 'rep_count', label: { ar: 'عدد العدات', en: 'Rep Count' }, unit: '', category: 'core',
    autoInclude: { repBased: true, hold: false, bilateral: true, weighted: true }
  },
  {
    code: 'duration', label: { ar: 'المدة', en: 'Duration' }, unit: 's', category: 'core',
    autoInclude: { repBased: true, hold: true, bilateral: true, weighted: true }
  },

  // Kinematic
  {
    code: 'rom', label: { ar: 'المدى الحركي', en: 'Range of Motion' }, unit: '°', category: 'kinematic',
    autoInclude: { repBased: true, hold: false, bilateral: true, weighted: true }
  },
  {
    code: 'symmetry', label: { ar: 'التوازن', en: 'Symmetry' }, unit: '%', category: 'kinematic',
    autoInclude: { repBased: false, hold: false, bilateral: true, weighted: false }
  },
  {
    code: 'stability', label: { ar: 'الثبات', en: 'Stability' }, unit: '%', category: 'kinematic',
    autoInclude: { repBased: false, hold: true, bilateral: true, weighted: false }
  },

  // Temporal
  {
    code: 'tempo', label: { ar: 'الإيقاع', en: 'Tempo' }, unit: 's', category: 'temporal',
    autoInclude: { repBased: true, hold: false, bilateral: true, weighted: true }
  },
  {
    code: 'tut', label: { ar: 'الوقت تحت الضغط', en: 'Time Under Tension' }, unit: 's', category: 'temporal',
    autoInclude: { repBased: true, hold: false, bilateral: true, weighted: true }
  },
  {
    code: 'hold_duration', label: { ar: 'مدة الثبات', en: 'Hold Duration' }, unit: 's', category: 'temporal',
    autoInclude: { repBased: false, hold: true, bilateral: true, weighted: false }
  },

  // Quality
  {
    code: 'alignment', label: { ar: 'دقة المحاذاة', en: 'Alignment Accuracy' }, unit: '%', category: 'quality',
    autoInclude: { repBased: false, hold: false, bilateral: false, weighted: false, hasPositionChecks: true }
  },
  {
    code: 'form_consistency', label: { ar: 'ثبات الشكل', en: 'Form Consistency' }, unit: '%', category: 'quality',
    autoInclude: { repBased: true, hold: false, bilateral: true, weighted: true }, minReps: 4
  },
  {
    code: 'fatigue_index', label: { ar: 'نقطة التعب', en: 'Fatigue Index' }, unit: '#', category: 'quality',
    autoInclude: { repBased: true, hold: false, bilateral: true, weighted: true }, minReps: 4
  },

  // Quality (V2)
  {
    code: 'tempo_consistency', label: { ar: 'ثبات الإيقاع', en: 'Tempo Consistency' }, unit: '%', category: 'quality',
    autoInclude: { repBased: true, hold: false, bilateral: true, weighted: true }, minReps: 3
  },

  // Power
  {
    code: 'velocity', label: { ar: 'السرعة', en: 'Velocity' }, unit: '°/s', category: 'power',
    autoInclude: { repBased: false, hold: false, bilateral: false, weighted: false }
  },
  {
    code: 'velocity_loss', label: { ar: 'فقدان السرعة', en: 'Velocity Loss' }, unit: '%', category: 'power',
    autoInclude: { repBased: true, hold: false, bilateral: true, weighted: true }, minReps: 3
  },

  // Load
  {
    code: 'weight', label: { ar: 'الوزن', en: 'Weight' }, unit: 'kg', category: 'load',
    autoInclude: { repBased: false, hold: false, bilateral: false, weighted: true }
  },
  {
    code: 'volume', label: { ar: 'الحجم الكلي', en: 'Total Volume' }, unit: 'kg', category: 'load',
    autoInclude: { repBased: false, hold: false, bilateral: false, weighted: true }
  },
  {
    code: 'est_1rm', label: { ar: 'القوة القصوى', en: 'Est. 1RM' }, unit: 'kg', category: 'load',
    autoInclude: { repBased: false, hold: false, bilateral: false, weighted: true }
  },
];

/**
 * Report metrics configuration (stored in Exercise.reportMetrics)
 */
export interface ReportMetricsConfig {
  primary: MetricCode[];     // Main cards to show (2-3)
  optional?: MetricCode[];   // Extra metrics admin selected
  excluded?: MetricCode[];   // Explicitly hidden metrics
}

/**
 * Get auto-included metrics based on exercise type
 */
export function getAutoIncludedMetrics(options: {
  countingMethod: 'up_down' | 'hold';
  isBilateral: boolean;
  supportsWeight: boolean;
  hasPositionChecks?: boolean;
}): MetricCode[] {
  const { countingMethod, isBilateral, supportsWeight, hasPositionChecks } = options;
  const isRepBased = countingMethod !== 'hold';

  return METRIC_DEFINITIONS
    .filter(m => {
      // Special case: alignment requires position checks
      if (m.autoInclude.hasPositionChecks) {
        return hasPositionChecks === true;
      }

      if (isRepBased && m.autoInclude.repBased) return true;
      if (!isRepBased && m.autoInclude.hold) return true;
      if (isBilateral && m.autoInclude.bilateral && m.code === 'symmetry') return true;
      if (supportsWeight && m.autoInclude.weighted && ['weight', 'volume', 'est_1rm'].includes(m.code)) return true;
      return false;
    })
    .map(m => m.code);
}

/**
 * Get auto-excluded (disabled) metrics based on exercise type.
 * These are metrics that are NOT APPLICABLE for this exercise type.
 * 
 * This should be merged with user-excluded metrics when saving to DB.
 */
export function getAutoExcludedMetrics(options: {
  countingMethod: 'up_down' | 'hold';
  isBilateral: boolean;
  supportsWeight: boolean;
  hasPositionChecks: boolean;
}): MetricCode[] {
  const { countingMethod, isBilateral, supportsWeight, hasPositionChecks } = options;
  const isHold = countingMethod === 'hold';

  const excluded: MetricCode[] = [];

  // Hold exercise restrictions
  if (isHold) {
    excluded.push('rep_count', 'tempo', 'tut', 'rom', 'form_consistency', 'fatigue_index', 'velocity', 'velocity_loss', 'tempo_consistency');
  } else {
    // Rep-based exercise restrictions
    excluded.push('hold_duration');
  }

  // Weight restrictions
  if (!supportsWeight) {
    excluded.push('weight', 'volume', 'est_1rm');
  }

  // Bilateral restrictions
  if (!isBilateral) {
    excluded.push('symmetry');
  }

  // Position Checks restrictions (Alignment)
  if (!hasPositionChecks) {
    excluded.push('alignment');
  }

  return excluded;
}

/**
 * Merge user-excluded metrics with auto-excluded (disabled) metrics.
 * Returns a complete excluded list for storage in DB.
 */
export function mergeExcludedMetrics(
  userExcluded: MetricCode[],
  autoExcluded: MetricCode[]
): MetricCode[] {
  const combined = new Set([...userExcluded, ...autoExcluded]);
  return Array.from(combined);
}

/**
 * Get default primary metrics for display
 */
export function getDefaultPrimaryMetrics(options: {
  countingMethod: 'up_down' | 'hold';
  isBilateral: boolean;
  supportsWeight: boolean;
}): MetricCode[] {
  const { countingMethod, isBilateral, supportsWeight } = options;

  if (countingMethod === 'hold') {
    return supportsWeight
      ? ['form_score', 'hold_duration', 'weight']
      : ['form_score', 'hold_duration'];
  }

  if (isBilateral && supportsWeight) {
    return ['form_score', 'symmetry', 'weight'];
  }

  if (isBilateral) {
    return ['form_score', 'symmetry', 'rom'];
  }

  if (supportsWeight) {
    return ['form_score', 'rom', 'weight'];
  }

  return ['form_score', 'rom'];
}

// ============================================
// POSITION CHECK TYPES
// ============================================

/**
 * Position check landmarks
 */
export interface PositionCheckLandmarks {
  primary: string;
  secondary: string;
  tertiary?: string;
  quaternary?: string;
}

/**
 * Position check condition (single threshold)
 */
export interface PositionCheckCondition {
  operator:
  | 'should_not_exceed'
  | 'should_exceed'
  | 'approximately_equal'
  | 'greater_than_ratio'
  | 'less_than_ratio';
  threshold: number;
}

/**
 * Position check input
 */
export interface PositionCheckInput {
  checkId: string;
  type: string;
  landmarks: PositionCheckLandmarks;
  condition: PositionCheckCondition;
  activePhases: string[];
  errorMessage: LocalizedTextWithAudio;
  messageId?: string; // Link to message library
  severity?: string;
  cooldownMs?: number;
  minErrorFrames?: number;
  sortOrder?: number;
}

// ============================================
// REP COUNTING CONFIG
// ============================================

/**
 * Rep counting config (unified - no difficulty levels)
 */
export interface RepCountingConfig {
  reps?: number;
  duration?: number;
  minRepIntervalMs?: number;
  maxRepIntervalMs?: number;
  gracePeriodMs?: number;
}

// ============================================
// FEEDBACK MESSAGES
// ============================================

/**
 * Feedback message assignment input (library-based)
 */
export type FeedbackMessageAssignmentTarget = 'joint_state' | 'feedback' | 'position';

export interface FeedbackMessageAssignmentInput {
  messageId: string;
  target: FeedbackMessageAssignmentTarget;
  context?: string;   // perfect | normal | pad | warning | danger | motivational | tip | error
  jointCode?: string; // For joint_state assignments
  zone?: 'up' | 'down';
  checkId?: string;   // For position check assignments
  sortOrder?: number;
}

// ============================================
// POSE VARIANT TYPES
// ============================================

/**
 * Pose variant input
 */
export interface PoseVariantInput {
  id?: string;
  tempId?: string;
  name: LocalizedText;
  description?: LocalizedText;
  posePositionId: string;
  trackedJointsConfig?: TrackedJoint[];
  positionChecks?: PositionCheckInput[];
  messageAssignments?: FeedbackMessageAssignmentInput[];
  sortOrder?: number;
}

// ============================================
// EXERCISE INPUT TYPES
// ============================================

/**
 * Weight configuration for exercise
 */
export interface WeightConfig {
  supportsWeight: boolean;
  minWeight?: number;      // kg
  maxWeight?: number;      // kg
  defaultWeight?: number;  // kg
}

/**
 * Bilateral configuration for exercise
 * Controls per-rep left/right side alternation
 */
export interface BilateralConfigInput {
  switchEvery: number;           // Switch side every N reps (default: 1)
  startSide: 'left' | 'right';  // Which side starts (default: 'right')
}

/**
 * Exercise creation input
 */
export interface CreateExerciseInput {
  name: LocalizedText;
  description?: LocalizedText;
  instructions?: LocalizedText;
  categoryId: string;
  countingMethodId: string;
  imageUrl?: string;
  slug?: string;
  muscles?: string[];
  equipment?: string[];
  tags?: string[];
  repCountingConfig?: RepCountingConfig;
  poseVariants?: PoseVariantInput[];

  // Weight configuration
  weightConfig?: WeightConfig;

  // Bilateral configuration
  bilateralConfig?: BilateralConfigInput;

  // Report metrics configuration
  reportMetrics?: ReportMetricsConfig;
}

/**
 * Exercise update input
 */
export interface UpdateExerciseInput extends Partial<CreateExerciseInput> {
  status?: 'draft' | 'published';
}

// ============================================
// HELPER FUNCTIONS
// ============================================

/**
 * Get paired joint code
 */
export function getPairedJointCode(jointCode: string): string | undefined {
  const pair = JOINT_PAIRS.find(p => p.left === jointCode || p.right === jointCode);
  if (!pair) return undefined;
  return pair.left === jointCode ? pair.right : pair.left;
}

/**
 * Check if joint is primary
 */
export function isPrimaryJoint(joint: TrackedJoint): joint is PrimaryTrackedJoint {
  return joint.role === 'primary';
}

/**
 * Check if joint is secondary
 */
export function isSecondaryJoint(joint: TrackedJoint): joint is SecondaryTrackedJoint {
  return joint.role === 'secondary';
}

/**
 * Get outer min from StateRanges
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
 * Get outer max from StateRanges
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
 * Create default primary joint
 */
export function createDefaultPrimaryJoint(jointCode: string): PrimaryTrackedJoint {
  return {
    joint: jointCode,
    role: 'primary',
    startPose: { min: 150, max: 180 },
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
    stateMessages: createDefaultStateMessages(jointCode),
    pairedWith: getPairedJointCode(jointCode),
  };
}

/**
 * Create default secondary joint
 */
export function createDefaultSecondaryJoint(jointCode: string): SecondaryTrackedJoint {
  return {
    joint: jointCode,
    role: 'secondary',
    startPose: { min: 150, max: 180 },
    range: {
      perfect: { min: 160, max: 180 },
      normal: { min: 150, max: 180 },
      warning: { min: 0, max: 150 },
    },
    stateMessages: createDefaultStateMessages(jointCode),
    pairedWith: getPairedJointCode(jointCode),
  };
}

/**
 * Create default state messages for a joint
 * @param jointCode - The joint code (e.g., 'left_elbow')
 * @param useZoneMessages - If true, creates zone-based messages (up/down)
 */
export function createDefaultStateMessages(
  jointCode: string,
  useZoneMessages: boolean = false
): StateMessages {
  const jointName = jointCode.replace(/_/g, ' ').replace(/left |right /gi, '');
  const isLeft = jointCode.startsWith('left_');
  const side = isLeft ? 'left' : 'right';
  const sideAr = isLeft ? 'اليسرى' : 'اليمنى';

  if (useZoneMessages) {
    // Zone-based messages for up_down exercises
    return {
      perfect: {
        up: { ar: 'ممتاز! وضع مثالي', en: 'Perfect! Great form' },
        down: { ar: 'ممتاز! ثني مثالي', en: 'Perfect! Great bend' },
      },
      normal: {
        up: { ar: 'جيد، حاول أكثر', en: 'Good, try a bit more' },
        down: { ar: 'جيد، حاول الثني أكثر', en: 'Good, try bending more' },
      },
      pad: {
        up: { ar: 'مقبول', en: 'Acceptable' },
        down: { ar: 'مقبول', en: 'Acceptable' },
      },
      warning: {
        up: { ar: `${jointName} ${sideAr} بحاجة لتعديل`, en: `Adjust your ${side} ${jointName}` },
        down: { ar: `${jointName} ${sideAr} بحاجة لتعديل`, en: `Adjust your ${side} ${jointName}` },
      },
      danger: {
        down: { ar: 'توقف! وضع خطير', en: 'Stop! Dangerous position' },
      },
    };
  }

  // Simple messages for hold exercises
  return {
    perfect: { ar: 'ممتاز!', en: 'Perfect!' },
    normal: { ar: 'جيد', en: 'Good' },
    pad: { ar: 'مقبول', en: 'Acceptable' },
    warning: {
      ar: `تحقق من وضع ${jointName} ${sideAr}`,
      en: `Check your ${side} ${jointName} position`
    },
    danger: {
      ar: `توقف! ${jointName} ${sideAr} في وضع خطير`,
      en: `Stop! ${side} ${jointName} in dangerous position`
    },
  };
}

/**
 * Copy joint settings to paired joint
 */
export function copyToPairedJoint(source: TrackedJoint): TrackedJoint | null {
  const pairedCode = getPairedJointCode(source.joint);
  if (!pairedCode) return null;

  const newMessages = createDefaultStateMessages(pairedCode);

  if (isPrimaryJoint(source)) {
    return {
      ...source,
      joint: pairedCode,
      stateMessages: newMessages,
      pairedWith: source.joint,
    };
  } else {
    return {
      ...source,
      joint: pairedCode,
      stateMessages: newMessages,
      pairedWith: source.joint,
    };
  }
}

/**
 * Get default rep counting config based on counting method
 */
export function getDefaultRepCountingConfig(countingMethod: CountingMethodCode): RepCountingConfig {
  if (countingMethod === 'hold') {
    return {
      duration: 30,
      gracePeriodMs: 2500,
    };
  }
  return {
    reps: 12,
    minRepIntervalMs: 1500,
    maxRepIntervalMs: 5000,
  };
}

/**
 * Validate StateRanges for transition zone (for primary joints)
 */
export function validateTransitionZone(
  upRange: StateRanges,
  downRange: StateRanges
): { valid: boolean; error?: string } {
  const upMin = getOuterMin(upRange);
  const downMax = getOuterMax(downRange);

  if (upMin <= downMax) {
    return {
      valid: false,
      error: `Invalid transition zone: upRange min (${upMin}) must be greater than downRange max (${downMax})`,
    };
  }

  return { valid: true };
}

/**
 * Get state at a given angle for StateRanges
 * Returns the highest priority state that contains the angle
 */
export function getStateAtAngle(angle: number, ranges: StateRanges): JointStateName | null {
  // Priority: danger > warning > perfect > normal > pad
  if (ranges.danger && angle >= ranges.danger.min && angle <= ranges.danger.max) {
    return 'danger';
  }
  if (ranges.warning && angle >= ranges.warning.min && angle <= ranges.warning.max) {
    return 'warning';
  }
  if (angle >= ranges.perfect.min && angle <= ranges.perfect.max) {
    return 'perfect';
  }
  if (ranges.normal && angle >= ranges.normal.min && angle <= ranges.normal.max) {
    return 'normal';
  }
  if (ranges.pad && angle >= ranges.pad.min && angle <= ranges.pad.max) {
    return 'pad';
  }
  return null;
}
