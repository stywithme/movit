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
 * Counting method codes
 */
export type CountingMethodCode = 'counter' | 'up_down' | 'push_pull';

/**
 * Camera position codes
 */
export type CameraPositionCode = 'side' | 'front' | 'back' | 'angle_45';

/**
 * Difficulty type codes (fixed 3 levels)
 */
export type DifficultyTypeCode = 'beginner' | 'normal' | 'advanced';

/**
 * Joint codes (matching MediaPipe Pose landmarks 11-32 + custom spine)
 */
export type JointCode =
  // Upper body (11-14)
  | 'left_shoulder'
  | 'right_shoulder'
  | 'left_elbow'
  | 'right_elbow'
  // Wrists (15-16)
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
