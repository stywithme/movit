/**
 * Core types for multi-language support and enums
 * ================================================
 * 
 * Updated: State-based system (no difficulty levels)
 */

/**
 * Localized text type for multi-language support
 */
export type LocalizedText = {
  ar: string;
  en: string;
};

/**
 * Localized text with optional audio URLs
 * Used for messages that support TTS generation
 */
export interface LocalizedTextWithAudio extends LocalizedText {
  audioAr?: string;
  audioEn?: string;
}

/**
 * Audio URLs for localized content
 */
export interface LocalizedAudio {
  ar?: string;
  en?: string;
}

/**
 * Exercise status
 */
export type ExerciseStatus = 'draft' | 'published';

/**
 * Media type
 */
export type MediaType = 'image' | 'video';

/**
 * Feedback message type (simplified - removed common_mistake)
 */
export type FeedbackType = 'motivational' | 'tip';

/**
 * Counting method codes (ALIGNED WITH ANDROID CONTRACT)
 */
export type CountingMethodCode = 'up_down' | 'hold';

/**
 * Camera position codes (internal - more specific)
 */
export type CameraPositionCode = 'side_left' | 'side_right' | 'front' | 'back';

/**
 * Camera position schema codes (for Android export)
 */
export type CameraPositionSchemaCode = 'side_view' | 'front_view' | 'back_view';

/**
 * Joint role in tracking
 */
export type JointRole = 'primary' | 'secondary';

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
 * Position check types (7 types - synced with Android PositionCheckType enum)
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
 * Severity levels for errors/warnings
 */
export type Severity = 'error' | 'warning' | 'tip';

/**
 * Phase names (matching Android engine)
 */
export type PhaseName =
  | 'all'
  | 'top'
  | 'down'
  | 'bottom'
  | 'up';

/**
 * Joint state names (new state-based system)
 */
export type JointStateName = 'perfect' | 'normal' | 'pad' | 'warning' | 'danger';

/**
 * Zone types for messages (up/down)
 */
export type ZoneType = 'up' | 'down';

/**
 * All zone types as array
 */
export const ZONE_TYPES: ZoneType[] = ['up', 'down'];

/**
 * All joint state names as array (for iteration)
 */
export const JOINT_STATE_NAMES: JointStateName[] = ['perfect', 'normal', 'pad', 'warning', 'danger'];

/**
 * Counted states (contribute to rep score)
 */
export const COUNTED_STATES: JointStateName[] = ['perfect', 'normal', 'pad'];

/**
 * State configuration (colors, rates, behavior)
 */
export const STATE_CONFIG: Record<JointStateName, {
  rate: number;
  isRepCounted: boolean;
  invalidatesRep: boolean;
  color: string;
  colorHex: string;
  label: { ar: string; en: string };
}> = {
  perfect: {
    rate: 100,
    isRepCounted: true,
    invalidatesRep: false,
    color: 'green',
    colorHex: '#22c55e',
    label: { ar: 'مثالي', en: 'Perfect' },
  },
  normal: {
    rate: 60,
    isRepCounted: true,
    invalidatesRep: false,
    color: 'yellow',
    colorHex: '#eab308',
    label: { ar: 'جيد', en: 'Good' },
  },
  pad: {
    rate: 20,
    isRepCounted: true,
    invalidatesRep: false,
    color: 'orange',
    colorHex: '#f97316',
    label: { ar: 'مقبول', en: 'Acceptable' },
  },
  warning: {
    rate: 0,
    isRepCounted: false,
    invalidatesRep: false,
    color: 'red',
    colorHex: '#ef4444',
    label: { ar: 'تحذير', en: 'Warning' },
  },
  danger: {
    rate: 0,
    isRepCounted: false,
    invalidatesRep: true,
    color: 'darkred',
    colorHex: '#991b1b',
    label: { ar: 'خطر', en: 'Danger' },
  },
};

/**
 * Angle joints that can be tracked (subset of all landmarks)
 */
export type AngleJointCode =
  | 'left_shoulder'
  | 'right_shoulder'
  | 'left_elbow'
  | 'right_elbow'
  | 'left_hip'
  | 'right_hip'
  | 'left_knee'
  | 'right_knee'
  | 'left_ankle'
  | 'right_ankle'
  | 'spine';

/**
 * Full landmark codes (for position checks)
 * Matches MediaPipe Pose landmarks 0-32
 */
export type LandmarkCode =
  // Face (0-10)
  | 'nose'
  | 'left_eye_inner'
  | 'left_eye'
  | 'left_eye_outer'
  | 'right_eye_inner'
  | 'right_eye'
  | 'right_eye_outer'
  | 'left_ear'
  | 'right_ear'
  | 'mouth_left'
  | 'mouth_right'
  // Upper body (11-16)
  | 'left_shoulder'
  | 'right_shoulder'
  | 'left_elbow'
  | 'right_elbow'
  | 'left_wrist'
  | 'right_wrist'
  // Hands (17-22)
  | 'left_pinky'
  | 'right_pinky'
  | 'left_index'
  | 'right_index'
  | 'left_thumb'
  | 'right_thumb'
  // Lower body (23-28)
  | 'left_hip'
  | 'right_hip'
  | 'left_knee'
  | 'right_knee'
  | 'left_ankle'
  | 'right_ankle'
  // Feet (29-32)
  | 'left_heel'
  | 'right_heel'
  | 'left_foot_index'
  | 'right_foot_index'
  // Custom
  | 'spine';

/**
 * List of joints that can be used for angle tracking
 */
export const ANGLE_JOINTS: AngleJointCode[] = [
  'left_shoulder',
  'right_shoulder',
  'left_elbow',
  'right_elbow',
  'left_hip',
  'right_hip',
  'left_knee',
  'right_knee',
  'left_ankle',
  'right_ankle',
  'spine',
];

/**
 * Check if a joint code is valid for angle tracking
 */
export function isAngleJoint(code: string): code is AngleJointCode {
  return ANGLE_JOINTS.includes(code as AngleJointCode);
}
