/**
 * Core types for multi-language support and enums
 * ================================================
 */

/**
 * Localized text type for multi-language support
 */
export type LocalizedText = {
  ar: string;
  en: string;
};

/**
 * Exercise status
 */
export type ExerciseStatus = 'draft' | 'published';

/**
 * Priority level for angle rules
 */
export type PriorityLevel = 'high' | 'medium' | 'low';

/**
 * Media type
 */
export type MediaType = 'image' | 'video';

/**
 * Feedback message type
 */
export type FeedbackType = 'motivational' | 'common_mistake' | 'tip';

/**
 * Counting method codes (ALIGNED WITH ANDROID CONTRACT)
 * IMPORTANT: These MUST match the Android JSON schema exactly
 */
export type CountingMethodCode = 'up_down' | 'push_pull' | 'hold';

/**
 * Camera position codes (internal - more specific)
 */
export type CameraPositionCode = 'side_left' | 'side_right' | 'front' | 'back';

/**
 * Camera position schema codes (for Android export)
 */
export type CameraPositionSchemaCode = 'side_view' | 'front_view' | 'back_view';

/**
 * Difficulty type codes (fixed 3 levels)
 */
export type DifficultyTypeCode = 'beginner' | 'normal' | 'advanced';

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
 * Position check types (7 types as per Android schema)
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
  | 'should_equal';

/**
 * Severity levels for errors/warnings
 */
export type Severity = 'error' | 'warning' | 'tip';

/**
 * Phase names (matching Android engine)
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
 * Angle joints that can be tracked (subset of all landmarks)
 * These are the joints that support angle-based tracking
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