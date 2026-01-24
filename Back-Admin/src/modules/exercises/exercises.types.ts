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
 * Zone-based message (for up_down and push_pull exercises)
 */
export interface ZoneBasedMessage {
  up?: LocalizedTextWithAudio;
  down?: LocalizedTextWithAudio;
}

/**
 * State message value - can be simple or zone-based
 * Simple (for hold): { ar: "...", en: "...", audioAr?: "...", audioEn?: "..." }
 * Zone (for up_down/push_pull): { up: {...}, down: {...} }
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
    | 'should_be_within'
    | 'should_equal'
    | 'approximately_equal';
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
 * Feedback message input (simplified - no common_mistake)
 * Audio URLs are now part of message: { ar, en, audioAr, audioEn }
 */
export interface FeedbackMessageInput {
  type: 'motivational' | 'tip';
  message: LocalizedTextWithAudio;
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
  cameraPositionId: string;
  referenceImageUrl?: string;
  expectedFacingDirection?: string;
  trackedJointsConfig?: TrackedJoint[];
  positionChecks?: PositionCheckInput[];
  feedbackMessages?: FeedbackMessageInput[];
  sortOrder?: number;
}

// ============================================
// EXERCISE INPUT TYPES
// ============================================

/**
 * Exercise creation input
 */
export interface CreateExerciseInput {
  name: LocalizedText;
  description?: LocalizedText;
  instructions?: LocalizedText;
  categoryId: string;
  countingMethodId: string;
  slug?: string;
  muscles?: string[];
  equipment?: string[];
  tags?: string[];
  repCountingConfig?: RepCountingConfig;
  poseVariants?: PoseVariantInput[];
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
    // Zone-based messages for up_down and push_pull exercises
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
