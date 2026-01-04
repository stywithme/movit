import { LocalizedText, CountingMethodCode } from '@/lib/types/localized';
import { getPhasesForCountingMethod } from './phase-templates';

// ============================================
// ROM (Range of Motion) Configuration
// ============================================

/**
 * ROM config for each difficulty level
 */
export interface RomConfig {
  targetAngle: number;  // Bottom/Extended position target angle
  tolerance: number;    // ±tolerance degrees acceptable
}

/**
 * Rep counting config (auto-derived from ROM or manual)
 */
export interface RepCountingConfig {
  reps?: number;              // Target reps (for workout plan)
  duration?: number;          // For Counter method (seconds)
  eccentricThreshold?: number;  // AUTO: targetAngle + tolerance
  concentricThreshold?: number; // AUTO: from startPoseAngles
}

/**
 * Start pose angle range for a single joint
 */
export interface StartPoseAngleRange {
  min: number;
  max: number;
}

/**
 * Start pose angles - joint name to angle range mapping
 */
export interface StartPoseAngles {
  [jointCode: string]: StartPoseAngleRange;  // e.g., { "left_knee": { min: 160, max: 180 } }
}

/**
 * Error messages for a joint (localized)
 */
export interface JointErrorMessages {
  tooHigh: LocalizedText;  // When angle is above expected
  tooLow: LocalizedText;   // When angle is below expected
}

/**
 * Joint role in the exercise
 */
export type JointRole = 'primary' | 'secondary';

/**
 * Tolerance settings per difficulty level for a joint
 */
export interface JointTolerances {
  beginner: number;
  normal: number;
  advanced: number;
}

/**
 * Tracked joint configuration
 */
export interface TrackedJoint {
  jointCode: string;
  jointId: string;
  role: JointRole;                   // 'primary' for rep counting, 'secondary' for feedback only
  startPose: StartPoseAngleRange;    // Range for start position
  targetAngle: number;               // Target angle at bottom/extended
  errorMessages: JointErrorMessages; // Error messages for this joint
  tolerances: JointTolerances;       // Tolerance per difficulty level for THIS joint
  pairedWith?: string;               // Joint code of paired joint (e.g., left_knee paired with right_knee)
}

/**
 * Joint pair for mirroring settings (left/right)
 */
export interface JointPair {
  left: string;   // Left joint code
  right: string;  // Right joint code
  label: string;  // Display label (e.g., "Knees", "Hips")
}

/**
 * Predefined joint pairs (matching MediaPipe Pose landmarks)
 */
export const JOINT_PAIRS: JointPair[] = [
  // Upper body
  { left: 'left_shoulder', right: 'right_shoulder', label: 'Shoulders' },
  { left: 'left_elbow', right: 'right_elbow', label: 'Elbows' },
  { left: 'left_wrist', right: 'right_wrist', label: 'Wrists' },
  // Hands
  { left: 'left_pinky', right: 'right_pinky', label: 'Pinkies' },
  { left: 'left_index', right: 'right_index', label: 'Index Fingers' },
  { left: 'left_thumb', right: 'right_thumb', label: 'Thumbs' },
  // Lower body
  { left: 'left_hip', right: 'right_hip', label: 'Hips' },
  { left: 'left_knee', right: 'right_knee', label: 'Knees' },
  { left: 'left_ankle', right: 'right_ankle', label: 'Ankles' },
  // Feet
  { left: 'left_heel', right: 'right_heel', label: 'Heels' },
  { left: 'left_foot_index', right: 'right_foot_index', label: 'Foot Index' },
];

// ============================================
// Pose Variant Types
// ============================================

/**
 * Pose variant input with movement config (new structure)
 */
export interface PoseVariantInput {
  id?: string;
  tempId?: string;
  name: LocalizedText;
  description?: LocalizedText;
  cameraPositionId: string;
  referenceImageUrl?: string;
  startPoseAngles?: StartPoseAngles;      // { "left_knee": { min, max }, ... }
  primaryJoint?: string;                   // Comma-separated primary joint codes
  trackedJointsConfig?: TrackedJoint[];    // Full tracked joints configuration
  sortOrder?: number;
}

/**
 * Movement config for a pose variant (used in wizard)
 */
export interface MovementConfig {
  trackedJoints: TrackedJoint[];    // All joints being tracked (can be multiple)
  // Rep settings per difficulty level
  beginnerReps?: number;
  normalReps?: number;
  advancedReps?: number;
  // For Counter method
  beginnerDuration?: number;
  normalDuration?: number;
  advancedDuration?: number;
  // Note: Tolerance is now per-joint in TrackedJoint.tolerances
}

// ============================================
// Difficulty Level Types
// ============================================

/**
 * Difficulty level input (basic)
 */
export interface DifficultyLevelInput {
  difficultyTypeId?: string;
  difficultyTypeCode?: string;
  name: LocalizedText;
  description?: LocalizedText;
  romConfig?: RomConfig;
  repCountingConfig?: RepCountingConfig;
  sortOrder?: number;
}

/**
 * Difficulty level input (full with phases and messages)
 */
export interface DifficultyLevelFullInput extends DifficultyLevelInput {
  poseVariantId: string;
  phases?: PhaseInput[];
  feedbackMessages?: FeedbackMessageInput[];
}

// ============================================
// Phase and Rule Types
// ============================================

/**
 * Angle rule input
 */
export interface AngleRuleInput {
  jointId: string;
  minAngle: number;
  maxAngle: number;
  errorMessageOver?: LocalizedText;
  errorMessageUnder?: LocalizedText;
  priority?: 'high' | 'medium' | 'low';
}

/**
 * Phase input
 */
export interface PhaseInput {
  code: string;
  name: LocalizedText;
  sortOrder?: number;
  angleRules?: AngleRuleInput[];
}

/**
 * Feedback message input
 */
export interface FeedbackMessageInput {
  type: 'motivational' | 'common_mistake' | 'tip';
  message: LocalizedText;
  audioUrl?: string;
  sortOrder?: number;
}

// ============================================
// Exercise Input Types
// ============================================

/**
 * Exercise creation input (basic)
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
  poseVariants?: PoseVariantInput[];
}

/**
 * Exercise update input
 */
export interface UpdateExerciseInput extends Partial<CreateExerciseInput> {
  status?: 'draft' | 'published';
}

/**
 * Complete exercise input (for wizard flow)
 */
export interface CompleteExerciseInput {
  name: LocalizedText;
  description?: LocalizedText;
  instructions?: LocalizedText;
  categoryId: string;
  countingMethodId: string;
  muscles?: string[];
  equipment?: string[];
  tags?: string[];
  poseVariants?: (PoseVariantInput & { tempId?: string; id?: string })[];
  movementConfigs?: Record<string, MovementConfig>;  // keyed by poseVariant tempId/id
  difficultyLevels?: DifficultyLevelFullInput[];
}

// ============================================
// API Response Types (for Mobile)
// ============================================

/**
 * Complete exercise config for API response
 */
export interface ExerciseConfig {
  id: string;
  name: LocalizedText;
  description?: LocalizedText;
  instructions?: LocalizedText;
  category: {
    code: string;
    name: LocalizedText;
  };
  countingMethod: string;
  primaryImage?: string;
  muscles: string[];
  equipment: string[];
  updatedAt: string;
  poseVariants: PoseVariantConfig[];
}

export interface PoseVariantConfig {
  id: string;
  name: LocalizedText;
  cameraPosition: string;
  referenceImage?: string;
  requiredJoints?: string[];
  startPoseAngles?: StartPoseAngles;
  primaryJoint?: string;
  trackedJointsConfig?: TrackedJoint[];  // Full tracked joints configuration
  difficultyLevels: DifficultyLevelConfig[];
}

export interface DifficultyLevelConfig {
  id: string;
  level: string;
  name: LocalizedText;
  description?: LocalizedText;
  romConfig?: RomConfig;
  repCountingConfig?: RepCountingConfig;
  phases: PhaseConfig[];
  feedbackMessages: {
    motivational: LocalizedText[];
    common_mistake?: LocalizedText[];
    tip?: LocalizedText[];
  };
}

export interface PhaseConfig {
  code: string;
  name: LocalizedText;
  rules: AngleRuleConfig[];
}

export interface AngleRuleConfig {
  joint: string;
  min: number;
  max: number;
  errorOver?: LocalizedText;
  errorUnder?: LocalizedText;
  priority: string;
}

// ============================================
// Helper Functions
// ============================================

/**
 * Calculate rep counting thresholds from tracked joint config
 */
export function calculateThresholds(
  trackedJoint: TrackedJoint,
  tolerance: number
): { eccentricThreshold: number; concentricThreshold: number } {
  return {
    // Eccentric: when angle goes below target + tolerance
    eccentricThreshold: trackedJoint.targetAngle + tolerance,
    // Concentric: when angle returns to near starting position (use min of start range)
    concentricThreshold: trackedJoint.startPose.min - 10, // 10° buffer
  };
}

/**
 * Build difficulty levels from movement config with phases and angle rules
 */
export function buildDifficultyLevelsFromMovementConfig(
  poseVariantId: string,
  config: MovementConfig,
  countingMethodCode: CountingMethodCode,
  feedbackMessages?: FeedbackMessageInput[]
): DifficultyLevelFullInput[] {
  const levels: DifficultyLevelFullInput[] = [];
  const primaryJoints = config.trackedJoints.filter(j => j.role === 'primary');
  
  if (primaryJoints.length === 0) {
    return levels;
  }

  // Use first primary joint for threshold calculations
  const firstPrimaryJoint = primaryJoints[0];

  // Get phase templates for this counting method
  const phaseTemplates = getPhasesForCountingMethod(countingMethodCode);

  const difficultyData = [
    { 
      code: 'beginner', 
      name: { ar: 'مبتدئ', en: 'Beginner' },
      toleranceKey: 'beginner' as const,
      reps: config.beginnerReps,
      duration: config.beginnerDuration,
    },
    { 
      code: 'normal', 
      name: { ar: 'عادي', en: 'Normal' },
      toleranceKey: 'normal' as const,
      reps: config.normalReps,
      duration: config.normalDuration,
    },
    { 
      code: 'advanced', 
      name: { ar: 'محترف', en: 'Advanced' },
      toleranceKey: 'advanced' as const,
      reps: config.advancedReps,
      duration: config.advancedDuration,
    },
  ];

  for (const diff of difficultyData) {
    // Get tolerance from first primary joint for ROM config
    // Fallback to default tolerances if not set
    const defaultTolerances = createDefaultTolerances();
    const tolerance = firstPrimaryJoint.tolerances?.[diff.toleranceKey] ?? defaultTolerances[diff.toleranceKey];
    
    const romConfig: RomConfig = {
      targetAngle: firstPrimaryJoint.targetAngle,
      tolerance: tolerance,
    };

    const thresholds = calculateThresholds(firstPrimaryJoint, tolerance);

    const repCountingConfig: RepCountingConfig = {
      reps: diff.reps,
      duration: countingMethodCode === 'counter' ? diff.duration : undefined,
      eccentricThreshold: thresholds.eccentricThreshold,
      concentricThreshold: thresholds.concentricThreshold,
    };

    // Build phases with angle rules from tracked joints
    const phases: PhaseInput[] = phaseTemplates.map((template, index) => {
      // Determine angle ranges for each phase based on counting method and phase code
      const angleRules: AngleRuleInput[] = config.trackedJoints.map((joint) => {
        let minAngle = joint.startPose.min;
        let maxAngle = joint.startPose.max;

        // Adjust angles based on phase
        if (countingMethodCode === 'up_down') {
          if (template.code === 'start' || template.code === 'up') {
            // Start and Up phases: use start pose range
            minAngle = joint.startPose.min;
            maxAngle = joint.startPose.max;
          } else if (template.code === 'down' || template.code === 'bottom') {
            // Down and Bottom phases: use target angle ± tolerance
            const jointTolerance = joint.tolerances?.[diff.toleranceKey] ?? tolerance;
            minAngle = Math.max(0, joint.targetAngle - jointTolerance);
            maxAngle = Math.min(180, joint.targetAngle + jointTolerance);
          }
        } else if (countingMethodCode === 'push_pull') {
          if (template.code === 'start' || template.code === 'pull') {
            // Start and Pull phases: use start pose range
            minAngle = joint.startPose.min;
            maxAngle = joint.startPose.max;
          } else if (template.code === 'push' || template.code === 'extended') {
            // Push and Extended phases: use target angle ± tolerance
            const jointTolerance = joint.tolerances?.[diff.toleranceKey] ?? tolerance;
            minAngle = Math.max(0, joint.targetAngle - jointTolerance);
            maxAngle = Math.min(180, joint.targetAngle + jointTolerance);
          }
        } else if (countingMethodCode === 'counter') {
          // Counter: all phases use start pose range
          minAngle = joint.startPose.min;
          maxAngle = joint.startPose.max;
        }

        return {
          jointId: joint.jointId,
          minAngle,
          maxAngle,
          errorMessageOver: joint.errorMessages.tooHigh,
          errorMessageUnder: joint.errorMessages.tooLow,
          priority: joint.role === 'primary' ? 'high' : 'medium',
        };
      });

      return {
        code: template.code,
        name: template.name,
        sortOrder: template.sortOrder,
        angleRules,
      };
    });

    levels.push({
      poseVariantId,
      difficultyTypeCode: diff.code,
      name: diff.name,
      description: { ar: '', en: '' },
      romConfig,
      repCountingConfig,
      phases,
      feedbackMessages: feedbackMessages || [],
    });
  }

  return levels;
}

/**
 * Convert TrackedJoints to StartPoseAngles format for database
 */
export function trackedJointsToStartPoseAngles(trackedJoints: TrackedJoint[]): StartPoseAngles {
  const result: StartPoseAngles = {};
  for (const joint of trackedJoints) {
    result[joint.jointCode] = joint.startPose;
  }
  return result;
}

/**
 * Get primary joint codes from tracked joints (supports multiple primary joints)
 */
export function getPrimaryJointCodes(trackedJoints: TrackedJoint[]): string[] {
  return trackedJoints.filter(j => j.role === 'primary').map(j => j.jointCode);
}

/**
 * Get first primary joint code from tracked joints (for backwards compatibility)
 */
export function getPrimaryJointCode(trackedJoints: TrackedJoint[]): string | undefined {
  return trackedJoints.find(j => j.role === 'primary')?.jointCode;
}

/**
 * Get all primary joints from tracked joints
 */
export function getPrimaryJoints(trackedJoints: TrackedJoint[]): TrackedJoint[] {
  return trackedJoints.filter(j => j.role === 'primary');
}

/**
 * Get secondary joints from tracked joints
 */
export function getSecondaryJoints(trackedJoints: TrackedJoint[]): TrackedJoint[] {
  return trackedJoints.filter(j => j.role === 'secondary');
}

/**
 * Check if all primary joints have reached the target angle
 */
export function areAllPrimaryJointsAtTarget(
  trackedJoints: TrackedJoint[],
  currentAngles: Record<string, number>,
  tolerance: number
): boolean {
  const primaryJoints = getPrimaryJoints(trackedJoints);
  
  return primaryJoints.every(joint => {
    const currentAngle = currentAngles[joint.jointCode];
    if (currentAngle === undefined) return false;
    return currentAngle <= joint.targetAngle + tolerance;
  });
}

/**
 * Get error messages for joints that are out of range
 */
export function getJointErrors(
  trackedJoints: TrackedJoint[],
  currentAngles: Record<string, number>,
  tolerance: number
): Array<{ jointCode: string; type: 'tooHigh' | 'tooLow'; message: LocalizedText }> {
  const errors: Array<{ jointCode: string; type: 'tooHigh' | 'tooLow'; message: LocalizedText }> = [];
  
  for (const joint of trackedJoints) {
    const currentAngle = currentAngles[joint.jointCode];
    if (currentAngle === undefined) continue;
    
    // Check if angle is too high (above start pose max)
    if (currentAngle > joint.startPose.max + 10) {
      errors.push({
        jointCode: joint.jointCode,
        type: 'tooHigh',
        message: joint.errorMessages.tooHigh,
      });
    }
    // Check if angle is too low (below target - tolerance)
    else if (currentAngle < joint.targetAngle - tolerance - 10) {
      errors.push({
        jointCode: joint.jointCode,
        type: 'tooLow',
        message: joint.errorMessages.tooLow,
      });
    }
  }
  
  return errors;
}

/**
 * Create default error messages for a joint
 */
export function createDefaultErrorMessages(jointCode: string): JointErrorMessages {
  const jointName = jointCode.replace(/_/g, ' ').replace(/left |right /gi, '');
  const isLeft = jointCode.startsWith('left_');
  const side = isLeft ? 'left' : 'right';
  const sideAr = isLeft ? 'اليسرى' : 'اليمنى';
  
  return {
    tooHigh: {
      ar: `اثني ${jointName} ${sideAr} أكثر`,
      en: `Bend your ${side} ${jointName} more`,
    },
    tooLow: {
      ar: `لا تنزل ${jointName} ${sideAr} كثيراً`,
      en: `Don't lower your ${side} ${jointName} too much`,
    },
  };
}

/**
 * Create default tolerances for a joint
 */
export function createDefaultTolerances(): JointTolerances {
  return {
    beginner: 30,
    normal: 15,
    advanced: 5,
  };
}

/**
 * Copy joint settings to another joint (for mirroring left/right)
 */
export function copyJointSettings(
  source: TrackedJoint,
  targetJointCode: string,
  targetJointId: string
): TrackedJoint {
  const isTargetLeft = targetJointCode.startsWith('left_');
  const sideAr = isTargetLeft ? 'اليسرى' : 'اليمنى';
  const side = isTargetLeft ? 'left' : 'right';
  const jointName = targetJointCode.replace(/_/g, ' ').replace(/left_|right_/gi, '');
  
  return {
    jointCode: targetJointCode,
    jointId: targetJointId,
    role: source.role,
    startPose: { ...source.startPose },
    targetAngle: source.targetAngle,
    tolerances: { ...source.tolerances }, // Copy tolerances from source
    errorMessages: {
      tooHigh: {
        ar: `اثني ${jointName} ${sideAr} أكثر`,
        en: `Bend your ${side} ${jointName} more`,
      },
      tooLow: {
        ar: `لا تنزل ${jointName} ${sideAr} كثيراً`,
        en: `Don't lower your ${side} ${jointName} too much`,
      },
    },
    pairedWith: source.jointCode,
  };
}
