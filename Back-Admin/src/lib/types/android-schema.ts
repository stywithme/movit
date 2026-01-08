/**
 * Android JSON Schema Types
 * =========================
 * 
 * These types EXACTLY match the Android app's expected JSON format.
 * This is the SINGLE SOURCE OF TRUTH for the mobile contract.
 * 
 * Reference: /Docs/Exercise-JSON-Schema.md
 * 
 * IMPORTANT RULES:
 * 1. Never send `null` for any field - omit the key instead
 * 2. Use exact casing as specified (e.g., "Range" with capital R for secondary joints)
 * 3. All LocalizedText must have both `ar` and `en` keys
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
 * Difficulty level codes
 */
export type DifficultyLevel = 'beginner' | 'normal' | 'advanced';

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
  | 'should_equal';

/**
 * Severity levels for errors
 */
export type Severity = 'error' | 'warning' | 'tip';

/**
 * Phase names (used in activePhases and phases array)
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
  | 'count'; // HOLD exercises use 'count' phase

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
 * Ranges per difficulty level
 */
export interface DifficultyRanges {
  beginner: AngleRange;
  normal: AngleRange;
  advanced: AngleRange;
}

/**
 * Thresholds per difficulty (for position checks)
 */
export interface DifficultyThresholds {
  beginner: number;
  normal: number;
  advanced: number;
}

// ============================================
// TRACKED JOINTS
// ============================================

/**
 * Error messages for a tracked joint
 */
export interface JointErrorMessages {
  tooLow: LocalizedText;
  tooHigh: LocalizedText;
}

/**
 * Tracked joint configuration (PRIMARY joints)
 * IMPORTANT: Primary joints MUST have upRange and downRange
 */
export interface PrimaryTrackedJoint {
  joint: string;
  role: 'primary';
  startPose: AngleRange;
  upRange: DifficultyRanges;
  downRange: DifficultyRanges;
  errorMessages: JointErrorMessages;
  pairedWith?: string;
}

/**
 * Tracked joint configuration (SECONDARY joints)
 * IMPORTANT: Secondary joints MUST have Range (capital R!)
 */
export interface SecondaryTrackedJoint {
  joint: string;
  role: 'secondary';
  startPose: AngleRange;
  Range: DifficultyRanges; // Capital R - Android expects this casing!
  errorMessages: JointErrorMessages;
  pairedWith?: string;
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
 * Condition for position check
 */
export interface PositionCheckCondition {
  operator: ConditionOperator;
  thresholds: DifficultyThresholds;
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
 * Rep counting configuration for UP_DOWN / PUSH_PULL
 */
export interface RepBasedConfig {
  reps: number;
  minRepIntervalMs?: number;
  maxRepIntervalMs?: number;
}

/**
 * Rep counting configuration for HOLD
 */
export interface HoldBasedConfig {
  duration: number; // seconds
  gracePeriodMs?: number;
}

/**
 * Union type for rep counting config
 */
export type RepCountingConfig = RepBasedConfig | HoldBasedConfig;

// ============================================
// FEEDBACK MESSAGES
// ============================================

/**
 * Grouped feedback messages
 */
export interface FeedbackMessages {
  motivational: LocalizedText[];
  common_mistake: LocalizedText[];
  tip: LocalizedText[];
}

// ============================================
// DIFFICULTY LEVEL CONFIG
// ============================================

/**
 * Difficulty level configuration for Android
 */
export interface DifficultyLevelConfig {
  level: DifficultyLevel;
  repCountingConfig: RepCountingConfig;
  phases: PhaseName[];
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
  expectedFacingDirection: FacingDirection;
  trackedJoints: TrackedJoint[];
  positionChecks: PositionCheck[];
  feedbackMessages: FeedbackMessages;
  difficultyLevels: DifficultyLevelConfig[];
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
export function isHoldConfig(config: RepCountingConfig): config is HoldBasedConfig {
  return 'duration' in config;
}

/**
 * Check if rep counting config is for rep-based exercise
 */
export function isRepBasedConfig(config: RepCountingConfig): config is RepBasedConfig {
  return 'reps' in config;
}

// ============================================
// VALIDATION HELPERS
// ============================================

/**
 * Validate that a primary joint has required fields
 */
export function validatePrimaryJoint(joint: TrackedJoint): string[] {
  const errors: string[] = [];
  
  if (joint.role !== 'primary') return errors;
  
  const primary = joint as PrimaryTrackedJoint;
  
  if (!primary.upRange) {
    errors.push(`Primary joint ${primary.joint} is missing upRange`);
  }
  if (!primary.downRange) {
    errors.push(`Primary joint ${primary.joint} is missing downRange`);
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
  
  if (!secondary.Range) {
    errors.push(`Secondary joint ${secondary.joint} is missing Range (capital R)`);
  }
  
  return errors;
}

/**
 * Validate complete exercise config before export
 */
export function validateExerciseConfig(config: ExerciseConfig): string[] {
  const errors: string[] = [];
  
  // Must have at least one pose variant
  if (!config.poseVariants || config.poseVariants.length === 0) {
    errors.push('Exercise must have at least one pose variant');
  }
  
  for (const variant of config.poseVariants) {
    // Must have at least one tracked joint
    if (!variant.trackedJoints || variant.trackedJoints.length === 0) {
      errors.push(`Pose variant "${variant.name.en}" must have at least one tracked joint`);
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
    
    // Must have all 3 difficulty levels
    if (!variant.difficultyLevels || variant.difficultyLevels.length !== 3) {
      errors.push(`Pose variant "${variant.name.en}" must have exactly 3 difficulty levels`);
    }
    
    const levels = variant.difficultyLevels.map(d => d.level);
    if (!levels.includes('beginner')) {
      errors.push(`Pose variant "${variant.name.en}" is missing beginner difficulty`);
    }
    if (!levels.includes('normal')) {
      errors.push(`Pose variant "${variant.name.en}" is missing normal difficulty`);
    }
    if (!levels.includes('advanced')) {
      errors.push(`Pose variant "${variant.name.en}" is missing advanced difficulty`);
    }
  }
  
  return errors;
}
